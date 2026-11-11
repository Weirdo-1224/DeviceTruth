package io.devicetruth.adapter;

import io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure;
import java.util.function.Function;

/**
 * 通用 Agent 的在线供应商连接：复用既有 ModelConfiguration 的“私有文件优先、环境补缺”契约，
 * 不扩大读取范围，也不把密钥写入日志或报告。缺少密钥时立即以稳定错误码失败，
 * 由运行标记为 FAILED，而不是悄悄降级到离线脚本。
 */
public final class AgentModelConnections {
    private AgentModelConnections() {}

    public static StructuredAgentReasoner qwen() {
        return new StructuredAgentReasoner("qwen", new QwenCompletion());
    }

    public static StructuredAgentReasoner openai() {
        return new StructuredAgentReasoner("openai", new OpenAiCompletion(ModelConfiguration.local()));
    }

    private static final class QwenCompletion implements StructuredAgentReasoner.Completion {
        private final Function<String, String> configuration = ModelConfiguration.local();
        private AgentScopeStep step;

        @Override public String complete(String system, String input) {
            if (step == null) {
                String key = configuration.apply("DASHSCOPE_API_KEY");
                if (key == null || key.isBlank()) throw new ReasonerFailure("DASHSCOPE_API_KEY_MISSING");
                step = AgentScopeStep.qwen(key);
            }
            return step.complete(system, input);
        }
    }

    private static final class OpenAiCompletion implements StructuredAgentReasoner.Completion {
        private final Function<String, String> configuration;
        private AgentScopeStep step;

        private OpenAiCompletion(Function<String, String> configuration) { this.configuration = configuration; }

        @Override public String complete(String system, String input) {
            if (step == null) {
                String key = required("OPENAI_API_KEY");
                String baseUrl = required("OPENAI_BASE_URL");
                String model = required("OPENAI_MODEL");
                try {
                    step = AgentScopeStep.openai(key, baseUrl, model);
                } catch (IllegalArgumentException e) {
                    throw new ReasonerFailure("OPENAI_CONFIGURATION_INVALID");
                }
            }
            return step.complete(system, input);
        }

        private String required(String name) {
            String value = configuration.apply(name);
            if (value == null || value.isBlank()) throw new ReasonerFailure(name + "_MISSING");
            return value.trim();
        }
    }
}
