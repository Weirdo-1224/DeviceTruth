---
phase: 02-open-rag
plan: "02"
subsystem: agent-conversation
tags: [postgresql, agent, tool-gateway, citation, vue, typescript]

requires:
  - phase: 02-open-rag
    provides: "02-01 知识导入/权限检索/引用登记、ToolContext、KnowledgeTools 合同"
  - phase: 01-baseline-contracts
    provides: "V1 会话/消息/QUEUED任务/事件表、product profile 装配、服务器固定身份与复合外键所有权模型"

provides:
  - "V5迁移：dt_message.run_id 关联运行，补答后可精确重建本 run 的消息序列"
  - "通用动作协议 CALL_TOOL/ASK_USER/ANSWER 与 GeneralReasoner/AgentTaskInput/AgentResult 合同"
  - "CapabilityGateway 通用工具注册与执行：未知工具/未授权/顶层参数错误一律拒绝"
  - "TaskOrchestrator.runAgent 通用主循环：唯一主循环演进，不复制 Runtime"
  - "AnswerGuard：引用子集校验、风险由服务器判定、无资产不得确诊"
  - "有界 worker：QUEUED→RUNNING→(WAITING_FOR_USER→QUEUED→RUNNING)→COMPLETED/FAILED"
  - "补答三元组 API 与 GET /api/runs/{id}/references"
  - "最小 Vue3/TS/Vite 产品页与 10 项前端单测"

affects: ["02-03 PDF/混合检索与真实供应商验收", "03-02 跨进程检查点与补答可靠性", "04-01 Skill 工具绑定", "05-01 SSE 实时进度", "05-02 工作台与评测"]

actuals:
  tokens: 78000
  tasks: 3
  commits: 0

tech-stack:
  added: ["vue@3.5.45", "vite@8.3.7", "vitest@5.1.0", "vue-tsc@3.3.14", "typescript@5.9.3", "@vitejs/plugin-vue@6.0.10"]
  patterns:
    - "工具注册即服务器授权：模型只能看到 toolCatalog(ToolContext) 里的工具与 JSON Schema"
    - "每次状态迁移都是带前置状态与版本号的条件更新，重复 worker 无法接手同一 run"
    - "答案只提交草稿，引用子集与风险等级由 AnswerGuard 重新判定"
    - "等待态只写一次事务后返回，worker 线程立即释放；补答重新入队沿用同一 runId"

key-files:
  created:
    - src/main/resources/db/migration/V5__agent_run_message_link.sql
    - src/main/java/io/devicetruth/core/contract/AgentTaskInput.java
    - src/main/java/io/devicetruth/core/contract/AgentResult.java
    - src/main/java/io/devicetruth/core/capability/AgentToolTypes.java
    - src/main/java/io/devicetruth/core/reasoning/GeneralReasoner.java
    - src/main/java/io/devicetruth/core/reasoning/ScriptedAgentReasoner.java
    - src/main/java/io/devicetruth/core/decision/AnswerGuard.java
    - src/main/java/io/devicetruth/adapter/StructuredAgentReasoner.java
    - src/main/java/io/devicetruth/adapter/AgentModelConnections.java
    - src/test/java/io/devicetruth/application/GeneralAgentFlowTest.java
    - src/test/java/io/devicetruth/server/GeneralAgentApiTest.java
    - frontend/package.json
    - frontend/package-lock.json
    - frontend/index.html
    - frontend/tsconfig.json
    - frontend/vite.config.ts
    - frontend/src/main.ts
    - frontend/src/env.d.ts
    - frontend/src/App.vue
    - frontend/src/api.ts
    - frontend/src/api.test.ts
  modified:
    - src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java
    - src/main/java/io/devicetruth/core/capability/CapabilityGateway.java
    - src/main/java/io/devicetruth/core/reasoning/ActionProposal.java
    - src/main/java/io/devicetruth/application/agent/GeneralAgentService.java
    - src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java
    - src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java
    - src/main/java/io/devicetruth/api/AgentConversationController.java
    - src/main/java/io/devicetruth/server/ServerConfiguration.java
    - src/main/java/io/devicetruth/server/ProductConfiguration.java
    - src/main/java/io/devicetruth/knowledge/KnowledgeTools.java
    - src/main/resources/application-product.properties
    - src/test/java/io/devicetruth/server/PersistentConversationTest.java
    - src/test/java/io/devicetruth/knowledge/KnowledgeSearchTest.java
    - src/test/java/io/devicetruth/knowledge/KnowledgeUpgradeMigrationTest.java

coverage:
  - id: A1
    description: "已注册的知识工具目录与 JSON Schema 真正进入推理器上下文，模型按协议提议 CALL_TOOL"
    requirement: TOOL-01
    verification:
      - kind: integration
        ref: "GeneralAgentFlowTest#registeredCatalogIsExposedToTheModelWithKnowledgeSchemas（真实Spring装配+真实PG，本轮执行）"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest#protocolModelSearchesThroughGatewayAndTheRunRegistersReferences（真实PG片段与引用登记）"
        status: pass
  - id: A2
    description: "网关拒绝未知工具、未授权上下文、顶层与嵌套参数错误，不执行 handler、不产生引用"
    requirement: TRUST-01
    verification:
      - kind: integration
        ref: "GeneralAgentFlowTest#unknownToolIsRejectedWithoutExecutingAndWithoutLeakingData"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest#nestedArgumentsAndAuthorizationAreEnforcedByTheGateway"
        status: pass
  - id: A3
    description: "答案引用必须是本 run 已取回集合的子集；伪造引用被拒绝且有界重试后 FAILED，不落库为答案"
    requirement: TRUST-05
    verification:
      - kind: integration
        ref: "GeneralAgentFlowTest#fabricatedCitationIsRejectedAndNeverStoredAsAnAnswer（ANSWER_REJECTED×2、无 RUN_ANSWER 事件）"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest#answerCitesOnlyReferencesCollectedByTheSameRun"
        status: pass
  - id: A4
    description: "无故障码的充电问题进入 Agent，追问后补答，同一 run 再检索并给出带引用答案（E2E-07-basic）"
    requirement: AGENT-03
    verification:
      - kind: integration
        ref: "GeneralAgentApiTest#twoRoundChargingConversationCompletesInTheSameRunWithCitations（真实PG+HTTP两轮，重复执行两次）"
        status: pass
      - kind: manual
        ref: "独立HTTP进程探针：既有devicetruth库V4→V5升级后，导入201→提交202→WAITING→补答202同run→COMPLETED(POSSIBLE_CAUSES/HIGH/2 claims/5 refs)，错版本补答409且runs=1（本轮实际执行）"
        status: pass
  - id: A5
    description: "WAITING_FOR_USER 释放 worker 线程，等待不是终局；错 run/错题号/旧版本/终态回复一律拒绝且不新建 run"
    requirement: AGENT-01
    verification:
      - kind: integration
        ref: "GeneralAgentFlowTest#waitingForUserReleasesTheWorkerSlotForOtherRuns（单 worker+容量0 下第二个任务仍被接手）"
        status: pass
      - kind: integration
        ref: "GeneralAgentApiTest#mismatchedOrConsumedReplyIsRejectedWithoutOpeningAnotherRun"
        status: pass
  - id: A6
    description: "调度拒绝与模型配置失败明确 FAILED 且不产生答案；M0 QUEUED 被正常接手"
    requirement: AGENT-02
    verification:
      - kind: integration
        ref: "GeneralAgentFlowTest#rejectedDispatchFailsTheRunInsteadOfLeavingItQueued（QUEUE_REJECTED 事件、无 ASSISTANT 消息）"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest#modelFailureEndsRunAsFailedWithoutAnswer"
        status: pass
      - kind: integration
        ref: "GeneralAgentApiTest#unsupportedReasonerConfigurationFailsTheRunInsteadOfAnswering（MODEL_CONFIGURATION_MISSING）"
        status: pass
  - id: A7
    description: "跨 owner 读取 run/引用/会话与越权补答统一 404，且不泄露原文"
    requirement: TRUST-01
    verification:
      - kind: integration
        ref: "GeneralAgentApiTest#otherOwnerCannotReadRunsOrReplyToThem"
        status: pass
  - id: A8
    description: "前端只提交原文与补答三元组，轮询安全、不误建新 run，错误码可直接展示"
    requirement: WEB-01
    verification:
      - kind: unit
        ref: "frontend/src/api.test.ts（12项：请求形状、幂等键、三元组、409/400错误码、等待态/完成/失败/超时停止轮询、失败原因映射）"
        status: pass
      - kind: build
        ref: "npm ci && npm run test -- --run && npm run build（vue-tsc 类型检查 + vite 构建，本轮实际执行）"
        status: pass
  - id: A9
    description: "无现场核验不得确诊：无资产/伪造资产/仅知识引用三种情况的实际答案正文与断言均不得表现为已确诊"
    requirement: TRUST-05
    verification:
      - kind: unit
        ref: "AnswerGuardTest#confirmedFindingIsRejectedWithoutFieldVerification / #confirmedClaimAloneIsRejectedEvenUnderKnowledgeCategory / #fabricatedOrUnauthorizedAssetCannotConfirmEither / #knowledgeOnlyAnswerStaysPossibleCausesAndSimulated"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest#unverifiedConfirmedFindingIsRefusedAndCorrectedToPossibleCauses（拒绝后改答可能原因）；#insistingOnConfirmedFindingFailsTheRun（坚持确诊则 FAILED/UNCONFIRMED_FINDING，无答案）"
        status: pass
      - kind: unit
        ref: "独立守卫探针：无现场证据时确定性故障结论可被缓和词掩盖，见 02-02-REVIEW.md"
        status: fail
  - id: A10
    description: "危险设备操作建议（短接/上电/绕过互锁/拆装等）在 summary、claim、suggestion 任一处出现即拒绝；安全边界引文仍可发布并标 HIGH"
    requirement: TRUST-05
    verification:
      - kind: unit
        ref: "AnswerGuardTest#dangerousControlAdviceIsRejectedInSeveralWordings / #safetyBoundaryStatementWithCitationIsAllowedAtHighRisk"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest#dangerousControlAdviceIsRefusedEvenWhenTheModelInsists（FAILED/UNSAFE_ANSWER）"
        status: pass
      - kind: unit
        ref: "独立守卫探针：肯定危险操作可被同一子句的禁止词掩盖，见 02-02-REVIEW.md"
        status: fail
  - id: A11
    description: "混合资料 dataNature 取保守值：全授权才 AUTHORIZED，混合/无引用为 SIMULATED"
    requirement: KNOW-06
    verification:
      - kind: unit
        ref: "AnswerGuardTest#mixedOrMissingDataNatureStaysSimulated"
        status: pass
  - id: A12
    description: "M0 遗留 QUEUED 在 worker 启动时被接手或明确失败；并发写入序号唯一、任务不留 RUNNING"
    requirement: AGENT-02
    verification:
      - kind: integration
        ref: "GeneralAgentApiTest#queuedRunFromPreviousProcessIsHandedOffAtStartup（关闭 worker 提交→重启 worker 接手为 WAITING_FOR_USER）"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest#concurrentRunsKeepUniqueMessageSequenceAndTerminalState（双 worker 并发，序号 1..4 唯一连续）"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest#persistenceFailureMarksTheRunFailedInsteadOfLeavingItRunning（触发器注入落库失败→FAILED/PERSIST_FAILURE）"
        status: pass
  - id: A13
    description: "失败原因对用户可见且安全：RunView.failureCode 为白名单码，前端映射为可读说明"
    requirement: WEB-01
    verification:
      - kind: integration
        ref: "GeneralAgentApiTest#unsupportedReasonerConfigurationFailsTheRunInsteadOfAnswering（failureCode=MODEL_CONFIGURATION_MISSING）"
        status: pass
      - kind: unit
        ref: "frontend/src/api.test.ts#失败原因码映射成可读说明 / #失败运行把原因码透传给页面"
        status: pass

status: complete
review_status: passed_with_known_issues
---

# 02-02 接通 Agent、Vue 与同一 run 的补证对话（用户接受已知问题后通过）

> **2026-11-11 独立复审：** 用户明确要求“如果还有问题，就做好记录，直接通过”。本计划按该决定推进，但仍有 3 项 P1、1 项 P2 未关闭，详见 [02-02-REVIEW](02-02-REVIEW.md)。A9/A10 的现有用例通过不代表安全边界完整；独立反例为 fail。下方修复轮关于“结构性关闭”“最终展示由服务器控制”的描述仅为当时的开发报告，已被本次复审纠正：模型自由文本仍可透传。真实模型、真实 embedding、浏览器 E2E、真实现场及跨进程恢复仍未验证。

## 审查修复轮（2026-11-11，独立审查提出 P1×5 + P2×2）

审查意见全部按“修复 + 可验证测试”处理，未降低任何断言：

| 编号 | 问题 | 修复 | 验证 |
| --- | --- | --- | --- |
| P1-1 | 无现场核验仍可能输出确诊（仅降类别、正文与断言保留） | `AnswerGuard` 增加服务器判定的 `fieldVerified`（M1 恒为 false）：`CONFIRMED_FINDING` 或任一 `CONFIRMED` 断言一律 `UNCONFIRMED_FINDING` 拒绝，不再“降级但保留确诊正文”；资产编号只登记不当作核验，并在 limitations 中声明“尚未核验” | `AnswerGuardTest` 4 项；`GeneralAgentFlowTest#unverifiedConfirmedFindingIsRefusedAndCorrectedToPossibleCauses`、`#insistingOnConfirmedFindingFailsTheRun` |
| P1-2 | 危险建议可绕过（只匹配固定短语） | 改为**逐句**判断：动作词（短接/上电/强制闭合/绕过互锁/拆装/拆除/屏蔽保护等）出现在**没有安全语义**的句子里即 `UNSAFE_ANSWER`；summary、claims、suggestions 三处都查；带“不得/禁止/必须由具备资质/断电验电”等安全边界的引文仍允许，但标记 HIGH + REQUIRES_QUALIFIED_PERSONNEL | `AnswerGuardTest#dangerousControlAdviceIsRejectedInSeveralWordings`（5 种表述 + claim/suggestion 夹带）、`#safetyBoundaryStatementWithCitationIsAllowedAtHighRisk`；`GeneralAgentFlowTest#dangerousControlAdviceIsRefusedEvenWhenTheModelInsists` |
| P1-3 | 混合资料被整体标为 AUTHORIZED | dataNature 改保守口径：**全部引用都是 AUTHORIZED** 才标 AUTHORIZED，混合或无引用一律 SIMULATED | `AnswerGuardTest#mixedOrMissingDataNatureStaysSimulated`（混合/纯授权/无引用三态） |
| P1-4 | 启动时不接手 M0 遗留 QUEUED | `GeneralAgentService.recoverQueued()`（`@Bean(initMethod=...)`）在装配完成后扫描本身份 QUEUED 并重新入队；执行器拒绝时明确 FAILED/QUEUE_REJECTED；RUNNING 遗留仍留给 M2（不冒充可恢复） | `GeneralAgentApiTest#queuedRunFromPreviousProcessIsHandedOffAtStartup`（worker 关闭提交→重启 worker 开启→接手为 WAITING_FOR_USER） |
| P1-5 | 同会话并发写助手消息可能撞 `UNIQUE(conversation_id,seq)` 并把 run 留在 RUNNING | 会话行锁下沉到 `ConversationRepository.append(...)`（所有写入者都先 `SELECT ... FOR UPDATE`）；`persist` 包在异常处理内，落库失败置 `PERSIST_FAILURE`；事务内加锁顺序固定为“会话行→run 行” | `GeneralAgentFlowTest#concurrentRunsKeepUniqueMessageSequenceAndTerminalState`（双 worker 并发提交+完成，序号 1..4 唯一连续、无 RUNNING/QUEUED 遗留）、`#persistenceFailureMarksTheRunFailedInsteadOfLeavingItRunning`（触发器注入落库失败） |
| P2-1 | 只在 claim 中声明的引用不出现在答案引用列表 | 引用并集 = 草稿声明 + 各断言声明，按出现顺序去重，只保留本 run 已取回对象 | `AnswerGuardTest#claimOnlyCitationsAppearInTheAnswerReferences` |
| P2-2 | 页面只显示“执行失败” | `RunView` 增加白名单 `failureCode`（读自 RUN_FAILED 事件），前端 `describeFailure()` 映射为可读说明并在失败卡片展示原因码 | `GeneralAgentApiTest#unsupportedReasonerConfigurationFailsTheRunInsteadOfAnswering`（failureCode=MODEL_CONFIGURATION_MISSING）；`frontend/src/api.test.ts` 新增 2 项 |

修复后实际执行（同一台机、同一 PG 实例）：

| 命令 | 结果 |
| --- | --- |
| `mvn -o test` | **215 / 0 / 0 / 0**，BUILD SUCCESS（原 199 + AnswerGuard 10 + Flow 4 + API 2） |
| `GeneralAgentFlowTest` | 14 / 0 / 0 / 0 |
| `GeneralAgentApiTest` | 6 / 0 / 0 / 0 |
| `AnswerGuardTest` | 10 / 0 / 0 / 0 |
| `mvn -o -q -DskipTests package` + `python` `scripts/verify_cli.py` | 通过（A/B/C/D 与缺密钥 FAILED） |
| 独立 HTTP 进程两轮探针（既有 devicetruth 库，迁移已是 V5） | 仍 COMPLETED / POSSIBLE_CAUSES / HIGH / SIMULATED / 2 claims / 1 run；错版本补答 409 |
| `npm run test -- --run` / `npm run build` | 12 / 12 通过；构建成功 |

审查项仍未覆盖的部分保持 NOT_RUN：真实模型、真实 embedding、浏览器人工演示、跨进程 RUNNING 恢复（M2）。
`fieldVerified` 在 M1 恒为 false 是刻意的：03-01 接入带来源与时点的资产记录前，服务器没有可信的“已核验”来源。

## 审查修复轮 2（2026-11-11，复审 P1×3）

| 编号 | 问题 | 修复 | 验证 |
| --- | --- | --- | --- |
| P1-1 | 确诊正文绕过守卫：类别填 KNOWLEDGE、claims 为空，摘要写“已确诊”仍原样返回 | `AnswerGuard` 第 4 步在枚举之外新增正文级检查：无 `fieldVerified` 时，summary/claims/suggestions/uncertainties/limitations 任何字段出现完成时态确诊表述（已确诊/已确认/确认为/已确定/已查明/已证实/裸“确诊”）一律 `UNCONFIRMED_FINDING` 拒绝且不回传正文；否定/待查/建议核实语境（未、无法、疑似、需、建议…）与 `fieldVerified=true` 不受影响 | `AnswerGuardTest` +3 项（摘要绕过枚举、POSSIBLE 断言正文确诊、软化语境与已核验放行） |
| P1-2 | 安全词同句放行 + uncertainties/limitations 未检查：“有风险，但请短接高压互锁端子”因含“风险”通过；危险操作还能夹带进 uncertainties/limitations | `recommendsControlAction` 由逐句改为**逐句逐子句**：句子再按转折词（但/但是/然而/不过/却）与冒号切分，动作词出现在无安全语义的子句即 `UNSAFE_ANSWER`；检查字段扩展到 uncertainties 与 limitations；逗号不切分，保留“任何涉及…的操作，都必须由具备资质的人员现场执行”这类安全告知句式 | `AnswerGuardTest` +3 项（转折子句、冒号子句、uncertainties/limitations 夹带、禁止性安全提示放行） |
| P1-3 | 遗留 QUEUED 超过 100 条时启动扫描只取一页，其余变幽灵任务 | `AgentRunRepository.queued(identity)` 去掉 `LIMIT 100`  artificial 上限，取本身份全部 QUEUED；执行器容量不足时既有 `FAILED/QUEUE_REJECTED` 路径兜底 | `GeneralAgentApiTest#queuedRunsBeyondOldRecoveryPageAreAllHandedOffOrFailed`：worker 关闭积压 105 条 → 启动后无一条残留 QUEUED，全部被接手（WAITING_FOR_USER 带待答问题）或明确 FAILED/QUEUE_REJECTED |

修复后实际执行（同一台机、docker `interview-postgres` pgvector/pg16，凭据仅注入测试进程）：

| 命令 | 结果 |
| --- | --- |
| `AnswerGuardTest` | 16 / 0 / 0 / 0（原 10 + 新增 6） |
| `GeneralAgentApiTest` | 7 / 0 / 0 / 0（原 6 + 新增 1；105 条遗留队列用例实际跑过，日志确认 105 条全部派发、容量不足产生 QUEUE_REJECTED） |
| `GeneralAgentFlowTest` | 14 / 0 / 0 / 0（新守卫无误伤） |

## 审查修复轮 3（2026-11-11，复审剩余 P1×2）

| 编号 | 问题 | 修复 | 验证 |
| --- | --- | --- | --- |
| P1-a | 危险操作建议仍可能通过守卫：“请短接高压互锁端子，注意风险”“验电后短接互锁端子”复现为 allowed | 放弃“关键词+安全词”的猜测式判断，改为**白名单式**：含设备控制动作的子句只有两类可放行——禁止性安全提示（不得/禁止/不要/严禁…）或资质边界声明（必须由/应由 + 具备资质/专业人员/厂家）；弱安全词（风险/断电/验电/现场执行）不再放行任何动作。其余一律 `UNSAFE_ANSWER` 拒绝 → 模型重答一次 → 仍不合法 FAILED。五个返回字段全部检查。高风险答案另附**服务端受控安全提示**（`SERVER_SAFETY_NOTICE`，幂等），最终展示由服务器控制 | `AnswerGuardTest` +5 项（弱安全词 4 句式、五字段夹带、资质边界放行+服务端提示幂等）；`GeneralAgentFlowTest#dangerousControlAdviceIsRefusedEvenWhenTheModelInsists`（ insisting → FAILED/UNSAFE_ANSWER） |
| P1-b | 无现场证据仍可输出确定性故障结论：“该设备的故障原因是充电控制器损坏”复现为 allowed | 确诊路径整体关闭（M1）：无 `fieldVerified` 时，除完成时态确诊表述外，新增**当前设备确定性故障断言**检查——设备指代（该设备/该车/本车/当前车辆…）+ 确定性结论词（是/为/导致/损坏/失效/故障…）且无缓和词（可能/疑似/常见/建议/尚未…）一律 `UNCONFIRMED_FINDING`；知识引用只支持一般知识与可能原因，`POSSIBLE_CAUSES`+引用+限制的结构化路径完全保留，否定性表述（尚未确诊/需要现场核实）不误判；`fieldVerified=true`（03-01 才接入真实核验）后才开放 | `AnswerGuardTest` +2 项（摘要/断言两路拒绝 + 已核验放行、可能原因完整保留、否定表述不误判）；`GeneralAgentFlowTest` +2 项（拒绝→修正为可能原因后 COMPLETED 且引用保留、最终内容不含确定性设备结论；反复提交 → FAILED/UNCONFIRMED_FINDING） |

另：`TaskOrchestrator` 的拒绝反馈按错误码附带修正指引（UNSAFE_ANSWER→移除具体危险步骤；UNCONFIRMED_FINDING→只能给带来源的可能原因），有界重试（1 次）后 FAILED 的语义不变。

修复后实际执行（同一台机、docker `interview-postgres`，凭据仅注入测试进程）：

| 命令 | 结果 |
| --- | --- |
| `AnswerGuardTest` | 21 / 0 / 0 / 0（新增 5 项） |
| `GeneralAgentFlowTest` | 16 / 0 / 0 / 0（新增 2 项） |
| 全量 `mvn -o test` | **229 / 0 / 0 / 0**，BUILD SUCCESS |
| `mvn -o -DskipTests package` + `python` `scripts/verify_cli.py` | 通过（A/B/C/D、缺密钥拒绝、私钥文件读取均 PASS） |

既有保护（引用子集校验、dataNature 保守标记、只读工具、同 run 补答、legacy A–D 回归）全部保留并随全量测试复验。
真实模型、真实 embedding、浏览器 E2E 仍未验证；`fieldVerified` 在 M1 恒为 false，真实资产与现场证据能力留到 03-01。


## 本轮交付

- **通用协议**：`ActionProposal` 在保留 legacy 五动作的前提下追加 `CALL_TOOL`/`ASK_USER`/`ANSWER`；
  新合同 `AgentTaskInput`（原文、消息序列、可选资产、步数预算）与 `AgentResult`（事实/可能性/建议/人工项、
  引用、风险、dataNature）。legacy 循环遇到新动作会显式拒绝，不会被静默放行。
- **唯一主循环演进**：`TaskOrchestrator.runAgent(...)` 与 `run(...)` 共用同一编排器类；
  通用循环每轮只执行一个动作，工具经 `CapabilityGateway.executeTool`，答案经 `AnswerGuard`。
- **网关**：`registerTool/toolCatalog/executeTool` 与 legacy 单值能力分离；顶层参数按 `inputSchema` 校验，
  嵌套 `filters` 由 `KnowledgeTools` 强类型解析拒绝，未知工具返回 `UNKNOWN_TOOL`。
- **产品装配**：`ServerConfiguration.agentCapabilityGateway` 只登记知识工具（legacy `capabilityGateway` 保留并 `@Primary`）；
  `ProductConfiguration.generalAgentService` 提供每 run 新建推理器、有界 worker 与
  `devicetruth.agent.*` 配置（reasoner / worker-enabled / max-concurrent-runs / max-queue）。
- **持久化**：`V5__agent_run_message_link.sql` 给 `dt_message` 增加可空 `run_id`（复合外键 + CHECK），
  补答后能按 run 精确重建消息序列；`AgentRunRepository` 负责状态机、待答问题、引用读取与白名单事件；
  `ConversationRepository` 新增助手消息、补答消息与 `runMessages`。
- **API**：`POST /api/conversations/{id}/messages` 支持 `replyTo` 三元组（`REPLY_TARGET_REQUIRED`/`STALE_OR_UNRELATED_REPLY`），
  `GET /api/runs/{id}` 返回 `pendingQuestion` 与 `result`，新增 `GET /api/runs/{id}/references`。
- **前端**：`frontend/` 为 Vue3 + TS + Vite 最小产品页，`api.ts` 冻结两个合同（新消息、补答三元组）与安全轮询，
  `App.vue` 展示持久消息、运行状态、追问、答案与引用；SSE 留到 05-01。

## 实际命令与结果（本轮，JDK 21.0.13.0.2 / Maven 3.9.9）

| 命令 | 结果 |
| --- | --- |
| `mvn -o test`（真实PG环境变量指向既有 interview-postgres） | 215 / 0 / 0 / 0，35 suites，BUILD SUCCESS（首轮 199，审查修复后 215） |
| `mvn -o -q -DskipTests package` | 成功，`target/devicetruth-core-0.1.0.jar` |
| `python` `scripts/verify_cli.py` | A/B/C/D 与缺密钥 FAILED、私有配置读取全部 PASS |
| 打包类 + 真实 `devicetruth` 库启动独立 HTTP 进程 | Flyway 只应用 V5（既有库 V4→V5 升级），健康启动 8.5s；修复后重跑两轮探针仍 COMPLETED |
| `frontend: npm ci` | 从 lockfile 干净安装 70 包 |
| `frontend: npm run test -- --run` | 12 / 12 通过（审查修复新增 2 项失败原因映射） |
| `frontend: npm run build` | `vue-tsc --noEmit` + `vite build` 成功 |

真实 PG 细节：随机库 `devicetruth_test_*`，每轮创建并清理；PostgreSQL 16.15 / pgvector 0.8.7 / Flyway 5 个迁移。
新增用例：`GeneralAgentFlowTest` 14 项、`GeneralAgentApiTest` 6 项、`AnswerGuardTest` 10 项；既有 185 项全部保留
（另调整 3 处与本次行为变化直接相关的断言，见下）。

## 有意变更的既有断言（不是降低标准）

1. `PersistentConversationTest` 的 `replyTo` 负例：原来断言 `REPLY_NOT_AVAILABLE`（补答尚未实现），
   现在补答已实现，缺字段请求按合同返回 400 `REPLY_TARGET_REQUIRED`，断言仍要求 400 且不写消息、不建 run。
2. `PersistentConversationTest` 迁移数量 4 → 5（新增 V5），并显式 `--devicetruth.agent.worker-enabled=false`，
   保持该用例只验证 M0 持久化合同（否则异步 worker 会推进状态、破坏“重启前后一致”的比较）。
3. `KnowledgeSearchTest` / `KnowledgeUpgradeMigrationTest`：同样关闭 worker 并把迁移计数改为 5，
   避免并发检索改变引用归属断言。

## 未验证（NOT_RUN，不得视为通过）

- **真实模型（千问/OpenAI）通用 Agent 调用**：只验证了协议解析与 `AgentModelConnections` 的构造路径，
  本机无可用供应商密钥，未发真实请求；`qwen`/`openai` 分支保持未验收。02-03 负责真实供应商验收。
- **真实 embedding 与真实向量召回**：本计划全部使用确定性字符向量，只证明合同，不证明语义质量。
- **浏览器人工演示 E2E-01/02/03 与 E2E-07-basic**：无浏览器自动化授权/环境，界面只做了类型检查、构建与单测；
  页面与后端的真实点击流程未演示。
- **PDF、混合检索、知识工具接入 Skill/双域**：属 02-03 与 Phase 4。
- **跨进程恢复、租约/CAS、补答幂等与崩溃扫描**：按计划留到 M2（03-02/03-03）；当前只有单进程正常路径与
  “条件更新防重复接手”。
- **并发补答压力、规模与性能**：未测。

## 风险与后续

- **风险 1**：`WAITING_FOR_USER → QUEUED` 依赖数据库条件更新，单进程可靠；多实例部署前必须补租约/CAS 与幂等（M2）。
- **风险 2**：脚本化推理器只是离线确定性替身，`POSSIBLE_CAUSES` 文案与检索质量不代表真实模型表现。
- **风险 3**：`AnswerGuard` 的禁用词与风险关键词是最小集合，真实产品需要更完整的风险策略与评测（M4）。
- **风险 4**：`dt_message.run_id` 为可空列，历史消息不回填；查询依赖 `run_id=? OR id=goal_message_id`，
  迁移已按“只新增可空列 + 复合外键 + CHECK”实现，可前进不可回退。
- **风险 5**：`AnswerGuard` 的安全性依赖“动作词 + 安全语义”逐句判断，属于规则匹配而非语义理解；
  真实模型可能用未收录的表述绕过，M4 的答案安全评测需要更完整的策略与语料。
- **风险 6**：`fieldVerified` 在 M1 恒为 false，因此本阶段**不存在**合法的确诊答案路径；
  03-01 接入带来源与时点的资产记录后，才由服务器提供“已核验”判定。
- **风险 7**：启动恢复只处理 QUEUED；进程崩溃留下的 RUNNING 仍需 M2 的租约/CAS 与检查点恢复。
- **待审查**：`status: blocked`、`review_status: pending`；未标记 PLAN 完成、未修改 ROADMAP 阶段态、未 commit/push。
