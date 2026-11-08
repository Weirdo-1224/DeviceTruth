package io.devicetruth.application;

import io.devicetruth.core.contract.Models.DiagnosisRequest;
import java.time.Instant;

/**
 * 应用层诊断目标：保留用户原始目标与程序归一化结果。
 * originalUserMessage/normalizedGoal 只留在应用层；旧 DiagnosisRequest 没有这两个字段，不能宣称已传入 Core。
 */
public record DiagnosisGoal(String originalUserMessage, String normalizedGoal, String assetId, String domain,
                            String symptomText, String symptomCode, Instant requestedAt, Instant diagnosisAsOf,
                            String dataNature) {
    public static final String SIMULATED = "SIMULATED";

    public DiagnosisGoal {
        if (originalUserMessage == null || originalUserMessage.isBlank())
            throw new IllegalArgumentException("originalUserMessage is required");
        if (normalizedGoal == null || normalizedGoal.isBlank()) throw new IllegalArgumentException("normalizedGoal is required");
        if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId is required");
        if (domain == null || domain.isBlank()) throw new IllegalArgumentException("domain is required");
        if (symptomCode == null || symptomCode.isBlank()) throw new IllegalArgumentException("symptomCode is required");
        if (requestedAt == null) throw new IllegalArgumentException("requestedAt is required");
        if (diagnosisAsOf == null) throw new IllegalArgumentException("diagnosisAsOf is required");
        if (!SIMULATED.equals(dataNature)) throw new IllegalArgumentException("Only SIMULATED goals accepted");
    }

    /** 映射到既有 Core 契约：业务时间必须用 Catalog 的固定模拟时间，不能用请求时间替代。 */
    public DiagnosisRequest toRequest() {
        return new DiagnosisRequest(assetId, domain, symptomCode, diagnosisAsOf);
    }
}