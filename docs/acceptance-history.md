# 历史验收记录（留档）

> 本文件保存从 README 迁入的历史实施与验收记录（2026-11-14 整理）。这些段落描述的是**各历史时点的事实**，不再随项目更新；当前状态一律以 [.planning/STATE.md](gsd-archive/.planning/STATE.md) 为准。内容按原 README 原文保留，仅按阶段归类。

## V0.1 历史（Core 内核，整体 DONE）

### 基线与契约（01-01 / 01-02）

- 01-01 的验证、配置策略与局限见 [BASELINE](gsd-archive/.planning/phases/01-baseline-contracts/BASELINE.md)，新输入/追问/引用/风险约定见 [CONTRACTS](gsd-archive/.planning/phases/01-baseline-contracts/CONTRACTS.md)，主循环取舍见 [Runtime ADR](gsd-archive/.planning/phases/01-baseline-contracts/ADR-runtime.md)。测试原型复现命令：`mvn -q '-Dtest=RuntimeComparisonTest' clean test`，不需要密钥或数据库。
- 01-01 与 01-02 完成：基线/契约/Runtime 原型，以及使用 interview-postgres 的 PG 会话/消息/QUEUED 切片。

### V0.1 结论（历史时点）

DeviceTruth 是可运行的 Java 单 Agent Core。每步读取独立的结构化状态，由 Reasoner 提出一个动作；Gateway 执行至多一个只读能力；Evidence Manager 重建事实并使旧依赖失效；State Manager 提交新版本；Decision Guard 决定是否允许结论。模型不能修改事实、写证据或自行执行工具。

**资料与规则均为 `SIMULATED`，仅用于工程验收，不能指导实际车辆或工业设备维护。** 离线验收及 OpenAI 兼容接口的真实场景 B 已通过（6 步、DEMO-D-120B，共 2 次独立运行）；全部 MUST 任务已完成，整体 DONE。千问 DashScope 适配路径尚未被真实执行过，属已知残留风险，记录在 state.md 的设计决定 16，不是阻塞项。

### 离线 CLI 实际结果（历史时点）

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

### 真实模型验收（历史时点）

2026-11-05 已使用实际填写的私有配置执行 `python scripts/verify_online.py --reasoner=openai`，B 以 `RESOLVED` 采用 `DEMO-D-120B`，6 次模型提议、5 次受控能力查询、6 次状态提交和 1 次 Guard 放行；报告位于 `outputs/runs/684b5a60-b3d3-4a7e-ae28-87af7b248d6b/`。这是外部真实模型调用，独立于本机 HTTP 替身测试。调查缺口在工具刷新后持续保留；过滤查询不会被当成全资产调查完成，历史原始记录按程序重建的事实时间区间解释。

### 2026-11-12 模型配置统一决定

对话模型与 embedding 均可使用根目录 `devicetruth.local.properties`（示例见 `devicetruth.local.properties.example`），私有文件不提交。PG 凭据仍通过服务端环境注入。对话模型密钥沿用私有文件非空值优先；embedding 的显式 `DEVICETRUTH_EMBEDDING_*` 环境变量优先，其次为私有文件，再使用服务端默认值。专用 embedding 密钥为空时复用 `DASHSCOPE_API_KEY`。不打印或写入报告。

## Phase 2 历史（02-01 已复审通过 / 02-02 已提交 / 02-03 随阶段带遗留项关闭）

### M1 知识导入与检索（02-01，已复审通过）

`V2__knowledge_sources.sql` 在既有库上**追加**`dt_uploaded_file`、`dt_knowledge_document`、`dt_knowledge_chunk`、`dt_evidence_reference` 四张表，并创建 pgvector 余弦距离的 HNSW 索引。`V3__knowledge_embedding_identity.sql` 曾按模型名推断供应商，无法证明旧向量来源；`V4__knowledge_legacy_quarantine.sql` 因此把所有修复前写入的知识向量统一标记为 `NEEDS_REBUILD`、档案改为 `legacy-unverified:@<dim>-cosine-r0`，**不删除正文/切片/历史引用，也不重算向量**。三张迁移都不改动 V1 会话/消息/任务/事件表，重复启动只校验已执行迁移（02-02 的 `V5` 另行给 `dt_message` 增加可空 `run_id`）。

| 方法 | 路径 | 当前行为（历史时点） |
|---|---|---|
| POST | `/api/knowledge/documents` | 导入MD/TXT：校验→按章节切片→固定profile向量化→一个事务写文件元数据、文档与切片；相同内容+版本且档案一致返回`reused=true`；被隔离的旧文档返回`409 DOCUMENT_NEEDS_REBUILD`（需用新版本重建） |
| GET | `/api/knowledge/documents/{id}` | 当前身份可见的文档元数据（版本、profile、维度、切片数） |
| GET | `/api/knowledge/tools` | 服务端知识工具合同（`search_knowledge`、`get_document_excerpt`）与JSON Schema |
| POST | `/api/knowledge/search` | `{runId,query,filters?,limit?,mode?}`：按owner/project（及领域/型号/文档）先过滤候选，再进行向量/关键词/RRF混合召回；`mode`可供固定样本分通道评估，Agent默认HYBRID |
| GET | `/api/knowledge/references/{referenceId}?runId=` | 打开本run已取回引用的原文与章节/页码/文档版本；未取回409 `INVALID_CITATION`，越权404 |
| GET | `/api/knowledge/documents/{documentId}/chunks/{chunkId}?runId=` | 按文档/切片定位读取本run已取回原文，伪造或越权定位返回404 |

`query` 允许中文、空格与标点（上限2000码点）；`filters` 只接受 `domain/deviceModel/documentId`，其他键与伪造 documentId 均被拒绝。embedding 档案标签由 `provider:model@dimension-cosine-r<revision>` 推导（默认 `openai:text-embedding-v4@1024-cosine-r1`），供应商/模型/维度/修订任一变化都产生新档案，不能复用旧文档或交叉召回；配置维度与数据库列（`vector(1024)`）不符时导入与检索都返回409 `EMBEDDING_DIMENSION_MISMATCH`。修复前写入的旧向量按 `NEEDS_REBUILD` 隔离，既不参与召回也不能被当前档案复用；用新的文档版本重新导入同一内容即可重建，旧正文与历史引用仍可通过引用ID读取。原始文件字节不写入数据库，`path_key` 由服务器生成；Markdown 原文件保存在受控目录。

embedding 配置可放入私有文件或 `DEVICETRUTH_EMBEDDING_*` 服务端环境变量。默认是**千问 `text-embedding-v4`**（DashScope compatible-mode，1024维 cosine）；`DEVICETRUTH_EMBEDDING_API_KEY` 为空时复用既有的 `DASHSCOPE_API_KEY`。`devicetruth.embedding.provider=deterministic` 只是离线合同测试与本地无密钥演示，**不能证明语义召回质量**。

### M1 通用 Agent 与 Vue 产品页（02-02）

产品入口不再只登记 QUEUED：`application/agent/GeneralAgentService` 从 PG 重建上下文，用唯一主循环 `TaskOrchestrator.runAgent(...)` 单步推进。模型只能提出 `CALL_TOOL`/`ASK_USER`/`ANSWER` 三种动作；工具必须已通过 `agentCapabilityGateway` 注册。

`AnswerGuard` 只在服务器侧判定，规则比"过滤几个坏词"更严：

- **引用**：草稿与各断言里的 `referenceId` 都必须是本 run 已取回集合，且都进入答案引用列表；伪造引用 → `INVALID_CITATION`。
- **不得确诊**：`fieldVerified` 由服务器给出，M1 恒为 `false`（03-01 接入资产记录后才可能为真）。此阶段任何 `CONFIRMED_FINDING` 类别或 `CONFIRMED` 断言一律 `UNCONFIRMED_FINDING` 拒绝，**不会降级后继续保留"已确诊"正文**；资产编号只登记、不当作核验。
- **危险建议**：`短接/上电/强制闭合/绕过互锁/拆装/拆除/屏蔽保护` 等动作词出现在**没有安全语义**的句子里（summary、claim、suggestion 都查）即 `UNSAFE_ANSWER`；带"不得/禁止/必须由具备资质的人员现场执行"的引文可以发布，但标 HIGH + `REQUIRES_QUALIFIED_PERSONNEL`。
- **数据性质**：只有全部引用都是 `AUTHORIZED` 才标 `AUTHORIZED`；混合或没有引用一律 `SIMULATED`。

`V5__agent_run_message_link.sql` 给 `dt_message` 增加可空 `run_id`（复合外键与CHECK），使补答后的上下文只包含本 run 的消息。该迁移只新增可空列，不回填历史数据。

### M1 Markdown 上传与混合检索（02-03）

按用户决定，本计划以 Markdown 上传替代 PDF 实现。`POST /api/files` 接收 multipart `.md`，限制1MiB、Markdown MIME和严格UTF-8；服务器生成`knowledge/<uuid>`路径并计算SHA-256，原始文件名不能参与路径。`FileUploadService`以有界后台任务完成解析、切片、向量化和PG事务写入，状态为`UPLOADED/PROCESSING/PARSED/FAILED`；`GET /api/files/{id}`读取状态与稳定失败码，`POST /api/files/{id}/retry`重试失败项。`V6`追加导入状态和元数据，`V7`解除"同内容只能一个文件"的旧约束并建立文件—文档关联；同内容同版本且元数据/embedding档案一致可复用文档，不同版本新建文档，冲突明确失败。

消息可使用`attachments:[{fileId}]`关联同owner/project且已完成导入的文件，也可用`contextRefs:[{referenceId}]`把同归属历史证据登记到当前run。非法、未完成或跨权引用均在写消息/run前拒绝。目标消息和每次补答的附件以各自的结构化元数据进入Agent上下文，不拼成用户指令。Vue可上传Markdown、显示失败状态并展开章节引用。

默认检索为向量+关键词RRF混合召回，精确部件号有额外排名权重；权限、档案与READY过滤仍在SQL候选阶段。真实embedding小样本实测后，默认余弦相似度下限为0.45，可通过`DEVICETRUTH_KNOWLEDGE_MIN_SIMILARITY`调整；低覆盖通用关键词与低相似度同时出现时不登记引用。阈值不代表适合所有模型和语料，实际使用须评估漏召回与误召回。`datasets/eval/m1-rag/`冻结12个样本及语料hash，`scripts/verify_general_agent.py`分别输出VECTOR/KEYWORD/HYBRID结果、Hit@5、定位正确率与耗时，并将版本、跨权、空结果和伪造引用纳入最终门槛。`GET /api/runs/{id}/tool-events`只公开白名单工具摘要。确定性向量只证明合同；真实embedding/LLM必须用`--embedding=real`，不会回退fake。

## Phase 3 / 4 衔接验收（历史时点）

产品 Agent 从 `DEVICETRUTH_SKILLS_ROOT` 指向的目录读取标准 Skill，默认使用启动目录下的 `skills/`。从别的目录启动 JAR 时可设置此变量为指引目录的绝对路径。`DEVICETRUTH_SKILLS_INSTALLATIONS` 配置默认安装的 `id@version`，`DEVICETRUTH_SKILLS_ALLOWED_TOOLS` 配置服务器工具上限。已有安装的停用状态和工具绑定在重启时保留。每次调查选中的 Skill 和版本可从 `GET /api/runs/{id}/skill` 查看。

工厂演示可显式设置 `DEVICETRUTH_ASSETS_SEED_FIXTURES=true` 与 `DEVICETRUTH_ASSETS_FIXTURES=SC-0245-B`，由产品启动时导入模拟资产。观测工具只查询授权资产已导入的模拟记录，并给出质量、来源和时点。没有记录时，调查会收到尚未读取现场数据的状态。

用仓库说明中的 PostgreSQL 测试连接变量，令 `DEVICETRUTH_EVAL_DATABASE` 指向单独创建的空测试库。双域验收会导入固定样例，应分别为离线与在线运行使用独立空库。浏览器验收需要已打包 JAR、已构建的 `frontend/dist`、本机 Chrome 或 Edge 以及 Python Playwright。

> 2026-11-11 轮次记录：本轮真实PG全量241项、脚本判定10项、前端15项、打包与CLI通过；离线固定样本三通道均6/6命中，混合通道部件号2/2、全部必需负例通过。三个真实供应商命令均因缺少embedding密钥返回`BLOCKED_BY_ENV`（退出2），PDF与浏览器演示当时保持NOT_RUN。后续阶段已补齐浏览器演示与真实模型E2E。

## Phase 5（05-01–05-03，已完成并实测）

**05-01 SSE 实时进度**：`AgentConversationController` 新增 `GET /api/runs/{id}/events`（历史事件）与 `GET /api/runs/{id}/stream`（SSE，先历史后订阅再补读，订阅有界，断开清理）；`SafeAgentEventViews` 白名单映射事件；V16 渐进持久化运行事件并保证终态事件同事务。`AgentStreamTest` 7/7。验收：`scripts/verify_sse_browser.py` 浏览器三场景 PASS（断点重连、会话切换零冗余请求、终态历史回读）。

**05-02 工作台与 E2E**：前端新增 `DiagnosisReport`/`EvidenceComparison`/取消/Skill 组件；14 例 E2E 矩阵（`scripts/verify_v1_e2e.py`）——离线 PASS6/覆盖4/未跑4；在线 DeepSeek+DashScope PASS9/覆盖4/FAIL1（E2E-07 模型首轮未追问，如实记录）；PDF 保持明确未实现状态。

**05-03 部署与运维**：交付 `Dockerfile`、`frontend/Dockerfile`+`nginx.conf`、`compose.yaml`（product/standalone-db profile）与 [operations.md](operations.md)。演练 PASS（`scripts/verify_deployment.ps1`，证据 `outputs/validation/deployment-20261114014426/`）：启动→产品链路→重启持久→备份恢复 6 表行数一致→迁移失败阻断→forward-fix 恢复。真实认证未接入，共享部署前必须补。

## HTTP Server（V0.2 S1，M1–M3，legacy）

CLI 是默认 fat JAR 主类（`Start-Class=io.devicetruth.app.DeviceTruthApplication`）。Web 使用独立主类 `io.devicetruth.server.DeviceTruthServerApplication`，默认只监听 `127.0.0.1:8080`。旧 sessions/diagnoses 接口继续保留，无需数据库。

| 方法 | 路径 | 行为（legacy） |
|---|---|---|
| POST | `/api/sessions` | `201` + `{sessionId,createdAt,dataNature}` |
| GET | `/api/sessions/{sessionId}/messages` | `200` + 有界消息列表（USER/SYSTEM） |
| POST | `/api/sessions/{sessionId}/messages` | `{content,assetId?}`；`CLARIFY`/`UNSUPPORTED`/`QUERY` 返回 `200`；`DIAGNOSE` 固定 `202` + `Location` |
| GET | `/api/diagnoses/{id}?sessionId=` | `200` 当前执行状态；结束后含真实 `outcome/stepsUsed/appliedRuleIds/supportingEvidenceIds` 与安全映射字段 |
| GET | `/api/diagnoses/{id}/events?sessionId=` | `200` + 白名单事件；运行中 `eventsAvailable=false` |
| GET | `/api/health` | `200` 稳定健康端点 |

错误体统一为 `{code,message,requestId}`：`400 INVALID_REQUEST`、`404 NOT_FOUND`、`429 SESSION_CAPACITY_EXCEEDED`、`429 RUN_CAPACITY_EXCEEDED`、`503 ROUTER_UNAVAILABLE`、`500 INTERNAL_ERROR`。诊断执行是真实异步链路：`DiagnosisRunService` 用有界线程池（默认并发 2、队列 10），POST 只表示"已接受"。公开派生字段按白名单过滤（28 个停止码、不回显 `RunEvent.data`/Prompt 等）。

## 结构、证据和替换接口（V0.1 legacy 详述）

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
| `adapter/ModelConfiguration.java` | 首次模型调用时读取工作目录中的私有配置，环境变量补缺 |

`skills/automotive-demo/rules.json` 是轻量业务规则包，不是 AgentScope 标准 SKILL.md 加载器。`datasets/fixtures/` 存 A–D 虚构资料，不含预填诊断答案。legacy 工具为 `asset.profile.get`、`asset.history.query`、`evidence.search`、`evidence.get`、`diagnosis.rules.query`。

事实由记录的 `payload.subject/predicate/value` 映射。规则仅支持 `EQ`、`IN` 和所需证据类型。当前事实采用通用来源语义：`FIELD_CONFIRMATION` 优先于 `REPLACEMENT` 与出厂记录；同类取明确的 `authority`；同级重叠且取值不同保留 `CONFLICTED`。有效区间为 `[validFrom, validTo)`。跨领域替换只需提供 Repository、字段、规则并向 Gateway 注册；`ExtensibilityTest` 用 PLC、不同组件/属性和值与 `IN` 条件，通过同一 Core。每条记录当前承载一个字段断言；状态存于内存并落盘，不支持跨进程恢复执行。

## 验证详情（历史时点）

- `PersistentConversationTest` 覆盖空库迁移、服务关闭重开、原文/Unicode、事务回滚、重复/并发提交及双身份/项目隔离。缺少真实PG环境时失败，不跳过冒充通过。
- M2 新增事实查询与诊断目标映射测试：`QueryService` 只走真实 `profile/history`，空结果不编造，跨资产记录被拒。
- M3 新增异步诊断测试：真实调用 `TaskOrchestrator`、每任务独立 Reasoner 实例、Core `FAILED` → `FAILED` 不包装 `COMPLETED`、队列拒绝无幽灵任务、`RUNNING` 期间事件不可读。
- 本机 HTTP 替身测试检查 OpenAI 兼容请求的路径/认证/JSON、配置错误、有限重试、HTTP 失败脱敏，以及 B 经真实 SDK 和 Core 的完整链路；这些不是外部真实模型验收。
- 扰动测试真实写出和读取临时 JSON，改变 B 的确认值/移除确认/注入冲突，Core 不变。`scripts/verify_cli.py` 另行启动打包 JAR 汇总到 `target/cli-verification.json`。

## 交接说明

下一会话先读 [.planning/STATE.md](gsd-archive/.planning/STATE.md)、[PROJECT.md](gsd-archive/.planning/PROJECT.md) 和 [NEXT_SESSION.md](gsd-archive/.planning/NEXT_SESSION.md)。旧 [spec.md](../docs/DeviceTruth_Core_V0.1_Spec_Pack/spec.md)、task.md、state.md 用于理解 legacy 回归，不自动续跑旧任务。真实模型验收与离线结果分开记录；历史 DeepSeek 成功不代表新产品全链路通过。
