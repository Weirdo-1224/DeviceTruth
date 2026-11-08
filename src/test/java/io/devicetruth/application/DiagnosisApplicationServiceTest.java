package io.devicetruth.application;

import io.devicetruth.adapter.JsonRepositories;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** TC-13：DiagnosisGoal 保留原始目标，业务时间固定取 Catalog 的模拟 asOf，不与请求时间混淆。 */
class DiagnosisApplicationServiceTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-08T09:30:00Z");
    private DiagnosisApplicationService service;

    @BeforeEach void setUp() throws Exception {
        var repositories = JsonRepositories.fromClasspath();
        service = new DiagnosisApplicationService(SupportedScenarioCatalog.fromClasspath(),
                new RouteValidator(SupportedScenarioCatalog.fromClasspath(), repositories, repositories, repositories));
    }

    @Test void mapsGoalToLegacyRequestWithCatalogBusinessTime() {
        String message = "CAR-B 控制器通信超时，帮我诊断";
        var mapping = service.map("CAR-B", message, REQUESTED_AT);
        assertThat(mapping.reason()).isEqualTo("DIAGNOSE_READY");
        var goal = mapping.goal().orElseThrow();
        assertThat(goal.originalUserMessage()).isEqualTo(message);
        assertThat(goal.normalizedGoal()).isEqualTo(message.trim());
        assertThat(goal.assetId()).isEqualTo("CAR-B");
        assertThat(goal.domain()).isEqualTo("automotive-demo");
        assertThat(goal.symptomCode()).isEqualTo("COMM_TIMEOUT");
        assertThat(goal.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(goal.diagnosisAsOf()).isEqualTo(Instant.parse("2026-11-03T04:00:00Z"));
        assertThat(goal.dataNature()).isEqualTo("SIMULATED");

        var request = DiagnosisApplicationService.toRequest(goal);
        assertThat(request.assetId()).isEqualTo("CAR-B");
        assertThat(request.domain()).isEqualTo("automotive-demo");
        assertThat(request.symptomCode()).isEqualTo("COMM_TIMEOUT");
        // 旧 DiagnosisRequest 只有四个字段：业务时间必须是固定模拟时间，而不是请求时间。
        assertThat(request.asOf()).isEqualTo(Instant.parse("2026-11-03T04:00:00Z"));
        assertThat(request.asOf()).isNotEqualTo(REQUESTED_AT);
    }

    @Test void finiteAliasIsRequiredAndArbitrarySymptomIsNotMapped() {
        assertThat(service.map("CAR-B", "CAR-B 通信中断，看看", REQUESTED_AT).goal()).isPresent();
        var unknown = service.map("CAR-B", "CAR-B 有点奇怪，帮我看看", REQUESTED_AT);
        assertThat(unknown.goal()).isEmpty();
        assertThat(unknown.reason()).isEqualTo("SYMPTOM_MISSING");
    }

    @Test void unregisteredAssetIsRejectedByCatalog() {
        var mapping = service.map("SN-0245", "SN-0245 通信超时", REQUESTED_AT);
        assertThat(mapping.goal()).isEmpty();
        assertThat(mapping.reason()).isEqualTo("UNSUPPORTED_ASSET");
    }
}