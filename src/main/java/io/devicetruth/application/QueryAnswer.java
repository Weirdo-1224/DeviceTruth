package io.devicetruth.application;

import java.util.List;

/**
 * 受限事实查询的回答：只暴露允许公开的字段。
 * sources 的每一项都取自真实记录（evidenceId/sourceRef/sourceType），不是拼接出来的假来源。
 */
public record QueryAnswer(String scope, String assetId, String answer, List<Source> sources,
                          List<String> limitations, String dataNature) {
    public static final String SIMULATED = "SIMULATED";

    /** 记录来源：evidenceId 与 sourceRef 是两个不同字段，不能互相顶替。 */
    public record Source(String evidenceId, String sourceRef, String sourceType, String dataNature) {
        public Source {
            if (evidenceId == null || evidenceId.isBlank()) throw new IllegalArgumentException("evidenceId is required");
            if (sourceRef == null || sourceRef.isBlank()) throw new IllegalArgumentException("sourceRef is required");
            if (sourceType == null || sourceType.isBlank()) throw new IllegalArgumentException("sourceType is required");
        }
    }

    public QueryAnswer {
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope is required");
        if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId is required");
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("answer is required");
        sources = List.copyOf(sources);
        limitations = List.copyOf(limitations);
        if (!SIMULATED.equals(dataNature)) throw new IllegalArgumentException("Only SIMULATED answers accepted");
    }
}