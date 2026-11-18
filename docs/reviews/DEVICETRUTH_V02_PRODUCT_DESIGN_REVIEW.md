# DeviceTruth 商业化产品设计方案 V0.2 专项审核

2026-11-18（北京时间） · 源码基线 **codex/frontend-workbench-v2 @ 6d23cd2e296ba72ebb138d00875665e601c7988b** · 静态审核

## A. 总体评估

**V0.2 可以作为最终产品设计的基础。保留一个用户端、普通与专业两种交互模式、一个独立授权的管理端，以及现有 Java/Spring Boot 单体。** R0 先恢复现有诊断闭环。R1 的上线门槛是请求到后台执行的身份传播、资源隔离、安全和额度闭环。R2 聚焦一个资料与安全样本可验证的普通用户场景。管理端首版宜收敛为账户、脱敏运行记录和健康/额度/审计，知识、Skill 与设备的日常维护先使用受控部署流程。

| 评估维度 | 分数（1–5，5 为最合理） | 评分依据 |
| --- | --- | --- |
| 产品定位合理性 | 5 | 普通用户关心风险和下一步，专业人员需要核查来源和继续调查。两者面向同一个任务与证据模型，平台管理具备独立授权需求。 |
| 三端功能完整性 | 3 | 主路径齐全。普通端追问与建议并显缺少明确的数据来源，专业历史时点和操作对象易混，管理端展示多于可执行操作。 |
| 与现有后端的匹配程度 | 4 | 会话、消息补答、Run、引用、SSE、资产只读、RAG 和 Skill 均有可复用入口或服务。幂等回执、平台知识共享、Skill 禁用语义、PDF 登记与分析需要重写口径。 |
| 前端交互合理性 | 3 | 原型有清晰的任务焦点和共同视觉语言。普通端竞抢焦点，专业端仍接近并排报告，窄屏及非成功轮的证据操作存在缺口，管理端导航与内容有重复。 |
| 商业化基础完整性 | 2 | 识别了登录、隔离、额度和审计四项门槛。仍需明确异步身份、停用/撤权、共享资料溯源、配额入口覆盖、留存及供应商数据边界。 |
| 技术实现复杂度 | 3 | 现有诊断交互局部修补可控。多用户迁移、共享知识与历史引用、在线 Skill 版本、可信现场写入和多模态分别有跨模块成本。 |
| 开发顺序合理性 | 3 | 专业稳定性和身份并行的方向正确。§8 依赖图把整组管理写入和普通端一并挂到试点门槛，与 R1/R2 的定位冲突。 |

### 审核资料、判定口径与运行链

- 已定位原文 [DeviceTruth 商业化产品设计方案 V0.2](<D:/download/DeviceTruth_Commercial_Product_Design_V0_2/DeviceTruth_商业化产品设计方案_V0.2.md>)。对照资料包括 [普通端 PNG](<D:/download/DeviceTruth_Commercial_Product_Design_V0_2/prototypes/01_普通用户端.png>)、[专业端 PNG](<D:/download/DeviceTruth_Commercial_Product_Design_V0_2/prototypes/02_专业用户端.png>)、[管理端 PNG](<D:/download/DeviceTruth_Commercial_Product_Design_V0_2/prototypes/03_管理端.png>)、[HTML 原型](<D:/download/DeviceTruth_Commercial_Product_Design_V0_2/prototypes/index.html>)、包内总览图及 39 项映射表。原文简称「方案」，原型行号均指 index.html。原型 PNG 已逐张查看，HTML 的样式与按钮逻辑按源码核查。
- 参考 [能力盘点](DEVICETRUTH_PRODUCT_CAPABILITY_AUDIT.md)、[专业 MVP 实施后审查](DEVICETRUTH_PRO_MVP_POST_IMPLEMENTATION_REVIEW.md)、[诊断呈现审查](DEVICETRUTH_DIAGNOSIS_PRESENTATION_AUDIT.md)、[README](../../README.md)、[AGENTS](../../AGENTS.md)、[V1.0 技术设计](../design/DeviceTruth_V1.0_通用设备检修Agent_完整产品设计方案_PostgreSQL.md)、[历史 Core 规格目录](../DeviceTruth_Core_V0.1_Spec_Pack/)、[运维文档](../operations/operations.md) 与 [当前专业工作台 OpenSpec](../../openspec/changes/devicetruth-pro-workbench-mvp/)。仓库现存专业 Change 的主体已有验收记录，8.1/8.2 仍未勾选，P1-05 已声明延期。4de7614 冻结后 6d23cd2 改了布局，历史验收与当前页面证据需分别标注。
- 以下「已确认」指源码中能追到实现。「UNVERIFIED」用于本轮未做的运行观察、未验证的供应商/资料与拟新增能力。显式业务路由静态重计为 **32 个**，其中 product 下 27 个（Agent 13、资产 4、文件 3、知识 6、共用健康 1），legacy 专用 5 个。Flyway V1–V17 共创建 17 张 dt_ 表。框架端点、可访问部署和上线能力使用各自的验证口径。能力报告的 A/B/C/D 项数只作盘点索引。
- 以 PLC 型号记录有差异为例，App 向 AgentConversationController 发消息，GeneralAgentService 在事务里保存问题与待执行 Run。Worker 从已存消息、有限历史与已登记引用拼回上下文。TaskOrchestrator 每步只接受模型提出的一项动作，CapabilityGateway 决定能调用的工具。若需要追问，Run 会等待，用问题三元组补答同一轮。若已给出答案，AnswerGuard 核对来源与风险，再由 Service 保存结果，Vue 经 Run 回读和 SSE 展示。点选判断只改变页面焦点，点「深入调查」才通过同一消息入口开启下一轮。相同链路可承接普通端的汽车充电咨询，普通端优先显示问题、风险、用户可安全完成的下一步和来源。证据见 [提交入口](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L43)、[持久化与调度](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L280)、[Worker 上下文](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L462)、[Core](../../src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java#L211)、[答案守卫](../../src/main/java/io/devicetruth/core/decision/AnswerGuard.java#L86)、[Vue 深入调查](../../frontend/src/App.vue#L393)。

## B. 三端逐项审核

### B.1 普通用户端

| 范围 | 结论 | 建议落地 |
| --- | --- | --- |
| 首页与输入 | 保留自由描述、设备可选、自然用语。类别按钮目前只是原型选择效果，资产接口返回 ID，家电/其他类别也未证明具备资料覆盖。 | 选类别时帮助组织描述，提交可在消息里附可编辑的型号/类别提示。仅显示已经过评测的覆盖范围。无资产也能咨询。删去近期历史和独立「知识记录」的重复入口。方案 §§2.1–2.4，HTML 29–32，[资产列表](../../src/main/java/io/devicetruth/api/AssetController.java#L36)。 |
| ASK_USER | 保留动态追问。现有合同只有问题和 requestedFields 等信息，「充电一段时间后中断」一类选项缺少结构化来源。 | 始终显示问题正文，首版用文本框与「暂时无法确认」「无法安全检查」通用回答，可编辑后提交。沿用 replyTo 和同 Run 恢复，问题版本变化时回读并保留草稿。无需固定问答树或额外模型生成选项。方案 §§2.3、7.3，HTML 32、59，[现有补答](../../frontend/src/App.vue#L426)、[问题状态持久化](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L545)。 |
| 当前进度、风险与结果 | 保留「当前判断 / 风险 / 下一步」和来源入口。原型同时摆放待答卡、当前判断与建议；现有等待视图不携带正在生成的 AgentResult。 | 等待时主显示问题和安全提示，前轮答案若展示则明确标注。完成时用「本轮回答已完成」和证据状态，保留未知与人工处理选项。风险前置，首屏只精选低风险动作，前提、限制、资质要求独立可见。「建议联系专业人员」先给去向，无须工单。方案 §§2.2、2.5，HTML 31–33，[RunView 与终态结果](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L37)、[AnswerGuard](../../src/main/java/io/devicetruth/core/decision/AnswerGuard.java#L106)。 |
| 资料与附件 | 已有 Markdown 文件链路、MD/TXT 文本导入和引用。PDF 可接收并存成待处理状态。图片、视频及 PDF 理解均未实现。 | 普通首版可只开放文字；有需求时在次级位置复用 Markdown 上传与解析状态。明确「描述或抄录设备提示文字」，不使用户误认已有拍照识别或 PDF 自动诊断。来源卡优先展示标题、章节、版本、数据性质；缺失时说明暂无可核实来源。方案 §§2.4、5.1，[上传实现](../../src/main/java/io/devicetruth/knowledge/FileUploadService.java#L79)、[附件就绪校验](../../src/main/java/io/devicetruth/knowledge/FileUploadService.java#L149)。 |
| 历史与设备档案 | 复用会话历史和已有资产选择。个人档案、归档、专业配置修改是新增业务，资产快照导入只提供底层写入。 | 首版从会话回读，设备名称/型号可在输入中一次性说明。反复使用的设备确有价值后加「我的设备」基本字段与归档，保持历史关联。列表超过现有 100 条时增加游标。方案 §§2.2、2.4，[会话列表上限](../../src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java#L24)、[资产内部导入](../../src/main/java/io/devicetruth/adapter/persistence/AssetRegistryRepository.java#L119)。 |
| 首发资料与安全 | 目前历史验证主要是工业和汽车的演示案例。家电实际覆盖、车型有效期和现场正确性均 **UNVERIFIED**。 | 在有许可资料的单一场景做首发候选，例如汽车充电中断的信息收集与安全分流。设置命中、未知型号、资料冲突、无资料和高风险样本。普通端只允许非侵入信息采集及经过审核的安全指导。遇到高风险或安全性不确定，明确停止自查并引导合格人员。无需等待个人档案、多模态。方案 §§1.3、2.4、9，[双域历史范围](../../openspec/changes/devicetruth-pro-workbench-mvp/acceptance.md#L209)、[守卫与追问安全检查](../../src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java#L373)。 |

普通端的数据性质还需一处口径修订。AnswerGuard 对无引用的通用回答也会给出 SIMULATED。页面应保留该标记，同时明确「未引用项目资料，仅为通用说明」，避免用户理解为系统已读取模拟设备。当前产品调用三参数守卫，现场核验默认 false，已有资产记录和 AUTHORIZED 引用无法开启确诊路径。[守卫默认值](../../src/main/java/io/devicetruth/core/decision/AnswerGuard.java#L78)、[输出性质](../../src/main/java/io/devicetruth/core/decision/AnswerGuard.java#L135)、[产品调用](../../src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java#L398)。

### B.2 专业用户端

**保持「Agent 自主调查 + 人员主动干预」。** 原型的单焦点卡、证据和下一步方向合理，但静态画面集中表现完成轮，缺少排队、追问、失败/取消与历史回看的可操作状态。现版工作台已有三栏、底部动作栏和记录抽屉，主要差别在证据详情的承载方式和进入动作的时机。

| 能力 | 保留、修改或延期 | 最小实现边界 |
| --- | --- | --- |
| 调查焦点 | 保留本地点击切换和只在明确提交时启动 Agent。 | 左侧用短标签区分候选原因/调查要点，中间展示选中判断、关联证据和下一步。无判断时仍可直接继续文字调查。建议当前为 Run 级，应标「本轮建议」，静态稿焦点切换只改标题和正文，关联来源与建议不可固化。方案 §§3.2–3.4，HTML 40–41、60，[判断映射](../../frontend/src/diagnosisViewModel.ts#L155)、[页面与动作计划](../../frontend/src/components/ProfessionalDiagnosisPage.vue#L45)。 |
| 证据 | 保留原始引用、文档版本、章节、有效时间和数据性质。优先改 F02/F08。 | 为成功、失败/取消和窄屏提供同一详情入口。视觉可保留三栏，但让详情浮层不受证据栏隐藏影响。切轮清空或按 runId 缓存旧资料；区分加载中、无来源、拒绝与错误。方案 §§3.2、3.5，[失败/取消按钮及成功分支](../../frontend/src/components/ProfessionalDiagnosisPage.vue#L129)、[详情挂载](../../frontend/src/components/ProfessionalDiagnosisPage.vue#L225)、[窄屏规则](../../frontend/src/pro.css#L198)。 |
| 深入调查 | 保留新 Run，复用关联引用，明确其业务时点、资产和上限。 | 从所看轮取得资产、时点和引用，默认沿用时点或显式选择「按现在重查」，预览继承与省略。现有服务接收 asOf，Vue 未发送；声明继承的资料属于原业务时点，当前工具重查发现差异另呈。首版 UI 只提供同会话主动继续，服务端目前按同 scope 校验，跨会话可用范围需写入合同。方案 §§3.1、3.3，[Vue 请求](../../frontend/src/api.ts#L464)、[服务端默认时点](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L290)、[引用复制 SQL](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L545)。 |
| 现场补充与追问 | 保留两条语义。补答原 Run，主动补充开新 Run。 | 等待时光标进入追问框，其他状态允许输入草稿或提示取消后再提交。记录「用户报告」及时间，分开显示资料记载、授权来源、现场核实和模拟观测。当前补充按钮只聚焦输入，普通提交会从下拉取设备，不能承诺已选焦点及来源随之继承。方案 §§3.1–3.3，[现场输入](../../frontend/src/App.vue#L420)、[新消息参数](../../frontend/src/App.vue#L368)、[事实 Schema](../../src/main/resources/db/migration/V8__asset_registry_history.sql#L31)。 |
| 多轮、终态与恢复 | 优先解决 F01–F04 和 F08，保留旧轮可读。 | 页面区分「正在查看」与「该会话正在执行」，统一按钮和处理函数的许可条件。SSE 事件触发持久状态回读；补答等待也做状态校准。提交丢回执时按原键、原载荷回读。Agent 只读取同会话有限已完成历史，引用需要显式登记。方案 §§3.3、3.5，[历史查询](../../src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java#L126)、[时间线](../../frontend/src/App.vue#L128)、[补答与重试](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L307)。 |
| 报告与扩展 | 基础打印视图可以延期到有人需要留档。保留调查记录和证据详情。 | 现有 generate_diagnosis_report 是内部工具，单轮聚合可参考，不构成正式报告/签署 API。首批无需动态推理图、跨 Run 自动匹配、Case/Canvas、正式工单或任何新的 Core 动作。方案 §§3.2、3.6，[报告工具](../../src/main/java/io/devicetruth/application/agent/AgentReportTools.java#L39)。 |

HTML 41 将 R8 描述为返回引用的「有效时点」。Reference DTO 没有独立 asOf、资产或核验状态字段。现版界面能分别展示 Run 的业务时点与资产事实记录的有效区间。若确需展示继承引用的原始时点，应在读侧显式提供该元数据，避免用新 Run 的时间代填。[Reference 合同](../../src/main/java/io/devicetruth/core/contract/AgentResult.java#L34)、[R8 投影](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L428)、[比对组件](../../frontend/src/components/EvidenceComparison.vue#L93)。

### B.3 管理端

独立管理入口和授权保留。同源部署可以共用静态资源、登录会话和 Vue 技术栈。下面的「现有 API」均为当前产品 API，管理授权或跨账号视图均需新增。

| 管理能力 | 管理员是否需要 | 现有数据、Service 与 API | 最小操作与安全边界 | 首版与合并/延期 |
| --- | --- | --- | --- | --- |
| 用户与权限 | 需要，试点账户生命周期是基本运维。 | 无用户表、登录或角色 API；ProductConfiguration 从部署配置创建固定身份。 | 采用受控开户或一次性有时效邀请，查询状态、停用、恢复和会话撤销；少量管理员角色由有权限的主体分配并审计，防止误停最后一个有效管理员。高权限 API 服务端强制授权。 | R1 必须有账户处置，可先以受控开户替代完整邀请页面。自助注册、组织和细粒度角色编辑延期。[身份装配](../../src/main/java/io/devicetruth/server/ProductConfiguration.java#L57) |
| Agent 调用记录 | 需要，用来追溯异常和支持工单。 | Run/Event/会话 SQL 与当前归属内的明细、模型/工具事件 API；没有平台列表。 | 分页按时间/状态筛选，显示 Run ID、账号的最小识别字段、状态、错误码、耗时。业务原文、资产详情、来源和内部模型输出不默认展示。关联日志用 traceId/Run ID。 | R1。并入一个「运行与用量」，总览是列表上方的几个真实计数。模式筛选仅在定义并记录模式后开放。[Controller](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L50)、[会话内 Run 列表](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L122) |
| 运营统计与 Token | 需要失败率、排队与成本可见性，初期对账粒度可简化。 | 每次模型事件记录可选 Token/cost，按 Run 聚合并暴露 usageStatus。无平台统计或账单 API。 | 先定义时间窗、分母、尝试与 Run 粒度，排队/等用户与失败分列。展示已报用量、缺失/部分覆盖率。供应商重试、超时、embedding 和估价按独立口径，未知不得计零。 | R1 轻量运行统计，Token 看板可合并或延期。收费、结算与套餐等实际计量完整后再做。[聚合 SQL](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L269)、[传输计量](../../src/main/java/io/devicetruth/adapter/AgentScopeStep.java#L107) |
| 知识库 | 需要知道资料是否导入失败、哪些允许检索，以及到期/撤回如何处理。 | FileUploadService/KnowledgeImportService/KnowledgeSearchService；文件上传/状态/重试、单文档、检索和已登记引用扩读。现有 READY 表示入库可检索。 | 先做受控入库、失败重试、许可/型号/版本登记；若首批存在跨账号资料共享，增加最小发布范围与撤回行为。私有上传归私人，来源信任由服务端设定。历史引用与撤许可、纠错、隐私删除的规则明示。 | R1 可用预置目录或小型只读列表，确有频率再开发布写操作。无需两套上传引擎、通用知识 CMS、审批流或重建向量平台。[文件处理](../../src/main/java/io/devicetruth/api/FileController.java#L41)、[导入落库](../../src/main/java/io/devicetruth/knowledge/KnowledgeImportService.java#L151)、[检索边界](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L187) |
| Skill | 需要知道部署版本、工具许可和本轮选中，紧急停用有运维价值。 | Registry/Runtime、安装和选中表，Run 下 Skill 查询。管理目录/启停 API 缺失。 | 首版从部署清单或显式只读目录查；运维可受控撤授权。上线启停写入前区分停新轮与紧急撤权，处理等待/恢复和历史选择，不开放任意包或可执行脚本。 | 可与知识并为「资料与能力」，网页写入可延期。避免市场、热装、多版本编排和逐账号重复维护。[运行时](../../src/main/java/io/devicetruth/skill/SkillRuntime.java#L42)、[安装与选中](../../src/main/java/io/devicetruth/adapter/persistence/SkillInstallationRepository.java#L25)、[Run Skill](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L53) |
| 设备及业务数据 | 仅验证工业/汽车资产流程的人需要维护。大量查看和修改生产设备事实需要独立业务权限。 | 资产、时点配置、维修四组只读 API；内部快照导入；观测为模拟。没有设备写入、批次管理或现场核验 API。 | 试点沿用受控数据装载和只读查阅。确有写入需求时按设备档案、配置/维修事实与现场核验分别授权和留痕。归档与纠错须保持历史时点回读。 | R1 隐藏空页面。设备目录并入资料页，个人轻档案、正式资产导入、现场写入分开论证。删除通用业务表控制台设想。[资产 API](../../src/main/java/io/devicetruth/api/AssetController.java#L36)、[快照写入](../../src/main/java/io/devicetruth/adapter/persistence/AssetRegistryRepository.java#L119)、[模拟观测](../../src/main/resources/db/migration/V15__asset_observation.sql#L2) |
| 模型与供应商配置 | 需要识别在用供应商、模型、错误率和响应时间。在线改密钥或热切换初期价值低。 | reasoner 由进程配置选择，AgentModelConnections 建供应商连接，已有每 Run 模型尝试；无管理配置 API。 | 在运维状态区显示脱敏模型标识和可验证的状态，业务账号无法读取凭据、Prompt 和原始返回。Embedding 改版需与索引兼容，测试连接与配置发布由运维控制。 | R1 只读信息可并入系统状态。管理配置、热切换、供应商策略与索引迁移延期。[模型选择](../../src/main/java/io/devicetruth/server/ProductConfiguration.java#L200)、[供应商连接](../../src/main/java/io/devicetruth/adapter/AgentModelConnections.java#L14)、[索引版本约束](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L99) |
| 配额、监控与审计 | 需要，属于试点门槛。 | 有单 Run 活动时限/步数、worker 和 SSE 容量、硬编码健康响应与框架依赖。业务 RunEvent 可供运维监控，不是管理员变更记录。 | 原子限制每账号创建/同时执行次数，独立限制上传、文本导入、直调检索、SSE 与存储。追加式写入管理员变更，结合保留/访问策略，至少记录操作者、目标、时刻、结果和关联 ID。监控需能发现 DB 失败、队列滞留、超时和供应商错误。 | R1 核心可用，先用部署默认额度与运维告警。独立额度设置页、审计检索高级功能与完整指标看板可延期。metrics 500 是历史观测，根因及当前复现 **UNVERIFIED**。[Run 预算](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L100)、[SSE 容量](../../src/main/java/io/devicetruth/application/agent/RunEventStreamService.java#L101)、[健康接口](../../src/main/java/io/devicetruth/api/HealthController.java#L12) |

## C. 问题清单

P0 的阻断范围随阶段标明。R0 是本地诊断闭环，R1 是多用户试点，R2 是普通用户发布。F01–F09 沿用既有审查编号。F07 处理验收版本和说明材料，F09 安全工具摘要仍可延期，不应阻塞基础交互。能力盘点 §5.1 的等待关闭流说法按当前 Server 源码修正，等待不触发服务端 RUN_TERMINAL。[服务端终态集合](../../src/main/java/io/devicetruth/application/agent/RunEventStreamService.java#L37)、[可选工具摘要任务](../../openspec/changes/devicetruth-pro-workbench-mvp/tasks.md#L54)。

| ID | 严重程度 | 方案章节 | 问题描述 | 源码或设计证据 | 影响 | 修改建议 |
| --- | --- | --- | --- | --- | --- | --- |
| V02-01 | P0 · R1 | §§4.3、5.2、6.1、9.2 | 方案提出动态身份和「重新校验」，需要补齐后台认领、SSE 已建连撤权与停用后的行为。Run 已有持久归属，执行仍读固定身份。 | [固定身份](../../src/main/java/io/devicetruth/server/ProductConfiguration.java#L57)、[Agent 执行](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L462)、[恢复扫描](../../src/main/java/io/devicetruth/application/agent/RunRecoveryService.java#L79)、[流建立与转播](../../src/main/java/io/devicetruth/application/agent/RunEventStreamService.java#L106)、[Skill 固定域](../../src/main/java/io/devicetruth/skill/SkillRuntime.java#L58) | 仅完成登录时，两个真实账户仍可能读写同一批数据。停用后的连接和后台任务也无政策可遵循。 | 入口解析可信账户，复用持久 owner/project，后台按受控任务加载身份并复查授权。定义停用、撤权和在途策略，阻断 R1 直到双账号验证完成。 |
| V02-02 | P0 · R1 | §§4.3、5.1、6.1–6.2 | 私有上传可从入口提交 dataNature，JSON 导入也接收该字段。READY 与审核发布是不同状态。K4 检索会登记证据，不能当作只读目录。 | [上传字段](../../src/main/java/io/devicetruth/api/FileController.java#L41)、[导入请求](../../src/main/java/io/devicetruth/knowledge/KnowledgeImportService.java#L35)、[K4](../../src/main/java/io/devicetruth/api/KnowledgeController.java#L79)、[登记写入](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L301) | 平台可能错误展示可信度，或把管理员测试检索写进业务 Run。开放入口也带来资源消耗。 | 普通上传的来源性质由服务端赋值并标为用户资料。平台受控入库与诊断登记分权，限制 K1/K4，必要时分离只读预览。 |
| V02-03 | P0 · R2 | §§2.4–2.5、7、9.2 | 安全兜底主要针对关键词、语义合同及证据约束。原型把可自查建议与高危提示放在独立区域，实际执行边界仍缺产品规则。 | [答案与风险约束](../../src/main/java/io/devicetruth/core/decision/AnswerGuard.java#L86)、[追问过滤](../../src/main/java/io/devicetruth/core/decision/AnswerGuard.java#L229)、方案 146–150，HTML 32–33 | 用户可能把资料支持、模型建议和可自行执行混同，高风险样本未验前不宜开放至普通用户。 | 高风险或未知安全性以明确停止/转介为主，普通端只展示经过规则与场景验收的可自查动作。保留完整报告的安全限制作专业复核，普通端详情同样受安全规则约束。 |
| V02-04 | P0 · R0 · F01 | §§2.2、3.5、7.3 | 追问正文依赖末条消息是否相同，正文实际位于另一个抽屉时会隐藏。 | [问题文本持久化](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L545)、[卡片条件](../../frontend/src/App.vue#L543)、[记录抽屉](../../frontend/src/App.vue#L602) | 常见等待态无法理解要补充什么。 | 问题正文随当前 pendingQuestion 固定展示，版本变化及时回读。 |
| V02-05 | P0 · R0 · F02 | §§3.2、3.5、7.2 | 失败和取消按钮设置选中引用，但详情只在成功分支挂载。窄屏隐藏详情所在证据列。 | [失败/取消分支](../../frontend/src/components/ProfessionalDiagnosisPage.vue#L129)、[详情](../../frontend/src/components/ProfessionalDiagnosisPage.vue#L225)、[CSS](../../frontend/src/pro.css#L198) | 已取回的证据有列表但详情可能不可达。 | 共用可独立挂载的浮层，处理焦点、关闭和回到触发按钮。 |
| V02-06 | P0 · R0 · F03 | §§3.3、3.5、5.1、9.2 | UUID 键只做重复拒绝。普通提交和深入调查传 undefined，补答也默认生成新键，重复补答先检查过期状态。 | [Vue 调用](../../frontend/src/App.vue#L368)、[键默认值](../../frontend/src/api.ts#L464)、[重复校验](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L280)、[补答校验顺序](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L307)、[消息唯一约束](../../src/main/resources/db/migration/V1__agent_conversations.sql#L35) | 丢回执后换键可重复执行与计费。同键 409 也不能确定 UI 应回到哪一轮。 | 保存逻辑操作键与冻结输入。按键回放/查询原接受结果，重复键载荷不一致明确 409，补答重放应早于过期校验且守住授权。 |
| V02-07 | P1 · R0 · F04 | §§3.5、7.3、9.2 | terminalStatus 来自非 Vue 响应式快照。流终态仅完成、失败、取消，等待是独立持久状态。 | [终态监听](../../frontend/src/App.vue#L128)、[快照](../../frontend/src/timeline.ts#L47)、[流终态](../../src/main/java/io/devicetruth/application/agent/RunEventStreamService.java#L124) | 状态可能只在轮询后更新，回到旧会话时依靠人工刷新。 | 从 onChange 投影响应式状态或以受控事件触发回读，保留轮询、游标和乱序保护。等待后按 UI 是否仍需要实时流决定是否关闭。 |
| V02-08 | P1 · R0 · F08 | §§3.2–3.3、5.1 | 历史 Run 时点与当前下拉资产混用。新 Run 不自动沿用原时点，引用复制保留原时点但不复制已保存的 citation_excerpt。 | [资产比对绑定](../../frontend/src/App.vue#L667)、[提交 DTO](../../frontend/src/api.ts#L464)、[引用复制 SQL](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L545)、[引用回读](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L429) | 历史对比易误读，新轮可能拿到不同截长或缺失的摘录。 | 绑定所看 Run 的资产/时点/证据。注明按原时点或当前复查。以测试覆盖原摘录、资产或时点不匹配及权限撤回。 |
| V02-09 | P1 · R0/R1 | §§3.3、6.1、9.2 | 页面可阻止并发点击，新建 Run 的服务端事务只有会话锁和请求键检查，未限制不同键并存。引用继承服务端按同 scope，未限定同会话、选中判断或资产时点。 | [提交事务](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L290)、[继承 SQL](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L545)、[当前入口守卫](../../frontend/src/App.vue#L393) | 多标签或网络重试可能造成多个活动轮，最新轮等待可能掩盖旧待答。历史资料可能被当成当前支持依据。 | 如采用每会话一个未决 Run，在会话锁内校验并返回可定位的冲突。首版关闭无实际需求的跨会话继承操作，服务端明确资料范围和回收策略。 |
| V02-10 | P0 · R1 | §§4.2、5.2、6.2、9.2 | 配额只描述 Run，遗漏附件、直接导入/检索、SSE 及磁盘的账号级约束。管理写审计和供应商数据处理的操作边界欠清。 | [上传文件落盘](../../src/main/java/io/devicetruth/knowledge/FileUploadService.java#L79)、[检索向量化](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L99)、[Run 预算](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L100)、方案 288–296、474–475 | 试点难控制开销与数据暴露，管理员动作难追责。 | 定义额度占用/释放、等待态口径和申请重放。入口逐项限制，明确文件与历史留存、删除例外、备份恢复、供应商传输和日志脱敏。 |
| V02-11 | P1 · R1 | §§4.2–4.4、6.1–6.2 | 增加知识发布状态并不足以共享检索。候选筛选、引用登记、扩读依赖同 owner/project 等值。下架后何种历史内容可继续读取也未定。 | [候选过滤](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L187)、[扩读](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L251)、[登记](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L301) | 资料共享会跨检索、访问和历史证据边界，默认覆盖历史存在两难。 | 少量试点先按账号使用同一许可版本的受控预置；真共享独立交付，明确发布范围、私有优先级、版本标识和撤回后的历史可见性。 |
| V02-12 | P1 | §§4.2–4.3、5.2、6.2 | Skill 启停看似单字段，当前仅能匹配 Registry 中对应版本，并会验证已选版本安装与工具授予仍有效。 | [启动及持续授权](../../src/main/java/io/devicetruth/skill/SkillRuntime.java#L42)、[版本匹配](../../src/main/java/io/devicetruth/skill/SkillRuntime.java#L157)、[仓储锁](../../src/main/java/io/devicetruth/adapter/persistence/SkillInstallationRepository.java#L73) | 在线停用可能使在途/补答 Run 失败。仅存版本和摘要不足以在线保留两份内容。 | 首版运维预部署，网页只读。未来区分停止新选和紧急撤权，明确旧版本可读或受控失败。 |
| V02-13 | P1 | §§2.4、5.1、7.2、10 | “仅 Markdown”适用于目前可用页面及可检索的 multipart 文件。PDF 文件可登记；直接 JSON 入口支持 Markdown/纯文本。 | [Markdown 及 PDF 路由](../../src/main/java/io/devicetruth/knowledge/FileUploadService.java#L79)、[PDF 状态](../../src/main/java/io/devicetruth/knowledge/FileUploadService.java#L122)、[JSON 文本导入](../../src/main/java/io/devicetruth/knowledge/KnowledgeImportService.java#L35)、[Vue 文件选择](../../frontend/src/App.vue#L563) | 容易误判留存成本、重试意义和分析能力。 | 分开写明入口、登记、解析、检索和可随消息使用。首版隐藏 PDF/图片入口，历史 PDF 记录给明确状态。 |
| V02-14 | P1 | §§1.3、4.2、8.1–8.2、9.1 | R1 说专业先试，图上却要求普通端与知识/Skill/资产管理整组完成。管理页看板也缺模式字段。 | 方案 84–91、416–443，HTML 46–50，[产品 DTO](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L47) | 依赖多且不可独立验收，容易把未支持功能画成可用。 | 专业小范围试点以安全基线、脱敏运行记录和预置资料为门槛。普通端 UI 并行，按资料/安全门槛另行发布。 |
| V02-15 | P1 | §§3.5、9.2–9.3 | RAG 错误及末步答案仍有已知恢复窗口。冻结后的布置不能由上一版浏览器验收覆盖。 | [RAG 事务边界](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L149)、[末步结果提交](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L432)、[历史延期](../../openspec/changes/devicetruth-pro-workbench-mvp/acceptance.md#L243) | PG 异常可能使外层事务回滚，进程在答案提交前退出可能重推理或耗尽预算。本轮未注入。 | 单独验证两个故障，各自决定是否修复或承诺可恢复失败。保留现有租约与补派架构。 |
| V02-16 | P2 | §§4.2、7、9.3 | 原型绿色健康点和示例 KPI 易被截图单独传播。普通端窄屏规则仍保留固定侧栏。 | HTML 21、25、46–50、61，[健康响应](../../src/main/java/io/devicetruth/api/HealthController.java#L12) | 状态与真实测量混淆；移动端内容区受挤压。 | 成品管理导航只显示真实能力，探针标签写明测量对象。普通端窄屏默认收起历史，按状态显示单列主任务。 |

## D. 功能与现有代码适配矩阵

### D.1 已有 HTTP 合同及增量

方案 §5.1 列出的路径与方法基本正确。R1、R2 等阶段编号与盘点 R1、R2 等端点编号撞名，建议正文优先写方法和路径。下表省略重复的 /api 前缀，按 Controller 注解和 DTO 复核。所有管理身份、平台总览和生产用量仍属 **UNVERIFIED** 的拟建能力。

| 关键功能及状态 | 对应接口、类与 DTO | 需要的增量 → 修改范围 | 风险 → 建议优先级 |
| --- | --- | --- | --- |
| 会话和历史 · 已有 / 可小幅增强 | POST /conversations 空体/空对象 → 201 ConversationView 与 Location；GET /conversations → 最近 100 条；GET /conversations/{id} → ConversationDetail（conversation/messages/runs）；GET /conversations/{id}/messages → MessageView[]。[Controller](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L27)、[列表 SQL](../../src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java#L24) | 复用历史。真实规模按更新时间及 ID 用游标查询。标题需要时使用首条用户消息的授权短标题，不批量读取全量明细。 | 导入既有记录和私有标题；R0 复用、R1 绑定身份。 |
| 提交、继续、补答 · 已有 / 可小幅增强 | POST /conversations/{id}/messages；UUID Idempotency-Key，AgentTaskInput {schemaVersion=1, userMessage, selectedAssetId?, asOf?, attachments?:[{fileId}], contextRefs?:[{referenceId}], replyTo?:{runId,pendingQuestionId,questionVersion}} → 202 RunView 与 Location。GET /runs/{id} 回读 RunView 含状态、时点、待答、结果、失败码、耗时、用量。 [Controller](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L43)、[DTO](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L47)、[附件及引用校验](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L336) | 同键回执或查询、活跃轮规则、前端待确认提交记录。补答继续原 Run，普通现场说明/深入调查新 Run。 | 载荷重放、重复模型调用、失联二次入队；R0 P0。 |
| 撤销、事件和状态 · 已有 / 可小幅增强 | POST /runs/{id}/cancel → RunView，终态再取消 409；GET /runs/{id}/events?after=N → PublicEvent[]；GET /runs/{id}/stream + Last-Event-ID → SSE；GET /runs/{id}/tool-events 与 /model-events → 项列表。[Controller](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L65)、[取消](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L115) | 修前端终态/等待校准与取消提示，SSE 和 HTTP 同权。区分实际输出与事件摘要。失败、取消回读也应恢复历史。 | 迟到响应、长连接撤权及错误码差异；R0 修交互，R1 修权限。 |
| Skill 本轮 / 管理 · 已有 / 必须新增（管理操作开放时） | GET /runs/{id}/skill → SelectionView（skillId/version/devices/tasks），无选中返回 404。当前附属于 Run。Registry 以预部署目录读文件，安装与选中走 V14。[Controller](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L53)、[无选择返回分支](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L207)、[V14](../../src/main/resources/db/migration/V14__skill_installation.sql#L1) | 用户端直接复用。管理端可先读部署清单，未来新增经审计的目录/授权服务。 | 关闭授权、版本摘要不匹配及恢复失败；网页写入延期。 |
| 引用与知识扩读 · 已有 / 可小幅增强 | GET /runs/{id}/references → Reference[]（含已登记未被回答使用的来源）。GET /knowledge/references/{referenceId}?runId=… 和 GET /knowledge/documents/{documentId}/chunks/{chunkId}?runId=… → Excerpt，需要相应 Run 已登记引用，适用于知识片段。当前成功页主要使用 result.references 与所带摘录。[引用 Controller](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L59)、[知识扩读入口](../../src/main/java/io/devicetruth/api/KnowledgeController.java#L94)、[Vue 引用加载](../../frontend/src/App.vue#L80) | 详情给可达入口，按需接知识扩读，资产证据使用引用元数据及资产只读接口。标明「本回答引用」和「本轮查阅」两个集合。 | 取回范围或版本错位；R0 P0/P1，R1 补共享/撤权。 |
| RAG · 已有 / 必须新增（平台共享） | POST /knowledge/documents（ImportRequest 文本 → 201 ImportResult），GET /knowledge/documents/{documentId} → DocumentMetadata；GET /knowledge/tools → 工具描述；POST /knowledge/search（runId/query/filters/limit/mode → SearchResponse，命中登记）。[知识 Controller](../../src/main/java/io/devicetruth/api/KnowledgeController.java#L41)、[导入合同](../../src/main/java/io/devicetruth/knowledge/KnowledgeImportService.java#L35) | 运行内复用 KnowledgeTools。文档目录、发布治理、只读测试检索按实际需求补；受控预置可避免 R1 即上共享检索。 | 私有资料污染、版本误命中、付费入口和历史引用；R1 安全边界，治理功能分期。 |
| 文件 · 已有 / 可小幅增强 | POST /files（multipart file/title/domain/deviceModel/version/dataNature → 202 FileView）、GET /files/{fileId}、POST /files/{fileId}/retry → 202 FileView。Markdown 翻到 PARSED 后方可作为附件。PDF 进 OCR_REQUIRED / PDF_NOT_SUPPORTED。[文件 Controller](../../src/main/java/io/devicetruth/api/FileController.java#L41)、[文件服务](../../src/main/java/io/devicetruth/knowledge/FileUploadService.java#L122) | 用户私有资料强制来源标识，失败重试只对可恢复类型开放。原件下载/删除/业务文件目录按数据治理需求设计。 | 磁盘与向量成本、权限和失败提示；R1 限制暴露面，R2 视用户需求开放。 |
| 资产读取 · 已有，资产写入 · 必须新增（开放维护时） | GET /assets → string[]；GET /assets/{assetId}?asOf=… → Composite；GET /assets/{assetId}/configuration 支持 componentId/attribute/asOf，GET /assets/{assetId}/maintenance 支持 componentId/asOf。日期为 ISO-8601 时刻。[资产 Controller](../../src/main/java/io/devicetruth/api/AssetController.java#L36)、[资产主键](../../src/main/resources/db/migration/V8__asset_registry_history.sql#L10) | 读侧冻结 Run 上下文。个人档案若做，扩展现有资产或在现有资产上加个人资料字段，用服务端内部 ID，保留历史引用。配置/维修/确认使用专门事实写合同。 | dt_asset.id 为全局主键，不能把同名模型/车牌当成 ID。归档不导致历史丢失；R0 修读，档案按需求延期。 |
| 账号、准入、审计、运营 · 必须新增 | 当前唯一公开健康业务路由 GET /health；ProductConfiguration 固定身份，Run repo 提供归属内查询。[健康](../../src/main/java/io/devicetruth/api/HealthController.java#L12)、[装配](../../src/main/java/io/devicetruth/server/ProductConfiguration.java#L57)、[Run 列表](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L122) | 真正增量是登录/注销/我是谁、账号处置、脱敏跨会话分页、配额占用和管理审计。先给运维查阅与默认限额，按需要加 overview/quotas 等页面 API。 | 认证、恢复、SSE、成本与原文访问；R1 门槛。 |
| 扩展功能 · 不建议首期实施 | 个人账户只需复用现有 Conversation/Run 与知识资产栈。观测表仅支持 SIMULATED，报告是内部聚合工具，前端目前从 App 挂载。[V15](../../src/main/resources/db/migration/V15__asset_observation.sql#L2)、[内部报告](../../src/main/java/io/devicetruth/application/agent/AgentReportTools.java#L46)、[Vue 入口](../../frontend/src/main.ts#L1) | 延后动态图谱、Case 表、通用资产 CRUD、签署/工单、自动多模态、热切换与计费。实现时各自需数据合同和验收。 | 过早改 Core、拉长安全发布路径；R3 或需求实证后复议。 |

补充 API 口径。错误体并非全产品统一，Agent Controller 提供 code/message/retryable/traceId，知识和文件使用 ApiDtos.ErrorResponse 中的 requestId，前端错误处理需兼容。per-run 明细与 role=ADMIN 的运营查询应保持不同 DTO。Run 当前未持久化普通/专业模式，若运营确需筛选，只记录发起时的界面模式标签，授权仍以服务端身份为准。[Agent 错误体](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L147)、[通用错误 DTO](../../src/main/java/io/devicetruth/api/ApiDtos.java#L45)、[Run DTO](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L55)。

### D.2 数据模型和作用域改造边界

| 现有数据 | 建议复用与边界 | 改动范围及成本 |
| --- | --- | --- |
| V1 会话、消息、Run、事件，V5 消息关联，V12/V13/V16/V17 租约、生命周期、尝试与检查点 | 保留表、归属列、关系、CAS 和检查点。首版个人 scope 可由稳定账户 ID 和固定项目规则推导。只在需要多个 scope/成员时加映射表。幂等同键用现有消息唯一约束，按消息、目标 Run 或补答关联取得回执，必要时补最小载荷摘要。 | 复用 DDL 所需工作小，异步身份和兼容迁移工作高。演示 scope 留在隔离环境或经明确映射迁入，备份、演练并检验关联完整性。[V1](../../src/main/resources/db/migration/V1__agent_conversations.sql#L3)、[V5](../../src/main/resources/db/migration/V5__agent_run_message_link.sql#L5)、[V12](../../src/main/resources/db/migration/V12__run_checkpoints.sql#L6)、[V17](../../src/main/resources/db/migration/V17__agent_execution_checkpoint.sql#L1) |
| V2/V3/V4/V6/V7 文件、文档、向量、引用、导入状态和来源关系 | 复用现有版本号、校验和、embedding profile 和文件—文档关系。授权发布是独立的可见性状态。若共享平台库，显式区分「引用属于哪个 Run」和「被引用资料原属哪里」，实现从检索到详情/扩读/继承的授权传播。 | 索引/查询与历史引用环环相扣。保留历史时需决定保留版本、摘要、元数据或受控遮蔽，区分普通替换、纠错、许可撤回和隐私删除。[V2](../../src/main/resources/db/migration/V2__knowledge_sources.sql#L22)、[V4](../../src/main/resources/db/migration/V4__knowledge_legacy_quarantine.sql#L6)、[V7](../../src/main/resources/db/migration/V7__file_document_versions.sql#L33)、[扩读关联](../../src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java#L251) |
| V8–V11、V15 资产、组件、时态配置、维修及模拟观测 | 复用资产与引用 ID，保留有效区间、核验状态、资料性质。个人便笺与授权事实写入分权，归档不直接硬删除业务记录。 | 用户档案中等，生产资产导入与现场数据可信治理高。当前快照写入以幂等装载为目的，普通编辑不可直接暴露同一写入。[V8](../../src/main/resources/db/migration/V8__asset_registry_history.sql#L10)、[V11](../../src/main/resources/db/migration/V11__asset_configuration_validity_identity.sql#L23)、[V15](../../src/main/resources/db/migration/V15__asset_observation.sql#L2)、[快照导入](../../src/main/java/io/devicetruth/adapter/persistence/AssetRegistryRepository.java#L119) |
| V14 Skill 绑定、选中和参考摘要 | 复用安装和选中表，临时按账号受控初始化或经明确平台授权读取预部署 Skill。网页首期只展示实际安装信息。 | 按账号初始化与权限解析需统一；多版本共存/即时撤回会涉及 Registry、在途任务和恢复。[V14](../../src/main/resources/db/migration/V14__skill_installation.sql#L1)、[SkillRuntime](../../src/main/java/io/devicetruth/skill/SkillRuntime.java#L42) |
| 商业化核心新增 | 账户与会话认证、管理员动作记录、原子账户准入/计量。部署默认限额较足时省去独立套餐、价格和可配置额度对象。 | 用增量 Flyway，保留历史迁移。写审计与状态变更尽可能同事务，限制审计读取及写入身份并设置留存。按功能开放逐项证明数据授权。[目前固定身份](../../src/main/java/io/devicetruth/server/ProductConfiguration.java#L57)、[运行事件](../../src/main/resources/db/migration/V1__agent_conversations.sql#L66) |

## E. 建议修改的具体内容（按原文顺序）

下列引文短语用于定位。块引用是可直接用于人工修订的建议稿，实际取舍仍按发布范围确认。

### 第 0 章　执行摘要与原则（10–40 行）

**原表述与问题。** 「一个用户端，两种交互模式」「单轮真实调查先于全量图谱」保留。能力快照的「已有」同时涵盖 HTTP、内核工具和内部导入方法，容易误作均可开通的产品功能。接口计数和表数正确，39 项分级受分类口径影响。

> 两种使用模式共享会话、Run、证据与只读 Agent 工具。当前部署使用固定 scope，限隔离环境使用。文本咨询、Markdown 检索资料、动态追问、证据回读和逐轮调查已有代码路径。PDF 可登记存储，无法用于诊断；图片和视频理解待定。平台账号、管理授权、资料共享和账户额度属于商业化增量。文档记载、用户自述、模拟观测与现场核验分别标注。

**改动影响。** 文档口径调整。保留 Core 与已有模块，不为不同模式重新定义诊断生命周期。[核心运行](../../src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java#L211)、[固定 scope 装配](../../src/main/java/io/devicetruth/server/ProductConfiguration.java#L57)。

### 第 1 章　定位、入口与 R0–R3（44–91 行）

**原表述与问题。** /app/simple、/app/pro、/admin 合理。首版入口挂了设备、资料、报告等未成闭环的菜单。第 8 章把 R2 一并挂到试点门槛，增加场景准备和数据治理成本。

> 用户应用内可切换普通与专业显示并继续查看同一会话。管理入口使用独立的服务器授权，普通账号只访问自己的数据。R0 为隔离的专业演示，验收已存在的调查、追问、引用、历史和提交恢复。R1 为少量受邀的专业试点，先达到身份与授权、基础额度、审计、脱敏运维和已许可预置资料门槛。R2 按首发域发布普通咨询，复用同一安全底座，设备档案按实际需求排期。R3 按需求与评估扩展图像/PDF、团队、设备接入、工单与计费。

**改动影响。** 用户端只需专业、普通和共享历史入口。独立管理路由可同一个前端构建。减少空导航、测试组合和早期设备 CRUD 投入。[Vue 入口](../../frontend/src/main.ts#L1)、[依赖清单](../../frontend/package.json#L13)。

### 第 2 章　普通用户模式（95–150 行）

**原表述与问题。** 「按模型实际问题生成选项」缺少选项 DTO，可以在不改变协议的情况下提供有意义的快速回答。首屏三条建议也要保留安全前提与来源。设备类别只是描述线索，当前原型展示的家电诊断广度未证明。首版启动条件无需个人设备档案。

> 用户可直接描述现象，设备类别与型号均可选。排队和调查中显示当前状态，不展示推测结论。等待补充时完整呈现实际问题，支持文字及「暂时无法确认」「无法安全检查」，用当前问题版本补答原 Run。完成后展示证据状态、当前判断和少量安全下一步。高风险或措施安全性未能判断时，突出停止自查和专业转介。已检索来源与模型推测可展开查看，声明当前轮的来源覆盖、不确定性和必须遵守的安全限制。
>
> 先上线一个资料许可、型号适用性和安全样本均通过验收的场景。默认接收文字；Markdown 附件仅在界面确认可读后随消息使用。既有 PDF 记录标注无法分析。图片、视频与设备档案待需求验证后加入。用户可在会话历史恢复进度，申请维修或紧急协助先通过明确的线下去向完成。

**改动影响。** 复用 Conversation、pendingQuestion、RunView、引用、时间线和历史，新增的是普通视图与风险/支持范围呈现。领域语料许可、评测和安全策略有独立投入；单次答复质量不从模板保证。若后续加真实选项，再考虑可选结构字段和模型回归。[补答请求](../../frontend/src/api.ts#L489)、[服务端追问过滤](../../src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java#L373)、[风险与限制](../../src/main/java/io/devicetruth/core/decision/AnswerGuard.java#L126)。

### 第 3 章　专业工作台（154–207 行）

**原表述与问题。** 「一个焦点」「选中不等于调查」保留。「当前所选焦点的下一步」在合同里没有逐判断建议关联。F01/F02/F03 为 R0 主路径，F04 与 F08 可局部并改。P1-03 和 P1-05 是不同事务/崩溃窗口，不能归为一次普通重启验收。

> 工作台以当前任务、活动状态、调查焦点和下一步为核心。选中要点后展示其引用。未逐条关联的建议统一标为本轮建议。全部证据、历次 Run、资产时点事实和完整答案从抽屉或折叠区访问。完成、失败、取消和窄屏均可打开本轮已登记来源。无结论时保留来源、重试或继续补充的入口。
>
> 紧接回答一条补充问题时，用 replyTo 恢复原 Run。「补充现场观察」保留为用户报告；如当前不存在待答问题，可用文字开新轮。「深入调查」从所看 Run 读取资产、业务时点、当前判断和可继承引用，提交前显示要沿用的时点以及最多十条引用的继承情况。可以显式改为按当前时点复查。旧引用标示原时点，继续调查重新核验。历史轮的浏览许可与当前会话的提交许可独立显示。
>
> 重复同一操作使用原 UUID 键与相同载荷，服务器按已接受请求回放 Run 定位。不同载荷使用同键时返回冲突。运行中采用有界草稿，服务端若限制一会话一个未决 Run，则把活动轮定位一起返回。SSE 提供事件提示，Run 回读校准等待或终态。保持断线续接和序列去重。

**改动影响。** App.vue、ProfessionalDiagnosisPage、EvidenceDrawer、api.ts 与时间线局部修改；幂等和并发保护只触及 Controller、Service、Repository 及必要测试/索引。现有近期历史上限为 12 条和 6000 字符口径的预算，不能承诺跨轮完备记忆。当前先做 ViewModel 的短标题和全文展开，真实样本需要时再收紧 StructuredAgentReasoner 的表达指导。没有首发需求要求改 Core 动作或实现动态图谱。RAG 登记异常由真实 PG 注入，末步结果单独测答案生成后、落库前的退出窗口。[历史限制](../../src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java#L123)、[结构化协议](../../src/main/java/io/devicetruth/adapter/StructuredAgentReasoner.java#L24)、[深入调查计划](../../frontend/src/components/ProfessionalDiagnosisPage.vue#L59)、[已有延期](../../openspec/changes/devicetruth-pro-workbench-mvp/acceptance.md#L243)。

### 第 4 章　管理端（211–258 行）

**原表述与问题。** 模块方向可用，但「默认业务表目录」「Skill 网页启停」「知识发布与下架」按当前代码实现是三个不同成本项。模式列无数据来源，运营列表默认显示问题原文也超出最低运维目的。指标 500 是历史观测，按当前受控部署重新验证。

> 首版实际开放账户处置、脱敏运行查询及系统状态/审计。运行列表按时间与状态筛选，查看状态、耗时、稳定错误码和必要模型摘要；汇总直接基于可解释的计数。用户私有文本、文档和资产详情需单独获授权。账务以外的用量显示已知总量与报告覆盖率。配额先采用部署默认值并能拒绝超额请求。
>
> 受控运维准备平台资料与预部署 Skill。需要网页时先提供可信的只读目录，知识上传/导入复用现有服务。实际启用发布/下架、Skill 启停、设备事实写入前分别定义权限、在途行为、历史回读与审计合同。首版没有可执行操作的模块从导航移除。监控分别报告进程、数据库、队列与供应商的可验证状态。

**改动影响。** 平台新增一组最小账号与运行 API，健康与审计可以先从运维侧交付；省去空页面和平台 CMS 开发。依赖 §6 身份和授权；若资料需跨账号自动共享，按 D.2 独立估算。[运行访问入口](../../src/main/java/io/devicetruth/api/AgentConversationController.java#L50)、[模型用量聚合](../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java#L269)、[健康响应](../../src/main/java/io/devicetruth/api/HealthController.java#L12)。

### 第 5 章　API 与 Vue 结构（262–326 行）

**原表述与问题。** §5.1 路径主体准确。省略了 K4 有写入、K5/K6 的 runId、重复键 409、F3 与传参限制等。§5.2 把尚无用户需求的目录、可编辑配额、资产维护列成同一批「必须新增」。§5.3 的文件移动容易顺手牵连现有 App 的刷新、轮询、本地恢复和抽屉。

> 产品 API 以 D.1 的逐操作合同为准。用户诊断沿用会话、消息、Run、引用、历史、补答和取消。当前键提供重复拒绝，稳定回执由本次增量补齐。K4 是登记 Run 引用的检索入口。知识来源扩读必须带正确 runId。管理端新增可信账号识别、管理员账号处置、脱敏运行查询，以及必要的审计访问；余下的路由仅随实际开放功能加入。每种路由明确调用者、数据范围、返回字段与验收条件。
>
> Vue 保留 App 现有运行逻辑，先抽出已稳定的追问表单、引用详情和风险提示，再接普通视图。管理端独立 Shell，真实深链接成为需要时可引入 Vue Router，负责导航和加载页面。小步提取状态逻辑，保留已有 API 应答、SSE 和恢复测试。首批联调使用账本明确的测试或演示环境，同源生产版本再打通会话与静态页面访问策略。

**改动影响。** 先抽实际共享组件，api.ts/专业组件无需仅为目录树改名搬家。管理 API 使用独立文件及最小 DTO。引入路由时检查刷新深链接回退、登录后跳转、权限导航、切模式保留上下文，以及切用户清理本地会话定位与缓存。Pinia 可按实需决定。[现有 App 切换与定位](../../frontend/src/App.vue#L249)、[本地恢复键](../../frontend/src/App.vue#L317)、[Vue 入口](../../frontend/src/main.ts#L1)。

### 第 6 章　身份、权限与数据（330–369 行）

**原表述与问题。** 采用可信主体、复用 owner/project、写时审计的方向正确。目前已有持久 Run 归属。真正改动是请求解析、执行读取、恢复扫描、SSE 订阅和 Skill 范围使用该归属。账号「密码加密存储」应改为不可逆密码哈希（自建凭据时）。6.3 表格应明确使用模式与资源授权的关系。

> 由服务端认证主体解析稳定账户和授权范围。自管密码使用成熟的密码哈希方案和逐账户随机盐，登录节流、同源安全会话、CSRF 与 TLS 按部署验收。邀请或开户受控。用户身份在创建 Run 时与会话归属同事务写入。后台从已存业务归属恢复上下文，认领、续跑、补答和工具访问按操作复查账户与资源权限；运行中停用、撤权或归档时执行明确的收尾策略。SSE 订阅、重连和持续转播遵循同一账号边界，退出或角色变更的连接清理时限可验证。
>
> 普通与专业使用模式可切换；每项业务操作的访问取决于真实角色、归属、许可与业务资格。平台权限用于运营动作，不内含读取私密原文。试点个人账号直接映射单一 scope，存量演示范围独立处置。平台预置资料和私人上传分开管理。共享检索启用前，落实发布、适用范围、原始版本、撤回和历史引用访问。
>
> 复用已有 Run、资产、文档、引用、Skill 和事件表。新增实际需要的账户、审计与账号准入数据。管理员变更记录实际操作者、目标和结果，限制改变及读取权限。明确用户数据采集、AI 供应商传输、用户历史与原件的保留/授权删除及备份恢复。所有 DDL 增量迁移。

**改动影响。** 认证页面成本低于作用域改造。变化覆盖 Spring Security/Controller、GeneralAgentService、RunRecoveryService、RunEventStreamService、Knowledge/File/Asset、SkillRuntime 与授权测试。跨库/批量迁移、任意角色编辑和 RLS 均无需成为第一版实现前提。额度应对同键、取消、等待与过期租约有一致定义。[固定 Worker 身份](../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java#L462)、[SSE 读侧](../../src/main/java/io/devicetruth/application/agent/RunEventStreamService.java#L106)、[Skill scope 检查](../../src/main/java/io/devicetruth/skill/SkillRuntime.java#L69)。

### 第 7 章　三端视觉与交互（373–404 行）

**原表述与问题。** 品牌、浅色留白和管理深色侧栏有一致性可保留。普通端已有侧栏又有顶部导航，输入、追问、结果并列；窄屏 CSS 在 900px 下仍保留 180px 侧栏。专业端既有状态徽标又有任务卡状态，兜底态和历史态需清楚。管理端模块跳转只改标题，HTML 的弹窗有 Escape 关闭但缺显式焦点转移与返回。PNG 是同一概念稿的静态画面，响应式实测、键盘与色对比均 **UNVERIFIED**。HTML 21、25、29–33、38–50、53–64。

> 产品版去掉原型三端试切条和示例数字。用户端统一品牌与状态样式，普通端使用单一任务面板，移动端历史从折叠菜单进入，提问和风险在不滚动侧栏时可见。专业端默认突出设备、查看轮次、焦点与主要操作，进程、来源和完整答案从可返回的详情打开。区分「需要你回答」「本轮回答已保存」「材料不足」「正在取消」等状态。风险用文字、图标和等级共同呈现。管理端只呈现可读或可执行的真实能力，运营状态写明数据范围、时间和缺失。证据抽屉提供键盘、关闭和焦点回归；移动和窄屏可用同一个入口。

**改动影响。** 复用 ClaimList、ClaimDetail、EvidenceDrawer、RunTimeline、EvidenceComparison；专业端现行深色布局可逐步收敛语义色，无需像素复刻、图谱库或样式全量重做。后续验收覆盖窄屏、键盘及长内容。HTML 现有交互只演示提示与 DOM 切换，不用它评定后端效果。[现有证据组件](../../frontend/src/components/EvidenceDrawer.vue#L1)、[专业窄屏样式](../../frontend/src/pro.css#L198)。

### 第 8 章　实施顺序、Change 范围与成本（408–449 行）

**原表述与问题。** 「按依赖拆开」原则正确。pro-experience-stabilization 混合 UI、事务幂等和输出质量；admin-operations-foundation 同时承接账号、审计、额度、健康与运营；admin-knowledge-skill-assets 是三类互不相同的写入；consumer-guided-diagnosis 不宜把管理、档案、资料、提示词同时变成前置。

> R0 先修追问、证据与状态显示，随后补同键回执、活动轮和历史上下文，用最新浏览器复验。R1 的身份与 scope 传播是一个独立完整的安全交付，可按 HTTP、后台和流式链路分批实现，整体验收后开放。额度/审计和最薄管理查询可以依据已定身份合同并行开发。普通页面、资料准备和安全样本可在隔离开发环境同时推进。试点最小运行闭环通过后再逐步开放，其余管理写入按实际需求各自成项。

**改动影响。** UI 与只读列表成本相对低。幂等回执中等，涉及普通提交、补答、在途状态和并发。动态身份、跨 scope 共享知识、在途 Skill 版本、安全评测及真实资产治理成本高。AI Coding 可缩短适配与测试编写，现场核验、授权资料、真实供应商对照、故障注入和迁移演练仍是时间约束。以合同复杂度和验收证据报价；不把 39 条盘点估时相加。见 F 的阶段顺序。

### 第 9 章　验收与安全（453–482 行）

**原表述与问题。** 权限与可靠性列表覆盖大方向。仍需把待答、同键、并发多端、账号停用、撤回资料、在途 Skill 和恢复分别定义。历史 metrics 500 和历史双域真模不能替代当前运行状态。只读目录管理试点也应有验收闭环，无须临时补齐所有管理写操作。

> R0 按当前构建跑补答、失败/取消/窄屏引用、历史/活跃切换、事件终态与断线轮询、同键丢回执。R1 用两账号和一个管理员验证全资源隔离、旧会话/长连接撤权、后台重启重领、管理访问脱敏、额度并发、审计及备份恢复。普通端发布增加设备域许可、正确资料或未知输出、安全拒绝、用户可读性与移动操作。真实模型、测试 PG、脚本替身、演示资产、现场反馈分开记录。没有执行的用例保留未验证状态。

**改动影响。** 优先复用现有组件合同、PG、生命周期、恢复和授权回归，补上本次确定的缺口。安全验收确认实际控制生效，页面开关或用户确认不抵替失败。先测试已上线运营动作，后续添加知识/Skill/设备写操作时追加验收。[历史冻结记录](../../openspec/changes/devicetruth-pro-workbench-mvp/acceptance.md#L234)、[未执行任务](../../openspec/changes/devicetruth-pro-workbench-mvp/tasks.md#L54)。

### 第 10 章　风险与人工确认（486–507 行）

**原表述与问题。** 保留对图谱、全套计费、公开 Skill 脚本和通用 SQL 控制台的取舍。六个问题中单体/聚焦工作台/一账号两模式已有一致依据，人工关注应放在确实影响上线范围的决策。

> 人工集中确认三项。第一，R1 专业试点使用哪个设备域、哪些账号和哪些授权数据，确定运营是否必须经网页变更资料或 Skill。第二，R2 选择哪个普通咨询子场景，确定资料与安全验收负责人、用户转介边界以及是否急需档案或附件。第三，确定用户资料留存、删除、服务商传输与异常处理责任。共享 Agent、个人身份为起点、专业单焦点工作台和独立管理权限作为设计基线。

**改动影响。** 将决定项目成本的真实业务问题集中确认，认证、计量和隔离以工程验收证明。资料许可、现场资质和真实场景安全不由演示样例推断。

## F. 优化后的增量路线

| 顺序 | 建设内容 | 依赖及并行 | 验收门槛 |
| --- | --- | --- | --- |
| 1. R0 专业体验修复 | 复用现 App/Run/AgentResult/References/SSE。先修 F01、F02、F04 和 F08，将诊断状态、有效证据与可执行动作聚焦。说明失败/取消、等待、无结论及历史侧视图。 | 可立即开始。普通端状态视图、语料盘点和 R1 身份合同可独立推进。 | 最新制品实际覆盖完整追问、同轮补答、失败/取消引用、窄屏与上下文切换、等待/终态同步。保留既有双域样本。 |
| 2. R0 提交与恢复可靠性 | 在原消息入口实现同键回执或作用域内按键回查，统一普通提交、深入调查、补答的客户端待确定状态。落定多标签并发行为、引用继承时点与原文处理。 | 可与第 1 项并行做服务端，联合验收时使用同一客户端。沿用已有队列、租约、检查点。 | 失联后重试保持相同 Run/补答，改载荷得到冲突，两标签不会触发未定义行为。定向证明 RAG 登记异常与末步崩溃处理各自达到承诺，记录残余窗口。 |
| 3. R1 真实账号与作用域 | 最少新增账号认证与管理员授权，复用存量归属和 PostgreSQL。统一请求解析，改 Worker/Recovery/SSE 的执行范围及资源读取。从普通账号切换到另一个账号时清除私有会话定位、缓存与订阅。 | 从第 1 项开始即可并行。接口、后台、长连接可多批落地，整条链路通过才进入共享环境。资料先按账号隔离预置。 | 使用真实测试 PG 验证双账号隔离矩阵，覆盖文件、知识、资产、Skill、直接检索与运行引用。模拟进程重启、取消、撤权和过期 SSE。确认无固定 scope 回退。 |
| 4. R1 最薄运营和有界开放 | 新增原子账户准入和管理审计，开账户处置、脱敏 Run 查询、健康与额度观测。收紧上传/检索和流的资源开销，实际演练运维处置。管理网页逐步接入，运维流程已有能力可先交付。 | 需约定账号、归属和审计合同后并行。专业试点可在第 1–4 项通过后独立开始，知识、Skill、设备的管理页及普通 UI 均无强制依赖。 | 两位普通用户与管理员权限检查，配额并发占用/回收、审计原子性、备份恢复、供应商数据范围、记录查阅脱敏。确认实际 liveness/readiness 指标有效。 |
| 5. R2 单域普通用户发布 | 用普通视图复用 Run、追问、历史与证据。语言简化、状态优先、移动端风险和来源可见。准备一类合法且已验收的设备资料。 | UI 与资料样本可前置并行。同源真实联调依赖第 3–4 项；发布门槛还包括普通用户安全。用户默认模式切换延续历史。 | 对应域实际校验有资料/无资料/资料冲突、用户无法确认、长回应、危险及不合格输入与人工转介。独立识别模拟、真实外部模型和现场反馈。 |
| 6. 按实需扩展 | 先按实际使用频率增补知识发布治理、Skill 目录与启停、个人轻档案、设备数据授权导入、基础打印。后续才做多模态、工单、组织、在线模型更新、计费或跨轮结构化关联。 | 知识、Skill、资产分别有独立合同和历史数据边界，分别验收。PDF 可先验证文本文档检索需求，图片理解需要独立评估与授权输入。 | 先证明真实用户需要，具体功能通过权限、数据治理和运行回归后开放。 |

后续如另行授权进入 OpenSpec，可复用现有专业 Change 的验收与未完成项。交付范围建议按「专业交互回归」「提交回执与上下文」「账号隔离」「额度与审计」「最小运营读面」「普通文本咨询」推进。账号隔离内部允许分批，外部发布按整条安全链路验收。知识共享、Skill 管理与设备事实写入各自单列，避免一个 Change 同时引入多个数据治理模型。

## G. 最终审核结论

1. **可以作为最终产品设计的基础。** 用户端双模式、独立管理授权、保留单体和可追溯自主调查的定位与当前代码一致。第 5–6 章需要细化接口合同与授权边界，第 8 章需要调整发布条件。
2. **没有需要推翻重做的总体架构。** 第 4 章的管理首版范围需要缩减。第 8 章的试点依赖图应换成专业安全试点和普通域发布两条路线。普通端原型要由「同时展示输入、追问、回答」改为按实际 Run 状态组织，专业端保留现有工作台并修交互。
3. **优先修改五项。** ① 把请求、Worker/Recovery、SSE、Skill 和资源读取串成可验收的真实账号隔离链（V02-01）。② 修复 F01/F02，确保问题正文与证据详情可达（V02-04/05）。③ 补齐同键回执、活动轮冲突及资产/时点和 SSE 校准（V02-06–09）。④ 把普通端可安全自查、资料覆盖与转介写成发布门槛，区分授权资料和现场确认（V02-02/03/13）。⑤ 以原子额度、审计和脱敏运维作为试点中的管理核心，解除普通端与三类管理写入的强依赖（V02-10/11/12/14）。
4. **建议保留。** 统一的 Java/Spring Boot + Vue + PostgreSQL/pgvector；一个用户应用和独立管理入口；自然语言、不指定资产也可以咨询；ASK_USER 原轮恢复、主动深入调查开新轮；证据与安全语义原样可查；只读工具、租约、有限恢复与显式逐轮调查；演示与真实现场的区分；延后动态推理图、热装和完整计费。
5. **完成上述人工修订后适合进入 OpenSpec 规划。** 近期先规划专业体验与可靠性。真实受控试点前，账号/授权、配额、审计和资料边界必须能按 F 的条件独立验收。普通用户发布以首发域安全和资料条件为准。

### 本轮核验记录

| 核验 | 本轮执行及结果 |
| --- | --- |
| 版本和变更边界 | 使用 git branch --show-current、git rev-parse HEAD、git log -5 --oneline、git status --short、git diff --stat 和 git diff --cached --stat。初始存在 3 份未跟踪历史审核文件。本轮交付为当前报告。 |
| 方案、源码与原型 | 使用 rg --files、rg -n、Get-Content、Get-ChildItem 和 Import-Csv 定位与逐节复核。查看普通/专业/管理三个 PNG 及视觉总览。HTML 按 DOM、CSS 媒体规则和 JS 实现审阅。显式端点 32 个及 Flyway 17 张业务表已按注解与 DDL 重计，端点运行可用性 **UNVERIFIED**。 |
| 自动化与部署 | 本轮执行测试 0，通过 0，失败 0，错误 0，跳过 0。未起服务、未运行浏览器或模型、未修改数据库，最新浏览器交互、真实 PG/故障注入、当前 metrics 状态及供应商数据 **UNVERIFIED**。 |
| 历史数据（不可相加） | 专业冻结记录 402 总计，398 通过、0 失败、0 错误、4 真模门控跳过。11-18 能力盘点另一环境为 261 总计、245 通过、0 失败、14 环境错误、2 跳过；该报告排除指定 PG 用例后为 247 总计、245 通过、0 失败、0 错误、2 跳过，前端 Vitest 60/60。双域历史真模检索使用自制演示资料及模拟观测，设备实际现场、家电支持范围和生产资料许可 **UNVERIFIED**。[冻结记录](../../openspec/changes/devicetruth-pro-workbench-mvp/acceptance.md#L234)、[能力盘点执行记录](DEVICETRUTH_PRODUCT_CAPABILITY_AUDIT.md#L313) |
