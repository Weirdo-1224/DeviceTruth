# Design

## Context

2026-11-17 规划快照。动机见 proposal；本轮只生成规划，不执行开发或环境变更。

已完整阅读 `docs/DEVICETRUTH_PRO_MODE_MVP_DEVELOPMENT_PLAN_V0_2.md`、`docs/reviews/DEVICETRUTH_BACKEND_MVP_READINESS_REVIEW.md`、README、AGENTS、OpenSpec 配置，并查看 `docs/DEVICETRUTH_PRO_MODE_UI_PROTOTYPE.png`。资料均存在。V0.2 是范围基准，Review 是能力证据，源码决定真实接口；原型中的具体诊断、PDF/XLS 图标和关系图入口均不作为已实现能力。

当前无 OpenSpec 主规格，使用本地标准 spec-driven schema；三份新增能力规格只界定专业模式增量及对现有协议的复用约束。历史 V0.1 文档用于 legacy 背景，不续跑历史队列。

### Source evidence and discrepancies

以下均为本轮源码阅读结论，不代表重新执行了 Review 中的测试。

| 依据/位置（相对仓库根） | 当前事实及本设计处理 |
|---|---|
| `src/main/java/io/devicetruth/api/AgentConversationController.java` | 已有会话/消息、Run、引用、事件/SSE、取消和 Skill 接口；不新增接口。 |
| `frontend/src/api.ts` 的 sendMessage | Review 说前端已有对应封装，但函数目前只提交消息、附件和 selectedAssetId，缺 contextRefs；本次最小扩展。 |
| `frontend/src/components/DiagnosisReport.vue` | 当前按 certainty=POSSIBLE 列候选原因，其余列事实；与 V0.2 的 category 门槛不一致。必须在新 ViewModel 中改正，不能把 SUPPORTED 自动解释为已核验事实。 |
| `frontend/src/App.vue` 的 refresh/openConversation | 已有会话列表、本地会话 ID、刷新、轮询、补答、取消、证据与 Skill 展示；refresh 只取最后一个 Run，尚未保留全量 runs 供逐轮选择。仅补选择和状态隔离，不重新建设持久会话。 |
| `AgentResult.java` | 无候选对象 ID、概率、逐判断建议、结构化冲突或核验状态；safetyDisposition 是字符串，SIMULATED/AUTHORIZED 属于 dataNature（Review 的并列示意不够准确）。 |
| `GeneralAgentService.validatedContextRefs` / `AgentRunRepository.importReference` | 每项只能有 referenceId UUID，最多 10 条；SQL 限 owner/project，未限制源引用必须来自同一 Conversation；导入分配新引用 ID（同 chunk 去重可返回已有 ID）。UI 只取所选本会话判断的引用，但不声称服务端有同会话限制。 |
| `TaskOrchestrator.runAgent` / `ActionProposal.CallTool` | CALL_TOOL 已含必填 purpose 和 arguments，但审计未保存；RUN_TOOL 的 references 是数量而非 ID 数组，不能据此生成工具到证据的精确关系。 |
| `GeneralAgentService.persistToolStep` | 引用与检查点在工具事务内；RUN_TOOL 经 appendProgress 在提交后单独写。Review §3.1 所述“引用+检查点+RUN_TOOL 同事务”与源码不符；事件可缺失，不能当执行真相唯一来源。 |
| `GeneralAgentService.recoverQueued` / `RunRecoveryService` | 启动 reconcile 扫 QUEUED；周期仅 reconcileExpired 扫过期 RUNNING，领取失败直接 return，P1-04 仍存在。已有 queued 查询无分页，周期补派不可直接无限全扫。 |
| `KnowledgeSearchService.search` / `GeneralAgentService.persistToolStep` | 引用登记仍用 REQUIRED 事务模板，外层已有工具 savepoint；登记错误可能使外事务 rollback-only，P1-03 仍是未修复风险。 |
| `TaskOrchestrator.runAgent` / `GeneralAgentService.persist` | ASK_USER/ANSWER outcome 返回后才写最终事务，P1-05 内存结果窗口仍存在；不承诺 exactly-once 模型回答。 |
| README / Review | README 提醒本地知识库未建设、缺 embedding 密钥；Review 验证的是隔离测试数据及定向真模，不证明当前演示库已就绪。D1 核验 API 合同、运行环境与至少一份可读的真实持久 Run；真实模型、embedding 与双域导入资料并行准备，不作为前端启动门槛。 |
| README / AgentConversationController.error / frontend api.ts | README 的通用错误示例为 requestId；产品接口实际为 code/message/retryable/traceId，前端已有对应类型。专业页沿用实际产品合同，不凭文档增加 requestId 字段。 |

## Goals / Non-Goals

**Goals:** 以 Conversation 为连续调查，以 Run 为调查轮次，专业页确定性投影已存数据；依赖顺序和验收见 tasks。复用服务端授权、幂等、租约 CAS、预算和只读取证边界。

**Non-Goals:** 本版是专业交互式诊断工作台，不实现跨 Run 判断实体匹配、自动结论冲突合并或完整动态推理图谱；不把消息变成结构化现场事实；不迁移 AgentResult，不新增 CanvasComposer、Case/Canvas 表、Vue Flow、Agent 协议、状态机或运行时框架。视觉使用现有样式与图标，无像素级复刻目标。

## Decisions

### 1. One page and small components

选择专业模式单页，无个人模式切换和无效关系图按钮。`App.vue` 保留现有会话/上传入口并接入拟新增 `ProfessionalDiagnosisPage.vue`；不建立并行的第二套请求/订阅控制器。

| 页面区域 | 拟实现/复用 | 内容 |
|---|---|---|
| 顶部任务区 | 专业页内轻量 header | 当前 Run 的 goalMessageId 对应问题、selectedAssetId（无设备则显示未关联）、状态、风险、dataNature、已有 Skill 版本 |
| 左侧判断区 | 拟新增 `ClaimList.vue` | 按 category 命名、显示 certainty、引用数；点击仅切焦点 |
| 中央详情区 | 拟新增 `ClaimDetail.vue` | 判断原文、真实关联引用；本轮 summary、suggestions、uncertainties、limitations 分组显示 |
| 右侧抽屉 | 拟新增 `EvidenceDrawer.vue`，抽取已有引用展示 | 实际来源、版本、定位、摘录、数据性质，关闭回到原焦点 |
| 底部协作区 | 复用 App 提交/补答/取消逻辑 | 深入调查、普通补充草稿、ASK_USER 独立补答表单、进度与错误 |
| 历史/过程 | 复用 `RunTimeline.vue`、现有会话入口 | 追加 Run 选择；`DiagnosisReport.vue` 可复用摘要/风险显示但统一语义；`EvidenceComparison.vue` 保留资产详情用途，不充当答案级引用来源 |

不要求为每个区域新建组件；header/action bar 可内联，控制改动面。原型七组件是建议，不是现有文件清单。

### 2. AgentResult to ViewModel

拟新增 `frontend/src/diagnosisViewModel.ts` 纯函数，输入当前 Run、对应目标消息、可读引用和事件，输出界面对象；无 fetch、LLM 或数据库写入。轻量局部 ref/computed 足够，无新增全局 store 依赖。

| 源字段 | 展示映射与限制 |
|---|---|
| result.category + claims | POSSIBLE_CAUSES → 候选原因；KNOWLEDGE → 资料断言；CONFIRMED_FINDING → 服务端核验结论；HUMAN_REVIEW → 待人工核验。保留所有 claims 及原顺序，不按 certainty 错分业务类别。 |
| claim.text / certainty | 原文及“可能/资料支持/服务端已核验”标签；不生成概率、评分或诊断置信度。 |
| claim.referenceIds | 与当前 result.references 按 ID 连接；需要运行中/失败后证据时读取 /references，标为“本轮已取回资料”，不强行挂到某条判断。 |
| summary / suggestions / uncertainties / limitations | 都是本轮字段；不伪造判断级关系，不从建议推导已完成检查。 |
| riskLevel / safetyDisposition / dataNature | 保留服务端值和受控提示，逐引用性质另行展示；AUTHORIZED 不等于真实现场已核验。 |
| Reference | title/sourceType/sourceId/sourceVersion/chunkId/page/section/excerpt/dataNature 原样映射；未知类型显示安全通用标签，缺失定位不补写。无 fileId/downloadUrl，不能凭 sourceId 编造下载链接。 |
| events | 已有安全字段展示工具名、成败、耗时、引用数及模型过程；不当作完整工具结果，不推测缺失步骤或精确引用关系。 |

选中键仅用于当前快照，可为 `runId:claimIndex`；切 Run 或结果替换时重置，不持久化虚假 claimId。模型没有候选原因时按实际类别展示一般判断；没有 claims 仍展示摘要、风险与本轮建议，禁用依赖选择的深入调查按钮，但保留普通文字继续调查入口（仍遵守运行中禁提交及等待态补答规则）。不得补造候选原因来满足演示。用户现场陈述来自 MessageView.content，只显示“用户报告”，不写入引用或改变 certainty。

### 3. Existing API reuse and continuation

| 操作 | 现有接口/封装 | 本次适配 |
|---|---|---|
| 会话和首条消息 | createConversation/listConversations/readConversation/sendMessage | 现有 POST/GET `/api/conversations`，POST `/api/conversations/{id}/messages`；保留 schemaVersion=1 与 UUID Idempotency-Key |
| 当前结果和来源 | readRun/readReferences/readRunSkill | GET `/api/runs/{id}`、`/references`、`/skill`；无 Skill 的既有 404 归一 null |
| 过程 | readRunEvents/openRunEventStream/waitForRun | GET `/events?after=N`、`/stream` 与安全轮询；无需新详情 API |
| 补答/取消 | replyToQuestion/cancelRun | 同消息端点带 replyTo；POST `/api/runs/{id}/cancel` |
| 演示资料 | 既有文件/知识导入及资产查询 | 提前导入已支持的 Markdown 等有效资料；不新增 PDF/XLS 导入 |

扩展 sendMessage 的兼容可选参数支持 `{referenceId}[]`，原有调用默认空列表。深入调查请求正文示例：“请深入调查以下候选判断（尚未确诊）：<claim.text>。请结合继承资料重新取证，说明依据、不确定点和建议。”类别不是 POSSIBLE_CAUSES 时称“判断”。正文对用户可见，不添加模型生成的目的或隐藏指令。

contextRefs 从所选快照的 claim.referenceIds 与已取得引用交集去重，最多前 10 条，按钮旁显示继承清单及省略数量；无引用也可调查。保留当前目标消息的 selectedAssetId，而不是未提交的设备下拉草稿。不传身份、权限或自建 parentRunId。新 Run 的引用从新接口响应重读，绝不复制旧 ID 到新答案；会话历史已有最近 12 条/6000 码点上限，选中判断原文显式带入避免仅依赖历史记忆。

幂等键每次明确的新操作生成一次，同一次网络重试保持同键同载荷。提交中禁用重复点击；响应丢失时先刷新会话核对，不能自动以新键重发。新 Run 完成切换到新结果，保留旧 Run 入口，不做判断的自动合并和更新覆盖。

### 4. ASK_USER and ordinary input

QUEUED/RUNNING 禁用普通提交和深入调查，保留草稿，可取消。WAITING_FOR_USER 从 RunView.pendingQuestion 渲染原问题和 requestedFields，使用 replyToQuestion 携带当前三元组，服务器原有 CAS/消费逻辑恢复同一 Run。追问可能再次产生新版本，不能缓存旧三元组。版本冲突保留草稿并回读，不转成普通消息。

COMPLETED/FAILED/CANCELLED 的普通补充无 replyTo，开新 Run；将现场观察作为用户报告保留。再次调查均是显式操作，不自动重跑失败任务。

### 5. SSE and page synchronization

复用 timeline.ts 的历史先读、seq 去重和 generation 防旧事件；复用 api.ts 的 EventSource 与 waitForRun。当前 lastSeq 参数没有拼到 SSE URL，原生同一连接重连由浏览器带 Last-Event-ID；刷新后新连接允许重放历史再按 seq 去重，不声称本地游标已实现跨页面续传。

SSE 是通知，RunView/Conversation 是权威。RUN_WAITING/RUN_ANSWER/RUN_FAILED/取消及 RUN_TERMINAL 后回读 Run/消息/引用，WAITING 的流收尾不等于业务终态；补答恢复后重新 follow 同 Run。连接异常回到现有有界轮询，超时显示可重新同步，不自动新建任务。现有轮询已支持 CANCELLED，不重复建设。

页面保留 `runs[]`，默认最新 Run，用户主动查看历史时自动刷新不得强制跳回最新；新提交成功才切新 Run。readRun、引用、Skill、消息请求均用会话+Run/generation 检查迟到结果。localStorage 仅记会话定位，失效或不可用时通过 listConversations 选择；服务器返回消息和全部 Run，不在本地保存权威结果。

查看的历史 Run 与会话中最新活动 Run 分开保存：切到旧结果不解除 QUEUED/RUNNING 的提交禁用；仍有 WAITING_FOR_USER 时指向对应问题补答。取消操作明确指向活动 Run，历史终态只读，避免误取消或误将旧轮视为仍在运行。

### 6. Minimum backend work

**P0 / Review P1-04：** D2–D3 与前端并行实施，不等待双域资料就绪。GeneralAgentService.recoverQueued 周期任务加入有界 QUEUED 补派，通过 RunRecoveryService 复用现有超时与领取规则。AgentRunRepository 增加受身份约束、按 created_at/id 排序的有限候选查询（保留原启动扫描语义）。避免无限重复向 executor 堆同一 run，可在现有调度入口维护进程内待执行集合并在领取失败/结束/拒绝时释放；执行资格最终仍由 markRunning 的 version CAS+租约保证。周期异常必须捕获，防止一次异常永久取消后续调度；不重派 WAITING/终态，不重置预算，不引入消息队列或新状态。拒绝仍沿用 QUEUE_REJECTED 明确失败语义。有界扫描、抢占/租约和临时领取故障恢复均保留故障注入回归，不新增运行时框架。

**P1 / RUN_TOOL 摘要：** 不作为卡片页前置。只在 TaskOrchestrator 的 audit 构建与 SafeAgentEventViews 映射扩展安全字段，必要时 RunTimeline 显示。按工具名限定 1–2 个明确键（如已注册合同的 query/assetId/componentId），单值最多 120 码点、合计最多 240；未知工具/复杂值省略。query 本身是用户文本，白名单键不等于无敏感值，必须过滤凭据、Bearer、连接串和路径等敏感模式，不安全则省略；原始参数不落审计。可选 purpose 仅来自 CallTool.purpose，经过同样限长过滤，不安全省略，不额外调模型生成。公开映射须再次过滤嵌套字段，不能只增加顶层 arguments 白名单后透传任意 Map。旧事件缺字段显示“未记录”，不回填。RUN_TOOL.references 继续为计数。

**P1 / Review P1-03：** 优先用 KnowledgeSearchTest/RunRecoveryTest 对真实 PG 登记异常做定向回归。若实施，登记在已有外层工具事务中直接参与，避免内层 REQUIRED 模板捕获异常后把外层标为 rollback-only；无外层调用仍有本地事务。保留 savepoint 对失败引用的回滚和失败工具检查点提交，不用 REQUIRES_NEW 制造孤立已提交引用。上游检索失败不能假装检索成功；连接整体断开等不可提交故障允许明确技术失败，不要求数据库不可用时仍能保存检查点。

**Review P1-05：** 首周只做正常完成和既有崩溃恢复路径冒烟、记录未提交末步答案/问题可能丢失甚至预算耗尽失败的限制。前端仅承认已持久化结果；不新增 WAL、待提交结果表或大规模事件存储改造。

## Risks / Trade-offs

- [模型不保证输出候选类别或 ASK_USER] → 按实际类别展示已有判断，无 claims 时保留普通文字继续调查。D4 对选择/引用继承分支使用已有合法持久结果实测；ASK_USER 若真模未触发，则由既有协议替身实际触发并验证同 Run 恢复，单列协议证据，不替代真模+真实资料验收。
- [演示知识库和 embedding 当前状态未知] → D1 只确认启动合同、环境和可读 Run，前端立即启动；双域资料及供应商核验并行，D4 前完成至少一条真模+真实授权资料端到端调查，D6 完成双域验收。缺环境如实记阻塞；确定性向量/模拟资料不冒充真实 embedding 或真实资料验收，健康探针不证明模型/PG 正常。
- [RUN_TOOL 审计可能缺失] → 以已持久引用/检查点/结果为准，页面不还原不存在的精确调用关系。
- [P1-03 未修、P1-05 窗口仍在] → 记录触发条件及失败码，不把恢复历史承诺成内存结果必达；P1-03 若实际阻断主演示则先最小修复再冻结，不扩架构。
- [一周工期] → P0 约 36 小时，留约 6 小时风险余量；P1 合计最多再投入 6 小时，仅核心闭环通过后选择。D4 核心往返未通即停止视觉细节；D7 冻结。

## Migration Plan

无 schema/API 迁移。实施时 D1 确认 API 合同、product 运行环境及至少一份系统真实持久 Run 后立即推进专业页与 ViewModel；真实模型和工业/汽车资料准备并行，不能作为前端强制前置。D2–D3 并行完成 QUEUED 修复及故障注入回归；D4 前完成一条真模结合真实授权资料的端到端调查。D4 实际验证六项核心行为：选择已有判断、深入调查新建 Run、正确继承授权引用、新结果展示与旧结果回看、用户补充信息开启后续 Run、ASK_USER 补答恢复同一 Run；记录请求/响应的 Run ID、引用及界面证据，模拟/协议替身路径与真模路径分开。没有候选原因时保留安全结果展示和普通文字继续调查，不能以伪造候选代替验收。D6 完成双域验收，D7 功能冻结。同一版本的静态前端/后端配套发布，复用现有运维流程，不在本轮部署。回退可恢复前端与服务版本，保留所有 Conversation/Run/引用数据；不得删除调查历史或复制秘密到验收文档。
