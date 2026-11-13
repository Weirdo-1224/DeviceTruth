package io.devicetruth.core.reasoning;

import io.devicetruth.core.state.DiagnosisState;
import io.devicetruth.core.contract.Models.EvidenceRecord;
import io.devicetruth.core.capability.CapabilityTypes.Descriptor;
import java.math.BigDecimal;
import java.util.List;

/** 推理器接口：每轮迭代基于当前状态快照提出恰好一个动作。
 *  实现方（scripted / qwen / openai）只"提议"，能否执行由编排器与守卫决定。 */
public interface Reasoner {
    ActionProposal propose(Context context);
    String name();
    /** 取出并清空本轮的尝试记录（如模型调用的成败），供运行事件流使用。 */
    default List<Attempt> drainAttempts() { return List.of(); }
    /** 供应商返回的实际用量。字段未报告时保留 null。 */
    record TokenUsage(Long inputTokens, Long outputTokens, BigDecimal cost) {
        public TokenUsage(long inputTokens, long outputTokens) { this(inputTokens, outputTokens, null); }
        public TokenUsage {
            if ((inputTokens != null && inputTokens < 0) || (outputTokens != null && outputTokens < 0)
                    || (cost != null && cost.signum() < 0)) throw new IllegalArgumentException("Invalid model usage");
        }
    }

    /** 单次尝试审计。耗时或模型标识未知时为 null。 */
    record Attempt(int number, String outcome, Long durationMs, String model, TokenUsage usage, boolean retryable) {
        public Attempt(int number, String outcome) { this(number, outcome, null); }
        public Attempt(int number, String outcome, Long durationMs) {
            this(number, outcome, durationMs, null, null, false);
        }
    }
    /** 提供给推理器的上下文：完整状态快照、能力目录、当前已取回的全部证据。 */
    record Context(DiagnosisState state, List<Descriptor> capabilities, List<EvidenceRecord> evidence) {
        public Context { capabilities=List.copyOf(capabilities); evidence=List.copyOf(evidence); }
    }
}
