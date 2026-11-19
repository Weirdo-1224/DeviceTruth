# Proposal

## Why

真实模型 Run 已出现多次协议失败、传输超时和 `TOTAL_TIMEOUT`。当前每个工具前后都需要一次模型调用，宿主又缺少无进展收口和最终回答预算，导致已经取得证据的任务也只能以技术失败结束。需要优先保证每次调查都能及时形成正常答案或明确标注限制的可用结果。

## What Changes

- 增加小批量只读工具动作，让模型一次提出最多 3 个相互独立的查询，由宿主逐个授权、执行并汇总结果。
- 增加调查与最终回答两个阶段；连续无新增信息、调查步数或时间接近预算时，禁止继续调查并进入最终回答。
- 为最终回答预留步数与执行时间，单次模型请求不得超过 Run 剩余时间，取消和总截止时间继续生效。
- 最终模型调用或答案审核失败时，在已经开展调查的前提下，基于真实工具摘要和引用生成 `HUMAN_REVIEW` 有限结果，记录原失败原因，不伪造诊断。
- 对明确的 JSON 包装和枚举大小写变体做安全归一化，减少非语义格式错误。
- 保持引用归属、设备对象匹配、未核验不确诊、危险操作拒绝及 `SIMULATED` 标记等核心门禁不变。
- 增加动作、无进展和降级结果的安全审计信息；不保存密钥、完整 Prompt 或模型原始输出。

## Capabilities

### New Capabilities

- `agent-execution-availability`: 通用产品 Agent 的批量调查、有界收口、剩余时间控制与有限结果交付。

### Modified Capabilities

无。

## Impact

- 影响 `ActionProposal`、`StructuredAgentReasoner`、`TaskOrchestrator`、`GeneralAgentService`、`AgentScopeStep`、`AgentModelConnections` 和 `AnswerGuard`。
- 模型动作协议新增 `CALL_TOOLS`，保留现有 `CALL_TOOL`、`ASK_USER`、`ANSWER` 兼容性。
- 有限结果仍使用现有 `AgentResult`、`COMPLETED` 和 `HUMAN_REVIEW` 合同，通过限制说明和安全审计区分降级交付，不改变前端接口形状。
- 需要补充核心运行时、真实 PG 生命周期、协议适配和答案守卫回归测试，执行 Maven 全量测试、前端测试和构建；不做浏览器模拟点击。
