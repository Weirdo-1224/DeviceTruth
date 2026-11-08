# DeviceTruth V0.2｜S0/S1 分会话实施任务（Task）

> 状态基线：M0 已由 Codex Review #2 判定 **PASS 并置 `DONE`**；M1 已由 Codex Review #3 判定 **PASS 并置 `DONE`**；M2 已由 Codex Review（`reviews/M2.md` 首审）判定 **PASS 并置 `DONE`**（均为 2026-11-08；未解决 P0=0/P1=0）。M3 仍为 `TODO`，仅在 M2 PASS 后启动。每次只推进一个里程碑；Review PASS 后才能改 DONE。原 R01~R07 要求保留为里程碑内部检查项，不再各自创建会话和 Review。M0 的真实命令、退出码与源码依据见 [S0_AUDIT.md](./S0_AUDIT.md) §2.3。
>
> 每个里程碑由开发者实施或完成文档审计，Codex 独立 Review。**不默认允许模型执行 commit/push/reset/clean。**

## 0. 状态和审查规约

状态只允许 `TODO / IN_PROGRESS / REVIEW_PENDING / CHANGES_REQUIRED / DONE / BLOCKED`。

- `TODO → IN_PROGRESS`：开始当前里程碑，记录基线 Git 状态。
- `IN_PROGRESS → REVIEW_PENDING`：开发者完成自测，交付具体文件/命令/结果，启动 Codex Review。
- `REVIEW_PENDING → DONE`：Codex 报告为 PASS，P0/P1 全部关闭；可记录 P2。
- `REVIEW_PENDING → CHANGES_REQUIRED`：P0/P1 需要修复，进入同里程碑修复和复审；**不开始下一里程碑**。
- `BLOCKED`：环境/事实障碍阻断关键验收，报告原因、证据和可替代验证；不得标 DONE。

每个里程碑结束仅更新当前状态、下一里程碑 ID、已验证事实和未解决问题；`state.md` 是唯一跨会话进度快照。`reviews/Mx.md` 是证据记录，不要在 `state.md` 复制全部内容。

## 1. 总任务看板

| ID | 阶段 | 里程碑范围 | 依赖 | 状态 | Codex 报告 |
|---|---|---|---|---|---|
| M0 | S0 | 原 R01+R02：基线审计 → 契约冻结 | 无 | **DONE**（2026-11-08，Review #2 PASS） | `reviews/M0.md`（#1 CHANGES_REQUIRED → #2 PASS） |
| M1 | S1 | 原 R03+R04：双 Main/Catalog/Session/Router | M0 PASS | **DONE**（2026-11-08，Review #3 PASS） | reviews/M1.md（#1 CHANGES_REQUIRED → #2 CHANGES_REQUIRED → **#3 PASS**） |
| M2 | S1 | 原 R05：QueryService + Goal→Core 请求映射 | M1 PASS | **DONE**（2026-11-08，Review 首审 PASS） | reviews/M2.md（PASS，P2×2 延期） |
| M3 | S1 | 原 R06+R07：异步运行/结果事件/最终验收 | M2 PASS | TODO | reviews/M3.md（未生成） |

### 通用开发边界

- 每个里程碑先验证前置里程碑确实为 DONE、`state.md` 和 Git 工作树一致；异常先记录，不自行假设已完成。
- 直接修改范围以当前里程碑为准；修改其他关联代码必须说明依赖和最小必要性。
- 跑实际测试并记录命令/退出码/日志位置，不能用“预期通过”替代。
- 开发过程中跑定向测试；里程碑交付前跑相关模块和必要旧回归；M3 跑干净全量构建与集成验收。
- Codex 必须审查从上一个 checkpoint 到当前交付的实际 diff、依赖路径和真实测试，不得仅依据开发者总结打 PASS。
- P0/P1 修复由开发者处理，Codex 复审；P2 登记不阻塞，除非影响安全或正确性。
- JDK、编译、离线测试、打包和 scripted CLI 是强制门禁；这些失败时必须 `BLOCKED`。在线 Key/网络/额度可以单独记录 `BLOCKED_BY_ENV`。

## 2. M0｜S0：基线审计与契约冻结

M0 在同一个连续工作周期内完成两个严格有序的子阶段：先核对事实，再依据事实冻结契约。前半段完成前不得修改 `spec.md` 的技术结论；M0 结束时只做一次独立 Review。

### 2.1 事实审计（原 R01）

**目标**：防止在不存在的方法、错误启动方式、历史测试假设上开发。仅只读审计 + 运行现有测试；**禁止写 Java 业务源码**。

**需要读取**：根 README/AGENTS（如存在）、pom.xml、V0.1 spec/task/state、当前真实源码与 datasets、原修订方案。

**必须完成**：

1. 记录工作树是否干净、分支、commit、tag、修改/未跟踪文件；不清理用户内容。
2. 核对 JDK/Maven 真实环境、依赖版本、CLI Main 的 `WebApplicationType.NONE`/Context 退出和现有打包配置。
3. 核对 `TaskOrchestrator.run()` 输入、返回、内部 StateManager、RunReport/result、事件生命周期。
4. 核对 Reasoner 各实现的可变状态及构造方式。
5. 核对 `AssetRepository/RuleRepository/CapabilityGateway` 真正签名、JSON fixture 与 scenes。
6. 核对 `DiagnosisRequest.asOf` 语义、CAR-B 证据有效期；列出现有 ActionProposal/Hypothesis/Guard 行为与已知缺口。
7. **实际执行** `java -version`、`mvn -version`、`mvn -q clean test`、`mvn -q package`，以真实 jar 文件/README 方式跑 CLI A/B/C/D（至少可核对真实命令与输出），E 只跑现有负例测试，不能伪造 `--scenario=E`。
8. 检查实际测试项数量，与“历史 42 项”区分；失败时写出可重现障碍。

**允许写**：`S0_AUDIT.md`，必要时写文档性审计笔记。禁止业务编码、直接改变既有技术契约。

**交付**：填充 `S0_AUDIT.md` 的源码表、命令、结果、风险和候选回退点（未授权不创建 commit）。

**内部检查点 A**：所有事实必须能追溯到真实文件/方法/行、真实命令/输出；审计报告不可把历史结论当今天的验证。JDK、编译、离线测试、打包或 scripted CLI 未验证时，M0 必须 `BLOCKED`，不得只记录 blocker 后继续冻结规格。

### 2.2 技术契约冻结（原 R02）

**目标**：将本里程碑前半段的审计事实用于修订并冻结规格；不写业务代码。重大架构取舍请用户确认。

**必须完成**：

1. 对 `spec.md` 的全部 `[HISTORICAL]`/待验证条目，以 M0 审计事实核对，修正不一致接口；不凭空制造 Repository 方法。
2. 冻结**独立 CLI / Server Main**启动与 Spring Boot Maven mainClass 选择方案，记可执行验证命令或 S1 必跑验证项。
3. 冻结 Catalog 的资产/症状/固定 asOf，区分 `requestedAt` 和 `diagnosisAsOf`。
4. 冻结 QUERY 两类只读 Repository 查询、不伪造 DiagnosisRequest；DIAGNOSE 必须 Catalog+Rule 双重校验。
5. 冻结 `diagnosisId/coreTaskId/reportPath`、`ExecutionStatus/Core RunStatus/Outcome`、POST 202/QUEUED、GET 最终事件、白名单 DTO。
6. 冻结每任务独立 Reasoner、离线/在线模型路径、线程池/缓存上限和 loopback/非鉴权边界。
7. **将 Hypothesis 因果调查缺口明确登记到 S2，不在 S1 实现**；保留 V0.1 Guard。
8. 对 `task.md/state.md` 与验收测试矩阵作必要契约调整，形成 M1 明确入口。

**允许写**：`spec.md/task.md/state.md` 和设计变更记录；不改业务源码。`spec.md` 在 Codex PASS、重大问题经用户裁决后由 DRAFT 变 FROZEN。

**M0 Codex Review**：同时复核审计事实的可追溯性，以及冻结方案与实际源码是否一致、能否实现、是否越界、是否错误宣称已有能力；重点复核所有“历史审查修订”的 18 条已知偏差。报告写入 `reviews/M0.md`。

**通过判据**：S0_AUDIT 可追溯；核心契约确切；S1 不需要靠猜测 API 开发；CLI 保护和测试可实施。此后每次改规格必须写变更说明并经用户批准。

### 2.3 M0 执行记录（2026-11-08，开发者自测完成；已由 Review #2 判定 DONE）

事实全部记录在 [S0_AUDIT.md](./S0_AUDIT.md)，此处只留最小索引。基线：`main` @ `14952b6`，工作树干净、无 tag、无 stash。

| 门禁 | 真实命令 | 退出码 | 结果 |
|---|---|---|---|
| Java | `java -version`（`JAVA_HOME=.tools/jdk-21.0.13.0.2+1`） | 0 | `21.0.13.0.2`（默认环境仍是 17，必须显式设置） |
| Maven | `mvn -version` | 0 | 3.9.9 / Java 21 / UTF-8 |
| 单测 | `mvn -q clean test` | 0 | **42 项，0 失败/0 错误/0 跳过** |
| 打包 | `mvn -q package` | 0 | `target/devicetruth-core-0.1.0.jar`（46,924,270 字节） |
| CLI A–D | `java -jar target/devicetruth-core-0.1.0.jar --scenario=<A/B/C/D> --reasoner=scripted` | 0×4 | A/B `RESOLVED`、C `CONFLICT`、D `NEED_EVIDENCE`（7/7/7/6 步） |
| 非法参数 | `--reasoner=invalid` | 2 | 报错并拒绝 |
| E 负例 | `mvn -q '-Dtest=AcceptanceTest#caseEUnknownToolAndForgedEvidenceNeverResolve' test` | 0 | 1 项通过；E 不是 CLI 场景 |
| 打包壳验收 | `python scripts/verify_cli.py` | 0 | A–D、缺 Key、私有配置本机 HTTP 全部 PASS |

冻结要点（详情见 `spec.md`）：双 Main 与 `mainClass` 显式固定（M1 必做必验）、Catalog 只从 `scenarios.json` 派生（A–D 同 `domain=automotive-demo`、同 `symptomCode=COMM_TIMEOUT`、同 `asOf=2026-11-03T04:00:00Z`）、症状别名白名单、`requestedAt`/`diagnosisAsOf` 分离、三 ID 分离（报告目录 UUID ≠ `taskId`）、`ExecutionStatus` 与 Core `RunStatus` 映射、源码识别 11 类事件的公开白名单视图（§4.8 安全映射，B 实跑触发其中 8 类）、QUERY 两类只读 Repository 查询、队列满 `429`、Router 不可用 `503`、并发与容量上限。

审计新发现（需 Review 关注）：`HypothesisStatus.SUPPORTED` 在主源码中**从未被赋值**（假设只有 OPEN/INVALIDATED），因此 S1 不得宣称假设驱动闭环；`pom.xml` 目前**没有** `mainClass` 配置，第二个 Main 出现前必须显式固定；`pom.xml` **没有** `spring-boot-starter-web`；主源码仅一处 Spring 注解，所有组件由手工 `new` 装配。

未执行且有明确原因的事项：`python scripts/verify_online.py` 与任何真实外部模型调用（本轮未触碰在线额度，历史在线结论只作 `[HISTORICAL]` 记录）；Server 启动命令无法在本轮验证（Server 类尚不存在，M1 必须实测）。

**复审轮次（2026-11-08，Codex Review #1 = CHANGES_REQUIRED）**：P0=0、P1=1（`M0-P1-01` 终止事件/`stopReason` 安全映射）、P2=1（`M0-P2-01` 事件类型措辞）。两项均已在文档层闭合：

- P1：用真实 Core 复现（合法 `ESCALATE`/`REQUEST_INFORMATION` 的 reason 进入 `stopReason`/`diagnosisSummary`/`NEED-AGENT` 描述/`TERMINATED.data`），随后在 `spec.md` 新增 §4.8——28 项显式停止码白名单、`STOP_REASON_WITHHELD`/`RUN_FAILED_WITHHELD_REASON`、缺口只给 `openNeeds`、`TERMINATED` 用过滤值；Core/Guard/Orchestrator 零改动，安全回归列为 M3 必测。
- P2：区分“源码识别 11 类”与“scripted B 实跑 8 类”，并给出其余 3 类的真实覆盖证据。
- 复现细节、命令与修复后门禁（`mvn -q clean test` 42/0/0/0、`mvn -q package`、`verify_cli.py`、E 定向测试、`Start-Class` 均为退出 0）见 `S0_AUDIT.md` §K。
- 首审当时状态为 **REVIEW_PENDING**，未标 DONE、未进入 M1（该状态已被下面的 #2 复审结论取代）。

### 2.4 M0 复审结果与里程碑关闭（2026-11-08）

- Codex Review #2（`reviews/M0.md` §8）判定 **PASS**：未解决 P0=0、P1=0、P2=0，未发现新增问题。`M0-P1-01`、`M0-P2-01` 均 CLOSED。
- 复审实际执行：`mvn -q '-Dtest=AcceptanceTest,RuntimeTest' test` 退出 0（9 项，0 失败/0 错误/0 跳过）；Python 只读核验 §4.8 白名单来源 `WHITELIST_PROVENANCE=PASS 5+8+15=28`；`git diff 14952b6 -- src pom.xml scripts datasets skills .mvn .vscode` 为空。首审的干净全量 42 项、package、`verify_cli.py`、CLI A–D 证据对相同代码继续有效（本轮未重跑，也不冒充重跑）。
- 复审结论限定在 M0 范围：`stopReason` 安全映射只在**契约层**闭合，HTTP 过滤实现与实测归 M3；PASS 不代表 S1 完成或在线模型通过。
- 未发现需要用户裁决的重大架构分歧，因此按 §2.2 约定把 `spec.md` 由 `DRAFT`/`FROZEN_CANDIDATE` 转为 **FROZEN**（变更表已记录）。
- 状态转换：`REVIEW_PENDING → DONE`。用户授权后已建立**本地 checkpoint commit**（不 push、不打 tag），M1 以该提交为比较基线；提交前的补丁与哈希副本见 `state.md` §6。

## 3. M1｜S1：HTTP 接入层

M1 连续完成双入口/Catalog 和 Session/Router；开发者不在两者之间切换会话或等待正式 Review，但必须先验证入口与 Catalog，再接会话和路由。

### 3.1 独立 Server Main 与演示 Catalog（原 R03）

**目标**：Web 可以持续提供 HTTP，同时原 CLI JAR 的 `java -jar` 不回退；Catalog 能对已存在的真实模拟 fixture 提供安全映射。

**必须实现**：

1. 只添加必要 Spring Web 依赖，新建独立 `DeviceTruthServerApplication`，不得复用 CLI 的单次关闭逻辑。
2. Maven 插件显式确定**原 CLI 为默认 fat JAR Main**；通过实际执行确认 CLI 仍会退出。
3. Server 用在当前项目上**已真实验证**的命令启动，绑定 loopback（开发配置）；验证健康检查（或稳定端点）以及持续运行。
4. `SupportedScenarioCatalog` 从真实 `scenarios.json` 和已核实配置得到 `assetId/domain/symptomCode/diagnosisAsOf`；不可虚构 `domainOf/listSymptoms`。
5. 未知 SN-0245 拒绝映射到 CAR-B；CAR-B 的模拟业务时间一致。

**建议修改**：`pom.xml`、server 配置、Catalog 模块、相关 tests、README；可能需要依赖装配最小调整。

**测试/验收**：CLI A/B/C/D/原测试回归、Server 稳定运行、Catalog 已知/未知映射及 CAR-B 时间正确。对应 TC-08/TC-13/TC-18/TC-19 的相关部分。

**内部检查点 A**：双 Main 生命周期、扫描范围、jar mainClass、真实启动日志、可回退性；不为了 Web 改坏原 CLI。通过后继续本里程碑的 Session/Router，不单独生成 Review 报告。

**M0 冻结补充（M1 必须遵守）**：

- `pom.xml` 的 `spring-boot-maven-plugin` 必须补 `<configuration><mainClass>io.devicetruth.app.DeviceTruthApplication</mainClass></configuration>`，并重新打包核对 `META-INF/MANIFEST.MF` 的 `Start-Class`。
- 新增代码只落在 `io.devicetruth.server`、`io.devicetruth.api`、`io.devicetruth.application`；`core/`、`adapter/`、`app/` 不得新增任何 Spring 组件注解（现网事实：全主源码只有一处 `@SpringBootApplication`）。
- Catalog 只从打包资源 `datasets/scenarios.json` 派生，不得手工复制第二份资产表，也不得新增 `domainOf/listAssets/listSymptoms`。
- Server 启动命令与健康端点必须在 M1 真实跑通并记录日志/端口；未验证前不得写入“已通过”。

### 3.2 会话与四类路由（原 R04）

**目标**：通过会话 API 接收中文消息，程序验证四种路由，不接入完整故障调查。

**必须实现**：

1. `POST /api/sessions` 返回 `201` 和合法 UUID；`GET /api/sessions/{id}/messages` 可读。
2. 有界线程安全会话存储；资产上下文只复用已明确且经 Catalog 核实的 assetId；`sessionId` 非认证。
3. `POST /api/sessions/{id}/messages` 的 RouteDecision 仅 `QUERY/DIAGNOSE/CLARIFY/UNSUPPORTED`；结构化 DTO、入参验证、模型输出程序校验。
4. 离线受限 Stub/确定性 Router 和在线真实 LLM Router 分离；在线配置只能服务端设置；不可冒充离线路由为中文模型理解。
5. 未指定“这台设备”且无上下文→CLARIFY；不支持设备/危险请求→UNSUPPORTED；不能编造 Controller 答案。
6. 与 M2/M3 尚未实现的处理路径连接时可以返回明确的 `501/NOT_IMPLEMENTED` 测试态，不得用 `200` 或伪造 DTO 冒充真实 QUERY/DIAGNOSE 结果；M2/M3 完成后必须有测试证明临时分支已删除或不可达。

**测试/验收**：`201/200`、四路由、上下文、非法 ID、模型不可用、容量限制、session 隔离；在线路由须实际可用才标通过。对应 TC-01/TC-07/TC-08/TC-09/TC-19/TC-20 的相关部分。

**M0 冻结补充（M1 必须遵守）**：

- 症状别名只允许 `spec.md` §4.2 的白名单命中 `COMM_TIMEOUT`，且必须再经 `RuleRepository.query` 确认有规则；其它表述不得默认映射。
- 会话/消息容量超限 → `429 SESSION_CAPACITY_EXCEEDED`；诊断队列满 → `429 RUN_CAPACITY_EXCEEDED`；Router 依赖模型不可用 → `503 ROUTER_UNAVAILABLE`；归属不符/不存在 → `404 NOT_FOUND`。
- 客户端不得提交 `reasoner/provider/apiKey/baseUrl/model`；模式只由服务端属性决定，默认 `offline`+`scripted`。
- 会话/消息必须线程安全且有界（默认 `maxSessions=100`、`maxMessagesPerSession=40`、单条 ≤ 2000 字符）。

**M1 Codex Review**：同时复核双 Main、Catalog、Session 和 Router 的实际 diff；确认 Router 只是分类而不是另一套 Agent，Catalog 边界、会话线程安全、未知/缺失信息准确处理。报告写入 `reviews/M1.md`。

### 3.3 M1 执行记录（2026-11-08，Review 待执行）

状态：`REVIEW_PENDING`。以下为开发者自测事实，不等同 Codex Review PASS。

- **双入口**：`pom.xml` 显式 `<mainClass>io.devicetruth.app.DeviceTruthApplication</mainClass>`；`mvn -q clean package` 退出 0，JAR 的 `META-INF/MANIFEST.MF` 为 `Main-Class: org.springframework.boot.loader.launch.JarLauncher`、`Start-Class: io.devicetruth.app.DeviceTruthApplication`。
- **全量测试**：`mvn -q clean package`（含 test 阶段）退出 0，20 个测试类 **88 项**，0 失败/0 错误/0 跳过（M0 基线 42 + M1 新增 46）。
- **Server 启动**：`spring-boot:run` 的 `-Dspring-boot.run.main-class` 在本项目 3.5.16 插件下被 `pom.xml` 显式 `mainClass` 覆盖，实测启动成 CLI；按 `spec.md` §7 备用方案使用显式 classpath：
  `mvn -q dependency:build-classpath '-Dmdep.outputFile=target/server-cp.txt'`，再 `java -cp "target/classes;$(cat target/server-cp.txt)" io.devicetruth.server.DeviceTruthServerApplication`。
  日志确认 `Tomcat started on port 8080 (http)`；`GET http://127.0.0.1:8080/api/health` → `200 {"status":"UP","dataNature":"SIMULATED"}`；验证后已停止进程并确认端口释放。
- **四路由实测**（UTF-8 请求体）：CLARIFY（缺设备/缺症状）`200`；UNSUPPORTED（`SN-0245`、危险操作）`200`；QUERY 与 DIAGNOSE 明确 `501 NOT_IMPLEMENTED`；501 路径不写入会话消息。`400/404/429/503` 由 HTTP 集成测试覆盖。
- **Catalog**：只从打包 `datasets/scenarios.json` 派生 CAR-A/B/C/D；`SN-0245` → `UNSUPPORTED_ASSET`；`diagnosisAsOf=2026-11-03T04:00:00Z`；未知资产不会复用 CAR-B。
- **在线 Router**：仅验证“服务端缺少模型配置 → `503 ROUTER_UNAVAILABLE`”契约，未做真实外部模型调用。在线验收仍为未执行，不得记为通过。
- **CLI 回归**：A/B/C/D 退出 0（7/7/7/6；A/B `RESOLVED`、C `CONFLICT`、D `NEED_EVIDENCE`）；`python scripts/verify_cli.py` 退出 0（PASS）。
- **遗留**：QUERY/DIAGNOSE 的临时 `501` 分支须在 M2/M3 完成后删除或证明不可达；`reviews/M1.md` 首审已生成。

### 3.4 M1 首审修复记录（2026-11-08，Codex Review #1 = CHANGES_REQUIRED → 待复审）

Codex `reviews/M1.md` 首审结论：**CHANGES_REQUIRED**，P0=0、**P1=4**、P2=2。本轮只修 4 个 P1，不扩大为架构重构、不改冻结 `spec.md`、不动 `core/` 与 `DecisionGuard`。

| ID | 根因（首审定位） | 最小修复 | 复现证据（真实 HTTP，修复后） |
|---|---|---|---|
| M1-P1-01 | `RouteValidator.resolveAsset` 无文本资产时直接采用模型 `suggestedAssetId`，且优先于会话上下文 | 删除 `suggestedAssetId` 入参，资产只由「消息明确编号 / 请求 hint / 已确认上下文」确定；模型候选只在 `validateSuggestion` 做一致性校验，不一致 → `CLARIFY ASSET_CONFLICT`，无可信资产 → `CLARIFY ASSET_MISSING` | `ONLINE no-asset` → `200 CLARIFY`（原 501）；`ONLINE override` → `200 CLARIFY`，`assetId=None`（原错误绑定 `CAR-C`） |
| M1-P1-02 | 正则 `[A-Z]{2,}-[0-9]{2,}` 漏 `CAR-Z` 并回落上下文 B；`catalogAssetInText` 命中即返回，多资产无歧义检测 | 正则改为 `(?i)(?<![A-Za-z0-9-])[A-Z]{2,}-(?:[A-Z]|[0-9]{2,})(?![A-Za-z0-9-])`（识别 `CAR-Z`，排除 `DEMO-D-120A` 三段式）；`catalogAssetsInText` 返回全集 `Set`，>1 → `CLARIFY ASSET_AMBIGUOUS` | `OFFLINE CAR-Z after bind` → `200 UNSUPPORTED`（原 501）；`OFFLINE multi` → `200 CLARIFY`，`assetId=None`（原 501） |
| M1-P1-03 | 危险词检测只在 `DeterministicTaskRouter`，在线模型建议 DIAGNOSE 时程序不复核 | 危险词表移入 `RouteValidator.mentionsDangerousOperation`，`validateSuggestion` 首行统一复核，两种模式共用；确定顺序「危险 → 模型 UNSUPPORTED → 资产 → 症状/规则」 | `ONLINE dangerous`（`帮我重启 CAR-B 通信超时`）→ `200 UNSUPPORTED`（原 501） |
| M1-P1-04 | 先改资产再两次 `append`，429 时留下半条 USER 与新资产；逐方法 `synchronized` 不保证交互原子 | `ConversationSession.commitInteraction`：同一把锁内 `messages.size()+2 > max` 预检 → 绑定资产 → 追加 USER+SYSTEM；`ConversationFlowService` 只调用这一次 | `max-messages=3` 下 429 后 `count: 2 roles [USER, SYSTEM] leak: False`（原 3 条含半条 USER） |

修复后门禁（本轮真实执行）：

| 门禁 | 真实命令 | 退出码 | 结果 |
|---|---|---|---|
| 全量测试 | `mvn -q clean package` | 0 | **21 个测试类 101 项，0 失败/0 错误/0 跳过**（原 88 + 新增 13） |
| 打包 | 同上 | 0 | `Start-Class=io.devicetruth.app.DeviceTruthApplication` |
| CLI A–D | `java -jar target/devicetruth-core-0.1.0.jar --scenario=<A/B/C/D> --reasoner=scripted` | 0×4 | 7/7/7/6；RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE |
| 打包核验 | `python scripts/verify_cli.py` | 0 | 4 项 PASS |
| 真实 HTTP 探针 | `target/review-fix-probe.py`（3 个真实 Server + 本机模型替身，不入交付） | 0 | 4 个 P1 全部翻转；前后健康均 200 |

新增/修改测试（13 项）：`TaskRouterTest` +2（CAR-Z 有上下文仍 UNSUPPORTED、多资产 ASSET_AMBIGUOUS）、`LlmTaskRouterTest` +5（模型不能授权资产/不能覆盖上下文/不能替换未知资产/危险请求拒绝/CAR-Z 拒绝）、`ConversationServiceTest` +3（拒绝时资产与消息不变、满容量不改上下文、20 并发配对提交）、`ServerCapacityHttpTest` 改 1（429 后仍 2 条）、`ServerHttpTest` +2（CAR-Z→UNSUPPORTED、多资产→CLARIFY）、新增 `ServerAtomicityHttpTest`（奇数容量）。

未修 P2（按用户指令延期，不阻塞）：`M1-P2-01`（`assetId:" "` 空白被当作未提供，应 400）、`M1-P2-02`（`RouterUnavailableHttpTest` 走的是「不支持模式」而非 qwen/openai 缺配置 `required` 路径）。

### 3.5 M1 Review #2 修复记录（2026-11-08，M1-P1-02 同根残留 → 待独立复审）

Codex `reviews/M1.md` §8 结论：**CHANGES_REQUIRED**，P0=0、**P1=1（M1-P1-02 同根残留）**、P2=2（延期）。本轮只修这一项，沿用原问题编号，不另建问题、不改 `spec.md`、不动 `core/`、`adapter/`、`app/`、Guard、Gateway、证据算法、`pom.xml` 和运行配置。

**共同根因**：`RouteValidator` 内存在两套互不一致的编号边界——`EXPLICIT_ASSET`（单字母或 ≥2 位数字后缀）漏掉 `CAR-ZZ`/`CAR-B2`，漏检后回落到会话中的 CAR-B；`catalogAssetsInText` 的 `(?![A-Za-z0-9])` 不把连字符算作编号的一部分，把 `CAR-B-UNKNOWN` 的前缀 `CAR-B` 当成已登记资产。

**最小修复**（`application/RouteValidator.java`）：

1. 用唯一 `ASSET_TOKEN` 正则（`(?<![A-Za-z0-9_-])[A-Za-z]{2,}(?:-[A-Za-z0-9]+)+(?![A-Za-z0-9_-])`）提取完整编号，`assetTokensInText` 是唯一入口，`resolveAsset` 不再有第二套匹配规则。
2. Catalog 命中改为大小写不敏感的**精确**匹配（`findAsset`），不做前缀/子串匹配；因此 `CAR-B-UNKNOWN` 不再被截成 `CAR-B`。
3. 未登记判定：文本出现完整编号且不属于 Catalog 即 `UNSUPPORTED_ASSET`，不再回落 `currentAssetId`。
4. 为不误伤相关表达，已登记的规则/证据 ID（如 `DEMO-D-120A`、`CAR-B-FIELD-1`）通过 `isKnownNonAssetId` 豁免。ID 只来自现有只读仓储：按 Catalog 的 `domain/symptomCode` 调 `RuleRepository.query`，按已登记资产取 `AssetRepository.profile` 和 `EvidenceRepository.search`；未扩充 Catalog、未新增数据源、未改 adapter。
5. 相应删除已无调用方的 `catalogAssetsInText`/`mentionsUnregisteredAsset`；`RouteValidator` 构造器新增 `AssetRepository`/`EvidenceRepository`，`ServerConfiguration` 仍传入同一个 `JsonRepositories` 实例。

修复前后真实 HTTP 实测（UTF-8，请求只含 `content`）：

| 案例 | 修复前 | 修复后 |
|---|---|---|
| 有 CAR-B 上下文 + `CAR-ZZ 帮我看看` | 200 CLARIFY，assetId=CAR-B | **200 UNSUPPORTED，assetId=null** |
| 有 CAR-B 上下文 + `CAR-B2 帮我看看` | 200 CLARIFY，assetId=CAR-B | **200 UNSUPPORTED，assetId=null** |
| 全新会话 + `CAR-B-UNKNOWN 帮我看看` | 200 CLARIFY，assetId=CAR-B | **200 UNSUPPORTED，assetId=null** |
| 在线（本机替身建议 DIAGNOSE/CAR-B）+ `CAR-B-UNKNOWN 通信超时` | 501 NOT_IMPLEMENTED | **200 UNSUPPORTED，assetId=null** |

保留行为回归（同轮真实 HTTP）：`CAR-C 帮我看看` → CLARIFY/assetId=CAR-C；`这台设备 通信超时`（有 B 上下文）→ 501（进入诊断，资产仍为 B）；多资产 → CLARIFY/assetId=null；文本 B + hint C → CLARIFY/assetId=null；`SN-0245` → UNSUPPORTED；`CAR-C 通信超时，参考 DEMO-D-120A` → 501（规则 ID 未被当成未知资产）；`CAR-B-FIELD-1 这条证据可靠吗` → CLARIFY/assetId=null（**未**被截成 CAR-B）；`car-b 帮我看看` → CLARIFY/assetId=CAR-B；拒绝 `CAR-ZZ` 后仍复用 CAR-B（501）。

修复后门禁（本轮真实执行）：

| 门禁 | 真实命令 | 退出码 | 结果 |
|---|---|---|---|
| 定向测试 | `mvn -o '-Dtest=TaskRouterTest,LlmTaskRouterTest,ConversationServiceTest,SupportedScenarioCatalogTest,SymptomResolverTest' -Dsurefire.useFile=false test` | 0 | 46 项，0 失败 |
| 全量测试与打包 | `mvn -q -o clean package` | 0 | **21 个测试类 109 项，0 失败/0 错误/0 跳过**（原 101 + 新增 8） |
| CLI 核验 | `python scripts/verify_cli.py` | 0 | A/B/C/D 仍为 RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE，7/7/7/6；4 项 PASS |
| 真实 HTTP 探针 | `target/p1-02-probe.py`（真实 Server + 本机模型替身，不入交付） | 0 | 4 个案例全部翻转；上述保留行为回归通过 |

新增测试（8 项）：`TaskRouterTest` +5（无上下文完整未知编号拒绝、有上下文不回落、完整编号提取不被截短、规则/证据 ID 不误伤、拒绝后已知资产/多资产/上下文复用不变）、`LlmTaskRouterTest` +3（模型建议绕过完整编号的无上下文/有上下文负例、规则/证据 ID 不变成资产且模型冲突只澄清）。

**未验证**：外部真实在线模型与 `verify_online.py`（本机替身不记在线 PASS）；P2 按指令延期。

### 3.6 M1 复审结果与里程碑关闭（2026-11-08）

- Codex Review #3（`reviews/M1.md` §9）判定 **PASS**：未解决 P0=0、P1=0、P2=2（`M1-P2-01` 空白 `assetId` 应 400、`M1-P2-02` RouterUnavailableHttpTest 走的非缺配置路径；均经用户指令延期）。`M1-P1-01/02/03/04` 全部 CLOSED。
- 复审实际执行：`mvn -q -o clean package` 退出 0（**21 类 109 项，0 失败/0 错误/0 跳过**）；`Start-Class` 核对为 CLI 主类；`python scripts/verify_cli.py` 退出 0（A/B `RESOLVED`、C `CONFLICT`、D `NEED_EVIDENCE`，7/7/7/6）；独立 HTTP 探针退出 0（**16/16**：四个 §8.3 复现案例全部翻转 + 九项保留行为回归 + P1-01/03 保持关闭，本机模型替身 3 次调用，端口无残留）。首审 88 项、Review #2 101 项不重复累计。
- 本轮变更面仅 4 个文件（`RouteValidator`、`ServerConfiguration`、`TaskRouterTest` +5、`LlmTaskRouterTest` +3），其余 30 个交付文件 SHA-256 与 Review #2 一致；`spec.md` 与 `core/adapter/app` 零改动。
- 状态转换：`REVIEW_PENDING → DONE`。用户已授权建立**本地 checkpoint commit `2c599e7`**（不 push、不打 tag），M2 以该提交为比较基线；交接时对照快照另存于 `outputs/baselines/M1/`（`M1-delivery.patch`、`untracked/` 31 个未跟踪文件副本、`SHA256SUMS.txt` 33 个交付文件全 OK、聚合 SHA-256 `6abaca40…`，task/state 流程文档不入清单）。
- M2 启动前置：M1 = DONE（本记录）+ 基线快照可校验；QUERY/DIAGNOSE 的临时 501 分支仍为测试态，M2/M3 完成后必须删除或证明不可达。

## 4. M2｜S1：事实查询 + Core 请求适配（原 R05）

**目标**：受限 QUERY 返回有来源的事实；自然语言 DIAGNOSE 被正确、审慎映射到既有 Core 契约。

**必须实现**：

1. `QueryService` 仅支持 `ASSET_PROFILE` / `MAINTENANCE_HISTORY`；通过实际 `AssetRepository.profile/history` 查询，不为事实查询伪造 DiagnosisRequest。
2. 检查 session/assetId 与返回记录资产一致；`sources` 正确包含 `evidenceId/sourceRef/sourceType`；`dataNature=SIMULATED`。
3. 出厂记录、维修历史与“当前实际装机事实”语义严格区分；不能从出厂记录直接给出当前确定配置。
4. `DiagnosisGoal` 保留原始 message 和 normalizedGoal；Catalog 固定模拟 asOf、RuleRepository 确认 symptom 规则；转换旧 `DiagnosisRequest`。
5. 症状映射是有限别名，不把所有自然语言故障都匹配 `COMM_TIMEOUT`；未知 Asset/Rule 走 CLARIFY/UNSUPPORTED。
6. M3 未完成前，诊断交付仅可验证 Goal→Request；不可伪装诊断已执行。

**测试/验收**：真实 profile/history 查询、来源字段、当前配置不能确定时的安全表达、固定业务时间、未知故障。对应 TC-02/TC-03/TC-08/TC-13/TC-14/TC-15。

**M0 冻结补充（M2 必须遵守）**：

- `ASSET_PROFILE` → `AssetRepository.profile(assetId)`；`MAINTENANCE_HISTORY` → `AssetRepository.history(assetId, null)`（`componentId` 必须传 `null`，`JsonRepositories` 只按 `payload.subject` 过滤）。
- `sources[]` 直接取记录的 `evidenceId/sourceRef/sourceType`（真实值形如 `simulated:CAR-B/FACTORY`），并校验返回记录 `assetId` 与请求一致。
- 空结果要明确说“该资产没有此类模拟记录”，不得回落到别家资产或编造记录。
- `originalUserMessage` 只留在应用层；旧 `DiagnosisRequest` 不含该字段，不得宣称已传入 Core。

**M2 Codex Review**：Repo 和 Gateway 边界、真实 fixture 数据、时间源、源 ID 严格对应、没有硬编码答复。报告写入 `reviews/M2.md`。

### 4.1 M2 执行记录（2026-11-08，开发者自测；已由 Review 首审置 DONE）

- **基线**：M1 checkpoint `2c599e7`；开工前 `git status` 为空，HEAD 仅多出文档提交 `fc067d7`；基线测试 `mvn -q -o clean test` 退出 0（21 类 109 项）。
- **交付内容**：
  - `QueryService`（新）：仅 `ASSET_PROFILE → AssetRepository.profile(assetId)` 与 `MAINTENANCE_HISTORY → AssetRepository.history(assetId, null)`；不构造 `DiagnosisRequest`、不经过 `CapabilityGateway`、不做状态重建。
  - `QueryAnswer`（新）：`scope/assetId/answer/sources/limitations/dataNature`；`sources[]` 逐项取记录真实 `evidenceId/sourceRef/sourceType`，`evidenceId` 与 `sourceRef` 分开保存。
  - 资产一致性：返回记录 `assetId` 与请求不一致 → `QueryAssetMismatchException` → `500 INTERNAL_ERROR`，不返回他人数据。
  - 语义边界：`ASSET_PROFILE` 明确“不代表当前实际安装配置”，`MAINTENANCE_HISTORY` 明确“发生过更换 ≠ 当前已现场确认”；两者都带 `limitations`；空结果明确“没有该类模拟记录”。
  - `DiagnosisGoal` + `DiagnosisApplicationService`（新）：由 Catalog 取 `domain/symptomCode/diagnosisAsOf`，别名只认 `SymptomResolver` 白名单，再用 `RuleRepository.query`（经 `RouteValidator.hasRule`）确认规则存在；`originalUserMessage/normalizedGoal` 只留应用层；`toRequest()` 生成旧四字段 `DiagnosisRequest`。
  - `ConversationFlowService`：`QUERY` 走真实查询并返回 `200` + 结构化 `query`；`DIAGNOSE` **仍抛 501 且在任何会话写入前抛出**（M3 才接 Core），另提供 `planDiagnosis(...)` 只做 Goal→Request 映射、不写会话、回复中写明“诊断尚未执行”。
  - 原子性：`ConversationSession.commitPlanned(route, Supplier)` 在同一会话锁内“容量预检 → 执行只读查询 → 写入 USER/SYSTEM”，查询失败或 429 都不留半条消息；原 `commitInteraction` 复用同一 `writeLocked`。
  - 装配：`ServerConfiguration` 新增两个 `@Bean`（`QueryService` 只依赖只读 `JsonRepositories`；`DiagnosisApplicationService` 依赖 Catalog + `RouteValidator`）；`core/adapter/app` 仍无 Spring 注解，扫描范围未放宽。
- **门禁（真实执行）**：

| 命令 | 退出码 | 结果 |
|---|---|---|
| `mvn -q -o '-Dtest=QueryServiceTest,DiagnosisApplicationServiceTest,ServerHttpTest,QueryHttpTest,ServerAtomicityHttpTest' test` | 0 | 定向测试全绿（含新增 16 项） |
| `mvn -q -o clean package` | 0 | **24 类 125 项，0 失败/0 错误/0 跳过**（109 + 16）；`Start-Class=io.devicetruth.app.DeviceTruthApplication` |
| `java -jar target/devicetruth-core-0.1.0.jar --scenario=A|B|C|D --reasoner=scripted` | 0×4 | 7/7/7/6；RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE |
| `--scenario=E` | 2 | 非法参数仍拒绝，未新增 E 场景 |
| `python scripts/verify_cli.py` | 0 | 4 项 PASS |
| `python target/m2-probe.py`（真实 Server，自起自停，随机端口） | 0 | **12/12**：TC-01/02/03/08/14/15 与“DIAGNOSE 未执行”“消息原子记录”全部通过；端口无残留 |

- **未验证**：在线 Router / 真实模型未调用（`BLOCKED_BY_ENV` 语义沿用）；M3 的异步 Core、FAILED 映射、事件安全视图不在本轮范围。
- **本轮未修**：`M1-P2-01/02` 按要求延期，状态不变。

### 4.2 M2 复审结果与里程碑关闭（2026-11-08）

- Codex Review（`reviews/M2.md` §7）判定 **PASS**：未解决 P0=0、P1=0，新增 P2=2（`M2-P2-01` 死代码：`ConversationSession.Interaction.queryAnswer` 与 `ConversationFlowService.renderQueryReply` 无消费方；`M2-P2-02` `planDiagnosis` 无交付内测试，行为已经探针实测）。连同 M1 延期的 `M1-P2-01/02`，共 4 项 P2 登记延期，不阻塞 M3。
- 复审实际执行：`mvn -q -o clean package` 退出 0（**24 类 125 项，0 失败/0 错误/0 跳过**，M1 基线 109 + 新增 16）；`Start-Class=io.devicetruth.app.DeviceTruthApplication`；CLI A–D 退出 0（7/7/7/6；RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE）、`--scenario=E` 退出 2；`python scripts/verify_cli.py` 退出 0（4 项 PASS）。
- Reviewer 独立 HTTP 探针（真实 Server 自起自停，随机端口）退出 0：**13/13**——QUERY 两类真实来源（`CAR-B-FACTORY/simulated:CAR-B/FACTORY/FACTORY`、仅 `CAR-B-REPLACE`）、空历史不编造、SN-0245 无 CAR-B 泄漏、DIAGNOSE 501 无半条消息、响应无 `reportPath/stopReason/diagnosisSummary`。
- Reviewer 独立 `PlanProbe`（真实装配 `planDiagnosis`）退出 0：`assetId=CAR-B, domain=automotive-demo, symptomCode=COMM_TIMEOUT, asOf=2026-11-03T04:00:00Z`，会话 0 消息，回复含“诊断尚未执行”。
- 状态转换：`REVIEW_PENDING → DONE`。用户已授权建立**本地 checkpoint commit `0dff7c9`**（不 push、不打 tag）；M3 以该提交为比较基线（`git diff 0dff7c9..HEAD`），交接快照另存于 `outputs/baselines/M2/`（`M2-delivery.patch` = `git diff 2c599e7 -- src README.md`；`untracked/` 8 个新增文件副本含 `reviews/M2.md`；`SHA256SUMS.txt` 18 个交付文件全 OK，聚合 SHA-256 `6282c5f2…`；task/state 流程文档不入清单）。
- M3 启动前置：M2 = DONE（本记录）；DIAGNOSE 的 501 分支在 M3 接入 `DiagnosisRunService` 后必须删除或用测试证明不可达；§4.8 安全映射 M3 必测。

## 5. M3｜S1：异步诊断与最终验收

M3 连续完成异步诊断和最终验收，但设两个内部检查点：A 先证明状态机和 Core 调用正确，B 再完成结果/事件安全视图和全量验收。任一检查点失败都保持 M3 `IN_PROGRESS` 或 `CHANGES_REQUIRED`。

### 5.1 异步运行、结果与结束后事件（原 R06）

**目标**：中文诊断经 M1/M2 的 Route/Goal 进入原 `TaskOrchestrator`，有真实异步状态与结果。

**必须实现**：

1. `ReasonerFactory.create()` 每任务独立创建 Scripted/OpenAI/Qwen Reasoner（以当前实现为准）。
2. `DiagnosisRunService` 有界执行器、队列拒绝、`diagnosisId` 公共 ID 和 Core/Report 内部 ID 对照。
3. 接受诊断 POST 固定 `202 Accepted + Location + QUEUED`；GET 查询实际执行状态。**Core RunStatus FAILED 不可包装为 COMPLETED**。
4. 真实读取 RunReport 的 `outcome/stepsUsed/stopReason/supportingEvidenceIds`（存在则按真实字段）；未结束时为 null/不出现。
5. `GET /api/diagnoses/{id}/events?sessionId=...`：运行中不可用；完成时按真实事件顺序提供白名单 `RunEventView`，不泄漏 Prompt/Context/密钥/绝对文件路径。
6. 运行/会话归属校验，不匹配 404。正确处理失败、超时、队列满、服务端模型配置失败，无永久 QUEUED 幽灵任务。
7. 保留原 CLI 和 Guard/安全负例行为，不实现实时推流或中途补证。

**测试/验收**：真实 Core 调用可在日志/报告中证明；双并发状态不串案；Core FAILED→API FAILED；202 竞态稳定；事件安全。对应 TC-04/TC-05/TC-06/TC-10/TC-11/TC-16/TC-17。

**内部检查点 A**：先验证有界执行器、Reasoner 实例作用域、状态转换、Core FAILED 映射和无幽灵任务；再完成 GET 结果、事件来源/过滤、归属与脱敏。不要仅凭 Controller 单测宣告全链通过。

**M0 冻结补充（M3 必须遵守）**：

- `coreTaskId` 只能取自 `report.state().taskId()`；`reportPath` 是 `RunReportWriter` 返回的独立 UUID 目录，两者必须分别保存，API 不返回路径。
- 事件视图严格按 `spec.md` §4.7 白名单映射；`REASON_INPUT` 禁止输出 `context`，`RunEvent.data` 一律不得透传。
- `ExecutionStatus.FAILED` 的错误码取 Reasoner 稳定错误码或 `stopReason`（如 `MODEL_OUTPUT_INVALID_AFTER_RETRY`、`DASHSCOPE_API_KEY_MISSING`），`outcome` 保持空。
- 队列拒绝时必须同时移除注册项，避免永久 `QUEUED`。
- **安全回归（M0-P1-01 修复要求，必测）**：用含合成路径与 `PROMPT_FRAGMENT` 标记的**合法** `ESCALATE` / `REQUEST_INFORMATION` 提案跑真实 Core，断言 `GET /api/diagnoses/{id}` 的 `stopReason/diagnosisSummary/openNeeds` 与 `/events` 的 `TERMINATED.summary` 都不回显该标记（应得到 `STOP_REASON_WITHHELD` 等固定值），同时断言白名单内的停止码（如 `DASHSCOPE_API_KEY_MISSING`）原样返回；Core 报告内的原值不受影响。

### 5.2 端到端验收与文档收敛（原 R07）

**目标**：按权威修订方案 §7 的 **TC-01~TC-20** 完整复核，不再增加需求。

**必须完成**：

1. 运行单测/打包/CLI A/B/C/D 与 E 现有负例、安全约束回归；记录本轮实际测试数，不以历史 42 代替。
2. 本地真实 HTTP：创建会话、QUERY 两类、DIAGNOSE、CLARIFY、UNSUPPORTED、GET status/events/messages。
3. 校验请求与诊断业务时间分离；CAR-B 不会因提交时间改变证据有效性；全部响应有 SIMULATED 语义。
4. 校验未知资产、未知症状、不安全动作、并发串案、排队拒绝、模型失败、来源准确性、事件脱敏。
5. 仅在真实模型 API 可用的环境下执行 online Router + online Reasoner；不可用则记录 `BLOCKED_BY_ENV`、具体障碍、离线测试范围，不宣称在线 PASS。
6. 填写 `S1_ACCEPTANCE_REPORT.md` 中 TC-01~TC-20 逐项结果、请求响应、命令、输出/日志、修改文件、遗留问题。
7. 更新根 README 的**真实可运行**启动/API 命令，并记录当前 Java 调用链便于用户理解。
8. 最终结果明确区分“已有 HTTP+受控 Core”与“尚未实现完整 Hypothesis O-H-E-D / 多轮恢复 / 双域 Skill / Vue”。

**M3 Codex Review**：审查从 M2 checkpoint 到当前交付的代码，并复核整个 S0/S1 的架构一致性与**真实集成证据**；拒绝静态编造的 API 响应、虚假的在线通过。针对 P0/P1 要求修复复测。无在线模型条件时可以注明 S1 离线验收通过、在线验收阻塞，不能混为全量通过。报告写入 `reviews/M3.md`。

## 6. 测试编号映射（原方案 TC-01~TC-20）

| 原测试 | 主要里程碑 | 核心验证 |
|---|---|---|
| TC-01 | M1,M3 | Session 创建/查询 |
| TC-02 | M2,M3 | profile 事实与真实来源 |
| TC-03 | M2,M3 | history 事实不编造 |
| TC-04 | M3 | DIAGNOSE 真正调用 Core |
| TC-05 | M3 | 202/QUEUED/Location + GET |
| TC-06 | M3 | 结束后真实事件 |
| TC-07 | M1,M3 | 无当前设备 CLARIFY |
| TC-08 | M1,M2,M3 | 未登记 SN-0245 不混用证据 |
| TC-09 | M1,M2,M3 | 未支持/危险操作明确拒绝 |
| TC-10 | M3 | 并发隔离及 session 归属 |
| TC-11 | M3 | 模型/Core 失败映射 |
| TC-12 | M0,M1,M3 | CLI A/B/C/D + E 负例 |
| TC-13 | M1,M2,M3 | requestedAt 与 asOf |
| TC-14 | M2,M3 | 出厂历史≠当前确认状态 |
| TC-15 | M2,M3 | evidenceId/sourceRef/SIMULATED |
| TC-16 | M3 | 脱敏与内部路径保护 |
| TC-17 | M3 | 容量拒绝、无幽灵任务 |
| TC-18 | M1,M3 | Server/CLI 双入口 |
| TC-19 | M1,M3 | 无 Key 离线链路 |
| TC-20 | M1,M3 | 在线 Router + Core 模型（可用时） |

## 7. 不属于 S0/S1 的后续任务（禁止提前编码）

- **S2**：多轮会话、状态持久化/补证/暂停/恢复；建立候选 Hypothesis、支持/反驳证据、SUPPORTED/INVALIDATED、因果结论依赖的非平凡故障闭环。
- **S3**：标准 `SKILL.md`、工厂 `SC-0245-B`、新能源车专业场景、双域数据与工具接入。
- **S4**：Vue 产品工作台；如需运行时可视化，届时再评估 Event Sink/SSE。
