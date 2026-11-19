# 团队空间与成员接口

## 创建团队后再邀请同事

先按 `account-api.md` 登录并取得当前 CSRF，再 `POST /api/workspaces` 发送 `{name:"维修团队"}`。服务器在同一事务创建空团队和创建人的 ADMIN 资格；保留个人空间，不复制任何旧资源或其他空间的安装授权。邀请加入已在 3.2 接通（见 `authorization-runtime.md`），没有通过 userId 任意新增成员的 HTTP 接口。

| 接口 | 输入 / 输出 |
|---|---|
| GET `/api/workspaces` | 当前账号可见空间数组：workspaceId、kind、name、role；不返回内部存储键 |
| POST `/api/workspaces` | `{name}` → 新团队 WorkspaceView |
| PATCH `/api/workspaces/{id}` | `{name}` → 改名后的 WorkspaceView；仅团队 ADMIN |
| GET `/api/workspaces/{id}/members` | 本空间成员数组：userId、displayName、email、role |
| PATCH `/api/workspaces/{id}/members/{userId}` | `{role:"ADMIN"或"COLLABORATOR"或"VIEWER"}` → `{updated:true}` |
| DELETE `/api/workspaces/{id}/members/{userId}` | → `{removed:true}`；保留账号、历史作者和资源 |

全部要求登录，写请求要求 CSRF。路径 UUID 确定目标空间；若提供 X-Workspace-ID 必须相同，否则 404。请求体只接受明确字段，不能通过 owner/project/actor/role 附加字段给自己授予创建权限。名称 strip 后 1–100 字符。

## 角色矩阵

| 动作 | ADMIN | COLLABORATOR | VIEWER |
|---|---|---|---|
| 看本空间成员 | 是 | 是 | 是 |
| 团队改名 / 改角色 / 移除 | 是 | 否 | 否 |
| 新建自己的团队 | 是 | 是 | 是（作为已登录用户） |
| 管理他空间 | 否 | 否 | 否 |

这里的角色只属于空间，不授予平台后台权限。个人空间不允许团队式改名/改角色/移除。调查写入与持续授权已在 3.3–3.5 接通，见 `authorization-runtime.md`；前端仍待第 6 组接入。

成员变更事务先检查成员资格，再锁 workspace 行，拿锁后重新检查管理员资格，然后检查目标角色及管理员数量，再更新/删除。两个管理员并发自行降权或移除自己也只能成功一次，第二个请求返回 409 `LAST_WORKSPACE_ADMIN`。此锁约定也适用于后续邀请接受；不能直接写成员表绕过服务约束。

匿名 401；跨空间/无成员资格 404；非管理员 403 `WORKSPACE_ADMIN_REQUIRED`；个人空间变更 403 `PERSONAL_WORKSPACE_IMMUTABLE`；目标成员不存在 404 `MEMBER_NOT_FOUND`；非法名称 400 `INVALID_WORKSPACE_NAME`。客户端角色是 JSON 枚举，非法值为 400；空间管理员不能赋予平台角色。

成员移除只撤销该空间资格，账号和其他空间仍可用，历史作者不删除。浏览器已有订阅及执行中任务的撤权路径已在 3.5 接通：成员移除使旧订阅停止输出，执行段 actor 失去写权使 Run 以 AUTHORIZATION_REVOKED 收口。
