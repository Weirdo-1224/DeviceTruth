# DeviceTruth V0.2｜S0 / S1 实施规格（Spec）

| 项目 | 值 |
|---|---|
| 状态 | **FROZEN（M0 Codex Review #2 = PASS，2026-11-08；改动须走 §10 变更表并经用户批准）** |
| 生成日期 | 2026-11-08（M0 审计与冻结同日完成） |
| 权威设计来源 | [改造实施方案_修订版.md](./改造实施方案_修订版.md) |
| 事实依据 | [S0_AUDIT.md](./S0_AUDIT.md)：本轮真实命令（退出码 0/2）与源码行号 |
| 改造范围 | S0 基线保护 / S1 HTTP 中文交互与受控诊断闭环 |
| 后续阶段 | S2 多轮+补证+Hypothesis；S3 标准 Skill+双域；S4 Vue |

## 0. 用词与事实级别

- `[VERIFIED]`：M0 用源码行号或真实命令验证过，见 `S0_AUDIT.md` 对应条目。
- `[FROZEN]`：M0 冻结的 S1 契约，M1–M3 按此实现，改动须走 §10 变更表并经用户批准。
- `[S2]`：明确登记但 S1 不实施。

今天要验证的是：中文 HTTP 请求经轻量 Router 走受限 QUERY 或**真正进入既有 `TaskOrchestrator`**，产出可核查的**模拟数据**查询/诊断结果、执行状态与**结束后**事件。**不是**开放式故障调查、多轮补证、跨领域 Skill 或真实设备诊断。

## 1. 不变约束（`[FROZEN]`）

1. 不用 `ReActAgent` 替代自研受控单 Agent Runtime；不引入第二套 Agent 编排、Spring AI Alibaba、子代理。
2. 原 Core（`TaskOrchestrator/Reasoner/CapabilityGateway/EvidenceManager/DecisionGuard/StateManager`）原样复用；**不放宽 Guard 的 `RESOLVED` 条件**（`DecisionGuard.java:23-60` 不动）。
3. CLI `java -jar target/devicetruth-core-0.1.0.jar --scenario=A|B|C|D --reasoner=scripted` 保持可运行、可正常退出；**E 只是既有负例测试方法**（`AcceptanceTest#caseEUnknownToolAndForgedEvidenceNeverResolve`），不新增 `--scenario=E`（`CliOptions.java:22`）。
4. 不硬编码诊断结果、不伪造资产/故障码/来源/事件/测试通过数/在线模型能力。
5. HTTP 模型配置只能来自服务端；S1 只允许本机 loopback Demo；`sessionId` 不是认证。
6. S1 事件仅在运行结束后从 `RunReport` 取得，不做 Event Sink/SSE/实时推送。
7. 不新增数据库、长期记忆、Skill Loader、真实 IoT、Vue、通用 DSL、`HypothesisEvaluator`。
8. 保留 V0.1 `spec/task/state` 原稿；不覆盖项目外文件；不执行未授权的 git 变更。

## 2. M0 已核实的既有事实（`[VERIFIED]`，均为本轮实测）

| 主题 | 事实 | 依据 |
|---|---|---|
| 环境 | 项目内 JDK `21.0.13.0.2`、Maven 3.9.9；默认环境 `java` 是 17，必须显式设置 `JAVA_HOME` | `S0_AUDIT.md` §B |
| 基线测试 | `mvn -q clean test` 退出 0，**42 项 0 失败/0 错误/0 跳过**；`mvn -q package` 退出 0 | 同上 |
| CLI | A `RESOLVED/DEMO-D-120A/7`、B `RESOLVED/DEMO-D-120B/7`、C `CONFLICT/7`、D `NEED_EVIDENCE/6`，退出码均 0；非法参数退出 2；`verify_cli.py` 退出 0 | 同上 |
| CLI 结构 | `@SpringBootApplication` + `WebApplicationType.NONE` + `try-with-resources` 关闭 Context；主源码**仅此一处** Spring 注解，其余组件全部手工 `new` | `DeviceTruthApplication.java:21-40` |
| 打包 | `spring-boot-maven-plugin` **未配置 `mainClass`**，当前自动探测为 `Start-Class=io.devicetruth.app.DeviceTruthApplication` | `pom.xml:23`；`META-INF/MANIFEST.MF` |
| Web 依赖 | `pom.xml` **没有** `spring-boot-starter-web` | `pom.xml:12-18` |
| Core 循环 | `RunReport run(DiagnosisRequest,Budget,Reasoner)`；`StateManager` 在 `run()` 内创建，`taskId=UUID` | `TaskOrchestrator.java:41-42`；`StateManager.java:16` |
| 报告 | `RunReportWriter.write(root,report)` 用**另一个** UUID 建目录，写 4 个文件 | `RunReportWriter.java:13-23` |
| 状态枚举 | `RunStatus{ACTIVE,COMPLETED,FAILED}`；`Outcome{RESOLVED,NEED_EVIDENCE,CONFLICT,ESCALATE}`；`FAILED` 时 `outcome=null` | `Models.java:11-12`；缺 Key 实测报告 |
| 事件 | **源码识别 11 类**（scripted B 实跑 34 条事件只触发其中 8 类），`REASON_INPUT.data` 携带完整 `Reasoner.Context`；无 `CAPABILITY_CALLED` | `TaskOrchestrator.java:48-49` 等；B 的 `events.jsonl`；`S0_AUDIT.md` §C/§K |
| Repository | 仅有 `profile(assetId)`、`history(assetId,componentId)`、`search/get`、`query(domain,symptomCode)`；**无** `domainOf/listAssets/listSymptoms/exists` | `Repositories.java:10-20` |
| Gateway | `execute(DiagnosisRequest scope, CallCapability)` 校验 assetId/domain/symptomCode 三域一致 | `CapabilityGateway.java:29-50` |
| Reasoner | `StructuredReasoner.attempts` 无同步；在线 Reasoner `step`、`ModelConfiguration.properties` 懒初始化；`ScriptedReasoner` 无状态 | `StructuredReasoner.java:16`；`OpenAiReasoner.java:14-24`；`ModelConfiguration.java:26-38` |
| 场景数据 | `scenarios.json` 的 A/B/C/D：`domain=automotive-demo`、`symptomCode=COMM_TIMEOUT`、`asOf=2026-11-03T04:00:00Z`（四者相同），仅 `assetId` 不同 | `datasets/scenarios.json` |
| 证据时间 | `CAR-B-FIELD-1` `observedAt=2026-11-03T03:00:00Z`、`validTo=2026-11-04T00:00:00Z`；有效区间 `[validFrom,validTo)` | `datasets/fixtures/b/asset.json`；`Models.java:52-56` |
| 规则 | 仅两条 `DEMO-D-120A/DEMO-D-120B`，`SIMULATION_ONLY`，均要求 `FIELD_CONFIRMATION` | `skills/automotive-demo/rules.json` |
| 假设缺口 | `HypothesisStatus.SUPPORTED` **在主源码中从未被赋值**；假设只有 `OPEN` 与 `INVALIDATED` 两种实际状态 | `grep HypothesisStatus.` 结果见 `S0_AUDIT.md` §E |
| 工业数据 | `src/datasets/skills/scripts/pom.xml` 中无 `SC-0245`；S1 无工业资产可用 | `S0_AUDIT.md` §D |

**与方案冲突的处置**：上述事实与修订版方案 §12 的 18 条一致或更具体；唯一需要额外强调的是“报告目录 UUID ≠ `taskId`”与“`SUPPORTED` 无赋值路径”。不需要推翻任何设计的兼容性缺口。

## 3. 系统边界与依赖方向（`[FROZEN]`）

```text
HTTP Client
    │
    ▼
DeviceTruthServerApplication（独立 Web Main，io.devicetruth.server）
    │  显式装配：JsonRepositories / CapabilityGateway / Catalog / Services
    ▼
ConversationController + DTO ── ConversationService（内存、有界、线程安全）
    │
    ▼
TaskRouter（结构化分类，只建议 route；程序二次校验）
    ├─ QUERY ────────► QueryService ──► AssetRepository.profile / history（只读、真实记录）
    ├─ DIAGNOSE ─────► Catalog 校验 + RuleRepository.query 校验
    │                     ▼
    │                 DiagnosisApplicationService（DiagnosisGoal → 旧 DiagnosisRequest）
    │                     ▼
    │                 DiagnosisRunService（有界异步、每任务独立 Reasoner）
    │                     ▼
    │                 TaskOrchestrator（V0.1 原样）→ RunReport + 事件
    ├─ CLARIFY ──────► 询问最少缺失信息
    └─ UNSUPPORTED ──► 明确能力边界
```

### 3.1 原组件改动策略（`[FROZEN]`）

| 组件 | S1 处理 |
|---|---|
| `DeviceTruthApplication` | 不动；继续作为默认 fat JAR Main |
| `TaskOrchestrator` | 不改循环、不注入事件回调 |
| `Reasoner` | 不改协议；每个诊断 Run 新建实例 |
| `CapabilityGateway` | 诊断模型工具调用必须经过它；QUERY **不经过**它 |
| `EvidenceManager` / `DecisionGuard` | 零改动 |
| `RunReportWriter` | 复用；`reportPath` 仅内部保存 |

### 3.2 包与扫描边界（`[FROZEN]`）

- 新增代码只放在 `io.devicetruth.server`、`io.devicetruth.api`、`io.devicetruth.application` 三个新包。
- `DeviceTruthServerApplication` 位于 `io.devicetruth.server`，其默认扫描根为 `io.devicetruth.server`；`DeviceTruthApplication` 的扫描根是 `io.devicetruth.app`。二者**互不重叠**，不会把 CLI 生命周期注入 Web，也不会把 Web Bean 注入 CLI。
- 由于 `core/`、`adapter/`、`app/` 无任何 Spring 组件注解，`core/`、`adapter/` **不得**新增 `@Component/@Service/@Repository/@Configuration`；Server 侧装配集中在一个 `io.devicetruth.server` 内的 `@Configuration`。
- 禁止用 `scanBasePackages="io.devicetruth"` 之类放宽扫描范围。

## 4. 数据契约（`[FROZEN]`）

### 4.1 演示目录 `SupportedScenarioCatalog`

```java
record SupportedDiagnosis(String assetId, String domain, String symptomCode, Instant diagnosisAsOf) {}
```

- 数据来源：**只允许**从打包资源 `datasets/scenarios.json` 派生（`JsonRepositories.readResource(...)`），不得手工维护第二份资产表。
- 冻结内容（本轮实测）：`CAR-A/CAR-B/CAR-C/CAR-D` × `automotive-demo` × `COMM_TIMEOUT` × `2026-11-03T04:00:00Z`。
- 该 Catalog 是 S1 的**唯一资产白名单**：QUERY 与 DIAGNOSE 都必须先命中它。`SN-0245` 等未登记资产一律不进入任何 Repository 查询、不复用 `CAR-B` 证据。
- 不得伪造 `domainOf/listAssets/listSymptoms`；未命中 Catalog 即视为不支持（CLARIFY/UNSUPPORTED），不调用 Repository 试探存在性。

### 4.2 症状别名（`[FROZEN]`）

- `SymptomResolver` 只做**有限别名**匹配到 `COMM_TIMEOUT`：`通信超时`、`通信中断`、`通信异常`、`通讯超时`、`comm timeout`、`communication timeout`（大小写与空白归一化后匹配）。
- 匹配后必须再调用 `RuleRepository.query(domain, symptomCode)` 确认至少存在一条规则；无规则 → 不可诊断。
- 其它症状表述不得默认映射到 `COMM_TIMEOUT`；资产已知但症状无法识别 → CLARIFY；症状属于明确不支持域 → UNSUPPORTED。

### 4.3 双时间（`[FROZEN]`）

| 字段 | 含义 | 来源 |
|---|---|---|
| `requestedAt` | 用户提交 HTTP 请求的服务器时刻 | 接入时 `Instant.now()`，只做记录 |
| `diagnosisAsOf` | Core 重建状态的业务时间 | Catalog 的固定模拟时间 `2026-11-03T04:00:00Z`，**禁止**用 `now()` 替代 |

- 所有 HTTP 响应都带 `dataNature="SIMULATED"`；诊断响应同时返回两个时间。
- 因为 `CAR-B-FIELD-1` 的 `validTo=2026-11-04T00:00:00Z`，用请求时间做 `asOf` 会使 B 退化；这是必须被测试守住的语义（TC-13）。

### 4.4 应用层对象（`[FROZEN]`）

```text
ConversationSession { sessionId: UUID, currentAssetId?: String, activeDiagnosisId?: UUID,
                      createdAt: Instant, messages: bounded List<ConversationMessage> }
ConversationMessage { messageId: UUID, role: USER|SYSTEM, route?: RouteDecision, content: String, createdAt: Instant }
DiagnosisGoal { originalUserMessage, normalizedGoal, assetId, domain, symptomText?,
                symptomCode, requestedAt: Instant, diagnosisAsOf: Instant, dataNature="SIMULATED" }
DiagnosisRunSnapshot { diagnosisId: UUID, sessionId: UUID, coreTaskId?: String, reportPath?: String(内部),
                       executionStatus: ExecutionStatus, outcome?: DiagnosisOutcome,
                       requestedAt, diagnosisAsOf, createdAt, startedAt?, completedAt?,
                       errorCode?: String, result?: 结果视图, events?: List<RunEventView> }
```

- `DiagnosisOutcome` 直接复用 Core 的 `Models.Outcome`（`RESOLVED/NEED_EVIDENCE/CONFLICT/ESCALATE`），**不新建同义枚举**。
- 应用层状态名为 `ExecutionStatus{QUEUED,RUNNING,COMPLETED,FAILED}`，**不与 Core `RunStatus` 同名**。

### 4.5 三个 ID（`[FROZEN]`）

| ID | 创建者 | 用途 |
|---|---|---|
| `diagnosisId` | 应用层，接受任务时 | **唯一公开主 ID** |
| `coreTaskId` | 运行结束后 `report.state().taskId()` | 内部关联（可选对外展示） |
| `reportPath` | `RunReportWriter` 返回目录（独立 UUID） | 仅内部，绝不返回绝对路径 |

### 4.6 状态映射（`[FROZEN]`）

```text
ExecutionStatus: QUEUED → RUNNING → COMPLETED | FAILED
Core RunStatus:  ACTIVE / COMPLETED / FAILED
```

- **必须**读取 `report.result().runStatus()`；Future 正常结束 ≠ 诊断成功。
- `COMPLETED` + `CONFLICT/NEED_EVIDENCE/ESCALATE` 属于业务结论，不是执行失败。
- Core `FAILED` → `ExecutionStatus.FAILED` + 安全 `errorCode`；`errorCode` **必须**经 §4.8 白名单过滤（例如 `MODEL_OUTPUT_INVALID_AFTER_RETRY`、`DASHSCOPE_API_KEY_MISSING` 原样输出；不在白名单则输出固定 `RUN_FAILED_WITHHELD_REASON`）。**不得**降级成 `NEED_EVIDENCE` 或 `COMPLETED`。
- 运行中/未结束时 `outcome/stepsUsed/supportingEvidenceIds/stopReason` 一律为 `null`。

### 4.7 事件视图（`[FROZEN]`）

S1 只在运行结束后提供事件：

```text
QUEUED/RUNNING                    → eventsAvailable=false, events=[]
COMPLETED/FAILED 且有最终 RunReport → eventsAvailable=true, events=[RunEventView...]
FAILED 且无报告                    → eventsAvailable=false, events=[]
```

```java
record RunEventView(int sequence, Instant recordedAt, String type, long stateVersion,
                    String summary, List<String> referenceIds) {}
```

白名单映射（`type` 必须等于真实 Core 类型，不得改名或新增）：

| 真实事件 | `summary`（固定文案 + 安全码/序号） | `referenceIds` |
|---|---|---|
| `REASON_INPUT` | `"第 N 轮推理输入"` | `[]`（**绝不输出 `context`**） |
| `MODEL_ATTEMPT` | `"模型尝试 #N：<attempt.outcome>"` | `[]` |
| `ACTION_PROPOSED` | `"提议动作 <type>"` | `capabilityId` / `candidateRuleId` / `targetEvidenceNeedIds` 之一（存在则取） |
| `CAPABILITY_RESULT` | `"能力结果：成功"` 或 `"能力结果：<error.code>"` | `capabilityId` |
| `HYPOTHESIS_INVALIDATED` | `"假设失效"` | `hypothesisId` |
| `RULE_SELECTION_INVALIDATED` | `"候选规则选择失效"` | `ruleId` |
| `GUARD_RESULT` | `"结案校验：<verdict.reason>"` | 规则 ID（存在时） |
| `GUARD_REVIEW` | `"证据需求复审"` | 冲突证据 ID（存在时） |
| `STATE_COMMITTED` | `"提交状态版本 <version>"` | `[]` |
| `TECHNICAL_FAILURE` | `"技术失败：<code>"` | `[]` |
| `TERMINATED` | `"运行结束：<outcome>/<safeStopReason>"`（`safeStopReason` 按 §4.8 过滤） | `appliedRuleIds + supportingEvidenceIds` |

禁止输出：`RunEvent.data` 原文、`Reasoner.Context`、完整 Prompt、模型原始输出、`purpose/rationale/statement` 自由文本、密钥、绝对路径、堆栈。

### 4.8 公开派生字段的安全映射（P1 修复冻结，M3 必测）

**根因（R01，已复现）**：Core 的 `stopReason` 有两个赋值点直接使用模型自由文本——`TaskOrchestrator.java:139`（`RequestInformation.reason`）与 `:141`（`Escalate.reason`）。派生字段因此不可信：

- `DiagnosisResult.stopReason`；
- `DiagnosisResult.diagnosisSummary`（未采纳规则时为 `"Investigation stopped: " + reason`）；
- `DiagnosisResult.missingEvidenceNeeds[].description`（`NeedSource.AGENT` 时等于 `RequestInformation.missingFields`）；
- `TERMINATED.data.result`（以上三者整份进入事件载荷）；
- 应用层 `errorCode`（FAILED 时取 `stopReason`）。

**复现（真实 Core，非推测）**：用合法提案 `new Escalate("review-only C:\\review-fixture\\context.txt PROMPT_FRAGMENT")` 与 `new RequestInformation(同串, List.of(同串))` 跑 CAR-B，实测三种派生字段与 `TERMINATED.data` 均出现该标记；命令与输出见 `S0_AUDIT.md` §K。

**冻结规则（仅应用层公开视图，Core 内部报告保持原值，不改 Guard/Orchestrator）**：

1. `stopReason` / `errorCode`：**只允许**下列程序生成码原样输出；其它任何值（含模型 reason、未知串、空串）一律替换为固定 `STOP_REASON_WITHHELD`（`errorCode` 场景为 `RUN_FAILED_WITHHELD_REASON`）。持久化与日志仍保留 Core 原值，但不得进入响应或事件视图。
2. `diagnosisSummary`：仅当 `outcome==RESOLVED` 且 `appliedRuleIds` 非空时输出 Core 原文（来源是打包规则包的 `SIMULATION_ONLY` 文案）；否则固定输出 `SIMULATED: no applied rule; see outcome and open needs`。
3. `warnings`：只允许两种形状——`GATEWAY_REJECTED: <白名单错误码>` 或 `EVIDENCE_REJECTED: <Evidence asset mismatch|Evidence integrity rejected|Evidence ID collision>`；其它值替换为 `WARNING_WITHHELD`。
4. 缺口：**不返回** `missingEvidenceNeeds` 原文。只返回 `openNeeds: [{needId, status, relatedRuleId?}]`；`description` 一律不对外（`NeedSource.AGENT` 的描述就是模型自由文本）。
5. 事件：`TERMINATED` 的 `summary` 使用过滤后的 `safeStopReason`；`RunEvent.data` 仍然整体禁止输出。

**`stopReason` 白名单（源码逐点核对，共 28 个稳定值）**：

| 来源 | 值 |
|---|---|
| `TaskOrchestrator` 字面量 | `REASONER_FAILURE`、`REPEATED_ACTION_WITHOUT_NEW_INFORMATION`、`TOOL_FAILURE_NO_PROGRESS`、`CURRENT_EVIDENCE_CONFLICT`、`EXECUTION_BUDGET_EXHAUSTED` |
| `ReasonerFailure` 错误码 | `MODEL_TRANSPORT_OR_TIMEOUT`、`MODEL_OUTPUT_INVALID_AFTER_RETRY`、`MODEL_CONFIGURATION_UNREADABLE`、`OPENAI_CONFIGURATION_INVALID`、`OPENAI_API_KEY_MISSING`、`OPENAI_BASE_URL_MISSING`、`OPENAI_MODEL_MISSING`、`DASHSCOPE_API_KEY_MISSING` |
| `DecisionGuard` 判定 | `RULE_AND_EVIDENCE_VERIFIED`、`MISSING_REQUIRED_EVIDENCE`、`RULE_REQUIRES_HUMAN_REVIEW`、`UNKNOWN_RULE`、`RULE_OUTSIDE_TASK_SCOPE`、`ADVICE_LEVEL_DENIED`、`INVALID_FACT_REFERENCE`、`FACT_NOT_SUPPORTED`、`FACT_OUTSIDE_TASK_TIME`、`INVALID_EVIDENCE_REFERENCE`、`INVALID_HYPOTHESIS_REFERENCE`、`HYPOTHESIS_DEPENDENCIES_NOT_CITED`、`FACT_EVIDENCE_NOT_CITED`、`FACT_EVIDENCE_MISMATCH`、`RULE_CONDITIONS_NOT_SATISFIED` |

白名单实现为**显式集合**，不得改成“大写形状正则”（那会让模型自造的 `IGNORE_ALL_RULES` 之类冒充停止码）。**M3 必测**：用含合成路径与 `PROMPT_FRAGMENT` 标记的合法 `ESCALATE`/`REQUEST_INFORMATION` 提案跑真实 Core，断言 `GET /api/diagnoses/{id}` 与 `/events` 都不回显该标记，且白名单内的停止码原样返回。

## 5. 路由与查询契约（`[FROZEN]`）

### 5.1 RouteDecision

| route | 进入条件（程序二次校验后） | 处理 |
|---|---|---|
| `QUERY` | 命中 Catalog 资产 + `queryKind∈{ASSET_PROFILE,MAINTENANCE_HISTORY}` | `QueryService` 只读查询 |
| `DIAGNOSE` | 命中 Catalog 资产 + 别名命中 `COMM_TIMEOUT` + `RuleRepository.query` 有规则 | 异步诊断 |
| `CLARIFY` | 缺资产（如“这台设备”且无上下文）或缺症状 | 追问最少信息，200 |
| `UNSUPPORTED` | 未登记资产/未覆盖症状/要求实际操作设备 | 说明边界，200，不编造结果 |

- 模型只建议 `route`、资产候选、`queryKind`、症状文本；**任何 route 都要经程序按上表复核**，模型不能授予权限或给出结论。
- Router 独立 Prompt 与 DTO，**不复用** `StructuredReasoner` 的 `ActionProposal` 协议。

### 5.2 QueryService（不伪造诊断 Scope）

```text
ASSET_PROFILE        → AssetRepository.profile(assetId)          （FACTORY 记录）
MAINTENANCE_HISTORY  → AssetRepository.history(assetId, null)    （REPLACEMENT 记录，componentId 必须为 null）
```

- 调用前已完成 Catalog 白名单校验；不构造 `DiagnosisRequest`，不经过 `CapabilityGateway`，不创建 `Hypothesis`，不做状态重建。
- 返回 `QueryAnswer{answer, scope, dataNature=SIMULATED, sources[], limitations[]}`；`sources[]` 每项含真实 `evidenceId / sourceRef / sourceType / dataNature`（来源为记录真实字段，例如 `simulated:CAR-B/FACTORY`）。
- 校验返回记录的 `assetId` 必须与请求资产一致；不一致视为内部错误，不返回数据。
- 语义边界：`ASSET_PROFILE` 只是出厂记录，**不能**回答“当前装的是不是 D-120A”；`MAINTENANCE_HISTORY` 只说明发生过更换，**不等于**已现场确认当前配置。两者都必须写 `limitations`。
- 记录为空时明确说明“该资产没有该类模拟记录”，不得编造。

### 5.3 DiagnosisApplicationService

1. 由 Catalog 取 `domain/symptomCode/diagnosisAsOf`；2. 别名命中症状文本；3. `RuleRepository.query(domain,symptomCode)` 非空；4. 构造 `new DiagnosisRequest(assetId, domain, symptomCode, diagnosisAsOf)`；5. `originalUserMessage/normalizedGoal` 只留在应用层，**不宣称**已把自由目标交给 Core。

## 6. HTTP API 契约（`[FROZEN]`）

| Method | Path | 成功语义 |
|---|---|---|
| POST | `/api/sessions` | `201` + `{sessionId(UUID), createdAt}` |
| POST | `/api/sessions/{sessionId}/messages` | DIAGNOSE：`202` + `Location: /api/diagnoses/{diagnosisId}` + `{route,messageId,diagnosisId,executionStatus:"QUEUED",reply,dataNature}`；QUERY/CLARIFY/UNSUPPORTED：`200` |
| GET | `/api/sessions/{sessionId}/messages` | `200` + 受限消息列表 |
| GET | `/api/diagnoses/{diagnosisId}?sessionId=` | `200` 当前状态；完成时含真实 `outcome/stepsUsed/supportingEvidenceIds`，以及经 §4.8 过滤的 `stopReason/diagnosisSummary/warnings/errorCode` 与 `openNeeds[{needId,status,relatedRuleId?}]` |
| GET | `/api/diagnoses/{diagnosisId}/events?sessionId=` | `200` + `{diagnosisId,eventsAvailable,events[]}` |

- 诊断 POST **永远**返回 `202`+`QUEUED`，即使后台已经完成；真实状态只能 GET。
- 请求体字段：`{content, assetId?}`；`content` 非空白、长度 ≤ 2000；`assetId` 若提供必须符合 `Checks.id` 正则 `[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}`。
- 响应体禁止出现 `reportPath`、模型配置、Prompt、堆栈。
- 结果与事件中的 `stopReason/diagnosisSummary/warnings/errorCode/缺口描述` 必须按 §4.8 过滤；**禁止**直接拼接 Core 的 `DiagnosisResult` 字段。

### 6.1 错误契约（`[FROZEN]`）

| 情况 | 状态码 | code |
|---|---|---|
| 空消息/超长/非法 ID/非法 JSON | `400` | `INVALID_REQUEST` |
| session 不存在、diagnosis 不存在、归属不匹配 | `404` | `NOT_FOUND`（不区分“不存在”与“不属于你”） |
| 已知但不支持的资产/症状/危险操作 | `200` + `UNSUPPORTED`/`CLARIFY` | —— |
| 有界执行队列满 | **`429`** | `RUN_CAPACITY_EXCEEDED`（并移除注册项，无幽灵任务） |
| Router 依赖模型不可用 | `503` | `ROUTER_UNAVAILABLE` |
| 会话/消息容量超限 | `429` | `SESSION_CAPACITY_EXCEEDED` |
| 未预期内部错误 | `500` | `INTERNAL_ERROR` |
| 异步 Core 失败 | GET 返回 `FAILED` | 安全 `errorCode` |

统一错误体：`{code, message, requestId}`；`message` 不含内部细节。

## 7. 双入口与打包（`[FROZEN]`）

- CLI Main 不变：`io.devicetruth.app.DeviceTruthApplication`（`WebApplicationType.NONE`，单次运行，Context 关闭）。
- 新 Server Main：`io.devicetruth.server.DeviceTruthServerApplication`，持续 Servlet Web Context，不调用 `System.exit`，不嵌入 CLI 的 `execute()`。
- **M1 必做**：`pom.xml` 中 `spring-boot-maven-plugin` 显式配置
  `<configuration><mainClass>io.devicetruth.app.DeviceTruthApplication</mainClass></configuration>`，
  并重新打包验证 `META-INF/MANIFEST.MF` 的 `Start-Class` 仍为 CLI 主类、`java -jar ... --scenario=B` 仍退出 0。
- Server 依赖只新增 `spring-boot-starter-web`（Servlet）；不引入 WebFlux、Actuator、数据库或消息框架；健康检查用自有稳定端点（如 `GET /api/health`）。
- Server 启动命令（M1 必须真实验证并写入 README 与验收报告）：

  ```bash
  mvn -q spring-boot:run '-Dspring-boot.run.main-class=io.devicetruth.server.DeviceTruthServerApplication'
  ```

  若该参数在本项目 3.5.16 插件下不可用，改用显式 classpath 启动并记录真实命令与日志；**不得只写概念**。
- S1 不产出第二份 fat JAR。
- Server 默认绑定 `127.0.0.1:8080`（可配置），仅 loopback，无鉴权、无多租户。

## 8. 并发、配置与存储（`[FROZEN]`）

| 项 | 冻结值 | 可配置 |
|---|---|---|
| 运行执行器 | `maxConcurrentRuns=2`、`maxQueue=10`，队列满 `429` 并清理注册项 | 是 |
| 会话 | `maxSessions=100`，超限拒绝创建 | 是 |
| 单会话消息 | `maxMessagesPerSession=40`，超限 `429` | 是 |
| 运行快照 | `maxStoredRuns=100`，仅清理**已结束**记录 | 是 |
| 单条消息长度 | ≤ 2000 字符（`@Size`/手工校验） | 否 |
| 绑定地址 | `127.0.0.1:8080` | 是 |
| 模式 | 服务端属性 `devicetruth.router.mode=offline|online`、`devicetruth.core.reasoner=scripted|openai|qwen`，默认 `offline`+`scripted` | 是（仅服务端） |

- 每个诊断 Run 必须 `ReasonerFactory.create()` 新建实例：`ScriptedReasoner`（无状态）、`OpenAiReasoner`、`QwenReasoner`（各有可变 `attempts`/懒初始化 `step`）。**禁止**把任何 Reasoner 注册成共享 Bean。
- 可共享的对象仅限只读 `JsonRepositories`、构造完成即不可变的 `CapabilityGateway`（`register` 之后不再修改）、`SupportedScenarioCatalog`。
- 客户端**不得**提交 `reasoner/provider/apiKey/baseUrl/model`；服务端忽略或直接 400。
- 在线 Router 不可用时返回 `503 ROUTER_UNAVAILABLE`（或仅对已明确支持的请求做确定性降级，并在响应 `limitations` 标注），不得冒充模型识别成功。
- 会话/运行存于内存，重启丢失，不宣称长期记忆。

## 9. 验收标准（`[FROZEN]`，执行见 task.md）

按 task.md 的 M0–M3 推进；最终以修订版方案 §7 的 **TC-01~TC-20** 为准。重点：中文 DIAGNOSE **真实进入 Core**、双时间不混淆、来源不伪造、CLI 与安全负例不退化、每任务 Reasoner 隔离、Core 失败映射正确、结束后事件清洗、在线/离线验收分开记录。

`[S2]` 明确登记但 S1 不实施：多轮恢复与补证、完整 Hypothesis `OPEN → SUPPORTED/INVALIDATED` 闭环（当前 `SUPPORTED` 无赋值路径）、因果结论可追溯、双域专业数据。

## 10. 变更管理

| 日期 | 变更 | 证据 | 批准人 | 状态 |
|---|---|---|---|---|
| 2026-11-08 | 建立 S0/S1 规格草案 | 修订版方案 v0.2.1-S0S1 | 用户 | DRAFT |
| 2026-11-08 | M0 依据真实审计冻结：以 `S0_AUDIT.md` 的源码行号/命令输出替换全部 `[HISTORICAL]` 断言；冻结双入口与 `mainClass`、Catalog 四场景同域同症状同 asOf、症状别名白名单、双时间、三 ID、`ExecutionStatus` 映射、事件白名单视图、QUERY 两类只读查询、错误码（429/503）、并发与配置上限；登记 `HypothesisStatus.SUPPORTED` 无赋值路径到 S2 | `S0_AUDIT.md` §C–§E；`mvn -q clean test` 42/0/0/0 退出 0；CLI A–D 与 `verify_cli.py` 退出 0 | 待 M0 Codex Review + 用户确认 | FROZEN_CANDIDATE |
| 2026-11-08 | 关闭 M0 Review P1（M0-P1-01）：新增 §4.8 公开派生字段安全映射——`stopReason/errorCode` 改用 28 项显式白名单，未知值替换为 `STOP_REASON_WITHHELD`/`RUN_FAILED_WITHHELD_REASON`；`diagnosisSummary` 仅在采纳规则时输出原文；`warnings` 限两种形状；缺口不再返回 `description`，改为 `openNeeds[{needId,status,relatedRuleId?}]`；`TERMINATED` 摘要使用过滤后的值；M3 增加“合成路径/`PROMPT_FRAGMENT` 不得回显”的必测项。Core/Guard/Orchestrator 零改动 | `reviews/M0.md` §2.1；`S0_AUDIT.md` §K 的真实 Core 复现（`target/probe` 临时探针，退出 0） | 用户（本轮要求） | FROZEN_CANDIDATE（待复审） |
| 2026-11-08 | 关闭 M0 Review P2（M0-P2-01）：区分“源码类型集合 11 类”与“scripted B 实际触发 8 类”，并列出覆盖其余 3 类的既有测试方法 | `S0_AUDIT.md` §C/§E 与 §K；`events.jsonl` 逐条核对 | 用户（本轮要求） | CLOSED |
| 2026-11-08 | **DRAFT/FROZEN_CANDIDATE → FROZEN**：M0 Codex Review #2 判定 PASS（未解决 P0/P1/P2 均为 0），且明确“未发现需要用户裁决的重大架构分歧”，故按 `task.md` §2.2 约定直接冻结，无需另行架构裁决。本行只改规格状态，不改任何技术结论 | `reviews/M0.md` §8.3/§8.6 | Codex Review #2 PASS（重大分歧无需用户裁决） | FROZEN |

冻结后确需变动必须在本表登记原因、影响的 TC 与用户批准；**不允许为通过测试而修改 Guard、降低安全要求或把 S2/S3 提前塞进 S1**。
