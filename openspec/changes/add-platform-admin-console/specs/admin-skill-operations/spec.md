## ADDED Requirements

### Requirement: 部署目录与内容受控读取

目录 SHALL 来自当前已加载 Registry，详情/reference SHALL 仅按登记 ID/文件名有界读取，无服务器路径。Skill 材料 MUST NOT 绕过工具网关或作为管理指令执行。

#### Scenario: 伪造 reference 路径
- **WHEN** 管理员请求绝对路径、穿越路径或未登记 reference
- **THEN** 服务端拒绝且不泄露文件内容/内部路径

### Requirement: 按空间安装及版本工具约束

管理安装 SHALL 复用 dt_skill_installation，要求目标 grant、当前加载版本及服务端工具交集。目录/安装/启用/版本匹配 SHALL 分开表达；首期 MUST NOT 任意编辑工具或热升级。

#### Scenario: 已安装版本与加载版本不同
- **WHEN** 空间授权记录版本不在当前 Registry
- **THEN** 显示 VERSION_MISMATCH/NOT_LOADED，不因 enabled=true 显示可用，恢复/新开通拒绝

### Requirement: 未决 Run 阻止普通停用

Skill 停用 SHALL 检查已选该授权版本的所有未决 Run，包括 WAITING/运行中/恢复排队；存在时返回 SKILL_HAS_UNRESOLVED_RUNS 和有界范围内 Run ID。检查、状态更新与运行选择 SHALL 协调同一授权记录。

#### Scenario: 已选 Skill 的任务等待补答
- **WHEN** 管理员停用仍被 WAITING Run 使用的空间授权
- **THEN** 409 拒绝，保留 enabled 和 revision，不返回问题/答案

#### Scenario: 选择与停用并发
- **WHEN** 新选择和普通停用同时发生
- **THEN** 停用先提交则选择失败；选择先提交则停用看到未决 Run 并拒绝

### Requirement: 状态与历史重启保留

开通/启停 SHALL 写审计并校验管理 revision；重启默认安装 MUST NOT 覆盖停用或缩减工具集。已完成历史选中版本 SHALL 保留，首期不承诺多版本同时在线。

#### Scenario: 停用后重启及重复开通
- **WHEN** 授权停用后服务重启，或再次开通同一安装版本
- **THEN** 重启保留停用；重复安装冲突，不借开通恢复许可
