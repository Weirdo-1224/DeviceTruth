package io.devicetruth.adapter.persistence;

import io.devicetruth.application.agent.GeneralAgentService.ConversationView;
import io.devicetruth.application.agent.GeneralAgentService.MessageView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 所有查询显式携带服务器归属；SQL只使用绑定参数。 */
public final class ConversationRepository {
    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ConversationView create(ServerIdentity identity, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,created_at,updated_at) VALUES (?,?,?,?,?)",
                id, identity.ownerId(), identity.projectId(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return new ConversationView(id, 0, now, now);
    }

    public List<ConversationView> list(ServerIdentity identity) {
        return jdbc.query("SELECT id,version,created_at,updated_at FROM dt_conversation WHERE owner_id=? AND project_id=? ORDER BY updated_at DESC,id LIMIT 100",
                (r, n) -> new ConversationView(r.getObject("id", UUID.class), r.getLong("version"),
                        r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant()),
                identity.ownerId(), identity.projectId());
    }

    public ConversationView require(ServerIdentity identity, UUID id, boolean lock) {
        var rows = jdbc.query("SELECT id,version,created_at,updated_at FROM dt_conversation WHERE id=? AND owner_id=? AND project_id=?" + (lock ? " FOR UPDATE" : ""),
                (r, n) -> new ConversationView(r.getObject("id", UUID.class), r.getLong("version"),
                        r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant()),
                id, identity.ownerId(), identity.projectId());
        if (rows.isEmpty()) throw io.devicetruth.application.agent.GeneralAgentService.denied();
        return rows.getFirst();
    }

    public boolean hasRequest(ServerIdentity identity, UUID id, UUID key) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM dt_message WHERE conversation_id=? AND owner_id=? AND project_id=? AND request_key=?)",
                Boolean.class, id, identity.ownerId(), identity.projectId(), key));
    }

    public UUID append(ServerIdentity identity, UUID conversationId, UUID requestKey, String text,
                       String assetId, Instant requestedAt, Instant asOf) {
        UUID messageId = UUID.randomUUID();
        // 同一会话行锁覆盖序号分配及写入，失败时消息/任务/版本一起回滚。
        Long seq = jdbc.queryForObject("SELECT COALESCE(MAX(seq),0)+1 FROM dt_message WHERE conversation_id=? AND owner_id=? AND project_id=?",
                Long.class, conversationId, identity.ownerId(), identity.projectId());
        jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,request_key,selected_asset_id,requested_at,as_of) VALUES (?,?,?,?,?,'USER',?,?,?,?,?)",
                messageId, conversationId, identity.ownerId(), identity.projectId(), seq, text, requestKey, assetId,
                java.sql.Timestamp.from(requestedAt), java.sql.Timestamp.from(asOf));
        jdbc.update("UPDATE dt_conversation SET version=version+1,updated_at=? WHERE id=? AND owner_id=? AND project_id=?",
                java.sql.Timestamp.from(requestedAt), conversationId, identity.ownerId(), identity.projectId());
        return messageId;
    }

    public List<MessageView> messages(ServerIdentity identity, UUID id) {
        return jdbc.query("SELECT id,seq,role,content,selected_asset_id,requested_at,as_of FROM dt_message WHERE conversation_id=? AND owner_id=? AND project_id=? ORDER BY seq",
                (r, n) -> new MessageView(r.getObject("id", UUID.class), r.getLong("seq"), r.getString("role"),
                        r.getString("content"), r.getString("selected_asset_id"), r.getTimestamp("requested_at").toInstant(),
                        r.getTimestamp("as_of").toInstant()), id, identity.ownerId(), identity.projectId());
    }
}
