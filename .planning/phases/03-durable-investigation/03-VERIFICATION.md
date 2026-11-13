---
phase: 03-durable-investigation
verified: 2026-11-13T08:49:26Z
status: passed
score: 8/9 must-haves verified
covered_files: [
  ".planning/phases/03-durable-investigation/03-01-PLAN.md",
  ".planning/phases/03-durable-investigation/03-01-SUMMARY.md",
  ".planning/phases/03-durable-investigation/03-02-PLAN.md",
  ".planning/phases/03-durable-investigation/03-02-SUMMARY.md",
  ".planning/phases/03-durable-investigation/03-03-PLAN.md",
  ".planning/phases/03-durable-investigation/03-03-SUMMARY.md",
  "frontend/src/App.vue",
  "frontend/src/api.ts",
  "src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java",
  "src/main/java/io/devicetruth/adapter/persistence/AssetFactReferenceWriter.java",
  "src/main/java/io/devicetruth/adapter/persistence/AssetRegistryRepository.java",
  "src/main/java/io/devicetruth/api/AgentConversationController.java",
  "src/main/java/io/devicetruth/api/AssetController.java",
  "src/main/java/io/devicetruth/application/agent/AgentReportTools.java",
  "src/main/java/io/devicetruth/application/agent/AssetFactService.java",
  "src/main/java/io/devicetruth/application/agent/AssetTools.java",
  "src/main/java/io/devicetruth/application/agent/GeneralAgentService.java",
  "src/main/java/io/devicetruth/application/agent/RunRecoveryService.java",
  "src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java",
  "src/main/java/io/devicetruth/core/contract/AssetFacts.java",
  "src/main/resources/db/migration/V12__run_checkpoints.sql",
  "src/main/resources/db/migration/V13__run_lifecycle.sql",
  "src/main/resources/db/migration/V8__asset_registry_history.sql",
  "src/test/java/io/devicetruth/application/AgentLifecycleTest.java",
  "src/test/java/io/devicetruth/application/AssetToolTest.java",
  "src/test/java/io/devicetruth/application/RunRecoveryTest.java"
]
covered_digest: "v4:sha256:0f8ad00c3766d1f4b0e0f81761fe0650f3afbed44ca3b7a0c5db1843f8ea1080"
behavior_unverified: 1
overrides_applied: 1
re_verification:
  previous_status: gaps_found
  previous_score: 8/9
  gaps_closed:
    - "TOOL-03 死枚举 DATA_STALE/AMBIGUOUS 清理：提交 4cfb625 删除 AssetFacts.FailureCode 两枚枚举值并更新注释为'语义随 recordedAt 边界延后到 Phase 05'；REQUIREMENTS.md TOOL-03 行已更新为明确转 Phase 05 质量交付。复核方式：git show 4cfb625 diff 逐行核对 + 全仓 grep 确认无残留代码引用（DATA_STALE 仅余 AssetFacts.java:16 注释说明；AMBIGUOUS 剩余命中均为 legacy 路由 ASSET_AMBIGUOUS 字符串，与工具 FailureCode 无关）+ mvn -q compile 通过（枚举删除若有悬挂引用必然编译失败）+ 修复方回归 AssetToolTest 25/25、AgentLifecycleTest 15/15 真实 PG 绿"
  gaps_remaining: []
  regressions: []
overrides:
  - must_have: "TOOL-03 工具错误语义明确 NOT_FOUND / DATA_STALE / DENIED / AMBIGUOUS"
    reason: "NOT_FOUND/DENIED/INVALID 已覆盖本阶段工具负例并有真实 PG 负例测试；DATA_STALE/AMBIGUOUS 语义随 recordedAt 边界延后到 Phase 05 质量交付阶段连同负例补回（REQUIREMENTS.md TOOL-03 行已更新），死枚举经 4cfb625 清理、无误导性死代码（用户 2026-11-13 关闭 Phase 03 决定，复核确认）"
    accepted_by: "user close decision via orchestrator"
    accepted_at: "2026-11-13T08:49:26Z"
human_verification:
  - test: "浏览器人工演示：两轮问答（追问→补答）→刷新页面→重启浏览器，确认历史会话、待答问题与结果可找回且补答沿同一 run 继续"
    expected: "刷新/重开后无需原页面线程即可看到历史消息、待答问题并可继续补答；进行中 run 恢复轮询"
    why_human: "前端恢复逻辑（localStorage 找回 + GET /api/conversations + 轮询）已核对接线且 api 合同测试 18/18、构建通过，但真实浏览器交互行为无自动化覆盖（03-02 SUMMARY D6 偏差 d）"
  - test: "真实模型（qwen/openai）与真实 embedding（DashScope）链路验收：多轮调查、引用回溯、预算/审计事件"
    expected: "真实供应商路径下调查可完成、引用可回溯；供应商返回 usage 时 Token/成本落库，无 usage 时保持 UNKNOWN 不编造"
    why_human: "investigation 套件仅用 scripted+确定性 embedding 验收产品语义；环境缺 DASHSCOPE_API_KEY/OPENAI_API_KEY，真实链路 NOT_RUN"
  - test: "PDF/OCR 文档导入验收"
    expected: "PDF 可解析或明确报需 OCR，不伪造正文"
    why_human: "PDF 未实现，按 03-VALIDATION 明确转 Phase 5，本阶段无代码可验"
  - test: "真实多人认证（05-03 接入后）：不同用户/项目的会话、资产、知识、附件隔离"
    expected: "真实认证身份映射到 owner/project，跨用户访问被拒绝；不以 session UUID 代替认证"
    why_human: "本阶段为服务器固定本地身份（SECURITY.md AR-03-01 接受风险），真实认证尚未接入"
behavior_unverified_items:
  - truth: "刷新页面可找到历史会话、问题与结果而不依赖原线程（WEB-02）"
    test: "浏览器中完成一轮追问→补答→得到结果后刷新页面并重开浏览器"
    expected: "会话列表、历史消息、待答问题与答案从 PG 读回，可继续同一调查"
    why_human: "App.vue 的 localStorage 找回/readConversation/轮询接线与 api 合同测试已验证，但浏览器运行时行为（真实刷新/重开）无任何自动化测试 exercises"
---

# Phase 03: durable-investigation Verification Report

**Phase Goal:** 把同一任务的补证、资产时序查询、刷新/重启恢复、预算和取消贯通为完整调查。
**Verified:** 2026-11-13T08:49:26Z
**Status:** passed
**Re-verification:** Yes — after gap closure（gap 1 由 4cfb625 修复，详见 Gap Closure）

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Agent 可按资产和业务时点查询配置/维修来源（含有效期/记录时间/观测时间/来源类型） | ✓ VERIFIED | `AssetTools.java:90-165`（get_asset/get_configuration/get_maintenance_history 强类型入口与错误码）；`AssetRegistryRepository.java:71-116`（参数化 SQL + `valid_from<=? AND (valid_to IS NULL OR valid_to>?)` 时点过滤，全部带 owner/project）；V8 四表含 `valid_from/valid_to/recorded_at/observed_at/source_type`；行为测试 `AssetToolTest`（25 项，本验证人实跑 `latestDocumentWithoutFieldVerificationStaysUnconfirmed` 通过） |
| 2 | 历史改造与当前现场确认不会被混淆 | ✓ VERIFIED | V8 CHECK 强制 `FIELD_VERIFIED ⇒ source_type='FIELD_CONFIRMATION' AND observed_at IS NOT NULL`（V8__asset_registry_history.sql:52,83）；`AssetTools.installation()` 按组件+属性分组判定（AssetTools.java:309-318），局部确认不扩大为整体；测试 `fieldConfirmationAtValidTimeIsTheOnlyConfirmedPath`、`userStatementIsDocumentedNotFieldVerified`、`mixedVerificationAcrossComponentsKeepsChecksForUnverifiedGroups`、`conflictingFieldVerificationsAreNotConfirmed` 均存在（AssetToolTest） |
| 3 | 非本人/项目的资产、知识、附件和运行均不可越权 | ✓ VERIFIED | `ResourceAuthorizationTest` 8 项真实 PG HTTP 负例（跨归属 404 不枚举、`requestParametersCannotWidenIdentityOrScope`、`forgedOrForeignReferenceIdsCannotBeAttachedToOwnConversation` 等）；`AgentRunRepository.require/load` 全部 `WHERE owner_id=? AND project_id=?`；服务器固定身份（ProductConfiguration），请求体不得带 owner/project |
| 4 | 用户回复等待问题后同一调查继续，已有引用与事实保留 | ✓ VERIFIED | `GeneralAgentService.reply()`（216-241）：同事务校验 WAITING/pendingQuestionId/questionVersion，写消息+`queueReply` CAS（AgentRunRepository.java:337-345）；行为测试 `RunRecoveryTest#waitingRunSurvivesProcessRestartAndContinuesOnSameRun`（本验证人实跑通过，断言同 runId、步数递增、引用不丢、goal 消息仅一条） |
| 5 | WAITING 及已提交上下文可跨服务重启恢复 | ✓ VERIFIED | V12 租约列 + `RunRecoveryService.reconcile`（40-93）：QUEUED 重调度、过期租约 RUNNING 回收/超限 FAILED，version+租约双重 CAS；`GeneralAgentService.doRun`（361-390）每轮从 PG 重建上下文；`recoverQueued` 经 `ProductConfiguration.java:62 @Bean(initMethod="recoverQueued")` 启动接线；测试 `crashedRunningRunIsReclaimedByNewProcessAndResumesFromCheckpoint`、`concurrentReconciliationReclaimsCrashedRunOnlyOnce`、`crashedRunBeyondRecoveryLimitFailsExplicitlyInsteadOfStayingRunning`、`staleVersionAndCrossIdentityRepliesAreRejectedAfterRestart` |
| 6 | 刷新页面可找到历史会话、问题与结果而不依赖原线程 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | 接线已核实：`App.vue:208-235`（localStorage 找回 + readConversation + listConversations）、`api.ts:214`、轮询 `track()`；`api.test.ts` 18/18 + `npm run build` 通过。但真实浏览器刷新/重开行为无自动化测试 exercises → 转人工验证 |
| 7 | 重复提交不会重复执行任务（幂等） | ✓ VERIFIED | `GeneralAgentService.submit`（201-210）同事务 `hasRequest`→409 DUPLICATE_REQUEST（`ConversationRepository` 请求键唯一约束兜底）；行为测试 `AgentLifecycleTest#duplicateRequestKeyIsRejectedAndDoesNotDuplicateRun`（本验证人实跑通过，断言 run 数=1）；HTTP 套件 `verify_general_agent.py --suite=investigation`（scripts/verify_general_agent.py:424-438） |
| 8 | 取消/超时后的迟到响应不能覆盖终态 | ✓ VERIFIED | 三态取消：`AgentRunRepository.requestCancel`（284-308）QUEUED/WAITING 立即 CANCELLED、RUNNING 只打标；`persist()` 行锁内重查，非 RUNNING/已取消只记 RUN_LATE_RESPONSE（GeneralAgentService.java:398-420）；终态迁移全部 `WHERE status='RUNNING'` CAS；行为测试 `cancelRunningRunDiscardsLateModelResponse`（本验证人实跑通过，断言 RUN_ANSWER=0、RUN_LATE_RESPONSE=1、CANCELLED 终态不被覆盖）；`totalActiveDurationTerminatesSlowRunWithoutGhost`、`exhaustedQueuedRunFailsOnRecoveryInsteadOfDispatching` |
| 9 | 用户能看到稳定错误码，审计可追踪耗时/重试/可得用量 | ✓ VERIFIED | 稳定码：DUPLICATE_REQUEST/QUEUE_REJECTED/TOTAL_TIMEOUT/CANCELLED/RUN_ALREADY_TERMINAL/STALE_OR_UNRELATED_REPLY/MODEL_CONFIGURATION_MISSING/PERSIST_FAILURE/RECOVERY_ATTEMPT_LIMIT；审计：`RUN_TOOL`（含 durationMs，`AgentRunRepository.java:375-389`）、`RUN_MODEL`（版本/第几次尝试/耗时，394-406）、`budget.used` 回写（USAGE_SET_SQL 217-220）、`GET /api/runs/{id}/model-events`（AgentConversationController.java:59）；无 usage 时 `usageStatus=UNKNOWN` 不编造（enqueue 默认 budget，AgentRunRepository.java:87-90）；测试 `usageDurationAndToolCountsAreAuditableWithoutFabricatedNumbers`、`modelAttemptsAreRecordedWithBoundedRetryAndDuration`、`modelTransportFailureIsRecordedAsStableCodeNotEvidenceLack` |

**Score:** 8/9 truths verified（1 present-but-behavior-unverified，见 Human Verification）

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | ----------- | ------ | ------- |
| `src/main/resources/db/migration/V8__asset_registry_history.sql` | 带来源与时点的设备记录及全链路授权 | ✓ VERIFIED | 四表 + CHECK + 复合外键 + 时点索引；计划字面 V3 按 Flyway 版本排序调整为 V8，合理 |
| `src/main/resources/db/migration/V12__run_checkpoints.sql` | 执行租约/尝试次数 | ✓ VERIFIED | lease_owner/lease_expires_at/lease_heartbeat_at/attempts |
| `src/main/resources/db/migration/V13__run_lifecycle.sql` | 取消标记/起止/累计执行时长 | ✓ VERIFIED | cancel_requested_at/started_at/finished_at/active_duration_ms |
| `src/main/java/io/devicetruth/application/agent/GeneralAgentService.java` | 幂等/取消/预算/恢复的服务层 | ✓ VERIFIED | 482 行，三段构造器、submit/reply/cancel/dispatch/executeRun/persist 全链路 |
| `frontend/src/api.ts`、`frontend/src/App.vue` | 历史恢复与设备选择 | ✓ VERIFIED | 非桩：真实 fetch + localStorage + 轮询；18 项合同测试 |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| AssetTools | AssetRegistryRepository | 参数化资产/配置/维修查询带服务器授权与 asOf | ✓ WIRED | AssetTools.java:95/118/149 全部经 `context.ownerId()/projectId()` 调用仓储 |
| AssetRegistryRepository | V8 schema | 归属、有效期、来源条件落 SQL | ✓ WIRED | 查询/导入 SQL 与 V8 列名、唯一键一致；本验证人实跑 Flyway V1–V13 迁移成功 |
| AgentConversationController | GeneralAgentService | 补答显式绑定 waiting run 与版本 | ✓ WIRED | replyTo 三元组校验（replyTarget，GeneralAgentService.java:271-285） |
| RunRecoveryService | AgentRunRepository | 从 PG 检查点领取/恢复并 CAS 提交 | ✓ WIRED | requeueRecovered/failRecovered 双重 CAS（version + 租约过期） |
| frontend/src/api.ts | AgentConversationController | 刷新读取历史并继续同一调查 | ✓ WIRED | listConversations/readConversation/replyToQuestion 对应后端 GET/POST 端点 |
| ServerConfiguration | CapabilityGateway | 工具注册 | ✓ WIRED | `agentCapabilityGateway` 登记 knowledgeTools+assetTools+reportTools（ServerConfiguration.java:104-109） |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| RunView（前端轮询） | pendingQuestion/result/failureCode/activeDurationMs | `dt_agent_run` + `dt_run_event` RUN_ANSWER/RUN_FAILED 子查询（AgentRunRepository.java:35-51） | 是 | ✓ FLOWING |
| AssetTools 引用登记 | citation_excerpt/asOf/assetId | `AssetFactReferenceWriter` → `dt_evidence_reference`（V10 列） | 是 | ✓ FLOWING |
| AgentReportTools 报告 | stepsUsed/references/toolEvents | 本 run 持久视图聚合（AgentReportTools.java:54-90） | 是 | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| 幂等：重复请求键 409 且不重复建 run | `mvn -q '-Dtest=AgentLifecycleTest#duplicateRequestKeyIsRejectedAndDoesNotDuplicateRun+cancelRunningRunDiscardsLateModelResponse' test`（真实 PG 16.15，自建随机库，V1–V13 迁移） | Tests run: 2, Failures: 0, Errors: 0（verifier 自建临时角色 devicetruth_verify，跑完已 DROP） | ✓ PASS |
| 补证跨进程恢复 | `mvn -q '-Dtest=RunRecoveryTest#waitingRunSurvivesProcessRestartAndContinuesOnSameRun,AssetToolTest#latestDocumentWithoutFieldVerificationStaysUnconfirmed' test` | Tests run: 1+1, 全部 0 失败（surefire 报告核对） | ✓ PASS |
| 既有全量基线 | `target/surefire-reports/`（2026-11-13 16:24，03-03 打包产物） | 329/0/0/1：AgentLifecycleTest 15、RunRecoveryTest 9、AssetToolTest 25、ResourceAuthorizationTest 8、AgentReportToolTest 4 全绿 | ✓ PASS（引用既有证据） |

### Probe Execution

Phase plans 未声明 probe 脚本；CLI/investigation 套件（`scripts/verify_cli.py`、`scripts/verify_general_agent.py --suite=investigation`）在 SUMMARY 中记录为已实际执行 PASS，本次未重跑（HTTP 套件需起服务，超出 spot-check 约束；其断言内容已逐行核对，见 Requirements Coverage）。

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| AGENT-04 | 03-03 | 步数/重复/时间预算及有界错误重试 | ✓ SATISFIED（边界内） | `abortCheck`（GeneralAgentService.java:352-358，取消优先、累计执行时长不含等待）；AgentLifecycleTest#totalActiveDurationTerminatesSlowRunWithoutGhost、#modelAttemptsAreRecordedWithBoundedRetryAndDuration、#stepBudgetExhaustionFailsRunWithStableCodeAndNoAnswer、#waitingWallClockDoesNotConsumeTotalActiveDurationBudget、#resumedRunWithExhaustedActiveDurationBudgetFailsInsteadOfContinuing（ba7bf4f 补 3 项） |
| AGENT-06 | 03-02 | 任务摘要/待答/引用/工具输出可重建 | ✓ SATISFIED | RunRecoveryTest#waitingRunSurvivesProcessRestartAndContinuesOnSameRun、#crashedRunningRunIsReclaimedByNewProcessAndResumesFromCheckpoint；doRun 从 PG 重建（GeneralAgentService.java:360-390） |
| AGENT-07 | 03-02 | Hypothesis 为可选笔记 | ✓ SATISFIED（判断级） | 通用 Agent 路径无 hypothesis 准入/结案前置（hypothesis 仅存于 legacy Models/DecisionGuard，grep 核实）；VALIDATION：由 legacy E07/E10 证据承载，符合"可选笔记"边界 |
| DATA-01 | 03-01 | 资产/组件主数据与身份 | ✓ SATISFIED（边界内） | V8 `dt_asset/dt_asset_component`；AssetToolTest#assetQueryReturnsIdentityAndComponentsOnly；工业数据集延后（REQUIREMENTS 已记部分实现） |
| DATA-02 | 03-01 | 配置/维修结构化查询，不以 RAG 代替现场事实 | ✓ SATISFIED（边界内） | AssetTools 三工具 + `limitations()` 明示 RAG_NOT_FACT/USER_STATEMENT_ONLY（AssetTools.java:358-360）；AssetToolTest#maintenanceHistoryRespectsTimePoint |
| DATA-03 | 03-01 | 有效期/记录时间/观测时间与来源类型 | ✓ SATISFIED（边界内） | V8 时点列 + CHECK(recorded_at >= COALESCE(observed_at, valid_from))；AssetToolTest#fixtureImportKeepsIdentitySourceAndIsSimulated、#userStatementIsDocumentedNotFieldVerified、#recordsOutsideValidityWindowAreNotReturned；recordedAt 边界语义仍部分（REQUIREMENTS 如实记录） |
| STORE-04 | 03-02 | 重启后任务恢复或明确失败、无幽灵队列 | ✓ SATISFIED | V12 + RunRecoveryService + RunRecoveryTest 9 项（回收/并发对账/无孤儿/超限 FAILED） |
| TOOL-03 | 03-01 | 明确 NOT_FOUND/DATA_STALE/DENIED/AMBIGUOUS 错误 | ✓ RESOLVED（override + 4cfb625 清理） | NOT_FOUND（ASSET_NOT_FOUND）/DENIED/INVALID_ARGUMENTS 实现并有负例测试（AssetToolTest#invalidArgumentsAndUnauthorizedToolAreRejectedDistinctly 等）；DATA_STALE/AMBIGUOUS 语义延后 Phase 05 已接受为 override（见 frontmatter overrides），死枚举经 4cfb625 删除、全仓 grep + `mvn -q compile` 复核无残留引用 |
| TOOL-04 | 03-02 | 检索/资产/原文/追问/报告工具齐备 | ✓ SATISFIED | `generate_diagnosis_report` 只读白名单聚合（AgentReportTools.java:46-96），ServerConfiguration.java:109 注册进产品网关；AgentReportToolTest 4 项（含跨身份 REPORT_UNAVAILABLE） |
| TRUST-02 | 03-01 | 来源/归属/时间/冲突与引用完整性 | ✓ SATISFIED（边界内） | 引用身份=事实记录 ID + 实际查询资产/时点 + 摘要（AssetTools.java:261-293）；AssetToolTest#referencesPersistFactExcerptAndActualQueryScope、#configurationReferencesKeepEachQueryTimeAndRemainIdempotent、#sameSourceSameAttributeHistoryIsKeptAndQueryFollowsValidity |
| TRUST-04 | 03-01 | 身份与跨会话资产/文档授权 | ✓ SATISFIED（边界内） | ResourceAuthorizationTest 8 项；服务器固定身份、客户端不可扩权；真实多人认证 → 人工（05-03） |
| WEB-02 | 03-02 | 历史会话、设备选择与刷新恢复 | ⚠️ NEEDS HUMAN | 前端实现 + api.test.ts 18/18 + 构建通过；浏览器人工演示未执行（D6）→ 人工验证项 |
| OPS-01 | 03-03 | 有界队列、幂等提交、取消与任务总超时 | ✓ SATISFIED | AgentLifecycleTest#duplicateRequestKey…、#queueRejectionMarksFailedWithoutGhost、#cancelQueued/Waiting/Running/Completed…、#totalActiveDuration…；verify_general_agent.py investigation（409 幂等/CANCELLED/再取消 409 断言逐行核对） |
| OPS-03 | 03-03 | 模型/工具错误观测、调用用量与成本记录 | ✓ SATISFIED（边界内） | RUN_TOOL/RUN_MODEL 事件 + model-events 端点 + budget.used 回写 + usageStatus=UNKNOWN 诚实策略；AgentLifecycleTest#usageDurationAndToolCounts…、#transientToolError…、#modelTransportFailure…；真实供应商 usage 链路 → 人工 |

孤儿需求检查：REQUIREMENTS.md 中映射到 Phase 3 的需求即上述 14 项，三个 PLAN 的 requirements 字段合计正好覆盖 14/14，无孤儿。

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| — | — | TBD/FIXME/XXX/PLACEHOLDER/not yet implemented | — | 阶段修改的主代码文件中无命中（全 src/main/java 扫描）；无空返回桩；无 console.log 式实现 |
| src/main/java/io/devicetruth/core/contract/AssetFacts.java | 16 | 死枚举值（DATA_STALE/AMBIGUOUS 无生产者） | ⚠️ Warning（已解决） | 首轮发现的残留声明已由 4cfb625 删除并改注释为延后说明；复核 grep 仅余注释文字，无代码引用 |

### Human Verification Required

1. **浏览器两轮问答/刷新/重启人工演示（WEB-02）**
   **Test:** 浏览器完成 提问→追问→补答→得结果，然后刷新页面、关闭重开浏览器。
   **Expected:** 历史会话列表、消息、待答问题与答案从服务器读回，可继续同一调查；进行中 run 自动恢复轮询。
   **Why human:** 自动化仅覆盖 api 合同（18/18）、构建与后端真实 HTTP 链路；真实浏览器交互行为无测试 exercises。

2. **真实模型（qwen/openai）与真实 embedding 链路（AGENT-04/06、OPS-03、EVAL-02 相关）**
   **Test:** 配置 DASHSCOPE_API_KEY/OPENAI_API_KEY 后跑真实模型多轮调查与 `verify_general_agent.py` 在线套件。
   **Expected:** 调查完成、引用可回溯、真实 usage 落库；无 usage 时保持 UNKNOWN 不编造。
   **Why human:** 环境缺 key，本轮 NOT_RUN，不以 scripted 冒充真实供应商通过。

3. **PDF/OCR 文档导入**
   **Test:** 上传 PDF 观察解析/导入。
   **Expected:** 可解析或明确报需 OCR，不伪造正文。
   **Why human:** PDF 未实现，03-VALIDATION 明确转 Phase 5。

4. **真实多人认证（05-03 接入后，TRUST-04 边界）**
   **Test:** 两个真实用户互访对方会话/资产/知识/附件。
   **Expected:** 全部拒绝；身份来自真实认证而非 session UUID。
   **Why human:** 本阶段为服务器固定本地身份（AR-03-01 接受的计划内风险）。

### Gap Closure（2026-11-13 复审）

首轮唯一需求级缺口 **TOOL-03 的 DATA_STALE/AMBIGUOUS 死枚举** 已关闭：

- **修复提交 `4cfb625`**：删除 `AssetFacts.FailureCode` 中无生产者的 DATA_STALE/AMBIGUOUS 枚举值，注释更新为"语义随 recordedAt 边界延后到 Phase 05（REQUIREMENTS.md TOOL-03 记部分实现），届时连同负例一并补回"；REQUIREMENTS.md TOOL-03 行同步更新为明确转 Phase 05 质量交付。
- **复核方式（本验证人执行）**：`git show 4cfb625` diff 逐行核对（仅 AssetFacts.java 一文件、3 增 2 删）；全仓 grep 确认无残留代码引用——`DATA_STALE` 仅余 AssetFacts.java:16 注释说明文字，`AMBIGUOUS` 剩余命中均为 legacy 路由 `ASSET_AMBIGUOUS` 字符串（ConversationFlowService/DeterministicTaskRouter/RouteValidator/TaskRouterTest，与工具 FailureCode 无关）；`mvn -q compile` 通过（枚举删除若有悬挂引用必然编译失败）；修复方回归 AssetToolTest 25/25、AgentLifecycleTest 15/15（真实 PG）绿。
- **决策痕迹**：该延后以前文 overrides 记录保留（用户 2026-11-13 关闭 Phase 03 决定），两项语义的补回归属 Phase 05，届时需连同负例一并验收。

阶段核心目标（补证跨进程续跑、资产时序查询、刷新/重启恢复、预算与取消贯通）在代码与真实 PG 行为测试中全部成立；首次验证时本验证人独立实跑 4 个关键行为测试通过，本轮修复未触碰任何实现行为（仅删死枚举），无回归风险。其余结论与首轮一致：8/9 truths verified，1 项（浏览器刷新找回）保持 present-behavior-unverified 并保留在人工验证清单。

---

_Verified: 2026-11-13T08:49:26Z_
_Verifier: the agent (gsd-verifier)_
