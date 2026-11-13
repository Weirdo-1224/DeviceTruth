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
    private static final List<String> KNOWN_DETAIL_TERMS = List.of("交流", "直流", "慢充", "快充");
    private static final String ACTIVATE = "activate_skill";
    private static final String CONFIGURATION = "get_configuration";
    private static final String OBSERVATIONS = "get_observations";
    private static final java.util.regex.Pattern ATTRIBUTE =
            java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])([a-z][a-z0-9]*[A-Z][A-Za-z0-9]{0,63})(?![A-Za-z0-9_])");
    private static final java.util.regex.Pattern QUERY_DATE = java.util.regex.Pattern.compile("\\b[0-9]{4}-[0-9]{2}-[0-9]{2}\\b");

    @Override public String name() { return "scripted"; }

    @Override public ActionProposal propose(Context context) {
        if (context.activeSkill() == null && available(context, ACTIVATE) && !context.attempted(ACTIVATE)) {
            String normalized = context.goalText().replace("经常", "").replace("频繁", "");
            for (var skill : context.availableSkills())
                if (skill.tasks().stream().anyMatch(normalized::contains) || normalized.contains(skill.skillId()))
                    return new ActionProposal.CallTool(ACTIVATE, Map.of("skillId", skill.skillId()), "读取匹配任务的领域指引");
        }
        var attribute = ATTRIBUTE.matcher(context.goalText());
        boolean assetQuery = context.selectedAssetId() != null && attribute.find() && available(context, CONFIGURATION);
        if (assetQuery && !context.attempted(CONFIGURATION))
            return new ActionProposal.CallTool(CONFIGURATION, Map.of("attribute", attribute.group(1)), "按任务时点查询登记配置");
        var historical = QUERY_DATE.matcher(context.goalText());
        if (assetQuery && historical.find() && java.util.Collections.frequency(context.attemptedTools(), CONFIGURATION) == 1)
            return new ActionProposal.CallTool(CONFIGURATION, Map.of("attribute", attribute.group(1),
                    "asOf", historical.group() + "T00:00:00Z"), "核对问题中指定的历史时点");
        // 每一轮都先检索一次：补答后的新一轮会用新的补充内容重新检索，而不是沿用旧引用下结论
        if (!assetQuery && !context.attempted(SEARCH) && available(context, SEARCH)) return search(context);
        if (context.activeSkill() != null && available(context, OBSERVATIONS) && !context.attempted(OBSERVATIONS))
            return new ActionProposal.CallTool(OBSERVATIONS, Map.of(), "核查该任务是否已有观测记录");
        if (assetQuery) return configurationAnswer(context);
        if (!context.askedUser() && needsClarification(context)) return ask();
        return answer(context);
    }

    private static boolean available(Context context, String tool) {
        return context.tools().stream().anyMatch(spec -> tool.equals(spec.name()));
    }

    private ActionProposal configurationAnswer(Context context) {
        List<Reference> records = context.references().stream()
                .filter(ref -> List.of("CONFIGURATION", "FIELD_OBSERVATION").contains(ref.sourceType())).limit(8).toList();
        var status = context.toolResults().stream().filter(result -> CONFIGURATION.equals(result.toolName()))
                .map(ToolResult::status).findFirst().orElse(Map.of());
        String confidence = "FIELD_CONFIRMED".equals(status.get("installationStatus"))
                ? "台账中有现场核验记录，请核对其有效期和适用范围。"
                : "当前装机配置仍待现场核验，出厂、改造与模板记录应分别对照来源和有效期。";
        if ("CONFLICT".equals(status.get("conflict"))) confidence = "配置记录存在冲突。" + confidence;
        List<Claim> claims = records.stream().map(ref -> new Claim(sourceLabel(ref.excerpt()) + ref.excerpt(), List.of(ref.referenceId()),
                Certainty.SUPPORTED)).toList();
        return new ActionProposal.Answer("HUMAN_REVIEW", "按任务时点读取设备配置。" + confidence,
                claims, List.of("请由现场设备负责人核对版本与记录适用范围"), List.of(confidence),
                observationLimits(context), records.stream().map(Reference::referenceId).toList());
    }

    private static String sourceLabel(String excerpt) {
        if (excerpt.contains(";source=FACTORY;")) return "出厂记录：";
        if (excerpt.contains(";source=REPLACEMENT;")) return "改造记录：";
        if (excerpt.contains(";source=CONFIGURATION;")) return "配置模板记录：";
        return "";
    }

    private ActionProposal search(Context context) {
        String query = context.lastUserMessage();
        if (context.userMessageCount() > 1 && !query.contains(context.goalText()))
            query = context.goalText() + "\n补充：" + query;
        return new ActionProposal.CallTool(SEARCH, Map.of("query", query), "检索本项目已授权的知识资料");
    }

    private boolean needsClarification(Context context) {
        String goal = context.goalText();
        boolean symptom = goal.contains("充电") && List.of("中断", "无法", "不能", "异常", "故障")
                .stream().anyMatch(goal::contains);
        boolean enoughDetail = KNOWN_DETAIL_TERMS.stream().anyMatch(goal::contains);
        return symptom && !enoughDetail;
    }

    private ActionProposal ask() {
        return new ActionProposal.AskUser("这次充电中断发生在交流慢充还是直流快充？是否只在特定充电桩发生？中断时大概的 SOC 是多少？",
                List.of("充电方式", "是否同一充电桩", "中断时的 SOC"));
    }

    private ActionProposal answer(Context context) {
        List<Reference> used = context.references().stream().limit(2).toList();
        if (context.goalText().contains("高压") && context.goalText().contains("安全边界"))
            return new ActionProposal.Answer("HUMAN_REVIEW", "高压设备检修必须由具备资质的人员按现场规程处理。",
                    List.of(), List.of(), List.of("尚需现场人员核实设备条件和作业许可"),
                    observationLimits(context), used.stream().map(Reference::referenceId).toList());
        if (used.isEmpty()) {
            // 空检索不是模型故障：如实说明没有资料，不编造引用
            return new ActionProposal.Answer("KNOWLEDGE",
                    "在已授权的项目资料中没有检索到可以引用的依据，无法给出有来源的结论。",
                    List.of(), List.of("补充故障现象、发生条件与设备型号后再检索"),
                    List.of("当前资料库缺少直接相关的章节"),
                    limitations(context, "知识库未命中时不得编造文档、页码或结论"), List.of());
        }
        List<Claim> claims = new ArrayList<>();
        for (Reference reference : used)
            claims.add(new Claim(locate(reference) + "：" + excerpt(reference.excerpt()), List.of(reference.referenceId()),
                    Certainty.SUPPORTED));
        List<String> suggestions = new ArrayList<>();
        suggestions.add("按资料记录的排查顺序收集现场日志与设备记录");
        suggestions.add("需要确认具体配置或更换记录时，补充资产编号后再做设备事实核查");
        return new ActionProposal.Answer("POSSIBLE_CAUSES",
                "根据已检索到的项目资料，" + locate(used.getFirst()) + "说明了这类问题的排查顺序与常见原因。"
                        + "这些是资料记载的一般原理，不代表本台设备当前的配置或现场状态。",
                claims, suggestions, List.of("资料版本与适用设备需在具体资产上再次核对"),
                limitations(context, "知识引用只支持一般原理，不能替代现场测量与资质人员的判断"),
                used.stream().map(Reference::referenceId).toList());
    }

    private static List<String> limitations(Context context, String first) {
        var result = new ArrayList<String>();
        result.add(first);
        result.addAll(observationLimits(context));
        return result;
    }

    private static List<String> observationLimits(Context context) {
        return context.toolResults().stream().filter(result -> OBSERVATIONS.equals(result.toolName()))
                .map(result -> result.status().get("message")).filter(java.util.Objects::nonNull).toList();
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
