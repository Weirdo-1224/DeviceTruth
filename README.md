# DeviceTruth Agent Core V0.1

V1.0当前入口为[.planning/README.md](.planning/README.md)。01-01与01-02已完成：基线/契约/Runtime原型，以及使用interview-postgres的PG会话/消息/QUEUED切片。02-01（受权限约束的知识导入、向量检索与可定位引用）已于2026-11-11独立复审通过并提交。02-02（通用Agent、同run补证与Vue最小产品页）已提交。02-03按用户决定改为Markdown上传、混合检索与固定小样本，已实施并自测，处于待审查状态。**PDF、真实模型/真实embedding和浏览器人工演示仍未验收；真实供应商命令因缺少embedding密钥返回BLOCKED_BY_ENV。**下文V0.1的DONE和在线记录只描述历史版本，不能作为V1.0验收结果。

01-01 的当前验证、配置策略与局限见 [BASELINE](.planning/phases/01-baseline-contracts/BASELINE.md)，新输入/追问/引用/风险约定见 [CONTRACTS](.planning/phases/01-baseline-contracts/CONTRACTS.md)，主循环取舍见 [Runtime ADR](.planning/phases/01-baseline-contracts/ADR-runtime.md)。测试原型的复现命令为 `mvn -q '-Dtest=RuntimeComparisonTest' clean test`，不需要密钥或数据库。

HTTP 默认 `devicetruth.router.mode=offline`、`devicetruth.core.reasoner=openai`，两者含义分别是离线路由与模型诊断供应商；离线路由不意味着诊断不访问模型。独立服务入口为 `io.devicetruth.server.DeviceTruthServerApplication`，可运行 JAR 默认入口仍是 CLI。已有 Actuator 与 `/api/health`；这不代表数据库或模型可用。

2026-11-12 用户决定统一模型配置：对话模型与 embedding 均可使用根目录 `devicetruth.local.properties`（示例见 `devicetruth.local.properties.example`），私有文件不提交。PG 凭据仍通过服务端环境注入。对话模型密钥沿用私有文件非空值优先；embedding 的显式 `DEVICETRUTH_EMBEDDING_*` 环境变量优先，其次为私有文件，再使用服务端默认值。专用 embedding 密钥为空时复用 `DASHSCOPE_API_KEY`。不打印或写入报告。

DeviceTruth 是可运行的 Java 单 Agent Core。每步读取独立的结构化状态，由 Reasoner 提出一个动作；Gateway 执行至多一个只读能力；Evidence Manager 重建事实并使旧依赖失效；State Manager 提交新版本；Decision Guard 决定是否允许结论。模型不能修改事实、写证据或自行执行工具。

**资料与规则均为 `SIMULATED`，仅用于工程验收，不能指导实际车辆或工业设备维护。** 离线验收及 OpenAI 兼容接口的真实场景 B 已通过（6 步、DEMO-D-120B，共 2 次独立运行）；全部 MUST 任务已完成，整体 DONE。千问 DashScope 适配路径尚未被真实执行过，属已知残留风险，记录在 state.md 的设计决定 16，不是阻塞项。最新命令、报告路径与进度见 [state.md](docs/DeviceTruth_Core_V0.1_Spec_Pack/state.md)。

技术版本锁定为 Java 21、Maven 3.9+、Spring Boot 3.5.16、AgentScope Java 2.0.4。AgentScope 使用 `agentscope-core`、DashScope 和 OpenAI 模型扩展，不引入 harness 或另一个编排框架。版本依据 Maven Central 元数据与官方源码验证，见 [API Spike](docs/research/notes/agentscope-spike.md)。新增扩展首次构建需要下载依赖。

## 启动与构建

### V1.0 M0 持久化接口

新接口仅在独立HTTP服务启用`product`配置时提供。它保存用户原文、会话、任务与初始事件，并由有界 worker 推进：`QUEUED → RUNNING →（WAITING_FOR_USER → QUEUED → RUNNING）→ COMPLETED / FAILED / CANCELLED`。worker 关闭（`DEVICETRUTH_AGENT_WORKER_ENABLED=false`）时只保留 M0 的 QUEUED 持久化合同。崩溃遗留队列、租约/CAS 与跨进程恢复由 M2（03-02）实现：RUNNING 持执行租约并续租，重启后按租约过期+版本 CAS 回收恢复；幂等、取消与总时限由 M2（03-03）实现：持久请求键重复提交 409 不重复执行，`POST /api/runs/{id}/cancel` 取消（QUEUED/WAITING 立即 CANCELLED，RUNNING 协作式收尾，终态再取消 409），累计执行时长超限明确 FAILED/TOTAL_TIMEOUT，取消/超时后迟到的模型响应只记 `RUN_LATE_RESPONSE` 事件不覆盖终态。legacy CLI和默认HTTP服务无需数据库，旧sessions/diagnoses接口继续保留。

本机按用户指定复用`interview-postgres`，实测PostgreSQL 16.15 + pgvector 0.8.7，端口5432；DeviceTruth使用独立`devicetruth`数据库，不修改`interview_guide`。Flyway执行`V1`–`V7`迁移（会话/消息/任务、知识四表、embedding档案、历史向量隔离、消息与run关联、受控文件导入状态、文件与文档版本关联），重复启动只校验已有迁移，不重建业务表。JDBC/Flyway/Hikari版本由Spring Boot 3.5.16管理，分别为42.7.11/11.7.2/6.3.3。实现按[Spring Boot 3.5迁移文档](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)使用Flyway PostgreSQL模块；扩展参考[pgvector官方文档](https://github.com/pgvector/pgvector)。

在项目根目录使用既有容器（以下只创建独立库，不清空原库；密码仅在当前进程内存中使用）：

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
docker start interview-postgres
$taskDbExists = docker exec interview-postgres psql -U postgres -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='devicetruth'"
if ($taskDbExists -ne '1') { docker exec interview-postgres psql -U postgres -d postgres -c 'CREATE DATABASE devicetruth' }
$taskPgEnvironment = docker inspect interview-postgres --format '{{json .Config.Env}}' | ConvertFrom-Json
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/devicetruth'
$env:SPRING_DATASOURCE_USERNAME = ($taskPgEnvironment | Where-Object { $_.StartsWith('POSTGRES_USER=') }).Substring(14)
$env:SPRING_DATASOURCE_PASSWORD = ($taskPgEnvironment | Where-Object { $_.StartsWith('POSTGRES_PASSWORD=') }).Substring(18)
mvn -q -DskipTests package
java '-Dloader.main=io.devicetruth.server.DeviceTruthServerApplication' -cp target/devicetruth-core-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --spring.profiles.active=product --debug=false
```

Spring进程只使用三个`SPRING_DATASOURCE_*`环境变量，不读取模型私有配置文件中的数据库凭据。`compose.yaml`保留给需要独立部署的环境：只有显式指定`--profile standalone-db`才启动数据库，锁定本机已有PG16镜像摘要并设置`pull_policy: never`，不会自动下载。可按`.env.example`配置独立实例密码及54329端口；复用interview时不需要运行Compose，也不复用其数据卷。

`product`默认采用服务器固定身份`local-owner/local-project`，可通过`DEVICETRUTH_OWNER_ID/DEVICETRUTH_PROJECT_ID`设置本地测试身份。客户端body中的owner/project/permissions/requestedAt会被拒绝。此模式仅供单用户本地演示，共享部署须在05-03接入真实认证。

| 方法 | 路径 | 当前行为 |
|---|---|---|
| POST | `/api/conversations` | 空body或`{}`，201并返回conversationId |
| GET | `/api/conversations` | 当前身份最近100个会话 |
| GET | `/api/conversations/{id}` | 持久会话、按序消息和任务摘要 |
| GET | `/api/conversations/{id}/messages` | 持久消息列表 |
| POST | `/api/conversations/{id}/messages` | `Idempotency-Key: UUID`必填，202并返回同事务登记的任务和Location；带`replyTo`三元组时为补答 |
| GET | `/api/runs/{id}` | 持久任务状态、待答问题`pendingQuestion`与答案`result` |
| GET | `/api/runs/{id}/references` | 本run已取回的知识引用（文档版本/章节/页码），按归属过滤 |

提交示例（会话ID由URL提供）：

```powershell
$conversation = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/conversations
$payload = @{schemaVersion=1; userMessage="充电经常中断，没有故障码，先看哪些资料？"} | ConvertTo-Json
$queued = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/conversations/$($conversation.conversationId)/messages" -Headers @{'Idempotency-Key'=[Guid]::NewGuid().ToString()} -ContentType 'application/json; charset=UTF-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/runs/$($queued.runId)"
```

`userMessage`原样保存，必填、非空白、最多8000个Unicode码点；`selectedAssetId/asOf`可选，不要求故障码或Catalog准入。未给asOf时采用服务器requestedAt。当前只保存资产线索，不读取或确认资产现状。非空attachments/contextRefs会整体拒绝，不会静默丢弃；`replyTo`缺失字段返回400 `REPLY_TARGET_REQUIRED`，错run/错题号/旧版本/终态回复返回409 `STALE_OR_UNRELATED_REPLY`，都不会新建run。重用会话内的请求键返回409 `DUPLICATE_REQUEST`，不增加消息/任务；M2才提供完整幂等重放。无权和不存在均返回404 `NOT_FOUND_OR_DENIED`，其他新接口错误使用固定message、code、retryable、traceId，不透传SQL或配置内容。

真实PG测试复用该实例，每次生成独立`devicetruth_test_<随机UUID>`空库，结束后仅删除自己生成的测试库，不拉取镜像：

```powershell
$env:DEVICETRUTH_TEST_PG_BASE_URL = 'jdbc:postgresql://127.0.0.1:5432/'
$env:DEVICETRUTH_TEST_PG_USERNAME = $env:SPRING_DATASOURCE_USERNAME
$env:DEVICETRUTH_TEST_PG_PASSWORD = $env:SPRING_DATASOURCE_PASSWORD
mvn -q '-Dtest=PersistentConversationTest' test
mvn -q test
mvn -q package
python scripts/verify_cli.py
```

`PersistentConversationTest`覆盖空库迁移、服务关闭重开、原文/Unicode、事务回滚、重复/并发提交及双身份/项目隔离。测试账号须能创建和删除专用测试库；缺少测试环境或真实PG不可连接时失败，不跳过冒充通过。仅验证旧离线链路时可执行`mvn -q '-Dtest=!PersistentConversationTest' test`，结果应单列为legacy回归。数据库可以是Docker内的既有实例，也可以是明确授权的独立真实测试服务器；无需Testcontainers依赖。

### V1.0 M1 知识导入与检索（02-01，已复审通过）

`V2__knowledge_sources.sql`在既有库上**追加**`dt_uploaded_file`、`dt_knowledge_document`、`dt_knowledge_chunk`、`dt_evidence_reference`四张表，并创建pgvector余弦距离的HNSW索引。`V3__knowledge_embedding_identity.sql`曾按模型名推断供应商，无法证明旧向量来源；`V4__knowledge_legacy_quarantine.sql`因此把所有修复前写入的知识向量统一标记为`NEEDS_REBUILD`、档案改为`legacy-unverified:@<dim>-cosine-r0`，**不删除正文/切片/历史引用，也不重算向量**。三张迁移都不改动V1会话/消息/任务/事件表，重复启动只校验已执行迁移（02-02 的`V5`另行给`dt_message`增加可空`run_id`）。

| 方法 | 路径 | 当前行为 |
|---|---|---|
| POST | `/api/knowledge/documents` | 导入MD/TXT：校验→按章节切片→固定profile向量化→一个事务写文件元数据、文档与切片；相同内容+版本且档案一致返回`reused=true`；被隔离的旧文档返回`409 DOCUMENT_NEEDS_REBUILD`（需用新版本重建） |
| GET | `/api/knowledge/documents/{id}` | 当前身份可见的文档元数据（版本、profile、维度、切片数） |
| GET | `/api/knowledge/tools` | 服务端知识工具合同（`search_knowledge`、`get_document_excerpt`）与JSON Schema |
| POST | `/api/knowledge/search` | `{runId,query,filters?,limit?,mode?}`：按owner/project（及领域/型号/文档）先过滤候选，再进行向量/关键词/RRF混合召回；`mode`可供固定样本分通道评估，Agent默认HYBRID |
| GET | `/api/knowledge/references/{referenceId}?runId=` | 打开本run已取回引用的原文与章节/页码/文档版本；未取回409 `INVALID_CITATION`，越权404 |
| GET | `/api/knowledge/documents/{documentId}/chunks/{chunkId}?runId=` | 按文档/切片定位读取本run已取回原文，伪造或越权定位返回404 |

`query`允许中文、空格与标点（上限2000码点）；`filters`只接受`domain/deviceModel/documentId`，其他键与伪造documentId均被拒绝。embedding档案标签由`provider:model@dimension-cosine-r<revision>`推导（默认`openai:text-embedding-v4@1024-cosine-r1`），供应商/模型/维度/修订任一变化都产生新档案，不能复用旧文档或交叉召回；配置维度与数据库列（`vector(1024)`）不符时导入与检索都返回409 `EMBEDDING_DIMENSION_MISMATCH`。修复前写入的旧向量按`NEEDS_REBUILD`隔离，既不参与召回也不能被当前档案复用；用新的文档版本重新导入同一内容即可重建，旧正文与历史引用仍可通过引用ID读取。原始文件字节不写入数据库，`path_key`由服务器生成；Markdown原文件保存在受控目录，PDF尚未实现。

embedding配置可放入私有文件或 `DEVICETRUTH_EMBEDDING_*` 服务端环境变量。默认是**千问 `text-embedding-v4`**（DashScope compatible-mode，1024维 cosine）；`DEVICETRUTH_EMBEDDING_API_KEY` 为空时复用既有的 `DASHSCOPE_API_KEY`，无需为同一供应商维护两份密钥。`devicetruth.embedding.provider=deterministic`只是离线合同测试与本地无密钥演示，**不能证明语义召回质量**。真实供应商→PG→召回仍需实际执行。知识工具已注册进产品Agent网关并接入Vue。

```powershell
# 真实PG：本次新增10项集成测试（每个测试类自建随机库并在结束时删除）
$env:DEVICETRUTH_TEST_PG_BASE_URL = 'jdbc:postgresql://127.0.0.1:5432/'
$env:DEVICETRUTH_TEST_PG_USERNAME = $env:SPRING_DATASOURCE_USERNAME
$env:DEVICETRUTH_TEST_PG_PASSWORD = $env:SPRING_DATASOURCE_PASSWORD
mvn -q '-Dtest=KnowledgeSearchTest' test
# 离线embedding适配合同（本机HTTP替身，不需要密钥或数据库）
mvn -q '-Dtest=EmbeddingClientTest' test
```

### V1.0 M1 通用 Agent 与 Vue 产品页（02-02，待独立Review）

产品入口不再只登记 QUEUED：`application/agent/GeneralAgentService` 从 PG 重建上下文，用唯一主循环 `TaskOrchestrator.runAgent(...)` 单步推进。模型只能提出 `CALL_TOOL`/`ASK_USER`/`ANSWER` 三种动作；工具必须已通过 `agentCapabilityGateway` 注册（当前为 `search_knowledge`、`get_document_excerpt`，03-01 追加 `get_asset`/`get_configuration`/`get_maintenance_history`，03-02 追加只读报告工具 `generate_diagnosis_report`）。

`AnswerGuard` 只在服务器侧判定，规则比“过滤几个坏词”更严：

- **引用**：草稿与各断言里的 `referenceId` 都必须是本 run 已取回集合，且都进入答案引用列表；伪造引用 → `INVALID_CITATION`。
- **不得确诊**：`fieldVerified` 由服务器给出，M1 恒为 `false`（03-01 接入资产记录后才可能为真）。此阶段任何 `CONFIRMED_FINDING` 类别或 `CONFIRMED` 断言一律 `UNCONFIRMED_FINDING` 拒绝，**不会降级后继续保留“已确诊”正文**；资产编号只登记、不当作核验。
- **危险建议**：`短接/上电/强制闭合/绕过互锁/拆装/拆除/屏蔽保护` 等动作词出现在**没有安全语义**的句子里（summary、claim、suggestion 都查）即 `UNSAFE_ANSWER`；带“不得/禁止/必须由具备资质的人员现场执行”的引文可以发布，但标 HIGH + `REQUIRES_QUALIFIED_PERSONNEL`。
- **数据性质**：只有全部引用都是 `AUTHORIZED` 才标 `AUTHORIZED`；混合或没有引用一律 `SIMULATED`。

```text
POST /api/conversations/{id}/messages  → 202 QUEUED
  → 服务启动时 recoverQueued() 对账上一进程：遗留 QUEUED 重新调度；
    租约已过期的遗留 RUNNING 按持久检查点回收重跑（版本+租约 CAS，超限明确 FAILED），WAITING 保留
  → worker: QUEUED → RUNNING（领取执行租约并周期续租）→ 检索 → ASK_USER → 事务写问题并置 WAITING_FOR_USER（释放线程）
  → 客户端带 replyTo{runId,pendingQuestionId,questionVersion} 补答 → 同 run 重新入队
  → RUNNING → 再检索 → ANSWER（AnswerGuard 校验）→ 事务写答案并置 COMPLETED
执行/模型失败 → FAILED（无答案，RunView.failureCode 给出白名单原因码）
落库失败 → FAILED/PERSIST_FAILURE；调度队列满 → FAILED/QUEUE_REJECTED，不留幽灵 QUEUED
```

前端把 `failureCode` 映射为可读说明（如 `MODEL_CONFIGURATION_MISSING` → “服务端模型未配置或配置无效”），失败卡片同时显示原因码，不回显异常或配置值。

`V5__agent_run_message_link.sql` 给 `dt_message` 增加可空 `run_id`（复合外键与CHECK），使补答后的上下文只包含本 run 的消息。该迁移只新增可空列，不回填历史数据。

相关配置（`application-product.properties`）：`devicetruth.agent.reasoner=scripted|qwen|openai`（默认 scripted，离线确定性）、`devicetruth.agent.worker-enabled`（默认 true，false 时只保留 M0 的 QUEUED 合同）、`devicetruth.agent.max-concurrent-runs`、`devicetruth.agent.max-queue`。未知 reasoner 名称不会让容器启动失败，运行开始前以 `MODEL_CONFIGURATION_MISSING` 失败。03-02 起：`devicetruth.agent.lease-seconds`（执行租约时长，默认 60）、`devicetruth.agent.max-recovery-attempts`（崩溃恢复重调度上限，默认 3，超限 `RECOVERY_ATTEMPT_LIMIT` 明确 FAILED，不留永久 RUNNING）。03-03 起：`devicetruth.agent.max-active-duration-ms`（总时限=累计执行时长上限，默认 120000，不含等待用户的时间，写入每个 run 的 `budget.limits.maxActiveDurationMs`，恢复不清零）。运行审计：工具事件带单次耗时（`GET /api/runs/{id}/tool-events`），模型尝试带版本/重试/耗时（`GET /api/runs/{id}/model-events`），预算回写 `used.steps/activeDurationMs/modelAttempts/toolCalls`；供应商未返回 usage 时 Token/成本为 null、`usageStatus=UNKNOWN`，不编造数值。

前端为 `frontend/`（Vue 3.5 + TypeScript + Vite，Node ≥20.19 / npm ≥10）：

```bash
cd frontend
npm ci                       # 按 lockfile 安装
npm run test -- --run        # api 合同单测（Vitest）
npm run build                # vue-tsc 类型检查 + 构建到 dist/
npm run dev                  # 开发页，/api 代理到 127.0.0.1:8080
```

页面流程：输入问题 → 轮询运行状态 → 若有追问则在同页补答（自动带三元组）→ 展示答案类别/风险/断言/引用章节与限制。**浏览器人工演示尚未执行**，本阶段只有类型检查、构建与单测证据。

```powershell
# 真实PG + HTTP 两轮补证（随机测试库，结束时删除）
$env:DEVICETRUTH_TEST_PG_BASE_URL = 'jdbc:postgresql://127.0.0.1:5432/'
$env:DEVICETRUTH_TEST_PG_USERNAME = $env:SPRING_DATASOURCE_USERNAME
$env:DEVICETRUTH_TEST_PG_PASSWORD = $env:SPRING_DATASOURCE_PASSWORD
mvn -q '-Dtest=GeneralAgentFlowTest' test    # 协议/网关/引用/风险/并发/落库失败（真实PG）
mvn -q '-Dtest=GeneralAgentApiTest' test     # 两轮HTTP、错题/跨权/启动接手/失败原因码
mvn -q '-Dtest=AnswerGuardTest' test         # 答案安全规则（离线，无需数据库）
```

### V1.0 M1 Markdown 上传与混合检索（02-03，待审查）

按用户决定，本计划以 Markdown 上传替代 PDF 实现。`POST /api/files` 接收 multipart `.md`，限制1MiB、Markdown MIME和严格UTF-8；服务器生成`knowledge/<uuid>`路径并计算SHA-256，原始文件名不能参与路径。`FileUploadService`以有界后台任务完成解析、切片、向量化和PG事务写入，状态为`UPLOADED/PROCESSING/PARSED/FAILED`；`GET /api/files/{id}`读取状态与稳定失败码，`POST /api/files/{id}/retry`重试失败项。`V6`追加导入状态和元数据，`V7`解除“同内容只能一个文件”的旧约束并建立文件—文档关联；同内容同版本且元数据/embedding档案一致可复用文档，不同版本新建文档，冲突明确失败。

消息可使用`attachments:[{fileId}]`关联同owner/project且已完成导入的文件，也可用`contextRefs:[{referenceId}]`把同归属历史证据登记到当前run。非法、未完成或跨权引用均在写消息/run前拒绝。目标消息和每次补答的附件以各自的结构化元数据进入Agent上下文，不拼成用户指令。Vue可上传Markdown、显示失败状态并展开章节引用。

默认检索为向量+关键词RRF混合召回，精确部件号有额外排名权重；权限、档案与READY过滤仍在SQL候选阶段。真实embedding小样本实测后，默认余弦相似度下限为0.45，可通过`DEVICETRUTH_KNOWLEDGE_MIN_SIMILARITY`调整；低覆盖通用关键词与低相似度同时出现时不登记引用。阈值不代表适合所有模型和语料，实际使用须评估漏召回与误召回。`datasets/eval/m1-rag/`冻结12个样本及语料hash，`scripts/verify_general_agent.py`分别输出VECTOR/KEYWORD/HYBRID结果、Hit@5、定位正确率与耗时，并将版本、跨权、空结果和伪造引用纳入最终门槛。`GET /api/runs/{id}/tool-events`只公开白名单工具摘要；RAG验收同时核对答案引用、上传文档、章节原文、工具事件，正文安全无法可靠判断时保持NOT_RUN。确定性向量只证明合同；真实embedding/LLM必须用`--embedding=real`，不会回退fake。

```powershell
mvn -q '-Dtest=KnowledgeImportTest,KnowledgeSearchTest' test
python scripts/verify_general_agent.py --suite=m1-retrieval --embedding=deterministic
python scripts/verify_general_agent.py --suite=m1-retrieval --embedding=real
python scripts/verify_general_agent.py --suite=rag-smoke --embedding=real
python scripts/verify_general_agent.py --suite=multi-turn-basic --embedding=real
```

### Phase 3 / 4 衔接验收

产品 Agent 从 `DEVICETRUTH_SKILLS_ROOT` 指向的目录读取标准 Skill，默认使用启动目录下的 `skills/`。从别的目录启动 JAR 时可设置此变量为指引目录的绝对路径。`DEVICETRUTH_SKILLS_INSTALLATIONS` 配置默认安装的 `id@version`，`DEVICETRUTH_SKILLS_ALLOWED_TOOLS` 配置服务器工具上限。已有安装的停用状态和工具绑定在重启时保留。每次调查选中的 Skill 和版本可从 `GET /api/runs/{id}/skill` 查看。

工厂演示可显式设置 `DEVICETRUTH_ASSETS_SEED_FIXTURES=true` 与 `DEVICETRUTH_ASSETS_FIXTURES=SC-0245-B`，由产品启动时导入模拟资产。观测工具只查询授权资产已导入的模拟记录，并给出质量、来源和时点。没有记录时，调查会收到尚未读取现场数据的状态。

用仓库说明中的 PostgreSQL 测试连接变量，令 `DEVICETRUTH_EVAL_DATABASE` 指向单独创建的空测试库。双域验收会导入固定样例，应分别为离线与在线运行使用独立空库。浏览器验收需要已打包 JAR、已构建的 `frontend/dist`、本机 Chrome 或 Edge 以及 Python Playwright。

```powershell
python scripts/verify_general_agent.py --suite=investigation --reasoner=scripted --embedding=deterministic
python scripts/verify_general_agent.py --suite=dual-domain --reasoner=scripted --embedding=deterministic
python scripts/verify_browser_recovery.py --browser-path 'C:/Program Files/Google/Chrome/Application/chrome.exe'
```

本轮真实PG全量241项、脚本判定10项、前端15项、打包与CLI通过；离线固定样本三通道均6/6命中，混合通道部件号2/2、全部必需负例通过。三个真实供应商命令均因缺少embedding密钥返回`BLOCKED_BY_ENV`（退出2），PDF与浏览器演示保持NOT_RUN；02-03仍待独立复审。

在已有 `DeviceTruth` 根目录运行，不要创建第二层同名目录。需要 JDK 21 和 Maven 3.9+；首次 Maven 构建需要下载依赖，以后可用已缓存依赖离线运行。`.mvn/maven.config` 将依赖缓存限制到本项目 `.m2/repository`，不改用户全局 Maven 设置。运行 JAR 不需要 Python；两个验收辅助脚本需要 Python 3。

Windows PowerShell：本工作区已下载微软 JDK 21 到 `.tools/`。以下只改变当前终端进程，不安装全局组件，也不修改 PowerShell 执行策略。

```powershell
Set-Location D:\Java_Project\DeviceTruth
$env:JAVA_HOME = (Get-ChildItem -Directory .\.tools -Filter 'jdk-21*' | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
mvn -version
mvn -q test
mvn -q package
java -jar target/devicetruth-core-0.1.0.jar --help
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted
python scripts/verify_cli.py
```

在其他电脑上，先从 JDK 厂商官方渠道安装/解压 JDK 21，再将 `JAVA_HOME` 指向实际目录；`.tools/`、`.m2/` 和 `target/` 均不作为源文件交付。若 `scripts/use-java21.ps1` 被执行策略拒绝，使用上面的内联命令，无需更改策略。

Windows **Git Bash**：`JAVA_HOME` 可以用 `D:\...`，但 `PATH` 必须用 `/d/...` 形式。`PATH` 以 `:` 分隔，`D:\...` 或 `D:/...` 里的冒号会把路径截成两段，导致 `export PATH="$JAVA_HOME/bin:$PATH"` 静默失效、`java` 仍解析到系统里的 JDK 17（`mvn` 因只读 `JAVA_HOME` 反而是 21）。设置后务必用 `java -version` 核对：

```bash
cd /d/Java_Project/DeviceTruth
export JAVA_HOME='D:\Java_Project\DeviceTruth\.tools\jdk-21.0.13.0.2+1'
export PATH="/d/Java_Project/DeviceTruth/.tools/jdk-21.0.13.0.2+1/bin:$PATH"
java -version   # 必须是 21
```

bash（在已经安装 JDK 21 的 Linux/macOS 环境）：

```bash
cd /path/to/DeviceTruth
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
mvn -q test
mvn -q package
java -jar target/devicetruth-core-0.1.0.jar --help
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted
python3 scripts/verify_cli.py
```

无 Key 的 `scripted` 模式不会调用外部 LLM。规则和 fixtures 会打入 JAR；修改 JSON 后需重新打包。演示时间固定为 `datasets/scenarios.json` 中的 `2026-11-03T04:00:00Z`，确保复现不受系统日期影响。

## HTTP Server（V0.2 S1，M1–M3）

CLI 仍是默认 fat JAR 主类（打包后 `Start-Class=io.devicetruth.app.DeviceTruthApplication`）。Web 使用独立主类 `io.devicetruth.server.DeviceTruthServerApplication`，默认只监听 `127.0.0.1:8080`，不调用 CLI 的单次执行、报告落盘或退出逻辑。

已验证的启动方式（2026-11-08，JDK 21.0.13.0.2 / Maven 3.9.9 / Spring Boot 3.5.16）：本项目的 `spring-boot-maven-plugin` 显式固定了 CLI 主类，`-Dspring-boot.run.main-class` 会被该配置覆盖而仍启动 CLI，因此按 spec §7 的备用方案使用显式 classpath。

```powershell
mvn -q package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/server-cp.txt"
java -cp "target/classes;$(Get-Content target/server-cp.txt)" io.devicetruth.server.DeviceTruthServerApplication
```

```bash
mvn -q package
mvn -q dependency:build-classpath '-Dmdep.outputFile=target/server-cp.txt'
java -cp "target/classes:$(cat target/server-cp.txt)" io.devicetruth.server.DeviceTruthServerApplication
```

启动成功后日志包含 `Tomcat started on port 8080 (http)`；健康检查返回 `{"status":"UP","dataNature":"SIMULATED"}`。

| 方法 | 路径 | 行为 |
|---|---|---|
| POST | `/api/sessions` | `201` + `{sessionId,createdAt,dataNature}`，`sessionId` 为合法 UUID，不是认证凭据 |
| GET | `/api/sessions/{sessionId}/messages` | `200` + 有界消息列表（USER/SYSTEM） |
| POST | `/api/sessions/{sessionId}/messages` | `{content,assetId?}`；`CLARIFY`/`UNSUPPORTED`/`QUERY` 返回 `200`；`DIAGNOSE` 固定 `202` + `Location: /api/diagnoses/{id}` + `executionStatus=QUEUED` |
| GET | `/api/diagnoses/{id}?sessionId=` | `200` 当前执行状态；结束后含真实 `outcome/stepsUsed/appliedRuleIds/supportingEvidenceIds` 与经安全映射的 `stopReason/diagnosisSummary/warnings/openNeeds/errorCode` |
| GET | `/api/diagnoses/{id}/events?sessionId=` | `200` + `{diagnosisId,eventsAvailable,events[]}`；运行中 `eventsAvailable=false`，结束后返回白名单事件 |
| GET | `/api/health` | `200` 稳定健康端点 |

错误体统一为 `{code,message,requestId}`：`400 INVALID_REQUEST`、`404 NOT_FOUND`（诊断不存在与不属于当前会话同码，不区分）、`429 SESSION_CAPACITY_EXCEEDED`（会话/消息满）、`429 RUN_CAPACITY_EXCEEDED`（诊断执行队列满，同时移除注册项，不留幽灵任务）、`503 ROUTER_UNAVAILABLE`、`500 INTERNAL_ERROR`。客户端不能提交 `reasoner/provider/apiKey/baseUrl/model`，未知字段会被严格拒绝（`spring.jackson.deserialization.fail-on-unknown-properties=true`）。

诊断执行是**真实异步**链路：`DiagnosisRunService` 用有界线程池（默认并发 2、队列 10）接受任务，每个任务通过 `ReasonerFactory.create()` 新建独立 Reasoner，再调用原有 `TaskOrchestrator.run(...)`。POST 永远只表示"已接受"，真实状态与结论只能 GET；Core 自己判定 `runStatus=FAILED` 时才映射为 `FAILED`，绝不包装成 `COMPLETED`。`coreTaskId` 取自 `report.state().taskId()`，报告目录只留在内部，响应里没有 `reportPath`。

公开派生字段按 `spec.md` §4.8 过滤：`stopReason/errorCode` 只放行 28 个显式白名单停止码（如 `DASHSCOPE_API_KEY_MISSING` 原样返回，其余含模型自由文本一律换成 `STOP_REASON_WITHHELD`/`RUN_FAILED_WITHHELD_REASON`）；`diagnosisSummary` 仅在真正采纳规则时输出原文；缺口只给 `openNeeds[{needId,status,relatedRuleId}]`，不带 `description`；事件摘要按白名单生成，绝不透传 `RunEvent.data`、`Reasoner.Context` 或完整 Prompt。

服务端配置（`src/main/resources/application.properties`）：`devicetruth.router.mode=offline|online`、`devicetruth.core.reasoner=scripted|openai|qwen`、`devicetruth.session.max-sessions=100`、`devicetruth.session.max-messages-per-session=40`、`devicetruth.run.max-concurrent-runs=2`、`devicetruth.run.max-queue=10`、`devicetruth.run.max-stored-runs=100`、`devicetruth.run.output-root=outputs/runs`。默认 `offline` + `scripted`；在线 Router 缺少服务端配置时返回 `503`，不会降级冒充模型识别成功。`reasoner` 配置成未知值不会让容器启动失败，而是在诊断运行时得到 `FAILED`。

用 curl/PowerShell 直接发送中文时，请确保请求体按 UTF-8 编码并声明 `Content-Type: application/json; charset=UTF-8`；Windows 终端默认本地编码会导致 `400`。所有响应和数据集均为 `SIMULATED`。

诊断示例（PowerShell，先按上文启动 Server）：

```powershell
$sid = (Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/sessions).sessionId
$r = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/sessions/$sid/messages" `
  -ContentType 'application/json; charset=UTF-8' -Body '{"content":"CAR-B 通信超时，帮我诊断"}'
$r.route; $r.executionStatus; $r.diagnosisId
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/diagnoses/$($r.diagnosisId)?sessionId=$sid"
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/diagnoses/$($r.diagnosisId)/events?sessionId=$sid"
```

## CLI 与运行产物

```text
--help / -h
--scenario=A|B|C|D             默认 A
--reasoner=scripted|qwen|openai 默认 scripted
--max-steps=12                 范围 1..200，包含假设和决策等全部动作
--max-repeated-actions=2       至少 1，不得大于 max-steps
```

采用 `--name=value`；未知参数、重复参数、非法预算或模式返回退出码 2。正常诊断结果（包括缺证、冲突、转人工）返回 0；模型或技术失败返回 2。减少总步数时请相应设置重复预算，例如 `--max-steps=1 --max-repeated-actions=1`。

每次实际诊断打印 `report=<绝对路径>`，并在 `outputs/runs/<runId>/` 写入：

| 文件 | 内容 |
|---|---|
| `result.json` | 结果、规则、引用、缺口、终止原因、步数、状态版本、reasoner、SIMULATED 标记 |
| `final_state.json` | 事实、假设与失效原因、证据需求、候选规则依赖、动作历史和预算 |
| `evidence.json` | 与状态分开保存的原始记录，含资产、来源、有效时间、记录时间和完整性 |
| `events.jsonl` | 输入快照、模型尝试、动作、工具结果、Guard 判定、版本提交和终止 |

离线 CLI 的实际结果：

| 案例 | 结果 | 最终规则 | 步数 |
|---|---|---|---|
| A：稳定配置 | `RESOLVED` | `DEMO-D-120A` | 7 |
| B：历史更换 | `RESOLVED` | `DEMO-D-120B` | 7 |
| C：当前同级证据冲突 | `CONFLICT` | 无 | 7 |
| D：缺少现场确认 | `NEED_EVIDENCE` | 无 | 6 |

例如 B 的 `result.json` 包含：

```json
{
  "outcome": "RESOLVED",
  "assetId": "CAR-B",
  "appliedRuleIds": ["DEMO-D-120B"],
  "supportingEvidenceIds": ["CAR-B-FIELD-1"],
  "stepsUsed": 7,
  "stateVersion": 7,
  "reasoner": "scripted",
  "dataNature": "SIMULATED",
  "runStatus": "COMPLETED"
}
```

这是节选；实际结果还有任务 ID、asOf、缺口、警告和停止原因。C 不选择任一版本规则，D 不把维修记录当成当前现场确认。`selectedRuleId` 可以表示待门禁通过的候选；只有 `result.appliedRuleIds` 表示已经核验采用的规则。

## 本地私有模型配置（推荐）

项目根目录的 `devicetruth.local.properties` 已加入 Git 忽略规则，不打进 JAR。当前工作区已生成该文件；新克隆项目可从 [配置模板](devicetruth.local.properties.example) 复制一份。用 UTF-8 保存，在等号后直接填写值，不加引号：

```properties
OPENAI_API_KEY=在这里填 DeepSeek API Key
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-flash

# 使用千问时填写这一项
DASHSCOPE_API_KEY=
```

DeepSeek 地址和模型名已预填，只需补 `OPENAI_API_KEY`；其他兼容服务按其要求修改地址与模型。然后从项目根目录运行，无需设置密钥环境变量：

```powershell
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=openai
python scripts/verify_online.py --reasoner=openai
```

千问填写 `DASHSCOPE_API_KEY` 后，使用 `--reasoner=qwen`。配置读取优先级为：**私有文件中的非空值 → 环境变量**；文件不存在或字段留空时保留环境变量用法。文件按进程的当前工作目录查找，修改后重启运行即可，无需重新打包。配置加载只发生在在线模式首次推理时，离线 scripted 不读取此文件；无法读取或解析时输出 `MODEL_CONFIGURATION_UNREADABLE`，不输出文件内容。

2026-11-05 已使用实际填写的私有配置执行 `python scripts/verify_online.py --reasoner=openai`，B 以 `RESOLVED` 采用 `DEMO-D-120B`，6 次模型提议、5 次受控能力查询、6 次状态提交和 1 次 Guard 放行；报告位于 `outputs/runs/684b5a60-b3d3-4a7e-ae28-87af7b248d6b/`。这是外部真实模型调用，独立于本机 HTTP 替身测试。调查缺口在工具刷新后持续保留；过滤查询不会被当成全资产调查完成，历史原始记录按程序重建的事实时间区间解释。

## 真实 Qwen 模式

在上述私有文件中填写 `DASHSCOPE_API_KEY`，也可通过自己的凭据管理方式在当前进程配置同名环境变量。密钥不写入源码、日志或报告；默认模型为 DashScope `qwen-plus`。

PowerShell：

```powershell
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=qwen
python scripts/verify_online.py
```

bash：

```bash
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=qwen
python3 scripts/verify_online.py
```

`verify_online.py` 会真实调用模型并核验 B 的规则与轨迹，需要 Key、网络和模型额度。它不属于 `mvn test`，不能用离线替身测试替代。无 Key 时脚本明确报告 `BLOCKED_BY_ENV`，不尝试模型调用。直接以 qwen 启动时，缺 Key 会写 `FAILED` 报告，`outcome=null`、`stopReason=DASHSCOPE_API_KEY_MISSING`，不会切换为 scripted。

一个提议采用一次 `Model.stream(messages, emptyTools, options)` 订阅。SDK 自动重试被明确限制为 1 次尝试，调用超时 45 秒；非法 JSON/协议最多额外重试 1 次，仍非法则 `FAILED`。工具只在外层执行。`ReActAgent.call()` 是内部完整循环，不能视作这里的一步；源码依据见 API Spike。

## OpenAI 兼容协议模式（可接 DeepSeek）

使用 `--reasoner=openai`，通过私有文件或环境变量配置以下三个同名字段，均为必填：

| 配置字段 / 环境变量 | 用途 | DeepSeek 示例 |
|---|---|---|
| `OPENAI_API_KEY` | 该服务的 API Key | 使用 DeepSeek Key |
| `OPENAI_BASE_URL` | API 基础地址，包含服务要求的路径前缀 | `https://api.deepseek.com` |
| `OPENAI_MODEL` | 服务支持的模型 ID | `deepseek-flash`，以服务当前提供的模型为准 |

接口为 `POST <OPENAI_BASE_URL>/chat/completions`，会处理基础地址末尾的斜杠。填写基础地址，不要填写完整的 `/chat/completions` 地址；若服务要求 `/v1`，将 `/v1` 写入基础地址，例如 `https://api.openai.com/v1`。使用 Bearer 认证、非流式请求和 `response_format={"type":"json_object"}`；所选服务与模型需支持这些 Chat Completions 字段。这里不使用 Responses API 或模型自动执行工具。

填写私有文件后直接运行即可。以下环境变量方式仍可使用；如果已经配置 `DEEPSEEK_API_KEY`，可按以下方式映射（私有文件的非空值优先）。PowerShell（先按上文设置 JDK 21）：

```powershell
$env:OPENAI_API_KEY = $env:DEEPSEEK_API_KEY
$env:OPENAI_BASE_URL = 'https://api.deepseek.com'
$env:OPENAI_MODEL = 'deepseek-flash'
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=openai
python scripts/verify_online.py --reasoner=openai
```

bash：

```bash
export OPENAI_API_KEY="${DEEPSEEK_API_KEY:?请先配置 DeepSeek Key，或直接配置 OPENAI_API_KEY}"
export OPENAI_BASE_URL=https://api.deepseek.com
export OPENAI_MODEL=deepseek-flash
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=openai
python3 scripts/verify_online.py --reasoner=openai
```

千问仍使用 `--reasoner=qwen`、`DASHSCOPE_API_KEY` 和 `qwen-plus`，与这三个变量互不替代。`verify_online.py` 不带参数时继续验收千问，也支持显式 `--reasoner=qwen`。两种模式分别记录 `reasoner=qwen/openai`，均沿用 45 秒超时、每次请求一次尝试、非法动作最多纠正一次和程序端证据门禁。

缺少配置时，直接运行 JAR 会生成 `FAILED` 报告、`outcome=null`，错误码分别为 `OPENAI_API_KEY_MISSING`、`OPENAI_BASE_URL_MISSING`、`OPENAI_MODEL_MISSING`；非法地址为 `OPENAI_CONFIGURATION_INVALID`。地址只允许 HTTP(S)，不接受 URL 内凭据、查询参数或片段；配置值和服务端错误正文不写入报告。在线脚本由 JAR 统一读取私有文件及环境变量，配置缺失/不可读/非法时根据失败报告返回 `BLOCKED_BY_ENV`，不发模型请求。协议依据见 [DeepSeek 接入文档](https://api-docs.deepseek.com/) 和 [JSON 输出要求](https://api-docs.deepseek.com/guides/json_mode/)。

## 结构、证据和替换接口

下表路径以 `src/main/java/io/devicetruth/` 为基准：

| 位置 | 责任 |
|---|---|
| `core/reasoning/` | 五类 ActionProposal、不可写状态的 Reasoner、可见状态驱动的 ScriptedReasoner |
| `core/runtime/TaskOrchestrator.java` | 单步循环、重复/总步数预算、提交次序和可追踪终止 |
| `core/state/` | 不可变快照、版本检查、原子提交与不可改写的证据存储 |
| `core/evidence/` | 去重、归属和完整性校验、时间重建、冲突、依赖失效 |
| `core/decision/` | 有限条件与证据门禁，输出程序核验过的规则建议 |
| `core/capability/` | 注册/发现、schema/强类型校验、只读执行、结构化错误及三个 Repository 接口 |
| `adapter/JsonRepositories.java` | JSON 适配，可替换成数据库/API 实现 |
| `adapter/AgentScopeStep.java` | AgentScope DashScope / OpenAI Chat Completions 单步模型调用 |
| `adapter/StructuredReasoner.java`、`QwenReasoner.java`、`OpenAiReasoner.java` | 共用动作协议、严格解析及有限重试，分别装配模型配置 |
| `adapter/ModelConfiguration.java` | 首次模型调用时读取工作目录中的私有配置，环境变量补缺；加载失败只返回稳定错误码 |

`skills/automotive-demo/rules.json` 是轻量业务规则包，不是 AgentScope 标准 SKILL.md 加载器。`datasets/fixtures/` 存 A–D 虚构资料，不含预填诊断答案。

工具为 `asset.profile.get`、`asset.history.query`、`evidence.search`、`evidence.get`、`diagnosis.rules.query`。Core 经 Gateway 获取数据；模型不能传入文件路径。越权资产、未知记录、非只读能力、额外/错误参数均拒绝，工具异常为结构化错误。Guard 要求完成资产历史查询、无过滤的证据发现和已发现记录获取，避免只看第一条确认就结束。

事实由记录的 `payload.subject/predicate/value` 映射。规则仅支持 `EQ`、`IN` 和所需证据类型。当前事实采用通用来源语义：`FIELD_CONFIRMATION` 优先于 `REPLACEMENT` 与出厂记录；同类取明确的 `authority`；同级重叠且取值不同保留 `CONTESTED`，不会按 `recordedAt` 随意覆盖。更换事件关闭前一配置的事实区间。有效区间为 `[validFrom, validTo)`；未来有效或在 asOf 后才记录的证据不支持当时结论。依赖事实/证据被替代、争议或时间排除后，保留原假设并记录失效原因。

跨领域替换只需提供 Repository、字段、规则并向 Gateway 注册；`ExtensibilityTest` 用 PLC、不同组件/属性和值与 `IN` 条件，通过同一 Core。V0.1 不提供完整工业产品或动态 DSL。每条记录当前承载一个字段断言；状态存于内存并落盘，不支持跨进程恢复执行。

## 验证、排错与交接

`mvn -q test` / `mvn -q package` (V0.2 S1 当前 **145 tests，28 个测试类**，0 失败/0 错误/0 跳过) 覆盖五类动作、JSON/ID/时间校验、Gateway、单步提交、预算、证据修订、Guard、A/B/C/D/E、模型协议替身、数据扰动和工业契约，以及 M1 的 Catalog/Session/四类路由/HTTP 契约（含 `201/200/400/404/429/501/503` 与容量上限），另含 M1 修复回归（模型建议不能授权/覆盖资产、`CAR-Z` 与多资产边界、危险请求程序复核、会话交互原子提交）。M2 新增事实查询与诊断目标映射测试：`QueryService` 只走真实 `profile/history`，`sources` 取真实 `evidenceId/sourceRef/sourceType`，出厂/维修记录不等同当前配置，空结果不编造，跨资产记录被拒；`DiagnosisGoal` 的业务时间固定取 Catalog 的模拟 `asOf`。M3 新增异步诊断测试：`DiagnosisRunService` 真实调用 `TaskOrchestrator`（CAR-B → `RESOLVED`/`DEMO-D-120B`）、每任务独立 Reasoner 实例、Core `FAILED` → `FAILED` 且不包装 `COMPLETED`、队列拒绝移除注册项（无幽灵任务）、`RUNNING` 期间事件不可读、归属不匹配与不存在同为 404；`GET /api/diagnoses/{id}` 与 `/events` 的 202/QUEUED/Location、白名单事件视图、以及"合成路径 + `PROMPT_FRAGMENT`"负例全部断言不回显（Core 内部报告仍保留原值）。新增本机 HTTP 替身测试检查 OpenAI 兼容请求的路径/认证/JSON、配置错误、有限重试、HTTP 失败脱敏，以及 B 经真实 SDK 和 Core 的完整链路；这些不是外部真实模型验收。扰动测试真实写出和读取临时 JSON，改变 B 的确认值/移除确认/注入冲突，Core 不变。`scripts/verify_cli.py` 另行启动打包 JAR，检查输出文件、事件序号、两种在线模式的缺 Key 失败，以及无环境变量时从私有文件连接本机模拟接口（不访问外部服务），汇总到 `target/cli-verification.json`。

- `release version 21 not supported`：检查 `mvn -version` 的 Java 路径，确保当前终端 JAVA_HOME 和 PATH 均指向 21。
- Maven 首次下载失败：确认可访问 Maven Central；不要把依赖下载失败写成测试通过。已缓存后可执行 `mvn -o -q test`。
- PowerShell 若拆分 Java `-D` 参数，请整体引用，如 `java '-Djava.io.tmpdir=target' -jar ...`。测试 JVM 已自动将临时文件限制在项目 `target/`。
- `MODEL_TRANSPORT_OR_TIMEOUT`：查模型尝试和技术失败代码，核验 Key、网络及服务；不记录凭据或原始异常详情。
- `NEED_EVIDENCE`：查缺口和 stopReason；预算/重复动作耗尽会保留原因。工具持续失败且无进展时可 `ESCALATE`。
- `CONFLICT`：需补充能够裁决的证据，版本专属规则不能绕过同级冲突。

下一会话先读 [.planning/STATE.md](.planning/STATE.md)、[PROJECT.md](.planning/PROJECT.md) 和 [NEXT_SESSION.md](.planning/NEXT_SESSION.md)。旧 [spec.md](docs/DeviceTruth_Core_V0.1_Spec_Pack/spec.md)、[task.md](docs/DeviceTruth_Core_V0.1_Spec_Pack/task.md)、[state.md](docs/DeviceTruth_Core_V0.1_Spec_Pack/state.md) 用于理解 legacy 回归，不自动续跑旧任务。真实模型验收与离线结果分开记录；历史 DeepSeek 成功不代表新产品全链路通过。

后续可选方向为数据库/API Repository、更多领域适配和任务恢复；V0.1 没有实现 UI、多 Agent、知识库、MCP 平台或设备控制。
