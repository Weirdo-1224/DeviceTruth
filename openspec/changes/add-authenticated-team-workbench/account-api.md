# 账号接口与管理端接入

## 浏览器先取 CSRF，再注册或登录

1. `GET /api/auth/csrf`，保留同源 Cookie，读取返回的 `headerName` 和 `token`。
2. `POST /api/auth/register` 发送 `{email,password,displayName}`，或 `POST /api/auth/login` 发送 `{email,password}`，在上述 header 中携带 token。
3. 成功返回 `{userId,email,displayName}` 并建立 JDBC Session；重新获取 CSRF token 后执行后续写操作。刷新使用 `GET /api/auth/me`。
4. `POST /api/auth/logout` 携带当前 CSRF，撤销当前会话；再次登录前重新获取 CSRF。密码重置成功会撤销该账号全部既有 JDBC 会话。

账号 UUID 是认证 principal，不是资源 owner；注册同时创建空个人空间及 ADMIN 成员，资源存储键为新 workspace UUID 派生的稳定键，不绑定旧数据。团队 ADMIN 不产生任何平台管理权限。此服务不含管理端用户列表、平台角色、停用或管理开户接口。

邮箱 strip 后以 Locale.ROOT 小写存储，最多 254 字符，需具有本接口支持的邮箱格式；显示名称 strip 后 1–100 字符；口令不 trim，12–128 个 Java 字符。口令使用带 `{pbkdf2}` 标识的 Spring Security PBKDF2 编码，不返回编码或口令。重复邮箱返回 409 `EMAIL_ALREADY_REGISTERED`，账号输入不合法 400 `INVALID_ACCOUNT_INPUT`，有效格式下错误邮箱/口令统一 401 `INVALID_CREDENTIALS`。

## Cookie 和启用范围

`DTSESSION`：HttpOnly、SameSite=Lax、Path=/，默认 Secure=true。仅本机 HTTP 隔离调试显式设置 `devicetruth.auth.cookie-secure=false`；正式 HTTPS 保持默认。空闲时限 8 小时；Session 表由 V19/Flyway 管理，不自动初始化表。登录/注册轮换已有 Session ID，清除旧 CSRF；退出使旧 Cookie 无法恢复身份。浏览器不把会话/口令存 localStorage。

仅 product 显式启用 Web Security/JDBC Session。两个启动入口排除默认安全、用户和会话自动配置，防止 legacy/CLI 被默认登录页及自动生成用户影响；product 自行安装安全链，健康探针保持公开。按 Spring Security 6 的自定义认证要求显式保存 SecurityContext，并清除登录前 CSRF，参见 [认证持久化](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html) 与 [CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)。

## 当前可联调范围

第 3 组已把调查/Run/资产/文件/知识接口接到真实空间授权；匿名 401，成员读写按角色和资源实际归属裁决。`/actuator/**` 和未定义的 API 仍拒绝，不使用固定身份回退。详细规则与恢复/SSE 链路见 `authorization-runtime.md`。前端认证/协作接线尚在第 6 组，未部署或重启现有 Docker 产品容器。

SMTP 适配和认证限流已接通；真实邮箱收件仍待 8.3 验收。实际产品联调仍须真模型；本次 HTTP 测试显式关闭 worker、使用 scripted/deterministic 进行认证协议隔离，不计模型或 embedding 验收。

## 密码恢复：一次性限时凭证

- `POST /api/auth/password-reset-requests`：`{email}`，需要先取匿名 CSRF。邮件适配器可用时，所有合法邮箱统一返回 `{status:"REQUEST_ACCEPTED",message:"如果该邮箱已注册，将尝试发送重置邮件。"}`，不返回账号是否存在、凭证或投递成功承诺。
- `POST /api/auth/password-resets`：`{token,password,confirmPassword}`，同样校验 CSRF。成功返回 `{passwordReset:true}`，不会自动登录，用户须以新密码重新登录。
- 密码沿用 12–128 字符、不 trim；确认不一致为 400 `PASSWORD_CONFIRMATION_MISMATCH`；错误、过期或已消费凭证统一 400 `INVALID_OR_EXPIRED_RESET_TOKEN`。响应不回显输入。

服务器生成 32 字节 SecureRandom 凭证，URL-safe Base64 无 padding（43 字符），V21 只保存 SHA-256 验证摘要。默认有效期 30 分钟，可用 `devicetruth.auth.reset-token-lifetime`（Duration，例如 `30m`）配置正时长。重新申请在账号锁内替换旧凭证；消费在同一账号锁下校验有效期/未使用、更新口令、消费账号全部重置凭证并删除 principal 对应的所有会话及属性，任一步失败回滚。不同实例也由 PG 锁裁决，不能同时成功消费同一凭证。

`ResetMailSender` 仅发送时获得原始凭证。`SmtpResetMailSender` 使用 Spring Mail，同步发送；SMTP host/from/public-url 任一缺失时对所有邮箱统一返回 503 `MAIL_UNAVAILABLE`，不假装已发送。发送成功记 ACCEPTED_BY_PROVIDER；失败记 FAILED 并使凭证不可消费，外部仍统一申请应答。PENDING/ACCEPTED_BY_PROVIDER/FAILED 是技术投递状态，不等于用户收件。

恢复路径：凭证过期/失败后重新申请；成功重置后重新登录。不要在普通 API、日志、资料或模型输入中输出完整令牌/新密码。旧 SSE 及在途 worker 授权收口仍由 3.5 接通，不将删除会话表视为持续流已完成验收。

## SMTP 配置、故障与显式重发

`.env.example` 列出 `DEVICETRUTH_SMTP_HOST/PORT/USERNAME/PASSWORD/FROM` 与 `DEVICETRUTH_PUBLIC_URL`；Compose 已声明向 backend 透传这些环境变量，不需把秘密写入 Java 或前端。非 Compose 启动直接注入进程环境；不会自动读取 shell 的 `.env` 文件。public URL 必须 HTTPS、无 userinfo/query/fragment，邮件链接使用 `#reset-token=`，不把凭证放到服务器访问日志的查询串。前端消费该 fragment 在 6.1 接通。

生产适配固定要求 STARTTLS，协议 debug=false。连接/读取/写入默认各 5000ms，环境变量 `DEVICETRUTH_SMTP_CONNECT_TIMEOUT_MS/READ_TIMEOUT_MS/WRITE_TIMEOUT_MS` 可调 1–30000ms。按 [Boot 3.5 邮件配置](https://docs.spring.io/spring-boot/3.5/reference/io/email.html) 显式设置三项，避免默认无限等待。此配置使用端口 587 的 STARTTLS 风格，不支持隐式 TLS 465 配置。

连接失败、超时或收件人拒绝时服务不打印原始 SMTP 异常，记录 FAILED、消费失败凭证；用户可明确再次提交同一申请接口重新发送，新凭证替换旧凭证。没有后台自动重试或令牌明文查询接口。SMTP 接受后仍可能退信，不能据 ACCEPTED_BY_PROVIDER 显示“已到达收件箱”。邀请重发与其限流在 3.2 使用同一原则接通。

## 有界认证限流

注册、登录、申请恢复分别同时按服务器看到的 remoteAddress 和规范化邮箱计数；重置消费按来源计数，不存令牌作为 key。不信任客户端 `X-Forwarded-For`。每类默认 15 分钟最多 20 次（成功/失败均计），最多 10000 个活跃 key，窗口到期清理；容量满时拒绝新 key，不驱逐尚在限流的来源。超限响应 429 `AUTH_RATE_LIMITED`，发生在编码口令、创建会话或发邮件前。

可通过 `devicetruth.auth.rate-limit`、`devicetruth.auth.rate-capacity`、`devicetruth.auth.rate-window` 配置。当前是单实例有界计数，重启会清空；多实例不能当作共享全局限额，部署侧需统一入口限流。反向代理环境应结合可信代理配置确认 remoteAddress，否则会把同一代理下用户合并计数。限流不等于管理端资源配额，不另建计费平台。
