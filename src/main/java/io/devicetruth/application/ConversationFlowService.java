package io.devicetruth.application;

import io.devicetruth.application.ConversationSession.Interaction;
import io.devicetruth.core.contract.Checks;
import io.devicetruth.core.contract.Models.DiagnosisRequest;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会话消息处理：先路由并做程序复核，再执行受限处理并原子写入消息。
 * M2 真正交付 QUERY（只读事实查询）与 DIAGNOSE 的 Goal→Request 映射；诊断执行归 M3。
 */
public final class ConversationFlowService {
    public record FlowResult(int status, Route route, UUID messageId, UUID diagnosisId, String reply, String assetId,
                             Instant requestedAt, QueryAnswer queryAnswer, DiagnosisRequest diagnosisRequest) {
        static FlowResult text(Route route, UUID messageId, String reply, String assetId, Instant requestedAt) {
            return new FlowResult(200, route, messageId, null, reply, assetId, requestedAt, null, null);
        }
    }

    /** 一次诊断目标映射的可见结果：只证明"目标是怎样映射成 Core 请求的"，不声称已执行。 */
    public record DiagnosisPlanResult(Route route, String reply, String assetId, DiagnosisRequest request) {}

    private final ConversationService conversations;
    private final TaskRouter router;
    private final SupportedScenarioCatalog catalog;
    private final QueryService queries;
    private final DiagnosisApplicationService diagnoses;

    public ConversationFlowService(ConversationService conversations, TaskRouter router, SupportedScenarioCatalog catalog,
                                   QueryService queries, DiagnosisApplicationService diagnoses) {
        this.conversations = conversations;
        this.router = router;
        this.catalog = catalog;
        this.queries = queries;
        this.diagnoses = diagnoses;
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
            // M2 只验证 Goal→Request：不进入 Core，也不返回任何"已执行"的外观结果。
            case DIAGNOSE -> throw new ApplicationExceptions.NotImplementedException(Route.DIAGNOSE);
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
            return new Interaction(decision.assetId(), content, result.answer(), result);
        });
        return new FlowResult(200, Route.QUERY, userMessage.messageId(), null, answer[0].answer(),
                decision.assetId(), requestedAt, answer[0], null);
    }

    /**
     * 诊断路由的 Goal→Request 映射（M2 验收用）：
     * 不创建会话消息、不调用 Core，只返回映射结果，并明确说明诊断尚未执行。
     */
    public DiagnosisPlanResult planDiagnosis(UUID sessionId, String content) {
        validateContent(content);
        var session = conversations.require(sessionId);
        RouteDecision decision = router.decide(new TaskRouter.RouteRequest(content, session.currentAssetId(), null));
        if (decision.route() != Route.DIAGNOSE)
            throw new IllegalArgumentException("message does not map to a supported diagnosis");
        var mapping = diagnoses.map(decision.assetId(), content, Instant.now());
        if (mapping.goal().isEmpty())
            throw new IllegalArgumentException("diagnosis goal rejected: " + mapping.reason());
        var goal = mapping.goal().get();
        DiagnosisRequest request = DiagnosisApplicationService.toRequest(goal);
        String reply = String.format(
                "诊断目标已映射为 Core 请求（assetId=%s, domain=%s, symptomCode=%s, asOf=%s），诊断尚未执行。",
                request.assetId(), request.domain(), request.symptomCode(), request.asOf());
        return new DiagnosisPlanResult(Route.DIAGNOSE, reply, request.assetId(), request);
    }

    private String renderQueryReply(QueryAnswer answer) {
        return answer.answer();
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