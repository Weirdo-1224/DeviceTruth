package io.devicetruth.core;

import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput;
import io.devicetruth.core.contract.AnswerReview.*;
import io.devicetruth.core.decision.AnswerEvidenceResolver;
import io.devicetruth.core.reasoning.ActionProposal.Answer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnswerEvidenceResolverTest {
    @Test void layoutOnlyQuotesAreRestoredToContinuousOriginalText() {
        for (String original : List.of("确认中止方是桩侧还是车侧，\n再检查 BMS 温度。",
                "确认中止方是桩侧还是车侧，\r\n再检查 BMS\t 温度。")) {
            var ref = new Reference("r1", "KNOWLEDGE_DOCUMENT", "manual", "1", "chunk", "手册", null,
                    "章节", original, "SIMULATED");
            var proposed = unit(Nature.DOCUMENT_FACT, "r1", "确认中止方是桩侧还是车侧，再检查 BMS 温度。", null, null);
            var resolved = resolver.resolve(input(), List.of(ref), draft(proposed));
            assertThat(resolved.hardDecisions()).isEmpty();
            assertThat(resolved.units().getFirst().supports().getFirst().quote()).isEqualTo(original);
            var review = new Review(1, List.of(proposed), List.of(new Decision("summary", Disposition.RETAINED,
                    "SUPPORTED", List.of("r1"))), false);
            var verdict = new io.devicetruth.core.decision.AnswerGuard().acceptReviewed(input(), List.of(ref),
                    draft(proposed), review, java.util.Set.of());
            assertThat(verdict.allowed()).isTrue();
            assertThat(verdict.answer().review().units().getFirst().supports().getFirst().quote()).isEqualTo(original);
        }
    }

    @Test void layoutMatchingCannotChangeMeaningJoinNumbersOrSkipContent() {
        for (var pair : List.of(
                List.of("电压为500 V。", "电压为800 V。"),
                List.of("电压为500 V。", "电压为500 A。"),
                List.of("不得上电。", "得上电。并执行"),
                List.of("型号CAR-A。", "型号CAR-B。"),
                List.of("数值为1 0。", "数值为10。"),
                List.of("设备未损坏。", "设备损坏。"),
                List.of("读取日志。不得跳过检查。核对温度。", "读取日志。核对温度。"))) {
            var ref = new Reference("r1", "KNOWLEDGE_DOCUMENT", "manual", "1", "chunk", "手册", null,
                    "章节", pair.getFirst(), "SIMULATED");
            var result = resolver.resolve(input(), List.of(ref), draft(unit(Nature.DOCUMENT_FACT, "r1",
                    pair.getLast(), null, null)));
            assertThat(result.hardDecisions()).extracting(Decision::rule).containsExactly("UNSUPPORTED_QUOTE");
        }
    }
    private final AnswerEvidenceResolver resolver = new AnswerEvidenceResolver();
    private final Instant at = Instant.parse("2026-11-19T00:00:00Z");
    private final Reference source = new Reference("r1", "KNOWLEDGE_DOCUMENT", "manual", "2026.1", "chunk",
            "手册", null, "版本", "手册版本为 2026.1", "SIMULATED");

    private AgentTaskInput input() {
        return new AgentTaskInput("owner", "project", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "版本说明", List.of(new AgentTaskInput.Message("USER", "当前设备显示故障码 E1")),
                "CAR-A", at, at, 0, 0, 12);
    }

    private Unit unit(Nature nature, String evidence, String quote, String subject, Instant observedAt) {
        return new Unit("summary", Position.SUMMARY, 0, "手册版本为 2026.1", nature,
                subject, observedAt, List.of(new Support(evidence, quote)), List.of());
    }

    private Answer draft(Unit unit) {
        return new Answer("KNOWLEDGE", "手册版本为 2026.1", List.of(), List.of(), List.of(), List.of(),
                List.of("r1"), null, 1, List.of(unit));
    }

    private String rule(Unit unit) {
        return resolver.resolve(input(), List.of(source), draft(unit)).hardDecisions().getFirst().rule();
    }

    @Test void hostPreservesDocumentAndUserSourceNature() {
        var result = resolver.resolve(input(), List.of(source),
                draft(unit(Nature.DOCUMENT_FACT, "r1", "版本为 2026.1", null, null)));
        assertThat(result.hardDecisions()).isEmpty();
        assertThat(result.evidence()).extracting(AnswerEvidenceResolver.Evidence::dataNature)
                .containsExactly("SIMULATED", "USER_REPORTED_UNVERIFIED");
        assertThat(result.evidence().getLast().sourceId()).isEqualTo("message.0");
    }

    @Test void forgedSourcesQuotesAndVerificationAreRejectedIndependentlyOfClassification() {
        assertThat(rule(unit(Nature.DOCUMENT_FACT, "cross-space-id", "版本", null, null))).isEqualTo("INVALID_CITATION");
        assertThat(rule(unit(Nature.DOCUMENT_FACT, "r1", "当前控制器已经损坏", null, null))).isEqualTo("UNSUPPORTED_QUOTE");
        assertThat(rule(unit(Nature.INDEPENDENT_VERIFICATION, "r1", "版本", null, null))).isEqualTo("VERIFICATION_SOURCE_REQUIRED");
        assertThat(rule(unit(Nature.USER_REPORT, "r1", "版本", null, null))).isEqualTo("USER_SOURCE_REQUIRED");
        assertThat(rule(unit(Nature.DEVICE_INFERENCE, "r1", "版本", null, null))).isEqualTo("CURRENT_DEVICE_EVIDENCE_REQUIRED");
        var verdict = new io.devicetruth.core.decision.AnswerGuard().review(input(), List.of(source),
                draft(unit(Nature.INDEPENDENT_VERIFICATION, "r1", "版本", null, null)));
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("VERIFICATION_SOURCE_REQUIRED");
        assertThat(verdict.issues()).extracting(io.devicetruth.core.decision.AnswerGuard.Issue::unitId)
                .containsExactly("summary");
    }

    @Test void objectAndSnapshotTimeCannotBeOverridden() {
        assertThat(rule(unit(Nature.DOCUMENT_FACT, "r1", "版本", "CAR-B", null))).isEqualTo("SUBJECT_MISMATCH");
        assertThat(rule(unit(Nature.DOCUMENT_FACT, "r1", "版本", null, at.plusSeconds(1))))
                .isEqualTo("TIME_OUTSIDE_SNAPSHOT");
    }

    @Test void unreviewedVisibleTextCannotHideOutsideUnits() {
        var incomplete = new Answer("KNOWLEDGE", "手册版本为 2026.1", List.of(), List.of("夹带建议"),
                List.of(), List.of(), List.of("r1"), null, 1,
                List.of(unit(Nature.DOCUMENT_FACT, "r1", "版本", null, null)));
        assertThatThrownBy(() -> resolver.resolve(input(), List.of(source), incomplete))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unreviewed");
    }

    @Test void dependencyClosureRemovesIndirectAdviceButRetainsIndependentKnowledge() {
        var fact = unit(Nature.DOCUMENT_FACT, "r1", "版本", null, null);
        var cause = new Unit("cause", Position.CLAIM, 0, "当前故障", Nature.DEVICE_INFERENCE,
                null, null, List.of(), List.of());
        var advice = new Unit("advice", Position.SUGGESTION, 0, "依赖故障的建议", Nature.DEVICE_INFERENCE,
                null, null, List.of(), List.of("cause"));
        var dependent = new Unit("dependent", Position.SUGGESTION, 1, "间接建议", Nature.DEVICE_INFERENCE,
                null, null, List.of(), List.of("advice"));
        var decisions = AnswerEvidenceResolver.closeDependencies(List.of(fact, cause, advice, dependent), List.of(
                new Decision("summary", Disposition.RETAINED, "SUPPORTED", List.of("r1")),
                new Decision("cause", Disposition.REMOVED, "CURRENT_DEVICE_EVIDENCE_REQUIRED", List.of()),
                new Decision("advice", Disposition.RETAINED, "SUPPORTED", List.of()),
                new Decision("dependent", Disposition.RETAINED, "SUPPORTED", List.of())));
        assertThat(decisions).extracting(Decision::disposition).containsExactly(
                Disposition.RETAINED, Disposition.REMOVED, Disposition.REMOVED, Disposition.REMOVED);
        assertThat(decisions.getLast().rule()).isEqualTo("DEPENDENCY_REMOVED");
    }
}
