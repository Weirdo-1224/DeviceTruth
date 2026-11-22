# 平台管理端运维与交付说明

`add-platform-admin-console` 已完成管理端实现与隔离验证，2026-11-22 继续整合到新版工作台。原管理端交付及本次合并的验证分别记录在 [change 验收记录](../../openspec/changes/add-platform-admin-console/acceptance.md)。本次代码合并不自动部署、认领旧数据或为原运行库初始化管理员；数据库兼容条件见下方“迁移与旧库边界”。

## 2026-11-21 本地完整数据体验环境

用户要求启动可查看全部已有管理内容的环境。当前入口 `http://127.0.0.1:5175/admin/knowledge`，product 后端 `127.0.0.1:8083`。知识/Skill 的目标空间选择“已有正式知识库（343篇）”；原“管理端功能测试空间”仍为独立空测试空间。账号为 `admin@devicetruth.local`，本地测试密码沿用会话提供值。

数据准备采用逻辑复制：原库 `pg_dump -Fc` → 冻结 V19 参考库 → 在已经由当前 Flyway 正常迁移到 V27 的独立测试库中导入原业务列。原库历史和当前迁移历史分别保留，没有 repair 或改写 V1–V24。复制 24 张表逐表摘要一致，检查 60 个外键，按原 checksum/byte_size 恢复 343 份 corpus 原文件；后续再次核对原库 24 表摘要未变。只对复制库显式建立旧知识/Skill scope 的管理空间并授予平台 grant，未给历史 Run 伪造作者或空间归属。

当前可展示 343 文档、682 原向量、343 历史入库记录、63 个资料实际使用的受控来源、8 个已部署 Skill、21 条安装记录及 36 条历史 Run。历史入库展示来自真实 corpus/file/import_item 关联，标记“已有知识入库”，不是本次网页上传；不创建对应的虚构管理操作。历史 Run 默认按最近7天筛选，查看全部本批历史可设 `2026-11-01T00:00:00+08:00` 至 `2026-11-22T00:00:00+08:00`。

复用旧向量，没有模型或 embedding 外呼；release 仍 READY/active=false，文档 STAGED，管理可见不等于产品检索已激活。配额仍 UNCONFIGURED、未知计量为 null，当前网络模型可用性未额外付费探测。启动配置使用真实模型供应商，不切换 scripted。

仅供当前本机重启：`python3 outputs/admin-preview-20261121/resume.py`（需要本机 Docker 和本地端口权限；已有后端运行时会拒绝重复启动）。它读取 state.json 中的隔离数据库，不重新导入数据；首次复制脚本受完成标记保护，不要绕过重跑。进程 PID、备份、复制清单、12 张页面截图、34 项浏览器/API 检查见 `outputs/admin-preview-20261121/`。这套逻辑复制流程不是对原运行库执行的原地升级。

## 入口与身份

前端五个入口：`/admin/users`、`/admin/runs`、`/admin/knowledge`、`/admin/skills`、`/admin/system`。Vite 开发代理或现有 nginx `try_files` 提供深链接；Java 的 `/index.html` 仍是原演示入口，不代替 Vue 管理端。

V30 起管理端使用专用 ADMIN 账号与 `/api/admin/auth/me|login|logout|csrf`，独立 DTADMINSESSION；用户端保留 `/api/auth/*` 和 DTSESSION。账号存储及 PBKDF2 底座共用，但会话域在服务器绑定，两端登录/退出分别生效。旧统一会话不再恢复管理身份。部署需同源 HTTPS、Secure/HttpOnly/SameSite Cookie，转发 Cookie 与 X-CSRF-TOKEN；不把密码或 CSRF 存入 localStorage。切换、开户与恢复见 [两端登录说明](login-realms.md)。开发联调显式接启用已配置真实模型的 product 后端，协议/故障测试才使用确定性替身。

`PLATFORM_ADMIN` 与团队 ADMIN 独立。平台管理员初始无空间 grant；管理目标空间必须单独选择，不能默认落入个人空间。管理 grant 不创建团队成员，也不授予产品调查、报告、附件或来源正文阅读权。

## 迁移与旧库边界

管理端原交付基线为 22e5bab，以下 V25–V27 是当时新增的迁移：

| 迁移 | 内容 |
| --- | --- |
| V25 | 账号状态/版本、执行账号状态代次、平台角色/空间 grant、共同管理锁、审计、Skill 管理版本 |
| V26 | 部署配额策略、持久预留与对账结构 |
| V27 | 文档可用性/管理版本、管理导入来源/actor/版本、导入执行租约及普通上传 actor 状态代次 |

先备份匹配的数据库、文件目录、知识数据包、Skill 目录和运行配置，确认实际 flyway_schema_history。隔离测试已覆盖空库及从知识库分支 V24 升级；历史本机知识 V18/V19 与新版编号不同的库不能直接启动新版或执行 repair。先制定专门兼容迁移，不能删除旧历史或隐式认领资料。

### 2026-11-22：与新版工作台合并后的编号

合并版保留知识库分支的 V23 `knowledge_corpus_profiles`、V24 `knowledge_staged_visibility` 及上述 V25–V27；工作台分支原 V23 `collaboration_round_number`、V24 `legacy_ownership_claim` 内容不变，分别移到 V28、V29。空库按 V1–V29 顺序初始化；已使用知识库分支 V23/V24 且历史兼容的库可继续正向迁移。

**已经执行过工作台原 V23/V24 的数据库不能直接启动合并版。** 必须先核对 `flyway_schema_history` 的版本与描述，并制定保留业务数据和迁移历史的兼容方案；不能删除历史、直接 `repair` 或以空库测试代替旧库升级验证。本次代码合并不自动迁移现有运行库。

只有确认库历史兼容后，product 启动才允许 Flyway 应用尚未执行的迁移。现有 CLI、legacy 和 health-only 入口不装管理安全链或自行迁移数据库。

## 可信维护入口

维护程序需要明确传入数据库环境变量，不能通过 HTTP 使用。数据库口令仅留在可信进程环境，不放在命令参数或 shell 历史。

- `DEVICETRUTH_MAINTENANCE_PG_URL`
- `DEVICETRUTH_MAINTENANCE_PG_USERNAME`
- `DEVICETRUTH_MAINTENANCE_PG_PASSWORD`

以下 UUID 是已注册账号/现有空间的占位符；执行前在可信运维环境核对对象。operatorRef 是受控运维人员引用，不能填命令、路径或自由文本。

```bash
java -Dloader.main=io.devicetruth.admin.application.AdminMaintenanceMain \
  -cp target/devicetruth-core-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  bootstrap ops-ref ACCOUNT_UUID

java -Dloader.main=io.devicetruth.admin.application.AdminMaintenanceMain \
  -cp target/devicetruth-core-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  platform ops-ref ACCOUNT_UUID enable OPS_REQUEST

java -Dloader.main=io.devicetruth.admin.application.AdminMaintenanceMain \
  -cp target/devicetruth-core-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  grant ops-ref ACCOUNT_UUID WORKSPACE_UUID KNOWLEDGE_MANAGE enable PERMISSION_CHANGE
```

空间能力为 KNOWLEDGE_READ / KNOWLEDGE_MANAGE / SKILL_READ / SKILL_MANAGE，MANAGE 包含对应 READ。撤销使用 `disable`。初始化/授予要求已有 ACTIVE 账号；撤销可针对已有停用账号。平台撤权和账号停用共用管理锁，至少保留一位有效平台管理员。所有维护动作写 MAINTENANCE 审计，网页没有扩权接口。

## 知识目录与上传

`devicetruth.admin.knowledge-root` 默认 `datasets/knowledge`，应指向已收集资料数据包，包含 `sources/source_registry.jsonl`、`reports/knowledge_v1_manifest.json`、curated 文件与对应原件。沿用 KnowledgeCorpus 的目录范围、元数据、来源原件及校验值验证；数据包不完整时 KNOWLEDGE_WRITE 不就绪，不伪造许可。

管理端仅接受清单内原始 Markdown 校验值和 sourceId 一致的资料。使用依据沿用 USER_AUTHORIZED_LOCAL_TECHNICAL_RESEARCH_IMPORT；保留 REAL/PROJECT_RULE 等原始性质，不把网页可获取等同于出版社再分发许可。未知新资料需要先进入受控收集/审核清单，不能仅选择一个已有来源绕过。

上传受原 1,000,000 字节上限约束；受理返回 202 与 fileId，异步处理结果需通过管理导入列表/状态接口确认。异步处理在每次 embedding 前与最终提交时复核 actor 状态代次、管理范围、来源和导入租约；数据库锁不跨付费外呼。受理与处理成功分别审计，最终文档/文件成功状态与处理成功审计同事务。失败保留 fileId；重试校验管理 revision，不能重复 enqueue。

导入租约 60 秒、15 秒续租；管理导入恢复扫描每 10 秒检查过期任务，同一安装实例内去重、跨实例通过 PG 租约争抢。租约丢失或授权撤销后不采纳迟到结果。普通 product 上传也保存真实上传者和账号状态代次，停用后恢复不会让旧上传继续提交。

停用文档只阻止新检索、附件关联和新引用/继承，历史已登记证据按原产品权限保留。状态变更与新引用共用原 KnowledgeVisibilityGuard 的 scope 锁，管理侧取 exclusive、登记取 shared。重复上传、重试、崩溃恢复不重置可用性。恢复需处理成功、来源有效、明确空间知识/发布资格及 release 条件，不自动激活 release。

后台仅返回知识元数据、章节索引和导入状态，不提供正文或原文件下载。未发布调查附件排除在管理空间库与空间检索之外；原产品 change 的调查附件 UI/发布流程仍由其自身任务交付。

## 账号、Skill 与统计

账号开户调用原 AccountService，同事务创建账号/个人空间/成员及成功审计，不调用注册 Controller 的 signIn。新账号无平台角色，管理员原会话保持不变。停用原子撤销全部 Session 和待消费重置凭证；HTTP、空间、Run 领取/模型返回/工具/结果/恢复、普通上传及 SSE 持续复核。恢复不恢复旧 Session，不重放终止 Run。

执行 actor 固定到授权时的账号状态版本，防止停用后立即恢复让旧 Run 复活。撤权扫描每秒执行，与原租约恢复扫描分开；SSE 每批/每条/终态/最长 15 秒心跳前复核。产品页退出仅当前会话，不冒充取消持久 Run；其他有效团队成员保留原空间访问。

Skill 目录来自实际加载 Registry；管理页只能开通当前版本，工具权限取服务器/声明/安装交集。选择持安装记录共享锁，停用持该记录排他锁后检查 QUEUED/RUNNING/WAITING 的已选版本；有未决 Run 返回最多 20 个范围内 ID，不代用户取消。默认安装不覆盖停用或缩减工具集。

管理运行查询只 SELECT 独立白名单列，不复用产品 RunView、原始事件或答案。用量是已报告小计；历史未知 actor、token、币种不回填。现有成本缺可信币种时只计未知币种记录，不默认人民币/美元，不作正式账单。多人补答缺可靠执行段尝试归属时显示 INCOMPLETE。

## 配额边界

用户已定案：产品同键提交继续返回 409 DUPLICATE_REQUEST。完整产品准入接线留给依赖 change，本轮不改变此合同，不启用多资源执行配额。独立 AdminQuotaLedger 已验证跨实例原子多维预留、同键复用、异载荷冲突、幂等释放与 WAITING/终态对账；这些测试不代表产品已受保护。

[试运行策略](admin-quota-policy.md)覆盖账号/空间/平台三维。可信维护命令可安装策略，但不会打开缺失适配器：

```bash
java -Dloader.main=io.devicetruth.admin.application.AdminMaintenanceMain \
  -cp target/devicetruth-core-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  quota-pilot ops-ref OPS_REQUEST
```

未安装为 UNCONFIGURED；安装后为 PENDING_INTEGRATION。未知已用、占用、剩余保持 null，不把空账本当作零消耗或已对账。上传、导入、检索、Embedding、SSE、存储与执行段的统一准入/释放/计量须在依赖 change 接齐后再开放。HTTP 没有额度编辑或支付接口。

## 备份、恢复、回退和排错

备份应使用 pg_dump 自定义格式，配套保存受控文件目录、知识数据包及 Skill 版本。先在新的隔离库 pg_restore，用相同迁移版本 validate，再校验账号状态/版本、角色/grant、审计、文件 hash、Run/引用和安装状态。不能仅检查健康探针。`AdminBackupRestoreTest` 已用自造资料演练，并确认恢复后 DISABLED 账号仍被当前服务拒绝。

回退可关闭前端管理入口，保留新增表列及审计；后端必须继续执行已生效账号撤权、状态代次和知识停用。不能回到忽略这些字段的旧二进制。不得清空配额账本后重放工作，或通过重装默认 Skill 恢复停用许可。

| 现象 | 定位 / 处理 |
| --- | --- |
| 401 / ACCOUNT_DISABLED | 重新核对当前账号与会话；恢复后重新登录，不复用旧 Cookie |
| 403 PLATFORM_ADMIN_REQUIRED | 用可信入口核对独立平台角色，团队 ADMIN 无效 |
| 空间 404 | 核对显式 workspaceId、grant 和 X-Workspace-ID 是否冲突，不扩大产品成员范围 |
| STATE_VERSION_CONFLICT | 回读最新状态，不用新版本盲重试原动作 |
| ADMIN_AUDIT_UNAVAILABLE | 业务已回滚；恢复审计写入后重新定位目标和版本，错误中无底层异常 |
| 导入 FAILED / 授权撤销 | 用原 fileId 定位；恢复合法授权后显式版本化重试，不改来源或提升许可 |
| SKILL_HAS_UNRESOLVED_RUNS | 用返回的有限 ID 联系原有授权执行者，通过产品权限完成/取消任务 |
| 健康 UNKNOWN | 没有足够证据；页内查询不发付费模型请求，近期成功也不保证当前连通 |
| INVALID_CURSOR | 账号、权限、筛选、进程密钥或 30 分钟有效期已变；清游标刷新。多实例应固定页请求到同实例 |

所有管理响应带 X-Request-ID；错误仅返回稳定 code/requestId/允许的冲突字段。审计不保存请求体、密码、令牌、用户调查、模型正文、路径、SQL 或供应商原始异常。
