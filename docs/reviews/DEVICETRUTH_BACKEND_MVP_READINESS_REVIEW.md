# DeviceTruth 后端与 Agent Core V2.1 MVP 适配性审查

审查日期：2026-11-17（Asia/Shanghai）。范围：当前工作树最新源码（R1/R2 审查 + 定向修复 A 之后）。
本次仅 Review：不改代码、不做 POC、不重构。验证方式：源码走读 + 复用现有自动化测试（临时 pgvector/PG16 容器注入 `DEVICETRUTH_TEST_PG_*` 实跑）+ 真实模型定向用例（若本地密钥可用）。

验证状态图例：**[源码]** 仅源码确认；**[测试]** 自动化测试已验证（本次实跑）；**[真模]** 真实模型已验证；**[未验]** 尚未验证。

---

## 1. 总体结论

**CONDITIONALLY READY —— 可以立即开始动态画布开发，但画布所需的“工具调用细节” persistence 需要一处小改。**

- Agent Core 已能支撑产品 MVP 的个人模式（自由提问、自主工具调用、ASK_USER 追问、补答恢复调查）与双域 Skill/RAG 检索；通用 Agent 路径不依赖固定故障码、固定场景或硬编码流程。
- 动态调查画布的数据**大部分已经落在 PostgreSQL**（结构化答案 + 类型化引用 + 工具/模型事件时间线），前端可以直接基于现有 API 画出第一版画布；缺的是“工具调用了什么参数/目的”这一层持久化（当前 RUN_TOOL 事件只记工具名与成败，不记参数）。
- 人机双向协作（点击候选原因深入调查、补充观察重新分析）**现有接口直接支持**：新消息开新 Run（带会话历史），或 replyTo 恢复同一 Run；`contextRefs` 可显式继承历史引用。不需要新协议。
- 三个 R1/R2 P1 缺陷（P1-01/02 + P2-01/02）已修复并有真实模型定向用例；**P1-03/04/05 仍未修复**（修复 A 报告明确保留），比赛演示场景下概率低但需知晓。

---

## 2. 核心能力矩阵

| 功能名称 | 当前状态 | 代码证据（文件·方法） | 测试证据 | 主要缺口 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| 自然语言理解（任意检修任务） | 可用 [源码][测试] | `StructuredAgentReasoner.PROTOCOL`（三动作协议提示词）+ `GeneralReasoner.Context`（goalText/messages/toolResults/references 全量上下文） | `GeneralAgentFlowTest`（协议替身模拟真实模型单步提案，19 项） | 真实模型对任意中文检修意图的遵循度需在演示前用真实密钥冒烟 | P0 验证 |
| 自主连续工具调用 | 可用 [源码][测试] | `TaskOrchestrator.runAgent`（CALL_TOOL 分支，逐步校验授权+参数+去重） | `AgentToolFeedbackTest`（18 项，含真实模型提案） | — | — |
| 根据新信息重新规划 | 可用 [源码][测试] | 补答后同一 Run 重建上下文：`GeneralAgentService.doRun`；scripted 按新 query 重新检索：`ScriptedAgentReasoner.propose/search`（签名比对而非 attempted 标志）；工具摘要滑窗进下一轮 `Context.toolResults` | `AgentStreamTest`（补答后新检索 + SSE 补齐）、`RunRecoveryTest#realModelUsesReplyToProposeNewSearch` [真模] | 真实模型是否“主动”再检索取决于供应商输出（修复 A 已披露） | 已知限制 |
| 无固定流程限制 | 已确认 [源码] | 通用协议只有 CALL_TOOL/ASK_USER/ANSWER；legacy 五动作在 `TaskOrchestrator.runAgent` 末段被拒绝（“unsupported action type”）；提示词与 legacy 诊断提示词分离 | `ProtocolTest`、`GeneralAgentFlowTest` | — | — |
| 真实模型链路 vs 离线链路 | 双链路就绪 [源码][测试][真模] | `AgentModelConnections.qwen/openai` → `AgentScopeStep`；scripted 为确定性离线替身；`ProductConfiguration.agentReasoners` 按 `devicetruth.agent.reasoner` 切换；缺密钥→稳定错误码 FAILED，不降级 | `ModelConfigurationTest`、`AgentScopeStepTest`；真实模型定向 3/3（本轮实跑，§10） | 真实模型对任意中文检修意图的遵循度，演示前建议再冒烟一条自由提问 | P0 验证 |
| 工具结果进入下一轮模型上下文 | 可用 [源码][测试] | `TaskOrchestrator.runAgent:333-368` 工具结果→`boundedToolResult` 白名单摘要→`Context.toolResults`；有界滑窗 8 条/6000 字符 | `AgentToolFeedbackTest`（资产组件/配置核验/维修/命中/空结果/失败均有上下文断言） | — | — |
| 结构化设备信息/维修历史 | 可用 [源码][测试] | `AssetTools`（get_asset/get_configuration/get_maintenance_history，强类型 + 归属校验 + FIELD_VERIFIED 语义）；`AssetRegistryRepository`（PG，V8 表） | `AssetToolTest`、`DualDomainAgentTest`（SC-0245-B 工厂快照） | 数据接入靠夹具导入（`AssetFixtureSeeder`/`AssetSnapshotImporter`），真实台账需自行灌库 | 已知限制 |
| 知识原文获取 | 可用 [源码][测试] | `KnowledgeTools`（search_knowledge + get_document_excerpt，扩读沿用同引用 ID） | `KnowledgeSearchTest`（15 项，真 PG+pgvector） | PDF 未实现；真实 embedding 可选（默认确定性向量） | P2 |
| Skill 参与决策 | 可用 [源码][测试] | `SkillRuntime`（activate_skill/read_skill_reference，每 Run 一个版本，激活后收缩工具授权，正文进 `ActiveSkill` 上下文）；安装持久化 V14 | `SkillRuntimeIntegrationTest`（3 项） | Skill 只影响提示词与工具范围，不保证模型必然遵循 | 已知限制 |
| 工具结果/引用可追溯 | 可用 [源码][测试] | 引用登记：`dt_evidence_reference`（知识/配置/现场观测三类，含 sourceId/version/chunkId/摘录/dataNature）；事件：`dt_run_event` 白名单 safe_payload | `GeneralAgentFlowTest` 引用子集校验；`ResourceAuthorizationTest`（8 项） | RUN_TOOL 事件不含工具参数与 purpose（画布主要缺口） | P1 |
| 多轮对话上下文 | 可用 [源码][测试] | 新 Run 自动带会话历史：`ConversationRepository.recentConversationHistory`（已完成 Run 的最近 12 条/6000 码点）；Run 内消息按 seq 重建 | `PersistentConversationTest`、`AgentStreamTest` | 历史仅角色+正文，无引用继承（需显式 contextRefs） | 已知限制 |
| ASK_USER 后恢复调查 | 可用 [源码][测试] | `GeneralAgentService.reply`（三元组校验→消费问题→queueReply→重调度）；worker 从检查点+引用+消息重建 | `AgentLifecycleTest`（17 项）、`AgentStreamTest` | — | — |
| 失败/重启/超时恢复 | 基本可用 [源码][测试] | 执行租约+心跳 V12、崩溃对账 `RunRecoveryService`、总时限 V13、执行检查点 V17 | `RunRecoveryTest`（12 项，含恢复后引用一致性） | P1-04（QUEUED 可能滞留至下次启动）、P1-05（末步答案提交前崩溃窗口）未修 | P2 |
| 结构化诊断输出 | 可用 [源码][测试] | `AgentResult`：summary/category/claims(text+referenceIds+certainty)/suggestions/uncertainties/limitations/references(全字段)/riskLevel/safetyDisposition/dataNature | `AnswerGuardTest`、`GeneralAgentFlowTest` | 无独立“候选原因”结构化对象（claims 兼任）；冲突/核验状态仅存在于引用摘录文本 | P1 见 §4 |
| 结果持久化可回读 | 可用 [源码][测试] | 答案写 RUN_ANSWER 事件（`AgentRunRepository.complete`），RunView 从事件回读（VIEW_SQL answer_payload） | `GeneralAgentApiTest`、`PersistentConversationTest` | — | — |
| 实时状态/工具执行展示 | 可用 [源码][测试] | SSE `/api/runs/{id}/stream`（先历史后订阅再补读，Last-Event-ID 续传）；事件白名单 `SafeAgentEventViews` | `AgentStreamTest`（12 项） | 事件无“工具参数/目的” | P1 |
| 固定身份与权限 | 演示足够 [源码][测试] | `ProductConfiguration.productServerIdentity`（服务端固定 owner/project）；所有 SQL 按归属过滤；请求体禁止身份字段 | `ResourceAuthorizationTest` | 多用户共用实例时无逐人区分（既有约束，比赛演示够用） | 无需处理 |
| 取消 Run | 可用 [源码][测试] | `GeneralAgentService.cancel`（QUEUED/WAITING 立即终态；RUNNING 协作式） | `AgentLifecycleTest` | — | — |

---

## 3. 完整后端调用链（真实类/方法/数据落点）

### 3.1 首条链路：提问 → 诊断 → 持久化 → 前端获取

1. **提交**：`AgentConversationController.submit` `POST /api/conversations/{id}/messages`（需 `Idempotency-Key`）→ `GeneralAgentService.submit`：校验 schemaVersion=1 → 同事务写 `dt_message`（USER，request_key 幂等）+ `dt_agent_run`（QUEUED，budget 默认 maxSteps=12）+ `importContextRefs`。
2. **调度**：`dispatch` → 有界线程池 `executeRun`：`markRunning`（版本 CAS + 租约领取）→ `doRun`：
   - 从 PG 重建：`ConversationRepository.runContextMessages`（目标+追问+补答）、`recentConversationHistory`（会话历史）、`AgentRunRepository.references/checkpoint`；
   - `SkillRuntime.open` 建会话网关（注册 activate_skill/read_skill_reference）；
   - `TaskOrchestrator.runAgent(input, gateway, toolContext, seedReferences, reasoner, abortCheck, skillStep, progressWriter, history, checkpoint, reserveStep, executeTool, saveCheckpoint)`。
3. **单步循环**：`GeneralReasoner.propose(context)`（scripted 或 StructuredAgentReasoner→`AgentModelConnections` 真实供应商）→ 三动作之一：
   - CALL_TOOL：`CapabilityGateway.executeTool`（注册∩只读∩授权∩参数校验）→ 工具 handler（`KnowledgeTools`/`AssetTools`/`ObservationTools`/`AgentReportTools`/`SkillRuntime`）→ `ToolOutcome`（data + references + modelContext 白名单摘要）→ 引用合并入 `references`、摘要入 `toolResults`（8 条/6000 字符滑窗）→ `persistToolStep` 同事务写引用登记+检查点+RUN_TOOL 事件（工具步）；
   - ASK_USER：`AnswerGuard.unsafeFollowUp` 越界过滤 → 返回 WAITING_FOR_USER → `persist` 写助手消息 + `waitForUser`（pending_question JSONB + RUN_WAITING 事件）；
   - ANSWER：`AnswerGuard.review`（引用子集/确诊禁令/危险动作/风险判定/dataNature）→ 合法则返回 COMPLETED → `persist` 写助手消息 + `complete`（RUN_ANSWER 事件，载荷为完整 AgentResult JSON）。
4. **前端获取**：`GET /api/runs/{id}`（RunView：status/pendingQuestion/result/failureCode/用量）、`GET /api/runs/{id}/references`（类型化引用列表）、`GET /api/runs/{id}/events` + SSE `/stream`（白名单事件）、`GET /api/conversations/{id}`（消息+全部 Run）。前端 `frontend/src/api.ts` 已有全部对应封装。

关键持久化位置：`dt_agent_run`（状态机+预算+租约+检查点）、`dt_run_event`（RUN_QUEUED/RUN_TOOL/RUN_MODEL/RUN_ANSWER/RUN_WAITING/RUN_FAILED/…）、`dt_evidence_reference`（引用=画布证据节点）、`dt_message`（会话）、`dt_run_skill*`（Skill 选择）。

### 3.2 第二条链路：补充信息/新方向 → 继续调查

- **补答（同一 Run）**：`replyToQuestion`（api.ts）→ `GeneralAgentService.reply`：校验 run 属于本会话且状态 WAITING_FOR_USER、问题 ID/版本匹配 → 写补答消息（reply_to 三元组）→ `queueReply`（CAS）→ 重新 dispatch → `doRun` 读回全部上下文+检查点，主循环继续，scripted 会以“目标+补答”新 query 重新检索（P1-02 修复点）。
- **新方向（新 Run）**：`sendMessage` 无 replyTo → 新 Run；自动带会话历史；可选 `contextRefs:[{referenceId}]` 把历史 Run 的引用导入本 Run（`AgentRunRepository.importReference`，归属校验）。
- 结果返回同 3.1 第 4 步。

---

## 4. CanvasComposer 可行性（重点）

### 4.1 当前最终输出的完整数据结构（已持久化，可回读）

`AgentResult`（RUN_ANSWER 事件 + RunView.result，SSE 同推）：

```
summary: string                      // 回答正文（≤8000 码点）
category: KNOWLEDGE|POSSIBLE_CAUSES|CONFIRMED_FINDING|HUMAN_REVIEW
claims: [{text, referenceIds[], certainty: POSSIBLE|SUPPORTED|CONFIRMED}]  // ≤2000 码点/条
suggestions: string[]                 // 建议/待检查项（自由文本）
uncertainties: string[]               // 不确定点
limitations: string[]                 // 限制（含服务端受控安全提示）
references: [{referenceId, sourceType: KNOWLEDGE_DOCUMENT|CONFIGURATION|FIELD_OBSERVATION,
              sourceId, sourceVersion, chunkId, title, page, section, excerpt, dataNature}]
riskLevel: LOW|MEDIUM|HIGH|PROHIBITED（服务端判定）
safetyDisposition / dataNature: SIMULATED|AUTHORIZED
```

另可回读：`dt_evidence_reference` 全行（含 retrieved_at、asset_id、as_of）、RUN_TOOL 事件（seq/toolName/success/errorCode/referenceCount/durationMs）、RUN_MODEL 事件（模型/尝试/耗时/token）、消息序列、Run 预算与状态时间线。

### 4.2 回答本节审查问题

- **是否包含候选故障原因/证据/冲突/待检查项/结论？**
  - 候选原因：**以 claims 形式存在**（category=POSSIBLE_CAUSES 时 claims 即候选原因，各带 certainty 与引用）——结构化但无独立字段名。
  - 证据：references 完全结构化（类型/来源/版本/定位/摘录/数据性质）。
  - 待检查项：suggestions + limitations（自由文本）；配置类工具的 requiredChecks 只作为 CONFIGURATION 引用摘录文本的一部分存在。
  - 冲突：`AssetTools` 在工具结果里有结构化 `conflict/installationStatus/requiredChecks`，但**不持久化为字段**，只进入引用 excerpt 文本（如 `;verification=...;value=...`）。画布若要做“冲突”节点，需要解析文本或补持久化。
  - 结论：category + summary + riskLevel。
- **哪些字段结构化、哪些只在自然语言？** 结构化：claims/references/category/risk/事件。纯文本：suggestions/uncertainties/limitations、配置核验状态、冲突、待核验项、追问。
- **Run 完成后能否从数据库重新获取？** 能。答案在 RUN_ANSWER 事件，RunView/事件端点/SSE 三处同源。
- **ToolResult 与 Reference 是否足以构建动态画布？** 足够搭第一版：节点=引用（三类）+ 工具调用（时间线）+ 候选原因（claims）+ 结论卡；边=claim.referenceIds 与 RUN_TOOL.referenceCount/seq 顺序。**不足以**还原“Agent 这一步查了什么（参数/目的）”，也不足以结构化表达冲突/核验状态。
- **缺什么输入？** ① 工具调用参数与 purpose（RUN_TOOL 载荷当前只有 toolName/success/errorCode/references/durationMs/retryable）；② 结构化冲突/核验标记（可选）；③ 跨 Run 的“同一调查”聚合只有 conversation 维度（够用）。
- **是否需要额外模型调用？** 不需要。画布是持久数据的投影，不应再调模型。
- **是否需要改 AgentResult 协议？** **MVP 不需要**。claims+references 已能表达候选原因与证据链。建议把“工具参数进 RUN_TOOL”作为唯一后端必改项。

### 4.3 明确判断

**当前后端不能直接生成信息完整的动态画布，但可以立即生成第一版可用画布；最小补充只有一项：把工具调用参数/目的以白名单形式写入 RUN_TOOL 事件。** 改动点：`TaskOrchestrator.runAgent` 的 `audit` Map（现有 `Map.of("toolName",...)`）增加 `arguments`（每个工具挑 1-2 个可读参数，如 query/attribute/componentId，限长）与 `purpose`，并在 `SafeAgentEventViews.ALLOWED_FIELDS` 的 `RUN_TOOL` 条目中放行。半天以内，风险低。

---

## 5. 人机双向协作适配性

目标交互 → 现有支持度：

| 目标交互 | 支持度 | 实现方式（现有代码） |
| --- | --- | --- |
| 点击候选原因 → 新调查 | **现有接口直接支持** | 前端取 claim.text/referenceIds → `sendMessage` 发“请深入调查：<原因>”（新 Run），自动带会话历史（`recentConversationHistory`）；要继承证据则传 `contextRefs`（`importReference` 归属校验）。无需后端改动。 |
| 补充现场观察 → 重新分析 | **现有接口直接支持** | Run 等待中：`replyToQuestion`（同 Run 恢复，scripted/真实模型都会拿新消息继续，修复 A 已验证新检索）；Run 已结束：发新消息开新 Run（历史自动携带）。 |
| 后续调查关联此前证据/结论 | **现有接口直接支持** | 会话历史自动；`contextRefs` 显式导入引用；`generate_diagnosis_report` 工具让模型读取本 Run 持久事实。 |
| 同一任务连续多个 Run | **现有接口直接支持** | `GET /api/conversations/{id}` 返回 `runs[]`；conversation 即“案子”，MVP 无需新增 DiagnosisCase 表。 |
| 点击后给前端一个可追踪的新 Run | 少量适配 | 提交接口已返回 RunView + Location；前端只需把新 Run 挂到画布线程。 |

结论：人机双向协作**不需要新增后端接口**；唯一建议是前端发深入调查消息时把 claim 文本+引用 ID 一起带上（文本即可，模型可见历史与引用）。

---

## 6. 前端接口与实时通信（复用清单）

可直接复用（product profile）：

- `POST /api/conversations`、`GET /api/conversations/{id}`（消息+runs）
- `POST /api/conversations/{id}/messages`（新 Run / replyTo 补答，幂等键）
- `GET /api/runs/{id}`（状态/待答问题/结构化结果/失败码/用量）
- `GET /api/runs/{id}/references`（证据卡）
- `GET /api/runs/{id}/tool-events`、`/model-events`（工具/模型时间线）
- `GET /api/runs/{id}/events?after=` + `GET /api/runs/{id}/stream`（SSE，白名单事件含完整 RUN_ANSWER）
- `POST /api/runs/{id}/cancel`
- `GET /api/runs/{id}/skill`（Skill 展示，前端已封装 `readRunSkill`）
- `GET /api/assets`、`/api/assets/{id}`（组合视图，供画布侧栏）
- `POST /api/files`、`/api/knowledge/documents`（演示前灌手册）

支持动态画布最少新增（仅一项后端）：RUN_TOOL 事件携带工具参数/目的（§4.3）。前端侧把现有 `RunTimeline`/`DiagnosisReport`/`EvidenceComparison` 组合成画布即可。

---

## 7. 数据持久化与权限

- 已有关系：`dt_conversation 1—n dt_message 1—n dt_agent_run`（goal_message_id 唯一；message.run_id 反挂），`dt_agent_run 1—n dt_run_event`、`1—n dt_evidence_reference`、`dt_run_skill`。Flyway V1–V17 只前进迁移，product 启动自动应用。**Case=conversation 已够用，不需要新增 DiagnosisCase；画布是投影，不需要 Canvas 表。**
- 身份：固定 `ServerIdentity`（`devicetruth.product.owner-id/project-id`），全表按 owner/project 过滤，请求体禁带身份。演示环境足够；多租户不在本次范围。
- 模拟 vs 真实数据：观测 `get_observations` 只读 `dt_asset_observation` 已导入数据（SIMULATED，明确不连真机）；资产台账 PG 化但当前由夹具导入（`AssetSnapshotImporter`/`AssetFixtureSeeder`，支持 CAR-B/SC-0245-B 等）；知识库支持真实上传导入（Markdown，真实 embedding 可选）。比赛演示需提前灌入手册与台账。

---

## 8. 必须补齐的后端能力（仅比赛 MVP 必需）

### 8.1 必做

| # | 缺失原因 | 修改位置 | 最小实现 | 依赖 | 工作量 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| B1 工具参数/目的持久化 | 画布无法显示“Agent 查了什么” | `TaskOrchestrator.runAgent` audit Map；`SafeAgentEventViews.ALLOWED_FIELDS`（RUN_TOOL 加 `arguments/purpose`） | 每个工具在 audit 中放 1-2 个可读参数（限长截断），白名单放行 | 无 | 0.5 天 | 跑一次工具调用后，`GET /api/runs/{id}/events` 的 RUN_TOOL 带 arguments；现有测试不回归 |
| B2 演示数据与真实模型配置 | 比赛要用真实模型+双域资料 | 仅配置与运维，不改代码：`devicetruth.local.properties`（OPENAI_*/DASHSCOPE_API_KEY）、`devicetruth.agent.reasoner=qwen/openai`、灌入手册（/api/files）与资产台账 | 沿用现有导入 API | 密钥 | 0.5 天 | 真实模型跑通用例（仓库已有 `devicetruth.test.real-agent=true` 定向测试）+ 浏览器冒烟 |

### 8.2 建议但可延后（不阻塞画布）

| # | 事项 | 说明 |
| --- | --- |
| C1 | P1-03 RAG 登记事务与工具 savepoint 耦合 | `KnowledgeSearchService.search` 的 REQUIRED 模板与 worker 同事务，登记异常会把工具失败升级成 CHECKPOINT_PERSIST_FAILURE。演示低概率；最小修复是让登记复用 worker 事务。 |
| C2 | P1-04 滞留 QUEUED 只靠启动对账 | 周期任务只扫过期 RUNNING 不扫 QUEUED；演示中如遇瞬时 DB 错误，Run 可能停 QUEUED 直到重启。最小修复：有界周期补派 QUEUED。 |
| C3 | P1-05 末步答案提交前崩溃窗口 | 校验后的追问/答案只在内存，worker 在 persist 前崩溃则丢失（耗尽步数时不可恢复）。演示低概率。 |
| C4 | AgentResult 增加结构化候选原因/冲突字段 | MVP 用 claims+引用即可；若评委要求“冲突”作为一等节点，再扩 protocol（涉及 AnswerGuard/前端，约 1-2 天）。 |

---

## 9. 一周内的最小开发顺序

按依赖排列（不含长期路线图）：

1. **Day 1 上午：B2 环境** — 起 PG（compose）+ 配置真实模型密钥 + `reasoner=qwen`；跑通 `DualDomainAgentTest` 同款冒烟（工业 SC-0245-B + 汽车 CAR-B 各一条真实模型请求）。
2. **Day 1 下午–Day 2：B1 工具参数持久化** — 半天开发 + 回归（`AgentToolFeedbackTest`/`AgentStreamTest`）。这是画布开发的唯一后端前置。
3. **Day 2–Day 4：前端动态画布** — 基于现有 API：左=Run 时间线（RUN_TOOL/RUN_MODEL 事件），中=证据卡墙（references 按 sourceType 着色），右=结论卡（claims 按 certainty 分组 + 引用展开）。复用 `RunTimeline.vue`/`EvidenceComparison.vue` 组件。
4. **Day 4–Day 5：人机协作闭环** — 点击候选原因 → `sendMessage` 深入调查 → 新 Run 追加到画布线程；补答 → replyTo 恢复。全部为前端工作。
5. **Day 5–Day 6：演示数据灌入与双域脚本** — 汽车充电手册/工业控制器手册导入，资产快照导入，准备 2 条演示提问路径。
6. **Day 7：端到端彩排 + 容错兜底** — 取消、刷新恢复（SSE Last-Event-ID）、失败码展示；可选 C1/C2 若彩排命中再修。

---

## 10. 本次实际验证

| 执行命令 | 总数 | 通过 | 失败 | 错误 | 跳过 | 说明 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `docker run pgvector/pgvector:pg16`（临时容器，127.0.0.1:55432） | — | — | — | — | — | 一次性测试库，验证后移除 |
| `mvn -o -B test`（注入 `DEVICETRUTH_TEST_PG_*`） | 394 | 394 | 0 | 0 | 4 | 全量离线测试，Maven 3.9.9 / JDK 21.0.13.0.2，BUILD SUCCESS（退出码 0），03:06 |
| `mvn -o -B test -Ddevicetruth.test.real-agent=true -Dtest=RunRecoveryTest#realModelUsesReplyToProposeNewSearch+realModelReadsLongAssetReferenceAfterReplyAndWorkerRestart,AgentToolFeedbackTest#realModelProposalsExerciseReuseRefreshAndBoundedSummary` | 3 | 3 | 0 | 0 | 0 | 真实模型定向：补答后新检索、长资产引用恢复、复用/刷新/摘要边界；BUILD SUCCESS（退出码 0） |

跳过说明：4 项跳过 = 3 项真实模型用例（未加 `-Ddevicetruth.test.real-agent=true` 时按设计跳过；本轮已单独启用并 3/3 通过）+ 1 项 Windows 无法建符号链接的 Skill 测试（`SkillRegistryTest.symlinkedSkillDirectoryEscapingRootIsRejected`）。

未执行/未验证项：真实 embedding（默认确定性向量，真实供应商接入未启用）、真实设备连接（观测工具明确只读已导入模拟数据）、浏览器端到端。P1-03/04/05 触发路径未做专用复现。

## 11. R1/R2 P1 缺陷当前状态核对

| 缺陷 | 状态 | 核对依据（当前源码） |
| --- | --- | --- |
| P1-01 去重误拦合法取数 | **已修复** [源码][测试] | `TaskOrchestrator.reusableSignature` 对动态工具并入 `userMessages` 轮次；`canReuse` 对 Skill 资料/激活单独判定；修复 A 报告 + `AgentToolFeedbackTest` |
| P1-02 补答后不重新检索 | **已修复** [源码][测试][真模] | `ScriptedAgentReasoner.propose:43-48` 按查询签名判断是否已检索；`RunRecoveryTest#realModelUsesReplyToProposeNewSearch` 真实模型通过 |
| P1-03 RAG 登记事务耦合 | **未修复** [源码] | `KnowledgeSearchService`（:150）仍用共享 REQUIRED 模板；修复 A 报告明确保留 |
| P1-04 恢复后滞留 QUEUED | **未修复** [源码] | `GeneralAgentService.recoverQueued:217-222` 周期任务仅 `reconcileExpired`（过期 RUNNING），不补派 QUEUED |
| P1-05 末步结果提交前崩溃丢失 | **未修复** [源码] | 主循环只返回内存 outcome，`persist` 前无暂存态；全库无待提交持久化逻辑 |
