package io.devicetruth.application;

import io.devicetruth.core.capability.Repositories.AssetRepository;
import io.devicetruth.core.capability.Repositories.EvidenceRepository;
import io.devicetruth.core.capability.Repositories.RuleRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;


/** 路由的程序二次校验：资产必须由用户明确指定或来自可信上下文，症状必须命中白名单，诊断必须有真实规则。 */
public final class RouteValidator {
    /**
     * 资产编号的完整 token：字母前缀 + 一串字母/数字段（段与段之间用单个连字符连接）。
     * 显式编号提取与 Catalog 精确匹配共用这一条边界，避免一边把编号截短、另一边漏检。
     * 规则/证据 ID（DEMO-D-120A、CAR-B-FIELD-1）也是完整 token，故不会被拆成已知资产前缀；
     * 其中已登记的 ID 由 {@link #isKnownNonAssetId} 豁免，执行“未登记资产”判定时排除。
     */
    private static final Pattern ASSET_TOKEN =
            Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z]{2,}(?:-[A-Za-z0-9]+)+(?![A-Za-z0-9_-])");
    private static final List<String> DANGEROUS = List.of(
            "控制设备", "远程控制", "写入", "刷写", "重启设备", "关闭设备", "启动设备", "执行命令",
            "修改配置", "解锁", "禁用设备", "重启", "关机");
    private static final List<String> UNSUPPORTED_SYMPTOM_WORDS = List.of(
            "电池", "充电", "续航", "刹车", "制动", "发动机", "电机", "空调", "转向", "悬挂", "变速箱", "气囊", "胎压", "油耗");

    private final SupportedScenarioCatalog catalog;
    private final RuleRepository rules;
    private final AssetRepository assets;
    private final EvidenceRepository evidence;
    private final SymptomResolver symptoms = new SymptomResolver();

    public RouteValidator(SupportedScenarioCatalog catalog, RuleRepository rules, AssetRepository assets,
                          EvidenceRepository evidence) {
        this.catalog = catalog;
        this.rules = rules;
        this.assets = assets;
        this.evidence = evidence;
    }

    public record AssetResolution(Optional<SupportedScenarioCatalog.SupportedDiagnosis> asset,
                                  boolean unregistered, boolean conflict, boolean ambiguous) {}

    public SupportedScenarioCatalog catalog() { return catalog; }

    /**
     * 只从用户可信来源解析资产：消息中的明确资产、请求体显式 hint、已确认的会话上下文。
     * 模型建议不参与解析，只能在 {@link #validateSuggestion} 中与这里的权威结果做一致性校验。
     * 文本中出现的完整编号必须原样参与判定：未登记即拒绝，绝不截成已知资产前缀。
     */
    public AssetResolution resolveAsset(String content, String currentAssetId, String assetIdHint) {
        Set<String> mentioned = assetTokensInText(content);
        Set<String> unregistered = new LinkedHashSet<>();
        for (String token : mentioned)
            if (findAsset(token).isEmpty() && !isKnownNonAssetId(token)) unregistered.add(token);
        if (!unregistered.isEmpty()) return new AssetResolution(Optional.empty(), true, false, false);

        Set<String> registered = new LinkedHashSet<>();
        for (String token : mentioned) findAsset(token).ifPresent(asset -> registered.add(asset.assetId()));
        if (registered.size() > 1)
            return new AssetResolution(Optional.empty(), false, false, true);
        if (registered.size() == 1) {
            String assetId = registered.iterator().next();
            if (isBlank(assetIdHint)) return new AssetResolution(catalog.find(assetId), false, false, false);
            if (!catalog.supports(assetIdHint))
                return new AssetResolution(Optional.empty(), true, false, false);
            if (!assetIdHint.equals(assetId))
                return new AssetResolution(Optional.empty(), false, true, false);
            return new AssetResolution(catalog.find(assetId), false, false, false);
        }
        if (!isBlank(assetIdHint)) {
            if (!catalog.supports(assetIdHint))
                return new AssetResolution(Optional.empty(), true, false, false);
            return new AssetResolution(catalog.find(assetIdHint), false, false, false);
        }
        if (currentAssetId != null && catalog.supports(currentAssetId))
            return new AssetResolution(catalog.find(currentAssetId), false, false, false);
        return new AssetResolution(Optional.empty(), false, false, false);
    }

    /** 文本中出现的资产编号片段（保留用户原始写法，大小写不敏感），作为编号判定的唯一入口。 */
    public Set<String> assetTokensInText(String content) {
        var found = new LinkedHashSet<String>();
        if (content == null) return found;
        var matcher = ASSET_TOKEN.matcher(content);
        while (matcher.find()) found.add(matcher.group());
        return found;
    }


    /** 大小写不敏感地精确命中 Catalog；不做前缀或子串匹配。 */
    private Optional<SupportedScenarioCatalog.SupportedDiagnosis> findAsset(String token) {
        return catalog.all().stream().filter(entry -> entry.assetId().equalsIgnoreCase(token)).findFirst();
    }

    /**
     * 文本中的编号若是已登记的规则或证据 ID，则不是“未知资产”，不能因此拒绝整条请求。
     * 数据只来自现有只读仓储：按 Catalog 的 domain/symptomCode 取规则，按已登记资产取场景证据。
     */
    public boolean isKnownNonAssetId(String token) {
        if (token == null || token.isBlank()) return false;
        if (knownRuleIds().stream().anyMatch(id -> id.equalsIgnoreCase(token))) return true;
        return knownEvidenceIds().stream().anyMatch(id -> id.equalsIgnoreCase(token));
    }

    /** 规则 ID 集合：严格按 Catalog 的 domain/symptomCode 查询，不遍历或猜测其它领域。 */
    private Set<String> knownRuleIds() {
        var ids = new LinkedHashSet<String>();
        for (var asset : catalog.all())
            for (var rule : rules.query(asset.domain(), asset.symptomCode())) ids.add(rule.ruleId());
        return ids;
    }

    /** 证据 ID 集合：只取已登记资产的出厂档案与更换历史，不试探未登记资产。 */
    private Set<String> knownEvidenceIds() {
        var ids = new LinkedHashSet<String>();
        for (var asset : catalog.all()) {
            for (var record : assets.profile(asset.assetId())) ids.add(record.evidenceId());
            for (var record : evidence.search(asset.assetId(), null, null)) ids.add(record.evidenceId());
        }
        return ids;
    }


    public Optional<String> supportedSymptom(String content) {
        return symptoms.resolve(content);
    }

    public boolean mentionsUnsupportedSymptom(String content) {
        String normalized = SymptomResolver.normalize(content);
        return UNSUPPORTED_SYMPTOM_WORDS.stream().anyMatch(normalized::contains);
    }

    /** 离线与在线 Router 共用同一有限危险词策略，避免只有离线路由拒绝设备控制类请求。 */
    public boolean mentionsDangerousOperation(String content) {
        String normalized = SymptomResolver.normalize(content);
        return DANGEROUS.stream().map(SymptomResolver::normalize).anyMatch(normalized::contains);
    }

    public boolean hasRule(String domain, String symptomCode) {
        return !rules.query(domain, symptomCode).isEmpty();
    }

    /** 在线模型建议必须经过这里；模型不能授权/覆盖资产、自造症状或规则，也不能绕过危险请求拒绝。 */
    public RouteDecision validateSuggestion(Route suggested, String suggestedAssetId, String queryKindText,
                                            String content, String currentAssetId, String assetIdHint) {
        if (mentionsDangerousOperation(content)) return RouteDecision.unsupported("UNSUPPORTED_DANGEROUS");
        if (suggested == Route.UNSUPPORTED) return RouteDecision.unsupported("UNSUPPORTED_REQUEST");

        AssetResolution resolution = resolveAsset(content, currentAssetId, assetIdHint);
        if (resolution.unregistered()) return RouteDecision.unsupported("UNSUPPORTED_ASSET");
        if (resolution.ambiguous()) return RouteDecision.clarify(null, "ASSET_AMBIGUOUS");
        if (resolution.conflict()) return RouteDecision.clarify(null, "ASSET_CONFLICT");
        var asset = resolution.asset();
        // 模型候选不是用户授权：没有可信资产时只能澄清，不能凭模型建议进入查询/诊断。
        if (asset.isEmpty()) return RouteDecision.clarify(null, "ASSET_MISSING");

        var supported = asset.get();
        if (!isBlank(suggestedAssetId)) {
            if (!catalog.supports(suggestedAssetId)) return RouteDecision.unsupported("UNSUPPORTED_ASSET");
            if (!suggestedAssetId.equals(supported.assetId())) return RouteDecision.clarify(null, "ASSET_CONFLICT");
        }
        return switch (suggested) {
            case QUERY -> {
                QueryKind kind = parseQueryKind(queryKindText);
                yield kind == null
                        ? RouteDecision.clarify(supported.assetId(), "QUERY_KIND_MISSING")
                        : RouteDecision.query(supported.assetId(), kind);
            }
            case DIAGNOSE -> {
                if (supportedSymptom(content).isEmpty()) {
                    yield mentionsUnsupportedSymptom(content)
                            ? RouteDecision.unsupported("UNSUPPORTED_SYMPTOM")
                            : RouteDecision.clarify(supported.assetId(), "SYMPTOM_MISSING");
                }
                yield hasRule(supported.domain(), supported.symptomCode())
                        ? RouteDecision.diagnose(supported.assetId(), supported.symptomCode())
                        : RouteDecision.unsupported("UNSUPPORTED_RULE");
            }
            case CLARIFY -> RouteDecision.clarify(supported.assetId(), "ROUTER_CLARIFY");
            case UNSUPPORTED -> RouteDecision.unsupported("UNSUPPORTED_REQUEST");
        };
    }

    private static QueryKind parseQueryKind(String text) {
        if (text == null || text.isBlank()) return null;
        try { return QueryKind.valueOf(text.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

}