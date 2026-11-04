package io.devicetruth.core.state;

import io.devicetruth.core.capability.CapabilityTypes.CapabilityResult;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.reasoning.ActionProposal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StateManager {
    private DiagnosisState state;
    private Map<String,EvidenceRecord> evidence=Map.of();
    public StateManager(DiagnosisRequest request,Budget budget) { state=DiagnosisState.initial(UUID.randomUUID().toString(),request,budget); }
    public synchronized DiagnosisState snapshot() { return state; }
    public synchronized Map<String,EvidenceRecord> evidence() { return evidence; }

    public synchronized DiagnosisState commit(long expectedVersion,ActionProposal proposal,CapabilityResult observation,
                                               List<Fact> facts,List<Hypothesis> hypotheses,List<EvidenceNeed> needs,
                                               String ruleId,List<String> ruleFactIds,RunStatus status,String detail,
                                               Map<String,EvidenceRecord> records) {
        if(state.version()!=expectedVersion||state.status()!=RunStatus.ACTIVE) throw new IllegalStateException("Stale or terminal state commit");
        if(!records.entrySet().containsAll(evidence.entrySet())) throw new IllegalArgumentException("Raw evidence cannot be removed or rewritten");
        var history=new ArrayList<>(state.actionHistory());
        history.add(new DiagnosisState.ActionHistory(history.size()+1,expectedVersion,proposal,observation,detail));
        var rules=new LinkedHashMap<String,Rule>(); state.knownRules().forEach(r->rules.put(r.ruleId(),r));
        var discovered=new LinkedHashMap<String,EvidenceMetadata>(); state.discoveredEvidence().forEach(e->discovered.put(e.evidenceId(),e));
        if(observation!=null&&observation.success()) {
            observation.data().rules().forEach(r->rules.put(r.ruleId(),r));
            observation.data().discovered().forEach(e->discovered.put(e.evidenceId(),e));
        }
        var next=new DiagnosisState(state.taskId(),expectedVersion+1,state.task(),facts,hypotheses,needs,ruleId,
                ruleFactIds,history,state.budget(),status,new ArrayList<>(rules.values()),new ArrayList<>(discovered.values()));
        evidence=Map.copyOf(records); state=next; return next;
    }
}
