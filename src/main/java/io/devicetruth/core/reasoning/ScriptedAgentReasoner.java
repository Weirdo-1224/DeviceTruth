package io.devicetruth.core.reasoning;

import io.devicetruth.core.contract.AgentResult.Certainty;
import io.devicetruth.core.contract.AgentResult.Claim;
import io.devicetruth.core.contract.AgentResult.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 脚本化通用 Agent 推理器：离线、确定性，只依据本轮上下文决定下一步，不读仓库也不看预期答案。
 * 策略顺序即“最小可信问答”：先检索已授权资料 → 线索不足时追问一次 → 基于已取回引用作答。
 * 补答后会因为“用户消息数 > 已检索次数”而重新检索，因此同一 run 的两轮上下文都能继续。
 */
public final class ScriptedAgentReasoner implements GeneralReasoner {
    public static final String SEARCH = "search_knowledge";
    private static final List<String> CLARIFY_TERMS = List.of("中断", "无法", "不能", "异常", "不工作", "故障", "报错");
    private static final List<String> KNOWN_DETAIL_TERMS = List.of("交流", "直流", "慢充", "快充");

    @Override public String name() { return "scripted"; }

    @Override public ActionProposal propose(Context context) {
        // 每一轮都先检索一次：补答后的新一轮会用新的补充内容重新检索，而不是沿用旧引用下结论
        if (!context.attempted(SEARCH)) return search(context);
        if (!context.askedUser() && needsClarification(context)) return ask(context);
        return answer(context);
    }

    private ActionProposal search(Context context) {
        String query = context.lastUserMessage();
        if (context.userMessageCount() > 1 && !query.contains(context.goalText()))
            query = context.goalText() + "\n补充：" + query;
        return new ActionProposal.CallTool(SEARCH, Map.of("query", query), "检索本项目已授权的知识资料");
    }

    private boolean needsClarification(Context context) {
        String goal = context.goalText();
        boolean symptom = CLARIFY_TERMS.stream().anyMatch(goal::contains);
        boolean enoughDetail = KNOWN_DETAIL_TERMS.stream().anyMatch(goal::contains);
        return symptom && !enoughDetail;
    }

    private ActionProposal ask(Context context) {
        return new ActionProposal.AskUser("这次充电中断发生在交流慢充还是直流快充？中断时大概的 SOC 是多少？",
                List.of("充电方式", "中断时的 SOC"));
    }

    private ActionProposal answer(Context context) {
        List<Reference> used = context.references().stream().limit(2).toList();
        if (used.isEmpty()) {
            // 空检索不是模型故障：如实说明没有资料，不编造引用
            return new ActionProposal.Answer("KNOWLEDGE",
                    "在已授权的项目资料中没有检索到可以引用的依据，无法给出有来源的结论。",
                    List.of(), List.of("补充故障现象、充电方式与设备型号后再检索"),
                    List.of("当前资料库缺少直接相关的章节"),
                    List.of("知识库未命中时不得编造文档、页码或结论"), List.of());
        }
        List<Claim> claims = new ArrayList<>();
        for (Reference reference : used)
            claims.add(new Claim(locate(reference) + "：" + excerpt(reference.excerpt()), List.of(reference.referenceId()),
                    Certainty.SUPPORTED));
        List<String> suggestions = new ArrayList<>();
        suggestions.add("按资料记录的排查顺序先收集充电桩日志与车辆侧记录");
        suggestions.add("需要确认具体配置或更换记录时，补充资产编号后再做设备事实核查");
        return new ActionProposal.Answer("POSSIBLE_CAUSES",
                "根据已检索到的项目资料，" + locate(used.getFirst()) + "说明了这类问题的排查顺序与常见原因。"
                        + "这些是资料记载的一般原理，不代表本台设备当前的配置或现场状态。",
                claims, suggestions, List.of("资料版本与适用车型需在具体资产上再次核对"),
                List.of("知识引用只支持一般原理，不能替代现场测量与资质人员的判断"),
                used.stream().map(Reference::referenceId).toList());
    }

    /** 引用定位：标题 + 章节/页码，便于用户回到原文。 */
    private static String locate(Reference reference) {
        StringBuilder text = new StringBuilder("《").append(reference.title()).append("》");
        if (reference.section() != null && !reference.section().isBlank()) text.append(' ').append(reference.section());
        else if (reference.page() != null) text.append(" 第 ").append(reference.page()).append(" 页");
        return text.toString();
    }

    private static String excerpt(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "…";
    }
}
