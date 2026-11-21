## Context

基线为 22e5bab7b033028c09dd6a31a8143d99e69fa384，迁移到 V24。账号/团队 change 已完成第 1–3 组，讨论/串行 Run、材料发布、前端仍在后续组。账号停用、平台权限和管理 API 尚不存在。已有知识 release 运维能力不能直接等同于网页文档启停。

需求来源与源码事实见 [需求梳理](../../../docs/design/admin-management-requirements-review-20261119.md)。本 change 的规范分层：

- specs/：必须实现的行为及场景。
- [permission-matrix.md](permission-matrix.md)：权限矩阵及初始化。
- [page-structure.md](page-structure.md)：五页结构、字段与交互。
- [admin-api.md](admin-api.md)：路径、输入、DTO、错误和脱敏白名单。
- [shared-contracts.md](shared-contracts.md)：产品侧接入点、就绪门禁和并发约定。
- tasks.md：实施任务，全部以真实验证为勾选依据。

这些文件为实施合同；本轮实现、共享签名与验证证据见 shared-contracts.md 和 acceptance.md，原规划历史予以保留。

## Goals / Non-Goals

**Goals:**

交付五模块前后端；复用统一认证；区分平台运营、目标空间管理和用户私密内容；真实反映状态/用量/依赖；让写操作与审计形成闭环，并准确表达配额账本与尚未接通的执行边界。

**Non-Goals:**

第二套账号/密码/成员体系、另建 /api/me、团队管理后台、任意私密正文阅读、平台角色网页编辑、公共知识平台、Skill 编辑/热升级、多版本引擎、设备事实写入、PDF/OCR、支付。

## Decisions

### 1. 平台角色与目标空间授权分层

首期只有一个平台角色 PLATFORM_ADMIN，未授权用户为 NONE；业务能力名称用于逐接口校验，不建设可编辑 RBAC 平台。全局用户/脱敏运行/用量/健康/审计由平台角色授权；知识和空间 Skill 操作另需显式 workspace grant。详细矩阵为唯一权限入口。

不使用空间 ADMIN、ServerIdentity 配置或 Session 中缓存的角色判断平台权限。每个请求查询当前账号状态、平台授权和必要的目标范围；客户端 capability 仅用于展示。平台管理员初始没有任何空间管理授权。

初始化/平台角色/范围授予撤销采用可信维护入口，目标必须是现有账号/空间，写审计并保护最后有效平台管理员。首期无网页授权接口。选择此方案可交付必要运营能力，避免引入多角色策略编辑及隐式全空间内容访问。

### 2. 管理查询使用独立投影

新增 AdminRunSummary/AdminRunDetail/AdminUsageSummary 等 DTO，只 SELECT 白名单列。现有 RunView、公开事件映射均包含用户内容，不能直接序列化。未知历史作者和用量为 null，不能回填为当前管理员或零。

用户列表可读完整邮箱用于受控开户定位；运行列表只含 actor UUID，不带邮箱、名称、问题/调查标题。允许的知识标题限已获管理授权的空间，Skill 正文限受控部署目录；用户资料正文首期无接口。禁用通用 payload/debug/raw 参数、任意字段投影和导出。

### 3. 账号创建与状态

受控开户接收 email/displayName/initialPassword，复用统一校验/编码和账号+个人空间+成员的原子创建，不调用会 signIn 的注册 Controller。新用户仅为普通账号，创建不附送平台角色或团队资格。初始密码仅本次 HTTPS 请求内使用，提交结束清空表单，不回显/存草稿；首期不宣称强制首次改密。

若 AccountService 的事务封装阻碍管理成功审计与创建同事务，由底座负责人提取可复用创建单元，保持公开注册合同不变。

dt_account 增量增加 status、status_version、status_changed_at、status_reason_code，旧账号默认 ACTIVE。停用事务写状态、递增版本、撤销全会话和待消费恢复凭证、写成功审计。统一账号状态读取接 HTTP、成员解析、后台持久 actor 检查、恢复和 SSE；不另写互不知情的认证过滤器。

停用导致受影响未决 Run 按现有 AUTHORIZATION_REVOKED 路径收口；不更改其他团队成员或自动转交执行 actor。恢复不恢复会话、不自动重放。账号退出仅当前会话，团队移除仅对应成员资格，维持产品原合同。

### 4. 操作并发与审计

状态写入使用 expectedVersion 与数据库锁/CAS，返回冲突而非覆盖他人操作。知识/Skill 新增管理 revision，不复用 Skill 内容 version 或 Run lease version。重复设置相同状态只有在 expectedVersion 匹配时返回 changed=false，记录 NO_CHANGE 审计；过期版本仍冲突。

业务修改和 SUCCESS/NO_CHANGE 审计同事务；审计失败则回滚。FAILURE/DENIED 在业务事务外记录，独立失败不能改成成功。输入 reasonCode 使用有限枚举，无自由文本审计详情。请求 body/密码/凭证/SQL/模型文本永不作为审计 payload。

保留产品既有锁顺序；账号状态、成员、授权记录、引用/release 和配额的共同锁顺序由 shared-contracts.md 的接入项落实。数据库锁只覆盖准入/状态/登记，不跨模型或 embedding 外呼持有。

### 5. 用量和配额

Run 数按 ID 去重；活跃时长与总历时分开，等待不计失败。token/cost 分别计覆盖率，cost 无币种/价格口径时不能跨货币求和或显示人民币账单。多模型/币种按 bucket 返回。已有 cost 缺币种时列入 unknownCurrencyCostCount，不进入货币总额。

初始发起人可用于 Run 创建次数统计；补答后的模型用量若无可靠执行段关联，账号维度 attributionStatus=INCOMPLETE、未归属部分单列；空间统计仍按真实资源空间去重。不得把完整 Run 计入每个补答人。

配额按 account/workspace/platform 三维声明，首期部署策略只读展示。资源种类含 RUN_CREATE、ACTIVE_RUN、UNRESOLVED_RUN、UPLOAD_BYTES、IMPORT_JOB、RETRIEVAL、EMBEDDING_TOKENS、SSE_CONNECTION、STORAGE_BYTES。区分周期消耗与在途占用；取消不返还已发生模型费用/存储。等待释放 ACTIVE_RUN，保留 UNRESOLVED_RUN；补答重新竞争活跃槽。

2026-11-19 用户决定：保留当前产品同键返回 409 DUPLICATE_REQUEST；本轮不升级为返回原 Run。完整配额准入与各资源执行接线由依赖 change 统一实现。本轮提供策略、持久账本部件及只读视图，独立账本的幂等预留不改变产品提交合同。无明确策略显示 UNCONFIGURED，不能把未接通配额宣传为已保护。阈值与日界线为部署决定，运行前需显式配置。

### 6. 知识与 Skill 状态

知识管理为显式空间知识，复用文件/解析/切片/向量化/重试服务。分开表示 processingStatus、ingestStatus、availability、materialScope、publicationStatus、releaseActive。可用不等于 READY，也不证明来源许可。

启停只影响新检索/新引用；既有历史证据按原空间授权继续读取。重复上传保留停用；恢复同时检查处理、来源、发布和 release。复用现有 scope/release 引用登记协调机制，并对齐产品第 5 组附件/发布规则。

Skill 目录来自实际加载 Registry，展示 registryVersion 与 installation.version，不伪造多版本支持。管理授权只能选择当前加载版本，工具集取服务器/Skill/安装交集。停用在安装记录锁内检查已选该 Skill 的全部未决 Run；运行选择参与同一协调。冲突只返回范围内 Run ID，不返回问题内容。

### 7. 页面与 API 隔离

沿用现有 Vue/Vite，frontend/src/admin 独立 shell/五页/client/样式。页面逻辑键先定义 /admin/users、/admin/runs、/admin/knowledge、/admin/skills、/admin/system；是否用 Router 由产品入口负责人决定，本 change 不强行引入路由库。

账号/空间切换和权限失效递增 generation、取消请求、关闭旧连接、清理缓存；晚到结果不入当前页。管理端不发起 Run SSE 内容订阅，也不借用户 API 读取诊断详情。

后端 io.devicetruth.admin 下划分 api/application/authorization/persistence。管理认证复用 Cookie/CSRF，平台门禁加入现有 product 安全链；产品 DTO 和旧 CLI/legacy 保持兼容。

## Risks / Trade-offs

- [停用或启停只在入口生效] → 同时覆盖持久 actor、执行边界、结果采纳、引用登记、恢复及 SSE，未接齐时阻止对应写功能开放。
- [共享类与产品第 4/5 组冲突] → 每个共享方法单一合入人，先交换接口与锁顺序，再串行接入，不整类替换。
- [统计把未知当零/重复计费] → 独立覆盖率、执行段归属和币种桶，禁止以估算填补未知。
- [范围授权被当成团队成员] → 独立管理 grant，不写空间成员表，普通资源接口不因平台角色放宽。
- [审计或幂等故障产生假成功] → 同事务成功审计、原幂等回执与占用键复用，冲突/失败可定位。
- [部署旧库迁移历史不兼容] → 隔离演练后按现库历史制定方案，不修改 V1–V24 或直接 repair。

## Migration Plan

1. 实施前核对双方 commit、现有未提交修改及最大 Flyway 版本；为管理增量迁移共同分配新编号。
2. 隔离库验证新建和从当前 V24 升级，保留账号、空间、历史作者、Run、知识与 Skill。
3. 先启用管理权限门禁、只读 API/页面与审计；可信维护入口为已注册账号授平台角色及显式空间范围。
4. 共享合同验证后逐项启用开户/停用、知识、Skill 和配额。未接通项通过 capability readiness 和 503 INTEGRATION_NOT_READY 表达，不假成功。
5. 回退先关闭管理入口和写能力，保留审计与新增表列；已停用状态和撤权必须由兼容它们的后端继续执行，不能回滚到忽略状态的旧二进制。配额回退按实际资源对账，不能清账重放。

## 实施决策状态（2026-11-20）

| 原待定事项 | 已落实结果 |
| --- | --- |
| 共享快照、负责人、新迁移编号 | 22e5bab/V24基线，同一工作区串行接线，新增V25–V27；签名见shared-contracts |
| 账号/成员/Run与授权锁顺序 | 已记录共同管理锁、账号代次、原Run租约/CAS、知识scope及Skill安装行顺序，并做竞态验证；锁不跨模型外呼 |
| 静默SSE墙钟容差 | 最长15秒心跳前复核，隔离测试允许17秒墙钟容差 |
| 材料字段与引用接口 | 复用SPACE_KNOWLEDGE、CONVERSATION_ATTACHMENT、dt_knowledge_publication和KnowledgeVisibilityGuard；产品附件UI仍由依赖change交付 |
| 配额阈值、日界线与币种 | pilot-v1三维策略，Asia/Shanghai日界线；未知币种不合计，未知用量不填零；完整执行准入后续接线 |
| 页面切换方式 | main.ts对/admin独立挂载AdminShell，五个同源深链接，产品页仅增加管理入口 |

## 2026-11-19 实施决策补充

- 用户明确授权使用此前收集的知识。管理上传接受现有完整 corpus 清单校验值匹配的 Markdown，保留原来源性质及本地研究授权依据，不接受把未知文件挂到已登记 sourceId；重新收集/新资料登记走后续维护流程。
- 管理知识写入按现有 SPACE_KNOWLEDGE / CONVERSATION_ATTACHMENT / dt_knowledge_publication / release 实际字段接入。后台只管理显式空间知识；未发布附件不参与空间检索和新的继承。产品侧调查附件页面及发布操作仍属原产品 change。
- 产品重复提交继续 409 DUPLICATE_REQUEST；原有返回原回执的配额方案在本轮不实施。前一版本完整准入任务保留在 acceptance 的决策历史，不能误勾为已执行。
- 账号状态增加持久执行代次，恢复账号不复活停用前的 Run；撤权扫描与既有租约恢复扫描分别调度，避免改变原队列时序。
