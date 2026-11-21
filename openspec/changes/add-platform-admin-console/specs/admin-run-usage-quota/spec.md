## ADDED Requirements

### Requirement: 运营运行投影固定白名单

系统 SHALL 提供 admin-api.md 的 Run 列表/详情/尝试分页，仅选择受控列。用户问题、标题、答案、附件/引用内容、Prompt、工具参数、密钥、路径及供应商原始异常 MUST NOT 出现在响应、错误或管理审计。

#### Scenario: 用户事件包含内容探针
- **WHEN** Run 答案、问题、工具参数和异常字段含独特私密探针
- **THEN** 管理员仍可查询状态/计数/稳定错误码，但所有管理响应中探针均不存在

### Requirement: 管理分页绑定过滤与权限

系统 SHALL 使用有界分页、稳定复合排序及绑定端点/principal/授权 revision/筛选/高水位的完整性校验游标，每页重新鉴权。未知历史 actor/workspace SHALL 保持未知。

#### Scenario: 换空间或账号复用游标
- **WHEN** 列表游标用于另一端点、账号、筛选或授权 revision
- **THEN** 返回 INVALID_CURSOR 或先返回当前权限拒绝，不能跨界继续翻页

### Requirement: 用量反映真实覆盖与归属

统计 SHALL 区分 Run/模型尝试、等待/失败、活跃耗时/总历时，返回时间窗/时区/生成时间。未知 token/cost SHALL 保持 null，部分上报 SHALL 标覆盖不足，金额 SHALL 按已知币种/模型分桶。账号尝试归属不完整 MUST 显式表达。

#### Scenario: 部分 token 且币种未知
- **WHEN** 多次模型尝试只有部分上报 token，现存 cost 缺币种
- **THEN** 返回已报告小计和覆盖率，标 PARTIAL；未知币种不计入货币总额，不当作零或正式账单

#### Scenario: 团队补答与等待
- **WHEN** 同一 Run 有多个执行段 actor 且正在等待补答
- **THEN** Run 只计一次，等待不算失败，缺可靠段归属的尝试单列未归属，不向多人重复计整条 Run

### Requirement: 产品幂等合同保持兼容

本 change SHALL 保留当前产品同键重复提交返回 409 DUPLICATE_REQUEST 的合同，不增加重复 Run 或额外占用。完整配额准入 SHALL 由依赖 change 在统一产品合同下接入，本管理端 MUST NOT 提前宣称已启用强制配额。

#### Scenario: 同一幂等键重试
- **WHEN** 已受理的产品提交以相同幂等键再次发送
- **THEN** 沿用 409 DUPLICATE_REQUEST，不新增 Run；本管理端不插入产品尚未接通的配额占用

### Requirement: 策略和独立账本具备可核验能力

系统 SHALL 提供 account/workspace/platform 的部署策略、持久预留结构和独立原子账本部件。部件 SHALL 支持多维原子预留、同键同载荷复用、异载荷冲突、幂等在途释放和 Run 对账；这些部件的独立测试 MUST NOT 冒充完整产品接线。

#### Scenario: 两实例争抢最后额度
- **WHEN** 在隔离 PG 的账本测试中两实例竞争最后一个资源额度
- **THEN** 最多一个成功，失败不留下任何部分维度占用；同键复用不重复预留

#### Scenario: 终态释放和未知计量
- **WHEN** 独立账本对 WAITING/终态 Run 对账，或取消后执行释放
- **THEN** 只释放对应在途资源，不返还每日消耗或持久存储；未接通的上传/导入/检索/Embedding/SSE 计量保持未知

### Requirement: 未接通配额如实展示

未配置策略 SHALL 显示 UNCONFIGURED；已配置但原幂等、执行段、多资源准入及对账尚未全部接通 SHALL 显示 PENDING_INTEGRATION。相关已用/剩余 SHALL 保持 null，不得将空账本认作已完整保护。

#### Scenario: 安装试运行策略但执行接线未完成
- **WHEN** 运维安装 pilot-v1 策略，管理端查询额度
- **THEN** 可显示明确阈值/周期/时区，enforcementState 仍为 PENDING_INTEGRATION，未知占用与剩余不填零
