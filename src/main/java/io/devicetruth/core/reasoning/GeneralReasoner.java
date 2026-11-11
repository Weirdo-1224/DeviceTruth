package io.devicetruth.core.reasoning;

import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput.Message;
import java.util.List;

/**
 * 通用 Agent 的推理器接口：每轮只提出一个动作（CALL_TOOL/ASK_USER/ANSWER）。
 * 实现方只“提议”，能否执行、引用是否成立、风险如何，全部由主机校验。
 * Context 由持久层重建：原始目标、完整消息序列、已授权工具目录与本 run 已取回引用。
 */
public interface GeneralReasoner {
    ActionProposal propose(Context context);

    String name();

    /** 取出并清空本轮尝试记录（模型调用成败），供运行事件审计。 */
    default List<Reasoner.Attempt> drainAttempts() { return List.of(); }

    /**
     * 单轮推理输入；attemptedTools 是本轮已尝试过的工具名（含失败，用于避免原地重试），
     * feedback 是上一次工具失败或答案被拒的稳定原因。
     */
    record Context(String goalText, List<Message> messages, List<ToolSpec> tools,
                   List<Reference> references, List<String> attemptedTools,
                   int stepsUsed, int maxSteps, String feedback) {
        public Context {
            messages = List.copyOf(messages);
            tools = List.copyOf(tools);
            references = List.copyOf(references);
            attemptedTools = List.copyOf(attemptedTools);
        }

        public boolean attempted(String toolName) { return attemptedTools.contains(toolName); }

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
