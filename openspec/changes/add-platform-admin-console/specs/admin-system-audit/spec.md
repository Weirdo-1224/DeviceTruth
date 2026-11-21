## ADDED Requirements

### Requirement: 依赖健康基于实际证据

系统 SHALL 显示依赖状态、检测时间和证据范围；无证据 SHALL 为 UNKNOWN。GET 健康接口 MUST NOT 发起付费模型调用或输出配置秘密/连接串/路径/供应商异常。

#### Scenario: 只有静态探针成功
- **WHEN** /api/health 返回 UP，但没有近期模型或数据库检测证据
- **THEN** 不宣称所有依赖健康，缺少证据的组件保持 UNKNOWN

### Requirement: 管理操作成功审计原子一致

开户、账号状态、知识导入/重试/启停、Skill 开通/启停及维护授权 SHALL 记录真实 actor、目标、动作、结果、时间和关联 ID。业务写入与 SUCCESS/NO_CHANGE 审计 SHALL 一致，失败/拒绝 SHALL 留受控安全记录。

#### Scenario: 审计落库故障
- **WHEN** 管理业务变更可以写入但成功审计失败
- **THEN** 业务回滚并返回 ADMIN_AUDIT_UNAVAILABLE，不产生没有审计的成功

### Requirement: 审计数据最小化和分页

审计 SHALL 按 admin-api.md 有界筛选/稳定分页，仅存允许的状态及有限 reasonCode；密码、令牌、Session ID、私密内容、请求体和任意异常 MUST NOT 进入审计。

#### Scenario: 开户请求与失败异常包含秘密
- **WHEN** 开户或导入操作失败，输入/底层异常包含密码、文件内容或连接信息
- **THEN** 审计仅记录稳定错误/关联 ID，列表/日志不出现秘密探针

### Requirement: 运维与迁移保留数据和隔离

系统 SHALL 提供初始化、HTTPS/CSRF、迁移、备份恢复与故障说明，在隔离库验证。迁移 MUST 使用共同分配新编号，保留 V1–V24；回退 MUST 保留已生效撤权与审计。

#### Scenario: 旧知识迁移编号与当前历史不同
- **WHEN** 部署库曾使用旧本机知识 V18/V19
- **THEN** 先备份并制定兼容迁移，不直接启动新版或 repair 覆盖历史
