package io.devicetruth.adapter;

import io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure;

/** 保留原有千问入口和补全注入契约，使用 DashScope qwen-plus。 */
public final class QwenReasoner extends StructuredReasoner {
    @FunctionalInterface public interface Completion { String complete(String system,String input); }

    public QwenReasoner() { this(new LiveCompletion()); }
    public QwenReasoner(Completion completion) { super("qwen",completion::complete); }

    private static final class LiveCompletion implements Completion {
        private final ModelConfiguration configuration=ModelConfiguration.local();
        private AgentScopeStep step;
        public String complete(String system,String input) {
            if(step==null) {
                String key=configuration.apply("DASHSCOPE_API_KEY");
                if(key==null||key.isBlank()) throw new ReasonerFailure("DASHSCOPE_API_KEY_MISSING");
                step=AgentScopeStep.qwen(key);
            }
            return step.complete(system,input);
        }
    }
}
