package io.devicetruth.application.agent;

import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.Repositories.AssetRegistryRepository;
import io.devicetruth.core.capability.Repositories.QueryException;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.Checks;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 读取已导入的模拟观测，不连接设备或将样本升级为现场核验。 */
public final class ObservationTools {
    public static final String GET_OBSERVATIONS = "get_observations";
    private static final String NATURE = "SIMULATED";
    private static final String NOT_READ = "尚未读取到现场数据；此工具只读取已导入的模拟观测，不连接实时设备。";
    private static final String SIMULATED = "已读取模拟观测；这些记录不代表实时设备状态，也不构成现场核验。";
    private final AssetRegistryRepository assets;
    private final JdbcTemplate jdbc;

    public ObservationTools(AssetRegistryRepository assets, JdbcTemplate jdbc) {
        this.assets = assets;
        this.jdbc = jdbc;
    }

    public enum Quality { GOOD, UNCERTAIN, BAD }
    public enum Status { NOT_INTEGRATED, NO_MATCHING_DATA, SIMULATED_DATA }
    public record Entry(Instant observedAt, String signal, String value, String unit, String sourceId,
                        Quality quality) {}
    public record Result(String assetId, Instant from, Instant asOf, Status status, String message,
                         String dataNature, List<Entry> entries) {
        public Result { entries = List.copyOf(entries); }
    }

    public void registerInto(CapabilityGateway gateway) {
        Map<String, Object> id = Map.of("type", "string", "pattern", "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$");
        Map<String, Object> time = Map.of("type", "string", "format", "date-time");
        gateway.registerTool(new ToolSpec(GET_OBSERVATIONS,
                "读取当前资产已接入的模拟观测，按信号、质量及时间筛选；无数据时明确未读取到现场数据。",
                "asset", true, Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("assetId", id, "signal", id, "from", time, "asOf", time,
                        "quality", Map.of("type", "string", "enum", List.of("GOOD", "UNCERTAIN", "BAD")))),
                "Result{assetId,from,asOf,status,message,dataNature,entries[{observedAt,signal,value,unit,sourceId,quality}]}"),
                this::query);
    }

    private ToolOutcome query(Map<String, Object> args, ToolContext context) {
        String requestId = UUID.randomUUID().toString();
        String assetId = id(args, "assetId");
        if (assetId == null) assetId = context.selectedAssetId();
        if (context.selectedAssetId() != null && !context.selectedAssetId().equals(assetId))
            throw new QueryException("ASSET_NOT_FOUND", "Asset absent or outside observation scope");
        String signal = id(args, "signal");
        Instant from = instant(args, "from");
        Instant asOf = instant(args, "asOf");
        if (asOf == null) asOf = context.asOf();
        if (asOf.isAfter(context.asOf()) || (from != null && from.isAfter(asOf)))
            throw new QueryException("INVALID_ARGUMENTS", "Observation time range exceeds run scope");
        String quality = text(args, "quality");
        if (quality != null) {
            try { Quality.valueOf(quality); }
            catch (IllegalArgumentException ignored) {
                throw new QueryException("INVALID_ARGUMENTS", "Invalid observation quality");
            }
        }
        if (assetId == null) return result(requestId, null, from, asOf, Status.NOT_INTEGRATED, List.of(), List.of());
        assets.asset(context.ownerId(), context.projectId(), assetId);
        var entries = jdbc.query("""
                SELECT observed_at, signal, value, unit, source_id, quality FROM dt_asset_observation
                WHERE owner_id=? AND project_id=? AND asset_id=? AND observed_at<=?
                  AND (CAST(? AS timestamptz) IS NULL OR observed_at>=?)
                  AND (CAST(? AS varchar) IS NULL OR signal=?)
                  AND (CAST(? AS varchar) IS NULL OR quality=?)
                ORDER BY observed_at DESC, signal, source_id LIMIT 12
                """, (rs, row) -> new Entry(rs.getObject("observed_at", OffsetDateTime.class).toInstant(),
                rs.getString("signal"), rs.getString("value"), rs.getString("unit"), rs.getString("source_id"),
                Quality.valueOf(rs.getString("quality"))), context.ownerId(), context.projectId(), assetId,
                utc(asOf), utc(from), utc(from), signal, signal, quality, quality);
        if (entries.isEmpty()) {
            Boolean integrated = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM dt_asset_observation WHERE owner_id=? AND project_id=?
                        AND asset_id=? AND observed_at<=?)
                    """, Boolean.class, context.ownerId(), context.projectId(), assetId, utc(asOf));
            return result(requestId, assetId, from, asOf,
                    Boolean.TRUE.equals(integrated) ? Status.NO_MATCHING_DATA : Status.NOT_INTEGRATED, entries, List.of());
        }
        final String scopedAsset = assetId;
        final Instant scopedAsOf = asOf;
        List<Reference> references = entries.stream()
                .map(entry -> reference(context, scopedAsset, scopedAsOf, entry)).toList();
        return result(requestId, assetId, from, asOf, Status.SIMULATED_DATA, entries, references);
    }

    private static ToolOutcome result(String requestId, String assetId, Instant from, Instant asOf, Status status,
                                      List<Entry> entries, List<Reference> references) {
        String message = status == Status.SIMULATED_DATA ? SIMULATED : NOT_READ;
        return ToolOutcome.ok(requestId, GET_OBSERVATIONS, new Result(assetId, from, asOf, status,
                message, NATURE, entries), references, NATURE,
                Map.of("status", status.name(), "asOf", asOf.toString(), "message", message,
                        "entries", String.valueOf(entries.size())));
    }

    private Reference reference(ToolContext scope, String assetId, Instant asOf, Entry entry) {
        UUID chunkId = UUID.nameUUIDFromBytes(String.join("\n", "observation", assetId, asOf.toString(),
                entry.signal(), entry.observedAt().toString(), entry.sourceId()).getBytes(StandardCharsets.UTF_8));
        String version = entry.observedAt().toString();
        String title = "模拟观测 " + entry.signal();
        String excerpt = "signal=" + entry.signal() + ";value=" + entry.value() + ";unit=" + entry.unit()
                + ";observedAt=" + version + ";quality=" + entry.quality() + ";source=" + entry.sourceId();
        jdbc.update("""
                INSERT INTO dt_evidence_reference(id,run_id,conversation_id,owner_id,project_id,source_type,
                    source_id,source_version,chunk_id,asset_id,as_of,citation_section,citation_excerpt,retrieved_at,data_nature)
                SELECT ?,id,conversation_id,owner_id,project_id,'FIELD_OBSERVATION',?,?,?,?,?,?,?,now(),'SIMULATED'
                FROM dt_agent_run WHERE id=? AND owner_id=? AND project_id=?
                ON CONFLICT (run_id,owner_id,project_id,chunk_id) DO NOTHING
                """, UUID.randomUUID(), entry.sourceId(), version, chunkId, assetId, utc(asOf), title, excerpt,
                scope.runId(), scope.ownerId(), scope.projectId());
        UUID id = jdbc.queryForObject("""
                SELECT id FROM dt_evidence_reference WHERE run_id=? AND owner_id=? AND project_id=? AND chunk_id=?
                """, UUID.class, scope.runId(), scope.ownerId(), scope.projectId(), chunkId);
        return new Reference(id.toString(), "FIELD_OBSERVATION", entry.sourceId(), version, chunkId.toString(),
                title, null, title, excerpt, NATURE);
    }

    private static String id(Map<String, Object> args, String key) {
        String value = text(args, key);
        if (value == null) return null;
        try { return Checks.id(value, key); }
        catch (IllegalArgumentException ignored) { throw new QueryException("INVALID_ARGUMENTS", "Invalid " + key); }
    }

    private static Instant instant(Map<String, Object> args, String key) {
        String value = text(args, key);
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) { throw new QueryException("INVALID_ARGUMENTS", "Invalid " + key); }
    }

    private static String text(Map<String, Object> args, String key) {
        if (!args.containsKey(key)) return null;
        if (args.get(key) instanceof String value && !value.isBlank() && value.length() <= 128) return value;
        throw new QueryException("INVALID_ARGUMENTS", "Invalid " + key);
    }

    private static OffsetDateTime utc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
