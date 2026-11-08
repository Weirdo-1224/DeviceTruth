# DeviceTruth V0.2｜S1 最终验收报告

> 状态：**离线验收 PASS；在线 Router / 在线 Reasoner 为 `BLOCKED_BY_ENV`（未执行真实外部模型调用）**。
> 本报告全部数据来自本机实际执行的命令与日志，不做"理论上通过"的推断。运行产物目录 `.m3-logs/` 不入交付。

## 1. 验收元数据

- 验收时间：2026-11-08（M3 修复回合：M3-P1-01 修复后的代码）
- 仓库分支/commit：`main` @ `5c82058`（M2 checkpoint 后文档提交）+ 未提交的 M3 工作树（未 commit / 未 push / 未打 tag）
- 比较基线：M2 checkpoint `0dff7c9`
- JDK/Maven：JDK `21.0.13.0.2`（项目内 `.tools/jdk-21.0.13.0.2+1`，显式前置 `JAVA_HOME`/`PATH`）、Maven 3.9.9、Spring Boot 3.5.16
- 设备/操作系统：Windows 11 家庭中文版，Git Bash 终端
- CLI JAR 实际名称：`target/devicetruth-core-0.1.0.jar`；`Start-Class=io.devicetruth.app.DeviceTruthApplication`
- Server 真实启动命令/端口：显式 classpath 启动 `io.devicetruth.server.DeviceTruthServerApplication`，每次随机空闲端口（本次探针 59470），`finally` 中终止进程并释放端口
- Router 模式：`offline`（`DeterministicTaskRouter`）
- Core Reasoner 模式：`scripted`
- 模型连接是否真实可用：**否** —— 未执行任何真实 Qwen/OpenAI 兼容调用（仅判断 `DASHSCOPE_API_KEY` 是否存在，未读取值）
- 证据文件/报告存放位置：`.m3-logs/probe-results.json`（主探针 20 项）、`.m3-logs/probe-special-results.json`（特殊探针 7 项）、`.m3-logs/m3-package.log`、`.m3-logs/cli-{A,B,C,D,E}.log`、`.m3-logs/verify_cli.log`、`.m3-logs/probe-server.log`、`.m3-logs/probe-tc11.log`、`.m3-logs/probe-tc17.log`

## 2. 基线与新增测试总览

| 项目 | 真实命令 | 退出码与统计 | 产物/备注 |
|---|---|---|---|
| `mvn -q clean test`（M3 开工基线） | `mvn -q -o clean test` | 0；24 类 **125 项** 0/0/0 | M2 基线 |
| `mvn test` / `mvn package`（最终） | `mvn -o clean package` | 0；**28 类 145 项，0 失败/0 错误/0 跳过** | `.m3-logs/m3-package.log`；`Start-Class=io.devicetruth.app.DeviceTruthApplication` |
| 原 CLI A/B/C/D | `java -jar target/devicetruth-core-0.1.0.jar --scenario={A,B,C,D} --reasoner=scripted` | 0×4 | 7/7/7/6；RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE（`.m3-logs/cli-*.log`） |
| 未知工具/伪造证据 E 负例 | `java -jar ... --scenario=E --reasoner=scripted` | 2 | 非法参数仍拒绝，未新增 E 场景 |
| `scripts/verify_cli.py` | `python scripts/verify_cli.py` | 0 | 4 项 PASS（含缺 Key FAILED 无降级、私有配置仅本机 HTTP 替身） |
| Server 启动+运行 | 真实 Server，显式 classpath，随机端口 | 0 | `probe-server.log` 记录启动命令；探针结束已终止并释放端口 |
| HTTP 端到端 | `python .m3-logs/m3_probe.py` | 0 | **20/20 PASS**（TC-01~TC-10、TC-13~TC-16、TC-18/19） |
| HTTP 特殊场景 | `python .m3-logs/m3_probe_special.py` | 0 | **7/7 PASS**（TC-11、TC-16b、TC-17，两个独立真实 Server） |
| online Router | — | **BLOCKED_BY_ENV** | 未检测到可用模型凭据（`DASHSCOPE_API_KEY` 为空），未执行真实调用 |
| online Core Reasoner | — | **BLOCKED_BY_ENV** | 同上；离线 scripted 已覆盖完整 Core 链路 |

## 3. TC-01~TC-20 验收矩阵

状态只允许：`PASS / FAIL / NOT_RUN / BLOCKED_BY_ENV`。每个 PASS 都有具体证据（测试类、探针项或命令日志）。

| TC | 验收内容 | 状态 | 命令/请求/测试类与证据 | 失败或环境阻碍 |
|---|---|---|---|---|
| 01 | Session 201 + 合法 UUID，可查消息 | PASS | 探针 TC-01（`POST /api/sessions` → 201 + UUID）；`ServerHttpTest.createSessionReturns201WithRealUuid`、`newSessionHasEmptyMessageList` | — |
| 02 | CAR-B profile 查询与来源 | PASS | 探针 TC-02：`GET` 后 `QUERY`，`sources=[CAR-B-FACTORY / simulated:CAR-B/FACTORY / FACTORY]`，回答含 `D-120A`；`QueryHttpTest.profileQueryReturns200WithRealFactorySource` | — |
| 03 | CAR-B history 查询，不编造 | PASS | 探针 TC-03：`MAINTENANCE_HISTORY` 仅 `CAR-B-REPLACE`，回答含"不等于当前现场确认"；`QueryHttpTest.historyQueryReturnsReplacementRecordsOnly`、`emptyHistorySaysNoRecordsInsteadOfInventing` | — |
| 04 | DIAGNOSE 真实进入 Core | PASS | 探针 TC-04：`COMPLETED / RESOLVED / appliedRuleIds=[DEMO-D-120B] / stepsUsed=7`；`DiagnosisRunServiceTest.scriptedRunReachesCoreAndExposesRealResult`、`DiagnosisHttpTest.diagnosisRunsRealCoreAndExposesRealResultAndEvents` | — |
| 05 | 202/Location/QUEUED，GET 真实结果 | PASS | 探针 TC-05+TC-04：`202`，`Location=/api/diagnoses/{id}`，`executionStatus=QUEUED`，GET 返回真实结论；`DiagnosisHttpTest.startDiagnosis/awaitFinished` | — |
| 06 | 运行中无事件，结束后白名单事件 | PASS | 探针 TC-06：结束后 34 条事件 / 8 类，序号 1..34 连续；`DiagnosisRunServiceTest.eventsAreNotAvailableBeforeCompletion`；M3-P1-01 修复后 `DiagnosisHttpTest` 断言 7 类事件 summary 与 referenceIds 均来自真实载荷（`提议动作 CALL_CAPABILITY`/`能力结果：成功`/`结案校验：RULE_AND_EVIDENCE_VERIFIED`/`假设失效 [H-1]`） | — |
| 07 | "这台设备"无上下文 CLARIFY | PASS | 探针 TC-07：`200 CLARIFY`，提示需提供设备编号；`ServerHttpTest.clarifyReturns200AndRecordsUserAndSystemMessages` | — |
| 08 | 未登记 SN-0245 无证据串用 | PASS | 探针 TC-08：`200 UNSUPPORTED`，响应不含 `CAR-B-FACTORY`/`simulated:CAR-B`；`QueryHttpTest.queryAboutUnregisteredAssetIsUnsupportedAndNeverReusesCarBEvidence`、TaskRouterTest 的完整编号边界 | — |
| 09 | 未支持故障/危险操作边界 | PASS | 探针 TC-09a（重启请求 → `UNSUPPORTED` 危险词文案）、TC-09b（空调 → `UNSUPPORTED` 未覆盖症状）；`ServerHttpTest.dangerousOperationIsExplicitlyUnsupported`、`unregisteredAssetIsExplicitlyUnsupported` | — |
| 10 | 两会话并发，Reasoner/归属隔离 | PASS | 探针 TC-10：两次并发诊断得 `RESOLVED` 与 `CONFLICT`（互不串案，`sessionId` 各自正确）；TC-10b 归属不匹配与不存在同为 404 且文案相同；`DiagnosisRunServiceTest.eachRunGetsItsOwnReasonerInstance`、`ownershipMismatchIsIndistinguishableFromMissing` | — |
| 11 | timeout/非法 JSON/Core FAILED | PASS（离线 FAILED 映射） | 探针 TC-11（`reasoner=qwen` 且缺 Key 的真实 Server）：POST 仍 `202/QUEUED` → GET `FAILED / outcome=null / errorCode=DASHSCOPE_API_KEY_MISSING`；`DiagnosisRunServiceTest.coreFailureIsMappedToFailedNotCompleted`、`freeTextFailureReasonIsWithheld`；timeout/非法 JSON 的稳定码由 `StructuredReasoner` 既有测试覆盖 | — |
| 12 | 原 CLI A/B/C/D 与 E 负例 | PASS | CLI A–D 退出 0（7/7/7/6；RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE），E 退出 2；`verify_cli.py` 退出 0 | — |
| 13 | requestedAt≠diagnosisAsOf 且时间有效 | PASS | 探针 TC-13/TC-13b：`requestedAt=2026-11-08T11:10:22Z`（请求时刻）、`diagnosisAsOf=2026-11-03T04:00:00Z`（固定业务时间）；`DiagnosisApplicationServiceTest` | — |
| 14 | 出厂记录不能冒充当前控制器 | PASS | 探针 TC-14：profile 结果 `limitations` 含"出厂/档案记录只代表设备初始档案，不能回答当前实际安装的是不是该型号"；`QueryHttpTest` 断言 limitations | — |
| 15 | evidenceId/sourceRef/SIMULATED | PASS | 探针 TC-15：`evidenceId=CAR-B-FACTORY`、`sourceRef=simulated:CAR-B/FACTORY` 两者独立、`dataNature=SIMULATED`；`QueryServiceTest` | — |
| 16 | 事件/错误/日志不泄漏机密 | PASS | 探针 TC-16：诊断+事件响应无 `reportPath`/`outputs`/`PROTOCOL`/`apiKey`/`Investigation stopped`/`PROMPT_FRAGMENT`；TC-16a 事件体无 `context`/`capabilities`；TC-16b FAILED 事件仅 `技术失败：DASHSCOPE_API_KEY_MISSING`；`DiagnosisSafetyMappingTest` 4/4（合法 ESCALATE / REQUEST_INFORMATION 携带 `C:\review-fixture\... PROMPT_FRAGMENT` 不回显，白名单码原样返回，Core 内部报告保留原值） | — |
| 17 | 超容量 429/503，无幽灵 QUEUED | PASS | 探针 TC-17（`max-concurrent-runs=1`、`max-queue=0`）：`statuses=[202,429,429,429,429,429]`，`429 RUN_CAPACITY_EXCEEDED`；会话消息 `2` 条（=1 次成功交互，拒绝不留半条）；已接受运行全部离开 QUEUED，`ghosts=0`；`DiagnosisRunServiceTest.rejectedTaskLeavesNoGhostQueuedRun`；Router 不可用时 503 由 `RouterUnavailableHttpTest` 覆盖 | — |
| 18 | Web/CLI 双入口独立工作 | PASS | 探针 TC-18：真实 Server 独立运行（随机端口）；CLI A–D 单独回归通过；`Start-Class` 仍为 CLI 主类；`ApplicationTest` | — |
| 19 | 无 API Key 离线通路 | PASS | 探针 TC-19：offline + scripted 下 QUERY `200`；`verify_cli.py` 缺 Key 场景无降级 | — |
| 20 | 真实在线 Router + online Reasoner | **BLOCKED_BY_ENV** | 未执行 `verify_online.py` 与任何真实外部模型调用。障碍：环境未提供可用模型凭据（只判断 `DASHSCOPE_API_KEY` 非空性，未读取值）；在线 Router 仅验证过"缺配置 → 503"，Core 在线 Reasoner 仅验证过"缺 Key → FAILED" | **不计在线 PASS**；如实记录为环境阻塞 |

## 4. 关键完整请求链（真实证据）

```text
POST /api/sessions
    → 201 {"sessionId":"a8465d04-b411-44bc-a30c-f2d4cbba8417", ...}

POST /api/sessions/{sessionId}/messages  {"content":"CAR-B 通信超时，帮我诊断"}
    → 202  Location: /api/diagnoses/441a98cb-2943-4a00-96a7-889b7a0a17b1
    → {"route":"DIAGNOSE","executionStatus":"QUEUED","diagnosisId":"441a98cb-...", "reply":"已接受模拟诊断任务…"}

GET /api/diagnoses/441a98cb-...?sessionId=...
    → executionStatus=COMPLETED, outcome=RESOLVED, appliedRuleIds=["DEMO-D-120B"], stepsUsed=7,
      requestedAt=2026-11-08T11:10:22.145882900Z, diagnosisAsOf=2026-11-03T04:00:00Z,
      stopReason=RULE_AND_EVIDENCE_VERIFIED, dataNature=SIMULATED（无 reportPath）

GET /api/diagnoses/441a98cb-.../events?sessionId=...
    → eventsAvailable=true，34 条事件 / 8 类，sequence 1..34
      2 ACTION_PROPOSED | 提议动作 CALL_CAPABILITY | [diagnosis.rules.query]
      3 CAPABILITY_RESULT | 能力结果：成功 | [diagnosis.rules.query]
     17 HYPOTHESIS_INVALIDATED | 假设失效 | [H-1]
     32 GUARD_RESULT | 结案校验：RULE_AND_EVIDENCE_VERIFIED | [DEMO-D-120B]
     34 TERMINATED | 运行结束：RESOLVED/RULE_AND_EVIDENCE_VERIFIED | [DEMO-D-120B, CAR-B-FIELD-1]
Core 对应调用/RunReport：报告目录 `<output-root>/<独立UUID>/`（仅内部，响应不返回）；coreTaskId 取 report.state().taskId()
```

- QUERY 真实请求/响应：`CAR-B 的出厂控制器型号？` → `200 QUERY`，`sources=[{evidenceId:CAR-B-FACTORY, sourceRef:simulated:CAR-B/FACTORY, sourceType:FACTORY, dataNature:SIMULATED}]`；`CAR-B 发生过哪些更换？` → `200 QUERY`，`sources=[CAR-B-REPLACE]`。
- 在线模型 trace 摘要：**无**（`BLOCKED_BY_ENV`，未发起请求）。

## 5. 实际新增/修改文件（`git diff 0dff7c9` + 未跟踪）

**新增主源码**（`src/main/java/io/devicetruth/application/`）：

- `DiagnosisRunService.java`：有界线程池（并发 2 / 队列 10，0 容量用 `SynchronousQueue`）、`QUEUED→RUNNING→COMPLETED|FAILED`、队列拒绝同步移除注册项、`coreTaskId`/`reportPath` 分离、归属校验。
- `ReasonerFactory.java`：只保存创建方式，每任务 `create()` 新实例。
- `RunEventViews.java`：11 类真实事件 → §4.7 白名单 `RunEventView`；**M3-P1-01 修复**：先把内存 record 载荷规范化为 JSON Map 再取字段。
- `SafePublicFields.java`：§4.8 的 28 项显式停止码白名单、摘要/警告/缺口脱敏。
- `ExecutionStatus.java`：应用层四态（与 Core `RunStatus` 不同名）。

**新增测试**：`DiagnosisRunServiceTest`(7)、`SafePublicFieldsTest`(5)、`DiagnosisHttpTest`(4，含 M3-P1-01 事件视图回归断言)、`DiagnosisSafetyMappingTest`(4)。

**修改**：`api/ApiDtos.java`（新增 `DiagnosisView`/`DiagnosisEventsResponse`）、`api/ConversationController.java`（DIAGNOSE 202+Location、两个诊断 GET 端点）、`api/ApiExceptionHandler.java`（`RUN_CAPACITY_EXCEEDED`→429）、`application/ApplicationExceptions.java`（`RunCapacityExceededException`）、`application/ConversationFlowService.java`（删除 DIAGNOSE 501，接异步调度）、`application/ConversationSession.java`（清理 M2-P2-01 死代码）、`server/ServerConfiguration.java`（Gateway/ReasonerFactory/DiagnosisRunService 装配）、`test/.../ServerHttpTest.java`（501 断言 → 202 契约）、`README.md`、`docs/.../task.md`、`docs/.../state.md`、本报告。

## 6. 当前能力与边界结论

- 能否证明中文 HTTP 已真实驱动已有 Core：**能（离线 scripted）**。`POST → DiagnosisRunService → TaskOrchestrator.run → RunReport` 全链路真实执行，CAR-B 得 `RESOLVED/DEMO-D-120B/7 步`，事件 34 条 8 类。
- 离线/CI 结果：`mvn -o clean package` 退出 0，28 类 **145 项** 0 失败/0 错误/0 跳过；CLI A–D 退出 0；`verify_cli.py` 退出 0。
- 在线 Router/Reasoner 结果：**BLOCKED_BY_ENV，未验证**，不记 PASS。
- 原 CLI、安全/证据回归：CLI A–D/E 与 `verify_cli.py` 全通过；`core/`、`adapter/`、`app/`、`pom.xml`、`datasets/`、`skills/`、`scripts/` 相对 `0dff7c9` 零改动；`PROMPT_FRAGMENT` 负例不回显。
- 已实现（S1 范围）：HTTP 四类路由、受限 QUERY、异步 DIAGNOSE（真实 Core）、状态/结果/结束后事件、容量与归属、§4.8 安全映射。
- **尚未实现（不属 S1，不阻塞）**：S2 的 Hypothesis 专业调查闭环与补证恢复（`HypothesisStatus.SUPPORTED` 仍无赋值路径）、S3 双域 Skill 与工厂场景、S4 Vue 工作台与实时轨迹；S1 不含实时推流（SSE/WebSocket）与中途补证。

## 7. Codex 最终 Review

- M3 复审文件：`reviews/M3.md`（首审 CHANGES_REQUIRED → 修复复审 **PASS**，2026-11-08）
- 结论：**离线验收 PASS；TC-20 在线 Router / 在线 Reasoner 保持 `BLOCKED_BY_ENV`，未验证、不计入通过**
- 主要阻塞项：无（首审两个 P1——事件视图映射错误、验收报告未填写——已修复并经 Reviewer 独立复测关闭；`mvn -q -o clean package` 28 类 145 项 0/0/0、CLI A–D/E、`verify_cli.py`、三场景真实 HTTP 探针含 `PROMPT_FRAGMENT` 负例全部通过）
- 残留非阻塞项：P2×6 登记延期（M1×2、M3×4），见 `state.md` §5
- 用户确认：待填
