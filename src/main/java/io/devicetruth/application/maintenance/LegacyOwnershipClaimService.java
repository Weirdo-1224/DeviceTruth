package io.devicetruth.application.maintenance;

import io.devicetruth.adapter.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class LegacyOwnershipClaimService {
    private final JdbcTemplate jdbc;
    private final DataSourceTransactionManager transactions;

    public LegacyOwnershipClaimService(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
        this.transactions = new DataSourceTransactionManager(source);
    }

    public record Request(String ownerId, String projectId, UUID adminUserId, String teamName, Path filesRoot) {}
    public record ActiveRun(UUID runId, UUID conversationId, String status, Instant leaseExpiresAt,
            boolean leaseExpired, UUID pendingQuestionId, int questionVersion,
            boolean unknownInitialActor, boolean unknownExecutionActor) {}
    public record FileCheck(UUID fileId, String pathKey, long expectedBytes, Long actualBytes,
            Boolean checksumMatches, String status) {}
    public record Manifest(String mode, String ownerId, String projectId, UUID adminUserId, String teamName,
            boolean valid, List<String> problems, Map<String, Long> tableCounts,
            Map<String, Long> unknownAuthorCounts, List<ActiveRun> activeRuns, List<FileCheck> files,
            UUID workspaceId, Instant claimedAt) {}

    public Manifest dryRun(Request request) {
        validate(request);
        var transaction = new TransactionTemplate(transactions);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionTemplate.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> inspect(request, "DRY_RUN", false));
    }

    public Manifest claim(Request request) {
        validate(request);
        return new TransactionTemplate(transactions).execute(status -> {
            Manifest checked = inspect(request, "EXECUTE", true);
            if (!checked.valid()) throw new IllegalStateException("CLAIM_PRECHECK_FAILED:" + checked.problems());
            UUID workspaceId = UUID.randomUUID();
            jdbc.update("INSERT INTO dt_workspace(id,kind,name,owner_id,project_id) VALUES (?,'TEAM',?,?,?)",
                    workspaceId, request.teamName().trim(), request.ownerId(), request.projectId());
            jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'ADMIN')",
                    workspaceId, request.adminUserId());
            int expected = checked.tableCounts().getOrDefault("dt_conversation", 0L).intValue();
            int updated = jdbc.update("UPDATE dt_conversation SET workspace_id=? WHERE owner_id=? AND project_id=? AND workspace_id IS NULL",
                    workspaceId, request.ownerId(), request.projectId());
            if (updated != expected) throw new IllegalStateException("CLAIM_CONCURRENT_CHANGE");
            jdbc.update("""
                    INSERT INTO dt_legacy_ownership_claim(id,workspace_id,owner_id,project_id,claimed_by_user_id,
                        table_counts,unknown_author_counts,active_runs,files)
                    VALUES (?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb))
                    """, UUID.randomUUID(), workspaceId, request.ownerId(), request.projectId(), request.adminUserId(),
                    Json.write(checked.tableCounts()), Json.write(checked.unknownAuthorCounts()),
                    Json.write(checked.activeRuns()), Json.write(checked.files()));
            Instant claimedAt = jdbc.queryForObject("SELECT claimed_at FROM dt_legacy_ownership_claim WHERE workspace_id=?",
                    (rs, row) -> rs.getTimestamp(1).toInstant(), workspaceId);
            return new Manifest("EXECUTE", checked.ownerId(), checked.projectId(), checked.adminUserId(), checked.teamName(),
                    true, List.of(), checked.tableCounts(), checked.unknownAuthorCounts(), checked.activeRuns(), checked.files(),
                    workspaceId, claimedAt);
        });
    }

    private Manifest inspect(Request request, String mode, boolean lock) {
        List<String> problems = new ArrayList<>();
        if (lock) jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?),hashtext(?))", rs -> { },
                request.ownerId(), request.projectId());
        boolean adminExists = lock
                ? !jdbc.queryForList("SELECT id FROM dt_account WHERE id=? FOR UPDATE", UUID.class, request.adminUserId()).isEmpty()
                : count("SELECT count(*) FROM dt_account WHERE id=?", request.adminUserId()) == 1;
        if (!adminExists) problems.add("ADMIN_NOT_FOUND");
        if (count("SELECT count(*) FROM dt_workspace WHERE owner_id=? AND project_id=?",
                request.ownerId(), request.projectId()) != 0) problems.add("STORAGE_IDENTITY_ALREADY_BOUND");
        if (count("SELECT count(*) FROM dt_legacy_ownership_claim WHERE owner_id=? AND project_id=?",
                request.ownerId(), request.projectId()) != 0) problems.add("ALREADY_CLAIMED");
        if (lock) jdbc.queryForList("SELECT id FROM dt_conversation WHERE owner_id=? AND project_id=? FOR UPDATE",
                UUID.class, request.ownerId(), request.projectId());
        if (count("SELECT count(*) FROM dt_conversation WHERE owner_id=? AND project_id=? AND workspace_id IS NOT NULL",
                request.ownerId(), request.projectId()) != 0) problems.add("CONVERSATION_ALREADY_CLAIMED");

        Map<String, Long> tableCounts = tableCounts(request);
        if (tableCounts.values().stream().mapToLong(Long::longValue).sum() == 0) problems.add("NO_LEGACY_RESOURCES");
        Map<String, Long> unknownAuthors = unknownAuthors(request);
        List<ActiveRun> activeRuns = activeRuns(request);
        List<FileCheck> files = fileChecks(request, problems);
        return new Manifest(mode, request.ownerId(), request.projectId(), request.adminUserId(), request.teamName().trim(),
                problems.isEmpty(), List.copyOf(problems), tableCounts, unknownAuthors, activeRuns, files, null, null);
    }

    private Map<String, Long> tableCounts(Request request) {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema='public' AND table_name LIKE 'dt\\_%' ESCAPE '\\'
                GROUP BY table_name
                HAVING count(*) FILTER (WHERE column_name='owner_id')=1
                   AND count(*) FILTER (WHERE column_name='project_id')=1
                ORDER BY table_name
                """, String.class);
        Map<String, Long> counts = new TreeMap<>();
        for (String table : tables) {
            if (table.equals("dt_workspace") || table.equals("dt_legacy_ownership_claim")) continue;
            counts.put(table, count("SELECT count(*) FROM \"" + table + "\" WHERE owner_id=? AND project_id=?",
                    request.ownerId(), request.projectId()));
        }
        counts.put("dt_run_skill_reference", count("""
                SELECT count(*) FROM dt_run_skill_reference ref
                JOIN dt_run_skill skill ON skill.run_id=ref.run_id
                WHERE skill.owner_id=? AND skill.project_id=?
                """, request.ownerId(), request.projectId()));
        return counts;
    }

    private Map<String, Long> unknownAuthors(Request request) {
        Map<String, Long> result = new TreeMap<>();
        Object[] identity = {request.ownerId(), request.projectId()};
        result.put("conversationCreators", count("SELECT count(*) FROM dt_conversation WHERE owner_id=? AND project_id=? AND created_by_user_id IS NULL", identity));
        result.put("messageActors", count("SELECT count(*) FROM dt_message WHERE owner_id=? AND project_id=? AND actor_user_id IS NULL", identity));
        result.put("runInitialActors", count("SELECT count(*) FROM dt_agent_run WHERE owner_id=? AND project_id=? AND initiated_by_user_id IS NULL", identity));
        result.put("runExecutionActors", count("SELECT count(*) FROM dt_agent_run WHERE owner_id=? AND project_id=? AND execution_actor_user_id IS NULL", identity));
        result.put("eventActors", count("SELECT count(*) FROM dt_run_event WHERE owner_id=? AND project_id=? AND actor_user_id IS NULL", identity));
        result.put("fileUploaders", count("SELECT count(*) FROM dt_uploaded_file WHERE owner_id=? AND project_id=? AND uploaded_by_user_id IS NULL", identity));
        result.put("inputSegmentActors", count("SELECT count(*) FROM dt_run_input_segment WHERE owner_id=? AND project_id=? AND actor_user_id IS NULL", identity));
        return result;
    }

    private List<ActiveRun> activeRuns(Request request) {
        return jdbc.query("""
                SELECT id,conversation_id,status,lease_expires_at,lease_expires_at<now() AS lease_expired,
                    pending_question_id,question_version,initiated_by_user_id IS NULL AS unknown_initial,
                    execution_actor_user_id IS NULL AS unknown_execution
                FROM dt_agent_run WHERE owner_id=? AND project_id=?
                    AND status IN ('QUEUED','RUNNING','WAITING_FOR_USER') ORDER BY created_at,id
                """, (rs, row) -> {
                Timestamp lease = rs.getTimestamp("lease_expires_at");
                return new ActiveRun(rs.getObject("id", UUID.class), rs.getObject("conversation_id", UUID.class),
                        rs.getString("status"), lease == null ? null : lease.toInstant(), rs.getBoolean("lease_expired"),
                        rs.getObject("pending_question_id", UUID.class), rs.getInt("question_version"),
                        rs.getBoolean("unknown_initial"), rs.getBoolean("unknown_execution"));
                }, request.ownerId(), request.projectId());
    }

    private List<FileCheck> fileChecks(Request request, List<String> problems) {
        Path root = request.filesRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) problems.add("FILES_ROOT_NOT_DIRECTORY");
        List<FileCheck> checks = new ArrayList<>();
        jdbc.query("SELECT id,path_key,checksum,byte_size FROM dt_uploaded_file WHERE owner_id=? AND project_id=? ORDER BY id",
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String pathKey = rs.getString("path_key");
                    long expectedBytes = rs.getLong("byte_size");
                    FileCheck check = checkFile(root, id, pathKey, rs.getString("checksum"), expectedBytes);
                    checks.add(check);
                    if (!check.status().equals("OK") && !problems.contains(check.status())) problems.add(check.status());
                }, request.ownerId(), request.projectId());
        return List.copyOf(checks);
    }

    private FileCheck checkFile(Path root, UUID id, String pathKey, String checksum, long expectedBytes) {
        try {
            Path resolved = root.resolve(pathKey).normalize();
            if (!resolved.startsWith(root)) return new FileCheck(id, pathKey, expectedBytes, null, null, "FILE_PATH_INVALID");
            if (!Files.isRegularFile(resolved)) return new FileCheck(id, pathKey, expectedBytes, null, null, "FILE_MISSING");
            Path realRoot = root.toRealPath();
            Path realFile = resolved.toRealPath();
            if (!realFile.startsWith(realRoot)) return new FileCheck(id, pathKey, expectedBytes, null, null, "FILE_PATH_INVALID");
            byte[] bytes = Files.readAllBytes(realFile);
            boolean sizeMatches = bytes.length == expectedBytes;
            boolean checksumMatches = sizeMatches && sha256(bytes).equals(checksum);
            String status = !sizeMatches ? "FILE_SIZE_MISMATCH" : checksumMatches ? "OK" : "FILE_CHECKSUM_MISMATCH";
            return new FileCheck(id, pathKey, expectedBytes, (long) bytes.length, checksumMatches, status);
        } catch (IOException e) {
            return new FileCheck(id, pathKey, expectedBytes, null, null, "FILE_UNREADABLE");
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static void validate(Request request) {
        if (request == null || request.ownerId() == null || request.ownerId().isBlank() || request.ownerId().length() > 128
                || request.projectId() == null || request.projectId().isBlank() || request.projectId().length() > 128
                || request.adminUserId() == null || request.teamName() == null || request.teamName().isBlank()
                || request.teamName().trim().length() > 100 || request.filesRoot() == null)
            throw new IllegalArgumentException("Invalid legacy ownership claim request");
    }
}
