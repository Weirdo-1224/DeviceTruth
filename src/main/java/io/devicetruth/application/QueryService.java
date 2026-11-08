package io.devicetruth.application;

import io.devicetruth.core.capability.Repositories.AssetRepository;
import io.devicetruth.core.contract.Models.EvidenceRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 受限事实查询：只支持 ASSET_PROFILE 与 MAINTENANCE_HISTORY 两类只读查询。
 * 不构造 DiagnosisRequest、不经过 CapabilityGateway、不做状态重建；回答内容全部来自真实证据记录。
 */
public final class QueryService {
    private final AssetRepository assets;

    public QueryService(AssetRepository assets) { this.assets = assets; }

    public QueryAnswer query(String assetId, QueryKind kind) {
        if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId is required");
        if (kind == null) throw new IllegalArgumentException("queryKind is required");
        return switch (kind) {
            case ASSET_PROFILE -> profile(assetId);
            case MAINTENANCE_HISTORY -> history(assetId);
        };
    }

    private QueryAnswer profile(String assetId) {
        // 出厂档案：AssetRepository.profile 只返回 FACTORY 记录。
        var records = checked(assetId, assets.profile(assetId));
        var limitations = List.of("出厂/档案记录只代表设备初始档案，不能回答“当前实际安装的是不是该型号”。",
                "当前配置需以有效的现场确认证据为准，本查询不做状态重建。");
        if (records.isEmpty())
            return new QueryAnswer(QueryKind.ASSET_PROFILE.name(), assetId,
                    "该资产没有出厂档案类模拟记录。", List.of(), limitations, QueryAnswer.SIMULATED);
        String facts = describe(records);
        return new QueryAnswer(QueryKind.ASSET_PROFILE.name(), assetId,
                "模拟出厂记录：" + facts + "。", sources(records), limitations, QueryAnswer.SIMULATED);
    }

    private QueryAnswer history(String assetId) {
        // 维修/更换历史：componentId 必须传 null，JsonRepositories 只按 payload.subject 过滤。
        var records = checked(assetId, assets.history(assetId, null));
        var limitations = List.of("维修/更换记录只说明曾经发生过更换，不等同于当前配置已获现场确认。",
                "当前实际装机状态需以有效的现场确认证据为准，本查询不做状态重建。");
        if (records.isEmpty())
            return new QueryAnswer(QueryKind.MAINTENANCE_HISTORY.name(), assetId,
                    "该资产没有维修/更换类模拟记录。", List.of(), limitations, QueryAnswer.SIMULATED);
        String facts = describe(records);
        return new QueryAnswer(QueryKind.MAINTENANCE_HISTORY.name(), assetId,
                "模拟维修/更换记录：" + facts + "；以上记录不等于当前现场确认的实际配置。",
                sources(records), limitations, QueryAnswer.SIMULATED);
    }

    /** 返回记录必须属于请求资产，否则视为内部错误，不把别人的数据当成本资产的事实。 */
    private static List<EvidenceRecord> checked(String assetId, List<EvidenceRecord> records) {
        if (records == null) return List.of();
        for (var record : records)
            if (!assetId.equals(record.assetId()))
                throw new ApplicationExceptions.QueryAssetMismatchException();
        return List.copyOf(records);
    }

    private static String describe(List<EvidenceRecord> records) {
        return records.stream()
                .map(r -> String.format("%s %s=%s（%s，生效时间 %s）",
                        r.payload().get("subject"), r.payload().get("predicate"), r.payload().get("value"),
                        r.sourceType(), r.effectiveAt()))
                .collect(Collectors.joining("；"));
    }

    private static List<QueryAnswer.Source> sources(List<EvidenceRecord> records) {
        var sources = new ArrayList<QueryAnswer.Source>();
        for (var record : records)
            sources.add(new QueryAnswer.Source(record.evidenceId(), record.sourceRef(), record.sourceType(),
                    QueryAnswer.SIMULATED));
        return sources;
    }
}