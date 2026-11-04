# AgentScope 2.0.4 单步调用调研（spike）

已从 Maven Central 下载并查阅了已发布的 `agentscope-core` 与 `agentscope-extensions-model-dashscope` 2.0.4 源码包。本目录下抽取的源文件为参考材料，保留其原始 Apache-2.0 声明，位于构建源码树之外。

- 官方发布页：https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.4
- 版本对应的模型文档：https://github.com/agentscope-ai/agentscope-java/blob/v2.0.4/docs/v2/en/docs/building-blocks/model.md
- `ReActAgent.java`：`coreAgent` 进入 `executeIteration(0)`；`reasoning` 调用模型，`runPostReasoningPipeline` 可分发 `acting`；`maxIters` 耗尽时调用 `summarizing`，可能再发起一次模型调用。`call()` 是一个完整循环，而不是我们需要的"单动作边界"。虽然存在 hook / 外部工具挂起机制，但就本规范而言，它们并不提供比 Model API 更简单的边界。
- 选定的 API：`Model.stream(List<Msg>, List<ToolSchema>, GenerateOptions)` 返回 `Flux<ChatResponse>`；`ChatResponse.getContent()` 暴露 `TextBlock.getText()`。
- 接入方式：`io.agentscope.extensions.model.dashscope.DashScopeChatModel.builder().apiKey(...).modelName("qwen-plus").stream(false).enableThinking(false).build()`。
- 结构化 JSON：`GenerateOptions.builder().responseFormat(ResponseFormat.jsonObject()).temperature(0.0).build()`；本地严格协议解析仍是强制的。传入空的工具 schema，不挂载 `Toolkit`。
- `AgentTool` 暴露 `getParameters`、`getOutputSchema`、`isReadOnly` 和 `callAsync`。DeviceTruth 刻意不把运行时工具交给 SDK，执行器只归外部 Gateway 所有。
- `AgentScopeStepTest` 使用伪造的 SDK Model，统计订阅次数，校验空工具列表并解析一个 proposal。这是离线 API/契约测试，绝不发起真实 Qwen 调用。
- 真实 Qwen 冒烟测试 BLOCKED_BY_ENV，直到配置好 `DASHSCOPE_API_KEY`。编译/测试结果在执行后记录在 `docs/DeviceTruth_Core_V0.1_Spec_Pack/state.md`。

GitHub 上原始 Tool 文档请求超时；猜测的 structured-output.md 路径返回 404。因此改为直接查阅已发布的 `AgentTool`、`ResponseFormat`、`GenerateOptions` 和 ReAct 结构化调用实现，而不是凭空臆造 API。

SDK 执行配置最终确认：ExecutionConfig.MODEL_DEFAULTS 使用三次尝试。AgentScopeStep 显式提供 ExecutionConfig.builder().timeout(Duration.ofSeconds(45)).maxAttempts(1).build()，并设有整体阻塞截止时间。QwenReasoner 仅在 JSON/schema 无效时最多额外调用一次模型。AgentScopeStepTest 校验 maxAttempts 与有界超时。这仍是离线 API 验证；尚无真实调用通过。
