package io.devicetruth.application;

import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.core.capability.Repositories.AssetRepository;
import io.devicetruth.core.contract.Models;
import io.devicetruth.core.contract.Models.EvidenceRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TC-02/TC-03/TC-14/TC-15：事实查询只走真实 profile/history，来源字段取自记录本身。 */
class QueryServiceTest {
    private QueryService service;

    @BeforeEach void setUp() throws Exception {
        service = new QueryService(JsonRepositories.fromClasspath());
    }

    @Test void profileReturnsFactoryFactWithRealSources() {
        var answer = service.query("CAR-B", QueryKind.ASSET_PROFILE);
        assertThat(answer.scope()).isEqualTo("ASSET_PROFILE");
        assertThat(answer.assetId()).isEqualTo("CAR-B");
        assertThat(answer.dataNature()).isEqualTo("SIMULATED");
        assertThat(answer.answer()).contains("CTRL-01", "D-120A");
        assertThat(answer.sources()).hasSize(1);
        var source = answer.sources().get(0);
        assertThat(source.evidenceId()).isEqualTo("CAR-B-FACTORY");
        // evidenceId 与 sourceRef 是两个不同字段，不能互相顶替。
        assertThat(source.sourceRef()).isEqualTo("simulated:CAR-B/FACTORY");
        assertThat(source.sourceType()).isEqualTo("FACTORY");
        assertThat(source.dataNature()).isEqualTo("SIMULATED");
    }

    @Test void profileIsNotPresentedAsCurrentConfiguration() {
        var answer = service.query("CAR-B", QueryKind.ASSET_PROFILE);
        assertThat(answer.answer()).doesNotContain("当前实际安装", "现装为");
        assertThat(answer.limitations()).anyMatch(text -> text.contains("不能回答"));
        assertThat(answer.limitations()).anyMatch(text -> text.contains("现场确认"));
    }

    @Test void historyReturnsReplacementRecordsWithoutClaimingCurrentState() {
        var answer = service.query("CAR-B", QueryKind.MAINTENANCE_HISTORY);
        assertThat(answer.scope()).isEqualTo("MAINTENANCE_HISTORY");
        assertThat(answer.sources()).extracting(QueryAnswer.Source::evidenceId).containsExactly("CAR-B-REPLACE");
        var source = answer.sources().get(0);
        assertThat(source.sourceRef()).isEqualTo("simulated:CAR-B/REPLACE");
        assertThat(source.sourceType()).isEqualTo("REPLACEMENT");
        assertThat(answer.answer()).contains("D-120B");
        assertThat(answer.answer()).contains("不等于当前现场确认");
    }

    @Test void emptyHistorySaysNoSimulatedRecordsInsteadOfInventing() {
        // CAR-A 只有出厂档案与现场确认，没有更换记录。
        var answer = service.query("CAR-A", QueryKind.MAINTENANCE_HISTORY);
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.answer()).contains("没有维修/更换类模拟记录");
        assertThat(answer.dataNature()).isEqualTo("SIMULATED");
    }

    @Test void queryDoesNotMixEvidenceFromAnotherAsset() {
        var carA = service.query("CAR-A", QueryKind.ASSET_PROFILE);
        var carB = service.query("CAR-B", QueryKind.ASSET_PROFILE);
        assertThat(carA.sources()).extracting(QueryAnswer.Source::evidenceId).containsExactly("CAR-A-FACTORY");
        assertThat(carB.sources()).extracting(QueryAnswer.Source::evidenceId).containsExactly("CAR-B-FACTORY");
        assertThat(carA.answer()).doesNotContain("CAR-B");
    }

    @Test void unregisteredAssetIsNotProbed() {
        // 查询层不做存在性试探：未登记资产由路由拦截，这里只断言仓储级错误码不被包装成假答案。
        assertThatThrownBy(() -> service.query("SN-0245", QueryKind.ASSET_PROFILE))
                .isInstanceOf(io.devicetruth.core.capability.Repositories.QueryException.class);
    }

    @Test void recordFromAnotherAssetIsRejectedAsInternalError() {
        EvidenceRecord foreign = new EvidenceRecord("CAR-X-FACTORY", "CAR-X", "FACTORY", "simulated:CAR-X/FACTORY",
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"), null, null, 10,
                Map.of("subject", "CTRL-01", "predicate", "moduleVersion", "value", "D-120A"),
                Models.IntegrityStatus.VERIFIED);
        AssetRepository mismatched = new AssetRepository() {
            @Override public List<EvidenceRecord> profile(String assetId) { throw new UnsupportedOperationException(); }
            @Override public List<EvidenceRecord> history(String assetId, String componentId) {
                return List.of(foreign);
            }
        };
        assertThatThrownBy(() -> new QueryService(mismatched).query("CAR-B", QueryKind.MAINTENANCE_HISTORY))
                .isInstanceOf(ApplicationExceptions.QueryAssetMismatchException.class);
    }
}