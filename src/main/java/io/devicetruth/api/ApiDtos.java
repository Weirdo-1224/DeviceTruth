package io.devicetruth.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HTTP DTO：只含允许公开的字段，不携带 reportPath、Prompt、模型配置或 Core 原始 data。 */
public final class ApiDtos {
    private ApiDtos() {}
    public static final String SIMULATED = "SIMULATED";

    public record CreateSessionResponse(UUID sessionId, Instant createdAt, String dataNature) {}
    public record MessageRequest(String content, String assetId) {}
    public record MessageView(UUID messageId, String role, String route, String content, Instant createdAt) {}
    public record MessagesResponse(UUID sessionId, List<MessageView> messages, String dataNature) {}

    /** 消息响应：QUERY 时 query 非空；requestedAt 是请求时刻，不代表业务 asOf。 */
    public record RouteResponse(String route, UUID messageId, String reply, String dataNature, String assetId,
                                Instant requestedAt, QueryView query) {}

    /** 受限查询结果视图：sources 直接来自真实记录，limitations 说明语义边界。 */
    public record QueryView(String scope, String assetId, String answer, List<SourceView> sources,
                            List<String> limitations, String dataNature) {}

    public record SourceView(String evidenceId, String sourceRef, String sourceType, String dataNature) {}

    public record ErrorResponse(String code, String message, String requestId) {}
    public record HealthResponse(String status, String dataNature) {}
}