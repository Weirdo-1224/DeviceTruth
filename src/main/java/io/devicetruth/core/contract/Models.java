package io.devicetruth.core.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import static io.devicetruth.core.contract.Checks.*;

/** Immutable protocol values. No record holds a mutable collection supplied by a caller. */
public final class Models {
    private Models() {}
    public enum RunStatus { ACTIVE, COMPLETED, FAILED }
    public enum Outcome { RESOLVED, NEED_EVIDENCE, CONFLICT, ESCALATE }
    public enum FactStatus { SUPPORTED, CONTESTED, SUPERSEDED }
    public enum HypothesisStatus { OPEN, SUPPORTED, INVALIDATED }
    public enum NeedStatus { OPEN, SATISFIED, BLOCKED }
    public enum NeedSource { RULE, AGENT }
    public enum IntegrityStatus { VERIFIED, REJECTED }

    public record DiagnosisRequest(String assetId, String domain, String symptomCode, Instant asOf) {
        public DiagnosisRequest {
            id(assetId, "assetId"); id(domain, "domain"); id(symptomCode, "symptomCode"); required(asOf, "asOf");
        }
    }
    public record Budget(int maxSteps, int maxRepeatedActions) {
        public Budget {
            if (maxSteps < 1 || maxSteps > 200 || maxRepeatedActions < 1 || maxRepeatedActions > maxSteps)
                throw new IllegalArgumentException("Invalid execution budget");
        }
        public static Budget defaults() { return new Budget(12, 2); }
    }
    public record EvidenceRecord(String evidenceId, String assetId, String sourceType, String sourceRef,
                                 Instant recordedAt, Instant effectiveAt, Instant observedAt, Instant validTo,
                                 int authority, Map<String, String> payload, IntegrityStatus integrityStatus) {
        public EvidenceRecord {
            id(evidenceId, "evidenceId"); id(assetId, "assetId"); id(sourceType, "sourceType");
            text(sourceRef, "sourceRef"); required(recordedAt, "recordedAt"); required(effectiveAt, "effectiveAt");
            if (recordedAt.isBefore(effectiveAt) || (observedAt != null && recordedAt.isBefore(observedAt)))
                throw new IllegalArgumentException("Record cannot precede its effective/observation time");
            if (observedAt != null && observedAt.isBefore(effectiveAt)) throw new IllegalArgumentException("Observation cannot precede effective time");
            if (sourceType.equals("FIELD_CONFIRMATION") && observedAt == null) throw new IllegalArgumentException("Field confirmation requires observedAt");
            if (validTo != null && !validTo.isAfter(observedAt != null ? observedAt : effectiveAt))
                throw new IllegalArgumentException("Empty evidence validity interval");
            if (authority < 0) throw new IllegalArgumentException("Invalid authority");
            payload = Map.copyOf(required(payload, "payload"));
            id(payload.get("subject"), "payload.subject"); id(payload.get("predicate"), "payload.predicate");
            text(payload.get("value"), "payload.value"); required(integrityStatus, "integrityStatus");
        }
        public Instant validFrom() { return observedAt != null ? observedAt : effectiveAt; }
        public boolean validAt(Instant time) {
            return !recordedAt.isAfter(time) && !validFrom().isAfter(time) && (validTo == null || time.isBefore(validTo));
        }
    }
    public record EvidenceMetadata(String evidenceId, String assetId, String sourceType, String sourceRef,
                                   Instant effectiveAt, Instant recordedAt) {
        public EvidenceMetadata { id(evidenceId, "evidenceId"); id(assetId, "assetId"); }
        public static EvidenceMetadata from(EvidenceRecord record) {
            return new EvidenceMetadata(record.evidenceId(), record.assetId(), record.sourceType(), record.sourceRef(),
                    record.effectiveAt(), record.recordedAt());
        }
    }
    public record Fact(String factId, String subject, String predicate, String value, Instant validFrom,
                       Instant validTo, List<String> evidenceIds, FactStatus status) {
        public Fact {
            id(factId, "factId"); id(subject, "subject"); id(predicate, "predicate"); text(value, "value");
            required(validFrom, "validFrom"); evidenceIds = list(evidenceIds); required(status, "status");
        }
    }
    public record Hypothesis(String hypothesisId, String statement, List<String> dependsOnFactIds,
                             List<String> dependsOnEvidenceIds, HypothesisStatus status, String invalidationReason) {
        public Hypothesis {
            id(hypothesisId, "hypothesisId"); text(statement, "statement");
            dependsOnFactIds = list(dependsOnFactIds); dependsOnEvidenceIds = list(dependsOnEvidenceIds);
            required(status, "status");
        }
    }
    public record EvidenceNeed(String needId, String description, NeedSource source, NeedStatus status,
                               String relatedRuleId) {
        public EvidenceNeed { id(needId, "needId"); text(description, "description"); required(source,"source"); required(status,"status"); }
    }
    public record Condition(String subject, String predicate, String operator, List<String> values) {
        public Condition {
            id(subject,"subject"); id(predicate,"predicate"); values = list(values);
            if (!List.of("EQ", "IN").contains(operator) || values.isEmpty() || (operator.equals("EQ") && values.size() != 1))
                throw new IllegalArgumentException("Unsupported condition");
        }
        public boolean matches(Fact fact) {
            return fact.status() == FactStatus.SUPPORTED && subject.equals(fact.subject())
                    && predicate.equals(fact.predicate()) && values.contains(fact.value());
        }
    }
    public record EvidenceRequirement(String subject, String predicate, String sourceType) {
        public EvidenceRequirement { id(subject,"subject"); id(predicate,"predicate"); id(sourceType,"sourceType"); }
    }
    public record Rule(String ruleId, String domain, String symptomCode, List<Condition> conditions,
                       List<EvidenceRequirement> requiredEvidence, String allowedAdviceLevel,
                       String simulatedAdvice, boolean requiresHuman) {
        public Rule {
            id(ruleId,"ruleId"); id(domain,"domain"); id(symptomCode,"symptomCode");
            conditions = list(conditions); requiredEvidence = list(requiredEvidence);
            if (conditions.isEmpty() || requiredEvidence.isEmpty()) throw new IllegalArgumentException("Rule prerequisites required");
            if (!"SIMULATION_ONLY".equals(allowedAdviceLevel)) throw new IllegalArgumentException("V0.1 only permits SIMULATION_ONLY advice");
            text(simulatedAdvice,"simulatedAdvice");
        }
    }
    public record DiagnosisResult(String taskId, Outcome outcome, String assetId, Instant asOf,
                                   String diagnosisSummary, List<String> appliedRuleIds, List<String> supportingEvidenceIds,
                                   List<EvidenceNeed> missingEvidenceNeeds, List<String> warnings, String stopReason,
                                   int stepsUsed, long stateVersion, String reasoner, String dataNature, RunStatus runStatus) {
        public DiagnosisResult {
            appliedRuleIds = list(appliedRuleIds); supportingEvidenceIds = list(supportingEvidenceIds);
            missingEvidenceNeeds = list(missingEvidenceNeeds); warnings = list(warnings);
        }
    }
}
