# DeviceTruth Agent Core V0.1 — 产品与工程规格（spec.md）

> 版本：0.1.0 | 日期：2026-11-03 | 状态：**离线已实现；真实模型验收 BLOCKED_BY_ENV**  
> 工作目录：用户**已经创建**的 `DeviceTruth/`；三份工作文档统一保存在 `docs/DeviceTruth_Core_V0.1_Spec_Pack/`，只维护这一套，构建与运行命令仍在项目根目录执行。  
> 角色：本文件是**唯一功能与验收基线**；`task.md` 为执行顺序，`state.md` 为跨会话事实记录。发生冲突时，先遵循本文件；不能静默修改核心契约。

## 0. 项目一句话定义

实现一个**跨设备领域复用、状态驱动、证据约束的单 Agent Core**：接收设备故障任务 → 基于当前结构化认知提出下一步行动 → 调用只读能力获取证据 → 程序校验并提交新状态 → 继续调查或输出可追溯的决策。

**V0.1 不开发完整汽车诊断产品；汽车只是用来检验 Core 的最小模拟场景。** 不使用真实维修规程、不接设备控制接口，不将模拟规则解释为现实安全建议。

## 1. 不可妥协的六条原则

1. **Core 优先**：真实开发对象是跨领域 Core；汽车模拟 Skill 和 JSON 数据只是替换方便的最小适配器。
2. **模型提议，程序执行**：LLM 仅产生下一步 `ActionProposal` 或诊断草案；所有工具调用都经 Gateway 验证；不得直接修改事实或决策状态。
3. **每步提交状态**：每轮最多执行一次受控工具动作；得到返回值、校验证据、更新结构化状态后，才允许下一次模型推理。一个工具动作可返回多条原始记录。
4. **来源与时间完整**：保留原始记录、证据 ID、资产 ID、事件有效时间、记录时间、来源；不能将“出厂为 A”和“后来换成 B”误判为矛盾。
5. **可解释修订**：当某个判断依赖的事实被新证据替代或否定时，标记相关推断/规则选择为待重新核验；不直接删除历史。
6. **离线可测、在线可用**：没有 API Key 的机器也能用 `ScriptedReasoner` 完成协议和核心逻辑测试；有 API Key 时再用真正 LLM 测试自主寻证。不能拿 scripted 测试冒充真实智能能力验证。

## 2. V0.1 范围

### 必做（MUST）

- Java 21、Maven、Spring Boot（单体应用）；AgentScope Java 2.x 作为模型/Agent 集成基础。
- **状态驱动的外层控制循环**：读取状态 → 提议动作 → 校验 → 调用能力 → 证据归一化与状态提交 → 决策门禁 → 继续/结束。
- **独立的结构化 DiagnosisState**：任务、事实（带来源）、假设、缺失证据、选中规则、执行预算、状态版本；与 LLM 聊天历史分离。
- **Capability Gateway**：能力注册/发现、JSON Schema 或等价强类型参数校验、只读执行、结果来源包装、错误处理。
- **最小 Evidence Manager**：证据去重、归属检查、时间有效性检查、冲突识别、事实关联与简单判断依赖失效。
- **Decision Guard**：判定证据是否满足模拟领域规则；不给缺证或冲突案例输出肯定的危险操作建议。
- **轻量模拟领域包**：一份 JSON/YAML 汽车诊断规则；三组虚构设备资料和维修/现场记录；查询工具原子化。
- **CLI 入口和运行报告**：可选择案例、推理器，输出状态与事件轨迹；单元、契约和集成测试。
- 可选择增加极简 HTTP 健康探针，但不作为验收前置。

### 不做（MUST NOT）

- 多智能体、复杂 Graph、长期记忆、向量 RAG、知识图谱、MCP 服务治理、动态 Skill 市场、真实 IoT 设备控制、前端工作台、生产级权限/租户系统。
- 为三个测试案例硬编码不同的最终诊断结果或固定工具调用序列。
- 在 Skill 文件中直接预填设备当前状态或最终答案；设备状态必须从模拟数据查询和证据推导。
- 为追求“看起来像 Agent”而让工具直接返回诊断结论。

## 3. 运行模型（最重要的契约）

外层 `TaskOrchestrator` 执行以下循环：

```text
接受 DiagnosisRequest
  ↓
创建 DiagnosisState(version=0)
  ↓
[读取状态快照 + 可用能力目录 + 可用领域约束]
  ↓
Reasoner.propose(snapshot) → ActionProposal
  ↓
检查提议合法性与预算
  ├─ CALL_CAPABILITY → Gateway 执行一个只读工具动作
  │                    → EvidenceManager 校验/归一化
  │                    → StateManager 原子提交 version+1
  │                    → Guard 检查 → 继续 / 结束
  ├─ PROPOSE_HYPOTHESIS → 记录暂定假设及其依赖（不得直接视为事实）→ 继续
  ├─ PROPOSE_DECISION → Guard 校验依据与规则
  │                    ├─ 通过 → RESOLVED
  │                    └─ 不通过且可调查 → 生成缺证需求 → 继续
  ├─ REQUEST_INFORMATION → 停止为 NEED_EVIDENCE（附待补信息）
  └─ ESCALATE → 停止为 ESCALATE（附理由）

证据冲突无法解决 → CONFLICT；预算耗尽 → NEED_EVIDENCE 或 ESCALATE，附具体阻塞原因。
```

- 不能将 `ReActAgent.call()` **默认**等同于单轮推理：它可能自行完成多步工具调用。要保持外层逐步控制，第一步先做 API Spike，确认当前 AgentScope Java 版本能否可靠暴露单步/受控推理。如无法保证，则使用 **AgentScope Model API + 结构化提议输出**，由我们的 Orchestrator 执行动作；**禁止用无限制的内层 ReAct 自动循环再套一个外层循环**。
- 允许模型生成自由文本的简短推理摘要，但可执行动作必须是严格结构化、可校验的协议对象。
- 模型输出非法 JSON/Schema：最多有限重试（默认 1 次），仍失败则结构化失败退出，写入事件；不允许假造工具结果。
- 工具错误可记录并允许有界重试；重复同一动作且无新增信息超过阈值时中止，以避免死循环。
- 测试中 `ScriptedReasoner` 只能根据可见 `DiagnosisState`/能力列表选择动作，不能读取隐藏期望答案、绕过 Gateway 或写状态。

### 3.1 统一 ActionProposal（语义协议）

```json
{
  "type": "CALL_CAPABILITY",
  "capabilityId": "asset.history.query",
  "arguments": {"assetId": "CAR-B", "componentId": "CTRL-01"},
  "purpose": "确认当前控制器是否存在历史更换",
  "targetEvidenceNeedIds": ["NEED-CURRENT-COMPONENT"]
}
```

- `type` 枚举：`CALL_CAPABILITY | PROPOSE_HYPOTHESIS | PROPOSE_DECISION | REQUEST_INFORMATION | ESCALATE`。
- `CALL_CAPABILITY` 需有效 `capabilityId`、`arguments`；不存在的工具、越权/非只读能力、错误参数须拒绝执行并记事件。
- `PROPOSE_HYPOTHESIS` 携带 `statement`、`dependsOnFactIds`、`dependsOnEvidenceIds`；必须能解析出真实依赖，不得将假设自动升级为事实。
- `PROPOSE_DECISION` 携带 `candidateRuleId`、`claimedFactIds`、`claimedEvidenceIds`、`rationale`；所有引用由程序查证，不可信任模型随意填写的 ID。
- 实现细化：`PROPOSE_DECISION` 另含可空列表 `claimedHypothesisIds` 和必填 `adviceLevel`；V0.1 的 `adviceLevel` 仅允许与规则一致的 `SIMULATION_ONLY`。这使 §6 的失效假设与建议级别校验显式可测，不改变五类动作。
- 其他动作携带 `reason`、`missingFields` 等必要参数。Java 可按类型设计 sealed interface / records，序列化时统一为上述形态。

### 3.2 DiagnosisState 最小字段（独立于模型历史）

```json
{
  "taskId": "uuid",
  "version": 0,
  "task": {"assetId": "CAR-B", "domain": "automotive-demo", "symptomCode": "COMM_TIMEOUT", "asOf": "2026-11-03T12:00:00+08:00"},
  "facts": [],
  "hypotheses": [],
  "evidenceNeeds": [],
  "selectedRuleId": null,
  "actionHistory": [],
  "budget": {"maxSteps": 12, "maxRepeatedActions": 2},
  "status": "ACTIVE"
}
```

状态管理补充契约：

- `Fact`：`factId`、`subject`、`predicate`、`value`、`validFrom/validTo`（可空）、`evidenceIds`、`status`（`SUPPORTED/CONTESTED/SUPERSEDED`）。
- `Hypothesis`：`hypothesisId`、`statement`、`dependsOnFactIds`、`dependsOnEvidenceIds`、`status`（`OPEN/SUPPORTED/INVALIDATED`）；依赖不满足则不得保留为有效诊断依据。
- `EvidenceNeed`：`needId`、`description`、`source`（`RULE/AGENT`）、`status`（`OPEN/SATISFIED/BLOCKED`）、`relatedRuleId`。
- `EvidenceRecord` 与 `DiagnosisState` **分开保存**：有 `evidenceId`、`assetId`、`sourceType`、`sourceRef`、`recordedAt`、`effectiveAt/observedAt`、`payload`、`integrityStatus`；原始记录不可由模型更改。
- 同一个事实只描述某个时间段内的状态；过去记录与当前记录不同不必然冲突。相同时间范围的可信现场确认值不一致且无法排序时，该事实标为 `CONTESTED`，不许确定最终配置。
- `actionHistory` 和独立的 `events.jsonl` 必须能追踪每一步的输入摘要、能力调用、证据引用、状态变更与 Guard 结果。
- V0.1 可使用内存状态加运行结果 JSON 落盘；不要求跨进程恢复任务执行。状态变更必须是显式的、测试可见的。

## 4. Core 与外部能力的边界

### 4.1 Capability Gateway

每个能力具备：`id`、`description`、`inputSchema`、`outputSchema`、`domain`、`readOnly`。Core 只调用 Gateway，不直接读取 `datasets/` 文件。

V0.1 至少实现五个只读能力：

| ID | 输入 | 输出 | 责任 |
|---|---|---|---|
| `asset.profile.get` | `assetId` | 出厂档案及记录引用 | 查档案，不推断最终诊断 |
| `asset.history.query` | `assetId`, `componentId?` | 维修/更换事件列表、证据引用 | 返回历史原始事实 |
| `evidence.search` | `assetId`, `componentId?`, `sourceType?` | 该资产可发现的证据 ID 与元信息 | 先发现证据，不能暴露其他资产记录 |
| `evidence.get` | `assetId`, `evidenceId` | 一条现场/维修证据 | 提供来源、时间及有效属性 |
| `diagnosis.rules.query` | `domain`, `symptomCode` | 可能适用的规则与前置要求 | **不代替** Core 完成规则适用性校验 |

工具不能读取其他资产的数据；未知记录/权限不符须返回结构化错误。非法参数、文件路径穿越或伪造 evidenceId 的行为需拦截。返回结果不允许包含 “建议最终输出规则 X” 之类的预填答案。

### 4.2 Mock Skill（仅作领域规则包）

- 建议 `skills/automotive-demo/rules.json` 或 `rules.yaml`，包括版本、适用故障 `COMM_TIMEOUT`、适用模块版本条件、必需证据类型、允许的建议级别和文字说明。
- **注意 Skill 与 Tool 的区别**：V0.1 的“Mock Skill”是业务规则与能力描述包，不假装等同于 AgentScope 标准 `SKILL.md` 动态加载机制；未来可以写 Adapter 转成标准 Skill。
- Core 只理解通用的条件表达与引用；具体 D-120A / D-120B、ECU 等字符串必须留在规则或数据层。
- V0.1 的规则只允许**有限、可验证的谓词**（如字段相等、集合包含、所需证据类型），不要做完整 DSL/通用规则引擎。

### 4.3 Mock Data（仅作最小数据适配器）

- 建议 `datasets/fixtures/` 下 JSON，采用有 ID、有来源、有时间戳的统一记录结构。
- 通过接口 `AssetRepository`、`EvidenceRepository`、`RuleRepository` 提供数据；初始实现本地 JSON，未来可替换 PostgreSQL / API。
- 当前配置必须依据 `asOf`、更换事件和现场确认综合判定；不能仅取最新一条随意覆盖其他证据。

## 5. 三个强制测试场景（全部为虚构数据）

同一故障码 `COMM_TIMEOUT`，同一个组件 `CTRL-01`，模型看到的工具相同。

| 场景 | 数据 | 正确外部结果 |
|---|---|---|
| **A — 稳定配置** | 出厂 D-120A；当前可信现场确认仍为 A；无更换事件 | `RESOLVED`，仅可使用针对 A 的模拟规则，引用对应证据 |
| **B — 历史更换** | 出厂 A；中途换成 B；当前现场确认 B | `RESOLVED`，只能采用适用于 B 的模拟规则；如先形成基于 A 的假设，新证据须使其失效 |
| **C — 当前冲突** | 同一有效时间内两个同级可信现场记录分别指向 A/B，无法裁决 | `CONFLICT`，不得确定采用 A/B 的版本专属处理建议 |

补充负例（自动化测试至少两个）：

- **D — 缺少现场确认**：虽然有更换记录，但规则要求当前配置确认；当前证据缺失 → `NEED_EVIDENCE`，不伪造确认。
- **E — 错误引用/越权**：模型伪造证据 ID 或调用不存在的能力 → Guard/Gateway 拒绝，不能 `RESOLVED`。

### 5.1 防止“脚本演戏”

- A/B/C 数据只通过 Repository + Gateway 提供；Core 源码不得通过 `if(assetId == "CAR-B")` 决定规则或诊断结果。
- 至少一次采用**数据扰动测试**：把 B 的现场确认改为 A / 移除确认 / 注入冲突之一，在**不修改 Core 代码**的情况下，结果相应变化。
- 跨领域扩展测试：在单元/集成测试中用通用假域（如工业 PLC）配置另一组字段和规则，证明不修改核心循环就能执行；不要求完整工业 Demo。
- 在线真实 LLM 运行保留轨迹，但模型的逐步工具选择可能波动；可重复的确定性验收以 scripted + 程序规则测试为主，在线测试作为独立 smoke test。

## 6. 决策与终止协议

外部 `DiagnosisResult.outcome` 枚举：

- `RESOLVED`：规则适用性通过 + 必需证据满足 + 引用有效，方可输出相应模拟建议；
- `NEED_EVIDENCE`：必要信息/证据缺失，继续查询无进展、需用户补充或执行预算耗尽（附 `missingEvidenceNeeds`/`stopReason`）；
- `CONFLICT`：同一时段关键状态无法裁决，禁止选择版本专属方案；
- `ESCALATE`：规则明确要求人工、风险越界或工具异常无法安全处理（附原因）。

`DiagnosisResult` 至少：`taskId`、`outcome`、`assetId`、`asOf`、`diagnosisSummary`、`appliedRuleIds`、`supportingEvidenceIds`、`missingEvidenceNeeds`、`warnings`、`stopReason`、`stepsUsed`、`stateVersion`。

另有运行状态 `ACTIVE/COMPLETED/FAILED`；`FAILED` 是内部技术失败，不是正常的诊断结论。不能把 “API Key 缺失/JSON 解析失败” 冒充 `NEED_EVIDENCE`。

Guard 最低校验：

1. Rule ID 存在，适用当前领域/故障/当前确认的组件版本；
2. 规则所需证据齐全、来源 ID 存在且属于当前设备、有效时间满足要求；
3. 支持该结论的事实不是 `CONTESTED` / `SUPERSEDED`；
4. 相关假设未标记 `INVALIDATED`；
5. 建议级别不超出规则允许的安全范围；
6. 未满足时拒绝 `RESOLVED` 并给出明确阻塞原因；禁止模型通过重复调用绕过校验。

## 7. 默认技术与依赖策略

- **JDK 21**；**Maven 3.9+**；**Spring Boot 3.5.x**（优先稳定补丁）；**AgentScope Java 2.0.x**，优先已发布稳定版本（截至编写参考 2.0.3，编码前再核对仓库与 Maven Central）。
- `agentscope-core` + `agentscope-extensions-model-dashscope` 足以进行受控模型调用；优先避免 `agentscope-harness` 的不必要工作区和持久会话能力。模型默认 `dashscope:qwen-plus`，通过 `DASHSCOPE_API_KEY` 配置。
- 运行 `--reasoner=scripted` 时不调用任何外部 LLM；`--reasoner=qwen` 缺 Key 须给可理解的启动/运行错误，不得自动切回 scripted 并宣称模型通过。
- 精确 AgentScope Java API **以选定版本的官方源码和可编译示例为准**；若无法获得可靠的单步结构化推理 API，先做隔离 PoC，在 `state.md` 记录决定，再实现适配器。不要编造接口/类名。
- T00/T02 已核实并锁定 Spring Boot **3.5.16**、AgentScope Java **2.0.4**。采用 `Model.stream(...)`、空工具列表和 JSON 对象响应，再由本地严格协议解析；明确关闭 SDK 自动重试（每次模型尝试最多一次请求，45 秒超时）。源码与编译验证见 `docs/research/agentscope-spike.md`；真实模型调用仍需独立验收。
- Spring AI Alibaba **不进入 V0.1 执行链**；不禁止以后引入，但当前避免双编排引擎。
- JSON 用 Jackson；校验用 Jakarta Validation / 手工 guard；测试 JUnit 5 + AssertJ（按版本可用性选择）。
- 密钥只读环境变量；`.env` 等私密文件不得提交、不得写入报告；Mock 数据统一标注 `SIMULATED`。

官方技术资料：

- AgentScope Java Quickstart: https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/en/docs/quickstart.md
- AgentScope Java Agent: https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/en/docs/building-blocks/agent.md
- AgentScope Java Tool: https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/en/docs/building-blocks/tool.md
- Spring Boot 3.5 要求：https://docs.spring.io/spring-boot/3.5/system-requirements.html

## 8. 工程结构建议（不是必须拆分多模块 Maven）

```text
DeviceTruth/
├── docs/DeviceTruth_Core_V0.1_Spec_Pack/
│   ├── spec.md             # 本文件
│   ├── task.md             # 逐项执行清单
│   └── state.md            # 唯一交接状态
├── pom.xml
├── README.md
├── src/main/java/.../devicetruth/
│   ├── app/               # CLI 入口 / 请求与输出
│   ├── core/runtime/      # TaskOrchestrator、Budget/Loop
│   ├── core/reasoning/    # Reasoner、ActionProposal
│   ├── core/state/        # DiagnosisState、StateManager
│   ├── core/evidence/     # EvidenceRecord、EvidenceManager
│   ├── core/decision/     # DecisionGuard、DiagnosisResult
│   ├── core/capability/   # Gateway、Descriptor
│   ├── domain/demo/       # Mock 领域规则适配
│   └── adapter/           # AgentScope、JSON repositories
├── src/test/java/...
├── skills/automotive-demo/rules.json
├── datasets/fixtures/{a,b,c,d}/
└── outputs/runs/          # 运行结果，加入 .gitignore
```

不得因为目录结构建议而机械增加空类、微服务、接口层层包装；能简单实现则简单实现。

## 9. 首版使用方式（Codex 实现后需保证可运行）

- `mvn -q test`：无需 API Key，所有离线测试通过。
- `mvn -q package`：构建可运行 JAR。
- `java -jar target/<实际jar名>.jar --scenario=B --reasoner=scripted`：离线演示，打印结果路径，输出 JSON + JSONL。
- `java -jar target/<实际jar名>.jar --scenario=B --reasoner=qwen`：真实模型演示（需要环境变量 Key）。
- `README.md` 必须给 Windows PowerShell 和 bash 两种运行示例，`--help` 显示有效参数；CLI 命令选项若与 Spring Boot 约定冲突可以调整，但需同步更新规格与 README 并记录缘由。
- 每次运行生成 `outputs/runs/<runId>/result.json`、`events.jsonl`、`final_state.json`。报告中注明 reasoner 类型与 `SIMULATED` 数据性质。
- CLI 还支持 `--max-steps=12`、`--max-repeated-actions=2`；前者范围 1–200，后者至少 1 且不大于前者。未知/重复参数返回退出码 2。原始证据另存 `evidence.json`，与状态分离。正常诊断结果返回 0；内部技术 `FAILED` 返回 2、`outcome=null`，不伪装为正常诊断结论。

## 10. Definition of Done（最终验收）

以下全部完成才可将 Core V0.1 标记 `DONE`：

- [x] 无外部服务和 API Key，`mvn -q test` 全绿；JAR 可启动；离线 A/B/C/D 全部符合预期。
- [ ] 真实 LLM 路径不是假适配器：配置 Key 后能完成至少一条真实模型的端到端调用，并保存工具选择/结果轨迹；如果缺 Key，必须标记 `BLOCKED`，不得宣称线上通过。
- [x] 新事实能够引起正确的状态版本递增、事实替代/冲突标记及有关假设失效；提供专门测试。
- [x] 未经过规则与证据门禁不得得到 `RESOLVED`；越权、无效证据和非法 Tool 参数有负例测试。
- [x] Core 不含 `CAR-A/B/C` 业务结果分支；模拟规则和数据在独立目录/适配器内。
- [x] 数据扰动测试通过，至少一项跨领域扩展契约测试通过。
- [x] `README.md` 提供零基础启动步骤、真实/离线模式、示例输出、边界与限制。
- [ ] `task.md` 所有 MUST 任务完成，`state.md` 最终状态、命令结果、阻塞项真实准确。

2026-11-03 审核：以上未勾的真实 LLM 验收及所有 MUST 完成项，均受 T07 在线验收缺少 `DASHSCOPE_API_KEY` 阻塞；不得标记整体 DONE。复现命令与实际报告路径见本目录 `state.md`，不能将脚本/替身测试当成在线结果。

## 11. 明确不允许的交付行为

- 未运行命令就写“测试通过”；未验证版本就虚构 API；遇到问题使用大量空桩、硬编码或吞异常使测试绿。
- 只做结构文档和空项目；只返回一段 Prompt 而不提交可运行源码；偷偷变更 spec 实现更简单的另外一个产品。
- 删除用户原有代码、移动 `DeviceTruth/` 以外文件、触碰用户其他项目或保存真实密钥。
- 将模拟诊断建议描述为可用于真实车辆或工业设备的维修操作指导。
