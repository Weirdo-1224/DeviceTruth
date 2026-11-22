# 管理端脱敏接口合同 v1

状态：本 change 的接口合同，实施与验证状态见 acceptance.md。类型和白名单以下文为准；产品 /api/auth/*、/api/workspaces/* 继续沿用原合同，不改统一返回包装。

后续认证合同变更：`separate-user-admin-login` 从 V30 起用独立 `/api/admin/auth/csrf|login|me|logout`、DTADMINSESSION 和 ADMIN 账号用途替代本文原“管理端复用用户 Session”部分。匿名只允许管理 csrf/login，其他接口仍校验管理域、ACTIVE、ADMIN 和 PLATFORM_ADMIN；USER 不能通过重命名 Cookie 获得管理身份。正文业务接口、字段白名单和空间 grant 不变，原验收保留历史含义。详见 `docs/operations/login-realms.md` 和该后续 change 的规格。

## 1. 通用协议

- 同源 Cookie Session + 现有 CSRF；不增加 Bearer token、管理密码或 /api/me。
- 全部管理接口要求 ACTIVE + PLATFORM_ADMIN；知识/空间 Skill 再查目标 grant。先鉴权再返回资源、readiness 或冲突信息。
- 路径/参数 ID 用 UUID，skillId 用 Registry 已登记 ID；不接受 ownerId/projectId/actorUserId/platformRole 或任意文件路径作为扩权参数。表格中的 actor 筛选指查询条件，绝不改变操作者身份。
- 请求体严格拒绝未定义字段；查询参数也按端点白名单验证。失败不回显 body、q、底层异常或 URL 凭证。
- 服务端生成 requestId，所有管理响应包含 X-Request-ID；错误 body 为 {code,requestId,details?}。details 仅允许本文定义的安全冲突信息。
- 时间输出 UTC ISO-8601；筛选 [from,to)，from/to 必须带偏移，统计另有 IANA timezone。默认最近 7 天、Asia/Shanghai；单次窗口上限 31 天，超范围 400 INVALID_TIME_RANGE。该默认是提案设计值，部署可显式调整并在响应回显生效值。
- 列表 limit 默认 20、最大 100，固定稳定排序。返回 Page<T>={items:T[],nextCursor:string|null,snapshotAt:Instant}；不默认 COUNT 全表。
- 游标绑定端点、principal、平台/范围授权 revision、所有筛选、排序、snapshotAt 和最后排序键，经服务端完整性校验；错用/篡改/过期返回 400 INVALID_CURSOR。每页重新鉴权；snapshotAt 限定新增行高水位，不承诺跨页状态不变。
- 缺省时间窗在第一页固化并放入游标；后续未显式给出窗口时沿用第一页值，不能重新按当前时间计算“最近7天”导致正常翻页冲突。显式改变筛选仍拒绝复用游标。
- 全局管理接口拒绝 X-Workspace-ID；空间接口使用显式 workspaceId，Header 若存在必须匹配，否则 404 RESOURCE_NOT_FOUND。管理端无“默认个人空间”。
- 状态 PATCH 提交 {status或enabled,expectedVersion,reasonCode}；reasonCode 枚举 OPS_REQUEST/SECURITY_RESPONSE/ACCOUNT_RECOVERY/DATA_OUTDATED/PERMISSION_CHANGE/OTHER，无自由文本。expectedVersion 非负整数。
- 首期不提供 raw/debug/includeContent/fields/export 等绕过字段白名单的开关。前端隐藏不能代替服务端裁剪。

### 错误合同

| HTTP | code | 使用位置 |
| --- | --- | --- |
| 400 | INVALID_ADMIN_INPUT / INVALID_TIME_RANGE / INVALID_CURSOR | 输入、窗口、游标 |
| 401 | AUTHENTICATION_REQUIRED | 会话不存在/失效；停用已删会话也落此码 |
| 401 | ACCOUNT_DISABLED | 有已认证 principal 但实时发现停用；不用于匿名登录枚举 |
| 403 | PLATFORM_ADMIN_REQUIRED / ADMIN_CAPABILITY_REQUIRED | 平台门禁/非空间能力拒绝 |
| 403 | ACCESS_DENIED | 复用现有安全链 CSRF 拒绝；其 body 可无 requestId，由响应 Header 定位 |
| 404 | RESOURCE_NOT_FOUND | 不存在/无目标空间管理范围/Header 冲突 |
| 409 | EMAIL_ALREADY_REGISTERED | 复用账号规范化唯一性 |
| 409 | STATE_VERSION_CONFLICT / LAST_PLATFORM_ADMIN | 状态竞争/最后有效平台管理员 |
| 409 | SKILL_HAS_UNRESOLVED_RUNS / SKILL_VERSION_UNAVAILABLE | 未决 Run/当前加载版本不匹配 |
| 409 | KNOWLEDGE_NOT_RESTORABLE / RETRY_NOT_ALLOWED | 恢复前提不满足/非可重试处理状态 |
| 429 | QUOTA_EXCEEDED | 已接通的资源配额拒绝；details 仅含 resource、dimension、retryAt（可空） |
| 503 | INTEGRATION_NOT_READY / QUOTA_POLICY_NOT_CONFIGURED | 对应执行接入/部署策略未就绪 |
| 503 | ADMIN_AUDIT_UNAVAILABLE | 成功审计无法持久化，业务回滚 |

统一账号输入规则及 INVALID_ACCOUNT_INPUT/INVALID_CREDENTIALS/AUTH_RATE_LIMITED 沿用原 auth 合同。登录时停用账号与错误凭据均为 401 INVALID_CREDENTIALS。管理开户不自动重试；响应丢失后通过列表定位，重复邮箱 409，不再次创建个人空间。

## 2. 权限与范围

### GET /api/admin/access

返回 AdminAccess={userId,platformRole:"PLATFORM_ADMIN",grantRevision,capabilities:string[],workspaceCapabilities:WorkspaceCapability[],features:FeatureReadiness[]}。

- WorkspaceCapability={workspaceId,capabilities:string[]}，仅当前有效 grant；数量较大时 access 只报告全局能力，范围列表通过下方分页接口获取，不截断后假装完整。响应另含 workspaceCapabilitiesComplete:boolean。
- FeatureReadiness={feature:"USER_CREATE"|"USER_STATUS"|"KNOWLEDGE_WRITE"|"SKILL_WRITE"|"QUOTA_ENFORCEMENT",state:"READY"|"PENDING_INTEGRATION"|"UNCONFIGURED",reasonCode:string|null}。
- 首期全局 capabilities 由平台角色固定；空间权限独立。features 不授予权限，不能凭 READY 绕过 capability。

### GET /api/admin/workspaces

参数 capability（KNOWLEDGE_READ/KNOWLEDGE_MANAGE/SKILL_READ/SKILL_MANAGE，可选）、limit、cursor。返回 Page<AdminWorkspace>，字段 workspaceId、kind(PERSONAL/TEAM)、name、capabilities；按 workspaceId 升序。

只列显式授权空间；不返回成员邮箱、存储键或全部私有空间。name 是被管理目标的元数据，不用于全局 Run 列表。

## 3. 用户管理

### GET /api/admin/users

参数 q（规范化邮箱/显示名称检索，最多 100 字符）、status、platformRole、limit、cursor。按 createdAt DESC,userId DESC。

AdminUser 白名单：userId、email、displayName、platformRole(PLATFORM_ADMIN/NONE)、status(ACTIVE/DISABLED)、statusVersion、createdAt、statusChangedAt。用户列表 capability 允许完整邮箱用于运营定位；无密码编码、重置/邀请令牌、Session、成员和资源正文。

### POST /api/admin/users

输入 {email,displayName,initialPassword}；email/名称/密码规则沿用 AccountService。只允许普通开户。

201 返回 AdminUserCreated={userId,email,displayName,personalWorkspaceId,status:"ACTIVE",platformRole:"NONE",statusVersion,createdAt,auditId}。调用共享创建单元，与成功审计原子一致，不调用 signIn、不旋转管理员会话/CSRF。不返回初始密码或发送成功承诺。个人空间不认领旧数据，不继承其他空间授权。

### PATCH /api/admin/users/{userId}/status

输入 {status:"ACTIVE"|"DISABLED",expectedVersion,reasonCode}。200 返回 {user:AdminUser,changed:boolean,auditId}。同状态且版本匹配为 changed=false/NO_CHANGE；过期版本总是 STATE_VERSION_CONFLICT，details={currentVersion}。

停用原子撤销会话及待消费恢复凭证，持续授权按 shared-contracts.md 接通；恢复保持原密码/成员/历史，不恢复会话或重放 Run。自停用成功响应可返回，后续请求必须失效。最后有效平台管理员即使自停用也拒绝。

## 4. 运行与用量

### GET /api/admin/runs

参数 from、to、workspaceId、initiatedByUserId、executionActorUserId、status、failureCode、limit、cursor。未指定 workspaceId 表示全平台脱敏元数据，不代表可阅读内容。按 requestedAt DESC,runId DESC。

AdminRunSummary：

| 字段 | 类型/含义 |
| --- | --- |
| runId、workspaceId | UUID；历史未知 workspaceId=null |
| scopeState | BOUND/LEGACY_UNCLAIMED |
| initiatedByUserId、executionActorUserId | UUID|null，未知不回填 |
| status | 原 Run 状态枚举 |
| requestedAt、startedAt、finishedAt | Instant，可空字段保留 null |
| activeDurationMs、wallDurationMs | long|null；前者不含等待，后者截至 snapshotAt/finishedAt 的总历时 |
| models | string[]，只读已登记模型名 |
| failureCode | string|null，仅稳定宿主白名单错误码 |
| modelAttemptCount、toolCallCount | long|null，有持久证据才返回计数 |
| usage | UsageCounters，见下方 |

不得含 conversationTitle、goalText、pendingQuestion、answer、summary、claims、附件/引用正文、工具参数、Prompt、原始 provider 错误。

### GET /api/admin/runs/{runId}

返回 AdminRunDetail={run:AdminRunSummary,eventCounts:{model,tool,phase},usageAttribution:"COMPLETE"|"INCOMPLETE"|"UNKNOWN"}，计数可空。详情保持同一白名单，不复用用户 RunView。

### GET /api/admin/runs/{runId}/attempts

参数 kind=MODEL|TOOL（必填）、limit、cursor；按 seq ASC。返回 Page<AdminAttempt>。

AdminAttempt={seq,recordedAt,kind,model:string|null,toolName:string|null,attemptNumber:int|null,outcome:string,errorCode:string|null,durationMs:long|null,retryable:boolean|null,inputTokens:long|null,outputTokens:long|null,cost:CostAmount|null}。

仅从模型/工具审计受控列读取。outcome/errorCode 为有限服务端码，不能直接拷贝供应商文本。无 tool arguments、reference 内容/ID 列表、原始 payload 或 RUN_ANSWER；工具引用仅可用计数表示，如需新增字段须扩展本白名单。

### GET /api/admin/usage/summary

参数 from、to、timezone、workspaceId、initiatedByUserId（按初始发起人筛 Run，不宣称是尝试费用归属）、groupBy=NONE|DAY|WORKSPACE。默认 NONE。

AdminUsageSummary={window:{from,to,timezone},generatedAt,runCount,statusCounts,modelAttemptCount,usage:UsageCounters,amountBuckets:CostBucket[],unknownCurrencyCostCount,attributionStatus,unattributedAttemptCount,groups:UsageGroup[]}。

- UsageCounters={status:UNKNOWN|PARTIAL|REPORTED,inputTokens:long|null,outputTokens:long|null,attemptCount:long|null,tokenReportedAttemptCount:long|null,costReportedAttemptCount:long|null,tokenCoverage:number|null,costCoverage:number|null}。
- CostAmount={amount:decimal-string,currency:ISO-4217-string}；币种未知时 cost=null，不默认 CNY/USD。
- CostBucket={model,currency,amount:decimal-string,reportedAttemptCount}，同币种才求和；只反映已报告小计，非正式账单。
- UsageGroup={key,runCount,statusCounts,usage:UsageCounters,amountBuckets:CostBucket[]}。
- statusCounts 必含 WAITING_FOR_USER；失败数只统计实际 FAILED，COMPLETED 不表示答案质量通过。
- 无匹配 Run 可真实返回 runCount=0；没有模型尝试时覆盖率=null。输入/输出部分缺失时保留可证明小计并标 PARTIAL，不以 0 填未知。
- 多人补答若无可靠执行段归属，不发布伪精确的账号尝试费用统计；attributionStatus=INCOMPLETE 并单列未归属数。

### GET /api/admin/quotas

参数 dimension=ACCOUNT|WORKSPACE|PLATFORM、subjectId（前两者必填）、resource、limit、cursor。按 dimension,subjectId,resource 升序。

QuotaView={dimension,subjectId:string|null,resource,configuredLimit:long|null,consumed:long|null,reserved:long|null,remaining:long|null,periodStart:Instant|null,periodEnd:Instant|null,timezone:string|null,unit,policyVersion:string|null,enforcementState:READY|PENDING_INTEGRATION|UNCONFIGURED,reconciliationState:CURRENT|REQUIRED|UNKNOWN,measuredAt}。

不配置在线额度调整/支付接口。没接入对账或策略时相关字段为 null，不展示假剩余额度。用户已明确本轮保留产品同键提交 409 DUPLICATE_REQUEST，完整准入移交依赖 change；本接口不宣称已启用执行配额。独立账本部件规则见 shared-contracts.md。

## 5. 知识库管理

### GET /api/admin/knowledge/documents

参数 workspaceId（必填）、q（标题，最多100字符）、availability、ingestStatus、limit、cursor；按 createdAt DESC,documentId DESC。只查询显式空间知识，排除未发布调查附件。

AdminKnowledgeDocument={documentId,workspaceId,title,domain:string|null,models:string[],version:string|null,sourceNature:string,accessNature:string,ingestStatus:string,availability:ENABLED|DISABLED,revision:long,materialScope:WORKSPACE_KNOWLEDGE,publicationStatus:string,releaseActive:boolean|null,createdAt,updatedAt}。

sourceNature/accessNature 保留真实性质，不因上传或 READY 提升为 AUTHORIZED。状态值与底层材料/release 对应，不在前端合成“已发布”。

### GET /api/admin/knowledge/documents/{documentId}

参数 workspaceId（必填）。返回 {document:AdminKnowledgeDocument,file:AdminImportFile|null,sectionCount:long,chunkCount:long,sections:[{sectionId,heading,ordinal}],sectionsComplete:boolean}；章节数组最多100，更多通过下方接口分页。章节标题仅在已有目标范围内展示；无正文、摘录、路径或原文件 URL。

### GET /api/admin/knowledge/documents/{documentId}/sections

参数 workspaceId、limit、cursor；按 ordinal ASC,sectionId ASC。返回 Page<{sectionId,heading,ordinal,chunkCount}>。无正文。

### POST /api/admin/knowledge/imports

multipart：workspaceId、file(Markdown)、title、domain（可选）、models(JSON string array，可选）、version（可选）、sourceId、licenseCode。服务器根据现有 corpus 登记及内容校验值核对 sourceId/licenseCode，仅接受已收集资料，不能凭请求提升原来源性质；许可代码为既有 USER_AUTHORIZED_LOCAL_TECHNICAL_RESEARCH_IMPORT 使用依据。大小上限复用现有受控上传配置并在 UI 展示；超限 413 FILE_TOO_LARGE，格式不支持 415 UNSUPPORTED_MEDIA_TYPE。

202 返回 AdminImportAccepted={fileId,workspaceId,processingStatus,statusUrl,auditId}；statusUrl 固定同源管理状态路径。复用异步服务，返回受理不等于索引/发布完成；重复内容保持原可用状态。

### GET /api/admin/knowledge/files

参数 workspaceId（必填）、processingStatus（可选）、limit、cursor；按 createdAt DESC,fileId DESC。返回 Page<AdminImportFile>，包含未形成 documentId 的受理/处理中/失败任务，刷新后仍能定位重试。列出管理端显式空间知识导入任务及同空间已有 corpus 入库记录，不枚举调查附件。历史 corpus 记录必须有真实 document/file/import_item 关联，不能通过伪造 dt_admin_import 补齐；importOrigin=CORPUS_IMPORT、retryable=false。

### GET /api/admin/knowledge/sources

参数 workspaceId（必填）、limit、cursor；按 sourceId ASC。返回 Page<{sourceId,displayLabel,sourceNature,accessNature,allowedLicenseCodes:string[]}>，仅当前目标可用的受控来源登记；无连接凭据/本机路径。此接口不授予许可，POST 导入仍重新校验登记；没有有效来源时阻止提交并提示运维登记，首期不提供网页编辑来源权限。

### GET /api/admin/knowledge/files/{fileId}

参数 workspaceId；返回 AdminImportFile={fileId,workspaceId,processingStatus,documentId:UUID|null,errorCode:string|null,retryable:boolean,revision:long,createdAt,updatedAt,importOrigin:ADMIN_UPLOAD|CORPUS_IMPORT}。该接口可轮询失败或尚无 documentId 的上传，不依赖“成功文档列表”定位失败。历史 corpus 文件展示原文件处理状态与创建时间，不虚构管理操作者、管理受理审计或重试资格。

### POST /api/admin/knowledge/files/{fileId}/retry

输入 {workspaceId,expectedVersion,reasonCode}，expectedVersion 对应文件 revision；202 返回 AdminImportAccepted。仅既有失败可重试状态，防重复 enqueue，旧 revision 冲突。不重新上传原内容，不覆盖成功文档。

### PATCH /api/admin/knowledge/documents/{documentId}/status

输入 {workspaceId,status:"ENABLED"|"DISABLED",expectedVersion,reasonCode}。200 返回 {document:AdminKnowledgeDocument,changed,auditId}。成功前完成引用登记并发协调；恢复前提失败 409 KNOWLEDGE_NOT_RESTORABLE，details={blockingCode}，仅有限码 PROCESSING_NOT_READY/SOURCE_NOT_VALID/NOT_PUBLISHED/RELEASE_INACTIVE。

所有状态写入的 changed=false 保持原 revision；真实变化才递增。文件重试成功受理递增文件 revision，即使新一轮处理随后失败也不复用旧 revision。

不提供正文读取、原文件下载、物理删除、覆盖正文或由此触发全平台发布的接口。

## 6. Skill 管理

### GET /api/admin/skills；GET /api/admin/skills/{skillId}

列表 limit/cursor，skillId ASC；返回 Page<AdminSkillCatalog>。AdminSkillCatalog={skillId,name,description,registryVersion,devices:string[],tasks:string[],declaredTools:string[],loadedAt:Instant|null}，不返回根目录路径。

详情返回 {skill:AdminSkillCatalog,bodyMarkdown,references:[{name,sizeBytes}],referencesComplete:boolean}。内容限定受控已加载 Skill，不包含账号资料；按部署加载上限限制正文/清单大小。

### GET /api/admin/skills/{skillId}/references/{name}

返回 {skillId,registryVersion,name,bodyMarkdown}。仅 Registry 登记的单篇 reference；拒绝路径穿越、绝对路径、多重编码绕过及超过加载大小限制，未知项 404。

### GET /api/admin/workspaces/{workspaceId}/skill-installations

参数 limit/cursor，按 skillId ASC,version ASC；Page<AdminSkillInstallation>。

AdminSkillInstallation={workspaceId,skillId,version,enabled,revision,registryVersion:string|null,effectiveTools:string[],availability:AVAILABLE|DISABLED|VERSION_MISMATCH|NOT_LOADED,updatedAt}。旧安装版本保留查询，当前 Registry 不支持的版本不可开通/恢复。

### POST /api/admin/workspaces/{workspaceId}/skill-installations

输入 {skillId,version,reasonCode}；201 返回 {installation:AdminSkillInstallation,auditId}。工具按服务端交集决定，首期不接收 allowedTools。重复已安装版本 409 INSTALLATION_ALREADY_EXISTS，不能以再次开通绕过停用。

### PATCH /api/admin/workspaces/{workspaceId}/skill-installations/{skillId}/versions/{version}/status

输入 {enabled:boolean,expectedVersion,reasonCode}；200 返回 {installation:AdminSkillInstallation,changed,auditId}。URL 明确安装内容 version；expectedVersion 是管理 revision，二者不同。

停用阻止条件：409 SKILL_HAS_UNRESOLVED_RUNS，details={runIds:UUID[],hasMore:boolean}，最多20条，按 requestedAt/runId 稳定排序；只返回该受控空间相关 ID。先完成/取消，不开放平台代执行用户任务。

## 7. 系统与审计

### GET /api/admin/system/health

返回 {generatedAt,components:DependencyHealth[]}。DependencyHealth={name:APPLICATION|DATABASE|WORKER|QUEUE|MODEL_CONFIG|RECENT_MODEL_CALLS,status:UP|DOWN|DEGRADED|UNKNOWN,checkedAt,evidenceWindow:{from,to}|null,reasonCode:string|null,metrics:HealthMetrics}。

HealthMetrics 固定字段 {latencyMs:long|null,activeWorkers:long|null,queuedRuns:long|null,queueCapacity:long|null,recentSuccessCount:long|null,recentFailureCount:long|null}，按 component 只返回适用值。无连接串、hostname、配置原值、密钥、路径或供应商原始异常；近期成功不表示当前外部依赖必然可用。该 GET 不调用付费模型。

### GET /api/admin/audit-events

参数 from、to、actorUserId、action、targetType、targetId、result、requestId、limit、cursor；按 occurredAt DESC,auditId DESC。

AdminAuditEvent={auditId,occurredAt,actorKind:ACCOUNT|MAINTENANCE,actorUserId:UUID|null,operatorRef:string|null,workspaceId:UUID|null,targetType,targetId,action,result:SUCCESS|NO_CHANGE|FAILURE|DENIED,reasonCode:string|null,errorCode:string|null,requestId,before:AuditState|null,after:AuditState|null}。

AuditState 仅 status/enabled/revision/platformRole/capabilities 等受控状态；无任意 JSON payload。开户审计不存密码/请求体，其他事件也不存邮件令牌、Cookie、Session ID、用户资料或模型内容。维护 operatorRef 为可信操作人引用，不包含 shell 命令/环境转储。

## 8. 脱敏验证要求

后续合同测试在用户问题、答案、附件、来源、Prompt、工具参数、异常文本和路径放唯一探针，逐个扫描所有管理响应与审计，断言探针不出现。允许的用户列表邮箱和授权知识标题单独按矩阵验收；禁止用“所有字符串脱敏”破坏合法字段。

对白名单额外字段、null/UNKNOWN、未知币种、部分报告、跨账号/空间游标、旧版本状态冲突与无管理范围统一404做明确断言。本文示例类型不表示线上返回已验证。
