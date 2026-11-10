package io.devicetruth.application.agent;

import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.core.contract.Checks;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** 产品入口的持久化切片：先校验/授权，再原子写消息、任务和事件。 */
public final class GeneralAgentService {
    public record ServerIdentity(String ownerId, String projectId) {
        public ServerIdentity { Checks.id(ownerId, "ownerId"); Checks.id(projectId, "projectId"); }
    }
    public record AgentTaskInput(Integer schemaVersion, String userMessage, String selectedAssetId,
                                 List<Map<String, Object>> attachments, List<Map<String, Object>> contextRefs,
                                 Instant asOf, Map<String, Object> replyTo) {}
    public record ConversationView(UUID conversationId, long version, Instant createdAt, Instant updatedAt) {}
    public record MessageView(UUID messageId, long seq, String role, String content, String selectedAssetId,
                              Instant requestedAt, Instant asOf) {}
    public record RunView(UUID runId, UUID conversationId, UUID goalMessageId, String status, long version,
                          Instant requestedAt, Instant asOf, String dataNature) {}
    public record ConversationDetail(ConversationView conversation, List<MessageView> messages, List<RunView> runs) {}
    public static final class RequestFailure extends RuntimeException {
        private final String code;
        private final int status;
        public RequestFailure(String code, int status) { super(code); this.code = code; this.status = status; }
        public String code() { return code; }
        public int status() { return status; }
    }

    private final ConversationRepository conversations;
    private final AgentRunRepository runs;
    private final TransactionTemplate transactions;
    private final ServerIdentity identity;

    public GeneralAgentService(ConversationRepository conversations, AgentRunRepository runs,
                               TransactionTemplate transactions, ServerIdentity identity) {
        this.conversations = conversations;
        this.runs = runs;
        this.transactions = transactions;
        this.identity = identity;
    }

    public ConversationView create() { return conversations.create(identity, Instant.now()); }
    public List<ConversationView> list() { return conversations.list(identity); }

    public ConversationDetail read(UUID conversationId) {
        return transactions.execute(status -> {
            // 与提交共用行锁，避免把旧版本与刚提交的消息拼成一次读取结果。
            var conversation = conversations.require(identity, conversationId, true);
            return new ConversationDetail(conversation, conversations.messages(identity, conversationId), runs.list(identity, conversationId));
        });
    }

    public RunView run(UUID runId) { return runs.require(identity, runId); }

    public RunView submit(UUID conversationId, UUID requestKey, AgentTaskInput input) {
        if (requestKey == null || input == null || input.userMessage() == null || input.userMessage().isBlank()
                || input.userMessage().codePointCount(0, input.userMessage().length()) > 8000
                || input.userMessage().indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid message");
        if (!Integer.valueOf(1).equals(input.schemaVersion())) throw new RequestFailure("SCHEMA_VERSION_UNSUPPORTED", 400);
        // 文件解析及同run补答由后续计划实现；当前拒绝整条请求，不能静默丢弃关联信息。
        if (input.replyTo() != null) throw new RequestFailure("REPLY_NOT_AVAILABLE", 400);
        if (input.attachments() != null && !input.attachments().isEmpty()
                || input.contextRefs() != null && !input.contextRefs().isEmpty())
            throw new RequestFailure("REFERENCES_NOT_AVAILABLE", 400);
        if (input.selectedAssetId() != null) Checks.id(input.selectedAssetId(), "selectedAssetId");
        Instant now = Instant.now();
        Instant asOf = input.asOf() == null ? now : input.asOf();
        return transactions.execute(status -> {
            conversations.require(identity, conversationId, true);
            if (conversations.hasRequest(identity, conversationId, requestKey))
                throw new RequestFailure("DUPLICATE_REQUEST", 409);
            UUID messageId = conversations.append(identity, conversationId, requestKey, input.userMessage(),
                    input.selectedAssetId(), now, asOf);
            return runs.enqueue(identity, conversationId, messageId, now, asOf);
        });
    }

    public static RequestFailure denied() { return new RequestFailure("NOT_FOUND_OR_DENIED", 404); }
}
