package io.devicetruth.core.reasoning;

import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput.Message;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 通用 Agent 的推理器接口：每轮只提出一个动作（CALL_TOOL/CALL_TOOLS/ASK_USER/ANSWER）。
 * 实现方只“提议”，能否执行、引用是否成立、风险如何，全部由主机校验。
 * Context 由持久层重建：原始目标、完整消息序列、已授权工具目录与本 run 已取回引用。
 */
public interface GeneralReasoner {
    ActionProposal propose(Context context);

    /** 允许带内部重试的适配器在发起下一次请求前检查本次运行是否已结束。 */
    default ActionProposal propose(Context context, java.util.function.Supplier<String> abortCode) {
        return propose(context);
    }

    /** 真实模型可按 Run 剩余时间缩短单次等待；本地推理器无需使用该预算。 */
    default ActionProposal propose(Context context, java.util.function.Supplier<String> abortCode,
                                   java.util.function.LongSupplier remainingMillis) {
        return propose(context, abortCode);
    }

    String name();

    default boolean supportsAnswerReview() { return false; }
    default int deliveryReviewVersion() { return 1; }

    default io.devicetruth.core.contract.AnswerReview.Review reviewDelivery(
            io.devicetruth.core.contract.AgentTaskInput input, List<Reference> references,
            ActionProposal.Answer draft, java.util.function.Supplier<String> abortCode,
            java.util.function.LongSupplier remainingMillis) {
        return reviewAnswer(input, references, draft, abortCode, remainingMillis);
    }

    default io.devicetruth.core.contract.AnswerReview.Review reviewAnswer(
            io.devicetruth.core.contract.AgentTaskInput input, List<Reference> references,
            ActionProposal.Answer draft, java.util.function.Supplier<String> abortCode,
            java.util.function.LongSupplier remainingMillis) {
        throw new UnsupportedOperationException("Semantic review is not configured");
    }

    default ActionProposal.Answer correctAnswer(Context context, ActionProposal.Answer draft,
            io.devicetruth.core.contract.AnswerReview.Review review,
            java.util.function.Supplier<String> abortCode, java.util.function.LongSupplier remainingMillis) {
        throw new UnsupportedOperationException("Targeted correction is not configured");
    }

    /** 取出并清空本轮尝试记录（模型调用成败），供运行事件审计。 */
    default List<Reasoner.Attempt> drainAttempts() { return List.of(); }

    /** 已安装且启用的版本元数据。正文只在选中后读取。 */
    record SkillSummary(String skillId, String version, String description, List<String> devices,
                        List<String> tasks) {}
    /** Skill 及单篇参考资料都是不可信领域指引。 */
    record ActiveSkill(SkillSummary metadata, String content, List<String> evidence, List<String> risks,
                       List<String> citations, List<String> references, SkillReference reference) {}
    record SkillReference(String name, String content) {}
    /** 结构化工具返回的白名单状态；可引用的实际记录仍从 references 读取。 */
    record ToolResult(String toolName, String dataNature, Map<String, String> status,
                      @com.fasterxml.jackson.annotation.JsonInclude(
                              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String callSignature) {
        public ToolResult(String toolName, String dataNature, Map<String, String> status) {
            this(toolName, dataNature, status, null);
        }
    }

    /**
     * 单轮推理输入；attemptedTools 是本轮已尝试过的工具名（含失败，用于避免原地重试），
     * feedback 是上一次工具失败或答案被拒的稳定原因。
     */
    record Context(String goalText, List<Message> messages, List<ToolSpec> tools,
                   List<Reference> references, List<String> attemptedTools,
                   int stepsUsed, int maxSteps, String feedback, List<SkillSummary> availableSkills,
                   ActiveSkill activeSkill, String selectedAssetId, Instant asOf, List<ToolResult> toolResults,
                   List<Message> conversationHistory) {
        public Context(String goalText, List<Message> messages, List<ToolSpec> tools,
                       List<Reference> references, List<String> attemptedTools, int stepsUsed, int maxSteps,
                       String feedback, List<SkillSummary> availableSkills, ActiveSkill activeSkill,
                       String selectedAssetId, Instant asOf, List<ToolResult> toolResults) {
            this(goalText, messages, tools, references, attemptedTools, stepsUsed, maxSteps, feedback,
                    availableSkills, activeSkill, selectedAssetId, asOf, toolResults, List.of());
        }

        public Context(String goalText, List<Message> messages, List<ToolSpec> tools,
                       List<Reference> references, List<String> attemptedTools, int stepsUsed, int maxSteps,
                       String feedback) {
            this(goalText, messages, tools, references, attemptedTools, stepsUsed, maxSteps, feedback,
                    List.of(), null, null, null, List.of());
        }

        public Context {
            messages = List.copyOf(messages);
            conversationHistory = List.copyOf(conversationHistory);
            tools = List.copyOf(tools);
            references = List.copyOf(references);
            attemptedTools = List.copyOf(attemptedTools);
            availableSkills = List.copyOf(availableSkills);
            toolResults = List.copyOf(toolResults);
        }

        public boolean attempted(String toolName) { return attemptedTools.contains(toolName); }

        /** 检查点签名只用于 Runtime 与本地推理器，供应商只收到公开的工具摘要。 */
        public Context modelView() {
            return new Context(goalText, messages, tools, references, attemptedTools, stepsUsed, maxSteps,
                    feedback, availableSkills, activeSkill, selectedAssetId, asOf,
                    toolResults.stream().map(result -> new ToolResult(
                            result.toolName(), result.dataNature(), result.status())).toList(), conversationHistory);
        }

        /** 最近一条用户消息（首次为目标，补答后为补充内容）。 */
        public String lastUserMessage() {
            for (int index = messages.size() - 1; index >= 0; index--)
                if ("USER".equals(messages.get(index).role())) return messages.get(index).content();
            return goalText;
        }

        public long userMessageCount() {
            return messages.stream().filter(message -> "USER".equals(message.role())).count();
        }

        public boolean askedUser() {
            return messages.stream().anyMatch(message -> "ASSISTANT".equals(message.role()));
        }
    }
}
