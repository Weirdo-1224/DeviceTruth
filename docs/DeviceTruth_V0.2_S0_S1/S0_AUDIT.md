# DeviceTruth V0.2｜S0 真实仓库审计报告

> 状态：**AUDIT_COMPLETE（2026-11-08 实测完成，事实可追溯到命令输出与源码行号）**
>
> 审计人：开发者（本会话）。执行环境：Windows 11 + Git Bash，工作目录 `D:/Java_Project/DeviceTruth`。
> 本报告只写本轮亲自执行的命令输出与亲自读取的源码，不引用历史报告结论；历史线索单独标注 `[HISTORICAL]`。
> 本轮**未写任何 Java 业务代码**，未执行 commit / push / tag / reset / clean。

## A. 仓库与安全基线

| 项目 | 实际结果 | 证据命令/路径 |
|---|---|---|
| 仓库根目录 | `D:/Java_Project/DeviceTruth`（无第二层同名目录） | `pwd` → `/d/Java_Project/DeviceTruth` |
| 当前分支 | `main` | `git rev-parse --abbrev-ref HEAD` |
| 当前 HEAD commit | `14952b6cd1d1d2fa92dc3d4d8fea82bf9291ceb0`（`docs: 将 V0.2 任务粒度从 R01~R07 合并为 M0~M3 里程碑`） | `git rev-parse HEAD`, `git log --oneline -10` |
| 现有 tag | **无**（空列表） | `git tag --list` |
| 工作树已修改文件 | **无** | `git status --short` → 空输出 |
| 未跟踪文件 | **无** | `git status --short` → 空输出 |
| 安全回退依据 | 干净工作树 + HEAD `14952b6`；本轮未建 checkpoint（未授权 commit） | 见 §H-1 与 §I |

最近提交链（本轮读取）：

```text
14952b6 docs: 将 V0.2 任务粒度从 R01~R07 合并为 M0~M3 里程碑
2d91a35 chore: 为 VS Code 终端注入 JDK 21
2a7117b docs: 引入 V0.2 S0/S1 开发文档包
4da988a chore: 忽略本机 .arts/ IDE 配置
2829c9e docs: 修订 T07 在线验收口径并判定 V0.1 DONE
```

### H-1 文档与工作树不一致（必须先记录，不得默默补写历史）

`state.md` §1 记载“当前工作树安全状态：**存在本次流程合并与 VS Code JDK 21 配置改动**”。本轮实测 `git status --short` 为空、`git stash list` 为空，这两类改动**已经分别落在 `2d91a35` 与 `14952b6` 两个提交中**。结论：该条描述已过期，工作树实际干净；本轮除本报告与文档冻结外不产生业务 diff。本报告不改写历史提交，只登记事实。

## B. 环境与基线测试（全部为本轮真实执行）

统一环境准备（Git Bash，每个命令前导，不持久化到系统）：

```bash
export JAVA_HOME="/d/Java_Project/DeviceTruth/.tools/jdk-21.0.13.0.2+1"
export PATH="$JAVA_HOME/bin:$PATH"
```

| 测试 | 实际命令 | 退出码 | 结果/证据 |
|---|---|---|---|
| Java（默认环境） | `java -version`（未设 JAVA_HOME） | 0 | `openjdk 17.0.16`（Microsoft）——**默认 JDK 是 17，不能直接用于本项目** |
| Java（项目内 JDK） | `java -version`（设上述 JAVA_HOME/PATH） | 0 | `openjdk version "21.0.13.0.2" 2026-10-22 LTS`，路径 `.tools/jdk-21.0.13.0.2+1` |
| Maven | `mvn -version` | 0 | Maven 3.9.9，`Java version: 21.0.13.0.2`，`platform encoding: UTF-8`（未设 JDK21 时为 Java 17 + GBK） |
| 单测 | `mvn -q clean test` | **0** | **42 项，0 失败、0 错误、0 跳过**；证据 `target/surefire-reports/TEST-*.xml`（12 个测试类） |
| 打包 | `mvn -q package` | **0** | `target/devicetruth-core-0.1.0.jar`，46,924,270 字节 |
| CLI A | `java -jar target/devicetruth-core-0.1.0.jar --scenario=A --reasoner=scripted` | 0 | `runStatus=COMPLETED outcome=RESOLVED steps=7`，报告 `outputs/runs/d1c80a44-cbff-4329-ae2d-e7b2ee4637c4` |
| CLI B | 同上 `--scenario=B` | 0 | `outcome=RESOLVED steps=7`，报告 `outputs/runs/2a32fa44-292b-4e70-a9a9-4f75f0d982a7`，`appliedRuleIds=[DEMO-D-120B]` |
| CLI C | 同上 `--scenario=C` | 0 | `outcome=CONFLICT steps=7`，报告 `outputs/runs/317a435b-c5fd-49ad-a045-880b68f0dbef` |
| CLI D | 同上 `--scenario=D` | 0 | `outcome=NEED_EVIDENCE steps=6`，报告 `outputs/runs/06cb6f41-04c9-4199-973d-ae24668e6bce` |
| CLI 帮助 | `... --help` | 0 | 输出 `--scenario=A\|B\|C\|D --reasoner=scripted\|qwen\|openai --max-steps=12 --max-repeated-actions=2` |
| CLI 非法参数 | `... --reasoner=invalid` | **2** | `ERROR ... reasoner must be scripted, qwen or openai`，不产生报告 |
| E 相关负例 | `mvn -q '-Dtest=AcceptanceTest#caseEUnknownToolAndForgedEvidenceNeverResolve' test` | 0 | 该 surefire 报告 `tests="1" errors="0" skipped="0" failures="0"`。**E 是测试方法，不是 CLI 场景**（`CliOptions` 只接受 A–D） |
| 打包壳验收 | `python scripts/verify_cli.py` | **0** | 见下方实测输出 |

`verify_cli.py` 本轮真实输出（离线，仅访问本机 127.0.0.1 替身；汇总写入 `target/cli-verification.json`）：

```text
{"scenario": "A", "outcome": "RESOLVED", "rules": ["DEMO-D-120A"], "steps": 7, "runId": "413a2920-7b5e-416b-997e-9001979f76ae", "events": 34}
{"scenario": "B", "outcome": "RESOLVED", "rules": ["DEMO-D-120B"], "steps": 7, "runId": "14a2b6b7-3185-4f4c-afbb-d80bb03c85b1", "events": 34}
{"scenario": "C", "outcome": "CONFLICT", "rules": [], "steps": 7, "runId": "f0acb99d-f4fc-4e4e-8f7d-d731d4d65c6e", "events": 35}
{"scenario": "D", "outcome": "NEED_EVIDENCE", "rules": [], "steps": 6, "runId": "e2968e54-941a-426f-a378-21889dc73f12", "events": 29}
PASS: qwen missing-key FAILED report, no fallback
PASS: openai missing-key FAILED report, no fallback
PASS: packaged CLI reads private configuration without environment variables (local HTTP only)
PASS: help, A/B/C/D persisted reports, invalid CLI option, missing keys, private file; D:\Java_Project\DeviceTruth\target\cli-verification.json
EXIT=0
```

测试类与项数（`target/surefire-reports`，本轮统计）：

| 测试类 | 项数 | 失败/错误/跳过 |
|---|---|---|
| `adapter.AgentScopeStepTest` | 3 | 0 |
| `adapter.ModelConfigurationTest` | 3 | 0 |
| `adapter.OpenAiReasonerTest` | 5 | 0 |
| `adapter.QwenReasonerTest` | 2 | 0 |
| `app.ApplicationTest` | 2 | 0 |
| `core.AcceptanceTest` | 5 | 0 |
| `core.DecisionGuardTest` | 2 | 0 |
| `core.EvidenceManagerTest` | 4 | 0 |
| `core.ExtensibilityTest` | 4 | 0 |
| `core.GatewayTest` | 3 | 0 |
| `core.ProtocolTest` | 5 | 0 |
| `core.RuntimeTest` | 4 | 0 |
| **合计** | **42** | **0/0/0** |

`[HISTORICAL]` 与今天的对照：V0.1 报告 42 项通过，本轮实测同为 **42 项**（`@Test` 计数 42）。A/B/C/D 的结果、步数与历史一致（7/7/7/6）；B 的报告另有 34 个事件（历史记录 34，C 为 35，D 为 29），本轮实测相同。**在线真实模型调用本轮未执行**（无授权触碰外部额度；`verify_online.py` 未运行），历史两次在线 B 只作为 `[HISTORICAL]` 记录，不作为本轮证据。

### 环境注意（实测副作用，供后续会话复用）

- 默认 `java` 指向 `C:/Users/11929/.jdks/ms-17.0.16`，即使把 `JAVA_HOME/bin` 前置到 `PATH`，`java -version` 仍解析到 17；**只有把 `JAVA_HOME` 写成 POSIX 形式** `/d/Java_Project/DeviceTruth/.tools/jdk-21.0.13.0.2+1` 时 `PATH` 前置才生效。Maven 只要 `JAVA_HOME` 正确即使用 21。
- `scripts/verify_cli.py` 通过 `Path(os.environ['JAVA_HOME'])/'bin'/'java.exe'` 定位 java，因此跑该脚本时应传 Windows 形式 `JAVA_HOME=D:/Java_Project/DeviceTruth/.tools/jdk-21.0.13.0.2+1`。两种写法本轮都实际用过。

## C. 真实源码契约（逐项核对，签名以源码为准）

| 核对项 | 实际签名或行为 | 文件位置（路径:行） | 与方案是否冲突 |
|---|---|---|---|
| CLI Main | `@SpringBootApplication public class DeviceTruthApplication`；`main` → `execute`，仅当退出码非 0 时 `System.exit` | `app/DeviceTruthApplication.java:21-28` | 无。方案称“CLI 单次入口”成立 |
| CLI 非 Web 模式 | `app.setWebApplicationType(WebApplicationType.NONE)`，`try (ConfigurableApplicationContext context = app.run(...))` 关闭上下文 | `app/DeviceTruthApplication.java:37-40` | 无 |
| CLI 退出码 | 参数错误/运行异常/`RunStatus.FAILED` → 2；正常 → 0；`--help` → 0 | `app/DeviceTruthApplication.java:33-34,55-58,60-63` | 无 |
| CLI 数据装配 | 从 classpath 读 `datasets/scenarios.json` → `Json.MAPPER.treeToValue(node, DiagnosisRequest.class)`；`JsonRepositories.fromClasspath()`；`CapabilityGateway.standard(...)`；`new TaskOrchestrator(...)`；`new RunReportWriter().write(Path.of("outputs/runs"), report)` | `app/DeviceTruthApplication.java:42-52` | 无。**全部手工 `new`，没有任何 Spring Bean** |
| Spring 注解面 | 主源码中**只有** `@SpringBootApplication` 一处，无 `@Component/@Bean/@Configuration/@Service/@Repository` | `grep -rn "@Component\|@Bean\|@Configuration" src/main/java` 仅返回上面一行 | **重要**：新增 Server 的默认扫描范围不会意外装配 CLI 逻辑，但也意味着 Server 必须自己显式装配 |
| pom 依赖 | parent `spring-boot-starter-parent:3.5.16`，`java.version=21`，`agentscope.version=2.0.4`；依赖 = `spring-boot-starter`、`jackson-databind`、`jackson-datatype-jsr310`、`agentscope-core`、`agentscope-extensions-model-dashscope`、`agentscope-extensions-model-openai`、`spring-boot-starter-test`(test) | `pom.xml:5-18` | **无 `spring-boot-starter-web`**，M1 必须新增 |
| boot plugin Main | `<plugin>spring-boot-maven-plugin</plugin>` **没有任何 `<configuration>`，未设置 `mainClass`**；打包结果 `Main-Class: org.springframework.boot.loader.launch.JarLauncher`、`Start-Class: io.devicetruth.app.DeviceTruthApplication`（由 Maven 插件自动探测唯一主类） | `pom.xml:23`；`unzip -p target/…jar META-INF/MANIFEST.MF` | **风险成立**：一旦出现第二个 `@SpringBootApplication`，插件自动探测会失败/不确定，M1 必须显式固定 `mainClass` |
| 资源打包 | `<resource>` 打入 `src/main/resources`、`datasets`→`datasets`、`skills`→`skills` | `pom.xml:19-23` | 无。Server 可继续用 classpath 资源 |
| 测试 JVM 临时目录 | surefire `argLine=-Djava.io.tmpdir=${project.build.directory}` | `pom.xml:24-27` | 无 |
| `TaskOrchestrator.run()` | `public RunReport run(DiagnosisRequest request, Budget budget, Reasoner reasoner)`；内部 `var manager=new StateManager(request,budget); var events=new ArrayList<RunEvent>()`；`while(manager.snapshot().status()==RunStatus.ACTIVE)` | `core/runtime/TaskOrchestrator.java:41-45` | 无。**`StateManager` 在 run() 内创建，TaskId 在外部无法预知** |
| RunReport | `record RunReport(DiagnosisResult result, DiagnosisState state, List<EvidenceRecord> evidence, List<RunEvent> events)` | `core/runtime/TaskOrchestrator.java:29-31` | 无 |
| RunEvent | `record RunEvent(int sequence, Instant recordedAt, String type, long stateVersion, Map<String,Object> data)`，`data=Map.copyOf(data)` | `core/runtime/TaskOrchestrator.java:25-27` | 无 |
| 真实事件类型 | **源码识别 11 类**：`REASON_INPUT, MODEL_ATTEMPT, ACTION_PROPOSED, CAPABILITY_RESULT, HYPOTHESIS_INVALIDATED, RULE_SELECTION_INVALIDATED, GUARD_RESULT, GUARD_REVIEW, STATE_COMMITTED, TECHNICAL_FAILURE, TERMINATED`。**没有 `CAPABILITY_CALLED`**。动态覆盖要分开表述：scripted B 的 34 条事件只触发其中 **8 类**（`REASON_INPUT, ACTION_PROPOSED, CAPABILITY_RESULT, HYPOTHESIS_INVALIDATED, GUARD_REVIEW, GUARD_RESULT, STATE_COMMITTED, TERMINATED`）；`MODEL_ATTEMPT` 由 `AcceptanceTest#invalidModelJsonIsFailedWithTwoAttemptsAndNoCapabilityExecution`(2 条) 与 `OpenAiReasonerTest#localHttpModelDrivesScenarioBThroughGatewayEvidenceAndGuard`(7 条) 断言，`RULE_SELECTION_INVALIDATED` 由 `AcceptanceTest#provisionalRuleAndBaselineHypothesisAreInvalidatedWhenHistoryArrives` 断言，`TECHNICAL_FAILURE` 由 §K 的临时探针实跑产生 | 赋值点源码 `TaskOrchestrator.java:49,58,62,64,88,96,101,119,153,165,174`；B 的 `events.jsonl` 逐条核对；测试断言见 §K | 无。方案“不得虚构 `CAPABILITY_CALLED`”已被事实支持 |
| `RunEvent.data` 敏感度 | `REASON_INPUT` 的 data 为 `{"context": <Reasoner.Context>, "reasoner": name}`，**Context 含完整 DiagnosisState、能力目录、全部已取回证据**；`MODEL_ATTEMPT` 为 `{"attempt": Attempt(number,outcome)}`（仅序号与结果码）；`STATE_COMMITTED` 含 facts/hypotheses/needs；`TERMINATED` 含整个 DiagnosisResult | `TaskOrchestrator.java:48-49,62,165,174` | 无。**API 必须白名单，绝不能透传 `data`** |
| `DiagnosisResult` | `record DiagnosisResult(String taskId, Outcome outcome, String assetId, Instant asOf, String diagnosisSummary, List<String> appliedRuleIds, List<String> supportingEvidenceIds, List<EvidenceNeed> missingEvidenceNeeds, List<String> warnings, String stopReason, int stepsUsed, long stateVersion, String reasoner, String dataNature, RunStatus runStatus)` | `core/contract/Models.java:119-127` | 无。方案要求的 `outcome/stepsUsed/stopReason/supportingEvidenceIds` 全部存在 |
| `RunStatus` / `Outcome` | `enum RunStatus { ACTIVE, COMPLETED, FAILED }`；`enum Outcome { RESOLVED, NEED_EVIDENCE, CONFLICT, ESCALATE }` | `core/contract/Models.java:11-12` | 无。**终局 `COMPLETED` 可携带 `CONFLICT/NEED_EVIDENCE/ESCALATE`**；`FAILED` 时 `outcome` 为 null（实测见 B 缺 Key 的报告） |
| `DiagnosisRequest` / `asOf` | `record DiagnosisRequest(String assetId, String domain, String symptomCode, Instant asOf)`，构造器用 `Checks.id` 校验三个 ID 与必填 `asOf` | `core/contract/Models.java:20-24` | 无。**没有 `requestedAt` 字段**，双时间必须在应用层加 |
| `Budget` | `record Budget(int maxSteps,int maxRepeatedActions)`，`maxSteps` 1..200 且重复上限不超过步数；`defaults()=new Budget(12,2)` | `core/contract/Models.java:26-32` | 无 |
| `StateManager` 创建位置 | `public StateManager(DiagnosisRequest request,Budget budget)` → `DiagnosisState.initial(UUID.randomUUID().toString(), request, budget)`；`commit(...)` 校验 `expectedVersion` 与 `RunStatus.ACTIVE`，证据只能新增 | `core/state/StateManager.java:16-39` | 无。**`coreTaskId` 只能在运行结束后从 `report.state().taskId()`/`result.taskId()` 取得** |
| 报告目录 ID | `RunReportWriter.write(Path outputRoot, RunReport)` → `outputRoot.resolve(UUID.randomUUID().toString())`，写 `result.json/final_state.json/evidence.json/events.jsonl` | `adapter/RunReportWriter.java:13-23` | **需注意**：报告目录 UUID 与 `taskId` 是**两个不同 UUID**，所以 `coreTaskId`、`reportPath` 必须分别记录 |
| `AssetRepository` | `List<EvidenceRecord> profile(String assetId)`；`List<EvidenceRecord> history(String assetId, String componentId)` | `core/capability/Repositories.java:10-13` | 无。**确无 `domainOf/listAssets/exists`**；返回的是原始 `EvidenceRecord`（含 evidenceId/sourceRef/sourceType） |
| `RuleRepository` | `List<Rule> query(String domain, String symptomCode)` | `core/capability/Repositories.java:20` | 无。**确无 `listSymptoms`** |
| `EvidenceRepository` | `List<EvidenceMetadata> search(String assetId,String componentId,String sourceType)`；`EvidenceRecord get(String assetId,String evidenceId)` | `core/capability/Repositories.java:15-18` | 无 |
| JSON 实现过滤语义 | `profile()` = 该资产全部 `sourceType=="FACTORY"` 记录；`history()` = `sourceType=="REPLACEMENT"` 且 `componentId==null || componentId==payload.subject`；`search()` 同规则；未知资产抛 `QueryException("ASSET_NOT_FOUND")` | `adapter/JsonRepositories.java:59-77` | 无。**QUERY 可直接复用，不需要伪造 DiagnosisRequest** |
| `CapabilityGateway.execute` | `CapabilityResult execute(DiagnosisRequest scope, CallCapability call)`：校验已注册、`readOnly`、`descriptor.domain()=="*"‖==scope.domain()`、必填/未知参数、参数 ID 正则、`assetId==scope.assetId()`、`domain==scope.domain()`、`symptomCode==scope.symptomCode()`，并校验返回记录 assetId 不越界；异常统一转 `ToolError` | `core/capability/CapabilityGateway.java:29-57` | 无。**确需完整诊断 Scope**，事实查询不得借用 |
| 标准能力集 | `standard(assets,evidence,rules)` 注册 5 个只读能力：`asset.profile.get`、`asset.history.query`、`evidence.search`、`evidence.get`、`diagnosis.rules.query` | `core/capability/CapabilityGateway.java:68-81` | 无 |
| Reasoner 可变状态 | `StructuredReasoner`：`private final List<Attempt> attempts=new ArrayList<>()`，`propose()` 读写、`drainAttempts()` 清空，**均未同步**；`OpenAiReasoner.LiveCompletion`：`private AgentScopeStep step` 懒初始化、非 volatile，构造时 `ModelConfiguration.local()`；`QwenReasoner.LiveCompletion`：`configuration` + `step` 懒初始化；`ModelConfiguration`：`private Properties properties` 懒加载、相对路径 `devicetruth.local.properties`、读 CWD | `adapter/StructuredReasoner.java:16,45-65`；`adapter/OpenAiReasoner.java:8-32`；`adapter/QwenReasoner.java:9-23`；`adapter/ModelConfiguration.java:18-39` | 无。**确认不能 Singleton 并发共享**；`ScriptedReasoner` 无可变字段（`core/reasoning/ScriptedReasoner.java` 全文按状态推导动作） |
| 五类 `ActionProposal` | `CALL_CAPABILITY / PROPOSE_HYPOTHESIS / PROPOSE_DECISION / REQUEST_INFORMATION / ESCALATE`，多态按 `"type"` | `core/reasoning/ActionProposal.java:11-64` | 无 |
| `Reasoner.Context` | `record Context(DiagnosisState state, List<Descriptor> capabilities, List<EvidenceRecord> evidence)` | `core/reasoning/Reasoner.java:18-20` | 无。与“不可外泄”直接相关 |
| `Checks.id` 正则 | `[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}`（字母数字开头，最长 128） | `core/contract/Checks.java:15-19` | 无。可作为 HTTP 入参 ID 校验的既有口径 |
| 数据集 | `datasets/fixtures/{a,b,c,d}/asset.json` + `skills/automotive-demo/rules.json`（`RulePack.dataNature` 与 `Dataset.dataNature` 构造时强制 `SIMULATED`） | `adapter/JsonRepositories.java:16-28,44-48` | 无 |

## D. 数据与时间语义（实测 fixture）

- `CAR-B / COMM_TIMEOUT` **真实存在**：`datasets/scenarios.json` 的 `B.assetId=CAR-B`、`domain=automotive-demo`、`symptomCode=COMM_TIMEOUT`、`asOf=2026-11-03T04:00:00Z`。
- **A/B/C/D 四个场景的 `domain`、`symptomCode`、`asOf` 完全相同**（`automotive-demo` / `COMM_TIMEOUT` / `2026-11-03T04:00:00Z`），只有 `assetId` 不同（`CAR-A/B/C/D`）。这一点此前文档未逐字段写明，M1 的 Catalog 可直接由该文件派生出 `SupportedDiagnosis(assetId, domain, symptomCode, diagnosisAsOf)`。
- `CAR-B-FIELD-1`：`sourceType=FIELD_CONFIRMATION`、`sourceRef=simulated:CAR-B/FIELD-1`、`recordedAt=2026-11-03T03:00:00Z`、`observedAt=2026-11-03T03:00:00Z`、`validTo=2026-11-04T00:00:00Z`、`payload={CTRL-01,moduleVersion,D-120B}`。**与方案 §S0-04 记录一致。**
- 有效区间语义由 `EvidenceRecord.validAt()` 实现：`!recordedAt.isAfter(time) && !validFrom().isAfter(time) && (validTo==null || time.isBefore(validTo))`，即半开区间 `[validFrom, validTo)`（`core/contract/Models.java:52-56`）。
- 因此若把 `diagnosisAsOf` 换成请求当天（≥2026-11-04），`CAR-B-FIELD-1` 失效，B 会从 `RESOLVED` 退化为 `NEED_EVIDENCE`。**双时间分离是必需的，不是可选优化。**
- 逐场景记录（本轮读取真实 JSON）：

| 场景 | assetId | 记录（evidenceId / sourceType / 值） |
|---|---|---|
| A | CAR-A | `CAR-A-FACTORY` FACTORY D-120A；`CAR-A-FIELD-1` FIELD_CONFIRMATION D-120A（obs 2026-11-03T03:00Z，validTo 2026-11-04T00:00Z） |
| B | CAR-B | `CAR-B-FACTORY` FACTORY D-120A；`CAR-B-REPLACE` REPLACEMENT D-120B（recordedAt 2026-06-01）；`CAR-B-FIELD-1` FIELD_CONFIRMATION D-120B |
| C | CAR-C | `CAR-C-FACTORY` FACTORY D-120A；`CAR-C-FIELD-1` FIELD_CONFIRMATION D-120A；`CAR-C-FIELD-2` FIELD_CONFIRMATION D-120B（同级同权冲突） |
| D | CAR-D | `CAR-D-FACTORY` FACTORY D-120A；`CAR-D-REPLACE` REPLACEMENT D-120B；**无 FIELD_CONFIRMATION** |

- 规则包 `skills/automotive-demo/rules.json` 只有两条规则，均 `domain=automotive-demo`、`symptomCode=COMM_TIMEOUT`、`allowedAdviceLevel=SIMULATION_ONLY`：`DEMO-D-120A`（条件 CTRL-01.moduleVersion EQ D-120A）、`DEMO-D-120B`（EQ D-120B），都要求 `FIELD_CONFIRMATION`。**没有其它领域、没有其它症状码。**
- `CAR-B`（汽车演示）与 `SC-0245-B`（工业）在本仓库无任何数据交集：`src/ datasets/ skills/ scripts/ pom.xml` 中检索不到 `SC-0245`（该字符串只出现在 `docs/` 方案文档里）。方案“不可混用”成立，且 S1 无工业数据可用。
- 未知资产 `SN-0245`：`JsonRepositories.asset()` 抛 `QueryException("ASSET_NOT_FOUND")`，不会回落到任何 CAR-*。**不存在“SN-0245 被映射到 CAR-B”的隐患**，但 M1 仍需在 Catalog 层显式拒绝。

## E. 假设调查缺口（实测结论，不是推测）

- 存在 `PROPOSE_HYPOTHESIS`：`ActionProposal.ProposeHypothesis`（`core/reasoning/ActionProposal.java:31-40`），由 `TaskOrchestrator` 处理并创建 `Hypothesis(..., HypothesisStatus.OPEN, null)`（`TaskOrchestrator.java:109-115`）。`ScriptedReasoner` 确实会在有成立事实且无假设时提出一次假设（`ScriptedReasoner.java:24-28`）。
- **`HypothesisStatus.SUPPORTED` 在主源码中从未被赋值**：全源码检索 `HypothesisStatus.` 只有三处使用——`DecisionGuard.java:34`（拒绝 INVALIDATED）、`EvidenceManager.java:73,76`（保持/置为 INVALIDATED）、`TaskOrchestrator.java:95,114`（事件与 OPEN）。**因此“OPEN → SUPPORTED”通路当前不存在**。
- `INVALIDATED` 规则：`EvidenceManager.invalidateDependencies(...)` 在依赖事实/证据被取代、争议或超出任务时间时置 INVALIDATED 并写原因（`core/evidence/EvidenceManager.java:68-78`）。
- 实测 B：`final_state.json` 中假设状态为 `INVALIDATED`，事件流第 17 条为 `HYPOTHESIS_INVALIDATED`；脚本推理器随后改用 `DEMO-D-120B` 结案。**能证明的是“假设可创建、可因新证据失效”，不能证明“假设被支持并驱动因果结论”。**
- 结论：Hypothesis 闭环缺口**确认存在且比文档描述更明确**（不是“验证不充分”，而是 `SUPPORTED` 根本没有赋值路径）。按方案 §S0-08 登记到 S2，S1 不实现、不放宽 Guard。

## F. 启动方案与已冻结契约（细节见 `spec.md` §2、§7）

1. **旧 CLI fat JAR 主类**：当前由 `spring-boot-maven-plugin` 自动探测（`Start-Class=io.devicetruth.app.DeviceTruthApplication`）。M1 出现第二个 Main 前**必须**在 `pom.xml` 显式 `<configuration><mainClass>io.devicetruth.app.DeviceTruthApplication</mainClass></configuration>`；本轮不改代码，因此该风险以“M1 必做+必验”登记，不作为本轮已验证事实。
2. **Server 独立 Main**：新增 `io.devicetruth.server.DeviceTruthServerApplication`。因主源码无任何 Spring 组件注解，且 `DeviceTruthApplication` 的扫描根是 `io.devicetruth.app`，新 Server 的默认扫描根将是 `io.devicetruth.server`，**不存在把 CLI 生命周期注入 Web 的路径**。Server 侧推荐在 `io.devicetruth.server` 包内用一个 `@Configuration` 显式装配 Repository/Gateway/Catalog/Service 实例。
3. **Server 启动命令**：M1 需真实验证 `mvn -q spring-boot:run '-Dspring-boot.run.main-class=io.devicetruth.server.DeviceTruthServerApplication'`（插件 3.5.16 支持该参数，但**本轮未验证**，因为尚无 Server 类）。M1 必须记录真实启动日志、端口与健康检查结果；若该参数不可用，改用 `java -cp target/classes:target/dependency/*` 方式并同样记录。
4. **Repository 缺口**：确无 `domainOf/listAssets/listSymptoms/exists`。Catalog 只能由 `datasets/scenarios.json` 派生，规则存在性只能用 `RuleRepository.query(domain,symptomCode)` 验证。
5. **运行/Session 限制**：见 `spec.md` §8（有界、内存、loopback、无鉴权）。
6. **ID 关系**：`diagnosisId`（应用层新生成）↔ `coreTaskId`（运行结束后 `report.state().taskId()`）↔ `reportPath`（`RunReportWriter` 返回的目录，含独立 UUID）。三者都必须分别保存映射。

## G. M0 交付与 Codex Review

- DeepSeek（开发者）自查是否完成：**是**——A–F 全部填实，命令与源码行号可复核。
- 强制离线门禁：`java -version`(21) / `mvn -version` / `mvn -q clean test` / `mvn -q package` / CLI A–D / E 负例 / `verify_cli.py` **全部真实通过（退出码均为 0，非法参数为 2）**。
- Codex Review：`reviews/M0.md`（**待生成**，同时复核审计事实与契约冻结）。
- M0 状态：**REVIEW_PENDING**。
- 可以进入 M1：**否，等待 M0 独立 Review PASS**。

## H. 与方案文本的逐条核对（修订版 §12 的 18 条）

| # | 方案主张 | 本轮审计结论 |
|---|---|---|
| 1 | Hypothesis 缺口 | **成立**，且更明确：`SUPPORTED` 无赋值路径（§E） |
| 2 | `requestedAt`/`asOf` 混淆 | **成立**：`DiagnosisRequest` 只有 `asOf`；CLI 直接用场景固定时间 |
| 3 | Repository 无 `domainOf/listSymptoms` | **完全成立**（`Repositories.java:10-20`） |
| 4 | CLI/Web Main 生命周期冲突 | **部分成立**：当前只有一个 Main，无显式 `mainClass`；冲突是**将发生**的打包风险，M1 必须显式固定并实测 |
| 5 | QUERY 不适合 Gateway | **完全成立**：`execute()` 校验 assetId/domain/symptomCode 三域一致 |
| 6 | 三个 ID | **成立**，并补充：报告目录 UUID 与 `taskId` 不是同一个值 |
| 7 | `RunStatus` 同名/失败映射 | **成立**：`RunStatus{ACTIVE,COMPLETED,FAILED}`；`FAILED` 时 `outcome=null` |
| 8 | Reasoner 不能 Singleton | **成立**：`attempts` 无同步、`step`/`properties` 懒初始化 |
| 9 | 事件只能结束后读取 | **成立**：`events` 是 `run()` 局部变量，仅经 `RunReport` 返回 |
| 10 | 不存在 `CAPABILITY_CALLED` | **成立**：源码识别 11 类、无该类型；scripted B 实跑触发 8 类（详见 §C 与 §K） |
| 11 | POST 状态竞态 | 设计约束，M1/M3 实施并测试 |
| 12 | `sourceRefs` 混用 | 成立，真实 `sourceRef` 形如 `simulated:CAR-B/FACTORY`，与 `evidenceId` 明显不同 |
| 13 | 模拟数据措辞 | **成立**：`Dataset/RulePack` 构造强制 `SIMULATED`，`DiagnosisResult.dataNature="SIMULATED"` |
| 14 | S1 太大 | 设计约束，按 M1–M3 拆解执行 |
| 15 | 在线/离线策略 | **成立**：配置来自 CWD 私有文件 + 环境变量，客户端无从设置 |
| 16 | session UUID 非鉴权 | 设计约束，M1 实现归属校验 |
| 17 | `RunEvent.data` 泄露 | **成立且更严重**：`REASON_INPUT` 直接携带完整 `Reasoner.Context` |
| 18 | E 不是 CLI 场景 | **成立**：`CliOptions` 只接受 A–D；E 仅为 `AcceptanceTest` 方法 |

## I. 风险与最小修复建议

| 风险 | 事实依据 | 最小处置（由后续里程碑执行） |
|---|---|---|
| 第二个 Main 导致 `repackage` 选主类失败 | `pom.xml:23` 无 `mainClass` | M1 显式固定 CLI `mainClass` 后打包验证 |
| 无 `spring-boot-starter-web` | `pom.xml:12-18` | M1 仅新增该 starter，不加其它框架 |
| Server 装配方式与 CLI 不同（CLI 全手工 new） | 主源码仅一处 Spring 注解 | M1 在 `io.devicetruth.server` 内显式装配，禁止把 Bean 散落到 core/adapter |
| 事件透传泄密 | `REASON_INPUT.data.context` | M3 只映射白名单视图，`summary/referenceIds` 由允许字段生成 |
| `DiagnosisRequest` 无 `requestedAt` | `Models.java:20` | 不改 Core DTO，在应用层 `DiagnosisGoal`/`DiagnosisRunSnapshot` 承载双时间 |
| 报告目录 UUID ≠ taskId | `RunReportWriter.java:14` vs `StateManager.java:16` | 内部映射同时保存两者，API 不暴露绝对路径 |
| Hypothesis 闭环缺失被误宣传 | `SUPPORTED` 无赋值 | S2 独立验收；S1 文档与响应不得宣称假设驱动闭环 |
| 默认 JDK 17 | `java -version` | 每次开发/验收显式设置项目 JDK 21（README 已有说明） |
| 本会话产生的运行产物 | `outputs/runs/`、`target/` 均被 `.gitignore` 覆盖 | 无需清理，也不纳入交付 diff（`git status` 仍为空） |

## J. 本轮实际产生的文件变更

| 文件 | 类型 | 说明 |
|---|---|---|
| `docs/DeviceTruth_V0.2_S0_S1/S0_AUDIT.md` | 重写 | 本报告（审计事实） |
| `docs/DeviceTruth_V0.2_S0_S1/spec.md` | 修订 | 依据审计事实冻结契约，状态改为 `FROZEN_CANDIDATE` |
| `docs/DeviceTruth_V0.2_S0_S1/task.md` | 修订 | M0 → `REVIEW_PENDING`，按审计事实校正契约细节 |
| `docs/DeviceTruth_V0.2_S0_S1/state.md` | 修订 | 更新已核实事实、去掉过期工作树描述、指向 M1 待 Review 通过后启动 |

未写 Java 代码，未改 `pom.xml`，未动 Core / Guard / Orchestrator。

## K. Codex 第一轮 Review 修复记录（M0-P1-01 / M0-P2-01）

### K.1 审查结论与基线

- 审查报告：`reviews/M0.md`，2026-11-08，结论 **CHANGES_REQUIRED**，P0=0、P1=1（`M0-P1-01`）、P2=1（`M0-P2-01`）。
- 比较基线：`14952b6`（`main`），与本文 §A 一致；Codex 同时记录了被审 5 份文档的 SHA-256。
- 本轮修复**只动文档**：`spec.md`、`task.md`、`state.md`、本文件；`src/`、`pom.xml`、`scripts/`、`datasets/`、`skills/` 零差异。

### K.2 M0-P1-01 根因（逐行源码链）

| 步骤 | 真实位置 | 行为 |
|---|---|---|
| 1 | `adapter/StructuredReasoner.java:45-60` | 只校验“能解析为合法 `ActionProposal` JSON”，**不限制 `reason` 内容** |
| 2 | `core/reasoning/ActionProposal.java:55-58,62-63` | `RequestInformation.reason`、`Escalate.reason` 仅过 `Checks.text`（拒绝 null/空白） |
| 3 | `core/runtime/TaskOrchestrator.java:139,141` | `reason=proposal.reason()` 直接使用模型自由文本 |
| 4 | `core/runtime/TaskOrchestrator.java:169-174` | 该值写入 `DiagnosisResult.stopReason`，同时 `diagnosisSummary="Investigation stopped: "+reason`，并把整份 `result` 放进 `TERMINATED.data` |
| 5 | `core/runtime/TaskOrchestrator.java:137` | `RequestInformation.missingFields` 被写成 `NeedSource.AGENT` 的 `EvidenceNeed.description` |

因此原冻结契约中 `TERMINATED` 摘要 `运行结束：<outcome>/<stopReason>` 与 GET 的 `stopReason` 会原样回显任意合法模型文本（例如路径或上下文片段），与同一节的“禁止绝对路径/模型原始输出”冲突。`errorCode`（FAILED 时取 `stopReason`）同样受影响。

### K.3 真实复现（真实 Core，非推测、非伪造）

临时探针放在 `target/probe/`（被 `.gitignore` 覆盖，且随后 `mvn -q clean` 已物理删除），用真实 `JsonRepositories` + 真实 `TaskOrchestrator` 跑 CAR-B：

```text
java -cp "target/classes;target/probe/BOOT-INF/lib/*" target/probe/StopReasonProbe.java
EXIT=0
--- ESCALATE ---
runStatus=COMPLETED outcome=ESCALATE
stopReason=review-only C:\review-fixture\context.txt PROMPT_FRAGMENT
eventTypes=[ACTION_PROPOSED, REASON_INPUT, STATE_COMMITTED, TERMINATED]
markLeakedInResult=true
markLeakedInTerminatedEvent=true
--- REQUEST_INFORMATION ---
runStatus=COMPLETED outcome=NEED_EVIDENCE
stopReason=review-only C:\review-fixture\context.txt PROMPT_FRAGMENT
need=NEED-AGENT-1 source=AGENT desc=review-only C:\review-fixture\context.txt PROMPT_FRAGMENT
eventTypes=[ACTION_PROPOSED, REASON_INPUT, STATE_COMMITTED, TERMINATED]
markLeakedInResult=true
markLeakedInTerminatedEvent=true
--- REASONER_FAILURE ---
runStatus=FAILED outcome=null
stopReason=DASHSCOPE_API_KEY_MISSING
eventTypes=[REASON_INPUT, TECHNICAL_FAILURE, TERMINATED]
markLeakedInResult=false
markLeakedInTerminatedEvent=false
```

复现结论：**P1 成立**，且泄漏面比 Review 描述多两处同源派生字段——`diagnosisSummary`（`"Investigation stopped: "+reason`）与 `NEED-AGENT-*` 的 `description`（模型 `missingFields`）。第三组同时证明 `TECHNICAL_FAILURE` 事件确实会动态产生（P2 相关）。

### K.4 最小修改（仅公开视图契约，0 行业务代码）

| 文件 | 修改 |
|---|---|
| `spec.md` §4.8（新增） | `stopReason`/`errorCode` 改用 **28 项显式白名单**，未知值 → `STOP_REASON_WITHHELD` / `RUN_FAILED_WITHHELD_REASON`；`diagnosisSummary` 仅在采纳规则时输出打包规则文案；`warnings` 限两种形状；缺口只返回 `openNeeds[{needId,status,relatedRuleId?}]`（不返回 `description`）；`TERMINATED` 摘要用过滤后的值；明文拒绝“大写形状正则”式弱校验 |
| `spec.md` §4.6/§4.7/§6 | `errorCode`、`TERMINATED` 摘要、GET 字段改为引用 §4.8，并禁止直接拼接 Core 的 `DiagnosisResult` |
| `spec.md` §10 | 增补本轮 P1/P2 变更行（批准人：用户本轮要求；状态仍 `FROZEN_CANDIDATE`） |
| `task.md` M3 冻结补充 | 增加必测项：用含合成路径与 `PROMPT_FRAGMENT` 的合法 `ESCALATE`/`REQUEST_INFORMATION` 提案跑真实 Core，断言 GET 结果与 `/events` 均不回显该标记，白名单内停止码原样返回 |
| Core / Guard / Orchestrator / EvidenceManager / pom | **零改动**。原始 `stopReason` 继续保留在 Core 报告与 `outputs/runs` 中 |

### K.5 M0-P2-01 措辞修正

- 依据：本轮 §B 的 B 报告 `2a32fa44…` 与 Codex 的 `c999f220…` 均为 **34 条事件、8 类**；本文旧版把“源码类型集合 11 类”写成了“实测 11 种”。
- 修正后表述：源码识别 11 类（§C 行内列出赋值行号）；scripted B 实跑触发 8 类；其余 3 类的覆盖证据——
  - `MODEL_ATTEMPT`：`AcceptanceTest#invalidModelJsonIsFailedWithTwoAttemptsAndNoCapabilityExecution`（断言 2 条）、`OpenAiReasonerTest#localHttpModelDrivesScenarioBThroughGatewayEvidenceAndGuard`（断言 7 条）；
  - `RULE_SELECTION_INVALIDATED`：`AcceptanceTest#provisionalRuleAndBaselineHypothesisAreInvalidatedWhenHistoryArrives`；
  - `TECHNICAL_FAILURE`：§K.3 探针实跑 `eventTypes` 含该类型。
- 已同步 `spec.md` §2 与 `state.md` §4，避免再次混用“静态类型集合”和“动态覆盖”。

### K.6 修复后门禁（2026-11-08 本轮实测）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `mvn -q clean test`（`JAVA_HOME=.tools/jdk-21.0.13.0.2+1`） | **0** | **42 项，failures=0 errors=0 skipped=0**（`target/surefire-reports/TEST-*.xml`） |
| `mvn -q package` | **0** | `target/devicetruth-core-0.1.0.jar`，46,924,270 字节；`Start-Class: io.devicetruth.app.DeviceTruthApplication` |
| `python scripts/verify_cli.py` | **0** | A `RESOLVED/DEMO-D-120A/7/34`（`42fcaf86-0278-45c3-939f-61c3909f5421`）、B `RESOLVED/DEMO-D-120B/7/34`（`7f559904-f150-448d-8fbf-2d7dd9079bee`）、C `CONFLICT/7/35`（`90cb4595-fb59-42a9-8188-899964e6beac`）、D `NEED_EVIDENCE/6/29`（`4eb34b56-c6e0-4d8b-bab8-5e2741f26878`）；帮助、非法参数、两种缺 Key FAILED、私有配置本机 HTTP 均 PASS |
| `mvn -q '-Dtest=AcceptanceTest#caseEUnknownToolAndForgedEvidenceNeverResolve' test` | **0** | 1 项，0 失败/错误/跳过（E 仍只是测试方法） |
| `unzip -p ... META-INF/MANIFEST.MF` | 0 | `Start-Class` 仍为 CLI 主类，旧 CLI 入口未退化 |

`mvn -q clean` 同时清除了 `target/probe/`，确认临时探针没有留下仓库内文件；`git status --short` 只显示 5 份文档（4 份修订 + 使用说明），无源码/pom 差异。

**未验证（不得当作已修复）**：§4.8 的应用层过滤实现与其安全回归属于 M3（当前无 HTTP 代码）；`scripts/verify_online.py` 与任何外部模型调用本轮未运行。P1 的契约问题已闭合，但**实现层**仍需 M3 用上述必测项证明。
