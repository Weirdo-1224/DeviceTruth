---
phase: 03-durable-investigation
plan: "02"
subsystem: agent-runtime
tags: [postgres, flyway, lease, crash-recovery, checkpoint, vue, vitest, spring-boot]

requires:
  - phase: 03-01
    provides: [资产注册表 V8-V11、AssetTools、双身份授权测试基线]
  - phase: 02-03
    provides: [Markdown 导入、混合检索、补答附件上下文]
provides:
  - V12 执行租约迁移（dt_agent_run.lease_owner/lease_expires_at/lease_heartbeat_at/attempts）
  - RunRecoveryService 启动对账（QUEUED 重调度、过期租约 RUNNING 回收、超限 FAILED）
  - GeneralAgentService 租约领取 + 周期心跳 + 恢复集成
  - generate_diagnosis_report 只读报告工具（白名单聚合，注册进产品网关）
  - Vue 历史会话列表/重开找回/待答与进行中恢复轮询/可选设备选择
  - RunRecoveryTest（9 项）与 AgentReportToolTest（4 项）真实 PG 回归
affects: [03-03, 04-01, 05-01, verify-work]

actuals:
  tokens: 29641
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "执行租约：RUNNING 领取租约并心跳续租，崩溃判活靠租约过期而非进程探活"
    - "恢复 CAS：version + 租约过期双重条件更新，双进程并发对账只成功一次"
    - "有界恢复：attempts 累计领取次数，超限明确 FAILED/RECOVERY_ATTEMPT_LIMIT，禁止永久 RUNNING"
    - "报告工具白名单：只聚合持久层公开视图，推理输入/密钥永不进入工具输出"

key-files:
  created:
    - src/main/resources/db/migration/V12__run_checkpoints.sql
    - src/main/java/io/devicetruth/application/agent/RunRecoveryService.java
    - src/main/java/io/devicetruth/application/agent/AgentReportTools.java
    - src/test/java/io/devicetruth/application/RunRecoveryTest.java
    - src/test/java/io/devicetruth/application/AgentReportToolTest.java
  modified:
    - src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java
    - src/main/java/io/devicetruth/application/agent/GeneralAgentService.java
    - src/main/java/io/devicetruth/server/ServerConfiguration.java
    - src/main/java/io/devicetruth/server/ProductConfiguration.java
    - src/main/resources/application-product.properties
    - frontend/src/api.ts
    - frontend/src/App.vue
    - frontend/src/api.test.ts
    - README.md

key-decisions:
  - "迁移命名为 V12 而非计划的 V3_1：Flyway 按版本号排序，V3_1(=3.1) 会插到 V4 之前破坏顺序"
  - "租约由持有者 worker 周期性续租（lease_seconds/3 间隔），崩溃判活不依赖进程探活，容器/JVM 僵死同样可回收"
  - "恢复重调度以 attempts（领取次数）为界，超限 FAILED/RECOVERY_ATTEMPT_LIMIT；只读工具重试有界且不伪称外部 exactly-once"
  - "报告工具零参数、只读、按 ToolContext 归属取数，跨身份 run 统一 REPORT_UNAVAILABLE，不区分存在性"

patterns-established:
  - "检查点在工具边界：引用/工具事件各自事务落库，崩溃恢复重建后以 (run_id,chunk_id) 幂等去重"
  - "前端恢复不依赖线程：localStorage 记忆 + GET /api/conversations 历史列表 + 刷新后对 QUEUED/RUNNING 续轮询"

requirements-completed: ["AGENT-06", "AGENT-07", "STORE-04", "TOOL-04", "WEB-02"]

coverage:
  - id: D1
    description: "跨进程稳定检查点：用户回复等待问题后同一调查继续，引用与累计预算保留；新进程从 PG 重建上下文"
    requirement: "AGENT-06"
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#waitingRunSurvivesProcessRestartAndContinuesOnSameRun"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#crashedRunningRunIsReclaimedByNewProcessAndResumesFromCheckpoint"
        status: pass
      - kind: other
        ref: "node /tmp/demo-03-02.mjs（真实HTTP两轮问答+杀进程重启，run2 恢复 COMPLETED；输出见执行记录）"
        status: pass
    human_judgment: false
  - id: D2
    description: "崩溃任务对账与有界重调度：QUEUED 全量接手、过期租约 RUNNING 回收、超限明确 FAILED、无幽灵队列/重复终态"
    requirement: "STORE-04"
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#crashedRunBeyondRecoveryLimitFailsExplicitlyInsteadOfStayingRunning"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#concurrentReconciliationReclaimsCrashedRunOnlyOnce"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#startupReconciliationLeavesNoOrphanedQueuedRuns"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#crashAfterToolCheckpointResumesWithoutDuplicatingReferencesOrMessages"
        status: pass
    human_judgment: false
  - id: D3
    description: "并发补答可靠性：同一待答问题只被消费一次，另一补答 409；旧版本/跨身份/伪造引用负例在重启后仍成立"
    requirement: "AGENT-07"
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#concurrentRepliesAdvanceTheQuestionOnlyOnce"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#staleVersionAndCrossIdentityRepliesAreRejectedAfterRestart"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/RunRecoveryTest.java#fabricatedCitationAfterRestartIsStillRejected"
        status: pass
    human_judgment: false
  - id: D4
    description: "generate_diagnosis_report 只读报告工具：目录可发现、聚合 run 白名单事实、跨身份拒绝、模型可调用"
    requirement: "TOOL-04"
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/application/AgentReportToolTest.java（4 项）"
        status: pass
    human_judgment: false
  - id: D5
    description: "Vue 历史恢复：刷新/重开可找回历史会话、待答问题与结果，进行中 run 恢复轮询，可选设备切换"
    requirement: "WEB-02"
    verification:
      - kind: unit
        ref: "frontend/src/api.test.ts（18 项，含 listConversations/listAssets/selectedAssetId 合同）"
        status: pass
      - kind: other
        ref: "cd frontend && npm run build（vue-tsc --noEmit + Vite 通过）"
        status: pass
    human_judgment: false
  - id: D6
    description: "浏览器人工演示：新版§10.1最小纵向闭环（两轮问答/刷新/重启浏览器操作体验）"
    verification: []
    human_judgment: true
    rationale: "需要真实浏览器交互体验评估（布局、流程顺畅度）；自动化已覆盖合同、构建与后端闭环，浏览器端仍为计划内人工验收项"

duration: 75min
completed: 2026-11-13
status: complete
plan_head_before: 873004445e6ff2eae3b13f6f5fbf42af69f6784e
plan_head_after: cc0bee6bba62e5d55d67e4e5dbb9c1007786e5de
---

# Phase 03 Plan 02: 跨进程检查点与崩溃恢复 Summary

**执行租约 + 启动对账让 RUNNING 崩溃遗留可按 PG 检查点安全回收，补答协议在同 run 上跨进程续跑；generate_diagnosis_report 补齐只读报告工具；Vue 历史会话刷新/重开可恢复。**

## Performance

- **Duration:** ~75 min
- **Started:** 2026-11-13T13:40:00Z（Asia/Shanghai 21:40）
- **Completed:** 2026-11-13T15:05:00Z
- **Tasks:** 3/3
- **Files modified:** 17（代码 13 + 文档 4）

## Accomplishments

- 补证协议强化为跨进程稳定检查点：V12 租约迁移、worker 领取/续租、新进程按“租约过期 + version CAS”回收崩溃遗留并续跑（引用、累计预算、消息序列不丢）。
- 崩溃任务对账与有界重调度：QUEUED 全量接手、RUNNING 超限明确 FAILED/RECOVERY_ATTEMPT_LIMIT、双进程并发对账不重复、无孤儿队列。
- 通用调查报告工具 generate_diagnosis_report 注册进产品网关，只聚合持久白名单事实；Vue 历史会话列表 + 重开找回 + 待答/进行中恢复 + 可选设备选择。

## Task Commits

1. **Task 1: 强化补证协议为跨进程稳定检查点** - `cf9fd49` (feat)
2. **Task 2: 崩溃任务对账与有界重调度** - `a4ed5c9` (test；主代码随任务1交付)
3. **Task 3: Vue历史恢复与通用报告工具** - `cc0bee6` (feat)

**Plan metadata:** 见最终 docs 提交（本文件 + STATE/ROADMAP/REQUIREMENTS）。

## Files Created/Modified

- `src/main/resources/db/migration/V12__run_checkpoints.sql` - dt_agent_run 租约/尝试次数字段
- `src/main/java/io/devicetruth/application/agent/RunRecoveryService.java` - 启动对账：QUEUED 重调度、过期 RUNNING 回收/失败
- `src/main/java/io/devicetruth/application/agent/GeneralAgentService.java` - 租约领取、心跳调度、恢复集成、doRun 拆分
- `src/main/java/io/devicetruth/application/agent/AgentReportTools.java` - generate_diagnosis_report 白名单聚合
- `src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java` - markRunning 租约化、heartbeat、runningWithExpiredLease、requeue/failRecovered
- `src/main/java/io/devicetruth/server/ProductConfiguration.java` / `ServerConfiguration.java` - 新配置键与报告工具 Bean/网关注册
- `frontend/src/api.ts` / `App.vue` / `api.test.ts` - 历史恢复、设备选择、合同测试
- `src/test/java/io/devicetruth/application/RunRecoveryTest.java`（9 项）、`AgentReportToolTest.java`（4 项）

## Decisions Made

- 迁移命名 V12（非计划字面的 V3_1）：Flyway 版本排序约束，V3_1=3.1 会错误插入 V4 之前。
- 恢复有界以“领取次数 attempts”为界，而非固定扫描窗口：超限 FAILED 是唯一不会留下永久 RUNNING 的终态。
- 报告工具不做新 API 端点：模型经注册网关调用，人经既有 `/api/runs/{id}/tool-events` 与引用端点读回，攻击面最小。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] PersistentConversationTest/KnowledgeUpgradeMigrationTest 的 Flyway 计数断言**
- **Found during:** Task 1（V12 迁移落地后）
- **Issue:** 两处测试硬编码“成功迁移 = 11”，V12 使其变为 12
- **Fix:** 断言更新为 12 并注明迁移范围
- **Files modified:** 上述两个测试文件
- **Verification:** 两个套件重新通过
- **Committed in:** `cf9fd49`（任务 1 提交）

### 计划偏差（非自动修复）

**a. 迁移文件名 V3_1 → V12**：Flyway 版本号语义强制；已在 key-decisions 记录。
**b. 任务 2 的主代码随任务 1 提交**：RunRecoveryService（含恢复上限 FAILED 语义）在任务 1 已实现，任务 2 提交为故障注入/并发对账/无孤儿队列三组测试。行为覆盖与计划一致。
**c. 报告工具测试独立为 AgentReportToolTest**：计划任务 3 把所有测试列入 RunRecoveryTest.java；报告工具与恢复无关，按 Runtime 选型调整路径。
**d. 任务 3 的浏览器两轮问答/刷新/重启验收**：自动化仅覆盖到前端合同 + 构建 + 后端真实 HTTP 链路；浏览器交互体验为 D6 人工验收项（end-of-phase UAT）。

**Total deviations:** 1 自动修复（Rule 3）+ 4 项计划口径调整。
**Impact on plan:** 无范围蔓延；检查点/对账/报告/历史恢复四项交付齐全。

## Issues Encountered

- 真实 PG 测试凭据（DEVICETRUTH_TEST_PG_*）未注入会话环境：按 README 约定在 interview-postgres 实例内新建专用角色 devicetruth_test（LOGIN/CREATEDB/SUPERUSER，随机口令），全部验证结束后已删除该角色，未改动既有 postgres 角色与库。
- ScriptedAgentReasoner 的追问策略是“仅未追问过才追问”：初期测试误期补答后出 v2 问题，修正断言为“补答后作答、预算累计”，属测试理解修正而非产品缺陷。
- 工作树存在并行其它计划的未提交改动（04-01 skill 包、AGENTS.md 增补、datasets/v1 等）：全部排除在本计划提交之外，未提交、未修改。

## Verification（实际执行）

- `mvn test`（真实 PG，任务 3 后全量）：**317 项，0 失败/0 错误/1 跳过**，含 RunRecoveryTest 9 项、AgentReportToolTest 4 项。
- `cd frontend && npm run test -- --run`：**18/18 通过**；`npm run build`：vue-tsc + Vite 通过。
- 真实 HTTP 演示（独立库 devicetruth_demo_03_02，已清理）：导入手册→自由问题(带 CAR-B)→WAITING v1→同 run 补答→COMPLETED(1 引用)→历史 4 消息 1 run；提交第二个问题后杀进程→重启→恢复 COMPLETED。
- `mvn -q package`：**PACKAGE_OK**（含全量测试再次通过）。
- `python scripts/verify_cli.py`：**全部 PASS**（A/B RESOLVED、C CONFLICT、D NEED_EVIDENCE；qwen/openai 缺 key 失败不降级；私有文件连接本机替身；报告持久化）。
- 未验证（NOT_RUN）：真实模型（qwen/openai）与真实 embedding 链路、浏览器人工演示（D6）、PDF/OCR。模拟数据全链路已验证。

## Self-Check: PASSED

- 关键文件存在：V12 迁移、RunRecoveryService、AgentReportTools、RunRecoveryTest、AgentReportToolTest、03-02-SUMMARY.md 均 FOUND（写入前逐一代码与测试验证过）。
- 任务提交可达：cf9fd49（任务1）、a4ed5c9（任务2）、cc0bee6（任务3）与 docs 提交均为 HEAD 祖先。
- 计划级 verification 全部实际执行：mvn test 317/0/0/1、前端 18/18、build 通过、package 通过、verify_cli 全 PASS、真实 HTTP 演示通过。
- 提交数实测（8730044..HEAD）：4（3 任务 + 1 文档）。

## User Setup Required

None - 无外部服务配置变更；新配置键均有默认值（lease-seconds=60、max-recovery-attempts=3）。

## Next Phase Readiness

- 03-03（幂等、取消、总预算与错误观测）可直接复用：attempts/租约列、RunRecoveryService.reconcile 报告、RECOVERY_ATTEMPT_LIMIT 终态。
- 报告工具为 05-02 的报告/轨迹 UI 提供后端聚合；E2E-07-restart 自动化证据已就位，浏览器 UAT 留待 phase 验收。

---
*Phase: 03-durable-investigation*
*Completed: 2026-11-13*
