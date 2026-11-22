# 实施与验收记录

## 2026-11-22：整合到新版工作台

本次继续已有合并，目标分支 `codex/frontend-workbench-v2`（UI 父提交 `057a578`），来源 `codex/platform-admin-console-20261121`（管理端父提交 `7e26639`）。保留普通页面的 `AuthenticatedWorkbench`、今天的 `App.vue` 与工作台样式；`/admin/*` 独立加载 `AdminShell`。纳入此前未暂存的附件校验、Windows embedding 账本写入和轮次测试夹具修正。代码合并不自动推送或部署。

### 合并遗漏及修复

修复前全量回归发现 3 项失败：`AdminKnowledgeTest` 的停用知识新引用检查，以及 `InvestigationMaterialTest` 的私有附件检索、显式导入同内容材料检查。原因是两分支的可用性与材料范围条件未同时保留。现已修复：

- `AgentRunRepository.importReference` 同时检查本轮 `MaterialScope`、文档启停/就绪和 release 可用性，历史引用不删除。
- `KnowledgeSearchService` 与 `ScopedKnowledgeStore` 允许当前 Run 冻结的私有材料，但不使其跨调查可见；空间知识与显式发布保留原用途，无 Run 的检索不新增 Run 前置条件。
- `FileUploadService.readyAttachments` 保留归属、导入和启停检查，不要求私有附件先公开发布；会话归属继续由 `requireAttachmentConversation` 检查。
- 新增混合检索下的私有附件隔离/停用回归，并补充未发布附件可用、停用后拒绝的断言。

### 本次实际验证

环境：Windows、Microsoft JDK 21.0.13、Maven 3.9.9；真实 PostgreSQL 16/pgvector 使用本次新建的独立临时容器，测试自行创建随机数据库，结束后临时容器已删除。模型和向量使用离线协议替身/确定性实现，不代表真实供应商验收。现有产品容器和数据库未改动。

| 验证 | 实际命令 | 总数/通过/失败/错误/跳过 |
| --- | --- | --- |
| 首轮缺数据库配置，后终止 | `mvn -o -q -Ddevicetruth.build-directory=outputs/validation/merge-admin-ui-20261122/maven test` | 已生成报告 352/267/0/76/9；未完成，错误为缺少测试 PG 配置 |
| 修复前完整后端回归 | `mvn -o -q -Ddevicetruth.build-directory=outputs/validation/merge-admin-ui-20261122/pg-maven test` | 671/652/3/0/16 |
| 修复后全部知识库及受影响产品路径 | `mvn -o -q -Ddevicetruth.build-directory=outputs/validation/merge-admin-ui-20261122/fix-maven -Dtest=io.devicetruth.knowledge.*Test,AdminKnowledgeTest,InvestigationMaterialTest,AuthenticatedResourceTest test` | 176/176/0/0/0 |
| 前端合同 | `cd frontend && npm run test -- --run` | 174/174/0/0/0 |
| 前端类型检查与构建 | `cd frontend && npm run build` | 通过 |
| 差异与冲突检查 | `git diff --check`、`git diff --cached --check`、`git ls-files -u` | 无 whitespace 错误、无未解决冲突 |

三轮后端结果分别报告，不相加为一次全量通过。修复后未再次运行全量；复验覆盖全部知识库测试和这次修复直接影响的管理知识、调查材料及认证资源路径。修复前全量的 16 项跳过包含显式开关控制的真模、备份、浏览器宿主、SMTP 和 Windows 符号链接条件测试，未标为通过。原始日志、Surefire XML 和 summary.json 保留在 `outputs/validation/merge-admin-ui-20261122/`，不提交生成报告。

### 数据库与交付边界

保留知识库分支 V23–V27；工作台原 V23/V24 内容不变，重编号为 V28/V29。隔离库已执行 V1–V29；已经执行工作台原 V23/V24 的运行库仍需独立兼容方案，不能直接启动合并版或删除/repair 历史。运维说明已更新。未执行现有运行库升级、服务重启、合并版浏览器体验、真实模型/embedding 外呼或远端推送；此前历史验收保留如下。

## 2026-11-21：完整数据管理端启动

用户要求管理端完整展示已有内容，替换此前只有空测试空间的体验。原库仅备份和只读核对：24 张业务表复制前后摘要均未变，保留 V19 与未激活发布状态。将冻结快照的业务列复制到已经正常迁移的独立 V27 库，逐表摘要一致、60 个外键检查通过，343 份原文件字节/hash 校验通过；旧 Flyway 历史没有 repair、改写或覆盖当前历史。明确建立并授权“已有正式知识库（343篇）”，不认领历史 Run 作者/调查空间。启动与边界见 docs/operations/platform-admin.md。

补齐历史 corpus 入库展示：AdminKnowledgeViews 在受控空间内读取真实 file/document/import_item 关联，返回 importOrigin=CORPUS_IMPORT、retryable=false；管理上传仍保留失败重试路径。文档详情可定位原入库文件，其他空间、未发布调查附件不进入列表。前端区分“已有知识入库”与“管理端上传”，修正导入空态文案和知识启停确认目标标题。未虚构 dt_admin_import 或历史管理员审计。

本轮实际验证（不覆盖下方历史）：

| 验证 | 命令/证据 | 总数/通过/失败/错误/跳过 |
| --- | --- | --- |
| 定向 PostgreSQL 回归 | `python3 scripts/verify_admin_console.py -Dtest=AdminKnowledgeTest,PlatformAdminTest` | 18/18/0/0/0（含继承迁移测试） |
| 前端 | `cd frontend && npm run test -- --run` | 105/105/0/0/0 |
| 当前构建 | 前端 `npm run build`；隔离 Maven 目录 `mvn -o -q -Ddevicetruth.build-directory=outputs/validation/platform-admin/maven -DskipTests package` | 均通过；package 不重复运行测试 |
| 完整数据浏览器/API | `/private/tmp/devicetruth-admin-venv/bin/python outputs/admin-preview-20261121/verify_full.py` | 34/34/0/0/0，0 JS 错误，12 张页面截图已实际检查知识列表和入库列表 |

实际分页核对 343 文档/343 历史入库/63 来源/8 部署 Skill/21 安装/36 历史 Run/2 当前账号/9 配额资源行；文档详情及文件定位、全历史时间筛选、用量与 Run 数一致、旧作者未知不伪造、3 个 WAITING 不重放、普通成员拒绝均通过。没有新增供应商请求或费用；原682向量复用，343文档仍STAGED、release READY未激活，配额UNCONFIGURED/计量未知保持。真实供应商当前连通、全量后端回归和真实现场质量本轮未重复验证；原成功真模回执仍见下方历史。未commit/push、未改原运行服务或原库。

## 2026-11-20：实现交付

基线 HEAD 为 `22e5bab7b033028c09dd6a31a8143d99e69fa384`，新增 V25–V27，V1–V24 未改。验证使用随机隔离 PostgreSQL 16/pgvector；没有迁移原运行库、部署、暂存、commit 或 push。原有未跟踪需求梳理文档保留。OpenSpec采用spec-driven apply；完成后保留活跃change供审阅，未执行sync/archive。

### 用户决策与范围调整

- 用户要求保留产品同键提交 `409 DUPLICATE_REQUEST`，已同步proposal/design/admin-api/shared-contracts及配额规格和任务7.2–7.5。原方案“同键返回原Run、全链多资源准入/释放/对账”未在本轮实施，交由依赖change统一接入；没有把原方案当作已完成。
- 用户授权代理设计配额，交付 `pilot-v1` 三维阈值、Asia/Shanghai日界线及独立原子账本。未配置为UNCONFIGURED，配置后仍为PENDING_INTEGRATION；未知已用/剩余为null，当前未强制产品配额。
- 用户授权使用此前收集资料。上传限定已有corpus清单、sourceId及内容hash匹配，保留原REAL/PROJECT_RULE性质与本地研究使用依据，不扩大原出版物许可。
- 用户明确授权一次EV-0001及相关Skill/隔离问题发送至DeepSeek和DashScope，已完成；不重复付费验收，不恢复暂停的Phase4.4/Holdout/批量实验。

### 交付及实际运行链路

`frontend/src/main.ts` 对 `/admin/*` 挂载独立 `AdminShell.vue`，五页复用原Cookie和CSRF。`AdminGateFilter` / `AdminAuthorization` 实时检查账号、平台角色与目标空间grant；团队ADMIN没有平台权。`AdminController` 调用管理服务，成功变更与脱敏审计同事务，查询只读白名单SQL。游标绑定账号、授权版本、筛选及快照，30分钟有效，重启失效；前端generation/AbortController丢弃过期读取和写错误。

开户由 `AdminService.createUser → AccountService.register` 原子创建账号和个人空间，不切换管理员会话。账号停用撤销全部会话与重置凭证，并通过持久statusVersion覆盖HTTP、worker领取/模型返回/工具/结果/恢复、上传和SSE；恢复不会复活旧Cookie或旧Run。撤权扫描每秒，与原租约恢复周期分开；SSE最长15秒心跳复核，隔离验收允许17秒墙钟容差。

知识导入经 `AdminKnowledgeOperations → FileUploadService / KnowledgeImportService`，提交后派发、持续检查actor代次/grant/来源与60秒租约，15秒续租、10秒恢复扫描。停用与新引用共用原 `KnowledgeVisibilityGuard` scope锁，历史引用保留；同内容新版本继承停用。Skill开通使用当前Registry、服务器/实际注册/声明/安装工具交集；选择持安装SHARE锁，停用持UPDATE锁检查全部已选未决Run，避免并发绕过。

### 实际验证

以下计数分别报告，不相加为一次运行，也不把继承测试重复算新增测试。

| 验证 | 命令 / 证据 | 总数 / 通过 / 失败 / 错误 / 跳过 |
| --- | --- | --- |
| 后端全量（最后工具交集收紧前） | `python3 scripts/verify_admin_console.py --full`；`outputs/validation/platform-admin/full-summary.json`及full-maven.log | 630 / 624 / 0 / 0 / 6 |
| 最后代码定向复验 | `python3 scripts/verify_admin_console.py -Dtest=PlatformAdminTest,AdminKnowledgeTest,AdminQuotaLedgerTest,SkillRuntimeIntegrationTest`；summary.json及maven.log | 25 / 25 / 0 / 0 / 0 |
| 前端合同 | `cd frontend && npm run test -- --run` | 105 / 105 / 0 / 0 / 0 |
| 前端构建 | `cd frontend && npm run build` | vue-tsc及Vite成功 |
| 打包与CLI | JDK21 `mvn -o -q -DskipTests package`；`python3 scripts/verify_cli.py`；target/cli-verification.json | 构建通过；A/B/C/D、help、错误参数、缺Key及本地HTTP私有配置读取全部通过 |
| 备份恢复独立开关验收 | `AdminBackupRestoreTest`；outputs/validation/platform-admin/backup/result.json | 4 / 4 / 0 / 0 / 0（含3个继承迁移测试） |
| 真模独立开关验收 | `AdminRealModelTest`；outputs/validation/platform-admin/real/report.json | 4 / 4 / 0 / 0 / 0（含3个继承迁移测试） |

全量6项跳过为需显式开关的可选验证，不能标为通过；新增真模与备份已另以显式开关执行。全量和定向摘要保留执行时源码指纹，未改写全量指纹；最终AdminService/AdminConfiguration的实际注册工具交集收紧由25项定向复验及浏览器覆盖；全量后AdminShell/AdminTable的筛选与显示收尾由105项前端测试、build及最终浏览器覆盖。

最终浏览器15项通过、0 JavaScript错误，覆盖1600×1000、1366×768、390×844五页及键盘Escape/焦点归还，保存17张截图（15张页面、1张Skill对话框、1张权限拒绝）。命令为 `/private/tmp/devicetruth-admin-venv/bin/python scripts/verify_admin_browser.py`，证据为outputs/validation/platform-admin/browser/report.json；已查看最终桌面与手机截图，表格局部横滚，无整页溢出。初版12项通过；增强版补用户/审计筛选和切页后晚到401协议故障。普通权限拒绝、开户、账号状态、知识导入/启停、Skill开通/停用均走真实服务和真实隔离PG；晚到401明确为故障注入，不冒充服务实际401。

### 真实模型与数据性质

隔离完整链路为开户→可信空间grant→受控EV-0001导入→当前Skill安装→产品Run→白名单统计→停用/恢复。Run `d42f1df7-ee6b-4646-b0c4-227e09471433` 为COMPLETED，DeepSeek `deepseek-flash` 3次模型尝试、5条引用；DashScope `text-embedding-v4` 11次成功请求、0重试、1385输入token。资料为Tesla公开资料整理，sourceNature=REAL，使用依据为USER_AUTHORIZED_LOCAL_TECHNICAL_RESEARCH_IMPORT；没有真实现场数据、Holdout或专家诊断质量验收。quotaEnforcement=UNCONFIGURED；未查询供应商账单，不估算正式费用。完整报告见real/report.json。

备份恢复使用自造停用账号、审计及文件：pg_dump/pg_restore和Flyway validate成功，statusVersion=7保留，恢复后的DISABLED账号仍拒绝访问，审计和文件字节保留。未在原运行库演练。

### 失败、修复与复验历史

- 早期专项5项通过后，新增测试出现泛型断言、Timestamp限定名、long常量编译问题，均修复；编译失败不计运行通过。
- 账号代次首次8项中1失败：WAITING不能由原fail()收口，补专用revokeAuthorization转换并复验；保留first-epoch-test-failure.log。
- 首次全量610项中1失败：将原恢复周期误改为1秒导致105条积压只剩31条。拆开撤权扫描与原租约恢复扫描后原GeneralAgentApi回归及最终全量通过，原失败日志保留。
- 管理导入首次Invalid deviceModel，改用既有KnowledgeCorpus.modelKey并保留型号原文；恢复重复关联曾触发dt_knowledge_document_file主键冲突，改同关联幂等、拒绝换绑。保留first-managed-import-failure.log及first-import-recovery-failure.xml。
- 后续全量619项中1 error：V17升级测试夹具调用当前导入器缺availability列。只修KnowledgeCorpusIntegrationTest夹具直接写V17列和1024维旧向量，完整升级断言保留，没有让生产代码绕过可用性；保留pre-v17-fixture-failure.log，最终630项通过。
- 浏览器首次隐藏dialog的邮箱定位歧义，后续Skill标签/加载定位和审计select精确标签定位超时；修正测试定位，保留browser/first-browser-failure.txt与audit-filter-locator-failure.txt。失败不覆盖为成功。
- 真实外呼最初被自动审批拒绝；用户明确payload/provider后授权。首个重试因测试类未落地报“无匹配测试”，外呼0；创建后唯一实际付费烟测通过。
- 最终CLI首次在A–D和缺Key检查后被沙箱禁止本地监听中断；允许本地端口后完整复验通过，日志/tmp/devicetruth-admin-cli-final.log。

### 最终工件校验

`openspec validate add-platform-admin-console --strict --no-interactive --json` 通过（1/1，issues=[]）；`git diff --check` 通过。最终 `openspec instructions apply` 返回all_done，40/40任务完成；改动源码/文档的本地私有配置值扫描命中0，最终定向运行后源码指纹无漂移。JAR检查包含23个管理类，未包含devicetruth.local私有配置、.env或浏览器/真模测试fixture。

### 运维与未验证边界

接口见admin-api.md，真实共享签名/锁序见shared-contracts.md；初始化、配置、迁移、恢复、回退及排错见 `docs/operations/platform-admin.md`，配额见 `docs/operations/admin-quota-policy.md`。回退需保留新增状态/审计并继续执行已生效撤权，不得换回忽略状态的旧二进制。

未执行：原运行库迁移/管理员初始化与部署、正式邮件服务、完整产品多资源配额准入、专家/现场质量验收及暂停的Holdout/批量实验。当前游标密钥为进程级，多实例分页应固定同实例；跨实例共享密钥属于后续部署增强。未提交/推送、未同步归档，不把规划历史当作当前结果。

---

以下为规划历史原文，保留当时结论。

# 设计与验收记录

## 2026-11-19：规划产物

用户授权落实独立 OpenSpec、平台权限矩阵、五页结构及脱敏接口合同。本轮创建 add-platform-admin-console，包含 proposal/design、7份能力规格、权限矩阵、页面结构、管理接口合同、共享接入合同和实施任务。

基线：22e5bab7b033028c09dd6a31a8143d99e69fa384；当前迁移到V24。开始时仅有上一轮未跟踪的需求梳理文档。未改账号/团队原 change、业务代码、数据库或运行服务，未提交/推送、未使用付费 API。

设计采用：独立 PLATFORM_ADMIN；空间知识/Skill 另需显式 grant；新开户默认无平台角色且不切换管理员会话；运营 Run/尝试投影无用户正文；金额按已知币种/模型分桶；状态与成功审计一致；共享写能力未就绪时明确拒绝。

实际检查：

- openspec status --change add-platform-admin-console：proposal/design/specs/tasks 四类工件 4/4 齐全。
- openspec validate add-platform-admin-console --strict --no-interactive --json：1 个 change 校验通过，0 失败，issues=[]。
- Python 静态自检：15份 Markdown，7能力与目录一一对应，31条 Requirement、42个 Scenario，所有需求有 SHALL/MUST 及 WHEN/THEN 场景；本地链接、UTF-8、围栏/行尾格式通过。
- tasks 共40项，0已实施；任务均未勾选。42个场景是规格，尚未实际运行，不能计为42项测试通过。
- 自查补充失败导入任务分页/状态读取、受控来源选择、Skill安装URL明确内容version与管理revision、默认时间窗游标固化及未知币种分桶规则。

后续修改完成后再次执行同一严格校验；业务回归未运行。检查范围为本 change，不宣称整个仓库所有旧变更通过。

## 规划时尚未执行（历史，当前以顶部实施记录为准）

- 管理代码、迁移和五页实际实现。
- 双方快照/共享签名/锁顺序的接入确认及新迁移编号分配。
- 后端/前端/真实PG/浏览器/真实模型/邮件测试与生产部署。
- 正式权限引导、账号开户、启停、配额策略生效或运行库变更。
