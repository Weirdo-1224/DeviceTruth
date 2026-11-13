package io.devicetruth.core.contract;

import java.time.Instant;
import java.util.List;

/**
 * 设备事实查询的公开协议（schemaVersion = 1）：get_asset / get_configuration / get_maintenance_history。
 * 只表达“记录里写了什么、来源是什么、在什么时点有效、是否已现场核实、是否存在冲突”，
 * 不预填诊断结论，也不把最新资料或 RAG 相似度当作当前装机事实。
 * dataNature 始终保留 SIMULATED/AUTHORIZED 标记。
 */
public final class AssetFacts {
    private AssetFacts() {}

    /** 工具失败码：缺数据、越权与参数错误必须区分，不能混成“无证据”或“成功结论”。
     * DATA_STALE / AMBIGUOUS 语义随 recordedAt 边界延后到 Phase 05（REQUIREMENTS.md TOOL-03 记部分实现），届时连同负例一并补回。 */
    public enum FailureCode { NOT_FOUND, PERMISSION_DENIED, INVALID_ARGUMENTS, INVALID_TOOL_OUTPUT }

    /** 核实状态：资料记载（文档/改造/历史）与现场已核实分开表达。 */
    public enum VerificationStatus { DOCUMENTED, FIELD_VERIFIED }

    /** 冲突状态：同一字段在同一时点存在多个高权威取值且未获现场确认时为 CONFLICT。 */
    public enum ConflictStatus { NONE, CONFLICT }

    /**
     * 当前装机判断：只有“时点内有效的现场核实记录”才可能是 FIELD_CONFIRMED，其余一律 UNCONFIRMED。
     * requiredChecks 说明还缺什么才能确认，供 Agent 在答案里作为人工核验项。
     */
    public enum InstallationStatus { FIELD_CONFIRMED, UNCONFIRMED }

    /** 资产身份：只包含登记信息与登记了哪些组件，不带任何配置或诊断。 */
    public record AssetView(String assetId, String ownerId, String projectId, List<String> componentIds,
                            Instant asOf, String dataNature) {
        public AssetView {
            Checks.id(assetId, "assetId");
            Checks.id(ownerId, "ownerId");
            Checks.id(projectId, "projectId");
            componentIds = Checks.list(componentIds);
            Checks.required(asOf, "asOf");
            Checks.text(dataNature, "dataNature");
        }
    }

    /** 一条配置记录：value 是该 attribute 在此时点的取值，来源与核实状态独立给出。 */
    public record ConfigurationEntry(String recordId, String componentId, String attribute, String value,
                                     String sourceType, String sourceId, String sourceRef, String dataNature,
                                     VerificationStatus verificationStatus, int authority,
                                     Instant validFrom, Instant validTo, Instant recordedAt, Instant observedAt) {
        public ConfigurationEntry {
            Checks.text(recordId, "recordId");
            Checks.id(componentId, "componentId");
            Checks.id(attribute, "attribute");
            Checks.text(value, "value");
            Checks.id(sourceType, "sourceType");
            Checks.text(sourceId, "sourceId");
            Checks.text(sourceRef, "sourceRef");
            Checks.text(dataNature, "dataNature");
            Checks.required(verificationStatus, "verificationStatus");
            Checks.required(validFrom, "validFrom");
            Checks.required(recordedAt, "recordedAt");
        }

        public boolean fieldVerified() { return verificationStatus == VerificationStatus.FIELD_VERIFIED; }
    }

    /** 一条维修记录：before/after 是维修前后取值，不是最终诊断。 */
    public record MaintenanceEntry(String recordId, String componentId, String operation, String beforeValue,
                                   String afterValue, String sourceType, String sourceId, String sourceRef,
                                   String dataNature, VerificationStatus verificationStatus, int authority,
                                   Instant validFrom, Instant validTo, Instant recordedAt, Instant observedAt) {
        public MaintenanceEntry {
            Checks.text(recordId, "recordId");
            Checks.id(componentId, "componentId");
            Checks.text(operation, "operation");
            Checks.id(sourceType, "sourceType");
            Checks.text(sourceId, "sourceId");
            Checks.text(sourceRef, "sourceRef");
            Checks.text(dataNature, "dataNature");
            Checks.required(verificationStatus, "verificationStatus");
            Checks.required(validFrom, "validFrom");
            Checks.required(recordedAt, "recordedAt");
        }
    }

    /**
     * get_asset 结果：身份与组件清单。
     * dataNature 取该资产导入记录中最保守的标记（任一 SIMULATED 即整体 SIMULATED）。
     */
    public record AssetOutcome(boolean success, String requestId, String toolName, AssetView asset,
                               String errorCode, String message, boolean retryable) {}

    /**
     * get_configuration 结果：时点内有效的记录、当前装机判断与冲突状态。
     * installationStatus 为 UNCONFIRMED 时 requiredChecks 必须非空；
     * conflict 为 CONFLICT 时 conflict 说明哪些取值互相矛盾。
     */
    public record ConfigurationOutcome(boolean success, String requestId, String toolName, String assetId,
                                       String componentId, String attribute, Instant asOf,
                                       List<ConfigurationEntry> entries, InstallationStatus installationStatus,
                                       ConflictStatus conflict, List<String> requiredChecks,
                                       List<String> limitations, String dataNature,
                                       String errorCode, String message, boolean retryable) {
        public ConfigurationOutcome {
            entries = Checks.list(entries);
            requiredChecks = Checks.list(requiredChecks);
            limitations = Checks.list(limitations);
            if (success && installationStatus == InstallationStatus.UNCONFIRMED && requiredChecks.isEmpty())
                throw new IllegalArgumentException("UNCONFIRMED requires requiredChecks");
        }
    }

    /** get_maintenance_history 结果：只返回 before/after 与来源，不返回诊断结论。 */
    public record MaintenanceOutcome(boolean success, String requestId, String toolName, String assetId,
                                     String componentId, Instant asOf, List<MaintenanceEntry> entries,
                                     List<String> limitations, String dataNature,
                                     String errorCode, String message, boolean retryable) {
        public MaintenanceOutcome {
            entries = Checks.list(entries);
            limitations = Checks.list(limitations);
        }
    }

}