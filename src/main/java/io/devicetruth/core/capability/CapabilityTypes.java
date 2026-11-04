package io.devicetruth.core.capability;

import io.devicetruth.core.contract.Models.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static io.devicetruth.core.contract.Checks.*;

public final class CapabilityTypes {
    private CapabilityTypes() {}
    public record Descriptor(String id, String description, Set<String> requiredParameters,
                             Set<String> optionalParameters, String domain, boolean readOnly,
                             Map<String, Object> inputSchema, String outputSchema) {
        public Descriptor {
            requiredParameters = Set.copyOf(requiredParameters); optionalParameters = Set.copyOf(optionalParameters);
            inputSchema = Map.copyOf(inputSchema);
        }
    }
    public record Observation(List<EvidenceRecord> records, List<EvidenceMetadata> discovered,
                              List<Rule> rules, Map<String, String> attributes) {
        public Observation {
            records = list(records); discovered = list(discovered); rules = list(rules);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
        public static Observation records(List<EvidenceRecord> records) { return new Observation(records,null,null,null); }
    }
    public record ToolError(String code, String message, boolean retryable) {}
    public record CapabilityResult(boolean success, String requestId, String capabilityId,
                                   String sourceRef, Observation data, ToolError error) {}
    @FunctionalInterface public interface ReadOnlyHandler { Observation execute(Map<String, String> arguments); }
}
