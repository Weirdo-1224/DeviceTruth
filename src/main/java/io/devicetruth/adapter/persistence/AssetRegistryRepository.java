package io.devicetruth.adapter.persistence;

import io.devicetruth.core.capability.Repositories.AssetComponent;
import io.devicetruth.core.capability.Repositories.AssetConfigurationRecord;
import io.devicetruth.core.capability.Repositories.AssetIdentity;
import io.devicetruth.core.capability.Repositories.AssetMaintenanceRecord;
import io.devicetruth.core.capability.Repositories.AssetSnapshot;
import io.devicetruth.core.capability.Repositories.QueryException;
import io.devicetruth.core.contract.Checks;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL 结构化设备主数据仓储（03-01）。
 * 所有查询都带 owner/project 条件并参数化；时点过滤在 SQL 内完成，避免把越界数据带进内存。
 * 导入是幂等的插入式快照：已存在的来源条目保持原值，不删除、不改写历史证据。
 */
public final class AssetRegistryRepository
        implements io.devicetruth.core.capability.Repositories.AssetRegistryRepository {
    private static final String CONFIGURATION_COLUMNS =
            "id,asset_id,component_id,attribute,value,source_type,source_id,source_ref,data_nature,"
                    + "verification_status,authority,valid_from,valid_to,recorded_at,observed_at";
    private static final String MAINTENANCE_COLUMNS =
            "id,asset_id,component_id,operation,before_value,after_value,source_type,source_id,source_ref,"
                    + "data_nature,verification_status,authority,valid_from,valid_to,recorded_at,observed_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public AssetRegistryRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        Checks.required(jdbc, "jdbc");
        Checks.required(transactions, "transactions");
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public AssetIdentity asset(String ownerId, String projectId, String assetId) {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        Checks.id(assetId, "assetId");
        List<AssetIdentity> rows = jdbc.query(
                "SELECT id,owner_id,project_id FROM dt_asset WHERE id=? AND owner_id=? AND project_id=?",
                (r, n) -> new AssetIdentity(r.getString("id"), r.getString("owner_id"), r.getString("project_id")),
                assetId, ownerId, projectId);
        if (rows.isEmpty()) throw new QueryException("ASSET_NOT_FOUND", "Asset not found in authorized scope");
        return rows.getFirst();
    }

    @Override
    public List<AssetComponent> components(String ownerId, String projectId, String assetId) {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        Checks.id(assetId, "assetId");
        return jdbc.query("""
                SELECT asset_id,component_id,label FROM dt_asset_component
                WHERE asset_id=? AND owner_id=? AND project_id=? ORDER BY component_id
                """, (r, n) -> new AssetComponent(r.getString("asset_id"), r.getString("component_id"),
                r.getString("label")), assetId, ownerId, projectId);
    }

    @Override
    public List<AssetConfigurationRecord> configurations(String ownerId, String projectId, String assetId,
                                                          String componentId, String attribute, Instant asOf) {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        Checks.id(assetId, "assetId");
        Checks.required(asOf, "asOf");
        StringBuilder sql = new StringBuilder("SELECT ").append(CONFIGURATION_COLUMNS)
                .append(" FROM dt_asset_configuration WHERE owner_id=? AND project_id=? AND asset_id=?")
                // 时点口径：已生效、尚未失效；记录时间不晚于查询时点可让“迟到记录”保持可追溯。
                .append(" AND valid_from<=? AND (valid_to IS NULL OR valid_to>?)");
        List<Object> arguments = new ArrayList<>(List.of(ownerId, projectId, assetId,
                timestamp(asOf), timestamp(asOf)));
        if (componentId != null && !componentId.isBlank()) {
            Checks.id(componentId, "componentId");
            sql.append(" AND component_id=?");
            arguments.add(componentId);
        }
        if (attribute != null && !attribute.isBlank()) {
            Checks.id(attribute, "attribute");
            sql.append(" AND attribute=?");
            arguments.add(attribute);
        }
        sql.append(" ORDER BY component_id,attribute,valid_from,source_type,source_id");
        return jdbc.query(sql.toString(), this::configuration, arguments.toArray());
    }

    @Override
    public List<AssetMaintenanceRecord> maintenanceHistory(String ownerId, String projectId, String assetId,
                                                           String componentId, Instant asOf) {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        Checks.id(assetId, "assetId");
        Checks.required(asOf, "asOf");
        StringBuilder sql = new StringBuilder("SELECT ").append(MAINTENANCE_COLUMNS)
                .append(" FROM dt_asset_maintenance WHERE owner_id=? AND project_id=? AND asset_id=?")
                .append(" AND valid_from<=? AND (valid_to IS NULL OR valid_to>?)");
        List<Object> arguments = new ArrayList<>(List.of(ownerId, projectId, assetId,
                timestamp(asOf), timestamp(asOf)));
        if (componentId != null && !componentId.isBlank()) {
            Checks.id(componentId, "componentId");
            sql.append(" AND component_id=?");
            arguments.add(componentId);
        }
        sql.append(" ORDER BY valid_from,component_id,source_type,source_id");
        return jdbc.query(sql.toString(), this::maintenance, arguments.toArray());
    }

    @Override
    public void importSnapshot(AssetSnapshot snapshot) {
        Checks.required(snapshot, "snapshot");
        AssetIdentity identity = snapshot.identity();
        transactions.execute(status -> {
            jdbc.update("""
                    INSERT INTO dt_asset(id,owner_id,project_id,created_at) VALUES (?,?,?,?)
                    ON CONFLICT (id,owner_id,project_id) DO NOTHING
                    """, identity.assetId(), identity.ownerId(), identity.projectId(), timestamp(Instant.now()));
            for (AssetComponent component : snapshot.components()) {
                checkOwner(component.assetId(), identity.assetId());
                jdbc.update("""
                        INSERT INTO dt_asset_component(asset_id,component_id,owner_id,project_id,label,created_at)
                        VALUES (?,?,?,?,?,?) ON CONFLICT (asset_id,component_id) DO NOTHING
                        """, component.assetId(), component.componentId(), identity.ownerId(), identity.projectId(),
                        component.label(), timestamp(Instant.now()));
            }
            for (AssetConfigurationRecord record : snapshot.configurations()) {
                checkOwner(record.assetId(), identity.assetId());
                jdbc.update("""
                        INSERT INTO dt_asset_configuration(id,asset_id,owner_id,project_id,component_id,attribute,
                            value,source_type,source_id,source_ref,data_nature,verification_status,authority,
                            valid_from,valid_to,recorded_at,observed_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (asset_id,owner_id,project_id,source_id,component_id,attribute,valid_from) DO NOTHING
                        """, uuid(record.recordId()), record.assetId(), identity.ownerId(), identity.projectId(),
                        record.componentId(), record.attribute(), record.value(), record.sourceType(),
                        record.sourceId(), record.sourceRef(), record.dataNature(), record.verificationStatus(),
                        record.authority(), timestamp(record.validFrom()), nullableTimestamp(record.validTo()),
                        timestamp(record.recordedAt()), nullableTimestamp(record.observedAt()));
            }
            for (AssetMaintenanceRecord record : snapshot.maintenance()) {
                checkOwner(record.assetId(), identity.assetId());
                jdbc.update("""
                        INSERT INTO dt_asset_maintenance(id,asset_id,owner_id,project_id,component_id,operation,
                            before_value,after_value,source_type,source_id,source_ref,data_nature,
                            verification_status,authority,valid_from,valid_to,recorded_at,observed_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT (asset_id,owner_id,project_id,source_id,component_id,operation,valid_from) DO NOTHING
                        """, uuid(record.recordId()), record.assetId(), identity.ownerId(), identity.projectId(),
                        record.componentId(), record.operation(), record.beforeValue(), record.afterValue(),
                        record.sourceType(), record.sourceId(), record.sourceRef(), record.dataNature(),
                        record.verificationStatus(), record.authority(), timestamp(record.validFrom()),
                        nullableTimestamp(record.validTo()), timestamp(record.recordedAt()),
                        nullableTimestamp(record.observedAt()));
            }
            return null;
        });
    }

    /** 快照里的每条记录必须属于同一资产，避免一次导入把别的资产记录挂进来。 */
    private static void checkOwner(String recordAssetId, String expectedAssetId) {
        if (!expectedAssetId.equals(recordAssetId))
            throw new IllegalArgumentException("Snapshot record belongs to another asset");
    }

    private AssetConfigurationRecord configuration(ResultSet r, int n) throws SQLException {
        return new AssetConfigurationRecord(r.getObject("id", UUID.class).toString(), r.getString("asset_id"),
                r.getString("component_id"), r.getString("attribute"), r.getString("value"),
                r.getString("source_type"), r.getString("source_id"), r.getString("source_ref"),
                r.getString("data_nature"), r.getString("verification_status"), r.getInt("authority"),
                instant(r, "valid_from"), instant(r, "valid_to"), instant(r, "recorded_at"),
                instant(r, "observed_at"));
    }

    private AssetMaintenanceRecord maintenance(ResultSet r, int n) throws SQLException {
        return new AssetMaintenanceRecord(r.getObject("id", UUID.class).toString(), r.getString("asset_id"),
                r.getString("component_id"), r.getString("operation"), r.getString("before_value"),
                r.getString("after_value"), r.getString("source_type"), r.getString("source_id"),
                r.getString("source_ref"), r.getString("data_nature"), r.getString("verification_status"),
                r.getInt("authority"), instant(r, "valid_from"), instant(r, "valid_to"),
                instant(r, "recorded_at"), instant(r, "observed_at"));
    }

    /**
     * 读回 TIMESTAMPTZ 统一经 getTimestamp().toInstant() 得到绝对时刻；
     * 驱动按 JVM 默认时区构造 Timestamp，toInstant() 后与时区无关。
     */
    private static Instant instant(ResultSet r, String column) throws SQLException {
        var value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** 绑定显式使用 UTC OffsetDateTime，语义清晰；Timestamp.from(Instant) 经驱动绑定同样正确。 */
    private static OffsetDateTime timestamp(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime nullableTimestamp(Instant value) { return timestamp(value); }

    /** 记录 ID 使用 UUID 主键；非 UUID 形式的来源 ID 由数据库自身生成，保持导入可重入。 */
    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }


}