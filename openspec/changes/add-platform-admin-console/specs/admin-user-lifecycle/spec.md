## ADDED Requirements

### Requirement: 平台用户查询与统一开户

系统 SHALL 提供有界用户列表；受控开户 SHALL 调用统一校验、密码编码及账号/个人空间/成员原子创建能力，与成功审计一致。新账号 SHALL 无平台角色、无额外团队资格、无旧数据认领。

#### Scenario: 开户保留管理员登录
- **WHEN** 管理员提交合法 email/displayName/initialPassword
- **THEN** 创建一个账号及空个人空间，管理员原 Session/身份保持不变，响应和日志不含密码/编码

#### Scenario: 创建或审计失败
- **WHEN** 邮箱规范化后重复，或成功审计无法持久化
- **THEN** 重复返回 409；审计故障使创建整体回滚，不留下孤立账号/空间

### Requirement: 账号状态具备版本与审计

系统 SHALL 持久化 ACTIVE/DISABLED 及管理版本，以 expectedVersion 裁决停用/恢复。成功状态和审计 SHALL 同事务；同状态同版本返回 NO_CHANGE，旧版本 MUST 返回冲突。

#### Scenario: 并发状态更新
- **WHEN** 两个请求基于相同版本修改同一账号
- **THEN** 至多一次修改成功，过期请求返回 STATE_VERSION_CONFLICT，不覆盖较新状态

### Requirement: 停用接通会话和持续授权

停用 SHALL 原子撤销全部会话及待消费恢复凭证，并在 HTTP、空间解析、worker 领取、模型/工具边界、结果采纳、恢复和 SSE 统一识别。账号停用后新登录 SHALL 与错误凭据统一拒绝；SSE SHALL 在每次输出和不超过15秒心跳前复核。

#### Scenario: 模型在途时停用
- **WHEN** 模型调用期间账号被停用并提交
- **THEN** 在途费用可保留，但返回内容不能继续采纳/触发后续工具；相关 Run 按 AUTHORIZATION_REVOKED 收口

#### Scenario: 已有连接和服务崩溃
- **WHEN** 账号停用后旧 Cookie/SSE 继续使用，或服务在任务收尾前重启
- **THEN** 旧会话/流停止读取并释放连接占用，恢复不得继续执行被撤销 actor 的任务

### Requirement: 恢复与其他撤销动作保持区分

恢复 SHALL 保留原成员/资源/作者，不恢复旧会话或自动重放终止任务。退出、团队移除、账号停用 SHALL 保持各自范围；密码重置 MUST NOT 改变停用状态或借此恢复权限。

#### Scenario: 团队成员不因他人停用失去资源
- **WHEN** 某执行 actor 被停用后，另一有效成员读取共享调查
- **THEN** 其访问按原团队角色裁决，调查和历史作者保留，不自动把执行 actor 改为该成员

#### Scenario: 单次退出与账号恢复
- **WHEN** 用户仅退出当前会话，或停用账号随后被恢复
- **THEN** 退出不冒充取消持久 Run；恢复后旧 Cookie 仍无效，已终止 Run 不自动重放
