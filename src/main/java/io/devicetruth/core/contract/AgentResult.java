package io.devicetruth.core.contract;

import java.util.List;

/**
 * 通用 Agent 答案（schemaVersion = 1）：服务器校验引用与风险后生成的公开视图。
 * 事实、可能性、建议与人工核验项分开表达；知识引用只说明“资料这么写”，
 * 不等于这台设备当前配置。dataNature 始终保留 SIMULATED/AUTHORIZED 标记。
 */
public record AgentResult(String summary, Category category, List<Claim> claims,
                          List<String> suggestions, List<String> uncertainties, List<String> limitations,
                          List<Reference> references, RiskLevel riskLevel, String safetyDisposition,
                          String dataNature) {
    /** 答案类别：手册解释 / 可能原因 / 已确认发现 / 需人工核验。 */
    public enum Category { KNOWLEDGE, POSSIBLE_CAUSES, CONFIRMED_FINDING, HUMAN_REVIEW }

    /** 断言确定性：推测 / 有引用支持 / 现场已确认。CONFIRMED 只允许出现在已核验的资产场景。 */
    public enum Certainty { POSSIBLE, SUPPORTED, CONFIRMED }

    /** 服务器判定的风险等级，模型自报不作为结论依据。 */
    public enum RiskLevel { LOW, MEDIUM, HIGH, PROHIBITED }

    /** 一条断言：必须绑定本 run 已取回的引用 ID；SUPPORTED/CONFIRMED 不允许零引用。 */
    public record Claim(String text, List<String> referenceIds, Certainty certainty) {
        public Claim {
            Checks.text(text, "claim.text");
            if (text.codePointCount(0, text.length()) > 2000) throw new IllegalArgumentException("claim too long");
            referenceIds = Checks.list(referenceIds);
            Checks.required(certainty, "claim.certainty");
        }
    }

    /** 可公开引用的来源：文档版本、章节/页码与片段原文摘要，用于回答与界面展示。 */
    public record Reference(String referenceId, String sourceType, String sourceId, String sourceVersion,
                            String chunkId, String title, Integer page, String section, String excerpt,
                            String dataNature) {
        public Reference {
            Checks.required(referenceId, "referenceId");
            Checks.id(sourceType, "sourceType");
            Checks.text(sourceId, "sourceId");
            Checks.text(sourceVersion, "sourceVersion");
            if (chunkId == null) throw new IllegalArgumentException("chunkId is required");
            Checks.text(title, "title");
            if (page != null && page < 1) throw new IllegalArgumentException("Invalid page");
            if (excerpt == null) excerpt = "";
            if (excerpt.codePointCount(0, excerpt.length()) > 2000)
                throw new IllegalArgumentException("excerpt too long");
            if (!"SIMULATED".equals(dataNature) && !"AUTHORIZED".equals(dataNature))
                throw new IllegalArgumentException("Invalid dataNature");
        }
    }

    public AgentResult {
        Checks.text(summary, "summary");
        if (summary.codePointCount(0, summary.length()) > 8000) throw new IllegalArgumentException("summary too long");
        Checks.required(category, "category");
        claims = Checks.list(claims);
        suggestions = Checks.list(suggestions);
        uncertainties = Checks.list(uncertainties);
        limitations = Checks.list(limitations);
        references = Checks.list(references);
        Checks.required(riskLevel, "riskLevel");
        Checks.text(safetyDisposition, "safetyDisposition");
        if (!"SIMULATED".equals(dataNature) && !"AUTHORIZED".equals(dataNature))
            throw new IllegalArgumentException("Invalid dataNature");
    }

    public List<String> referenceIds() {
        return references.stream().map(Reference::referenceId).toList();
    }
}
