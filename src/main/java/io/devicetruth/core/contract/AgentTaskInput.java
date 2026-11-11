package io.devicetruth.core.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 通用 Agent 任务输入（schemaVersion = 1）：服务器已解析并授权后的原文、消息序列与可选资产。
 * 客户端不能填写 owner/project/权限；资产可空，因此“没有资产的充电问题”也能进入同一主循环。
 * messages 来自持久层重建，包含原始目标、追问与用户补答，顺序即会话顺序。
 */
public record AgentTaskInput(String ownerId, String projectId, UUID conversationId, UUID runId,
                             UUID goalMessageId, String goalText, List<Message> messages,
                             String selectedAssetId, Instant requestedAt, Instant asOf,
                             long runVersion, int stepsUsed, int maxSteps) {
    private static final int MAX_MESSAGE_CODE_POINTS = 8000;

    public AgentTaskInput {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        Checks.required(conversationId, "conversationId");
        Checks.required(runId, "runId");
        Checks.required(goalMessageId, "goalMessageId");
        Checks.text(goalText, "goalText");
        if (goalText.codePointCount(0, goalText.length()) > MAX_MESSAGE_CODE_POINTS)
            throw new IllegalArgumentException("goalText too long");
        messages = Checks.list(messages);
        if (selectedAssetId != null) Checks.id(selectedAssetId, "selectedAssetId");
        requestedAt = Checks.required(requestedAt, "requestedAt");
        if (asOf == null) asOf = requestedAt;
        if (runVersion < 0) throw new IllegalArgumentException("Invalid runVersion");
        if (stepsUsed < 0 || maxSteps < 1 || stepsUsed > maxSteps)
            throw new IllegalArgumentException("Invalid step budget");
    }

    /** 会话中的一条消息；角色只允许 USER/ASSISTANT，内容保持原文。 */
    public record Message(String role, String content) {
        public Message {
            if (!"USER".equals(role) && !"ASSISTANT".equals(role))
                throw new IllegalArgumentException("Invalid message role");
            Checks.text(content, "content");
            if (content.codePointCount(0, content.length()) > MAX_MESSAGE_CODE_POINTS)
                throw new IllegalArgumentException("content too long");
        }
    }

    /**
     * 客户端补答关联：runId + pendingQuestionId + questionVersion 三元组缺一不可。
     * 三元组只是定位符，服务器仍会再次校验归属、等待状态与问题是否已被消费。
     */
    public record ReplyTarget(UUID runId, UUID pendingQuestionId, Integer questionVersion) {
        public ReplyTarget {
            Checks.required(runId, "replyTo.runId");
            Checks.required(pendingQuestionId, "replyTo.pendingQuestionId");
            Checks.required(questionVersion, "replyTo.questionVersion");
            if (questionVersion < 1) throw new IllegalArgumentException("Invalid questionVersion");
        }
    }

    /** 运行中的待答问题；版本从 1 单调递增，已消费的问题不可再次激活。 */
    public record PendingQuestion(UUID pendingQuestionId, int questionVersion, String question,
                                 List<String> requestedFields, Instant askedAt) {
        public PendingQuestion {
            Checks.required(pendingQuestionId, "pendingQuestionId");
            if (questionVersion < 1) throw new IllegalArgumentException("Invalid questionVersion");
            Checks.text(question, "question");
            if (question.codePointCount(0, question.length()) > MAX_MESSAGE_CODE_POINTS)
                throw new IllegalArgumentException("question too long");
            requestedFields = Checks.list(requestedFields);
            askedAt = Checks.required(askedAt, "askedAt");
        }

        public PendingQuestion withVersion(int version) {
            return new PendingQuestion(pendingQuestionId, version, question, requestedFields, askedAt);
        }
    }
}
