package io.devicetruth.core.state;

import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.capability.CapabilityTypes.CapabilityResult;
import io.devicetruth.core.reasoning.ActionProposal;
import java.util.List;
import static io.devicetruth.core.contract.Checks.*;

/** 诊断状态快照：推理器每轮看到的完整世界视图，不可变，每次提交产生新版本。 */
public record DiagnosisState(String taskId, long version, DiagnosisRequest task, List<Fact> facts,
                             List<Hypothesis> hypotheses, List<EvidenceNeed> evidenceNeeds,
                             String selectedRuleId, List<String> selectedRuleFactIds,
                             List<ActionHistory> actionHistory, Budget budget, RunStatus status,
                             List<Rule> knownRules, List<EvidenceMetadata> discoveredEvidence) {
    public DiagnosisState {
        id(taskId,"taskId"); required(task,"task"); required(budget,"budget"); required(status,"status");
        if (version < 0) throw new IllegalArgumentException("Negative state version");
        facts=list(facts); hypotheses=list(hypotheses); evidenceNeeds=list(evidenceNeeds);
        selectedRuleFactIds=list(selectedRuleFactIds); actionHistory=list(actionHistory);
        knownRules=list(knownRules); discoveredEvidence=list(discoveredEvidence);
    }
    /** 单步行动历史：第几步、基于哪个状态版本、提案、执行结果及处理说明。 */
    public record ActionHistory(int step, long inputVersion, ActionProposal proposal, CapabilityResult observation,
                                String result) {}
    /** 初始状态：版本 0，仅任务与预算，一切列表为空，状态为进行中。 */
    public static DiagnosisState initial(String taskId, DiagnosisRequest task, Budget budget) {
        return new DiagnosisState(taskId,0,task,null,null,null,null,null,null,budget,RunStatus.ACTIVE,null,null);
    }
}
