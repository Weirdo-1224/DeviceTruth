# Proposal

## Why

DeviceTruth 已有可持久化的调查、补答、证据和报告链路，但 product 仍采用服务器固定身份，无法承载真实用户和多人协作。用户已在 2026-11-19 确认登录、个人/团队双空间及共同调查的交互原型，现在需要把设计转化为可实施、可验证的权限、数据和界面契约。

## What Changes

- 增加邮箱密码注册、登录、退出、密码重置和受邀加入；注册后创建独立个人空间，不继承演示资料或团队资格。
- 增加团队创建、邀请、成员角色及团队设置；管理员、协作成员和只读成员具有明确的服务端权限。
- 将身份拆为操作人和资源空间；同一空间统一管理调查、设备和资料，个人空间与各团队之间隔离。
- 团队成员可接续同一调查；记录每条正式补充、讨论、引用讨论及每次运行/补答/取消的操作人。讨论不会自动进入模型输入。
- 同一调查只允许一个未决 Run；普通补充创建新 Run，待答问题消费原三元组并恢复同一 Run；并发冲突保留草稿、要求同步确认，不自动排队。
- 调查上传默认是本调查附件；明确发布后才可被同空间其他调查检索，保留原作者、原文版本和数据性质。
- 按已确认原型重整 Vue 主链路：空间导航、当前判断、就近补充、讨论/分析记录分流、按需证据与报告；保留候选判断、历史轮、风险及真实状态语义。
- **BREAKING**：正式 product HTTP 资源接口将要求登录，不再把匿名请求归入固定演示身份；上线需明确认领旧数据。CLI、legacy profile、既有内部离线调用不因此要求用户登录。
- 原型归档到本变更的 `prototype/`，作为界面验收基准；预览身份切换和“结束本轮演示”不进入产品。

## Capabilities

### New Capabilities

- `account-authentication`：邮箱账号、会话、密码重置及受邀邮箱身份确认。
- `workspace-access-control`：个人/团队空间、成员邀请、角色、资源范围及旧数据兼容迁移。
- `team-investigation-collaboration`：作者归属、讨论引用、串行调查、补答竞争、取消与持续授权。
- `investigation-material-scoping`：调查附件与空间知识库分层、显式发布及工具检索范围。
- `collaborative-workbench-experience`：已确认原型对应的桌面主链路、角色反馈和跨空间异步隔离。

### Modified Capabilities

无。`openspec list --specs` 当前返回 `No specs found`，主规格目录仅有 `.gitkeep`，因此本次使用 ADDED requirements。既有行为通过各未归档变更的 delta 和实际代码识别，本变更不代为归档、补勾旧任务或覆盖旧验收记录；兼容及新增收紧项在 design 中逐项说明。

## Impact

- 后端：`ProductConfiguration`、product 控制器、`GeneralAgentService`、会话/Run 仓储、`RunRecoveryService`、`RunEventStreamService`、工具上下文与资产/知识授权。保留唯一 `TaskOrchestrator` 主循环及租约/CAS 恢复。
- 数据：在现有 V1–V18 之后添加 Flyway 迁移，新增账号、空间、成员、邀请、讨论、发布及审计数据，兼容原 owner/project 外键链与历史未知作者。
- 前端：重整 `App.vue` 组合边界，扩展 `api.ts` 的登录/空间合同，复用 `diagnosisViewModel.ts`、`timeline.ts`、`scope.ts`、既有报告和组件，避免另造状态/事件框架。
- 依赖/部署：沿用 Java 21、Spring Boot 3.5.16、PG/pgvector；拟使用 Boot 管理版本的 Spring Security、Spring Session JDBC 及邮件适配。部署需 HTTPS、会话表和真实邮件配置；缺失时不宣称登录恢复/邀请已验收。
- 范围外：企业 SSO、派工审批、实时共同编辑、并行调查分支、跨空间自动共享/迁移、PDF/OCR、新模型运行时及设备控制。
- 本轮仅创建规划和已确认原型快照；实施任务全部保持未完成。
