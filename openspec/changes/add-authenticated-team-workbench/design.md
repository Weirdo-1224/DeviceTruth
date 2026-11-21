# Design

## Context

动机与范围见 [proposal.md](proposal.md)。用户在 2026-11-19 认可完整原型并授权进入 OpenSpec；本轮只规划，不实施。

### Observed baseline

| 已查阅对象 | 实际状态及对本变更的约束 |
|---|---|
| `pom.xml` | Java 21、Boot 3.5.16、Spring JDBC、PG、Flyway；尚无 Spring Security/Session 依赖，不升级整套技术栈 |
| `GeneralAgentService` | 单例持有配置注入的 `ServerIdentity(ownerId,projectId)`；Controller 无用户 principal。不能仅加登录页面，或把单例 identity 改为可变的“当前用户” |
| `ConversationRepository`、V1–V18 | owner/project 是现有隔离键，存在贯穿消息、Run、引用、文件和设备的复合外键；历史作者并未独立记录 |
| `submit/reply` | 已有会话行锁、UUID 幂等键、补答三元组及 CAS；普通提交目前没有服务端单会话活动轮拦截，现有前端禁发不能保护多人并发 |
| `RunEventStreamService` | 固定 identity，先历史后订阅再补读、专用线程及有界连接已实现；身份与撤权必须贯穿整个 relay，而不只建立连接时检查 |
| `FileUploadService` | 上传后自动异步导入；`readyAttachments` 要求 PARSED 文档，最多 5 个 fileId。新附件不能继续无区别进入空间检索 |
| `KnowledgeSearchService` | SQL 先按 owner/project 过滤，再进行向量/关键词/RRF；新作用域必须进入所有召回、直接原文及引用登记路径 |
| Vue `App.vue/api.ts` | 已有消息/补答、会话分页、轮次选择、取消、SSE 与安全轮询；复用 `scope.ts`、`timeline.ts`、`diagnosisViewModel.ts`，不再造第二套调查状态机 |
| OpenSpec | 主 specs 为空；既有未归档变更记载实际演进。`devicetruth-pro-workbench-mvp` 尚有两个可选任务未勾选，本次不续跑它们 |

既有 task-loop、interaction、availability 等变更的调查对象、引用子集、有限结果和状态恢复行为继续保留。当前 `AnswerGuard` 对 UNKNOWN 的保守降级按实际代码及最新可用性变更保留，不恢复早期 delta 中更严的组合拒绝。

## Goals / Non-Goals

**Goals:**

- 在原有 owner/project 存储边界之上引入可信操作人及空间成员关系，避免逐表拆毁既有外键链。
- 让身份、材料和执行上下文在 HTTP、后台 worker、恢复、工具与持续流中保持同一语义。
- 用已确认原型驱动产品，但把所有演示身份开关和模拟执行移除；契约先行、后端纵向切片、前端接真服务。
- 精确保留旧入口和旧数据；新团队用法的串行与版本前提不反向改写既有内部测试/legacy 合同。

**Non-Goals:**

- 不引入微服务、Redis、消息队列、第二个 Agent 主循环、通用 ACL 规则平台或实时协同编辑引擎。
- 不把用户讨论建模成已确认设备事实；不跨空间搬迁调查；不增加 SSO、审批派工、PDF/OCR。
- 不以原型检查通过替代真实身份授权、PG 并发、邮件及真实模型验收。

## Decisions

### D1. 登录采用同源 Cookie 会话，不自研 JWT 体系

选择 Boot 3.5.16 管理版本的 `spring-boot-starter-security`、`spring-session-jdbc`，测试使用 `spring-security-test`；邮件沿用 Spring Mail 的 SMTP 适配。采用 Spring Security `PasswordEncoder` 的自适应口令编码，选择无需额外加密库的 PBKDF2，并保留编码标识供后续升级；不使用普通摘要当密码编码。

同源 SPA 使用 HttpOnly、正式部署 Secure、SameSite=Lax 的会话 Cookie；身份变更轮换 session，写请求包含 CSRF。提供读取 CSRF 凭证的 GET 接口，登录/退出后重新取得；前端不把会话凭证存入 localStorage。会话由 PG 保存，Flyway 管理相应表，不在启动时自动重建正式库。仅在 product profile 装此安全链；CLI、legacy 和无 product 的健康探针不受其默认登录页/全局安全自动配置干扰。

规划默认值：密码 12–128 字符，邮箱 trim 后按统一小写标识匹配；口令不 trim；重置令牌 30 分钟，邀请 7 天，会话空闲超时 8 小时，全部可配置。接口明确字段长度和错误反馈。原型没规定这些数值，作为首版工程默认，不改变已定交互。

**替代方案**：自签 JWT + 前端 token 存储增加撤销、刷新和泄漏处理；内存会话不支持重启/多实例。选择框架会话以适配已有 PG 和同源 SSE。

官方依据：[Boot 3.5 的 Session 配置](https://docs.spring.io/spring-boot/3.5/reference/web/spring-session.html)、[Security 6.5 的 SPA/CSRF 说明](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)、[PasswordEncoder](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html)。依赖由现有 BOM 解析，不照搬 Boot 4 的 starter 名称或手工升级版本。

### D2. 密码重置和邀请使用一次性邮箱凭证

新增账号、重置令牌、团队邀请持久记录；仅保存高熵令牌的验证摘要和有效期，原始凭证仅在生成邮件时使用，不出现在普通 API、日志、模型上下文。

邀请必须绑定空间、规范化邮箱、受控角色、发起管理员及状态；持有效凭证且登录邮箱匹配才可原子加入。新用户可注册后加入；已有成员重复接受不提升角色。邀请本身证明收件箱控制权，公开注册只创建个人空间，不凭未确认邮箱加入团队。

密码重置提交采用统一外部应答；重置成功消费令牌、撤销该账号所有会话并要求重新登录。限流覆盖登录、注册、重置和邀请发送，使用有界并可过期清理的计数；不能依赖无限增长的内存 Map 或客户端角色限制。

SMTP 使用环境配置及有界连接/读取超时。记录 PENDING、ACCEPTED_BY_PROVIDER、FAILED 等投递状态，和邀请 ACCEPTED 分开；交给邮件服务不等于收件箱已收到。发送失败可显式重发，生成新令牌使旧令牌失效；不增加消息队列。全局邮件配置不可用时统一返回服务不可用，不能仅对存在的邮箱暴露不同结果。测试替身不计真实邮件验收。

### D3. 操作人和存储归属分开，不共享可变单例身份

新增拟议 `AccessScope(actorUserId, workspaceId, role, storageIdentity)`，由请求会话和成员表解析。集合/创建及非全局唯一资产编号路由通过 `X-Workspace-ID` 选择空间，无 Header 的已登录旧调用默认个人空间。包含全局资源 UUID 的路由先从资源元数据确定所属空间，再验证成员资格；若附带 Header 则必须匹配，不匹配返回 404，不能以 Header 扩权。

现有浏览器原生 EventSource 不能设置任意 Header，因此 `/api/runs/{id}/stream` 使用同源会话 Cookie及 Run UUID 解析并验证空间，不要求 X-Workspace-ID，不将认证令牌放进 URL。建立流和后续每批输出均校验该实际空间，保留 Last-Event-ID 续传。前端切换空间仍必须关闭旧流，迟到事件按 generation 丢弃。

每个新 workspace 具有独一的 owner/project 存储键对，原仓储过滤和外键继续使用该键对。`owner_id` 在新路径是资源归属键，不冒充消息作者。新增 `created_by_user_id`、`initiated_by_user_id`、`execution_actor_user_id` 及正式消息 actor 字段保存真实人员；历史行可空并显示“历史作者未知”。会话绑定 workspace，Run 从其会话取得空间。

`GeneralAgentService` 增加显式 scope 的入口，原构造器/内部方法保留原有固定身份调用用途。新 HTTP 路径全部使用显式 scope，不能提供匿名 fallback。后台执行不读取请求 ThreadLocal：Run/检查点持久化资源范围、当前执行段 actor 和材料快照，`dispatch`、恢复、查询和保存都从记录恢复。ToolContext 在新路径区分 actor 与 storage scope；旧构造/调用用途保持可用，工具目录和权限不能由模型扩充。

`SkillRuntime` 当前也在构造时固定 identity，并拒绝其他 ToolContext，需同步改为按已验证存储范围建立 run 会话和读取安装/选择；共享只读 SkillRegistry，不共享可变 run 选择。新空间可按服务器默认清单初始化安装，工具上限仍由服务器配置决定，不能复制其他空间的安装授权或绑定演示设备资料。原固定身份构造用于旧内部调用时保持原行为。

**替代方案**：把 owner_id 改成每次发言人的 userId 会让团队同一调查跨归属并破坏外键；把 identity 改成 request-scoped Bean 不能覆盖异步 worker。选择显式上下文及稳定资源键。

### D4. 角色作用于空间，每次敏感动作及输出重新授权

| 动作 | 个人本人 | 团队管理员 | 协作成员 | 只读成员 |
|---|---|---|---|---|
| 读取调查/资料/讨论/报告 | 是 | 是 | 是 | 是 |
| 创建、讨论、上传、发布、发起/补答 | 是 | 是 | 是 | 否 |
| 取消当前执行段 | 本人 | 是 | 仅本执行段发起人 | 否 |
| 成员、角色、团队名称 | 不适用 | 是 | 否 | 否 |

团队管理员不越过其他空间。成员更新及保留至少一位管理员在事务内锁定空间/成员关系；不靠按钮判断。新增成员移除入口保留历史姓名/用户 ID，不删除其调查。

401 表示登录失效；当前空间已知但无写权限为 403；资源不存在或属于无权空间为 404；并发/版本为 409。SSE 建立前授权，在每批业务数据发出前及心跳周期复核会话和成员资格，失效先终止输出，不用匿名轮询降级。后台每步工具及最终采纳前检查当前执行段 actor 的编辑权限；撤权按 `AUTHORIZATION_REVOKED` 收口，审计写入由受控宿主完成。补答更换 execution actor，初始发起人和各次补答人仍各自保留。

### D5. 协作复用同一会话和 Run 主循环

新增 `dt_investigation_discussion` 保存不可变原文、作者、版本、时间及分页序号，不混入 `dt_message` 或知识切片。讨论使用独立序号，不因单纯发表讨论改变正式分析输入的期望版本。首版不实现编辑历史/实时共编；引用传 `{discussionId,version}`，服务器锁内解析并保存原文快照和作者。不得把讨论 ID 塞进知识 `contextRefs`。

消息协议保持 schemaVersion=1，追加可选 `discussionQuotes` 和 `expectedConversationVersion`，不允许 owner/project/角色字段。新团队路径要求期望版本；旧个人/内部调用原字段仍可用。原 UUID `Idempotency-Key` 与重复请求 409 合同保留，先判已处理请求，再判版本及忙碌，不能生成新 key 自动重发。

新认证空间普通提交：验证身份 → 会话行锁 → 幂等/期望版本 → 查询任何 QUEUED/RUNNING/WAITING → 解析讨论/附件/引用 → 写正式消息和输入快照 → enqueue → 提交事务 → 通知/dispatch。复用既有会话锁，可跨进程裁决；无需另造分布式锁或删掉旧重复 Run 来建立全局唯一索引。

补答沿原三元组和 CAS，在相同锁顺序中竞争消费，只恢复同一 runId。若存在迁移前多个未决轮，可按明确目标补答/取消，但不能新开普通运行；旧数据全部保留。新轮序号按历史尝试递增，包括取消轮；Run 状态和最新可用结果分别表达。

每次执行段固定正式消息边界、所引用讨论快照、附件文档版本及授权 contextRefs；worker 不扫描“最新所有讨论”或后加附件。补答可明确确认新增材料并建立新的执行段快照。知识库仍由工具主动查询并登记实际取得的版本；发布不会主动推送到运行上下文，也不能改变已登记引用的原文。

团队讨论和列表更新使用有界分页/轻量同步，不建设 WebSocket。运行事件继续复用现有 Run SSE；跨浏览器依靠会话 version、列表回读和有界同步发现新的 Run。

### D6. 新增调查附件入口，不改旧显式知识上传的默认含义

新增调查级上传和附件列表入口，写入 conversation 关联、上传者及 `CONVERSATION_ATTACHMENT` 使用范围。复用存储、解析、异步重试、embedding 与索引，但所有向量/关键词候选 SQL、直接 excerpt 和引用登记都增加资格条件：同 storage scope 且属于已发布空间知识，或本执行段已确认的本调查附件版本。

复用原 `/api/files` 和 `/api/knowledge/*` 的显式知识导入用途，不把旧调用方静默改为必须带 conversationId。新的前端调查“添加资料”改走调查级入口。解析完成不等于发布；PDF 仍按现有 OCR_REQUIRED/未支持状态处理。

新增 `dt_knowledge_publication` 关联不可变文档版本、空间、名称、适用范围、发布人和原作者；显式发布是新增可复用授权，不是移动/重写原附件。发布幂等且依赖有效解析/索引，不成功时不扩大可检索范围。知识库列表与受控原文读取补齐 UI 所需接口，不回传文件路径。

同团队成员可以主动进入调查 A 阅读 A 的附件；但调查 B 的 Agent 不能以“用户能看 A”为由检索其未发布附件。人员读取权限和工具材料范围分开验证。

### D7. 前端按已确认原型组合，复用有效能力

保留 Vue/Vite，不引入新的状态库。以轻量页面状态组合拟议 AuthPages、WorkspaceShell、TeamMembers、InvestigationDiscussion、KnowledgeLibrary 等组件；`api.ts` 统一 Cookie、CSRF、空间选择和错误处理。实际文件粒度由实施者按现有组件决定，不能把逻辑重新堆成单个巨大 App。

数据加载 generation/缓存 key 从 conversation 扩展到 account/workspace/conversation/run/version；离开空间/退出关闭 EventSource 和轮询，晚到响应丢弃。草稿按账号/空间/调查分开，默认当前浏览器会话内私人保存，不上报团队、退出清理；冲突或有限结果不会清空未接受内容。

当前判断与输入靠近，讨论、正式分析记录、过程分别可见；证据与报告按需打开。复用原候选分类、完整结果、报告打印/另存 PDF、调查对象一致性与历史选择。原型只演示有限结果，不意味着删除有候选答案视图。技术原因折叠查看，但影响当前操作的失败与风险保留在相关位置。

新建仍首次提交才创建会话；未选设备允许调查。按钮、Enter 键及处理函数共享角色/活动轮判定；只读 UI 是反馈，真正授权在服务端。

### D8. API additions

以下为实施合同基准，响应新增字段采用向后兼容扩展，最终 DTO 在对应任务内与测试同步落地：

| API | 用途及边界 |
|---|---|
| `GET /api/auth/csrf`、`GET /api/auth/me`、`POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/logout` | 登录/注册/退出与用户读取；登录注册不接收空间权限 |
| `POST /api/auth/password-reset-requests`、`POST /api/auth/password-resets` | 邮件请求与一次性消费；匿名应答不枚举账号 |
| `GET/POST /api/workspaces`、`PATCH /api/workspaces/{id}` | 可访问空间、创建团队与管理员改名 |
| `GET /api/workspaces/{id}/members`、`PATCH/DELETE /api/workspaces/{id}/members/{userId}` | 成员读取、改角色、移除；个人空间不可变团队 |
| `POST/GET /api/workspaces/{id}/invitations`、`POST /api/invitations/accept` | 管理员创建/查询邀请，匹配邮箱接受 |
| `POST /api/workspaces/{id}/invitations/{invitationId}/revoke`、`/resend` | 管理员撤销/重发；凭证重新生成，旧凭证失效 |
| 原 `/api/conversations`、`/messages`、`/api/runs/*` | 登录并解析空间，保留 UUID/补答/SSE 合同；新增作者、当前执行段操作者及讨论引用字段 |
| `GET/POST /api/conversations/{id}/discussions` | 分页讨论；与模型消息严格隔离 |
| `GET/POST /api/conversations/{id}/attachments` | 调查附件列表/上传，不发起 Run |
| `GET /api/files/{id}/content`、`POST /api/files/{id}/publications` | 受控原文及明确发布；按原文件空间与使用资格授权 |
| `GET /api/knowledge/documents` | 当前空间已发布文档分页列表，保留原知识检索/导入接口 |

分页游标绑定空间和筛选，不允许拿其他空间游标泄露列表。错误码区分 `CONVERSATION_BUSY`、`CONVERSATION_VERSION_CONFLICT`、既有 `STALE_OR_UNRELATED_REPLY`、`DUPLICATE_REQUEST`、`AUTHORIZATION_REVOKED`、邮件失败及解析未就绪。禁止自动把任何 409 改成新一轮。

## Risks / Trade-offs

- **固定身份贯穿范围广** → 实施首先完成 request/worker/recovery 的 scope 接力与授权测试，再开放协作，不能只修 Controller。
- **角色撤销与长连接/模型等待竞态** → 每次输出前/工具前/采纳前重查；不在长模型等待中持成员锁或会话事务。
- **多成员提交冲突** → 复用 PG 行锁和版本/CAS；只有获胜者 dispatch；草稿保持私人且禁止自动排队。
- **附件解析复用导致误召回** → 将范围条件放入两个召回通道和直接原文路径，用同内容不同空间/调查的反例测试，发布保留来源性质。
- **邮件传输中断** → 记录真实投递状态、显式重发并旋转凭证；真实邮件验收与本地替身分列。
- **历史资源归属不等于真实用户作者** → 维护者显式认领，旧作者不补造；不能把首个注册用户变成演示数据所有者。
- **多个活动变更的规格尚未同步主 specs** → 本变更明确承接最新已实现行为，主 specs 同步/历史归档另行执行，不能用此规划掩盖旧未完成任务。

## Migration Plan

1. **仅规划阶段**：固化原型与已确认决策，生成五项 delta、设计和任务；所有实施任务未勾选。本文件不触发运行环境、数据库或凭据更改。
2. **实施新增表与列**：从当前最大 Flyway 版本之后追加迁移，不修改 V1–V18。新增 user/workspace/membership/invitation/reset/session/discussion/publication 表及必要的 scope、actor、输入快照字段；历史作者允许为空。新 workspace 使用新存储键对，旧数据仍按原键保留。
3. **认领演练**：复用现有迁移/脚本方式提供一个显式维护命令，输入旧 owner/project 和指定管理员，创建独立的“旧资料认领团队空间”，绑定原存储键对并回填关联，不默认合并到已有非空空间。先 dry-run 核对会话、Run、事件、引用、文件、文档和设备数量/关联/实际存储文件，再在备份及授权下执行。未认领资料不对新用户开放。
4. **活动运行与旧作者**：部署前停止新写入并盘点/尽量排空 worker；保留 WAITING 和多未决记录。有旧非终态记录时逐项记录其租约/恢复安排，不强制取消或删除。无用户 actor 的旧路径仅允许显式系统维护范围恢复；用户接续时记录新的 execution actor，不伪造初始发起人。移交前必须验证旧恢复路径。
5. **切换与验收**：scope/授权/并发/附件回归通过后，将 product 的匿名固定身份 HTTP 切为认证路径，前端接真实 product；配置 SMTP、HTTPS 和会话存储。CLI/legacy 独立回归。真实模型和 embedding/资料性质按实际分别记录。
6. **恢复路线**：保留切换前部署物、数据库和文件备份及认领映射清单。停写后可回退到仍理解新 scope 的安全兼容版本；不能把创建了多空间数据的库直接交给旧匿名 product。需回到旧程序时，恢复匹配的库/文件备份到隔离实例并检查健康与入口，旧备份后的新数据先导出留存，不向旧程序暴露新空间。实际操作需在实施验收时批准，规划不执行。

## Verification Strategy

- 复用 `GeneralAgentApiTest`、`ResourceAuthorizationTest`、`AgentLifecycleTest`、`AgentStreamTest`、`RunRecoveryTest`、`KnowledgeSearchTest` 及现有上传测试；新增针对 auth/workspace/discussion/material 的明确断言，不建设独立测试平台。
- 真实 PG 测两用户/两团队/个人隔离、并发普通提交、同三元组补答竞争、最后管理员并发、索引/发布失败、重启恢复和撤权中的 SSE/worker。协议替身测试状态机，不能当作真实模型结果。
- 前端沿用 Vitest、类型检查、构建及既有浏览器恢复脚本；浏览器使用两个独立账号上下文，覆盖权限失效、旧空间晚到、讨论引用、原轮补答、无候选和候选结果、报告打印及草稿保留。
- 真实产品验收默认外部模型，记录模型名、Run ID、实际引用、DB/embedding/资料性质；邮件使用真实 SMTP 核对服务接受/失败与链接生成，邀请和重置协议流程在隔离环境验证，实际收件由用户后续人工复核。HTTPS/Cookie/CSRF 独立检查。缺任一实际环境只阻塞对应验收，不改用模拟并标通过。
- 本次计划不执行业务测试。原型历史 47+2 项检查及失败修复见 `prototype/review-history.md`；用户认可原型不等于后端已验收。
