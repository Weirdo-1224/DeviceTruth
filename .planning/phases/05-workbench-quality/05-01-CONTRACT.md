# 05-01 共享接口契约（主代理定稿，A/B/C 三个子代理统一遵守）

> 本文档由主代理依据当前源码核对后定稿。计划文件里的类名/路径与实际代码有偏差的，**以本文档和实际接线为准**。
> 已核对偏差：① 计划写 `AgentRunController.java`，实际是 `AgentConversationController.java`（`@Profile("product")`，已有 `/api/runs/{id}/tool-events` 等端点）；② 持久事件表为 `dt_run_event(run_id, seq, conversation_id, owner_id, project_id, type, recorded_at, safe_payload)`，主键 `(run_id, seq)`，**seq 是 run 内单调递增序号（bigint，从 1 开始）**；③ 写库时只存白名单 `safe_payload`（`AgentRunRepository.appendEvent`），Prompt/密钥/模型原始输出永不落库；④ 目前持久化的事件类型：RUN_QUEUED、RUN_RECOVERED、RUN_TOOL、RUN_MODEL、RUN_ANSWER、RUN_FAILED、RUN_CANCELLED、RUN_LATE_RESPONSE。

## 1. 端点

| 用途 | 方法与路径 | 说明 |
| --- | --- | --- |
| 历史回读 | `GET /api/runs/{id}/events?after={seq}` | JSON 数组，按 seq 升序。无 `after` 返回全部；`after=N` 返回 seq>N。`after` 非法（非数字或 <0）→ 400 `INVALID_REQUEST`。run 不存在或跨身份 → 404（与现有 `/api/runs/{id}` 一致，错误体同现有 `{code,message,retryable,traceId}`）。 |
| 实时流 | `GET /api/runs/{id}/stream` | SSE，`Accept: text/event-stream`。支持请求头 `Last-Event-ID: {seq}`（等价游标；缺省视为 0=从头补历史）。非法游标 → 400。run 不存在或跨身份 → 404（连接建立前校验，返回 JSON 错误体）。 |

## 2. 公开事件 JSON（历史与 SSE 共用同一映射 `SafeAgentEventViews`）

```json
{"seq": 7, "type": "RUN_TOOL", "recordedAt": "2026-11-13T12:00:00Z", "data": { "toolName": "search_knowledge", "success": true, "...": "..." }}
```

- 字段固定为 `seq`(number)、`type`(string)、`recordedAt`(ISO-8601 UTC)、`data`(object)。camelCase，与现有 API 风格一致。
- `data` 为按类型的**字段白名单**（不能把 safe_payload 整体直透，防未来写入侧漂移）：

| type | 允许字段 |
| --- | --- |
| RUN_QUEUED | status |
| RUN_RECOVERED | attempts |
| RUN_TOOL | toolName, success, errorCode, references, durationMs, retryable |
| RUN_MODEL | model, attempt, outcome, durationMs, retryable, inputTokens, outputTokens, cost |
| RUN_ANSWER | summary, category, claims, suggestions, uncertainties, limitations, references, riskLevel, safetyDisposition, dataNature（即 AgentResult 公开结构，与 `GET /api/runs/{id}` 的 result 一致） |
| RUN_FAILED | code |
| RUN_CANCELLED | phase |
| RUN_LATE_RESPONSE | status, code |
| 其他/未知 | 空对象 `{}`（防御） |

## 3. SSE 线格式

```
id: 7
event: RUN_TOOL
data: {"seq":7,"type":"RUN_TOOL","recordedAt":"...","data":{...}}

```

- SSE `id` = 持久事件 seq（十进制字符串）。
- 心跳：每 15 秒一行 `: heartbeat`。
- 终态：发送完最后一条持久事件后，再发送一条 `event: RUN_TERMINAL`、`data: {"status":"COMPLETED"|"FAILED"|"CANCELLED"}`（无 id），然后服务端正常结束响应体。浏览器 EventSource 收到 `error`/流结束后由客户端决定重连或停止（客户端见 §5）。
- 发送顺序保证：先补历史（seq > 游标，升序），再进入实时订阅；交界竞态用「先读历史、再订阅、再补读订阅点之后的所有 seq（含订阅期间错过的）」处理，单实例内配合内存通知 + 低频兜底轮询，保证**至少一次、按 seq 升序**。
- 资源上限：单 run 同时活跃订阅有界（默认 16，超出 409 `STREAM_CAPACITY`）；`RUN_TERMINAL` 或客户端断开后必须清理订阅与调度任务；订阅期间服务端不持数据库事务/行锁。

## 4. 后端职责（子代理 A）

- `AgentConversationController` 增加两个端点（@Profile("product") 内，复用现有错误处理风格）。
- 新增 `SafeAgentEventViews`：`dt_run_event` 行 → 公开事件（白名单映射，历史与 SSE 共用）。
- 新增 `RunEventStreamService`：历史查询 + 实时订阅 + 心跳 + 终态收尾 + 断开清理；PG 提交先于事通知（单实例内存事件总线即可，不引 Redis/MQ）；兜底周期补读防漏。
- `AgentRunRepository` 增加：`List<PublicEvent> eventsAfter(identity, runId, afterSeq, limit)`（limit 有界，如 500，超出分批）、`latestSeq(...)`；复用 appendEvent 后在事务提交后触发通知的钩子（由 service 层保证先提交后发布）。
- `GeneralAgentService` 增加 `events(runId, afterSeq)` 供历史端点使用（内部 `runs.require` 授权）。
- `ProductConfiguration` 装配新 Bean（@Profile("product")、@DependsOn("productFlyway")）。
- 不做前端、不写测试文件（集成测试由 C 负责）。

## 5. 前端职责（子代理 B）

`api.ts` 新增（保持现有风格、类型冻结、可注入替身便于 Vitest）：

```ts
export interface RunEvent { seq: number; type: string; recordedAt: string; data: Record<string, unknown> }
export function readRunEvents(runId: string, afterSeq?: number): Promise<RunEvent[]>
export interface RunStreamHandle { close(): void }
export interface RunStreamOptions {
  lastSeq: number               // 已收到的最大 seq，初次为 0
  onEvent: (event: RunEvent) => void
  onTerminal?: (status: string) => void
  onError?: (error: unknown) => void
  eventSourceFactory?: (url: string) => EventSourceLike   // 测试注入
}
export function openRunEventStream(runId: string, options: RunStreamOptions): RunStreamHandle
```

- 去重/续传语义：页面维护 `maxSeq`，只追加 `seq > maxSeq` 的事件（按 seq 幂等）；EventSource 原生重连会自动带 `Last-Event-ID`，重连后服务端从历史补齐，页面再按 seq 去重——“页面读到第 12 条断线、服务写入 13/14 条、重连后依序补齐且各显示一次”靠这一机制保证。
- 收到 `RUN_TERMINAL` 或流错误且不再重连时调用 `close()`；`RunTimeline.vue` 负责展示（工具调用成功/失败、模型尝试次数与耗时、追问、答案终态、取消/失败原因码）。
- `App.vue`：当前 run 进入 QUEUED/RUNNING 时打开时间线订阅，WAITING_FOR_USER/COMPLETED/FAILED/CANCELLED（收到 RUN_TERMINAL）后保留时间线展示；**切换会话/任务时先 close() 旧订阅，迟到事件只在事件 seq > maxSeq 且 runId 仍是当前 run 时才更新页面**；保留现有轮询/追问/引用/失败展示（轮询作为断线后的兜底与最终状态校准）。
- 单测写在 `frontend/src/api.test.ts`（或新增紧邻测试文件），用假 EventSource/fetch 验证：历史加载、增量追加、重复 seq 去重、断线重连补齐、RUN_TERMINAL 关闭、任务切换关闭旧流。

## 6. 验收职责（子代理 C）

- `src/test/java/io/devicetruth/server/AgentStreamTest.java`：真实临时 PostgreSQL（沿用 `DEVICETRUTH_TEST_PG_*` 环境变量、随机 `devicetruth_test_*` 库、用后清理）+ 产品 Spring 装配 + scripted reasoner。至少覆盖：
  1. 连接前已存在历史（先跑完一个 run，再连 SSE 能收到全部历史事件）；
  2. 历史与实时交界（补历史期间新事件不丢不重）；
  3. 断线期间新增事件、按 `Last-Event-ID` 重连补齐且按序不重复；
  4. `readRunEvents` 游标语义与非法游标 400、跨身份 404；
  5. 终态：COMPLETED/FAILED 收到 RUN_TERMINAL 后流结束；
  6. 白名单：事件 JSON 不含 prompt/key/stack 字样，data 只含契约 §2 字段；
  7. 场景断言：页面侧逻辑（12 条断线→写入 13、14→重连依序补齐各一次）在浏览器脚本中验证，Java 侧用两个连接模拟等价行为。
- 浏览器脚本 `scripts/verify_sse_browser.py`（复用 `verify_general_agent.py` 的 Server/环境约定，playwright 驱动打包后的 `frontend/dist` + 产品服务）：断线重连补齐场景、任务切换后迟到事件不更新页面。
- 只使用独立临时测试库；记录命令、退出码、测试统计、原始证据位置（outputs/ 下已忽略目录）。
- 不做产品源码改动；若发现后端/前端缺陷，记录并报告主代理修复。

## 7. 工作区规则（三个子代理共同遵守）

- 共享工作树，按上面文件所有权编辑，**保留他人改动**；越界改动先报告主代理，不直接改别人负责的文件。
- 构建错峰：A 只跑 Maven（compile/针对性 test），B 只跑 npm（vitest/build），C 在 A/B 完成后跑 Maven 测试与浏览器脚本；都不跑 `mvn clean`，不删 target 里的 JAR。
- 子代理不得宣告阶段/计划验收完成，最终验收由主代理整合后执行。

## 8. 修复契约（05-01 复验轮，2026-11-13）

### 8.1 渐进持久事件（P1 实时进度延迟）

- `TaskOrchestrator.runAgent(...)` 增加可选进度回调（新重载，旧签名委托 null；legacy `run(...)` 不变）。回调在每个内部事件记录点同步触发。
- `GeneralAgentService` 把 `TOOL_RESULT`/`MODEL_ATTEMPT` 在**执行过程中**立即以短事务落库为 `RUN_TOOL`/`RUN_MODEL`（先提交后通知），`persist()` 收尾**不再重复写**这两类事件，只写终态迁移（与现有状态更新同事务）。
- 新增持久事件类型 **`RUN_WAITING`**：`data={questionVersion}`，`waitForUser` 迁移同事务写入；非终态（流保持打开），白名单表增加该类型；时间线显示“等待补充信息”。
- **attempt 语义**：V16 新增 `attempt` 列。新的生命周期事件记 `attempt=0`，步骤事件记录当前执行尝试号。恢复时保留已发布步骤和用量，追加带被回收尝试号的 `RUN_RECOVERED`。新尝试的事件继续使用更大的 seq。客户端可以从原游标继续，之前的步骤保留为执行历史。
- 进度写入在同一事务内锁定 run，核对版本、尝试号和有效租约后再追加。恢复失败状态和 `RUN_FAILED` 同事务提交。

### 8.2 异步 SSE 与容量（P1 占用请求线程）

- 控制器改用 `SseEmitter`（Spring 异步）：校验（400/404/409/全局容量 503）仍在提交响应前完成；通过后在**专用有界线程池**上跑 relay，不占用 Tomcat 请求线程。
- 新配置：`devicetruth.stream.max-connections`（全局并发流上限，默认 64，超出 503 `STREAM_CAPACITY`）；`devicetruth.stream.ttl-minutes`（单连接期限，默认 60，到期由 emitter timeout 回调清理）。单 run 上限（16/409）保留。
- 完成（终态/客户端断开/IOException/超时）都走同一清理路径，摘除订阅、回减计数、唤醒正在等待的 relay 并结束 emitter。

### 8.3 原子订阅状态（P2 计数/列表竞态）

- 订阅登记与容量判定共享同一把锁：单一 `Map<runId, Set<Subscription>>`，add/remove/size 全部在 `synchronized(set)` 内完成；全局并发数用同一锁保护的计数。禁止计数器与列表分开更新。

### 8.4 公开视图空值规则（P2 Map.copyOf NPE）

- 规则：**`data` 中 null 值字段在公开视图中省略**（不报错、不出现 null）。`SafeAgentEventViews.pick` 跳过 null 值后再构造不可变 Map。同一条含空值事件必须能同时通过历史端点与 SSE 两条路径。

### 8.5 前端原生错误转发与永久失败（P2 onerror 未绑定）

- `defaultEventSourceFactory` 用 getter/setter 把包装器的 `onerror` 真正挂到原生 `EventSource` 的 error 事件上（原生 `source.onerror` 恒转发到当前 handler）。
- 原生连接进入 CLOSED 时立即触发 `onError` 并结束订阅。仍处于 CONNECTING 时交给浏览器按游标重连；连续错误达到阈值（默认 8 次，期间未收到成功事件）时停止。浏览器验收以明确的 HTTP 409 验证 CLOSED 路径，连续 CONNECTING 错误计数由前端单测验证。
- 阈值经 `RunStreamOptions.maxRetryErrors` 可配（测试调小）。

### 8.6 无 run 会话的状态清理（P2 旧时间线残留）

- `timeline.ts` 新增 `clear()`：关流、generation+1、runId=null、事件/maxSeq/terminalStatus 全部重置并通知。
- `App.vue`：当前会话无任何 run（run==null）→ `timeline.clear()`（替代只 close 的旧行为）。迟到事件由 generation 丢弃 + runId 为空双保险。

### 8.7 文件所有权（本轮）

- A「后端 SSE」：`TaskOrchestrator`（runAgent 重载）、`GeneralAgentService`、`AgentRunRepository`、`RunRecoveryService`、`RunEventStreamService`（SseEmitter 重构）、`AgentConversationController`、`ProductConfiguration`、`SafeAgentEventViews`、`V16__run_event_attempt.sql`。仅跑 Maven。
- B「前端恢复」：`frontend/src/api.ts`、`timeline.ts`、`App.vue`、`components/RunTimeline.vue`、`api.test.ts`。仅跑 npm。
- C「回归验收」：`AgentStreamTest`、`scripts/verify_sse_browser.py`（可新增断言）；A/B 合入后运行。
- 事件/游标/终态/续传/容量语义以 §1–§3 + §8 为准；公开事件白名单在 §2 表上增加 `RUN_WAITING: questionVersion`。

### 8.8 复验轮实施中新增的两条事实（主代理记录）

- **SSE 错误体绕过内容协商**：SSE 客户端 Accept 只有 `text/event-stream`，若让 400/404/409/503 走 `@ExceptionHandler` 的 JSON 返回，Spring 内容协商会 406 并升级为 500（复验实测 6 项用例红）。控制器改为在校验失败时手写 JSON 错误体（`{code,message,retryable,traceId}`）并返回 null，与旧同步实现一致。
- **容量回收时延是心跳语义**：客户端 close 后，服务端在下一个心跳写失败时（最长约两个 15s 周期）才感知断开并释放订阅额度。验收用例按此语义设计：单连接探针排水（1 条/5s，低于清理速率）+ 16s 退避重试，不把"立即回收"当契约。产品侧如需更快回收，可后续把心跳间隔做成配置（NOT_RUN，本轮不做）。
