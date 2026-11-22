> **2026-11-19 用户定案**：保留产品同键提交 409 DUPLICATE_REQUEST。本 change 的完整配额准入不接产品执行链，改为策略/账本部件/只读展示，等待依赖 change 统一实现。下文 WAITING 重新准入及多资源计量为后续接线合同；同键重复提交始终遵守现有409，不表示本轮已接通执行配额。

# 与产品侧共享执行合同

本文件规定管理侧需要的行为和接入交付物；实际方法签名、锁顺序和本轮验证见文末2026-11-20接线记录。现有基础见 ../add-authenticated-team-workbench/account-api.md、authorization-runtime.md、workspace-api.md。

## 1. 开工与就绪门禁

| 能力 | 可独立实施部分 | 开放真实写入前必须齐备 |
| --- | --- | --- |
| 平台只读 | 平台门禁、DTO/投影、五页、统计/审计设计 | 当前账号实时状态与平台授权、字段白名单回归 |
| 开户 | 管理表单、输入 DTO、管理编排 | 统一账号创建与成功审计同事务，不切换会话 |
| 停用/恢复 | 状态操作与审计服务 | HTTP、账号/成员检查、worker、工具、结果、恢复、SSE 全部接通 |
| 知识写入 | 受控列表、导入表单、元数据 | 管理 grant、来源许可、原导入/重试、引用并发、产品第5组材料范围 |
| Skill 写入 | 目录、安装查询、操作编排 | 授权记录锁、运行选择协调、未决 Run 保护、恢复与重启 |
| 配额 | 策略/账本/视图设计 | 保留产品409、原子准入/释放、恢复对账、阈值/时区配置 |

管理 capability 的 feature readiness 默认未就绪；相应操作在鉴权后返回 503 INTEGRATION_NOT_READY，不能只写状态后回成功。readiness 由启动配置和已装配适配器明确判定，不依赖前端开关或“测试通过”动态猜测。

同一方法一次只有一个合入负责人；不得同时整类修改 GeneralAgentService/恢复/SSE/知识检索/SkillRuntime。记录双方 commit、方法、事务/锁约定和实际验证证据后，才打开对应 feature。本文不代替这一记录。

## 2. 账号创建、状态与持续授权

### 统一创建单元

输入规范化前的 email/password/displayName，复用 AccountService 同一校验、编码、唯一约束及个人空间初始化。管理服务外层事务需覆盖成功审计；若现服务不能参与同一事务，由底座负责人提取创建单元，不允许管理侧直接插账号/成员表。

注册 Controller 的 signIn 留在公开注册路径，管理开户不调用。失败全部回滚；重复邮箱不能多建空间。

### 停用

1. 在共同约定的管理/账号锁下实时确认操作者、目标及最后有效平台管理员约束。
2. 校验 expectedVersion；状态、版本、全 Session 删除、待消费恢复凭证撤销、成功审计同事务。
3. 新登录统一无效凭据；已有 principal 在 HTTP 与空间解析中查当前 ACTIVE，不能仅凭保存的 SecurityContext。
4. Run 持久 actor 授权在领取/每步/模型返回/工具事务/结果提交/恢复时识别状态。事务内的授权与副作用/结果登记有共同并发裁决：停用先提交则后续边界拒绝；已获有效授权先提交的操作保留事实。
5. 模型外呼不持账号/成员数据库锁；在途请求可能已收费，返回后拒绝采纳。相关未决 Run 经原 AUTHORIZATION_REVOKED 收口，不清除历史、不自动转给其他成员。
6. SSE 建立、每批/每条/终态与不超过15秒的心跳前复查会话、账号及成员，失败停止输出并释放订阅/连接占用。静默连接墙钟容差由联合测试环境定约。

停用状态必须持久有效，即使进程在状态提交后、任务收尾前崩溃，恢复扫描也拒绝继续执行；不能依靠内存通知实现撤权。

### 恢复及其他动作

恢复仅写 ACTIVE/版本/审计，保留成员与资源，原 Cookie 不复活、原撤销 Run 不自动重放。密码重置不改停用状态；申请/消费不得恢复账号权限。退出只撤销当前浏览器会话，团队移除只撤销该空间资格，继续遵守产品原合同。

## 3. 知识可用性与材料范围

- 输入目标 workspaceId 经平台 grant 解析成服务端存储键；独立的管理授权上下文记录真实 actor，不伪造 AccessScope.ADMIN 或目标用户。
- 导入/重试复用 FileUploadService/KnowledgeImportService；管理操作受理成功与后续处理成功分别审计，异步处理持续检查账号/目标管理授权及来源条件，不以旧请求的授权无限运行。已发生费用保留。
- 先确定普通空间知识和第5组调查附件/发布字段的映射；未发布附件不得被后台空间库列表/检索自动纳入。
- 新检索、附件关联、继承和引用登记在状态/发布/release 共同裁决下生效；参考现有 scope/release 引用锁，不另开不相交的管理锁。
- 停用阻止新的使用，既有 Run 已登记来源在当前资源读取授权内继续可读；历史引用不能当作新调查继承的捷径。
- 重复上传复用原文档时不重置 availability/revision；恢复还要检查处理、来源、材料发布与 release，不自动激活全 release 或提升许可。
- 普通停用和隐私/侵权紧急撤回分开；本期不实施后者。

## 4. Skill 安装与运行选择

开通复用安装表，当前 Registry 版本决定可选内容，工具授权交集由服务器计算。

停用与选择必须协调同一安装记录，并保持 Run 租约/CAS 的既有顺序。联合实现明确“安装锁与 Run 锁谁先取”，并验证两种竞争：

- 停用先完成：后续选择失败，不能新登记 dt_run_skill。
- 选择先完成：未决 Run 可见，停用返回 SKILL_HAS_UNRESOLVED_RUNS。

不能只对答案提交已有 FOR SHARE 的逻辑作推断。覆盖 WAITING、恢复排队、运行中及已选 Skill 的 QUEUED；未选择的排队 Run 不阻止停用，但之后不能选择已停用许可。重启 installDefault 不覆盖停用或工具缩减。

## 5. 配额与提交/恢复

| 事件 | RUN_CREATE 周期消耗 | ACTIVE_RUN | UNRESOLVED_RUN |
| --- | --- | --- | --- |
| 首次成功受理新 Run | 增加一次 | 派发/领取前原子占用 | 增加一次 |
| 同键重复提交（产品409拒绝） | 不增加 | 不新增，保留已有事实 | 不新增，保留已有事实 |
| 同键异载荷冲突 | 不增加 | 不新增 | 不新增 |
| WAITING_FOR_USER | 保留已消耗 | 释放 | 保留 |
| 合法补答恢复原 Run | 不增加 | 重新准入 | 复用 |
| 失败/取消/完成 | 不返还已受理次数 | 幂等释放 | 幂等释放 |
| 崩溃恢复 | 不增加 | 对账原占用，防重复 | 复用/修复原占用 |

这张表为提案首期默认规则；阈值与周期日界线由部署明确，不因测试便利改变。上传失败前未实际保存的字节预留释放，已保存文件/向量继续计存储；检索/Embedding 分别按操作数/真实报告单位计消耗，未知费用保留，不承诺供应商严格一次计费。

后续接线先查原 request 去重记录，已有键继续返回409且不新增占用；仅新键在事务中为相关维度原子预留、保存任务/占用关联。外呼前有界预留，失败或未知收费按可证明信息结算。任一维度不足则全体不新增占用。SSE 建立与释放同样有连接 reservation；退出/撤权/断线均幂等释放。

补答准入失败必须在消费三元组之前拒绝，原 WAITING/问题版本不变，不能消费后因没有槽而丢失补答。恢复不能通过新 reservation 绕过原上限。

账本是共享持久事实，不新建另一套 Run 提交去重表；管理操作自己的审计不取代产品 request 去重合同。多实例锁竞争、重复释放、孤儿占用与资源已存在但账本缺失都需隔离 PG 证据。

## 6. 接入记录与验收证据

每项写能力开放前记录：双方 commit、负责人、真实签名/字段、迁移号、锁顺序、feature 配置、测试命令、总数/通过/失败/错误/跳过、数据/模型性质、未验证项。先写规格不勾实施任务。

验证复用原 AgentLifecycleTest、AgentStreamTest、RunRecoveryTest、ResourceAuthorizationTest、SkillRuntimeIntegrationTest 等；新增管理权限/脱敏/审计/配额场景。真实 PG 使用隔离库，scripted 只用于明确协议/竞态回归，产品联调用真实模型；不恢复暂停的 Phase4.4/Holdout 实验。

## 2026-11-20 实际共享接线记录

基线 22e5bab/V24；本轮同一工作区串行维护管理底座与产品共享路径，未使用并行 agent 整类改写。管理增量编号分配为 V25–V27，原 V1–V24 不变。具体代码与测试证据见 acceptance.md。

| 路径 | 已落地方法 / 字段 | 事务与锁 |
| --- | --- | --- |
| 统一开户 | AdminService.createUser → AccountService.register | 外层管理事务包含原账号/空间/成员创建及成功审计，内层 REQUIRED 加入同一数据源事务；不调用 signIn |
| 状态 | dt_account.status/status_version；AccessScope.accountStatusVersion；Run.execution_actor_status_version | AdminService.userStatus 先共同管理锁，再当前 actor/目标账号锁；会话、重置凭证、状态、审计同事务 |
| 平台/范围 | AdminAuthorization.require/lock/workspace；AdminMaintenance.platform/grant | 权限写持管理控制行 UPDATE，管理操作持 SHARE；版本变更失效旧游标，grant 不写成员表 |
| 产品接入 | AgentRunRepository.canWrite/lockWriteAuthorization/persistedScope/revokedRuns/revokeAuthorization；GeneralAgentService.lockCurrentActor/reconcileRevokedAccounts | 提交事务重新校验；模型外呼不持账号锁；工具/结果在共享账号授权锁下裁决，旧执行代次不随恢复复活 |
| SSE | HttpAccess.streamGuard + 原 RunEventStreamService | 当前 Session/账号/成员复核复用原每批/每条/终态/心跳路径；心跳最大15秒，隔离环境静默墙钟容差17秒 |
| 知识导入 | AdminKnowledgeOperations.upload/retry/authorizeFile；FileUploadService.managedAuthorization/recoverManagedImports；KnowledgeImportService.managedAuthorization/managedWrite | 受理事务审计+afterCommit 派发；持久 actor/来源/版本与60秒租约；15秒续租，过期扫描10秒；外呼前及最终提交复核，不跨外呼持锁 |
| 普通上传 | FileUploadService.uploadForActor/retryForActor；uploaded_by_user_id/uploaded_by_status_version | HTTP 真实 actor 进入上传记录；处理边界复核当前成员/账号代次，停用/恢复不复活在途上传；legacy 未知作者保持未知 |
| 新资料使用 | availability/management_revision；ScopedKnowledgeStore.load；KnowledgeSearchService.scopeSql；AgentRunRepository.importReference；readyAttachments | 管理启停取原 scope exclusive；检索最终登记、引用继承和提交时附件复核取 shared；历史已登记读取不加可用性过滤 |
| Skill | AdminService.install/skillStatus；SkillInstallationRepository.select 的 current_installation CTE | 原 Run 租约锁后选择安装 SHARE；管理仅持安装 UPDATE，未决 Run 查询不取 Run UPDATE；两种竞争均实际验证 |
| 配额 | AdminQuotaPolicy、AdminQuotaLedger、AdminQuotaViews | 本轮仅独立账本；策略行按稳定顺序锁定裁决多维预留，释放不返还 DAY/PERSISTENT；产品依旧409重复提交，无强制接线 |

材料字段采用现有 SPACE_KNOWLEDGE / CONVERSATION_ATTACHMENT / dt_knowledge_publication / release 的真实结构。本管理变更已守住未发布附件排除与新使用可用性；产品侧调查附件页面、显式发布入口和执行段材料快照仍属原产品 change，不代勾其任务。

Readiness：USER_CREATE / USER_STATUS / SKILL_WRITE 已装配；KNOWLEDGE_WRITE 还需本地受控 corpus 完整可验证；QUOTA_ENFORCEMENT 无策略为 UNCONFIGURED，有策略也保持 PENDING_INTEGRATION。后续完整配额接线必须继续保留用户确认的产品幂等合同，不能从本账本的同键复用推断产品要返回原 Run。
