package io.devicetruth.application;

import io.devicetruth.core.contract.Models.DiagnosisRequest;
import java.time.Instant;
import java.util.Optional;

/**
 * 诊断目标映射：由 Catalog 提供领域/症状码/固定业务时间，别名表只识别有限症状，
 * 并再用 RuleRepository 确认规则真实存在。M2 只交付 Goal→Request，不执行诊断。
 */
public final class DiagnosisApplicationService {
    private final SupportedScenarioCatalog catalog;
    private final RouteValidator validator;
    private final SymptomResolver symptoms = new SymptomResolver();

    public DiagnosisApplicationService(SupportedScenarioCatalog catalog, RouteValidator validator) {
        this.catalog = catalog;
        this.validator = validator;
    }

    public record Mapping(Optional<DiagnosisGoal> goal, String reason) {
        static Mapping ok(DiagnosisGoal goal) { return new Mapping(Optional.of(goal), "DIAGNOSE_READY"); }
        static Mapping reject(String reason) { return new Mapping(Optional.empty(), reason); }
    }

    /**
     * 由已通过路由校验的资产与用户消息构造诊断目标。
     * requestedAt 是请求时刻（只记录），diagnosisAsOf 必须取 Catalog 的固定模拟业务时间。
     */
    public Mapping map(String assetId, String userMessage, Instant requestedAt) {
        if (assetId == null || assetId.isBlank()) return Mapping.reject("ASSET_MISSING");
        Optional<SupportedScenarioCatalog.SupportedDiagnosis> supported = catalog.find(assetId);
        if (supported.isEmpty()) return Mapping.reject("UNSUPPORTED_ASSET");
        var entry = supported.get();
        // 症状只认有限别名，不做近似猜测；规则必须真实存在于只读规则仓储。
        Optional<String> symptomCode = symptoms.resolve(userMessage);
        if (symptomCode.isEmpty()) return Mapping.reject("SYMPTOM_MISSING");
        if (!validator.hasRule(entry.domain(), entry.symptomCode())) return Mapping.reject("UNSUPPORTED_RULE");
        DiagnosisGoal goal = new DiagnosisGoal(userMessage, userMessage.trim(), entry.assetId(), entry.domain(),
                symptomCode.get(), entry.symptomCode(), requestedAt, entry.diagnosisAsOf(), DiagnosisGoal.SIMULATED);
        return Mapping.ok(goal);
    }

    /** 只做契约映射，便于测试与后续里程碑复用；M2 不调用 Core。 */
    public static DiagnosisRequest toRequest(DiagnosisGoal goal) { return goal.toRequest(); }
}