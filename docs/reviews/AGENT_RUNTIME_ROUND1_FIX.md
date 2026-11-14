# Agent Runtime 第一轮缺陷修复报告（Round 1）

- 日期：2026-05-22
- 范围：`TaskOrchestrator.runAgent()` 主循环的工具结果反馈链路（Fix 1–4）
- 性质：Runtime 局部修复，非架构重构；未改动数据库结构、持久化生命周期、前端与 Skill 机制

## 1. 修复背景

### 1.1 当前 Agent 产品链路

浏览器/客户端经 `AgentConversationController` 提交消息 → `GeneralAgentService` 校验并原子写入 PG（`dt_message` + `dt_agent_run`）→ 有界 worker 领取租约 → `TaskOrchestrator.runAgent()` 作为唯一主循环：每轮由 `GeneralReasoner`（在线为 `StructuredAgentReasoner` 单步协议补全，离线为 `ScriptedAgentReasoner`）提议恰好一个动作（`CALL_TOOL`/`ASK_USER`/`ANSWER`）→ 工具经 `CapabilityGateway.executeTool()` 执行（只读、注册即授权、逐参数校验）→ `ANSWER` 草稿经 `AnswerGuard` 校验后落库，全程事件写入 `dt_run_event`。

### 1.2 原有问题与影响

工具执行成功 ≠ 模型拿到结果。主循环的工具分支只把 `outcome.references()` 和**非空的** `outcome.modelContext()` 放进下一轮上下文，`outcome.data()` 从未被读取。导致：

- `get_asset` 返回空引用 + 空 `modelContext` → 模型对刚查到的资产详情一无所知；
- `generate_diagnosis_report` 的报告本体只留在 `data` 里 → 模型看不到报告；
- `get_configuration`/`get_maintenance_history` 的 `requiredChecks`、`limitations` 永远到不了模型，破坏了"缺口显式化"设计（`AssetFacts` 注释明确 requiredChecks"供 Agent 在答案里作为人工项"，与实际链路不符）；
- `get_document_excerpt` 的 2000 字原文因 `references.putIfAbsent()` 被 search 阶段的 600 字短摘要挡住，扩读取不到更长原文；
- 知识检索空结果时模型无法区分"工具失败"与"没有资料"；
- 相同工具+相同参数的重复调用没有任何拦截，浪费步数预算。

### 1.3 本轮范围

仅修复 `runAgent()` 循环内的工具结果反馈、引用富化、重复调用拦截与上下文边界，以及各工具 `modelContext` 白名单补全。**不涉及**：数据库结构/迁移、`GeneralAgentService` 持久化生命周期、前端、Skill 加载机制、跨会话记忆、Legacy `run()`。

## 2. 修复前的代码问题（已确认）

| # | 位置 | 问题 | 影响 |
| --- | --- | --- | --- |
| P1 | `TaskOrchestrator.runAgent()` CallTool 分支（原 311–334 行） | 仅 `modelContext` 非空才生成 `ToolResult`；`outcome.data()` 从不消费 | 工具结果不可见（Fix 1） |
| P2 | `TaskOrchestrator.runAgent()` 引用合并 | `putIfAbsent` 拒绝同 ID 引用替换 | 原文扩展被短摘要挡住（Fix 2） |
| P3 | `TaskOrchestrator.runAgent()` | 仅按工具名记录 `attemptedTools`，无参数级去重 | 相同调用空转（Fix 3） |
| P4 | `AssetTools.assetOutcome` / `maintenanceOutcome`、`AgentReportTools.reportOutcome`、`KnowledgeTools.searchOutcome` / `excerptOutcome` | 未填或填充不全 `modelContext` | 资产/维修/报告/检索结果丢失（Fix 1） |
| P5 | `CapabilityGateway` 与各工具 | 无单值/累计长度约束 | 结果增大后 Prompt 膨胀风险（Fix 4） |

潜在风险（未独立验证，随修复一并覆盖）：`get_configuration` 的 `requiredChecks`/`limitations` 丢失（已随 P4 一并修复并测试）。

## 3. 实际代码修改

### 3.1 `TaskOrchestrator`（核心）

- **新增常量与助手方法**：
  - `boundedToolResult(ToolOutcome)`：组装模型可见白名单。标准字段在前（`success`、`errorCode`、`dataNature`、`referenceCount`、`referenceIds`），工具自述 `modelContext` 以 `putIfAbsent` 补充在后；**任何值都不取自 `outcome.data()` 的原始序列化**。
  - 长度边界：`RESULT_VALUE_MAX_CHARS=1000`（单值）、`RESULT_ENTRY_MAX_CHARS=1600`（单条键值合计）、`TOOL_RESULTS_MAX=8`（沿用原滑窗条数）、`TOOL_RESULTS_TOTAL_MAX_CHARS=6000`（累计上限，依据：8 条 × 约 750 字符 ≈ 6k，控制在 12 步预算的 Prompt 开销内）。超限值截断并加 `…(截断)` 标记；首键（含 success/errorCode）必留，可选键超条目预算时丢弃而非截断键名。
  - `trimToolResults`：条数与累计长度双重约束，超限先淘汰最旧结果。
  - `mergeReference`：同 ID 且来源身份（sourceType/sourceId/sourceVersion/chunkId）一致时，允许更长摘录替换旧值；身份不一致保留原值。
  - `canonicalJson`/`sortKeys`：参数键递归排序，结构等价的调用得到相同去重键。
- **CallTool 分支改造**：
  - 修改前：成功时 `putIfAbsent` 引用 + 非空 modelContext 才加 ToolResult；失败只回 feedback。
  - 修改后：先做重复调用检查（`attemptedCalls` 记录"规范化参数键 → 上次成败"，上次成功才拦截；拦截时不执行、不记为成功证据，记 `TOOL_RESULT(success=false, errorCode=DUPLICATE_CALL)` 事件并给模型明确反馈）；执行后无论成败都生成有界 `ToolResult` 入滑窗；引用经 `mergeReference` 合并。
- **兼容性**：`AgentRunOutcome`、`RunEvent` 结构不变；`TOOL_RESULT` 事件载荷键不变（DUPLICATE_CALL 沿用同一事件类型，前端白名单无需改动）；`GeneralReasoner.Context`/`ToolResult` 契约不变，仅 status map 内容更丰富。

### 3.2 各工具 `modelContext` 补全（Fix 1）

| 工具 | 新增模型可见字段 |
| --- | --- |
| `AssetTools.get_asset` | assetId、asOf、componentCount、components、dataNature |
| `AssetTools.get_configuration` | 在原 installationStatus/conflict 基础上加 entries、requiredChecks（"；"连接）、limitations（"；"连接）、dataNature；未核实信息保持 UNCONFIRMED 语义不变 |
| `AssetTools.get_maintenance_history` | entries、limitations、dataNature |
| `KnowledgeTools.search_knowledge` | hits、empty（空检索显式可见） |
| `KnowledgeTools.get_document_excerpt` | documentId、chunkId、title、excerpt（引用同一份截断至 2000 的正文，身份一致） |
| `ObservationTools.get_observations` | 原 status/asOf/message 基础上加 entries |
| `AgentReportTools.generate_diagnosis_report` | status、steps（used/max）、references、toolEvents、answer（类别+摘要）、failureCode |
| `SkillRuntime` activate/read_reference | 回执调用的参数（skillId/name）；效果仍经下一轮 activeSkill 上下文可见 |

### 3.3 `StructuredAgentReasoner` 协议适配器

- **协议提示词**：新增第 8 条规则：说明 `toolResults` 是最近工具结果的白名单摘要（工具名、成败、关键字段与引用 ID），事实细节以 references 为准；相同工具与参数已成功的结果不要重复调用；报告类摘要不得当作现场证据。**协议约束只增不减**，工具权限与安全边界不变。
- **输出归一化（在线验收驱动）**：`normalizeActionType` 纠正真实模型的高频笔误——把工具名误写进 `type` 字段（如 `{"type":"activate_skill","toolName":"activate_skill",...}`）。仅当 `type` 不是三种合法动作且载荷带文本 `toolName` 时归为 `CALL_TOOL`，随后仍走严格模式解析与网关授权/参数校验；`ASK_USER`/`ANSWER` 混入 `toolName` 等混合格式因未知字段被严格拒绝，**不猜测语义、不放宽安全**。原因：2026-05-22 在线验收中 qwen-plus 连续 4 次输出该笔误导致 run 失败，简化探针（同协议、同供应商）证明协议本身可解析，该归一化是使在线链路可达的最小修复。

### 3.4 方案选择理由与风险

- 不复用 `outcome.data()` 直塞：各工具 data 为强类型结果对象，直接序列化会随设备记录/RAG 增长造成 Token 膨胀，且绕过工具自身的白名单语义。采用"标准字段 + 工具自述白名单"两级组装，既统一又保留各工具对"什么能给模型看"的控制权。
- 引用富化采用内存替换而非 DB 更新：`dt_evidence_reference` 行本就不存摘录正文（前端/查询视图从 `dt_knowledge_chunk.content` 实时取并截 600），内存中换更长摘录不造成展示不一致。
- 重复拦截在执行前进行，不绕过网关（被拦截的请求根本不执行，授权语义不受影响）；失败调用允许原样重试，不同参数不受工具名影响。
- 兼容性风险：极低。`ScriptedAgentReasoner` 依赖的 `installationStatus`/`conflict`/`message` 键保留原值原义；唯一行为变化是新增 DUPLICATE_CALL 事件与更丰富的 ToolResult，均为增量。

## 4. 修复后的完整调用链（示例：用户查询设备配置）

1. 用户消息"查一下 CAR-B 的充电控制器型号"经 `AgentConversationService.submit` 落库，run 入队；
2. worker 领取租约，从 PG 重建 `AgentTaskInput`/`ToolContext`；
3. 主循环组装 `GeneralReasoner.Context`（goalText、消息、工具目录、seed 引用）；
4. `StructuredAgentReasoner` 收到上下文 JSON（现含 `toolResults` 字段说明），提议 `CALL_TOOL get_configuration {attribute: model}`；
5. `CapabilityGateway.executeTool` 校验只读/授权/参数 → `AssetTools.configurationOutcome`：按 asOf 双时态查询，分组判定 installationStatus/conflict，生成 requiredChecks/limitations，经 `AssetFactReferenceWriter` 登记 `dt_evidence_reference`；
6. 工具返回 `ToolOutcome{data: 完整结果, references: [cfg-1..], modelContext: {installationStatus, conflict, requiredChecks, ...}}`；
7. 主循环：`mergeReference` 合并引用；`boundedToolResult` 生成白名单摘要 `{success:true, dataNature, referenceIds, installationStatus, conflict, requiredChecks, limitations}` 压入 8 条滑窗（受 1000/1600/6000 边界约束）；记 `TOOL_RESULT` 事件；
8. 下一轮 Context 携带上摘要与引用；若模型对同一参数再发相同调用，被拦截并收到"结果已在上下文中"的反馈；
9. 模型提交 ANSWER 草稿 → `AnswerGuard` 校验引用子集/确诊路径/风险 → 事务落库，SSE 推送事件与终态。

## 5. 测试与验收

### 5.1 新增离线测试（确定性，无外部依赖）

`src/test/java/io/devicetruth/core/runtime/AgentToolFeedbackTest.java`，以脚本化替身推理器捕获**每轮实际 `GeneralReasoner.Context`**，断言下一轮模型真实输入：

| 用例 | 测试方法 | 结果 |
| --- | --- | --- |
| T01 资产查询 | `assetQueryFieldsReachNextModelContext` | PASS |
| T02 配置查询 | `configurationStatusConflictAndReferencesReachModel` | PASS |
| T03 维修历史 | `maintenanceHistoryFactsStayDocumentedNotConfirmed` | PASS |
| T04 RAG 检索 | `knowledgeSearchHitsAndReferenceIdsReachModel` | PASS |
| T05 原文扩展 | `documentExcerptEnrichesReferenceKeepingIdentity` | PASS（引用 ID/文档/版本/chunk 不变，摘录变长，status 带 excerpt） |
| T06 空结果 | `emptyToolResultReportsExplicitStatus` | PASS |
| T07 工具失败 | `toolFailureExposesOnlySafeErrorCode` | PASS |
| T08 重复调用拦截 | `identicalSuccessfulCallIsIntercepted` | PASS（含键序不同的等价参数） |
| T09 合法多次调用 | `sameToolWithDifferentArgumentsExecutesTwice` / `failedCallCanBeRetriedWithSameArguments` | PASS |
| T10 上下文边界 | `oversizedToolResultsAreBounded` | PASS（值截断带标记、conflict/installationStatus 不丢、累计 ≤6000） |
| T11 权限回归（离线部分） | `unauthorizedAndUnknownToolsStayRejected` | PASS |
| T12 原有闭环 | `askUserPathStillWaitsForUser` / `answerGuardStillRejectsUnconfirmedFinding` / `stepBudgetStillTerminatesTheLoop` | PASS |
| 协议输入断言 | `structuredReasonerInputCarriesToolResults` | PASS（补全输入 JSON 含 toolResults 及白名单字段） |
| 在线验收驱动：type 笔误归一化 | `toolNameMisplacedInTypeFieldIsNormalizedToCallTool` | PASS（归一为 CALL_TOOL） |
| 在线验收驱动：混合格式不放行 | `mixedAskUserWithToolNameStaysRejected` | PASS（严格拒绝，不猜测语义） |

- 执行命令：`mvn -q test -Dtest=AgentToolFeedbackTest -DfailIfNoTests=false`
- 结果：Tests run: 18, Failures: 0, Errors: 0, Skipped: 0

### 5.2 全量回归

- 命令：`DEVICETRUTH_TEST_PG_BASE_URL=jdbc:postgresql://127.0.0.1:54329/ DEVICETRUTH_TEST_PG_USERNAME=devicetruth DEVICETRUTH_TEST_PG_PASSWORD=devicetruth mvn test`
- 环境：本机 compose 的 pgvector/pg16（容器 `devicetruth-preview-postgres-1`）。Windows 宿主机经 Docker 端口映射访问时，容器内看到的来源 IP 是 Docker 网关（172.23.0.1），落在 pg_hba 的 scram-sha-256 兜底规则上；为执行集成测试临时在 pg_hba 插入了 `host all all 172.16.0.0/12 trust` 规则并在测试完成后删除恢复（`pg_reload_conf()` 生效）。
- 结果：**Tests run: 378, Failures: 0, Errors: 0, Skipped: 1；BUILD SUCCESS**（最终代码全量回归，含在线验收驱动的归一化修复及其 2 个新单测）。其中包含全部 PG 集成测试（`GeneralAgentFlowTest`、`AssetToolTest`、`KnowledgeSearchTest`、`SkillRuntimeIntegrationTest`、`RunRecoveryTest` 等 10+ 个类，本轮改动涉及的产品链路用例全部真实执行），Skipped 1 为既有在线模型用例（需真实密钥，非本轮引入）。修复前基线（未配 PG 环境变量时）：249 项中 14 个 PG 类因连接失败报错——属环境前置，非代码回归。

### 5.3 在线真实模型验收（2026-05-22，qwen-plus，DashScope）

经用户授权消耗真实模型额度，在本机 compose PG 上以 `devicetruth.agent.reasoner=qwen` 启动完整产品服务（CAR-B 夹具已入库，手册已在库）做了端到端验证：

- **修复前**：真实 run 连续失败——诊断（临时日志，已移除）确认 qwen-plus 把工具名误写进 `type` 字段（`{"type":"activate_skill",...}`），严格解析 4 次拒绝，run 以 `MODEL_OUTPUT_INVALID_AFTER_RETRY` 失败。
- **归一化修复后**：同一提问下真实模型完整走通 6 步：`activate_skill → get_asset → get_configuration×3（不同字段）→ ASK_USER`，零解析失败；因查到的配置均为 `UNCONFIRMED`（无现场核验记录），模型按 `AnswerGuard` 约束未下确诊结论，转而向用户索要铭牌/现场型号信息，run 以 `WAITING_FOR_USER` 持久化——**行为符合设计预期**。验证脚本因未将 `WAITING_FOR_USER` 视为终态而轮询至超时（脚本限制，非服务卡死）。
- **真实模型用量**：约 4.8k 输入 token/轮，模型尝试事件（版本/结果码/耗时/token）均按白名单落库可审计。

### 5.4 未验证项

- 追问-补答的在线闭环（用户补答后重新入队续跑）本轮未继续执行（用户决定到此为止）；离线补答闭环由既有 PG 集成测试覆盖。
- 在线验收仅覆盖 qwen-plus；openai 兼容路径未实测（本地未配置 OPENAI_* 变量）。

## 6. 修复前后对比

| 项目 | 修复前 | 修复后 |
| --- | --- | --- |
| 工具结果可见性 | 仅 references + 非空 modelContext；get_asset/报告等对模型全黑 | 每次调用都产生有界白名单 ToolResult（成败/错误码/dataNature/引用 ID + 工具关键字段）；requiredChecks/limitations 可达模型 |
| RAG 原文扩展 | putIfAbsent 挡住同 ID 更长摘录，2000 字原文进不了上下文 | 身份一致时允许更长摘录替换；excerpt 同时经 ToolResult 白名单到达模型 |
| 重复调用 | 无参数级控制，相同调用可空转到预算耗尽 | 规范化参数键去重；仅拦截"已成功且无新信息"的相同调用，失败可重试、不同参数放行，反馈明确且不算成功取证 |
| 上下文大小 | 仅条数滑窗（8 条），单值无上限 | 单值 ≤1000、单条 ≤1600、累计 ≤6000，截断带标记，关键字段优先保留 |
| 引用和权限安全 | excerpt 必须本 run 已登记（DB 层强制） | 不变；mergeReference 校验来源身份，防同 ID 篡改；拦截不绕过网关鉴权；data 原始对象仍不进上下文 |

## 7. 遗留问题与后续建议（本轮不实现）

- 重复调用控制按"执行段"生效：补答重入后是新的 `runAgent` 调用，段间不共享去重键（步数预算与 attemptedTools 同样按段重建，口径一致）。
- `get_asset` 结果仍无引用登记（组件清单是登记信息而非事实记录），其可见性现经 ToolResult 摘要解决。
- 跨 Run 对话历史、执行状态恢复的更细粒度检查点、轻量 InvestigationState：均按任务要求记录为后续工作。
- 真实在线模型的协议遵从度验收（含 DUPLICATE_CALL 反馈的模型响应质量）。
