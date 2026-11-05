# DeviceTruth Agent Core V0.1 — 进度状态与跨会话交接（state.md）

> **这是 Codex 必须持续维护的唯一进度快照。**  
> 初始化日期：2026-11-03 | 当前总状态：`BLOCKED_BY_ENV`（仅千问独立在线验收缺 Key；离线与兼容接口真实 B 均已通过）
> 三份最新工作文档统一位于 `docs/DeviceTruth_Core_V0.1_Spec_Pack/`；根目录副本已移除，仅维护当前这一套。

## A. 当前真实状态（每次完成任务后更新）

| 字段 | 当前值 |
|---|---|
| 项目 | DeviceTruth Agent Core V0.1 |
| 工作目录 | 用户已经创建的 `DeviceTruth/` 根目录（不要创建第二层 `DeviceTruth/DeviceTruth/`） |
| 文档目录 | `docs/DeviceTruth_Core_V0.1_Spec_Pack/`，唯一的 spec/task/state 工作版本 |
| 规格基线 | `spec.md v0.1.0` |
| 当前阶段 | S4 — 集成与交付 |
| 当前任务 | **T07：千问独立在线验收**（BLOCKED_BY_ENV）；T12 兼容接口真实 B 已 PASS |
| 最近完成任务 | T00–T06、T08–T14；T12 真实 B 和 T14 调查缺口修复已验证；仅 T07 真实在线项未勾 |
| 当前代码状态 | 23 个主源码文件、12 个测试类；scripted/qwen/openai、私有配置、CLI 与报告已实现；已有 Git 仓库，用户新增 AGENTS.md 未跟踪，保留不动 |
| `mvn test` | `PASS`：42 项，0 失败、0 错误、0 跳过；最新 mvn -o -q package 执行全部测试并退出 0 |
| `mvn package` | `PASS`：最终 `mvn -o -q package` 退出 0，可运行 target/devicetruth-core-0.1.0.jar |
| 离线 A/B/C/D | `PASS`：真实 JAR 和报告核验；A/B RESOLVED，C CONFLICT，D NEED_EVIDENCE；另通过无环境变量时读取私有文件的本机 HTTP CLI 核验 |
| 在线模型 smoke | `openai PASS`（两次真实 B，均 RESOLVED / DEMO-D-120B / 6 步）：684b5a60-b3d3-4a7e-ae28-87af7b248d6b（2026-11-05 首次）、fd06ae85-61a5-4814-a00b-2f80a7b9195d（2026-11-05 13:41 复跑）；`qwen BLOCKED_BY_ENV`：未填写 DASHSCOPE_API_KEY |
| 数据扰动与跨领域测试 | `PASS`：三种 JSON 文件扰动、资产/证据 ID 改名、PLC 不同字段/IN 规则；Core 不变 |
| 风险 / 阻塞 | 仅千问独立在线验收缺 Key；默认 Java 17，使用仓库 JDK 21 的 java.exe 或按 README 设置进程 JAVA_HOME/PATH；在线模型工具选择可能波动 |
| 下一步 | 已可用私有配置运行 --reasoner=openai；如需完成全部原规格任务，在私有文件填千问 Key 后用 JDK 21 执行 python scripts/verify_online.py --reasoner=qwen，单独核验并更新 T07 |
| 最后更新者 / 时间 | Codex / 2026-11-05 13:41（真实兼容接口 B 复跑通过，仅补日志、未改代码） |

进度标记只能使用：`NOT_STARTED`、`IN_PROGRESS`、`BLOCKED_BY_ENV`、`BLOCKED_BY_DECISION`、`DONE`。测试标记只能使用：`NOT_RUN`、`PASS`、`FAIL`、`BLOCKED_BY_ENV`。不要把无法验证写成通过。

## B. 已确认的设计决定（跨会话不重复争论）

1. **目标**：开发通用设备诊断 `Agent Core`，而非优先开发新能源汽车完整 Demo。
2. **架构**：单 Agent；**State-Driven Controlled ReAct**（状态驱动的受控 Reason–Act–Observe–Update 循环）。
3. **动作控制**：LLM 提出 `ActionProposal`，Orchestrator 校验并执行单步动作，状态提交后才再推理。
4. **状态与证据**：结构化 `DiagnosisState` 独立于对话历史；保持来源、时间、假设依赖与失效关系。
5. **能力接口**：Core 只经 Gateway 使用工具、模拟规则与数据；模拟 Skill、JSON 数据允许未来替换。
6. **技术**：Java 21 + Spring Boot 3.5.16 + AgentScope Java 2.0.4 + Maven 3.9+；已从 Maven Central 及官方源码验证并锁定。
7. **开发策略**：先离线完整机制，再接真实 Qwen Reasoner；Spring AI Alibaba、UI、多 Agent 不属于 V0.1。
8. **验收**：A/B/C/D/E、错误证据/越权、状态修订、扰动、最小跨领域测试、真实模型 smoke 独立验收。
9. **安全范围**：模拟数据和模拟规程，所有工具只读；不控制真实设备；不收集或输出密钥。
10. **单步 SDK 边界**：使用 Model.stream、空工具列表和 JSON 响应；不使用 ReActAgent.call 内层循环。SDK 每次尝试 maxAttempts=1、45 秒超时；非法协议只额外重试一次。依据见 docs/research/agentscope-spike.md，在线尚未验证。
11. **防止过早裁决**：Guard 要求完整历史、无过滤 evidence.search 及获取全部已发现记录，再核验规则/引用；同级当前冲突不能被第一条确认或较晚 recordedAt 覆盖。
12. **协议细化**：决策显式含 claimedHypothesisIds/adviceLevel；仅允许 SIMULATION_ONLY；未知字段、类型转换、重复 JSON 键、数字枚举均拒绝。预算参数、evidence.json 和上述细化已同步 spec/task/README，未削减 MUST。
13. **兼容服务入口（2026-11-05）**：原实现只支持 DashScope，用户要求增加 OpenAI 协议并保留千问；采用同版本 AgentScope OpenAI 扩展，复用 Model API 和共享 StructuredReasoner，避免另写 HTTP/重试实现。新增环境变量及 CLI，不改变证据/状态/Guard 契约。baseUrl 显式包含服务要求的前缀，仅追加 /chat/completions；本机 HTTP 测试与真实在线验收分开。
14. **私有配置（2026-11-05 用户最新要求）**：替代仅环境变量限制，允许根目录被忽略的 devicetruth.local.properties；UTF-8 properties，非空文件值优先，环境变量补缺。两种模型共用 ModelConfiguration，只在首次在线推理加载；忽略文件不打包，失败不记录文件内容。在线脚本让 JAR 统一解析配置；离线 CLI 缺配置测试使用独立工作目录，避免读取开发者真实 Key。
15. **调查缺口反馈（真实运行验证后修复）**：过滤组件历史不满足全资产完整性检查，此门禁保留；evaluate/review 共用稳定的历史、搜索、获取记录需求 ID 和 OPEN/SATISFIED 状态，避免工具刷新丢掉待办。提示词说明程序重建的 Fact 时间区间为准，不因原始出厂记录未改写而捏造当前冲突。未硬编码案例动作或放宽决策条件。

以上决定如需调整，必须写明“现有方案问题 → 备选方案 → 更改理由 → 对测试/接口的影响”，再同步修改 `spec.md` 和 `task.md`，绝不可无记录改变。

## C. 当前已知资产

- 工作文档：docs/DeviceTruth_Core_V0.1_Spec_Pack 三份文件已由最新开发版本替换，并移除根目录副本；后续只维护本目录这套文件。
- 现有源码/资料：pom.xml、src、skills/automotive-demo/rules.json、datasets/fixtures/a–d、README、scripts/verify_cli.py、scripts/verify_online.py、官方 API 研究资料。
- 可运行资产：target/devicetruth-core-0.1.0.jar；outputs/runs 各次报告；target/surefire-reports；target/cli-verification.json。均为可重建/运行产物。
- 工具环境：.tools/jdk-21.0.13.0.2+1；Maven 3.9.9；依赖缓存仅项目 .m2；默认全局 Java 仍为 17，未改全局设置。已有 Git 仓库；私有配置文件已忽略，example 模板可交付；不输出私有文件内容。

## D. 开发日志（Codex 每次任务完成/阻塞时追加，不删除旧记录）

### 2026-11-03 — 计划初始化

- 完成：确定 V0.1 规格、任务执行计划、跨会话提示词。
- 未完成：所有编码、依赖验证、构建、测试。
- 下一动作：T00。

**后续日志格式（复制追加）：**

```text
### YYYY-MM-DD HH:mm — Txx / 阶段
- 本轮修改：涉及文件与核心行为（简短列出）
- 真实执行：`命令` → PASS/FAIL/NOT_RUN/BLOCKED_BY_ENV（退出码 / 关键输出）
- 验证证据：测试名、结果文件路径、差异摘要
- 设计决定：新增或变更的协议/框架决定及理由
- 遗留问题：具体未解决事项；如无写“无”
- 下一任务：Txx；为什么可以继续
```

**日志纪律**：执行失败必须保留事实，允许记录修复后再次成功，但不能抹去失败记录；不可记录没亲自运行过的命令为 PASS。

---

## E. Codex 首次启动提示词（复制整段，首次会话使用）

> 将下方代码块完整复制到 Codex。在 VS Code/终端中先打开你**已经创建的** `DeviceTruth` 文件夹，确保工作区根目录就是它。三个 Markdown 文件统一保存在 `docs/DeviceTruth_Core_V0.1_Spec_Pack/`。

```text
你现在是 DeviceTruth Agent Core V0.1 项目的主开发工程师。工作区是我已经创建的 DeviceTruth 文件夹，所有新增/修改仅限于该文件夹，禁止在其中再新建一层同名项目目录，也不得修改其他仓库。

请先完整阅读当前项目中的：
1. docs/DeviceTruth_Core_V0.1_Spec_Pack/spec.md —— 唯一功能与验收基线
2. docs/DeviceTruth_Core_V0.1_Spec_Pack/task.md —— 有顺序、可验收的开发任务
3. docs/DeviceTruth_Core_V0.1_Spec_Pack/state.md —— 唯一跨会话进度与决策记录

目标是按 Spec 驱动开发真正可运行的 Java Agent Core，而不是只输出方案。核心：以结构化状态为中心的受控单步 Reason–Act–Observe–Update 循环；LLM 仅提议动作，Gateway 执行只读工具，Evidence/State Manager 提交状态，Decision Guard 控制结论；模拟 Skill 与 JSON 资料只是测试配套，后续可替换。

执行方式：
A. 从 task.md 的 T00 开始，先只读检查现有目录、Java/Maven/Git/版本条件；绝不覆盖已有用户文件。
B. 按 task.md 的依赖顺序持续完成任务，不要每一步都停下询问确认。能编码就编码、能测试就测试；遇到仅在线 API Key 缺失不阻塞离线核心开发。
C. 在 T02 先按当前官方源码和依赖验证 AgentScope Java 2.x 单步推理能力；不可把 ReActAgent.call() 的完整自动循环假定为“单步”。如必要，用 AgentScope Model API 输出 ActionProposal，由外部 Orchestrator 执行工具，并记录原因。不得凭记忆杜撰 API。
D. 实现真实的核心源码、最小模拟规则/数据、离线 ScriptedReasoner、真实 Qwen Reasoner、测试和 CLI。严格遵循 spec 的五类 Action、状态与证据契约、Guard、A/B/C/D/E 验收以及不写死案例答案的要求。
E. 每完成一个任务：先运行对应真实命令/测试，再勾选 task.md；随后更新 state.md 的当前任务、已完成项、实际命令结果、下一步和开发日志。若验证没做或失败，不能标 DONE。
F. 无 API Key 时不能伪造在线调用。把在线项标 BLOCKED_BY_ENV，离线继续；遇到环境阻塞请提供具体缺什么、如何验证，避免编造通过。
G. 不做 UI、多 Agent、复杂知识库、MCP 平台或超出 V0.1 的扩展；不允许以假实现/静态 mock 诊断答案来“通过测试”。
H. 阅读 spec §10 Definition of Done，所有 MUST 项完成后才可宣告整体 DONE。尽量在当前会话推进多个有连续依赖的任务，避免只给计划不执行。

现在请开始执行 T00，并在本次会话内持续实际开发；每轮回答只需简要汇报已完成任务、真实测试结果、当前阻塞和接下来任务。结束前务必维护 state.md，使下次会话能够无缝接续。
```

## F. Codex 跨会话续开发提示词（以后每次新会话使用）

> 这是最重要的交接提示词。每次重新打开 Codex 或上下文不足时，直接复制以下整段。**不需要把上次聊天记录重新发给 Codex。**

```text
继续开发当前 DeviceTruth 工作区的 Agent Core V0.1。这不是新项目，不要重新初始化、不重写已有成果，也不要另建一个 DeviceTruth 子目录。

启动步骤（必须按顺序）：
1. 确认当前工作目录为用户已有的 DeviceTruth 根目录；只读检查目录结构与 Git 变更（若存在 Git）。不要覆盖现有未提交文件。
2. 完整读取 docs/DeviceTruth_Core_V0.1_Spec_Pack/ 内的 spec.md、task.md、state.md；这是唯一工作版本。以 spec.md 为功能和验收基线，以 state.md 为最新事实快照，以 task.md 为执行与完成清单。构建与运行命令仍在 DeviceTruth 根目录执行。
3. 核对 state.md 上一条开发日志、上次测试结果、未完成任务，以及代码/测试当前是否与描述一致。如状态记录与实际仓库冲突，以代码和可重复运行的测试为准，并先更正 state.md。
4. 从第一个“未完成且依赖满足”的任务继续开发；如上一任务中断，则先恢复它，不要从头重写。

工作规则：
- 真实修改代码、运行命令、检查结果；不得只输出设计建议或声称稍后执行。
- 先做最小正确实现和可重复测试，禁止范围蔓延；不能把 scripted 测试当真实 LLM 通过。
- 所有工具通过 Gateway；模型只能提出动作或假设/诊断草案；证据和状态由程序校验更新，Guard 决定能否 RESOLVED。
- 不得硬编码 A/B/C 案例结果，不得绕过证据校验、忽略时间关系、直接把维修历史等同于当前配置。
- 任何新增/修改只限当前 DeviceTruth 根目录。避免删除原有文件、安装全局组件或读取项目外敏感内容。
- 每完成一个任务，执行其验收命令，更新 task.md 勾选和 state.md 状态/日志（包括失败、阻塞、验证路径）。
- 若在线模型缺 Key/网络阻塞，记录 BLOCKED_BY_ENV，但继续所有离线可开发项；不准静默降级为 mock。
- 如发现当前框架 API 与旧文档不一致，阅读官方版本源码/示例，给出有证据的最小修订并同步文档。
- 已完成的部分不要反复讨论，只在真实发现问题或测试失败时返工。

本次输出简要包含：从何任务继续、实际完成哪些文件/行为、执行了哪些命令及结果、剩余阻塞和下一任务。现在直接开始实作，并在结束前保存真实 state.md。
```

## G. 一次会话结束时 Codex 必须执行的交接检查

- [x] `task.md` 中已勾选的任务都有真实可复现的完成证据。
- [x] `state.md` 的“当前真实状态”已根据最新代码与测试更新，而不是保留初始化状态。
- [x] 所有新做出的架构/依赖决定写入“已确认的设计决定”或开发日志。
- [x] 清楚标记离线测试和在线 smoke 的区别；失败或受限没有假报成功。
- [x] 有具体的“下一个任务编号 + 第一项行动”，新会话不需要猜测。
- [x] 不包含密钥、用户隐私或没有依据的性能声称。

## H. 当遇到阻塞时的处理原则

| 情形 | 允许的行动 | 禁止行为 |
|---|---|---|
| 未配置 `DASHSCOPE_API_KEY` | 用 scripted 模式完成离线研发；在线标 `BLOCKED_BY_ENV` | 虚构 Qwen 成功轨迹 |
| SDK 版本 API 不兼容 | 查官方源码与样例，做最小适配，记录最终版本 | 编造 AgentScope 不存在的方法 |
| Maven 网络不可用 | 写出正确源码、可离线测试组件，记录失败命令与原因 | 声称 `mvn test PASS` |
| 发现旧文件/非空目录 | 先检查并保留，差异写日志 | 粗暴清空目录或覆盖未读内容 |
| 预算用尽/证据不足 | 正常输出 `NEED_EVIDENCE` 并列明原因 | 猜测或伪造 `RESOLVED` |
| 当前数据冲突 | 标 `CONFLICT`，保留双方证据 | 简单取最后一条记录 |

---

**提示：** `state.md` 必须随源码一同维护。下一次用户只需要把 Codex 放到原 DeviceTruth 文件夹，粘贴 F 节提示词，即可依照实际状态继续开发。

## I. Actual development log (continuation of D)

以下历史日志保留当时的路径与操作事实；最新文档位置以 A 节和文档归位日志为准。

### 2026-11-03 15:07 - T00 DONE / T01 IN_PROGRESS
- Inventory: only docs/DeviceTruth_Core_V0.1_Spec_Pack/{spec,task,state}.md existed; all read, originals preserved. Working copies created at project root. No source, AGENTS.md or Git repository.
- Read-only commands: Java/javac Microsoft 17.0.16; Maven 3.9.9; Git 2.56.0. git rev-parse/status reported not a git repository; no git init performed.
- Maven Central metadata saved to docs/research, stable versions pinned: AgentScope 2.0.4, Spring Boot 3.5.16.
- DASHSCOPE_API_KEY_PRESENT=False: online BLOCKED_BY_ENV. PowerShell/curl HTTPS failed (TLS/Schannel); Python HTTPS works. Adoptium API 403; Microsoft official aka.ms JDK 21 download running in .tools only.
- Added Maven skeleton, CLI help, JUnit test, README, .gitignore, workspace-local .m2 config. No tests run yet. mvn -q dependency:resolve running.
- Next: T01 test/package/help with JDK 21; T02 inspect published 2.0.4 sources and compile single model call.

### 2026-11-03 15:25 - T01-T05 verified / T06 IN_PROGRESS
- Workspace JDK: Microsoft 21.0.13.0.2+1, official aka.ms download, archive SHA256 c5706a9cf482ef1a0622a86eea5b13d557b07f04554572af061e2d02b58c0f99. No global installation/settings changed.
- Executed with process-local JAVA_HOME/PATH: mvn -q dependency:resolve (exit 0); mvn -q test (exit 0); mvn -q package (exit 0); java -jar target/devicetruth-core-0.1.0.jar --help (exit 0). First build downloaded dependencies; project .m2 is ignored.
- Reports: ApplicationTest 1, AgentScopeStepTest 2, ProtocolTest 4, GatewayTest 3; 10 total, zero failures/errors. JAR help printed SIMULATED and reasoner options. SDK default logging was noisy; added own logback.xml.
- T02 decision: Model.stream(messages, empty tools, GenerateOptions JSON object) returns a single proposal boundary. ReActAgent.call enters internal reasoning/acting iterations, and maxIters may still summarize with another model call. Do not use it for outer controlled steps. API source/probe documented in docs/research/agentscope-spike.md. Offline one-subscription test passed; real Qwen call BLOCKED_BY_ENV (missing key).
- T03: immutable protocol/state objects, sealed five actions, ID/time validation and strict JSON rejection tested.
- T04/T05: SIMULATED JSON A-D and A/B rules, replaceable repositories, five raw read-only capabilities; unknown/missing args, traversal, cross-asset/forged IDs, write capability and adapter failure tested.
- A subsequent attempt to dot-source scripts/use-java21.ps1 failed under Windows execution policy; Maven then used Java 17 and failed release 21 (exit 1). Corrective action: use inline process-local JAVA_HOME/PATH instead; no policy change. New full test run is in progress, not yet verified.
- T06 current source: StateManager, bounded loop, events/reports and CLI added. Guard deliberately denies all proposals until T09; no premature RESOLVED. T07 adapter code exists with offline parser/retry tests, awaiting execution. No live call claimed.
- Next: verify current full test suite, execute offline A trace; then T08 temporal evidence revision and T09 Guard.

### 2026-11-03 15:36 - T06 DONE / T07 online BLOCKED_BY_ENV / T08 DONE / T09 IN_PROGRESS
- Initial T06 run: 15 tests, one ERROR from JUnit default system-temp cleanup AccessDeniedException. Fixed Surefire JVM temp directory to project target; removed unnecessary Mockito attachment from SDK spike test.
- Re-ran mvn -q test and mvn -q package with process-local JDK 21: both exit 0, 16 tests zero failures/errors. SDK timeout test passed. A subsequent java -Djava.io.tmpdir=target failed because PowerShell split an unquoted -D dotted argument (exit 1); corrected by quoting it.
- CLI offline A: java '-Djava.io.tmpdir=target' -jar target/devicetruth-core-0.1.0.jar --scenario=A --reasoner=scripted -> exit 0; outputs/runs/493f50eb-8051-4bc1-9a17-841f8cb4beb2. At T06 pending Guard, outcome NEED_EVIDENCE after bounded rejection, never fake RESOLVED. Reports include result, final_state, events.jsonl and separate raw evidence.json.
- T07 real adapter uses AgentScopeStep + DashScope qwen-plus, current immutable snapshot/catalog/rules, strict parsing with one retry, timeout/transport failure and MODEL_ATTEMPT events. QwenReasonerTest 2 passed using an injected offline completion boundary; these are not online smoke tests.
- CLI qwen B without key: exit 2; outputs/runs/1461efcf-719e-4e3d-bdfb-0f020c5dc29a; runStatus FAILED, outcome null, DASHSCOPE_API_KEY_MISSING. Online single-step and E2E remain BLOCKED_BY_ENV. No fallback and no real call claimed.
- T08: mvn -q test -Dtest=EvidenceManagerTest -> exit 0, 4 tests zero errors/failures. Factory interval closes at replacement; field observations adjudicate current state; equal-authority overlaps conflict despite recordedAt ordering; dedup/collision/ownership/integrity, future/late/expired timestamps, half-open intervals and fact/evidence-only dependency invalidation covered.
- T09 Guard implementation in progress: validates references/prerequisites/advice, requires completed history and broad evidence discovery before resolving, and generates traceable missing/conflict needs. Final outcomes still require dedicated tests.
- Next: T09 A/B/C/D/E and Guard negative tests; then T10 perturbation/cross-domain, rebuilt CLI runs, T11 documentation/DoD audit.

### 2026-11-03 15:43 - T09 DONE / T10 IN_PROGRESS
- mvn -q test (process-local JDK 21) -> exit 0; 26 tests, zero failures/errors. AcceptanceTest 4 and DecisionGuardTest 2 added and passed; full existing suite remains green.
- A RESOLVED with DEMO-D-120A; B RESOLVED with DEMO-D-120B and field evidence only; C CONFLICT cites both current field records; D NEED_EVIDENCE with missing FIELD_CONFIRMATION; E unknown capability/forged evidence never RESOLVED.
- Guard checks rule scope, supported and temporally valid facts, raw evidence ownership/time/integrity/value correspondence, citations, invalidated hypotheses, prerequisites and SIMULATION_ONLY level. Requires successful unfiltered history and broad evidence search plus fetching discovered records; a model cannot resolve using only the first conflicting record.
- Provisional baseline rule selection is tracked with fact dependencies, then invalidated on replacement evidence. Both RULE_SELECTION_INVALIDATED and HYPOTHESIS_INVALIDATED events verified. Human-review rules escalate; persistent malformed model output is FAILED with outcome null, two attempts and no tools.
- T06/T07 failure corrections retained in previous log; no live model verification claimed. T07 online remains BLOCKED_BY_ENV.
- Next: T10 JSON perturbations, renamed asset, industrial contract, packaged A-D CLI runs; T11 README and final DoD evidence audit.

### 2026-11-03 15:52 - T10 DONE / T11 IN_PROGRESS
- mvn -q test -> exit 0, 30 tests, zero failures/errors; mvn -q package -> exit 0. Python scripts/verify_cli.py -> exit 0, independently launched actual JAR help, A/B/C/D and invalid option; parsed result/state/evidence/events.
- Actual CLI runs: A 5fe37a32-b46e-4fbc-b1dc-94051ba3ba7c RESOLVED DEMO-D-120A 7 steps/34 events; B 691d2e40-961b-4053-8f49-6e4c1f51f48b RESOLVED DEMO-D-120B 7/34; C 2b09df08-f0ea-4d10-a096-48fae6735ff7 CONFLICT no rule 7/35; D bdacfab1-e24b-4bea-b8e4-9011dedb87b6 NEED_EVIDENCE no rule 6/29. Summary: target/cli-verification.json.
- ExtensibilityTest 4 passed: JSON-file perturbation (B field changed to A -> A rule, removed -> NEED_EVIDENCE, conflict injected -> CONFLICT), renamed asset/evidence IDs, PLC domain with new fields/values and IN condition, early decision rejected before second conflicting record fetched.
- SDK follow-up: default MODEL_DEFAULTS can retry three times. Explicit ExecutionConfig maxAttempts(1) + 45-second timeout added, compiled and tested; JSON correction allows one extra proposal attempt. Model reused across turns. Source retained.
- Core scan showed no business asset/version branches; naive ECU regex matched EXECUTION_BUDGET_EXHAUSTED, so final audit uses token boundaries. No direct dataset reads in core.
- Final tightening: only missing-evidence verdict retains provisional rule, terminal missing requirements marked BLOCKED. README and real online verification helper added. These last code changes require final test/package/CLI rerun before handoff.
- Next: T11 synchronize spec protocol/CLI clarifications, audit DoD and final verification; online remains BLOCKED_BY_ENV.

### 2026-11-03 16:07 Asia/Shanghai - T11 DONE / final handoff BLOCKED_BY_ENV
- Final implementation: 20 main Java files / 10 test classes. Strict parsing additionally rejects numeric/boolean string arguments, duplicate JSON keys, numeric enums, null primitives and float-to-int budget conversion. Provisional rule selection cannot retain invalid-reference decisions. Repeated adapter failures escalate after two executions with no progress.
- Executed final commands with workspace JDK 21: mvn -o -q test -> PASS exit 0 (32 tests, zero failures/errors); mvn -o -q package -> PASS exit 0; python scripts/verify_cli.py -> PASS exit 0. The -o build proves cached dependency builds need no network.
- Final actual CLI runs (all in outputs/runs): A 32958c08-a18a-44eb-96da-152177479767 -> RESOLVED / DEMO-D-120A / 7 steps / 34 events; B 41b13460-ee6c-4ee2-9c6a-8122a844a88f -> RESOLVED / DEMO-D-120B / 7 / 34; C 731f9740-0a8f-428b-91eb-4e8fa46988e5 -> CONFLICT / no applied rule / 7 / 35; D 946920db-2fc0-4578-a492-af3985dd49b3 -> NEED_EVIDENCE / no applied rule / 6 / 29.
- Packaged artifact: target/devicetruth-core-0.1.0.jar, 46,769,759 bytes; SHA256 CEFF5ACFBAF76B6F1A63569ADAA79BBD8ACF94A288DF073F685C918D76D81AAB.
- Online helper: python scripts/verify_online.py -> expected exit 2, BLOCKED_BY_ENV: DASHSCOPE_API_KEY missing; no online call attempted. Earlier real qwen CLI missing-key FAILED report is retained at outputs/runs/1461efcf-719e-4e3d-bdfb-0f020c5dc29a. No online success claimed.
- Documentation audit (Python inline): README local links and required PowerShell/bash/runtime/error instructions PASS; root documents plus original archive exist; no nested DeviceTruth directory; core domain/data-access regex scan PASS (no asset/version/domain constants or direct file/repository reads). All checks exit 0.
- Root spec now records concrete SDK/CLI/protocol refinements and checks only supported DoD items. Root task checks T00-T06/T08-T11; T07 last online checkbox stays open. README provides real/offline modes, sample results, source/adapter map, limits and exact continuation.
- Editing failures recovered: one PowerShell-to-Python state write lost Chinese characters and was corrected; documentation patches rejected stale context/duplicate README operations and were reapplied after reading actual files. No test failure was erased; historical Java 17, PowerShell argument and JUnit temp failures remain above.
- Remaining work: T07 only. In a fresh session read root spec/task/state, set process-local JDK 21 per README, check key presence without printing its value, run python scripts/verify_online.py. Review real MODEL_ATTEMPT/action/tool/state/Guard events. If real B passes, check T07 and the two remaining DoD boxes and then mark overall DONE; otherwise diagnose the actual recorded failure without weakening Guard or using scripted fallback.

### 2026-11-03 - Documents consolidated at user request
- User requested replacing the old document pack with the latest working versions. Copied root spec/task/state into docs/DeviceTruth_Core_V0.1_Spec_Pack, verified each source/destination SHA256 pair, then removed only the three root duplicates.
- Verified transfer hashes before path edits: spec F7C8F5BAA13B5BD2D2F24B4BE0EB388255FB11EF69EDDE2D0D83391C754BA403; task C01A34BFA2624B15986518A24F47F882F5F3F8DCB11905E85464A15D2ECEA4FB; state 09DCEF51F93638B836C9E91BD550C7444E144D1A29045BB0C004D7BD2A37D0B2.
- Updated canonical paths in spec structure, task instructions, state current inventory/startup/continuation prompts, README links and SDK spike references. Historical logs retain their original paths as facts of earlier work; current paths are defined by section A and this entry.
- Actual validation: Copy-Item/Get-FileHash/Remove-Item command exit 0; Python document/link/progress/DoD/artifact checks exit 0. Only docs/DeviceTruth_Core_V0.1_Spec_Pack contains the working spec/task/state. Packaged JAR hash is unchanged.
- No Java tests rerun for this documentation-only relocation. Previous 32-test PASS remains the last code verification; T07 online remains BLOCKED_BY_ENV, and no completion checkboxes changed.
- Next: read spec/task/state from this directory, execute build/runtime commands from project root, and complete real Qwen acceptance when DASHSCOPE_API_KEY is configured.

### 2026-11-05 Asia/Shanghai — T12 implementation/offline PASS / live acceptance BLOCKED_BY_ENV

- 用户要求：增加 OpenAI 协议入口，保留千问。新增 AgentScope OpenAI 扩展 2.0.4、OpenAiReasoner 和共享 StructuredReasoner；AgentScopeStep 增加 openai 工厂；CLI 支持 scripted/qwen/openai。Qwen 默认模型、密钥变量及公开补全构造器保留。
- 配置：OPENAI_API_KEY、OPENAI_BASE_URL、OPENAI_MODEL 均必填；只追加 /chat/completions，服务要求的 /v1 或代理前缀由 baseUrl 提供；缺配置/非法配置使用稳定 FAILED 错误码。空工具列表、JSON object、45 秒超时、单次 SDK 尝试和最多一次协议纠正沿用；不记录配置值及上游错误正文。
- 新增 OpenAiReasonerTest 五项本机 HTTP 契约/集成测试：实际 SDK 请求路径/认证/JSON，非法/缺配置，非法动作最多重试一次，HTTP 401 不重试且报告脱敏，B 通过 SDK→Gateway→证据重建→Guard 得出 DEMO-D-120B。ApplicationTest 增加模式选择与默认值回归；这些均不是外部真实模型验收。
- 首轮 mvn -q test：退出 1，38 项中 1 项失败；无 scheme 的地址在 List.of.contains(null) 触发空指针，被误分类为传输错误。改用空值安全的 scheme 比较后，mvn -o -q test → PASS 退出 0，38 项、0 失败、0 错误、0 跳过。随后 mvn -o -q package → PASS 退出 0，全部测试再次通过。使用进程级 Microsoft JDK 21.0.13.0.2，未改全局环境。
- python scripts/verify_cli.py → PASS 退出 0：实际新 JAR help、A/B/C/D、非法 CLI 选项、qwen/openai 缺 Key FAILED 报告及不回退脚本模式。汇总 target/cli-verification.json。A 745cc91f-4b28-4d08-8b74-33e5d3523c46 → RESOLVED / DEMO-D-120A / 7 步；B 2655ab34-6052-4b0a-a3a8-c0d9485ecafd → RESOLVED / DEMO-D-120B / 7；C a1fc2e8e-ef74-448a-914a-3c190f74a858 → CONFLICT / 7；D 21e1e3dc-3f18-4fde-90ea-4ae293a214c7 → NEED_EVIDENCE / 6。
- python scripts/verify_online.py（默认和显式 --reasoner=qwen）及 --reasoner=openai → 均退出 2、BLOCKED_BY_ENV，必需环境变量缺失，未发外部模型请求。T07 与 T12 在线项保持未勾。
- README、spec、task、state 和 API spike 已同步；在线脚本新增 --reasoner，默认 qwen 不变。git diff --check → PASS。更正旧快照“无 Git 仓库”的陈旧信息；保留用户未跟踪 AGENTS.md。源码调研归档与下载依赖留在忽略的 target/.m2，不提交报告、缓存或凭据。
- 构建产物 target/devicetruth-core-0.1.0.jar SHA256：1A3A45604CFDCA1BE7D8845199D3B41C3F2017CDF2662B883FFCA8824EFB8CD0。
- 文档补丁曾在部分文件写入后报告上下文不匹配；重新读取实际内容，仅补缺失部分，未覆盖已有成果。首次移动共享代码的补丁因同一文件多操作被拒绝，拆成顺序补丁后完成；未影响最终测试结果。
- 下一步：配置所选服务环境后执行真实 B 验收，检查 MODEL_ATTEMPT、CAPABILITY_RESULT、STATE_COMMITTED、GUARD_RESULT；按真实结果分别更新 T07/T12，失败时按错误诊断，不降低门禁标准。

### 2026-11-05 Asia/Shanghai — T13 DONE / 私有模型配置

- 用户要求不设置密钥环境变量，改为填被忽略的文件；最新指令替代原仅环境变量约束。新增精确忽略规则 /devicetruth.local.properties，在不存在时从无凭据 example 模板创建私有文件；预填 DeepSeek 地址与模型，两个 Key 创建时留空，未覆盖已有文件。
- 新增 ModelConfiguration：首次在线推理使用 UTF-8 Properties 读取工作目录文件；非空文件值优先、环境变量补缺；文件可选，不可读/解析失败返回 MODEL_CONFIGURATION_UNREADABLE，不附带原始异常或文件内容。两种在线模式共用读取逻辑，scripted 不读取配置。
- verify_online.py 让 JAR 统一解析配置，根据 FAILED 报告区分配置阻塞与模型失败。verify_cli.py 的缺配置及本机 HTTP 检查使用临时工作目录、移除模型环境变量，避免开发者填写真实 Key 后离线脚本发外部请求。
- mvn -o -q test → PASS 退出 0；mvn -o -q package → PASS 退出 0；41 项、0 失败、0 错误、0 跳过，23 个主源码文件、12 个测试类。新增缺文件回退、文件覆盖环境、空字段回退、读取/解析失败脱敏测试；本机 HTTP 的 B 链路使用真实临时配置文件。
- python scripts/verify_cli.py → PASS 退出 0：help、A/B/C/D、非法参数、两种模式缺 Key，以及无环境变量时实际 JAR 从私有文件连接本机模拟接口并正常 ESCALATE；验证认证、地址、模型及报告/控制台不含测试密钥。A ba9f03ff-7f28-46ea-a60e-5001d84f80de → RESOLVED / DEMO-D-120A；B 22a0b490-c5d1-4b1b-aebc-d5181a8b36d3 → RESOLVED / DEMO-D-120B；C 381fd04b-21d1-4481-8614-93c116f7ff9f → CONFLICT；D 7d1ecc6a-8f91-4f1a-a13a-bd7fee5f2bbb → NEED_EVIDENCE。汇总 target/cli-verification.json。
- 在线脚本配置阻塞回归：以 runpy 和 subprocess 包装隔离实际 JAR 的工作目录、移除模型环境变量，qwen/openai 均退出 2 并正确输出 BLOCKED_BY_ENV；没有模型请求。未执行外部真实模型验收。
- git check-ignore -v devicetruth.local.properties → 命中 .gitignore 第 6 行；检查 JAR 不含私有文件及 example → PASS；git diff --check、Python 语法、文档链接及 UTF-8 检查 → PASS。README、spec、task 和当前快照已同步；凭据/报告/缓存不提交。
- 日志首次经 PowerShell 管道写入时中文被系统编码替换，发现后移除本次损坏条目，并通过 UTF-8 补丁重写；旧日志保留。文档补丁的上下文/顺序错误已更正后应用，未修改私有文件内容。
- T13 完成，T07/T12 在线项保留未勾。填写 Key 后从项目根目录用 JDK 21 运行 --reasoner=openai 或 --reasoner=qwen，无需重新打包；实际线上表现仍待验证。

### 2026-11-05 Asia/Shanghai — T12/T14 DONE / 真实兼容服务 B PASS

- 用户填写 API 后请求确认运行。只检查配置存在性，不输出值；OPENAI_API_KEY、OPENAI_BASE_URL、OPENAI_MODEL 已填写，千问 Key 未填写，未改动私有文件。
- 首次执行 python scripts/verify_online.py --reasoner=openai：实际外部模型连接成功，12 次合法动作提议；CLI COMPLETED / ESCALATE，验收脚本断言失败（退出 1），报告 outputs/runs/da0160f2-1711-4f87-a939-14d5c0ad03f4。保留失败报告，不冒称 B 通过。
- 根因：模型使用带 componentId 的历史查询，不满足原有全资产调查门禁；evaluate 给出的通用调查缺口又被下一次工具后的 review 覆盖，模型缺少具体可执行反馈，最终误把出厂/更换的原始记录当当前冲突。修复反馈持久性和提示词，不改完整性/冲突/引用门禁条件。
- 先写轨迹回放回归 scopedHistoryLeavesAnActionableNeedAfterOtherEvidenceIsFetched；mvn -o -q '-Dtest=AcceptanceTest#scopedHistoryLeavesAnActionableNeedAfterOtherEvidenceIsFetched' test → 退出 1，缺口列表大小预期 1 实际 0，确定性复现。DecisionGuard evaluate/review 改用三个稳定调查需求，提示词说明无过滤查询及 Fact 时间语义后，mvn -o -q package → PASS 退出 0，42 项、0 失败/错误/跳过，回放补齐后 8 步 RESOLVED。
- 再执行 python scripts/verify_online.py --reasoner=openai → PASS 退出 0；真实报告 outputs/runs/684b5a60-b3d3-4a7e-ae28-87af7b248d6b，COMPLETED / RESOLVED / DEMO-D-120B，supportingEvidenceIds=[CAR-B-FIELD-1]，6 步、状态版本 6，stopReason=RULE_AND_EVIDENCE_VERIFIED。应用日志从启动到结果约 25 秒，非时延保证。
- 轨迹核验 PASS：6 次 MODEL_ATTEMPT 均 VALID_PROPOSAL、5 次 CAPABILITY_RESULT、6 次 STATE_COMMITTED、1 次 GUARD_RESULT 且 allowed=true、31 个事件；实际工具由模型自主选择：全资产历史、全量搜索、两次 evidence.get、规则查询，最后提出决策。没有 scripted 替身或自动降级。检查本次四份报告均不含实际私有 Key，未打印凭据。
- python scripts/verify_cli.py → PASS 退出 0：实际新 JAR A 5e4d1584-98c3-4502-9784-7fcc89e20c05 → RESOLVED / A 规则；B 70b020ef-2373-4182-9d78-62ab36207f61 → RESOLVED / B 规则；C f7504f7e-aacb-4ada-a064-40ad108d2a8e → CONFLICT；D cb5be558-4b40-4012-8875-ad437e264285 → NEED_EVIDENCE；两种模式缺 Key、本机私有配置读取及脱敏回归均通过。
- README、spec、task、state 已同步，勾选 T12、T14 和 spec 至少一条真实模型路径；T07 千问单独保留 BLOCKED_BY_ENV，未勾所有 MUST 完成项。本次未修改用户 Key，未提交报告或缓存，未添加调试日志。下一步可直接运行兼容接口；千问在线验收需其自身凭据。

### 2026-11-05 13:41 Asia/Shanghai — 真实兼容接口 B 复跑（仅补证据，未改代码）

- 用户要求用已填写的私有配置真实跑一次 B，确认在线链路仍可用。执行前只核对配置字段是否非空（OPENAI_API_KEY/BASE_URL/MODEL 已填、DASHSCOPE_API_KEY 空），未读取或输出任何配置值。
- 同一会话先跑离线对照：`java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted` → PASS 退出 0，RESOLVED / DEMO-D-120B / 7 步，报告 outputs/runs/336e3171-f195-4551-9137-35127452cea2；离线链路与 scripted 剧本未变（规则→出厂档案→假设→历史→搜索→取回→决策）。
- 真实模型：`java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=openai` → PASS 退出 0，进程约 13 秒（启动 1.7 秒，其余为 6 次模型往返）；报告 outputs/runs/fd06ae85-61a5-4814-a00b-2f80a7b9195d，COMPLETED / RESOLVED / DEMO-D-120B / supportingEvidenceIds=[CAR-B-FIELD-1] / 6 步 / 状态版本 6 / stopReason=RULE_AND_EVIDENCE_VERIFIED。
- 轨迹核验：6 次 MODEL_ATTEMPT 全部 VALID_PROPOSAL 且无重试；5 次 CAPABILITY_RESULT；6 次 STATE_COMMITTED；1 次 GUARD_RESULT allowed=true。模型自主顺序为 asset.history.query → evidence.search → evidence.get(CAR-B-FACTORY) → evidence.get(CAR-B-FIELD-1) → diagnosis.rules.query → PROPOSE_DECISION，与 684b5a60 那次完全一致。CAR-B-REPLACE 已由 history 查询取回，故 NEED-DISCOVERED-RECORDS 只需再打开两条，说明门禁按"内容是否到手"而非"打开次数"判定。
- 观察（未处理，供后续讨论）：该轮 hypotheses 全程为空，ProposeHypothesis 与 claimedHypothesisIds 链路在真实模型下仍未被执行；events.jsonl 66 KB，体积主要来自每轮 REASON_INPUT 的全量上下文快照；TaskOrchestrator 对 targetEvidenceNeedIds 只做"ID 存在"校验，模型传空数组即可通过。
- 脱敏检查：result.json / final_state.json / evidence.json / events.jsonl 四个文件逐文件与私有 Key 字符串比对，均不含；未打印、未修改私有配置。outputs/ 已被 .gitignore 忽略，本次运行未新增跟踪文件。
- 未执行：未重跑 mvn test / mvn package（本次未改任何代码，最近一次 42 项 PASS 仍对应当前源码）；未跑 python scripts/verify_cli.py；未做千问验收。
- 下一任务：T07 仍为 BLOCKED_BY_ENV；填 DASHSCOPE_API_KEY 后以 JDK 21 执行 `python scripts/verify_online.py --reasoner=qwen`，核验 MODEL_ATTEMPT/CAPABILITY_RESULT/STATE_COMMITTED/GUARD_RESULT 后再更新 T07 与 DoD。
