# DeviceTruth V0.2｜跨会话状态快照（State）

> 此文件只记录**最新有效状态**；每个里程碑通过 Codex Review 后更新。不要复制聊天记录、完整测试日志或长篇 Review。

## 1. 当前总体状态

- 计划：S0 → S1；后续 S2 → S3 → S4，不调整。
- 当前阶段：**S1 进行中**（S0 已关闭；M0、M1 均 DONE）。
- 当前里程碑：**M2 = TODO**（M1 已由 Review #3 PASS 置 DONE，2026-11-08；`reviews/M1.md` §9，未解决 P0=0/P1=0，P2=2 延期）。M2 仅在完成 §6 交接后启动。
- 下一里程碑：**M3（S1）**，仍是 `TODO`，仅在 M2 Review PASS 后启动。
- 文档规格状态：**FROZEN**（M0 Review #2 PASS 后转入；此后改规格须走 `spec.md` §10 并经用户批准）。
- 当前开发基线 commit：仍是 **M0 checkpoint** `d4832de`（未 push、无 tag、无 stash）。M1 交付未获 commit 授权，**未建 checkpoint commit**；M1 基线快照保存在 `outputs/baselines/M1/`（`M1-delivery.patch`、`untracked/` 副本、`SHA256SUMS.txt` 33 个交付文件全 OK、聚合 SHA-256 `6abaca40…`（task/state 流程文档不入清单）），M2 以当前工作树 + 该快照为比较基线。
- 工作树状态（M1 交付 + M1.md 报告）：改 `pom.xml`、`README.md`、`docs/.../task.md`、`docs/.../state.md`；新增 `src/main/resources/application.properties`、`io/devicetruth/{server,api,application}/` 与对应测试、`reviews/M1.md`。`target/`、`outputs/`、`m1-*.log` 为运行产物，不纳交付。
- 是否完成 S1：**否**（M1 DONE；M2/M3 未开始）。

## 2. 里程碑进度

| ID | 状态 | Review | 交付摘要 |
|---|---|---|---|
| M0 | **DONE** | `reviews/M0.md`（#1 CHANGES_REQUIRED → #2 PASS） | `S0_AUDIT.md` 审计 + `spec.md` 冻结 + Review P1/P2 修复 |
| M1 | **DONE** | `reviews/M1.md`（#1 CHANGES_REQUIRED → #2 CHANGES_REQUIRED → **#3 PASS**） | 双入口 + Server/健康端点 + Catalog + Session + 四类路由（QUERY/DIAGNOSE 暂为明确 501）；完整 token 边界资产校验（CAR-ZZ/CAR-B2/CAR-B-UNKNOWN 不再回落 CAR-B，规则/证据 ID 豁免） |
| M2 | TODO | 未执行 | 待 Query/Goal→Core |
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

### 4.1 M1 交付已验证事实（2026-11-08，Review 未执行）

- `mvn -q clean package` 退出 0；20 个测试类 **88 项**，0 失败/0 错误/0 跳过（M0 基线 42 + 新增 46）。
- `META-INF/MANIFEST.MF`：`Main-Class=org.springframework.boot.loader.launch.JarLauncher`、`Start-Class=io.devicetruth.app.DeviceTruthApplication`。
- Server 真实启动（显式 classpath，见 task.md §3.3）：`Tomcat started on port 8080 (http)`；`GET /api/health` → `200 {"status":"UP","dataNature":"SIMULATED"}`；验证后进程已停止、端口已释放。
- 四路由实测：CLARIFY/UNSUPPORTED → `200`；QUERY/DIAGNOSE → `501 NOT_IMPLEMENTED` 且不写消息；`SN-0245` 不被映射为 CAR-B。
- `spring-boot:run -Dspring-boot.run.main-class=...` 在本项目 3.5.16 下被 pom 显式 `mainClass` 覆盖，实际启动 CLI；不能作为 Server 启动命令写入文档。
- CLI 回归：A/B/C/D 退出 0（7/7/7/6）；`python scripts/verify_cli.py` 退出 0。

### 4.2 M1 首审 P1 修复已验证事实（2026-11-08，待复审）

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
- M1 基线快照：`outputs/baselines/M1/`（`SHA256SUMS.txt` 33 个交付文件全 OK，聚合 `6abaca40…`；task/state 流程文档随交接更新不入清单）；M2 比较基线 = 当前工作树 + 该快照（checkpoint commit 仍为 `d4832de`）。

## 5. 未解决的风险与限制

- **`spec.md` §4.8 安全映射只在契约层存在**（M0-P1-01 = CLOSED 指契约，不指实现）。Core 仍会把模型自由 `reason` 写进 `stopReason`/`diagnosisSummary`/`NEED-AGENT` 描述/`TERMINATED`。M3 必须实现 28 项白名单过滤并用“合成路径 + `PROMPT_FRAGMENT`”合法提案做负例，证明 GET 结果与 `/events` 不回显；Core 内部报告保留原值。
- **在线一律 `BLOCKED_BY_ENV`**：交接环境未检测到 `DASHSCOPE_API_KEY`（只判断存在性，未读取值），也未执行 `verify_online.py` 或任何真实 Qwen/OpenAI 兼容调用。M1 在线 Router 仅验证过“缺配置 → 503”与“本机替身完整适配路径”，真实中文模型能力 **NOT_VERIFIED**；历史在线结论仅作 `[HISTORICAL]`。
- **M1 的 QUERY/DIAGNOSE 仍为临时 `501` 测试态**：M2/M3 完成后必须删除该分支或提供测试证明不可达；不得把 501 当作查询/诊断已实现。
- **`HypothesisStatus.SUPPORTED` 在主源码中从未被赋值**（实际只有 `OPEN`/`INVALIDATED`）→ 任何文档/答复不得宣称假设驱动 O-H-E-D 闭环；已登记 S2。
- 未解决 P2：**2**（`M1-P2-01` 空白 `assetId:" "` 被当作未提供，应 400；`M1-P2-02` `RouterUnavailableHttpTest` 走「不支持模式」而非 qwen/openai 缺配置 `required` 路径）。按用户指令延期，不阻塞 M2。以下为非 Review 项、但下一里程碑必须遵守的约束：M2 的 `MAINTENANCE_HISTORY` 查询必须 `componentId=null`（`JsonRepositories` 只按 `payload.subject` 过滤）；空结果明确说明“没有此类模拟记录”，不得编造；M3 队列拒绝须同时移除注册项，避免永久 `QUEUED`；`ExecutionStatus.FAILED` 不得包装成 `COMPLETED`。
- 工作树在 M2 开始时：M1 交付（含 `reviews/M1.md`）在工作树未提交，基线快照见 `outputs/baselines/M1/`。M2 的 diff 对照 = 当前工作树状态 + 快照哈希（不要再只用 `git diff d4832de` 圈 M0→M1 的全部改动）。

## 6. 下一里程碑交接卡（开发者开始工作时首先读取）

```text
MILESTONE_ID: M2
STATUS: TODO（M1 = DONE，Review #3 PASS；完成本交接后由开发者置 IN_PROGRESS）
REVIEW_GATE: 只有 reviews/M2.md = PASS 后，才能把 M2 改为 DONE 并启动 M3
PHASE: S1
PRECONDITION: M1 = DONE（reviews/M1.md §9 PASS），spec.md = FROZEN
BASELINE: 当前工作树未提交（M1 交付 + reviews/M1.md），checkpoint commit 仍为 d4832de
          比较基线 = 工作树现状 + outputs/baselines/M1/ 快照
          （M1-delivery.patch、untracked/ 31 文件副本、SHA256SUMS.txt 33 个交付文件全 OK、
          聚合 SHA-256 6abaca40…（task/state 流程文档随交接更新不入清单），可 sha256sum -c 自校验；只认 LF 版清单）
          不要只用 git diff d4832de 圈 M2 交付（那包含 M0→M1 的全部改动）
READ_FIRST（本轮只需这些，不要全仓扫描）:
  1. docs/DeviceTruth_V0.2_S0_S1/state.md（本文件，特别是 §4.3 与 §5 约束）
  2. docs/DeviceTruth_V0.2_S0_S1/task.md §4（M2 必做项 + M0 冻结补充）
  3. docs/DeviceTruth_V0.2_S0_S1/spec.md §4/§5（§4.3 双时间、§4.4 应用层对象、§5.2 QueryService）
  4. docs/DeviceTruth_V0.2_S0_S1/reviews/M1.md §9（M1 通过事实与遗留）
  5. src/main/java/io/devicetruth/application/{RouteValidator,RouteDecision,QueryKind,
     ConversationFlowService}.java（M1 路由出口，M2 的 QUERY 分支在此接真实实现）
  6. src/main/java/io/devicetruth/core/capability/Repositories.java 与
     src/main/java/io/devicetruth/adapter/JsonRepositories.java（profile/history/search/query 真实签名）
  7. src/main/java/io/devicetruth/core/contract/Models.java（DiagnosisRequest/EvidenceRecord 字段）
  8. datasets/fixtures/*/asset.json（FACTORY/REPLACEMENT 记录的真实字段与 evidenceId/sourceRef/sourceType）
GOAL:
  QueryService（仅 ASSET_PROFILE / MAINTENANCE_HISTORY，走真实 profile/history）
  + 来源与资产一致性校验 + DiagnosisGoal/旧 DiagnosisRequest 映射
  （QUERY 路由返回 200 真实查询结果，替换 M1 的临时 501 之一；DIAGNOSE 仍只验证 Goal→Request）
MUST_PROVE:
  - 事实查询带 evidenceId/sourceRef/sourceType 且 dataNature=SIMULATED
  - 不使用 CapabilityGateway 的完整诊断 Scope 冒充事实查询
  - 诊断仍不得伪造已执行（M3 前只验证 Goal→Request）
DON'T:
  - 不为事实查询伪造 DiagnosisRequest；不从出厂记录推断当前配置
  - 不提前实现 M3 的异步 Run/事件
  - 不改 Guard/Orchestrator/EvidenceManager，不新增第二套 Agent 编排
  - 不伪造 Repository 方法，不放宽扫描范围，core/adapter/app 不加 Spring 组件注解
  - 不宣称已完成在线模型验收或 S1 完成
  - 不发起未经授权的 git reset/clean/commit/push/tag
```

### 6.1 M3 交接预告（仅 M2 Review PASS 后由交接步骤填充）

（占位：M2 PASS 前不展开 M3 交接卡，避免提前实现或误读范围。）

## 7. 本文更新规则

每次 Codex Review `PASS` 后更新：里程碑状态、Review 文件路径、有效事实、遗留 P2/阻塞、下一个 `MILESTONE_ID` 和下一轮需读文件；然后在用户授权下建立本地 checkpoint。`CHANGES_REQUIRED` 时保持同一里程碑，不推进。**任何 Review 报告只证明其真实审查过的范围。**
