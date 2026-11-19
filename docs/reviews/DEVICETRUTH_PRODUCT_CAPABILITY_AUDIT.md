# DeviceTruth 全产品能力盘点与商业化增量开发调研

> 2026-11-18（北京时间） · 源码基线 codex/frontend-workbench-v2 @ 6d23cd2 · 产品调研供人工评审

## 1. 执行摘要

DeviceTruth 已有可复用的检修调查主链路。自然语言进入持久会话，Agent 在有界循环中按需查询知识、资产和 Skill，服务端校验答案后保存带引用的结果。会话和运行历史、追问恢复、取消、断线回读、知识导入和引用、资产时点查询均已有实现和验证证据。工业与新能源汽车已有真模演示验收，采用项目自制演示手册和模拟观测。

当前形态适合单用户本地演示和受控试点。对外商用的第一道门槛是**请求没有真实用户身份**。同一部署中的访客共享服务器固定 owner/project。现有资产、知识、会话和 Skill 已具备归属过滤的基础，仍需把身份从部署配置改为可信请求身份，并让后台 worker、SSE、知识和配置管理使用同一权限边界。

推荐维持 Spring Boot 单体和 PostgreSQL。先打通登录与隔离，修复专业页补答、证据可达性和重复提交，再以现有专业工作台作为首个试点。普通用户用同一链路提供简化咨询和追问。管理端首期围绕用户、调用记录、可检索知识、资产和 Skill 的受控管理，商业计费、团队协作、设备实时接入后置。

### 调研依据与证据口径

- 当前源码、配置和 DDL 为现状基准。配置包含 [application.properties](../../src/main/resources/application.properties)、[application-product.properties](../../src/main/resources/application-product.properties)、[compose.yaml](../../compose.yaml) 和部署代理。未读取或披露私有配置值。
- 产品基准为 [V1.0 产品设计](../design/DeviceTruth_V1.0_通用设备检修Agent_完整产品设计方案_PostgreSQL.md)，同时核阅 [README](../../README.md)、[AGENTS](../../AGENTS.md)、[V0.1 规格](../DeviceTruth_Core_V0.1_Spec_Pack/spec.md)、[任务](../DeviceTruth_Core_V0.1_Spec_Pack/task.md)、[状态](../DeviceTruth_Core_V0.1_Spec_Pack/state.md)、[数据补充](../skills/data-enrichment.md)、[Skill 规范化](../skills/skill-normalization.md)、[AgentScope 选型](../research/notes/agentscope-spike.md)、[数据库旧字典](DEVICETRUTH_DATABASE_DICTIONARY.csv) 和 [运维手册](../operations/operations.md)。
- [OpenSpec 配置](../../openspec/config.yaml) 使用 spec-driven，产物约定 zh-CN。主 specs 与 archive 只有占位，仓库当前可见变更是未归档的 [专业工作台 MVP](../../openspec/changes/devicetruth-pro-workbench-mvp/tasks.md)，含三个 delta（professional-diagnosis、investigation-continuation、investigation-recovery）。14 个 P0 勾选，两项 P1 未勾选。任务文本中真实授权资料与修订后的演示口径有冲突，以 [实际验收记录](../../openspec/changes/devicetruth-pro-workbench-mvp/acceptance.md) 为历史证据。冻结之后还有最新布局提交。
- [后端准备度审查](DEVICETRUTH_BACKEND_MVP_READINESS_REVIEW.md) 的 QUEUED 滞留结论已被源码和历史故障注入更新。[专业 MVP 实施后审查](DEVICETRUTH_PRO_MVP_POST_IMPLEMENTATION_REVIEW.md) 的 F01–F08 与 [诊断呈现审查](DEVICETRUTH_DIAGNOSIS_PRESENTATION_AUDIT.md) 的结果密度问题用于交叉核验。后两份为本工作区已有未跟踪文件。
- 矩阵证据简称：**本测**是本轮实际执行，**回读**是本机部署的只读 GET，**库验**是本机 PG 只读结构查询，**历验**是仓库已有测试/验收记录，**源码**表示静态核验。新开发建议的行为均为 **UNVERIFIED**。历验环境、真模和产品浏览器版本的适用范围见第 12 节。

## 2. 总体架构与入口

~~~mermaid
flowchart LR
  U["Vue 工作台 / 未来普通用户页"] --> C["product Controllers"]
  C --> S["GeneralAgentService"]
  C --> K["文件 / 知识 / 资产服务"]
  S --> W["有界 Worker 与恢复服务"]
  W --> O["TaskOrchestrator.runAgent"]
  O --> R["GeneralReasoner / 单步模型适配"]
  O --> G["CapabilityGateway"]
  G --> K
  G --> SK["SkillRuntime / Registry"]
  O --> A["AnswerGuard"]
  W --> P["JDBC Repository / PostgreSQL + pgvector"]
  K --> P
  SK --> P
  P --> E["RunEventStreamService / 历史事件"]
  E --> U
~~~

- CLI 的 DeviceTruthApplication 装配 JSON、legacy Reasoner 和 TaskOrchestrator.run，最后由 RunReportWriter 落盘。legacy HTTP 的 ConversationFlowService 先路由，再由 DiagnosisRunService 运行固定场景，使用内存会话，受支持目录和模拟数据约束。
- 产品 HTTP 的 DeviceTruthServerApplication 通过 product profile 装配 ProductConfiguration 和 ServerConfiguration。产品任务直接进入 GeneralAgentService，绕过 legacy Router。两种 profile 应单独启动，代码使用两个独立的 @Profile 条件，部署配置负责选择。无 profile 时业务 Controller 只剩健康探针。静态演示页与 Vue 工作台属于不同入口。
- ProductConfiguration 手工创建 Hikari、Flyway（Bean 初始化调用 migrate）、worker、恢复、流和默认 Skill/可选资产种子。服务端启动可能写库。本次仅访问现有运行进程。模型/embedding 配置由进程属性、环境和本地私有文件提供，具体解析顺序见第 5 节。
- 产品 Repository 聚焦会话、Run、资产和 Skill；知识和文件服务直接使用 JdbcTemplate/TransactionTemplate。AgentResult、RunView、事件公开视图分别承担业务答案、执行合同和时间线。核心复用点是动作协议、网关授权、引用登记、答案 Guard、运行状态机与事件流。

## 3. 完整业务 API 清单

按 Spring MVC 中显式声明的 HTTP 方法与路径计数。**26 个 product 专用 + 5 个 legacy 专用 + 1 个共用，共 32 个**。product 启动时 27 个，legacy 启动时 6 个。另有框架 Actuator、静态资源和隐式 HEAD/OPTIONS，未计入业务数。本轮从后端直连 /actuator/health 得到 200，/actuator/metrics 得到 500，未核验该错误的根因，未确认指标端点可用。前端同源代理只明示 /api/。

表中“工作台”表示当前生产组件沿调用链到达该接口。“封装”表示已导出 API 函数但未见生产组件调用。“固定域”指当前服务器 owner/project，产品没有登录、角色或管理员鉴权。G 表示 Run/会话归属过滤，K 表示文档/文件归属，KR 表示归属加 Run 内引用边界，AS 表示资产归属。错误合同按 Controller 实际代码分为 Group A 的 {code,message,retryable,traceId} 与文件/知识/legacy 的 ErrorResponse {code,message,requestId}，参见前端 ApiError 的兼容解析。

| 编号 | HTTP Method + Path | Controller 方法与请求 → 响应 | 权限、使用方 |
|---|---|---|---|
| H1 | GET /api/health | HealthController.health，无参数 → HealthResponse，静态 UP/SIMULATED | 共用，运维探针 |
| R1 | POST /api/conversations | create，空体/空对象 → 201 ConversationView + Location | 固定域，工作台 |
| R2 | GET /api/conversations | list → ConversationView[] | G，工作台，最近 100 |
| R3 | GET /api/conversations/{id} | read → ConversationDetail（会话、消息、runs） | G，工作台 |
| R4 | GET /api/conversations/{id}/messages | messages → MessageView[]，由 detail 投影 | G，联调，Vue 用 R3 |
| R5 | POST /api/conversations/{id}/messages | submit，UUID Idempotency-Key，AgentTaskInput → 202 RunView + Location | G，工作台，普通消息与补答同入口 |
| R6 | GET /api/runs/{id} | run → RunView | G，工作台轮询/回读 |
| R7 | GET /api/runs/{id}/skill | skill → SelectionView（skillId/version/devices/tasks），无记录走 404 | G，工作台 |
| R8 | GET /api/runs/{id}/references | references → AgentResult.Reference[] | G，工作台 |
| R9 | GET /api/runs/{id}/tool-events | toolEvents → ToolEvent[] | G，联调，Vue 主要用 R11/R12 |
| R10 | GET /api/runs/{id}/model-events | modelEvents → ModelEvent[] | G，联调，Vue 主要用 R11/R12 |
| R11 | GET /api/runs/{id}/events | runEvents，after 可选非负 seq → PublicEvent[] | G，工作台 |
| R12 | GET /api/runs/{id}/stream | runEventStream，Last-Event-ID 可选 → SSE（id、事件名、公开数据） | G，工作台，连接限额与 TTL |
| R13 | POST /api/runs/{id}/cancel | cancel → RunView，终态重复取消 409 | G，工作台 |
| A1 | GET /api/assets | assets → 资产 ID 列表 | AS，工作台下拉 |
| A2 | GET /api/assets/{assetId} | asset，可选 ISO-8601 asOf → AssetFactService.Composite | AS，EvidenceComparison |
| A3 | GET /api/assets/{assetId}/configuration | configuration，componentId/attribute/asOf 可选 → ConfigurationOutcome | AS，联调 |
| A4 | GET /api/assets/{assetId}/maintenance | maintenance，componentId/asOf 可选 → MaintenanceOutcome | AS，联调 |
| K1 | POST /api/knowledge/documents | importDocument，ImportRequest（title/domain/deviceModel/version/filename/mimeType/content/dataNature）→ 201 ImportResult | K，脚本/联调 |
| K2 | GET /api/knowledge/documents/{documentId} | document → DocumentMetadata | K，脚本/联调 |
| K3 | GET /api/knowledge/tools | tools → ToolCatalog（知识工具合同） | product，无资源 ID，无管理员角色检查 |
| K4 | POST /api/knowledge/search | search，SearchRequest（runId/query/filters/limit/mode）→ SearchResponse | KR，有引用登记写入，Vue 由 Agent 用工具 |
| K5 | GET /api/knowledge/references/{referenceId} | reference，query runId → Excerpt | KR，联调，Vue 使用已回读的片段 |
| K6 | GET /api/knowledge/documents/{documentId}/chunks/{chunkId} | excerpt，query runId → Excerpt | KR，联调 |
| F1 | POST /api/files | upload，multipart file/title/domain/deviceModel/version/dataNature → 202 FileView | K，工作台 |
| F2 | GET /api/files/{fileId} | file → FileView | K，工作台轮询 |
| F3 | POST /api/files/{fileId}/retry | retry，空体 → 202 FileView | K，Vue 仅封装 |
| L1 | POST /api/sessions | ConversationController.createSession → 201 CreateSessionResponse | legacy 内存，随包演示页 |
| L2 | GET /api/sessions/{sessionId}/messages | messages → MessagesResponse | legacy session，随包演示页 |
| L3 | POST /api/sessions/{sessionId}/messages | message，MessageRequest(content,assetId) → RouteResponse，诊断 202 + Location | legacy session，随包演示页 |
| L4 | GET /api/diagnoses/{diagnosisId} | diagnosis，sessionId query → DiagnosisView | legacy session 范围，随包演示页 |
| L5 | GET /api/diagnoses/{diagnosisId}/events | events，sessionId query → DiagnosisEventsResponse | legacy session 范围，随包演示页 |

源码索引：[Controller 目录](../../src/main/java/io/devicetruth/api)、[App.vue](../../frontend/src/App.vue)、[api.ts](../../frontend/src/api.ts)、[timeline.ts](../../frontend/src/timeline.ts)、[legacy 静态页](../../src/main/resources/static/index.html)。R5 请求含 schemaVersion、userMessage、selectedAssetId、asOf、attachments、contextRefs、replyTo；R6 返回状态、问题、结果、错误、时点与预算/用量等。已有 14 个 product API 在 Vue 运行路径使用。仅内部能力包括资产快照导入、默认 Skill 安装与运行时激活、孤儿 Run 恢复、模拟观测、诊断资料汇总工具。登录、管理、跨会话 Run 列表、文档列表/删除、资产写入、Skill 管理、统计/配额均没有业务 API。

## 4. 数据库与业务实体盘点

V1–V17 为增量迁移，Flyway 历史表单独计数。V1 会话/Run/事件与 vector；V2 知识/引用；V3–V4 embedding 档案及旧向量隔离；V5 消息绑定 Run；V6–V7 文件状态/版本关联；V8–V11 资产及引用/记录身份、配置有效期；V12–V13 租约及生命周期预算；V14 Skill；V15 观测；V16 事件 attempt；V17 执行检查点与最近激活参考。当前本机 PG 16.15、vector 0.8.7，17 条迁移均成功，17 张 dt_ 业务表。DDL 的 scope 外键、唯一键及 SQL 过滤共同约束访问。本库没有用户、角色、Workspace、账单、配置目录、独立知识库或专门模型用量表。

| 表 | 域及用途 | 核心字段、关联 | Repository/Service 与真实操作 | 缺少的管理能力 |
|---|---|---|---|---|
| dt_conversation | 用户，会话根 | id、owner/project、version、时间 | ConversationRepository.create/list/require，消息事务更新版本 | 分页、名称、归档、保留和经授权导出 |
| dt_message | 用户，输入/回答 | conversation_id、run_id、seq、role、content、request_key、selected_asset_id、as_of、attachment/context refs、reply_to | ConversationRepository.append*、messages、runContextMessages、recentConversationHistory | 对话导出/留存、隐私处理；现场记录独立结构 |
| dt_agent_run | 内部，运行和结果 | conversation/goal_message/result_ref、status/version、pending_question、budget、lease、execution_checkpoint、active_accounted_at | AgentRunRepository.enqueue/load/list/markRunning/queueReply/complete/fail/requestCancel/saveCheckpoint/updateModelUsage | 可分页运营索引、分状态统计、保留；末步待提交结果 |
| dt_run_event | 运营兼内部，追加事件 | run_id/seq、conversation_id、type、recorded_at、attempt、safe_payload | AgentRunRepository.appendEvent/eventsAfter/toolEvents/modelEvents，SSE 回放 | 运营聚合、事件保留、独立管理操作审计 |
| dt_evidence_reference | 内部，Run 取证 | run/conversation、source_type/id/version、chunk_id、asset_id、as_of、citation_page/section/excerpt、data_nature | KnowledgeSearchService 登记/扩读，AssetFactReferenceWriter.register，AgentRunRepository.references/importReference | 生命周期联动、保留/脱敏策略；历史引用不可任意删除 |
| dt_uploaded_file | 知识，原件索引/异步导入 | path_key、原名/MIME/checksum/大小、parse_status/error、import 元数据 | FileUploadService.upload/require/retry/readyAttachments，KnowledgeImportService 文本导入建关联文件记录 | 列表/审核，归档、孤儿文件清理、授权下载 |
| dt_knowledge_document | 知识，文档版本 | source_file、title/domain/device_model、doc_version/checksum、ingest_status、embedding 档案、data_nature | KnowledgeImportService.importDocument/importMarkdown/document，同 scope 指纹及版本复用 | 集合、版本发布/回退、下架、重建接口 |
| dt_knowledge_chunk | 知识，片段索引 | document_id、chunk_no/content/section/page_no、embedding、profile | KnowledgeImportService 批量写；KnowledgeSearchService 查询及扩读 | 管理员预览、重索引任务入口；避免直接编辑向量 |
| dt_knowledge_document_file | 知识，文件到版本的关联 | file_id、document_id、owner/project，复合关联 | KnowledgeImportService 关联写，FileUploadService join 回读 | 清理引用前的关系核对、附件保留策略 |
| dt_asset | 资产，登记身份 | id（全局主键）、owner/project、created_at | AssetRegistryRepository.asset/importSnapshot；AssetFactService.assets/exists/composite | 自助创建、名称/类型/型号、归档、跨域 ID 策略 |
| dt_asset_component | 资产，部件/逻辑节点 | asset_id、component_id、label、scope | AssetRegistryRepository.components/importSnapshot | 受控部件写入与改名/移除策略 |
| dt_asset_configuration | 资产，时态配置 | id、asset/component、attribute/value、source、valid_from/to、recorded/observed_at、authority、verification_status、data_nature | AssetRegistryRepository.configurations/importSnapshot；AssetTools.configuration | 审核更正、配置冲突标注、核验来源责任 |
| dt_asset_maintenance | 资产，维修记录 | id、asset/component、operation、before/after、source、valid_from/to、核验和数据性质 | AssetRegistryRepository.maintenanceHistory/importSnapshot；AssetTools.maintenance | 经授权追加工单、撤销更正、关联原件 |
| dt_asset_observation | 资产，模拟读数 | asset、observed_at、signal/value/unit、source、quality、仅 SIMULATED | ObservationTools 按时间范围读取；数据脚本导入 | 可信现场写入、新鲜度验证、传感器接入 |
| dt_skill_installation | 运营，部署授权 | owner/project + skill_id/version、enabled、allowed_tools | SkillInstallationRepository.installDefault/enabled | 目录列表、启停/升级、授权变更和审计 |
| dt_run_skill | 内部兼运营，运行选择 | run_id、conversation/scope、skill/version、工具集合、document_digest、active_reference_name | SkillInstallationRepository.select/selection/lockEnabledSelection/lastReferenceName | 按版本统计、在途版本保留和归档 |
| dt_run_skill_reference | 内部，参考快照指纹 | run_id → dt_run_skill、name、content_digest | SkillInstallationRepository.pinReference/referenceDigest | 安全查阅已读记录及历史文件保留 |

DDL 源码：[迁移目录](../../src/main/resources/db/migration)。数据访问：[持久化目录](../../src/main/java/io/devicetruth/adapter/persistence)、[知识服务](../../src/main/java/io/devicetruth/knowledge)、[资产工具与服务](../../src/main/java/io/devicetruth/application/agent)。用户视图宜操作自有档案、会话归档和文件；管理员业务页宜操作受控导入批次、资产记录及其来源、可检索文档的版本、Skill 授权和运行汇总。Run、事件、引用和检查点供诊断与审计读取。备份恢复走运维手册的 pg_dump/恢复流程；资产快照由 AssetSnapshotImporter/AssetFixtureSeeder 及数据脚本导入，现有系统没有通用业务导出或后台数据管理 API。


## 5. Agent、知识、Skill、资产与运营能力

### 5.1 Agent 调查、协作及安全

1. **开始。** GeneralAgentService.submit 校验 schemaVersion=1、消息上限、附件和 contextRefs，在事务中写消息及 QUEUED Run，再派发。Worker 领取 RUNNING，建立执行租约、作用域和本轮工具目录。GeneralReasoner.Context 带任务、同 Run 消息、近期会话、工具反馈和已登记引用。ConversationRepository.recentConversationHistory 将跨 Run 近期历史限制为 12 条、6000 字符，运行时还限制工具结果的单条及总长度。当前粒度是会话、轮次、结果和引用。
2. **自主调查。** 在线模型每步提出 CALL_TOOL、ASK_USER 或 ANSWER，可按反馈选择下一工具、参数和是否追问。TaskOrchestrator.runAgent 预留步数，执行最多一个获准工具，将真实成败、数据及引用反馈给模型，再循环。运行时固定的是工具集合、参数合同、只读执行、资产及文件范围、预算、去重、重试、安全和持久化。没有预先固定每域的完整路径，也没有后台开放式调查、任意网络搜索或用户离线时无限运行。
3. **模型。** ProductConfiguration.agentReasonerName 以显式 Spring 属性/环境优先，私有文件补缺。agentReasoners 每次执行取得独立推理器，可选 scripted、qwen、openai。AgentModelConnections 与 AgentScopeStep 承接 DashScope qwen-plus 与 OpenAI 兼容对话（可接 DeepSeek），结果为单步文本/JSON及可选 usage，非模型 token 流。连接配置读私有文件再用环境补缺。AgentScopeStep 单次超时 45 秒，StructuredAgentReasoner 对可重试异常有界重试。缺配置或协议错误可见为失败，不自动切 scripted。ScriptedAgentReasoner 由关键词、资产参数、先前动作等决定检索/配置/回答，仅适合可重复演示与回归。Legacy CLI A–D 与 HTTP 固定场景仍走 JSON 场景和旧规则。
4. **追问与继续。** ASK_USER 写公开问题和 WAITING_FOR_USER。本轮答复提交包含 runId、pendingQuestionId、questionVersion 的 replyTo，GeneralAgentService.reply 校验等待状态/归属/版本，入队原 Run。普通补充和“深入调查”走新 Run。App.investigateCurrent 由选中判断构造问题，携原 Run 资产和最多 10 个合法来源 ID。AgentRunRepository.importReference 在新 Run 重新登记，旧结果可回看。仓储按 owner/project 校验继承，未要求来源会话等于目标会话；一旦开放共享，必须补充具体读权校验。当前继承 SQL 也未复制 citation_excerpt，非知识快照摘录继承完整性需定向验证。选择判断只切本地焦点。回复和续查具备明确的两条协议，当前没有可跨轮稳定标识的判断实体、持续更新的调查图谱或事件化现场工单。
5. **落地和边界。** AnswerGuard.review 校验总引用和 claim 引用均属于本轮，SUPPORTED/CONFIRMED 需要引用，服务端判风险，拦危险动作与未核验确诊。当前产品 orchestrator 调三参数重载，现场核验固定为 false。即使挂了资产编号、已有配置或标为 AUTHORIZED 的文档，也无法据此出确诊。AgentResult 有 summary、category、claims（text/referenceIds/certainty）、suggestions、uncertainties、limitations、references、riskLevel、safetyDisposition、dataNature。类别为知识、可能原因、确诊发现和人工核验。建议当前是 Run 级。服务端规则为安全底线，需按开放域样本评估覆盖。
6. **可靠性和实时。** 运行可从 QUEUED、RUNNING 进入 WAITING_FOR_USER 或终态 COMPLETED/FAILED/CANCELLED。Run 默认预算为 12 步，重复动作上限配置为 2，总活跃时限默认 120 秒，排除用户等待，跨恢复累计。RUNNING 取消协作式收尾，晚到模型结果不能覆写终态。RunRecoveryService 回收过期租约，有界周期补派 QUEUED，版本 CAS 和进程内待派去重防重复执行。V17 恢复工具上下文、步数与相关指纹。RunEventStreamService 先历史、后订阅、再补读，按 seq 和 Last-Event-ID 续传，连接及订阅有界，终态/等待关闭流。前端回落到 GET，事件进度来自服务端白名单。
7. **优先增强。** 维持核心接口和执行框架；先修闭环以及知识引用登记的事务故障（P1-03）、末步 outcome 写入前崩溃风险（P1-05）。再按产品样本验证精简输出、危险表述及公开进展摘要。后端准备度旧报告所述 P1-04 已有补派修复和历史定向回归。

工业控制器历史配置、工单事件、模拟故障信号，和 EV 充电/供电风险/维修建议均能用现有流程。Carrier 等专项 Skill 可引导取证。任意家电与长尾设备可接受自由文本和通用风险提示，其专用诊断深度依赖经许可的型号资料、结构化数据和评测。历史真模、真实向量和本机持久记录见第 12 节，真实现场及外部实时设备未验证。普通与专业共用同一授权、模型循环、追问、风险和引用。起步差异可放在展示层，确需差异时再传服务端校验过的模式枚举来控制表达和预算。

源码：[TaskOrchestrator](../../src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java)、[GeneralAgentService](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java)、[AnswerGuard](../../src/main/java/io/devicetruth/core/decision/AnswerGuard.java)、[StructuredAgentReasoner](../../src/main/java/io/devicetruth/adapter/StructuredAgentReasoner.java)、[RunRecoveryService](../../src/main/java/io/devicetruth/application/agent/RunRecoveryService.java)。

### 5.2 知识与 RAG

- **导入与状态。** K1 以 JSON 直接送 MD/TXT 文本，由 KnowledgeImportService.importDocument 规范化切片和写库。F1 是受控原文件存储与有界异步导入，仅接受 Markdown，表记录大小、MIME、校验和、版本和解析结果。DocumentParser 严格 UTF-8 解析，KnowledgeChunker 按标题/段落与长度切分，常规切片上限默认 480 字，索引保存章节，当前页码为空。F2 可回读，F3 重试失败，只有 PARSED 文件能作为消息附件，附件给 Agent 的是授权文档元数据，正文仍按工具检索查阅。PDF、图片、视频、通用原件下载均未实现。
- **检索与权限。** OpenAiCompatibleEmbeddingClient 支持真实兼容端点（默认 DashScope text-embedding-v4，1024 维 cosine），DeterministicEmbeddingClient 用于替身。embedding profile 含供应商、模型、维度、度量和修订，防止旧向量与新模型混查，旧数据可进入 NEEDS_REBUILD。KnowledgeSearchService 在 READY、scope 及可选 domain/deviceModel/documentId 范围做 pgvector 向量、SQL ILIKE 关键词及 RRF 融合，结果写入 Run 引用。K5/K6 及 get_document_excerpt 要求该 Run 已有引用。V2 建 HNSW 索引，当前未做大库召回或执行计划测试。
- **已有版本基础。** checksum 与 doc_version 可去重和保留多个版本，V7 关联一个上传记录与文档版本。当前没有逻辑文档系列、当前发布版本、差异、回退、下架或删除 API。只读 READY 计数说明入库状态，实际召回和许可须按目标资料验证。后台首期可做带分页筛选的“文件导入记录”和“文档版本列表”，复用 F1–F3/K1–K2，新增审核/下架状态和检索过滤。下架后应保留已有历史引用的可解释性。多集合或共享资料按真实权限业务再引入。
- **应用边界。** FileController/KnowledgeController 直接通过 @Value 固定身份，与 Agent 的构造注入路径分开。K4 检索会登记引用。公开部署时需区分用户私有附件、平台发布资料、管理员测试检索，明确谁能把资料标为 AUTHORIZED，资料来源与许可应单独记录。

源码：[FileUploadService](../../src/main/java/io/devicetruth/knowledge/FileUploadService.java)、[KnowledgeImportService](../../src/main/java/io/devicetruth/knowledge/KnowledgeImportService.java)、[KnowledgeSearchService](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java)、[KnowledgeTools](../../src/main/java/io/devicetruth/knowledge/KnowledgeTools.java)。

### 5.3 Skill

- 组织遵循 SKILL.md + frontmatter + 正文 + references 的渐进读取惯例。DeviceTruth 的受控子集额外要求九个键（name、description、version、devices、tasks、tools、evidence、risk、citation），严格限制 YAML 形态。兼容的是组织方式，外部 Skill 移植需按本地合同适配，代码没有脚本执行机制。
- 业务开发者在 skills 根目录增加一层包目录，按 [领域 Skill](../skills/domain-skills.md) 与 [规范化自检](../skills/skill-normalization.md) 编写，提供版本、适用设备、检修任务、工具范围、证据风险与引用指引，经本地校验后由部署指定根路径、允许工具及默认安装（当前配置 8 套）。SkillRegistry.load 扫描目录，仅发现元数据。SkillContextLoader 在激活或请求单篇资料时读取正文/参考；限制路径、UTF-8、大小和数量，默认 8 KiB manifest、128 KiB 正文、256 KiB 单参考、32 篇参考。已安装许可不会被启动默认覆盖。
- SkillRuntime.open 提供 activate_skill/read_skill_reference，向推理器暴露可用描述。模型提议激活，服务端根据安装启用状态、版本和工具白名单执行，允许范围受产品工具和安装许可共同约束。运行时可用工具共 9 种：资产/配置/维修 3 种、知识检索/扩读 2 种、模拟观测、报告汇总、Skill 激活/参考读取各 1 种。V14/V17 持久化选择、文档及参考摘要、当前参考名和版本，恢复需校验授权与内容。Run 查询 R7 和事件能展示本轮 Skill，前端仅有运行标签/时间线；无 Skill 目录页、启停、API 上传、新建、升级、卸载或沙箱试跑。
- 首期管理后台能复用加载器、解析器、安装/运行表、工具校验、R7 和时间线。建议先提供预部署包目录/版本与诊断状态、按 scope 启停、允许工具收窄、变更审计。为在途 Run 保留可读旧版本或定义可解释的终止策略；热装、市场和通用脚本执行可延期。开发自检和回归沿用 SkillRegistryTest、SkillRuntimeIntegrationTest。
  
源码：[Skill 包](../../src/main/java/io/devicetruth/skill)、[SkillInstallationRepository](../../src/main/java/io/devicetruth/adapter/persistence/SkillInstallationRepository.java)。

### 5.4 资产和现场数据

AssetFactService.composite 聚合资产身份、组件、指定时点配置及维修事实。AssetRegistryRepository.importSnapshot 提供事务导入，配置/历史保留来源类型、记录/有效/观测时间、权威级别和核验状态。AssetTools 能将本 Run 读取的资产事实登记为证据。消息上的 selectedAssetId 作为调查上下文，资源读取仍经 scope 校验；它不等同于具强外键的独立工单关联。当前资产 ID 是全局 PK，直接把外部序列号作为多用户主键会遇到碰撞。建议引入服务端生成的档案 ID 与可选用户标识，逐步补名称、类别、型号和归档。

新能源汽车以车辆和充电/供电部件、充电现象、检修风险为主。工业侧更依赖控制器/逻辑节点、时点配置、替换历史及告警信号。两类可共享资产和组件模型，领域字段按受控属性扩展。数据应区分公开登记/工单文档快照（资料有记载）、项目自制演示手册和 fixtures（示例）、dt_asset_observation 的 SIMULATED 读数（模拟输入）、用户自述（待核实）。现有代码未接实时车辆/工业网关，未建立设备遥测授权、可信现场核验、一般文档与资产的显式关联。普通用户需要私有档案的受控增编归档；专业用户可直接读取当前配置与维修记录，正式现场记录应记录人员、时间、来源和核实状态。复用同一资产服务；模板、导入预校验与新的写入端点由业务服务执行。

### 5.5 调用记录、计量和监控

| 能力 | 现有来源 | 可直接查询或聚合 | 商用缺口 |
|---|---|---|---|
| 会话和 Run | dt_conversation、dt_message、dt_agent_run | R2/R3/R6，按创建时间/状态/scope 聚合，Run 的 errorCode、完成时间、活跃执行时长 | 跨会话分页、角色鉴权、历史留存与脱敏 |
| 模型调用 | dt_run_event 的 RUN_MODEL、内部尝试记录；AgentRunRepository.modelEvents | R10，模型名、尝试、结果、耗时、可重试和可选 usage；按模型/时间汇总 | 供应商/配置 ID 未独立可靠持久化，缺统一配置映射和尝试级采集完整性评估 |
| Token/成本 | 事件 safe_payload 与 Run budget.used/usageStatus | 按时间汇总已知 input/output token 与供应商可选 cost；用已知/未知样本分母 | 缓存/计价维度、币种、计价规则版本、明细对账和配额预留；null 不能折算为 0 |
| 工具/Skill | RUN_TOOL、dt_run_skill、dt_run_skill_reference | R7/R9/R11/R12，工具成功率、耗时、错误、引用计数以及 Skill 版本热度 | 安全业务摘要未实施；无敏感参数或 prompt 公共回读 |
| 日志/运行状态 | Logback、/api/health、Actuator 健康、部署日志 | 静态业务探针、只读健康、单 Run 事件排查 | 专用结构化日志检索、相关 ID、告警、DB/模型依赖探针、指标授权与可用性验证 |
| 营运统计与配额 | 已有 scope、预算及并发上限 | 可按日聚合运行量、完成率、失败码和已知用量覆盖率；建议定义完成率分母包含哪些终态 | 没有套餐、账户额度账本、并发预留、支付复核；既有预算是单 Run 保护 |

现有 R9/R10 人工定位价值高。首期管理 API 可增加受授权的分页 Run 列表（时间、状态、用户/项目、模型）、模型与用量汇总、失败状态/错误分布，单轮明细复用现有 DTO 和事件映射。工具原始参数、文件原始路径、模型输入输出及日志中的隐私字段应限制暴露。Token 在兼容响应有提供字段时才有效，重试与崩溃恢复产生的供应商费用需要对账。暂用每日请求和并发硬上限做可控试点，费用估算独立于正式计费。


## 6. 普通用户端功能适配矩阵

分级 **A** 表示可复用且有基本验证，**B** 表示有基础需增量，**C** 表示缺关键业务模块，**D** 表示首期可延后。优先级 **P0** 为共享试点上线门槛，**P1** 为首期产品功能，**P2** 为后续。工作量为单功能工程人日，含实现及定向测试，不含资料许可、人审与部署等待；共享依赖有交叠，勿直接相加。API 使用第 3 节的编号对应具体 HTTP 方法和路径，“—”表示无对应端点。表名省略统一前缀 dt_。源码路径可由第 2–5 节索引定位。

| ID / 功能 | 级 / 优先 / 人日 | 当前实现与代码 | API / 表 / 可复用前端 | 缺口、最小增量、依赖与验证 |
|---|---|---|---|---|
| U01 自然语言咨询 | A / P1 / 1–2 | GeneralAgentService.submit、TaskOrchestrator.runAgent 接受自由目标 | R1/R3/R5/R6，conversation/message/agent_run，App 输入与跟踪 | 直接接简化对话页；依赖平台身份、资料。历验、回读支持通用链路，任意设备回答质量 UNVERIFIED |
| U02 描述问题与选择设备 | A / P1 / 1–2 | AgentTaskInput、AssetFactService.assets 支持自由文本和可选资产 | R5/A1，message/asset，输入与下拉 | 只需简化字段，资产档案自助另见 U08。历验、回读 |
| U03 引导追问和补答 | B / P0 / 1–3 | GeneralAgentService.reply、App.answerQuestion | R5/R6/R11/R12，agent_run/message，补答表单 | 专业页问题正文可被隐藏，抽共用表单并固定显示正文与所需字段；依赖同 Run 三元组。历验，最新组件交互 UNVERIFIED |
| U04 风险提醒及安全检查建议 | B / P0 / 3–6 | AnswerGuard.review、ViewModel safetyNoticeOf | R6，agent_run/run_event，风险提示 | 保留风险和人工升级，补车型/家电高风险样本及规范措辞，依赖目标域资料；本测 Guard，真实开放域覆盖 UNVERIFIED |
| U05 简明诊断结果 | B / P1 / 2–4 | AgentResult、buildDiagnosisViewModel 已投影摘要/类别/风险 | R6/R8，agent_run/evidence_reference，报告组件和纯映射 | 普通页默认短摘要、处理建议和人工复核，细节渐进展开。依赖 Guard 与语义标签；本测 ViewModel，面向普通用户可读性 UNVERIFIED |
| U06 简要证据与来源 | A / P1 / 1–2 | AgentRunRepository.references、toReferenceView | R8，evidence_reference/knowledge_chunk，EvidenceDrawer 数据映射 | 可直接展示来源、章节和摘录；依赖真实引用，扩读需 K5/K6；API 回读、本测映射 |
| U07 个人会话与诊断历史 | B / P1 / 2–4 | ConversationRepository.list、App.openConversation | R2/R3/R6，conversation/message/agent_run，历史抽屉 | 隔离后接个人页，补分页、可选名称或归档。当前 100 条上限。依赖 M02/M03；回读当前固定域，跨账户 UNVERIFIED |
| U08 个人设备档案 | B / P1 / 4–8 | AssetRegistryRepository.asset/importSnapshot，仅后台写基础 | A1/A2，asset/asset_component，资产下拉/对比 | 增名称、车型类/型号、创建编辑归档，服务端生成 ID；依赖身份隔离与稳定资产引用。历史资产测试，个人写入 UNVERIFIED |
| U09 Markdown 文件 | A / P1 / 1–2 | FileUploadService.upload/require/readyAttachments | F1/F2（F3 可接），uploaded_file/knowledge_document，上传+轮询 | 可直接复用保留解析失败提示和来源标记，依赖权限与配额。前端合同本测、PG 历验 |
| U10 图片、视频与一般 PDF | D / P2 / 8–20 | FileUploadService/DocumentParser 当前 Markdown 入口，无识图/视频/页解析 | —，uploaded_file 可作元数据参考，上传 UI 壳 | 后续按真实需求分别评估 PDF 解析和多模态；依赖授权存储、模型和安全评测。源码；目标能力 UNVERIFIED |
| U11 日常设备领域知识 | B / P1 / 5–12 | KnowledgeSearchService.search、现有 EV 等 Skill | K1/K4/K5、R6，knowledge_document/knowledge_chunk/skill_installation，来源展示 | 按首发设备域补许可资料、领域过滤与可复核样本。历验支持演示域，真实厂商手册与家电广度 UNVERIFIED |

## 7. 专业用户端功能适配矩阵

| ID / 功能 | 级 / 优先 / 人日 | 当前实现与代码 | API / 表 / 可复用前端 | 缺口、最小增量、依赖与验证 |
|---|---|---|---|---|
| P01 Agent 自主调查 | A / P1 / 1–3 | StructuredAgentReasoner.propose、TaskOrchestrator.runAgent、Gateway 可依据反馈自主提议 | R5/R6/R9/R12，agent_run/run_event，专业页/时间线 | 先保留；依赖配置、数据与 Skill。历验双域真模，广域泛化 UNVERIFIED |
| P02 当前调查焦点 | B / P1 / 2–4 | claimSelection 局部聚焦；SafeAgentEventViews 有工具状态 | R6/R11/R12，run_event，ClaimList/ClaimDetail/RunTimeline | 若需运行时调查方向，用服务端受控步骤标签及脱敏参数范围；依赖白名单与安全样本。选择本测，实时焦点语义 UNVERIFIED |
| P03 候选判断与证据 | A / P1 / 1–2 | AgentResult.claims、ViewModel 按 category/certainty 投影并绑定引用 | R6/R8，agent_run/evidence_reference，ClaimList/ClaimDetail | 保留基本成功结果投影。失败轮证据可达性另见 P04。映射本测，历史真模候选输出存在变化 |
| P04 证据查阅与资料冲突 | B / P0 / 3–6 | EvidenceComparison 按属性展示快照差异；EvidenceDrawer 展来源 | R8/A2/K5，evidence_reference/asset_configuration，两个组件 | 修复失败/取消和小屏证据详情，标“资料不一致待核实”，需要机器可用冲突时增结构字段。依赖来源/时间，当前仅源码和历史证据；最新组件 UNVERIFIED |
| P05 深入调查 | B / P0 / 2–4 | App.investigateCurrent、GeneralAgentService.submit、AgentRunRepository.importReference | R5/R6/R8，message/agent_run/evidence_reference，专业动作栏 | 异常重试沿用逻辑请求键，并针对 409/丢响应回查；依赖后端授权与既有幂等约束。历史成功往返，最新组件丢响应路径 UNVERIFIED |
| P06 用户现场补充 | A / P1 / 1–2 | App.focusFieldNote/submit 可将用户文字送新 Run | R5/R6，message/agent_run，输入栏 | 用户自述保持待核实。可直接复用，正式字段见 P10。历史真模往返、本测前端合同 |
| P07 ASK_USER 与多轮恢复 | B / P0 / 2–4 | reply/queueReply/recoverQueued、timeline.ts | R3/R5/R6/R11/R12，agent_run/message/run_event，App/Timeline | 修问题可见与终态回读，补刷新/断线回归。依赖 U03 共享表单与 R5/R12。历史恢复、前端合同本测，最新布局 UNVERIFIED |
| P08 资产时点配置及维修查询 | A / P1 / 1–2 | AssetFactService.composite、AssetTools.configuration/maintenance | A1–A4，asset/component/configuration/maintenance，EvidenceComparison | 可复用为业务只读资产卡，依赖真实资料和隔离。历史真实 PG、部署资产列表回读 |
| P09 结构化专业报告与归档 | B / P1 / 3–6 | AgentReportTools 运行内只读汇总；AgentResult 已持久化；DiagnosisReport.vue 可参考 | R3/R6/R8/R9，agent_run/run_event/evidence_reference，专业页/报告组件 | 先用现有 DTO 做可打印报告和历史回看，再按需求加编号、模板、人工签署；依赖权限及引用。历验工具，正式导出 UNVERIFIED |
| P10 正式现场记录与维修履历录入 | B / P1 / 5–10 | AssetRegistryRepository.importSnapshot 与观察/维护表提供基础 | A2/A4 只读；asset_observation/asset_maintenance，EvidenceComparison | 新增授权写入、来源/人员/时间及核实审核。SIMULATED 列约束需要专门设计可信录入。依赖账号和资产，真机/现场 UNVERIFIED |
| P11 持续调查图谱 | D / P2 / 10–25 | 仅有近期历史、Run 和 contextRefs，无跨轮判断实体/合并 | R3/R5/R8 基础，message/agent_run/evidence_reference，ClaimList | 试点用逐轮记录，有跨轮可追责需求再设计实体与冲突流。源码，目标 UNVERIFIED |
| P12 小型维修团队协作 | D / P2 / 10–20 | owner/project 范围有字段，无成员、共享、指派及权限表 | —，conversation/asset 的 scope 基础，可复用历史页 | 单人试点后增加 Workspace/member、共享范围、指派和审计。依赖账号模型与更广查询改造，UNVERIFIED |
| P13 真实设备实时接入 | D / P2 / 15–30+ | ObservationTools 读模拟 PG，未接车端/工控网关 | —（内部 get_observations），asset_observation，设备事实 UI | 需求明确后单域只读数据适配、设备授权/质量/时延，不改变默认无确诊。真实接口/现场 UNVERIFIED |

## 8. 平台管理端功能适配矩阵

| ID / 功能 | 级 / 优先 / 人日 | 当前实现与代码 | API / 表 / 可复用前端 | 缺口、最小增量、依赖与验证 |
|---|---|---|---|---|
| M01 运营总览 | B / P1 / 3–5 | AgentRunRepository 和事件/预算可聚合 | R2/R3/R6/R9/R10 明细，conversation/agent_run/run_event，状态样式/Timeline | 加按时间窗口、状态及维度聚合，标未知 usage；依赖 M02/M03/M08。库验、回读基础，聚合 UNVERIFIED |
| M02 登录注册与用户管理 | C / P0 / 6–12 | ProductConfiguration.productServerIdentity 固定 Bean；无用户表 | —，无账号表，复用表单样式 | 加账户/状态、登录退出/当前账号、管理员邀请和停用。无自助注册接口，首期可用邀请。依赖部署安全、迁移及审计，UNVERIFIED |
| M03 角色与资源隔离 | B / P0 / 8–15 | ServerIdentity、Repository scope、ToolContext 基础存在 | R/K/F/A 现有作用域保护，业务表 owner/project；前端仅权限显隐 | 请求 principal、USER/ADMIN 与 scope 授权，改 controller/worker/recovery/SSE/Skill 和资料读写，双身份端到端测试。历验服务层 scope，真正账号隔离 UNVERIFIED |
| M04 知识导入与失败处理 | B / P1 / 3–6 | FileUploadService 与 KnowledgeImportService 具导入/状态/重试 | F1–F3/K1/K2，uploaded_file/knowledge_document/..._file，上传控件/轮询 | 加权限、列表筛选和来源审核，复用状态；依赖 M03，避免用户上传直接发布到平台。历验功能、库验 READY；管理员路径 UNVERIFIED |
| M05 知识库目录、版本与下架 | B / P1 / 5–10 | KnowledgeImportService 文档版本/去重，KnowledgeSearchService READY 过滤 | K2/K4–K6，knowledge_document/chunk/..._file/evidence_reference，证据预览 | 加列表、版本系列、发布/下架及历史引用规则，实际需要多集合再加集合。依赖 M03/M04。源码及库验，版本治理 UNVERIFIED |
| M06 Skill 管理 | B / P1 / 4–8 | Registry.load、InstallationRepository.installDefault/enabled、Runtime.selection | R7，skill_installation/run_skill/run_skill_reference，Skill 标签/Timeline | 预部署目录、分页查看、scope 启停/许可收窄，复用验证器。依赖 M03/M13，版本热更新 UNVERIFIED |
| M07 设备和业务数据管理 | B / P1 / 5–10 | AssetRegistryRepository.importSnapshot 与 AssetFactService | A1–A4，5 张资产表，EvidenceComparison | 增分页、导入预检、追加和归档、来源审计，复用 U08/P10 的写服务。依赖 M03/M13，通用后台写 UNVERIFIED |
| M08 Agent 调用记录与明细 | B / P1 / 3–6 | AgentRunRepository.list/modelEvents/toolEvents、RunEventStreamService | R3/R6–R12，agent_run/run_event/run_skill，RunTimeline | 新增运营范围跨会话分页列表、可见字段和搜索；详情复用 DTO。依赖 M03/M13，明细回读，跨用户 UNVERIFIED |
| M09 Token 与模型成本统计 | B / P1 / 3–6 | AgentScopeStep.UsageTransport、AgentRunRepository.updateModelUsage | R6/R10，agent_run.budget/run_event，Timeline 可参考 | 补使用量覆盖率、模型渠道 ID、币种和版本价目，展示估算与实报。依赖 M08；部分 usage 历验，计价/对账 UNVERIFIED |
| M10 模型与供应商配置 | B / P1 / 2–5 | ProductConfiguration.agentReasoners、AgentModelConnections、ServerConfiguration embedding | —（R10 仅事实记录），无配置表，复用状态组件 | 首期只读脱敏配置与可用性，由运维变更；需要在线切换再加审计和版本，并用部署侧密钥引用。历验在线适配，运行时配置平台 UNVERIFIED |
| M11 系统状态及日志 | B / P0 / 2–5 | HealthController、Actuator、Logback/容器、事件流 | H1、框架 /actuator/health、R11，run_event，Timeline | 先补依赖探针、错误统计、安全日志访问及告警。回读健康 200/metrics 500，指标能力 UNVERIFIED |
| M12 基础配额 | C / P0 / 4–8 | AgentRunRepository budget 和有界 worker 可复用，缺用户配额 | R5/R6 无配额合同，agent_run/run_event，失败提示 | 按账户/日限额+并发拒绝，提交时原子占用、终态结算、重试幂等。依赖 M02/M03/M08，跨并发 UNVERIFIED |
| M13 管理操作审计 | C / P0 / 3–6 | SafeAgentEventViews 记录 Run，无管理审计表 | —，run_event 仅诊断，Timeline 可借展示结构 | 新增追加式记录操作者、对象、动作、结果、时间、请求 ID，脱敏和独立权限。依赖 M02/M03，UNVERIFIED |
| M14 套餐价格、账单和支付 | D / P2 / 12–25+ | 仅有可选 usage/cost 和单 Run 预算 | —，agent_run/run_event 计量来源，状态组件可参考 | 计量覆盖率足够再做套餐、价格版本、支付和对账。依赖 M09/M12，UNVERIFIED |
| M15 高级数据运维 | D / P2 / 8–15+ | PG/Flyway、运维备份脚本、资产导入脚本 | —，全库备份与业务表；无后台组件 | 首期保留运维备份恢复与业务归档，后续再加按角色和数据保留的批量导出、删除审核。依赖 M13；历史运维演练，新场景 UNVERIFIED |


## 9. 前端已实现资源、回归与复用

main.ts 仅挂载 App.vue，Vue 3 + TypeScript，无 Vue Router、全局 store 或账号模式。Vite 开发代理和 Nginx 部署代理连接真实 /api。App 本地响应式状态管理会话列表、所选轮次、最新活动轮、附件、问题、引用及 Skill；localStorage 存会话定位，刷新后从 R3 恢复。timeline.ts 负责跟踪目标隔离、历史和 SSE，api.ts 负责 UUID 幂等键、错误兼容、SSE 重连/轮询、上传、补答和 contextRefs。UI 样式可沿用 design.css、workbench.css、pro.css 的变量和控件；不是独立设计系统组件库。

| 资源 | 当前用途与真实数据 | 三端复用 |
|---|---|---|
| App.vue 输入、活动状态、历史抽屉 | R1/R2/R3/R5/R6/R13，选择旧轮与最新活动轮分开；附件 F1/F2，A1 选资产；补答与新 Run 分开 | 专业保留；普通复用发送/状态/补答与历史；管理员单独分页入口 |
| ProfessionalDiagnosisPage、ClaimList、ClaimDetail | 三栏、中间结果、底部动作（App）及历史；纯映射、局部选中，只有提交深入调查才发 R5 | 保留专业；普通用同一映射的摘要和风险字段 |
| EvidenceDrawer、EvidenceComparison | R8 回读片段；后者独立 A2 查询资产配置/维修对比 | 普通仅来源卡；专业保留证据及配置对比；管理员可用同一资产只读卡与资料预览 |
| RunTimeline、timeline.ts | R11/R12，事件映射、seq 去重、断线重连/轮询，显示状态/工具/模型/Skill 的安全摘要 | 专业继续；普通可显示简进度；管理 Run 详情可用时间线 |
| DiagnosisReport.vue、diagnosisViewModel.ts、UiIcon.vue | 前者仍在仓库但 App 当前直接挂专业页；后两者提供投影和图标，避免重新解释风险、引用与数据性质 | 普通与专业共用纯映射，管理优先复用状态/图标；不把报告组件视作当前独立页 |
| 待新增入口 | 项目目前只有专业单页和 legacy 静态演示 | 普通对话/设备档案、登录及账号、管理端导航和分页表单；专业新增正式现场记录及可打印视图按需 |

### 最新源码仍可见的问题

| 问题 | 复核依据 | 最小收敛 |
|---|---|---|
| F01 追问正文在常见情况下被隐藏 | App.vue:545 比较最后一条消息内容，等于问题时隐藏正文；聊天内容又在记录抽屉 | 补答表单始终展示问题正文，回归 WAITING、刷新和过期问题 |
| F02 失败/取消轮和窄屏中的证据可达性 | ProfessionalDiagnosisPage.vue 的 FAILED/CANCELLED 分支可 toggle，EvidenceDrawer 仅在结果三栏；窄屏 CSS 收起证据列。记录抽屉有独立来源摘要 | 让失败、取消和窄屏共享可达详情并验证焦点 |
| F03 丢响应后可能重复创建 Run | App.vue 的业务动作每次生成新键；api.ts 支持传键，但失败交互尚未保留逻辑操作键或回查 | 保留 pending 请求键和输入，丢响应后回读已有 Run，再允许重发；测双击和断开 |
| F04 SSE 收尾监听未绑定 Vue 响应式值 | App.vue:165 watch 普通 timeline.snapshot；syncTimelineView 只同步事件和 live，提交轮询仍可回读 | 从 onChange 写入响应式 terminal 或直接触发 Run 回读，保留 generation 保护 |
| F07/F08 冻结记录与现页版本、历史上下文 | 冻结后布局改版；历史 Run 与最新活动轮是不同变量，抽屉资产 ID/时点可能来自不同上下文 | 对当前所查 Run 固定资产、时点和引用，最新轮单独展示操作约束，核对最新版浏览器 |
| 信息密度与语义 | 本轮建议、限制、不确定性等直接投影，模型明细可能长且重复；成功分支保留多块文本 | 改默认展开项与字段短句约束，保留风险/来源语义，以上游真实输出做快照评估 |

F01–F04 与 F08 依据静态核验，前端单元测试未覆盖最新模板交互，浏览器状态标为 **UNVERIFIED**。具体源码 [App.vue](../../frontend/src/App.vue)、[专业页](../../frontend/src/components/ProfessionalDiagnosisPage.vue)、[样式](../../frontend/src/pro.css)、[呈现审查](DEVICETRUTH_DIAGNOSIS_PRESENTATION_AUDIT.md)。

## 10. 商业化基础缺口与架构判断

### 10.1 从一套固定身份进到真实账号

现状中 ProductConfiguration.productServerIdentity 将配置映射为单例 ServerIdentity。GeneralAgentService、RunEventStreamService、SkillRuntime、AssetController 使用该 Bean 或从固定域构造 ToolContext。知识和文件 Controller 另从 @Value 读取同一配置。客户端 JSON 不允许自行扩大 owner/project，SQL 和部分复合外键提供资源边界。服务端目前无注册/登录/退出/角色 API，无 Spring Security 认证链、真实用户表、角色/权限分配表、Workspace 或 RLS。现有隔离测试验证了将不同 ServerIdentity 传入仓储时的授权边界，共享 HTTP 访问仍只看见一个固定域。

最小增量建议：

1. 加内部账号 ID、状态和 USER/ADMIN，首期用管理员邀请。使用 Spring Security 同源会话和现成密码编码器，登录限速，提供退出、当前用户与停用生效机制。SSE 用同源认证 cookie，明确 CSRF、会话失效后重连和权限撤销语义。若后续接身份供应商，保留稳定内部 ID 映射。
2. 首期每账号一个个人 scope，明确平台共享资料的受权读取途径。用户请求从服务器 principal 解析 scope，所有 product Controller 使用同一接口。按 owner/project 查询的既有 Repository 方法保留；补足文件、知识、资产列表、引用、事件、RunSkill 和全局资产 ID 的边界。ADMIN 平台权限由明确的管理服务校验，普通资源访问仍遵循资源范围。
3. 异步执行要从已授权提交中固化 owner/project，恢复扫描从已持久化任务取得其 scope。SSE 订阅与事件回放校验当前用户，Skill 安装许可按作用域查询。实施中需逐处消掉构造时单例身份，避免用请求线程上下文替代后台 Run 身份。已有单用户记录可保留为演示 scope，迁移到真实用户须显式映射并审计。
4. P0 验收至少覆盖双账号和管理员，逐一证明不同身份不能访问、补答、取消、续订、引用、重试、导出对方资源，不能用上传标记或 URL 改变归属。需覆盖失效登录、异步恢复、管理员越界、并发配额和同源约束。

### 10.2 复用边界、耦合与上线要求

单体适合目前三端规模，资产、知识和 Skill 仍由一套事务与 PostgreSQL 管理。用户体验差异主要发生在页面和任务表达，平台管理操作要在服务端独立授权和审计。可给管理 Controller 使用明确的管理路由，复用业务服务、查询 DTO 和映射器。增加分页列表、知识状态、Skill 启停、设备导入、统计可围绕新增 Controller/Service 实现；身份传播、平台资料共享、团队成员权限和旧数据归属会影响多个 Repository 及权限测试。

高风险耦合包括构造时固定身份、文件/知识另外注入固定属性、恢复及 SSE 的同域订阅、RAG 登记与 worker savepoint、终态持久化前的进程窗口。Run 数据与事件构成现有运行审计，不提供管理人员操作审计。前端已有轮询和 SSE 两种状态更新，需要共享同一响应式事实来源。Legacy 与 product 分工清楚，维持部署隔离即可。

试点上线门槛包括服务端登录鉴权及跨主体测试、TLS 和会话安全、上传和模型调用限速/限额、密钥及诊断日志脱敏、审计留痕、资料来源审核、按资源可撤销权限、备份恢复演练、最小依赖监控、保留与删除策略、现场高风险人工核验。运行预算已有技术保护，商业入口还需账户级并发和日限额。高风险答复、安全文本及引用真实性用代表性资料与真实设备业务样本评估。后端依赖共享进程和数据库的数据量及负载阈值目前 UNVERIFIED。

## 11. 增量顺序与依赖

~~~mermaid
flowchart LR
  I["账号与可信作用域"] --> T["专业工作台受控试点"]
  I --> M["管理端用户与 Run 查询"]
  Q["可靠性、风险和资料验证"] --> T
  M --> T
  M --> K["知识、资产与 Skill 管理"]
  I --> K
  T --> U["普通用户简化咨询"]
  K --> U
  M --> B["配额、用量覆盖率"]
  B --> U
  B --> D["后续套餐与支付"]
~~~

| 顺序 | 最值得投入的五项增量 | 现有代码为什么不足 | 首个可验收交付 |
|---|---|---|---|
| 1 | 账号、角色与请求/后台统一作用域 | 当前身份是服务器固定配置，任何访客共享同域 | 双账号和 ADMIN 经 HTTP、SSE、工具和恢复隔离 |
| 2 | 专业闭环可靠性 | 补答文案隐藏、无答轮证据不可达、重发可能重复、SSE 收尾监听失效，RAG/结尾持久化仍有故障窗口 | 最新布局通过补答/续查/失败/刷新；定位并验证 RAG 登记故障及末步恢复 |
| 3 | 管理端最小运营和配额 | 只有单 Run/会话详情、可空 usage 及 Run 级预算 | 受控分页查询、操作审计、失败分布、按日/并发限制及用量覆盖率 |
| 4 | 知识、资产与 Skill 的受控业务管理 | 已有导入/读接口和库表，缺发布/归档/启停及来源治理 | 管理知识状态与历史引用、资产增编归档、预部署 Skill 授权启停 |
| 5 | 普通用户简明咨询与首发域资料 | 自由提交与风险结果可复用，缺独立体验、家用设备资料深度和评测 | 选择首发设备域，简明视图、显著风险说明、来源、历史、个人设备和 Markdown |

三端次序以**专业端先收敛，运营管理骨架同步，普通端随后上线**为建议。试点开始前完成账号隔离、管理员可停用用户和排查 Run、配额与基本监控。专业试点用现有工作台取得真实业务反馈；管理写接口和资料治理可按实际运营迭代。普通端可与管理第二批并行开发，受控资料/配额通过后再开放。建议首期范围为单人专业与普通咨询，平台维护一组经审核的知识与预部署 Skill。持续图谱、团队、平台在线密钥编辑、通用数据控制台、多媒体、实时设备、复杂套餐和支付按矩阵延期。

## 12. 最终决策摘要与实际验证

**建议保留 Agent Core、只读工具网关、Run 和事件协议、PostgreSQL 与引用体系。** 普通端可以从现有文本咨询、附件、追问和简要来源做起；专业端优先修复补答和证据可达性、重试和收尾一致性；管理端先将既有表和明细变成有范围的用户、运行、知识、资产和 Skill 管理。账户与作用域是三端共享部署的依赖。平台管理、产品体验与资料优先级由人工评审。

| 矩阵 | A | B | C | D | 合计 |
|---|---:|---:|---:|---:|---:|
| 普通用户 | 4 | 6 | 0 | 1 | 11 |
| 专业用户 | 4 | 6 | 0 | 3 | 13 |
| 平台管理 | 0 | 10 | 3 | 2 | 15 |
| 合计 | 8 | 22 | 3 | 6 | 39 |

### 本轮执行记录

| 命令或检查 | 总数 | 通过 | 失败 | 错误 | 跳过 | 结果与限制 |
|---|---:|---:|---:|---:|---:|---|
| mvn -version / node --version / npm --version | — | — | — | — | — | Maven 3.9.9、Java 21、Node 24.14.0、npm 11.9.0 |
| mvn -q '-Dspring.flyway.enabled=false' test | 261 | 245 | 0 | 14 | 2 | Surefire 统计；14 项为测试环境初始化错误，缺 DEVICETRUTH_TEST_PG_BASE_URL。本项目手工装配 Flyway，此属性不能禁用手工迁移 Bean。报错发生于测试库创建之前 |
| mvn -q -Dtest=<排除引用测试库变量的15个类> test | 247 | 245 | 0 | 0 | 2 | 36 个类，退出 0；根据 rg -l DEVICETRUTH_TEST_PG_BASE_URL src/test -g '*.java' 排除，用 Surefire XML 汇总。1 项真模门控、1 项因本机无符号链接权限中止。与上一行存在重叠，勿相加 |
| frontend 下 npm.cmd run test -- --run | 60 | 60 | 0 | 0 | 0 | 3 个 Vitest 文件，包含 API 合同、纯映射和判断选择；组件浏览器路径未覆盖 |
| frontend 下 npm.cmd run build | — | — | — | — | — | vue-tsc 和 Vite 构建均通过。Node 进程有 TLS 校验环境提示，未做外部调用 |
| docker exec -i devicetruth-postgres-1 … psql，BEGIN READ ONLY，SELECT，ROLLBACK | — | — | — | — | — | PG 16.15/vector 0.8.7，Flyway 17 条成功，17 张业务表，20 份 READY 文档，其中索引档案前缀 deterministic 16 份、openai 4 份，数据标签 AUTHORIZED 8 份、SIMULATED 12 份。17 张业务表均未启用 RLS。只查元数据与聚合，档案前缀不能确定当前进程配置 |
| Python urllib GET 本机当前部署 | 12 | 10 | 0 | 1 | 0 | 另 1 次为 Skill 样本 404（该响应不能独立证明具体原因）；health、Actuator health、assets、conversations、会话与 Run 详情、事件/引用/模型/工具回读均 200，Actuator metrics 500。仅记录状态和 DTO 顶层类型，部署制品与当前提交的对应未验证 |

### 历史验证及未验证边界

- 历史 OpenSpec 第 7.2 批记录真实测试 PostgreSQL 下 402 项、398 通过、0 失败、0 错误、4 真模门控跳过。第 7.1 批记录隔离库双域真实 DeepSeek、真实 text-embedding-v4 和浏览器演示，项目自制手册由授权渠道入库。当前真模证据主要覆盖 OpenAI 兼容路径，Qwen 官方在线路径仍待单独复验。历史脚本另用 scripted + deterministic 覆盖恢复，协议替身与真模分开。有真模补答及同 Run 恢复记录；已知冻结后布局回归独立处理。
- **UNVERIFIED**：本轮未执行新测试库迁移与真实 PG 写入集成；现部署实际向量供应商、召回效果、模型输出及 Token/费用完整性未重跑。最新布局的浏览器交互、跨账号权限、RAG 失败登记和末步崩溃窗口、指标端点、生产容量、真实厂商许可、真实设备与现场核验均未验收。不会用历史通过填补这些缺口。
- 本次运行仅包含源码/配置/资料核验、离线测试/构建、只读 HTTP 和本机 PG 结构回读。交付限定于本报告，原有回顾与验收记录保持历史口径。

