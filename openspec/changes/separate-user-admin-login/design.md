# Design

## Context

动机和范围见 [proposal.md](proposal.md)。本文件记录选定方案；本变更已进入实施，实际完成情况、运行切换及用户确认见 acceptance.md。

已检查：`main.ts` 只按 `/admin/*` 加载 `AdminShell`；`AuthenticatedWorkbench.vue` 自带业务登录/注册/恢复/邀请流程；`AdminShell.restore/login/logout` 调用同一 `/api/auth/*`。`ProductAuthConfiguration` 使用一个 DTSESSION、Spring Session JDBC 和 CSRF 仓库，默认空闲超时 28800 秒；`AccountController` 登录后轮换 Session ID。`AdminGateFilter` 当前要求所有 `/api/admin/*` 具有 ACTIVE 账号及平台角色，没有管理域标记。密码验证使用现有 PBKDF2。

`AdminMaintenance/Main` 目前只能为现有普通账号授予平台角色和空间 grant；`AccountService.register` 总会创建个人空间，不能直接拿来开户专用管理员。当前主规格为空，旧活跃 change 的“统一 Session”是需要明确替换的历史合同，而非本次继续遵循的目标；旧测试和验收保留原记录。

现有运行版已在 `devicetruth_merged_20261122`，原库及恢复镜像保留；新库有一条与原库一致的 AUTHORIZED 观测兼容约束。认证升级不重建、repair 或标准化这份库。当前 xie 为 USER 业务身份且被授予平台角色/两项 grant；用户已指定专用管理员邮箱，具体部署标识保存在本地运维记录，不写成代码默认值。

## Goals / Non-Goals

**Goals:**
- 同一套可复用登录视觉与明确的两端意图，两个持久认证域，不增加 Vue Router 或另一套账号存储。
- 业务旧入口、邀请、密码恢复、调查、报告、附件、SSE 和授权不因管理域的新前提失效。
- 密码正确后的用途校验、服务端会话域绑定和实时权限校验构成完整链路，不把页面切换当成认证隔离。

**Non-Goals:**
- 不新增 JWT/SSO/MFA，不拆服务，不创建通用权限平台，不迁移业务 owner/project，不扩大管理员正文访问。
- 不新增邮件服务配置或独立管理员找回邮件系统；本轮管理员初始设置及紧急恢复采用可信本机交互。普通用户既有恢复流程保留。
- 不由代理在聊天中生成/接收管理员密码，不改变已有业务密码。

## Decisions

### D1. 统一视觉组件，保留两端容器与现有业务控制器

提取共享 `LoginEntry`（品牌、tab、字段、错误、按钮和注册/恢复展示），由薄登录控制器按 realm 选择认证客户端。`/login` 和 `/admin/login` 为正式登录地址；普通 `/` 和管理深链接在缺少本端身份时挂载同一入口并保存受限返回目标。`main.ts` 先识别登录地址，不能把 `/admin/login` 当用户管理页。

切换在共享入口内更新 realm 和地址，邮箱仅保留在该组件内存，立即清空密码及旧请求。认证成功后使用校验过的目标导航到对应现有应用；业务 `App.vue` 仍是唯一调查控制器。用户端注册/恢复/重置继续复用原 API，邀请和 reset fragment 只交给原有受控处理，不作为 returnTo 透传。管理端无注册入口。

视觉沿用当前浅背景、440px 登录卡、蓝色品牌、16px 正文、44px 控件；卡内采用两项分段切换和清晰选中/焦点状态，不添加营销插画或新的 UI 库。验证桌面和 390px 窄屏。

替代方案：仅两条链接切换会丢邮箱和请求状态；全靠邮箱自动路由让登录意图含糊，且可能先建立了错误用途会话，均不选。

### D2. 最小账号用途字段，而非第二张密码表

新增不可由公开表单写入的 `dt_account.account_type`，值 USER / ADMIN，既有行默认 USER；计划新增 V30（实施时核对下一空闲编号），V1–V29 不变。认证服务先按原规则验证邮箱、密码、ACTIVE，再判用途；错误密码统一 401 INVALID_CREDENTIALS，用途不匹配返回 409 LOGIN_REALM_MISMATCH 和合法目标 realm。不能据客户端 role、字段或入口认定用途。

ADMIN 管理登录还要求 PLATFORM_ADMIN，缺少角色返回 403 PLATFORM_ADMIN_REQUIRED。管理开户建立 ADMIN 账号但不创建个人空间；普通注册/管理端的普通用户开户仍按原合同创建 USER 与个人空间。管理角色/grant 与账号用途保持不同概念：用途为 ADMIN 不代表已经授予平台角色。

可信维护入口增加受控创建/设置 ADMIN 凭据的交互命令，复用原 PasswordEncoder、管理锁和审计。初始密码从隐藏终端输入读取，不使用命令参数、环境文件、日志或聊天交付。公开注册不能用 account_type 或 platformRole 参数扩大权限。普通密码恢复不能把 ADMIN 引导成业务登录或改变用途；管理员维护恢复不自动签发任一端会话。

### D3. 两种 Cookie、同一个持久仓库、必须有服务端 realm

沿用 Spring Session JDBC，使用按请求域分派的 `HttpSessionIdResolver`：

| 请求范围 | Session Cookie | Cookie Path | 认证域 |
| --- | --- | --- | --- |
| 现有业务 API 和 `/api/auth/*` | DTSESSION | `/` | USER |
| `/api/admin/*`（含管理认证） | DTADMINSESSION | `/api/admin` | ADMIN |

在成功登录时写入服务端 session 的认证域标记，再保存 SecurityContext；每次资源请求核对域与当前账号用途。旧 DTSESSION 无标记时仅允许 USER 业务身份，绝不恢复成 ADMIN。将一个有效 session ID 放入另一 Cookie 仍须因服务端域不符拒绝；拒绝不能顺手 invalidate 原域会话。

仅更换 Cookie 名不够：两个 Cookie 的 ID 都进入同一个 JDBC 会话仓库，缺少服务端域会让重命名绕过。无需新增第二套 session 表或 Redis。

### D4. 管理认证接口完整独立，写入和退出仍用原安全机制

保留 `/api/auth/csrf|login|me|logout` 作为 USER 接口；新增 `/api/admin/auth/csrf|login|me|logout`。管理登录和 CSRF 的精确路径在 Security chain / AdminGate 中允许匿名进入，其余管理路径仍须管理认证和平台权限，不使用宽泛 `/api/admin/auth/**` 放行。

CSRF 仍使用当前 session 仓库，所以两端令牌天然跟随不同 session；管理前端不再为写请求获取用户端令牌或恢复用户端 `/me`。登录轮换本域会话，退出只撤销本域；停用和密码重置继续按 principal 撤销该账号所有 session。USER 资源请求也核对用途/域，ADMIN Cookie 不能调用业务资源。

保留当前限流、HttpOnly、SameSite、生产 Secure，以及实时 status/statusVersion、worker 和 SSE 撤权。Cookie Path 只用于浏览器投递收敛，不作为安全边界。两个客户端保持独立 generation、CSRF 缓存和错误清理；一端迟到 401/403 不清除另一端身份。

### D5. 返回路由采用小型白名单，而非开放重定向

共用 `loginRouting` 纯函数解析 realm、初始路径和 returnTo；只接受 `/` 及实际产品路由、`/admin/users|runs|knowledge|skills|system`。拒绝外域、`//`、反斜杠、控制字符、多层编码绕过、其他端、认证动作、未知路径和跳回登录页的循环。只规范化一次，不盲目多次 decode。没有合法目标时 USER 回 `/`，ADMIN 回 `/admin/users`。

不把密码、邮箱、Cookie 或令牌写入 URL。切换入口保留邮箱只发生在共享组件内存；不能用 query、localStorage、sessionStorage 或 window.name 搬运密码。

### D6. 专用管理员按接管顺序部署，不先撤唯一管理员

预检新邮箱是否存在；若已存在业务账号，不自动接管或转换用途。先备份并应用加性字段，用新版可信维护程序创建专用 ADMIN、设置用户控制的密码、授予平台角色及“历史演示数据”的 KNOWLEDGE_MANAGE / SKILL_MANAGE。角色和 grant 审计独立保留，不迁移旧 actor 或作者。

发布两端隔离的候选版，旧共享 Session 不能进入管理端，新管理员必须重新在 ADMIN 域登录。在新登录及目标空间读取/管理能力验证完成之前，保留 xie 原角色/grant 和可信维护恢复路径；验证通过后撤销 xie 平台角色及两项 grant，保留 USER 账号、密码、团队 ADMIN 和原全部调查数据。最后有效管理员保护须以 ACTIVE ADMIN 且仍有平台角色为准。

## Risks / Trade-offs

- [新管理员密码未设置或用户尚未完成登录] → 这是账号切换依赖；可完成实现和隔离验收，但不得撤销原角色或宣称账号接管完成。执行时由用户通过隐藏本机交互设置密码。
- [新 UI 部署但仍调用旧共享接口] → 前后端同批发布；API 合同测试禁止管理客户端调用 `/api/auth/*`；不能以界面效果代替会话验证。
- [通过 Cookie 重命名或旧 Session 绕过] → 服务端 realm 及账号用途都验证，加入真实 PG 的双向攻击回归。
- [独立域误伤普通业务/邀请/重置] → 保留原入口和 USER 默认值，回归现有 AccountAuthentication、AuthenticatedResource、认证前端合同及邀请/重置路径。
- [两端在同浏览器相互影响] → Cookie/CSRF/generation 分开，退出不清理另一端本地状态，浏览器实际用两个测试身份验证。
- [跨端旧登录请求已经提交到服务器后才切换] → 清除前端晚到状态不等于撤销已发生的认证；切换期间禁用或中止提交，独立域确保不会把旧端身份当新端身份，验收记录实际会话结果。

## Migration Plan

1. 在隔离 PG 上验证空库及现有 V29 加性升级、USER 默认值、旧业务 Session 兼容和两端认证/退出/攻击场景；协议替身与真模分别记载。
2. 构建后检查实际登录页，验证桌面/窄屏、切换、入口不匹配、合法深链接与恶意 returnTo；普通邀请/恢复不退化。
3. 备份现有库、文件、运行配置和旧镜像；核对实际迁移历史、现有 ACTIVE Run 与管理员。只应用加性迁移，不复制/清空运行库，不改变 AUTHORIZED 约束或向量档案。
4. 新账号准备、隐藏密码设置、角色和目标 grant 授予；失败保留现有服务及权限。先验证候选服务恢复路由和独立会话，再接管、撤销 xie 管理授权。
5. 发布后保存实际计数、跳过/失败历史、角色/grant/审计、容器健康及浏览器证据。默认使用已配置真实推理器，不静默切换 scripted；保留现有 deterministic embedding 与其数据性质说明。
6. 如需恢复旧版，先撤销新建 ADMIN 的有效会话并停止候选服务，保留新增字段/账号/审计和所有业务写入；仅在确认旧服务不会接受 ADMIN 进入普通业务、不会恢复 xie 管理权时切回。不能盲目换回忽略用途/域的旧镜像；无法满足时采用正向修复，保留可信维护入口。
