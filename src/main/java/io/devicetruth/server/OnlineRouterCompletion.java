package io.devicetruth.server;

import io.devicetruth.adapter.AgentScopeStep;
import io.devicetruth.application.ApplicationExceptions.RouterUnavailableException;
import io.devicetruth.application.LlmTaskRouter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;

/** 在线 Router 的服务端模型适配：连接参数只从私有文件/环境变量读取，HTTP 客户端不可传入。 */
final class OnlineRouterCompletion implements LlmTaskRouter.Completion {
    private final String reasoner;

    OnlineRouterCompletion(String reasoner) { this.reasoner = reasoner; }

    @Override public String complete(String system, String input) {
        Function<String, String> configuration = new LocalModelConfiguration();
        if ("qwen".equalsIgnoreCase(reasoner))
            return AgentScopeStep.qwen(required(configuration, "DASHSCOPE_API_KEY")).complete(system, input);
        if ("openai".equalsIgnoreCase(reasoner))
            return AgentScopeStep.openai(required(configuration, "OPENAI_API_KEY"),
                    required(configuration, "OPENAI_BASE_URL"),
                    required(configuration, "OPENAI_MODEL")).complete(system, input);
        throw new RouterUnavailableException();
    }

    private static String required(Function<String, String> configuration, String name) {
        String value = configuration.apply(name);
        if (value == null || value.isBlank()) throw new RouterUnavailableException();
        return value.trim();
    }

    /** 与 CLI 的在线 Reasoner 保持同样的读取优先级：私有文件非空值优先，环境变量补缺。 */
    private static final class LocalModelConfiguration implements Function<String, String> {
        private Properties properties;

        @Override public String apply(String name) {
            if (properties == null) load();
            String value = properties.getProperty(name);
            if (value == null || value.isBlank()) value = System.getenv(name);
            return value == null ? null : value.trim();
        }

        private void load() {
            var loaded = new Properties();
            try (var reader = Files.newBufferedReader(Path.of("devicetruth.local.properties"), StandardCharsets.UTF_8)) {
                loaded.load(reader);
            } catch (NoSuchFileException e) {
                // 私有文件可选，继续使用环境变量。
            } catch (IOException | IllegalArgumentException | SecurityException e) {
                throw new RouterUnavailableException();
            }
            properties = loaded;
        }
    }
}