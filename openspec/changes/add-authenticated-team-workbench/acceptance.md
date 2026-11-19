# 规划与验收记录

日期：2026-11-19（Asia/Shanghai）。阶段：OpenSpec 提案完成，实施尚未开始。

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
