# 01-01 当前源码与运行基线

核验日期：2026-11-09（Asia/Shanghai）。本次授权为“执行 01-01-PLAN.md”，不包含 01-02、旧 S2、部署、Git 提交或线上模型调用。

## 起点与数据性质

- 当前 HEAD：`a9607cf7b1631c221a9f9865036751642772e352`。开始时 `git status --short` 为空；保留全部已有设计/规划文件。
- 前序提交：a9607cf（PLAN 可读性整理）、17854b2（V1.0 设计与规划）、b7befa0（现状报告）。规划记录的 b7befa0 是历史源码起点，不能冒充当前 HEAD。
- 指定产品设计 SHA256：`C80A4C9292787B181DDB2406CAE4746B83F602993B22EAAA8C2C42AF5D698120`，本轮复核未变。
- Java 主源码 52 个文件，继承测试 28 个文件；本计划仅新增 1 个测试文件和固定 JSON 夹具，生产实现/依赖/fixture 不变。
- A–D、E 和新原型使用虚构 `SIMULATED` 数据；本机 HTTP 模型替身不是外部真实模型。历史两次 DeepSeek 结果仅作历史记录。

## 环境和已执行命令

每个 Maven/Python 进程使用下列进程级配置；没有改全局环境或 PowerShell 执行策略：

```powershell
$taskJdk = Get-ChildItem -Directory .tools -Filter 'jdk-21*' | Select-Object -First 1
$env:JAVA_HOME = $taskJdk.FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -version
```

结果：Maven 3.9.9、Microsoft JDK 21.0.13.0.2、Windows 11 amd64、UTF-8。pom 仍锁定 Spring Boot 3.5.16 / AgentScope 2.0.4；没有安装或升级依赖。

| 实际命令 | 退出码 | 本轮结果 |
|---|---|---|
| git status --short / git rev-parse HEAD / git log -3 --oneline | 0 | 起点干净，HEAD 如上；Git 全局 ignore 路径权限提示不影响只读核对 |
| mvn -version | 0 | 实际 JDK21/Maven3.9.9 |
| mvn -q test（修改前） | 0 | 145 tests / 0 failures / 0 errors / 0 skipped，28 suites |
| mvn -q package（修改前） | 0 | 测试通过并重建当前 CLI JAR |
| python scripts/verify_cli.py（基于该新 JAR） | 0 | help、A–D、非法参数、双供应商缺 Key、私有文件本机 HTTP 合同全部通过 |
| mvn -q '-Dtest=RuntimeComparisonTest' clean test（原型首轮） | 1 | SDK fake 工具块缺原始 content，未通过；修正见 RUNTIME-SPIKE |
| mvn -q '-Dtest=RuntimeComparisonTest' test（诊断两次） | 1 / 1 | 确认 SDK 参数校验缺 queryKey；保留失败记录，未算 PASS |
| mvn -q '-Dtest=RuntimeComparisonTest' clean test（修正后两次） | 0 / 0 | 1 测试方法 × 6 样例 × 2 候选；两次输出逐字节一致 |
| mvn -q test（新增原型后） | 0 | 146 tests / 0 failures / 0 errors / 0 skipped，29 suites |
| mvn -q package（最终） | 0 | 146/0/0/0；生成新 JAR，Start-Class=CLI，私有文件未打包 |
| python scripts/verify_cli.py（最终 JAR 再跑） | 0 | A/B/C/D 结果及步数/事件数与首次一致；缺 Key/私有文件本机 HTTP 合同通过 |
| 独立 HTTP Main 冒烟（下方命令，Python 管理进程及断言） | 0 | /api/health=UP/SIMULATED，/actuator/health=UP，/index.html=200；结束已关闭进程 |

测试汇总来自实际 `target/surefire-reports/TEST-*.xml`，不是 Maven 安静输出的猜测。原始日志保存在忽略目录 `outputs/validation/`；target 被 clean 清理后，前轮日志/报告可按命令重建；当前结果摘要保留在本文件及 RUNTIME-SPIKE。

本轮在 `.gitignore` 追加 `outputs/validation/`，排除生成的比较JSON/校验摘要；源码、固定输入和人工整理证据仍保留在测试及规划目录。

## 当前 CLI 结果（首次新打包实测）

| 场景 | outcome | 规则 | 步数/事件数 | 报告 runId |
|---|---|---|---|---|
| A | RESOLVED | DEMO-D-120A | 7 / 34 | 952daedf-176a-4611-a17f-68406017253a |
| B | RESOLVED | DEMO-D-120B | 7 / 34 | 5bdd2bc5-0d5e-45be-bed1-64560b2d77cd |
| C | CONFLICT | 无 | 7 / 35 | 5a098ccf-7017-4a70-a007-595780ac2c57 |
| D | NEED_EVIDENCE | 无 | 6 / 29 | 50c2703b-ab46-4913-b9dd-a0e128c169ea |

以上 `runStatus=COMPLETED / dataNature=SIMULATED / reasoner=scripted`。脚本还检查状态版本=动作数、引用 ID 真实存在且归属本资产、事件序号连续、B 旧依赖失效、C contested、D 缺证。

CAR-B 脱敏结果节选：

```json
{"outcome":"RESOLVED","assetId":"CAR-B","appliedRuleIds":["DEMO-D-120B"],"stepsUsed":7,"stateVersion":7,"reasoner":"scripted","dataNature":"SIMULATED","runStatus":"COMPLETED"}
```

E 不新增 CLI 参数，由 AcceptanceTest.caseEUnknownToolAndForgedEvidenceNeverResolve 覆盖：未知工具/伪造证据均不得 RESOLVED；GatewayTest 等继承测试同时保护跨资产/参数/只读边界。本轮 test/package 真实运行这些用例，不借用历史结果。

缺 Key 检查在隔离工作目录移除模型环境变量：qwen/openai 均返回技术 FAILED，不降级 scripted。私有文件检查只写测试假 Key 并访问 127.0.0.1 模型替身，核验端点/认证且不泄漏到报告，未读取项目真实私有文件。

最终 CLI 的 CAR-B runId 为 `32f4fca8-4052-4ad8-958f-6f0fb0cc27e5`；完整本机输出位于 `outputs/validation/01-01-final-cli.log`，汇总 `target/cli-verification.json`。

## 当前入口与装配

- CLI：DeviceTruthApplication.execute 解析参数，从 scenarios/JsonRepositories 取 fixture，装配 Gateway/Reasoner，调用 TaskOrchestrator.run，再由 RunReportWriter 写报告。Spring 以 NONE 模式启动，JAR 默认 Start-Class 仍为 CLI。
- HTTP：DeviceTruthServerApplication.main 启动独立 Web 服务，ServerConfiguration 显式装配并扫描 api；ConversationController→ConversationFlowService→DiagnosisRunService 有界线程池→同一旧 Core。不是 CLI 启动后附带一个 Web 服务。
- `application.properties` 默认 `router.mode=offline`、`core.reasoner=openai`：前者不调用 Router 模型，后者诊断选择 OpenAI-compatible Reasoner；不能将其描述为“HTTP 诊断默认离线”。默认绑定 127.0.0.1:8080。
- pom 含 Actuator；现有 ServerHttpTest/DiagnosisHttpTest 在随机端口验证旧 HTTP 行为。`/api/health` 是自定义 UP/SIMULATED，并不证明模型、数据库健康。
- 当前没有 PG/RAG/标准 Skill/Vue/生产 WAITING；原型不是这些功能的交付。

HTTP 冒烟使用实际打包 JAR 的 PropertiesLauncher，等效启动命令如下（本轮端口由本机空闲端口探测分配，不修改配置默认端口）：

```powershell
java '-Dloader.main=io.devicetruth.server.DeviceTruthServerApplication' -cp target/devicetruth-core-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --server.port=<空闲端口> --debug=false --logging.level.org.springframework=INFO
```

Python 启动上述子进程，以 HTTP GET 断言三个地址并在 finally 中终止/等待该进程。包内 properties 同时断言 offline/openai；未提交诊断，不触发外部模型。结果摘要 `target/http-baseline.json`；启动日志 `outputs/validation/01-01-http.log`。

## 服务端配置策略（本计划冻结）

继承模型的私有配置文件兼容保留：文件非空值优先、环境补缺；文件被忽略且不打包。新 PG 使用服务端 `SPRING_DATASOURCE_*`，新 embedding 使用服务端 `DEVICETRUTH_EMBEDDING_*`，不加入现有模型私有文件，也不暴露前端；配置键/脱敏规则详见 [CONTRACTS §8](CONTRACTS.md#8-服务端配置与-legacy-兼容)。没有修改读取逻辑或访问真实私密值。新配置实际加载/脱敏测试留后续实现。

## 未验证项

没有本轮外部 Qwen/DeepSeek/OpenAI 请求，没有 PG/pgvector、真实 embedding/RAG、前端、重启/租约/CAS/幂等/取消或全量 V1.0 E2E。没有独立 Review、提交、推送或部署。不能用 146 项离线通过说明这些产品能力已经通过。
