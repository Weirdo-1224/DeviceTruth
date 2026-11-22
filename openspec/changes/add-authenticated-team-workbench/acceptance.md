# 规划与验收记录

当前状态：2026-11-22（Asia/Shanghai），第1–8组全部完成，整体33/33。最新验收汇总、真实证据边界及后续人工事项见文末。

以下保留历次记录：2026-11-19最初为提案完成、实施尚未开始，后续按实际证据推进。

## 用户确认与输入

用户已确认一线维修/工程师、调查驱动、电脑优先、克制工程风格，以及邮箱密码注册、个人/团队双空间、共同接续调查、讨论显式引用、单调查串行、附件明确发布的设计。完整原型交付后，用户回复“ok，我认同这个设计，可以开始openspec”。

认可原型已保存在 `prototype/workbench.html`；上一阶段原型 49 项断言和原始失败/修复历史保留在 `prototype/review-history.md`，不计入本轮后端验收。

## 本次实际检查

| 检查 | 实际结果 |
|---|---|
| `openspec status --change add-authenticated-team-workbench` | 4/4 规划工件 complete：proposal、specs、design、tasks |
| `openspec validate add-authenticated-team-workbench --strict` | valid，退出码 0 |
| 工件结构核对 | 5 个 capability spec、30 项 requirement、54 个 scenario，与 proposal 一致 |
| 实施状态核对 | 33 项实施任务全部未完成，完成数 0 |
| 范围核对 | 仅新增本 change 目录中的规划及原型快照；未修改业务源码、旧变更或运行配置 |

两项 OpenSpec 规划检查均通过：通过 2、失败 0、错误 0、跳过 0。结构与任务数量是文档核对，不折算业务测试数量。

## 未执行的验收

本轮业务测试执行数为 0；未运行 Maven/JUnit、前端 Vitest/build、产品浏览器、PG 迁移、真实 SMTP、embedding 或外部模型调用，也未执行数据认领/部署。相应实现与验证均在 tasks 中保持未完成；没有据原型或健康探针将它们标为通过。

## 实施前明确的合同变化

- product HTTP 将从匿名固定身份转为登录身份，属于 proposal 标注的 BREAKING 行为；CLI、legacy 和原内部用法保留。
- 新认证调查采用单未决 Run，团队普通提交增加期望版本，原补答三元组和幂等合同保留；旧多个未决轮不删除。
- 新调查级上传默认附件作用域；旧显式知识导入用途保留，不强迫旧知识上传调用携带调查 ID。
- 不能把原型的预览身份、模拟运行、内存角色判定当作服务端实现。Spring/PG/邮件配置需在实施中落地并分别验收。

## 后续入口

用户已追加 apply 授权，先按第 1–3 组落地账号/空间/后台作用域，再推进共同调查、资料范围和前端真服务接入。原有其他变更及未完成可选任务保持独立。

## 2026-11-19 首次实施：1.1 部分实现，隔离 PG 验证阻塞

- 新增 `V19__account_workspace_session.sql`，含账号、个人/团队空间、成员和 JDBC Session 表与索引；未修改 V1–V18，未连接或认领运行库。
- 新增 `AccountWorkspaceMigrationTest`，覆盖空库约束及 V18 历史会话升级；完整历史 Run/引用夹具仍待补充。数据字典和隔离测试入口见 `data-foundation.md`。
- `mvn -version`：Maven 3.9.9，Microsoft JDK 21.0.13。
- `mvn -q '-Dtest=AccountWorkspaceMigrationTest' test`：退出 1；总数 2，通过 0，断言失败 0，错误 2，跳过 0。两项均在建库前报 `Missing test configuration: DEVICETRUTH_TEST_PG_BASE_URL`。当前进程也未提供测试用户名/密码；源码及测试编译已完成，迁移 SQL 尚未在 PG 执行。
- 1.1 保持未勾选；整体 0/33，第 1–3 组里程碑未达到。未执行全量测试、前端构建/浏览器、真实模型、embedding、SMTP 或部署；没有切换产品入口。
- 恢复条件：提供专用隔离 PostgreSQL/pgvector 实例的上述三项环境配置（不要把密码写入此记录或聊天），允许测试账号创建随机测试库。恢复后先运行迁移测试并补齐历史关联反例，再继续 1.2/1.3 和账号授权链路。

## 2026-11-19 Docker 隔离验证与第 1 组完成

用户提示查 Docker 后，确认本机运行 `devicetruth-postgres-1`（54329）。未提取其凭据，未操作其运行库；复用本机 pgvector 镜像启动独立 `dt-workbench-migration-test`，仅绑定 `127.0.0.1:54339`、无持久卷。该容器使用 trust 仅供本机临时测试，不是部署配置。每项测试在其中创建/清理随机数据库，容器保留供下一组测试复用，完成后需移除。

失败及修复历史：普通沙箱 Docker pipe 权限不足，获工具授权后成功；首次普通沙箱 Maven 连接失败（2 项均连接错误），授权连接后 2/2 通过。扩展 V20 历史多个未决 Run 和讨论重复序号断言后 2/2 再通过。加入真实 PG 作用域测试后最新执行 3 项，通过 3、失败 0、错误 0、跳过 0；命令仍为 `mvn -q '-Dtest=AccountWorkspaceMigrationTest' test`。

新增 V20、AccessScope 和 WorkspaceScopeResolver；数据字典/调用约定见 `data-foundation.md`。作用域测试合并在 AccountWorkspaceMigrationTest 中复用隔离建库，不另建 WorkspaceScopeTest。检查个人默认空间、两团队隔离、资源/Header 不一致、匿名、降权与移除。V18 升级测试保存旧消息和两个 QUEUED Run，并断言历史作者/空间保持 NULL。

第 1 组任务 1.1–1.3 已完成，OpenSpec 3/33；第 1–3 组首个产品里程碑仍未达到。组件尚未接入 HTTP/worker，未开放半成品认证接口。第 2 组依赖预取时普通沙箱 DNS 失败，授权下载后 `mvn -q -U -DskipTests compile` 成功；尚未保留 Security 依赖到 pom，避免默认自动配置在专用安全链落地前影响 legacy/health。

下一项是 2.1 注册/登录及 product 专用安全链，然后继续密码恢复/邮件和第 3 组全链授权。原隔离 PG 阻塞已解除，不再需要用户提供数据库配置。尚未执行全量回归、前端、真实模型、SMTP 或部署验收。

## 2026-11-19 任务 2.1：账号与 JDBC Session

已实现 `AccountService`、`AccountController`、`ProductAuthConfiguration`；Boot BOM 管理 Security/Session 依赖。注册事务创建账号/个人空间/ADMIN 成员，PBKDF2 编码，登录轮换会话并显式保存上下文到 JDBC Session；CSRF、me、退出及旧会话失效已接通。Cookie 默认 Secure/HttpOnly/SameSite=Lax，测试显式关闭 Secure 以运行本机 HTTP。完整合同和当前资源拒绝策略见 `account-api.md`。

首次定向 `AccountAuthenticationTest` 4/4 通过（含继承的三项隔离基础测试）。扩展旧 Cookie 重放并运行 `AccountAuthenticationTest,ServerHttpTest,DiagnosisHttpTest,QueryHttpTest,CliOptionsTest,AcceptanceTest` 后共 34 项，通过 9、失败 0、错误 25、跳过 0：legacy 启动触发 Actuator 的 ManagementWebSecurityAutoConfiguration，缺 HttpSecurity。两个入口同步排除此默认配置，仅 product 显式装安全链后，同命令 34/34 通过。CliOptionsTest 未匹配实际测试文件，不计为独立通过证据。

随后增加非法短密码、无 profile 健康入口、无安全链 Bean 和实际 CLI `execute(--help)` 断言，重跑 AccountAuthenticationTest：5 项，通过 5、失败/错误/跳过均 0。与未受此次测试增补影响的 legacy/core 30 项合计，最新定向证据覆盖 35 项均通过；不是全量套件通过。真实 PG 16.15、真实 HTTP/Cookie，worker 关闭，scripted/deterministic 仅作协议隔离，外部模型/embedding 调用数为 0。

2.1 勾选，整体 4/33。尚未执行 HTTPS 实际浏览器 Cookie、应用重启后会话回读、全量 product 回归、前端、真实 SMTP 或模型验收。资源 API 在 scope 接线前匿名 401/登录后 403，不能用于工作台产品验收；原 product HTTP 测试需在第 3 组完成后接入新认证合同。现有 Docker 产品服务未重启、运行库未升级。本次不新增任何 `/api/admin/*`、平台角色或管理后台。

## 2026-11-19 任务 2.2：一次性密码恢复与全会话撤销

新增 V21 `dt_password_reset`：账号外键、唯一凭证摘要、到期/消费时间、PENDING/ACCEPTED_BY_PROVIDER/FAILED 投递状态；无原始凭证或密码字段。新增 PasswordRecoveryService，接通申请与消费接口。凭证 32 随机字节、默认 30 分钟；申请旋转旧凭证，消费按账号行锁裁决，口令更新/全部凭证失效/JDBC Session 及属性删除同一事务。API 和恢复路径见 `account-api.md`。

实际命令：`mvn -q '-Dtest=PasswordRecoveryTest,AccountAuthenticationTest' test`，使用本机隔离 pgvector/PG16.15 的随机测试库。首次总数 12，通过 11、失败 1、错误/跳过 0：恢复测试继承迁移测试后，其 BeforeEach 注册的账号污染了“V18 升级后无账号”断言。改为组合复用建库夹具而不继承测试方法后，最新总数 9，通过 9、失败/错误/跳过均 0。PasswordRecoveryTest 4 项；AccountAuthenticationTest 5 项（含原三项基础测试）。

验证覆盖：成功后两条旧会话及属性消失、旧密码拒绝/新密码可登录；原始令牌不落库；默认到期范围；不一致确认、过期、重放、重新申请使旧凭证失效；双线程同凭证消费仅一方成功；未知邮箱不建凭证，邮件失败记录 FAILED 且不可消费；真实 HTTP 无 SMTP 时已知/未知邮箱相同 503，错误消费响应不回显令牌/新密码。替身只捕获邮件凭证，不能计真实 SMTP 或收件验收。

2.2 完成，整体 5/33。当前无真实 ResetMailSender，恢复申请统一 503 MAIL_UNAVAILABLE；SMTP 适配、限流、重发及真实投递继续 2.3。未调用外部模型/embedding，未部署、认领或改运行库；未执行全量回归/浏览器/真实邮件。全会话持久撤销已验证，在途 SSE/worker 的持续撤权仍依赖 3.5，不能计为完成。

## 2026-11-19 任务 2.3：SMTP 与认证限流

新增 Boot BOM 管理的 Spring Mail、SmtpResetMailSender；product 装配 SMTP（默认 587、要求 STARTTLS、关闭协议 debug），连接/读取/写入各默认 5 秒，可配置 1–30000ms。发件人及 HTTPS public URL 配置缺失时所有邮箱统一 503。凭证通过 fragment 链接传给后续前端，不放服务器 query；实际邮件状态沿用 V21，成功仅表示 SMTP 接受，失败记 FAILED 并废弃凭证。显式重发复用重新申请并旋转凭证，不做隐式自动重试。

AuthRateLimiter 对注册/登录/恢复申请同时按来源和规范化邮箱固定窗口计数，对重置消费按来源计数；默认每类 15 分钟 20 次，最多 10000 活跃 key、过期清理、满容量拒绝，不信任任填代理 Header。Controller 在业务副作用前检查，统一 429 AUTH_RATE_LIMITED。此为每实例限流，不是全局配额；邀请发送在 3.2 接入此共享组件。

更新 `.env.example`、application-product.properties、Compose backend 环境透传与 `account-api.md` 故障恢复说明；未读写本机真实邮件配置/凭据，未重启部署。

验证：`mvn -q '-Dtest=AuthMailTest,PasswordRecoveryTest,AccountAuthenticationTest' test`，首次 12 项通过 11/失败 1/错误 0/跳过 0，原始 SMTP MIME 正文被编码，测试错误地直接找明文链接。改为 MimeMessage 解码后，第二次 13 项通过 12/失败 0/错误 1/跳过 0；新增 HTTP 限流测试预填 20 次时未扣除前面已有 2 次，夹具自身提前触发 429。修正为预填 18 次后仅重跑受影响的 AccountAuthenticationTest，5/5 通过，复用同候选有效的 AuthMailTest 4/4 和 PasswordRecoveryTest 4/4，最新共 13 项证据通过、失败/错误/跳过均 0。

SMTP 协议替身实际接受/拒绝邮件、解码正文并核对收件人/链接、无响应读取超时；配置拒绝无限等待/非 HTTPS URL；限流窗口/邮箱规范化/容量/过期清理；真实 HTTP 超限响应不含邮箱且不建恢复凭证；PG 重新申请/凭证轮换/未知邮箱与发送失败应答继续通过。应用不记录 SMTP 原始异常或秘密正文，响应脱敏由 HTTP 断言覆盖；故障替身不计外部真实收件。

2.3 完成，整体 6/33。第 1–3 组首个里程碑仍未完成，下一项 3.1 团队成员服务。真实 SMTP TLS握手、邮箱收件、HTTPS浏览器及完整前端链接消费留待 8.3/6.1；本次无外部模型/embedding 调用，未运行全量回归，不将配置存在或本地 SMTP 替身记为真实邮件验收。

## 2026-11-19 任务 3.1：团队与成员管理

新增 WorkspaceService/WorkspaceController，接通团队创建、可见空间列表、团队改名、成员列表、角色修改与移除。创建事务只写新团队及创建人 ADMIN 关系；查询只取当前成员空间，不复制演示资源。写事务先锁空间行、再复查当前管理员及最后管理员数量。个人空间禁止团队式修改；移除保留账号/作者/资源。空间 Header 与路径不一致 404，请求体未知字段拒绝。接口和角色矩阵见 `workspace-api.md`；没有平台后台权限或管理接口。

命令 `mvn -q '-Dtest=WorkspaceMembershipTest,AccountAuthenticationTest' test`：8 项，通过 8、失败/错误/跳过均 0。真实隔离 PG16.15 覆盖两空间隔离、协作/只读禁止改名/改角色/移除、只读可查成员、移除立即失去成员读资格、账号仍保留、个人空间不可变、非法名称和最后管理员单请求保护。两个服务实例双线程并发自行降权和自行移除各仅成功一次，最终均保留一位管理员。

追加 Header 不匹配及 HTTP PATCH/DELETE 回归后，只重跑受影响 AccountAuthenticationTest：5 项通过、失败/错误/跳过 0；复用未改动的 WorkspaceMembershipTest 3 项，总计最新有效证据 8 项。HTTP 验证登录空间列表、团队创建、成员读取、改名、删除最后管理员返回 409，以及伪造 role 字段被 400 拒绝。未出现失败/修复历史。

3.1 完成，整体 7/33；下一项 3.2 邮箱邀请与接受。邀请/前端/实际模型/SSE撤权尚未接通，其他产品资源继续封闭至 3.3–3.5 完成。未操作运行库或重启现有容器，未新增迁移；无真实 SMTP/模型/embedding 调用，未做全量回归或浏览器验收。

## 2026-11-19 第 3 组完成：邀请、HTTP、后台恢复与持续授权

用户要求完成整组后统一返回。本次连续完成 3.2–3.5，整体 11/33，第 1–3 组后端技术里程碑达到隔离验收要求；不是全产品或现场验收。

### 实现与接口接力

- 3.2：V22 邀请表及 TeamInvitationService/Controller，管理员创建/列表/撤销/重发，匹配邮箱接受；令牌摘要、默认 7 天可配置、邮件与接受状态分离、重发旋转凭证、已有成员不提权。邀请发送/接受接有界限流。真实 HTTP 验证先邀请→新用户注册→接受，个人空间仍保留，错误账号与重放拒绝，列表不泄露凭证。
- 3.3：HttpAccess 从已认证 principal 和资源元数据解析实际空间，四个产品控制器移除 HTTP 固定身份；集合缺 Header 仅个人空间，资源 Header 不匹配拒绝，跨空间 UUID 不泄露资源存在性；摘要游标绑定空间/筛选。知识导入仍保留显式空间知识用途，未提前改为调查附件。
- 3.4：GeneralAgentService 用不可变 scope 视图共享原唯一主循环和有界 worker，持久记录 workspace、初始/执行段 actor、消息作者和执行段输入。dispatch 从 Run 恢复 scope，启动/周期恢复遍历已绑定空间的活跃存储归属，保留租约/CAS。product 不自动执行未认领旧 Run，显式旧内部 recoverQueued 仍可恢复。ToolContext 向后兼容增加 actor/workspace；SkillRuntime 共享 registry，按存储范围安装服务器默认策略、选择与回读，不覆盖停用，不复制他空间授权。
- 3.5：模型步骤/返回后、工具事务和最终采纳前复核当前执行段 actor 写权；工具/结果事务锁成员关系，撤权按 AUTHORIZATION_REVOKED 失败收口，不采纳迟到问题/答案。SSE 在建立、每批/每条/终态及 15 秒心跳输出前复查 JDBC Session 与成员资格，退出或移除后清理旧流；worker 的持久执行授权不依赖浏览器会话仍在线。

详细合同与实际运行链路见 `authorization-runtime.md`。第 4 组的单未决 Run/期望版本、讨论引用和更完整材料快照、第 5 组附件发布分层、第 6 组前端都仍未完成，不计入此里程碑。本次没有平台角色或 `/api/admin/*`。

### 验证记录与失败修复

1. 新增 TeamInvitationTest、ScopedAgentRuntimeTest、AuthenticatedResourceTest：真实 PG 与 HTTP/SSE，推理使用明确协议/阻塞替身，不调用外部模型；分别覆盖邀请状态、跨空间/游标/Skill、交错 worker、重启恢复、未认领旧运行保护、迟到模型撤权、退出和成员移除后的旧 SSE。
2. 原六类回归首次使用旧匿名 HTTP，执行到 16 项：通过 4、失败 7、错误 5、跳过 0；认证拒绝和初始化失败使其余用例未能展开。没有在产品增加匿名兼容开关。新增仅限随机测试库的 AuthenticatedProductTestSupport，真实登录/Cookie/CSRF，并显式认领原固定身份测试夹具，历史作者不造假；保留原业务断言。
3. 改造后原六类展开为 71 项：通过 66、失败 1、错误 2、跳过 2。剩余问题是 Skill HTTP 回读还未登录、两个重启临时服务未登记认证夹具；修复相关调用后定向重跑受影响 GeneralAgentApiTest 和 SkillRuntimeIntegrationTest，分别 7/7、3/3 通过。其余未受修改影响的生命周期/恢复/SSE/授权有效证据复用，不重复整套跑作安慰性检查。
4. 最终补验标准 Skill 在两个空间实际激活/版本选择、游标绑定、product 仅恢复已绑定空间而旧内部维护入口仍可用、受邀新用户真实 HTTP 注册后加入；各新增/补充断言均通过。共享 SMTP 发送链接改动另跑 AuthMailTest 4/4。

实际 Maven 定向命令包括：

```text
mvn -q '-Dtest=TeamInvitationTest,AccountAuthenticationTest,WorkspaceMembershipTest' test
mvn -q '-Dtest=ScopedAgentRuntimeTest' test
mvn -q '-Dtest=AuthenticatedResourceTest' test
mvn -q '-Dtest=AgentLifecycleTest,RunRecoveryTest,SkillRuntimeIntegrationTest,AgentStreamTest,GeneralAgentApiTest,ResourceAuthorizationTest' test
mvn -q '-Dtest=GeneralAgentApiTest,SkillRuntimeIntegrationTest,TeamInvitationTest,AuthenticatedResourceTest,ScopedAgentRuntimeTest' test
mvn -q '-Dtest=ScopedAgentRuntimeTest,AccountAuthenticationTest,TeamInvitationTest' test
mvn -q '-Dtest=ScopedAgentRuntimeTest,GeneralAgentApiTest' test
mvn -q '-Dtest=ScopedAgentRuntimeTest,TeamInvitationTest' test
mvn -q '-Dtest=AuthMailTest' test
```

最新有效证据按测试类去重汇总（不是一次全量套件结果）：

| 测试类 | 总数 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| AccountAuthenticationTest | 5 | 5 | 0 | 0 | 0 |
| WorkspaceMembershipTest | 3 | 3 | 0 | 0 | 0 |
| AuthMailTest | 4 | 4 | 0 | 0 | 0 |
| PasswordRecoveryTest | 4 | 4 | 0 | 0 | 0 |
| TeamInvitationTest | 2 | 2 | 0 | 0 | 0 |
| ScopedAgentRuntimeTest | 5 | 5 | 0 | 0 | 0 |
| AuthenticatedResourceTest | 4 | 4 | 0 | 0 | 0 |
| AgentLifecycleTest | 18 | 18 | 0 | 0 | 0 |
| RunRecoveryTest | 22 | 20 | 0 | 0 | 2 |
| SkillRuntimeIntegrationTest | 3 | 3 | 0 | 0 | 0 |
| AgentStreamTest | 12 | 12 | 0 | 0 | 0 |
| GeneralAgentApiTest | 7 | 7 | 0 | 0 | 0 |
| ResourceAuthorizationTest | 9 | 9 | 0 | 0 | 0 |
| **合计** | **98** | **96** | **0** | **0** | **2** |

跳过的是 RunRecoveryTest 中显式 opt-in 的 `realModelReadsLongAssetReferenceAfterReplyAndWorkerRestart`、`realModelUsesReplyToProposeNewSearch`；没有把它们计为真实模型通过。PG16.15/pgvector 为真实隔离容器，embedding 为 deterministic，资料/资产仍为模拟夹具；SMTP 为协议或捕获替身，现场数据和真实收件均未验收。

### 交付和后续

第 1–3 组完成，但尚未部署或升级运行库，未认领用户真实资料、未重启现有 Docker 产品容器、未执行全量前后端/浏览器或外部模型验收。真实收件/TLS 与完整产品联调按 8.2/8.3 验证。后续实施从第 4 组共同调查和串行运行开始；不自动续跑其他变更。

## 2026-11-19 补测真实模型：链路与授权通过，知识答案降级

用户要求先测试真实模型、邮件后续再说。新增显式 opt-in 的 AuthenticatedRealModelTest，在随机隔离 PG 启动当前 product 后端，真实注册/登录三账号、创建团队、导入模拟手册、正式提交，并由团队成员回读 Run 和每条来源、团队外账号尝试读取。团队成员关系通过隔离 SQL 夹具建立，不冒充邀请收件验收。没有重启现有 Docker 产品服务或操作运行库。

执行：`mvn -q '-Dtest=AuthenticatedRealModelTest' '-Ddevicetruth.test.real-agent=true' test`。JUnit 1 项通过、失败 0、错误 0、跳过 0，测试耗时约 54 秒。该断言验证真实模型调用/持久结果/来源和授权，并允许宿主有限结果；**正常知识答案质量另判，本次未通过**。

- 模型：`deepseek-flash`，通过配置好的 OpenAI-compatible 连接实际外部调用，未使用 scripted 或模型替身。
- Run：`c96da39d-263f-4b35-84f8-c30fdab28ab0`；状态 COMPLETED，activeDurationMs=43509。
- 实际模型尝试 5 次：1 次 INVALID_JSON_OR_SCHEMA 后有界重试，4 次 VALID_PROPOSAL；实际工具 activate_skill 1 次、search_knowledge 2 次，均成功，登记 4 条知识引用。
- 答案为 HUMAN_REVIEW 有限结果，RUN_DEGRADED 原因 UNCONFIRMED_FINDING；没有得到本次请求期望的正常 KNOWLEDGE 说明。当前审计记录只有拒绝码，不能据此判定具体触发的是 certainty=CONFIRMED、确诊类别或正文确定性措辞。未放宽 Guard，也未重跑同一请求追求正结果。
- 团队协作成员可读 Run 及 4 条来源原文，外部账号对 Run/引用列表/每条来源均 404。
- 数据库为真实 PG16.15/pgvector 隔离库；embedding 明确为 deterministic，手册和场景为 SIMULATED，无设备档案或现场数据。此项不计真实 embedding/现场诊断、完整第 8.2 协作流程或浏览器验收。

脱敏报告位于被忽略的 `outputs/validation/authenticated-real-model/report-c96da39d-263f-4b35-84f8-c30fdab28ab0.json`，保留模型事件、工具事件、结果和引用，不保存凭据或原始 Prompt。测试结束已关闭临时后端并删除本次随机库；报告可继续回读。实际收件按用户要求暂缓，未测试。

下一步质量问题是围绕该类无现场资产的资料问答，定向检查模型草案与 AnswerGuard 的拒绝条件，区分正确拦截和过度拦截，再确定修复；不能把 COMPLETED 或本测试断言通过当成答案已可用。第 4 组交接需保留这一问题，不改变其协作实施范围。


## 2026-11-19 第 4 组完成：讨论、快照、串行、补答与取消

本轮仅实施 4.1–4.5，已全部勾选；OpenSpec apply 回读依次达到 12/33、13/33、14/33、15/33、16/33。第 5–8 组及独立 Guard change 未实施。接口与调用顺序详见 `discussion-api.md`。

### 实现与实际链路

- `ConversationRepository.addDiscussion/discussions` 使用独立表和独立分页序号，作者来自认证 scope，时间由 PG 写入；不写消息表、不改变正式调查版本、不创建 Run、知识切片或证据。HTTP GET/POST 已接当前资源空间授权。
- 消息 schemaVersion=1 保留旧构造和字段，新增 discussionQuotes/expectedConversationVersion。服务器解析同调查的 discussionId/version，拒绝跨调查、失效版本及伪造字段，保存原文/作者/时间/USER_REPORTED_UNVERIFIED。消息回读暴露引用快照；模型收到结构化 discussionQuotes，原文不拼成指令，也不突破单消息 8000 字限制。
- `AgentRunRepository.snapshot` 固定本 Run 的目标/追问/补答、附件 documentId/version、contextRefs、讨论快照、排查对象和时点，并追加 dt_run_input_segment。`doRun` 经 `executionMessages` 真正读取这些快照，旧基础记录/旧内部路径保留原读取兼容。新增材料不会自动进入当前段；调查上传与召回资格继续由第 5 组实现，旧 `/api/files` 知识导入用途不变。
- 认证普通提交在会话行锁内依次查幂等键、期望版本、任一未决 Run，再解析材料、写入/enqueue，提交后 dispatch。团队必须提供期望版本，个人可省略，提供则检查；冲突 409 不换键、不排队。旧内部多未决合同保留。
- 同轮补答沿原三元组及 CAS；双成员只有一次成功，原 Run 不增加，记录补答及 execution actor，保留 initiated actor。重启读取原执行段、排查对象和时点。
- 取消复核当前写权和当前 execution actor/空间管理员资格；普通同事不能取消他人段。新增 RUN_CANCEL_REQUESTED 记录实际取消人，系统结束事件保留 execution actor。事件新增顶层 actorUserId UUID/null，data 仍逐类型白名单。正式消息和 Run 分别追加作者字段，不补造历史作者。
- 确认迁移上限 V22 后追加 V23；V1–V22 不变。全部历史尝试按创建时间/id 编号，新轮在会话锁内 max+1，取消不回收轮号。运行库未升级，未部署或认领真实资料。

例如甲发表“怀疑控制信号，尚未检查”时只增加讨论；乙明确引用并提交后，服务器保存这条未核实讨论和附件版本，经串行裁决创建一个 Run，再走原 TaskOrchestrator。若模型追问，任一有写权成员可竞争补答，胜者恢复同一 Run 并成为新执行段 actor；其他人不能把失败补答转成新轮。历史输入、引用、旧结果和作者持续可回读。

### 实际命令及失败/修复历史

环境：`mvn -version` 确认为 Maven 3.9.9 / Microsoft Java 21.0.13；授权检查 Docker 后确认 `dt-workbench-migration-test` 仅绑定 127.0.0.1:54339。所有 PG 测试使用 dt_test 和随机 devicetruth_test_ 库，未访问 54329 产品库。测试密码为 trust 实例非空测试值，不读取产品密码。

```text
mvn -q -DskipTests compile
mvn -q -DskipTests test-compile
mvn -q '-Dtest=InvestigationDiscussionTest,ScopedAgentRuntimeTest' test
mvn -q '-Dtest=InvestigationDiscussionTest,ScopedAgentRuntimeTest,AgentLifecycleTest,RunRecoveryTest,AgentToolFeedbackTest,SafePublicFieldsTest,GeneralAgentApiTest,AgentStreamTest' test
mvn -q '-Dtest=InvestigationDiscussionTest' test
mvn -q '-Dtest=InvestigationDiscussionTest,GeneralAgentApiTest,AgentStreamTest,AgentToolFeedbackTest,SafePublicFieldsTest' test
mvn -q '-Dtest=InvestigationDiscussionTest,ScopedAgentRuntimeTest,AgentLifecycleTest,RunRecoveryTest,AgentToolFeedbackTest,SafePublicFieldsTest,GeneralAgentApiTest,AgentStreamTest,AuthenticatedResourceTest,AccountWorkspaceMigrationTest' test
mvn -q '-Dtest=InvestigationDiscussionTest,AuthenticatedResourceTest' test
openspec validate add-authenticated-team-workbench --strict
git -c core.fsmonitor=false diff --check
```

测试环境变量：DEVICETRUTH_TEST_PG_BASE_URL=jdbc:postgresql://127.0.0.1:54339/、DEVICETRUTH_TEST_PG_USERNAME=dt_test、DEVICETRUTH_TEST_PG_PASSWORD 为任意非空隔离测试值。网络/引擎访问按工具权限授权，不通过其他路径绕过拒绝。

1. 首次普通沙箱 7 项：通过 0、失败 0、错误 7、跳过 0，均为端口权限 Permission denied；获授权后同命令 7/7 通过。
2. 首轮扩展回归 106 项：通过 101、失败 2、错误 0、跳过 3。事件测试旧顶层白名单未包含新增 actorUserId；旧恢复夹具通过 HTTP 在一个认证调查积压 105 轮，触发新 CONVERSATION_BUSY。
3. 事件测试保留严格白名单，明确增加 UUID 作者字段；积压夹具改走已有内部服务入口，仍在同调查构造 105 个遗留未决轮，原恢复/数量断言不变，没有削弱新 HTTP 串行规则。修复后定向 63 项：通过 62、失败 0、错误 0、跳过 1。
4. 新竞争测试首轮 4 项：通过 3、失败 0、错误 1、跳过 0，原因是夹具调用不存在的 questionId()；改回 pendingQuestionId() 后补答竞争通过。
5. 最终统一回归 116 项：通过 113、失败 0、错误 0、跳过 3。再补真实 Cookie/CSRF 讨论接口、明确同事取消拒绝/QUEUED 和 WAITING 忙碌反例，定向两类 10/10 通过。最终候选按类去重有效证据如下，共 117 项，通过 114、失败 0、错误 0、跳过 3；不是全量套件。

| 测试类 | 总数 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| InvestigationDiscussionTest | 5 | 5 | 0 | 0 | 0 |
| ScopedAgentRuntimeTest | 5 | 5 | 0 | 0 | 0 |
| AgentLifecycleTest | 18 | 18 | 0 | 0 | 0 |
| RunRecoveryTest | 22 | 20 | 0 | 0 | 2 |
| AgentToolFeedbackTest | 35 | 34 | 0 | 0 | 1 |
| SafePublicFieldsTest | 5 | 5 | 0 | 0 | 0 |
| GeneralAgentApiTest | 7 | 7 | 0 | 0 | 0 |
| AgentStreamTest | 12 | 12 | 0 | 0 | 0 |
| AuthenticatedResourceTest | 5 | 5 | 0 | 0 | 0 |
| AccountWorkspaceMigrationTest | 3 | 3 | 0 | 0 | 0 |

新增证据覆盖真实 PG 双服务/双成员提交竞争、同三元组补答竞争、原 Run 数量不增、重启 actor/时点、取消轮号不复用、同事取消拒绝和管理员取消、讨论分页/越权/只读、引用伪造/版本/跨调查、实际模型上下文捕获不含后加讨论及附件新版本。附件新版本反例是隔离 PG 元数据夹具，不冒充第 5 组真实上传/发布验收。HTTP 补验实际登录并使用 Cookie/CSRF，核对讨论真实作者、零 Run 副作用及显式引用回读。

三项跳过：RunRecoveryTest 两项及 AgentToolFeedbackTest 一项真实模型 opt-in，均不计通过。本轮推理使用显式协议/阻塞/捕获替身、真实隔离 PG16.15；未调用外部模型或真实 embedding，非现场数据。日志位于忽略的 outputs/group4-*.log。OpenSpec strict validate 和 diff --check 均退出 0。

### 尚未验收与下一入口

未运行全量 Maven、CLI 打包/场景、前端构建/完整浏览器、真实 SMTP/TLS、外部模型/embedding 或现场测试；本轮不改变它们原有验收状态。前端协作接线仍属第 6 组，不能据后端测试宣称浏览器工作台完成。

上一轮 deepseek-flash Run c96da39d-263f-4b35-84f8-c30fdab28ab0 的 HUMAN_REVIEW / UNCONFIRMED_FINDING 及正常 KNOWLEDGE 答案质量未通过问题仍保留，未改 Guard、未自动修复或扩大本组范围。邮件验收仍暂缓。

下一入口：在另行 apply 授权下完成本 change 第 5 组调查附件/发布分层；第 5 组交付后按既定排期提示 apply 独立 improve-evidence-aware-answer-guard，再进入第 6 组接线及联合浏览器验收。本次不自动推进。


## 2026-11-19 第 5 组完成：调查附件与显式知识发布

本轮仅完成 5.1–5.4；apply 回读依次为 17/33、18/33、19/33、20/33。第 6–8 组和独立 Guard change 未推进。接口与生命周期见 `material-api.md`。沿用第四组尚未提交的工作区修改，未部署、认领或写入产品库，未推送 Git。本组不新增 Flyway：实际最大版本仍为第四组 V23，复用 V20 的文件作用域/上传者/发布表及执行段字段。

### 实际实现链路

1. 调查附件入口由 HttpAccess 解析真实空间。FileUploadService.uploadAttachment 在既有会话锁和成员授权检查内记录 conversation_id、uploaded_by_user_id、CONVERSATION_ATTACHMENT，文件路径仍由服务器生成。事务提交后才派发有界异步解析，回滚清理新文件。MD/TXT 使用原导入/切片/向量管线，PDF 保持 OCR_REQUIRED/PDF_NOT_SUPPORTED，不伪造正文。列表和受控原文不触发模型；只读不能上传/发布/重试。
2. 普通提交或补答沿原 GeneralAgentService 和 snapshot，在当前执行段保存当时已解析的本调查附件 documentId/version/fileId 到 materialVersions；新附件和后来解析完成的附件不会进入当前段，下一次明确确认才纳入。显式 attachments 中跨调查 fileId 被拒绝。
3. MaterialScope 统一用于向量/关键词候选、RRF 输入、引用登记、原文工具和 contextRefs 继承。只允许同归属的显式空间知识、已发布知识及本段附件版本。旧 source_file_id 指向 SPACE_KNOWLEDGE 的历史文档继续有效，不要求旧内部夹具补写新关联表。
4. 发布要求标题和适用范围、PARSED/READY/切片齐备。在空间锁、成员写授权和唯一约束下，两成员同时发布只得到一条 Publication，重复请求不改首次名称、范围或作者。发布保留原附件、文档版本和数据性质，不额外调用 embedding/模型；空间知识列表不把未就绪文档显示为可检索。
5. 成员仍能主动阅读 A 调查的附件与持久来源，但 B 的 Agent 不能因此检索或继承 A 的未发布资料。报告、来源、Skill/资产继续依赖原 Run/ToolContext 和空间授权，不增加模型调用。旧未知作者保持 null。
6. 旧 `/api/files`、`/api/knowledge/documents` 保留显式空间知识导入。相同附件文档被明确知识导入时，增加独立知识用途关联，不改原附件归属；既有知识重复导入保留幂等。旧上传、失败重试、元数据/embedding 冲突及跨身份断言继续验证。

### 执行命令与失败/修复历史

Maven 3.9.9、Microsoft Java 21.0.13。测试仅使用 `DEVICETRUTH_TEST_PG_BASE_URL=jdbc:postgresql://127.0.0.1:54339/`、`DEVICETRUTH_TEST_PG_USERNAME=dt_test` 和 trust 实例非空测试密码；每套件创建/清理随机 devicetruth_test_ 库。未读取产品密码或使用 54329 产品库。

```text
mvn -version
mvn -q -DskipTests compile
mvn -q '-Dtest=KnowledgeSearchTest,KnowledgeImportTest,GeneralAgentFlowTest,InvestigationDiscussionTest' test
mvn -q clean '-Dtest=InvestigationMaterialTest,KnowledgeSearchTest,KnowledgeImportTest,GeneralAgentFlowTest,InvestigationDiscussionTest' test
mvn -q '-Dtest=KnowledgeSearchTest,KnowledgeImportTest,GeneralAgentFlowTest,InvestigationMaterialTest' test
mvn -q '-Dtest=KnowledgeSearchTest,InvestigationMaterialTest' test
mvn -q '-Dtest=InvestigationMaterialTest,KnowledgeSearchTest,KnowledgeImportTest,GeneralAgentFlowTest,AuthenticatedResourceTest,ResourceAuthorizationTest,InvestigationDiscussionTest,ScopedAgentRuntimeTest,RunRecoveryTest,SkillRuntimeIntegrationTest' test
mvn -q '-Dtest=InvestigationMaterialTest,AuthenticatedResourceTest,ResourceAuthorizationTest' test
mvn -q '-Dtest=InvestigationMaterialTest' test
mvn -q '-Dtest=InvestigationMaterialTest,AgentReportToolTest' test
mvn -q '-Dtest=AgentReportToolTest' test
openspec validate add-authenticated-team-workbench --strict
git -c core.fsmonitor=false diff --check
```

- 首轮 8 项，通过 0、失败 0、错误 8、跳过 0；生成类含 Unresolved compilation problem，ProductAuthConfiguration 反射解析 AccountService 失败。执行 Maven clean 重建生成产物后不再出现该启动问题，源码和 outputs 历史记录未删除。
- 重建后展开 31 项，通过 9、失败 21、错误 1、跳过 0。原 KnowledgeSearchTest/KnowledgeImportTest/GeneralAgentFlowTest HTTP 夹具仍匿名，收到 401/403。迁移到已有 AuthenticatedProductTestSupport，实际 Cookie/CSRF 登录，不增产品匿名开关、不放宽原业务断言。
- 认证迁移后 32 项，通过 31、失败 0、错误 1、跳过 0；KnowledgeSearchTest 重复添加 cookie-secure=false，属性被合并为 false,false。移除重复测试参数后修复。
- 定向 18 项，通过 17、失败 1、错误 0、跳过 0；历史 profile 标签反例的合法文档只持有旧 source_file_id，无新 document_file 关联。资格条件保留旧显式 SPACE_KNOWLEDGE source_file_id 合同，不改变 profile 过滤断言；调查附件仍不因此获得空间知识资格。
- 统一回归 97 项，通过 94、失败 1、错误 0、跳过 2；ResourceAuthorizationTest 正向引用继承仍在认证调查第一个 QUEUED Run 未结束时开第二轮，收到第四组规定的 409。夹具改为先明确取消第一轮、保留引用再继承，所有新引用 ID/旧引用/跨身份断言保留，不修改串行规则。
- 最后三类补验 19 项，通过 15、失败 0、错误 4、跳过 0；新增材料测试中 AssertJ 对 TransactionTemplate 泛型结果推断歧义，生成错误类。把返回值显式接为 UUID 后，受影响 InvestigationMaterialTest 四项全部通过。

- 为 5.4 补充历史附件报告/版本回读、未知作者和零额外模型调用反例，并执行原 AgentReportToolTest。首次 5 项通过 4、失败 0、错误 1、跳过 0：报告测试在局部方法里另建匿名 TestRestTemplate，未覆盖到前述夹具迁移。改用同一真实认证助手后，AgentReportToolTest 四项全部通过；报告白名单及跨身份断言未改变。

### 最终有效证据（按类去重，非全量套件）

| 测试类 | 总数 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| InvestigationMaterialTest | 4 | 4 | 0 | 0 | 0 |
| KnowledgeImportTest | 8 | 8 | 0 | 0 | 0 |
| KnowledgeSearchTest | 15 | 15 | 0 | 0 | 0 |
| GeneralAgentFlowTest | 21 | 21 | 0 | 0 | 0 |
| AuthenticatedResourceTest | 6 | 6 | 0 | 0 | 0 |
| ResourceAuthorizationTest | 9 | 9 | 0 | 0 | 0 |
| InvestigationDiscussionTest | 5 | 5 | 0 | 0 | 0 |
| ScopedAgentRuntimeTest | 5 | 5 | 0 | 0 | 0 |
| RunRecoveryTest | 22 | 20 | 0 | 0 | 2 |
| SkillRuntimeIntegrationTest | 3 | 3 | 0 | 0 | 0 |
| AgentReportToolTest | 4 | 4 | 0 | 0 | 0 |
| **合计** | **102** | **100** | **0** | **0** | **2** |

两项跳过是 RunRecoveryTest 真实模型 opt-in，不计通过。OpenSpec strict validate 和 diff --check 均退出 0。

新增反例实际使用真实 PG：同内容跨空间文档不串、A 未发布/B 检索三通道为空、A 本段可检索、直接原文与 contextRefs 继承绕过拒绝、运行后新增附件本段不可用/下一明确段可用、发布后 B 可召回/继承、SIMULATED 不提升、原附件继续可读、双成员同时发布只一条、READY 状态失效不展示成功、空文档失败重试不冒充成功、PDF/只读/跨调查绑定拒绝。另用真实 Cookie/CSRF 完成 TXT 调查上传→状态→原文→知识列表未发布为空→显式发布→知识列表出现，全程无 Run 副作用。历史取消 Run 的报告与来源版本实际回读通过，报告读取不产生 RUN_MODEL 事件；旧空间知识上传者仍为 null，未补造作者。

协议使用明确 scripted/捕获替身及 deterministic embedding；数据库为真实隔离 PG16.15，测试资料为模拟材料，不是现场数据或真实 embedding 质量验收。日志保留在忽略的 `outputs/group5-*.log`。原第四组验收与此前真实 deepseek-flash 补验记录保留，不与本轮相加冒充单次套件。

### 未验证项与下一入口

本轮未运行全量 Maven/CLI 打包、前端构建或完整浏览器、真实模型/embedding、SMTP/TLS、真实现场或部署认领；未重启现有 backend/frontend，运行库未升级。前端附件和发布界面仍待第 6 组接线，不能据后端验收宣称完整浏览器产品完成。

此前真实模型 HUMAN_REVIEW / UNCONFIRMED_FINDING 和正常 KNOWLEDGE 答案质量未通过问题仍未修复，本组未放宽 Guard。按照已确认排期，下一步应单独授权 apply `improve-evidence-aware-answer-guard`，完成服务端/协议/组件后再进入本 change 第 6 组及联合浏览器验收。本轮不自动切换变更。


## 2026-11-20 第六组接线：主链已实测，整组尚未验收完成

本次未修改后端、迁移或产品运行库。保留进入时 Guard v2 的全部未提交实现与展示。第 6 组七项均保持未勾选：本次已有可用接线，但尚不足以证明各任务完整验收。整体仍为 20/33，不把部分实现折算为整项完成；第 7/8 组未执行。

### 已实现的前端接线

- `api.ts`：同源 Cookie、写请求 CSRF、登录后重新获取 CSRF、me/退出/恢复接口、空间 Header、期望调查版本、结构化讨论引用。账号或空间切换使旧请求失效并关闭旧 SSE；保留服务器错误状态，不自动重发冲突请求。
- `AuthenticatedWorkbench.vue`：登录/注册/找回/重置、邀请确认、真实空间选择及团队创建；口令和会话凭证不存浏览器存储。账号/空间/角色改变重建原 App；周期回读成员资格。
- `WorkspaceAdministration.vue`：真实成员、邀请投递状态、改角色/移除、团队改名、空间知识列表。管理员入口按服务器返回角色展示，实际写入仍由后端裁决。
- `App.vue` 继续复用原结果、历史、报告、补答和时间线组件，普通提交携带实际已看到的 conversation.version；409 保留草稿并回读。增加取消确认、只读写按钮条件、运行中草稿输入及周期状态同步；不恢复逐句审核或强制配引用。
- `InvestigationCollaboration.vue`：讨论独立发表、显式引用/移除，附件列表/原文/重试及发布名称/适用范围确认。调查上传改用调查级附件接口；旧 api.uploadMarkdown 仍保持原知识导入含义。

### 实际验证及失败历史

1. `npm --prefix frontend run test -- --run` 首次 106 项：通过 101、失败 5、错误 0、跳过 0。原因是旧 fetch 夹具没有提供新增 CSRF 读取，导致响应被提前消费或走到错误路由；为旧测试明确增加 CSRF 夹具，未关闭产品校验。随后 106/106 通过。
2. 增加 authentication.test.ts 五项合同测试：登录前后 CSRF、Cookie、空间 Header/调查版本、A→B→A 晚到响应、me/退出和 409 不重发。最新共 111 项，通过 111、失败/错误/跳过 0。不是 Vue DOM 或完整三角色浏览器验收。
3. 新测试最初使 `npm --prefix frontend run build` 类型检查失败：无参数 vi.fn 推断为零参数元组，不能读取第二项；补充真实 fetch 参数签名后修复。最新类型检查与 Vite build 均退出 0。
4. 隔离真模服务首次启动用错 PG 用户 postgres：测试总数 1、通过 0、失败 0、错误 1、跳过 0（role does not exist）。查原验收记录确认专用用户 dt_test 后，使用 127.0.0.1:54339 的随机库重启成功。未访问 54329 产品库，未改 8080/8081 产品服务。
5. 使用现有 AuthenticatedRealModelTest 的显式 hold 模式提供临时 product 服务，端口 58777，随机库 devicetruth_test_8447a201ec6e408dac5be9754e60859c。沿应用已有私有模型配置加载，不读取/回显密钥；推理 openai/实际模型 deepseek-flash，embedding deterministic，资料 SIMULATED，不是现场数据或真实 embedding 验收。
6. 浏览器实际登录 admin@example.com → 切换团队 → 不选设备提交一般知识问题 → COMPLETED → 打开持久报告。浏览器 Run `8ee1ed69-0072-43d6-b3c6-496da7b135ef`、调查 `bbaad94b-9776-4dca-aaa2-7e8b67a2accf`。已回读模型事件：VALID_PROPOSAL 和 VALID_SEMANTIC_REVIEW，模型均为 deepseek-flash；答案为 KNOWLEDGE，风险 LOW，来源为空时如实说明一般性解释。证据保存在忽略目录 `outputs/validation/authenticated-real-model/group6-browser-run.json`。
7. 浏览器验证登录刷新恢复、切回团队回读原调查、发表讨论及选入正式补充；选中引用未发起新分析。报告显示原 Run ID 和 SIMULATED，保留打印/另存 PDF 按钮。未实际输出 PDF 文件。
8. 使用浏览器 viewport 接口检查 1366×768、1920×1080、390×844，并观察返回截图；窄屏采用折叠记录入口。移除桌面侧栏与顶栏重复工作区导航，新增表单焦点/控件样式。截图仅在本次工具输出，尚未形成可追溯的本地截图组；大屏截图输出有黑色画布，不能算完整像素验收。最后的成员定时同步等修补仅通过测试/构建，未再做浏览器回归。

### 第六组仍需完成的工作

- 6.1：受邀邮箱不匹配、注册后接受、重置链接完整流程及登录失败具体码的前端/浏览器回归；真实邮件能力与前端流程分开记证据。
- 6.2：按认可原型进一步整理空间导航/管理工作区；新建、上传首次创建、搜索筛选分页以及草稿键的完整 DOM 回归。
- 6.3：管理员/协作/只读三账号浏览器操作与直接拒绝核对；管理提交后的角色/名称即时反馈及失败回滚。
- 6.4：同轮补答的讨论引用、并发 409、运行结束不排队及取消目标全路径；补答和深入调查草稿仍需按账号/空间/调查/Run 完整隔离测试。
- 6.5：附件上传/解析/原文/发布的真服务浏览器验收、分页超过 100 项的入口、来源作者/版本/数据性质完整呈现；当前列表只取首 100 项，不视为全量资料浏览完成。
- 6.6：撤权清页、同事推进、定时同步与切换交错的 DOM 回归；当前普通草稿已按账号/空间/调查存储，其他草稿尚未完整覆盖。
- 6.7：最终候选的三尺寸截图留档、加载/失败/只读/有限结果屏幕及键盘焦点复验。

下一责任仍为本 change 的前端实施与验收，不需要用户重复授权；按上述缺口继续第六组，不能据本次主链成功直接勾选七项。

临时服务收尾：通过本次 hold 的 stopFile 正常停止 58777 服务，Maven 退出 0；AuthenticatedRealModelTest 最终 1 项通过、失败/错误/跳过 0，随机测试库由其 finally 清理。未停止独立 PG 容器或任何既有产品服务。最后前端 111/111 与 build 再次通过。日志另有 NoResourceFoundException（静态资源请求）；尚未定位对应请求路径，不计作全站无错误验收。


## 2026-11-20 第六组继续实施（本节持续更新）

沿原 change 完成剩余接线；保留此前 Guard v2、前端及其他未提交工作，不修改产品部署、54329 产品 PG 或 8080/8081 服务。专用测试宿主 `WorkbenchBrowserAcceptanceTest` 在 54339 创建随机库，启动真实 product/OpenAI兼容 deepseek-flash；仅邮件传输是显式本地替身，embedding=deterministic，资料=SIMULATED。该宿主仅在 `devicetruth.test.workbench-browser=true` 时启用，无生产测试接口。

### 6.1 已完成

- 登录前强制获取当前 CSRF，登录成功先建立明确账号状态，再独立加载新 CSRF/授权空间；空间失败提供重试。401 清理内存凭证、关闭旧流，403 清除过期 CSRF 但不自动重发，退出清理当前账号私人草稿。存储不可用不阻止登录。
- 浏览器实测错误密码→明确提示→正确登录、刷新恢复、退出、受邀邮箱不匹配403→退出→注册匹配邮箱→明确接受并保留个人空间。没有演示身份开关。
- 前端合同测试覆盖请求Cookie/CSRF、凭据不写local/sessionStorage、过期me后重新登录、旧CSRF晚到拒绝、邀请403及密码恢复503/无效凭证400。最新前端121项：通过121、失败/错误/跳过0；类型检查与Vite构建退出0。
- 隔离真实HTTP的邀请/重置协议补充14项均通过：先注册后加入、保留个人空间、不匹配拒绝、确认不一致、有效重置、旧会话401、重放拒绝及新口令登录。邮件只经过捕获传输，不计真实收件或第8组验收。
- 修复历史：前端首次改认证顺序后旧测试预期失败1项（110/111通过），按“登录前新CSRF、登录后工作区初始化”的实际合同调整测试后通过；HTTP脚本误预期注册201，实际合同200，前段通过记录保留在 `outputs/validation/workbench-group6/api-checks-first-attempt.json`，只重跑受影响账号段，未重复已有效的真模调查。

本节证据目录：`outputs/validation/workbench-group6/`；121项是前端测试数，不折算浏览器或HTTP断言。其余任务完成情况待下文验收逐项补充。


### 6.2 空间与调查导航、6.3 团队角色已完成

- 按账号/空间切换重建原工作区；没有重复工作区导航、空证据列或演示成员注入。新建团队浏览器只出现真实创建人，头像取真实姓名首字。个人、团队、不选设备路径均实测；新调查附件改为本地待发送，首次有效提交才建调查和上传，不把上一轮“上传即建调查”的偏差继续当合同。
- 搜索、筛选、游标分页沿原 RecentInvestigations；真实 PG 的24条明确标注“协议夹具”调查用于界面/分页检查，不产生模型答案。浏览器实际加载20+4条；HTTP检查复核无重复。
- 本轮发现真实分页缺陷：GeneralAgentService 旧游标用 toEpochMilli 截断 PG 微秒，微秒相同的后续行被漏掉。最小修复新游标为 epochSecond+nano，仍读取旧三段毫秒 token，空间/筛选绑定不变；不是重做后端或迁移。ScopedAgentRuntimeTest 定向PG回归1项通过，验证微秒并列和旧游标兼容。旧临时54918保留至验收收尾，新候选49672使用同一专用随机库；产品8080/8081不变。
- 管理员浏览器实测最后管理员降权拒绝、选择器回滚、团队改名即时同步、邀请待接受/投递状态、受邀注册接受；协作和只读浏览器入口分别核对。真实HTTP覆盖只读创建/讨论/分析/发布403、协作管理403和只读读取200；角色调整/移除后原浏览器自动变只读、失去成员资格后清掉旧调查回到授权空间。
- 材料侧另通过真实上传/发布接口准备21份SIMULATED分页资料、1份非法UTF-8和1份PDF，48项定向检查通过。资料分页及失败文件与模型实际答案证据分别保存。
- 失败历史保留：分页最初20+0使生命周期脚本断言失败；修复后独立分页检查20+4通过，不重复先前已有效的真模补答/取消检查。注册201预期错误不影响团队服务合同；HTTP账号段已按实际200修复并通过。


### 6.4 共同接续、6.6 异步与草稿隔离已完成

- 讨论发表与选择引用不运行模型；正式消息/补答只发送 discussionId/version，由原服务解析作者与未核实快照。浏览器验证引用可移除，正式提交/Enter补答、实际作者与原Run/原asOf保留。真模首轮 `15ba25e8-e4b1-4e39-b118-f1b118335f8e`、同轮补答 `cb0cc9b2-09e0-4c53-8dda-4a6a55431f9b` 均COMPLETED，审核version=2。
- 真实两账号竞争普通提交只接受一项；过期普通版本/旧问题补答409拒绝，未转换新运行。实际浏览器竞争时，页面先收到同事消费问题后的权威更新，旧提交按钮已退出；因此没有在该次浏览器竞态中强行制造409。409响应、保留草稿和不自动重发分别由真实HTTP及Vitest合同覆盖。
- 本次竞态发现旧补答草稿原先虽保存在存储中却没有可见入口。补上“未提交的旧追问草稿”，按账号/空间/调查/Run/问题版本找回，可阅读/复制或主动丢弃，不自动降级成普通消息；浏览器刷新后仍看到原草稿。深入调查、普通输入、讨论及讨论引用草稿亦有各自范围键；退出清当前账号私人草稿。
- 取消改为绑定具体Run ID的原生dialog确认；按钮、Enter及处理函数共用runPermissions的写权与当前执行段发起人/管理员判定。真实浏览器验证保留运行、Escape、焦点限制在dialog、确认后CANCELLED；实际确认截图 `cancel-confirm.jpg`。HTTP另验证普通同事取消他人轮403、管理员可取消。侧栏取消状态同步更新，不复用轮号、不自动提交草稿。
- 账号/空间切换使旧HTTP读取失效并关闭SSE；Run/附件等待在下一次请求前停止旧范围。scope.ts的来源键显式包含账号/空间/调查/Run/version；同ID新版本拒绝旧来源读取，保留原事件seq续传及有界失败回读。
- 浏览器实测：补答草稿切空间再切回恢复；团队降权变只读；移除成员后旧调查/列表清页并回到可访问空间；删除本次测试账号JDBC Session模拟到期后返回登录，再登录成功，无反复CSRF403。其余现场/生产会话未操作。
- 前端定向新增角色共享许可、过期会话、跨版本、SSE关闭与晚到帧、停止旧轮询、私人草稿恢复等检查。最新前端127项通过。历史浏览器两条时序/文案断言失败保留，已用对应复验覆盖，最终浏览器清单在收尾时归档。


### 6.5 材料、来源与报告、6.7 最终视觉验收已完成

- 调查附件列表/原文/解析状态/重试、名称与适用范围确认后发布、空间知识分页均接真服务。浏览器验证MD和TXT，未发布附件不被伪装成空间知识；发布后保留原作者、原版本和SIMULATED。真实HTTP材料48项通过，浏览器实际打开第二页、非法UTF-8重试及PDF/OCR未实现状态。
- 保留直接候选、背景和未知分组、前三项下一步以及完整建议。新增ViewModel与reportView回归，覆盖换执行人后对象绑定、COMPLETED但HUMAN_REVIEW/limited、历史引用版本与性质、报告纯投影无模型请求。
- 正常资料问答和同轮补答来自真实deepseek-flash；有限/失败/候选分组及24项目录记录为明确标注“协议夹具”的隔离PG持久数据，只验证显示和分页，不计模型或现场结论。浏览器实际打开有来源、无来源、有限、失败、取消和历史报告；来源详情保留原文、版本、性质，删除重复的空来源提示。
- 报告仍复用原DiagnosisReport与window.print，新增账号/空间栏在打印样式中隐藏。最终只读回读模型事件与最初保存的次数相同，报告/来源读取没有额外模型请求。本次内置浏览器点击原生打印时工具CDP超时，未提供可操作的打印预览能力，未生成或声称验证PDF文件落盘；报告打印入口与浏览器原生流程保留，PDF/OCR输入能力仍未实现。
- 实际检查1366×768、1920×1080和390×844，三尺寸DOM均无横向溢出，窄屏记录抽屉可操作。使用tab.screenshot保存源JPEG，解决上一阶段截图黑画布的取证问题；已观察实际图像。另归档登录、真实加载等待、个人空状态、只读、来源报告、有限/失败协议夹具、取消确认/取消终态。
- 加载反馈通过在本次随机库短暂锁定成员表25秒观察真实异步读取，自动释放后页面恢复；未关闭安全校验、未替换产品推理器。取消使用原生dialog，焦点限制、Escape和正确目标收口已验。
- 视觉落地沿用专业页现有紫蓝token与候选三栏；空间在账号栏选择，成员/知识按需折叠，调查记录/过程和证据继续复用原抽屉，讨论与附件就近提供。原型的账号→空间→调查→结果/讨论/记录/来源层级保留，不引入原型身份/结束运行演示开关。

### 第六组最终验收汇总

| 检查 | 总数 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| 最新前端Vitest（12个文件） | 132 | 132 | 0 | 0 | 0 |
| 最终有效浏览器断言 | 35 | 35 | 0 | 0 | 0 |
| 游标微秒/旧格式及空间绑定PG定向回归 | 2 | 2 | 0 | 0 | 0 |
| 修复后的临时候选宿主收尾 | 1 | 1 | 0 | 0 | 0 |

`npm --prefix frontend run build`：vue-tsc和Vite均退出0。`mvn -q '-Dtest=ScopedAgentRuntimeTest#cursors*' test`：2/2通过，使用专用54339的随机测试库、worker关闭的协议夹具，不计真模。产品浏览器及HTTP材料/协作检查连接真实product/PG，推理openai/deepseek-flash，embedding=deterministic，资料SIMULATED。

HTTP分段有效证据：首段20项、账号14项、材料48项、生命周期12项、修复后的分页/角色4项、恢复授权7项、移除2项、最终只读回读4项；与JUnit/浏览器计数分开。原账号逐项文件被后续诊断读取覆盖，保留实际终端14/14输出汇总及可执行脚本，不补造逐项轨迹。

历史失败完整保留：浏览器初始37项中35通过、2项为时序和文案匹配断言问题，对应复验已通过，原项和supersededBy在browser-checks.json；HTTP注册成功码误预期与真实游标精度缺陷各1项；前端认证顺序预期1项；首版长期宿主收尾查询误读不存在的failure_code列，1项错误。failure_code实际来自RUN_FAILED事件，不应读主表；已修复源码，候选宿主相同有效列快照正常完成1/1。首版错误报告另存original-host-junit.xml，不覆盖候选的通过记录。

两临时服务54918/49672均已正常发出停止信号并退出；候选先关闭，原宿主随后关闭并在finally删除随机库。实际查询pg_database计数为0，专用54339及既有8080/8081/54329容器仍在。没有更新产品部署/运行库、没有执行第7/8组、没有Git提交或推送。

最终证据：`outputs/validation/workbench-group6/acceptance-summary.json`、`browser-checks.json`、`final-real-runs.json`、`browser-main-run.json`、`browser-reply-detail.json`、PG/宿主JUnit和日志、12张JPEG。源码与前端构建产物已就绪，当前产品服务未部署这些前端改动；临时URL不作为稳定入口。

**当前任务状态：6.1–6.7全部勾选，整体27/33。剩余6项仅为第7/8组；本次不继续执行。**


## 2026-11-20 原型落地偏差纠正

复核当前 HEAD `5f82847` 的正式前端后确认：页面仍以旧专业诊断工作台为外壳，顶部另叠账号/空间表单栏，团队成员与知识库仍在同一管理折叠区；调查讨论、附件和过程也继续堆在原结果页及旧记录抽屉中。这与已认可的 `prototype/workbench.html` 所确定的统一外壳、独立功能页、调查标签及按需资料抽屉不一致，因此此前 6.2、6.7 的完成勾选依据不足，现将两项恢复为未完成，整体任务状态相应回到 25/33。

本次纠正只针对界面组织与相应验收状态，不否定或重做已完成的账号、授权、协作、材料接口和既有前端业务接线。上文所有历史测试数量、失败修复、真实 deepseek-flash 主链、隔离 PG、截图及临时服务记录均原样保留；旧截图只能证明当时旧外壳的状态，不能替代本次原型同尺寸对照验收。后续只有在统一外壳和页面组织实际落地，并重新完成对应前端测试、构建、真实 product 关键路径与三尺寸截图检查后，才重新勾选 6.2/6.7。第 7/8 组仍不执行。


## 2026-11-20 原型外壳重构：实施、真模抽查与恢复阻塞

本次按用户指出的交付偏差重构正式前端，不新增 change，不执行第 7/8 组。统一外壳由 `WorkspaceShell.vue` 提供，顶栏只保留品牌、真实用户/角色和退出，侧栏统一空间、新建、功能导航、搜索筛选和近期调查；`App.vue` 仍是唯一调查/Run 控制器，没有另建状态机。设备、知识库、团队成员、创建团队分别由独立页面组件承担，不再使用共同 `details`。调查区按“调查概览/团队讨论/分析记录/调查过程”组织；附件、来源和设备快照按需进入统一资料抽屉，原 `ProfessionalDiagnosisPage` 的候选分组、前三项下一步、完整结果、`RunTimeline`、`EvidenceModal`、`DiagnosisReport` 和 `EvidenceComparison` 继续复用。

真实 API 合同未改：Cookie Session/CSRF、空间 Header、expectedConversationVersion、普通提交与原 Run 补答、409 草稿保留、账号/空间/调查/Run 隔离、三角色和取消许可均沿用原实现；没有恢复逐句审核或原型模拟运行。新调查标题通过 `initialMessage` 与首条问题一起提交，仍首次有效提交才创建会话。浏览器中发现并修复：切页保留旧滚动位置、主标题误用后端长摘要及随所看轮变化；最终主标题取首条正式输入第一行，切页回顶部。

### 最新前端检查

- `npm --prefix frontend run test -- --run`：12 个文件，135 项，通过 135、失败 0、错误 0、跳过 0。新增标签组织、首次标题提交及讨论引用原 Run 补答回归。
- `npm --prefix frontend run build`：`vue-tsc --noEmit` 与 Vite 均退出 0。
- `git diff --check`：退出 0；仅有 Git 的 LF/CRLF 提示。

### 隔离真实 product / PG / 模型抽查

使用专用 `dt-workbench-migration-test`（54339）中的随机库 `devicetruth_test_6f2c8a91d4e74b7f8d3a2c5e9107ab64` 和独立临时端口 55743；未连接 54329 产品库，未更新或重启 8080/8081 产品服务。推理器为 OpenAI-compatible，实际模型 `deepseek-flash`；embedding=deterministic，手册和运行资料为 SIMULATED，不是现场数据或真实 embedding 验收。

- 实际注册并刷新恢复管理员，创建“原型协作验收团队”；个人空间与团队空间切换回读正确，新团队只有真实创建人，无示例成员/设备/调查注入。
- 新建“模拟手册知识问答”，首次提交上传并解析真实 MD 附件。Run `0c0b3c51-de7e-467e-922a-98c096e33ba5` 为 COMPLETED，模型事件含 `deepseek-flash`、`VALID_PROPOSAL`、`VALID_SEMANTIC_REVIEW`，共 4 次有效模型尝试，2 条真实手册来源；页面显示直接候选、背景、未知分组、前三项下一步及 Guard v2 整体审核。
- 发表讨论后数据库仍为 1 个 Run；显式引用并正式提交后才创建第二轮。模型进入 WAITING_FOR_USER，目标 Run `6bca13d4-f1d4-48b5-aa01-6f28c39f57bc`；补答后同一 Run 为 COMPLETED，Run 总数仍为 2，没有第三个 Run，正式消息保留讨论引用。
- 统一附件抽屉实际显示解析状态、版本、SIMULATED 和真实上传者，打开原文，填写名称及适用范围后明确发布；独立知识库页显示发布人、原作者、版本、适用范围和 SIMULATED。设备页为真实空状态。
- 历史“分析记录”返回首轮后，资料抽屉列出 2 条来源；来源详情实际显示来源 ID、版本、章节、数据性质和摘录。等待对话框 role 的一次工具超时后，已通过文档 DOM 确认详情实际打开，不把超时记作功能失败或通过证据本身。
- 启动失败历史：首次误用系统 Java 17 读取 Java 21 类而失败；第二次误用 CLI JAR 入口而被参数拒绝。改用项目 JDK 21 并显式启动 `DeviceTruthServerApplication` 后健康检查通过。上述失败均已修复，不计产品运行测试数。

已有同设备参数的截图保存在 `outputs/validation/prototype-workbench/`：原型概览、正式登录、正式成员页。浏览器设备参数为 1366×768；工具预览报告 1270×714，故不能仅凭文件名宣称完成精确视口验收。

### 暂停后恢复阻塞及未验收项

用户暂停时两个临时 HTTP 服务均已正常停止，随机库保留。恢复时 Docker Desktop 已停止，`desktop-linux` 与 `default` context 均不可连接；本机没有 psql/postgres、没有 `DEVICETRUTH_TEST_PG_BASE_URL`，Ubuntu WSL 磁盘亦不可用。产品 backend/frontend 在 `compose.yaml` 中配置了 `restart: unless-stopped`，直接启动 Docker Desktop 可能连带重启现有产品服务，因此本次没有这样做，也没有操作产品数据库或改连 scripted。

由于隔离 PG 暂不可恢复，尚未完成：报告与只读账号的最终浏览器复验、调查过程标签复验、1920×1080 和窄屏正式对照、精确视口留档，以及恢复后的最终入口回归。6.2、6.7 继续保持未完成；本记录不把此前真模结果扩大为全部原型验收通过。恢复条件是一个不连带启动产品容器的独立 PG/pgvector 环境。


## 2026-11-20 原型外壳重构最终验收：6.2、6.7 完成

在隔离 PG 恢复后完成本轮剩余检查。正式页已不再是旧专业三栏加账号栏：统一顶栏/侧栏、设备/知识库/团队成员/创建团队独立页面、调查四标签和统一资料抽屉均接真实 API。6.2、6.7 现按以下完整证据重新勾选，整体回到 27/33；第 7/8 组未执行。

### 最终自动化结果

| 检查 | 总数 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| 前端 Vitest（12 个文件） | 135 | 135 | 0 | 0 | 0 |
| 精确视口/布局与报告只读检查 | 13 | 13 | 0 | 0 | 0 |
| 搜索、筛选、游标分页浏览器检查 | 6 | 6 | 0 | 0 | 0 |

- 最终命令仍为 `npm --prefix frontend run test -- --run`、`npm --prefix frontend run build`，类型检查与 Vite 均退出 0。
- `openspec validate add-authenticated-team-workbench --strict`、`git diff --check` 均退出 0。
- 13 项布局检查使用 Playwright 明确 viewport，不是内置浏览器缩放预览：原型和正式页各检查 1366×768、1920×1080、390×844，`innerWidth/scrollWidth` 一致且无横向溢出；另含只读与报告 Run 断言。所有最终 PNG 均已实际打开观察，位于 `outputs/validation/prototype-workbench/`。
- 分页使用 21 条明确标注“协议分页验收”的持久消息和 COMPLETED Run 行，首屏 20、第二页 1、ID 无重复；没有 `RUN_MODEL` 事件或诊断答案。搜索完成/进行中筛选及未知标题空结果均通过。主隔离库最终共有 24 个 Run，其中 21 个是这类协议夹具；真模主调查只有 3 个 Run。

### 真实 product 关键入口

- 登录/刷新、个人↔团队切换、创建团队、无设备新建与首次提交、四标签切换、讨论显式引用、原 Run 补答、附件原文/解析/发布、知识库、历史来源详情、报告和只读均已实际操作。只读账号创建调查及发表讨论的直接 HTTP 请求均为 403。
- 真模主链仍使用 `deepseek-flash`，embedding=deterministic，数据为 SIMULATED。成功 Run `0c0b3c51-de7e-467e-922a-98c096e33ba5`、补答 Run `6bca13d4-f1d4-48b5-aa01-6f28c39f57bc` 均 COMPLETED；后者恢复同一 Run，没有创建第三个补答 Run。
- 报告第二次打开前后该 Run 的 `RUN_MODEL` 事件均为 4，未额外调用模型。调查过程显示真实模型、等待、补答、审核/修正事件。取消目标 `8cf028df-b589-4fd4-ac40-bbbe2e871546` 已确认 CANCELLED；取消前私人补答草稿保留，未自动提交，只读回开没有新增 Run。主库最终 9 条 `RUN_MODEL` 事件对应上述 3 个真模 Run，21 个分页夹具没有模型事件。
- 加载、个人设备空状态、只读、取消确认/终态、失败均有最终截图。失败 Run `97baa69a-0e97-4e63-a875-e58c896ee3e2` 为独立随机库中的配置故障注入，实际原因是 `OPENAI_API_KEY_MISSING`；不计真实模型成功或外部网络故障验收。

### 看图后修复与失败历史

- 窄屏近期调查最初被旧 `pro.css` 的 1100px 规则隐藏，已在新容器恢复显示并修复重复标题；最终 390×844 可展开搜索和切换调查。
- 切页滚动位置、主标题随轮次/长摘要变化已修复；主标题固定取首条正式输入第一行。
- 常规截图加载页拍到完成后的登录页，改用 CDP 在真实 `/api/auth/me` 延迟期间立即抓帧，最终截图确为“正在恢复登录状态…”。
- 失败注入首次因私有配置优先而实际完成，第二次因隔离工作目录缺 Skill 根目录启动失败；补绝对 Skill 根目录后，最终在无私有配置工作目录得到上述缺失密钥失败页。失败卡片先出现时侧栏短暂“进行中”，已等待权威列表同步为“失败”再留档。
- 分页最初创建完全空会话但摘要不包含它们；随后显式补协议消息和持久 Run。一次试图更新不存在的 `dt_conversation.title` 列的 SQL 全部报错且无数据变化，未改表结构。取消脚本的文本查找不适用于 textarea 值，实际取消和草稿已在 DOM/数据库/回读中分别确认，没有为断言重复跑模型。

### 与原型的具体差异及理由

- 原型“预览场景”和“结束本轮演示”不进入产品，运行、角色和状态全部来自服务端。
- 正式概览保留直接候选、背景/未知判断、Guard v2 整体审核、前三项下一步和完整结果，因此页面比原型的有限结果样例更长；这些是必须保留的真实结果，不以裁切内容换取首屏一致。
- 窄屏原型隐藏近期调查；正式页改为可折叠的真实近期调查，避免窄屏无法切换历史。这是有意交互差异，已实测无横向溢出。
- 资料/来源/附件使用统一右侧按需抽屉；完整报告在主内容区按需打开，以保留原报告打印和宽屏排版，不再次调用模型。没有另设常驻来源栏。
- 成员数量、作者、设备、调查及知识条目都来自当前空间真实数据；新团队没有原型示例头像组、设备或调查。

本轮未生成实际 PDF 文件，打印/另存 PDF 入口保留；PDF/OCR 附件能力仍未实现。未执行第 7/8 组、真实 SMTP/收件、HTTPS、现场设备或真实 embedding 验收，未做全量 Maven/CLI 回归。

临时 55743、55744 和原型预览服务均已停止，不成为稳定依赖。随机库 `devicetruth_test_6f2c8a91d4e74b7f8d3a2c5e9107ab64`、`devicetruth_test_b71c94f2a63e48d0b58fc2d9a410e885` 均已删除，回查数量为 0。未更新/重启现有产品服务或操作产品数据库，未提交或推送 Git。


## 2026-11-21 用户调整 8.3 验收口径

用户明确决定：真实邮箱是否实际收到邀请和密码重置邮件，由用户次日自行检查，不再作为本 change 自动验收及勾选 8.3 的前置条件。相应修改当前 `tasks.md` 的 8.3 和 `design.md` 验证策略，历史原文、历史测试和此前“真实收件未完成”的记录均保留，不改写。

调整后的自动验收仍必须覆盖：真实 SMTP 的外部发送与服务接受/失败状态、链接生成、显式重发及凭证轮换；隔离协议流程中的邀请接受、错误邮箱拒绝、撤销/过期、密码重置、重放拒绝和旧会话失效；以及 HTTPS Cookie、CSRF 和匿名拒绝。不得把本地邮件捕获或协议替身冒充外部 SMTP 发送。SMTP 配置缺失仍阻塞本项。

此项只免除“自动证明邮件已到达收件箱”的前置要求，不免除发送功能、权限、安全和协议验证。用户后续人工收件结果另作补充记录，不影响本次按新口径完成任务 7/8。


## 2026-11-22 任务 7.1：显式旧归属认领与 dry-run 完成

新增 V24 `dt_legacy_ownership_claim` 审计表、`LegacyOwnershipClaimService` 和独立 `LegacyOwnershipClaimTool`。命令默认 dry-run，只有显式 `--execute` 才在事务内创建专用 TEAM、指定 ADMIN、绑定旧会话并写审计；没有新增 HTTP 管理入口。清单动态统计所有带 owner/project 的关联表，记录活动 Run/租约、未知作者和文件存在性/字节数/SHA-256。历史作者不回填。命令与安全切换边界见 `migration-runbook.md`。

实际命令：`mvn -q '-Dtest=LegacyOwnershipClaimTest' test`，使用仅绑定 127.0.0.1:54339 的隔离 PG16.15/pgvector 和随机 `devicetruth_test_` 数据库、临时文件目录。最终 3 项，通过 3、失败 0、错误 0、跳过 0。覆盖真实 CLI dry-run 不写库、实际执行、重复认领拒绝、错误管理员、文件缺失、触发器注入中途失败后团队/成员/审计/绑定全部回滚；真实 Cookie/CSRF HTTP 登录后读取三个旧未决 Run，对 WAITING 原 Run 补答且 Run 总数不变、asOf 不变、新 execution actor 为管理员、initiated actor 仍为空，并读取旧文件原文。推理 worker 关闭，无外部模型/embedding/SMTP 调用。

失败修复历史保留：首次测试编译错误 2 次（错误调用 DataSource.close、测试方法缺 throws），未执行测试；随后 3 项因夹具 JSONB 未显式 CAST 全部错误；修正后 3 项中 dry-run 通过，但执行锁把 PG void 错映射为 Java Void，2 失败/1 错误；改为只消费锁结果后 2 通过/1 失败，失败为测试在登录后仍使用旧 CSRF 得到 403。刷新 CSRF 后最终 3/3。上述失败均未操作用户运行库。

7.1 已勾选，apply 进度回读 28/33。未执行实际线上认领、切换或产品恢复；7.2 隔离恢复、HTTPS 与旧入口演练及第 8 组仍待完成。


## 2026-11-22 任务 7.2：HTTPS 与匹配备份隔离恢复完成

新增 `SecureDeploymentRecoveryTest`，实际启动两个独立临时 HTTPS product：源端使用 scripted/deterministic 和真实 PG 完成一个 COMPLETED Run、保留一个 WAITING Run、建立 JDBC Session 和文件元数据；停止源端后以 PG TEMPLATE 克隆随机库、复制临时文件，再由同一候选程序恢复。未连接 54329，未使用 compose product，不重启 8080/8081。

最终命令：`mvn -q '-Dtest=SecureDeploymentRecoveryTest,ScopedAgentRuntimeTest#authenticatedStartupDoesNotResumeUnclaimedLegacyRunsButInternalEntryStillWorks,AccountWorkspaceMigrationTest' test`。总数 6，通过 6、失败 0、错误 0、跳过 0：SecureDeploymentRecoveryTest 2 项、旧内部恢复定向 1 项、基础迁移 3 项。检查源/恢复库关键表行数一致、HTTPS Secure/HttpOnly/SameSite=Lax、旧 Cookie 与显式 Cookie 重放均可恢复会话、匿名 401、无 CSRF 写 403、当前 CSRF 写 201、COMPLETED/WAITING 状态与可展示引用集合/文件原文一致、SMTP HTTPS public URL 配置可装配、CLI `--help`、legacy 健康/会话创建/消息读取和旧内部 `recoverQueued`。scripted、deterministic、自签测试证书和未发送的 SMTP 装配不计真实模型、真实 embedding、受信任生产证书或真实邮件验收。

失败历史：首轮 6 项 5 通过/1 失败，知识导入实际合同为 201 而非测试误写 200；第二轮发现整表引用含 USER_STATEMENT，而接口按原合同只展示知识/配置/现场观测，改为按该 Run 可展示类型核对；第三轮文件 503 是因旧显式知识导入不生成调查上传原文件，隔离副本补入与元数据对应的原始手册字节后完成匹配文件恢复。没有为通过测试修改产品 Guard、来源过滤或旧导入用途。活动 Run/租约与历史未知作者盘点由 7.1 清单及测试覆盖。

部署、停写、SMTP、Session、恢复和回退说明见 `migration-runbook.md`，V24 数据字典见 `data-foundation.md`。7.2 已勾选，整体 29/33；未执行实际线上切换/认领/恢复，真实 SMTP 外部发送与真实模型双浏览器协作仍属第 8 组。


## 2026-11-22 任务 8.3：真实 QQ SMTP 与隔离 HTTPS 协议通过

新增显式 opt-in `RealSmtpAcceptanceTest`，实际命令：`mvn -q '-Dtest=RealSmtpAcceptanceTest' '-Ddevicetruth.test.real-smtp=true' test`。最终 1 项，通过 1、失败 0、错误 0、跳过 0。测试在随机隔离 PG 和临时 HTTPS product 上运行，邮件由真实 `JavaMailSenderImpl` 经 QQ SMTP 发送；只有父类发送成功后才从应用自己生成的邮件正文捕获本次令牌做协议回环，不是本地邮件替身，不读取邮箱、不使用 IMAP。

实际外部发送 6 封：邀请首次与显式重发、撤销和过期协议各一封、密码重置首次与显式重申请各一封。6 次均为 SMTP 服务接受，邀请/重置记录为 ACCEPTED_BY_PROVIDER；重发前后令牌不同且响应/数据库不保存原始令牌。真实 HTTP 验证受邀注册加入且原角色不被后续邀请提升、错误账号 403、撤销/过期/重放拒绝、重置确认不一致拒绝、成功重置后旧会话 401、旧密码拒绝/新密码可登录、Secure/HttpOnly/SameSite=Lax、无 CSRF 403、匿名 401。证据 `outputs/validation/workbench-group8/smtp-acceptance.json` 不含收件地址、口令、授权码或原始令牌，`mailboxReceipt=PENDING_USER_MANUAL_CHECK`、`imapUsed=false`。真实收件留用户后续人工检查，不据此提前宣称到达收件箱。

启动/配置失败历史：首次误把本地模型配置全局导入，空 embedding 维度导致启动错误，未发送；随后两次邮件配置解析未找到收件账号，仍未发送。改为邮件适配器内按批准配置文件读取邮件项，并为隔离协议使用显式 HTTPS 测试链接后通过。SMTP 服务失败/超时仍由既有 AuthMailTest 协议替身覆盖，不冒充外部失败注入。

8.3 已按用户调整后的口径勾选。未操作现有产品服务或产品数据库，未提交或推送 Git。


## 2026-11-22 任务 8.2：双账号真实模型团队接续通过

新增显式 opt-in `FinalCollaborationBrowserHostTest`，在随机隔离 PG 和独立端口启动真实 product，前端为当前 `frontend/dist`，推理器 openai、实际模型 deepseek-flash，embedding=deterministic，手册及全部运行资料为 SIMULATED，无现场数据。管理员与协作成员分别使用 127.0.0.1 和 localhost 两个源，Cookie/会话彼此独立但团队相同；未连接或修改现有产品服务/数据库。

实际浏览器链路：管理员创建手册调查，首轮 Run `2e4e456d-7724-4146-99a7-b4751b20f186` COMPLETED、5 次模型事件、2 条手册引用；协作成员读取同一调查并发表讨论时仍只有 1 个 Run，显式引用并正式提交后才出现第 2 轮 `c61b21a5-c970-47c1-be5c-1f380ad15706`。该 Run 两次 WAITING，管理员用同一问题三元组竞争补答，最终 COMPLETED、version=8、6 次模型事件、1 条引用，没有为补答新建 Run。第一次竞争文本仍缺设备对象，模型如实再次追问；补入模拟对象后完成，未篡改结果求绿。

管理员回读第 1 轮、2 条来源及第 2 轮报告；报告含真实 Run ID、手册版本/章节、SIMULATED、风险和限制。刷新后账号、空间、历史均恢复。另两次真实知识 Run `ae3c5218-221d-431d-90b4-dcae59b2f8b6`、`34987929-3426-4173-9c0b-dcb4790977d8` COMPLETED，分别有 4、3 条来源。浏览器出现取消按钮前这两轮已完成，未伪报取消；改用同一真实 product HTTP 提交后立即取消 `a504af05-cf73-41e8-affb-f2a84dcc7028`，接口 200，最终 CANCELLED，事件包含 RUN_CANCEL_REQUESTED、RUN_LATE_RESPONSE(code=CANCELLED)、RUN_CANCELLED，迟到提案未采纳。

宿主正常停止并自动清理随机库，JUnit 1 项通过、失败/错误/跳过 0。证据位于 `outputs/validation/workbench-group8/`：`browser-checks.json`、`browser-real-runs.json`、`browser-inputs.json`、`browser-environment.json`、`cancel-result.json`。取消结果文件保留接口返回时 RUNNING 的原始响应，最终 CANCELLED 与事件以宿主持久回读为准。

撤权/断线、普通提交与问题竞争 409、有限结果恢复分别由本次全量中的 AgentStreamTest、ScopedAgentRuntimeTest、AgentLifecycleTest、InvestigationDiscussionTest、DeliveryReviewTest、AnswerReviewLifecycleTest 以及第六组已有浏览器记录复核；这些是明确协议/状态机证据，不冒充外部模型调用。8.2 已勾选，整体 31/33；真实 SMTP 收件仍由用户人工检查，真实 embedding 与现场设备未验收。


## 2026-11-22 任务 8.1：完整候选全量回归、前端与 CLI 通过

最终执行环境为 Maven 3.9.9、Microsoft Java 21.0.13、真实隔离 PG16.15/pgvector（127.0.0.1:54339，随机库）及本机前端工具链；未使用 54329 产品库。最终 `mvn -q package` 退出 0，Surefire 73 个测试类共 515 项：通过 501、失败 0、错误 0、跳过 14。此前修复后的 `mvn -q test` 也已成功；这里不把两次执行累加冒充 1030 项不同测试。

14 项跳过均为显式 opt-in 或既有条件项：RunRecoveryTest 2、AgentToolFeedbackTest 1、AuthenticatedRealModelTest 2、WorkbenchBrowserAcceptanceTest 2、AnswerSemanticReviewTest 3、SkillRegistryTest 1、ReviewTransportDiagnosticTest 1，以及本次 RealSmtpAcceptanceTest 1、FinalCollaborationBrowserHostTest 1。后两项已在真实验收时分别显式执行通过，默认全量中仍跳过；其余未执行的外部模型/诊断项不冒充通过。

`npm --prefix frontend run test -- --run`：12 个文件、135 项，通过 135、失败 0、错误 0、跳过 0。`npm --prefix frontend run build`：vue-tsc 与 Vite 均退出 0。

`python scripts/verify_cli.py` 退出 0：真实打包 JAR 的 help、场景 A/B/C/D 持久报告分别为 RESOLVED/RESOLVED/CONFLICT/NEED_EVIDENCE，非法参数、Qwen/OpenAI 缺密钥明确 FAILED 且不降级、本地 HTTP 私有配置读取均通过。证据 `target/cli-verification.json`，日志 `outputs/group8-cli.log`。CLI 为 scripted/HTTP 替身离线验证，不计外部模型。

### 全量失败与修复历史

首轮 `mvn -q test` 为 486 项：通过 456、失败 16、错误 2、跳过 12。PersistentConversationTest 10 项和 DualDomainAgentTest 1 项仍使用匿名 HTTP，分别 403；AssetToolTest 旧夹具漏填 V23 round_number，1 项初始化错误；GeneralAgentFlowTest 6 项未区分旧内部 Guard 与新版 v2 审核适配器。没有修改产品 Guard、来源规则或旧入口来迁就测试。

修复方式为：旧 HTTP 夹具接入既有 AuthenticatedProductTestSupport 的真实 Cookie/CSRF 和随机库显式测试归属；资产夹具补 round_number=1；GeneralAgentFlowTest 明确走不支持语义复核的旧内部路径并保留模型尝试审计；并发测试第三次提交前按现合同取消活动 Run；游标测试先解空间外层再解微秒内层。定向命令 `mvn -q '-Dtest=PersistentConversationTest,DualDomainAgentTest,AssetToolTest,GeneralAgentFlowTest,RealSmtpAcceptanceTest' test` 最终 61 项：60 通过、0 失败、0 错误、1 跳过（SMTP 未开启时），随后完整 test/package 均通过。

OpenSpec strict validate 和 `git -c core.fsmonitor=false diff --check` 退出 0；后者只有 acceptance.md 的 LF/CRLF 提示。8.1 已勾选，整体 32/33，剩余仅为 8.4 汇总。


## 2026-11-22 任务 8.4：最终证据汇总与全场景核对

### 五份 spec 与认可原型

本次按五份 delta 全文复核 30 个 requirement、54 个 scenario；下表是证据映射，不把文档条数折算为新增测试数。原型另以其 `prototype/review-history.md` 的 49 项历史检查和第六组最终三尺寸截图/交互记录为基准。

| Capability | requirement / scenario | 最终证据与结论 |
|---|---:|---|
| account-authentication | 5 / 10 | AccountAuthenticationTest、PasswordRecoveryTest、AuthMailTest、TeamInvitationTest 覆盖注册/会话/CSRF/恢复/邀请/限流；RealSmtpAcceptanceTest 实际完成 QQ SMTP 邀请与重置、轮换/撤销/过期/旧会话失效及 HTTPS。真实收件待用户人工检查，按已确认口径不阻塞。 |
| workspace-access-control | 6 / 12 | WorkspaceMembershipTest、ScopedAgentRuntimeTest、AuthenticatedResourceTest、ResourceAuthorizationTest 覆盖空间/角色/持续授权；LegacyOwnershipClaimTest 覆盖旧归属不默授、显式认领、审计和回滚；SecureDeploymentRecoveryTest 覆盖匹配恢复与旧入口。 |
| team-investigation-collaboration | 7 / 13 | InvestigationDiscussionTest、AgentLifecycleTest、AgentStreamTest、RunRecoveryTest 覆盖讨论/引用/串行/竞争/取消/撤权/恢复；第8组两个独立账号的真实 deepseek-flash 主链实际完成讨论不入模、引用提交、竞争补答、同 Run 完成和历史回读。 |
| investigation-material-scoping | 5 / 10 | InvestigationMaterialTest、KnowledgeImportTest、KnowledgeSearchTest、AgentReportToolTest 覆盖附件/发布/版本/召回/直接原文/数据性质；第六组实际上传发布与第8组真实来源、报告回读一致。 |
| collaborative-workbench-experience | 7 / 9 | 前端135项、类型检查/构建、第六组1366×768、1920×1080、390×844截图与35项浏览器证据，第8组双账号真实操作覆盖登录/空间/讨论/结果/历史/来源/报告/取消/刷新；原型模拟开关未进入产品。 |

原型差异继续保持已记录的有意取舍：窄屏近期调查可折叠而非不可达；真实候选、Guard审核和完整限制使页面长于原型有限样例；资料使用统一抽屉，报告在主区按需打开以保留原打印排版。上述视觉/业务取舍集中留用户复核，不将截图当作服务端授权证明。

### 最终自动验收汇总

| 检查 | 最终结果 |
|---|---|
| `mvn -q package` | 73个测试类，515项：501通过、0失败、0错误、14跳过；JAR生成成功 |
| `npm --prefix frontend run test -- --run` | 12个文件，135/135通过 |
| `npm --prefix frontend run build` | vue-tsc和Vite均退出0 |
| `python scripts/verify_cli.py` | help、A–D、非法参数、缺密钥不降级、私有配置读取全部通过 |
| 第7组定向 | 认领3/3；迁移/恢复/legacy/旧内部恢复6/6 |
| 第8组真实验收 | 双账号真模宿主1/1；真实SMTP 1/1，6次SMTP服务接受 |
| 结构检查 | OpenSpec strict valid；diff --check退出0 |

日志和脱敏证据主要位于 `outputs/group7-*.log`、`outputs/group8-*.log`、`outputs/validation/workbench-group8/` 及第六组既有目录。历史失败/修复均保留在前述各组记录，不把定向、全量和真实验收相加冒充同一套件。

### 通俗中文运行链路

1. 用户先注册或登录。`AccountController` 和 `AccountService` 校验邮箱口令、轮换会话，Spring Security 把 DTSESSION 存入 PG；浏览器写请求还要带当前 CSRF。前端不保存口令或会话令牌。
2. 用户选择个人或团队空间。`HttpAccess` 从已登录账号和 `X-Workspace-ID` 找到成员关系，`WorkspaceScopeResolver` 生成 `AccessScope`：actor 是实际操作人，workspace 是授权空间，owner/project 仍是底层稳定存储键。客户端声明的角色或owner/project不被采信。
3. 团队成员可以发表讨论，但只有“引用到调查补充并正式提交”才把讨论快照加入输入。`GeneralAgentService.submit` 沿会话行锁依次检查写权、幂等键、期望调查版本和活动Run，再保存真实作者、引用讨论及附件版本；普通补充创建新Run，补答使用原runId/pendingQuestionId/questionVersion恢复原Run。另一成员竞争时只有一个事务获胜，失败方保留草稿且不自动排队。
4. worker从持久Run恢复scope、execution actor和执行段快照，不借用其他请求身份。模型只提出动作，`CapabilityGateway` 执行只读工具并校验当前权限，`EvidenceManager`/知识服务登记实际取得的来源，唯一 `TaskOrchestrator` 继续原主循环。Guard v2做整体审核和至多一次定向修正；取消、撤权、迟到结果不被采纳。
5. 最终结果、事件、引用、作者和输入快照由PG持久化。其他成员通过会话/Run回读和有界SSE看到同一状态；来源详情和报告只读持久记录，不再调用模型。个人、团队和未认领旧资料彼此隔离，旧资料只能由维护命令显式认领。

以本次第2轮为例：李工讨论“只有手册”没有运行模型；他引用并提交后才创建Run。模型追问排查对象，陈工补答时恢复同一Run并成为新执行段actor，原发起人和讨论快照仍在。最终报告能看到真实Run ID、手册版本/章节和SIMULATED限制，而不是把团队讨论当成现场事实。

### 旧归属维护链路

`LegacyOwnershipClaimTool` 默认只读，调用 `LegacyOwnershipClaimService.dryRun` 生成关联表、活动Run/租约、未知作者和文件校验清单。维护者明确 `--execute` 后，`claim` 在单事务内复查目标、创建专用TEAM和ADMIN、绑定旧conversation并写V24审计；文件或关联失败整体回滚。它不自动恢复无actor旧Run，不修改历史作者，也没有操作当前产品库。

### 未验证项、影响与下一动作

- QQ SMTP已接受6封邮件，但是否进入收件箱由用户后续人工检查；不使用IMAP，不影响本次按新口径勾选。若未收到，先查垃圾邮件和QQ投递日志，再用产品显式重发并轮换凭证。
- 未执行实际线上切换、旧资料认领或产品恢复。需要操作时先按 `migration-runbook.md` 停写、备份、dry-run并由维护者授权执行；本次没有操作8080/8081/54329。
- HTTPS演练使用自签测试证书；生产受信任证书/反向代理配置需在部署环境核验。embedding为deterministic，资料和现场均为SIMULATED/协议夹具，未验证真实embedding质量或真实现场设备。
- 原生打印入口已验，未实际生成PDF文件；PDF/OCR附件仍未实现。真实SMTP外部故障未故意注入，超时/失败由协议替身覆盖。
- 本change不同步主specs、不归档，不替其他change补勾任务，不提交或推送Git。后续可在 verify-change 复核后，另获授权决定同步/归档和实际部署。

8.4 的证据汇总、全场景核对和运行链路说明已完成；技术未通过项无隐藏遗留，以上均为明确的环境或后续人工边界。


## 2026-11-22 SMTP 示例配置安全整改

最终复核发现 `.env.example` 曾包含非占位 SMTP 账号和授权码，且 RealSmtpAcceptanceTest 会将示例文件作为实际配置来源。用户已手动将相关配置转移到被忽略的 `.env`；代理未读取或显示该文件内容。当前 `.env.example` 的 SMTP_USERNAME/PASSWORD/FROM 均为空占位，PUBLIC_URL 保持待配置，注释明确真实秘密只放 `.env`。

`RealSmtpAcceptanceTest` 的配置导入及 Properties 回退已删除 `.env.example`，现在只读取 `.env` 和批准的 `devicetruth.local.properties`。全文件检索确认不再引用示例配置或原示例账号/授权码。`git status` 未列出 `.env`，没有将秘密加入当前待提交工作。

验证命令：`mvn -q '-Dtest=AuthMailTest,RealSmtpAcceptanceTest' test`，使用隔离随机 PG。结果合计5项：AuthMailTest 4项通过、失败0、错误0、跳过0；RealSmtpAcceptanceTest 默认未开启真实发送，跳过1。此次未重新发送外部邮件，之前6次SMTP服务接受证据保留。OpenSpec strict validate 和 diff --check 通过；末尾空行问题已修正。

工作区“示例含秘密及测试读取示例”的问题已整改；但旧 Git 历史中的暴露不能靠删除解决，仍需用户在 QQ 邮箱侧轮换授权码，本记录不将轮换标为已完成。verify-change 另指出的 dry-run 漏计 `dt_run_skill_reference` 和实际 PDF 输出未验证仍分别保留，不因本次整改视为已解决。


## 2026-11-22 旧归属 Skill reference 漏计修复

verify-change 指出 `tableCounts` 只扫描自身带 owner/project 的表，漏掉只通过 run_id 关联的 `dt_run_skill_reference`。现已在 `LegacyOwnershipClaimService.tableCounts` 中追加 `dt_run_skill_reference.run_id → dt_run_skill.run_id` 联接统计，沿用参数化 owner/project 过滤，不改变认领写入、权限或历史数据。

`LegacyOwnershipClaimTest` 的旧数据夹具现在实际包含一条 Skill 安装、Run Skill 和 `rules.md` reference。测试同时断言 dry-run 的 `dt_run_skill=1`、`dt_run_skill_reference=1`，以及 V24 审计 JSON 中 `dt_run_skill_reference=1`；原有文件、Run、作者、回滚和 WAITING 接续断言继续保留。

实际命令：`mvn -q '-Dtest=LegacyOwnershipClaimTest,SkillRuntimeIntegrationTest,ScopedAgentRuntimeTest' test`，使用 127.0.0.1:54339 的真实隔离 PG16.15 和随机库。最终12项：通过12、失败0、错误0、跳过0，其中 LegacyOwnershipClaimTest 3项、SkillRuntimeIntegrationTest 3项、ScopedAgentRuntimeTest 6项。OpenSpec strict validate 和 diff --check 通过。维护手册及 V24 数据字典已同步说明该派生关联统计。

verify-change 的“dry-run 漏计 Skill reference”问题已修复；未重新执行全量515项，未操作产品数据库。SMTP历史凭证轮换和实际PDF输出验证仍是独立待办，不随本项标记完成。
