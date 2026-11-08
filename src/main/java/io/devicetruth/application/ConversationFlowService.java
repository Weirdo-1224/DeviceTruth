package io.devicetruth.application;


import io.devicetruth.core.contract.Checks;
import java.util.UUID;
import java.util.stream.Collectors;

/** 会话消息处理：先路由并做程序复核，再记录消息；M1 只真正交付 CLARIFY/UNSUPPORTED。 */
public final class ConversationFlowService {
    public record FlowResult(int status, Route route, UUID messageId, UUID diagnosisId, String reply, String assetId) {}

    private final ConversationService conversations;
    private final TaskRouter router;
    private final SupportedScenarioCatalog catalog;

    public ConversationFlowService(ConversationService conversations, TaskRouter router, SupportedScenarioCatalog catalog) {
        this.conversations = conversations;
        this.router = router;
        this.catalog = catalog;
    }

    public FlowResult handle(UUID sessionId, String content, String assetIdHint) {
        validateContent(content);
        validateAssetHint(assetIdHint);
        var session = conversations.require(sessionId);

        RouteDecision decision = router.decide(
                new TaskRouter.RouteRequest(content, session.currentAssetId(), assetIdHint));
        // M1 测试态：QUERY/DIAGNOSE 的真实处理分别由 M2/M3 实现；先于任何会话写入抛 501，避免留下半成品消息。
        if (decision.route() == Route.QUERY || decision.route() == Route.DIAGNOSE)
            throw new ApplicationExceptions.NotImplementedException(decision.route());

        String reply = decision.route() == Route.CLARIFY
                ? clarifyReply(decision.reason())
                : unsupportedReply(decision.reason());
        String assetToBind = decision.assetId() != null && catalog.supports(decision.assetId())
                ? decision.assetId() : null;
        // 容量预检、资产绑定与两条消息在同一会话锁内提交；429 时上下文和消息都保持原值。
        var userMessage = session.commitInteraction(decision.route(), assetToBind, content, reply);
        return new FlowResult(200, decision.route(), userMessage.messageId(), null, reply, decision.assetId());
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