package io.devicetruth.core.runtime;

import io.devicetruth.adapter.*;
import io.devicetruth.core.capability.*;
import io.devicetruth.core.contract.*;
import io.devicetruth.core.contract.AgentResult.*;
import io.devicetruth.core.contract.AnswerReview.*;
import io.devicetruth.core.reasoning.ActionProposal.Answer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DeliveryReviewTest {
    private final Instant at = Instant.parse("2026-11-20T00:00:00Z");
    private final AgentTaskInput task = new AgentTaskInput("owner", "project", UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "解释并建议下一步", List.of(new AgentTaskInput.Message("USER", "无现场记录")), null, at, at, 0, 0, 8);
    private final Answer answer = new Answer("KNOWLEDGE", "资料不足，建议补充日志时区。", List.of(
            new Claim("这是一条一般解释", List.of(), Certainty.SUPPORTED)), List.of("补充日志时区与时间戳来源"),
            List.of("当前没有现场记录"), List.of("未作现场核验"), List.of());
    private final Review issue = new Review(2, List.of(), List.of(), true, List.of(
            new Issue("summary", "UNSUPPORTED_CONCLUSION", "移除无依据当前故障结论", List.of())));

    private TaskOrchestrator.AgentRunOutcome run(StructuredAgentReasoner reasoner, AgentExecutionCheckpoint checkpoint,
            List<AgentExecutionCheckpoint> saved, java.util.function.Supplier<String> abort) {
        var gateway = new CapabilityGateway();
        var tools = new ToolContext("owner", "project", task.conversationId(), task.runId(), null, at, at, Set.of());
        var resumed = checkpoint == null ? task : new AgentTaskInput(task.ownerId(), task.projectId(), task.conversationId(),
                task.runId(), task.goalMessageId(), task.goalText(), task.messages(), task.selectedAssetId(), task.requestedAt(),
                task.asOf(), task.runVersion(), 3, task.maxSteps());
        return new TaskOrchestrator(gateway).runAgent(resumed, gateway, tools, List.of(), reasoner, abort,
                () -> new TaskOrchestrator.SkillStep(tools, List.of(), null), null, List.of(), checkpoint,
                step -> {}, java.util.function.Supplier::get, saved::add, () -> 100_000L, 30_000L);
    }

    @Test void usefulExplanationAndAdviceDoNotRequirePerSentenceCitations() {
        var responses = new ArrayDeque<>(List.of(Json.write(answer), "{\"issues\":[]}\n"));
        var calls = new AtomicInteger();
        var reasoner = new StructuredAgentReasoner("test", (system, payload) -> {
            calls.incrementAndGet(); return responses.removeFirst();
        });
        var outcome = run(reasoner, null, new ArrayList<>(), () -> null);
        assertThat(outcome.answer().suggestions()).containsExactlyElementsOf(answer.suggestions());
        assertThat(outcome.answer().review().version()).isEqualTo(2);
        assertThat(outcome.answer().review().units()).isEmpty();
        assertThat(outcome.limited()).isFalse();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void oneCorrectionHasNoSecondSemanticReviewAndDoesNotAutomaticallyLimitAnswer() {
        var responses = new ArrayDeque<>(List.of(Json.write(answer), Json.write(new DeliveryBatch(issue.issues())), Json.write(answer)));
        var calls = new AtomicInteger();
        var saved = new ArrayList<AgentExecutionCheckpoint>();
        var reasoner = new StructuredAgentReasoner("test", (system, payload) -> {
            calls.incrementAndGet(); return responses.removeFirst();
        });
        var outcome = run(reasoner, null, saved, () -> null);
        assertThat(outcome.limited()).isFalse();
        assertThat(calls.get()).isEqualTo(3);
        assertThat(saved.getLast().answerReview().reviews()).isEqualTo(1);
        assertThat(saved.getLast().answerReview().corrections()).isEqualTo(1);
        assertThat(Json.write(outcome.answer())).doesNotContain("移除无依据当前故障结论");
    }

    @Test void unknownLocationsAndFabricatedReviewReferencesRejectTheProtocol() {
        var missing = new StructuredAgentReasoner("test", (s,p) -> "{}");
        assertThatThrownBy(() -> missing.reviewDelivery(task, List.of(), answer, () -> null, () -> 1000))
                .hasMessage("INVALID_SEMANTIC_REVIEW");
        for (var invalid : List.of(new Issue("claims.99", "MISLEADING_SOURCE", "错误", List.of()),
                new Issue("summary", "MISLEADING_SOURCE", "错误", List.of("other-space")))) {
            var reasoner = new StructuredAgentReasoner("test", (s,p) -> Json.write(new DeliveryBatch(List.of(invalid))));
            assertThatThrownBy(() -> reasoner.reviewDelivery(task, List.of(), answer, () -> null, () -> 1000))
                    .hasMessage("INVALID_SEMANTIC_REVIEW");
        }
    }

    @Test void interruptedReviewOrCorrectionNeverRepeatsAnUncertainModelCall() throws Exception {
        for (var progress : List.of(
                new AnswerReviewProgress(1, 0, AnswerReviewProgress.Phase.REVIEWING, answer, null, null, 2),
                new AnswerReviewProgress(1, 1, AnswerReviewProgress.Phase.CORRECTING, answer, answer, issue, 2))) {
            var checkpoint = new AgentExecutionCheckpoint(1, task.runId(), 3, List.of(), Set.of(), List.of(), null, 0, 0, true, progress);
            checkpoint = Json.MAPPER.readValue(Json.write(checkpoint), AgentExecutionCheckpoint.class);
            var calls = new AtomicInteger();
            var outcome = run(new StructuredAgentReasoner("test", (s,p) -> { calls.incrementAndGet(); return "{}"; }),
                    checkpoint, new ArrayList<>(), () -> null);
            assertThat(calls.get()).isZero();
            assertThat(outcome.answer()).isNull();
        }
    }

    @Test void cancellationDuringReviewDiscardsTheResult() {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var calls = new AtomicInteger();
        var reasoner = new StructuredAgentReasoner("test", (s,p) -> {
            if (calls.incrementAndGet() == 1) return Json.write(answer);
            cancelled.set(true); return "{\"issues\":[]}";
        });
        var outcome = run(reasoner, null, new ArrayList<>(), () -> cancelled.get() ? "CANCELLED" : null);
        assertThat(outcome.status()).isEqualTo(TaskOrchestrator.AgentStatus.CANCELLED);
        assertThat(outcome.answer()).isNull();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void reviewFailureAndCorrectionFailureCannotDeliverUnreviewedDraft() {
        for (var responses : List.of(new ArrayDeque<>(List.of(Json.write(answer), "not json")),
                new ArrayDeque<>(List.of(Json.write(answer), Json.write(new DeliveryBatch(issue.issues())), "bad correction")))) {
            var outcome = run(new StructuredAgentReasoner("test", (s,p) -> responses.removeFirst()),
                    null, new ArrayList<>(), () -> null);
            assertThat(outcome.answer()).isNull();
            assertThat(outcome.status()).isEqualTo(TaskOrchestrator.AgentStatus.FAILED);
        }
    }
}
