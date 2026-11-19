package io.devicetruth.adapter.persistence;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.runtime.AgentExecutionCheckpoint;
import io.devicetruth.application.agent.GeneralAgentService.RunView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.contract.AgentResult;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput.PendingQuestion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 通用 Agent 任务的状态机与审计：
 * QUEUED → RUNNING →（WAITING_FOR_USER → QUEUED → RUNNING）→ COMPLETED / FAILED。
 * 每次状态迁移都是带前置状态与版本号的条件更新，另一个 worker 无法重复接手同一个 run。
 * 引用仍由知识工具在检索时按 run 登记，这里只做读取与授权过滤。
 */
public final class AgentRunRepository {
    /** 运行记录：主循环需要的全部持久事实，均由 PG 重建，不依赖原 Java 线程。 */
    public record RunRecord(UUID runId, UUID conversationId, UUID goalMessageId, String status, long version,
                            Instant requestedAt, Instant asOf, UUID pendingQuestionId, int questionVersion,
                            String selectedAssetId, String assetResolution, String changedFromAssetId,
                            String goalText, int stepsUsed, int maxSteps, int attempts,
                            long activeDurationMs, long maxActiveDurationMs, boolean cancelRequested) {}
    public record ToolEvent(int sequence, String toolName, boolean success, String errorCode, int referenceCount,
                            long durationMs, boolean retryable) {}
    /** 恢复扫描命中的遗留 RUNNING：只带对账需要的字段，不重建完整运行上下文。 */
    public record ExpiredRun(UUID runId, UUID conversationId, long version, int attempts) {}

    private static final String VIEW_SQL = """
            SELECT r.id, r.conversation_id, r.goal_message_id, r.status, r.version, r.requested_at, r.as_of,
                   COALESCE(r.subject_asset_id, m.selected_asset_id) AS investigated_asset_id,
                   CASE WHEN r.subject_asset_id IS NULL AND m.selected_asset_id IS NOT NULL
                             AND r.subject_status = 'UNCONFIRMED'
                        THEN 'SELECTED' ELSE r.subject_status END AS asset_resolution,
                   r.subject_changed_from,
                   r.pending_question, r.active_duration_ms, r.budget->>'usageStatus' AS usage_status,
                   (r.budget->'used'->>'inputTokens')::bigint AS input_tokens,
                   (r.budget->'used'->>'outputTokens')::bigint AS output_tokens,
                   (r.budget->'used'->>'cost')::numeric AS cost,
                   (SELECT e.safe_payload FROM dt_run_event e
                     WHERE e.run_id = r.id AND e.type = 'RUN_ANSWER' ORDER BY e.seq DESC LIMIT 1) AS answer_payload,
                   (SELECT e.safe_payload->>'code' FROM dt_run_event e
                     WHERE e.run_id = r.id AND e.type = 'RUN_FAILED' ORDER BY e.seq DESC LIMIT 1) AS failure_code
            FROM dt_agent_run r
            LEFT JOIN dt_message m ON m.id = r.goal_message_id
            WHERE r.owner_id = ? AND r.project_id = ?
            """;

    private static final RowMapper<RunView> VIEW_MAPPER = (r, n) -> new RunView(
            r.getObject("id", UUID.class), r.getObject("conversation_id", UUID.class),
            r.getObject("goal_message_id", UUID.class), r.getString("status"), r.getLong("version"),
            r.getTimestamp("requested_at").toInstant(), r.getTimestamp("as_of").toInstant(),
            r.getString("investigated_asset_id"), r.getString("asset_resolution"), r.getString("subject_changed_from"),
            "SIMULATED", pendingQuestion(r), answer(r), r.getString("failure_code"),
            r.getLong("active_duration_ms"), r.getString("usage_status"),
            r.getObject("input_tokens", Long.class), r.getObject("output_tokens", Long.class),
            r.getBigDecimal("cost"));

    private static PendingQuestion pendingQuestion(ResultSet row) throws SQLException {
        String payload = row.getString("pending_question");
        if (payload == null) return null;
        try {
            return Json.MAPPER.readValue(payload, PendingQuestion.class);
        } catch (Exception e) {
            throw new SQLException("Invalid pending question payload", e);
        }
    }

    private static AgentResult answer(ResultSet row) throws SQLException {
        String payload = row.getString("answer_payload");
        if (payload == null) return null;
        try {
            return Json.MAPPER.readValue(payload, AgentResult.class);
        } catch (Exception e) {
            throw new SQLException("Invalid answer payload", e);
        }
    }

    private final JdbcTemplate jdbc;
    /** 总时限（累计执行时长上限，毫秒）：入队时写入 budget.limits.maxActiveDurationMs，恢复不清零。 */
    private final long maxActiveDurationMs;
    /** 恢复状态与事件原子提交，事件序号分配由同一事务中的 run 行锁保护。 */
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public AgentRunRepository(JdbcTemplate jdbc) { this(jdbc, 120_000); }

    public AgentRunRepository(JdbcTemplate jdbc, long maxActiveDurationMs) {
        this(jdbc, maxActiveDurationMs, new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource())));
    }

    public AgentRunRepository(JdbcTemplate jdbc, long maxActiveDurationMs,
                              org.springframework.transaction.support.TransactionTemplate transactions) {
        this.jdbc = jdbc;
        if (maxActiveDurationMs < 1) throw new IllegalArgumentException("maxActiveDurationMs must be positive");
        this.maxActiveDurationMs = maxActiveDurationMs;
        this.transactions = java.util.Objects.requireNonNull(transactions, "transactions");
    }

    public RunView enqueue(ServerIdentity identity, UUID conversationId, UUID messageId, Instant now, Instant asOf) {
        UUID runId = UUID.randomUUID();
        String budget = "{\"limits\":{\"maxSteps\":12,\"maxRepeatedActions\":2,\"maxActiveDurationMs\":"
                + maxActiveDurationMs + ",\"maxTokens\":null,\"maxCost\":null},"
                + "\"used\":{\"steps\":0,\"modelAttempts\":0,\"toolCalls\":0,\"activeDurationMs\":0,"
                + "\"inputTokens\":null,\"outputTokens\":null,\"cost\":null},\"usageStatus\":\"UNKNOWN\"}";
        jdbc.update("""
                INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,
                    requested_at,as_of,budget,created_at,updated_at,subject_asset_id,subject_status)
                SELECT ?,?,?,?,?,'QUEUED',?,?,CAST(? AS jsonb),?,?,m.selected_asset_id,
                    CASE WHEN m.selected_asset_id IS NULL THEN 'UNCONFIRMED' ELSE 'SELECTED' END
                FROM dt_message m WHERE m.id = ?
                """, runId, conversationId, identity.ownerId(), identity.projectId(), messageId,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(asOf), budget,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), messageId);
        appendEvent(identity, runId, conversationId, "RUN_QUEUED", Map.of("status", "QUEUED"), now);
        return require(identity, runId);    }

    public RunView require(ServerIdentity identity, UUID id) {
        var rows = jdbc.query(VIEW_SQL + " AND r.id = ?", VIEW_MAPPER, identity.ownerId(), identity.projectId(), id);
        if (rows.isEmpty()) throw io.devicetruth.application.agent.GeneralAgentService.denied();
        return rows.getFirst();
    }

    public List<RunView> list(ServerIdentity identity, UUID conversationId) {
        return jdbc.query(VIEW_SQL + " AND r.conversation_id = ? ORDER BY r.created_at, r.id", VIEW_MAPPER,
                identity.ownerId(), identity.projectId(), conversationId);
    }

    /** 启动扫描：取本身份的全部 QUEUED 任务（不设条数上限，遗留超过一页也不能变成幽灵任务），供 M0 队列交接；RUNNING 遗留留给 M2 恢复。 */
    public List<UUID> queued(ServerIdentity identity) {
        return jdbc.queryForList("""
                SELECT id FROM dt_agent_run
                WHERE owner_id=? AND project_id=? AND status='QUEUED'
                ORDER BY created_at, id
                """, UUID.class, identity.ownerId(), identity.projectId());
    }

    /** 周期补派扫描：同一语义的有界候选（按 created_at, id 排序，LIMIT ?），防止周期任务无限全扫。 */
    public List<UUID> queuedPage(ServerIdentity identity, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM dt_agent_run
                WHERE owner_id=? AND project_id=? AND status='QUEUED'
                ORDER BY created_at, id LIMIT ?
                """, UUID.class, identity.ownerId(), identity.projectId(), limit);
    }

    /** 读取运行记录；lock=true 时加行锁，供补答事务串行消费同一个待答问题。 */
    public RunRecord load(ServerIdentity identity, UUID runId, boolean lock) {
        var rows = jdbc.query("""
                SELECT r.id, r.conversation_id, r.goal_message_id, r.status, r.version, r.requested_at, r.as_of,
                       r.pending_question_id, r.question_version,
                       (r.budget->'used'->>'steps')::int AS steps_used,
                       (r.budget->'limits'->>'maxSteps')::int AS max_steps,
                       COALESCE(r.subject_asset_id, m.selected_asset_id) AS selected_asset_id,
                       CASE WHEN r.subject_asset_id IS NULL AND m.selected_asset_id IS NOT NULL
                                 AND r.subject_status = 'UNCONFIRMED'
                            THEN 'SELECTED' ELSE r.subject_status END AS asset_resolution,
                       r.subject_changed_from, m.content AS goal_text,
                       r.attempts, r.active_duration_ms,
                       COALESCE((r.budget->'limits'->>'maxActiveDurationMs')::bigint, 0) AS max_active_duration_ms,
                       r.cancel_requested_at IS NOT NULL AS cancel_requested
                FROM dt_agent_run r
                JOIN dt_message m ON m.id = r.goal_message_id
                WHERE r.id = ? AND r.owner_id = ? AND r.project_id = ?
                """ + (lock ? " FOR NO KEY UPDATE OF r" : ""),
                (r, n) -> new RunRecord(r.getObject("id", UUID.class), r.getObject("conversation_id", UUID.class),
                        r.getObject("goal_message_id", UUID.class), r.getString("status"), r.getLong("version"),
                        r.getTimestamp("requested_at").toInstant(), r.getTimestamp("as_of").toInstant(),
                        r.getObject("pending_question_id", UUID.class), r.getInt("question_version"),
                        r.getString("selected_asset_id"), r.getString("asset_resolution"), r.getString("subject_changed_from"),
                        r.getString("goal_text"), r.getInt("steps_used"), r.getInt("max_steps"), r.getInt("attempts"),
                        r.getLong("active_duration_ms"), r.getLong("max_active_duration_ms"),
                        r.getBoolean("cancel_requested")),
                runId, identity.ownerId(), identity.projectId());
        if (rows.isEmpty()) throw io.devicetruth.application.agent.GeneralAgentService.denied();
        return rows.getFirst();
    }

    /**
     * QUEUED → RUNNING：领取执行租约并累计尝试次数。
     * 已被其他 worker 接手（状态或版本变化）时返回 0，调用方必须放弃执行；
     * 租约到期前其他进程不会回收这个 RUNNING，防双 worker 重复推进。
     * started_at 只记第一段执行的开始（COALESCE），恢复重跑不清零已累计的时长与步数。
     */
    public int markRunning(ServerIdentity identity, RunRecord run, String leaseOwner, Instant leaseExpiresAt,
                           Instant now) {
        return update("""
                UPDATE dt_agent_run SET status='RUNNING', version=version+1, updated_at=?,
                    lease_owner=?, lease_expires_at=?, lease_heartbeat_at=?, attempts=attempts+1,
                    started_at=COALESCE(started_at, ?), active_accounted_at=?
                WHERE id=? AND owner_id=? AND project_id=? AND status='QUEUED' AND version=?
                """, java.sql.Timestamp.from(now), leaseOwner, java.sql.Timestamp.from(leaseExpiresAt),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), run.runId(),
                identity.ownerId(), identity.projectId(), run.version());
    }

    /** 运行中续租：只有持有者且仍处于 RUNNING 时才生效，worker 崩溃后租约自然过期。 */
    public int heartbeat(ServerIdentity identity, UUID runId, String leaseOwner, long version,
                         Instant leaseExpiresAt, Instant now) {
        return jdbc.update("""
                UPDATE dt_agent_run SET lease_expires_at=?, lease_heartbeat_at=?
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING' AND lease_owner=?
                  AND version=? AND lease_expires_at > clock_timestamp()
                """, java.sql.Timestamp.from(leaseExpiresAt), java.sql.Timestamp.from(now),
                runId, identity.ownerId(), identity.projectId(), leaseOwner, version);
    }

    /** 恢复扫描：租约已过期（或从未持租约的旧版遗留）的 RUNNING，供启动对账回收。 */
    public List<ExpiredRun> runningWithExpiredLease(ServerIdentity identity, Instant now) {
        return jdbc.query("""
                SELECT id, conversation_id, version, attempts FROM dt_agent_run
                WHERE owner_id=? AND project_id=? AND status='RUNNING'
                  AND (lease_expires_at IS NULL OR lease_expires_at < ?)
                ORDER BY created_at, id
                """, (r, n) -> new ExpiredRun(r.getObject("id", UUID.class), r.getObject("conversation_id", UUID.class),
                        r.getLong("version"), r.getInt("attempts")),
                identity.ownerId(), identity.projectId(), java.sql.Timestamp.from(now));
    }

    private static final String RECOVERY_ACTIVE_MS = """
            active_duration_ms + COALESCE(GREATEST(0,
                floor(extract(epoch FROM (lease_expires_at - active_accounted_at)) * 1000)::bigint), 0)
            """;

    /** RUNNING → QUEUED：仅当版本未变且租约仍处过期状态时回收，成功才允许重新调度。
     *  已发布事件保持不变；回收事件与状态变更同事务提交。 */
    public int requeueRecovered(ServerIdentity identity, ExpiredRun run, Instant now) {
        return transactions.execute(status -> requeueRecoveredAtomic(identity, run, now));
    }

    private int requeueRecoveredAtomic(ServerIdentity identity, ExpiredRun run, Instant now) {
        int updated = update("""
                UPDATE dt_agent_run SET status='QUEUED', version=version+1, updated_at=?,
                    active_duration_ms=%1$s,
                    budget=jsonb_set(budget,'{used,activeDurationMs}',to_jsonb(%1$s),true),
                    active_accounted_at=NULL, lease_owner=NULL, lease_expires_at=NULL, lease_heartbeat_at=NULL
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING' AND version=?
                  AND (lease_expires_at IS NULL OR lease_expires_at < ?)
                """.formatted(RECOVERY_ACTIVE_MS), java.sql.Timestamp.from(now), run.runId(), identity.ownerId(),
                identity.projectId(), run.version(), java.sql.Timestamp.from(now));
        if (updated == 1) {
            appendEvent(identity, run.runId(), run.conversationId(), "RUN_RECOVERED",
                    Map.of("attempts", run.attempts()), now);
        }
        return updated;
    }

    /** 遗留 RUNNING 无法安全续跑时明确失败：同样以租约过期+版本 CAS 兜底，绝不留下永久 RUNNING。 */
    public int failRecovered(ServerIdentity identity, ExpiredRun run, String code, Instant now) {
        return transactions.execute(status -> failRecoveredAtomic(identity, run, code, now));
    }

    private int failRecoveredAtomic(ServerIdentity identity, ExpiredRun run, String code, Instant now) {
        int updated = update("""
                UPDATE dt_agent_run SET status='FAILED', version=version+1, updated_at=?,
                    lease_owner=NULL, lease_expires_at=NULL, lease_heartbeat_at=NULL
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING' AND version=?
                  AND (lease_expires_at IS NULL OR lease_expires_at < ?)
                """, java.sql.Timestamp.from(now), run.runId(), identity.ownerId(), identity.projectId(),
                run.version(), java.sql.Timestamp.from(now));
        if (updated == 1)
            appendEvent(identity, run.runId(), run.conversationId(), "RUN_FAILED", Map.of("code", code), now);
        return updated;
    }

    /** 终态统一回写：累计步数、执行时长、模型尝试与工具调用计数；Token/成本只在供应商真实返回时写。 */
    private static final String USAGE_SET_SQL = "budget=jsonb_set(jsonb_set(jsonb_set(jsonb_set("
            + "budget,'{used,steps}',to_jsonb(?::int),true),"
            + "'{used,activeDurationMs}',to_jsonb(active_duration_ms+?::bigint),true),"
            + "'{used,modelAttempts}',to_jsonb(COALESCE((budget#>>'{used,modelAttempts}')::int,0)+?::int),true),"
            + "'{used,toolCalls}',to_jsonb(COALESCE((budget#>>'{used,toolCalls}')::int,0)+?::int),true)";

    /** 同一事务内从本 run 的供应商审计汇总。混合缺失时显示已报告小计，标记 PARTIAL。 */
    public void updateModelUsage(ServerIdentity identity, UUID runId) {
        jdbc.update("""
                WITH usage AS (
                    SELECT count(*) AS attempts,
                           count(*) FILTER (WHERE safe_payload->>'inputTokens' IS NOT NULL
                                                  AND safe_payload->>'outputTokens' IS NOT NULL) AS token_reports,
                           count(safe_payload->>'cost') AS cost_reports,
                           sum((safe_payload->>'inputTokens')::bigint) AS input_tokens,
                           sum((safe_payload->>'outputTokens')::bigint) AS output_tokens,
                           sum((safe_payload->>'cost')::numeric) AS cost
                    FROM dt_run_event
                    WHERE run_id=? AND owner_id=? AND project_id=? AND type='RUN_MODEL'
                )
                UPDATE dt_agent_run r SET budget=jsonb_set(
                    jsonb_set(r.budget,'{used}',r.budget->'used' || jsonb_build_object(
                        'modelAttempts',usage.attempts,'inputTokens',usage.input_tokens,
                        'outputTokens',usage.output_tokens,'cost',usage.cost),true),
                    '{usageStatus}',to_jsonb((CASE
                        WHEN usage.token_reports=usage.attempts AND usage.cost_reports IN (0,usage.attempts)
                        THEN 'REPORTED' WHEN usage.input_tokens IS NULL AND usage.cost IS NULL
                        THEN 'UNKNOWN' ELSE 'PARTIAL' END)::text),true)
                FROM usage
                WHERE r.id=? AND r.owner_id=? AND r.project_id=?
                  AND usage.attempts > 0
                """, runId, identity.ownerId(), identity.projectId(), runId, identity.ownerId(), identity.projectId());
    }

    /** RUNNING → WAITING_FOR_USER：问题、版本、累计步数与引用计数同一事务写入后释放 worker。 */
    public int waitForUser(ServerIdentity identity, RunRecord run, PendingQuestion question, int stepsUsed,
                           int referenceCount, long activeDurationMs, int modelAttempts, int toolCalls,
                           Instant now) {
        String payload;
        try {
            payload = Json.write(question);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cannot encode pending question", e);
        }
        return update("""
                UPDATE dt_agent_run SET status='WAITING_FOR_USER', version=version+1, updated_at=?,
                    pending_question_id=?, question_version=?, pending_question=CAST(? AS jsonb),
                    context_summary=?, %s,
                    active_duration_ms=active_duration_ms+?, finished_at=?,
                    lease_owner=NULL, lease_expires_at=NULL, lease_heartbeat_at=NULL
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING' AND version=?
                """.formatted(USAGE_SET_SQL), java.sql.Timestamp.from(now), question.pendingQuestionId(),
                question.questionVersion(), payload,
                "steps=" + stepsUsed + ";references=" + referenceCount, stepsUsed, activeDurationMs,
                modelAttempts, toolCalls, activeDurationMs, java.sql.Timestamp.from(now),
                run.runId(), identity.ownerId(), identity.projectId(), run.version());
    }

    /** RUNNING → COMPLETED：答案写入 RUN_ANSWER 事件，界面与报告都从持久事件读回。 */
    public int complete(ServerIdentity identity, RunRecord run, AgentResult answer, int stepsUsed,
                        long activeDurationMs, int modelAttempts, int toolCalls, Instant now) {
        int updated = update("""
                UPDATE dt_agent_run SET status='COMPLETED', version=version+1, updated_at=?,
                    subject_changed_from=CASE WHEN ?::varchar IS NOT NULL AND subject_asset_id IS DISTINCT FROM ?::varchar
                        THEN subject_asset_id ELSE subject_changed_from END,
                    subject_asset_id=COALESCE(?::varchar, subject_asset_id),
                    subject_status=COALESCE(?::varchar, subject_status),
                    %s,
                    active_duration_ms=active_duration_ms+?, finished_at=?,
                    lease_owner=NULL, lease_expires_at=NULL, lease_heartbeat_at=NULL
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING' AND version=?
                """.formatted(USAGE_SET_SQL), java.sql.Timestamp.from(now),
                answer.subjectAssetId(), answer.subjectAssetId(), answer.subjectAssetId(), answer.assetResolution(),
                stepsUsed, activeDurationMs, modelAttempts, toolCalls, activeDurationMs, java.sql.Timestamp.from(now),
                run.runId(), identity.ownerId(), identity.projectId(), run.version());
        if (updated == 1) appendEvent(identity, run.runId(), run.conversationId(), "RUN_ANSWER", answer, now);
        return updated;
    }

    /** 任意非终态 → FAILED：技术失败没有答案，只保留稳定原因码。 */
    public int fail(ServerIdentity identity, RunRecord run, String code, int stepsUsed,
                    long activeDurationMs, int modelAttempts, int toolCalls, Instant now) {
        int updated = update("""
                UPDATE dt_agent_run SET status='FAILED', version=version+1, updated_at=?,
                    %s,
                    active_duration_ms=active_duration_ms+?, finished_at=?,
                    lease_owner=NULL, lease_expires_at=NULL, lease_heartbeat_at=NULL
                WHERE id=? AND owner_id=? AND project_id=? AND status IN ('QUEUED','RUNNING') AND version=?
                """.formatted(USAGE_SET_SQL), java.sql.Timestamp.from(now), stepsUsed, activeDurationMs,
                modelAttempts, toolCalls, activeDurationMs, java.sql.Timestamp.from(now),
                run.runId(), identity.ownerId(), identity.projectId(), run.version());
        if (updated == 1)
            appendEvent(identity, run.runId(), run.conversationId(), "RUN_FAILED", Map.of("code", code), now);
        return updated;
    }

    /**
     * 取消请求（03-03）：QUEUED/WAITING_FOR_USER 不持执行租约，立即迁移终态 CANCELLED；
     * RUNNING 只落取消标记，由持有租约的 worker 在每步之间协作式收尾，迟到响应只记录不提交。
     * 返回 1 表示请求已被接受（立即终态或已打标），0 表示 run 不存在或已是终态。
     */
    public int requestCancel(ServerIdentity identity, UUID runId, Instant now) {
        UUID conversationId = jdbc.query("""
                SELECT conversation_id FROM dt_agent_run
                WHERE id=? AND owner_id=? AND project_id=?
                """, (r, n) -> r.getObject("conversation_id", UUID.class),
                runId, identity.ownerId(), identity.projectId()).stream().findFirst().orElse(null);
        if (conversationId == null) return 0;
        int immediate = update("""
                UPDATE dt_agent_run SET status='CANCELLED', version=version+1, updated_at=?,
                    cancel_requested_at=?, finished_at=?,
                    pending_question_id=NULL, question_version=0, pending_question=NULL,
                    lease_owner=NULL, lease_expires_at=NULL, lease_heartbeat_at=NULL
                WHERE id=? AND owner_id=? AND project_id=? AND status IN ('QUEUED','WAITING_FOR_USER')
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now),
                runId, identity.ownerId(), identity.projectId());
        if (immediate == 1) {
            appendEvent(identity, runId, conversationId, "RUN_CANCELLED", Map.of("phase", "IMMEDIATE"), now);
            return 1;
        }
        return update("""
                UPDATE dt_agent_run SET cancel_requested_at=?, updated_at=?
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING' AND cancel_requested_at IS NULL
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now),
                runId, identity.ownerId(), identity.projectId());
    }

    /** RUNNING → CANCELLED：仅响应已打取消标记的运行，由 worker 收尾；终态 CAS 防迟到响应覆盖。 */
    public int cancelRunning(ServerIdentity identity, RunRecord run, int stepsUsed, long activeDurationMs,
                             int modelAttempts, int toolCalls, Instant now) {
        int updated = update("""
                UPDATE dt_agent_run SET status='CANCELLED', version=version+1, updated_at=?,
                    %s,
                    active_duration_ms=active_duration_ms+?, finished_at=?,
                    lease_owner=NULL, lease_expires_at=NULL, lease_heartbeat_at=NULL
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING' AND cancel_requested_at IS NOT NULL
                  AND version=?
                """.formatted(USAGE_SET_SQL), java.sql.Timestamp.from(now), stepsUsed, activeDurationMs,
                modelAttempts, toolCalls, activeDurationMs, java.sql.Timestamp.from(now),
                run.runId(), identity.ownerId(), identity.projectId(), run.version());
        if (updated == 1)
            appendEvent(identity, run.runId(), run.conversationId(), "RUN_CANCELLED", Map.of("phase", "WORKER"), now);
        return updated;
    }

    /** 每步核验 worker 仍持原版本的执行租约，防止已回收任务继续执行后续动作。 */
    public String claimStopCode(ServerIdentity identity, UUID runId, long version) {
        var rows = jdbc.query("""
                SELECT cancel_requested_at IS NOT NULL FROM dt_agent_run
                WHERE id=? AND owner_id=? AND project_id=?
                  AND status='RUNNING' AND version=? AND lease_expires_at > clock_timestamp()
                """, (r, n) -> r.getBoolean(1), runId, identity.ownerId(), identity.projectId(), version);
        if (rows.isEmpty()) return "RUN_CLAIM_LOST";
        return rows.getFirst() ? "CANCELLED" : null;
    }

    /** 补答中的结构化设备选择与待答消费同事务提交，版本仍由 queueReply 统一推进。 */
    public void confirmSelectedSubject(ServerIdentity identity, UUID runId, String assetId, String previousAssetId) {
        jdbc.update("""
                UPDATE dt_agent_run SET subject_changed_from=?, subject_asset_id=?, subject_status='CONFIRMED'
                WHERE id=? AND owner_id=? AND project_id=?
                """, previousAssetId, assetId, runId, identity.ownerId(), identity.projectId());
    }

    /** WAITING_FOR_USER → QUEUED：只有问题 ID 与版本都匹配且尚未被消费时才成功，防止重复补答。 */
    public int queueReply(ServerIdentity identity, RunRecord run, UUID pendingQuestionId, int questionVersion, Instant now) {
        return update("""
                UPDATE dt_agent_run SET status='QUEUED', version=version+1, updated_at=?,
                    pending_question_id=NULL, pending_question=NULL
                WHERE id=? AND owner_id=? AND project_id=? AND status='WAITING_FOR_USER'
                  AND pending_question_id=? AND question_version=?
                """, java.sql.Timestamp.from(now), run.runId(), identity.ownerId(), identity.projectId(),
                pendingQuestionId, questionVersion);
    }

    /**
     * 本 run 已成功取回的可引用事实：
     * 知识引用联查文档/切片取标题与原文；配置类结构化事实（03-01）没有切片表，直接从引用行还原
     * 来源、版本与摘要，两个来源的归属条件相同，均在 SQL 内按 run/owner/project 过滤。
     */
    public List<Reference> references(ServerIdentity identity, UUID runId) {
        return jdbc.query("""
                SELECT r.id AS reference_id, r.source_type, r.source_id, r.source_version,
                       COALESCE(r.chunk_id, r.id) AS chunk_id,
                       r.citation_page, r.citation_section, r.data_nature,
                       COALESCE(d.title, r.citation_section, r.source_type) AS title,
                       CASE WHEN c.id IS NULL THEN COALESCE(r.citation_excerpt, '')
                            ELSE COALESCE(r.citation_excerpt, left(c.content, 600)) END AS content
                FROM dt_evidence_reference r
                LEFT JOIN dt_knowledge_chunk c
                  ON c.id = r.chunk_id AND c.owner_id = r.owner_id AND c.project_id = r.project_id
                LEFT JOIN dt_knowledge_document d
                  ON d.id = c.document_id AND d.owner_id = c.owner_id AND d.project_id = c.project_id
                WHERE r.run_id = ? AND r.owner_id = ? AND r.project_id = ?
                  AND r.source_type IN ('KNOWLEDGE_DOCUMENT', 'CONFIGURATION', 'FIELD_OBSERVATION')
                ORDER BY r.retrieved_at, r.id
                """, (r, n) -> new Reference(r.getObject("reference_id", UUID.class).toString(),
                r.getString("source_type"), r.getString("source_id"), r.getString("source_version"),
                r.getObject("chunk_id", UUID.class).toString(), r.getString("title"),
                r.getObject("citation_page", Integer.class), r.getString("citation_section"),
                "KNOWLEDGE_DOCUMENT".equals(r.getString("source_type"))
                        ? truncate(r.getString("content"), 2000) : r.getString("content"),
                r.getString("data_nature")),
                runId, identity.ownerId(), identity.projectId());
    }

    public List<ToolEvent> toolEvents(ServerIdentity identity, UUID runId) {
        return jdbc.query("""
                SELECT seq,safe_payload->>'toolName' AS tool_name,
                       (safe_payload->>'success')::boolean AS success,
                       safe_payload->>'errorCode' AS error_code,
                       COALESCE((safe_payload->>'references')::int,0) AS reference_count,
                       COALESCE((safe_payload->>'durationMs')::bigint,0) AS duration_ms,
                       COALESCE((safe_payload->>'retryable')::boolean,false) AS retryable
                FROM dt_run_event
                WHERE run_id=? AND owner_id=? AND project_id=? AND type='RUN_TOOL'
                ORDER BY seq
                """, (r, n) -> new ToolEvent(r.getInt("seq"), r.getString("tool_name"),
                r.getBoolean("success"), r.getString("error_code"), r.getInt("reference_count"),
                r.getLong("duration_ms"), r.getBoolean("retryable")),
                runId, identity.ownerId(), identity.projectId());
    }

    /** 模型尝试审计（03-03）：每步的重试次数、结果码、单次耗时与模型版本，白名单字段不落 Prompt/密钥。 */
    public record ModelEvent(int sequence, String model, int attempt, String outcome, long durationMs,
                             boolean retryable, Long inputTokens, Long outputTokens, java.math.BigDecimal cost) {}

    public List<ModelEvent> modelEvents(ServerIdentity identity, UUID runId) {
        return jdbc.query("""
                SELECT seq, safe_payload->>'model' AS model,
                       COALESCE((safe_payload->>'attempt')::int,0) AS attempt,
                       COALESCE(safe_payload->>'outcome','') AS outcome,
                       COALESCE((safe_payload->>'durationMs')::bigint,0) AS duration_ms,
                       COALESCE((safe_payload->>'retryable')::boolean,false) AS retryable,
                       (safe_payload->>'inputTokens')::bigint AS input_tokens,
                       (safe_payload->>'outputTokens')::bigint AS output_tokens,
                       (safe_payload->>'cost')::numeric AS cost
                FROM dt_run_event
                WHERE run_id=? AND owner_id=? AND project_id=? AND type='RUN_MODEL'
                ORDER BY seq
                """, (r, n) -> new ModelEvent(r.getInt("seq"), r.getString("model"), r.getInt("attempt"),
                r.getString("outcome"), r.getLong("duration_ms"), r.getBoolean("retryable"),
                r.getObject("input_tokens", Long.class), r.getObject("output_tokens", Long.class), r.getBigDecimal("cost")),
                runId, identity.ownerId(), identity.projectId());
    }

    /** 单次查询批大小与单次调用硬上限：超出分批读取，防止一次性拉爆内存。 */
    private static final int EVENT_BATCH = 500;
    private static final int EVENT_HARD_LIMIT = 5000;

    private static final RowMapper<io.devicetruth.application.agent.SafeAgentEventViews.PublicEvent> EVENT_MAPPER =
            (r, n) -> io.devicetruth.application.agent.SafeAgentEventViews.fromPayload(
                    r.getLong("seq"), r.getString("type"), r.getTimestamp("recorded_at").toInstant(),
                    payload(r.getString("safe_payload")));

    /** jsonb 文本解析为字段 Map；解析失败按空载荷处理（公开视图退化为空 data，不猜测内容）。 */
    private static Map<String, Object> payload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return Json.MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 公开事件流：seq > afterSeq 按升序返回，limit 有界（超过内部批大小分批查询）。 */
    public List<io.devicetruth.application.agent.SafeAgentEventViews.PublicEvent> eventsAfter(
            ServerIdentity identity, UUID runId, long afterSeq, int limit) {
        if (limit < 1) return List.of();
        int capped = Math.min(limit, EVENT_HARD_LIMIT);
        var events = new java.util.ArrayList<io.devicetruth.application.agent.SafeAgentEventViews.PublicEvent>();
        long cursor = afterSeq;
        while (events.size() < capped) {
            int batchSize = Math.min(EVENT_BATCH, capped - events.size());
            var batch = jdbc.query("""
                    SELECT seq, type, recorded_at, safe_payload::text AS safe_payload FROM dt_run_event
                    WHERE run_id=? AND owner_id=? AND project_id=? AND seq>?
                    ORDER BY seq LIMIT ?
                    """, EVENT_MAPPER, runId, identity.ownerId(), identity.projectId(), cursor, batchSize);
            if (batch.isEmpty()) break;
            events.addAll(batch);
            cursor = batch.getLast().seq();
            if (batch.size() < batchSize) break;
        }
        return List.copyOf(events);
    }

    /** 本 run 当前最大事件序号；无事件返回 0。 */
    public long latestSeq(ServerIdentity identity, UUID runId) {
        Long latest = jdbc.queryForObject("""
                SELECT COALESCE(MAX(seq),0) FROM dt_run_event
                WHERE run_id=? AND owner_id=? AND project_id=?
                """, Long.class, runId, identity.ownerId(), identity.projectId());
        return latest == null ? 0 : latest;
    }

    /** 把同归属的历史证据登记到当前 run；不存在或跨权统一拒绝，返回的新 ID 才能用于本次回答。 */
    public UUID importReference(ServerIdentity identity, UUID sourceReferenceId, UUID targetRunId) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO dt_evidence_reference(id,run_id,conversation_id,owner_id,project_id,source_type,
                        source_id,source_version,chunk_id,asset_id,as_of,citation_page,citation_section,
                        retrieved_at,data_nature)
                    SELECT ?,target.id,target.conversation_id,target.owner_id,target.project_id,source.source_type,
                        source.source_id,source.source_version,source.chunk_id,source.asset_id,source.as_of,
                        source.citation_page,source.citation_section,?,source.data_nature
                    FROM dt_agent_run target
                    JOIN dt_evidence_reference source ON source.owner_id=target.owner_id
                      AND source.project_id=target.project_id
                    WHERE target.id=? AND target.owner_id=? AND target.project_id=? AND source.id=?
                    ON CONFLICT (run_id,owner_id,project_id,chunk_id) DO UPDATE
                    SET retrieved_at=EXCLUDED.retrieved_at
                    RETURNING id
                    """, UUID.class, UUID.randomUUID(), java.sql.Timestamp.from(Instant.now()), targetRunId,
                    identity.ownerId(), identity.projectId(), sourceReferenceId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw io.devicetruth.application.agent.GeneralAgentService.denied();
        }
    }

    public boolean lockClaim(ServerIdentity identity, UUID runId, long version, int attempt, String workerId) {
        return !jdbc.query("""
                SELECT id FROM dt_agent_run WHERE id=? AND owner_id=? AND project_id=?
                  AND status='RUNNING' AND version=? AND attempts=? AND lease_owner=?
                  AND lease_expires_at > clock_timestamp() FOR NO KEY UPDATE
                """, (r, n) -> r.getObject(1), runId, identity.ownerId(), identity.projectId(),
                version, attempt, workerId).isEmpty();
    }

    public AgentExecutionCheckpoint checkpoint(ServerIdentity identity, UUID runId) {
        String payload = jdbc.queryForObject("""
                SELECT execution_checkpoint FROM dt_agent_run WHERE id=? AND owner_id=? AND project_id=?
                """, String.class, runId, identity.ownerId(), identity.projectId());
        if (payload == null) return null;
        try {
            return Json.MAPPER.readValue(payload, AgentExecutionCheckpoint.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored execution checkpoint", e);
        }
    }

    /** 预扣模型步数及最近的活动时间。调用方在同一事务中锁定当前领取记录。 */
    public int reserveStep(ServerIdentity identity, UUID runId, int step, long totalActiveMs) {
        return jdbc.update("""
                UPDATE dt_agent_run SET
                    budget=jsonb_set(jsonb_set(budget,'{used,steps}',to_jsonb(?::int),true),
                                    '{used,activeDurationMs}',to_jsonb(?::bigint),true),
                    active_duration_ms=?, active_accounted_at=clock_timestamp()
                WHERE id=? AND owner_id=? AND project_id=?
                  AND (budget#>>'{used,steps}')::int = ?-1
                  AND (budget#>>'{limits,maxSteps}')::int >= ?
                """, step, totalActiveMs, totalActiveMs, runId, identity.ownerId(), identity.projectId(), step, step);
    }

    public int saveCheckpoint(ServerIdentity identity, AgentExecutionCheckpoint checkpoint, long totalActiveMs,
                              boolean toolAttempt) {
        return jdbc.update("""
                UPDATE dt_agent_run SET execution_checkpoint=CAST(? AS jsonb),
                    budget=jsonb_set(jsonb_set(budget,'{used,activeDurationMs}',to_jsonb(?::bigint),true),
                                    '{used,toolCalls}',
                                    to_jsonb(COALESCE((budget#>>'{used,toolCalls}')::int,0)+?::int),true),
                    active_duration_ms=?, active_accounted_at=clock_timestamp()
                WHERE id=? AND owner_id=? AND project_id=?
                  AND (budget#>>'{used,steps}')::int = ?
                  AND COALESCE((execution_checkpoint->>'step')::int, -1) <= ?
                """, Json.write(checkpoint), totalActiveMs, toolAttempt ? 1 : 0, totalActiveMs, checkpoint.runId(),
                identity.ownerId(), identity.projectId(), checkpoint.step(), checkpoint.step());
    }

    /** 事件落库：缺省 attempt=0（生命周期事件，见 05-01 复验 §8.1）。 */
    public void appendEvent(ServerIdentity identity, UUID runId, UUID conversationId, String type,
                            Object payload, Instant now) {
        appendEvent(identity, runId, conversationId, type, payload, now, 0);
    }

    /** 公开事件只写白名单类型与逐字段构造的安全载荷；推理输入与原始模型输出永不落库。
     *  attempt 为事件归属的执行尝试号：步骤事件记 worker 领取 RUNNING 时 attempts+1 后的当前值。 */
    public void appendEvent(ServerIdentity identity, UUID runId, UUID conversationId, String type,
                            Object payload, Instant now, int attempt) {
        transactions.executeWithoutResult(
                status -> appendEventAtomic(identity, runId, conversationId, type, payload, now, attempt));
    }

    private void appendEventAtomic(ServerIdentity identity, UUID runId, UUID conversationId, String type,
                                   Object payload, Instant now, int attempt) {
        jdbc.queryForObject("SELECT id FROM dt_agent_run WHERE id=? AND owner_id=? AND project_id=? FOR NO KEY UPDATE",
                UUID.class, runId, identity.ownerId(), identity.projectId());
        jdbc.update("""
                INSERT INTO dt_run_event(run_id,seq,conversation_id,owner_id,project_id,type,recorded_at,safe_payload,attempt)
                SELECT ?, COALESCE(MAX(seq),0)+1, ?,?,?,?,?,CAST(? AS jsonb),? FROM dt_run_event WHERE run_id=?
                """, runId, conversationId, identity.ownerId(), identity.projectId(), type,
                java.sql.Timestamp.from(now), Json.write(payload), attempt, runId);
    }

    private int update(String sql, Object... arguments) { return jdbc.update(sql, arguments); }

    private static String truncate(String content, int max) {
        if (content == null) return "";
        return content.length() <= max ? content : content.substring(0, max);
    }
}
