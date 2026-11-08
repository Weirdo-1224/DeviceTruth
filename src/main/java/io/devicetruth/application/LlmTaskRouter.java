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
            You classify exactly ONE user message for DeviceTruth, a SIMULATED device-diagnosis demo.
            Return exactly one JSON object and no markdown.
            You only classify; never answer the user, never invent facts, evidence, assets, symptoms or tools.
            Allowed routes: QUERY, DIAGNOSE, CLARIFY, UNSUPPORTED.
            QUERY is only for ASSET_PROFILE or MAINTENANCE_HISTORY questions about an allowed asset.
            DIAGNOSE is only for an allowed asset plus the finite symptom aliases: 通信超时, 通信中断, 通信异常, 通讯超时, comm timeout, communication timeout.
            CLARIFY when the asset or symptom is missing and no currentAssetId is available.
            UNSUPPORTED for an unregistered asset, uncovered symptom, dangerous physical operation, or any request outside the allowed catalog.
            Use the catalog below. assetId must be one of its entries or null. Never map an unknown asset to a known one.
            Output shape:
            {"route":"QUERY|DIAGNOSE|CLARIFY|UNSUPPORTED","assetId":"catalog assetId or null","queryKind":"ASSET_PROFILE|MAINTENANCE_HISTORY|null","symptomText":"short text or null","reason":"short reason"}
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