package io.devicetruth.application;

import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.adapter.RunReportWriter;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.contract.Models.Budget;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.ActionProposal.Escalate;
import io.devicetruth.core.reasoning.ActionProposal.RequestInformation;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.runtime.TaskOrchestrator.RunEvent;
import io.devicetruth.core.runtime.TaskOrchestrator.RunReport;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 检查点 A：有界执行器、每任务 Reasoner、状态转换、Core FAILED 映射与无幽灵任务。 */
class DiagnosisRunServiceTest {
    private static final Instant AS_OF = Instant.parse("2026-11-03T04:00:00Z");
    private static final String SYNTHETIC = "review-only C:\\review-fixture\\context.txt PROMPT_FRAGMENT";

    @TempDir Path outputRoot;

    private JsonRepositories repositories;
    private SupportedScenarioCatalog catalog;
    private ConversationService conversations;
    private DiagnosisRunService service;

    private void start(String reasonerName, java.util.function.Supplier<Reasoner> creator, int concurrent, int queue) {
        repositories = repositories();
        catalog = catalog();
        conversations = new ConversationService(10, 40);
        service = new DiagnosisRunService(conversations, catalog,
                CapabilityGateway.standard(repositories, repositories, repositories),
                ReasonerFactory.of(reasonerName, creator), Budget.defaults(), new RunReportWriter(), outputRoot,
                concurrent, queue, 100);
    }

    private void startScripted() {
        start("scripted", io.devicetruth.core.reasoning.ScriptedReasoner::new, 2, 10);
    }

    @AfterEach void tearDown() { if (service != null) service.shutdown(); }

    private static DiagnosisGoal goal(String assetId, Instant asOf) {
        return new DiagnosisGoal("CAR-B 通信超时", "CAR-B 通信超时", assetId, "automotive-demo", "通信超时",
                "COMM_TIMEOUT", Instant.now(), asOf, DiagnosisGoal.SIMULATED);
    }

    private static JsonRepositories repositories() {
        try { return JsonRepositories.fromClasspath(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static SupportedScenarioCatalog catalog() {
        try { return SupportedScenarioCatalog.fromClasspath(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private DiagnosisRunService.RunSnapshot await(UUID sessionId, UUID diagnosisId) throws Exception {
        for (int i = 0; i < 400; i++) {
            var snapshot = service.require(diagnosisId, sessionId);
            if (snapshot.finished()) return snapshot;
            Thread.sleep(25);
        }
        throw new AssertionError("diagnosis did not finish in time");
    }

    private DiagnosisRunService.RunSnapshot awaitStatus(UUID sessionId, UUID diagnosisId, ExecutionStatus expected)
            throws Exception {
        for (int i = 0; i < 400; i++) {
            var snapshot = service.require(diagnosisId, sessionId);
            if (snapshot.executionStatus() == expected) return snapshot;
            if (snapshot.finished() && expected != ExecutionStatus.COMPLETED) break;
            Thread.sleep(10);
        }
        throw new AssertionError("diagnosis did not reach " + expected);
    }

    @Test void scriptedRunReachesCoreAndExposesRealResult() throws Exception {
        startScripted();
        UUID sessionId = conversations.create().sessionId();
        var accepted = service.submit(goal("CAR-B", AS_OF), sessionId);
        assertThat(accepted.executionStatus()).isEqualTo(ExecutionStatus.QUEUED);

        var snapshot = await(sessionId, accepted.diagnosisId());
        assertThat(snapshot.executionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(snapshot.startedAt()).isNotNull();
        assertThat(snapshot.completedAt()).isNotNull();
        assertThat(snapshot.errorCode()).isNull();
        // 真实 Core 执行：CAR-B 有现场确认，应采纳 D-120B 规则。
        var result = snapshot.report().result();
        assertThat(result.outcome()).isEqualTo(io.devicetruth.core.contract.Models.Outcome.RESOLVED);
        assertThat(result.appliedRuleIds()).containsExactly("DEMO-D-120B");
        assertThat(result.asOf()).isEqualTo(AS_OF);
        assertThat(snapshot.report().state().taskId()).isEqualTo(snapshot.coreTaskId());
        assertThat(snapshot.reportPath()).isNotNull();
        assertThat(snapshot.reportPath()).isNotEqualTo(Path.of(snapshot.coreTaskId()));
        assertThat(snapshot.reportPath()).exists();
        assertThat(snapshot.eventsAvailable()).isTrue();
    }

    @Test void coreFailureIsMappedToFailedNotCompleted() throws Exception {
        start("scripted", () -> new FailingReasoner("MODEL_OUTPUT_INVALID_AFTER_RETRY"), 2, 10);
        UUID sessionId = conversations.create().sessionId();
        var accepted = service.submit(goal("CAR-B", AS_OF), sessionId);
        var snapshot = await(sessionId, accepted.diagnosisId());

        assertThat(snapshot.executionStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(snapshot.report().result().runStatus()).isEqualTo(io.devicetruth.core.contract.Models.RunStatus.FAILED);
        assertThat(snapshot.report().result().outcome()).isNull();
        assertThat(snapshot.errorCode()).isEqualTo("MODEL_OUTPUT_INVALID_AFTER_RETRY");
    }

    @Test void freeTextFailureReasonIsWithheld() throws Exception {
        start("scripted", () -> new FailingReasoner(SYNTHETIC), 2, 10);
        UUID sessionId = conversations.create().sessionId();
        var accepted = service.submit(goal("CAR-B", AS_OF), sessionId);
        var snapshot = await(sessionId, accepted.diagnosisId());

        assertThat(snapshot.executionStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(snapshot.errorCode()).isEqualTo(SafePublicFields.RUN_FAILED_WITHHELD_REASON);
        assertThat(snapshot.errorCode()).doesNotContain("PROMPT_FRAGMENT");
    }

    @Test void eachRunGetsItsOwnReasonerInstance() throws Exception {
        var created = new AtomicInteger();
        start("scripted", () -> { created.incrementAndGet(); return new io.devicetruth.core.reasoning.ScriptedReasoner(); }, 2, 10);
        UUID sessionId = conversations.create().sessionId();
        var first = service.submit(goal("CAR-B", AS_OF), sessionId);
        await(sessionId, first.diagnosisId());
        var second = service.submit(goal("CAR-A", AS_OF), sessionId);
        await(sessionId, second.diagnosisId());
        assertThat(created).hasValue(2);
    }

    @Test void rejectedTaskLeavesNoGhostQueuedRun() throws Exception {
        var gate = new java.util.concurrent.CountDownLatch(1);
        start("scripted", () -> new BlockingReasoner(gate), 1, 0);
        UUID sessionId = conversations.create().sessionId();
        var accepted = service.submit(goal("CAR-B", AS_OF), sessionId);
        assertThat(accepted.diagnosisId()).isNotNull();
        // 等第一个任务真正占住唯一线程，再提交第二个：单线程 + 零队列必被拒绝。
        awaitStatus(sessionId, accepted.diagnosisId(), ExecutionStatus.RUNNING);
        assertThatThrownBy(() -> service.submit(goal("CAR-A", AS_OF), sessionId))
                .isInstanceOf(ApplicationExceptions.RunCapacityExceededException.class);
        // 注册表里不能多出永远 QUEUED 的项。
        assertThat(service.storedRuns()).isEqualTo(1);
        gate.countDown();
        await(sessionId, accepted.diagnosisId());
    }

    @Test void ownershipMismatchIsIndistinguishableFromMissing() throws Exception {
        startScripted();
        UUID owner = conversations.create().sessionId();
        UUID other = conversations.create().sessionId();
        var accepted = service.submit(goal("CAR-B", AS_OF), owner);
        await(owner, accepted.diagnosisId());

        assertThatThrownBy(() -> service.require(accepted.diagnosisId(), other))
                .isInstanceOf(ApplicationExceptions.SessionNotFoundException.class);
        assertThatThrownBy(() -> service.require(UUID.randomUUID(), owner))
                .isInstanceOf(ApplicationExceptions.SessionNotFoundException.class);
        assertThatThrownBy(() -> service.require(accepted.diagnosisId(), UUID.randomUUID()))
                .isInstanceOf(ApplicationExceptions.SessionNotFoundException.class);
    }

    @Test void eventsAreNotAvailableBeforeCompletion() throws Exception {
        var gate = new java.util.concurrent.CountDownLatch(1);
        start("scripted", () -> new BlockingReasoner(gate), 1, 1);
        UUID sessionId = conversations.create().sessionId();
        var accepted = service.submit(goal("CAR-B", AS_OF), sessionId);
        var running = awaitStatus(sessionId, accepted.diagnosisId(), ExecutionStatus.RUNNING);
        assertThat(running.executionStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(running.eventsAvailable()).isFalse();
        assertThat(RunEventViews.of(running)).isEmpty();
        gate.countDown();
        var done = await(sessionId, accepted.diagnosisId());
        assertThat(done.eventsAvailable()).isTrue();
    }

    /** 失败推理器：第一次提议就抛出指定错误码。 */
    private static final class FailingReasoner implements Reasoner {
        private final String code;
        FailingReasoner(String code) { this.code = code; }
        @Override public String name() { return "failing"; }
        @Override public ActionProposal propose(Context context) {
            throw new io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure(code);
        }
    }

    /** 阻塞推理器：占住唯一工作线程，直到本测试自己的闩锁放行。 */
    private static final class BlockingReasoner implements Reasoner {
        private final java.util.concurrent.CountDownLatch gate;
        BlockingReasoner(java.util.concurrent.CountDownLatch gate) { this.gate = gate; }
        @Override public String name() { return "blocking"; }
        @Override public ActionProposal propose(Context context) {
            try { gate.await(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new Escalate("blocking reasoner released");
        }
    }

    /** 供安全负例复用：真实 Core 产生含合成标记的 stopReason 与缺口描述。 */
    static final class SyntheticReasoner implements Reasoner {
        private final boolean requestInformation;
        SyntheticReasoner(boolean requestInformation) { this.requestInformation = requestInformation; }
        @Override public String name() { return "synthetic"; }
        @Override public ActionProposal propose(Context context) {
            return requestInformation
                    ? new RequestInformation(SYNTHETIC, List.of(SYNTHETIC))
                    : new Escalate(SYNTHETIC);
        }
    }

    static List<RunEvent> syntheticEvents(RunReport report) { return report.events(); }
}