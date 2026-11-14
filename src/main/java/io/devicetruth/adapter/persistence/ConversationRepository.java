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
        return append(identity, conversationId, requestKey, "USER", text, assetId, requestedAt, asOf,
                null, null, null, null, "[]", "[]");
    }

    public UUID appendWithReferences(ServerIdentity identity, UUID conversationId, UUID requestKey, String text,
                       String assetId, Instant requestedAt, Instant asOf,
                       java.util.List<java.util.Map<String, Object>> attachments,
                       java.util.List<java.util.Map<String, Object>> contextRefs) {
        return append(identity, conversationId, requestKey, "USER", text, assetId, requestedAt, asOf,
                null, null, null, null, io.devicetruth.adapter.Json.write(attachments),
                io.devicetruth.adapter.Json.write(contextRefs));
    }

    /** 助手消息：待答问题或最终答案；没有 request_key，因此不受“同一会话请求键唯一”约束。 */
    public UUID appendAssistant(ServerIdentity identity, UUID conversationId, UUID runId, String text,
                                Instant requestedAt, Instant asOf) {
        return append(identity, conversationId, null, "ASSISTANT", text, null, requestedAt, asOf,
                runId, null, null, null, "[]", "[]");
    }

    /** 用户补答：同时写入 reply_to 三元组与 run_id，数据库层保留“这条消息回答哪个问题”的可审计关联。 */
    public UUID appendReply(ServerIdentity identity, UUID conversationId, UUID requestKey, String text,
                            String assetId, Instant requestedAt, Instant asOf,
                            UUID replyRunId, UUID replyQuestionId, Integer replyQuestionVersion) {
        return append(identity, conversationId, requestKey, "USER", text, assetId, requestedAt, asOf,
                replyRunId, replyRunId, replyQuestionId, replyQuestionVersion, "[]", "[]");
    }

    public UUID appendReplyWithReferences(ServerIdentity identity, UUID conversationId, UUID requestKey, String text,
                            String assetId, Instant requestedAt, Instant asOf,
                            UUID replyRunId, UUID replyQuestionId, Integer replyQuestionVersion,
                            java.util.List<java.util.Map<String, Object>> attachments,
                            java.util.List<java.util.Map<String, Object>> contextRefs) {
        return append(identity, conversationId, requestKey, "USER", text, assetId, requestedAt, asOf,
                replyRunId, replyRunId, replyQuestionId, replyQuestionVersion,
                io.devicetruth.adapter.Json.write(attachments), io.devicetruth.adapter.Json.write(contextRefs));
    }

    private UUID append(ServerIdentity identity, UUID conversationId, UUID requestKey, String role, String text,
                        String assetId, Instant requestedAt, Instant asOf, UUID runId, UUID replyRunId,
                        UUID replyQuestionId, Integer replyQuestionVersion, String attachmentRefs, String contextRefs) {
        UUID messageId = UUID.randomUUID();
        // 序号分配必须与自己写消息在同一把锁内：worker 写助手消息时不会经过 submit，
        // 因此锁放在这里而不是只放在提交入口，否则并发插入会撞 UNIQUE(conversation_id, seq)。
        if (jdbc.queryForList("SELECT id FROM dt_conversation WHERE id=? AND owner_id=? AND project_id=? FOR UPDATE",
                UUID.class, conversationId, identity.ownerId(), identity.projectId()).isEmpty())
            throw io.devicetruth.application.agent.GeneralAgentService.denied();
        Long seq = jdbc.queryForObject("SELECT COALESCE(MAX(seq),0)+1 FROM dt_message WHERE conversation_id=? AND owner_id=? AND project_id=?",
                Long.class, conversationId, identity.ownerId(), identity.projectId());
        jdbc.update("""
                INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,request_key,
                    selected_asset_id,requested_at,as_of,run_id,reply_to_run_id,reply_to_question_id,
                    reply_to_question_version,attachment_refs,context_refs)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb))
                """, messageId, conversationId, identity.ownerId(), identity.projectId(), seq, role, text, requestKey,
                assetId, java.sql.Timestamp.from(requestedAt), java.sql.Timestamp.from(asOf),
                runId, replyRunId, replyQuestionId, replyQuestionVersion, attachmentRefs, contextRefs);
        jdbc.update("UPDATE dt_conversation SET version=version+1,updated_at=? WHERE id=? AND owner_id=? AND project_id=?",
                java.sql.Timestamp.from(requestedAt), conversationId, identity.ownerId(), identity.projectId());
        return messageId;
    }

    public record ContextMessage(String role, String content, List<java.util.Map<String, Object>> attachments) {}

    /** 目标消息、助手追问和用户补答共用同一条排序查询，附件留在各自消息上。 */
    public List<ContextMessage> runContextMessages(ServerIdentity identity, UUID runId, UUID goalMessageId) {
        return jdbc.query("""
                SELECT role,content,attachment_refs FROM dt_message
                WHERE owner_id=? AND project_id=? AND (run_id=? OR id=?)
                ORDER BY seq
                """,
                (r, n) -> new ContextMessage(r.getString("role"), r.getString("content"),
                        attachments(r.getString("attachment_refs"))),
                identity.ownerId(), identity.projectId(), runId, goalMessageId);
    }

    private static final int HISTORY_MAX_MESSAGES = 12;
    private static final int HISTORY_MAX_CHARS = 6000;

    /** 只取目标之前已写完最终回答的 Run。附件与引用元数据不进入历史。 */
    public List<ContextMessage> recentConversationHistory(ServerIdentity identity, UUID conversationId,
                                                          UUID goalMessageId) {
        var recent = jdbc.query("""
                SELECT m.role, left(m.content, ?) AS content
                FROM dt_message goal
                JOIN dt_message m ON m.conversation_id=goal.conversation_id
                  AND m.owner_id=goal.owner_id AND m.project_id=goal.project_id AND m.seq < goal.seq
                JOIN dt_agent_run r ON r.id=COALESCE(m.run_id, (
                    SELECT g.id FROM dt_agent_run g WHERE g.goal_message_id=m.id
                      AND g.owner_id=m.owner_id AND g.project_id=m.project_id
                )) AND r.conversation_id=m.conversation_id
                  AND r.owner_id=m.owner_id AND r.project_id=m.project_id AND r.status='COMPLETED'
                WHERE goal.id=? AND goal.conversation_id=? AND goal.owner_id=? AND goal.project_id=?
                  AND (SELECT max(a.seq) FROM dt_message a
                       WHERE a.run_id=r.id AND a.conversation_id=r.conversation_id
                         AND a.owner_id=r.owner_id AND a.project_id=r.project_id
                         AND a.role='ASSISTANT') < goal.seq
                ORDER BY m.seq DESC LIMIT ?
                """, (r, n) -> new ContextMessage(r.getString("role"), r.getString("content"), List.of()),
                HISTORY_MAX_CHARS, goalMessageId, conversationId, identity.ownerId(), identity.projectId(),
                HISTORY_MAX_MESSAGES);
        var history = new java.util.ArrayList<ContextMessage>();
        int remaining = HISTORY_MAX_CHARS;
        for (var message : recent) {
            if (remaining == 0) break;
            String content = message.content();
            int length = content.codePointCount(0, content.length());
            if (length > remaining)
                content = content.substring(0, content.offsetByCodePoints(0, remaining - 1)) + "…";
            if (!content.isBlank()) history.add(new ContextMessage(message.role(), content, List.of()));
            remaining -= Math.min(length, remaining);
        }
        return List.copyOf(history.reversed());
    }

    private static List<java.util.Map<String, Object>> attachments(String json) {
        try {
            return io.devicetruth.adapter.Json.MAPPER.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Invalid stored attachment metadata", e);
        }
    }

    /** 本 run 的消息序列：目标消息 + 挂在该 run 上的助手消息与用户补答，按 seq 排序。 */
    public List<MessageView> runMessages(ServerIdentity identity, UUID runId, UUID goalMessageId) {
        return jdbc.query("""
                SELECT id,seq,role,content,selected_asset_id,requested_at,as_of FROM dt_message
                WHERE owner_id=? AND project_id=? AND (run_id=? OR id=?)
                ORDER BY seq
                """,
                (r, n) -> new MessageView(r.getObject("id", UUID.class), r.getLong("seq"), r.getString("role"),
                        r.getString("content"), r.getString("selected_asset_id"), r.getTimestamp("requested_at").toInstant(),
                        r.getTimestamp("as_of").toInstant()),
                identity.ownerId(), identity.projectId(), runId, goalMessageId);
    }

    public List<MessageView> messages(ServerIdentity identity, UUID id) {
        return jdbc.query("SELECT id,seq,role,content,selected_asset_id,requested_at,as_of FROM dt_message WHERE conversation_id=? AND owner_id=? AND project_id=? ORDER BY seq",
                (r, n) -> new MessageView(r.getObject("id", UUID.class), r.getLong("seq"), r.getString("role"),
                        r.getString("content"), r.getString("selected_asset_id"), r.getTimestamp("requested_at").toInstant(),
                        r.getTimestamp("as_of").toInstant()), id, identity.ownerId(), identity.projectId());
    }
}
