package io.devicetruth.application;

import io.devicetruth.core.contract.Models.DiagnosisResult;
import io.devicetruth.core.contract.Models.EvidenceNeed;
import io.devicetruth.core.contract.Models.Outcome;
import java.util.List;
import java.util.Set;

/**
 * spec §4.8：Core 的 stopReason/diagnosisSummary/缺口描述可能直接包含模型自由文本，
 * 因此公开视图一律经过这里过滤。白名单是显式集合，不能用"大写形状"正则代替，
 * 否则模型自造的 IGNORE_ALL_RULES 之类会被当成合法停止码。
 */
public final class SafePublicFields {
    public static final String STOP_REASON_WITHHELD = "STOP_REASON_WITHHELD";
    public static final String RUN_FAILED_WITHHELD_REASON = "RUN_FAILED_WITHHELD_REASON";
    public static final String SUMMARY_NO_APPLIED_RULE = "SIMULATED: no applied rule; see outcome and open needs";
    public static final String WARNING_WITHHELD = "WARNING_WITHHELD";

    /** 源码逐点核对的 28 个稳定停止码（task.md §5.1 / spec §4.8）。 */
    private static final Set<String> STOP_REASON_WHITELIST = Set.of(
            "REASONER_FAILURE",
            "REPEATED_ACTION_WITHOUT_NEW_INFORMATION",
            "TOOL_FAILURE_NO_PROGRESS",
            "CURRENT_EVIDENCE_CONFLICT",
            "EXECUTION_BUDGET_EXHAUSTED",
            "MODEL_TRANSPORT_OR_TIMEOUT",
            "MODEL_OUTPUT_INVALID_AFTER_RETRY",
            "MODEL_CONFIGURATION_UNREADABLE",
            "OPENAI_CONFIGURATION_INVALID",
            "OPENAI_API_KEY_MISSING",
            "OPENAI_BASE_URL_MISSING",
            "OPENAI_MODEL_MISSING",
            "DASHSCOPE_API_KEY_MISSING",
            "RULE_AND_EVIDENCE_VERIFIED",
            "MISSING_REQUIRED_EVIDENCE",
            "RULE_REQUIRES_HUMAN_REVIEW",
            "UNKNOWN_RULE",
            "RULE_OUTSIDE_TASK_SCOPE",
            "ADVICE_LEVEL_DENIED",
            "INVALID_FACT_REFERENCE",
            "FACT_NOT_SUPPORTED",
            "FACT_OUTSIDE_TASK_TIME",
            "INVALID_EVIDENCE_REFERENCE",
            "INVALID_HYPOTHESIS_REFERENCE",
            "HYPOTHESIS_DEPENDENCIES_NOT_CITED",
            "FACT_EVIDENCE_NOT_CITED",
            "FACT_EVIDENCE_MISMATCH",
            "RULE_CONDITIONS_NOT_SATISFIED");

    /** 允许原样出现的网关拒绝码（来自 CapabilityGateway 的固定码表）。 */
    private static final Set<String> GATEWAY_ERROR_CODES = Set.of(
            "UNKNOWN_CAPABILITY", "NOT_READ_ONLY", "DOMAIN_DENIED", "INVALID_ARGUMENTS", "ASSET_DENIED",
            "SYMPTOM_DENIED", "INVALID_TOOL_OUTPUT", "TOOL_FAILURE",
            "ASSET_NOT_FOUND", "EVIDENCE_NOT_FOUND");
    private static final Set<String> EVIDENCE_REJECTIONS = Set.of(
            "Evidence asset mismatch", "Evidence integrity rejected", "Evidence ID collision");

    /** 公开视图使用的缺口：只有 ID、状态与关联规则，绝不带 description。 */
    public record OpenNeed(String needId, String status, String relatedRuleId) {}

    private SafePublicFields() {}

    public static boolean isWhitelistedStopReason(String value) {
        return value != null && STOP_REASON_WHITELIST.contains(value);
    }

    /** 运行停止原因：白名单原样返回，其余（含模型自由文本、空串、未知码）一律固定替换。 */
    public static String stopReason(String value) {
        return isWhitelistedStopReason(value) ? value : STOP_REASON_WITHHELD;
    }

    /** 执行失败的错误码：白名单原样返回，否则固定 RUN_FAILED_WITHHELD_REASON。 */
    public static String errorCode(String value) {
        return isWhitelistedStopReason(value) ? value : RUN_FAILED_WITHHELD_REASON;
    }

    /** 诊断摘要只在真正采纳规则时输出 Core 原文（该文案来自打包规则包）。 */
    public static String diagnosisSummary(DiagnosisResult result) {
        if (result != null && result.outcome() == Outcome.RESOLVED && result.appliedRuleIds() != null
                && !result.appliedRuleIds().isEmpty()) return result.diagnosisSummary();
        return SUMMARY_NO_APPLIED_RULE;
    }

    /** 警告只允许 GATEWAY_REJECTED: <白名单错误码> 与三种证据拒绝形状。 */
    public static List<String> warnings(List<String> warnings) {
        if (warnings == null) return List.of();
        return warnings.stream().map(SafePublicFields::warning).toList();
    }

    public static String warning(String value) {
        if (value != null && value.startsWith("GATEWAY_REJECTED: ")) {
            String code = value.substring("GATEWAY_REJECTED: ".length());
            return GATEWAY_ERROR_CODES.contains(code) ? value : WARNING_WITHHELD;
        }
        if (value != null && value.startsWith("EVIDENCE_REJECTED: ")) {
            String reason = value.substring("EVIDENCE_REJECTED: ".length());
            return EVIDENCE_REJECTIONS.contains(reason) ? value : WARNING_WITHHELD;
        }
        return WARNING_WITHHELD;
    }

    /** 未满足的证据需求：只保留 ID/状态/规则 ID，description 一律不对外。 */
    public static List<OpenNeed> openNeeds(List<EvidenceNeed> needs) {
        if (needs == null) return List.of();
        return needs.stream()
                .map(need -> new OpenNeed(need.needId(), need.status().name(), need.relatedRuleId()))
                .toList();
    }
}