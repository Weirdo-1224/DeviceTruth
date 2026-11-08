package io.devicetruth.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.adapter.RunReportWriter;
import io.devicetruth.application.ConversationService;
import io.devicetruth.application.DiagnosisGoal;
import io.devicetruth.application.DiagnosisRunService;
import io.devicetruth.application.DiagnosisRunService.RunSnapshot;
import io.devicetruth.application.ExecutionStatus;
import io.devicetruth.application.ReasonerFactory;
import io.devicetruth.application.RunEventViews;
import io.devicetruth.application.SafePublicFields;
import io.devicetruth.application.SupportedScenarioCatalog;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.contract.Models.Budget;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.ActionProposal.Escalate;
import io.devicetruth.core.reasoning.ActionProposal.RequestInformation;
import io.devicetruth.core.reasoning.Reasoner;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0-P1-01 安全回归：合法的 ESCALATE / REQUEST_INFORMATION 携带合成路径与 PROMPT_FRAGMENT 标记，
 * 经真实 TaskOrchestrator 执行后，公开结果视图与事件视图都必须不回显该标记；
 * Core 内部报告仍保留原值（证明过滤发生在应用层公开视图，而不是篡改 Core）。
 */
class DiagnosisSafetyMappingTest {
    private static final Instant AS_OF = Instant.parse("2026-11-03T04:00:00Z");
    private static final String MARKER = "PROMPT_FRAGMENT";
    private static final String SYNTHETIC = "review-only C:\\review-fixture\\context.txt " + MARKER;

    @TempDir Path outputRoot;

    private DiagnosisRunService service;
    private ConversationService conversations;

    @AfterEach void tearDown() { if (service != null) service.shutdown(); }

    private UUID start(Supplier<Reasoner> creator) throws Exception {
        var repositories = JsonRepositories.fromClasspath();
        conversations = new ConversationService(10, 40);
        service = new DiagnosisRunService(conversations, SupportedScenarioCatalog.fromClasspath(),
                CapabilityGateway.standard(repositories, repositories, repositories),
                ReasonerFactory.of("probe", creator), Budget.defaults(), new RunReportWriter(), outputRoot,
                1, 1, 100);
        return conversations.create().sessionId();
    }

    private RunSnapshot submitAndAwait(UUID sessionId, String assetId) throws Exception {
        var goal = new DiagnosisGoal(SYNTHETIC, SYNTHETIC, assetId, "automotive-demo", "通信超时", "COMM_TIMEOUT",
                Instant.now(), AS_OF, DiagnosisGoal.SIMULATED);
        var accepted = service.submit(goal, sessionId);
        for (int i = 0; i < 400; i++) {
            var snapshot = service.require(accepted.diagnosisId(), sessionId);
            if (snapshot.finished()) return snapshot;
            Thread.sleep(25);
        }
        throw new AssertionError("diagnosis did not finish");
    }

    /** 与 ConversationController 使用同一组 §4.8 过滤器构造公开视图，避免两份映射漂移。 */
    private static JsonNode publicView(RunSnapshot snapshot) throws Exception {
        var result = snapshot.report().result();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("executionStatus", snapshot.executionStatus());
        view.put("outcome", result.outcome() == null ? null : result.outcome().name());
        view.put("stopReason", SafePublicFields.stopReason(result.stopReason()));
        view.put("diagnosisSummary", SafePublicFields.diagnosisSummary(result));
        view.put("warnings", SafePublicFields.warnings(result.warnings()));
        view.put("openNeeds", SafePublicFields.openNeeds(result.missingEvidenceNeeds()));
        view.put("errorCode", snapshot.errorCode());
        return Json.MAPPER.valueToTree(view);
    }

    @Test void escalateReasonNeverReachesPublicViewButStaysInCoreReport() throws Exception {
        UUID sessionId = start(() -> new SyntheticReasoner(false));
        var snapshot = submitAndAwait(sessionId, "CAR-B");

        assertThat(snapshot.executionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(snapshot.report().result().outcome().name()).isEqualTo("ESCALATE");
        // Core 内部报告保留原值：证明没有篡改 Core，只过滤公开视图。
        assertThat(snapshot.report().result().stopReason()).isEqualTo(SYNTHETIC);
        assertThat(snapshot.report().result().diagnosisSummary()).contains(MARKER);

        var view = publicView(snapshot).toString();
        assertThat(view).doesNotContain(MARKER, "review-fixture");
        assertThat(view).contains("STOP_REASON_WITHHELD", "SIMULATED：未采纳规则");

        var events = Json.MAPPER.writeValueAsString(RunEventViews.of(snapshot));
        assertThat(events).doesNotContain(MARKER, "review-fixture");
        assertThat(events).contains("运行结束：ESCALATE/STOP_REASON_WITHHELD");
    }

    @Test void requestInformationMissingFieldsNeverReachOpenNeeds() throws Exception {
        UUID sessionId = start(() -> new SyntheticReasoner(true));
        var snapshot = submitAndAwait(sessionId, "CAR-B");

        assertThat(snapshot.executionStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(snapshot.report().result().outcome().name()).isEqualTo("NEED_EVIDENCE");
        // Core 内部把模型自由文本写进了 stopReason 与缺口描述。
        assertThat(snapshot.report().result().stopReason()).isEqualTo(SYNTHETIC);
        assertThat(Json.MAPPER.writeValueAsString(snapshot.report().result().missingEvidenceNeeds())).contains(MARKER);

        var view = publicView(snapshot).toString();
        var events = Json.MAPPER.writeValueAsString(RunEventViews.of(snapshot));
        assertThat(view + events).doesNotContain(MARKER, "review-fixture");
        assertThat(view).contains("STOP_REASON_WITHHELD", "SIMULATED：未采纳规则");
        assertThat(view).doesNotContain("description");
    }

    @Test void whitelistedStopCodeIsReturnedVerbatim() throws Exception {
        UUID sessionId = start(() -> new FailingReasoner("DASHSCOPE_API_KEY_MISSING"));
        var snapshot = submitAndAwait(sessionId, "CAR-B");

        assertThat(snapshot.executionStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(snapshot.errorCode()).isEqualTo("DASHSCOPE_API_KEY_MISSING");
        var events = Json.MAPPER.writeValueAsString(RunEventViews.of(snapshot));
        assertThat(events).contains("技术失败：DASHSCOPE_API_KEY_MISSING");
    }

    @Test void unknownFailureCodeIsWithheld() throws Exception {
        UUID sessionId = start(() -> new FailingReasoner("IGNORE_ALL_RULES " + MARKER));
        var snapshot = submitAndAwait(sessionId, "CAR-B");

        assertThat(snapshot.executionStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(snapshot.errorCode()).isEqualTo("RUN_FAILED_WITHHELD_REASON");
        var events = Json.MAPPER.writeValueAsString(RunEventViews.of(snapshot));
        assertThat(events).contains("技术失败：STOP_REASON_WITHHELD");
        assertThat(events).doesNotContain(MARKER);
    }

    private static final class SyntheticReasoner implements Reasoner {
        private final boolean requestInformation;
        SyntheticReasoner(boolean requestInformation) { this.requestInformation = requestInformation; }
        @Override public String name() { return "synthetic"; }
        @Override public ActionProposal propose(Context context) {
            return requestInformation ? new RequestInformation(SYNTHETIC, List.of(SYNTHETIC)) : new Escalate(SYNTHETIC);
        }
    }

    private static final class FailingReasoner implements Reasoner {
        private final String code;
        FailingReasoner(String code) { this.code = code; }
        @Override public String name() { return "failing"; }
        @Override public ActionProposal propose(Context context) {
            throw new io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure(code);
        }
    }
}