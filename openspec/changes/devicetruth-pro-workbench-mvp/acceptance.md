# Acceptance — devicetruth-pro-workbench-mvp

本文件按实际执行记录验收；未执行项如实记未验证，不用确认代替通过。模拟数据、协议替身、真实 PG、外部真实模型、真实 embedding 分列表述。

## 环境与数据性质

- JDK 21.0.13 / Maven 3.9.9；真实测试 PG 16.15 + pgvector 0.8.7（本机 127.0.0.1:54329，每轮随机建库/自动清理）；浏览器验收用真实 Chrome（Playwright 驱动打包前端 + 本地代理）。
- 真模：DeepSeek（OPENAI_BASE_URL/OPENAI_MODEL，`.env`）；真实 embedding：官方 DashScope `text-embedding-v4`（1024 维，密钥在 `devicetruth.local.properties`）。演示知识库 = 项目自制《充电系统检修手册（演示资料）》，标注 SIMULATED。
- 已知环境问题：`.env` 的 `DASHSCOPE_API_KEY` 为旧失效密钥（服务器读本地私有文件的新密钥，不受影响）；`--reasoner=qwen` 路径在同步 `.env` 前不可用。

## 记录（按批次）

### 第 5 批（5.1/5.2 深入调查与补答往返）— 2026-11-17

- 前端单测 `npm run test -- --run`：60/60 通过（新增 contextRefs 请求体、无 replyTo、去重/上限/省略、深入调查正文口径等 9 项）；`npm run build`（vue-tsc + Vite）通过。
- 后端定向 `mvn -q '-Dtest=ResourceAuthorizationTest,AgentLifecycleTest,AgentStreamTest' test`（真实测试 PG）：38 项，失败 0/错误 0/跳过 0；其中新增正向用例验证继承引用在新 Run 以新引用 ID 重新登记、旧引用不变、跨身份 404。
- D4 真模浏览器验收 `scripts/verify_investigation_roundtrip.py`（两轮）：
  - 确定性 embedding 轮：run1 3 判断 1 引用；选择判断零提交；深入调查新 runId 且继承来源、引用 ID 全新；旧轮回看一致；现场补充作为用户报告开第 3 Run；**真模实际触发 ASK_USER**，三元组补答恢复同一 runId、Run 总数不变、补答后 COMPLETED。
  - 真实 embedding 轮：同流程 PASS（run1 5 判断 5 引用，语义召回明显更有效）；本轮 ASK_USER 未触发（以上一轮真模触发证据为准）。
  - result.json：`outputs/validation/investigation-roundtrip-bd6b5331/`、`-fca4680d/`。
- 恢复脚本回归 `verify_browser_recovery.py`：PASS（重构抽出 `serve_frontend` 并修复代理绑定旧 server 缺陷后）。

### 第 6 批（6.1/6.2 QUEUED 周期补派，P1-04 修复）— 2026-11-17

- 改动：`AgentRunRepository.queuedPage`（有界扫描）、`RunRecoveryService.reconcileQueued`（周期补派，单 run 失败不中断）、`GeneralAgentService`（`pendingDispatch` 进程内去重、周期任务合并并整体捕获异常、`recoveryScanLimit` 默认 50 可配）、`ProductConfiguration` 注入 `devicetruth.agent.recovery-scan-limit`。
- `mvn -q '-Dtest=RunRecoveryTest' test`（真实测试 PG）：22 项，失败 0/错误 0/跳过 2（2 项真模用例离线跳过）；新增 8 项回归：周期补派接手滞留 QUEUED（不重启）、临时调度失败保持 QUEUED 且下周期补派、真实 PG 故障注入（dt_agent_run 改名一个周期）后周期继续、worker 关闭不调度、双进程抢占仅一次有效领取（attempts=1）、有界扫描（上限 2，三轮排空）、预算不重置（steps/activeDuration 单调）。
- `mvn -q '-Dtest=RunRecoveryTest,AgentLifecycleTest,AgentStreamTest' test`（真实测试 PG）：合计 51 项，失败 0/错误 0/跳过 2（真模用例离线跳过）；重复补派仅一次有效领取（attempts=1）、WAITING/终态不被误执行、过期 RUNNING 恢复原回归继续通过。

## 可靠性说明（QUEUED 恢复）

- 启动全量对账合同不变：`reconcile` 仍用无界 `queued()`；周期补派走有界 `queuedPage`（默认 50，`devicetruth.agent.recovery-scan-limit` 可配）。
- 执行资格最终由 `markRunning` 的 version CAS + 租约保证；进程内 `pendingDispatch` 只防止重复入队，不替代 CAS。
- 周期任务整体捕获异常：`scheduleWithFixedDelay` 遇未捕获异常会永久取消后续执行，已在任务入口兜底；扫描与单 run 调度两层各自再捕获。
- 不重派 WAITING/终态（SQL 只扫 `status='QUEUED'`），不重置预算（补派路径不写 budget； TOTAL_TIMEOUT 判定沿用启动对账规则）。

## 未验证项

- `--reasoner=qwen`（DashScope 原生路径）在线验收：`.env` 旧密钥待同步后重验。
- 7.1 双域（工业 SN-0245、汽车 EV-DEMO-004）真模+真实授权资料全流程：待执行。
- 8.1/8.2（P1 可选）：未开始。
