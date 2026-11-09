# 验证记录与后续验收策略

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
