package io.devicetruth.application;

import io.devicetruth.core.contract.Models.DiagnosisResult;
import io.devicetruth.core.contract.Models.EvidenceNeed;
import io.devicetruth.core.contract.Models.NeedSource;
import io.devicetruth.core.contract.Models.NeedStatus;
import io.devicetruth.core.contract.Models.Outcome;
import io.devicetruth.core.contract.Models.RunStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** spec §4.8 安全映射：只有显式白名单可以原样出现在公开视图。 */
class SafePublicFieldsTest {
    private static final String SYNTHETIC = "review-only C:\\review-fixture\\context.txt PROMPT_FRAGMENT";

    @Test void whitelistIsExactlyTheFrozenTwentyEight() {
        // 28 项是 spec §4.8 冻结值；这 28 个必须原样保留。
        var frozen = List.of("REASONER_FAILURE", "REPEATED_ACTION_WITHOUT_NEW_INFORMATION", "TOOL_FAILURE_NO_PROGRESS",
                "CURRENT_EVIDENCE_CONFLICT", "EXECUTION_BUDGET_EXHAUSTED",
                "MODEL_TRANSPORT_OR_TIMEOUT", "MODEL_OUTPUT_INVALID_AFTER_RETRY", "MODEL_CONFIGURATION_UNREADABLE",
                "OPENAI_CONFIGURATION_INVALID", "OPENAI_API_KEY_MISSING", "OPENAI_BASE_URL_MISSING", "OPENAI_MODEL_MISSING",
                "DASHSCOPE_API_KEY_MISSING",
                "RULE_AND_EVIDENCE_VERIFIED", "MISSING_REQUIRED_EVIDENCE", "RULE_REQUIRES_HUMAN_REVIEW", "UNKNOWN_RULE",
                "RULE_OUTSIDE_TASK_SCOPE", "ADVICE_LEVEL_DENIED", "INVALID_FACT_REFERENCE", "FACT_NOT_SUPPORTED",
                "FACT_OUTSIDE_TASK_TIME", "INVALID_EVIDENCE_REFERENCE", "INVALID_HYPOTHESIS_REFERENCE",
                "HYPOTHESIS_DEPENDENCIES_NOT_CITED", "FACT_EVIDENCE_NOT_CITED", "FACT_EVIDENCE_MISMATCH",
                "RULE_CONDITIONS_NOT_SATISFIED");
        assertThat(frozen).hasSize(28);
        assertThat(frozen).allMatch(SafePublicFields::isWhitelistedStopReason);
        assertThat(SafePublicFields.isWhitelistedStopReason("IGNORE_ALL_RULES")).isFalse();
        assertThat(SafePublicFields.isWhitelistedStopReason("some_free_text")).isFalse();
    }

    @Test void arbitraryModelTextIsReplacedByFixedTokens() {
        assertThat(SafePublicFields.stopReason(SYNTHETIC)).isEqualTo(SafePublicFields.STOP_REASON_WITHHELD);
        assertThat(SafePublicFields.stopReason("")).isEqualTo(SafePublicFields.STOP_REASON_WITHHELD);
        assertThat(SafePublicFields.stopReason(null)).isEqualTo(SafePublicFields.STOP_REASON_WITHHELD);
        assertThat(SafePublicFields.errorCode(SYNTHETIC)).isEqualTo(SafePublicFields.RUN_FAILED_WITHHELD_REASON);
        assertThat(SafePublicFields.errorCode("DASHSCOPE_API_KEY_MISSING")).isEqualTo("DASHSCOPE_API_KEY_MISSING");
        assertThat(SafePublicFields.stopReason("RULE_AND_EVIDENCE_VERIFIED")).isEqualTo("RULE_AND_EVIDENCE_VERIFIED");
    }

    @Test void summaryIsOnlyRealAdviceWhenARuleWasApplied() {
        var resolved = result(Outcome.RESOLVED, List.of("DEMO-D-120B"), "SIMULATED advice");
        var needEvidence = result(Outcome.NEED_EVIDENCE, List.of(), "Investigation stopped: " + SYNTHETIC);
        var escalate = result(Outcome.ESCALATE, List.of(), "Investigation stopped: " + SYNTHETIC);
        assertThat(SafePublicFields.diagnosisSummary(resolved)).isEqualTo("SIMULATED advice");
        assertThat(SafePublicFields.diagnosisSummary(needEvidence)).isEqualTo(SafePublicFields.SUMMARY_NO_APPLIED_RULE);
        assertThat(SafePublicFields.diagnosisSummary(escalate)).isEqualTo(SafePublicFields.SUMMARY_NO_APPLIED_RULE);
    }

    @Test void warningsOnlyKeepTwoAllowedShapes() {
        assertThat(SafePublicFields.warning("GATEWAY_REJECTED: ASSET_DENIED")).isEqualTo("GATEWAY_REJECTED: ASSET_DENIED");
        assertThat(SafePublicFields.warning("GATEWAY_REJECTED: " + SYNTHETIC)).isEqualTo(SafePublicFields.WARNING_WITHHELD);
        assertThat(SafePublicFields.warning("EVIDENCE_REJECTED: Evidence asset mismatch"))
                .isEqualTo("EVIDENCE_REJECTED: Evidence asset mismatch");
        assertThat(SafePublicFields.warning("EVIDENCE_REJECTED: " + SYNTHETIC)).isEqualTo(SafePublicFields.WARNING_WITHHELD);
        assertThat(SafePublicFields.warning(SYNTHETIC)).isEqualTo(SafePublicFields.WARNING_WITHHELD);
    }

    @Test void openNeedsNeverCarryDescription() {
        var needs = Arrays.asList(
                new EvidenceNeed("NEED-AGENT-1", SYNTHETIC, NeedSource.AGENT, NeedStatus.OPEN, null),
                new EvidenceNeed("NEED-X", "Required FIELD_CONFIRMATION", NeedSource.RULE, NeedStatus.BLOCKED, "DEMO-D-120B"));
        var open = SafePublicFields.openNeeds(needs);
        assertThat(open).hasSize(2);
        assertThat(open.get(0).needId()).isEqualTo("NEED-AGENT-1");
        assertThat(open.get(0).relatedRuleId()).isNull();
        assertThat(open.get(1).status()).isEqualTo("BLOCKED");
        assertThat(open.toString()).doesNotContain("PROMPT_FRAGMENT");
    }

    private static DiagnosisResult result(Outcome outcome, List<String> rules, String summary) {
        return new DiagnosisResult("task", outcome, "CAR-B", Instant.parse("2026-11-03T04:00:00Z"), summary, rules,
                List.of(), List.of(), List.of(), SYNTHETIC, 1, 1, "scripted", "SIMULATED", RunStatus.COMPLETED);
    }
}