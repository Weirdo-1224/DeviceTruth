# DeviceTruth V0.2｜跨会话状态快照（State）

> 此文件只记录**最新有效状态**；每个里程碑通过 Codex Review 后更新。不要复制聊天记录、完整测试日志或长篇 Review。

## 1. 当前总体状态

- 计划：S0 → S1；后续 S2 → S3 → S4，不调整。
- 当前阶段：**S1 已关闭**（S0 已关闭；M0、M1、M2、M3 均 DONE，2026-11-08）。
- 当前里程碑：**无**；最后完成 M3（S1）。下一里程碑：**S2（未拆分、TODO）**，需先做里程碑拆分规划，不在 S1 文档内预建编号、不提前标完成。
- 文档规格状态：**FROZEN**（M0 Review #2 PASS 后转入；此后改规格须走 `spec.md` §10 并经用户批准）。
- 当前开发基线 commit：仍为 **M2 checkpoint `0dff7c9`**（M3 未授权 commit）。M3 交付 = 工作树相对 `0dff7c9` 的 diff；对照快照 `outputs/baselines/M3/`（patch + 未跟踪副本 + 18 个交付文件 SHA-256 全 OK、聚合 `9ed731c0…`），S2 比较基线 = `git diff 0dff7c9 -- src README.md`。
- 工作树状态（M3 交接时）：M3 业务交付 + 流程文档更新均未提交；`target/`、`outputs/runs`、`.m3-logs/` 为运行产物/证据，不纳交付。
- 是否完成 S1：**是（离线验收 PASS）**；在线 Router/在线 Reasoner 为 `BLOCKED_BY_ENV`，不属 S1 完成条件但不得宣称在线通过。

## 2. 里程碑进度

| ID | 状态 | Review | 交付摘要 |
|---|---|---|---|
| M0 | **DONE** | `reviews/M0.md`（#1 CHANGES_REQUIRED → #2 PASS） | `S0_AUDIT.md` 审计 + `spec.md` 冻结 + Review P1/P2 修复 |
| M1 | **DONE** | `reviews/M1.md`（#1 CHANGES_REQUIRED → #2 CHANGES_REQUIRED → **#3 PASS**） | 双入口 + Server/健康端点 + Catalog + Session + 四类路由（QUERY/DIAGNOSE 暂为明确 501）；完整 token 边界资产校验（CAR-ZZ/CAR-B2/CAR-B-UNKNOWN 不再回落 CAR-B，规则/证据 ID 豁免） |
| M2 | **DONE** | `reviews/M2.md`（首审 **PASS**） | QueryService（仅 ASSET_PROFILE/MAINTENANCE_HISTORY，真实 profile/history + 真实 sources）；DiagnosisGoal→DiagnosisRequest（Catalog 固定 asOf + 规则存在性）；QUERY 返回 200 真实结果，DIAGNOSE 仍明确 501 |
| M3 | **DONE** | `reviews/M3.md`（首审 CHANGES_REQUIRED → 修复复审 **PASS**） | 有界异步 DiagnosisRunService（队列拒绝同步移除注册项、Core FAILED 不包装 COMPLETED）+ 每任务 ReasonerFactory + 202/QUEUED/Location + GET 状态/结果（三 ID 分离、§4.8 白名单过滤、openNeeds 无 description）+ 结束后 11 类白名单事件视图 + TC-01~TC-20 验收报告（TC-20 在线 BLOCKED_BY_ENV） |

## 3. 已确定的**设计意图**（S1 契约已冻结，实现待 M1–M3）

- 保留 V0.1 自研 `TaskOrchestrator`/Guard/证据机制，不使用 ReActAgent 替代循环。
- S1 用 HTTP 进入自然语言路由，再分别执行受限 QUERY / DIAGNOSE；CLARIFY 和 UNSUPPORTED 不伪造执行。
- CLI 默认 JAR Main 保留；Web 单独 Main，装配集中在 `io.devicetruth.server`（现网事实：`core/`、`adapter/`、`app/` 无 Spring 组件注解）。
- 模拟数据/业务时间与请求时间分离；每任务独立 Reasoner；最终事件结束后可读。
- S2 再做 Hypothesis 专业调查闭环、补证恢复；S3/S4 不提前。

## 4. M0 收尾已核实的事实（细节见 `reviews/M0.md` §8.4、`S0_AUDIT.md`）

- Review #2 实跑：`mvn -q '-Dtest=AcceptanceTest,RuntimeTest' test` 退出 0（**9 项**，0 失败/0 错误/0 跳过）；`WHITELIST_PROVENANCE=PASS 5+8+15=28 distinct values`；两份真实 B 报告均为 **34 条事件 / 8 类**；`Start-Class=io.devicetruth.app.DeviceTruthApplication`；`git diff 14952b6 -- src pom.xml scripts datasets skills .mvn .vscode` 为空。
- 首审同代码基线：`mvn -q clean test` 退出 0（**42 项**）、`mvn -q package` 退出 0、CLI A/B/C/D 退出 0（7/7/7/6 步；A/B RESOLVED、C CONFLICT、D NEED_EVIDENCE）、`--reasoner=invalid` 退出 2、E 负例 1 项通过、`python scripts/verify_cli.py` 退出 0。**9 项与 42 项不混用，本轮未重跑全量/打包。**
- 交接时自查（2026-11-08）：`git diff 14952b6 -- src pom.xml scripts datasets skills .mvn .vscode` 仍为空，`HEAD=14952b6`，分支 `main`，无 tag/stash → 上述门禁证据对当前代码继续有效。
- 环境：项目内 JDK `21.0.13.0.2`、Maven 3.9.9（UTF-8）。**默认 `java` 是 17**，每次必须显式 `JAVA_HOME`，Git Bash 下需 POSIX 路径才能让 `PATH` 前置生效。
- `pom.xml`：parent 3.5.16、`java.version=21`、agentscope 2.0.4；**无 `spring-boot-starter-web`**，`spring-boot-maven-plugin` **未配置 `mainClass`**（当前靠自动探测 CLI 主类）→ M1 首项工作。
- Core 事实（未变）：`TaskOrchestrator.run(DiagnosisRequest,Budget,Reasoner)`；`reportPath` 目录 UUID **≠** `state().taskId()`；源码识别 **11 类**事件、scripted B 实跑触发 **8 类**；`Reasoner.Context` 曾整体进入 `REASON_INPUT.data`。
- Repository 只有 `profile/history/search/get/query`，**无** `domainOf/listAssets/listSymptoms/exists`；`CapabilityGateway.execute` 需完整诊断 Scope，事实查询不得借用。
- 数据：A–D 四场景同 `domain=automotive-demo`、同 `symptomCode=COMM_TIMEOUT`、同 `asOf=2026-11-03T04:00:00Z`；`CAR-B-FIELD-1` `observedAt=2026-11-03T03:00:00Z`、`validTo=2026-11-04T00:00:00Z`。

### 4.1 M1 交付已验证事实（2026-11-08，开发者自测）

- `mvn -q clean package` 退出 0；20 个测试类 **88 项**，0 失败/0 错误/0 跳过（M0 基线 42 + 新增 46）。
- `META-INF/MANIFEST.MF`：`Main-Class=org.springframework.boot.loader.launch.JarLauncher`、`Start-Class=io.devicetruth.app.DeviceTruthApplication`。
- Server 真实启动（显式 classpath，见 task.md §3.3）：`Tomcat started on port 8080 (http)`；`GET /api/health` → `200 {"status":"UP","dataNature":"SIMULATED"}`；验证后进程已停止、端口已释放。
- 四路由实测：CLARIFY/UNSUPPORTED → `200`；QUERY/DIAGNOSE → `501 NOT_IMPLEMENTED` 且不写消息；`SN-0245` 不被映射为 CAR-B。
- `spring-boot:run -Dspring-boot.run.main-class=...` 在本项目 3.5.16 下被 pom 显式 `mainClass` 覆盖，实际启动 CLI；不能作为 Server 启动命令写入文档。
- CLI 回归：A/B/C/D 退出 0（7/7/7/6）；`python scripts/verify_cli.py` 退出 0。

### 4.2 M1 首审 P1 修复已验证事实（2026-11-08，修复轮；已由 Review #3 关闭）

- 全量门禁：`mvn -q clean package` 退出 0，**21 个测试类 101 项，0 失败/0 错误/0 跳过**（原 88 + 首审修复新增 13）；`Start-Class=io.devicetruth.app.DeviceTruthApplication`。
- CLI A–D 退出 0（7/7/7/6；RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE）；`python scripts/verify_cli.py` 退出 0（4 项 PASS）。
- 真实 HTTP 探针（3 个真实 Server + 本机模型替身，退出 0）4 个 P1 全部翻转：
  - P1-01：`ONLINE no-asset` → `200 CLARIFY`（原 501）；`ONLINE override` → `200 CLARIFY assetId=None`（原错误绑定 CAR-C）。
  - P1-02：`OFFLINE CAR-Z after bind` → `200 UNSUPPORTED`（原 501）；`OFFLINE multi` → `200 CLARIFY assetId=None`（原 501）。
  - P1-03：`ONLINE dangerous` → `200 UNSUPPORTED`（原 501）。
  - P1-04：`max-messages=3` 下 429 后仍 2 条 `[USER, SYSTEM]`，无半条泄漏（原 3 条含半条 USER）。
- 修复均不触及 `core/`、`DecisionGuard` 与冻结 `spec.md`；未新增 Spring 组件注解；未伪造 Repository 方法。

### 4.3 M1 通过已核实的事实（2026-11-08，Review #3 PASS）

- 门禁：`mvn -q -o clean package` 退出 0（**21 类 109 项，0 失败/0 错误/0 跳过**）；`Start-Class=io.devicetruth.app.DeviceTruthApplication`；`python scripts/verify_cli.py` 退出 0（A/B `RESOLVED`、C `CONFLICT`、D `NEED_EVIDENCE`，7/7/7/6，缺 Key FAILED 无降级）。
- 独立 HTTP 探针（2 个真实 Server + 本机模型替身）退出 0：**16/16**——§8.3 四个复现案例（CAR-ZZ/CAR-B2/CAR-B-UNKNOWN 有/无上下文、在线替身建议 DIAGNOSE）全部翻转为 `200 UNSUPPORTED assetId=null`；九项保留行为回归（CAR-C、上下文复用、多资产 ASSET_AMBIGUOUS、文本/hint 冲突、SN-0245、规则/证据 ID 不误伤、小写 car-b、拒绝后上下文不污染）；P1-01/03 关闭状态保持；全程 `SIMULATED`，端口无残留。
- 变更面仅 4 文件（`RouteValidator`、`ServerConfiguration`、`TaskRouterTest` +5、`LlmTaskRouterTest` +3）；其余 30 个交付文件 SHA-256 与 Review #2 一致；`spec.md`、`core/adapter/app`、数据集零改动。
- M1 基线快照：`outputs/baselines/M1/`（`SHA256SUMS.txt` 33 个交付文件全 OK，聚合 `6abaca40…`；task/state 流程文档随交接更新不入清单）；M2 比较基线 = checkpoint commit `2c599e7`（快照仅作对照副本）。

### 4.4 M2 交付已验证事实（2026-11-08，开发者自测；已由 Review 首审 PASS 复核）

- 开工基线：`git status` 为空；`mvn -q -o clean test` 退出 0（21 类 109 项，0/0/0）。
- 门禁：`mvn -q -o clean package` 退出 0（**24 类 125 项，0 失败/0 错误/0 跳过**，109 + 新增 16）；`Start-Class=io.devicetruth.app.DeviceTruthApplication`。
- CLI 回归：A/B/C/D 退出 0（7/7/7/6；RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE）；`--scenario=E` 退出 2；`python scripts/verify_cli.py` 退出 0（4 项 PASS）。
- 真实 Server 探针（开发者 `target/m2-probe.py`，自起自停 + 随机端口，退出 0）：**12/12**——`CAR-B 出厂型号` → `200 QUERY`，`sources=[CAR-B-FACTORY / simulated:CAR-B/FACTORY / FACTORY / SIMULATED]`；`CAR-B 更换` → `MAINTENANCE_HISTORY`，来源仅 `CAR-B-REPLACE`；`CAR-A 更换` → 空结果且明确“没有维修/更换类模拟记录”；`SN-0245 出厂配置` → `200 UNSUPPORTED` 且无任何 CAR-B 来源；`CAR-B 通信超时` → `501 NOT_IMPLEMENTED`；会话消息 8 条成对记录。（该探针为 target 运行产物，已在 Review 的 `clean package` 中清除；等价证据见 §4.5 Reviewer 独立探针 13/13。）
- Goal→Request 映射探针（开发者 `target/probe-src/M2PlanProbe.java`）：`assetId=CAR-B, domain=automotive-demo, symptomCode=COMM_TIMEOUT, asOf=2026-11-03T04:00:00Z`，会话消息 0 条（未伪装执行），非诊断消息被拒绝。（同上已被清除；等价证据见 §4.5 `PlanProbe`。）
- 边界：`core/`、`adapter/`、`app/`、`spec.md`、`pom.xml`、`datasets/`、`skills/`、`scripts/` 相对 `2c599e7` 零改动；`core/adapter/app` 无新增 Spring 注解。

### 4.5 M2 通过已核实的事实（2026-11-08，Review 首审 PASS）

- 门禁：`mvn -q -o clean package` 退出 0（**24 类 125 项，0 失败/0 错误/0 跳过**）；`Start-Class=io.devicetruth.app.DeviceTruthApplication`；CLI A–D 退出 0（7/7/7/6；RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE）；`--scenario=E` 退出 2；`python scripts/verify_cli.py` 退出 0（4 项 PASS）。
- Reviewer 独立 HTTP 探针（真实 Server 自起自停、随机端口）退出 0：**13/13**——两类 QUERY 真实来源与语义 limitations、空历史不编造、SN-0245 无 CAR-B 泄漏、DIAGNOSE 501 且会话消息成对无 DIAGNOSE/无半条、响应无 `reportPath/stopReason/diagnosisSummary`。
- Reviewer 独立 `PlanProbe`（真实装配 `planDiagnosis`）退出 0：`assetId=CAR-B, domain=automotive-demo, symptomCode=COMM_TIMEOUT, asOf=2026-11-03T04:00:00Z`（固定业务时间，非请求时间），会话 0 消息，回复明示“诊断尚未执行”。
- 变更面相对 `2c599e7`：`api/application/server` 8 改 + 7 新增（含 3 个测试类），`core/adapter/app/pom.xml/datasets/skills/scripts` 零改动；`core/adapter/app` 无新增 Spring 注解。
- 基线：`outputs/baselines/M2/`（patch + 8 个未跟踪文件副本 + 18 个交付文件 SHA-256 全 OK，聚合 `6282c5f2…`）；本轮未授权 commit，M3 以工作树 diff `2c599e7 -- src README.md` 为比较基线。

### 4.6 M3 首审修复已验证事实（2026-11-08，开发者自测）

- M3-P1-01 根因：`RunEventViews` 把内存 `RunEvent.data` 当 Map 导航，而 Core 存的是 record，字段全取 null → 成功能力调用显示 `TOOL_FAILURE`、动作恒 `UNKNOWN`、`结案校验：` 空、referenceIds 全空。修复：`Json.MAPPER.convertValue` 先规范化为 JSON Map（与 `RunReportWriter` 落盘形状一致），`core/` 零改动。
- 前后对比（`EventsProbe`，真实 scripted Core 跑 CAR-B）：修复前 `提议动作 UNKNOWN / 能力结果：TOOL_FAILURE / 结案校验：（空）`；修复后 `提议动作 CALL_CAPABILITY / 能力结果：成功 / 结案校验：RULE_AND_EVIDENCE_VERIFIED / [DEMO-D-120B]`，34 条事件 8 类。
- M3-P1-02：`S1_ACCEPTANCE_REPORT.md` 已按真实证据逐项填写 TC-01~TC-20；TC-20 = `BLOCKED_BY_ENV`。
- 门禁：`mvn -o clean package` 退出 0（**145 项 0/0/0**，`Start-Class` 仍为 CLI 主类）；CLI A–D 退出 0（7/7/7/6）、E 退出 2；`verify_cli.py` 退出 0；P1-01 修复后**重跑**主探针 **20/20**、特殊探针 **7/7**。
- 顺带清理 M3-P2-02 死代码 `completedResult()`。范围仅 `RunEventViews`、`DiagnosisRunService`、`DiagnosisHttpTest` 与文档；`core/adapter/app/pom/datasets/skills/scripts` 相对 `0dff7c9` 仍零改动。

### 4.7 M3 通过已核实的事实（2026-11-08，Review 修复复审 PASS）

- 门禁：`mvn -q -o clean package` 退出 0（**28 类 145 项，0 失败/0 错误/0 跳过**）；`Start-Class=io.devicetruth.app.DeviceTruthApplication`；CLI A–D 退出 0（7/7/7/6）、`--scenario=E` 退出 2；`verify_cli.py` 退出 0。
- Reviewer `EventsProbe` 复核 P1-01：34 条事件 8 类，5 条 `CAPABILITY_RESULT` 全部 `能力结果：成功` + 真实能力 ID，7 条 `ACTION_PROPOSED` 类型真实（含 `PROPOSE_DECISION [DEMO-D-120B]`），`GUARD_RESULT` 带判定码与规则 ID，`HYPOTHESIS_INVALIDATED [H-1]`，缺陷摘要 0 条。
- Reviewer HTTP 探针（3 个真实 Server 自起自停）R-01~R-19 全 PASS：202/QUEUED/Location、真实结果、`diagnosisAsOf` 固定、无 `reportPath`、白名单码原样、FAILED 不包装、合成 `PROMPT_FRAGMENT` 经真实 GET 不回显、归属/不存在同码 404。
- `pom.xml/core/adapter/app/resources/datasets/skills/scripts` 相对 `0dff7c9` 零改动；`core/adapter/app` 无 Spring 组件注解；端口无残留。
- 基线：未授权 commit；`outputs/baselines/M3/`（patch + 未跟踪副本 + 18 个交付文件 SHA-256 全 OK、聚合 `9ed731c0…`）为 S2 比较基线。M2-P2-01（死代码）、M2-P2-02（`planDiagnosis` 无测试）已随 M3 交付清理/移除，视为关闭。

## 5. 未解决的风险与限制

- **§4.8 安全映射已在 M3 实现并实测**：28 项白名单过滤、`PROMPT_FRAGMENT`/合成路径经真实 GET 不回显、白名单停止码原样返回、Core 内部报告保留原值。残余注意点：过滤只在应用层公开视图，Core 仍会把模型自由文本写进内部 `stopReason`/`diagnosisSummary`——属冻结设计（Core 零改动），新增公开字段时必须过 `SafePublicFields`/`RunEventViews`。
- **在线一律 `BLOCKED_BY_ENV`**：未检测到 `DASHSCOPE_API_KEY`（只判断存在性，未读取值），未执行 `verify_online.py` 或任何真实外部模型调用；在线 Router 仅验证「缺配置 → 503」与本机替身适配路径，真实中文模型能力 **NOT_VERIFIED**，历史在线结论仅作 `[HISTORICAL]`。
- **`HypothesisStatus.SUPPORTED` 在主源码中从未被赋值**（实际只有 `OPEN`/`INVALIDATED`）→ 不得宣称假设驱动 O-H-E-D 闭环；已登记 S2。
- 未解决 P2：**6**（延期）——`M1-P2-01` 空白 `assetId:" "` 被当作未提供，应 400；`M1-P2-02` `RouterUnavailableHttpTest` 走「不支持模式」而非 qwen/openai 缺配置路径；`M3-P2-01` 安全负例为手工拼装视图而非真实 GET 端点（Reviewer 已补 GET 级证据）；`M3-P2-03` `NotImplementedException` 及其 501 handler 为无抛出方残留死代码；`M3-P2-04` 存储上限在无可清理项时可超限；`M3-P2-05` `ConversationSession.activeDiagnosisId` 无消费方。
- 会话/运行为内存态：重启丢失，不宣称长期记忆；`sessionId` 非认证，仅 loopback Demo。
- M2 遗留语义点（仍有效）：① `QueryAnswer.answer` 为模板化陈述，不含分析结论；② `QueryService` 对未登记资产直接抛仓储 `QueryException`（路由层已拦截）；`MAINTENANCE_HISTORY` 查询必须 `componentId=null`；空结果明确「没有此类模拟记录」，不得编造。

## 6. 下一里程碑交接卡（开发者开始工作时首先读取）

```text
MILESTONE_ID: 未定（S2；开工第一步先做 S2 里程碑拆分规划，再建编号，不得直接编码）
STATUS: TODO（M0–M3 = DONE，S1 已关闭 2026-11-08；开工时按规约置 IN_PROGRESS 并记录基线 Git 状态）
REVIEW_GATE: S2 首个里程碑完成后走独立 Review；未 Review PASS 不得推进
PHASE: S2（多轮会话、状态持久化/补证/暂停恢复、Hypothesis 调查闭环，范围见 task.md §7）
PRECONDITION: M3 = DONE（reviews/M3.md 修复复审 PASS，2026-11-08）；spec.md = FROZEN
BASELINE: 无 M3 checkpoint commit（未授权）。S2 比较基线 = git diff 0dff7c9 -- src README.md；
          对照副本 outputs/baselines/M3/（M3-delivery.patch + 未跟踪文件副本 +
          18 个交付文件 SHA-256 全 OK）。core/adapter/app/pom/datasets/skills/scripts 自 0dff7c9 零改动。
READ_FIRST（本轮只需这些，不要全仓扫描）:
  1. docs/DeviceTruth_V0.2_S0_S1/state.md（本文件，特别是 §4.7 与 §5）
  2. docs/DeviceTruth_V0.2_S0_S1/task.md §7（S2 范围与禁止提前实现项）
  3. docs/DeviceTruth_V0.2_S0_S1/spec.md §9/§10（[S2] 登记条目、冻结变更流程）
  4. docs/DeviceTruth_V0.2_S0_S1/reviews/M3.md（S1 出口契约与遗留 P2）
  5. docs/DeviceTruth_V0.2_S0_S1/S1_ACCEPTANCE_REPORT.md（S1 最终能力边界）
  6. src/main/java/io/devicetruth/application/{DiagnosisRunService,ConversationFlowService,RunEventViews}.java
     （S1 出口：异步运行、会话流、事件白名单视图）
  7. src/main/java/io/devicetruth/core/{runtime/TaskOrchestrator,state/StateManager,
     decision/DecisionGuard}.java（S2 要扩展的 Core 面；改动须先经用户批准并登记 spec §10）
GOAL（方向，具体以 S2 拆分规划为准）:
  多轮诊断会话与补证恢复；候选 Hypothesis 的 SUPPORTED/INVALIDATED 因果闭环
  （当前 SUPPORTED 无赋值路径，是 S2 首要技术缺口）
MUST_PROVE（沿用 S1 门禁）:
  - 离线全量构建/CLI 回归不退化；core/adapter/app 改动逐项登记并经用户批准
  - 在线能力仍单独记录，无凭据时 BLOCKED_BY_ENV，不得宣称在线通过
DON'T:
  - 不冻结/改写 spec.md 技术结论（S2 契约需先拆分并经用户批准）
  - 不宣称 S3/S4 能力（双域 Skill、Vue）；不发起未经授权的 git 操作
```

## 7. 本文更新规则

每次 Codex Review `PASS` 后更新：里程碑状态、Review 文件路径、有效事实、遗留 P2/阻塞、下一个 `MILESTONE_ID` 和下一轮需读文件；然后在用户授权下建立本地 checkpoint。`CHANGES_REQUIRED` 时保持同一里程碑，不推进。**任何 Review 报告只证明其真实审查过的范围。**
