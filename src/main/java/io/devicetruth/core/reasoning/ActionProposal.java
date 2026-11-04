package io.devicetruth.core.reasoning;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.devicetruth.core.contract.Checks;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ActionProposal.CallCapability.class, name = "CALL_CAPABILITY"),
    @JsonSubTypes.Type(value = ActionProposal.ProposeHypothesis.class, name = "PROPOSE_HYPOTHESIS"),
    @JsonSubTypes.Type(value = ActionProposal.ProposeDecision.class, name = "PROPOSE_DECISION"),
    @JsonSubTypes.Type(value = ActionProposal.RequestInformation.class, name = "REQUEST_INFORMATION"),
    @JsonSubTypes.Type(value = ActionProposal.Escalate.class, name = "ESCALATE")
})
public sealed interface ActionProposal {
    record CallCapability(String capabilityId, Map<String, String> arguments, String purpose,
                          List<String> targetEvidenceNeedIds) implements ActionProposal {
        public CallCapability {
            Checks.id(capabilityId, "capabilityId");
            arguments = Map.copyOf(Checks.required(arguments, "arguments"));
            Checks.text(purpose, "purpose");
            targetEvidenceNeedIds = Checks.list(targetEvidenceNeedIds);
        }
    }
    record ProposeHypothesis(String statement, List<String> dependsOnFactIds,
                             List<String> dependsOnEvidenceIds) implements ActionProposal {
        public ProposeHypothesis {
            Checks.text(statement, "statement");
            dependsOnFactIds = Checks.list(dependsOnFactIds);
            dependsOnEvidenceIds = Checks.list(dependsOnEvidenceIds);
            if (dependsOnFactIds.isEmpty() && dependsOnEvidenceIds.isEmpty())
                throw new IllegalArgumentException("Hypothesis must reference dependencies");
        }
    }
    record ProposeDecision(String candidateRuleId, List<String> claimedFactIds,
                           List<String> claimedEvidenceIds, List<String> claimedHypothesisIds,
                           String adviceLevel, String rationale) implements ActionProposal {
        public ProposeDecision {
            Checks.id(candidateRuleId, "candidateRuleId");
            claimedFactIds = Checks.list(claimedFactIds);
            claimedEvidenceIds = Checks.list(claimedEvidenceIds);
            claimedHypothesisIds = Checks.list(claimedHypothesisIds);
            Checks.text(adviceLevel, "adviceLevel");
            Checks.text(rationale, "rationale");
        }
    }
    record RequestInformation(String reason, List<String> missingFields) implements ActionProposal {
        public RequestInformation {
            Checks.text(reason, "reason"); missingFields = Checks.list(missingFields);
            if (missingFields.isEmpty()) throw new IllegalArgumentException("missingFields is required");
        }
    }
    record Escalate(String reason) implements ActionProposal {
        public Escalate { Checks.text(reason, "reason"); }
    }
}
