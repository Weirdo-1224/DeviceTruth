package io.devicetruth.application;

import io.devicetruth.application.ConversationSession.Interaction;
import io.devicetruth.core.contract.Checks;
import io.devicetruth.core.contract.Models.DiagnosisRequest;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会话消息处理：先路由并做程序复核，再执行受限处理并原子写入消息。
 * QUERY 走真实只读查询（200）；DIAGNOSE 只登记有界异步任务并返回 202 + QUEUED，真实状态由 GET 查询。
 */
public final class ConversationFlowService {
    public record FlowResult(int status, Route route, UUID messageId, UUID diagnosisId, String reply, String assetId,
                             Instant requestedAt, QueryAnswer queryAnswer, DiagnosisRequest diagnosisRequest) {
        static FlowResult text(Route route, UUID messageId, String reply, String assetId, Instant requestedAt) {
            return new FlowResult(200, route, messageId, null, reply, assetId, requestedAt, null, null);
        }
    }

    private final ConversationService conversations;
    private final TaskRouter router;
    private final SupportedScenarioCatalog catalog;
    private final QueryService queries;
    private final DiagnosisApplicationService diagnoses;
    private final DiagnosisRunService runs;

    public ConversationFlowService(ConversationService conversations, TaskRouter router, SupportedScenarioCatalog catalog,
                                   QueryService queries, DiagnosisApplicationService diagnoses, DiagnosisRunService runs) {
        this.conversations = conversations;
        this.router = router;
        this.catalog = catalog;
        this.queries = queries;
        this.diagnoses = diagnoses;
        this.runs = runs;
    }

    public FlowResult handle(UUID sessionId, String content, String assetIdHint) {
        validateContent(content);
        validateAssetHint(assetIdHint);
        var session = conversations.require(sessionId);

        RouteDecision decision = router.decide(
                new TaskRouter.RouteRequest(content, session.currentAssetId(), assetIdHint));
        Instant requestedAt = Instant.now();

        return switch (decision.route()) {
            case QUERY -> query(session, decision, content, requestedAt);
            case DIAGNOSE -> diagnose(session, decision, content, requestedAt);
            // CLARIFY/UNSUPPORTED 在锁内一次性提交，容量不足时不留半条交互。
            case CLARIFY, UNSUPPORTED -> recordText(session, decision, content, requestedAt);
        };
    }

    private FlowResult recordText(ConversationSession session, RouteDecision decision, String content,
                                  Instant requestedAt) {
        String reply = decision.route() == Route.CLARIFY
                ? clarifyReply(decision.reason())
                : unsupportedReply(decision.reason());
        String assetToBind = decision.assetId() != null && catalog.supports(decision.assetId())
                ? decision.assetId() : null;
        var userMessage = session.commitInteraction(decision.route(), assetToBind, content, reply);
        return FlowResult.text(decision.route(), userMessage.messageId(), reply, decision.assetId(), requestedAt);
    }

    /** 查询在同一会话锁内完成：先容量预检，再查真实仓储，最后一次性写入 USER/SYSTEM 两条消息。 */
    private FlowResult query(ConversationSession session, RouteDecision decision, String content, Instant requestedAt) {
        QueryAnswer[] answer = new QueryAnswer[1];
        var userMessage = session.commitPlanned(Route.QUERY, () -> {
            var result = queries.query(decision.assetId(), decision.queryKind());
            answer[0] = result;
            return new Interaction(decision.assetId(), content, result.answer());
        });
        return new FlowResult(200, Route.QUERY, userMessage.messageId(), null, answer[0].answer(),
                decision.assetId(), requestedAt, answer[0], null);
    }

    /**
     * 诊断：先在会话锁内做容量预检与 USER/SYSTEM 原子写入，再登记有界异步任务。
     * 会话超限时不留半条消息；执行器拒绝时移除注册项并返回 429，绝不留下永远 QUEUED 的 ID。
     * 回复只说"已接受"，不承诺任何诊断结论。
     */
    private FlowResult diagnose(ConversationSession session, RouteDecision decision, String content, Instant requestedAt) {
        var mapping = diagnoses.map(decision.assetId(), content, requestedAt);
        if (mapping.goal().isEmpty()) throw new IllegalStateException("diagnosis goal rejected: " + mapping.reason());
        var goal = mapping.goal().get();
        DiagnosisRequest request = DiagnosisApplicationService.toRequest(goal);

        String reply = String.format(
                "已接受模拟诊断任务（assetId=%s, symptomCode=%s, asOf=%s）。诊断尚未完成，请使用 diagnosisId 查询执行状态。",
                request.assetId(), request.symptomCode(), request.asOf());
        String[] diagnosisId = new String[1];
        var userMessage = session.commitPlanned(Route.DIAGNOSE, () -> {
            var accepted = runs.submit(goal, session.sessionId());
            diagnosisId[0] = accepted.diagnosisId().toString();
            return new Interaction(decision.assetId(), content, reply);
        });
        return new FlowResult(202, Route.DIAGNOSE, userMessage.messageId(), UUID.fromString(diagnosisId[0]),
                reply, decision.assetId(), requestedAt, null, request);
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        if (content.length() > ConversationMessage.MAX_CONTENT) throw new IllegalArgumentException("content is too long");
    }

    private static void validateAssetHint(String assetIdHint) {
        if (assetIdHint == null || assetIdHint.isBlank()) return;
        Checks.id(assetIdHint, "assetId");
    }

    private String clarifyReply(String reason) {
        return switch (reason) {
            case "ASSET_MISSING" -> "请提供设备编号。当前仅支持 " + assetIds() + " 的模拟数据。";
            case "ASSET_CONFLICT" -> "消息中的设备与请求指定的设备不一致，请确认要查询或诊断的设备编号。";
            case "ASSET_AMBIGUOUS" -> "消息中出现了多个设备，请只指定一个要查询或诊断的设备编号。";
            case "SYMPTOM_MISSING" -> "请说明具体故障现象。当前仅覆盖“通信超时/通信中断”等有限模拟症状。";
            case "QUERY_KIND_MISSING" -> "请说明要查询出厂档案还是维修/更换历史。";
            default -> "请补充设备编号和故障现象。";
        };
    }

    private String unsupportedReply(String reason) {
        return switch (reason) {
            case "UNSUPPORTED_ASSET" -> "该设备未登记在当前 SIMULATED 演示目录中，不能复用其他设备的证据或数据。";
            case "UNSUPPORTED_SYMPTOM" -> "当前仅支持通信超时类模拟症状，其他故障未接入。";
            case "UNSUPPORTED_RULE" -> "当前没有适用于该设备/症状的模拟规则，无法给出诊断结论。";
            case "UNSUPPORTED_DANGEROUS" -> "不会执行设备控制、写入或刷写等实际操作；本服务仅提供只读的模拟查询与诊断。";
            default -> "当前能力范围仅包括受限模拟查询和通信超时诊断。";
        };
    }

    private String assetIds() {
        return catalog.all().stream()
                .map(SupportedScenarioCatalog.SupportedDiagnosis::assetId)
                .collect(Collectors.joining("、"));
    }
}