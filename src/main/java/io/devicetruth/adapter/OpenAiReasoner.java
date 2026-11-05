package io.devicetruth.adapter;

import io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure;
import java.util.function.Function;

/** OpenAI Chat Completions 兼容入口，从私有配置文件或环境变量读取连接设置。 */
public final class OpenAiReasoner extends StructuredReasoner {
    public OpenAiReasoner() { this(ModelConfiguration.local()); }
    OpenAiReasoner(Function<String,String> configuration) { super("openai",new LiveCompletion(configuration)); }

    private static final class LiveCompletion implements Completion {
        private final Function<String,String> configuration;
        private AgentScopeStep step;
        private LiveCompletion(Function<String,String> configuration) { this.configuration=configuration; }

        public String complete(String system,String input) {
            if(step==null) {
                String key=required("OPENAI_API_KEY");
                String baseUrl=required("OPENAI_BASE_URL");
                String model=required("OPENAI_MODEL");
                try { step=AgentScopeStep.openai(key,baseUrl,model); }
                catch(IllegalArgumentException e) { throw new ReasonerFailure("OPENAI_CONFIGURATION_INVALID"); }
            }
            return step.complete(system,input);
        }

        private String required(String name) {
            String value=configuration.apply(name);
            if(value==null||value.isBlank()) throw new ReasonerFailure(name+"_MISSING");
            return value.trim();
        }
    }
}
