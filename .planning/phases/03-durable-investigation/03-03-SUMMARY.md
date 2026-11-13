---
phase: 03-durable-investigation
plan: "03"
subsystem: agent-runtime
tags: [postgres, flyway, idempotency, cancel, deadline, budget, observability, spring-boot]

requires:
  - phase: 03-02
    provides: [V12 执行租约、RunRecoveryService 启动对账、补答跨进程续跑、RECOVERY_ATTEMPT_LIMIT 终态]
provides:
  - V13 生命周期迁移（cancel_requested_at/started_at/finished_at/active_duration_ms）
  - CANCELLED 终态：POST /api/runs/{id}/cancel（QUEUED/WAITING 立即终态，RUNNING 协作式收尾）
  - 总时限 enforced：累计执行时长超限 FAILED/TOTAL_TIMEOUT，恢复/对账不清零、不复活
  - 迟到模型响应只记 RUN_LATE_RESPONSE 事件，绝不覆盖终态
  - 运行审计：RUN_TOOL 单次耗时、RUN_MODEL 版本/重试/耗时、budget.used 回写，无 usage 不编造
  - verify_general_agent.py --suite=investigation（scripted 确定性 HTTP 验收）
  - AgentLifecycleTest（12 项真实 PG 生命周期/观测回归）
affects: [04-01, 05-01, 05-02, verify-work]

actuals:
  tokens: 24369
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "协作式取消：RUNNING 只打 cancel 标记，worker 在模型每步之间检查并以终态 CAS 收尾"
    - "迟到响应防御：persist 在行锁内重查状态与取消标记，非 RUNNING/已取消时只写 RUN_LATE_RESPONSE"
    - "总时限=累计执行时长（budget.limits.maxActiveDurationMs，不含等待用户时间），跨段累计不清零"
    - "用量诚实：供应商无 usage 时 Token/成本为 null、usageStatus=UNKNOWN；计数只写可测量项"
    - "终态统一回写：steps/activeDurationMs/modelAttempts/toolCalls 在一次条件 UPDATE 内落库"

key-files:
  created:
    - src/main/resources/db/migration/V13__run_lifecycle.sql
    - src/test/java/io/devicetruth/application/AgentLifecycleTest.java
  modified:
    - src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java
    - src/main/java/io/devicetruth/application/agent/GeneralAgentService.java
    - src/main/java/io/devicetruth/application/agent/RunRecoveryService.java
    - src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java
    - src/main/java/io/devicetruth/core/reasoning/Reasoner.java
    - src/main/java/io/devicetruth/adapter/StructuredAgentReasoner.java
    - src/main/java/io/devicetruth/api/AgentConversationController.java
    - src/main/java/io/devicetruth/server/ProductConfiguration.java
    - src/main/resources/application-product.properties
    - frontend/src/api.ts
    - scripts/verify_general_agent.py
    - README.md

key-decisions:
  - "总时限口径=累计执行时长（activeDurationMs，不含等待用户的时间），复用每个 run 行内 budget.limits.maxActiveDurationMs（默认 120000ms，devicetruth.agent.max-active-duration-ms 可配）；恢复重跑与启动对账不清零，超限明确 FAILED/TOTAL_TIMEOUT"
  - "取消语义分态：QUEUED/WAITING_FOR_USER 不持租约，取消立即迁移 CANCELLED；RUNNING 由持有者 worker 每步之间协作式检查，取消标记优先于模型响应，收尾为 CANCELLED 且迟到响应只记 RUN_LATE_RESPONSE 事件"
  - "供应商（AgentScopeStep）当前不暴露 usage：Token/成本保持 null、usageStatus=UNKNOWN，只记录可测量的步数/尝试/工具调用/耗时，不编造数值"
  - "investigation 验收套件使用 scripted 推理器+确定性 embedding，不依赖真实模型密钥即可验收产品生命周期语义；真实模型链路保持 NOT_RUN 不冒称通过"

patterns-established:
  - "幂等：持久请求键（conversation 内唯一）重复提交 409 DUPLICATE_REQUEST，消息与 run 同事务创建，绝不重复执行任务"
  - "容量：有界队列拒绝即 FAILED/QUEUE_REJECTED；恢复对账只接手 QUEUED 且执行时长未超限的遗留，不留幽灵任务"
  - "预算跨段累计：等待释放 worker 后，恢复/补答从 budget.used 与 active_duration_ms 累计续跑"

requirements-completed: ["AGENT-04", "OPS-01", "OPS-03"]

coverage:
  - id: D1
    description: "幂等提交与有界队列：同 Idempotency-Key 重复提交 409 且不重复执行；队列占满拒绝即 FAILED/QUEUE_REJECTED，无幽灵任务"
    requirement: "OPS-01"
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#duplicateRequestKeyIsRejectedAndDoesNotDuplicateRun"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#queueRejectionMarksFailedWithoutGhostRun"
        status: pass
      - kind: e2e
        ref: "python scripts/verify_general_agent.py --suite=investigation --embedding=deterministic --reasoner=scripted（idempotency http=409 DUPLICATE_REQUEST runs=1）"
        status: pass
    human_judgment: false
  - id: D2
    description: "取消与总时限：QUEUED/WAITING 取消立即 CANCELLED，RUNNING 协作式收尾；终态再取消 409；累计执行时长超限 FAILED/TOTAL_TIMEOUT，恢复不复活"
    requirement: "OPS-01"
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#cancelQueuedRunWhileWorkerBusyStaysCancelledAfterRecovery"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#cancelWaitingRunTerminatesAndReplyIsRejected"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#cancelRunningRunDiscardsLateModelResponse"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#totalActiveDurationTerminatesSlowRunWithoutGhost"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#exhaustedQueuedRunFailsOnRecoveryInsteadOfDispatching"
        status: pass
      - kind: e2e
        ref: "python scripts/verify_general_agent.py --suite=investigation（cancel=CANCELLED，补答 409 STALE_OR_UNRELATED_REPLY，再取消 409 RUN_ALREADY_TERMINAL）"
        status: pass
    human_judgment: false
  - id: D3
    description: "预算跨段累计与有界重试：恢复/补答不清零累计步数与执行时长；模型重试上限 2 次后明确 FAILED；答案拒绝有界修正"
    requirement: "AGENT-04"
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#modelAttemptsAreRecordedWithBoundedRetryAndDuration"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#usageDurationAndToolCountsAreAuditableWithoutFabricatedNumbers"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#waitingRunSurvivesProcessRestartAndContinuesOnSameRun（stepsUsed/activeDurationMs 跨进程累计，03-02 基线）"
        status: pass
    human_judgment: false
  - id: D4
    description: "错误观测与用量诚实：工具事件带耗时/错误码；模型尝试带版本/重试/耗时；技术错误稳定码不伪装为证据不足；无 usage 时 UNKNOWN 不编造"
    requirement: "OPS-03"
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#transientToolErrorIsRecordedAndRunStillCompletes"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#modelTransportFailureIsRecordedAsStableCodeNotEvidenceLack"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentLifecycleTest.java#usageDurationAndToolCountsAreAuditableWithoutFabricatedNumbers"
        status: pass
      - kind: e2e
        ref: "python scripts/verify_general_agent.py --suite=investigation（audit=activeDurationMs>0 且 usageStatus=UNKNOWN）"
        status: pass
    human_judgment: false
  - id: D5
    description: "旧回归不退化：legacy CLI、旧 HTTP、知识迁移、前端合同与构建全部保持通过"
    verification:
      - kind: other
        ref: "mvn -o -q package：329 tests，0 failures/0 errors/1 skipped（含 AgentLifecycleTest 12、RunRecoveryTest 9）"
        status: pass
      - kind: other
        ref: "python scripts/verify_cli.py：A/B RESOLVED、C CONFLICT、D NEED_EVIDENCE，缺 key 不降级，全 PASS"
        status: pass
      - kind: other
        ref: "cd frontend && npm run test -- --run（18/18）与 npm run build（vue-tsc+Vite）"
        status: pass
    human_judgment: false
  - id: D6
    description: "真实模型/真实 embedding 链路验收（E2E-11 真实供应商路径、真实多轮追问质量）"
    verification: []
    human_judgment: true
    rationale: "本轮环境缺 DASHSCOPE_API_KEY/OPENAI_API_KEY 与真实 embedding 配额，保持 NOT_RUN；investigation 套件仅证明 scripted+确定性 embedding 下的产品语义，不冒称真实模型通过"

duration: 50min
completed: 2026-11-13
status: complete
plan_head_before: 052e3c16b5c396fade1721c220cd75c6e2408c6f
plan_head_after: 6be9be0426c41a35b75ad3c9edcc2888532a1c84
---

# Phase 03 Plan 03: 幂等、取消、预算与错误观测 Summary

**V13 生命周期迁移 + CANCELLED 终态让取消/超时后的迟到响应不能覆盖终态；持久请求键与有界队列拒绝不留幽灵任务；总时限按累计执行时长（不含等待）跨段强制；工具/模型事件带耗时、版本与重试计数，供应商无 usage 时保持 UNKNOWN 不编造。**

## Performance

- **Duration:** ~50 min
- **Started:** 2026-11-13T07:03:00Z（Asia/Shanghai 15:03）
- **Completed:** 2026-11-13T07:53:00Z
- **Tasks:** 3/3
- **Files modified:** 16

## Accomplishments

- 幂等、容量、取消与总时限贯通（任务 1）：持久请求键重复提交 409；队列拒绝 FAILED/QUEUE_REJECTED；`POST /api/runs/{id}/cancel` 三态取消；总时限=累计执行时长上限，超限 FAILED/TOTAL_TIMEOUT，恢复不清零、对账不复活。
- 取消/超时后的迟到模型响应只记 RUN_LATE_RESPONSE 事件，persist 行锁内重查状态与取消标记，绝不覆盖 CANCELLED/FAILED/COMPLETED 终态。
- 耗时、用量与错误尝试可审计（任务 2）：RUN_TOOL 单次耗时；RUN_MODEL 版本/第几次尝试/结果码/耗时；终态回写 budget.used（steps/activeDurationMs/modelAttempts/toolCalls）；Token/成本无供应商 usage 时为 null、usageStatus=UNKNOWN。
- 验收与旧回归（任务 3）：`--suite=investigation` HTTP 套件（多轮补证+幂等+取消+审计）PASS；全量 329/0/0/1；CLI 与前端不退化。

## Task Commits

1. **Task 1: 贯通幂等、容量、取消与总超时** - `11b749c` (feat)
2. **Task 2: 记录运行耗时、用量与错误尝试** - `4be1226` (feat)
3. **Task 3: 验收设备+补证+故障链与旧回归** - `6be9be0` (test；含脚本套件与回归修正)

**Plan metadata:** 见最终 docs 提交（本文件 + STATE/ROADMAP/REQUIREMENTS）。

## Files Created/Modified

- `src/main/resources/db/migration/V13__run_lifecycle.sql` - cancel_requested_at/started_at/finished_at/active_duration_ms
- `src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java` - 取消/时限/用量方法与 budget 回写；RunRecord/RunView/ToolEvent/ModelEvent 扩展
- `src/main/java/io/devicetruth/application/agent/GeneralAgentService.java` - cancel()、协作式中止、迟到响应防御、RUN_MODEL 白名单事件
- `src/main/java/io/devicetruth/application/agent/RunRecoveryService.java` - 遗留 QUEUED 超时不再调度，明确 FAILED/TOTAL_TIMEOUT
- `src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java` - AgentStatus.CANCELLED、abortCode 中止重载、工具/模型耗时
- `src/main/java/io/devicetruth/core/reasoning/Reasoner.java` / `src/main/java/io/devicetruth/adapter/StructuredAgentReasoner.java` - Attempt 增加可空 durationMs 并实测
- `src/main/java/io/devicetruth/api/AgentConversationController.java` - POST /api/runs/{id}/cancel、GET /api/runs/{id}/model-events
- `src/main/java/io/devicetruth/server/ProductConfiguration.java` / `application-product.properties` - max-active-duration-ms 配置（默认 120000）
- `frontend/src/api.ts` - RunView 增加可选 activeDurationMs/usageStatus
- `scripts/verify_general_agent.py` - investigation 套件（scripted/确定性 embedding 可跑）
- `src/test/java/io/devicetruth/application/AgentLifecycleTest.java`（12 项）

## Decisions Made

- 总时限口径定为“累计执行时长（不含等待用户时间）”：与 budget.limits.maxActiveDurationMs 既有合同一致，恢复/重调度不清零；若未来需要墙钟总时限可另加列，不与本口径混用。
- RUNNING 取消不打断正在进行的模型调用（协作式，每步之间检查）：强制中断模型线程会泄漏供应商连接且无法落审计；卡死模型由租约过期+恢复上限兜底（03-02）。
- 计划字面 AgentRunController 实际类名为 AgentConversationController，按 01-01 既有装配实际调整。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 两处 Flyway 成功迁移计数断言 12→13**
- **Found during:** 任务 3 全量回归（V13 落地后）
- **Issue:** `KnowledgeUpgradeMigrationTest`/`PersistentConversationTest` 硬编码“成功迁移=12”
- **Fix:** 更新为 13 并注明迁移范围
- **Files modified:** 上述两个测试文件
- **Verification:** 两个套件重新通过（全量 329/0/0/1）
- **Committed in:** `6be9be0`（任务 3 提交）

### 计划偏差（非自动修复）

**a. 计划任务书 frontmatter 标注“待审/未授权”，本轮以 GSD dispatch 明确授权执行**（STATE 已处于 executing，current_plan=3）。
**b. 任务 2 的终态预算回写 SQL 随任务 1 一起落在 repository/V13**（文件重叠，一次签名调整）；行为验收仍按任务 2 的 verify/acceptance 执行并记录。
**c. 总时限实现复用行内 `budget.limits.maxActiveDurationMs`**，未新增“总 deadline”独立列；契约（不含等待、恢复不清零）写入 application-product.properties 注释与 README。
**d. 真实模型/真实 embedding 未验证**（D6）：环境缺 key，investigation 套件用 scripted+确定性 embedding 验收产品语义，真实供应商路径保持 NOT_RUN。

**Total deviations:** 1 自动修复（Rule 3）+ 4 项计划口径调整。
**Impact on plan:** 无范围蔓延；幂等/取消/预算/观测四项交付齐全，must-haves 三真相均达成并有测试证据。

## Issues Encountered

- 本机 Maven 增量编译多次出现“源已改但 test-compile 用旧 class”的假象（空 RunView.class、测试类内嵌编译错误），通过删除对应 target class 并强制重编译确认；最终全量 package 干净通过。
- 工作树存在并行其它计划的未提交改动（04-01 skill 包、AGENTS.md 增补、datasets/v1 等）：全部排除在本计划提交之外，未提交、未修改。

## Verification（实际执行）

- `mvn -o -q package`（真实 PG）：**329 tests，0 failures/0 errors/1 skipped**（AgentLifecycleTest 12/12、RunRecoveryTest 9/9 无回归）。
- `python scripts/verify_cli.py`：**全 PASS**（A/B RESOLVED、C CONFLICT、D NEED_EVIDENCE；qwen/openai 缺 key FAILED 不降级；私有文件连接本机替身；报告持久化）。
- `python scripts/verify_general_agent.py --suite=investigation --embedding=deterministic --reasoner=scripted`：**PASS**（多轮同 run 补答闭环、引用可回溯、工具事件 2 条带耗时、activeDurationMs>0、usageStatus=UNKNOWN；幂等 409；取消 CANCELLED+补答 409+再取消 409）。
- `cd frontend && npm run test -- --run`：**18/18**；`npm run build`：vue-tsc + Vite 通过。
- 未验证（NOT_RUN）：真实模型（qwen/openai）与真实 embedding 链路、浏览器人工演示、PDF/OCR（D6）。

## Self-Check: PASSED

- 关键文件存在：V13 迁移、AgentLifecycleTest、03-03-SUMMARY.md 均 FOUND（写入前逐一验证）。
- 任务提交可达：`git merge-base --is-ancestor` 11b749c、4be1226、6be9be0 均为 HEAD 祖先。
- 计划级 verification 全部实际执行：package 329/0/0/1、verify_cli PASS、investigation PASS、前端 18/18+build 通过。
- 提交数实测（052e3c1..HEAD 生产提交）：3（与本文件记录的 3 个任务提交一致；docs 提交随后）。

## User Setup Required

None - 新配置键均有默认值（`devicetruth.agent.max-active-duration-ms=120000`）；无外部服务变更。

## Next Phase Readiness

- 04-01（标准 Skill 接入唯一 Runtime）可直接复用：取消/终态语义、稳定错误码、RUN_MODEL/RUN_TOOL 审计事件与 `generate_diagnosis_report` 白名单聚合。
- 05-01/05-02 的实时进度与评测可在 RUN_TOOL/RUN_MODEL 事件之上做 SSE 与效率指标；E2E-11 故障链自动化证据已就位。
- Phase 03 三个计划全部执行完毕，待独立 Review 与阶段验收（含 D6 真实供应商项）。

---
*Phase: 03-durable-investigation*
*Completed: 2026-11-13*
