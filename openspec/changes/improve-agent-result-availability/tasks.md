# Tasks

## 1. 模型协议与剩余时间

- [x] 1.1 在 `ActionProposal` 和 `StructuredAgentReasoner` 增加最多 3 项的 `CALL_TOOLS` 及安全格式归一化，补充批量、代码围栏、大小写和非法动作协议测试并运行对应适配器测试。
- [x] 1.2 为 `GeneralReasoner`、`AgentScopeStep`、`AgentModelConnections` 增加兼容旧调用的剩余时间传递，验证固定 45 秒与剩余预算取较小值、协议重试不重置预算，并运行 `AgentScopeStepTest` 等相关测试。

## 2. 批量执行与有界收口

- [x] 2.1 在 `TaskOrchestrator` 复用现有网关逐项执行独立只读批次，禁止批量 Skill 状态工具并保持逐项事件、引用和检查点；补充一次模型动作执行多个查询、越权不执行和恢复兼容测试。
- [x] 2.2 在运行时和 `AgentExecutionCheckpoint` 增加兼容旧数据的 `finalizing/noProgress`，实现连续无进展、最后一步和最终时间预留收口；补充空检索、预算触发及旧检查点恢复测试。

## 3. 有限结果与安全门禁

- [x] 3.1 在 `AnswerGuard`/`TaskOrchestrator` 增加显式 `limited` 结果和保守 `HUMAN_REVIEW` 构造，仅在已有工具记录时启用；验证无候选原因编造、真实引用、原原因码、设备匹配和 `SIMULATED` 保留。
- [x] 3.2 调整 `GeneralAgentService` 持久化，使总超时后的迟到模型提案仍被丢弃而宿主有限结果可以落库，并增加 `RUN_PHASE`/`RUN_DEGRADED` 白名单审计；补充正常超时、有限超时和首次模型失败测试。
- [x] 3.3 更新 `AGENTS.md` 中通用动作、批量工具、最终阶段和有限结果说明，检查旧注释与新行为一致。

## 4. 集成验证

- [x] 4.1 运行 `mvn -q test`、`cd frontend && npm run test -- --run`、`cd frontend && npm run build` 和 `openspec validate "improve-agent-result-availability" --strict`，记录实际通过、失败、错误和跳过数。
- [x] 4.2 重建真实 product 后端并保留真模配置供用户手动端到端验收，在 `acceptance.md` 记录自动化结果、部署状态、原失败历史及未验证的真模/真实现场项目；不执行浏览器模拟点击。
- [x] 4.3 放宽 `kind/relevance` 展示分类组合，保留全部安全门禁；补充回归、全量验证、重新部署并记录用户反复遇到 `INVALID_ANSWER` 的修复历史。
