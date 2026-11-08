package io.devicetruth.application;

import java.util.List;

/** 离线确定性 Router：只覆盖经验证的受限中文表达，不冒充任意中文理解。 */
public final class DeterministicTaskRouter implements TaskRouter {
    private static final List<String> PROFILE = List.of("出厂", "型号", "档案", "初始配置", "出厂配置", "原厂");
    private static final List<String> HISTORY = List.of("更换", "维修", "历史", "替换", "保养", "修过", "换过");

    private final RouteValidator validator;

    public DeterministicTaskRouter(RouteValidator validator) { this.validator = validator; }

    @Override public RouteDecision decide(RouteRequest request) {
        String normalized = SymptomResolver.normalize(request.content());
        if (validator.mentionsDangerousOperation(request.content()))
            return RouteDecision.unsupported("UNSUPPORTED_DANGEROUS");

        RouteValidator.AssetResolution resolution =
                validator.resolveAsset(request.content(), request.currentAssetId(), request.assetIdHint());
        if (resolution.unregistered()) return RouteDecision.unsupported("UNSUPPORTED_ASSET");
        if (resolution.ambiguous()) return RouteDecision.clarify(null, "ASSET_AMBIGUOUS");
        if (resolution.conflict()) return RouteDecision.clarify(null, "ASSET_CONFLICT");
        var asset = resolution.asset();

        var symptom = validator.supportedSymptom(request.content());
        if (symptom.isPresent()) {
            if (asset.isEmpty()) return RouteDecision.clarify(null, "ASSET_MISSING");
            var supported = asset.get();
            if (!validator.hasRule(supported.domain(), supported.symptomCode()))
                return RouteDecision.unsupported("UNSUPPORTED_RULE");
            return RouteDecision.diagnose(supported.assetId(), supported.symptomCode());
        }

        boolean profile = containsAny(normalized, PROFILE);
        boolean history = containsAny(normalized, HISTORY);
        if (profile || history) {
            if (asset.isEmpty()) return RouteDecision.clarify(null, "ASSET_MISSING");
            return RouteDecision.query(asset.get().assetId(), profile ? QueryKind.ASSET_PROFILE : QueryKind.MAINTENANCE_HISTORY);
        }
        if (validator.mentionsUnsupportedSymptom(request.content()))
            return RouteDecision.unsupported("UNSUPPORTED_SYMPTOM");
        if (asset.isEmpty()) return RouteDecision.clarify(null, "ASSET_MISSING");
        return RouteDecision.clarify(asset.get().assetId(), "SYMPTOM_MISSING");
    }

    private static boolean containsAny(String normalized, List<String> terms) {
        return terms.stream().map(SymptomResolver::normalize).anyMatch(normalized::contains);
    }
}