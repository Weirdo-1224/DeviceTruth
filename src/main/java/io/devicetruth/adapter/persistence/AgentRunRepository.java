package io.devicetruth.adapter.persistence;

import io.devicetruth.application.agent.GeneralAgentService.RunView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** 仅登记和读取QUEUED任务；本切片没有执行调度。 */
public final class AgentRunRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<RunView> MAPPER = (r, n) -> new RunView(r.getObject("id", UUID.class),
            r.getObject("conversation_id", UUID.class), r.getObject("goal_message_id", UUID.class),
            r.getString("status"), r.getLong("version"), r.getTimestamp("requested_at").toInstant(),
            r.getTimestamp("as_of").toInstant(), "SIMULATED");

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
        jdbc.update("INSERT INTO dt_run_event(run_id,seq,conversation_id,owner_id,project_id,type,recorded_at,safe_payload) VALUES (?,1,?,?,?,'RUN_QUEUED',?,'{\"status\":\"QUEUED\"}'::jsonb)",
                runId, conversationId, identity.ownerId(), identity.projectId(), java.sql.Timestamp.from(now));
        return require(identity, runId);
    }

    public RunView require(ServerIdentity identity, UUID id) {
        var rows = jdbc.query("SELECT * FROM dt_agent_run WHERE id=? AND owner_id=? AND project_id=?",
                MAPPER, id, identity.ownerId(), identity.projectId());
        if (rows.isEmpty()) throw io.devicetruth.application.agent.GeneralAgentService.denied();
        return rows.getFirst();
    }

    public List<RunView> list(ServerIdentity identity, UUID conversationId) {
        return jdbc.query("SELECT * FROM dt_agent_run WHERE conversation_id=? AND owner_id=? AND project_id=? ORDER BY created_at,id",
                MAPPER, conversationId, identity.ownerId(), identity.projectId());
    }
}
