# 旧归属认领与安全切换手册

## 边界

本手册的维护命令默认只读。只有维护者明确追加 `--execute` 才会创建专用认领团队。首个注册用户不会自动得到旧资源；历史消息、Run、事件、文件和执行段的空作者保持为空。

实际操作前必须停止新写入、尽量排空 worker，并备份匹配的数据库和文件目录。当前验收只在随机隔离 PG 和临时文件副本执行，未对用户运行库或现有产品服务执行切换、认领或恢复。

## 认领前盘点

- 数据库和文件备份必须来自同一停机时点，记录程序版本、Flyway 版本、文件根目录和存储键对。
- 盘点 QUEUED、RUNNING、WAITING_FOR_USER；保留多个历史未决 Run，不删除、不强制取消。
- RUNNING 逐项记录 `lease_owner`、`lease_expires_at` 和心跳。未过期租约仍归原 worker；崩溃恢复只处理符合原租约/CAS 规则的可回收记录。
- 旧 Run 无 execution actor 时，product 不自动恢复。需要系统维护恢复时沿用原内部 `recoverQueued` 入口；用户补答后才写入新的 execution actor，初始作者仍为空。
- 停写期间核对 dry-run 清单的所有表行数、活动 Run、未知作者和每个文件的存在性、字节数及 SHA-256。

## dry-run 与执行

先按仓库说明构建 JAR，并在当前维护进程中通过环境注入数据库连接；不要把口令写进命令、清单或日志。以下 PowerShell 示例仅展示参数：

```powershell
$env:SPRING_DATASOURCE_URL = '<由维护者提供的 JDBC URL>'
$env:SPRING_DATASOURCE_USERNAME = '<由维护者提供>'
$env:SPRING_DATASOURCE_PASSWORD = '<由维护者提供>'
java -cp target/devicetruth-core-0.1.0.jar io.devicetruth.app.LegacyOwnershipClaimTool `
  --owner-id=<旧owner> --project-id=<旧project> `
  --admin-user-id=<已存在账号UUID> --team-name='旧资料认领团队' `
  --files-root=<匹配备份的文件根目录> `
  --output=outputs/validation/legacy-claim/dry-run.json
```

不加 `--execute` 时不写数据库。退出码 0 表示预检通过，2 表示参数、目标、资源或文件预检失败。JSON 清单只含相对 `pathKey`，不含数据库口令、文件绝对路径或邮件秘密。`tableCounts` 动态覆盖所有带旧存储键的 `dt_` 表，并通过 `dt_run_skill` 的 `run_id` 联接补计没有冗余归属列的 `dt_run_skill_reference`；`unknownAuthorCounts` 和 `activeRuns` 用于核对历史作者和恢复安排。

确认清单、备份和停机窗口后，使用相同参数并显式追加：

```text
--execute --output=outputs/validation/legacy-claim/executed.json
```

执行在单一事务内完成：锁定认领键和管理员、复查目标与文件、创建 TEAM 空间、赋予指定账号 ADMIN、绑定全部旧会话并写入 `dt_legacy_ownership_claim`。重复认领、管理员不存在、已有空间绑定、文件缺失/变化或任一中途错误均整体回滚。不会把旧资料合并进已有非空空间。

V24 只新增认领审计表；V1–V23 不改写。审计表保存行数、未知作者、活动 Run 和文件核对快照，可用 `workspaceId` 对照实际空间及成员。

## HTTPS、Cookie、Session 与 SMTP

- 正式入口必须由受信任证书终止 HTTPS；若 TLS 在反向代理终止，代理到后端也按部署信任边界保护，不能只把公网 HTTP 标成安全。
- product 配置 `devicetruth.auth.cookie-secure=true`，保持 HttpOnly、SameSite=Lax；浏览器与 API 同源。写请求必须带当前 CSRF，登录/退出后重新获取。
- JDBC Session 表由 Flyway V19 管理，禁止启动时另行自动建表。恢复时同时恢复 `spring_session` 和属性表；退出或密码重置后的撤销语义不能绕过。
- SMTP 主机、端口、账号、授权码、发件人和 `DEVICETRUTH_PUBLIC_URL` 仅从批准的环境配置注入。公共 URL 必须 HTTPS；连接/读/写超时保持有界，关闭协议 debug。
- `ACCEPTED_BY_PROVIDER` 只表示 SMTP 服务接受，不等于收件箱收到。邀请接受状态与邮件状态分开；显式重发旋转凭证，失败不冒充成员或重置成功。

## 隔离恢复与回退

1. 在与产品隔离的实例恢复匹配的数据库和文件副本，使用相同候选程序及配置；不要连接运行库做“验证”。
2. 启动后检查健康、匿名 401、HTTPS Secure Cookie、CSRF、登录、会话刷新、调查/Run/来源/文件和原状态；worker 关闭时可安全只读核对。
3. 对 WAITING 使用原 runId/pendingQuestionId/questionVersion 补答；对多未决历史保留全部记录，未收窗前禁止普通新 Run。
4. 另验 CLI `--help`、离线场景和 legacy 健康/会话入口，确保 product 认证没有拦截旧入口。
5. 回退使用仍理解新 scope 的安全兼容版本。不能把已有多空间数据库直接交给旧匿名 product；必须运行旧程序时，只能恢复切换前匹配的库/文件备份到隔离实例，新数据先另行导出留存。

实际线上切换、旧归属认领和产品恢复必须另获操作授权。本 change 的自动演练不代替该授权。
