package io.devicetruth.core.reasoning;

import io.devicetruth.core.state.DiagnosisState;
import io.devicetruth.core.contract.Models.EvidenceRecord;
import io.devicetruth.core.capability.CapabilityTypes.Descriptor;
import java.util.List;

/** 推理器接口：每轮迭代基于当前状态快照提出恰好一个动作。
 *  实现方（scripted / qwen / openai）只"提议"，能否执行由编排器与守卫决定。 */
public interface Reasoner {
    ActionProposal propose(Context context);
    String name();
    /** 取出并清空本轮的尝试记录（如模型调用的成败），供运行事件流使用。 */
    default List<Attempt> drainAttempts() { return List.of(); }
    /** 单次尝试记录：第几次尝试 + 结果码。 */
    record Attempt(int number,String outcome) {}
    /** 提供给推理器的上下文：完整状态快照、能力目录、当前已取回的全部证据。 */
    record Context(DiagnosisState state, List<Descriptor> capabilities, List<EvidenceRecord> evidence) {
        public Context { capabilities=List.copyOf(capabilities); evidence=List.copyOf(evidence); }
    }
}
