package io.devicetruth.core.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import static io.devicetruth.core.contract.Checks.*;

/** 不可变协议值。任何 record 都不会持有调用方传入的可变集合。 */
public final class Models {
    private Models() {}
    public enum RunStatus { ACTIVE, COMPLETED, FAILED }          // 运行状态：进行中 / 正常结束 / 技术性失败
    public enum Outcome { RESOLVED, NEED_EVIDENCE, CONFLICT, ESCALATE } // 诊断结论：已解决 / 证据不足 / 证据冲突 / 需人工
    public enum FactStatus { SUPPORTED, CONTESTED, SUPERSEDED }  // 事实状态：成立 / 同权威冲突 / 已被取代
    public enum HypothesisStatus { OPEN, SUPPORTED, INVALIDATED } // 假设状态
    public enum NeedStatus { OPEN, SATISFIED, BLOCKED }          // 证据需求状态
    public enum NeedSource { RULE, AGENT }                       // 需求来源：规则要求 / 推理器提出
    public enum IntegrityStatus { VERIFIED, REJECTED }           // 完整性校验结果

    /** 诊断任务输入。 */
    public record DiagnosisRequest(String assetId, String domain, String symptomCode, Instant asOf) {
        public DiagnosisRequest {
            id(assetId, "assetId"); id(domain, "domain"); id(symptomCode, "symptomCode"); required(asOf, "asOf");
        }
    }
    /** 执行预算：步数上限 1..200，重复动作上限不得超过步数上限。 */
    public record Budget(int maxSteps, int maxRepeatedActions) {
        public Budget {
            if (maxSteps < 1 || maxSteps > 200 || maxRepeatedActions < 1 || maxRepeatedActions > maxSteps)
                throw new IllegalArgumentException("Invalid execution budget");
        }
        public static Budget defaults() { return new Budget(12, 2); }
    }
    /** 证据记录。构造时保证时间线自洽：recordedAt 不早于 effectiveAt/observedAt，
     *  FIELD_CONFIRMATION 必须带观察时间，有效区间不能为空。 */
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
        /** 判断该证据在指定时刻是否有效（已记录、已生效且未过期）。 */
        public boolean validAt(Instant time) {
            return !recordedAt.isAfter(time) && !validFrom().isAfter(time) && (validTo == null || time.isBefore(validTo));
        }
    }
    /** 证据元数据：evidence.search 返回的轻量视图，不含 payload 细节。 */
    public record EvidenceMetadata(String evidenceId, String assetId, String sourceType, String sourceRef,
                                   Instant effectiveAt, Instant recordedAt) {
        public EvidenceMetadata { id(evidenceId, "evidenceId"); id(assetId, "assetId"); }
        public static EvidenceMetadata from(EvidenceRecord record) {
            return new EvidenceMetadata(record.evidenceId(), record.assetId(), record.sourceType(), record.sourceRef(),
                    record.effectiveAt(), record.recordedAt());
        }
    }
    /** 派生事实：由 EvidenceManager 从证据记录推导，evidenceIds 记录其出处。 */
    public record Fact(String factId, String subject, String predicate, String value, Instant validFrom,
                       Instant validTo, List<String> evidenceIds, FactStatus status) {
        public Fact {
            id(factId, "factId"); id(subject, "subject"); id(predicate, "predicate"); text(value, "value");
            required(validFrom, "validFrom"); evidenceIds = list(evidenceIds); required(status, "status");
        }
    }
    /** 假设：需引用已有事实/证据作为依赖，依赖失效时由 EvidenceManager 置为 INVALIDATED。 */
    public record Hypothesis(String hypothesisId, String statement, List<String> dependsOnFactIds,
                             List<String> dependsOnEvidenceIds, HypothesisStatus status, String invalidationReason) {
        public Hypothesis {
            id(hypothesisId, "hypothesisId"); text(statement, "statement");
            dependsOnFactIds = list(dependsOnFactIds); dependsOnEvidenceIds = list(dependsOnEvidenceIds);
            required(status, "status");
        }
    }
    /** 证据需求：规则或推理器声明"还需要什么证据才能下结论"。 */
    public record EvidenceNeed(String needId, String description, NeedSource source, NeedStatus status,
                               String relatedRuleId) {
        public EvidenceNeed { id(needId, "needId"); text(description, "description"); required(source,"source"); required(status,"status"); }
    }
    /** 规则前提条件：按 (subject, predicate) 匹配已成立的事实，仅支持 EQ/IN 两种算子。 */
    public record Condition(String subject, String predicate, String operator, List<String> values) {
        public Condition {
            id(subject,"subject"); id(predicate,"predicate"); values = list(values);
            if (!List.of("EQ", "IN").contains(operator) || values.isEmpty() || (operator.equals("EQ") && values.size() != 1))
                throw new IllegalArgumentException("Unsupported condition");
        }
        /** 匹配事实：仅 SUPPORTED 状态、字段一致且取值在 values 列表中才算满足条件。 */
        public boolean matches(Fact fact) {
            return fact.status() == FactStatus.SUPPORTED && subject.equals(fact.subject())
                    && predicate.equals(fact.predicate()) && values.contains(fact.value());
        }
    }
    /** 规则要求的证据：某 (subject, predicate) 字段必须配指定来源类型（如 FIELD_CONFIRMATION）的证据。 */
    public record EvidenceRequirement(String subject, String predicate, String sourceType) {
        public EvidenceRequirement { id(subject,"subject"); id(predicate,"predicate"); id(sourceType,"sourceType"); }
    }
    /** 诊断规则：前提条件 + 必需证据 + 建议等级。V0.1 仅允许 SIMULATION_ONLY 建议。 */
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
    /** 运行结果报告：TaskOrchestrator 终止时生成，包含结论、缺失需求与诊断摘要。 */
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
