package io.devicetruth.core.runtime;

import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.StructuredAgentReasoner;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult.*;
import io.devicetruth.core.contract.AgentTaskInput;
import io.devicetruth.core.contract.AnswerReview.*;
import io.devicetruth.core.reasoning.ActionProposal.Answer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnswerReviewLifecycleTest {
    private final Instant at = Instant.parse("2026-11-19T00:00:00Z");
    private final Reference source = new Reference("r1", "KNOWLEDGE_DOCUMENT", "manual", "2026.1", "chunk",
            "手册", null, "版本", "本手册版本已确认为2026.1", "SIMULATED");
    private final Answer draft = new Answer("KNOWLEDGE", "当前设备已经损坏", List.of(
            new Claim("本手册版本已确认为2026.1", List.of("r1"), Certainty.CONFIRMED),
            new Claim("当前设备已经损坏", List.of("r1"), Certainty.SUPPORTED)),
            List.of("应更换故障部件"), List.of(), List.of(), List.of("r1"));

    private AgentTaskInput input(int steps, int maximum) {
        return new AgentTaskInput("owner", "project", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "查手册", List.of(new AgentTaskInput.Message("USER", "查手册，无现场记录")), null,
                at, at, 0, steps, maximum);
    }
    private Review review() {
        return new Review(1, List.of(
                new Unit("summary.0", Position.SUMMARY, 0, draft.summary(), Nature.DEVICE_INFERENCE, null, null,
                        List.of(new Support("r1", "版本")), List.of("claim.1")),
                new Unit("claim.0", Position.CLAIM, 0, draft.claims().getFirst().text(), Nature.DOCUMENT_FACT, null, null,
                        List.of(new Support("r1", source.excerpt())), List.of()),
                new Unit("claim.1", Position.CLAIM, 1, draft.claims().getLast().text(), Nature.DEVICE_INFERENCE, null, null,
                        List.of(new Support("r1", "版本")), List.of()),
                new Unit("suggestion.0", Position.SUGGESTION, 0, draft.suggestions().getFirst(), Nature.DEVICE_INFERENCE, null, null,
                        List.of(new Support("r1", "版本")), List.of("claim.1"))),
                List.of("summary.0", "claim.0", "claim.1", "suggestion.0").stream()
                        .map(id -> new Decision(id, Disposition.RETAINED, "SUPPORTED", List.of("r1"))).toList(), false);
    }
    private String batch() {
        return Json.write(new Batch(review().units().stream().map(unit -> new Assessment(unit.unitId(), unit.nature(),
                unit.subjectId(), unit.observedAt(), unit.supports(), unit.dependsOn(), Disposition.RETAINED, "SUPPORTED")).toList()));
    }
    private TaskOrchestrator.AgentRunOutcome run(AgentTaskInput input, StructuredAgentReasoner reasoner,
            AgentExecutionCheckpoint checkpoint, List<AgentExecutionCheckpoint> saved, AtomicInteger reserved,
            java.util.function.Supplier<String> abort) {
        var gateway = new CapabilityGateway();
        var tools = new ToolContext("owner", "project", input.conversationId(), input.runId(), null, at, at, Set.of());
        // 本类保留v1历史检查点/局部处理回归；新版交付另由DeliveryReviewTest覆盖。
        var legacy = new io.devicetruth.core.reasoning.GeneralReasoner() {
            public String name() { return reasoner.name(); }
            public boolean supportsAnswerReview() { return true; }
            public io.devicetruth.core.reasoning.ActionProposal propose(Context context) { return reasoner.propose(context); }
            public Review reviewAnswer(AgentTaskInput task, List<Reference> refs, Answer answer,
                    java.util.function.Supplier<String> stop, java.util.function.LongSupplier remaining) {
                return reasoner.reviewAnswer(task, refs, answer, stop, remaining);
            }
            public Answer correctAnswer(Context context, Answer answer, Review review,
                    java.util.function.Supplier<String> stop, java.util.function.LongSupplier remaining) {
                return reasoner.correctAnswer(context, answer, review, stop, remaining);
            }
        };
        return new TaskOrchestrator(gateway).runAgent(input, gateway, tools, List.of(source), legacy, abort,
                () -> new TaskOrchestrator.SkillStep(tools, List.of(), null), null, List.of(), checkpoint,
                step -> { assertThat(step).isGreaterThan(reserved.get()); reserved.set(step); },
                java.util.function.Supplier::get, saved::add, () -> 100_000L, 30_000L);
    }

    @Test void failedCorrectionKeepsIndependentKnowledgeAndRemovesDependentAdvice() {
        var responses = new ArrayDeque<>(List.of(Json.write(draft), batch(), "invalid correction"));
        var reasoner = new StructuredAgentReasoner("test", (system, data) -> responses.removeFirst());
        var saved = new ArrayList<AgentExecutionCheckpoint>();
        var reserved = new AtomicInteger();
        var result = run(input(0, 12), reasoner, null, saved, reserved, () -> null);
        assertThat(result.status()).isEqualTo(TaskOrchestrator.AgentStatus.COMPLETED);
        assertThat(result.limited()).isTrue();
        assertThat(result.answer().summary()).isEqualTo(source.excerpt());
        assertThat(result.answer().claims()).hasSize(1);
        assertThat(result.answer().claims().getFirst().certainty()).isEqualTo(Certainty.SUPPORTED);
        assertThat(result.answer().suggestions()).isEmpty();
        assertThat(Json.write(result.answer())).doesNotContain("当前设备已经损坏", "应更换故障部件");
        assertThat(result.answer().references()).containsExactly(source);
        assertThat(reserved.get()).isEqualTo(3);
        assertThat(saved.getLast().answerReview().reviews()).isEqualTo(1);
        assertThat(saved.getLast().answerReview().corrections()).isEqualTo(1);
        assertThat(saved.getLast().answerReview().phase()).isEqualTo(AnswerReviewProgress.Phase.FINISHED);
    }

    @Test void noCorrectionBudgetStillReturnsLocallyValidatedContent() {
        var responses = new ArrayDeque<>(List.of(Json.write(draft), batch()));
        var reasoner = new StructuredAgentReasoner("test", (system, data) -> responses.removeFirst());
        var saved = new ArrayList<AgentExecutionCheckpoint>();
        var result = run(input(0, 2), reasoner, null, saved, new AtomicInteger(), () -> null);
        assertThat(result.answer().claims()).hasSize(1);
        assertThat(result.stepsUsed()).isEqualTo(2);
        assertThat(saved.getLast().answerReview().corrections()).isZero();
    }

    @Test void interruptedCorrectionDoesNotRepeatModelCallAfterRestart() throws Exception {
        var task = input(3, 3);
        var progress = new AnswerReviewProgress(1, 1, AnswerReviewProgress.Phase.CORRECTING, draft, draft, review());
        var checkpoint = new AgentExecutionCheckpoint(1, task.runId(), 3, List.of(), Set.of(), List.of(), null, 0, 0, true, progress);
        checkpoint = Json.MAPPER.readValue(Json.write(checkpoint), AgentExecutionCheckpoint.class);
        var calls = new AtomicInteger();
        var reasoner = new StructuredAgentReasoner("test", (system, data) -> { calls.incrementAndGet(); throw new AssertionError(); });
        var result = run(task, reasoner, checkpoint, new ArrayList<>(), new AtomicInteger(3), () -> null);
        assertThat(calls.get()).isZero();
        assertThat(result.answer().claims()).hasSize(1);
        assertThat(result.limited()).isTrue();
    }

    @Test void cancellationDuringReviewDiscardsOutputAndStartsNoCorrection() {
        var cancelled = new AtomicBoolean();
        var calls = new AtomicInteger();
        var reasoner = new StructuredAgentReasoner("test", (system, data) -> {
            if (calls.incrementAndGet() == 1) return Json.write(draft);
            cancelled.set(true); return batch();
        });
        var result = run(input(0, 12), reasoner, null, new ArrayList<>(), new AtomicInteger(),
                () -> cancelled.get() ? "CANCELLED" : null);
        assertThat(result.status()).isEqualTo(TaskOrchestrator.AgentStatus.CANCELLED);
        assertThat(result.answer()).isNull();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void successfulCorrectionIsReviewedExactlyOnceBeforeAdoption() {
        var corrected = new Answer("KNOWLEDGE", source.excerpt(), List.of(
                new Claim(source.excerpt(), List.of("r1"), Certainty.CONFIRMED)),
                List.of(), List.of(), List.of(), List.of("r1"));
        var finalBatch = new Batch(List.of("summary.0", "claim.0").stream().map(id -> new Assessment(id,
                Nature.DOCUMENT_FACT, null, null, List.of(new Support("r1", source.excerpt())),
                List.of(), Disposition.RETAINED, "SUPPORTED")).toList());
        var responses = new ArrayDeque<>(List.of(Json.write(draft), batch(), Json.write(corrected), Json.write(finalBatch)));
        var calls = new AtomicInteger();
        var reasoner = new StructuredAgentReasoner("test", (system, data) -> { calls.incrementAndGet(); return responses.removeFirst(); });
        var saved = new ArrayList<AgentExecutionCheckpoint>();
        var result = run(input(0, 12), reasoner, null, saved, new AtomicInteger(), () -> null);
        assertThat(result.status()).isEqualTo(TaskOrchestrator.AgentStatus.COMPLETED);
        assertThat(result.limited()).isFalse();
        assertThat(result.answer().summary()).isEqualTo(source.excerpt());
        assertThat(result.answer().claims().getFirst().certainty()).isEqualTo(Certainty.SUPPORTED);
        assertThat(calls.get()).isEqualTo(4);
        assertThat(result.stepsUsed()).isEqualTo(4);
        assertThat(saved.getLast().answerReview().reviews()).isEqualTo(2);
        assertThat(saved.getLast().answerReview().corrections()).isEqualTo(1);
    }

    @Test void unresolvedSecondReviewCannotStartAThirdReviewOrAnotherCorrection() {
        var responses = new ArrayDeque<>(List.of(Json.write(draft), batch(), Json.write(draft), batch()));
        var calls = new AtomicInteger();
        var reasoner = new StructuredAgentReasoner("test", (system, data) -> { calls.incrementAndGet(); return responses.removeFirst(); });
        var result = run(input(0, 12), reasoner, null, new ArrayList<>(), new AtomicInteger(), () -> null);
        assertThat(result.limited()).isTrue();
        assertThat(result.answer().claims()).hasSize(1);
        assertThat(result.answer().suggestions()).isEmpty();
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test void alreadyReviewedContentSurvivesTotalDeadlineOnRecovery() {
        var task = input(3, 3);
        var progress = new AnswerReviewProgress(1, 1, AnswerReviewProgress.Phase.CORRECTING, draft, draft, review());
        var checkpoint = new AgentExecutionCheckpoint(1, task.runId(), 3, List.of(), Set.of(), List.of(), null, 0, 0, true, progress);
        var reasoner = new StructuredAgentReasoner("test", (system, data) -> { throw new AssertionError("deadline forbids calls"); });
        var result = run(task, reasoner, checkpoint, new ArrayList<>(), new AtomicInteger(3), () -> "TOTAL_TIMEOUT");
        assertThat(result.limited()).isTrue();
        assertThat(result.answer().claims()).hasSize(1);
        assertThat(result.answer().summary()).isEqualTo(source.excerpt());
    }
}
