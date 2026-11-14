# DeviceTruth — 通用设备检修 Agent

DeviceTruth 是 Java 单 Agent Core：每步读取独立的结构化状态，由 Reasoner 提出一个动作；Gateway 执行至多一个只读能力；Evidence Manager 重建事实并使旧依赖失效；State Manager 提交新版本；Guard 校验结论。模型只能提议动作，不能修改事实、写证据或自行执行工具。CLI 场景使用 `SIMULATED` 资料；产品数据库还包含公开登记和工单文档快照，文档记载与模拟观测均不代表当前现场核实，来源说明见 [数据建设记录](docs/data-enrichment.md)。

**当前状态（2026-11-14）**：Phase 1–4 已完成并提交；Phase 5 三个计划（SSE 实时进度、工作台与 E2E、Docker 部署与运维演练）全部完成并实测——全量 `mvn -o test` 360/0/0/1、前端 36/36+build、浏览器 A–E 五场景 PASS、部署演练 PASS。阶段待两项集中 UAT 确认。权威状态与跨会话接续见 [.planning/STATE.md](.planning/STATE.md) / [.planning/NEXT_SESSION.md](.planning/NEXT_SESSION.md)。

技术版本：Java 21、Maven 3.9+、Spring Boot 3.5.16、AgentScope Java 2.0.4（core + DashScope + OpenAI 扩展，不引入 harness 或另一个编排框架）。API 选型依据见 [API Spike](docs/research/notes/agentscope-spike.md)。新增扩展首次构建需要下载依赖。

桌面原包已适配为六个专项 Skill：充电、维护、供电/电池风险、工业配置、工业控制器故障、Carrier 屋顶机组。连同原有两个包，八包均已按工作手册规范化并升级默认启用版本，具备九键元数据、五类标准参考及只读取证边界。当前版本与来源见 [领域 Skill](docs/domain-skills.md)，格式、自检命令和验收见 [Skill规范化](docs/skill-normalization.md)。当前知识库尚未建设且缺 embedding 密钥，手册检索仍不可用。

## 快速开始

需要 JDK 21 与 Maven 3.9+；先用 `mvn -version` 核对实际 JDK。`.mvn/maven.config` 将依赖缓存放于项目内 `.m2/repository`，不改用户全局 Maven 设置。本工作区已下载微软 JDK 21 到 `.tools/`（不入库）；其他机器请把 `JAVA_HOME` 指向自己的 JDK 21。

```powershell
# PowerShell（.tools JDK，仅当前进程生效）
Set-Location D:\Java_Project\DeviceTruth
$env:JAVA_HOME = (Get-ChildItem -Directory .\.tools -Filter 'jdk-21*' | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

```bash
# Git Bash 注意：PATH 必须用 /d/... 形式，D:\... 里的冒号会把 PATH 截断
cd /d/Java_Project/DeviceTruth
export JAVA_HOME='D:\Java_Project\DeviceTruth\.tools\jdk-21.0.13.0.2+1'
export PATH="/d/Java_Project/DeviceTruth/.tools/jdk-21.0.13.0.2+1/bin:$PATH"
java -version   # 必须是 21
```

```bash
mvn -q test                                        # 全部测试（真实PG测试见下文环境变量）
mvn -q package                                     # 测试 + 可运行 JAR
java -jar target/devicetruth-core-0.1.0.jar --help
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted   # 离线，无需密钥
python scripts/verify_cli.py                       # 核验打包后 CLI 的 A–D 场景及报告
```

无 Key 的 `scripted` 模式不调用外部 LLM。规则和 fixtures 打入 JAR，修改 JSON 后需重新打包。演示时间固定为 `datasets/scenarios.json` 中的 `2026-11-03T04:00:00Z`，复现不受系统日期影响。

### 前端（Vue 3.5 + TypeScript + Vite，Node ≥20.19 / npm ≥10）

```bash
cd frontend
npm ci                  # 按 lockfile 安装
npm run test -- --run   # api 合同单测（Vitest）
npm run build           # vue-tsc 类型检查 + 构建到 dist/
npm run dev             # 开发页，/api 代理到 127.0.0.1:8080
```

### HTTP 服务与 product 模式

fat JAR 默认入口是 CLI（`Start-Class=io.devicetruth.app.DeviceTruthApplication`）。Web 用独立主类 `io.devicetruth.server.DeviceTruthServerApplication`，默认只监听 `127.0.0.1:8080`：

```bash
mvn -q package
mvn -q dependency:build-classpath '-Dmdep.outputFile=target/server-cp.txt'
java -cp "target/classes:$(cat target/server-cp.txt)" io.devicetruth.server.DeviceTruthServerApplication
```

健康检查 `GET /api/health` 返回 `{"status":"UP","dataNature":"SIMULATED"}`；这并不代表数据库或模型可用。旧 sessions/diagnoses 接口无需数据库、继续保留。

product 模式（PG 持久化会话/知识/资产/Skill 与产品 Agent）加 `--spring.profiles.active=product` 启动。共享部署用 `compose.yaml`（显式 `--profile standalone-db` 才启动数据库，`pull_policy: never`），运维与演练手册见 [docs/operations.md](docs/operations.md)。

## 配置

**模型（私有文件）**：根目录 `devicetruth.local.properties`（已 gitignore，模板见 `.example` 文件），UTF-8、等号后直接填值不加引号：

```properties
OPENAI_API_KEY=在这里填 DeepSeek API Key
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-flash
DASHSCOPE_API_KEY=          # 使用千问时填
```

配置优先级：私有文件非空值 → 环境变量。文件按进程工作目录查找，改动后重启即可。密钥不写入源码、日志或报告。`--reasoner=openai` 走 OpenAI 兼容协议（Bearer、非流式、`response_format=json_object`），`--reasoner=qwen` 走 DashScope `qwen-plus`。

**PostgreSQL**：Spring 进程只读 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 三个环境变量；凭据不写入模型私有文件。测试用再加 `DEVICETRUTH_TEST_PG_*`（详见 `docs/acceptance-history.md` 的 PG 段落）。本机当前使用 `devicetruth-postgres-1`（PG 16.15 + pgvector 0.8.7，本机端口 54329，`devicetruth` 库）；Flyway `V1`–`V16` 均已成功。历史 `interview-postgres:5432` 是先前验证环境。2026-11-14 已实际补充五张设备业务表：资产 166、部件/逻辑节点 709、配置 1,035、维修/工单文档 175、模拟观测 1,063；来源语义、幂等导入和 30 项实际产品检查见 [数据补充与验收](docs/data-enrichment.md)。

**其他环境变量**：`DEVICETRUTH_OWNER_ID/PROJECT_ID`（product 身份，默认 `local-owner/local-project`，仅供单用户本地演示，共享部署须接真实认证）；`DEVICETRUTH_SKILLS_ROOT/SKILLS_INSTALLATIONS/SKILLS_ALLOWED_TOOLS`（Skill 目录、默认安装与工具上限）；`DEVICETRUTH_ASSETS_SEED_FIXTURES=true` + `DEVICETRUTH_ASSETS_FIXTURES=SC-0245-B`（工厂演示资产）；embedding 走 `DEVICETRUTH_EMBEDDING_*`（缺省复用 `DASHSCOPE_API_KEY`）。

## 架构与模块

源码在 `src/main/java/io/devicetruth/`：`app/`（CLI 入口）、`core/`（reasoning/runtime/capability/evidence/state/decision/contract）、`adapter/`（模型、JSON 仓储、PG 持久化、报告）、`server/` `api/` `application/`（HTTP 与产品 Agent 状态机）、`knowledge/`（导入/检索/上传）、`skill/`（标准 Skill 加载与运行时）。逐模块职责见 [AGENTS.md](AGENTS.md)。

产品 Agent 的唯一主循环是 `TaskOrchestrator.runAgent(...)`：模型只提出 `CALL_TOOL`/`ASK_USER`/`ANSWER`，工具经 `CapabilityGateway` 只读执行，答案经 `AnswerGuard` 校验引用子集、危险建议与"无资产不得确诊"。崩溃恢复由执行租约 + 版本 CAS 实现；SSE 事件流先历史后订阅再补读，事件经白名单映射。

## 产品 HTTP 接口速览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/GET | `/api/conversations[/{id}[/messages]]` | 会话与消息；POST 消息需 `Idempotency-Key`，`replyTo` 三元组为补答 |
| GET | `/api/runs/{id}` | 任务状态、待答问题与答案；`/references` `/tool-events` `/model-events` `/skill` 为明细 |
| GET | `/api/runs/{id}/events`、`/stream` | 历史事件与 SSE 实时流（断线按 `Last-Event-ID` 续传） |
| POST | `/api/runs/{id}/cancel` | 取消（终态再取消 409） |
| POST/GET | `/api/knowledge/*` | 文档导入/检索/引用定位；`GET /api/knowledge/tools` 为工具合同 |
| POST/GET | `/api/files`、`/api/files/{id}[/retry]` | Markdown 上传与导入状态 |

错误体固定 `{code,message,requestId}`，无权与不存在同为 404，不透传 SQL/配置/异常详情。legacy sessions/diagnoses 接口表见 [docs/acceptance-history.md](docs/acceptance-history.md)。

## 验证与排错

```bash
mvn -q '-Dtest=!PersistentConversationTest' test   # 仅 legacy 离线回归
python scripts/verify_general_agent.py --suite=m1-retrieval --embedding=deterministic
python scripts/verify_browser_recovery.py --browser-path 'C:/Program Files/Google/Chrome/Application/chrome.exe'
```

- `release version 21 not supported`：当前终端 JAVA_HOME/PATH 未指向 21（Git Bash 下检查 PATH 是否被冒号截断）。
- Maven 首次下载失败：确认可访问 Maven Central；不要写成测试通过。已缓存可 `mvn -o -q test`。
- 缺模型配置：在线模式产出 FAILED 报告与稳定错误码（`DASHSCOPE_API_KEY_MISSING`/`OPENAI_*_MISSING` 等），脚本返回 `BLOCKED_BY_ENV`，不会降级冒充成功。
- 发中文 400：请求体按 UTF-8 编码并声明 `Content-Type: application/json; charset=UTF-8`。

## 文档导航

- 规划与状态：`.planning/`（STATE / NEXT_SESSION / ROADMAP / 各阶段 SUMMARY 与验收）
- 历史实施与验收记录：[docs/acceptance-history.md](docs/acceptance-history.md)
- 部署运维：[docs/operations.md](docs/operations.md) · V1.0 产品设计：[docs/DeviceTruth_V1.0_通用设备检修Agent_完整产品设计方案_PostgreSQL.md](docs/DeviceTruth_V1.0_通用设备检修Agent_完整产品设计方案_PostgreSQL.md)
- Legacy 规格：docs/DeviceTruth_Core_V0.1_Spec_Pack/ 与 docs/DeviceTruth_V0.2_S0_S1/（仅理解历史与回归，不自动续跑旧任务）
- 调研留档：[docs/research/](docs/research/)（AgentScope API 选型依据）

后续方向：真实认证、更多领域适配、PDF 支持。V0.1 没有实现多 Agent、MCP 平台或设备控制。
