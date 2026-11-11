# 新会话接续说明

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
