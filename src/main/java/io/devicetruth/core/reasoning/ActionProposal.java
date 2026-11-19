package io.devicetruth.core.reasoning;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.devicetruth.core.contract.AgentResult;
import io.devicetruth.core.contract.Checks;
import java.util.List;
import java.util.Map;

/** 推理器提出的动作：五种形态之一，按 "type" 字段做 JSON 多态反序列化。
 *  每种形态的紧缩构造函数都会做协议级校验，非法提案在解析处即被拒绝。
 *  通用 Agent 追加 CALL_TOOL/CALL_TOOLS/ASK_USER/ANSWER 四形态：查询由宿主校验执行，ANSWER 由服务器校验后落库。 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ActionProposal.CallCapability.class, name = "CALL_CAPABILITY"),
    @JsonSubTypes.Type(value = ActionProposal.ProposeHypothesis.class, name = "PROPOSE_HYPOTHESIS"),
    @JsonSubTypes.Type(value = ActionProposal.ProposeDecision.class, name = "PROPOSE_DECISION"),
    @JsonSubTypes.Type(value = ActionProposal.RequestInformation.class, name = "REQUEST_INFORMATION"),
    @JsonSubTypes.Type(value = ActionProposal.Escalate.class, name = "ESCALATE"),
    @JsonSubTypes.Type(value = ActionProposal.CallTool.class, name = "CALL_TOOL"),
    @JsonSubTypes.Type(value = ActionProposal.CallTools.class, name = "CALL_TOOLS"),
    @JsonSubTypes.Type(value = ActionProposal.AskUser.class, name = "ASK_USER"),
    @JsonSubTypes.Type(value = ActionProposal.Answer.class, name = "ANSWER")
})
public sealed interface ActionProposal {
    /** 调用只读能力：读取数据，但不能直接改变状态；targetEvidenceNeedIds 声明本调用针对哪些证据缺口。 */
    record CallCapability(String capabilityId, Map<String, String> arguments, String purpose,
                          List<String> targetEvidenceNeedIds) implements ActionProposal {
        public CallCapability {
            Checks.id(capabilityId, "capabilityId");
            arguments = Map.copyOf(Checks.required(arguments, "arguments"));
            Checks.text(purpose, "purpose");
            targetEvidenceNeedIds = Checks.list(targetEvidenceNeedIds);
        }
    }
    /** 提出假设：必须至少依赖一个已有事实或证据，不能凭空提出。 */
    record ProposeHypothesis(String statement, List<String> dependsOnFactIds,
                             List<String> dependsOnEvidenceIds) implements ActionProposal {
        public ProposeHypothesis {
            Checks.text(statement, "statement");
            dependsOnFactIds = Checks.list(dependsOnFactIds);
            dependsOnEvidenceIds = Checks.list(dependsOnEvidenceIds);
            if (dependsOnFactIds.isEmpty() && dependsOnEvidenceIds.isEmpty())
                throw new IllegalArgumentException("Hypothesis must reference dependencies");
        }
    }
    /** 请求下结论：引用规则、事实、证据与假设，由 DecisionGuard 校验后决定是否采纳。 */
    record ProposeDecision(String candidateRuleId, List<String> claimedFactIds,
                           List<String> claimedEvidenceIds, List<String> claimedHypothesisIds,
                           String adviceLevel, String rationale) implements ActionProposal {
        public ProposeDecision {
            Checks.id(candidateRuleId, "candidateRuleId");
            claimedFactIds = Checks.list(claimedFactIds);
            claimedEvidenceIds = Checks.list(claimedEvidenceIds);
            claimedHypothesisIds = Checks.list(claimedHypothesisIds);
            Checks.text(adviceLevel, "adviceLevel");
            Checks.text(rationale, "rationale");
        }
    }
    /** 请求人工补充信息：推理器判断调查缺少外部输入，运行将以此结束。 */
    record RequestInformation(String reason, List<String> missingFields) implements ActionProposal {
        public RequestInformation {
            Checks.text(reason, "reason"); missingFields = Checks.list(missingFields);
            if (missingFields.isEmpty()) throw new IllegalArgumentException("missingFields is required");
        }
    }
    /** 请求人工接管：推理器主动放弃自动结论，运行以 ESCALATE 结束。 */
    record Escalate(String reason) implements ActionProposal {
        public Escalate { Checks.text(reason, "reason"); }
    }
    /** 调用一个已注册的只读知识工具：参数允许嵌套对象（如 filters），键名与类型由工具模式约束。 */
    record CallTool(String toolName, Map<String, Object> arguments, String purpose) implements ActionProposal {
        public CallTool {
            Checks.id(toolName, "toolName");
            Checks.text(purpose, "purpose");
            // Map.copyOf 同时拒绝 null 值：模型不能用 null 冒充“已提供参数”。
            arguments = Map.copyOf(Checks.required(arguments, "arguments"));
        }

        /** Json 按键排序，调用目的不参与结果复用。 */
        public String signature() {
            String canonical = toolName + "|" + io.devicetruth.adapter.Json.write(arguments);
            try {
                byte[] bytes = canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
    /** 批量元素不携带动作多态信息，宿主校验后转换为 CallTool。 */
    record ToolCall(String toolName, Map<String, Object> arguments, String purpose) {
        public ToolCall {
            Checks.id(toolName, "toolName");
            Checks.text(purpose, "purpose");
            arguments = Map.copyOf(Checks.required(arguments, "arguments"));
        }
    }
    /** 一次提出少量相互独立的只读查询，由宿主逐项校验并执行。 */
    record CallTools(List<ToolCall> calls) implements ActionProposal {
        public CallTools {
            calls = List.copyOf(Checks.list(calls));
            if (calls.isEmpty() || calls.size() > 3) throw new IllegalArgumentException("calls size out of range");
        }
    }
    /** 向用户提出一个待答问题：问题的 ID/版本由服务器生成，模型只能给正文与所需字段。 */
    record AskUser(String question, List<String> requestedFields) implements ActionProposal {
        public AskUser {
            Checks.text(question, "question");
            requestedFields = Checks.list(requestedFields);
            if (requestedFields.isEmpty())
                throw new IllegalArgumentException("requestedFields is required");
        }
    }
    /** 提交答案草稿：引用必须指向本 run 已取回集合，风险等级由服务器重新判定。 */
    record Answer(String category, String summary, List<AgentResult.Claim> claims, List<String> suggestions,
                  List<String> uncertainties, List<String> limitations, List<String> referenceIds,
                  String subjectAssetId) implements ActionProposal {
        public Answer(String category, String summary, List<AgentResult.Claim> claims, List<String> suggestions,
                      List<String> uncertainties, List<String> limitations, List<String> referenceIds) {
            this(category, summary, claims, suggestions, uncertainties, limitations, referenceIds, null);
        }
        public Answer {
            Checks.text(category, "category");
            Checks.text(summary, "summary");
            claims = Checks.list(claims);
            suggestions = Checks.list(suggestions);
            uncertainties = Checks.list(uncertainties);
            limitations = Checks.list(limitations);
            referenceIds = Checks.list(referenceIds);
            if (subjectAssetId != null) Checks.id(subjectAssetId, "subjectAssetId");
        }
    }
}
