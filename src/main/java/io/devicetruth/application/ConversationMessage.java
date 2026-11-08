package io.devicetruth.application;

import java.time.Instant;
import java.util.UUID;

/** 会话中的一条受限消息；route 记录本轮程序复核后的分类，便于 GET messages 核验。 */
public record ConversationMessage(UUID messageId, Role role, Route route, String content, Instant createdAt) {
    public enum Role { USER, SYSTEM }
    public static final int MAX_CONTENT = 2000;

    public ConversationMessage {
        if (messageId == null) throw new IllegalArgumentException("messageId is required");
        if (role == null) throw new IllegalArgumentException("role is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        if (content.length() > MAX_CONTENT) throw new IllegalArgumentException("content is too long");
        if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
    }
}