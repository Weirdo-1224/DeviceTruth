# DeviceTruth Agent Core V0.1 — 开发任务清单（task.md）

> 基线：`spec.md v0.1.0`；离线核心与真实模型端到端路径（OpenAI 兼容 / DeepSeek，2 次）均已通过，全部 MUST 任务完成，整体 **DONE**。千问 DashScope 适配路径未经真实执行，按 D16 记为已知残留风险而非阻塞项，详见 `state.md` 与 `spec.md` 最新审核记录。三份工作文档唯一位置为 `docs/DeviceTruth_Core_V0.1_Spec_Pack/`。
> 使用方法：Codex 只在现有 `DeviceTruth/` 工作目录执行；按依赖顺序推进。每项严格实行 **实现 → 测试 → 证据记录 → 更新 state.md → 勾选**。  
> `[ ]` 未完成、`[x]` 已经真实验证完成、`[-]` 明确取消（需理由）。任务完成状态不能仅凭“代码已写”认定。

## 总体路线

**先把协议和离线闭环跑通，再接真实模型，最后做证据修订与完整评测。** 禁止先做 UI、MCP 或多 Agent。

| 阶段 | 要解决什么 | 关键交付 |
|---|---|---|
| S0 | 工程能跑、技术 API 可行 | 可编译骨架 + 单步推理验证记录 |
| S1 | Core 能按状态调用工具 | 结构化协议 + Gateway + 状态 + 受控循环 |
| S2 | Core 能被真实模型驱动 | AgentScope/Qwen Reasoner 适配与轨迹 |
| S3 | Core 能根据证据修订判断 | Evidence Manager + Decision Guard |
| S4 | 验证可扩展与可复现 | A/B/C/D/E 测试 + 数据扰动 + 文档 |

## S0 — 环境、仓库与可行性（先完成）

### T00. 审核现有目录和开发环境

- [x] 在 `DeviceTruth/` 下执行只读检查：当前路径、文件目录、`git status`（仅当已有 Git）、`java -version`、`mvn -version`。
- [x] **不覆盖现存文件**，若目录已有代码，先评估其与 spec 的兼容性；如冲突，记录在 `state.md`，只做最小修改。
- [x] 核对 AgentScope Java 与 Spring Boot 的稳定依赖版本、JDK 21 和 Maven 可用性。
- [x] 若需要联网下载依赖但不可用，在 `state.md` 标记具体阻塞；仍可先完成与外部依赖无关的源码和测试。

**完成证据**：环境输出摘要、采用的版本和发现的现有文件清单写入 `state.md`。

### T01. 初始化可运行单体工程

- [x] 创建最简 Maven + Spring Boot 工程、应用入口、配置、`.gitignore`、`README.md` 骨架。
- [x] 建立真实包结构，不要创建只含 TODO 的空接口泛滥。
- [x] 初始化 JUnit 5 测试，加入 `--help` 或等价 CLI 参数解析。
- [x] `mvn -q test`、`mvn -q package` 能执行，说明当前是否需要访问网络。

**验收**：可以运行最小 JAR / help；无 API Key 能启动本地离线入口；输出中不得打印密钥。

### T02. AgentScope 单步推理 API Spike（架构门槛）

- [x] 阅读对应版本 AgentScope 官方 `Agent`/`ReActAgent`、Model、结构化输出和 Tool API。
- [x] 以最小 PoC 验证“一个模型调用 → 一个可解析 ActionProposal → 控制权交还 Orchestrator”是否可实现。
- [x] 记录决定：若 ReAct 无法可靠限制为外层单步，则使用 AgentScope Model 及其模型扩展产出结构化动作，**Core 自己调工具**。
- [x] 将具体调用路径、依赖和成功/失败命令写入 `state.md`，禁止凭印象编写不存在的 SDK 方法。

**验收**：代码能编译；若无 Key，至少证明依赖/API 编译可用，并把真实 API 验证标记为未通过/待验证，不得假报。

## S1 — 最小 Core（完成后必须有离线闭环）

### T03. 契约对象与状态模型

- [x] 实现 `DiagnosisRequest`、`ActionProposal` 类型、`DiagnosisState`、`Fact`、`Hypothesis`、`EvidenceNeed`、`EvidenceRecord`、`DiagnosisResult`。
- [x] DTO 的 JSON 序列化/反序列化和枚举非法值处理明确；所有事实/证据 ID、时间统一校验。
- [x] `DiagnosisState` 初始状态、状态版本、执行预算和动作历史有测试。
- [x] 定义 `Reasoner` 接口，使 scripted 和 qwen 可互换；不让 Reasoner 直接更新 state。

**验收**：协议序列化测试、非法动作拒绝测试，全部离线通过。

### T04. Mock Data、Mock Skill 与只读查询接口

- [x] 创建标注 `SIMULATED` 的 A/B/C/D 虚构数据，配置 `asOf`、出厂、维修/更换、现场确认与来源引用。
- [x] 创建至少两条版本专属规则（适用 A/B），前置条件包括“确认当前版本”。
- [x] 实现 `AssetRepository` / `EvidenceRepository` / `RuleRepository` 的 JSON 文件适配。
- [x] 实现五种基础只读能力：`asset.profile.get`、`asset.history.query`、`evidence.search`、`evidence.get`、`diagnosis.rules.query`。

**验收**：工具返回**原始数据/规则候选**而非预写诊断；任一 assetId 越权查询、未知证据 ID 明确失败；fixtures 稳定可读。

### T05. Capability Gateway

- [x] 注册/列举能力描述，检验能力名、参数 schema、资产归属、只读权限。
- [x] 统一结果封装：成功/错误、来源、请求 ID、数据内容；模型不可直接指定本地文件路径。
- [x] 增加非法参数、未知工具、跨资产证据查询的负例测试。

**验收**：外层只经 Gateway 执行工具；无绕过校验的领域查询快捷路径。

### T06. State Manager 与 Task Orchestrator 离线循环

- [x] 每轮基于**最新状态快照**调用 `Reasoner.propose()`；支持五种动作（CALL / HYPOTHESIS / DECISION / REQUEST / ESCALATE）。
- [x] 工具返回后先规范化证据并更新状态，再发起下一轮；状态版本递增且可追踪。
- [x] 编写通用 `ScriptedReasoner`（只看当前已知状态、可用工具及返回结果；不得访问测试期望答案）。
- [x] 加入最大轮数、重复动作检测、动作无效、工具失败和结构化退出机制。
- [x] 保存 `events.jsonl`、`final_state.json`、`result.json`；报告说明使用 `scripted` 而非真实 LLM。

**验收**：能运行离线案例 A，自动读取档案、发现并获取证据、查询规则、形成可被校验的候选决策与完整 trace；本阶段尚未实现完整 Guard 时必须保持未裁决，不得假报 `RESOLVED`，最终裁决在 T09 完成。

## S2 — 真实模型接入

### T07. AgentScope / Qwen Reasoner Adapter

当前：实现与离线协议测试通过。最后一项在线验收按 D16（2026-11-05）改为“任一真实模型端到端路径”，已由 OpenAI 兼容接口（DeepSeek）的真实 B 满足，报告见 `state.md`。DashScope 专属适配（`DashScopeChatModel`）仍未真实执行，保留为残留风险。`--reasoner=qwen` 入口、密钥变量和契约测试不受影响；将来首次使用千问若报错，按 adapter 层问题诊断。

- [x] 基于 T02 核实的 API 实现真正模型调用，不得写一个返回固定 ActionProposal 的 `QwenReasoner`。
- [x] 系统指令只说明状态与行动协议；模型输入必须是**当前状态投影 + 能力描述 + 规则必要条件**，不能把测试答案放入 Prompt。
- [x] 处理结构化输出解析、1 次重试、模型异常和超时；不泄漏密钥。
- [x] 支持 `--reasoner=qwen` 与 `--reasoner=scripted` 切换；缺少 Key 时显式报错。
- [x] 任一真实模型端到端在线验收至少完成一次单步动作提议 smoke test，并完成完整案例 B 的在线端到端验收（D16，2026-11-05）：OpenAI 兼容接口 / DeepSeek 真实 B 两次 PASS，6 步、DEMO-D-120B，轨迹见 `state.md`。原字面为“真实 Qwen 可用时”，因当时仅有千问入口；DashScope 适配路径本身仍未真实执行，该残留风险已显式记录。

**验收**：在线模式实际调用模型并有真实行为轨迹；若受限，必须明确报告不可验收项，不可伪装通过。

## S3 — 证据驱动的关键能力

### T08. Evidence Manager 与当前状态重建

- [x] 证据归属、来源、有效时间、记录时间、去重及引用完整性校验。
- [x] 区分出厂配置、历史更换和**当前现场确认**；重建 `asOf` 的当前配置，不把历史改变误判冲突。
- [x] 同时有效且无法裁决的 A/B 现场确认应生成冲突，不准随意取最后一条。
- [x] 当事实被更可靠/更新且有效的记录替代时，关联的 `Hypothesis` 失效；保留原假设和失效原因。
- [x] 设计不依赖汽车具体字段的事实与依赖计算，最多实现 V0.1 所需简化规则。

**验收**：B 修订测试、C 冲突测试、时间边界测试、依赖传播测试；证据记录本身不能被模型改写。

### T09. Decision Guard 与可追溯结果

- [x] 校验规则 ID、适用领域/故障/组件版本及必需证据类型；核验被引用的 Fact/Evidence。
- [x] 拒绝冲突、缺证、失效假设、伪造 evidenceId 的 `RESOLVED`；阻塞原因可追踪。
- [x] 输出 `RESOLVED/NEED_EVIDENCE/CONFLICT/ESCALATE` 和足够的证据 ID、规则 ID、缺口、停止原因。
- [x] 规则与用户安全界限：模拟建议只用于演示；不可执行真实设备控制。

**验收**：A/B `RESOLVED` 使用对应规则，C `CONFLICT`，D `NEED_EVIDENCE`，E 伪造/越权拒绝；无误报成功。

## S4 — 集成与交付

### T10. 端到端回归与扰动测试

- [x] `mvn -q test` 离线测试全通过，无 API Key。
- [x] 构建 JAR；使用 CLI 分别运行 A/B/C/D，并核验实际 `result.json` 内容。
- [x] 验证更改 B 的现场证据、注入冲突等测试时，无需修改 Core，结果跟随证据变化。
- [x] 增加一个**最小工业领域规则/数据契约测试**，只证明 Core 的领域解耦，不建立工业完整 Demo。
- [x] 检查 `core/` 内没有资产 ID 判断、没有业务规则硬编码；检查异常、预算和事件轨迹。

**验收**：测试日志、输出路径、通过/失败项目汇总写入 `state.md`。

### T11. README、开发交接与最终验收

实现细化同步：精确依赖版本、决策的 hypothesis/advice 引用、预算 CLI 和原始 evidence.json，已同步到 spec 与 README；没有削减验收要求。

- [x] README 写出环境依赖、PowerShell/bash 启动步骤、无 Key 与有 Key 两种模式、CLI、示例结果和故障排查。
- [x] 在 README 明确模拟数据不能直接指导实际车辆/设备维护。
- [x] 根据 `spec.md §10`逐项复核 Done 证据；`task.md` 与 `state.md` 的进度对齐。
- [x] 记录遗留问题、未来替换 JSON/Mock Skill 的接口位置，以及 V0.2 可选方向；不在 V0.1 偷做。

**验收**：新 Codex 会话仅阅读这三个 Markdown 和代码，就能独立复现构建、离线测试与演示。

### T12. 新增 OpenAI Chat Completions 兼容入口（2026-11-05 用户要求）

保留 T07 千问入口，新增 `--reasoner=openai`。本地实现/契约测试与外部在线验收分开记录。原“T07 在线项不代勾”约束已被 2026-11-05 的 D16 修订取代：T07 在线口径改为“任一真实模型端到端路径”，由本入口的真实 B 满足；DashScope 路径的残留风险仍单独记录，不以本入口结果冒充千问已验证。

- [x] 核实 AgentScope 2.0.4 OpenAI 扩展源码，复用 Model API 的单步调用。
- [x] 配置 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`；支持基础地址路径前缀并拦截非法配置。
- [x] 共用动作协议、有限重试、技术失败和事件记录；保留 qwen/scripted 及原有 Qwen 补全测试接口。
- [x] 本机 HTTP 契约测试覆盖认证、请求字段、路径、缺配置/非法配置、重试、错误脱敏和场景 B 的完整 Core 链路。
- [x] 打包后重跑离线 CLI、两种模式缺 Key 失败，更新 README/spec/state 与实际验证证据。
- [x] 配置真实兼容服务后执行 `python scripts/verify_online.py --reasoner=openai`，核验 B 的规则及真实模型/工具/Guard 轨迹；实际外部请求 PASS，6 步 RESOLVED / DEMO-D-120B，报告 684b5a60-b3d3-4a7e-ae28-87af7b248d6b。

### T13. 被 Git 忽略的本地模型配置（2026-11-05 用户要求）

- [x] 创建私有 `devicetruth.local.properties`，预填 DeepSeek 地址/模型、留空 Key，并加入根目录精确忽略规则；保留不含凭据的 example 模板。
- [x] 两种在线模式首次推理读取文件；非空文件值优先、环境变量补缺；失败使用不含文件内容的错误码。
- [x] 测试缺文件、覆盖优先级、空字段回退、不可读/非法文件；本地 HTTP 场景 B 改用文件配置。
- [x] 打包核验无需环境变量的文件配置、密钥不进报告/JAR、CLI 缺配置隔离、在线脚本及文档同步。

### T14. 真实运行发现的调查缺口反馈修复

- [x] 记录首次真实运行：接口连通、12 次合法提议，但过滤历史查询未满足完整性门禁，最终 ESCALATE，B 验收未通过。
- [x] 新增确定性轨迹回放测试，先复现后修复：工具刷新必须保留明确的全资产历史需求，补齐后才能 RESOLVED。
- [x] evaluate/review 共用稳定调查需求，保留原门禁条件；模型提示词说明无过滤查询与事实时间解释。42 项测试及打包通过。
- [x] 重跑真实 B、离线打包 CLI 和轨迹审计；真实兼容接口 B PASS，离线 A/B/C/D、缺 Key 和私有配置回归 PASS，42 项测试通过。

## 执行纪律（必须遵守）

1. 每次开始先读 `docs/DeviceTruth_Core_V0.1_Spec_Pack/` 内的 `spec.md`、`task.md`、`state.md`，**从第一个未完成且无依赖阻塞的任务**继续；构建与运行仍在项目根目录执行。
2. 持续开发当前任务，原则上无需每完成一个任务就请求用户确认；只有触及范围变更、需要真实凭据、可能覆盖用户文件或无法继续时才停下说明。
3. 一个任务完成后，先执行对应检查并记录命令、退出码和摘要；再把该任务勾成 `[x]`；然后更新 `state.md` 的当前阶段、已完成、下一步与阻塞。
4. 若环境不支持依赖下载/LLM Key，把在线验收记为 `BLOCKED_BY_ENV`，不要把整个项目停在无法验证的事情上；离线部分继续完成。
5. 修改协议、CLI 或技术选型时需同步修改 `spec.md`、`task.md`、`README.md`（如果已存在），并在 `state.md` 写下原因。不得悄悄弱化测试标准。
6. 不得编造已运行的测试、不删测试迎合结果、不写静态 CASE-A/B/C 输出、不使用真实维修指导内容。
7. 所有新增代码都必须置于当前 `DeviceTruth/` 内。不得访问或修改用户的其他仓库；保留用户已有文件。

## 首次开发建议

直接使用 `state.md` 中的“首次启动提示词”启动 Codex。之后每次新会话使用同文件的“跨会话续开发提示词”。
