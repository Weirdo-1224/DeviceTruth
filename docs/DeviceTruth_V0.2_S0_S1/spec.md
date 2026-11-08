# DeviceTruth V0.2｜S0 / S1 实施规格（Spec）

| 项目 | 值 |
|---|---|
| 状态 | **DRAFT — 待 M0 前半段源码审计、M0 Codex Review 后冻结** |
| 生成日期 | 2026-11-08 |
| 权威设计来源 | [改造实施方案_修订版.md](./改造实施方案_修订版.md) |
| 实际实现依据 | **以 S0_AUDIT.md 记录的当前仓库真实签名与测试为准** |
| 改造范围 | S0 基线保护 / S1 HTTP 中文交互与受控诊断闭环 |
| 后续阶段 | S2 多轮+补证+Hypothesis；S3 标准 Skill+双域；S4 Vue |

## 0. 核心判断与用词

今天要验证的是：用户的中文 HTTP 请求经过轻量 Router，走受限 QUERY 或真正进入既有 `TaskOrchestrator`；产出可核查的**模拟数据**查询/诊断结果、运行状态与**结束后**事件。**不是**开放式故障调查、多轮补证、跨领域 Skill 或实际设备实时诊断。

状态级别：`[PLAN]` 为设计决定；`[HISTORICAL]` 为旧报告记载、需要 M0 审计复核；`[VERIFIED]` 仅 M0 能用类路径/源码/命令真实验证后标注。**本文所有历史事实默认未完成本轮验证。**

## 1. 不变约束

1. 不用 ReActAgent 替代自研受控单 Agent Runtime；不引入第二套 Agent 编排。
2. 原 Core 的 `TaskOrchestrator/Reasoner/CapabilityGateway/EvidenceManager/DecisionGuard/StateManager` 优先原样复用；不放宽 Guard 的 RESOLVED 条件。
3. 原 `java -jar <真实文件> --scenario=B --reasoner=scripted` 及 CLI A/B/C/D 可运行、正常退出；E 仅为既有未知工具/伪造证据安全负例。
4. 不硬编码诊断结果、不伪造资产、故障码、来源、事件类型、测试通过数或在线模型能力。
5. HTTP 模型配置只能来自服务端。S1 默认只允许本机 loopback Demo；Session UUID 不是认证。
6. S1 事件仅运行结束后从 RunReport 获取，不做 Event Sink/SSE。
7. 不新增数据库、长期记忆、Skill Loader、真实 IoT、子代理、Vue、通用复杂 DSL、HypothesisEvaluator。
8. 保留已有 V0.1 `spec/task/state` 原稿，禁止覆盖项目外的用户文件。

## 2. 已知历史线索（需 M0 审计核对）

- Java 21 / Spring Boot 3.5.x / AgentScope Java 2.0.x；使用 AgentScope Model API，不是 ReActAgent 循环。
- CLI 演示 A/B/C/D，模拟域 `automotive-demo`，B 为 `CAR-B / COMM_TIMEOUT`。
- V0.1 历史报告 42 项测试通过；本轮实际数量未知。
- `AssetRepository` 当前只有 `profile(assetId)`、`history(assetId, componentId)` 等；无 `domainOf/listAssets`。
- `RuleRepository.query(domain, symptomCode)`；无 `listSymptoms`。
- `CapabilityGateway.execute(DiagnosisRequest, CallCapability)` 需要诊断 Scope；普通事实查询不得伪造 DiagnosisRequest 使用它。
- `StructuredReasoner` 有可变尝试记录，在线 Reasoner 有延迟初始化字段；不能 Singleton 并发共享。
- `TaskOrchestrator.run()` 的事件列表为运行局部变量，默认只有结束后从 RunReport 可读。
- Hypothesis 可创建/失效，但 `SUPPORTED` 路线及故障因果调查闭环尚未验证充分。

任何一项实际不符：M0 前半段提供源码位置与证据，M0 后半段据此制定**最小兼容**修订，而不是依照文档虚构接口。

## 3. 系统边界与依赖方向

```text
HTTP Client (future: Vue)
    │
    ▼
Server Main + API Controller / DTO
    │
    ▼
ConversationService ── TaskRouter (结构化分类、非第二套 Agent)
    │                    ├─ QUERY ────────► QueryService ─► 已验证只读 Repository
    │                    ├─ DIAGNOSE ─────► Catalog + Rule 校验
    │                    │                         │
    │                    │                         ▼
    │                    │          DiagnosisApplicationService
    │                    │                         │
    │                    │                         ▼
    │                    │          DiagnosisRunService + ReasonerFactory
    │                    │                         │
    │                    │                         ▼
    │                    │                TaskOrchestrator (V0.1)
    │                    │                ├─ Reasoner / AgentScope Model API
    │                    │                ├─ CapabilityGateway / Repositories
    │                    │                ├─ EvidenceManager / DecisionGuard
    │                    │                └─ RunReport / events
    │                    ├─ CLARIFY ──────► 询问缺失信息
    │                    └─ UNSUPPORTED ─► 明确能力限制
    └─ 当前资产上下文（仅轻量内存）
```

### 3.1 原组件改动原则

| 组件 | S1 处理策略 |
|---|---|
| 原 CLI `DeviceTruthApplication` | 保留单次任务入口、退出行为和默认 fat JAR Main |
| `TaskOrchestrator` | 不为实时推流改变运行循环 |
| `Reasoner` | 保留动作协议，每个 Diagnosis Run 创建新实例 |
| `CapabilityGateway` | 所有**诊断模型工具调用**继续经过它；普通 QUERY 限定只读 Repository 查询 |
| `EvidenceManager`、`DecisionGuard` | 维持现有时间/冲突/结案约束 |
| `RunReportWriter` | 复用既有报告，内部映射 reportPath，外部不泄漏绝对路径 |

### 3.2 新增职责，不等于新增 Agent

`DeviceTruthServerApplication`（持续 Web 服务）；`SupportedScenarioCatalog`（演示目录）；`ConversationService`（内存会话）；`TaskRouter`（四路由）；`QueryService`（受限事实查询）；`DiagnosisApplicationService`（Goal→旧 Request）；`ReasonerFactory`（运行隔离）；`DiagnosisRunService`（异步与状态）；`Controller/DTO`（受控响应）。允许合并相邻简单职责，**禁止为了表格机械建空类和抽象层**。

## 4. 关键数据契约

### 4.1 支持范围目录 `[PLAN]`

`SupportedDiagnosis(assetId, domain, symptomCode, diagnosisAsOf)`，数据来自真实 `datasets/scenarios.json` 和已核实映射。`CAR-A/B/C/D` 的确切内容和时间以 M0 审计的源码/fixtures 为准。对未知设备 `SN-0245` 不可偷映射到 `CAR-B`；工厂 `SC-0245-B` 不属于 S1。

`SymptomResolver` 只能匹配 Catalog 明示别名，最终必须 `RuleRepository.query(domain, symptomCode)` 确认可用规则。用户明确资产但症状无法映射时返回 CLARIFY 或 UNSUPPORTED。

### 4.2 双时间 `[PLAN]`

- `requestedAt`：用户发 HTTP 请求的服务器时间，仅记录接入时刻。
- `diagnosisAsOf`：Core 的事实重建业务时间，S1 从 Catalog 的**固定模拟时间**取得；不得以 `Instant.now()` 覆盖。

历史样本 B：CLI `asOf=2026-11-03T04:00:00Z`、`CAR-B-FIELD-1` 的 `observedAt=2026-11-03T03:00:00Z`，`validTo=2026-11-04T00:00:00Z`，**均由 M0 核对**。接口需返回 `dataNature=SIMULATED`。

### 4.3 目标与身份 `[PLAN]`

```text
ConversationSession {
  sessionId: UUID, currentAssetId?: String, activeDiagnosisId?: UUID,
  createdAt: Instant, messages: bounded thread-safe list<ConversationMessage>
}
DiagnosisGoal {
  originalUserMessage: String, normalizedGoal: String,
  assetId: String, domain: String, symptomText?: String,
  symptomCode: String, requestedAt: Instant,
  diagnosisAsOf: Instant, dataNature: SIMULATED
}
DiagnosisRunSnapshot {
  diagnosisId: UUID, sessionId: UUID,
  coreTaskId?: String, reportPath?: String (internal only),
  executionStatus: ExecutionStatus, outcome?: DiagnosisOutcome,
  requestedAt: Instant, diagnosisAsOf: Instant, dataNature: SIMULATED,
  createdAt: Instant, startedAt?: Instant, completedAt?: Instant,
  errorCode?: String
}
```

`diagnosisId` 是 API 唯一公开主 ID，`coreTaskId` 与 `reportPath` 是内部关联。`coreTaskId` 在 Core 未生成时可为空。运行状态不要与 Core 的 `RunStatus` 同名。

### 4.4 状态与 outcome `[PLAN]`

```text
ExecutionStatus: QUEUED → RUNNING → COMPLETED 或 FAILED
Core RunStatus:    ACTIVE / COMPLETED / FAILED (以 M0 真实枚举为准)
DiagnosisOutcome: RESOLVED / NEED_EVIDENCE / CONFLICT / ESCALATE (核对真实类型)
```

Future 正常返回≠业务诊断成功。必须按 `RunReport.result().runStatus()` 映射执行状态。`COMPLETED` 可以伴随 `NEED_EVIDENCE`、`CONFLICT`、`ESCALATE`，不能把这些业务 outcome 当执行失败；Core `FAILED` 不能包装成 `NEED_EVIDENCE`。

### 4.5 事件契约 `[PLAN]`

S1: QUEUED/RUNNING → `eventsAvailable=false, events=[]`；完成且存在最终 RunReport → 将**真实**事件映射到白名单 `RunEventView(sequence, recordedAt, type, stateVersion, summary, referenceIds)`；失败且无报告 → 无事件。

必须按实际事件 Enum 转换；历史候选：`REASON_INPUT / MODEL_ATTEMPT / ACTION_PROPOSED / CAPABILITY_RESULT / HYPOTHESIS_INVALIDATED / RULE_SELECTION_INVALIDATED / GUARD_RESULT / GUARD_REVIEW / STATE_COMMITTED / TECHNICAL_FAILURE / TERMINATED`，待 M0 复核。不要虚构 `CAPABILITY_CALLED`。不得返回原始 `RunEvent.data`、Reasoner Context、完整 Prompt、秘钥、私有绝对路径、堆栈。

## 5. TaskRouter 与查询契约

Router 输入：当前消息、session 中的明确资产、受支持资产/领域/故障摘要。输出严格 DTO，值只允许：

| route | 条件与结果 | 边界 |
|---|---|---|
| QUERY | 仅设备档案 `ASSET_PROFILE` 或维修历史 `MAINTENANCE_HISTORY` | 只读、真实来源、不得当作当前状态重建 |
| DIAGNOSE | 验证 Catalog、症状别名、Rule 后映射旧 DiagnosisRequest | S1 不做任意症状、任意资产的自由诊断 |
| CLARIFY | 缺少必要资产/症状等 | 询问最少必要信息，不推测 |
| UNSUPPORTED | 资产/工具/规则/高风险实际操作不支持 | 明确边界，不构造虚假诊断 |

Router 模型独立 Prompt、输出 DTO、解析/重试策略；它只做**请求分类建议**，不能生成最终确诊、事实或访问权限。程序二次校验。离线模式只保证列明的受限请求；在线 LLM Router + Core Reasoner 必须实际网络验证，没条件标 `BLOCKED_BY_ENV`。

`QueryService`：`AssetRepository.profile(assetId)` / `history(assetId, null)`（实际签名待审计）两类固定只读操作。验证资产归属与来源，返回 `answer/scope/dataNature/sources/limitations`；sources 至少区分 `evidenceId`、`sourceRef`、`sourceType`。出厂档案≠当前实装状态；发生过更换≠已现场确认当前配置。

`DiagnosisApplicationService`：原始自然语言与 normalizedGoal 只保留应用层追溯；当前 S1 不宣称 Core 已得到完整自由目标。转换旧 Core 的 `DiagnosisRequest(assetId, domain, symptomCode, diagnosisAsOf)`，签名以 M0 审计为准。

## 6. Web API 契约

| Method | Path | 响应语义 |
|---|---|---|
| POST | `/api/sessions` | `201 Created`，合法 UUID 与 createdAt |
| POST | `/api/sessions/{sessionId}/messages` | DIAGNOSE `202 Accepted + Location + QUEUED`；其他路线 `200 OK` |
| GET | `/api/sessions/{sessionId}/messages` | 仅该 session 的受限消息视图 |
| GET | `/api/diagnoses/{diagnosisId}?sessionId=...` | 当前执行状态；完成后实际 outcome、来源、步骤、原因 |
| GET | `/api/diagnoses/{diagnosisId}/events?sessionId=...` | 仅结束后最终脱敏事件 |

对诊断 POST，成功接受后始终返回 `202`、`Location: /api/diagnoses/{id}` 与 body `executionStatus=QUEUED`，即使后台已瞬时完成。GET 才是实际状态。查询诊断时必须校验所属 session，不匹配返回 404。

错误约束：非法内容/ID=400；不存在或会话归属不符=404；未知可识别但不支持的资产/故障=200+UNSUPPORTED/CLARIFY；容量超限选择 `429`（M0 核实并冻结）或 503；Router 模型不可用=503 或明确的受限降级；未知系统错误=500，统一 `code/message/requestId`，不得泄露内部机密。Core 异步失败通过 GET 的 FAILED 呈现。

## 7. Server / CLI 两入口

- CLI Main：现有 `io.devicetruth.app.DeviceTruthApplication`，`WebApplicationType.NONE`、单次运行、Context 关闭（历史事实待 M0 再核对）。
- Web Main：新增 `io.devicetruth.server.DeviceTruthServerApplication`，持续 Servlet Web Context，不调用 `System.exit`。
- Spring Boot Maven Plugin **显式固定旧 CLI 主类**，保证默认 fat JAR 入口不变；Server 采用实际验证过的 Maven/Classpath 独立启动命令。**S1 不强制第二份 fat JAR**。
- 真实启动命令记录在 README、`S0_AUDIT.md` 和最终验收报告。扫描边界不得把 CLI 生命周期逻辑注入 Web。

## 8. 并发、配置和存储

- 每个诊断经 `ReasonerFactory.create()` 新建 Reasoner，不共享可变 attempts/延迟 Step；内部状态也要隔离。
- 异步线程池建议 `maxConcurrentRuns=2, maxQueue=10`，可配置、拒绝清晰，拒绝时不保留永久 QUEUED 的“幽灵任务”。
- 内存 registry 建议 `maxSessions=100, maxMessagesPerSession=40, maxStoredRuns=100`；线程安全和过期清理；不可在运行中删除内部对象。数值为建议，M0 冻结或按实测说明。
- 运行模式由服务端配置：离线受限 Router + 每任务 ScriptedReasoner；在线结构化 LLM Router + 每任务 OpenAi/QwenReasoner。客户端不得提交模型 API key/baseUrl/provider/reasoner。
- Server 默认绑定 `127.0.0.1`，无生产级鉴权/多租户。S1 数据和 session 重启后丢失，不宣称长期记忆。

## 9. 验收标准及依赖

按 `task.md` 的 M0~M3 四个里程碑交付，原 R01~R07 要求保留为内部检查项；最终以原修订方案 §7 TC-01~TC-20 为准，特别关注：中文 DIAGNOSE **真实进入 Core**，双时间不混淆，来源不伪造，CLI/安全负例不退化，每任务 Reasoner 隔离，Core 失败映射正确，结束后事件清洗，以及在线/离线验收分开。

**S2 明确登记但 S1 不实施：** 多轮恢复与补证、完整 Hypothesis `OPEN → SUPPORTED/INVALIDATED` 调查路径、因果结论可追溯与专业双域前置验收。事实查询不必强制创建 Hypothesis。

## 10. 变更管理

| 日期 | 变更 | 证据 | 批准人 | 状态 |
|---|---|---|---|---|
| 2026-11-08 | 建立 S0/S1 开发规格草案 | 修订版方案 v0.2.1-S0S1 | 待用户确认 | DRAFT |

M0 前半段只记录冲突事实，不自行重写架构；后半段只能依据已记录事实修订，M0 Codex 审核后将 `DRAFT → FROZEN`。之后确需变动须在此表记录原因、影响测试与用户批准；不允许为通过测试而修改 Guard、降低安全要求或把 S2/S3 提前塞入 S1。
