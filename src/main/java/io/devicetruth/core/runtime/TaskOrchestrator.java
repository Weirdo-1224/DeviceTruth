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

/** Each iteration proposes once, executes at most one capability, commits, then yields a fresh snapshot. */
public final class TaskOrchestrator {
    public record RunEvent(int sequence,Instant recordedAt,String type,long stateVersion,Map<String,Object> data) {
        public RunEvent { data=Map.copyOf(data); }
    }
    public record RunReport(DiagnosisResult result,DiagnosisState state,List<EvidenceRecord> evidence,List<RunEvent> events) {
        public RunReport { evidence=List.copyOf(evidence); events=List.copyOf(events); }
    }
    private record Repetition(String knowledge,int count) {}
    private final CapabilityGateway gateway;
    private final EvidenceManager evidenceManager;
    private final DecisionGuard guard;
    public TaskOrchestrator(CapabilityGateway gateway,EvidenceManager evidenceManager,DecisionGuard guard) {
        this.gateway=gateway; this.evidenceManager=evidenceManager; this.guard=guard;
    }
    public RunReport run(DiagnosisRequest request,Budget budget,Reasoner reasoner) {
        var manager=new StateManager(request,budget); var events=new ArrayList<RunEvent>();
        var repetitions=new HashMap<String,Repetition>(); var warnings=new ArrayList<String>();
        Outcome outcome=null; String reason=""; Rule approved=null; List<String> supporting=List.of();
        while(manager.snapshot().status()==RunStatus.ACTIVE) {
            var s=manager.snapshot();
            var context=new Reasoner.Context(s,gateway.catalog(),manager.evidence().values().stream().sorted(java.util.Comparator.comparing(EvidenceRecord::evidenceId)).toList());
            event(events,"REASON_INPUT",s.version(),Map.of("context",context,"reasoner",reasoner.name()));
            ActionProposal action;
            try {
                action=reasoner.propose(context);
                if(action==null) throw new IllegalArgumentException("Reasoner returned null proposal");
            } catch(RuntimeException e) {
                reason=e instanceof ReasonerFailure failure?failure.code():"REASONER_FAILURE";
                manager.commit(s.version(),null,null,s.facts(),s.hypotheses(),s.evidenceNeeds(),null,List.of(),RunStatus.FAILED,reason,manager.evidence());
                event(events,"TECHNICAL_FAILURE",manager.snapshot().version(),Map.of("code",reason));
                break;
            } finally {
                for(var attempt:reasoner.drainAttempts()) event(events,"MODEL_ATTEMPT",s.version(),Map.of("attempt",attempt));
            }
            event(events,"ACTION_PROPOSED",s.version(),Map.of("proposal",action));
            var facts=s.facts(); var hypotheses=new ArrayList<>(s.hypotheses()); var needs=new ArrayList<>(s.evidenceNeeds());
            var records=manager.evidence(); CapabilityResult observation=null;
            String selectedRule=s.selectedRuleId(); var selectedFacts=s.selectedRuleFactIds();
            RunStatus status=RunStatus.ACTIVE; String detail="accepted";
            String fingerprint=Json.write(action);
            String knowledge=Json.write(List.of(s.facts(),s.knownRules(),records.keySet().stream().sorted().toList()));
            var previous=repetitions.get(fingerprint);
            int count=previous!=null&&previous.knowledge().equals(knowledge)?previous.count()+1:1;
            repetitions.put(fingerprint,new Repetition(knowledge,count));
            if(count>budget.maxRepeatedActions()) {
                reason="REPEATED_ACTION_WITHOUT_NEW_INFORMATION"; outcome=Outcome.NEED_EVIDENCE;
                if(s.actionHistory().stream().anyMatch(h->h.observation()!=null&&!h.observation().success()&&h.observation().error().retryable())) {
                    outcome=Outcome.ESCALATE; reason="TOOL_FAILURE_NO_PROGRESS";
                }
                status=RunStatus.COMPLETED; needs.add(need("NEED-PROGRESS","Repeated action produced no new evidence",NeedSource.AGENT,null));
            } else if(action instanceof CallCapability call) {
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
                    approved=verdict.rule(); selectedRule=approved.ruleId(); selectedFacts=proposal.claimedFactIds();
                    supporting=verdict.evidenceIds(); outcome=Outcome.RESOLVED; status=RunStatus.COMPLETED; reason=verdict.reason();
                } else if(verdict.terminalOutcome()!=null) {
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
            if(status==RunStatus.ACTIVE&&s.actionHistory().size()+1>=budget.maxSteps()) {
                outcome=Outcome.NEED_EVIDENCE; status=RunStatus.COMPLETED; reason="EXECUTION_BUDGET_EXHAUSTED";
                needs.add(need("NEED-BUDGET","Further investigation requires additional execution budget",NeedSource.AGENT,null));
            }
            var next=manager.commit(s.version(),action,observation,facts,hypotheses,needs,selectedRule,selectedFacts,status,detail,records);
            event(events,"STATE_COMMITTED",next.version(),Map.of("inputVersion",s.version(),"facts",next.facts(),"hypotheses",next.hypotheses(),"needs",next.evidenceNeeds()));
        }
        var state=manager.snapshot();
        var result=new DiagnosisResult(state.taskId(),outcome,request.assetId(),request.asOf(),
                approved!=null?approved.simulatedAdvice():"Investigation stopped: "+reason,
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
    public static final class ReasonerFailure extends RuntimeException {
        private final String code;
        public ReasonerFailure(String code) { super(code); this.code=code; }
        public String code() { return code; }
    }
}
