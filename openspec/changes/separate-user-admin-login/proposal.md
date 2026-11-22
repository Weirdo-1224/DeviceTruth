# Proposal

## Why

现有用户端与管理端使用同一 DTSESSION，业务账号获平台角色后普通登录会话即带有管理能力，身份切换和退出边界不清晰。用户已决定采用可切换的统一登录页、专用管理账号和独立管理会话，保留现有业务账号与调查数据。

## What Changes

- 复用统一视觉登录组件，提供“用户端 / 管理端”切换；`/login`、`/admin/login` 默认选中对应端，管理端不提供注册。
- 成功后进入对应首页或经过同源、同端白名单校验的原目标页；切换保留内存中的邮箱并清空密码，不自动替用户跨端登录。
- **BREAKING**：管理端不再复用 `/api/auth/login` 和业务 DTSESSION，新增管理认证入口、独立 Cookie 与服务端会话域绑定；两端登录、CSRF、过期和退出独立。
- **BREAKING**：区分 USER / ADMIN 账号用途；统一账号存储和 PBKDF2 底座继续复用，管理账号只由可信维护入口创建，不自动创建个人空间或团队成员关系。跨端账号在验证凭据后被明确拒绝，不建立错误域会话。
- 按用户授权建立专用管理员并迁移“历史演示数据”的两项管理 grant；新管理员验证通过后再撤销 xie 的平台角色与 grant，保留 xie 密码、业务成员关系及全部历史数据。
- 复用当前状态代次、最后有效管理员保护、管理锁及审计；旧业务会话保留原使用合同，但不能升级为管理会话。

## Capabilities

### New Capabilities

- `login-entry-experience`：两端切换、登录状态恢复、账号入口不匹配提示及受限返回路由。
- `realm-session-authentication`：账号用途、用户/管理会话隔离、CSRF 与服务端授权绑定、专用管理员安全切换。

### Modified Capabilities

无已发布主规格可修改：`openspec list --specs` 返回空。现有未归档 `add-authenticated-team-workbench/account-authentication` 和 `add-platform-admin-console/platform-admin-access` 仍是认证及授权底座来源；本变更明确替换其中“管理端复用用户 Session”和“任意普通账号可直接作为管理身份”的行为，保留其余约束，不改写历史验收。

## Impact

- 前端：`main.ts`、`AuthenticatedWorkbench.vue`、`admin/AdminShell.vue`、`api.ts`、`admin/client.ts`、相关登录样式和合同测试；复用现有 Vue 和设计 tokens，不增加路由库。
- 后端：`ProductAuthConfiguration`、`AccountController`、`AccountService`、`AdminGateFilter`、`AdminAuthorization`、`AdminMaintenance/Main`，新增会话域路由和管理认证适配；沿用 Spring Security / Session JDBC。
- 数据：仅新增当前 V29 之后的账号用途迁移；V1–V29 不改，原数据与业务身份不搬迁。运行库现有 AUTHORIZED 观测兼容约束保留，不因本次认证变更规范化或重建。
- 运维：本机受控设置管理员初始密码，不在聊天、代码、参数或日志中传递口令；配置缺失不伪称密码/邮件已交付。未验证新管理员前不得撤销现有唯一管理身份。
- 非目标：SSO、JWT、外部身份提供方、整库重建、重新向量化、邮件基础设施改造及新增全平台业务正文访问权。
