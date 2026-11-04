package io.devicetruth.adapter;

import io.agentscope.core.formatter.ResponseFormat;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import java.time.Duration;
import java.util.List;

/** Exactly one model subscription; no Agent, Toolkit, filesystem or state access. */
public final class AgentScopeStep {
    private final Model model;
    private final Duration timeout;

    public AgentScopeStep(Model model, Duration timeout) {
        this.model = model;
        this.timeout = timeout;
    }

    public static AgentScopeStep qwen(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DASHSCOPE_API_KEY is required for --reasoner=qwen; online verification is BLOCKED_BY_ENV");
        }
        return new AgentScopeStep(DashScopeChatModel.builder().apiKey(apiKey)
                .modelName("qwen-plus").stream(false).enableThinking(false).build(), Duration.ofSeconds(45));
    }

    public String complete(String system, String input) {
        var messages = List.of(
                Msg.builder().name("protocol").role(MsgRole.SYSTEM).textContent(system).build(),
                Msg.builder().name("snapshot").role(MsgRole.USER).textContent(input).build());
        var responses = model.stream(messages, List.of(), GenerateOptions.builder()
                .temperature(0.0).responseFormat(ResponseFormat.jsonObject())
                .executionConfig(ExecutionConfig.builder().timeout(timeout).maxAttempts(1).build()).build())
                .collectList().block(timeout);
        if (responses == null) throw new IllegalStateException("Model returned no response");
        return responses.stream().flatMap(r -> r.getContent().stream())
                .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                .map(TextBlock::getText).reduce("", String::concat);
    }
}
