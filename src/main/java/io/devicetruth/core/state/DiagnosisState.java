package io.devicetruth.core.state;

import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.capability.CapabilityTypes.CapabilityResult;
import io.devicetruth.core.reasoning.ActionProposal;
import java.util.List;
import static io.devicetruth.core.contract.Checks.*;

public record DiagnosisState(String taskId, long version, DiagnosisRequest task, List<Fact> facts,
                             List<Hypothesis> hypotheses, List<EvidenceNeed> evidenceNeeds,
                             String selectedRuleId, List<String> selectedRuleFactIds,
                             List<ActionHistory> actionHistory, Budget budget, RunStatus status,
                             List<Rule> knownRules, List<EvidenceMetadata> discoveredEvidence) {
    public DiagnosisState {
        id(taskId,"taskId"); required(task,"task"); required(budget,"budget"); required(status,"status");
        if (version < 0) throw new IllegalArgumentException("Negative state version");
        facts=list(facts); hypotheses=list(hypotheses); evidenceNeeds=list(evidenceNeeds);
        selectedRuleFactIds=list(selectedRuleFactIds); actionHistory=list(actionHistory);
        knownRules=list(knownRules); discoveredEvidence=list(discoveredEvidence);
    }
    public record ActionHistory(int step, long inputVersion, ActionProposal proposal, CapabilityResult observation,
                                String result) {}
    public static DiagnosisState initial(String taskId, DiagnosisRequest task, Budget budget) {
        return new DiagnosisState(taskId,0,task,null,null,null,null,null,null,budget,RunStatus.ACTIVE,null,null);
    }
}
