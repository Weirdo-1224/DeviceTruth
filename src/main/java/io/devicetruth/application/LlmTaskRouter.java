package io.devicetruth.application;

import io.devicetruth.adapter.Json;
import java.util.List;
import java.util.Locale;

/** 在线 LLM Router：只做一次结构化分类，独立 Prompt/DTO，不复用 ActionProposal 协议。 */
public final class LlmTaskRouter implements TaskRouter {
    @FunctionalInterface public interface Completion { String complete(String system, String input); }

    public record RouterSuggestion(String route, String assetId, String queryKind, String symptomText, String reason) {}
    private record CatalogEntry(String assetId, String domain, String symptomCode, String diagnosisAsOf) {}
    private record RouterInput(String message, String currentAssetId, List<CatalogEntry> catalog) {}

    private static final String PROTOCOL = """
            你要为 DeviceTruth（一个 SIMULATED 设备诊断演示）对且仅对一条用户消息做分类。
            只返回一个 JSON 对象，不要输出 markdown。
            你只负责分类：绝不回答用户、绝不编造事实、证据、资产、症状或工具。
            允许的路由：QUERY、DIAGNOSE、CLARIFY、UNSUPPORTED。
            QUERY 仅用于允许资产上的 ASSET_PROFILE 或 MAINTENANCE_HISTORY 类问题。
            DIAGNOSE 仅用于允许资产加上有限的症状别名：通信超时、通信中断、通信异常、通讯超时、comm timeout、communication timeout。
            CLARIFY 用于资产或症状缺失、且没有可用 currentAssetId 的情况。
            UNSUPPORTED 用于未登记资产、未覆盖症状、危险实体操作，或任何超出允许目录的请求。
            使用下面的目录。assetId 必须是目录中的某一项或 null。绝不把未知资产映射成已知资产。
            输出形状（键名与枚举值必须保持英文原样，reason 用简体中文书写）：
            {"route":"QUERY|DIAGNOSE|CLARIFY|UNSUPPORTED","assetId":"目录中的 assetId 或 null","queryKind":"ASSET_PROFILE|MAINTENANCE_HISTORY|null","symptomText":"简短文本或 null","reason":"简短中文理由"}
            """;

    private final Completion completion;
    private final RouteValidator validator;

    public LlmTaskRouter(Completion completion, RouteValidator validator) {
        this.completion = completion;
        this.validator = validator;
    }

    @Override public RouteDecision decide(RouteRequest request) {
        String input = Json.write(new RouterInput(request.content(), request.currentAssetId(),
                validator.catalog().all().stream()
                        .map(d -> new CatalogEntry(d.assetId(), d.domain(), d.symptomCode(), d.diagnosisAsOf().toString()))
                        .toList()));
        for (int attempt = 1; attempt <= 2; attempt++) {
            String response;
            try {
                response = completion.complete(attempt == 1 ? PROTOCOL
                        : PROTOCOL + "\nPrevious output was invalid JSON/schema. Correct it; return exactly one valid object.", input);
            } catch (ApplicationExceptions.RouterUnavailableException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ApplicationExceptions.RouterUnavailableException();
            }
            try {
                if (response == null || response.length() > 8192) throw new IllegalArgumentException("Invalid response size");
                RouterSuggestion suggestion = Json.MAPPER.readValue(response, RouterSuggestion.class);
                if (suggestion == null || suggestion.route() == null) throw new IllegalArgumentException("Null suggestion");
                Route suggested = Route.valueOf(suggestion.route().trim().toUpperCase(Locale.ROOT));
                return validator.validateSuggestion(suggested, suggestion.assetId(), suggestion.queryKind(),
                        request.content(), request.currentAssetId(), request.assetIdHint());
            } catch (Exception e) {
                // 非法 JSON/协议最多纠正一次；仍失败则按 Router 不可用处理，不伪造分类。
            }
        }
        throw new ApplicationExceptions.RouterUnavailableException();
    }
}