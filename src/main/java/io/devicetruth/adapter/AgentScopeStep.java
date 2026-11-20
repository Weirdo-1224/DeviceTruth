package io.devicetruth.adapter;

import io.agentscope.core.formatter.ResponseFormat;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpResponse;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportFactory;
import io.devicetruth.core.reasoning.Reasoner.TokenUsage;
import java.math.BigDecimal;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Flux;

/** AgentScope 模型的最小封装：一次构建只对应一次模型订阅，不引入 Agent/Toolkit/文件系统等
 *  任何多余能力——本类是模型能触达系统的全部边界。 */
public final class AgentScopeStep {
    private final Model model;
    private final Duration timeout;
    private final UsageTransport transport;

    public AgentScopeStep(Model model, Duration timeout) {
        this(model, timeout, null);
    }

    private AgentScopeStep(Model model, Duration timeout, UsageTransport transport) {
        this.model = model;
        this.timeout = timeout;
        this.transport = transport;
    }

    /** 用 API Key 构造 qwen-plus 订阅：非流式、关闭思考模式，默认 45 秒且可被 Run 剩余预算缩短。 */
    public static AgentScopeStep qwen(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DASHSCOPE_API_KEY is required for --reasoner=qwen; online verification is BLOCKED_BY_ENV");
        }
        var transport = new UsageTransport("input_tokens", "output_tokens");
        return new AgentScopeStep(DashScopeChatModel.builder().apiKey(apiKey).httpTransport(transport)
                .modelName("qwen-plus").stream(false).enableThinking(false).build(), Duration.ofSeconds(45), transport);
    }

    /** baseUrl 包含服务所需的路径前缀，仅追加 /chat/completions，不自动添加 /v1。 */
    public static AgentScopeStep openai(String apiKey,String baseUrl,String modelName) {
        if(apiKey==null||apiKey.isBlank()||modelName==null||modelName.isBlank()||baseUrl==null||baseUrl.isBlank())
            throw new IllegalArgumentException("OPENAI_API_KEY, OPENAI_BASE_URL and OPENAI_MODEL are required");
        URI uri;
        try { uri=URI.create(baseUrl); }
        catch(IllegalArgumentException e) { throw new IllegalArgumentException("Invalid OPENAI_BASE_URL"); }
        if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null
                ||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getPath().replaceAll("/+$","").endsWith("/chat/completions"))
            throw new IllegalArgumentException("OPENAI_BASE_URL must be an HTTP(S) base URL without credentials, query, fragment or completion endpoint");
        var transport = new UsageTransport("prompt_tokens", "completion_tokens");
        return new AgentScopeStep(OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl).httpTransport(transport)
                .endpointPath("/chat/completions").modelName(modelName).stream(false).build(), Duration.ofSeconds(45), transport);
    }

    /** 单次补全：system 放协议、user 放状态快照。温度 0（确定性）、JSON 输出模式、
     *  不重试、同步阻塞直到超时；从响应中提取全部文本块拼接返回。 */
    public String complete(String system, String input) {
        return completeWithUsage(system, input).text();
    }

    StructuredAgentReasoner.CompletionResult completeWithUsage(String system, String input) {
        return completeWithUsage(system, input, timeout);
    }

    StructuredAgentReasoner.CompletionResult completeWithUsage(String system, String input, Duration remaining) {
        Duration requestTimeout = remaining.compareTo(timeout) < 0 ? remaining : timeout;
        if (requestTimeout.isZero() || requestTimeout.isNegative()) throw new IllegalStateException("Model deadline exhausted");
        if (transport != null) transport.reported = null;
        var messages = List.of(
                Msg.builder().name("protocol").role(MsgRole.SYSTEM).textContent(system).build(),
                Msg.builder().name("snapshot").role(MsgRole.USER).textContent(input).build());
        var options = GenerateOptions.builder().temperature(0.0).responseFormat(ResponseFormat.jsonObject())
                .executionConfig(ExecutionConfig.builder().timeout(requestTimeout).maxAttempts(1).build());
        // Flash 默认思考可耗尽整个单步期限；受控 JSON 单步显式使用非思考模式，不影响其他供应商。
        if ("deepseek-flash".equals(model.getModelName()))
            options.additionalBodyParam("thinking", java.util.Map.of("type", "disabled"));
        var responses = model.stream(messages, List.of(), options.build())
                .collectList().block(requestTimeout);
        if (responses == null) throw new IllegalStateException("Model returned no response");
        String text = responses.stream().flatMap(r -> r.getContent().stream())
                .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                .map(TextBlock::getText).reduce("", String::concat);
        if (transport != null)
            return new StructuredAgentReasoner.CompletionResult(text, model.getModelName(), transport.reported);
        // 直接注入的 Model 没有 HTTP 元数据，只能读 SDK 归一化后的 Token 数。
        var usage = responses.stream().map(r -> r.getUsage()).filter(java.util.Objects::nonNull)
                .reduce((previous, latest) -> latest).orElse(null);
        var tokens = usage == null || usage.getInputTokens() < 0 || usage.getOutputTokens() < 0
                || (usage.getInputTokens() == 0 && usage.getOutputTokens() == 0)
                ? null : new TokenUsage(usage.getInputTokens(), usage.getOutputTokens());
        return new StructuredAgentReasoner.CompletionResult(text, model.getModelName(), tokens);
    }

    /**
     * 保留 SDK 的网络实现，只从非流式响应提取用量。原始响应不缓存，避免保留正文、请求或凭据。
     * SDK 的 ChatUsage 不含费用，也无法区分字段缺失与 0。
     */
    private static final class UsageTransport implements HttpTransport {
        private static final ObjectReader READER = Json.MAPPER.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        private final HttpTransport delegate = HttpTransportFactory.getDefault();
        private final String inputKey;
        private final String outputKey;
        private volatile TokenUsage reported;

        private UsageTransport(String inputKey, String outputKey) {
            this.inputKey = inputKey;
            this.outputKey = outputKey;
        }

        @Override public HttpResponse execute(HttpRequest request) {
            HttpResponse response = delegate.execute(request);
            if (response.isSuccessful()) {
                try {
                    var usage = READER.readTree(response.getBody()).path("usage");
                    Long input = count(usage.path(inputKey));
                    Long output = count(usage.path(outputKey));
                    var amount = usage.path("cost");
                    BigDecimal cost = amount.isNumber() && amount.decimalValue().signum() >= 0
                            ? amount.decimalValue() : null;
                    if (input != null || output != null || cost != null) reported = new TokenUsage(input, output, cost);
                } catch (Exception ignored) {
                    // 用量缺失不影响 SDK 对同一响应的处理。
                }
            }
            return response;
        }

        private static Long count(JsonNode node) {
            return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0 ? node.longValue() : null;
        }

        @Override public Flux<String> stream(HttpRequest request) { return delegate.stream(request); }
        @Override public void close() { /* 共用的 SDK transport 由工厂关闭。 */ }
    }
}
