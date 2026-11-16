> **2026-11-14 最新：工作手册Skill规范化已完成。** 当前八包：汽车/工厂综合均1.0.1，充电0.2.4、维护0.2.2、供电0.2.3、工业配置0.2.3、控制器故障0.4.3、Carrier0.1.2。全部九键元数据、五类标准参考+补充矩阵，共82篇，默认版本与实际PG授权一致；旧CLI规则不变。先读 `docs/skill-normalization.md`、`docs/skill-rule-sources.csv`；生成源是 `scripts/adapt_domain_skills.py`/`skill_standards.py`，不要用旧版本脚本覆盖当前包。可用JDK21执行 `java -cp target/classes scripts/ValidateSkills.java`，直接使用实际产品解析器。43原件/24规则来源摘要一致、5类负例拒绝、副本重建一致；定向27通过，全量394（391通过/3可选真实模型项跳过），真实模型首批5/6（供电格式错误）+一次复验1/1，最新逐例6/6、66检查，原失败保留于 `outputs/validation/skill-normalization-20261114/online-initial-batch/`。没有真实现场、RAG导入或增益A/B验收。两个旧Carrier等待Run来自自动验证，保留旧0.1.1绑定；旧版本任务不能直接迁移摘要，验证请新建会话。服务运行target/classes，PID见outputs/logs/local-demo/processes.json。知识库仍空且缺embedding密钥，Phase5原两项UAT仍待确认，未commit/push。下段为首次适配历史。

> **2026-11-14 首次 Skill 适配历史：** 用户授权的六个桌面领域原包已适配并在本机product启用：充电0.2.3、维护0.2.1、供电/电池0.2.2、工业配置0.2.2、控制器故障0.4.2、Carrier0.1.1。原有两包未改，注册目录8包/41篇参考，43份原件校验一致。先读 `docs/domain-skills.md` 与 `docs/domain-skill-sources.json`。全量Maven394项，391通过/3可选真实模型项跳过；在线最后整批4/6，两条模型协议格式失败已保留在round4，并一次针对性复验2/2，最新逐例6/6、66检查通过，不能写成单批6/6或已解决所有模型格式波动。证据 `outputs/validation/domain-skills-20261114/`。源码仅额外修复AnswerGuard的充电症状/登记事实误判并加正反回归；服务运行的是target/classes，后端PID见 `outputs/logs/local-demo/processes.json`，不要用旧jar覆盖。当前知识库空且embedding密钥缺失，下一步可独立建设知识库；设备业务表不需重新导入。Phase5原两项UAT保持待确认，不commit/push。

> 2026-11-13 latest: 03-01 PASS; native advance-plan 1 -> 2 succeeded; next 03-02 NOT EXECUTED. Final tests 278/0/0/0, package/CLI PASS. No commit/push. Older pending statements below are historical.

> 2026-11-13 最新接续：**05-01 已完成并技术验收**（授权范围仅 05-01；05-02/05-03 未授权，须等用户明示）。主代理定契约 `.planning/phases/05-workbench-quality/05-01-CONTRACT.md`，三个子代理并行（A 后端 SSE / B Vue 时间线 / C 集成与浏览器验收），主代理整合。实测：全量 350/0/0/1、前端 28/28+build、打包、verify_cli 通过；`AgentStreamTest` 7/7；浏览器三场景 PASS（证据 `outputs/validation/sse-browser-7e894c22cc0a/`）。**代码与验收记录未提交，留在工作区供审阅**。主代理修复了两处前端接线（终态 run 历史回读、视图不完整自动重跟）与浏览器脚本两处时序坑（SSE 代理须逐块转发；断连前须等浏览器收到带 id 事件）。未验证：真实模型长耗时流、订阅压测、真实认证（05-03）。先读 [05-01-SUMMARY](phases/05-workbench-quality/05-01-SUMMARY.md) 与 [05-01-CONTRACT](phases/05-workbench-quality/05-01-CONTRACT.md)。

> 2026-11-14 最新接续：**Phase 5 三个计划全部完成并实测，阶段未关闭，等两项集中人工确认**。05-01（SSE/历史/时间线，含审查六项+追加六项修复）：全量 `mvn -o test` 360/0/0/1、前端 36/36+build、浏览器 A–E 5/5、verify_cli 通过。05-02：工作台（DiagnosisReport/EvidenceComparison/取消/Skill）+ 安全四项 + PDF 明确状态 + 14 例 E2E（离线 PASS6/覆盖4/未跑4；在线 PASS9/覆盖4/FAIL1=E2E-07 模型首轮未追问）。05-03：Dockerfile/compose/nginx/operations.md + **演练 PASS**（`outputs/validation/deployment-20261114014426`：启动→链路→重启持久→备份恢复 6 表行数一致→迁移失败阻断→forward-fix）。UAT 待确认：① 多人共享是否接真实认证；② E2E-07「首轮必须追问」是否硬约束。文档见 05-ACCEPTANCE/05-VERIFICATION/05-VALIDATION/05-UAT/ACCEPTANCE-RESULTS。全部改动未提交，留在工作区。

# 新会话接续说明

> **2026-11-14 数据建设接续：** 当前本机数据库为 `devicetruth-postgres-1` / `devicetruth`，端口54329。用户已授权并完成五张设备业务表各≥100条：166/709/1035/175/1063；同批次复跑新增0，30项PG/API/scripted产品检查全部通过。先读 `docs/data-enrichment.md`。脚本为 `scripts/enrich_device_data.py` 和 `scripts/verify_enriched_data.py`，逐行来源、全量输入盘点、排除项和备份在 `outputs/validation/domain-enrichment-20261114/`。公开资料是登记/工单文档快照，模拟资料仍为SIMULATED，原始桌面资料未改。下一步围绕这些资产建设匹配的知识库及场景Skill；当前未执行知识导入或新Skill安装。Phase5两项原UAT决定不受本次数据任务替代，未commit/push。

> **2026-11-13 最新接续：** Phase 3 总时限与用量修复、Chrome 恢复及在线多轮均已实测。Phase 4 两份计划完成，离线与真实 DeepSeek / DashScope 双域样本通过（保留原始报告和措辞复核）。原生阶段出口已通过，Phase 5 尚未开始。最近全量 343 项，342 通过、1 跳过。先读 [03-ACCEPTANCE](phases/03-durable-investigation/03-ACCEPTANCE.md) 与 [04-ACCEPTANCE](phases/04-skills-dual-domain/04-ACCEPTANCE.md) 的补验段。

> **2026-11-13 最新接续：** 03-01（设备结构化记录与查询）已实施并自测完成，处于 REVIEW_PENDING：V8 资产四表 + V9 引用外键修正、AssetTools 三工具进产品网关、AssetController 只读 API、ResourceAuthorizationTest 8 项双身份负例。AssetToolTest 16/16、三轮全量回归 269/0/0、打包与 CLI 通过。关键背景：① 资产引用写入曾被 `dt_evidence_reference.chunk_id` 外键静默吞掉（表象是"时点过滤全空"，时区理论已被对照实验证伪）；② 推理器来源已改为 显式属性>环境变量>私有文件，**不要**再把"全量回归偶发超时"归因为 PG 争抢。全部未 commit、未标 DONE、未推进 ROADMAP；下一动作是独立 Review 03-01。先读 [03-01-SUMMARY](phases/03-durable-investigation/03-01-SUMMARY.md)。

> **2026-11-12 最新接续：** 用户明确要求Phase 2带遗留项关闭；GSD原生UAT门禁与phase complete已成功，现进入Phase 03，5/13计划、2/5阶段。正文安全3项P1/1项P2、PDF/OCR/页码和浏览器端到端验收转Phase 5质量交付前处理；相关需求保持部分或未完成。Phase 03尚未实施；勿因关闭决定把遗留技术项标为通过。GSD原生progress json显示38%，state update-progress错误显示20%，两者原样记录。

> 下一计划03-01的历史文件清单仍写 `V3__asset_history.sql`，但当前迁移已经使用V3至V7。开始03-01时须按已有迁移现状调整计划和采用未占用的新版本，不能覆盖旧迁移；其他任务边界及权限、时点、事务要求按当前源码复核。

> **最新接续（2026-11-11，02-03 独立Review修复后待复审）：** 用户授权修复02-03独立Review的3项P1与1项P2，保留全部既有工作区修改且不commit/push。补答附件现按消息以结构化元数据进入Agent上下文；V7区分上传文件与文档版本，兼容旧多版本文件记录，重传保存真实字节并建立关联；验收脚本纳入全部负例、答案引用/章节原文/工具事件及无资产Guard判定，并公开白名单`GET /api/runs/{id}/tool-events`供验收。真实PG全量40 suites 241/0/0/0、Python判定10/10、前端15/15及构建、打包、CLI、确定性固定样本通过。三个真实供应商命令均因缺embedding key退出2/BLOCKED_BY_ENV；PDF与浏览器NOT_RUN。02-03未提交、未标DONE、未推进ROADMAP，必须继续由独立Review判断；02-02既有3项P1和1项P2仍未关闭。下方均为历史记录。

> **最新接续（2026-11-11，02-02 带已知问题通过）：** 用户明确要求发现问题记录后直接通过。独立复审记录3项P1与1项P2，均未修复，见 [02-02-REVIEW](phases/02-open-rag/02-02-REVIEW.md)；不代表安全边界验证通过。真实PG全量229/0/0/0、前端12/12及构建实际通过。SUMMARY为complete/passed_with_known_issues；GSD原生advance-plan已推进至02-03，Phase2为2/3，整体4/13。02-03仍未授权、未执行，Phase2未关闭；不自动commit/push。下方待复审等为历史记录。

> **最新接续（2026-11-11，02-02 实施完成 + 审查修复轮待复审）：** 02-01 已于本轮先前提交（`e9aed33`）。02-02 已按用户授权实施：通用协议（CALL_TOOL/ASK_USER/ANSWER）、`TaskOrchestrator.runAgent` 唯一主循环、`CapabilityGateway` 工具注册/执行、`AnswerGuard`、`V5 dt_message.run_id`、有界 worker 状态机、`replyTo` 三元组 API、`/api/runs/{id}/references`、最小 Vue3/TS/Vite 页面。**独立审查提出的 P1×5（无核验仍可确诊、危险建议绕过、混合资料标错 AUTHORIZED、M0 遗留队列未交接、并发写入卡 RUNNING）与 P2×2（claim 独有引用丢失、页面只显示“执行失败”）已全部修复并补测试**：`AnswerGuard` 增加服务器 `fieldVerified`（M1 恒 false，确诊一律 `UNCONFIRMED_FINDING`）、逐句危险动作判定、dataNature 全授权才 AUTHORIZED、引用取并集；`recoverQueued` 启动接手 QUEUED；会话行锁下沉到 `append` 并把落库失败置 `PERSIST_FAILURE`；`RunView.failureCode` + 前端可读说明。实际执行：`mvn -o test` 215/0/0/0（35 suites；Flow 14、Api 6、AnswerGuard 10）、打包、`verify_cli.py` 全通过；既有 `devicetruth` 库 V4→V5 升级后独立 HTTP 进程两轮探针仍 COMPLETED（错版本 409、runs=1）；前端 `npm ci`、12/12 单测、`vue-tsc`+`vite build` 通过。**未验证：真实模型/真实 embedding、浏览器人工演示 E2E-01/02/03 与 E2E-07-basic、PDF/混合检索、RUNNING 跨进程恢复（M2）。** 当前 `status: blocked`、`review_status: pending`，未标记 PLAN 完成、未改 ROADMAP 阶段态、未 commit/push。先读 [02-02-SUMMARY](phases/02-open-rag/02-02-SUMMARY.md) 的“审查修复轮”表。02-03 仍未授权。

> **最新接续（2026-11-11，02-01独立复审通过）：** 原P1（含V3历史向量污染）及两项P2全部关闭。独立实际运行185/0/0/0（35 suites）、打包及CLI通过；额外已有V3库升级V4探针9断言通过，迁移前引用仍可读，重建后再次启动不会重新隔离。SUMMARY complete/review passed；原生advance-plan实际推进到2，Phase2完成1/3、整体3/13，阶段保持进行中。02-02/02-03未获授权，不执行；未commit/push。先读[02-REVIEW](phases/02-open-rag/02-REVIEW.md)、[02-ACCEPTANCE](phases/02-open-rag/02-ACCEPTANCE.md)、[02-VALIDATION](phases/02-open-rag/02-VALIDATION.md)。STORE-03/KNOW-03/KNOW-06仍部分实现；真实embedding/模型、PDF/混合、Agent/Vue/真实文件存储等未验证。下方blocked、自测与旧HEAD内容均保留为历史，不覆盖本条。

> **最新接续（2026-11-11，02-01审核修复轮）：** 独立Review结论P0 0/P1 1/P2 2，仅P1阻止通过，三项均已修复并自测：embedding档案改为`provider:model@dimension-cosine-r<revision>`（离线强制`deterministic-char2`、复用前核验、检索同时核对model/维度），复审P1后新增`V4`把修复前向量统一隔离为`NEEDS_REBUILD`（不信任任何历史标签、保留正文与引用、用新版本重建），切片按`maxChunkChars`码点切分并计入换行，SUMMARY改`status: blocked`使GSD恢复2/13。实际执行`mvn -o test`185/0/0/0（原154+31）、`mvn -o -DskipTests package`成功，`KnowledgeSearchTest`13、`KnowledgeChunkerTest`5、`EmbeddingClientTest`6、`KnowledgeConfigurationTest`6、`KnowledgeUpgradeMigrationTest`1 全通过，CLI全通过；打包JAR在既有库应用V4后：旧版重导409、重建2026.2可检索、隔离前引用可读；探针复现provider切换/换档案/错维度均409、切片上限80实测最长80。当前`status: blocked`、`review_status: changes_requested`：未标记PLAN/阶段DONE、未改ROADMAP、未commit/push。真实千问调用与向量召回、PDF/混合检索、工具接入Runtime/Agent/Vue、真实文件存储与规模性能均NOT_RUN。详见[02-01-SUMMARY](phases/02-open-rag/02-01-SUMMARY.md)。下方记录保留为历史事实。

> **Phase1最终出口（2026-11-10）：** 用户集中回复“上述产品边界与设计符合预期”已保存；UAT6/6、独立Review clean、安全6/6/0open、VERIFICATION passed（6/6）。原生phase uat-passed 1 --require-verification通过（无阻断/无指纹不确定），phase complete 1已关闭M0并进入02。下方“待集中确认/未关闭”等为历史。

> **最新策略（2026-11-10，覆盖下方旧逐项流程）：** 用户要求技术测试、A–E场景、CLI/状态/安全全部由代理实际执行，仅产品/业务/设计等集中一次人工确认。已核对GSD1.20.1原生coverage及分类器并落实[ACCEPTANCE](ACCEPTANCE.md)和AGENTS规则，未改全局GSD/config开关；SUMMARY原生分类4自动/1人工，5个分类正负探针通过。策略调整后重新实际package仍154/0/0/0，打包双进程PG重启再次通过。用户第1项明确回复“第1项通过”，已保存在01-UAT；原技术项转为代理自动记录，不再提示用户重复操作。先读01-ACCEPTANCE、01-UAT及当前VERIFICATION，只剩第3项一次产品/设计集中确认。出口满足后提交Git并仅执行02-01的授权不变；02-02/02-03不执行。下方“第1项尚未确认/逐项提示”等内容为先前历史。

> **最新接续（2026-11-10，本轮验收）：** 用户已授权Phase 1逐项用户验收、独立Review与修复复验；出口全部满足后提交当前Git并仅执行02-01，明确不执行02-02/02-03。独立代理初审发现CR-01（HTTP JSON宽松转换），真实PG复现后复用Json.MAPPER.copy()修复并独立复审clean。clean test/package均154/0/0/0（PG8/旧HTTP16），CLI/新JAR两次独立进程原文与同run QUEUED读回/Compose均通过。原业务库public表17、随机测试库0，未拉镜像。
>
> 先读[01-UAT](phases/01-baseline-contracts/01-UAT.md)、[01-REVIEW](phases/01-baseline-contracts/01-REVIEW.md)、[01-VALIDATION](phases/01-baseline-contracts/01-VALIDATION.md)。逐项用户确认尚未完成：第1项用户碰到代理clean临时移除JAR（且两命令合并），已重新打包并提供显式JDK21单行命令重试；没有用户通过确认，不能记PASS。默认直接java实测17，Maven为21，后续手工命令显式用`.tools/jdk-21.0.13.0.2+1/bin/java.exe`，验收期间不再clean。Git未提交，Phase 1未关闭，02-01未执行。下方旧授权/Review待办为历史，不覆盖本段。

> 2026-11-10补验：`gsd-verify-work phase 1`首次被GSD门禁阻断，原因是COVERAGE中文表头未被解析。现已新增规范`capability / decision / reason`矩阵，重跑门禁通过（38项，21 INTEGRATE／17 OPT-OUT，block=false）。文档检查通过，109份业务文件未变；此次未重跑Maven／PG／CLI／UI。用户验收尚未开始，未创建UAT，独立Review仍待进行；继续时从第一项用户验收开始，不重复修复该格式问题。

> 2026-11-10：用户要求的[Phase 01 COVERAGE](phases/01-baseline-contracts/COVERAGE.md)已补齐并通过覆盖／源码证据／链接／范围检查，针对性回归51/0/0/0。下一步可逐项用户验收；独立Review和阶段出口仍待完成。本次只改文档，未执行后续PLAN，未提交Git。下方2026-11-09实施记录保留为历史事实，不代表本次重跑PG／打包。

> 2026-11-09，01-01/01-02已完成；M0独立Review待进行，02-01及后续计划未执行/未授权。用户明确指定复用interview-postgres，不再下载另一套PG。
> 本轮未commit/push/部署；开始时已有上一轮6份规划修改，均保留。当前工作树含01-02实现，不可覆盖或回滚。

## 必须先读

1. 根AGENTS.md、.planning/STATE.md、PROJECT.md。
2. 指定V1.0 PostgreSQL设计（hash仍为C80A4C9292787B181DDB2406CAE4746B83F602993B22EAAA8C2C42AF5D698120）。
3. [COVERAGE](phases/01-baseline-contracts/COVERAGE.md)、[01-02-SUMMARY](phases/01-baseline-contracts/01-02-SUMMARY.md)、根README的数据库/测试配置、[CONTRACTS](phases/01-baseline-contracts/CONTRACTS.md)、[ADR-runtime](phases/01-baseline-contracts/ADR-runtime.md)。01-01基线与原型见其SUMMARY/RUNTIME-SPIKE。
4. REQUIREMENTS/ROADMAP、目标PLAN和前置真实完成记录；旧spec/task/state仅理解legacy。

## 最新事实

- 当前HEAD仍为dbb8b2e567db68a1b8d78a07bbd3b7a03c245bb0；01-02实现与规划修改在工作树，未提交。当前主源码57文件、测试30文件；全量test/package均153/0/0/0（30 suites），新增真实PG专项7/0/0/0。
- 用户指定的interview-postgres已启动，PG16.15/pgvector0.8.7，端口5432；仅新建独立devicetruth库，没有在interview_guide业务表执行DDL/DML。该库public表只读基线17张。每轮随机devicetruth_test_*测试库已清理。
- 新JAR在product模式真实跨Java进程重启，原文及同run QUEUED读回一致；health通过、Java进程已关闭。PG容器按用户指定保留运行。旧CLI注入不可连接DB且启用product仍能完成B，verify_cli全通过。
- 两候选6样例×2，连续两次clean成功且输出完全相同。选演进现有TaskOrchestrator，继续Model API，SDK候选仅测试，不引入生产双栈。
- cases.json与比较测试保留在src/test；target/spikes只是输出，可clean重建。所有原始日志在忽略的outputs/validation。
- 当前已有PG消息/QUEUED持久化；仍无Agent worker/RAG/标准Skill/Vue/SSE/WAITING续跑或执行恢复。新JAR重启读到QUEUED不等于已经具备任务调度/崩溃恢复。
- 契约固定原文必填/资产可空、服务器owner/project与权限、三动作、引用/风险、runId/pendingQuestionId/questionVersion补答关联、M1/M2界限。
- 旧模型私有文件兼容保留，新PG/embedding只服务端环境注入；本轮只在内存使用已授权PG凭据，不打印/保存真实值，不读取真实模型私有文件。
- 独立Review未进行；M0计划2/2完成、阶段尚未关闭；整体2/13计划、6/37任务、0/5阶段。63需求主映射不变，WEB-06/TRUST-06仍部分，STORE-01/02/05在M0边界内实现。
- 最初选择PG17拉取失败是环境准备失误，用户纠正后已停止拉取并使用既有实例；Testcontainers依赖已移除。不要重启此前下载/改全局Docker配置；当前配置的备用Compose只在standalone-db显式profile启用且pull_policy=never。

## 如何继续

先以COVERAGE中的API／SDK边界及证据作为逐项用户验收依据；覆盖文档检查已通过，用户确认尚未记录。M0独立Review仍需完成并按结果修正，再核对阶段出口。下一份开发计划为02-01，但没有实施授权；不因两个M0计划或覆盖文档检查完成就自动续跑。

本次JDK21.0.13.0.2/Maven3.9.9，仍先核实实际版本，不更改全局执行策略。Docker可用管道为npipe:////./pipe/dockerDesktopLinuxEngine；权限需要时用正常工具审批，不误判“未运行”。真实PG测试要按README提供DEVICETRUTH_TEST_PG_BASE_URL/USERNAME/PASSWORD环境，只创建并清理自身随机测试库；缺环境不能跳过冒充通过。不打印容器完整Env或数据库密码。

.planning/validate_planning.py保留历史“仅规划”断言（未授权/0 SUMMARY/业务无新增），实施后不适用，不用它的结果判断本轮开发成败。按GSD结构/一致性、需求映射/链接/源码保护和真实Maven/CLI/PG测试分别核验，历史证据文件不覆盖。

每次完成后用通俗中文结合充电或配置冲突解释真实输入→服务→主循环→网关→引用/风险→结果，列出实际命令、结果与未验证项。独立Review和用户反馈按实际记录，不自动commit或推送。
