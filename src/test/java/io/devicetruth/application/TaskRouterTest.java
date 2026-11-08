package io.devicetruth.application;

import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.core.capability.Repositories.AssetRepository;
import io.devicetruth.core.capability.Repositories.EvidenceRepository;
import io.devicetruth.core.capability.Repositories.RuleRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TaskRouterTest {
    private RouteValidator validator;
    private TaskRouter router;

    @BeforeEach void setUp() throws Exception {
        var repositories = JsonRepositories.fromClasspath();
        validator = new RouteValidator(SupportedScenarioCatalog.fromClasspath(), repositories, repositories, repositories);
        router = new DeterministicTaskRouter(validator);
    }

    private RouteDecision decide(String content) {
        return router.decide(new TaskRouter.RouteRequest(content, null, null));
    }

    private RouteDecision decide(String content, String currentAssetId, String assetIdHint) {
        return router.decide(new TaskRouter.RouteRequest(content, currentAssetId, assetIdHint));
    }

    @Test void routesDiagnoseForFiniteCommunicationSymptom() {
        var decision = decide("CAR-B 控制器通信超时，帮我诊断");
        assertThat(decision.route()).isEqualTo(Route.DIAGNOSE);
        assertThat(decision.assetId()).isEqualTo("CAR-B");
        assertThat(decision.symptomCode()).isEqualTo("COMM_TIMEOUT");
    }

    @Test void routesProfileAndHistoryQueries() {
        var profile = decide("CAR-B 的出厂控制器型号？");
        assertThat(profile.route()).isEqualTo(Route.QUERY);
        assertThat(profile.queryKind()).isEqualTo(QueryKind.ASSET_PROFILE);
        var history = decide("CAR-B 发生过哪些更换？");
        assertThat(history.route()).isEqualTo(Route.QUERY);
        assertThat(history.queryKind()).isEqualTo(QueryKind.MAINTENANCE_HISTORY);
    }

    @Test void missingAssetOrSymptomRequestsClarification() {
        assertThat(decide("帮我看看这台设备").route()).isEqualTo(Route.CLARIFY);
        assertThat(decide("帮我看看这台设备").reason()).isEqualTo("ASSET_MISSING");
        var noSymptom = decide("CAR-B 帮我看看");
        assertThat(noSymptom.route()).isEqualTo(Route.CLARIFY);
        assertThat(noSymptom.reason()).isEqualTo("SYMPTOM_MISSING");
    }

    @Test void unknownAssetNeverReusesCarB() {
        var decision = decide("SN-0245 通信超时，帮我诊断");
        assertThat(decision.route()).isEqualTo(Route.UNSUPPORTED);
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_ASSET");
        assertThat(decision.assetId()).isNull();
    }

    @Test void dangerousOperationsAreUnsupported() {
        assertThat(decide("帮我重启 CAR-B").reason()).isEqualTo("UNSUPPORTED_DANGEROUS");
        assertThat(decide("远程控制设备写入配置").reason()).isEqualTo("UNSUPPORTED_DANGEROUS");
    }

    @Test void uncoveredSymptomsAreUnsupported() {
        var decision = decide("CAR-B 电池异常");
        assertThat(decision.route()).isEqualTo(Route.UNSUPPORTED);
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_SYMPTOM");
    }

    @Test void currentAssetContextAndExplicitHintAreUsed() {
        var byContext = decide("这台设备 通信超时", "CAR-B", null);
        assertThat(byContext.route()).isEqualTo(Route.DIAGNOSE);
        assertThat(byContext.assetId()).isEqualTo("CAR-B");
        var byHint = decide("通信超时", null, "CAR-C");
        assertThat(byHint.route()).isEqualTo(Route.DIAGNOSE);
        assertThat(byHint.assetId()).isEqualTo("CAR-C");
    }

    @Test void conflictingOrUnknownHintsAreNotSilentlyAccepted() {
        assertThat(decide("CAR-B 通信超时", null, "CAR-C").reason()).isEqualTo("ASSET_CONFLICT");
        assertThat(decide("通信超时", null, "SN-0245").reason()).isEqualTo("UNSUPPORTED_ASSET");
    }

    @Test void explicitUnknownAssetIsRejectedEvenWithConfirmedContext() {
        var decision = decide("CAR-Z 通信超时，帮我诊断", "CAR-B", null);
        assertThat(decision.route()).isEqualTo(Route.UNSUPPORTED);
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_ASSET");
        assertThat(decision.assetId()).isNull();
    }

    @Test void multipleKnownAssetsRequireClarification() {
        var decision = decide("CAR-B 和 CAR-C 通信超时，帮我诊断");
        assertThat(decision.route()).isEqualTo(Route.CLARIFY);
        assertThat(decision.reason()).isEqualTo("ASSET_AMBIGUOUS");
        assertThat(decision.assetId()).isNull();
    }

    @Test void knownAssetWithoutRuleIsUnsupported() {
        var catalog = new SupportedScenarioCatalog(List.of(new SupportedScenarioCatalog.SupportedDiagnosis(
                "CAR-X", "other-demo", "COMM_TIMEOUT", Instant.parse("2026-11-03T04:00:00Z"))));
        RuleRepository emptyRules = (domain, symptomCode) -> List.of();
        AssetRepository emptyAssets = new AssetRepository() {
            public List<io.devicetruth.core.contract.Models.EvidenceRecord> profile(String assetId) { return List.of(); }
            public List<io.devicetruth.core.contract.Models.EvidenceRecord> history(String assetId, String componentId) { return List.of(); }
        };
        EvidenceRepository emptyEvidence = new EvidenceRepository() {
            public List<io.devicetruth.core.contract.Models.EvidenceMetadata> search(String assetId, String componentId, String sourceType) { return List.of(); }
            public io.devicetruth.core.contract.Models.EvidenceRecord get(String assetId, String evidenceId) { return null; }
        };
        var localRouter = new DeterministicTaskRouter(
                new RouteValidator(catalog, emptyRules, emptyAssets, emptyEvidence));
        var decision = localRouter.decide(new TaskRouter.RouteRequest("CAR-X 通信超时", null, null));
        assertThat(decision.route()).isEqualTo(Route.UNSUPPORTED);
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_RULE");
    }

    @Test void assetMatchingIsCaseInsensitive() {
        assertThat(decide("car-b 通信超时").assetId()).isEqualTo("CAR-B");
    }

    @Test void completeUnregisteredIdsAreRejectedWithoutConfirmedContext() {
        for (String content : List.of("CAR-ZZ 帮我看看", "CAR-B2 帮我看看", "CAR-B-UNKNOWN 帮我看看",
                "CAR-ZZ 通信超时", "CAR-B-UNKNOWN 通信超时")) {
            var decision = decide(content);
            assertThat(decision.route()).as(content).isEqualTo(Route.UNSUPPORTED);
            assertThat(decision.reason()).as(content).isEqualTo("UNSUPPORTED_ASSET");
            assertThat(decision.assetId()).as(content).isNull();
        }
    }

    @Test void completeUnregisteredIdsNeverFallBackToConfirmedContext() {
        for (String content : List.of("CAR-ZZ 帮我看看", "CAR-B2 帮我看看", "CAR-B-UNKNOWN 帮我看看",
                "CAR-B-UNKNOWN 通信超时")) {
            var decision = decide(content, "CAR-B", null);
            assertThat(decision.route()).as(content).isEqualTo(Route.UNSUPPORTED);
            assertThat(decision.reason()).as(content).isEqualTo("UNSUPPORTED_ASSET");
            assertThat(decision.assetId()).as(content).isNull();
        }
    }

    @Test void knownAssetPrefixIsNotMisreadFromLongerId() {
        assertThat(validator.assetTokensInText("CAR-B-UNKNOWN 通信超时")).containsExactly("CAR-B-UNKNOWN");
        assertThat(validator.assetTokensInText("CAR-B2 帮我看看")).containsExactly("CAR-B2");
        assertThat(validator.assetTokensInText("CAR-ZZ 帮我看看")).containsExactly("CAR-ZZ");
        var decision = decide("CAR-Z 通信超时");
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_ASSET");
    }

    @Test void ruleAndEvidenceIdsAreNotTreatedAsAssets() {
        assertThat(validator.assetTokensInText("按 DEMO-D-120A 规则看看 CAR-B 通信超时"))
                .containsExactly("DEMO-D-120A", "CAR-B");
        assertThat(validator.assetTokensInText("CAR-B-FIELD-1 这条证据可靠吗"))
                .containsExactly("CAR-B-FIELD-1");

        // 规则/证据 ID 是独立完整 token，不能被拆成已知资产 CAR-D / CAR-B，也因此不会被当成未知资产而拒绝。
        var ruleIdOnly = decide("按 DEMO-D-120A 规则诊断");
        assertThat(ruleIdOnly.route()).isEqualTo(Route.CLARIFY);
        assertThat(ruleIdOnly.reason()).isEqualTo("ASSET_MISSING");
        assertThat(ruleIdOnly.assetId()).isNull();

        var evidenceIdOnly = decide("CAR-B-FIELD-1 这条证据可靠吗");
        assertThat(evidenceIdOnly.route()).isEqualTo(Route.CLARIFY);
        assertThat(evidenceIdOnly.reason()).isEqualTo("ASSET_MISSING");
        assertThat(evidenceIdOnly.assetId()).isNull();

        // 规则/证据 ID 与真实资产同现时，真实资产仍被正确识别，且不会被前缀误认。
        var withRealAsset = decide("CAR-C 通信超时，参考 DEMO-D-120A");
        assertThat(withRealAsset.route()).isEqualTo(Route.DIAGNOSE);
        assertThat(withRealAsset.assetId()).isEqualTo("CAR-C");
    }

    @Test void rejectingUnregisteredIdKeepsKnownAssetsAndAmbiguityBehaviour() {
        var known = decide("CAR-A 通信超时");
        assertThat(known.route()).isEqualTo(Route.DIAGNOSE);
        assertThat(known.assetId()).isEqualTo("CAR-A");

        var mixedKnownAndUnknown = decide("CAR-B 和 CAR-ZZ 通信超时", "CAR-C", null);
        assertThat(mixedKnownAndUnknown.reason()).isEqualTo("UNSUPPORTED_ASSET");
        assertThat(mixedKnownAndUnknown.assetId()).isNull();

        var ambiguous = decide("CAR-B 和 CAR-C 通信超时");
        assertThat(ambiguous.reason()).isEqualTo("ASSET_AMBIGUOUS");
        assertThat(ambiguous.assetId()).isNull();

        var contextReuse = decide("这台设备 通信超时", "CAR-D", null);
        assertThat(contextReuse.route()).isEqualTo(Route.DIAGNOSE);
        assertThat(contextReuse.assetId()).isEqualTo("CAR-D");
    }
}