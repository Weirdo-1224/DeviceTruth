---
phase: 05-workbench-quality
plan: "01"
status: complete
completed: 2026-11-13
requirements-completed: [TRUST-03, WEB-03]
coverage:
  - id: P5-SSE-STREAM
    description: "持久事件驱动的实时 SSE：历史补读→订阅→边界补读，断线按 Last-Event-ID 游标续传，终态 RUN_TERMINAL 收尾，订阅有界且断开清理"
    requirement: WEB-03
    verification:
      - kind: integration
        ref: "AgentStreamTest（真实临时 PG + 产品装配 + JDK HttpClient SSE 客户端）7/7：连接前历史全量回读、历史/实时交界不丢不重、Last-Event-ID 断线补齐按序各一次、非法游标 400、跨身份 404、COMPLETED/FAILED/CANCELLED 三态 RUN_TERMINAL 收尾、并发订阅有界与断开后清理"
        status: pass
      - kind: e2e
        ref: "scripts/verify_sse_browser.py（打包产品服务 + 真实 PG + 系统 Chrome）：A 实时时间线、B 代理断连后 EventSource 携带 Last-Event-ID=断点重连且页面补齐各一次、C 会话切换隔离 + 终态历史回读"
        status: pass
    human_judgment: false
  - id: P5-EVENT-HISTORY
    description: "GET /api/runs/{id}/events?after= 历史回读：seq 升序、游标语义、单次有界分批（500/批、5000/次），与 SSE 同一白名单映射"
    requirement: WEB-03
    verification:
      - kind: integration
        ref: "AgentStreamTest.historyCursorSemanticsInvalidCursorAndCrossIdentityFailures 及 historyBeforeConnectIsFullyReplayedThenTerminalEndsStream"
        status: pass
    human_judgment: false
  - id: P5-SAFE-PUBLIC-EVENTS
    description: "历史与实时共用 SafeAgentEventViews 白名单映射：data 按类型逐字段过滤，Prompt/密钥/模型原始输出/堆栈永不透出；注入敏感标记不回显"
    requirement: TRUST-03
    verification:
      - kind: integration
        ref: "AgentStreamTest.publicEventsStayWithinWhitelistAndContainNoSecrets（事件 JSON 断言无 prompt/key/secret/stack/trace 敏感内容且字段不越白名单）"
        status: pass
    human_judgment: false
  - id: P5-VUE-TIMELINE
    description: "Vue 运行时间线：历史加载 + SSE 增量、按 seq 幂等去重、断线重连补齐、切换会话关闭旧订阅且迟到事件不更新当前页面、选中历史终态 run 回读其完整历史"
    requirement: WEB-03
    verification:
      - kind: unit
        ref: "frontend Vitest 28/28（含新增 10：历史回读、按类型派发保序、RUN_TERMINAL 关闭、断开上报、断线重连补齐各一次、终态保留、切换 run 忽略迟到事件、重复 follow 去重）"
        status: pass
      - kind: e2e
        ref: "scripts/verify_sse_browser.py 场景 A/C（真实 Chrome 端到端）"
        status: pass
    human_judgment: false
  - id: P5-REGRESSION
    description: "SSE 接入对既有行为无回归：全量离线测试、前端构建、打包、旧 CLI A–D 场景"
    requirement: WEB-03
    verification:
      - kind: regression
        ref: "mvn -o test 350/0/0/1；frontend npm run test 28/28 + npm run build 通过；python scripts/verify_cli.py 全通过（A/B/C/D + 缺密钥 FAILED 不降级 + 私有配置）"
        status: pass
    human_judgment: false
---

# 05-01 用 SSE 展示安全、可续传的运行事件

> 2026-11-13 实施并验收完成。主代理统一契约（[05-01-CONTRACT](05-01-CONTRACT.md)）后派工三个子代理并行实施，主代理整合、修复并终验。

## 交付内容

- **后端（子代理 A）**
  - `AgentConversationController` 新增 `GET /api/runs/{id}/events`（`?after=` 游标，JSON 数组）与 `GET /api/runs/{id}/stream`（SSE，`Last-Event-ID` 等价游标）。计划文件中的 `AgentRunController.java` 不存在，以实际控制器为准（偏差已记录）。
  - `SafeAgentEventViews`：dt_run_event 行 → `{seq,type,recordedAt,data}` 公开事件的唯一白名单映射，历史与 SSE 共用，`safe_payload` 绝不直透。
  - `RunEventStreamService`：先读历史→订阅→补读订阅点之后全部 seq；内存通知（先提交后发布）+ 15s 心跳兜底补读；至少一次、按 seq 升序；终态判定后再补一轮才发 `RUN_TERMINAL`；单 run 订阅默认 16（`devicetruth.stream.max-subscriptions`），超出 409 `STREAM_CAPACITY`；断开清理订阅与计数。
  - `AgentRunRepository.eventsAfter/latestSeq`（500 分批、5000/次有界）；`GeneralAgentService` 在事件事务提交后调通知钩子，并新增 `events(runId, afterSeq)`。
- **前端（子代理 B + 主代理修复）**
  - `api.ts`：`RunEvent`、`readRunEvents`、`openRunEventStream`（原生 EventSource 包装、可注入替身）。
  - `timeline.ts`：框架无关的时间线控制器（maxSeq 幂等去重、generation 丢弃迟到事件、切换关闭旧流）。
  - `components/RunTimeline.vue`：按类型标签展示排队/检索/模型/答案/失败/取消/迟到响应。
  - `App.vue`：watch 接线（**主代理修复**：选中历史里的终态 run 必须回读其完整历史；流提前断开导致视图不完整时切回自动重跟补齐）。
- **验收（子代理 C + 主代理）**
  - `AgentStreamTest`（7 项，真实临时 PG + 产品装配）。
  - `scripts/verify_sse_browser.py`：系统 Chrome + 打包产品服务 + 真实 PG 的三场景端到端。

## 实际命令与结果

| 命令 | 结果 |
| --- | --- |
| `mvn -o '-Dtest=AgentStreamTest' test` | Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS（首轮 3 失败为子代理中断期间的陈旧编译产物，当前源码重跑全通过；失败未掩盖，过程记录在案） |
| `mvn -o test`（全量） | Tests run: 350, Failures: 0, Errors: 0, Skipped: 1 — BUILD SUCCESS（343 既有 + 7 新增；跳过 1 为历史既有项） |
| `cd frontend && npm run test -- --run` | 28/28 通过（新增 10 项） |
| `cd frontend && npm run build` | vue-tsc + Vite 构建通过 |
| `mvn -q -o -DskipTests package` | 成功（target/devicetruth-core-0.1.0.jar） |
| `python scripts/verify_cli.py` | 全通过：A/B/C/D 场景、缺密钥 FAILED 不降级、私有配置读取 |
| `python scripts/verify_sse_browser.py` | PASS：A 实时时间线（1..7）；B 断点 seq=4 断开、重连携带 `Last-Event-ID: 4`（代理侧取证）、补齐后 1..7 各一次；C 旧 run 推进时新页面冻结、切换后旧 run 零 stream 请求、切回终态会话历史回读 1..7；无 page error |

浏览器证据目录：`outputs/validation/sse-browser-7e894c22cc0a/`（终验 PASS 一轮；result.json、network.jsonl、proxy-stream.jsonl、timeline.json、screenshot-*.png、console.log）。调试过程中的失败轮次（`sse-browser-72e857182496`、`72442772b620`、`f826b39e35fe`、`70734eae8061`）保留在同级目录供追溯。

## 关键实施事实（真实链路）

一条事件的生命周期：worker 主循环执行检索/模型 → `GeneralAgentService.persist` 在同一事务里 `appendEvent` 写 `dt_run_event`（`seq=MAX(seq)+1`，白名单 `safe_payload`）并迁移 run 状态 → 事务提交后 `notifyEvents` 内存唤醒 → `RunEventStreamService` 从 `lastSent` 游标 `eventsAfter` 补读并按 `id: seq / event: type / data: {...}` 帧写入 SSE 响应 → 浏览器 EventSource 收到后页面按 `seq > maxSeq` 幂等追加。浏览器断线后原生 EventSource 自动带 `Last-Event-ID` 重连，服务端按游标补历史，页面去重——“读到第 4 条断开、服务写入 5–7、重连依序补齐各一次”已由 AgentStreamTest（HTTP 级两连接模拟）与浏览器脚本（真实 Chrome）双重验证。

## 偏差与修复记录

1. 计划写 `AgentRunController.java` / `SafeAgentEventViews` / `RunEventStreamService`：实际控制器为 `AgentConversationController`，后两个类按计划新增。
2. 子代理 C 首轮中断（其汇报输出被截断），主代理接续完成集成测试运行、浏览器脚本与终验；C 后续自动恢复并改进了脚本（代理流式转发、断连装置、代理侧 Last-Event-ID 取证），主代理与之合并。
3. 浏览器断连场景两处时序修复（主代理）：a) 代理缓冲 SSE 会破坏实时性，须逐块转发（子代理 C 修订版已修）；b) 断连必须等浏览器真正收到过带 id 的事件再断，否则 EventSource 无游标可续（已由两路子代理与主代理独立踩到并修复）。
4. 主代理修复 `App.vue` 两处接线缺口：选中历史终态 run 不回读历史；视图不完整（流提前断、未收到 RUN_TERMINAL）时切回不自动重跟。均已由浏览器终验覆盖。
5. 取证注意：playwright 的 `request.headers` 不暴露 EventSource 内部附带的 `Last-Event-ID`，浏览器验收以代理侧记录为准（裸 Chrome 行为对照实验证实浏览器确实会携带）。

## 未验证项

- 真实供应商模型（qwen/openai）+ SSE 的端到端联调未执行：本轮 scripted 推理器贯穿；SSE 传输层与模型无关，但真实模型的长耗时流（含心跳期间的持续事件）未实测。
- 多端并发大规模订阅（>16/run 的 409 路径有单测覆盖，压测未做）。
- SSE 端点未加独立认证（沿用 product profile 的本地固定身份模型，真实认证为 05-03 范围）。
- `RunRecoveryService` 启动对账产生的事件不触发内存通知，仅靠 15s 兜底补读（子代理 A 报备、主代理裁决保留）。

## 复验轮（2026-11-13 二轮，用户审查提出六项问题后）

> 修复契约见 [05-01-CONTRACT](05-01-CONTRACT.md) §8。子代理 A（后端四修）中途断线、主代理接手审查与验证；子代理 B（前端两修）33/33；子代理 C（验收）在扩展用例时中断，主代理接手并补完其脚本与断言（浏览器 D/E 场景）。

| 审查项 | 修复 | 实测证据 |
| --- | --- | --- |
| P1 实时进度延迟 | `TaskOrchestrator.runAgent` 新增进度回调重载；`GeneralAgentService` 执行中把工具/模型步骤立即以短事务落 `RUN_TOOL/RUN_MODEL`（attempt=当前尝试号），收尾不再重复写；`RUN_WAITING` 事件与 `waitForUser` 同事务；V16 加 `attempt` 列；`requeueRecovered` 删除被回收尝试的步骤事件 | `AgentStreamTest.progressiveToolEventIsVisibleOnHistoryAndLiveStreamBeforeSecondModelCall`：第二次模型调用被 latch 阻塞时，历史端点已见 RUN_TOOL(attempt=1)、新开 SSE 连接收到同一帧；释放后 COMPLETED |
| P1 SSE 占请求线程 | 控制器改 `SseEmitter` + 专用有界线程池；全局容量 503（默认 64）、单连接 TTL 60 分钟、完成/断开/超时统一清理；错误体手写 JSON（绕开 SSE Accept 的内容协商 406→500） | `globalStreamCapacityRejectsThirdConnectionButProductApiStaysAvailable`：全局=2 时第三条 503，同时补答/取消/历史仍可用 |
| P2 订阅计数竞态 | 单 `Map<runId, Set<Subscription>>` + 一把锁，容量判定与计数同锁 | `concurrentOpenCloseFullyReclaimsStreamCapacityAcrossRuns` 6 线程搅动 + 打满/409/重开全回收 |
| P2 空值 NPE | `pick` 跳过 null 值字段（公开视图不出现 null） | `nullDurationModelEventSurvivesBothPublicPathsWithKeyOmitted`（历史+SSE 双路径，durationMs 省略） |
| P2 onerror 未绑定 | 包装器 getter/setter 挂到原生 EventSource；连续错误 8 次永久放弃；成功事件重置计数 | 前端 36/36（新增转发/阈值/重置/clear 用例）；浏览器场景 E |
| P2 无 run 会话残留 | `timeline.clear()`；App.vue run==null 走 clear | 浏览器场景 D（旧时间线立即消失、迟到事件不填回） |

复验轮实测：`AgentStreamTest` **12/12**（新增 5 项）、`mvn -o test` **365/0/0/1**（350 + 12 项 SSE + 安全/知识新增）、`AnswerGuardTest` 25/25、`GeneralAgentFlowTest` 19/19、`KnowledgeImportTest` 8/8、前端 36/36 + build、打包与 `verify_cli` 通过；浏览器五场景（A 实时含 RUN_WAITING、B 断点 5 重连携带 `Last-Event-ID: 5` 补齐 1..8、C 切换隔离、D 无 run 清理、E 永久失败后刷新历史回读）全部 PASS，证据 `outputs/validation/sse-browser-*/`。

复验轮新增偏差与限制：① 容量回收时延是心跳语义（最长约两个 15s 周期），用例按探针排水+退避设计，未做更快回收；② 浏览器场景 E 的 give-up 触发点实测 2 次即被放弃（浏览器把永久失败提前为 CLOSED），与 8 次阈值不冲突但记录在案；③ `RunRecoveryService` 启动对账事件仍不触发内存通知（15s 兜底补读覆盖）。
