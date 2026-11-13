package io.devicetruth.application.agent;

import io.devicetruth.core.capability.Repositories.AssetIdentity;
import io.devicetruth.core.capability.Repositories.AssetRegistryRepository;
import io.devicetruth.core.capability.Repositories.QueryException;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AssetFacts.AssetOutcome;
import io.devicetruth.core.contract.AssetFacts.ConfigurationEntry;
import io.devicetruth.core.contract.AssetFacts.ConfigurationOutcome;
import io.devicetruth.core.contract.AssetFacts.ConflictStatus;
import io.devicetruth.core.contract.AssetFacts.InstallationStatus;
import io.devicetruth.core.contract.AssetFacts.MaintenanceEntry;
import io.devicetruth.core.contract.AssetFacts.MaintenanceOutcome;
import io.devicetruth.core.contract.Checks;
import java.time.Instant;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * product 模式的设备事实查询服务（03-01）：HTTP API 与资产工具共用同一套时点口径与归属校验。
 * 只读：不解锁诊断、不写资产主数据、不把查询结果变成已确认结论。
 */
public final class AssetFactService {
    private final AssetRegistryRepository registry;
    private final AssetTools tools;
    private final JdbcTemplate jdbc;

    public AssetFactService(AssetRegistryRepository registry, AssetTools tools, JdbcTemplate jdbc) {
        Checks.required(registry, "registry");
        Checks.required(tools, "tools");
        this.registry = registry;
        this.tools = tools;
        this.jdbc = jdbc;
    }

    /** 沿用既有资产查询语义（profile/history），不改变旧 QueryService 行为。 */
    public AssetOutcome asset(String ownerId, String projectId, String assetId, Instant asOf) {
        return tools.asset(new AssetTools.AssetRequest(assetId), context(ownerId, projectId, assetId, asOf));
    }

    public ConfigurationOutcome configuration(String ownerId, String projectId, String assetId, String componentId,
                                               String attribute, Instant asOf) {
        return tools.configuration(new AssetTools.ConfigurationRequest(assetId, componentId, attribute, asOf),
                context(ownerId, projectId, assetId, asOf));
    }

    public MaintenanceOutcome maintenance(String ownerId, String projectId, String assetId, String componentId,
                                          Instant asOf) {
        return tools.maintenance(new AssetTools.MaintenanceRequest(assetId, componentId, asOf),
                context(ownerId, projectId, assetId, asOf));
    }

    /**
     * 组合视图：同一资产在指定时点的配置与维修记录。
     * 资产不存在时抛出 ASSET_NOT_FOUND，由 API 层统一映射为 404。
     */
    public Composite composite(String ownerId, String projectId, String assetId, Instant asOf) {
        AssetIdentity identity = registry.asset(ownerId, projectId, assetId);
        ToolContext context = context(ownerId, projectId, assetId, asOf);
        Instant at = context.asOf();
        // 组合视图跨全部属性：模型工具的 configuration 合同要求显式 attribute，
        // 服务端视图直接走仓储（attribute=null 表示全部），不放宽工具侧校验。
        var records = registry.configurations(ownerId, projectId, assetId, null, null, at);
        List<ConfigurationEntry> configurations = records.stream().map(AssetTools::entry).toList();
        MaintenanceOutcome maintenance = tools.maintenance(
                new AssetTools.MaintenanceRequest(assetId, null, at), context);
        InstallationStatus installation = AssetTools.installation(configurations);
        ConflictStatus conflict = AssetTools.conflict(configurations);
        List<String> requiredChecks = installation == InstallationStatus.FIELD_CONFIRMED ? List.of()
                : configurations.isEmpty()
                ? List.of("需要对设备 " + assetId + " 的相关部件做现场核验（拍照/铭牌/读数）后才能确认当前实际配置")
                : AssetTools.requiredChecks(configurations);
        return new Composite(identity.assetId(), ownerId, projectId, at,
                configurations, maintenance.entries(), installation, conflict, requiredChecks,
                AssetTools.limitations(), AssetTools.conservativeNature(
                        records.stream().map(io.devicetruth.core.capability.Repositories.AssetConfigurationRecord::dataNature).toList()));
    }

    public record Composite(String assetId, String ownerId, String projectId, Instant asOf,
                            List<ConfigurationEntry> configurations, List<MaintenanceEntry> maintenance,
                            io.devicetruth.core.contract.AssetFacts.InstallationStatus installationStatus,
                            io.devicetruth.core.contract.AssetFacts.ConflictStatus conflict,
                            List<String> requiredChecks, List<String> limitations, String dataNature) {
        public Composite {
            configurations = Checks.list(configurations);
            maintenance = Checks.list(maintenance);
            requiredChecks = Checks.list(requiredChecks);
            limitations = Checks.list(limitations);
        }
    }

    /** 查询上下文的 run 是只读审计占位：为资产 API 单独建一条 RUNNING 不写消息的 run 会污染队列， */
    /** 因此 API 路径使用一次性的代理上下文，把 toolNames 限定为三个资产工具。 */
    private static final Set<String> ASSET_TOOLS = Set.of(AssetTools.GET_ASSET,
            AssetTools.GET_CONFIGURATION, AssetTools.GET_MAINTENANCE_HISTORY);

    private ToolContext context(String ownerId, String projectId, String assetId, Instant asOf) {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        if (assetId != null) Checks.id(assetId, "assetId");
        Instant at = asOf == null ? Instant.now() : asOf;
        return new ToolContext(ownerId, projectId, null, UUID.nameUUIDFromBytes(
                (ownerId + ":" + projectId + ":" + at).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                assetId, at, at, ASSET_TOOLS);
    }

    /** 供 API 层判断资产是否在该归属下登记（不泄露其他身份是否存在）。 */
    public boolean exists(String ownerId, String projectId, String assetId) {
        try {
            registry.asset(ownerId, projectId, assetId);
            return true;
        } catch (QueryException notFound) {
            if ("ASSET_NOT_FOUND".equals(notFound.code())) return false;
            throw notFound;
        }
    }

    /** 已登记资产清单（只含本人/项目下确有主数据的资产）。 */
    public List<String> assets(String ownerId, String projectId) {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        return jdbc.query("SELECT id FROM dt_asset WHERE owner_id=? AND project_id=? ORDER BY id",
                (r, n) -> r.getString(1), ownerId, projectId);
    }

}