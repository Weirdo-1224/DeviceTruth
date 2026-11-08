package io.devicetruth.application;

import io.devicetruth.adapter.JsonRepositories;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmTaskRouterTest {
    private RouteValidator validator;

    @BeforeEach void setUp() throws Exception {
        var repositories = JsonRepositories.fromClasspath();
        validator = new RouteValidator(SupportedScenarioCatalog.fromClasspath(), repositories, repositories, repositories);
    }

    private RouteDecision decide(LlmTaskRouter.Completion completion, String content) {
        return decide(completion, content, null, null);
    }

    private RouteDecision decide(LlmTaskRouter.Completion completion, String content,
                                 String currentAssetId, String assetIdHint) {
        return new LlmTaskRouter(completion, validator)
                .decide(new TaskRouter.RouteRequest(content, currentAssetId, assetIdHint));
    }

    private static LlmTaskRouter.Completion suggests(String route, String assetId) {
        return (system, input) -> "{\"route\":\"" + route + "\",\"assetId\":"
                + (assetId == null ? "null" : "\"" + assetId + "\"")
                + ",\"queryKind\":null,\"symptomText\":null,\"reason\":\"review\"}";
    }

    @Test void acceptsValidSuggestionOnlyAfterProgramValidation() {
        var decision = decide((system, input) ->
                "{\"route\":\"DIAGNOSE\",\"assetId\":\"CAR-B\",\"queryKind\":null,\"symptomText\":\"通信超时\",\"reason\":\"x\"}",
                "CAR-B 控制器通信超时");
        assertThat(decision.route()).isEqualTo(Route.DIAGNOSE);
        assertThat(decision.assetId()).isEqualTo("CAR-B");
    }

    @Test void retriesInvalidJsonOnceThenSucceeds() {
        var calls = new AtomicInteger();
        var decision = decide((system, input) -> calls.incrementAndGet() == 1 ? "not json"
                        : "{\"route\":\"QUERY\",\"assetId\":\"CAR-B\",\"queryKind\":\"ASSET_PROFILE\",\"symptomText\":null,\"reason\":\"x\"}",
                "CAR-B 的出厂型号");
        assertThat(calls).hasValue(2);
        assertThat(decision.route()).isEqualTo(Route.QUERY);
        assertThat(decision.queryKind()).isEqualTo(QueryKind.ASSET_PROFILE);
    }

    @Test void invalidJsonTwiceBecomesRouterUnavailable() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> decide((system, input) -> {
            calls.incrementAndGet();
            return "not json";
        }, "CAR-B 通信超时")).isInstanceOf(ApplicationExceptions.RouterUnavailableException.class);
        assertThat(calls).hasValue(2);
    }

    @Test void completionFailureBecomesRouterUnavailable() {
        assertThatThrownBy(() -> decide((system, input) -> {
            throw new IllegalStateException("down");
        }, "CAR-B 通信超时")).isInstanceOf(ApplicationExceptions.RouterUnavailableException.class);
    }

    @Test void modelCannotInventUnknownAsset() {
        var decision = decide((system, input) ->
                "{\"route\":\"QUERY\",\"assetId\":\"SN-0245\",\"queryKind\":\"ASSET_PROFILE\",\"symptomText\":null,\"reason\":\"x\"}",
                "SN-0245 的出厂型号");
        assertThat(decision.route()).isEqualTo(Route.UNSUPPORTED);
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_ASSET");
    }

    @Test void modelCannotDiagnoseWithoutFiniteSymptomAlias() {
        var decision = decide((system, input) ->
                "{\"route\":\"DIAGNOSE\",\"assetId\":\"CAR-B\",\"queryKind\":null,\"symptomText\":\"随便说\",\"reason\":\"x\"}",
                "CAR-B 有问题");
        assertThat(decision.route()).isEqualTo(Route.CLARIFY);
        assertThat(decision.reason()).isEqualTo("SYMPTOM_MISSING");
    }

    @Test void modelCannotAuthorizeAssetWithoutUserSelection() {
        var decision = decide(suggests("DIAGNOSE", "CAR-B"), "这台设备通信超时");
        assertThat(decision.route()).isEqualTo(Route.CLARIFY);
        assertThat(decision.reason()).isEqualTo("ASSET_MISSING");
        assertThat(decision.assetId()).isNull();
    }

    @Test void modelCannotOverrideConfirmedContext() {
        var decision = decide(suggests("CLARIFY", "CAR-C"), "这台设备帮我看看", "CAR-B", null);
        assertThat(decision.route()).isEqualTo(Route.CLARIFY);
        assertThat(decision.reason()).isEqualTo("ASSET_CONFLICT");
        assertThat(decision.assetId()).isNull();
    }

    @Test void dangerousRequestIsRejectedEvenWhenModelSuggestsDiagnose() {
        var decision = decide(suggests("DIAGNOSE", "CAR-B"), "帮我重启 CAR-B 通信超时");
        assertThat(decision.route()).isEqualTo(Route.UNSUPPORTED);
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_DANGEROUS");
    }

    @Test void modelCannotReplaceUserAssetWithUnknownAsset() {
        var decision = decide(suggests("DIAGNOSE", "SN-0245"), "CAR-B 通信超时");
        assertThat(decision.route()).isEqualTo(Route.UNSUPPORTED);
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_ASSET");
    }

    @Test void explicitUnknownAssetIsRejectedEvenWithConfirmedContext() {
        var decision = decide(suggests("DIAGNOSE", "CAR-B"), "CAR-Z 通信超时", "CAR-B", null);
        assertThat(decision.route()).isEqualTo(Route.UNSUPPORTED);
        assertThat(decision.reason()).isEqualTo("UNSUPPORTED_ASSET");
    }

    @Test void invalidRouteIsRetriedThenUnavailable() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> decide((system, input) -> {
            calls.incrementAndGet();
            return "{\"route\":\"EXECUTE\",\"assetId\":\"CAR-B\",\"queryKind\":null,\"symptomText\":null,\"reason\":\"x\"}";
        }, "CAR-B 通信超时")).isInstanceOf(ApplicationExceptions.RouterUnavailableException.class);
        assertThat(calls).hasValue(2);
    }

    @Test void modelSuggestionCannotBypassCompleteUnregisteredIdWithoutContext() {
        for (String content : java.util.List.of("CAR-ZZ 通信超时", "CAR-B2 通信超时", "CAR-B-UNKNOWN 通信超时")) {
            var decision = decide(suggests("DIAGNOSE", "CAR-B"), content);
            assertThat(decision.route()).as(content).isEqualTo(Route.UNSUPPORTED);
            assertThat(decision.reason()).as(content).isEqualTo("UNSUPPORTED_ASSET");
            assertThat(decision.assetId()).as(content).isNull();
        }
    }

    @Test void modelSuggestionCannotBypassCompleteUnregisteredIdWithContext() {
        for (String content : java.util.List.of("CAR-ZZ 帮我看看", "CAR-B2 帮我看看", "CAR-B-UNKNOWN 帮我看看")) {
            var decision = decide(suggests("DIAGNOSE", "CAR-B"), content, "CAR-B", null);
            assertThat(decision.route()).as(content).isEqualTo(Route.UNSUPPORTED);
            assertThat(decision.reason()).as(content).isEqualTo("UNSUPPORTED_ASSET");
            assertThat(decision.assetId()).as(content).isNull();
        }
    }

    @Test void ruleAndEvidenceIdsInOnlineTextDoNotBecomeAssets() {
        // 规则/证据 ID 不能被拆成已知资产，也不能借模型建议 CAR-B 冒充资产。
        // 没有可信资产时，模型建议 DIAGNOSE 也只能澄清，且不改写资产。
        for (String content : java.util.List.of("DEMO-D-120A 规则怎么用", "CAR-B-FIELD-1 这条证据可靠吗")) {
            var decision = decide(suggests("DIAGNOSE", "CAR-B"), content);
            assertThat(decision.route()).as(content).isEqualTo(Route.CLARIFY);
            assertThat(decision.reason()).as(content).isEqualTo("ASSET_MISSING");
            assertThat(decision.assetId()).as(content).isNull();
        }

        // 真实资产与规则 ID 同现时，真实资产被正确识别；模型建议与之一致则按症状复核。
        var withRealAsset = decide(suggests("DIAGNOSE", "CAR-C"), "CAR-C 通信超时，参考 DEMO-D-120A");
        assertThat(withRealAsset.route()).isEqualTo(Route.DIAGNOSE);
        assertThat(withRealAsset.assetId()).isEqualTo("CAR-C");

        // 模型建议与用户文本资产冲突时只澄清，不覆盖用户资产。
        var modelConflictsWithText = decide(suggests("DIAGNOSE", "CAR-B"), "CAR-C 通信超时，参考 DEMO-D-120A");
        assertThat(modelConflictsWithText.route()).isEqualTo(Route.CLARIFY);
        assertThat(modelConflictsWithText.reason()).isEqualTo("ASSET_CONFLICT");
        assertThat(modelConflictsWithText.assetId()).isNull();
    }
}