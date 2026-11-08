package io.devicetruth.core.runtime;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.CapabilityResult;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.decision.DecisionGuard;
import io.devicetruth.core.evidence.EvidenceManager;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.ActionProposal.*;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.state.DiagnosisState;
import io.devicetruth.core.state.StateManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 任务编排器：诊断运行的主循环。每轮迭代中推理器提出恰好一个动作，
 *  编排器校验并执行（至多一个能力调用），提交状态后给出新的状态快照。
 *  所有关键节点都会写入事件流，形成完整的可追溯审计轨迹。 */
public final class TaskOrchestrator {
    /** 运行事件：第几个事件、发生时刻、类型、当时的状态版本及载荷数据。 */
    public record RunEvent(int sequence,Instant recordedAt,String type,long stateVersion,Map<String,Object> data) {
        public RunEvent { data=Map.copyOf(data); }
    }
    /** 运行报告：结果 + 最终状态 + 全部证据 + 事件流。 */
    public record RunReport(DiagnosisResult result,DiagnosisState state,List<EvidenceRecord> evidence,List<RunEvent> events) {
        public RunReport { evidence=List.copyOf(evidence); events=List.copyOf(events); }
    }
    /** 重复检测指纹：动作指纹 + 当时知识快照的组合，用于识别"原地空转"。 */
    private record Repetition(String knowledge,int count) {}
    private final CapabilityGateway gateway;
    private final EvidenceManager evidenceManager;
    private final DecisionGuard guard;
    public TaskOrchestrator(CapabilityGateway gateway,EvidenceManager evidenceManager,DecisionGuard guard) {
        this.gateway=gateway; this.evidenceManager=evidenceManager; this.guard=guard;
    }
    /** 主循环：状态为 ACTIVE 时持续迭代，直到得出结论、预算耗尽或发生技术性失败。 */
    public RunReport run(DiagnosisRequest request,Budget budget,Reasoner reasoner) {
        var manager=new StateManager(request,budget); var events=new ArrayList<RunEvent>();
        var repetitions=new HashMap<String,Repetition>(); var warnings=new ArrayList<String>();
        Outcome outcome=null; String reason=""; Rule approved=null; List<String> supporting=List.of();
        while(manager.snapshot().status()==RunStatus.ACTIVE) {
            var s=manager.snapshot();
            // 组装推理器上下文：状态快照 + 能力目录 + 已取回证据（按 ID 排序保证确定性）
            var context=new Reasoner.Context(s,gateway.catalog(),manager.evidence().values().stream().sorted(java.util.Comparator.comparing(EvidenceRecord::evidenceId)).toList());
            event(events,"REASON_INPUT",s.version(),Map.of("context",context,"reasoner",reasoner.name()));
            ActionProposal action;
            try {
                action=reasoner.propose(context);
                if(action==null) throw new IllegalArgumentException("Reasoner returned null proposal");
            } catch(RuntimeException e) {
                // 推理器自身失败（如模型超时/密钥缺失）：记为技术性失败，状态置 FAILED 后退出
                reason=e instanceof ReasonerFailure failure?failure.code():"REASONER_FAILURE";
                manager.commit(s.version(),null,null,s.facts(),s.hypotheses(),s.evidenceNeeds(),null,List.of(),RunStatus.FAILED,reason,manager.evidence());
                event(events,"TECHNICAL_FAILURE",manager.snapshot().version(),Map.of("code",reason));
                break;
            } finally {
                // 无论成败， draining 本轮的模型尝试记录（如 qwen 的重试轨迹）写入事件流
                for(var attempt:reasoner.drainAttempts()) event(events,"MODEL_ATTEMPT",s.version(),Map.of("attempt",attempt));
            }
            event(events,"ACTION_PROPOSED",s.version(),Map.of("proposal",action));
            var facts=s.facts(); var hypotheses=new ArrayList<>(s.hypotheses()); var needs=new ArrayList<>(s.evidenceNeeds());
            var records=manager.evidence(); CapabilityResult observation=null;
            String selectedRule=s.selectedRuleId(); var selectedFacts=s.selectedRuleFactIds();
            RunStatus status=RunStatus.ACTIVE; String detail="accepted";
            String fingerprint=Json.write(action);
            String knowledge=Json.write(List.of(s.facts(),s.knownRules(),records.keySet().stream().sorted().toList()));
            // 重复检测：同一动作在"知识没有增长"的情况下重复出现，计数累加
            var previous=repetitions.get(fingerprint);
            int count=previous!=null&&previous.knowledge().equals(knowledge)?previous.count()+1:1;
            repetitions.put(fingerprint,new Repetition(knowledge,count));
            // 超过重复上限：判定空转。若期间有可重试的工具失败则升级为人工接管，否则结论为证据不足
            if(count>budget.maxRepeatedActions()) {
                reason="REPEATED_ACTION_WITHOUT_NEW_INFORMATION"; outcome=Outcome.NEED_EVIDENCE;
                if(s.actionHistory().stream().anyMatch(h->h.observation()!=null&&!h.observation().success()&&h.observation().error().retryable())) {
                    outcome=Outcome.ESCALATE; reason="TOOL_FAILURE_NO_PROGRESS";
                }
                status=RunStatus.COMPLETED; needs.add(need("NEED-PROGRESS","重复动作未能产生新证据",NeedSource.AGENT,null));
            } else if(action instanceof CallCapability call) {
                // 能力调用：先核对声明针对的证据缺口存在，再交给网关执行；成功后重新推导事实并失效依赖
                if(!needs.stream().map(EvidenceNeed::needId).toList().containsAll(call.targetEvidenceNeedIds())) {
                    detail="ACTION_REJECTED: unknown targetEvidenceNeedIds";
                } else {
                    observation=gateway.execute(request,call);
                    event(events,"CAPABILITY_RESULT",s.version(),Map.of("result",observation));
                    if(observation.success()) {
                        try {
                            var analysis=evidenceManager.analyze(request,records,observation.data().records());
                            facts=analysis.facts(); records=analysis.records();
                            var revised=evidenceManager.invalidateDependencies(hypotheses,facts,records);
                            for(var hypothesis:revised) {
                                if(hypothesis.status()==HypothesisStatus.INVALIDATED&&hypotheses.stream().anyMatch(h->h.hypothesisId().equals(hypothesis.hypothesisId())&&h.status()!=HypothesisStatus.INVALIDATED))
                                    event(events,"HYPOTHESIS_INVALIDATED",s.version(),Map.of("hypothesis",hypothesis));
                            }
                            hypotheses=new ArrayList<>(revised);
                            var validIds=facts.stream().filter(f->f.status()==FactStatus.SUPPORTED).map(Fact::factId).toList();
                            if(selectedRule!=null&&!validIds.containsAll(selectedFacts)) {
                                event(events,"RULE_SELECTION_INVALIDATED",s.version(),Map.of("ruleId",selectedRule));
                                selectedRule=null; selectedFacts=List.of();
                            }
                        } catch(IllegalArgumentException e) {
                            detail="EVIDENCE_REJECTED: "+e.getMessage(); warnings.add(detail);
                        }
                    } else { detail="GATEWAY_REJECTED: "+observation.error().code(); warnings.add(detail); }
                }
            } else if(action instanceof ProposeHypothesis proposal) {
                var validFactIds=s.facts().stream().filter(f->f.status()==FactStatus.SUPPORTED).map(Fact::factId).toList();
                var validEvidenceIds=s.facts().stream().filter(f->f.status()==FactStatus.SUPPORTED).flatMap(f->f.evidenceIds().stream()).toList();
                boolean valid=validFactIds.containsAll(proposal.dependsOnFactIds())&&validEvidenceIds.containsAll(proposal.dependsOnEvidenceIds())
                        &&records.keySet().containsAll(proposal.dependsOnEvidenceIds());
                if(valid) hypotheses.add(new Hypothesis("H-"+(hypotheses.size()+1),proposal.statement(),proposal.dependsOnFactIds(),proposal.dependsOnEvidenceIds(),HypothesisStatus.OPEN,null));
                else detail="ACTION_REJECTED: hypothesis dependencies do not resolve to supported facts/evidence";
            } else if(action instanceof ProposeDecision proposal) {
                // 请求下结论：由决策守卫做全量校验；放行则运行结束，否则按守卫给出的缺口/终局继续
                var verdict=guard.evaluate(s,records,proposal);
                event(events,"GUARD_RESULT",s.version(),Map.of("verdict",verdict));
                needs=new ArrayList<>(verdict.needs()); detail=verdict.reason();
                if(!verdict.allowed()&&"MISSING_REQUIRED_EVIDENCE".equals(verdict.reason())&&verdict.rule()!=null&&!proposal.claimedFactIds().isEmpty()) {
                    var supportedFacts=s.facts().stream().filter(f->f.status()==FactStatus.SUPPORTED&&proposal.claimedFactIds().contains(f.factId())).toList();
                    if(supportedFacts.size()==proposal.claimedFactIds().stream().distinct().count()&&verdict.rule().conditions().stream().allMatch(c->supportedFacts.stream().anyMatch(c::matches))) {
                        selectedRule=verdict.rule().ruleId();selectedFacts=proposal.claimedFactIds();
                    }
                }
                if(verdict.allowed()) {
                    // 结论被采纳：记录规则与支撑证据，运行以 RESOLVED 结束
                    approved=verdict.rule(); selectedRule=approved.ruleId(); selectedFacts=proposal.claimedFactIds();
                    supporting=verdict.evidenceIds(); outcome=Outcome.RESOLVED; status=RunStatus.COMPLETED; reason=verdict.reason();
                } else if(verdict.terminalOutcome()!=null) {
                    // 守卫给出终局（证据不足/冲突/需人工）：直接结束
                    outcome=verdict.terminalOutcome(); status=RunStatus.COMPLETED; reason=verdict.reason();
                }
            } else if(action instanceof RequestInformation proposal) {
                int index=0;
                for(String field:proposal.missingFields()) needs.add(need("NEED-AGENT-"+(++index),field,NeedSource.AGENT,null));
                outcome=guard.review(s,records).conflict()?Outcome.CONFLICT:Outcome.NEED_EVIDENCE;
                status=RunStatus.COMPLETED; reason=proposal.reason();
            } else if(action instanceof Escalate proposal) {
                outcome=Outcome.ESCALATE; status=RunStatus.COMPLETED; reason=proposal.reason();
            }
            // 能力调用成功后：刷新规则/已发现证据清单，并由守卫重审需求；
            // 若调查已完成且存在证据冲突，直接以 CONFLICT 终局（同等权威证据打架，机器不下结论）
            if(observation!=null&&observation.success()) {
                var history=new ArrayList<>(s.actionHistory());
                history.add(new DiagnosisState.ActionHistory(history.size()+1,s.version(),action,observation,detail));
                var rules=new java.util.LinkedHashMap<String,Rule>(); s.knownRules().forEach(r->rules.put(r.ruleId(),r)); observation.data().rules().forEach(r->rules.put(r.ruleId(),r));
                var discovered=new java.util.LinkedHashMap<String,EvidenceMetadata>(); s.discoveredEvidence().forEach(e->discovered.put(e.evidenceId(),e)); observation.data().discovered().forEach(e->discovered.put(e.evidenceId(),e));
                var view=new DiagnosisState(s.taskId(),s.version(),s.task(),facts,hypotheses,needs,selectedRule,selectedFacts,history,s.budget(),status,new ArrayList<>(rules.values()),new ArrayList<>(discovered.values()));
                var review=guard.review(view,records);
                needs.removeIf(n->n.source()==NeedSource.RULE); needs.addAll(review.needs());
                event(events,"GUARD_REVIEW",s.version(),Map.of("review",review));
                if(status==RunStatus.ACTIVE&&review.conflict()&&guard.investigationComplete(view,records)) {
                    outcome=Outcome.CONFLICT;status=RunStatus.COMPLETED;reason="CURRENT_EVIDENCE_CONFLICT";
                    supporting=review.conflictingEvidenceIds();selectedRule=null;selectedFacts=List.of();
                }
            }
            // 步数预算耗尽：以"证据不足"结束，并记录一条需要追加预算的开放需求
            if(status==RunStatus.ACTIVE&&s.actionHistory().size()+1>=budget.maxSteps()) {
                outcome=Outcome.NEED_EVIDENCE; status=RunStatus.COMPLETED; reason="EXECUTION_BUDGET_EXHAUSTED";
                needs.add(need("NEED-BUDGET","进一步调查需要追加执行预算",NeedSource.AGENT,null));
            }
            var next=manager.commit(s.version(),action,observation,facts,hypotheses,needs,selectedRule,selectedFacts,status,detail,records);
            event(events,"STATE_COMMITTED",next.version(),Map.of("inputVersion",s.version(),"facts",next.facts(),"hypotheses",next.hypotheses(),"needs",next.evidenceNeeds()));
        }
        // 组装最终结果：采纳规则时给出模拟建议，否则给出停止原因作为摘要
        var state=manager.snapshot();
        var result=new DiagnosisResult(state.taskId(),outcome,request.assetId(),request.asOf(),
                approved!=null?approved.simulatedAdvice():"调查已停止："+reason,
                approved==null?List.of():List.of(approved.ruleId()),supporting,
                state.evidenceNeeds().stream().filter(n->n.status()!=NeedStatus.SATISFIED).toList(),warnings,reason,
                state.actionHistory().size(),state.version(),reasoner.name(),"SIMULATED",state.status());
        event(events,"TERMINATED",state.version(),Map.of("result",result));
        return new RunReport(result,state,manager.evidence().values().stream().sorted(java.util.Comparator.comparing(EvidenceRecord::evidenceId)).toList(),events);
    }
    private static EvidenceNeed need(String id,String description,NeedSource source,String ruleId) { return new EvidenceNeed(id,description,source,NeedStatus.OPEN,ruleId); }
    private static void event(List<RunEvent> events,String type,long version,Map<String,Object> data) {
        events.add(new RunEvent(events.size()+1,Instant.now(),type,version,data));
    }
    /** 推理器失败异常：code 为稳定的错误码（如 DASHSCOPE_API_KEY_MISSING），区别于普通运行异常。 */
    public static final class ReasonerFailure extends RuntimeException {
        private final String code;
        public ReasonerFailure(String code) { super(code); this.code=code; }
        public String code() { return code; }
    }
}
