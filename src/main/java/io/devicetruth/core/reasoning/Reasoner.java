package io.devicetruth.core.reasoning;

import io.devicetruth.core.state.DiagnosisState;
import io.devicetruth.core.contract.Models.EvidenceRecord;
import io.devicetruth.core.capability.CapabilityTypes.Descriptor;
import java.util.List;

public interface Reasoner {
    ActionProposal propose(Context context);
    String name();
    default List<Attempt> drainAttempts() { return List.of(); }
    record Attempt(int number,String outcome) {}
    record Context(DiagnosisState state, List<Descriptor> capabilities, List<EvidenceRecord> evidence) {
        public Context { capabilities=List.copyOf(capabilities); evidence=List.copyOf(evidence); }
    }
}
