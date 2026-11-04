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

/** AgentScope 模型的最小封装：一次构建只对应一次模型订阅，不引入 Agent/Toolkit/文件系统等
 *  任何多余能力——本类是模型能触达系统的全部边界。 */
public final class AgentScopeStep {
    private final Model model;
    private final Duration timeout;

    public AgentScopeStep(Model model, Duration timeout) {
        this.model = model;
        this.timeout = timeout;
    }

    /** 用 API Key 构造 qwen-plus 订阅：非流式、关闭思考模式，45 秒超时。 */
    public static AgentScopeStep qwen(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DASHSCOPE_API_KEY is required for --reasoner=qwen; online verification is BLOCKED_BY_ENV");
        }
        return new AgentScopeStep(DashScopeChatModel.builder().apiKey(apiKey)
                .modelName("qwen-plus").stream(false).enableThinking(false).build(), Duration.ofSeconds(45));
    }

    /** 单次补全：system 放协议、user 放状态快照。温度 0（确定性）、JSON 输出模式、
     *  不重试、同步阻塞直到超时；从响应中提取全部文本块拼接返回。 */
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
