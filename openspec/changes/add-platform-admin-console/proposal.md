## Why

DeviceTruth 已有真实账号、个人/团队空间和产品授权底座，需要增加可安全运营的五模块管理端。现有交接的候选接口需要转化为明确的平台权限、页面、脱敏数据与共享执行合同，避免重复认证、私密内容泄露和状态操作只改标签。

## What Changes

- 增加独立 PLATFORM_ADMIN 平台授权及显式空间管理授权，复用现有 Session principal；团队 ADMIN 不产生后台权限。
- 交付用户管理、运行与用量、知识库管理、Skill 管理、系统与审计五页，统一入口、表格、确认和异常状态。
- 管理开户复用统一账号服务，初始化个人空间且保持管理员会话；账号停用/恢复接入产品既有授权、会话撤销、worker、恢复和 SSE。
- 新增 /api/admin/* 脱敏查询与受控操作合同，固定响应白名单、分页、错误、统计覆盖率及审计规则。
- 知识管理复用导入/重试/release 可见性，并与调查材料范围对齐；Skill 按空间授权并保护未决 Run。
- 配额提供部署策略、独立原子账本部件与只读展示；按用户定案保留产品重复提交 409，完整准入由依赖 change 统一接入，不提供支付或套餐。
- 共享执行接入按合同门禁实施，未接通的写能力返回明确不可用状态；保留现有 CLI/legacy、主循环、租约/CAS 与产品 API。

## Capabilities

### New Capabilities

- `platform-admin-access`：平台角色、目标空间管理范围、初始化与权限隔离。
- `admin-user-lifecycle`：用户查询、统一服务开户、停用/恢复及全链撤销。
- `admin-run-usage-quota`：运行脱敏查询、真实用量、配额策略/独立账本及未就绪展示。
- `admin-knowledge-operations`：受控空间知识导入、处理、重试、启停和历史引用。
- `admin-skill-operations`：部署目录、空间安装授权、版本与未决 Run 保护。
- `admin-system-audit`：依赖状态、管理审计及安全运维。
- `admin-console-experience`：五页信息结构、状态反馈、缓存隔离与可访问性。

### Modified Capabilities

无。主规格目录当前无已归档规格；上述能力使用 ADDED requirements。与未归档 `add-authenticated-team-workbench` 的账号/空间/持续授权合同通过 shared-contracts.md 协调，不代为改写或勾选其任务。

## Impact

- 沿用 Java 21、Boot 3.5、Spring JDBC、PG/pgvector、Vue 3/TypeScript/Vite；不增加第二套认证或执行引擎。
- 管理前端独立组织于 frontend/src/admin，后端于 io.devicetruth.admin；产品侧协调入口及共享方法合入。
- 增量扩展账号状态、平台授权/空间管理范围、审计、配额占用及知识可用性；当前基线 22e5bab，迁移 V1–V24 保留，新编号合入前分配。
- 本轮实施管理页面/API及增量迁移，在隔离库验证，不自动切换原运行库或部署。正文受控阅读、平台角色网页编辑、团队管理、Skill 在线编辑/热升级、PDF/OCR、设备事实写入与支付均在本期范围外。
