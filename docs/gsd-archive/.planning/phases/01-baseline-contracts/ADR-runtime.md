# ADR-01：以现有受控循环演进作为唯一产品 Runtime

状态：2026-11-09 基于 01-01 原型作出开发方向决定；生产改造未执行。证据见 [RUNTIME-SPIKE.md](RUNTIME-SPIKE.md)，通用协议见 [CONTRACTS.md](CONTRACTS.md)。独立 Review 尚未进行。

## 决策及依据

选定**演进现有 TaskOrchestrator 的服务端单步循环**；继续用 AgentScope Model API/AgentScopeStep 作为供应商连接。不把 ReActAgent/Toolkit 引入生产主循环，不将一个自主循环嵌套在另一个循环内。

同一 fake model、固定输入与 Gateway 下，两候选都通过六种正负样例，清理构建两次输出一致。ReAct 候选经过固定 2.0.4 API 编译/运行，不以“不能使用 SDK”作为选择理由。本原型中，演进方向的调度约 9 行，SDK 适配约 55 行，还必须保留引用/状态等业务边界。现有 Gateway、EvidenceManager、DecisionGuard、StateManager 与安全事件映射已经具备 legacy 回归保护；演进方向能保持执行/提交的直接边界，因此当前交付成本更小。

这不是延续旧故障码准入或固定结案流程：现有 `run(DiagnosisRequest, Budget, Reasoner)` 不能直接承担自由问答/WAITING。要按新 CONTRACTS 改造输入、动作、来源、等待边界与风险策略。小原型只是支持方向选择，不表示生产通用 Runtime 已完成。

## legacy 的归属

- 原 CLI/JSON fixture 和 `/api/sessions`、`/api/diagnoses` 保持兼容；旧五动作、严格 EvidenceManager/DecisionGuard 作为结构化诊断策略/适配。
- 新自由入口由 GeneralAgentService 构建上下文，通用动作进入同一受控循环方向；不再以 Catalog、symptom 或规则准入。
- 02-02 需先抽出可复用单步/提交边界，再把旧与新状态/动作适配为策略。不能简单复制现有循环为 GeneralAgentRuntime 后永久并存；也不能让新循环调用旧完整 run 当一个隐藏 Agent 工具。
- 迁移期间旧运行方法作为兼容门面，不削弱旧 Guard。若拆分文件，计划仍须明确唯一生产调度所有者，旧适配只承载领域/协议规则。

## 后续文件影响与实施检查

| 计划 | 已规划的修改点 | 本 ADR 要求 |
|---|---|---|
| 01-02 | AgentTaskInput、持久 conversation/message/run、API/ServerConfiguration | 先交付 QUEUED，不引入任何新执行引擎；按 CONTRACTS 固定 owner/project/schema |
| 02-01 | KnowledgeTools、Gateway 参数/ToolContext、引用 | 强类型 query/filters；授权在候选召回前；不依赖 ReAct Toolkit |
| 02-02 | GeneralAgentService、TaskOrchestrator、Reasoner/ActionProposal、Gateway、风险策略、ServerConfiguration | 演进现有循环后只留一处动作调度实现；旧 run 变适配，禁止复制第二套 while |
| 03-02/03-03 | Checkpoint/恢复、队列、预算、取消 | 在同一 Runtime 注入持久状态和预算账，不以 SDK Memory 为权威存储 |
| 04-01/04-02 | SkillContextLoader、GeneralAgentService、Gateway/ServerConfiguration | 领域指引与权限注入同一上下文，不启动领域专属 Agent 循环 |
| 05-01 | 持久事件/SSE/安全映射 | 使用应用白名单事件，不将 SDK 原始流直接公开 |

核对 02-02：files_modified、T1 的 files 及 GeneralAgentService→TaskOrchestrator key_link 已包含现有循环修改点，符合本次选择，无需改名或新增第二个 Runtime 类。其余后续 PLAN 保留既定类路径，本轮未执行。

## 代价、限制与重新评估条件

应用需要自己实现 PG 提交、等待恢复、生命周期预算和安全事件；两候选都没有替代这些业务规则。后续如实测 SDK Middleware/状态/流式能力显著降低这些成本，可另写 ADR 重新评估，并同时迁走主循环，不能增设并行栈。本次没有验证 PG/恢复/真实模型/RAG/SSE；保持各阶段的原验收标准。
