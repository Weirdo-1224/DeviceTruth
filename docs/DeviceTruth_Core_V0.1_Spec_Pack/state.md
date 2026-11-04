# DeviceTruth Agent Core V0.1 — 进度状态与跨会话交接（state.md）

> **这是 Codex 必须持续维护的唯一进度快照。**  
> 初始化日期：2026-11-03 | 当前总状态：`BLOCKED_BY_ENV`（仅真实模型验收缺 Key；离线核心已实现并通过）  
> 三份最新工作文档统一位于 `docs/DeviceTruth_Core_V0.1_Spec_Pack/`；根目录副本已移除，仅维护当前这一套。

## A. 当前真实状态（每次完成任务后更新）

| 字段 | 当前值 |
|---|---|
| 项目 | DeviceTruth Agent Core V0.1 |
| 工作目录 | 用户已经创建的 `DeviceTruth/` 根目录（不要创建第二层 `DeviceTruth/DeviceTruth/`） |
| 文档目录 | `docs/DeviceTruth_Core_V0.1_Spec_Pack/`，唯一的 spec/task/state 工作版本 |
| 规格基线 | `spec.md v0.1.0` |
| 当前阶段 | S4 — 集成与交付 |
| 当前任务 | **T07：配置真实环境后完成 Qwen 单步及 B 端到端验收**（BLOCKED_BY_ENV） |
| 最近完成任务 | T00–T06、T08–T11；T07 实现/离线协议测试完成，仅最后在线项未勾 |
| 当前代码状态 | 20 个主源码文件、10 个测试文件；受控 Core、Qwen 适配、CLI 与报告已实现；无 Git 仓库，未初始化 Git |
| `mvn test` | `PASS`：32 项，0 失败、0 错误；最终 `mvn -o -q test` 退出 0 |
| `mvn package` | `PASS`：最终 `mvn -o -q package` 退出 0，可运行 target/devicetruth-core-0.1.0.jar |
| 离线 A/B/C/D | `PASS`：真实 JAR 和报告核验；A/B RESOLVED，C CONFLICT，D NEED_EVIDENCE |
| 在线模型 smoke | `BLOCKED_BY_ENV`（未配置 DASHSCOPE_API_KEY） |
| 数据扰动与跨领域测试 | `PASS`：三种 JSON 文件扰动、资产/证据 ID 改名、PLC 不同字段/IN 规则；Core 不变 |
| 风险 / 阻塞 | 在线缺 Key；默认 Java 17，运行前需按 README 在当前进程设置 JAVA_HOME/PATH；PowerShell 禁止执行 .ps1，使用内联命令 |
| 下一步 | 检查 DASHSCOPE_API_KEY 是否存在（不输出值）；使用 JDK 21 执行 python scripts/verify_online.py。仅真实 B 成功并核验轨迹后勾 T07、spec §10 剩余项，方可整体 DONE |
| 最后更新者 / 时间 | Codex / 2026-11-03（按用户要求归位最新文档） |

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

以上决定如需调整，必须写明“现有方案问题 → 备选方案 → 更改理由 → 对测试/接口的影响”，再同步修改 `spec.md` 和 `task.md`，绝不可无记录改变。

## C. 当前已知资产

- 工作文档：docs/DeviceTruth_Core_V0.1_Spec_Pack 三份文件已由最新开发版本替换，并移除根目录副本；后续只维护本目录这套文件。
- 现有源码/资料：pom.xml、src、skills/automotive-demo/rules.json、datasets/fixtures/a–d、README、scripts/verify_cli.py、scripts/verify_online.py、官方 API 研究资料。
- 可运行资产：target/devicetruth-core-0.1.0.jar；outputs/runs 各次报告；target/surefire-reports；target/cli-verification.json。均为可重建/运行产物。
- 工具环境：.tools/jdk-21.0.13.0.2+1；Maven 3.9.9；依赖缓存仅项目 .m2；默认全局 Java 仍为 17，未改全局设置。无 Git 仓库；DASHSCOPE_API_KEY 未配置。

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
