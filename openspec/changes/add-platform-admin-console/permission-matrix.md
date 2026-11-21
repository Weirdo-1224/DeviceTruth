# 平台权限矩阵

本文为本 change 的权限合同；所有许可都要求账号 ACTIVE。团队角色只用于产品空间授权，平台角色只用于后台。首期平台角色为 PLATFORM_ADMIN 或 NONE，角色/范围通过可信维护入口管理。

## 判定顺序

Cookie Session → 当前账号状态 → 平台角色 → 路由 capability → 目标空间 grant（需要时）→ 资源实际空间/版本/生命周期 → 操作及审计。写请求先满足现有 CSRF 规则；CSRF 拒绝可先于业务鉴权返回。前端显示不是授权依据。

## 角色矩阵

“无”覆盖无平台授权的个人空间 ADMIN、团队 ADMIN/COLLABORATOR/VIEWER。

| 操作 | 匿名 | 无平台角色 | 平台管理员，无目标范围 | 平台管理员，有对应范围 |
| --- | --- | --- | --- | --- |
| 平台能力 GET /api/admin/access | 401 | 403 | 是 | 是 |
| 平台用户列表 USER_READ | 401 | 403 | 是 | 是 |
| 受控开户 USER_CREATE | 401 | 403 | 是 | 是 |
| 停用/恢复 USER_STATUS | 401 | 403 | 是，最后平台管理员保护 | 同左 |
| 脱敏 Run RUN_READ | 401 | 403 | 是，全平台最小运营字段 | 同左 |
| 用量/配额 USAGE_READ、QUOTA_READ | 401 | 403 | 是，脱敏统计与配置 | 同左 |
| Skill 目录 SKILL_CATALOG_READ | 401 | 403 | 是，部署内容受控读取 | 同左 |
| 空间知识元数据 KNOWLEDGE_READ | 401 | 403 | 目标空间 404 | 是 |
| 空间知识导入/重试/启停 KNOWLEDGE_MANAGE | 401 | 403 | 目标空间 404 | 是 |
| 空间 Skill 安装查询 SKILL_READ | 401 | 403 | 目标空间 404 | 是 |
| 空间 Skill 开通/启停 SKILL_MANAGE | 401 | 403 | 目标空间 404 | 是 |
| 依赖/审计 SYSTEM_READ、AUDIT_READ | 401 | 403 | 是，受控字段 | 同左 |
| 私密问题/附件/答案/Prompt/工具参数 | 无 | 无 | 无默认许可 | 无默认许可 |
| 管理网页授平台角色/空间范围 | 无 | 无 | 首期无此接口 | 首期无此接口 |

KNOWLEDGE_MANAGE 包含 KNOWLEDGE_READ，SKILL_MANAGE 包含 SKILL_READ。读取 grant 不能写入；任何 workspace grant 本身也不能替代 PLATFORM_ADMIN。/api/admin/workspaces 仅列当前管理员有上述 grant 的空间及能力，不枚举所有私有空间。

## 身份与范围

- 后台全局 Run 查询是最小元数据运营投影；不能拿 Run ID 转调用户详情/报告/引用原文绕过成员授权。
- 目标 workspaceId 必须显式给出，资源 ID 从数据库确定真实空间，再与目标及 grant 匹配；无管理范围和不存在统一 404 RESOURCE_NOT_FOUND。
- 账号 ID 仅为 actor 或管理对象。owner/project 由服务器解析目标空间；不接收客户端 ownerId/projectId/actorUserId/platformRole 等扩权字段。
- 管理请求不依赖产品当前空间缺省；若发送 X-Workspace-ID，必须与显式目标相同；全局管理查询禁止携带该 Header，避免界面误把全局查询当空间查询。
- 管理授权不创建团队成员，也不放宽 /api/conversations、/api/runs、/api/files 等产品接口。
- 平台授权/空间 grant 撤销后，下一个请求和写事务提交前复核必须拒绝；不能仅等浏览器刷新角色。

## 初始化与维护

可信维护入口先核对现有账号 ACTIVE；bootstrap 只授首个平台角色，不创建认证账号、不接受密码/固定用户映射。后续平台角色、范围授予撤销均要求明确操作人标识、目标和 reasonCode，写 actorKind=MAINTENANCE 的审计。

采用数据库共享管理锁串行裁决平台授予/撤销和平台管理员账号停用，保证至少一位 ACTIVE 的 PLATFORM_ADMIN。两管理员并发互相停用/撤权最多允许一次成功。最后一位返回 409 LAST_PLATFORM_ADMIN；团队 LAST_WORKSPACE_ADMIN 约束继续独立存在。

维护入口的操作人标识来自可信运维上下文，不能由普通 HTTP 请求伪造。不给网页管理员任意 SQL/命令能力。

## 私密内容范围

首期知识详情只有章节索引/切片数等元数据，不提供用户知识正文/原文件下载。Skill 是受控部署材料，可按已登记 skillId/reference 名称读取，仍不返回服务器路径，也不执行其中指令。未来增加私密内容访问需独立规格，不能通过此矩阵的“管理员”一项默许。
