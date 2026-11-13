package io.devicetruth.adapter.persistence;

import io.devicetruth.application.agent.AssetTools;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AssetFacts.ConfigurationEntry;
import io.devicetruth.core.contract.AssetFacts.ConfigurationOutcome;
import io.devicetruth.core.contract.AssetFacts.MaintenanceEntry;
import io.devicetruth.core.contract.AssetFacts.MaintenanceOutcome;
import io.devicetruth.core.contract.Checks;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 资产事实引用的持久登记（03-01）：把本次查询取到的配置/维修记录登记进 dt_evidence_reference。
 * run、会话与归属全部来自服务器决定的 ToolContext；同一 run 同一来源记录只登记一次（幂等）。
 * 只登记 CONFIGURATION 来源，读取仍按 run 归属校验。
 */
public final class AssetFactReferenceWriter implements AssetTools.FactReferenceWriter {
    private static final String SOURCE_TYPE = "CONFIGURATION";

    private final JdbcTemplate jdbc;

    public AssetFactReferenceWriter(JdbcTemplate jdbc) {
        Checks.required(jdbc, "jdbc");
        this.jdbc = jdbc;
    }

    @Override
    public boolean runBound(ToolContext context) {
        if (context == null || context.runId() == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM dt_agent_run WHERE id=? AND owner_id=? AND project_id=?",
                Integer.class, context.runId(), context.ownerId(), context.projectId());
        return count != null && count > 0;
    }

    /**
     * 登记一条可审计引用。引用身份 = 事实记录 ID + 实际资产 + 业务查询时点：
     * 同一来源可携带多条不同组件/属性/时点的事实，各自是独立引用；
     * 同 run 相同事实、资产和时点复用引用；不同时点保留独立审计记录。
     * 摘要与资产、时点随引用落库，挂实际查询范围而非上下文默认值。
     */
    @Override
    public UUID register(ToolContext context, String assetId, Instant asOf, String factKey, String sourceId,
                         String sourceVersion, String title, String excerpt, String dataNature) {
        Checks.required(context, "context");
        Checks.id(assetId, "assetId");
        Checks.required(asOf, "asOf");
        Checks.text(factKey, "factKey");
        Checks.text(sourceId, "sourceId");
        Checks.text(sourceVersion, "sourceVersion");
        Checks.text(title, "title");
        Checks.text(excerpt, "excerpt");
        Checks.text(dataNature, "dataNature");
        UUID factChunkId = factChunkId(factKey, assetId, asOf);
        List<String> conversationIds = jdbc.query(
                "SELECT conversation_id::text FROM dt_agent_run WHERE id=? AND owner_id=? AND project_id=?",
                (r, n) -> r.getString(1), context.runId(), context.ownerId(), context.projectId());
        if (conversationIds.isEmpty()) throw new IllegalStateException("Run not found in authorized scope");
        List<UUID> existing = jdbc.query("""
                SELECT id FROM dt_evidence_reference
                WHERE run_id=? AND owner_id=? AND project_id=? AND source_type=? AND chunk_id=?
                """, (r, n) -> r.getObject(1, UUID.class), context.runId(), context.ownerId(),
                context.projectId(), SOURCE_TYPE, factChunkId);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID referenceId = jdbc.queryForObject("""
                INSERT INTO dt_evidence_reference(id,run_id,conversation_id,owner_id,project_id,source_type,
                    source_id,source_version,chunk_id,asset_id,as_of,citation_page,citation_section,
                    citation_excerpt,retrieved_at,data_nature)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                RETURNING id
                """, UUID.class, UUID.randomUUID(), context.runId(),
                UUID.fromString(conversationIds.getFirst()), context.ownerId(), context.projectId(), SOURCE_TYPE,
                sourceId, sourceVersion, factChunkId, assetId, utc(asOf), null, title, excerpt,
                utc(Instant.now()), dataNature);
        return referenceId;
    }

    /** 登记与回读共用身份算法，避免跨时点复用首次查询的引用。 */
    private static UUID factChunkId(String factKey, String assetId, Instant asOf) {
        return UUID.nameUUIDFromBytes(("asset-fact:" + factKey + ":" + assetId + ":" + asOf)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 与仓储一致：绑定显式 UTC OffsetDateTime。 */
    private static OffsetDateTime utc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    /** 把本次查询登记过的引用整理成公开视图，供 Agent 答案引用使用；摘要随引用返回，可核对事实。 */
    public List<io.devicetruth.core.contract.AgentResult.Reference> forOutcome(ToolContext context,
                                                                               ConfigurationOutcome outcome) {
        return read(context, outcome.assetId(), outcome.asOf(),
                outcome.entries().stream().map(entryKey(outcome)).toList());
    }

    public List<io.devicetruth.core.contract.AgentResult.Reference> forOutcome(ToolContext context,
                                                                               MaintenanceOutcome outcome) {
        return read(context, outcome.assetId(), outcome.asOf(),
                outcome.entries().stream().map(entryKey(outcome)).toList());
    }

    private static java.util.function.Function<ConfigurationEntry, String[]> entryKey(ConfigurationOutcome outcome) {
        return entry -> new String[]{entry.recordId(), entry.sourceId(), entry.recordedAt().toString(),
                "配置记录 " + entry.componentId() + "." + entry.attribute()};
    }

    private static java.util.function.Function<MaintenanceEntry, String[]> entryKey(MaintenanceOutcome outcome) {
        return entry -> new String[]{entry.recordId(), entry.sourceId(), entry.recordedAt().toString(),
                "维修记录 " + entry.componentId() + "." + entry.operation()};
    }

    private List<io.devicetruth.core.contract.AgentResult.Reference> read(ToolContext context, String assetId,
                                                                                   Instant asOf, List<String[]> keys) {
        var result = new java.util.ArrayList<io.devicetruth.core.contract.AgentResult.Reference>();
        for (String[] key : keys) {
            UUID factChunkId = factChunkId(key[0], assetId, asOf);
            jdbc.query("""
                    SELECT r.id, r.source_id, r.source_version, r.citation_section, r.citation_excerpt, r.data_nature
                    FROM dt_evidence_reference r
                    WHERE r.run_id=? AND r.owner_id=? AND r.project_id=? AND r.source_type=? AND r.chunk_id=?
                    """, (r, n) -> {
                result.add(new io.devicetruth.core.contract.AgentResult.Reference(
                        r.getObject("id", UUID.class).toString(), SOURCE_TYPE, r.getString("source_id"),
                        r.getString("source_version"), factChunkId.toString(), key[3], null,
                        r.getString("citation_section"),
                        r.getString("citation_excerpt") == null ? "" : r.getString("citation_excerpt"),
                        r.getString("data_nature")));
                return null;
            }, context.runId(), context.ownerId(), context.projectId(), SOURCE_TYPE, factChunkId);
        }
        return result;
    }
}
