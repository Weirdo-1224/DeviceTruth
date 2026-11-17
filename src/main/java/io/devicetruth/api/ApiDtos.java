package io.devicetruth.api;

import io.devicetruth.application.ExecutionStatus;
import io.devicetruth.application.RunEventViews.RunEventView;
import io.devicetruth.application.SafePublicFields.OpenNeed;
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

    /** 消息响应：QUERY 时 query 非空、DIAGNOSE 时 diagnosisId 非空；requestedAt 是请求时刻，不代表业务 asOf。 */
    public record RouteResponse(String route, UUID messageId, String reply, String dataNature, String assetId,
                                Instant requestedAt, QueryView query, UUID diagnosisId,
                                ExecutionStatus executionStatus) {}

    /** 受限查询结果视图：sources 直接来自真实记录，limitations 说明语义边界。 */
    public record QueryView(String scope, String assetId, String answer, List<SourceView> sources,
                            List<String> limitations, String dataNature) {}

    public record SourceView(String evidenceId, String sourceRef, String sourceType, String dataNature) {}

    /**
     * 诊断状态视图：运行中 outcome/stepsUsed/supportingEvidenceIds/stopReason 一律为 null；
     * 完成后的派生字段已按 spec §4.8 过滤，openNeeds 不含缺口描述，不返回 reportPath。
     */
    public record DiagnosisView(UUID diagnosisId, UUID sessionId, ExecutionStatus executionStatus, String coreTaskId,
                                Instant requestedAt, Instant diagnosisAsOf, Instant createdAt, Instant startedAt,
                                Instant completedAt, String outcome, Integer stepsUsed, String diagnosisSummary,
                                List<String> supportingEvidenceIds, List<String> appliedRuleIds, List<String> warnings,
                                String stopReason, List<OpenNeed> openNeeds, String errorCode, String dataNature) {}

    /** 事件视图：运行中 available=false 且 events 为空；完成后按真实顺序返回白名单映射。 */
    public record DiagnosisEventsResponse(UUID diagnosisId, boolean eventsAvailable, List<RunEventView> events,
                                          String dataNature) {}

    public record ErrorResponse(String code, String message, String requestId) {}
    public record HealthResponse(String status, String dataNature) {}
}