# DeviceTruth 管理端五模块需求梳理（2026-11-19）

本文合并旧交接、今天的分工变更、账号接口说明及本地源码事实，作为下一轮设计依据。文中的候选接口和数据结构尚待管理端 OpenSpec 落实。本轮只整理文档。

## 1. 依据与源码基线

| 依据 | 使用方式 |
| --- | --- |
| Downloads/DeviceTruth_管理端开发交接_五模块含原型_20261118.html | 保留五模块、脱敏规则、知识/Skill 生命周期、配额、审计与视觉参考 |
| 用户 2026-11-19 补充说明 | 更新账号/空间职责、平台与团队权限、资源归属及共享代码协作规则；覆盖旧交接冲突内容 |
| Downloads/account-api.md | 认证协议早期交接版，进度需与仓库版本核对 |
| openspec/changes/add-authenticated-team-workbench/ | 当前账号、团队、空间与持续授权合同、任务及已有验收记录 |
| 源码、迁移、AGENTS.md | 判断实际能力及实施约束 |

本地核对快照：仓库 /Users/chiye/Projects/DeviceTruth；HEAD 为 **22e5bab7b033028c09dd6a31a8143d99e69fa384**，摘要“merge: 同步账号团队工作台并整合知识库检索”。开始梳理时 git status --short 无输出，工作区干净。这是本机快照；双方正式开工仍需交换并确认相同 commit。

迁移文件已到 **V24**：V19 账号/空间/会话，V20 协作溯源，V21 密码恢复，V22 团队邀请，V23/V24 知识库结构与可见性。旧交接的 d48c8c1、V1–V17，以及第四组历史提示词的“最大 V22”均已过时。合入前重新分配迁移编号，本文不预占下一编号。

输入文件 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| Downloads 管理端 HTML | c444b173e5623d088a849de05d1a9ef401a1ebdc81b69e5fa18f16b8a97c35fd |
| Downloads/account-api.md | 0ac711fc28a711c78b48cadf12b98c4ebf7b9796dd8c2850a98da4cfbf2dc2a7 |
| 仓库 change/account-api.md | ad216cbf8c8fb087d18c6ba09140680143706755546188a91c419ba90dd211a4 |

### 当前源码比补充说明中的阶段更靠后

Downloads 账号文档仍写“只有 auth/health 放行、密码恢复和 SMTP 未实现”；用户补充描述的是“HTTP/worker 尚未接完”。当前本地仓库已包含后续实现：

| 能力 | 源码核对结果 | 管理端处理 |
| --- | --- | --- |
| 注册/登录/退出/me/CSRF | AccountController、AccountService、ProductAuthConfiguration 已存在 | 直接复用 |
| 密码恢复、全会话撤销、SMTP、认证限流 | 对应服务及 V21 已存在 | 真实邮箱收件仍待验收；资源配额另行实现 |
| 团队、成员、邀请 | WorkspaceService、TeamInvitationService、V22 已存在 | 产品侧负责 |
| HTTP 空间授权 | HttpAccess、WorkspaceScopeResolver 已接产品资源 | 沿用真实空间解析 |
| worker、恢复、SSE 持续授权 | 第 3 组已有接线和隔离验证记录 | 在既有检查上接账号停用 |
| 共同调查/串行 Run、附件发布、工作台前端 | tasks 第 4/5/6 组尚未完成 | 管理侧按共享合同协作 |
| 平台角色、账号停用、五个管理页和 /api/admin/* | 本轮源码检索未发现实现 | 管理侧新增范围 |

acceptance.md 记录第 1–3 组 98 项定向有效证据：96 通过、0 失败、0 错误、2 跳过，非一次全量测试。另有真实模型链路补验 1 项通过，但正常知识答案质量未通过，结果降为 HUMAN_REVIEW。这些是已有记录，本轮未重跑；完整浏览器协作、真实邮件收件及管理端能力仍需各自验收。

## 2. 分工与旧需求修订

| 事项 | 产品侧统一负责 | 管理侧负责 |
| --- | --- | --- |
| 账号与认证 | 账号存储、编码、注册/登录/退出/me、Cookie/JDBC Session、CSRF、密码恢复 | 平台用户列表、受控开户、停用/恢复、审计 |
| 空间与团队 | 个人空间初始化、团队、邀请、成员角色/移除、稳定存储归属 | 识别目标空间、校验被授予的管理范围 |
| 产品授权 | 调查/Run/文件/知识/资产/引用/Skill，含 worker/恢复/SSE | 提供状态、配额和可用性操作，协调接入 |
| 调查执行 | 原主循环、Guard、幂等回执、补答 CAS、串行调查及材料快照 | 脱敏运营查询、真实统计与占用管理 |
| 管理端 | 协调入口及共享方法合入 | 五页前后端、DTO、管理 API、运营审计和测试 |

旧文档的修改口径：

- “新建账号/认证/成员体系”改为复用产品侧底座。
- “暂不公开注册、不做团队”改为这些能力由产品侧实现，管理端不重复建设。
- “给账号/项目导入知识、开通 Skill”改为明确目标个人/团队空间，由服务器映射存储键。
- “两用户全部不能互读”改为无共同授权空间不能互读，团队内按 ADMIN/COLLABORATOR/VIEWER 访问。
- GET /api/me 改为 **GET /api/auth/me**。
- 旧 USER/ADMIN 示意中的平台角色与空间成员角色分开设计。

## 3. 身份与授权共同前提

| 维度 | 含义 | 用法 |
| --- | --- | --- |
| userId | 谁在操作 | principal、审计 actor、Run 操作人、停用对象 |
| workspaceId | 资源属于哪里 | 个人/团队空间，经解析器映射稳定 owner/project |
| 平台授权 | 可以做哪些运营操作、管理哪些目标空间 | 管理侧新增，复用当前登录身份 |

**AccessScope.canManage() 仅表示空间 ADMIN，不能作为平台后台门禁。** 建议使用明确的平台权限命名（如 PLATFORM_ADMIN，候选名），单独判定运营权限与目标管理范围。角色不能来自客户端、Header 或团队 ADMIN。

平台运营权限不默认开放用户问题、Prompt、完整结果、私有附件、摘录、工具参数、密钥和内部路径。受控正文阅读需单独明确目的、范围、授权和审计。管理员操作目标空间时仍记录自己的 actor，不冒充目标用户、不自动加入团队、不把管理员 userId 写成资源 owner。

| 撤销动作 | 影响范围 | 后台 Run 语义 |
| --- | --- | --- |
| 单次退出 | 当前会话及相关浏览器订阅 | 现有合同下不自动取消持久执行的 Run |
| 移除团队成员 | 指定团队资格 | 当前执行段 actor 失去写权后按撤权规则收口；其他空间保留 |
| 停用账号 | 全平台登录、会话、请求和该账号执行授权 | 全会话撤销，领取/模型/工具/结果/恢复/SSE 统一检查 |

停用账号保留历史作者和团队资源；其他有效成员继续按权限访问。被停用账号未决调查的交接规则待对齐，恢复不自动重放已取消/终止任务。

源码缺口明确存在：dt_account 尚无停用字段；AccountService.authenticate/get、WorkspaceScopeResolver、AgentRunRepository.canWrite/lockWriteAuthorization 等尚未检查账号状态。只改列表标签或删除 Session 不能完成停用。后续统一定义状态、并发锁顺序和生效时限。

## 4. 五个模块与页面结构

正式导航保持五项。“运行与用量”合并原型的运营总览/运行记录；“系统与审计”可使用页内标签。团队管理保留在产品工作台。

| 模块 | 页面与字段 | 操作 | 核心要求 |
| --- | --- | --- | --- |
| 用户管理 | 筛选、分页、开户表单、确认框；账号、名称、平台角色、状态、创建时间 | 开户、停用、恢复 | 统一服务创建个人空间；最后有效平台管理员保护；审计 |
| 运行与用量 | 时间/账号/空间/状态/错误筛选、统计卡、Run 表、脱敏详情、额度占用 | 查询、定位、查看额度及占用 | 独立白名单 DTO，真实统计，原子准入/释放 |
| 知识库管理 | 目标空间、导入表单、列表/详情；标题、型号、领域、版本、来源、处理/可用状态 | 导入、失败重试、停用、恢复 | 复用原流程，协调文档/release/材料范围 |
| Skill 管理 | 加载目录、只读详情、空间授权；ID、版本、状态、工具集 | 开通、停用、恢复 | 目录/安装/可用分开；未决 Run 与并发保护 |
| 系统与审计 | 依赖卡、检测时间、审计列表、运维说明 | 受控排障、审计定位 | 实际探针、未知如实显示、业务与成功审计一致 |

### 4.1 用户管理

管理开户复用 AccountService.register 的“账号+个人空间+成员”原子创建能力。**公开注册 Controller 会 signIn，不能从管理页面直接调用该 HTTP 接口，否则管理员浏览器会切换成新用户。** 管理应用服务应调用统一创建能力并组织成功审计。

平台管理员初始化、授予/撤销渠道及最后有效管理员保护需独立设计。团队 LAST_WORKSPACE_ADMIN 约束不能代替平台保护；公开注册不能自行取得平台权限。管理页是否允许授予平台角色待定。

停用/恢复要求原因、确认、真实状态回读和审计；保留账号历史、成员与作者。初始密码或一次性激活方案待定；密码不回显、不进日志，恢复流程继续复用统一服务。

### 4.2 运行与用量

- 列表：runId、空间最小标识、初始发起人、执行段 actor、状态、时间、活跃耗时、模型、稳定失败码、usageStatus。总历时与活跃执行耗时分别表达。
- 详情：仅输出状态迁移、模型/工具次数、耗时、尝试结果和定位 ID 等受控字段；查询投影只选择允许的列。
- AgentRunRepository 用户 RunView 含 answer_payload/pending_question；SafeAgentEventViews 的 RUN_ANSWER 含 summary/claims 等用户内容，均不能直接作为管理脱敏 DTO。
- 复用已有 RUN_MODEL、budget.used 记录，保留 UNKNOWN/PARTIAL/REPORTED；token 已报告不等于 cost 完整，费用覆盖率单独表达。
- 统计注明时间窗、时区、生成时间、已报告小计和覆盖率；未知保持 null/未知，WAITING_FOR_USER 不计失败，未决数与活跃 worker 数分开。
- Run 按 ID 去重；多人补答时账号用量如何按执行段归属需定约，不能把整条 Run 重复累计给多成员。
- 首期额度可用部署配置，无需套餐/支付；限制覆盖创建次数/并发、上传、导入、检索/Embedding、SSE 和存储。

### 4.3 知识库管理

复用 FileUploadService、KnowledgeImportService、KnowledgeSearchService。管理上传首期以 Markdown 为主，TXT 沿用已有文本入口；PDF/图片解析继续明确未实现。

分开展示文件处理、文档入库、管理可用、材料范围/发布状态。READY 仅为技术状态，不能代替来源许可确认或发布。

现有 KnowledgeOperations、KnowledgeCorpusService、scripts/knowledge_admin.py 已具备 release 导入/激活/回滚及并发可见性保护。release 回滚与单文档停用粒度不同；管理接口复用/提取受控服务，不向网页暴露命令、任意路径或可任填的 owner/project。

普通停用阻止新的向量/关键词/混合检索、附件关联、contextRefs 继承和新引用登记；已登记历史证据在原授权内仍可读。状态更新与新登记需并发协调；重复上传复用内容时保留停用状态。恢复要求处理、来源、空间权限、发布/release 条件仍成立。

产品第 5 组负责“调查附件→显式空间发布”。管理导入明确为空间知识，不能把调查私有附件自动收入公共列表。首期使用新版本导入；隐私删除/侵权紧急撤回另有受控语义。

### 4.4 Skill 管理

复用 SkillRegistry、SkillContextLoader、dt_skill_installation、dt_run_skill。授权对象为明确空间；只选当前已加载版本；工具权限受服务器、Skill 和安装许可共同约束。

installDefault 已使用 ON CONFLICT DO NOTHING，不覆盖停用；已有答案提交时授权记录共享锁。管理停用仍需补“锁授权记录→检查已选该 Skill 的未决 Run→更新状态”，运行选择也需参与同一并发保护。

已选择该 Skill 的 QUEUED/RUNNING/WAITING_FOR_USER 等未决 Run 存在时，普通停用被后端拒绝并返回范围内的定位信息，先完成或取消。尚未选择的排队 Run 后续不能选择已停用授权。重启不恢复停用授权。

页面管目录查看与空间开通/启停；内容走受控部署和版本变更。首期不做 ZIP 上传、在线编辑、热升级或多版本同时在线。

### 4.5 系统与审计

健康覆盖应用、PG、worker、队列、模型配置和近期调用，各项标注检测时间与范围。/api/health 不代表所有依赖健康；无证据显示未知；普通健康页不自动发起付费模型请求。

审计至少含 actor、目标账号/空间/资源、动作、结果、原因码、时间、关联 ID 和允许的状态前后值。业务写入与成功审计一致；失败/拒绝安全记录与业务回滚分开处理。Run 事件不能代替管理审计。

提供初始化、HTTPS/Cookie/CSRF、权限引导、备份/恢复、迁移及故障说明。旧本机知识 V18/V19 数据库有历史兼容问题，应保留数据制定迁移方案，不能 Flyway repair 掩盖历史。

## 5. 接口复用与新增候选

### 5.1 现有合同

1. GET /api/auth/csrf，保留同源 Cookie，按响应 headerName 携带 token。
2. POST /api/auth/login，成功后重新获取 CSRF；刷新 GET /api/auth/me。
3. 写请求携带当前 CSRF；POST /api/auth/logout 后重新获取匿名 CSRF。
4. DTSESSION 为 HttpOnly/SameSite=Lax/Path=/，默认 Secure，空闲 8 小时；浏览器不在 localStorage 保存密码或会话。
5. /api/auth/me 当前仅返回 userId/email/displayName，无平台权限字段；后台能力发现可新增管理专用接口，权限仍由服务器执行。
6. 产品集合/创建接口用 X-Workspace-ID，缺省仅个人空间；UUID 路径按资源实际空间授权，Header 必须匹配。管理目标空间另校验平台管理范围。

### 5.2 以下全部为候选，尚未实现

| 模块 | 候选接口 | 重点 |
| --- | --- | --- |
| 平台能力 | GET /api/admin/access | 当前平台能力、管理范围 |
| 用户 | GET/POST /api/admin/users | 分页最小字段、统一服务开户 |
| 用户状态 | PATCH /api/admin/users/{id}/status | 状态、原因、并发版本、撤销与审计 |
| Run | GET /api/admin/runs；GET /api/admin/runs/{id} | 独立脱敏字段、稳定排序/游标 |
| 用量/配额 | GET /api/admin/usage/summary；GET /api/admin/quotas | 时间窗、覆盖率、部署额度与占用；网页调整另定约 |
| 知识查询 | GET /api/admin/knowledge/documents；GET /api/admin/knowledge/documents/{id} | 管理范围、元数据详情；正文单独授权 |
| 导入/重试 | POST /api/admin/knowledge/imports；POST /api/admin/knowledge/files/{id}/retry | 可信目标空间、原有流程 |
| 知识状态 | PATCH /api/admin/knowledge/documents/{id}/status | 独立可用状态、并发引用保护 |
| Skill 目录 | GET /api/admin/skills；GET /api/admin/skills/{id} | 受控 ID、加载版本、内容读取 |
| Skill 安装 | GET/POST /api/admin/workspaces/{workspaceId}/skill-installations | 目标范围、版本、工具上限 |
| Skill 状态 | PATCH /api/admin/workspaces/{workspaceId}/skill-installations/{skillId}/status | 明确安装版本、未决任务和并发保护 |
| 系统/审计 | GET /api/admin/system/health；GET /api/admin/audit-events | 脱敏检测、有界筛选和定位 |

当前安全链对未定义 /api/** 默认 denyAll。实施管理路由时需接明确的平台权限判定，不能仅改成 authenticated。沿用现有 DTO/code 风格，避免为包装统一修改用户合同；账号停用、额度不足、有未决 Run 等错误码在 OpenSpec 统一。

## 6. 数据与代码组织

沿用 Java 21、Spring Boot、Spring JDBC、PostgreSQL/pgvector、Vue 3/TypeScript/Vite。

| 能力 | 增量方案 |
| --- | --- |
| 账号状态 | 扩展 dt_account；字段、原因、时间、版本按撤销合同确定 |
| 平台授权/管理范围 | 关联现有账号的独立结构，空间成员维持原语义 |
| 管理审计 | 新增受控记录，覆盖成功、失败和拒绝 |
| 配额/占用 | 候选策略和账本，键含明确归属、资源、周期与原业务幂等关联 |
| 知识可用 | 先核对现有 release/文档可见性，再补单文档管理状态 |
| Skill 授权 | 复用安装/选择表，补管理查询、操作与锁保护 |

建议前端独立放 frontend/src/admin/，含 AdminShell、五页、公共组件、管理 api.ts 和作用域样式；后端放 io.devicetruth.admin 下的 api/application/authorization/persistence。名称是建议。

Controller 管输入/HTTP；应用服务管权限、事务、审计和共享服务编排；仓储管白名单投影与持久化。限制分页、时间窗、枚举和长度；返回稳定 code/关联 ID，避免透出用户输入和底层异常。

frontend/src/main.ts 目前直接挂 App.vue，未引入 Router。入口由产品侧统一接入；管理侧不直接大改 App.vue/api.ts/timeline.ts/全局样式。GeneralAgentService、恢复/SSE、检索、SkillRuntime 共享方法一次由一方协调合入，保留 CLI/legacy、原主循环、租约/CAS、预算与事件合同。

## 7. 执行接入前的共享合同

| 合同 | 需要对齐 | 当前依据/建议 |
| --- | --- | --- |
| 源码/合入 | 双方 commit、共享类负责人、迁移编号 | 本机记录 22e5bab，双方确认待完成 |
| 平台权限 | 初始化、授予/撤销、目标空间范围、最后管理员保护 | 当前 Session/me 无平台权限；不复用团队 ADMIN |
| 管理开户 | 初始密码/激活方式、统一服务事务、成功审计 | 保持管理员当前会话 |
| 账号停用 | 状态、全会话撤销、执行检查点、生效时限、恢复、并发裁决 | SSE 已有 15 秒心跳检查，停用 SLA 尚未定约 |
| 知识启停 | 单文档/release/发布关系、管理范围、历史引用、紧急撤回 | 对齐产品第 5 组 |
| Skill 启停 | 锁顺序、选择/恢复竞争、未决任务范围、定位字段 | 已有部分授权锁，管理操作仍需补齐 |
| 用量/配额 | 账号/空间/平台归属、阈值、日界线时区、失败返还、等待/恢复、对账 | 建议等待释放活跃槽并保留未决占用，最终口径待定 |

配额接产品原幂等回执：先定位同键同载荷的原 Run，再判断新占用；同键异载荷冲突。补答恢复原 Run，不按新 Run 重复占用。双方共享原子准入、幂等释放和恢复对账，不另建提交去重体系。

## 8. 推进顺序与验收

| 阶段 | 工作 | 验收重点 |
| --- | --- | --- |
| A 规格 | 管理端独立 OpenSpec、权限矩阵、五页结构、脱敏 DTO、共享合同 | 职责不重叠，现有/候选接口明确，源码/迁移一致 |
| B 只读管理 | 页面、平台门禁、元数据投影、用量、健康、审计查询 | 普通用户/团队 ADMIN 无平台权限，分页稳定，字段白名单有效 |
| C 用户操作 | 统一开户、权限初始化、停用/恢复和审计 | 个人空间原子创建，管理员会话保留，全链撤权和最后管理员并发保护 |
| D 知识/Skill | 导入重试、启停、材料范围、历史引用、未决 Run 保护 | 检索/引用/选择真实生效，竞态及重启回归 |
| E 配额联调 | 占用/释放/对账、工作台接线、浏览器与真模型验证 | 最后额度竞争不超额，重试/补答/取消/恢复不泄漏或重复占用 |

现在可推进 A 和 B 的页面、DTO、查询/统计设计；B 真实管理 API 开放以平台门禁为前提。C–E 执行接入按共享合同就绪情况串接。

测试矩阵应覆盖：匿名/普通用户/团队管理员/平台管理员/协作/只读；无共同空间隔离和团队共享；已有 Cookie、SSE、worker 领取、模型等待中撤权、结果采纳及重启；知识各检索/附件/继承/新引用路径；Skill 未决 Run 和并发选择；配额跨实例竞争/同键重试/补答/等待/取消/恢复；审计故障与健康未知。

PG 使用隔离库；协议/竞态回归可显式用 scripted，产品联调默认真实外部模型。模型、embedding、数据库、模拟数据和现场证据分别记录，不把状态 COMPLETED 当作答案质量通过。

页面沿用深色侧栏、浅色内容、紫色强调、卡片/表格。五页支持加载/空/失败/无权/操作中/冲突状态，检查 1600×1000、1366×768、390×844、窄屏局部滚动、弹窗键盘关闭和焦点回归。原型用户问题标题、示例 KPI、顶部身份预览切换条不进入正式运营后台。

首期范围外：设备事实写入、完整模型治理、支付、Skill 编辑/市场/多版本热升级、PDF/OCR。

## 9. 本轮结果与下一步

本轮读取两份 Downloads 文档、内嵌原型、仓库要求、相关 OpenSpec 合同/任务/验收记录和关键源码；只新增梳理文档与上下文入口，未修改业务代码、数据库、服务或本机凭据配置，未执行产品测试或付费请求。

下一步建议完成阶段 A：将平台权限、五页结构、脱敏 DTO、状态/错误和共享接入合同落实为管理端独立 OpenSpec。随后先实施平台门禁与只读管理，再接用户状态、知识/Skill 启停和配额。

### 后续落地（同日）

用户随后授权落实阶段 A，已创建 [add-platform-admin-console](../../openspec/changes/add-platform-admin-console/proposal.md)。详细设计以其 [权限矩阵](../../openspec/changes/add-platform-admin-console/permission-matrix.md)、[五页结构](../../openspec/changes/add-platform-admin-console/page-structure.md)、[脱敏接口合同](../../openspec/changes/add-platform-admin-console/admin-api.md) 和 [共享接入合同](../../openspec/changes/add-platform-admin-console/shared-contracts.md) 为准。本文上方“候选/待定”是梳理当时的记录，后续已作出的设计决定见该 change；仍待双方接线确认的事项保留在 design.md 的 Open Questions。此时业务实现仍为 0/40 项。
