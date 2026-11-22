## ADDED Requirements

### Requirement: 平台权限独立于空间角色

系统 SHALL 复用统一账号 Session principal，按当前 ACTIVE 状态及独立 PLATFORM_ADMIN 授权保护所有 /api/admin/*。空间 ADMIN、客户端角色、固定身份配置 MUST NOT 授予平台权限。

#### Scenario: 团队管理员不能访问平台后台
- **WHEN** 只有团队 ADMIN 的已登录用户调用任一管理接口
- **THEN** 服务端返回 403 PLATFORM_ADMIN_REQUIRED，管理数据和操作均不开放

#### Scenario: 匿名和已撤销平台授权
- **WHEN** 请求没有有效会话，或已有会话的账号刚被撤销平台角色
- **THEN** 前者返回 401，后者下一次管理请求返回 403，不等待会话自然过期

### Requirement: 目标空间管理授权显式裁决

系统 SHALL 按 permission-matrix.md 校验目标 workspace grant；KNOWLEDGE_MANAGE/SKILL_MANAGE 分别包含对应读取能力，读取能力 MUST NOT 授予写入。资源真实空间 SHALL 与显式目标一致，管理操作 MUST 保留真实 actor 和稳定空间存储键。

#### Scenario: 平台管理员没有目标授权
- **WHEN** 平台管理员查询或修改未获 grant 的空间知识/Skill
- **THEN** 返回与资源不存在一致的 404 RESOURCE_NOT_FOUND，不泄露空间资源

#### Scenario: 资源和请求空间冲突
- **WHEN** 请求携带其他空间的资源 ID、冲突 Header 或伪造 owner/project/actor 字段
- **THEN** 真实空间冲突返回 404，未定义扩权字段返回 400，资源归属和操作者不改变

### Requirement: 平台授权不放宽用户内容读取

平台运营接口 SHALL 仅输出 admin-api.md 的受控字段；平台授权或管理 grant MUST NOT 自动授予产品调查/报告/附件/来源正文读取或团队成员资格。

#### Scenario: 管理员拿运营 Run ID 请求用户详情
- **WHEN** 无该空间成员资格的平台管理员调用产品 Run/引用/报告接口
- **THEN** 原产品授权继续拒绝，不能以平台角色跳过隔离

### Requirement: 初始化和最后有效平台管理员保护

系统 SHALL 通过可信维护入口为现有 ACTIVE 账号授予平台角色/范围，记录审计；公开注册及管理开户 MUST NOT 自授平台角色。平台撤权与停用 SHALL 使用共同数据库裁决，保留至少一位有效平台管理员。

#### Scenario: 两管理员并发失效
- **WHEN** 两位有效平台管理员被并发撤权或停用，两个动作若都成功将无人管理
- **THEN** 最多一个成功，另一个返回 409 LAST_PLATFORM_ADMIN

#### Scenario: 维护初始化不创建另一套账号
- **WHEN** 可信维护入口 bootstrap 已注册账号
- **THEN** 仅写平台授权及 MAINTENANCE 审计，不创建密码/成员映射或自动认领资料

### Requirement: 后台能力发现与就绪状态

系统 SHALL 提供 /api/admin/access 和仅列授权空间的 /api/admin/workspaces，按当前授权输出能力及 feature readiness。未接通写能力 SHALL 在鉴权后返回 503 INTEGRATION_NOT_READY。

#### Scenario: 状态按钮依赖尚未接通
- **WHEN** 停用的持续授权适配器尚未完成，而管理员调用状态写接口
- **THEN** access 显示 PENDING_INTEGRATION，写请求拒绝且不改状态，不回假成功
