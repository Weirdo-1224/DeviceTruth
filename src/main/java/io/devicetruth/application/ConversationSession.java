package io.devicetruth.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 有界内存会话：所有读写同步，避免并发追加丢消息或串案。 */
public final class ConversationSession {
    private final UUID sessionId;
    private final Instant createdAt;
    private final int maxMessages;
    private final List<ConversationMessage> messages = new ArrayList<>();
    private String currentAssetId;
    private UUID activeDiagnosisId;

    /** 会话内一次完整交互的提交内容：资产绑定、用户消息、系统回复（可带结构化查询结果）。 */
    public record Interaction(String assetId, String userContent, String reply, QueryAnswer queryAnswer) {}

    ConversationSession(UUID sessionId, Instant createdAt, int maxMessages) {
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.maxMessages = maxMessages;
    }

    public synchronized UUID sessionId() { return sessionId; }
    public synchronized Instant createdAt() { return createdAt; }
    public synchronized String currentAssetId() { return currentAssetId; }
    public synchronized void currentAssetId(String value) { this.currentAssetId = value; }
    public synchronized UUID activeDiagnosisId() { return activeDiagnosisId; }
    public synchronized void activeDiagnosisId(UUID value) { this.activeDiagnosisId = value; }

    public synchronized ConversationMessage append(ConversationMessage.Role role, Route route, String content) {
        if (messages.size() >= maxMessages) throw new ApplicationExceptions.MessageCapacityExceededException();
        var message = new ConversationMessage(UUID.randomUUID(), role, route, content, Instant.now());
        messages.add(message);
        return message;
    }

    /**
     * 整个交互的原子提交：容量预检、资产绑定和 USER/SYSTEM 两条消息在同一把锁内完成。
     * 任一步失败时资产与消息都保持原值，不留下半条交互。
     */
    public synchronized ConversationMessage commitInteraction(Route route, String assetId,
                                                              String userContent, String reply) {
        return writeLocked(route, new Interaction(assetId, userContent, reply, null));
    }

    /**
     * 先在同一把会话锁内做容量预检并执行只读工作（如查询），成功后再一次性写入两条消息。
     * 查询抛异常或容量不足时资产与消息都不变，避免"先写消息再查询失败"留下半条交互。
     */
    public synchronized ConversationMessage commitPlanned(Route route, Supplier<Interaction> work) {
        if (messages.size() + 2 > maxMessages) throw new ApplicationExceptions.MessageCapacityExceededException();
        return writeLocked(route, work.get());
    }

    private ConversationMessage writeLocked(Route route, Interaction interaction) {
        if (messages.size() + 2 > maxMessages) throw new ApplicationExceptions.MessageCapacityExceededException();
        if (interaction.assetId() != null) currentAssetId = interaction.assetId();
        var userMessage = new ConversationMessage(UUID.randomUUID(), ConversationMessage.Role.USER,
                route, interaction.userContent(), Instant.now());
        messages.add(userMessage);
        messages.add(new ConversationMessage(UUID.randomUUID(), ConversationMessage.Role.SYSTEM,
                route, interaction.reply(), Instant.now()));
        return userMessage;
    }

    public synchronized List<ConversationMessage> messages() { return List.copyOf(messages); }
}