package io.devicetruth.application.agent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 05-01 契约 §2：dt_run_event 行 → 公开事件的唯一白名单映射，历史端点与 SSE 共用。
 * data 按事件类型逐字段过滤，safe_payload 整体绝不直透——即使未来写入侧多存了
 * Prompt、密钥或模型原始输出，公开视图也只透出契约允许的白名单字段。
 */
public final class SafeAgentEventViews {
    /** 公开事件：seq 为 run 内单调递增序号（从 1 开始），recordedAt 为 UTC ISO-8601。 */
    public record PublicEvent(long seq, String type, Instant recordedAt, Map<String, Object> data, java.util.UUID actorUserId) {
        public PublicEvent(long seq,String type,Instant recordedAt,Map<String,Object> data) {this(seq,type,recordedAt,data,null);}
        public PublicEvent { data = Map.copyOf(data); }
    }

    /** 各事件类型允许透出的 data 字段（契约 §2 表 + 复验 §8.1 RUN_WAITING）；不在表内的类型一律空对象。 */
    private static final Map<String, Set<String>> ALLOWED_FIELDS = Map.ofEntries(
            Map.entry("RUN_QUEUED", Set.of("status")),
            Map.entry("RUN_RECOVERED", Set.of("attempts")),
            Map.entry("RUN_TOOL", Set.of("toolName", "success", "errorCode", "references", "durationMs", "retryable")),
            Map.entry("RUN_MODEL", Set.of("model", "attempt", "outcome", "durationMs", "retryable",
                    "inputTokens", "outputTokens", "cost")),
            Map.entry("RUN_ANSWER", Set.of("summary", "category", "claims", "suggestions", "uncertainties",
                    "limitations", "references", "riskLevel", "safetyDisposition", "dataNature")),
            Map.entry("RUN_FAILED", Set.of("code")),
            Map.entry("RUN_CANCELLED", Set.of("phase")),
            Map.entry("RUN_LATE_RESPONSE", Set.of("status", "code")),
            Map.entry("RUN_WAITING", Set.of("questionVersion")),
            Map.entry("RUN_PHASE", Set.of("phase", "reason")),
            Map.entry("RUN_ANSWER_REVIEW", Set.of("code", "unitIds", "rules", "reviews", "corrections")),
            Map.entry("RUN_DEGRADED", Set.of("code", "references")));

    private SafeAgentEventViews() {}

    /** 持久行（seq/type/recorded_at 已读出，safe_payload 已解析为 Map）映射成公开事件。 */
    public static PublicEvent fromPayload(long seq, String type, Instant recordedAt,
                                          Map<String, Object> payload) {
        return new PublicEvent(seq, type, recordedAt, pick(type, payload));
    }

    /** 白名单逐字段过滤；null 值字段在公开视图中省略（复验 §8.4），绝不抛 NPE、不出现 null。 */
    private static Map<String, Object> pick(String type, Map<String, Object> payload) {
        Set<String> allowed = ALLOWED_FIELDS.getOrDefault(type, Set.of());
        if (allowed.isEmpty() || payload == null || payload.isEmpty()) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet())
            if (allowed.contains(entry.getKey()) && entry.getValue() != null) data.put(entry.getKey(), entry.getValue());
        return data.isEmpty() ? Map.of() : Map.copyOf(data);
    }
}
