package io.devicetruth.core.decision;

import io.devicetruth.core.contract.AgentResult;
import io.devicetruth.core.contract.AgentResult.Claim;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput;
import io.devicetruth.core.reasoning.ActionProposal.Answer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 通用答案守卫：模型只能提交草稿，引用成立性、风险等级与“是否可确诊”由服务器判定。
 * <ul>
 *   <li>引用必须是本 run 已成功取回集合的子集，草稿与 claim 中的引用都校验，且都进入答案引用列表；</li>
 *   <li>没有服务器判定的现场核验时，任何 CONFIRMED 断言、CONFIRMED_FINDING、完成时态确诊表述，
 *       或针对当前设备的确定性故障断言一律拒绝，知识引用只能支持一般知识与可能原因；</li>
 *   <li>含设备控制动作的句子只有两类可放行：禁止性安全提示，或“必须由具备资质人员执行”的边界声明，
 *       其余一律拒绝并要求模型重答；uncertainties/limitations 同样检查；</li>
 *   <li>高风险答案附服务端受控的安全提示，不展示模型生成的具体操作步骤；</li>
 *   <li>dataNature 取保守值：只有全部引用都是 AUTHORIZED 才标 AUTHORIZED，混合或无引用一律 SIMULATED。</li>
 * </ul>
 */
public final class AnswerGuard {
    /** 仅提及安全边界时降为 HIGH，并交给有资质人员现场处理。 */
    private static final Set<String> HAZARD_TERMS = Set.of("上电", "短接", "拆装", "绝缘", "高压", "互锁", "电池包", "热失控");
    /** 设备控制/危险执行动作：只允许出现在禁止性提示或资质边界声明中。 */
    private static final Set<String> CONTROL_ACTIONS = Set.of("短接", "上电", "强制闭合", "绕过互锁", "解除互锁",
            "拆除绝缘", "断开绝缘", "拆装电池包", "拆装", "拆除", "屏蔽保护", "强制复位", "越权");
    /** 禁止性安全提示：动作与这些词同子句出现时是“告知不得做”，可以展示。 */
    private static final Set<String> PROHIBITIVE_MARKERS = Set.of("不得", "禁止", "不要", "不能", "不可", "不应",
            "请勿", "勿", "严禁", "避免", "不允许", "切勿");
    /** 资质边界声明的引导词：必须与 QUALIFIED_ACTORS 同时出现才算安全语境。 */
    private static final Set<String> BOUNDARY_LEADS = Set.of("必须由", "应由", "须由", "只能由");
    /** 资质边界声明的执行主体。 */
    private static final Set<String> QUALIFIED_ACTORS = Set.of("具备资质", "有资质", "专业人员", "授权人员", "厂家");
    /** 祈使/顺序执行标记：出现时该子句是“要你去做”的指令，不能被资质边界句里的列举语义覆盖。 */
    private static final Set<String> IMPERATIVE_MARKERS = Set.of("请", "立即", "马上", "先", "再", "接着",
            "随后", "然后", "务必", "直接", "动手", "执行");
    /** 服务端受控的高风险提示：对高风险处置不展示模型生成的具体步骤。 */
    private static final String SERVER_SAFETY_NOTICE =
            "涉及高压等高风险处置：本系统不提供具体操作步骤，必须由具备资质的专业人员按厂家规程断电、验电后现场处理。";
    /** 完成时态确诊表述：类别和 certainty 枚举之外，正文这样写同样等于“声称已确诊”。 */
    private static final Set<String> CONFIRMATION_TERMS = Set.of("已确诊", "确诊为", "已确认", "确认为",
            "已确定", "已查明", "已证实");
    /** 确诊表述的软化语境：否定、待查或建议现场核实，不构成“已确诊”陈述。 */
    private static final Set<String> CONFIRMATION_SOFTENERS = Set.of("未", "不", "无", "尚", "待",
            "难", "疑似", "可能", "需", "建议");
    /** 当前设备的指代表述：与确定性结论同时出现时等于在断言眼前设备的故障。 */
    private static final Set<String> DEVICE_REFERENTS = Set.of("该设备", "该车辆", "该车", "此设备", "此车",
            "本设备", "本车", "当前设备", "当前车辆", "这台设备", "这台车", "这辆车", "设备本身", "车辆本身");
    /** 确定性故障结论：与设备指代同时出现且无缓和词时，当作“已确认的设备故障”拒绝。 */
    private static final Set<String> DEFINITE_CONCLUSIONS = Set.of("是", "为", "导致", "造成", "损坏", "失效",
            "故障", "漏电", "击穿", "断裂", "卡死", "粘连", "过热", "开路", "短路");
    /** 缓和词：可能原因与一般知识表述带有这些词时不算确定性断言。 */
    private static final Set<String> TENTATIVE_MARKERS = Set.of("可能", "疑似", "之一", "常见", "通常", "一般",
            "倾向", "建议", "待", "尚未", "不能", "无法", "需", "如果", "或");
    /** 句切分：以句号/分号/换行/感叹号/问号为界，避免跨句误判安全语义。 */
    private static final String SENTENCE_DELIMITERS = "[。；;！!？?\\n]";
    /** 子句切分：逗号/顿号/转折词/冒号之后的子句要单独判断，
     *  “不要短接互锁端子，随后短接高压互锁端子”不能靠前一个子句的禁止词整体放行。 */
    private static final String CLAUSE_DELIMITERS = "[：:，,、]|但(?:是)?|然而|不过|却";
    /** 确定性故障完成时态：即使没有设备指代，也是在断言“已经是这个故障”，无现场核验一律拒绝。 */
    private static final Set<String> DEFINITE_COMPLETIONS = Set.of("已经损坏", "已损坏", "损坏了", "已经失效",
            "已失效", "失效了", "已经击穿", "已击穿", "已经断裂", "已断裂", "已经卡死", "已卡死", "已经粘连",
            "已粘连", "已经短路", "已短路", "故障原因是", "原因就是", "确定是", "必然是", "一定是");

    /** 校验结论：拒绝时只带稳定错误码，不把草稿正文回传。 */
    public record Verdict(boolean allowed, String code, AgentResult answer) {
        public static Verdict rejected(String code) { return new Verdict(false, code, null); }
        public static Verdict accepted(AgentResult answer) { return new Verdict(true, "OK", answer); }
    }

    /** M1 契约入口：没有现场核验能力时始终按“未核验”处理。 */
    public Verdict review(AgentTaskInput input, List<Reference> collected, Answer draft) {
        return review(input, collected, draft, false);
    }

    /**
     * @param fieldVerified 由服务器根据“已授权资产 + 现场核验记录”判定，模型不能提供；
     *                      M1 尚无资产核验能力，调用方必须传 false，03-01 才接入真实核验。
     */
    public Verdict review(AgentTaskInput input, List<Reference> collected, Answer draft, boolean fieldVerified) {
        var known = new LinkedHashMap<String, Reference>();
        for (Reference reference : collected) known.put(reference.referenceId(), reference);
        // 1) 引用必须是本 run 已取回集合的子集：伪造或跨 run 的引用直接拒绝，草稿与 claim 都查
        for (String referenceId : draft.referenceIds())
            if (!known.containsKey(referenceId)) return Verdict.rejected("INVALID_CITATION");
        for (Claim claim : draft.claims()) {
            for (String referenceId : claim.referenceIds())
                if (!known.containsKey(referenceId)) return Verdict.rejected("INVALID_CITATION");
            if (claim.certainty() != AgentResult.Certainty.POSSIBLE && claim.referenceIds().isEmpty())
                return Verdict.rejected("INVALID_CITATION");
        }
        // 2) 类别必须是冻结枚举之一，未知类别不猜测
        AgentResult.Category category;
        try {
            category = AgentResult.Category.valueOf(draft.category().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Verdict.rejected("INVALID_ANSWER");
        }
        // 3) 危险执行建议：逐句逐子句判断，含动作的句子只允许禁止性提示或资质边界声明
        if (recommendsControlAction(draft.summary()) || draft.claims().stream().anyMatch(claim -> recommendsControlAction(claim.text()))
                || draft.suggestions().stream().anyMatch(AnswerGuard::recommendsControlAction)
                || draft.uncertainties().stream().anyMatch(AnswerGuard::recommendsControlAction)
                || draft.limitations().stream().anyMatch(AnswerGuard::recommendsControlAction))
            return Verdict.rejected("UNSAFE_ANSWER");
        // 4) 没有服务器判定的现场核验时，确诊路径一律关闭：CONFIRMED 枚举、完成时态确诊表述、
        //    以及针对当前设备的确定性故障断言都拒绝，模型只能给出带引用的可能原因
        if (!fieldVerified && (category == AgentResult.Category.CONFIRMED_FINDING
                || draft.claims().stream().anyMatch(claim -> claim.certainty() == AgentResult.Certainty.CONFIRMED)
                || assertsConfirmedFinding(draft.summary())
                || draft.claims().stream().anyMatch(claim -> assertsConfirmedFinding(claim.text()))
                || draft.suggestions().stream().anyMatch(AnswerGuard::assertsConfirmedFinding)
                || draft.uncertainties().stream().anyMatch(AnswerGuard::assertsConfirmedFinding)
                || draft.limitations().stream().anyMatch(AnswerGuard::assertsConfirmedFinding)
                || assertsDeviceDefect(draft.summary())
                || draft.claims().stream().anyMatch(claim -> assertsDeviceDefect(claim.text()))
                || draft.suggestions().stream().anyMatch(AnswerGuard::assertsDeviceDefect)
                || draft.uncertainties().stream().anyMatch(AnswerGuard::assertsDeviceDefect)
                || draft.limitations().stream().anyMatch(AnswerGuard::assertsDeviceDefect)))
            return Verdict.rejected("UNCONFIRMED_FINDING");
        String text = draft.summary() + " " + draft.claims().stream().map(Claim::text).toList() + " "
                + String.join(" ", draft.suggestions());
        // 5) 风险由服务器判定：模型自报的“低风险”不作数
        AgentResult.RiskLevel risk = risk(category, text);
        String disposition = risk == AgentResult.RiskLevel.HIGH ? "REQUIRES_QUALIFIED_PERSONNEL" : "NORMAL";
        List<String> limitations = new ArrayList<>(draft.limitations());
        if (category == AgentResult.Category.CONFIRMED_FINDING) disposition = "REQUIRES_FIELD_VERIFICATION";
        if (category == AgentResult.Category.HUMAN_REVIEW && "NORMAL".equals(disposition))
            disposition = "HUMAN_REVIEW_REQUIRED";
        if (input.selectedAssetId() != null && !fieldVerified)
            limitations.add("请求中的资产编号尚未经服务器核验，不能作为当前配置的依据。");
        if (referencesOf(draft, known).isEmpty())
            limitations.add("本次回答未使用项目知识资料，仅代表通用说明。");
        // 高风险处置附加服务端受控安全提示（幂等），最终展示由服务器说了算
        if (risk == AgentResult.RiskLevel.HIGH && !limitations.contains(SERVER_SAFETY_NOTICE))
            limitations.add(SERVER_SAFETY_NOTICE);
        // 6) 引用并集：草稿声明 + 各断言声明，去重后都返回，保证 claim 引用可在答案中找到
        List<Reference> used = referencesOf(draft, known);
        String dataNature = !used.isEmpty() && used.stream().allMatch(reference -> "AUTHORIZED".equals(reference.dataNature()))
                ? "AUTHORIZED" : "SIMULATED";
        return Verdict.accepted(new AgentResult(draft.summary(), category, draft.claims(), draft.suggestions(),
                draft.uncertainties(), limitations, used, risk, disposition, dataNature));
    }

    /** 引用并集：先草稿声明、后断言声明，按出现顺序去重，只保留本 run 已取回的对象。 */
    private static List<Reference> referencesOf(Answer draft, LinkedHashMap<String, Reference> known) {
        var ids = new LinkedHashSet<String>(draft.referenceIds());
        for (Claim claim : draft.claims()) ids.addAll(claim.referenceIds());
        var used = new ArrayList<Reference>(ids.size());
        for (String id : ids) {
            Reference reference = known.get(id);
            if (reference != null) used.add(reference);
        }
        return List.copyOf(used);
    }

    /**
     * 逐句逐子句判断：含设备控制动作的子句，只有禁止性安全提示
     * （“不要/严禁/不得短接…”）或资质边界声明（“必须由具备资质的专业人员…”）可放行；
     * 其余一律视为危险执行建议，由调用方要求模型重答，侥幸换措辞也不能绕过。
     */
    private static boolean recommendsControlAction(String text) {
        if (text == null || text.isBlank()) return false;
        for (String sentence : text.split(SENTENCE_DELIMITERS)) {
            // 资质边界声明覆盖整句时，只放行“名词性列举”的子句（如“任何涉及短接…的操作，都必须由…执行”）；
            // 祈使动作（“请短接…”/“随后短接…”）仍然拒绝，禁止词也只在自身子句内有效。
            boolean boundary = containsAny(sentence, BOUNDARY_LEADS) && containsAny(sentence, QUALIFIED_ACTORS);
            boolean nominalList = sentence.contains("涉及") || sentence.contains("任何")
                    || sentence.contains("包括") || sentence.contains("诸如");
            for (String clause : sentence.split(CLAUSE_DELIMITERS)) {
                if (!containsAny(clause, CONTROL_ACTIONS)) continue;
                if (containsAny(clause, PROHIBITIVE_MARKERS)) continue;
                if (containsAny(clause, BOUNDARY_LEADS) && containsAny(clause, QUALIFIED_ACTORS)) continue;
                // 资质边界句里的列举项（“…的操作”“…作业”）是名词性描述，不是执行指令；
                // 但只要子句带祈使/顺序标记（“请短接…”“随后短接…”），仍按危险指令拒绝
                if (boundary && nominalList && !containsAny(clause, IMPERATIVE_MARKERS)) continue;
                return true;
            }
        }
        return false;
    }

    /** 完成时态确诊表述：否定/待查/建议核实的软化语境除外，其余一律视为声称已确诊。
     *  逐子句判定，“尚未确诊为控制器损坏”这类否定性说明不得被当成已确诊。 */
    private static boolean assertsConfirmedFinding(String text) {
        if (text == null || text.isBlank()) return false;
        for (String sentence : text.split(SENTENCE_DELIMITERS)) {
            for (String clause : sentence.split(CLAUSE_DELIMITERS)) {
                boolean states = containsAny(clause, CONFIRMATION_TERMS) || clause.contains("确诊");
                if (!states) continue;
                if (containsAny(clause, CONFIRMATION_SOFTENERS)) continue;
                return true;
            }
        }
        return false;
    }

    /**
     * 当前设备的确定性故障断言：知识引用只能支持一般知识与可能原因，
     * “该设备的故障原因是 X”这类把资料当成眼前设备现场证据的陈述，没有现场核验一律拒绝；
     * 带“可能/疑似/常见/建议”等缓和词的可能原因表述不受影响。
     */
    private static boolean assertsDeviceDefect(String text) {
        if (text == null || text.isBlank()) return false;
        for (String sentence : text.split(SENTENCE_DELIMITERS)) {
            for (String clause : sentence.split(CLAUSE_DELIMITERS)) {
                // 完成时态故障断言：即使指代在别的子句里（“该设备可能有多种故障，充电控制器已经损坏”），
                // 只要本子句是“已经是这个故障”且没有缓和词，就等同声明确诊
                if (containsAny(clause, DEFINITE_COMPLETIONS) && !containsAny(clause, TENTATIVE_MARKERS)) return true;
                if (!containsAny(clause, DEVICE_REFERENTS)) continue;
                if (!containsAny(clause, DEFINITE_CONCLUSIONS)) continue;
                if (containsAny(clause, TENTATIVE_MARKERS)) continue;
                return true;
            }
        }
        return false;
    }

    /**
     * 追问安全边界（05-02 延期项）：模型提出的追问同样会直接展示给用户，
     * 不得夹带设备操作指令或“已确诊/已完成”类断言。越界由主循环拒绝该动作。
     */
    public static boolean unsafeFollowUp(String question, List<String> requestedFields) {
        if (recommendsControlAction(question) || assertsConfirmedFinding(question)) return true;
        if (requestedFields == null) return false;
        for (String field : requestedFields)
            if (recommendsControlAction(field) || assertsConfirmedFinding(field)) return true;
        return false;
    }

    private static AgentResult.RiskLevel risk(AgentResult.Category category, String text) {
        if (containsAny(text, HAZARD_TERMS)) return AgentResult.RiskLevel.HIGH;
        return switch (category) {
            case KNOWLEDGE -> AgentResult.RiskLevel.LOW;
            case POSSIBLE_CAUSES -> AgentResult.RiskLevel.MEDIUM;
            case CONFIRMED_FINDING, HUMAN_REVIEW -> AgentResult.RiskLevel.HIGH;
        };
    }

    private static boolean containsAny(String text, Set<String> terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
}
