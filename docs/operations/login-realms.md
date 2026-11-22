# 用户端与管理端分别登录

日常调查使用业务账号，平台管理使用专用管理账号。两端登录页都有“用户端 / 管理端”切换：

- `/login` 默认进入用户端，登录成功进入工作台。
- `/admin/login` 默认进入管理端，登录成功进入管理首页；从知识库等深链接进入时返回原页面。
- 切换保留当前页面内存中的邮箱，清空密码；验证后发现账号用途不匹配时提示切换，不自动跨端登录。
- 用户端可注册、申请邮件重置；管理账号不开放自注册，初始密码与紧急恢复走本机可信维护入口。

## 同一浏览器可以同时登录两端

业务账号使用原 `DTSESSION`，管理账号使用 `DTADMINSESSION`。两端分别保存服务端 Session、CSRF 和认证域；退出一端不影响另一端。管理员用管理会话不能读取产品调查，普通业务 Cookie 即使更名也不能访问管理接口。

接口分为两组：

| 用途 | 用户端 | 管理端 |
| --- | --- | --- |
| CSRF | `/api/auth/csrf` | `/api/admin/auth/csrf` |
| 登录 | `/api/auth/login` | `/api/admin/auth/login` |
| 当前身份 | `/api/auth/me` | `/api/admin/auth/me` |
| 退出 | `/api/auth/logout` | `/api/admin/auth/logout` |

每次管理请求还会核验 ACTIVE、ADMIN 用途及当前 PLATFORM_ADMIN，知识/Skill 仍需具体空间 grant。`MANAGE` 包含对应 `READ`，不自动增加业务成员资格。

错误密码/未知账号为 401 INVALID_CREDENTIALS；正确密码但用途不匹配为 409 LOGIN_REALM_MISMATCH；管理账号无平台角色为 403 PLATFORM_ADMIN_REQUIRED。缺少管理会话为 401；空间范围不足为 404 RESOURCE_NOT_FOUND。

## 在可信终端创建专用管理员

以下命令依赖维护进程的 `DEVICETRUTH_MAINTENANCE_PG_URL/USERNAME/PASSWORD`，数据库凭据继续只从环境读取。执行前核对目标库、邮箱和空间 UUID，初始密码必须在真正的交互终端输入，非交互管道会返回 INTERACTIVE_CONSOLE_REQUIRED。

```powershell
java -Dloader.main=io.devicetruth.admin.application.AdminMaintenanceMain -cp target/devicetruth-core-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher create-admin ops-ref admin@example.com 平台管理员
```

终端会隐藏输入并要求确认密码（12–128 字符）；只输出新账号 ID 和成功状态。它不创建个人空间，不自动授予平台角色。

随后通过既有维护命令为输出的账号 ID 配置 `platform ... enable OPS_REQUEST`，再按需执行 `grant ... KNOWLEDGE_MANAGE enable PERMISSION_CHANGE` 和 `grant ... SKILL_MANAGE enable PERMISSION_CHANGE`。首次没有平台角色时可使用 `bootstrap`。给 USER 直接授予平台角色会返回 ADMIN_ACCOUNT_REQUIRED，不会悄悄把业务账号改成管理账号。

管理账号紧急恢复使用 `reset-admin-password ops-ref ACCOUNT_UUID`，同样隐藏输入并撤销该账号旧 Session。公开用户恢复邮件不会把 ADMIN 引导到业务登录；不得在聊天、命令参数、脚本或日志里交付密码。

## 从旧共享登录方式切换

1. 备份并核对当前库，正向应用 V30 账号用途迁移；原业务账号保留 USER 和原密码、空间、作者及历史记录。V1–V29 不改。
2. 建立专用 ADMIN，授予平台角色和所需空间权限。新管理员必须在新管理登录页重新登录；旧共享会话不会被当作管理会话。
3. 验证新管理员的登录及权限后，再撤销旧业务账号的平台角色和管理 grant。团队 ADMIN 和全部业务资料保持不变。
4. 保留至少一位 ACTIVE ADMIN 平台管理员。新密码未设置、邮箱冲突、登录或授权验证失败时，不宣称接管完成，不先撤销现有角色。

账号停用/密码重置继续撤销该账号全部会话，另一账号的有效会话不受影响。旧用户会话无域标记时只兼容 USER 业务读取，不兼容管理端。

恢复旧程序前必须评估旧版忽略用途/域的风险；不能直接换回会把 ADMIN 当普通业务身份的旧镜像。保留新增账号、用途、审计和业务写入，必要时正向修复。
