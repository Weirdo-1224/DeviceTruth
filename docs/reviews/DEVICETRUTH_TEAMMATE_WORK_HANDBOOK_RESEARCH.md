# DeviceTruth 领域数据、RAG 知识库、Skill 建设与产品评审——技术现状与交接调查报告

- 生成日期：2026-11-27
- 生成方式：静态源码审查（未运行服务、未连接数据库、未修改任何代码/数据）
- 主要依据：`src/main/java/io/devicetruth/` 全部源码、`src/main/resources/db/migration/V1–V16`、`application-product.properties`、`frontend/src/`、`datasets/`、`skills/`、`docs/reviews/DEVICETRUTH_SKILL_INTEGRATION_AUDIT.md`（2026-11-27 专项审查，Skill 加载机制本文不重复展开，仅做差异核对）
- 事实级别标记：**【源码确认】**=代码路径完整存在；**【验证】**=有仓库内测试/验收记录证据；**【待运行验证】**=代码存在但本次未实机运行；**【未找到】**=全仓库检索后不存在
- 配套附录：`DEVICETRUTH_DATABASE_DICTIONARY.csv`（全字段字典）、`DEVICETRUTH_INTERFACE_INVENTORY.md`（接口/工具/错误码清单）

---

## 1. 执行摘要与当前项目状态

DeviceTruth 是一个 Java 21 + Spring Boot + PostgreSQL（pgvector）的"证据驱动设备检修 Agent"产品。产品 Agent 主链路已完整落地：会话 → run 状态机（QUEUED→RUNNING→WAITING_FOR_USER→COMPLETED/FAILED/CANCELLED）→ 有界循环调用只读工具 → 答案守卫（引用子集校验、风险校验、"无现场核验不得确诊"）→ SSE 实时事件流 → Vue3 工作台前端。**【验证】** GSD 状态（`.planning/STATE.md`，2026-11-14）：里程碑 v1.0 共 5 阶段 13 计划全部完成，全量测试 360/0/0/1、前端 36/36、部署演练 PASS，Phase 5 待两项人工 UAT 确认（多人共享真实认证、E2E-07 追问硬约束）。

对即将加入的领域开发者，最重要的五句话：

1. **知识库（RAG）是她可以完全自助的部分**：Markdown 文件上传（`POST /api/files`）→ 异步切片向量化 → 立即可被 `search_knowledge` 检索，全程不需要主开发者改代码。**【源码确认】**
2. **设备结构化数据目前没有导入 API**：`dt_asset*` 五张表只能靠运维开启夹具 Seeder（`devicetruth.assets.seed-fixtures=true`，默认关、默认灌 CAR-B）或手工 SQL 写入。她需要准备规定格式的数据交给主开发者导入。**【源码确认】**
3. **标准 Skill 是纯 Markdown**：按严格 frontmatter 规范写 `SKILL.md` + `references/*.md`，放 `skills/` 目录即可被系统加载；但最后"启用"需要主开发者在 `devicetruth.skills.installations` 配置追加 `skillId@version` 并重启。**【源码确认】**
4. **当前一切设备事实默认 SIMULATED**：`data_nature` 在配置/维修/观测/知识各表都有显式标记，观测表甚至被 CHECK 约束强制为 SIMULATED。所有示例数据均为模拟数据，不得对外宣称为真实设备数据。**【源码确认】**
5. **一个 run 只能激活一个 Skill，Skill 只能缩小工具权限不能放大**；没有运行时 Skill 管理 API、没有 Skill 脚本执行、没有 PDF 解析。**【源码确认】**

---

## 2. 产品功能清单与用户操作流程

### A. 产品功能清单

前端是单页工作台 `frontend/src/App.vue`（无路由，无 Mock 数据，全部走真实后端）。功能清单如下：

| 功能名称 | 前端入口 | 后端 API | 主要源码 | 实现状态 | 用户能完成的操作 | 当前限制 |
| --- | --- | --- | --- | --- | --- | --- |
| 新建/切换会话 | 侧栏"新调查"按钮、历史会话列表 | `POST/GET /api/conversations`、`GET /api/conversations/{id}` | `App.vue:400,407-421`；`AgentConversationController.java:27-41` | 【源码确认】 | 新建会话、按时间浏览历史、点击恢复任意历史会话（localStorage 记住当前会话 `App.vue:30`） | 会话无重命名/删除；无多用户 |
| 发起调查（自由文本） | 底部输入框 | `POST /api/conversations/{id}/messages`（带 `Idempotency-Key`） | `App.vue:499-525,302-324`；`GeneralAgentService.submit:246` | 【源码确认】 | 描述设备现象发起一次 run；可选关联设备、可选附带已上传的 Markdown 资料 | 提交后输入框锁定直到本轮结束；一次一条消息 |
| 关联设备 | 输入区"关联设备"按钮 → 依据面板资产页下拉 | `GET /api/assets`；下拉值随消息 `selectedAssetId` 提交 | `App.vue:510-512,568-578`；`AssetController.java:36` | 【源码确认】 | 从已登记设备清单选择一台设备关联到本次问题；可"不指定设备" | 设备清单只读，无设备增删改界面；不选设备时资产工具用不到 |
| Markdown 资料上传 | 输入区回形针按钮 | `POST /api/files`（multipart） | `App.vue:282-300,505-509`；`FileController.java:41`；`FileUploadService.upload:79` | 【源码确认】 | 上传 ≤1MB 的 `.md` 文件，显示导入状态（UPLOADED/PROCESSING/PARSED/FAILED/OCR_REQUIRED），PARSED 后随下一条消息作为检索线索（attachments） | 前端写死 `domain=general`、`version=1.0`（`api.ts:409-416`），无法在前端指定领域/型号/版本；仅 .md；PDF 上传后停在 OCR_REQUIRED |
| 多轮追问与补答 | 待答卡片 | `POST /api/conversations/{id}/messages`（带 `replyTo`） | `App.vue:487-493,327-351` | 【源码确认】 | Agent 提出待答问题时输入补充信息，沿同一 run 继续调查 | 补答必须逐条等待；问题版本不符会 409 |
| 调查过程实时展示 | 消息区下方 RunTimeline + "查看完整运行记录" | SSE `GET /api/runs/{id}/stream`（Last-Event-ID 断线续传、seq 去重、轮询兜底） | `App.vue:103-138`；`timeline.ts`；`RunTimeline.vue`；`RunEventStreamService` | 【验证】（05-01 复验 12 项落盘） | 看到排队/执行/等待状态、最近 3 条事件、工具调用与模型尝试的脱敏轨迹 | 事件是白名单安全视图，看不到 prompt 与模型原文 |
| 取消调查 | 顶栏"取消调查"按钮 | `POST /api/runs/{id}/cancel` | `App.vue:159-173` | 【源码确认】 | 取消排队/等待中的 run；执行中协作式收尾 | 终态后按钮消失 |
| 诊断结果展示 | 终态后 DiagnosisReport 卡片 | `GET /api/runs/{id}`（result） | `App.vue:494-496`；`DiagnosisReport.vue` | 【源码确认】 | 查看答案摘要、确定性分级声明（claims）、建议、不确定性、局限、风险等级与安全处置提示 | 结果保存在会话内，无导出/分享/收藏按钮 |
| 证据查看（依据面板） | 顶栏"依据"按钮 → 三个页签 | `GET /api/runs/{id}/references`、`GET /api/assets/{assetId}` | `App.vue:528-586`；`EvidenceComparison.vue` | 【源码确认】 | 查看本次调查登记的全部引用（来源/版本/章节/摘录，可逐条展开）、相关资产的配置与维修记录对比、完整运行事件 | 引用只读；资产对比是该设备的整体视图，不是 run 证据 |
| Skill 展示 | run 元信息行"领域包 xxx v1.0.0" | `GET /api/runs/{id}/skill` | `App.vue:140-156,442` | 【源码确认】 | 看到本 run 自动/手动激活的 Skill 名称与版本 | 无 Skill 选择、安装、管理界面；只能看 |
| 设备事实直查（运维向） | 前端无入口（仅依据面板间接展示） | `GET /api/assets*` | `AssetController.java` | 【源码确认】 | （curl 级）直查设备配置/维修事实 | 未挂 run 不登记引用；无前端页面 |
| 知识检索直查（运维向） | 前端无入口 | `POST /api/knowledge/search` 等 | `KnowledgeController.java` | 【源码确认】 | （curl 级）以 runId 身份检索知识库、读原文 | 必须挂 runId |
| legacy 场景诊断演示 | `src/main/resources/static/index.html` | `/api/sessions`、`/api/diagnoses` | `ConversationController.java`；`DeviceTruthApplication`（CLI） | 【源码确认】 | 用预定义场景 A–D 跑 legacy 诊断 | 与产品 Agent 是两条独立链路，新工作不在这里 |
| 知识库管理界面（文档列表/删除/替换） | **未找到** | **未找到** | — | 未实现 | — | 只能上传+查状态；无文档管理 UI |
| Skill 管理界面 | **未找到** | **未找到** | — | 未实现 | — | 只能看 run 已选的 Skill |
| 设备管理界面（登记/编辑设备） | **未找到** | **未找到** | — | 未实现 | — | 设备数据只能靠 Seeder/手工 SQL |

### B. 主要用户旅程

**① 新用户首次使用 【源码确认】**
已支持：打开页面 → 自动创建/找回会话（`App.vue:353-380`）→ 直接输入设备现象 → （可选）关联设备、上传资料 → 发送 → 看实时进度 → 看答案与引用。
未实现：注册/登录、选择项目、新手引导、示例问题推荐。

**② 设备故障诊断 【源码确认/待运行验证】**
已支持：描述现象（可关联设备）→ Agent 激活匹配的 Skill（scripted 模式按 tasks 关键词，qwen/openai 模式模型自主 `activate_skill`）→ 依次调用 `get_asset`/`get_configuration`/`get_maintenance_history`/`get_observations`/`search_knowledge` → 需要时 ASK_USER 转 WAITING → 用户补答 → 继续 → ANSWER 经 AnswerGuard 校验后落库 → 前端展示分级结论与引用。
未实现：确诊（系统结构性禁止——无时点内 FIELD_VERIFIED 记录不得出现确诊表述，`AnswerGuard`）；真实观测接入；跨 Skill 调查。

**③ 设备历史信息查询 【源码确认】**
已支持：依据面板"相关资产"页签选择设备 → EvidenceComparison 展示该设备的配置记录（来源/有效期/核实状态/冲突标记）与维修记录（before/after）。
未实现：按时间轴回放、导出台账、多设备对比。

**④ 知识文件上传与检索 【源码确认】**
已支持：上传 .md（≤1MB）→ 异步导入（队列容量 1 并发/4 排队，`application-product.properties`）→ 状态可查可重试 → PARSED 后随消息作为 attachment 进入 run 检索范围。
未实现：PDF/DOCX 解析（PDF 停 OCR_REQUIRED）、文档删除/替换、前端指定 domain/deviceModel/version（写死 general/1.0）、批量上传。

**⑤ Skill 激活与使用 【源码确认】**
已支持：Skill 随默认配置安装（`automotive-diagnosis@1.0.0`、`factory-equipment@1.0.0`）→ run 内由模型或 scripted 关键词匹配激活 → 正文+references 注入推理上下文 → 顶栏显示"领域包"徽章 → `GET /api/runs/{id}/skill` 可查版本与适用范围。
未实现：用户手动选 Skill、Skill 管理界面、运行时装卸、多 Skill/切换。

**⑥ 多轮补证与继续调查 【源码确认/验证】**
已支持：Agent ASK_USER → 前端待答卡片 → 补答带 `replyTo{runId,pendingQuestionId,questionVersion}` 续跑同一 run（PG 精确重建本 run 消息序列，V5 迁移）；断线/刷新后按持久状态恢复（刷新自动重新跟踪 QUEUED/RUNNING，`App.vue:204-216`；服务重启后租约恢复 `RunRecoveryService`）。
未实现：用户主动"继续深挖同一 run"（追问即开新 run）、分叉会话。

**⑦ 诊断结果查看 【源码确认】**
已支持：终态报告卡（摘要/claims 三档确定性/建议/不确定性/局限/风险与安全提示）、引用逐条展开（标题/章节/版本/SIMULATED 标记）、运行记录回放、历史会话找回。
未实现：导出（PDF/Markdown）、分享链接、结果对比、收藏。

### C. 产品体验评审建议（8 个可执行任务）

以下任务全部可用当前实现执行，无需等待新功能。执行环境：本地起 product 服务（需 PG + 模型配置；scripted 推理器可离线跑通核心链路，真实开放调查需 qwen/openai）。

| # | 用户身份 | 测试目的 | 前置条件 | 操作步骤 | 预期结果 | 需要观察的问题 | 验收判断标准 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T1 | 普通用户 | 首次使用零门槛 | 服务已启动，浏览器无痕窗口 | 打开页面→直接输入"我的设备最近总是停机"发送 | 自动建会话；出现状态徽章与进度；终态出报告 | 空状态文案是否看得懂；发送后是否知道在等什么；失败提示是否人话 | 10 分钟内无需文档完成一次问答；失败时有稳定错误码与可读说明（`api.ts:146-169` 白名单） |
| T2 | 设备检修人员 | 关联设备后调查 | 数据库已灌入 SC-0245-B 夹具 | 关联设备 SC-0245-B→提问"CTRL-01 当前装的是什么模块版本？" | Agent 查到三条配置记录（出厂 D-120A/技改 D-120B/模板 D-120A），给出 UNCONFIRMED 与冲突提示 | 冲突解释是否清楚；是否引导现场核验而非直接下结论；引用能否点开 | 答案不得确诊；`installationStatus=UNCONFIRMED`、`conflict=CONFLICT` 被如实转述；引用含 FACTORY/CONFIGURATION 来源 |
| T3 | 设备检修人员 | 多轮补证闭环 | T2 run 处于 WAITING | 在待答卡片回答 Agent 的追问 | 同一 run 继续；新证据进入答案；历史消息顺序正确 | 补答后上下文是否丢失；是否会话串台 | `dt_message.reply_to_run_id` 挂接正确；最终答案引用了补答内容 |
| T4 | 检修班组长 | 证据可审计 | 任一已完成 run | 打开"依据"面板逐条展开引用→切"运行记录"页签回放 | 每条引用有标题/版本/章节/摘录；事件链完整 | 引用摘录与结论是否对得上；SIMULATED 标记是否醒目 | 答案中每个 claim 的 referenceId 都能在引用列表找到（AnswerGuard 已强制，验证展示层是否完整呈现） |
| T5 | 资料管理员 | 上传手册并验证检索 | 有一份 <1MB 的 Markdown 手册 | 回形针上传→等待 PARSED→提问相关内容 | 上传状态可见；答案引用到手册章节 | 前端无法选 domain 是否造成检索噪声；导入失败提示是否可行动 | `GET /api/files/{id}` 状态最终为 PARSED；答案引用 `sourceType=KNOWLEDGE_DOCUMENT` 且章节正确 |
| T6 | 产品经理 | 取消与恢复 | 一个 RUNNING 中的 run | 点"取消调查"→刷新页面→查看会话历史 | run 转 CANCELLED；历史会话中状态正确 | 取消后界面残留；恢复后事件是否乱序 | `dt_agent_run.status=CANCELLED`；事件 seq 连续无重复 |
| T7 | 检修人员 | 上传不支持的文件 | 准备一个 PDF 或 >1MB 的 md | 回形针选择该文件 | 明确报错或不进入解析 | 错误提示是否说明原因与下一步 | PDF 停在 OCR_REQUIRED 且页面说明原因；大文件得到 413 提示 |
| T8 | 领域开发者 | Skill 生效验证 | Skill 已被主开发者启用 | 用与 Skill `tasks` 关键词匹配的问题发起 run | 顶栏出现"领域包 xxx"徽章；调查顺序符合 references 指引 | 模型是否遵守 evidence/risk/citation 约束；references 是否被实际读取（`dt_run_skill_reference` 有钉存记录） | `GET /api/runs/{id}/skill` 返回正确版本与 scope；`dt_run_skill_reference` 出现读取记录；答案引用全部来自工具取回记录 |

*需未来功能支持（单独标记）：真实设备接入验证（依赖未来采集通道）、多 Skill 协作、结果导出、PDF 知识导入、多用户认证。*

---

## 3. PostgreSQL 完整数据字典与业务数据需求

### 3.1 表清单与三类划分

完整字段级字典见附录 `DEVICETRUTH_DATABASE_DICTIONARY.csv`（17 张表、全部列、约束、读写方）。此处给出表级视图：

**A. 领域数据表（队友建设维护）**

| 表 | 业务用途 | 对应 Java 访问代码 | 写入方 | 数据来源 |
| --- | --- | --- | --- | --- |
| `dt_asset` | 设备身份登记（仅 ID+归属，不存描述字段） | `AssetRegistryRepository`（`adapter/persistence/AssetRegistryRepository.java`）；快照模型 `Repositories.AssetSnapshot` | Seeder/运维 | 需人工准备 |
| `dt_asset_component` | 设备的部件清单（部件 ID+显示名） | 同上 | Seeder/运维 | 需人工准备（从 BOM/台账提取） |
| `dt_asset_configuration` | 配置事实时间线（出厂/技改/模板/确认，含来源、有效期、核实状态、权威等级） | 同上；工具 `AssetTools.configuration` 读取 | Seeder/运维 | 需人工准备 |
| `dt_asset_maintenance` | 维修/更换事实（before/after、来源、核实状态） | 同上；工具 `AssetTools.maintenance` 读取 | Seeder/运维 | 需人工准备 |
| `dt_asset_observation` | 模拟观测数据（信号/值/质量；**CHECK 强制 SIMULATED**） | `ObservationTools.query`（JdbcTemplate 直读，`ObservationTools.java:77-87`） | 主代码**无写入通道**；测试用 SQL 直插（`DualDomainAgentTest.java:115`） | 需人工准备（模拟） |

**B. 知识库相关表（系统导入流程管理，队友只通过上传接口写入）**

`dt_uploaded_file`（受控文件+导入状态）、`dt_knowledge_document`（文档元数据与 embedding 档案）、`dt_knowledge_chunk`（切片+1024 维向量）、`dt_knowledge_document_file`（文件↔文档多对一关联，V7）。访问代码：`FileUploadService`、`KnowledgeImportService`、`KnowledgeSearchService`、`KnowledgeChunker`、`DocumentParser`。

**C. 系统运行与权限表（队友不可直接修改）**

`dt_conversation`、`dt_message`、`dt_agent_run`、`dt_run_event`（会话与 run 全生命周期）、`dt_evidence_reference`（证据引用登记）、`dt_skill_installation`（Skill 安装授权）、`dt_run_skill`、`dt_run_skill_reference`（run 级 Skill 选择与读取钉存）。

**核对结论**：Flyway V1–V16 共 17 张表，任务书列举的五张领域表 + 三张知识表 + 系统表之外**未发现遗漏的表**。**【源码确认】**

### 3.2 领域数据需求分析（逐表）

通用时点语义（V8 注释明确）：`valid_from/valid_to` 是记录在适用业务时点上的有效性区间（`valid_to` 空=未失效）；`recorded_at` 是记录进入系统的时刻，**不参与时点过滤**（防"资料最新"伪装现场状态）；`observed_at` 仅现场核验类记录存在；`verification_status` 只有 `source_type=FIELD_CONFIRMATION 且 observed_at 非空` 才是 FIELD_VERIFIED（`AssetSnapshotImporter.verification:78-81`）。

**dt_asset / dt_asset_component**
- 需要什么：设备业务编号（如 SC-0245-B、CAR-B）；部件 ID（如 CTRL-01）与显示名。
- 现实来源：设备台账、铭牌、资产管理系统导出。
- 提取资料：设备档案表、BOM 清单。
- 必须人工核实：设备编号与现场铭牌一致；部件 ID 命名规范（`^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$`，`Checks.id`）。
- 可程序转换：从 BOM JSON/Excel 批量生成。
- 不能 AI 推测：设备是否存在、编号是什么——没有就是没有。
- 缺数据影响：`get_asset` 返回组件清单；资产未登记则 `get_configuration` 等全部 ASSET_NOT_FOUND，整个调查链断裂。
- 完整性判断：`GET /api/assets` 能列出；`get_asset` 返回非空 componentIds。

**dt_asset_configuration**
- 需要什么：每条事实 = 部件+属性+取值+来源类型+来源单据 ID+来源引用+生效区间+权威等级+核实状态。
- 特别关注点：
  - **出厂 vs 当前**：出厂 FACTORY 记录应带 `valid_to`（被技改关闭）；当前生效记录 `valid_to` 为空。
  - **技改批准 vs 实施**：两者都是 `source_type=CONFIGURATION`（模板/方案）还是 `REPLACEMENT`（实施）必须区分；权威等级体现证据强度（出厂 10 > 站点模板 5，示例见 `sc-0245-b.json`）。
  - **不能 AI 推测**：`value`、`valid_from`、`source_id` 必须来自单据；`verification_status` 由导入程序按来源类型机械推导，**不允许人工填 FIELD_VERIFIED  unless 有 FIELD_CONFIRMATION+observed_at**。
  - **冲突与核实**：同组（component+attribute）最高权威取值分叉 → 工具输出 `conflict=CONFLICT`（`AssetTools.conflict:301-304`）；无 FIELD_VERIFIED 时 `installationStatus=UNCONFIRMED` 并生成"需现场核验"提示（`requiredChecks:344-358`）。
- 缺数据影响：`get_configuration` 是技改追溯调查的核心工具；缺来源 ID 会导致幂等键冲突或重复导入被静默丢弃（V10/V11 唯一键 `source_id+component+attribute+valid_from`）。
- 完整性判断：同一来源可含多条事实但不丢行（V10/V11 就是为修这个）；用 `GET /api/assets/{id}/configuration` 抽查时点过滤正确。

**dt_asset_maintenance**
- 需要什么：部件+操作类型（如 REPLACEMENT）+ before/after+来源+生效时刻。
- 特别关注点：**维修前后部件对应关系**靠 `component_id + before_value/after_value` 表达（如 D-120A→D-120B）；更换记录"说明发生过更换，不等同于当前已现场确认"（`AssetTools.limitations:360-362`）。
- 不能 AI 推测：工单号（source_id）、更换前后型号。
- 缺数据影响：`get_maintenance_history` 查空 → Agent 无法建立"何时换过什么"的时间线。
- 完整性判断：before/after 至少其一非空（schema 允许双空，但业务上应至少一个）。

**dt_asset_observation**
- 需要什么：信号名、值、单位、来源 ID、质量等级、观测时刻。**schema 强制 SIMULATED**，真实现场核验必须走 `dt_asset_configuration` 的 FIELD_CONFIRMATION 路径。
- 特别关注点：观测**不参与当前装机确认**（V15 注释）；时间范围不得超出 run 的 asOf（`ObservationTools.java:66-67`）；无数据时系统明确"未读取到现场数据"。
- 不能 AI 推测：观测值本身。
- 缺数据影响：`get_observations` 返回 NO_MATCHING_DATA/NOT_INTEGRATED——对充电排查类 Skill 是重要缺口，答案必须如实说明。
- 完整性判断：信号命名稳定（同义信号名会查不到）；时间戳用 UTC。

**跨领域复用性**：五张表无任何领域枚举约束（除来源类型/核实状态），`attribute`/`operation`/`signal` 都是自由文本——同一 Schema 可直接复用于汽车、工业、充电设备；但**属性命名规范需要在团队层面约定**（如 `moduleVersion` vs `模块版本`，工具参数 `attribute` 只允许 ASCII 标识符，`ATTRIBUTE_PATTERN` `AssetTools.java:54`），中文属性名将无法查询。

### 3.3 数据导入与维护能力

| 能力 | 现状 | 依据 |
| --- | --- | --- |
| 设备数据导入 API | **未找到** | 全仓库无写 `dt_asset*` 的 Controller |
| Fixture/Seeder | 【源码确认】有：`AssetFixtureSeeder`（`application/agent/AssetFixtureSeeder.java`）+ `AssetSnapshotImporter`，从打包的 `datasets/v1/factory/{asset}.json` 或 `datasets/fixtures/{a-d}/asset.json` 派生快照幂等灌库；显式开启才执行（`ProductConfiguration.java:164-175`，`devicetruth.assets.seed-fixtures=false` 默认，fixtures 默认 `CAR-B`） | |
| 测试数据写入方式 | 测试直接用 JdbcTemplate 插 SQL（如 `DualDomainAgentTest.java:115` 插观测） | |
| 批量导入 | 仅 Seeder 的多资产逗号列表；无通用批量 API | |
| 更新/删除 | **未找到**任何更新/删除设备数据的代码路径；Seeder"只插入缺失记录，不删除、不改写"（`AssetFixtureSeeder` 类注释） | |
| 事务/约束/重复保护 | 【源码确认】复合外键防跨归属串接；V10/V11 唯一键到"来源+部件+属性+生效时点"粒度，重复导入幂等不丢历史；导入走 TransactionTemplate（`KnowledgeImportService`） | |
| 项目/用户隔离 | owner/project 是服务器单身份常量，**不是**真正的多租户；隔离机制已预留（所有表带归属+复合外键） | |
| 可供队友安全使用的导入机制 | 知识文档：可自助上传；设备数据：无——需主开发者执行 Seeder 或 SQL | |

**推荐数据整理格式（准备格式，非系统导入格式）**：与 `AssetSnapshotImporter.fromEvidence` 的输入对齐的 JSON（每条记录对应一条证据事实）：

```json
{
  "dataNature": "SIMULATED",
  "assetId": "SC-0245-B",
  "records": [
    {
      "evidenceId": "SC0245B-RETROFIT-2025",
      "assetId": "SC-0245-B",
      "sourceType": "REPLACEMENT",
      "sourceRef": "simulated:SC-0245-B/WO-RETROFIT-2025-0915",
      "recordedAt": "2025-09-22T08:30:00Z",
      "effectiveAt": "2025-09-15T00:00:00Z",
      "observedAt": null,
      "validTo": null,
      "authority": 10,
      "payload": {"subject": "CTRL-01", "predicate": "moduleVersion", "value": "D-120B"},
      "integrityStatus": "VERIFIED"
    }
  ]
}
```

> 明确：这是**队友交给主开发者的准备格式**（直接对应现有 Seeder 可读的 fixture 结构），当前系统没有接收此 JSON 的 HTTP 接口。交接方式 = 队友产出 JSON/CSV + 来源说明 → 主开发者 review 后通过 Seeder 配置或 SQL 灌入。
> 最小可行导入协作方式（不写代码）：①队友按上格式整理数据文件放 `datasets/v1/{domain}/`；②主开发者把资产加入 `devicetruth.assets.fixtures` 并临时开 `seed-fixtures=true` 启动一次；③用 `GET /api/assets/{id}/configuration` 核对；④关闭 seed 开关。（本次调查未执行任何写入。）

### 3.4 SC-0245-B 场景示例（全部 SIMULATED）

`datasets/v1/factory/sc-0245-b.json`（XLP-200 产线控制器 CTRL-01 模块版本追溯）演示了全部五类表达方式：

| 业务概念 | 数据表达 |
| --- | --- |
| 设备档案 | `dt_asset`(SC-0245-B) + `dt_asset_component`(CTRL-01) |
| 出厂 BOM | `source_type=FACTORY`，valid 2024-03-12→2025-09-15，value=D-120A，authority=10 |
| 技改实施（维修记录） | `source_type=REPLACEMENT` → 同时生成配置事实与维修记录（before null/after D-120B），工单号 WO-RETROFIT-2025-0915 |
| 技改后仍生效的站点模板 | `source_type=CONFIGURATION`，valid_from 2026-02-01，value=D-120A，authority=5（**与技改记录冲突，且无现场核验 → UNCONFIRMED+CONFLICT**） |
| 现场确认 | schema 支持（FIELD_CONFIRMATION+observed_at → FIELD_VERIFIED），但该 fixture 的三个变体（`variants/` 下 field-verified/field-contradiction/three-source-conflict）专门演示此路径 |
| 观测 | 不在此 fixture；观测需另行准备 SQL/未来通道 |
| 关联 RAG | 技改方案原文、模块手册应整理为 Markdown 走 `POST /api/files`（domain 建议 `factory`），使 `search_knowledge` 能引用方案原文 |

**Schema 缺口**：设备表只有 ID 没有型号/位置/厂商等描述字段（型号目前只存在于知识文档元数据 `device_model` 与 fixture 文本中）；无"维修工单"独立实体（工单以 source_id 字符串存在）；观测与资产是松耦合（无外键到部件）。这些缺口如要补需主开发者新增迁移。

---

## 4. 设备数据整理、导入和维护方式（操作视角）

**"我要准备一条设备数据，具体做什么？"**

1. 确认设备已登记：`GET /api/assets` 查 assetId；没有则先准备 `dt_asset` + `dt_asset_component` 行。
2. 每条事实填写七要素：部件 ID、属性名（ASCII）、取值、来源类型（FACTORY/REPLACEMENT/CONFIGURATION/FIELD_CONFIRMATION/FIELD_OBSERVATION/USER_STATEMENT）、来源单据号（source_id，全局稳定唯一）、生效区间（valid_from/valid_to）、权威等级（团队约定刻度）。
3. 来源引用 `source_ref` 写可追溯字符串（真实：`authorized:...`；模拟：`simulated:...`），`data_nature` 如实标 SIMULATED/AUTHORIZED。
4. 现场核验记录必须 `source_type=FIELD_CONFIRMATION` 且带 `observed_at`；其余一律 DOCUMENTED（系统会机械校验，造假会被 CHECK 拒绝）。
5. 交付：JSON（推荐，与 fixture 同构）+ 每条的来源说明（哪份资料的哪一页）→ 主开发者导入 → 用 `GET /api/assets/{id}` 组合视图核对 installationStatus/conflict 是否符合预期 → 跑一次真实 run 验证 `get_configuration` 能查到、引用能登记。

**维护规则**：当前无更新/删除通道；数据错了 = 准备修正版事实（新 source_id 或新 valid_from）交主开发者评估，由主开发者决定迁移策略。Seeder 幂等但不修改既有行。

---

## 5. RAG 知识库架构、资料规范与操作流程

### 5.1 文件上传与导入 【源码确认】

- **支持类型**：`.md`（MIME 必须 text/markdown 或 text/x-markdown）；`.pdf` 可被"接受上传"但立即置 `OCR_REQUIRED/PDF_NOT_SUPPORTED`，不解析不切片（`FileUploadService.upload:83-86,122-128`）。TXT 仅能通过 `POST /api/knowledge/documents` JSON 接口（mimeType=text/plain）。
- **上传接口**：`POST /api/files` multipart，字段 `file/title?/domain(默认general)/deviceModel?/version(默认1.0)/dataNature(默认SIMULATED)`。
- **限制**：单文件 ≤1MB（Spring multipart 与表约束双重限制）；UTF-8 严格解码，非法字节 400 `INVALID_TEXT_ENCODING`（`DocumentParser.parseMarkdown:10-20`）；内容 ≤40 万字符；队列 1 并发/4 排队，满则 `IMPORT_QUEUE_REJECTED`。
- **异步**：是。状态机 UPLOADED→PROCESSING→PARSED/FAILED/OCR_REQUIRED；`GET /api/files/{fileId}` 查询；`POST /api/files/{fileId}/retry` 重试（仅 FAILED/OCR_REQUIRED）。
- **元数据**：domain/deviceModel/version/dataNature 在**上传表单**上设置（前端 UI 未暴露 domain/deviceModel，走 API 可设）。
- **去重/复用**：同归属同 checksum 同版本 → 复用既有文档（新文件行挂到同一 document，`dt_knowledge_document_file`）；元数据不一致 409 `DOCUMENT_METADATA_CONFLICT`；NEEDS_REBUILD 状态 409。
- **替换/删除**：无删除；替换 = 以新 version 重新上传（旧版本仍存，检索命中旧版需用 filters.documentId+版本管理策略——实际检索总是命中 READY 且档案匹配的最新导入，旧版本 document 依然 READY 也会被召回，团队需约定版本号或退役流程）。
- **权限隔离**：owner/project 服务器单身份；知识检索归属过滤在召回前（`KnowledgeSearchService.scopeSql:194-206`），跨归属查不到（评测用例 `acl-cross-owner/project` 验证零泄漏）。

### 5.2 文档解析与索引 【源码确认】

- **解析**：Markdown 按标题层级（`#{1-6}`）构建章节路径，纯文本按段落；`KnowledgeChunker.PROFILE = section-paragraph-v1`。
- **Chunk 策略**：按 `devicetruth.knowledge.max-chunk-chars`（默认 480，上限 4000）聚合段落；每个 chunk 带 `chunk_no`、`section`（标题路径，保证"页码或章节至少其一"可定位）；MD/TXT 导入 page 恒为 NULL。
- **Metadata 存储**：文档级在 `dt_knowledge_document`（title/domain/device_model/doc_version/embedding 档案/chunk_count/data_nature/ingest_status/checksum）；切片级在 `dt_knowledge_chunk`。
- **Embedding**：默认 `openai` provider + DashScope 兼容端点 + `text-embedding-v4`，1024 维 cosine（`application-product.properties`）；档案标签 `provider:model@dimension-cosine-r<revision>`；**维度与索引列不一致直接 409，换模型必须整体重建**（`KnowledgeIndexSchema.vectorDimension` 校验）；`deterministic` provider 为离线确定性向量（测试用）。API key 只走环境变量。
- **混合检索流程**（`KnowledgeSearchService.searchChannel:90-153`）：HYBRID = 向量候选（HNSW cosine，≥min_similarity 0.45，候选数=max(20, limit×4)）+ 关键词候选（ILIKE 词项覆盖，中文按二元组切）→ 去重合并 → 关键词覆盖 <0.20 且相似度不足者剔除 → RRF 融合（`1/(60+vectorRank)+1/(60+keywordRank)` + 部件号精确命中 +0.05）→ 排序取 limit → **同事务登记 `dt_evidence_reference`**。VECTOR/KEYWORD 单通道仅供评测（`mode` 参数）。
- **版本管理**：doc_version 显式字段；同 checksum 同版本复用；V4 遗留向量统一隔离 NEEDS_REBUILD（重建方式=用新版本重新导入同一内容）。
- **原文引用**：每个 hit 自带 referenceId/chunkId/section；`get_document_excerpt` 只能读本 run 已取回片段（按 run+chunk 校验）。
- **检索不到**：返回 `empty=true`，工具层不报错；Agent 应如实说明"资料库中没有可定位的相关内容"，不得编造。

### 5.3 队友实际操作流程（RAG）

```
原始资料(PDF/DOCX/纸质/网页)
  → 来源登记：标题/版本/发布方/适用型号/日期/真实或模拟(台账 Markdown 记录)
  → 分类：决定 domain 与 deviceModel 标签；判断是否含设备事实(应转结构化数据而非入库)
  → 清洗与转换：转 Markdown(保留章节标题层级!引用定位依赖 section)；<1MB 超限拆分多文件
  → 调用 POST /api/files 上传(file+title+domain+deviceModel+version+dataNature)
  → 轮询 GET /api/files/{fileId} 至 PARSED
  → 建索引：系统自动(异步)；核对 GET /api/knowledge/documents/{documentId} 的 chunkCount/ingestStatus
  → 检索验证：POST /api/knowledge/search {runId, query, filters:{domain,deviceModel}}
  → 引用验证：命中 hit 的 section 是否对应原文正确章节；GET .../references/{referenceId}?runId= 读回原文核对
```

> 全部为真实存在的 API；前端 UI 只覆盖其中"上传+状态"两步，其余用 curl/HTTP 工具完成。PDF 需先人工转 Markdown（转换质量与原文一致性由队友负责，系统不 OCR）。

---

## 6. Skill / RAG / PostgreSQL 的职责边界及示例

**分类规则**：Skill 回答"怎么查"（流程、顺序、证据要求、安全边界、引用规范）；RAG 回答"资料怎么写"（可检索、可引用原文）；PostgreSQL 回答"这台设备实际发生过什么"（结构化、可时点过滤、带核实状态的事实）。

| # | 内容案例 | 归属 | 理由与关联方法 |
| --- | --- | --- | --- |
| 1 | 设备操作手册（充电系统检修手册） | RAG | 可引用原文；`datasets/knowledge/charging-system-manual.md` 即此定位；设 domain=automotive、deviceModel |
| 2 | 出厂 BOM 表 | **PG（配置事实）+ RAG 二选一** | 结构化事实（哪台设备哪个部件什么型号）进 `dt_asset_configuration`（source_type=FACTORY）；若 BOM 还要作为"资料原文"被引用，可再入库一份 Markdown，两者用 sourceRef/文档标题互相关联 |
| 3 | 某台设备的技改事件 | PG 为主 | 事件=部件+前后值+工单号+生效时间，是 `get_maintenance_history` 的输入；技改**方案原文**入 RAG |
| 4 | 历史维修工单 | PG（事实摘要） | before/after/时间/来源进 `dt_asset_maintenance`；工单扫描件如需原文引用入 RAG |
| 5 | 厂家备件兼容表 | RAG | 无结构化工具能算兼容性；作为可检索文档供 search_knowledge 引用（或写进 references 决策规则，但确定性较弱） |
| 6 | 通用故障排查方法 | Skill references | "先查什么后查什么"是调查指引；`skills/automotive-diagnosis/references/charging-troubleshooting.md` 即此 |
| 7 | 现场观测数据 | PG `dt_asset_observation`（仅 SIMULATED） | 工具 `get_observations` 直读；不进 RAG（检索相似度不等于设备状态） |
| 8 | 安全操作规定 | Skill references + RAG | 边界约束（高压作业资质）进 references 的 risk，被注入模型约束答案；厂商安全规程原文入 RAG 可引用 |
| 9 | 专家诊断经验总结 | Skill references | "经验"是判断规则不是事实记录；写成决策规则文本供 `read_skill_reference` 按需读取 |
| 10 | 故障调查测试场景 | 仓库 `datasets/`（JSON） | 测试资产不属于任何运行时数据层；供验收与回归 |
| 11 | 一份工单中的结构化事实+技术分析原文 | 拆分：事实→PG，原文→RAG | 同一原始资料允许拆分；关联方法：PG 的 source_ref 写工单号，RAG 文档标题/章节注明工单号 |
| 12 | 站点默认参数模板（旧模板写旧型号） | PG（CONFIGURATION 事实） | 它是"资料记载的配置"，authority 应低于 FACTORY/REPLACEMENT，用于演示冲突 |

**同一资料拆分层时的铁律**：进入 PG 的事实必须能指回来源单据（source_id/source_ref）；进入 RAG 的原文必须保留章节结构；Skill 里**只写方法不写事实**（Skill 内容本身不能作为答案引用来源，`AgentConversationController.java:53-57` 只公开版本与适用范围）。

---

## 7. 现有 Skill 开发与集成规范

### 7.1 与专项审查报告的差异核对

本次逐条复核 `DEVICETRUTH_SKILL_INTEGRATION_AUDIT.md`（2026-11-27）与当前源码，**未发现不一致**；补充以下审查报告未展开的细节：

- `tools:` 声明的实际生效机制：激活时取 安装许可 ∩ 服务器白名单(`devicetruth.skills.allowed-tools`) ∩ metadata.tools ∩ 会话基线 四重交集（`SkillRuntime`）；默认白名单 6 个工具**不含** `generate_diagnosis_report`——Skill 想用报告工具需主开发者改白名单。
- `SkillRuntimeIntegrationTest`（`src/test/java/io/devicetruth/application/`）覆盖激活/权限交集/变更拒绝；`SkillRegistryTest`、`SkillContextLoaderTest`（`src/test/java/io/devicetruth/skill/`）覆盖加载与 references 读取约束；`SkillFixtures` 提供测试夹具。
- 前端 `readRunSkill` 对 404 `SKILL_NOT_SELECTED` 归一为 null（`api.ts:522-529`），领域开发者自查时用 curl 才能看到原始错误码。

### 7.2 一名不懂 Java Core 的开发者能否独立完成标准 Skill？

**能。** 证据：Skill 是纯 Markdown；加载（`SkillRegistry`）、解析（`SkillManifestParser`）、激活、references 读取、工具注入全部通用已实现；新增 Skill 零 Java 改动。唯一需要主开发者的两点：①把 `skillId@version` 加进 `devicetruth.skills.installations` 并重启；②若需要白名单外工具或新结构化工具。

### 7.3 可执行的 Skill 开发、交付、测试规范

**开发**：
1. 建目录 `skills/<skill-id>/`（skill-id = 小写字母数字连字符，必须等于目录名）。
2. 写 `SKILL.md`：frontmatter 严格 9 键——`name/description(≤1000)/version(x.y.z)/devices(≥1,≤32)/tasks(≥1,≤32)/tools/evidence(≥1)/risk(≥1)/citation(≥1)`；只支持顶层标量 + 两空格缩进 `- ` 列表；禁止注释/制表符/嵌套/行内列表；正文 ≤128KB，UTF-8 无 BOM。
3. 可选 `references/`：平铺 `.md`，文件名 `[a-z0-9][a-z0-9._-]{0,127}`，≤32 篇、每篇 ≤256KB；`scripts/` 可以放但**不会被执行**。
4. 照抄 `skills/automotive-diagnosis/SKILL.md` 结构最稳妥。

**交付**：Git 分支提交 `skills/<skill-id>/` + 一份启用请求（skillId@version、声明的 tools 清单、需要主开发者评估的工具需求规格）。知识类原文另行按 §5.3 流程入库。

**测试**：
1. 静态自检：9 键齐全、列表格式、目录名=name、references 文件名规范。
2. 加载测试：主开发者启用后，发起与 `tasks` 关键词匹配的 run，`GET /api/runs/{id}/skill` 核对版本/scope。
3. 行为测试（qwen/openai 模式）：跑完整调查，检查调查顺序是否符合 references、答案引用是否全部来自工具取回记录、是否遵守 risk/citation；查 `dt_run_skill_reference` 确认 references 被实际读取（而非仅加载）。
4. 回归：改任何内容必须升 version；运行期改文件会被 `SKILL_CHANGED` 拒绝（SHA-256 钉存）。

---

## 8. 现有设备数据、知识资料和测试场景盘点

### 8.1 仓库资产清单

| 类别 | 资产 | 位置 | 性质 |
| --- | --- | --- | --- |
| 设备领域 | 汽车演示（CAR-A…CAR-D，通信超时场景） | `datasets/scenarios.json`、`datasets/fixtures/{a-d}/asset.json` | SIMULATED，legacy 链路 |
| 设备领域 | 工业产线（SC-0245-B，XLP-200 控制器模块版本追溯） | `datasets/v1/factory/sc-0245-b.json` + 3 个变体 | SIMULATED，产品 Agent 链路 |
| 设备领域 | 新能源充电（CHARGE-CASE，无资产也能调查） | `datasets/v1/automotive/charging-case.json` | SIMULATED，产品 Agent 链路 |
| 知识文档 | 充电系统检修手册（演示资料，v2026.1，含 5 章+安全边界） | `datasets/knowledge/charging-system-manual.md` | SIMULATED（文档头明确标注） |
| 知识文档（评测） | M1-RAG 固定语料 charging.md/parts.txt（v2026.2） | `datasets/eval/m1-rag/corpus/` | SIMULATED 固定评测集，带 sha256 冻结清单 |
| 专业知识 | automotive-diagnosis references（充电排查、诊断码） | `skills/automotive-diagnosis/references/` | 领域指引（方法，非事实） |
| 专业知识 | factory-equipment references（控制器维护、安全） | `skills/factory-equipment/references/` | 领域指引 |
| 维修/配置记录 | 全部内嵌于 fixture JSON（3 条 SC-0245-B 事实、CAR-A…D 证据记录） | `datasets/` | SIMULATED |
| 测试场景 | legacy A–D；产品 Agent 双域案例（汽车 9/工厂 8 断言，见 `.planning/STATE.md`）；M1-RAG 12 例（6 语义+2 部件号+跨归属负例） | `datasets/eval/m1-rag/cases.json` 等 | SIMULATED |
| 观测数据 | 无静态文件；仅测试代码内联 SQL 插入 | `DualDomainAgentTest.java:115` | SIMULATED |
| Skill 规则 | automotive-demo/rules.json（legacy） | `skills/automotive-demo/` | legacy 遗留 |
| 真实可追溯资料 | **未找到**任何真实行业资料 | — | 全部为工程演示数据 |

**数据库实际数据量**：本次调查无数据库连接，**不猜测**记录数。可确认：默认配置下设备 Seeder 关闭（`devicetruth.assets.seed-fixtures=false`），即全新数据库默认无设备数据；Skill 默认安装两个版本（`application-product.properties` 第 21 行）。

**质量缺口**：观测数据无任何静态准备机制（主代码无写入通道，测试硬编码）；工业域只有单设备（SC-0245-B）单属性（moduleVersion）示例，缺多属性/多部件样本；真实/授权（AUTHORIZED）数据通路已定义但全仓库无真实数据样例。

### 8.2 建议优先收集的领域资料

**工业设备领域**：
| 资料 | 主要用途 | 去向 |
| --- | --- | --- |
| 设备台账/资产清单（含部件树） | 建 `dt_asset`/`dt_asset_component` | PG |
| 出厂 BOM/随机文件 | 出厂配置事实（FACTORY，高权威） | PG + 关键页 RAG |
| 技改申请/批复与实施工单 | 技改前后事实+冲突场景 | PG（事实）+ RAG（方案原文） |
| 维修工单/备件更换记录 | 维修历史时间线 | PG |
| 设备手册/维护规程 | 检索与引用 | RAG |
| 备件兼容表 | 兼容性判断依据 | RAG |
| 安全作业规程 | 答案安全边界 | Skill references + RAG |
| 典型故障案例 | 测试场景设计 | `datasets/` |

**新能源汽车领域**：
| 资料 | 主要用途 | 去向 |
| --- | --- | --- |
| 车型/充电设备配置档案 | 设备登记与配置事实 | PG |
| 充电系统维修手册（厂商） | 检索引用（替代现有演示手册） | RAG |
| 维修与升级记录（BMS/OBC 更换、软件升级） | 维修历史 | PG |
| 故障码/诊断流程资料 | 排查方法 | Skill references |
| 高压安全规范 | 安全边界 | Skill references + RAG |
| 充电日志/观测样本（脱敏） | 观测数据（未来真实通道） | PG（待通道实现） |

---

## 9. 数据质量、Skill 质量与产品体验验收标准

### 9.1 数据质量验收（复用现有测试框架）

- **字段完整性**：每条配置/维修事实七要素齐全（部件/属性/取值/来源类型/来源ID/生效区间/权威）；空值检查可用 `GET /api/assets/{id}/configuration` 抽查 + SQL `SELECT count(*) WHERE source_id IS NULL`（交付前主开发者执行）。
- **唯一 ID 与关联**：source_id 全局唯一稳定；`dt_asset_component` 外键必达；重复导入幂等（V10/V11 唯一键保证）——验收方法：同一 fixture 灌两次，行数不变（可写成对 `AssetRegistryRepository.importSnapshot` 的单测，模式参照现有 `AssetToolTest`）。
- **时间与来源一致性**：`recorded_at ≥ observed_at/valid_from`、`valid_to > valid_from` 由 CHECK 强制；人工核对 `source_ref` 与来源资料页码一致。
- **真实/模拟标识**：`data_nature` 逐行如实；观测表强制 SIMULATED。
- **证据版本/冲突**：多来源同属性场景必须覆盖"冲突可判"（参考 SC-0245-B 三来源变体）；用 `expected/factory-sc-0245-b.json` 的 contract 段作为断言蓝本（它精确写了 timeRule/verificationRule/installationRule/conflictRule/citationRule）。
- **缺失值处理**：不允许用 AI 推测补值；缺失就缺，由 Agent 如实报告并追问/要求现场核验。

### 9.2 RAG 质量验收（复用 M1-RAG 评测模式）

- **成功入库**：`parseStatus=PARSED`、`ingest_status=READY`、chunkCount>0、ingestStatus 非 NEEDS_REBUILD。
- **相关性**：沿用 `datasets/eval/m1-rag/cases.json` 模式为新语料写 positive/partNumber/negative 用例，阈值 positiveHitAt5、零授权泄漏、零伪造引用接受（manifest thresholds 段）。
- **元数据适用性**：用 `filters{domain,deviceModel}` 检索验证不误召其他型号文档。
- **引用定位**：hit 的 section 必须对应原文真实章节（人工抽 20% 核对）；`get_document_excerpt` 读回内容与上传原文逐字一致。
- **版本处理**：新版本上传后旧版本文档状态与检索行为符合团队约定（当前无自动退役，需人工流程）。

### 9.3 Skill 质量验收

- 能加载激活（`GET /api/runs/{id}/skill` 版本正确）；references 可被读取（`dt_run_skill_reference` 有钉存行）。
- 工具使用正确：参数合规（如 `get_configuration` 必带 attribute、`get_document_excerpt` 必先 search）；不越权（激活后 allowedTools 交集生效）。
- 体现领域方法：调查顺序符合 references；遇到缺证/冲突时追问或要求现场核验而非硬答。
- 安全边界：触发高危问题时答案 riskLevel/safetyDisposition 正确（前端有专门警示区，`App.vue:70-75`）。
- 修正能力：补答新证据后最终答案与旧结论矛盾时以新证据为准（可用多轮补答场景验证）。
- 复用测试：`SkillRuntimeIntegrationTest`、`DualDomainAgentTest` 的模式可扩展为新 Skill 的回归夹具。

### 9.4 产品质量验收

以 §2.C 的 8 个体验任务为清单；验收方法优先用已有脚本扩展（`scripts/verify_v1_e2e.py`、`verify_sse_browser.py` 模式：起服务→跑场景→核对报告），不新建评测系统。判断标准：任务可独立完成、错误可理解、证据可核对、问题可复现（附 runId 即可由主开发者用 tool-events/model-events 复盘）。

---

## 10. 双人团队职责、权限与协作流程

### 10.1 分工

**主开发者**：系统架构与业务代码、Flyway 迁移与 Schema、Tool 实现与网关白名单、Skill 安装配置与重启、产品需求筛选与开发、生产部署。

**领域开发者（队友）**：领域资料收集与来源台账、数据清洗结构化与质量校验（JSON/CSV 交付）、RAG 文档准备与检索/引用验证（可自助上传）、Skill 内容与 references 编写、测试场景设计、产品体验与需求整理、集成后的业务验收。

### 10.2 权限矩阵

| 操作 | 队友 | 主开发者 |
| --- | --- | --- |
| 知识文档上传/检索/重试（`/api/files`、`/api/knowledge/*`） | ✅ 可自助 | ✅ |
| `datasets/`、`skills/` 下新增内容文件 | ✅ Git 分支提交 | review 后合并 |
| 设备数据入库（Seeder 配置/SQL） | ❌ 只交付准备格式 | ✅ 唯一执行者 |
| `dt_asset*` 手工 UPDATE/DELETE | ❌ | ✅（当前代码无此路径，手工 SQL 需评审） |
| `dt_skill_installation` 变更（启用/停用/白名单） | ❌ 提启用请求 | ✅ 改配置+重启 |
| 系统表（conversation/run/event/reference）任何写入 | ❌ 绝对禁止 | 系统运行时独占 |
| Flyway 迁移 | ❌ | ✅ |
| 前端/后端业务代码 | ❌（提需求） | ✅ |

### 10.3 交接格式与流程

1. **数据交接**：队友交付 `datasets/v1/{domain}/xxx.json`（§3.3 格式）+ `xxx.sources.md`（来源台账：每条事实出自哪份资料哪一页、真实/模拟）→ 主开发者核对后配置 Seeder 导入 → 双方用 `GET /api/assets/{id}` 核对 → 记录验收结论在 PR 描述。
2. **知识交接**： teammate 直接经 API 上传（自助）+ 在仓库 `datasets/knowledge/` 或独立目录提交源 Markdown（版本管理）→ 检索验证记录（query/hit/section 截图或 curl 输出）附在 PR。
3. **Skill 交接**：`skills/<skill-id>/` 分支 + `docs/reviews/` 或 PR 描述中附启用请求（skillId@version、tools 清单、验证 runId 与结果）。
4. **产品需求**：统一用 GitHub Issue（或仓库 `docs/reviews/` 下体验报告），模板=§2.C 的任务结构（身份/目的/步骤/预期/观察/验收）+ 环境（版本/commit/浏览器）+ runId 证据；主开发者统一评审排期。
5. **冲突避免**：按 §10.1 分模块认领；队友不动 `src/`、迁移 SQL、配置文件（`application*.properties`）；主开发者改 `skills/` 结构时先与队友同步。
6. **轻量流程**：分支 → PR（描述=交付物清单+验证证据）→ 对方 review → 合并；数据/Skill 验收结论写在 PR；阶段验收沿用 `.planning/ACCEPTANCE.md` 流程。

---

## 11. 当前缺失能力与潜在开发需求

按影响排序（编号沿用并扩展专项审查报告 P0–P2）：

| 优先级 | 需求 | 现状 | 影响 |
| --- | --- | --- | --- |
| P0 | 设备数据导入机制 | 无 API；仅 Seeder/手工 SQL | 队友无法自助迭代设备数据，每次都要主开发者 |
| P0 | Skill 自助启用 | 改配置+重启 | 每加一个 Skill 都要主开发者操作 |
| P1 | 观测数据准备通道 | 主代码无写入路径 | 充电/工业排查类场景缺观测样本，无法演示完整调查 |
| P1 | Skill 安装管理 API + frontmatter 校验脚本 | 无 | 交付效率与格式错误早发现 |
| P1 | 设备表描述字段（型号/厂商/位置） | `dt_asset` 仅 ID | 设备档案信息只能散落在知识文档 |
| P1 | 知识文档管理（列表/退役/删除） | 无 API/UI | 版本增多后检索噪声不可控 |
| P2 | PDF 导入（OCR/版面解析） | 明确未实现（OCR_REQUIRED 暂存） | 大量行业资料是 PDF |
| P2 | 备件兼容等结构化领域工具 | 无 | 只能文本规则，确定性弱 |
| P2 | 多 Skill/切换、真实设备接入、多用户认证 | 无 | 产品化阶段需求 |
| P2 | 结果导出/分享 | 无 | 检修报告交付场景 |

---

## 12. 编写正式工作手册仍缺少的信息

## 《工作手册编制就绪度》

| 手册部分 | 就绪度 | 说明 |
| --- | --- | --- |
| 数据库数据建设说明 | **基本具备（差 1 项）** | Schema/约束/工具语义/示例全部查清；缺"主开发者认可的权威等级刻度约定"与真实数据样例 |
| RAG 知识库操作说明 | **已具备** | 上传/解析/检索/引用全流程 API 与限制已查清，可照 §5.3 直接成稿 |
| Skill 开发指南 | **已具备** | 已有专项审查报告 + 本次差异核对（无差异）+ 两个示例 Skill，可直接成稿 |
| 产品评审与测试指南 | **已具备** | §2.C 的 8 个任务可直接扩展为评审手册 |
| 团队交付和验收流程 | **基本具备（差 1 项）** | 分工/权限/格式已明确；缺双方对"验收结论记录位置与模板"的最终确认（建议 PR 模板） |

**仍需主开发者提供的信息（按影响排序，≤10 项）**：

1. 设备/属性/信号命名的团队规范（attribute 只能 ASCII 标识符，需定词表）。
2. 权威等级（authority）数值刻度约定（示例只有 10/5 两个点）。
3. 生产/演示环境的 PG 连接方式与"队友可用的只读查询账号"（用于自查数据，不给写权限）。
4. 观测数据的导入通道决策：是开放受限导入 API，还是继续测试内联 SQL（影响 §11 P1）。
5. 设备数据导入 API 的排期（决定 §3.3 的"最小可行协作方式"是过渡还是长期）。
6. 知识文档版本退役/替换的约定流程（在删除 API 出现前的人肉流程）。
7. 真实资料（AUTHORIZED）进入系统的审批标准与 dataNature 标记责任划分。
8. 模型环境：队友验证 Skill 时用 scripted（离线）还是配 qwen/openai key（影响验收深度）。
9. 新 Skill 的启用请求模板与评审人（建议固化到 PR 模板）。
10. 领域覆盖优先级：工业与新能源先做哪个域的数据/Skill（决定 §8.2 资料收集顺序）。

---

*本报告全部结论基于静态源码审查；标注【待运行验证】的条目需在服务实机运行后复核。未找到的信息均已显式标注"未找到"，未做任何推测性补全。*
