# 实施与验收记录

## 2026-11-22：已实施并切换正式服务

用户要求统一登录界面提供用户端/管理端切换，同时分开业务与管理身份、会话和退出；指定专用管理员邮箱，明确授权通过本机可见终端隐藏输入初始密码。用户已亲自设置新密码并登录新管理员；随后确认原 xie 账号已登录且能看到原调查。

### 当前交付

- 正式前端 `http://127.0.0.1:8081/`，用户登录 `/login`，管理登录 `/admin/login`，后端 8080。
- 专用 ADMIN 已创建并验证，具有 PLATFORM_ADMIN 和“历史演示数据”的 KNOWLEDGE_MANAGE / SKILL_MANAGE；实际邮箱及账号/空间 ID 保留在 Git 忽略的本地运维记录。
- xie 保持 USER、ACTIVE、原密码和个人空间/历史演示数据团队的 ADMIN 成员资格；平台角色及两项管理 grant 已撤销。没有修改原作者、调查、引用或其他账号权限。
- V30 加性迁移新增 account_type，V1–V29 未修改。运行库原 AUTHORIZED 观测兼容约束和 deterministic embedding 档案保留。
- 升级前后业务清单相同：2 个原账号、156 会话、110 Run、349 引用、4 条成员关系、18 个等待补答；随后仅新增 1 个独立管理账号，不创建个人空间或成员关系。
- 已备份库、文件、配置和原运行容器文件系统；旧镜像标签映射不可用时，导出文件系统并以明确入口导入恢复镜像，不携带运行环境密钥。恢复不得盲目回到忽略账号用途/会话域的旧程序。

### 关键实现和实际链路

`LoginEntry.vue` 统一品牌、两端选择、表单、错误及注册/恢复入口；切换仅在内存保留邮箱并清空密码，管理端没有注册。`loginRouting.ts` 白名单控制同端返回路径。两个现有应用容器继续负责业务与管理页面，管理客户端改用 `/api/admin/auth/*`，业务端保留原接口。

`AccountService` 复用 PBKDF2 和账号表，在验证密码与 ACTIVE 后核对 USER/ADMIN 用途；管理登录再要求平台角色。`RealmSessionIdResolver` 选择 DTSESSION / DTADMINSESSION；`LoginRealm` 的服务端 Session 标记阻止 Cookie 更名和跨端复用。匿名 CSRF 会话也绑定域；旧无标记 Session 仅兼容用户端。`AdminGateFilter` 每次核对域、用途和状态，管理接口进一步检查平台角色及目标空间 grant。两端退出分别失效，账号停用和密码重置仍按账号撤销会话。

`AdminMaintenanceMain` 通过隐藏交互创建/恢复 ADMIN 密码；`AdminMaintenance` 完成账号、角色、grant 和审计。先验证新管理员正式登录与两项管理范围，再撤销 xie 管理权限。实际 7 条维护审计均 SUCCESS，包括新建、角色、两项授予及旧角色/两项撤销。

### 实际命令和验证结果

结果分别报告，不叠加为一次全量运行。后端均使用独立真实 PostgreSQL/pgvector、真实 HTTP/Cookie/CSRF；其模型为明确的离线协议测试。浏览器候选服务使用已配置真实推理器。

| 验证 | 命令/证据 | 总数/通过/失败/错误/跳过 |
| --- | --- | --- |
| 首轮认证 | `mvn -o -q -Ddevicetruth.build-directory=outputs/login-separation-20261122/maven -Dtest=RealmAuthenticationTest,AccountAuthenticationTest test`；backend-first.log | 10/9/0/1/0 |
| 受影响回归 | 同目录，`-Dtest=RealmAuthenticationTest,AccountAuthenticationTest,PlatformAdminTest,AdminKnowledgeTest,AdminQuotaLedgerTest,AuthMailTest,AuthenticatedResourceTest,ResourceAuthorizationTest`；backend-regression.log | 53/52/1/0/0 |
| 修复后定向 | `mvn -o -q -Ddevicetruth.build-directory=outputs/login-separation-20261122/final-maven -Dtest=RealmAuthenticationTest,PlatformAdminTest#realHttpPermissionProjectionPagingAndControlledCreation test`；backend-final.log | 6/6/0/0/0 |
| 补充边界回归 | `mvn -o -q -Ddevicetruth.build-directory=outputs/login-separation-20261122/edge-maven -Dtest=RealmAuthenticationTest test`；backend-edges.log | 5/5/0/0/0 |
| 最终前端 | `npm --prefix frontend run test -- --run`；frontend-final-test.log | 188/188/0/0/0 |
| 编译/打包 | `mvn -o -q -DskipTests package`；`npm --prefix frontend run build` | 成功；打包不重复运行测试 |
| 实际部署 | `docker compose --profile product build backend frontend`；`docker compose --profile product up -d --no-deps backend frontend` | 正式服务已响应，候选服务在正式验证前保留 |

后端覆盖双账号双 Cookie、双向 Cookie ID 重放、匿名 CSRF 跨域重放、旧业务 Session 兼容、错端/错误密码、跨端 CSRF、双向独立退出、角色撤销、无个人空间的 ADMIN 新建、重复邮箱拒绝，以及 V29 升级成员/Run/引用原字段不变。原有状态代次、密码恢复、资源范围及非 product 健康/CLI 入口回归保留。

浏览器已实际验证登录选项切换、保留邮箱/清空密码、两种用途不匹配提示、深链接登录回 Skill 页面、两账号同浏览器独立身份、管理退出后业务仍可刷新、键盘切换和桌面渲染。视口工具未改变原生浏览器实际宽度，未据此冒称手机验证；改用独立预览的 390px iframe 验收页，实际测得 clientWidth=scrollWidth=390，并检查渲染无横向溢出。

真模通过新业务认证界面提交纯合成通用连通性问题，Run `dec2be3e-9c62-4afe-bce2-179d4688e668` COMPLETED，DeepSeek `deepseek-flash` 1 次 VALID_PROPOSAL（输入 4030、输出 89 token）。没有发送原运行库业务内容；不代表真实现场质量验收。Embedding 保留原 deterministic，不混称真实 embedding。

正式入口已实际显示新管理员身份，成功读取“历史演示数据”的知识列表与 Skill 安装，管理按钮可用；用户亲自登录确认，原 xie 仍能看到原调查。最后正式账号列表显示新账号 PLATFORM_ADMIN、xie NONE，均 ACTIVE。

### 失败和修复历史

- 首轮新建管理员的审计 state 因白名单缺少 accountType 而回滚；补枚举状态白名单后复验通过，未允许密码或任意审计字段。
- 受影响管理测试仍期待管理开户后 `/api/auth/me` 变成管理员；按已批准的双端合同改为同时验证 `/api/admin/auth/me` 管理身份和 `/api/auth/me` 原业务身份。旧 53 项运行保留失败，不改写成全量通过；该测试及新增边界已另行通过。
- 原生浏览器 viewport 设置未实际生效，改用真实 390px frame 检查；空间 select 的精确 label 定位失败，按实际可访问控件选择，保留会话中的工具错误。
- 旧运行镜像 tag 不可用，未停止旧服务，先完成文件系统恢复备份后再继续；新管理员设置与两项 grant 完成后才发布和撤权。

### 用户确认、停止核验及边界

- 用户明确同意打开可见终端隐藏输入初始密码；亲自完成新管理员登录并回复“成了”。代理没有取得、记录或代输新密码。
- 用户确认 xie 已登录、能看到原调查；业务确认与自动化测试分别记载。
- 用户随后要求停止页面核验：立即停止，不再追加浏览器操作、截图或测试；已运行的正式服务与用户正在使用的页面保留。
- 没有执行全仓库全量重跑、真实邮件或现场诊断质量验收；旧库恢复镜像存在但本次未执行数据恢复演练。没有同步归档或自动提交/推送。
- 现有 design.css/pro.css/workbench.css 有并行产生的样式改动，本次不覆盖或回退；实际前端构建和页面检查使用当时工作区状态。

本地备份、日志、管理员标识及候选环境信息位于 `outputs/login-separation-20261122/`，不提交秘密、配置备份或生成报告。
