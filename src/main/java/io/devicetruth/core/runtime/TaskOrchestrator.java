package io.devicetruth.core.runtime;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.CapabilityResult;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput;
import io.devicetruth.core.contract.Checks;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.decision.AnswerGuard;
import io.devicetruth.core.decision.DecisionGuard;
import io.devicetruth.core.evidence.EvidenceManager;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.ActionProposal.*;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.state.DiagnosisState;
import io.devicetruth.core.state.StateManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 任务编排器：诊断运行的主循环。每轮迭代中推理器提出恰好一个动作，
 *  编排器校验并执行（至多一个能力调用），提交状态后给出新的状态快照。
 *  所有关键节点都会写入事件流，形成完整的可追溯审计轨迹。
 *  runAgent 是同一编排器上的通用 Agent 循环：状态存 PG，工具经网关，答案经 AnswerGuard。 */
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
    /** 通用 Agent 运行状态：等待用户补答不是终局，技术失败不产生答案；取消是显式终态，不伪装成失败。 */
    public enum AgentStatus { WAITING_FOR_USER, COMPLETED, FAILED, CANCELLED }
    /** 通用 Agent 一轮运行的结果：状态、答案/待答问题、消息序列、引用、事件与步数。 */
    public record AgentRunOutcome(AgentStatus status, AgentResult answer, AgentTaskInput.PendingQuestion question,
                                  List<AgentTaskInput.Message> messages, List<Reference> references,
                                  List<RunEvent> events, int stepsUsed, String stopReason) {
        public AgentRunOutcome {
            Checks.required(status, "status");
            messages = List.copyOf(messages);
            references = List.copyOf(references);
            events = List.copyOf(events);
        }
    }
    private final CapabilityGateway gateway;
    private final EvidenceManager evidenceManager;
    private final DecisionGuard guard;
    /** 通用答案守卫：引用成立性与风险由服务器判定，无状态、可共享。 */
    private final AnswerGuard answerGuard=new AnswerGuard();
    public TaskOrchestrator(CapabilityGateway gateway,EvidenceManager evidenceManager,DecisionGuard guard) {
        this.gateway=gateway; this.evidenceManager=evidenceManager; this.guard=guard;
    }
    /** 通用 Agent 只需网关（不构造 legacy 的证据/决策守卫）。 */
    public TaskOrchestrator(CapabilityGateway gateway) { this(gateway,null,null); }
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
            } else {
                // 通用协议动作（CALL_TOOL/ASK_USER/ANSWER）不属于 legacy 诊断语义：拒绝而不是静默放行
                detail="ACTION_REJECTED: unsupported action type for legacy diagnosis";
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
    /**
     * 通用 Agent 主循环：状态与消息来自 PG，工具经网关（只读、已授权、逐参数校验），
     * 答案经 AnswerGuard 校验引用与风险。每轮只执行一个动作，事件流用于审计（不持久化推理输入）。
     * 返回 WAITING_FOR_USER 时 worker 立刻退出；补答后由应用层以新的上下文调用同一 run。
     */
    public AgentRunOutcome runAgent(AgentTaskInput input, CapabilityGateway gateway, ToolContext toolContext,
                                    List<Reference> seedReferences, GeneralReasoner reasoner) {
        // 默认无休中止条件；带取消/总时限的执行走下面的重载。
        return runAgent(input, gateway, toolContext, seedReferences, reasoner, () -> null);
    }

    /**
     * 通用 Agent 主循环（带协作式中止）：每一步开头先问 abortCode——
     * 返回非空即收尾（"CANCELLED" 为显式取消终态，"TOTAL_TIMEOUT" 记 FAILED），
     * 已产生的模型/工具结果随终态一起由应用层按 CAS 提交，迟到响应不会覆盖既有终态。
     */
    public AgentRunOutcome runAgent(AgentTaskInput input, CapabilityGateway gateway, ToolContext toolContext,
                                    List<Reference> seedReferences, GeneralReasoner reasoner,
                                    java.util.function.Supplier<String> abortCode) {
        return runAgent(input, gateway, toolContext, seedReferences, reasoner, abortCode,
                () -> new SkillStep(toolContext, List.of(), null));
    }

    /** 由产品服务在每步重验领域指引及工具权限，主循环仍负责实际调用。 */
    public record SkillStep(ToolContext toolContext, List<GeneralReasoner.SkillSummary> availableSkills,
                            GeneralReasoner.ActiveSkill activeSkill) {}

    public AgentRunOutcome runAgent(AgentTaskInput input, CapabilityGateway gateway, ToolContext toolContext,
                                    List<Reference> seedReferences, GeneralReasoner reasoner,
                                    java.util.function.Supplier<String> abortCode,
                                    java.util.function.Supplier<SkillStep> skillStep) {
        return runAgent(input, gateway, toolContext, seedReferences, reasoner, abortCode, skillStep, null);
    }

    /**
     * 通用 Agent 主循环（带渐进持久进度回调，05-01 复验 §8.1）：
     * 每个内部事件记录点同步回调一次（先内存事件、再回调），应用层据此把
     * TOOL_RESULT/MODEL_ATTEMPT 立即以短事务落库为 RUN_TOOL/RUN_MODEL；
     * 回调不得抛异常破坏主循环，应用层自行吞掉落库失败。
     */
    public AgentRunOutcome runAgent(AgentTaskInput input, CapabilityGateway gateway, ToolContext toolContext,
                                    List<Reference> seedReferences, GeneralReasoner reasoner,
                                    java.util.function.Supplier<String> abortCode,
                                    java.util.function.Supplier<SkillStep> skillStep,
                                    java.util.function.Consumer<RunEvent> progress) {
        return runAgent(input, gateway, toolContext, seedReferences, reasoner, abortCode, skillStep,
                progress, List.of(), null, step -> {}, java.util.function.Supplier::get, checkpoint -> {});
    }

    public record ToolStep(AgentExecutionCheckpoint checkpoint, Map<String, Object> audit) {}

    public AgentRunOutcome runAgent(AgentTaskInput input, CapabilityGateway gateway, ToolContext toolContext,
                                    List<Reference> seedReferences, GeneralReasoner reasoner,
                                    java.util.function.Supplier<String> abortCode,
                                    java.util.function.Supplier<SkillStep> skillStep,
                                    java.util.function.Consumer<RunEvent> progress,
                                    List<AgentTaskInput.Message> conversationHistory,
                                    AgentExecutionCheckpoint checkpoint, java.util.function.IntConsumer reserveStep,
                                    java.util.function.Function<java.util.function.Supplier<ToolStep>, ToolStep> executeTool,
                                    java.util.function.Consumer<AgentExecutionCheckpoint> saveCheckpoint) {
        if (input == null || gateway == null || toolContext == null || reasoner == null)
            throw new IllegalArgumentException("input, gateway, toolContext and reasoner are required");
        var events = new ArrayList<RunEvent>();
        var messages = new ArrayList<>(input.messages());
        var references = new LinkedHashMap<String, Reference>();
        for (Reference reference : Checks.list(seedReferences)) mergeReference(references, reference);
        if (checkpoint != null && (!input.runId().equals(checkpoint.runId())
                || checkpoint.step() > input.stepsUsed()))
            throw new IllegalArgumentException("Checkpoint does not match the run budget");
        var attemptedTools = new ArrayList<>(checkpoint == null ? List.<String>of() : checkpoint.attemptedTools());
        var toolResults = new ArrayList<>(checkpoint == null ? List.<GeneralReasoner.ToolResult>of()
                : checkpoint.toolResults());
        String feedback = checkpoint == null ? null : checkpoint.feedback();
        int steps = input.stepsUsed();
        int rejections = checkpoint == null ? 0 : checkpoint.rejections();
        while (true) {
            // 协作式取消/总时限：模型每步之间检查，终态由持久层 CAS 收口
            String abort = abortCode == null ? null : abortCode.get();
            if (abort != null) return aborted(input, messages, references, events, steps, abort, progress);
            if (steps >= input.maxSteps()) {
                event(events, "RUN_FAILED", input.runVersion(), Map.of("code", "BUDGET_EXHAUSTED"), progress);
                return new AgentRunOutcome(AgentStatus.FAILED, null, null, messages,
                        List.copyOf(references.values()), events, steps, "BUDGET_EXHAUSTED");
            }
            var skill = skillStep.get();
            var context = new GeneralReasoner.Context(input.goalText(), messages, gateway.toolCatalog(skill.toolContext()),
                    List.copyOf(references.values()), attemptedTools, steps, input.maxSteps(), feedback,
                    skill.availableSkills(), skill.activeSkill(), input.selectedAssetId(), input.asOf(), toolResults,
                    conversationHistory);
            event(events, "REASON_INPUT", input.runVersion(),
                    Map.of("stepsUsed", steps, "tools", context.tools().size(), "references", context.references().size()),
                    progress);
            reserveStep.accept(++steps);
            ActionProposal action;
            try {
                action = reasoner.propose(context, abortCode == null ? () -> null : abortCode);
                if (action == null) throw new IllegalArgumentException("Reasoner returned null proposal");
            } catch (RuntimeException e) {
                // 模型配置/传输/输出失败：技术失败，不产生答案，也不伪装成缺证
                String code = e instanceof ReasonerFailure failure ? failure.code() : "REASONER_FAILURE";
                for (var attempt : reasoner.drainAttempts())
                    event(events, "MODEL_ATTEMPT", input.runVersion(), modelAttempt(reasoner, attempt), progress);
                abort = abortCode == null ? null : abortCode.get();
                if (abort != null) return aborted(input, messages, references, events, steps, abort, progress);
                event(events, "RUN_FAILED", input.runVersion(), Map.of("code", code), progress);
                return new AgentRunOutcome(AgentStatus.FAILED, null, null, messages,
                        List.copyOf(references.values()), events, steps, code);
            }
            for (var attempt : reasoner.drainAttempts())
                event(events, "MODEL_ATTEMPT", input.runVersion(), modelAttempt(reasoner, attempt), progress);
            // 单次模型请求可能跨过总时限，不能将迟到的工具、追问或答案交给后续处理。
            abort = abortCode == null ? null : abortCode.get();
            if (abort != null) {
                event(events, "RUN_LATE_RESPONSE", input.runVersion(), Map.of("code", abort), progress);
                return aborted(input, messages, references, events, steps, abort, progress);
            }
            // 模型调用期间可能撤销许可。执行动作前重新核验，核验耗时也计入总时限。
            var authorizedStep = skillStep.get();
            var authorizedContext = authorizedStep.toolContext();
            abort = abortCode == null ? null : abortCode.get();
            if (abort != null) {
                event(events, "RUN_LATE_RESPONSE", input.runVersion(), Map.of("code", abort), progress);
                return aborted(input, messages, references, events, steps, abort, progress);
            }
            if (action instanceof CallTool call) {
                int step = steps;
                int rejected = rejections;
                ToolStep completed = executeTool.apply(() -> {
                    String signature = reusableSignature(call, context.userMessageCount());
                    String toolFeedback;
                    Map<String, Object> audit;
                    if (authorizedContext.allows(call.toolName())
                            && canReuse(call, signature, toolResults, authorizedStep.activeSkill())) {
                        attemptedTools.add(call.toolName());
                        toolFeedback = "相同工具与参数的结果仍可使用，请结合现有上下文继续调查或回答";
                        audit = Map.of("toolName", call.toolName(), "success", false, "errorCode", "DUPLICATE_CALL",
                                "references", 0, "durationMs", 0, "retryable", false);
                    } else {
                        long started = System.nanoTime();
                        var outcome = gateway.executeTool(authorizedContext, call.toolName(), call.arguments());
                        attemptedTools.add(outcome.toolName());
                        if (outcome.success()) {
                            for (Reference reference : outcome.references()) mergeReference(references, reference);
                            if (!"generate_diagnosis_report".equals(call.toolName()))
                                toolResults.removeIf(result -> "generate_diagnosis_report".equals(result.toolName()));
                        }
                        toolFeedback = outcome.success() ? null : "工具调用失败：" + outcome.error().code();
                        toolResults.add(boundedToolResult(outcome, signature));
                        trimToolResults(toolResults);
                        audit = Map.of("toolName", outcome.toolName(), "success", outcome.success(), "errorCode",
                                outcome.error() == null ? "" : outcome.error().code(), "references",
                                outcome.references().size(), "durationMs", (System.nanoTime() - started) / 1_000_000,
                                "retryable", outcome.error() != null && outcome.error().retryable());
                    }
                    return new ToolStep(checkpoint(input, step, attemptedTools, toolResults,
                            toolFeedback, rejected), audit);
                });
                feedback = completed.checkpoint().feedback();
                event(events, "TOOL_RESULT", input.runVersion(), completed.audit(), progress);
                continue;
            }
            if (action instanceof AskUser ask) {
                // 追问会原样展示给用户（05-02 延期项）：夹带设备操作指令或确诊断言时拒绝该动作，
                // 按有界重试交给模型纠正，超限明确失败，绝不把越界追问落库成待答问题。
                if (AnswerGuard.unsafeFollowUp(ask.question(), ask.requestedFields())) {
                    rejections++;
                    feedback = "追问不得包含设备操作指令或已经确诊的断言：只能询问现场状态、测量值或补证信息";
                    event(events, "ACTION_REJECTED", input.runVersion(),
                            Map.of("code", "UNSAFE_QUESTION"), progress);
                    if (rejections > 2) {
                        event(events, "RUN_FAILED", input.runVersion(),
                                Map.of("code", "UNSAFE_ANSWER"), progress);
                        return new AgentRunOutcome(AgentStatus.FAILED, null, null, messages,
                                List.copyOf(references.values()), events, steps, "UNSAFE_ANSWER");
                    }
                    saveCheckpoint.accept(checkpoint(input, steps, attemptedTools, toolResults,
                            feedback, rejections));
                    continue;
                }
                // 版本号由持久层按 run 的 question_version 递增，这里只给初值
                var question = new AgentTaskInput.PendingQuestion(UUID.randomUUID(), 1, ask.question(),
                        ask.requestedFields(), Instant.now());
                messages.add(new AgentTaskInput.Message("ASSISTANT", ask.question()));
                event(events, "QUESTION_ASKED", input.runVersion(), Map.of(
                        "pendingQuestionId", question.pendingQuestionId().toString(), "questionVersion", 1), progress);
                return new AgentRunOutcome(AgentStatus.WAITING_FOR_USER, null, question, messages,
                        List.copyOf(references.values()), events, steps, "AWAITING_USER_INPUT");
            }
            if (action instanceof Answer draft) {
                var verdict = answerGuard.review(input, List.copyOf(references.values()), draft);
                if (!verdict.allowed()) {
                    // 有界修正：只给模型一次重答机会，仍不合法就按失败结束，不伪装成答案
                    rejections++;
                    feedback = "答案被拒绝：" + verdict.code() + switch (verdict.code()) {
                        case "UNSAFE_ANSWER" -> "：请移除危险操作的具体建议，只保留禁止性安全提示或一般性说明";
                        case "UNCONFIRMED_FINDING" -> "：不得声称已确诊或当前设备的确定性故障，只能给出带来源的可能原因";
                        default -> "";
                    };
                    event(events, "ANSWER_REJECTED", input.runVersion(), Map.of("code", verdict.code()), progress);
                    if (rejections > 1) {
                        event(events, "RUN_FAILED", input.runVersion(), Map.of("code", verdict.code()), progress);
                        return new AgentRunOutcome(AgentStatus.FAILED, null, null, messages,
                                List.copyOf(references.values()), events, steps, verdict.code());
                    }
                    saveCheckpoint.accept(checkpoint(input, steps, attemptedTools, toolResults,
                            feedback, rejections));
                    continue;
                }
                messages.add(new AgentTaskInput.Message("ASSISTANT", verdict.answer().summary()));
                event(events, "ANSWER", input.runVersion(), Map.of(
                        "category", verdict.answer().category().name(),
                        "riskLevel", verdict.answer().riskLevel().name(),
                        "references", verdict.answer().references().size()), progress);
                return new AgentRunOutcome(AgentStatus.COMPLETED, verdict.answer(), null, messages,
                        List.copyOf(references.values()), events, steps, "ANSWERED");
            }
            // legacy 动作或未知类型：不执行，计数后要求模型纠正
            rejections++;
            feedback = "不支持的动作类型：只能使用 CALL_TOOL、ASK_USER 或 ANSWER";
            event(events, "ACTION_REJECTED", input.runVersion(), Map.of("code", "UNSUPPORTED_ACTION"), progress);
            if (rejections > 2) {
                event(events, "RUN_FAILED", input.runVersion(), Map.of("code", "MODEL_OUTPUT_INVALID"), progress);
                return new AgentRunOutcome(AgentStatus.FAILED, null, null, messages,
                        List.copyOf(references.values()), events, steps, "MODEL_OUTPUT_INVALID");
            }
            saveCheckpoint.accept(checkpoint(input, steps, attemptedTools, toolResults, feedback,
                    rejections));
        }
    }

    private static AgentExecutionCheckpoint checkpoint(AgentTaskInput input, int steps, List<String> attemptedTools,
            List<GeneralReasoner.ToolResult> results, String feedback,
            int rejections) {
        var successfulCalls = new java.util.HashSet<String>();
        for (var result : results)
            if (result.callSignature() != null && "true".equals(result.status().get("success")))
                successfulCalls.add(result.callSignature());
        return new AgentExecutionCheckpoint(1, input.runId(), steps, attemptedTools, successfulCalls, results,
                feedback, rejections);
    }

    /** 单个状态值的最大长度；超出截断并加标记，防止一条记录撑爆上下文。 */
    static final int RESULT_VALUE_MAX_CHARS = 1000;
    /** 单条工具结果（状态键值合计）的最大长度。 */
    static final int RESULT_ENTRY_MAX_CHARS = 1600;
    /** 工具结果滑窗条数：与既有 8 条一致。 */
    static final int TOOL_RESULTS_MAX = 8;
    /** 工具结果滑窗的累计长度上限：8 条 × 约 750 字符 ≈ 6k，控制在步数预算的 Prompt 开销内。 */
    static final int TOOL_RESULTS_TOTAL_MAX_CHARS = 6000;
    static final String TRUNCATION_MARKER = "…(截断)";

    /**
     * 由 ToolOutcome 组装模型可见的工具结果白名单：标准字段（成败、错误码、dataNature、
     * 引用数量与 ID）在前，工具自述的 modelContext 键值以 putIfAbsent 补充在后；
     * 任何值都不直接取自 outcome.data() 的原始序列化。
     */
    private static final List<String> PRIORITY_FIELDS = List.of(
            "installationStatus", "verificationStatus", "verification", "conflict",
            "sourceType", "sourceId", "sourceVersion", "source", "requiredChecks");

    private static GeneralReasoner.ToolResult boundedToolResult(ToolOutcome outcome, String signature) {
        var status = new LinkedHashMap<String, String>();
        status.put("success", String.valueOf(outcome.success()));
        if (!outcome.success() && outcome.error() != null) status.put("errorCode", outcome.error().code());
        status.put("dataNature", outcome.dataNature());
        status.put("referenceCount", String.valueOf(outcome.references().size()));
        if (!outcome.references().isEmpty()) status.put("referenceIds", boundedReferenceIds(outcome.references()));
        String omissionKey = "omittedFields";
        int remaining = RESULT_ENTRY_MAX_CHARS - omissionKey.length() - TRUNCATION_MARKER.length();
        remaining -= status.entrySet().stream().mapToInt(entry -> entry.getKey().length() + entry.getValue().length()).sum();
        var fields = new java.util.TreeMap<>(outcome.modelContext());
        fields.keySet().removeAll(status.keySet());
        fields.remove(omissionKey);
        var ordered = new ArrayList<>(PRIORITY_FIELDS);
        fields.keySet().stream().filter(key -> !PRIORITY_FIELDS.contains(key)).forEach(ordered::add);
        boolean omitted = false;
        for (String key : ordered) {
            if (!fields.containsKey(key)) continue;
            int cap = Math.min(remaining - key.length(), RESULT_VALUE_MAX_CHARS);
            // 为来源字段和待核验项都留空间，长来源描述不会独占条目预算。
            if (PRIORITY_FIELDS.contains(key) && !"requiredChecks".equals(key)) cap = Math.min(cap, 96);
            String value = fields.get(key);
            if (cap < 0 || (value.length() > cap && cap < TRUNCATION_MARKER.length())) {
                omitted = true;
                continue;
            }
            value = truncateResultValue(value, cap);
            status.put(key, value);
            remaining -= key.length() + value.length();
        }
        if (omitted) status.put(omissionKey, TRUNCATION_MARKER);
        return new GeneralReasoner.ToolResult(outcome.toolName(), outcome.dataNature(), Map.copyOf(status), signature);
    }

    private static String truncateResultValue(String value, int cap) {
        if (value.length() <= cap) return value;
        int end = cap - TRUNCATION_MARKER.length();
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + TRUNCATION_MARKER;
    }

    /** 引用 ID 列表只给到长度上限，ID 本体可在 references 中按 ID 找到。 */
    private static String boundedReferenceIds(List<Reference> references) {
        StringBuilder ids = new StringBuilder();
        for (int index = 0; index < references.size(); index++) {
            Reference reference = references.get(index);
            int separator = ids.isEmpty() ? 0 : 1;
            int suffix = index == references.size() - 1 ? 0 : TRUNCATION_MARKER.length();
            if (ids.length() + separator + reference.referenceId().length() + suffix > 256)
                return ids + TRUNCATION_MARKER;
            if (!ids.isEmpty()) ids.append(',');
            ids.append(reference.referenceId());
        }
        return ids.toString();
    }

    /** 滑窗条数与累计长度双重约束：超限先淘汰最旧结果，保证 Prompt 有界。 */
    private static void trimToolResults(List<GeneralReasoner.ToolResult> toolResults) {
        while (toolResults.size() > TOOL_RESULTS_MAX) toolResults.removeFirst();
        int total = toolResults.stream().mapToInt(TaskOrchestrator::resultLength).sum();
        while (total > TOOL_RESULTS_TOTAL_MAX_CHARS && !toolResults.isEmpty())
            total -= resultLength(toolResults.removeFirst());
    }

    static int resultLength(GeneralReasoner.ToolResult result) {
        int sum = result.toolName().length() + result.dataNature().length();
        for (var entry : result.status().entrySet()) sum += entry.getKey().length() + entry.getValue().length();
        return sum;
    }

    /**
     * 合并工具返回的引用：ID 不存在则新增；同 ID 且来源身份（sourceType/sourceId/
     * sourceVersion/chunkId）一致时，允许更长的摘录替换旧值（原文扩展），
     * 身份不一致保留原值，杜绝借同 ID 篡改来源身份。
     */
    private static void mergeReference(Map<String, Reference> references, Reference candidate) {
        Reference existing = references.get(candidate.referenceId());
        if (existing == null) {
            references.put(candidate.referenceId(), candidate);
            return;
        }
        boolean sameIdentity = existing.sourceType().equals(candidate.sourceType())
                && existing.sourceId().equals(candidate.sourceId())
                && existing.sourceVersion().equals(candidate.sourceVersion())
                && existing.chunkId().equals(candidate.chunkId());
        if (sameIdentity && candidate.excerpt().length() > existing.excerpt().length())
            references.put(candidate.referenceId(), candidate);
    }

    /** 动态资产查询和报告在用户推进调查时重新读取，同一补答内仍受去重保护。 */
    private static String reusableSignature(CallTool call, long userMessages) {
        return switch (call.toolName()) {
            case "get_asset", "get_configuration", "get_maintenance_history", "get_observations",
                 "generate_diagnosis_report" ->
                new CallTool(call.toolName(), Map.of("arguments", call.arguments(), "userMessages", userMessages),
                        call.purpose()).signature();
            default -> call.signature();
        };
    }

    private static boolean canReuse(CallTool call, String signature, List<GeneralReasoner.ToolResult> results,
                                    GeneralReasoner.ActiveSkill activeSkill) {
        return switch (call.toolName()) {
            case "read_skill_reference" -> activeSkill != null && activeSkill.reference() != null
                    && call.arguments().size() == 1
                    && activeSkill.reference().name().equals(call.arguments().get("name"));
            case "activate_skill" -> activeSkill != null
                    && call.arguments().size() == 1
                    && activeSkill.metadata().skillId().equals(call.arguments().get("skillId"));
            default -> results.stream().anyMatch(result -> signature.equals(result.callSignature())
                    && "true".equals(result.status().get("success")));
        };
    }

    private static EvidenceNeed need(String id,String description,NeedSource source,String ruleId) { return new EvidenceNeed(id,description,source,NeedStatus.OPEN,ruleId); }
    private AgentRunOutcome aborted(AgentTaskInput input, List<AgentTaskInput.Message> messages,
                                    Map<String, Reference> references, List<RunEvent> events, int steps, String code,
                                    java.util.function.Consumer<RunEvent> progress) {
        boolean cancelled = "CANCELLED".equals(code);
        event(events, cancelled ? "RUN_CANCELLED" : "RUN_FAILED", input.runVersion(), Map.of("code", code), progress);
        return new AgentRunOutcome(cancelled ? AgentStatus.CANCELLED : AgentStatus.FAILED,
                null, null, messages, List.copyOf(references.values()), events, steps, code);
    }

    /** 模型尝试事件：尝试记录 + 模型版本名（白名单审计字段）。 */
    private static Map<String,Object> modelAttempt(GeneralReasoner reasoner,Reasoner.Attempt attempt) {
        return Map.of("attempt", attempt, "model", attempt.model() == null ? reasoner.name() : attempt.model());
    }
    private static void event(List<RunEvent> events,String type,long version,Map<String,Object> data) {
        event(events,type,version,data,null);
    }
    /** 记录事件并按需同步触发渐进持久回调（先内存事件、后回调，05-01 复验 §8.1）。 */
    private static void event(List<RunEvent> events,String type,long version,Map<String,Object> data,
                              java.util.function.Consumer<RunEvent> progress) {
        var recorded=new RunEvent(events.size()+1,Instant.now(),type,version,data);
        events.add(recorded);
        if(progress!=null) progress.accept(recorded);
    }
    /** 推理器失败异常：code 为稳定的错误码（如 DASHSCOPE_API_KEY_MISSING），区别于普通运行异常。 */
    public static final class ReasonerFailure extends RuntimeException {
        private final String code;
        public ReasonerFailure(String code) { super(code); this.code=code; }
        public String code() { return code; }
    }
}
