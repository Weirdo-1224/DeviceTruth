# DeviceTruth V0.3 三端 UI 实施计划

**阶段 A · 源码审查与映射 · 2026-11-18 · 待确认。** 本轮只交付本文，不改业务代码、接口和 OpenSpec 任务，不执行 B/C。「已有」表示在当前工作树找到对应合同及实现，不表示已在本轮运行验证或有真实现场数据。

## 1. 基线与结论

- 已对照 `docs/DeviceTruth_Prototype_V0.3/` 的 `README.md`、`DESIGN_REVIEW.md`、`index.html`、`assets/styles.css`、`assets/app.js` 与三张桌面截图。HTML/CSS 定义排布与样式；`setView`、`setFocus`、`submitPro`、`sendConsumer`、`setAdminNav` 等只演示本地交互。生产不复制深色预览条、示例会话/配置/时间/事件/KPI/身份徽标，不使用旧原型 R 编号推定接口。V1.0 产品设计与现行源码是业务基准；V0.1 `spec/task/state` 仅用于理解 legacy 与证据边界。
- 当前工作树包含 `stabilize-pro-workbench-interactions` 的专业端收敛及未提交改动。现状为顶栏 + 左侧近期调查 + 主区内「判断列表 / 焦点」双列，来源在终态内容中，资产/消息/事件在调查记录抽屉。该 OpenSpec 任务 5.2 曾要求移除常驻证据列；V0.3 拟恢复的是**轻量证据上下文**，不是旧式大报告三栏。批准后用新 OpenSpec 显式处理这一差异，保留旧验收与工作树现有成果。
- `frontend/index.html` → `src/main.ts` → `App.vue` 是唯一 Vue 入口；无 Vue Router、普通端路由、管理端路由或角色菜单，无独立全局 store。Vite `/api` 同源代理指向本地 product 后端，部署 nginx 转发 `/api` 并关闭 SSE 缓冲。legacy `/api/sessions`、`/api/diagnoses` 和资源内静态演示页不是 Vue 产品数据源。`main.ts` 引 `design.css`，`App.vue` 引 `pro.css`，子组件有 scoped CSS；`workbench.css`、`DiagnosisReport.vue`、`EvidenceDrawer.vue` 不在当前入口渲染链中。
- **可复用**：持久会话、新建/历史 Run、同轮补答、SSE/历史事件、取消、登记引用、Markdown 导入、按时点查设备、Run Skill。**不能原样上线**：按角色分流、平台 KPI/管理操作、结构化观察写入、报告导出/归档、服务器追问选项、每个焦点独立拥有建议或证据对。取舍见第 5 节。

## 2. 当前数据流与能力核实

1. **会话与轮次**：`App.vue` 启动时从本地最近会话 ID 尝试恢复（不是授权），缺失或恢复失败则创建会话，随后读取设备与历史列表。`ConversationDetail` 给出 `conversation/messages/runs`；`RecentInvestigations` 显示真实更新时间和当前会话状态，已知首条用户消息可作标题，否则显示通用名称，不逐条多发详情请求。`resolveRunTargets` 区分所看轮、活动轮和待答轮。查看已结束历史报告不换掉活动目标；多个未决轮无唯一目标时先显式选轮。
2. **新问题与补答**：`App.submit/investigateCurrent/answerQuestion` → `api.sendMessage/replyToQuestion` → `AgentConversationController` → `GeneralAgentService.submit`。新消息带 `Idempotency-Key`，服务端写 `USER` 消息并排队新 Run；新建会话本身不启动 Run。补答带 `replyTo={runId,pendingQuestionId,questionVersion}`，事务里校验归属、同会话、等待状态及问题版本，再排队**原 Run**，可再次追问。过期返回 409，不转发成新任务。服务端持久化消息、追问、结果和事件；模型只提议，不能编排页面或直接写事实。
3. **进度与恢复**：`timeline.ts` 先读 `/events` 再通过 `api.openRunEventStream` 订阅活动 Run，按 `seq` 去重，保留历史补读与原生 `Last-Event-ID` 重连。历史查看单独读该轮事件；`RUN_WAITING/RUN_TERMINAL` 触发持久回读，不从 SSE payload 直接改状态。`scheduler.ts` 合并回读，`scope.ts` 和 App 用会话/轮次代次及版本防止旧响应串入；有轮询、手动重同步、刷新恢复。`RunTimeline` 消费白名单事件；运行中取消是协作式，需回读真实终态。
4. **证据、设备与 Skill**：所看轮由 `goalMessageId` 关联该轮目标消息及 `selectedAssetId`，时点取 `RunView.asOf`，不跟底部设备选择变化。`diagnosisViewModel` 以判断原文和 `referenceIds` 生成界面模型；`GET /api/runs/{id}/references` 提供登记列表，区分本判断直接引用、本轮其他登记来源、未回传引用 ID。`EvidenceComparison` 读该轮设备/时点，`EvidenceModal` 显示来源详情，切轮关旧详情；`/skill` 返回已选 Skill 元数据，未选不等于故障，用户不能通过该读接口安装/启停。

下表 Java 路径相对于 `src/main/java/io/devicetruth/`。以 Controller、服务与仓储交叉确认，不以视图文案推断后端能力。

| 核实点 | 源码结论 |
| --- | --- |
| 认证/RBAC | `server/ProductConfiguration.java` 从 `application-product.properties` 绑定固定 `ownerId/projectId`，不是登录用户的请求级 principal。Run、引用、流、资产、文件、知识有记录级归属过滤，不存在或越权可合并返回 404；工具权限不等于人员 RBAC。未发现登录/登出、`me`、角色/管理员授权合同，不能作为多身份隔离产品开放。 |
| 多轮补问 | `application/agent/GeneralAgentService.reply` 持久化补答与同轮恢复。`core/contract/AgentTaskInput.PendingQuestion` 有 ID、版本、问题文字、`requestedFields`、`askedAt`，无 `options`；`requestedFields` 不是候选答案。 |
| 独立调查 | 同一 Conversation 可有多独立 Run（各自状态/结果/引用/事件/取消）；无独立调查任务 CRUD、持久焦点实体或专门的「深入调查」HTTP 路径。普通消息开启新 Run；App 的权限门禁不因换布局而改变。 |
| 文件上传 | `api/FileController`：`POST /api/files` multipart、`GET /api/files/{fileId}`、`POST .../{fileId}/retry`。现 UI 选 `.md`；`FileUploadService` 与 `KnowledgeImportService` 限 1,000,000 字节、验证格式与归属，只有已 `PARSED` 可附带。PDF 服务端可标 `OCR_REQUIRED`，无可检索正文或图片解析。`api.ts` 有 `retryFile`，App 未接重试按钮；当前只有普通消息带已解析 `fileId`，一键深入和补答不自动带附件。 |
| 现场观察 | `application/agent/ObservationTools.get_observations` 只读授权范围的模拟观测，可能登记来源，不是用户写入观测。现按钮只聚焦普通输入，提交仍是用户报告；没有现场观察写入/确认 API，不得写观察表、配置或权威事实。 |
| 报告与导出 | `RunView.result` 是持久结构化报告，页面已有全文展开。`application/agent/AgentReportTools.generate_diagnosis_report` 是 Agent 内部只读工具；`adapter/RunReportWriter` 是 legacy CLI 本地输出。未发现产品报告下载、文件导出或归档 CRUD。 |
| 用量 | `RunView.activeDurationMs/usageStatus/inputTokens/outputTokens/cost` 与 `/api/runs/{id}/model-events` 存在。`adapter/persistence/AgentRunRepository.updateModelUsage` 仅按轮汇总 `REPORTED/PARTIAL/UNKNOWN`，cost 仍可为 null；无平台聚合、跨用户总览、费用或配额 API。未知不能填 0。 |
| 配置时点 | `api/AssetController` 有 `GET /api/assets/{assetId}`（组合）、`/configuration`、`/maintenance`，接收 `asOf`。`AssetRegistryRepository` 按 `validFrom ≤ asOf < validTo`（无结束为开放区间）查询，另有 `recordedAt/observedAt`；`AssetTools` 判定冲突/现场状态及待核验项。历史轮按当轮**业务时点查询**，不宣称是不可变快照或按获知时间重放，不能以最新写入时间覆盖冲突。 |
| 来源可信状态 | `core/contract/AssetFacts` 记录核实 `DOCUMENTED/FIELD_VERIFIED`、装机 `UNCONFIRMED/FIELD_CONFIRMED`、冲突 `CONFLICT/NONE`；`AgentResult.Claim.certainty` 是 `POSSIBLE/SUPPORTED/CONFIRMED`，受 `AnswerGuard` 约束；`SIMULATED/AUTHORIZED` 是数据性质而非核实。通用 `Reference` 有来源类型、ID、版本、位置、摘录、dataNature，**无**通用核实状态、有效期或原文件 URL。`FIELD_OBSERVATION` 来源类型不证明现场认证。 |
| 知识与 Skill | `api/KnowledgeController` 实现资料导入、按 ID 读、检索和依 Run 读取引用/章节，不提供管理员发布/撤回与审计。`skill/SkillRuntime` 通过服务器配置/V14 管安装及工具上限，只有 Run 选择可供 Vue 读取。`/api/health` 不能证明真实模型、计量或权限可用。 |

## 3. 设计到业务映射

### 共享与专业端（优先）

| 设计元素 | 当前业务组件 | API / 状态模型 | 适配方式 | 风险 |
| --- | --- | --- | --- | --- |
| 白顶栏、品牌、选中导航、身份区 | `App` 顶栏、`UiIcon` | 专业单入口 | 保留 DeviceTruth，移除预览条；导航落到工作台、设备、过程、报告真实区域，不造路由和身份徽标 | D1；不能依赖前端做角色授权 |
| 外侧最近调查/新建/工作空间 | `RecentInvestigations`、调查记录抽屉 | `GET/POST /api/conversations`、会话明细 | 桌面常驻近期列表；窄屏沿用抽屉；真实时间、状态、重试与空态 | 无真实标题用通用名；不要把新建会话当新 Run |
| 面包屑、任务标题、对象、历史 Run | `App`、专业页任务头、轮次选择器 | 首条用户消息、`goalMessageId`、`RunView`、`SkillSelection` | 展示真实任务及所看轮目标、资产、业务时点和状态；保留历史与活动轮区分 | 未设资产/未加载有提示，不硬编码任务编号 |
| 调查方向、序号、概览 | `ClaimList`、`claimSelection.ts` | 本轮 `ClaimView[]`、按轮选择 | 按真实判断生成短预览、序号与实际计数；无判断可自由输入；选焦点只换视图 | 不补齐三项、不合成小 Run、不串轮 |
| 焦点全文和状态标签 | `ClaimDetail`、`ProfessionalDiagnosisPage` | 判断原文、category/certainty，轮级 risk/dataNature | 中央突出本焦点；摘要明确属本轮，标签分别表达判断、风险与数据性质 | 禁止模型生成版式，不能从文字猜冲突/确证 |
| 关键证据对照和详情 | `ClaimDetail`、`EvidenceComparison`、`EvidenceModal` | 直接引用、登记引用、资产组合、用户消息 | 只对有明确同组件/属性/时点的配置做对照；用户口述独立显示未核实原文和时间，无法建立结构化对照时展示真实来源或缺口 | D5；不硬配双卡，不把资产快照自动算作本轮引用 |
| 下一步、限制与安全提示 | 专业页 `roundFields`/全文、`EvidenceComparison` | `suggestions/uncertainties/limitations`、`riskLevel/safetyDisposition`、资产 `requiredChecks` | 中央保留本轮建议/原始限制；资产待核验项有独立时点，风险文字与色标并用 | 不假设建议逐条绑定焦点、不暗示已完成核验 |
| 统一输入：深入调查 | `App.investigateCurrent`、`buildInvestigationMessage`、`runPermissions` | 同会话 `POST .../messages`、所看资产、授权 `contextRefs`（最多 10） | 模式切换不提交；显式提交继承实际焦点线索，生成新 Run；保留零引用路径、超限提示和活动门禁 | D6；不能吞草稿、附档和引用 |
| 统一输入：补充现场观察/自由问题 | `App.focusFieldNote/submit` | `USER` 消息、选中资产及附档 | 保留自由问题；若批准，观察模式把原文连同「未核实用户报告」标识提交，不套用焦点深入模板或默认继承焦点引用；提前说明会启动新一轮，待答另走版本校验路径 | D3；无真正观察写入，不声称仅保存或已核实 |
| 补答、设备选择与附档 | App 补答区、`upload`、`select` | `PendingQuestion/ReplyTarget`、`FileView`、`GET /api/assets` | 真实待答目标与输入相邻；可键盘访问的附件按钮触发现有 input，不露原生外观；显示大小/格式/解析、所属草稿及可重试状态；设备可不选 | D2/D6；历史轮资产不随选择变化、迟到上传不串会话、不能把「选中文件」当已上传 |
| 右侧证据与上下文 | 专业页 `pro-sources`、抽屉 `EvidenceComparison`、`EvidenceModal` | 所看轮直接引用、其他登记来源、设备快照、用户报告 | 轻量右栏按真实来源分组，详情可展开；保留加载、空、缺失、不可用、合并 404、错误重试；失败/取消仍可读已登记来源 | D5；不编造来源类别计数、通用可信评级或原文链接 |
| Run 状态、折叠过程、取消/恢复 | `RunTimeline`、App 订阅与历史、取消和同步 | `/api/runs/{id}`、`/events`、`/stream`、`POST /cancel`、`/skill` | 右栏区分执行完成与现场核验；中栏输入下折叠真实过程，保留进度/等待/终态/断线提示，区分重读与再发任务 | 折叠不改变订阅，操作绑定活动目标，禁止靠事件直接改持久态 |
| 完整报告、消息与归档入口 | 专业页 `full-result`、记录抽屉、`EvidenceModal` | `RunView.result`、`ConversationDetail.messages`、登记来源 | 默认收起长报告，通过展开或历史取全文；保留各类判断、风险、限制、引用及原消息 | D4；不能显示伪下载/归档，也不能删除全文 |
| 键盘、状态与窄屏 | `design.css/pro.css`、各子组件、`submitOnEnter` | `busy/uploading/permissions`、本地展开状态 | normal/hover/focus-visible/disabled/loading/empty/error 一致；Enter、Shift+Enter、IME、防重复、Esc/回焦；长文本断行与有条件滚动 | 窄屏不能藏掉唯一入口；点焦点、读历史不应强制滚到底 |

### 普通端

| 设计元素 | 当前业务组件 | API / 状态模型 | 适配方式 | 风险 |
| --- | --- | --- | --- | --- |
| Hero、最近会话、新咨询、历史 | 无普通页；可复用 `RecentInvestigations` | 会话列表/详情、新建会话 | 若入口获批，共用现有 Agent、标题、历史逻辑；只简化展示 | D1；不另建模拟聊天管线 |
| 设备类型 / 我的设备 | App 关联设备、`EvidenceComparison` | `/api/assets` 返回作用域内设备 ID，另有资产详情 | 用可选「关联设备」代替假类型按钮；分类与私有设备治理未接入 | D1；不推断归属于哪个登录人 |
| 对话消息、追问、快捷答案 | App 消息列表、待答区 | `MessageView`、`pendingQuestion`、`ReplyTarget` | 展示持久消息与实际问题，提交同轮；有正式候选合同才允许点选填入、编辑再提交 | D2；不把字段名当选项，不自动重发过期答案 |
| 聊天输入、附件、对话滚动 | App `submit/answerQuestion/upload` | 同专业消息与文件合同 | 用同一输入规则，明确当前是新问题还是补答；保留上传状态和适用边界；仅靠近底部时跟随新消息 | 防重复、IME 误发、历史阅读跳屏 |
| 当前判断、下一步、安全、资料来源 | `diagnosisViewModel`、`EvidenceModal` | 当前适用 Run 的 `result`、风险、引用 | 真实结果才显示结论与建议；等待、运行、失败和无来源明确展示；安全提醒独立 | 不用旧 Run 结论冒充当前，不硬编码某设备风险 |

### 管理端

| 设计元素 | 当前业务组件 | API / 状态模型 | 适配方式 | 风险 |
| --- | --- | --- | --- | --- |
| 深侧栏、白顶栏、管理总览与角色 | 无 | 无管理员授权接口 | 先在隔离 fixture 验证视觉和未接入壳；生产不开放入口，不请求业务数据 | D1；query/hash/localStorage 不能授予角色 |
| 会话/Run/知识、Token/费用 KPI | 无 | 仅作用域内基础读、单 Run 用量 | 保留卡区显示未接入/暂无完整计量，不放示例数值 | 不用 0 填未知，不从私人会话拼总量 |
| 近期 Agent 调用、运行记录与模式 | 可复用时间线视觉，不复用私有数据 | 单轮详情/工具和模型事件，无平台清单与模式字段 | 表格空态说明授权查询未接入；无私有 API 请求 | 禁止列举每个会话代替跨用户聚合 |
| 用户/权限、模型/配额、审计 | 无 | Run 公开事件与工具权限不等于管理员审计 | 显示边界，不造编辑、权限分配、配额写入按钮 | 禁止泄露凭据、Prompt、模型原始输出 |
| 知识治理、Skill、设备业务数据 | 普通上传、`EvidenceComparison`、Skill 标记 | `/api/files`、`/api/knowledge/*`、`/api/assets/*`、Run Skill | 管理壳只介绍现有基础和未接入的审批/治理；保留用户侧真实读/导入 | 不把产品 API 冒充管理 CRUD |
| 能力就绪与管理边界 | 无 | `/api/health` 只有进程探针 | 使用明确未接入说明，不做假状态灯 | 不混报健康、模型可用性、计量与安全 |

## 4. 版面与文件级改动（批准后）

桌面原型外壳最大 1530px，白顶栏 70px、外侧导航 222px、主区左右内边距 28px。专业内层三栏在 1600px 下约 **216 / 770 / 236px**，间距 14px；中央焦点、统一输入和折叠过程纵向成组，右栏靠顶，不放全文。普通端 Hero 后对话/判断约 836/400px；管理端四等分 KPI 槽，表格/就绪度约 804/431px。原型以 1600×1000 viewport、DPR 1 **全页**取图，专业/普通/管理 PNG 高度为 1208/1000/1071。对照留固定 viewport 与全页两种图，以生产品牌顶栏为基线扣除预览条，不把自然内容超首屏误判为结构错误。

1366×768 保留三栏和纵向滚动；≤1360 将证据侧栏移到主网格下方，详情仍可定位；≤1000 内层改单列，窄屏收纳外导航。390×844 保留历史、新建、取消、输入、附件和详情通路，不照抄原型隐藏操作。共享 tokens 控制中性底/白面/边界线/字号/10–14px 卡圆角/轻阴影；紫色用于主操作和选中，绿成功、橙待核验、蓝资料、红失败，同时用文字表达状态。

| 顺序 | 文件（拟） | 改动与保留 |
| --- | --- | --- |
| B0 | `openspec/changes/<批准后命名>/` | 获确认后用 `$openspec-propose` 明确取代旧布局约束，不覆盖旧验收或标记未跑任务。 |
| B1 | `frontend/src/design.css`、`pro.css`、`components/UiIcon.vue`（仅缺图标时）；新增 `components/ComposerPanel.vue` | 共用 tokens、按钮/卡片/标签/表单原语；共用有状态的输入/附件展示；只抽有复用的组件，无新依赖。 |
| B1 | `frontend/src/App.vue`、`components/ProfessionalDiagnosisPage.vue`、`ClaimList.vue`、`ClaimDetail.vue`、`RecentInvestigations.vue` | App 保留会话/Run 状态、门禁、查询和提交；专业页安放三栏，以 slot/props 放入输入和时间线；保留历史、所有终态来源和全文，不重复挂载请求。 |
| B1 | `frontend/src/diagnosisViewModel.ts`、`components/EvidenceComparison.vue`、`EvidenceModal.vue`、`RunTimeline.vue` | 原文/ID 不变，添加必要分组、状态布局；设备读取仍绑定所看轮的资产/时点；复用模态及回焦。 |
| B1 | `frontend/src/api.ts`、`runTargets.ts`、`timeline.ts`、`scope.ts`、`scheduler.ts` | 默认不改合同、门禁、去重、并发保护；因批准的路径确需调整客户端参数/回读时做最小修改并补测试。 |
| B2 | 新增 `frontend/src/components/ConsumerDiagnosisPage.vue` | 复用 App 状态、Composer、会话和证据；按 D1/D2 处理入口/追问，不复制控制器；未经批准不改生产 `/`。 |
| B3 | 测试专用 `frontend/tests/fixtures/AdminCapabilityPreview.vue`（按工程约定确定） | 只复用设计原语及未接入说明；默认不从生产入口导入，不放 mock 授权逻辑或实际数据。普通/管理预览不能成为生产查询参数切换器。 |
| C | `frontend/src/*.test.ts`、`scripts/pro_workbench_fixture.py`、`scripts/verify_pro_workbench_mock.py`；必要时加 `scripts/verify_ui_v03_browser.py` | 保持已有断言，更新确实变化的选择器，增三端、模式和边界用例。截图及差异记录放 `docs/design/ui-v03-acceptance/`，不覆盖参考或旧验收。 |
| 默认不改 | `src/main/java/`、`src/main/resources/db/`、`frontend/package*.json`、部署/权限配置 | 无后端新增、DB 迁移、新依赖或新授权；有业务扩展需求另立规格。 |

## 5. 待决策与风险

| 编号 | 冲突 / 风险 | 推荐审核决定 |
| --- | --- | --- |
| D1（阻断正式三端） | 设计依赖角色导航，当前既无角色合同也无现成多端路由 | 生产继续现有专业 `/`；普通端实现真实合同的可复用视图，管理端仅非生产未接入壳，C 分别在隔离 fixture 验收。若普通端要正式开放为**同权限的视图**，先批准入口和标签；正式角色分流、管理端开放需单独认证/RBAC。 |
| D2（普通追问选项） | 问题没有候选字段，不能搬示例选项 | 默认文本补答，不显示假 chips；若候选是发布要求，另定后端可选候选字段和文本兜底。 |
| D3（现场观察） | 目前只有用户报告消息，缺少「单独保存现场观察、不发 Run」 | 两种模式分别标明调查请求与未核实用户报告，两者都明确会启动本会话新一轮。若要求只存观察，将独立提交标未接入且保留自由输入，另立授权、存储、引用规格。 |
| D4（报告/归档/原文） | 有报告与摘录，无文件导出、归档或通用原文下载 | 文案用「报告与历史」「查看来源详情」，保留全文、引用、位置；缺失动作不出现或标未接入。 |
| D5（焦点建议和证据对照） | 原型每焦点各有方案和双证据，实为判断 + 关联引用 + 轮级建议；旧规格取消过常驻右栏 | 加回轻量右栏；仅在有真实同字段/时点证据时做对照，其他显示缺口及真实原文；轮级建议仍属本轮，不做推断匹配算法。 |
| D6（统一输入迁移） | 现在一键深入无需文字/附档，普通消息可带已解析文件，补答是独立草稿 | 模式切换只更新 UI；保留可编辑、显式确认的原焦点深入路径（可预填现有目标文本）。公开附件适用路径，不能静默从普通草稿或补答搬附件；需更改补答附档/附件生命周期时单定合同与测试。 |
| R1（串数据） | 会话、活动轮、所看轮、待答轮混用，易串引用/设备/Skill/草稿或误取消 | 保留控制器和代次/version/请求 key；补迟到读取和上传作用域断言。 |
| R2（操作不可达） | 窄屏隐藏操作、长资料压迫布局、断线反馈不显眼 | 1600、1366、1360、390 检查，保留键盘访问、状态跟踪、滚动和断行。 |
| R3（验收混淆） | 原型图、协议替身、真实 PG、外部模型与真实现场不可混报 | 明确记录环境与数据性质；真模失败不得静默换 scripted；没有产品图就写「视觉尚未验证」。 |

## 6. 阶段 C 验收方案（未执行）

| 用例 | 方式与通过条件 |
| --- | --- |
| C01 构建与定向测试 | `npm --prefix frontend run test -- --run` 与 `npm --prefix frontend run build`。复用 `api.test.ts`、`runTargets.test.ts`、`scope.test.ts`、`pageScenarios.test.ts`、`diagnosisViewModel.test.ts`、`claimSelection.test.ts`；补模式/证据缺口断言。不得以此代替视觉或交互验收。 |
| C02 专业交互与可达性 | Vue 最新制品 + 可重复协议替身，复用 `scripts/pro_workbench_fixture.py`、`scripts/verify_pro_workbench_mock.py`。覆盖完成含引用/冲突、空判断、等待、运行、失败、取消、缺失引用。焦点联动且零 POST；历史轮事件/设备/证据隔离；全文、历史和 Skill 可达；验证 Tab/Shift+Tab、Esc、回焦、Enter/Shift+Enter、中文 IME、长文与无横溢。 |
| C03 操作合同 | 新建会话零 Run；自由消息、深入只在提交时发起，继承批准的实际引用/资产。观察不写事实；`.md` 覆盖过大/不支持/解析中/成功/失败/重试，文件仅随有效路径发送。取消活动目标，错误后可重同步及显式重发。 |
| C04 普通同轮追问 | fixture 与 product 各验证：问题 → `WAITING_FOR_USER` → 原 ID+版本补答 → 原 Run 继续 → 再问/完成。刷新保留对话，旧版本 409 保留草稿不发新 Run，资料可打开；若未开放正式入口，隔离接线验证不能算生产路由通过。 |
| C05 管理安全 | 隔离未接入壳无假 KPI、角色、CRUD、私有 API 请求；生产构建不挂该壳，改 URL 或客户端状态不能获得管理能力。RBAC 缺失列为未接入，不能靠 mock 授权验收。 |
| C06 Run/SSE/资料授权 | 复跑 `scripts/verify_sse_browser.py`、`scripts/verify_browser_recovery.py --browser-path <Chrome>`、`scripts/verify_pro_page_31_32.py`；验证 seq、`Last-Event-ID`、历史补读、乱序、实时等待/终态和重启恢复。`mvn -version` 核对 JDK 21 后运行 `mvn -q '-Dtest=AgentLifecycleTest,AgentStreamTest,ResourceAuthorizationTest,DualDomainAgentTest,AssetToolTest,GeneralAgentApiTest,AgentReportToolTest,AcceptanceTest' test`，按测试要求接隔离 PG；验证含同资产跨 `asOf`、拒绝/缺失与模拟/资料状态不升级。 |
| C07 真模链路 | 用已配置真实外部模型的 product 联调，记录实际模型调用、Run 轨迹、持久结果和来源，覆盖普通问答及专业继续。确实未产生追问或无密钥/网络/额度如实记录。分别记模型、embedding、PG 和数据性质；scripted 只用于可复现回归，健康探针不算真模。 |
| C08 浏览器截图及差异 | 固定 fixture：三端各存 1600×1000、1366×768、390×844 的 viewport 图（9 张），1600 另存全页，专业加 1360 临界和运行/错误/证据详情。记录浏览器、DPR、构建/fixture 版本及步骤，存 `docs/design/ui-v03-acceptance/`。逐区核对侧栏/主体宽、标题与标签层级、卡片对齐、证据布局/下移、输入、折行和状态色；结构差异先修再截图，剩余差异写明原因和影响。 |

**阶段 A 实际验证**：用 `git status`、`rg`、`Get-Content` 对照当前工作树、源码、合同及历史规格；逐张审阅三张参考 PNG 并核对尺寸。新增功能用例执行 **0 项：通过 0 / 失败 0 / 错误 0 / 跳过 0（不在本阶段执行）**。旧验收只用于定位回归用例。未构建/启动 Vue，未拍产品图、未跑产品浏览器、PG、真模或现场验证；**产品视觉尚未验证**。C 阶段按实际命令、总数/通过/失败/错误/跳过、环境、差异和未验证项记录。等待第 5 节审核后再实施。
