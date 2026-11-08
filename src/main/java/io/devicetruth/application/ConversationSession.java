package io.devicetruth.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 有界内存会话：所有读写同步，避免并发追加丢消息或串案。 */
public final class ConversationSession {
    private final UUID sessionId;
    private final Instant createdAt;
    private final int maxMessages;
    private final List<ConversationMessage> messages = new ArrayList<>();
    private String currentAssetId;
    private UUID activeDiagnosisId;

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
        if (messages.size() + 2 > maxMessages) throw new ApplicationExceptions.MessageCapacityExceededException();
        if (assetId != null) currentAssetId = assetId;
        var userMessage = new ConversationMessage(UUID.randomUUID(), ConversationMessage.Role.USER,
                route, userContent, Instant.now());
        messages.add(userMessage);
        messages.add(new ConversationMessage(UUID.randomUUID(), ConversationMessage.Role.SYSTEM,
                route, reply, Instant.now()));
        return userMessage;
    }

    public synchronized List<ConversationMessage> messages() { return List.copyOf(messages); }
}