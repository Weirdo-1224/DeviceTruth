package io.devicetruth.application;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内会话注册表：有界、线程安全、重启丢失；sessionId 不是生产级身份凭据。 */
public final class ConversationService {
    private final int maxSessions;
    private final int maxMessagesPerSession;
    private final ConcurrentHashMap<UUID, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final Object createLock = new Object();

    public ConversationService(int maxSessions, int maxMessagesPerSession) {
        if (maxSessions < 1 || maxMessagesPerSession < 1)
            throw new IllegalArgumentException("Session limits must be positive");
        this.maxSessions = maxSessions;
        this.maxMessagesPerSession = maxMessagesPerSession;
    }

    public ConversationSession create() {
        synchronized (createLock) {
            if (sessions.size() >= maxSessions) throw new ApplicationExceptions.SessionCapacityExceededException();
            var session = new ConversationSession(UUID.randomUUID(), Instant.now(), maxMessagesPerSession);
            sessions.put(session.sessionId(), session);
            return session;
        }
    }

    public ConversationSession require(UUID sessionId) {
        ConversationSession session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) throw new ApplicationExceptions.SessionNotFoundException();
        return session;
    }

    public int size() { return sessions.size(); }
}