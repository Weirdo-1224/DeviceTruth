package io.devicetruth.application;

import io.devicetruth.adapter.Json;
import io.devicetruth.application.DiagnosisRunService.RunSnapshot;
import io.devicetruth.core.contract.Models.DiagnosisResult;
import io.devicetruth.core.contract.Models.RunStatus;
import io.devicetruth.core.runtime.TaskOrchestrator.RunEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * spec §4.7：结束后事件只按白名单映射成 RunEventView。
 * 真实事件类型原样保留；summary 完全由固定文案 + 安全码/序号拼装；
 * RunEvent.data 一律不透传（尤其 REASON_INPUT.context 与 TERMINATED 的整份结果）。
 */
public final class RunEventViews {
    public record RunEventView(int sequence, Instant recordedAt, String type, long stateVersion,
                               String summary, List<String> referenceIds) {
        public RunEventView { referenceIds = List.copyOf(referenceIds); }
    }

    private RunEventViews() {}

    public static boolean available(RunSnapshot snapshot) { return snapshot.eventsAvailable(); }

    /** 未结束、或失败但没有最终报告时返回空列表。 */
    public static List<RunEventView> of(RunSnapshot snapshot) {
        if (!snapshot.eventsAvailable()) return List.of();
        DiagnosisResult result = snapshot.report().result();
        var views = new ArrayList<RunEventView>();
        for (RunEvent event : snapshot.events()) views.add(view(event, result));
        return List.copyOf(views);
    }

    private static RunEventView view(RunEvent event, DiagnosisResult result) {
        // 内存中的 RunEvent.data 存的是 Java record（Attempt/ActionProposal/CapabilityResult/Verdict 等），
        // 直接当 Map 逐层导航会全部取到 null（成功的能力结果被显示成 TOOL_FAILURE、动作恒为 UNKNOWN）。
        // 这里统一规范化为 JSON 形状的 Map，与 RunReportWriter 落盘的 events.jsonl 完全一致。
        Map<String, Object> data = normalized(event.data());
        String summary;
        List<String> refs = List.of();
        switch (event.type()) {
            case "REASON_INPUT" -> summary = "第 " + (event.stateVersion() + 1) + " 轮推理输入";
            case "MODEL_ATTEMPT" -> summary = "模型尝试 #" + nestedRaw(data, "attempt", "number")
                    + "：" + nested(data, "attempt", "outcome");
            case "ACTION_PROPOSED" -> {
                summary = "提议动作 " + proposalKind(data);
                refs = actionReferences(data);
            }
            case "CAPABILITY_RESULT" -> {
                boolean success = Boolean.TRUE.equals(nestedRaw(data, "result", "success"));
                String capabilityId = nested(data, "result", "capabilityId");
                String errorCode = nested(data, "result", "error", "code");
                summary = success ? "能力结果：成功" : "能力结果：" + (errorCode == null ? "TOOL_FAILURE" : errorCode);
                refs = single(capabilityId);
            }
            case "HYPOTHESIS_INVALIDATED" -> {
                summary = "假设失效";
                refs = single(nested(data, "hypothesis", "hypothesisId"));
            }
            case "RULE_SELECTION_INVALIDATED" -> {
                summary = "候选规则选择失效";
                refs = single(valueAt(data.get("ruleId")));
            }
            case "GUARD_RESULT" -> {
                String reason = nested(data, "verdict", "reason");
                summary = "结案校验：" + (reason == null ? "" : reason);
                refs = single(nested(data, "verdict", "rule", "ruleId"));
            }
            case "GUARD_REVIEW" -> {
                summary = "证据需求复审";
                refs = stringList(nestedRaw(data, "review", "conflictingEvidenceIds"));
            }
            case "STATE_COMMITTED" -> summary = "提交状态版本 " + event.stateVersion();
            case "TECHNICAL_FAILURE" -> summary = "技术失败：" + SafePublicFields.stopReason(valueAt(data.get("code")));
            case "TERMINATED" -> {
                String outcome = result.outcome() == null ? RunStatus.FAILED.name() : result.outcome().name();
                summary = "运行结束：" + outcome + "/" + SafePublicFields.stopReason(result.stopReason());
                var ids = new LinkedHashSet<String>(result.appliedRuleIds());
                ids.addAll(result.supportingEvidenceIds());
                refs = List.copyOf(ids);
            }
            default -> summary = "事件 " + event.type();
        }
        return new RunEventView(event.sequence(), event.recordedAt(), event.type(), event.stateVersion(), summary, refs);
    }

    /** 动作类型按字段形状判定，避免输出 free-text 的 purpose/rationale/statement。 */
    private static String proposalKind(Map<String, Object> data) {
        Map<?, ?> proposal = nestedRaw(data, "proposal") instanceof Map<?, ?> map ? map : Map.of();
        if (proposal.containsKey("capabilityId")) return "CALL_CAPABILITY";
        if (proposal.containsKey("candidateRuleId")) return "PROPOSE_DECISION";
        if (proposal.containsKey("statement")) return "PROPOSE_HYPOTHESIS";
        if (proposal.containsKey("missingFields")) return "REQUEST_INFORMATION";
        if (proposal.containsKey("reason")) return "ESCALATE";
        return "UNKNOWN";
    }

    /** 提议动作的可披露引用：按固定优先级取第一个存在的 ID。 */
    private static List<String> actionReferences(Map<String, Object> data) {
        String capabilityId = nested(data, "proposal", "capabilityId");
        if (capabilityId != null) return List.of(capabilityId);
        String candidateRuleId = nested(data, "proposal", "candidateRuleId");
        if (candidateRuleId != null) return List.of(candidateRuleId);
        return stringList(nestedRaw(data, "proposal", "targetEvidenceNeedIds"));
    }

    /** 把事件载荷规范化成 JSON 形状的 Map；只用于读取白名单字段，绝不原样输出。 */
    private static Map<String, Object> normalized(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return Map.of();
        try {
            return Json.MAPPER.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            // 载荷无法规范化时不猜测内容：各分支取不到字段，退化为固定安全文案。
            return Map.of();
        }
    }

    private static String nested(Map<String, Object> data, String... path) { return valueAt(nestedRaw(data, path)); }

    private static Object nestedRaw(Map<String, Object> data, String... path) {
        Object current = data;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
        }
        return current;
    }

    private static String valueAt(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.isBlank() || "null".equals(text) ? null : text;
    }

    private static List<String> single(String id) {
        return id == null || id.isBlank() ? List.of() : List.of(id);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        var result = new ArrayList<String>();
        for (Object item : list) { String text = valueAt(item); if (text != null) result.add(text); }
        return List.copyOf(result);
    }
}