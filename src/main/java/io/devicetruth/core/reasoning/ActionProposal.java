package io.devicetruth.core.reasoning;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.devicetruth.core.contract.Checks;
import java.util.List;
import java.util.Map;

/** 推理器提出的动作：五种形态之一，按 "type" 字段做 JSON 多态反序列化。
 *  每种形态的紧缩构造函数都会做协议级校验，非法提案在解析处即被拒绝。 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ActionProposal.CallCapability.class, name = "CALL_CAPABILITY"),
    @JsonSubTypes.Type(value = ActionProposal.ProposeHypothesis.class, name = "PROPOSE_HYPOTHESIS"),
    @JsonSubTypes.Type(value = ActionProposal.ProposeDecision.class, name = "PROPOSE_DECISION"),
    @JsonSubTypes.Type(value = ActionProposal.RequestInformation.class, name = "REQUEST_INFORMATION"),
    @JsonSubTypes.Type(value = ActionProposal.Escalate.class, name = "ESCALATE")
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
}
