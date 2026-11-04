---
title: Model
description: 在 AgentScope Java 中配置和接入 LLM 模型供应商
zh_link: /v2/zh/docs/building-blocks/model
---

## 概述

模型层将共享契约与供应商实现分离。`agentscope-core` 保留通用 API（`Model`、`ChatModelBase`、`Formatter`、`ModelRegistry` 以及 `ModelProvider` SPI）。OpenAI、DashScope、Gemini、Anthropic 和 Ollama 的实现位于各自的模型扩展模块中。

运行时模型层分为两级：上层是**凭证（Credentials）**（基于 `io.agentscope.core.credential`），承载供应商的 API 认证字段；下层是**Chat Model**，即挂载到凭证上的具体推理实现。

```text
CredentialBase/
└── ChatModelBase/
    ├── OpenAIChatModel
    ├── AnthropicChatModel
    ├── DashScopeChatModel
    ├── GeminiChatModel
    └── OllamaChatModel
```

**凭证（Credential）**承载供应商的 API 认证字段（`apiKey`、`baseUrl` 等）。从凭证出发，可以调用 `listModels()` 枚举该供应商下可用的模型（返回 `Mono<List<ModelCard>>`）。

这种分层与前端的自然交互流程一致——先注册凭证，再在其下挑选模型——因此 UI 只需一次认证，即可展示该供应商支持的所有模型。

## 模型扩展模块

供应商特有的模型实现已从 `agentscope-core` 迁移到独立的扩展模块。每个供应商模块拥有自己的 chat model、credential、formatter、DTO、异常和 SDK/API 客户端等。

| 供应商 | Maven 构件 | 主包 |
|----------|----------------|--------------|
| OpenAI | `agentscope-extensions-model-openai` | `io.agentscope.extensions.model.openai` |
| OpenAI 官方 | `agentscope-extensions-model-openai-official` | `io.agentscope.extensions.model.openaiofficial` |
| DashScope | `agentscope-extensions-model-dashscope` | `io.agentscope.extensions.model.dashscope` |
| Gemini | `agentscope-extensions-model-gemini` | `io.agentscope.extensions.model.gemini` |
| Anthropic | `agentscope-extensions-model-anthropic` | `io.agentscope.extensions.model.anthropic` |
| Ollama | `agentscope-extensions-model-ollama` | `io.agentscope.extensions.model.ollama` |

### 迁移清单

1. 添加供应商扩展模块依赖。以 DashScope 为例：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
</dependency>
```

其他供应商构件遵循同样模式：`agentscope-extensions-model-openai`、`agentscope-extensions-model-openai-official`、`agentscope-extensions-model-gemini`、`agentscope-extensions-model-anthropic` 和 `agentscope-extensions-model-ollama`。

2. 将供应商相关 import 从 `io.agentscope.core.model.*` 替换为 `io.agentscope.extensions.model.<provider>.*`。
3. 将供应商 formatter 的 import 从 `io.agentscope.core.formatter.<provider>.*` 替换为 `io.agentscope.extensions.model.<provider>.formatter.*`。
4. 对于 Spring Boot 应用，用对应的供应商专用 starter 及其 `agentscope.<provider>.*` 配置项替换通用模型创建路径。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-dashscope-spring-boot-starter</artifactId>
</dependency>
```

## 选择创建路径

### 字符串模型 id

对于简单的非 Spring 应用，使用 `ModelRegistry` 字符串 id，例如 `dashscope:qwen-plus`、`openai:gpt-4.1-mini` 或 `deepseek:deepseek-v4-flash`。添加对应的模型扩展模块，设置供应商的标准环境变量（如 `DASHSCOPE_API_KEY`、`OPENAI_API_KEY` 或 `DEEPSEEK_API_KEY`），然后把 id 直接传给 Agent：

```java
ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model("dashscope:qwen-plus") // 内部由 ModelRegistry.resolve(modelId) 解析
                .build();
```

扩展模块通过 Java SPI 自动发现。模型供应商读取其标准环境变量，如 `DASHSCOPE_API_KEY`、`OPENAI_API_KEY`、`DEEPSEEK_API_KEY`、`GLM_API_KEY`、`ANTHROPIC_API_KEY` 或 `GEMINI_API_KEY`。Ollama 在存在 `OLLAMA_BASE_URL` 时读取它，否则默认使用本地 Ollama 端点。

### 显式模型 builder

当需要自定义 API key、base URL、formatter、传输层、超时、生成选项或其他供应商特有配置时，显式构建模型并把 `Model` 实例传给 Agent：

```java
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();

ReActAgent agent =
        ReActAgent.builder()
                .name("assistant")
                .model(model)
                .build();
```

### Spring Boot 应用

对于 Spring Boot，推荐使用供应商专用 starter，如 `agentscope-openai-spring-boot-starter`、`agentscope-dashscope-spring-boot-starter`、`agentscope-gemini-spring-boot-starter`、`agentscope-anthropic-spring-boot-starter` 和 `agentscope-ollama-spring-boot-starter`。这些 starter 直接依赖对应的模型扩展，创建由 Spring 管理的 `Model` bean，让通用 starter 专注于 AgentScope 公共基础设施。它们不通过静态 `ModelRegistry` 创建模型；高级用户始终可以提供自己的 `Model` bean。

OpenAI 示例：

```yaml
agentscope:
  model:
    provider: openai
  openai:
    api-key: ${OPENAI_API_KEY}
    model-name: gpt-4.1-mini
    stream: true
```

#### Builder 自定义器（Builder customizers）

供应商专用 starter 还为自动配置的 chat model builder 暴露了有序的 Spring bean 自定义器。当属性绑定覆盖常用设置、但还需要调整仅限 builder 的选项时——如自定义 formatter、默认生成选项、代理/客户端设置或供应商特有开关——使用它们。

| Starter | 自定义器类型 |
|---------|-----------------|
| `agentscope-openai-spring-boot-starter` | `OpenAIChatModelBuilderCustomizer` |
| `agentscope-dashscope-spring-boot-starter` | `DashScopeChatModelBuilderCustomizer` |
| `agentscope-gemini-spring-boot-starter` | `GeminiChatModelBuilderCustomizer` |
| `agentscope-anthropic-spring-boot-starter` | `AnthropicChatModelBuilderCustomizer` |
| `agentscope-ollama-spring-boot-starter` | `OllamaChatModelBuilderCustomizer` |

自定义器 bean 在 starter 属性绑定之后、`builder.build()` 调用之前应用。支持多个自定义器，遵循 Spring 的 `@Order` / `Ordered` 排序。

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.spring.boot.openai.OpenAIChatModelBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
class ModelCustomizerConfiguration {

    @Bean
    @Order(0)
    OpenAIChatModelBuilderCustomizer openAIModelDefaults() {
        return builder ->
                builder.defaultOptions(
                        GenerateOptions.builder()
                                .temperature(0.2)
                                .parallelToolCalls(false)
                                .build());
    }
}
```

## ModelRegistry 与 ModelCreationContext

`ModelRegistry` 是模型实例创建与查找的全局注册表，支持多种解析策略。解析时按优先级依次尝试：通过 `ModelRegistry.register(name, model)` 直接注册的命名模型实例、通过 `registerFactory(regex, factory)` 注册的自定义工厂，以及通过 Java SPI 机制从扩展模块自动发现的 `ModelProvider` 实现。

简单场景优先使用 `provider:model` 格式的字符串 id 加供应商标准环境变量；需要精细控制时使用显式模型 builder。`ModelCreationContext` 主要面向需要动态解析模型的集成层代码。

### 高级集成上下文

`ModelCreationContext` 面向必须动态创建模型、又不想 import 具体供应商 builder 的集成层，例如多租户网关、插件系统或框架适配器。它可以向 SPI 供应商传递公共值，如 API key、base URL、端点路径、流模式，以及扩展定义的选项/组件：

```java
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;

ModelCreationContext context =
        ModelCreationContext.builder()
                .apiKey(tenantApiKey)
                .baseUrl(tenantBaseUrl)
                .stream(false)
                // 扩展定义的标量选项，键名以供应商文档为准。
                .option("contextWindowSize", 128000)
                // 按类型键控的组件，用于更丰富的供应商设置、传输层或 formatter。
                .component(
                        GenerateOptions.class,
                        GenerateOptions.builder()
                                .parallelToolCalls(false)
                                .build())
                .build();

Model model = ModelRegistry.resolve("openai:gpt-4.1-mini", context);
```

### 缓存策略

`ModelRegistry` 会缓存从简单 `provider:model` 字符串解析出的模型。携带上下文的创建默认不缓存，以避免复用带有其他租户 API key、base URL 或流设置的模型实例。

| 策略 | 行为 |
|--------|----------|
| `DEFAULT` | `resolve(String)` 保留旧版模型 id 缓存。`resolve(String, nonEmptyContext)` 不缓存。 |
| `DISABLED` | 永不缓存；每次解析都创建新模型实例。 |
| `ENABLED` | 仅在调用方显式选择时缓存。使用 `cacheId(...)` 指定租户或配置特有的标识。 |

如果在 `CachePolicy.ENABLED` 下使用 `option(...)` 或 `component(...)`，用户必须提供 `cacheId`。

### ModelProvider SPI

供应商扩展模块通过 `META-INF/services/io.agentscope.core.model.spi.ModelProvider` 以 Java SPI 方式被发现。供应商可以实现 `supports(String, ModelCreationContext)` 和 `create(String, ModelCreationContext)` 来消费上下文值。简单供应商可以继续实现原有的 `supports(String)` 和 `create(String)` 方法，因为上下文相关方法带有兼容的默认实现。

## Chat model

**Chat Model** 是驱动对话和工具调用的 LLM，输入输出可能涵盖多种模态。AgentScope Java 目前提供：

| 供应商 | 类 | 说明 |
|----------|-------|-------|
| OpenAI | `OpenAIChatModel` | Chat Completions API；兼容 vLLM 和 OpenAI 兼容端点（DeepSeek、Kimi 等） |
| OpenAI 官方 | `OpenAIResponsesChatModel` | 通过官方 SDK 走 Responses API；推理、结构化输出 |
| Anthropic | `AnthropicChatModel` | Claude 模型；提示词缓存和 thinking |
| DashScope | `DashScopeChatModel` | Qwen 模型；多模态（视觉/音频/视频）、推理 |
| Gemini | `GeminiChatModel` | Google Gemini；多模态 |
| Ollama | `OllamaChatModel` | 本地部署的 LLM；凭证可选 |

供应商凭证类随其模型扩展模块提供，例如 `OpenAICredential`、`OpenAIOfficialCredential`、`AnthropicCredential`、`DashScopeCredential`、`GeminiCredential` 和 `OllamaCredential`。OpenAI 兼容的凭证如 `DeepSeekCredential`、`KimiCredential` 和 `XAICredential` 仍保留在 core 中。

### 创建 chat model

每个 chat model 都用 builder 构建。最常见的字段是 `apiKey`、`modelName`、`stream`、`formatter`、`defaultOptions`。三种典型配置：


<Tabs>


<Tab title="流式 Streaming">

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();
```

</Tab>


<Tab title="工具 Tools">

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(false)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(
                        GenerateOptions.builder()
                                .parallelToolCalls(false)
                                .build())
                .build();
```

</Tab>


<Tab title="推理 Reasoning">

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen3-235b-a22b-thinking-2507")
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(
                        GenerateOptions.builder()
                                .thinkingBudget(2048)
                                .build())
                .build();
```

</Tab>


</Tabs>


常见 builder 字段：

| 字段 | 类型 | 说明 |
|-------|------|-------------|
| `apiKey` | `String` | API key（部分供应商也接受 `credential(...)`） |
| `modelName` | `String` | 模型标识（如 `"qwen-plus"`） |
| `stream` | `boolean` | 是否流式输出 |
| `defaultOptions` | `GenerateOptions` | 供应商特有选项（`temperature`、`maxTokens`、`thinkingBudget`、`parallelToolCalls` 等） |
| `formatter` | `Formatter` | 覆盖默认消息 formatter |
| `baseUrl` | `String` | 自定义服务端点（如 OpenAI 兼容代理） |

### 调用 chat model

`Model` 接口暴露统一的 `stream(messages, tools, options)`，返回 `Flux<ChatResponse>`：

```java
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import java.util.List;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();

model.stream(
                List.of(new UserMessage("Count from 1 to 5.")),
                /* tools = */ List.of(),
                GenerateOptions.builder().build())
        .doOnNext(chunk -> System.out.println("Chunk: " + chunk.getContent()))
        .doOnComplete(() -> System.out.println("Stream completed"))
        .blockLast();
```

`ChatResponse` 携带一个内容块列表（`TextBlock`、`ThinkingBlock`、`ToolUseBlock`、`DataBlock`）和一个记录 token 数与耗时的 `ChatUsage`。

实际使用中通常通过 `ReActAgent` 间接调用模型。轻量级直接调用示例见 `agentscope-examples/documentation/.../model/ModelRegistryExample.java`。

### 生成结构化输出

Agent 层提供便捷重载 `ReActAgent.call(msgs, structuredOutputClass, runtimeContext)`，把模型输出绑定到 Java POJO：

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;

public class WeatherInfo {
    public String city;
    public double temperature;
    public String unit;
}

Msg msg =
        agent.call(
                        List.of(new UserMessage("What's the weather in Shanghai?")),
                        WeatherInfo.class,
                        RuntimeContext.empty())
                .block();

WeatherInfo info = msg.getStructuredData(WeatherInfo.class);
```

工作原理：框架从目标类合成一次强制的结构化工具调用，校验并修复模型输出，把结果以 `structured_output` 键写入 `Msg.metadata`，于是 `getStructuredData(Class)` 可以直接反序列化。完整示例：`agentscope-examples/documentation/.../structuredoutput/StructuredOutputExample.java`。

#### 结构化输出路径选择

框架提供两条结构化输出路径：

| 路径 | 条件 | 机制 |
|------|-----------|-------|
| **原生** | `supportsNativeStructuredOutput() = true` | 使用 `response_format` + `json_schema` 直接输出 JSON |
| **回退**（默认） | `supportsNativeStructuredOutput() = false` | 注入 `generate_response` 合成工具；模型通过工具调用返回结构化数据 |

如果原生路径失败（如模型返回 HTTP 400），框架会**自动回退**到合成工具路径——无需用户干预。

#### 各供应商默认行为

| 供应商 | `supportsNativeStructuredOutput` | 说明 |
|----------|----------------------------------|-------|
| OpenAI（GPT-4o 等） | `true` | 原生支持 `json_schema` |
| OpenAI 官方（Responses API） | `true` | 原生支持 `json_schema` |
| OpenAI（DeepSeek/GLM formatter） | `false` | 不支持；自动回退 |
| DashScope | `false` | 原生端点仅支持 `json_object`，不支持 `json_schema`；默认回退 |
| Anthropic | `false`（默认） | — |

> **DashScope 用户**：thinking 模式（`enableThinking(true)`）完全不支持结构化输出——框架会强制走回退路径。

#### 显式配置

如果确认你的模型/端点支持 `json_schema`，可通过 builder 启用原生路径：

```java
DashScopeChatModel model = DashScopeChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-plus")
        .nativeStructuredOutput(true)  // 显式启用原生 json_schema 路径
        .build();
```

#### 结构化输出与工具调用并存

当 Agent 同时有工具和结构化输出时，部分 OpenAI 兼容供应商（如 Kimi、DeepSeek）会优先遵守 `response_format` 约束而完全跳过工具调用。设置 `nativeStructuredOutputWithTools(false)` 可解决：

```java
OpenAIChatModel model = OpenAIChatModel.builder()
        .apiKey("...")
        .baseUrl("https://api.moonshot.cn/v1")
        .modelName("moonshot-v1-8k")
        .nativeStructuredOutputWithTools(false)
        .build();
```

`DashScopeChatModel` 同样支持该选项。对于原生 OpenAI 模型（GPT-4o 等），默认行为即可正确处理两者——无需配置。

### Formatter

**Formatter** 把 AgentScope 的 `Msg` 对象转换为各供应商 API 所期望的请求负载。通过 chat model builder 的 `formatter(...)` 配置。每个供应商提供两种 formatter：

| 类型 | 适用场景 |
|------|----------|
| **ChatFormatter**（默认） | 标准单 Agent 对话。每个 `Msg` 一对一映射为一条 API 消息，保留角色（`USER`、`ASSISTANT`、`SYSTEM`）。 |
| **MultiAgentFormatter** | 多 Agent 场景，如辩论或主持人设置。连续的 Agent 消息被聚合，并标注发送者名称。 |

切换到多 Agent 模式只需传入 MultiAgent 变体——Agent 代码无需改动：

```java
import io.agentscope.extensions.model.dashscope.formatter.DashScopeMultiAgentFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

DashScopeChatModel model =
        DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .stream(true)
                .formatter(new DashScopeMultiAgentFormatter())
                .build();
```

各供应商 formatter 现随其供应商扩展模块提供：

| 供应商 | Chat | MultiAgent |
|----------|------|------------|
| DashScope | `DashScopeChatFormatter` | `DashScopeMultiAgentFormatter` |
| OpenAI | `OpenAIChatFormatter` | `OpenAIMultiAgentFormatter` |
| OpenAI 官方 | — | `ResponsesMultiAgentFormatter` |
| Anthropic | `AnthropicChatFormatter` | `AnthropicMultiAgentFormatter` |
| Gemini | `GeminiChatFormatter` | `GeminiMultiAgentFormatter` |
| Ollama | `OllamaChatFormatter` | `OllamaMultiAgentFormatter` |

如果你的供应商负载与这些都不匹配，实现 `Formatter<TReq, TResp, TParams>` 接口（`io.agentscope.core.formatter`），通过同一个 `formatter(...)` builder 传入。

### 自定义供应商

接入新供应商的最小路径：实现一个 `CredentialBase` 子类和一个 `ChatModelBase` 子类。

#### 第 1 步：定义凭证

继承 `CredentialBase` 并实现 `getChatModelClass()`：

```java
import io.agentscope.core.credential.CredentialBase;
import io.agentscope.core.model.ChatModelBase;

public class MyProviderCredential extends CredentialBase {

    private final String apiKey;
    private final String baseUrl;

    public MyProviderCredential(String apiKey, String baseUrl) {
        super("my_provider:" + apiKey.substring(0, Math.min(4, apiKey.length())));
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null ? "https://api.myprovider.com/v1" : baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public Class<? extends ChatModelBase> getChatModelClass() {
        return MyProviderChatModel.class;
    }
}
```

#### 第 2 步：实现 chat model

继承 `ChatModelBase` 并实现 `doStream`：

```java
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import reactor.core.publisher.Flux;

public class MyProviderChatModel extends ChatModelBase {

    private final MyProviderCredential credential;
    private final String modelName;

    public MyProviderChatModel(MyProviderCredential credential, String modelName) {
        this.credential = credential;
        this.modelName = modelName;
    }

    @Override
    protected Flux<ChatResponse> doStream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        // 调用供应商 API，把响应包装为 Flux<ChatResponse>。
        return Flux.empty();
    }
}
```

#### 第 3 步：注册到 ModelRegistry（可选）

`ModelRegistry` 让 `ReActAgent.builder().model("provider:model-name")` 能从字符串解析模型：

```java
import io.agentscope.core.model.ModelRegistry;

ModelRegistry.registerFactory(
        "myprov:.*",
        modelId -> new MyProviderChatModel(
                new MyProviderCredential(System.getenv("MYPROV_API_KEY"), null),
                modelId.substring("myprov:".length())));

// 之后：
// ReActAgent.builder().model("myprov:my-model-v1")...
```

## 前端集成

### 什么是 ModelCard

`ModelCard`（`credential/ModelCard.java`）是对模型能力和约束的声明式描述。它支撑前端——模型选择器、参数表单和能力开关可以基于它动态渲染，无需硬编码任何供应商特有逻辑。

目前 `ModelCard` 是一个极简 record：

| 方法 | 类型 | 说明 |
|--------|------|-------------|
| `modelName()` | `String` | 模型标识（如 `"claude-sonnet-4-6"`） |
| `displayName()` | `String` | 人类可读标签（如 `"Claude Sonnet 4.6"`） |
| `contextSize()` | `Integer` | 最大上下文窗口（token 数） |


<Note>

`ModelCard` schema 在当前阶段刻意保持极简；随着模型发现基础设施的完善，会逐步加入能力标志（输入/输出 MIME 类型）和参数 schema。

</Note>


### 获取 ModelCard

调用 `CredentialBase#listModels()`，返回 `Mono<List<ModelCard>>`：

```java
import io.agentscope.core.credential.ModelCard;
import io.agentscope.extensions.model.anthropic.credential.AnthropicCredential;
import java.util.List;

AnthropicCredential cred = new AnthropicCredential(System.getenv("ANTHROPIC_API_KEY"));
List<ModelCard> cards = cred.listModels().block();

for (ModelCard card : cards) {
    System.out.println(
            card.modelName() + ": context=" + card.contextSize());
}
```

`getChatModelClass()` 返回匹配的 `ChatModelBase` 子类——对反射方式构建默认模型很有用：

```java
Class<? extends io.agentscope.core.model.ChatModelBase> modelCls = cred.getChatModelClass();
```

这个设计让前端仅凭一个凭证就能发现供应商下的所有可用模型——无需硬编码供应商逻辑。
