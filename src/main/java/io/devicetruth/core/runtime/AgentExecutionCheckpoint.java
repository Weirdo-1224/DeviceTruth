package io.devicetruth.core.runtime;

import io.devicetruth.core.reasoning.GeneralReasoner.ToolResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 已完成的执行进度。Run 行中的预算还可能包含执行中断的模型步骤。 */
public record AgentExecutionCheckpoint(int schemaVersion, UUID runId, int step, List<String> attemptedTools,
                                       Set<String> successfulCalls, List<ToolResult> toolResults,
                                       String feedback, int rejections, int noProgress, boolean finalizing) {
    public AgentExecutionCheckpoint(int schemaVersion, UUID runId, int step, List<String> attemptedTools,
                                    Set<String> successfulCalls, List<ToolResult> toolResults,
                                    String feedback, int rejections) {
        this(schemaVersion, runId, step, attemptedTools, successfulCalls, toolResults, feedback, rejections, 0, false);
    }
    public AgentExecutionCheckpoint {
        if (schemaVersion != 1 || runId == null || step < 0 || rejections < 0 || rejections > step
                || noProgress < 0 || noProgress > step)
            throw new IllegalArgumentException("Invalid execution checkpoint");
        attemptedTools = List.copyOf(attemptedTools);
        successfulCalls = Set.copyOf(successfulCalls);
        toolResults = List.copyOf(toolResults);
        if (attemptedTools.size() > step * 3 || successfulCalls.size() > attemptedTools.size()
                || successfulCalls.stream().anyMatch(hash -> !hash.matches("[a-f0-9]{64}"))
                || toolResults.size() > TaskOrchestrator.TOOL_RESULTS_MAX
                || toolResults.stream().mapToInt(TaskOrchestrator::resultLength).sum()
                    > TaskOrchestrator.TOOL_RESULTS_TOTAL_MAX_CHARS
                || (feedback != null && feedback.length() > 256))
            throw new IllegalArgumentException("Invalid execution checkpoint content");
    }
}
