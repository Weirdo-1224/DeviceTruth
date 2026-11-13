package io.devicetruth.adapter.persistence;

import io.devicetruth.adapter.Json;
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
                            String selectedAssetId, String goalText, int stepsUsed, int maxSteps) {}
    public record ToolEvent(int sequence, String toolName, boolean success, String errorCode, int referenceCount) {}

    private static final String VIEW_SQL = """
            SELECT r.id, r.conversation_id, r.goal_message_id, r.status, r.version, r.requested_at, r.as_of,
                   r.pending_question,
                   (SELECT e.safe_payload FROM dt_run_event e
                     WHERE e.run_id = r.id AND e.type = 'RUN_ANSWER' ORDER BY e.seq DESC LIMIT 1) AS answer_payload,
                   (SELECT e.safe_payload->>'code' FROM dt_run_event e
                     WHERE e.run_id = r.id AND e.type = 'RUN_FAILED' ORDER BY e.seq DESC LIMIT 1) AS failure_code
            FROM dt_agent_run r
            WHERE r.owner_id = ? AND r.project_id = ?
            """;

    private static final RowMapper<RunView> VIEW_MAPPER = (r, n) -> new RunView(
            r.getObject("id", UUID.class), r.getObject("conversation_id", UUID.class),
            r.getObject("goal_message_id", UUID.class), r.getString("status"), r.getLong("version"),
            r.getTimestamp("requested_at").toInstant(), r.getTimestamp("as_of").toInstant(), "SIMULATED",
            pendingQuestion(r), answer(r), r.getString("failure_code"));

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

    public AgentRunRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RunView enqueue(ServerIdentity identity, UUID conversationId, UUID messageId, Instant now, Instant asOf) {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,
                    requested_at,as_of,budget,created_at,updated_at)
                VALUES (?,?,?,?,?,'QUEUED',?,?,
                    '{"limits":{"maxSteps":12,"maxRepeatedActions":2,"maxActiveDurationMs":120000,"maxTokens":null,"maxCost":null},"used":{"steps":0,"modelAttempts":0,"toolCalls":0,"activeDurationMs":0,"inputTokens":null,"outputTokens":null,"cost":null},"usageStatus":"UNKNOWN"}'::jsonb,?,?)
                """, runId, conversationId, identity.ownerId(), identity.projectId(), messageId,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(asOf), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
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

    /** 读取运行记录；lock=true 时加行锁，供补答事务串行消费同一个待答问题。 */
    public RunRecord load(ServerIdentity identity, UUID runId, boolean lock) {
        var rows = jdbc.query("""
                SELECT r.id, r.conversation_id, r.goal_message_id, r.status, r.version, r.requested_at, r.as_of,
                       r.pending_question_id, r.question_version,
                       (r.budget->'used'->>'steps')::int AS steps_used,
                       (r.budget->'limits'->>'maxSteps')::int AS max_steps,
                       m.selected_asset_id, m.content AS goal_text
                FROM dt_agent_run r
                JOIN dt_message m ON m.id = r.goal_message_id
                WHERE r.id = ? AND r.owner_id = ? AND r.project_id = ?
                """ + (lock ? " FOR UPDATE OF r" : ""),
                (r, n) -> new RunRecord(r.getObject("id", UUID.class), r.getObject("conversation_id", UUID.class),
                        r.getObject("goal_message_id", UUID.class), r.getString("status"), r.getLong("version"),
                        r.getTimestamp("requested_at").toInstant(), r.getTimestamp("as_of").toInstant(),
                        r.getObject("pending_question_id", UUID.class), r.getInt("question_version"),
                        r.getString("selected_asset_id"), r.getString("goal_text"),
                        r.getInt("steps_used"), r.getInt("max_steps")),
                runId, identity.ownerId(), identity.projectId());
        if (rows.isEmpty()) throw io.devicetruth.application.agent.GeneralAgentService.denied();
        return rows.getFirst();
    }

    /** QUEUED → RUNNING；已被其他 worker 接手（状态或版本变化）时返回 0，调用方必须放弃执行。 */
    public int markRunning(ServerIdentity identity, RunRecord run, Instant now) {
        return update("UPDATE dt_agent_run SET status='RUNNING', version=version+1, updated_at=? "
                + "WHERE id=? AND owner_id=? AND project_id=? AND status='QUEUED' AND version=?",
                java.sql.Timestamp.from(now), run.runId(), identity.ownerId(), identity.projectId(), run.version());
    }

    /** RUNNING → WAITING_FOR_USER：问题、版本、累计步数与引用计数同一事务写入后释放 worker。 */
    public int waitForUser(ServerIdentity identity, RunRecord run, PendingQuestion question, int stepsUsed,
                           int referenceCount, Instant now) {
        String payload;
        try {
            payload = Json.write(question);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cannot encode pending question", e);
        }
        return update("""
                UPDATE dt_agent_run SET status='WAITING_FOR_USER', version=version+1, updated_at=?,
                    pending_question_id=?, question_version=?, pending_question=CAST(? AS jsonb),
                    context_summary=?, budget=jsonb_set(budget,'{used,steps}',to_jsonb(?::int),true)
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING'
                """, java.sql.Timestamp.from(now), question.pendingQuestionId(), question.questionVersion(), payload,
                "steps=" + stepsUsed + ";references=" + referenceCount, stepsUsed,
                run.runId(), identity.ownerId(), identity.projectId());
    }

    /** RUNNING → COMPLETED：答案写入 RUN_ANSWER 事件，界面与报告都从持久事件读回。 */
    public int complete(ServerIdentity identity, RunRecord run, AgentResult answer, int stepsUsed, Instant now) {
        int updated = update("""
                UPDATE dt_agent_run SET status='COMPLETED', version=version+1, updated_at=?,
                    budget=jsonb_set(budget,'{used,steps}',to_jsonb(?::int),true)
                WHERE id=? AND owner_id=? AND project_id=? AND status='RUNNING'
                """, java.sql.Timestamp.from(now), stepsUsed, run.runId(), identity.ownerId(), identity.projectId());
        if (updated == 1) appendEvent(identity, run.runId(), run.conversationId(), "RUN_ANSWER", answer, now);
        return updated;
    }

    /** 任意非终态 → FAILED：技术失败没有答案，只保留稳定原因码。 */
    public int fail(ServerIdentity identity, RunRecord run, String code, int stepsUsed, Instant now) {
        int updated = update("""
                UPDATE dt_agent_run SET status='FAILED', version=version+1, updated_at=?,
                    budget=jsonb_set(budget,'{used,steps}',to_jsonb(?::int),true)
                WHERE id=? AND owner_id=? AND project_id=? AND status IN ('QUEUED','RUNNING')
                """, java.sql.Timestamp.from(now), stepsUsed, run.runId(), identity.ownerId(), identity.projectId());
        if (updated == 1)
            appendEvent(identity, run.runId(), run.conversationId(), "RUN_FAILED", Map.of("code", code), now);
        return updated;
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
                       COALESCE(c.content, r.citation_excerpt, '') AS content
                FROM dt_evidence_reference r
                LEFT JOIN dt_knowledge_chunk c
                  ON c.id = r.chunk_id AND c.owner_id = r.owner_id AND c.project_id = r.project_id
                LEFT JOIN dt_knowledge_document d
                  ON d.id = c.document_id AND d.owner_id = c.owner_id AND d.project_id = c.project_id
                WHERE r.run_id = ? AND r.owner_id = ? AND r.project_id = ?
                  AND r.source_type IN ('KNOWLEDGE_DOCUMENT', 'CONFIGURATION')
                ORDER BY r.retrieved_at, r.id
                """, (r, n) -> new Reference(r.getObject("reference_id", UUID.class).toString(),
                r.getString("source_type"), r.getString("source_id"), r.getString("source_version"),
                r.getObject("chunk_id", UUID.class).toString(), r.getString("title"),
                r.getObject("citation_page", Integer.class), r.getString("citation_section"),
                truncate(r.getString("content"), 600), r.getString("data_nature")),
                runId, identity.ownerId(), identity.projectId());
    }

    public List<ToolEvent> toolEvents(ServerIdentity identity, UUID runId) {
        return jdbc.query("""
                SELECT seq,safe_payload->>'toolName' AS tool_name,
                       (safe_payload->>'success')::boolean AS success,
                       safe_payload->>'errorCode' AS error_code,
                       COALESCE((safe_payload->>'references')::int,0) AS reference_count
                FROM dt_run_event
                WHERE run_id=? AND owner_id=? AND project_id=? AND type='RUN_TOOL'
                ORDER BY seq
                """, (r, n) -> new ToolEvent(r.getInt("seq"), r.getString("tool_name"),
                r.getBoolean("success"), r.getString("error_code"), r.getInt("reference_count")),
                runId, identity.ownerId(), identity.projectId());
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

    /** 公开事件只写白名单类型与逐字段构造的安全载荷；推理输入与原始模型输出永不落库。 */
    public void appendEvent(ServerIdentity identity, UUID runId, UUID conversationId, String type,
                            Object payload, Instant now) {
        jdbc.update("""
                INSERT INTO dt_run_event(run_id,seq,conversation_id,owner_id,project_id,type,recorded_at,safe_payload)
                SELECT ?, COALESCE(MAX(seq),0)+1, ?,?,?,?,?,CAST(? AS jsonb) FROM dt_run_event WHERE run_id=?
                """, runId, conversationId, identity.ownerId(), identity.projectId(), type,
                java.sql.Timestamp.from(now), Json.write(payload), runId);
    }

    private int update(String sql, Object... arguments) { return jdbc.update(sql, arguments); }

    private static String truncate(String content, int max) {
        if (content == null) return "";
        return content.length() <= max ? content : content.substring(0, max);
    }
}
