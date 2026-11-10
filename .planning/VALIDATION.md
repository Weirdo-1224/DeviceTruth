# 验证记录与后续验收策略

## 自动验收策略落实（2026-11-10，最新）

按[ACCEPTANCE](ACCEPTANCE.md)使用GSD1.20.1原生coverage，不修改全局文件/config开关；4项自动/1项人工分类通过，5个分类正负探针通过。策略调整后再次实际package 154/0/0/0，CLI与打包PG双进程重启/无DB CLI均通过。既有用户CLI B确认保留，技术项不再逐项提示；仅剩一次产品/设计集中确认。见[简洁报告](phases/01-baseline-contracts/01-ACCEPTANCE.md)和[01-UAT](phases/01-baseline-contracts/01-UAT.md)。

## Phase 1 独立Review、修复与复验（2026-11-10，本轮）

最新结果见[01-VALIDATION](phases/01-baseline-contracts/01-VALIDATION.md)：独立Review初审CR-01真实HTTP/PG复现后修复，独立复审clean；clean test/package均154/0/0/0（PG8、旧HTTP16），CLI、两次独立Java进程原文/同run QUEUED读回及Compose通过。原业务库public表17、随机测试库0。逐项用户确认见[01-UAT](phases/01-baseline-contracts/01-UAT.md)，尚待完成；阶段未关闭、Git未提交、02-01未执行。用户已授权出口满足后提交Git并仅执行02-01，不执行02-02/02-03。下方51项/153项等记录保留为先前验证历史，不覆盖本轮结果。

## Phase 01 COVERAGE 补齐与检查（2026-11-10）

用户要求先补齐`.planning/phases/01-baseline-contracts/COVERAGE.md`，说明API／SDK已接入与明确不接入的能力、理由及实现／测试证据，通过检查后再逐项用户验收。本次已完成[覆盖表](phases/01-baseline-contracts/COVERAGE.md)，只修订文档和验收入口，没有修改业务源码／测试／依赖／迁移／数据，没有执行后续PLAN或提交Git。

- 按产品设计§7.3逐条核对9条建议API，并补充实际已存在的会话／消息列表2条：6条已接入M0存储切片、5条后续接入；区分旧sessions/diagnoses兼容与新conversations/runs。
- 记录10类SDK与3类数据库集成取舍，明确Model API已接入、ReAct仅测试原型、生产循环／权威状态／工具授权由应用控制；没有把vector扩展或schema预留算作RAG／执行恢复。
- 对应7项M0需求及20个证据ID；明确指出消息列表成功路径、contextRefs／NUL／显式asOf等分支尚无直接断言；未接入API没有行为测试，不用源码扫描冒充通过。
- `mvn -version`：Maven3.9.9／Microsoft JDK21.0.13.0.2。
- `mvn -q '-Dtest=RuntimeComparisonTest,AgentScopeStepTest,OpenAiReasonerTest,QwenReasonerTest,ModelConfigurationTest,AcceptanceTest,GatewayTest,ServerHttpTest,DiagnosisHttpTest,QueryHttpTest,DiagnosisSafetyMappingTest' test`退出0；仅汇总本次11个suite的Surefire XML，**51/0/0/0**。原型1个JUnit内执行6样例×2候选，不把12次内部执行重复计为JUnit数量。
- `python outputs/validation/01-coverage-check.py`退出0：核对矩阵ID／证据ID、6条已接入与5条未接入API的源码路由集合、测试及生产方法、本地链接／UTF-8、阶段需求、设计及fixture hash；核验前后109份业务文件摘要未变。脚本及摘要保存在忽略目录outputs/validation/01-coverage-check.*，不改写历史planning-validation.json。
- `git diff --check`退出0。同步STATE／NEXT_SESSION／ROADMAP及规划目录的覆盖入口；完成计划仍2/13、完成阶段仍0/5、review_pending及后续未授权边界不变。

本次PG测试环境变量未配置，未重跑真实PG、全量test/package、打包CLI或跨进程重启；没有外部LLM／embedding／RAG／UI验收。下面2026-11-09的真实PG7项、全量153项及打包结果作为有日期的历史证据引用，不冒称本次运行。独立Review及逐项用户验收尚未进行，覆盖文档检查通过不等于它们通过。

## 01-02 实施验收（2026-11-09，历史已验证）

用户授权执行01-02并明确复用interview-postgres；交付详见[01-02-SUMMARY](phases/01-baseline-contracts/01-02-SUMMARY.md)。本轮保留前次6份规划修改，不提交Git，不自动执行后续计划。

- 当前数据库为既有PG16.15/pgvector0.8.7、5432；启动用户指定容器，创建独立devicetruth库。interview_guide仅做只读基线检查，public表17张；未在其业务表执行DDL/DML。
- 初次未盘点本地镜像就拉PG17导致ContainerFetch失败，业务测试未开始；用户纠正后停止拉取，移除临时Testcontainers依赖，改用明确配置的真实PG。失败日志保留在outputs/validation/01-02-pg-test.log，不能计PASS。
- 首次新增依赖联网`mvn -q -U -DskipTests test-compile`退出0；固定Spring JDBC6.2.19/PG JDBC42.7.11/Flyway11.7.2/Hikari6.3.3，使用现有Boot3.5.16管理版本。
- `mvn -q '-Dtest=PersistentConversationTest' test`退出0：7/0/0/0，真实PG空库/重复迁移、重启读取、原文及Unicode、重复与并发、回滚、owner/project与数据库约束断言均执行。测试只创建并清理随机devicetruth_test_*库，不清理既有库。
- `mvn -q test`、`mvn -q package`均退出0：Surefire实际汇总**153/0/0/0、30 suites**；原146项保留。ServerHttpTest显式scripted，避免本机私有模型配置影响离线测试。
- `docker compose --profile standalone-db config --quiet`退出0；临时环境密码未输出。Compose为备用环境，固定已有PG16镜像摘要且pull_policy=never；本次未创建第二PG实例。
- `python outputs/validation/01-02-packaged-check.py`退出0：新JAR在devicetruth库201创建会话、202保存原文/QUEUED；关闭Java进程再启动后同会话/消息/run一致。两个health UP、进程均关闭；同脚本给CLI注入不可连接DB和product环境，B仍RESOLVED/DEMO-D-120B；密码未出现在验证日志/摘要。
- `python scripts/verify_cli.py`退出0：A/B正确规则（7步34事件），C冲突（7/35），D缺证（6/29），参数/缺Key无降级/本机假Key合同通过；E安全负例在153项内。
- `git diff --check`通过。JSON数据、规则和runtime-comparison固定输入未改，指定V1.0设计hash仍为C80A4C9292787B181DDB2406CAE4746B83F602993B22EAAA8C2C42AF5D698120。

原始日志/临时验收脚本为忽略目录outputs/validation/01-02-*，可重建摘要target/cli-verification.json及outputs/validation/01-02-packaged-persistence.json。凭据仅在内存和子进程环境使用，不保存到仓库/报告；不读取真实模型私有文件。

当前2/13计划、6/37任务完成，M0独立Review未进行，阶段0/5。新PG消息与QUEUED持久化已验收；Agent worker/真实RAG/embedding/WAITING/执行恢复/Vue/SSE仍未实现或未验收。下面各节保留为当时的历史结果，不以其“PG未验证/仅规划”描述覆盖本节。

## 接续核验（2026-11-09，仅核验与状态修正）

用户本次要求先核对代码、上一份SUMMARY与可重复测试，再汇报当前状态/下一步；不执行新PLAN、不自动提交Git。上一份01-01-SUMMARY保持为当时执行记录，不改写历史。当前完成记录仍为1/13计划、3/37任务、0/5阶段，独立Review未进行。

### 当前代码与记录核对

- 当前HEAD为`dbb8b2e567db68a1b8d78a07bbd3b7a03c245bb0`（feat: 执行01-01基线契约与Runtime测试原型），父提交为01-01实施起点`a9607cf`。本次开始`git status --short`为空；已修正STATE/NEXT_SESSION中“成果仍在工作树、尚无提交”的陈旧现状描述。本次没有Git提交、推送或部署。
- `git diff a9607cf HEAD --name-only -- src/main pom.xml datasets skills`为空；当前主源码52文件、测试29文件。比较测试和固定cases.json确实位于src/test，未引入生产第二套循环；CONTRACTS/BASELINE/RUNTIME-SPIKE/ADR及SUMMARY均存在并与实际代码边界一致。
- 指定V1.0设计SHA256仍为`C80A4C9292787B181DDB2406CAE4746B83F602993B22EAAA8C2C42AF5D698120`；cases.json仍为`74192b906d4d4cc81c838039503037a34c3876fbd4eb038cbfc6e035e2601277`。
- 本次初始`mvn -version`已显示Microsoft JDK21.0.13.0.2、Maven3.9.9；复测仍按BASELINE设置进程级JAVA_HOME/PATH。历史“默认Java17”不再作为本次现状判断，未修改全局环境/执行策略。

### 本次实际运行

| 命令 | 退出码 | 实际结果 |
|---|---|---|
| `mvn -q '-Dtest=RuntimeComparisonTest' clean test`，连续两次 | 0 / 0 | 每次1测试方法、6样例×2候选；两次输出hash均与原记录一致 |
| `mvn -q test` | 0 | 实际Surefire XML汇总29 suites、146 tests、0 failures、0 errors、0 skipped |
| `mvn -q package` | 0 | 再次运行同样146项并重新生成CLI JAR |
| `python scripts/verify_cli.py` | 0 | 新JAR的help、A–D报告、非法参数、两种供应商缺Key不降级及本机假Key私有文件合同通过 |
| `python outputs/validation/01-01-recheck-http.py` | 0 | 新JAR的独立HTTP Main经PropertiesLauncher启动，/api/health=UP/SIMULATED、/actuator/health=UP、/index.html=200；finally已结束子进程 |
| `git diff --check` | 0 | 本次规划文档修改无空白错误；未修改业务代码 |
| `docker version --format '{{.Client.Version}} {{if .Server}}{{.Server.Version}}{{end}}'`、`docker ps --format '{{.Image}} {{.Status}}'` | 1 / 1 | 客户端29.1.3存在，无法连接docker_engine管道，并有用户配置读取权限提示；未启动引擎/容器 |

两次原型输出SHA256均为`830d11a9aa9a24cd002ca67e881a90c3928361d45ed8ff9752772d399c773aeb`。AcceptanceTest实际运行5个方法，含A–D结果与E未知工具/伪造证据安全负例；原型1个方法内部比较12次，不计成12个JUnit测试。

新CLI结果：A=RESOLVED/DEMO-D-120A/7步34事件，B=RESOLVED/DEMO-D-120B/7步34事件，C=CONFLICT/7步35事件，D=NEED_EVIDENCE/6步29事件。B本次runId为`96879466-7da8-4576-af2e-371861f35be9`，仍为SIMULATED。HTTP辅助脚本还核对包内CLI Start-Class、offline Router/openai Reasoner及私有配置未打包；仅GET健康/静态页，没有提交外部模型诊断。

本次原始日志为忽略目录`outputs/validation/01-01-recheck-{spike-first,spike-second,test,package,cli,http}.log`，复核辅助脚本也在该目录；可重建摘要为`target/spikes/runtime-comparison.json`、`target/cli-verification.json`和`target/http-recheck.json`。不提交生成日志、报告或辅助脚本。

### 接续结论与下一项

01-01的三项交付及可重复测试已核实，保持完成；独立Review仍待进行，整个M0未完成。下一项待执行计划是01-02（depends_on仅01-01），代码/契约前置已满足；实施授权仍为false，Docker/真实PG环境尚未就绪，不能宣称已经具备全部开工条件。本次只更新接续状态和验证记录，不新增01-02-SUMMARY、不勾选01-02，不运行其实施命令。

后续01-02应先准备可用的Docker/真实PG测试环境并核对固定版本，再实现会话/消息/QUEUED run事务持久化、重启读取及legacy CLI隔离；M0不启动Agent worker。未验收项仍为真实PG/pgvector/迁移事务、外部LLM/embedding/RAG、生产自由目标API/WAITING、跨进程恢复、Vue/SSE和完整V1.0 E2E；本次测试不替代这些验收。

## 01-01 已授权实施验证（2026-11-09）

用户明确要求执行01-01，覆盖下文历史“仅规划”边界。当前实施证据见 [01-01-SUMMARY](phases/01-baseline-contracts/01-01-SUMMARY.md)、[BASELINE](phases/01-baseline-contracts/BASELINE.md) 和 [RUNTIME-SPIKE](phases/01-baseline-contracts/RUNTIME-SPIKE.md)。下文初始规划记录保留为历史，不用其0进度/未实施断言覆盖本轮结果。

- HEAD起点a9607cf，工作树开始干净；指定产品设计hash未变，生产源码/pom/脚本/fixture未改。
- 实际JDK21.0.13.0.2/Maven3.9.9。起点test145/0/0/0；新增1个比较测试后，最终 `mvn -q test` 和 `mvn -q package` 均退出0，146/0/0/0，29 suites。
- 原型首次clean及两次诊断失败如实保留；修正SDK fake工具块raw content后，连续两次 `mvn -q '-Dtest=RuntimeComparisonTest' clean test` 退出0，12次内部样例执行/轮，输出字节相同。
- 最新JAR的 `python scripts/verify_cli.py` 退出0，A/B正确规则、C冲突、D缺证、缺Key无降级、本机假Key私有文件合同通过；E由继承测试覆盖。
- 打包JAR经PropertiesLauncher启动独立HTTP Main，真实GET /api/health=UP/SIMULATED、/actuator/health=UP、index=200；未提交模型诊断，进程已关闭。包内默认offline/openai、CLI Start-Class和私有配置不进JAR已核验。
- 本计划只做现有安全回归与离线原型，没有外部模型/embedding、PG、真实RAG、Vue、生产暂停恢复或独立Review；后续计划12份均未执行。
- 收尾GSD结构/一致性无错误或警告，roadmap识别1/13计划、0/5阶段、8%；单独Python核对63需求唯一映射、所有本地Markdown链接/UTF-8、原95份源码hash及设计hash未变；`git diff --check`通过。机器摘要在忽略目录outputs/validation/01-01-closeout.json。

历史 `validate_planning.py` 包含“所有计划未授权/0SUMMARY/业务目录无新增”的保护断言，实施后不适用。本轮使用GSD `verify plan-structure`、`validate consistency`、`roadmap analyze` 以及单独需求/链接/源码范围检查；不覆盖历史planning-validation.json来冒充新运行结果。

## 前轮初始规划实际执行（历史记录）

| 项目 | 命令/方法 | 结果 |
|---|---|---|
| Git 起点 | git status --short / git log -5 --oneline / git rev-parse HEAD | 当前 b7befa08f702675d05dcc9560cf7eb87e4b778c2；开始时只有用户提供的 V1.0 设计为未跟踪文件 |
| GSD 安装 | node C:/Users/11929/.codex/gsd-core/bin/gsd-tools.cjs runtime-identity --raw | @opengsd/gsd-core 1.20.1 |
| 初始化探测 | 同工具 init ingest-docs | planning_exists=false；正确项目根；未自动创建 Git 或执行开发 |
| 初次 Java 环境 | mvn -version；尝试 dot-source scripts/use-java21.ps1 后 mvn -o -q test | 默认 17.0.16；.ps1 执行策略拒绝，测试退出1，class file 65/61 不兼容 |
| 修正当前进程 JDK | 直接设置 JAVA_HOME/PATH 到 .tools 的 JDK21，再 mvn -version | Java21.0.13.0.2、Maven3.9.9；未改全局环境 |
| 离线基线 | mvn -o -q test | **PASS，退出0；145/0/0/0，28 suite** |
| 测试报告汇总 | Python XML 读取 target/surefire-reports/TEST-*.xml | core27/adapter13/app2/application68/server35 |
| 环境盘点 | node --version；npm.cmd --version；发现 Docker CLI | Node24.14.0/npm11.9.0；Docker daemon/Compose 未验证；CLI 有配置读取权限提示，未启动容器 |
| 需求摘要 | Get-FileHash SHA256 指定 V1.0 设计 | C80A4C9292787B181DDB2406CAE4746B83F602993B22EAAA8C2C42AF5D698120 |
| 源码检查 | rg --files 与核心/应用/API/适配/测试读取；搜索 DB/Skill/SSE/状态与前端结构 | 与七类地图、E01–E22 一致；实现声明均有可定位依据 |

第一次 Python 通过 PowerShell 管道传中文路径时被编码替换，hash 读取失败，后用 PowerShell Get-FileHash 得到正确摘要；没有改用户文档。后续机器摘要使用 glob 定位和 UTF-8 文件读取，避免同类问题。

## 初始规划校验（2026-11-09，修订前历史记录）

- GSD `roadmap analyze`：5个阶段、13个PLAN、0个SUMMARY、0个完成阶段；无复选框冲突。
- GSD `state-snapshot`：planning、当前Phase1、阶段总数5、本阶段2计划、进度0%；接续文件指向NEXT_SESSION。
- GSD `validate consistency`：passed=true，errors=[]，warnings=[]。
- 对全部13份PLAN分别执行GSD `verify plan-structure`：valid=true；37项任务的files/action/verify/done字段齐备，无结构错误或警告。
- Python核对：63个唯一需求均恰好映射一个主计划，矩阵/PLAN/阶段一致；依赖存在且无环，同阶段wave先后正确；本地Markdown链接、UTF-8及STATE小于100行检查通过。
- 首次链接校验早于baseline.json生成，报告该链接缺失；补齐真实基线摘要后重新执行，全部通过。
- 源码保护检查：`git diff HEAD --name-only -- src pom.xml scripts datasets skills .mvn`为空，相同范围没有新增未跟踪文件；95份源码/资源/脚本等文件记录SHA256，不含私有配置。
- 机器证据：[baseline.json](evidence/baseline.json)、[planning-validation.json](evidence/planning-validation.json)。校验脚本退出0；结构通过不代表语义独立Review通过。

## 初始规划明确没有执行

- 未执行本规划中任何开发任务、原型、schema migration、数据导入、依赖安装、前端构建或 PG 容器启动。
- 未重新 package、未执行 verify_cli.py / verify_online.py，故本轮不宣称打包 CLI 或在线通过。
- 没有外部 LLM/embedding 请求；没有打开或打印真实私有配置值。
- 旧 DeepSeek CAR-B 两次成功仅见历史 state 记录；未用它证明 Router→新 Agent→RAG→结果链。
- 未进行独立代码/安全 Review；PLAN-REVIEW 只是本轮规划自检。

## 可重复的 JDK21 启动方式

```powershell
$taskJdk = Get-ChildItem -Directory -LiteralPath .tools -Filter 'jdk-21*' | Select-Object -First 1
if (-not $taskJdk) { throw 'JDK 21 missing' }
$env:JAVA_HOME = $taskJdk.FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -version
mvn -o -q test
```

只修改当前进程环境。后续新增依赖首次构建若离线缓存缺失，应如实报告；不能把 -o 失败当业务回归，亦不能未经验证宣称新增依赖可用。

## 未来验证层次（待执行）

1. 旧核心/HTTP/CLI：每次改契约后跑适当回归；阶段出口 mvn test、package、verify_cli.py。保留 A/B/C/D 和 E 负例；E 无 CLI 参数。
2. PG 集成：真实 PostgreSQL+pgvector，Testcontainers 或独立测试库；事务、唯一约束、时间/权限筛选、空库迁移/重复迁移。测试连接与实际服务配置分开。
3. 检索：固定中文手册/部件号/故障码集合；明确应召回文档/locator；比较向量/精确/混合，验证未授权资料不进候选。
4. Agent 离线：确定性 fake model 驱动协议、追问、恢复、错误引用及风险判断；验证机制，不冒充在线智能质量。
5. 真实模型：M1 真实 embedding/固定12样本、真实模型 smoke 与同run基本补证，M3 双域样例，M4 正式 E2E-01–14。记录模型/语料/Skill 版本、事件、引用、耗时与可得用量；缺配置/额度/网络分别标 BLOCKED_BY_ENV。
6. 重启/取消：等待态和检查点跨进程重建；幂等和状态竞争测试；不要求相同在线动作序列，要求来源、权限、恢复与结果边界满足。
7. UI：Vue 构建与对话/补证/引用/上传行为；SSE 断线恢复；用户可读结论和错误，不能只验 DOM 存在。
8. 运维：Compose 健康与持久卷、备份恢复、迁移失败回退；共享部署前身份与授权负例必须通过。

具体测试类/脚本在 PLAN 中是**拟新增**，本轮不存在也未执行。测试缺失不写 PASS，开发阶段需先实现对应验证资产。


## 本次针对性修订校验（2026-11-09）

实际命令：`python .planning/validate_planning.py`（退出0）。此脚本只读取规划和调用GSD只读校验，写出 .planning/evidence/planning-validation.json，不运行PLAN内命令。

- GSD 1.20.1：全部13份 `verify plan-structure` valid=true；`validate consistency` passed=true，无错误/警告。
- `roadmap analyze` / `state-snapshot`：5阶段、13计划、0 SUMMARY、0完成、0%进度。
- 63需求恰好一个主交付计划；ROADMAP阶段/PLAN需求一致，AGENT-03归02-02；依赖存在/无环、同阶段wave及4个跨阶段出口依赖通过。
- 37任务的files并集与各PLAN files_modified一致；key_links起点、规划链接、UTF-8及STATE边界通过。
- 对已记录基线的95份源码/测试/资源/依赖配置/数据文件重新核对SHA256，均未变；Git核对业务目录无修改或新增，并核对产品设计原文hash未变。保留用户原有AGENTS及未跟踪文档。范围与结果见planning-validation.json的revision_scope_check。
- 本次没有执行Maven/npm/Docker/产品脚本或在线请求，没有新实现产物；前轮145项结果未重跑。Runtime原型、真实PG/embedding/LLM、UI、恢复和M4评测均待后续授权实施。

修订摘要见 [PLAN-REVIEW.md](PLAN-REVIEW.md)。当前仍为review_pending；检查通过只证明本轮规划结构与追踪一致，不代表独立语义审查/实施授权。

## 计划可读性整理后的复核

13份PLAN的长行元数据展开，任务改为中文标题、分步说明和逐行文件清单。校验脚本相应支持多行数组及列表文件清单。实际重新运行 `python .planning/validate_planning.py`，结构、需求映射、依赖、文件清单、状态及业务文件保护检查通过。

另使用Python Markdown渲染13份计划正文，37个任务标题和37个有序步骤列表全部生成，正文没有残留未解析的粗体标记。所有PLAN的最长单行不超过180字符。实际开发仍为0%。
