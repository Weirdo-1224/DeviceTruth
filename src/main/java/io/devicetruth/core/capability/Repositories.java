package io.devicetruth.core.capability;

import io.devicetruth.core.contract.Models.*;
import java.time.Instant;
import java.util.List;

/** 数据仓库接口契约：能力网关背后只依赖这三个只读接口。 */
public final class Repositories {
    private Repositories() {}
    /** 资产档案仓库：出厂配置（profile）与更换事件历史（history）。 */
    public interface AssetRepository {
        List<EvidenceRecord> profile(String assetId);
        List<EvidenceRecord> history(String assetId, String componentId);
    }
    /** 证据检索仓库：按条件搜索元数据（search）与按 ID 取原始记录（get）。 */
    public interface EvidenceRepository {
        List<EvidenceMetadata> search(String assetId, String componentId, String sourceType);
        EvidenceRecord get(String assetId, String evidenceId);
    }
    /** 规则仓库：按领域+症状码查询候选规则。 */
    public interface RuleRepository { List<Rule> query(String domain, String symptomCode); }

    /**
     * 结构化设备事实（03-01）：身份、组件、配置与维修历史由 PG 主数据提供，替代 RAG 充当现场事实。
     * 所有方法都带服务器确定的归属范围，查询参数化；查询时点由调用方给出（asOf）。
     * 有效期口径：valid_from &lt;= asOf 且（valid_to 为空或 valid_to &gt; asOf）。
     */
    public interface AssetRegistryRepository {
        /** 资产身份；不存在或不属于该归属返回 NOT_FOUND。 */
        AssetIdentity asset(String ownerId, String projectId, String assetId);

        /** 该资产登记的组件（按组件 ID 排序）。 */
        List<AssetComponent> components(String ownerId, String projectId, String assetId);

        /** 指定时点内有效的配置记录；attribute 为空表示全部字段。 */
        List<AssetConfigurationRecord> configurations(String ownerId, String projectId, String assetId,
                                                      String componentId, String attribute, Instant asOf);

        /** 指定时点内的维修历史：返回 before/after 与来源，不返回最终诊断。 */
        List<AssetMaintenanceRecord> maintenanceHistory(String ownerId, String projectId, String assetId,
                                                        String componentId, Instant asOf);

        /**
         * 幂等导入一条资产快照（资产、组件、配置与维修记录）。
         * 已存在的来源条目不会被改写，旧记录不删除，可重复执行。
         */
        void importSnapshot(AssetSnapshot snapshot);
    }

    /** 资产身份：只说明“这台资产登记归属谁”，不携带任何配置或诊断结论。 */
    public record AssetIdentity(String assetId, String ownerId, String projectId) {
        public AssetIdentity {
            if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId is required");
            if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
            if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId is required");
        }
    }

    public record AssetComponent(String assetId, String componentId, String label) {
        public AssetComponent {
            if (componentId == null || componentId.isBlank()) throw new IllegalArgumentException("componentId is required");
        }
    }

    /**
     * 一条配置记录：value 是指定 attribute 在该记录里的取值，
     * sourceType/sourceId 说明出处，verificationStatus 区分“资料记载”与“现场已核实”。
     */
    public record AssetConfigurationRecord(String recordId, String assetId, String componentId, String attribute,
                                          String value, String sourceType, String sourceId, String sourceRef,
                                          String dataNature, String verificationStatus, int authority,
                                          Instant validFrom, Instant validTo, Instant recordedAt,
                                          Instant observedAt) {}

    /** 一条维修记录：before/after 是维修前后取值，不是最终诊断。 */
    public record AssetMaintenanceRecord(String recordId, String assetId, String componentId, String operation,
                                         String beforeValue, String afterValue, String sourceType, String sourceId,
                                         String sourceRef, String dataNature, String verificationStatus,
                                         int authority, Instant validFrom, Instant validTo, Instant recordedAt,
                                         Instant observedAt) {}

    /** 幂等导入用的资产快照；导入实现只插入缺失的条目，不删除、不改写既有来源记录。 */
    public record AssetSnapshot(AssetIdentity identity, List<AssetComponent> components,
                                List<AssetConfigurationRecord> configurations,
                                List<AssetMaintenanceRecord> maintenance) {
        public AssetSnapshot {
            if (identity == null) throw new IllegalArgumentException("identity is required");
            components = components == null ? List.of() : List.copyOf(components);
            configurations = configurations == null ? List.of() : List.copyOf(configurations);
            maintenance = maintenance == null ? List.of() : List.copyOf(maintenance);
        }
    }

    /** 查询失败异常：携带稳定的错误码（如 ASSET_NOT_FOUND），供上层映射为 ToolError。 */
    public static final class QueryException extends RuntimeException {
        private final String code;
        public QueryException(String code, String message) { super(message); this.code=code; }
        public String code() { return code; }
    }
}
