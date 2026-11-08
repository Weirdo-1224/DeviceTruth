# DeviceTruth V0.2｜跨会话状态快照（State）

> 此文件只记录**最新有效状态**；每个里程碑通过 Codex Review 后更新。不要复制聊天记录、完整测试日志或长篇 Review。

## 1. 当前总体状态

- 计划：S0 → S1；后续 S2 → S3 → S4，不调整。
- 当前阶段：**S1 进行中**（S0 已关闭；M0、M1、M2 均 DONE）。
- 当前里程碑：**M3（S1）= TODO**，仅在 M2 Review PASS 后启动（`reviews/M2.md` 首审 PASS，未解决 P0=0/P1=0，新增 P2=2 延期；连同 M1 延期共 4 项 P2）。
- 下一里程碑：**M3 之后为 S2**，不在 S1 范围内规划。
- 文档规格状态：**FROZEN**（M0 Review #2 PASS 后转入；此后改规格须走 `spec.md` §10 并经用户批准）。
- 当前开发基线 commit：**M2 checkpoint `0dff7c9`**（subject：`feat: M2 事实查询与诊断请求映射通过 Review 并交接至 M3`，用户已授权建立；未 push、无 tag、无 stash）。M3 的 diff 基线 = `git diff 0dff7c9..HEAD`（工作树已干净）；对照快照另存于 `outputs/baselines/M2/`（18 个交付文件 SHA-256 全 OK、聚合 `6282c5f2…`）。
- 工作树状态（M3 开工前）：M2 交付已提交为 checkpoint `0dff7c9`，`git status` 干净；`target/`、`outputs/runs` 为运行产物，不纳交付；根目录 m1-*/m2-*.log 已清理（证据已收敛进 reviews/task）。
- 是否完成 S1：**否**（M0/M1/M2 DONE；M3 未开始）。

## 2. 里程碑进度

| ID | 状态 | Review | 交付摘要 |
|---|---|---|---|
| M0 | **DONE** | `reviews/M0.md`（#1 CHANGES_REQUIRED → #2 PASS） | `S0_AUDIT.md` 审计 + `spec.md` 冻结 + Review P1/P2 修复 |
| M1 | **DONE** | `reviews/M1.md`（#1 CHANGES_REQUIRED → #2 CHANGES_REQUIRED → **#3 PASS**） | 双入口 + Server/健康端点 + Catalog + Session + 四类路由（QUERY/DIAGNOSE 暂为明确 501）；完整 token 边界资产校验（CAR-ZZ/CAR-B2/CAR-B-UNKNOWN 不再回落 CAR-B，规则/证据 ID 豁免） |
| M2 | **DONE** | `reviews/M2.md`（首审 **PASS**） | QueryService（仅 ASSET_PROFILE/MAINTENANCE_HISTORY，真实 profile/history + 真实 sources）；DiagnosisGoal→DiagnosisRequest（Catalog 固定 asOf + 规则存在性）；QUERY 返回 200 真实结果，DIAGNOSE 仍明确 501 |
| M3 | TODO | 未执行 | 待异步 Run/Events + S1 最终验收 |

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

## 5. 未解决的风险与限制

- **`spec.md` §4.8 安全映射只在契约层存在**（M0-P1-01 = CLOSED 指契约，不指实现）。Core 仍会把模型自由 `reason` 写进 `stopReason`/`diagnosisSummary`/`NEED-AGENT` 描述/`TERMINATED`。M3 必须实现 28 项白名单过滤并用“合成路径 + `PROMPT_FRAGMENT`”合法提案做负例，证明 GET 结果与 `/events` 不回显；Core 内部报告保留原值。
- **在线一律 `BLOCKED_BY_ENV`**：交接环境未检测到 `DASHSCOPE_API_KEY`（只判断存在性，未读取值），也未执行 `verify_online.py` 或任何真实 Qwen/OpenAI 兼容调用。M1 在线 Router 仅验证过“缺配置 → 503”与“本机替身完整适配路径”，真实中文模型能力 **NOT_VERIFIED**；历史在线结论仅作 `[HISTORICAL]`。
- **M2 已交付 QUERY，但 DIAGNOSE 仍为临时 `501` 测试态**：QUERY 已走真实只读仓储；DIAGNOSE 的 Goal→Request 映射已具备（`DiagnosisApplicationService` / `planDiagnosis`），但**尚未接入 Core**，HTTP 仍明确 501 且不写会话。M3 接上 `DiagnosisRunService` 后必须删除该 501 分支或提供测试证明不可达；不得把 501 当作诊断已实现。
- **`HypothesisStatus.SUPPORTED` 在主源码中从未被赋值**（实际只有 `OPEN`/`INVALIDATED`）→ 任何文档/答复不得宣称假设驱动 O-H-E-D 闭环；已登记 S2。
- 未解决 P2：**4**（延期，不阻塞 M3）——`M1-P2-01` 空白 `assetId:" "` 被当作未提供，应 400；`M1-P2-02` `RouterUnavailableHttpTest` 走「不支持模式」而非 qwen/openai 缺配置 `required` 路径；`M2-P2-01` 死代码（`ConversationSession.Interaction.queryAnswer` 无读取方、`ConversationFlowService.renderQueryReply` 无调用方，建议随 M3 清理）；`M2-P2-02` `planDiagnosis` 无交付内测试（行为已经 Reviewer 探针实测正确，M3 接入真实链路时补测）。以下为非 Review 项、但下一里程碑必须遵守的约束：M2 的 `MAINTENANCE_HISTORY` 查询必须 `componentId=null`（`JsonRepositories` 只按 `payload.subject` 过滤）；空结果明确说明“没有此类模拟记录”，不得编造；M3 队列拒绝须同时移除注册项，避免永久 `QUEUED`；`ExecutionStatus.FAILED` 不得包装成 `COMPLETED`。
- M2 遗留语义点（Review 已确认可接受）：① `QueryAnswer.answer` 由记录 `payload` 生成的说明文本是**模板化陈述**（含 subject/predicate/value/sourceType/effectiveAt），不含分析结论；② `QueryService` 对未登记资产直接抛仓储 `QueryException`（路由层已拦截，HTTP 不会触达），未在查询层再兜一次；③ `planDiagnosis` 目前只作为应用层 API 供 M3 复用，HTTP 未暴露。

## 6. 下一里程碑交接卡（开发者开始工作时首先读取）

```text
MILESTONE_ID: M3
STATUS: TODO（M2 = DONE；开工时先按规约置 IN_PROGRESS 并记录基线 Git 状态）
REVIEW_GATE: 只有 reviews/M3.md = PASS 后，才能把 M3 改为 DONE 并关闭 S1
PHASE: S1
PRECONDITION: M2 = DONE（reviews/M2.md PASS，2026-11-08），spec.md = FROZEN
BASELINE: M2 checkpoint commit 0dff7c9（用户已授权建立；未 push、无 tag）。
          M3 的 diff 基线 = git diff 0dff7c9..HEAD；工作树当前干净，
          对照副本 outputs/baselines/M2/（18 个交付文件 SHA-256 全 OK，聚合 6282c5f2…）。
READ_FIRST（本轮只需这些，不要全仓扫描）:
  1. docs/DeviceTruth_V0.2_S0_S1/state.md（本文件，特别是 §4.5 与 §5）
  2. docs/DeviceTruth_V0.2_S0_S1/task.md §5（M3 必做项 + M0 冻结补充）
  3. docs/DeviceTruth_V0.2_S0_S1/spec.md §4.4–§4.8、§6、§8（执行状态、三 ID、事件白名单、安全映射、上限）
  4. docs/DeviceTruth_V0.2_S0_S1/reviews/M2.md（Review 结论与遗留 P2）
  5. src/main/java/io/devicetruth/application/{ConversationFlowService,DiagnosisApplicationService,
     DiagnosisGoal,RouteValidator}.java（M2 出口：DIAGNOSE 的 501 与 planDiagnosis 在此）
  6. src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java 与
     src/main/java/io/devicetruth/adapter/RunReportWriter.java（RunReport 真实字段与报告目录）
  7. src/main/java/io/devicetruth/adapter/{OpenAiReasoner,QwenReasoner,StructuredReasoner}.java（Reasoner 作用域）
GOAL:
  有界异步 DiagnosisRunService + ReasonerFactory（每任务独立）+ 202/QUEUED/Location
  + GET 状态/结果 + 结束后白名单事件视图；ExecutionStatus.FAILED 不得包装成 COMPLETED
MUST_PROVE:
  - Core FAILED → API FAILED 且 errorCode 经 §4.8 白名单过滤
  - coreTaskId 只取 report.state().taskId()，reportPath 仅内部
  - 队列拒绝同时移除注册项，无永久 QUEUED 幽灵任务
  - 合成路径 + PROMPT_FRAGMENT 负例：GET/events 均不回显，白名单停止码原样返回
  - 接入后删除 DIAGNOSE 501 分支或测试证明不可达；顺手清理 M2-P2-01 死代码
DON'T:
  - 不改 Guard/Orchestrator/EvidenceManager，不新增第二套 Agent 编排
  - 不实现实时推流/中途补证，不宣称完整 Hypothesis O-H-E-D
  - 不伪造事件、诊断结论或在线模型能力；不发起未经授权的 git 操作
```

## 7. 本文更新规则

每次 Codex Review `PASS` 后更新：里程碑状态、Review 文件路径、有效事实、遗留 P2/阻塞、下一个 `MILESTONE_ID` 和下一轮需读文件；然后在用户授权下建立本地 checkpoint。`CHANGES_REQUIRED` 时保持同一里程碑，不推进。**任何 Review 报告只证明其真实审查过的范围。**
