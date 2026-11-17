# Tasks

主体方案已通过人工审核；本轮仅修订规划，所有实施框保持未完成。P0 共 14 项、约 36 小时，P1 共 2 项、最多 6 小时，另留约 6 小时故障余量。时间为工程估算，不是完成承诺。保留任务编号便于追踪，执行以显式依赖及时间窗口为准，不按章节串行：D1 确认合同后立即启动前端；1.2 双域资料准备并行推进；6.1–6.2 QUEUED 修复于 D2–D3 与前端并行。P1 不阻塞 P0 页面，未选择的 P1 保持未完成并明确延期，不能为了归档勾选。

## 1. P0 — 启动基线与并行资料准备（1.1：D1；1.2：D1–D6，4 小时）

- [x] 1.1 D1 确认 API 合同及 JDK21/Maven、product 进程、PG/Flyway 运行环境，读取至少一份系统实际持久化的 Run（含状态和可读结果/引用合同）；复用 README 启动流程、ProductConfiguration 和现有 API，在现有验收记录旁保留本变更 acceptance.md（后续实施时创建）。验收：`mvn -version`、受权会话/Run API 回读及合同核对成功；记明样本模型与数据性质，不要求此时完成双域资料、真模或真实 embedding 验收，健康探针不代替 Run 回读。合同确认后 ViewModel 可立即启动，不等待本项其余核验结束。（2 小时）
- [x] 1.2 与前端并行核验真实模型/embedding 配置，并使用现有 `/api/files`/知识及资产导入方式准备工业 SN-0245、汽车 EV-DEMO-004 的演示知识库资料（项目自制演示手册，入库渠道为 owner 授权，文件自身保留 SIMULATED 演示标注），核对 Skill、RAG 命中、引用定位及模拟观测标记；不建设新导入器。验收：D4 前至少一域完成一条真实模型结合演示知识库的端到端调查，记录 RUN_MODEL、实际工具/Skill 和引用；D6 前双域资料准备就绪并交 7.1 验收。缺真实 embedding 单列未验证，不记录密钥。口径修订（2026-11-17 用户确认）：比赛接受演示知识库口径，真实厂商授权资料不再作为本变更验收条件，演示资料不得冒充真实资料对外宣称。（2 小时）

完成条件：D1 只以 API 合同、运行环境及一份可读真实持久 Run 作为启动基线；“真实 Run”指系统实际记录，不自动表示真实模型或真实现场。1.2 不阻塞第 2–6 组开发，分别按 D4 前单域真模/真实资料及 D6 双域里程碑记录。acceptance.md 分列真实 PG、真模、真实资料、embedding、模拟观测与协议替身；缺环境如实记阻塞，不勾选未执行项。复用现有测试，不新建环境探测框架。

## 2. P0 — 页面骨架与结果投影（仅依赖 1.1 合同/可读样本，不依赖 1.2，D1–D2，6 小时）

- [x] 2.1 在 1.1 合同确认后立即新增 `frontend/src/diagnosisViewModel.ts`，按 design 映射 category/claims/references/风险/本轮建议，修正 `DiagnosisReport.vue` 仅按 certainty 分类的语义；补最小 Vitest 映射回归。验收：覆盖 POSSIBLE_CAUSES+SUPPORTED、KNOWLEDGE+POSSIBLE、空 claims、缺引用及模拟/授权混合，不生成概率、已执行检查或判断级建议关联；`npm run test -- --run` 通过，测试样本不计入真模/真实资料验收。（3 小时）
- [x] 2.2 在 App 接入拟新增 `ProfessionalDiagnosisPage.vue`，组合顶部任务、判断/详情、底部协作区，沿用 workbench.css/design.css、UiIcon 和已有会话入口；不增加路由框架或全局 store。验收：用 1.1 的实际 Run 渲染任务/设备/风险/摘要，不等待双域资料；没有候选原因时安全展示实际结果并保留普通文字继续调查入口，不伪造候选；`npm run build` 通过，浏览器专业模式单页可用，无个人模式与关系图假入口。（3 小时）

完成条件：真实持久结果驱动页面，允许用已有样本开发并明确数据性质；新组件名称为拟新增，不误记为现有实现。保留已通过人工审核的布局及专业交互式诊断边界，不新增跨 Run 判断实体匹配、自动结论冲突合并、完整动态推理图谱、CanvasComposer、Case 表、Vue Flow 或新 Agent 协议。

## 3. P0 — 判断聚焦与证据详情（依赖 2，D2–D3，5 小时）

- [x] 3.1 新增/组合 `ClaimList.vue`、`ClaimDetail.vue`，接入局部选中索引及真实引用连接，显示本轮建议、不确定点、限制。验收：选择两条判断只切 UI、无 POST 或模型请求；类别标题正确，无引用有说明；在现有前端测试体系补最小选择/引用关联回归并通过。（3 小时）
- [x] 3.2 抽取已有来源显示到拟新增 `EvidenceDrawer.vue`，复用 readReferences 为无答案的失败/运行轮加载已登记资料。验收：抽屉显示真实 title/sourceType/sourceId/版本/章节/摘录/dataNature，可关闭；缺页码不伪造，用户自述不显示为核验事实，失败轮仍能看已取回证据；完成浏览器核对并记录，构建通过。（2 小时）

完成条件：`professional-diagnosis` 场景可验证；EvidenceComparison 只作资产快照详情，不拼接成虚构的答案引用或冲突关系。

## 4. P0 — 状态同步与历史恢复（依赖 3，D3，5 小时）

- [x] 4.1 复用 App 的跟踪/取消、timeline.ts、RunTimeline.vue 和 api.ts 轮询，接入新页面；流收尾回读 Run/消息/引用，所有异步响应按会话/Run 隔离。验收：QUEUED/RUNNING/WAITING/完成/失败/取消区别明确，断流不误判失败，重复 seq 不重复，旧事件不串页；扩充现有 `api.test.ts` 的相关行为回归，复用 `AgentStreamTest`、`AgentLifecycleTest` 通过。（3 小时）
- [x] 4.2 App 保留 readConversation 返回的全部 runs，新增轻量轮次选择，刷新默认最新且可回看旧轮；复用本地会话定位和服务器列表，避免 refresh 强制覆盖主动选择。验收：两轮完成+一轮执行中刷新可恢复，等待问题恢复，断流/本地定位失效可重新同步；扩展 `scripts/verify_browser_recovery.py` 现有流程中必要的专业页选择器/断言并实跑，记录限制。（2 小时）

完成条件：`investigation-recovery` 的页面状态、失败及刷新场景通过；不重写 SSE 或轮询框架。每组保留实际命令和结果，浏览器验证不可用时不写通过。

## 5. P0 — 深入调查与补答往返（依赖 4，D4，5 小时）

- [x] 5.1 在 `api.ts` 兼容扩展 sendMessage 的 contextRefs；专业页显式提交选中判断原文、原设备与合法引用，防重复并复用同次请求幂等键。验收：D4 浏览器实测选择已有判断、深入调查返回不同 runId、正确继承授权引用、新结果展示且旧结果可回看；核对请求 contextRefs 及新 Run 重新登记的引用关联，选择动作零提交。`api.test.ts` 覆盖无 replyTo、schemaVersion、UUID 请求键、去重且最多10条/省略提示；复用 ResourceAuthorizationTest 并补必要的新引用 ID 断言。（3 小时）
- [x] 5.2 将普通现场补充与 ASK_USER 表单分流到 sendMessage/replyToQuestion；执行中保留草稿且禁并发提交，过期补答保留输入并回读。验收：D4 实测用户补充现场信息返回新 runId；实际触发 WAITING_FOR_USER 后完整三元组补答恢复同一 runId，Run 数量不增加；用既有 AgentLifecycleTest/AgentStreamTest 及前端请求回归验证问题版本冲突不会转成新消息。另实测无候选原因时普通文字仍可开启后续 Run，界面不伪造候选并明确用户报告语义。（2 小时）

完成条件：D4 必须保留六项实际执行证据：选择已有判断、深入调查创建新 Run、正确继承授权引用、新结果展示且旧结果回看、用户补充信息开启后续 Run、ASK_USER 补答恢复同一 Run。不得仅凭代码走读或测试计划标为通过；此前 D4 前至少一条真模+真实授权资料端到端调查也必须完成。若真模未给候选原因，可选择已有一般判断；无 claims 时安全展示并走普通文字入口，选择/继承分支另用已有合法持久结果实测。若真模未发 ASK_USER，使用既有协议替身实际触发等待并验证补答，记录为协议路径，不冒充真模验收。D4 缺任一核心证据立即停止视觉打磨和所有 P1；仍需完成 `investigation-continuation` 其他边界场景回归。

## 6. P0 — QUEUED 自动恢复最小修复（仅依赖 1.1 运行环境；D2–D3 与前端并行，5 小时）

- [ ] 6.1 D2–D3 修改 `application/agent/GeneralAgentService.recoverQueued/dispatch`、`RunRecoveryService` 及 `adapter/persistence/AgentRunRepository` 必要查询，加入有界周期 QUEUED 补派、进程内待调度去重和异常后继续扫描；复用 version CAS/租约/超时，不新增消息队列、状态机或运行时框架，不等待 1.2 双域资料。验收：RunRecoveryTest 增补一次临时领取失败后无需重启恢复、worker关闭不调度、扫描异常后下一周期继续；不改变现有启动全量对账合同。（3 小时）
- [ ] 6.2 补齐同测试类的重复扫描/多服务抢占、有界扫描及预算不重置回归，更新本变更验收记录和可靠性说明。验收：`mvn -q '-Dtest=RunRecoveryTest,AgentLifecycleTest,AgentStreamTest' test` 在真实测试 PG 上通过；重复补派只有一次有效领取，WAITING/终态/有效租约不被误执行，过期 RUNNING 恢复原回归继续通过。（2 小时）

完成条件：D3 完成 Review P1-04 的 P0 修复及故障注入回归，为 D4 实测提供稳定基础；失败码、租约与预算语义保持既有合同。不能用仅调用启动 recoverQueued 的测试代替周期恢复验收；该组在第 2–4 组前端开发期间并行执行，不等到 D5。

## 7. P0 — 双域集成与演示冻结（依赖 1–6，D6–D7，6 小时）

- [ ] 7.1 D6 在同一专业页完成工业、汽车各一条真实模型结合真实授权资料的全流程验收，复核 D4 六项核心证据，保留脱敏 Run ID、实际 Skill/RAG/设备工具事件、引用与新 Run 往返证据；复用 DualDomainAgentTest、SkillRuntimeIntegrationTest、AgentToolFeedbackTest 和已有真模定向用例；浏览器覆盖刷新、失败、取消及 ASK_USER（确定性协议路径）。验收：两域各有真模+真实资料成功结果，至少一次真实后续调查，至少一次同 Run 补答；真实 embedding/真机/协议替身/模拟观测明确分列，模拟资料不能替代真实资料，未连接真机不声称现场验收。（3 小时）
- [ ] 7.2 冻结演示，执行全量离线/PG回归及前端测试构建，复用现有浏览器恢复脚本；完成正常答案回读和既有恢复冒烟，记录 P1-03、P1-05 状态及 P1 延期，整理双域演示步骤。验收：acceptance.md 按实际报告总数/通过/失败/错误/跳过及命令、环境、未验证项，保留历史失败与修复；仅将产品布局/业务预期集中交人工确认，技术失败不得靠确认改为通过。（3 小时）

完成条件：D7 不再新增功能；无未解释的阻塞性失败。P1-05 明确为未提交末步结果可能丢失的已知风险，正常恢复冒烟不等于已修复该窗口。

## 8. P1 — 可选增强（P0 核心闭环通过后，最多 6 小时）

- [ ] 8.1 可选 RUN_TOOL 安全摘要：改 TaskOrchestrator 审计构造、SafeAgentEventViews 嵌套白名单及 RunTimeline 展示；仅取 1–2 个合同键、120码点/值和240码点总量，敏感值省略，purpose 仅用真实 CallTool.purpose。验收：在 AgentToolFeedbackTest、SafePublicFieldsTest、AgentStreamTest 增补未知键、嵌套对象、长文本、凭据/Bearer/连接串/路径不泄漏及旧事件兼容；事件接口/SSE可展示安全关键词，缺字段不阻断页面。（3 小时）
- [ ] 8.2 可选 P1-03 定向最小修复：在 KnowledgeSearchService 引用登记事务入口与现有工具 savepoint 协作，必要时仅局部适配 GeneralAgentService；复用 KnowledgeSearchTest/RunRecoveryTest 注入真实 PG 登记错误。验收：登记失败回滚引用、留下失败工具检查点并可继续，不变成 rollback-only 导致的 CHECKPOINT_PERSIST_FAILURE；独立检索事务保持原子，整体断库仍明确失败；记录修复或未完成状态。（3 小时）

完成条件：每项自带回归与记录，时间不足整体延期。P1-03 若实际阻断主演示，暂停其他增强优先处理此最小修复并更新实际工时，不借机重构。P1-05 大型修复、结构化冲突字段、关系图、登录等不在任务列表中。

## Verification commands

以下为实施时复用命令，不代表本次规划已经执行。先按 README 配置 JDK21 及隔离 `DEVICETRUTH_TEST_PG_*`，构建前端在 frontend 目录运行。

```powershell
mvn -version
mvn -q '-Dtest=ModelConfigurationTest,GeneralAgentApiTest,ResourceAuthorizationTest' test
mvn -q '-Dtest=RunRecoveryTest,AgentLifecycleTest,AgentStreamTest' test
mvn -q '-Dtest=DualDomainAgentTest,SkillRuntimeIntegrationTest,AgentToolFeedbackTest,KnowledgeSearchTest' test
mvn -q '-Ddevicetruth.test.real-agent=true' '-Dtest=RunRecoveryTest#realModelUsesReplyToProposeNewSearch+realModelReadsLongAssetReferenceAfterReplyAndWorkerRestart,AgentToolFeedbackTest#realModelProposalsExerciseReuseRefreshAndBoundedSummary' test
mvn -q test
npm --prefix frontend run test -- --run
npm --prefix frontend run build
python scripts/verify_browser_recovery.py --browser-path 'C:/Program Files/Google/Chrome/Application/chrome.exe'
```

已有 `verify_general_agent.py --suite=dual-domain --embedding=real --reasoner=openai`（以实际供应商为准）可作补充；源码明确在线套件需要 real embedding、测试 PG 环境变量、最新 JAR 和隔离 DEVICETRUTH_EVAL_DATABASE，不能用 deterministic 参数声称完成在线验收。若不具备这些条件，使用既有真实模型定向测试加产品 API/浏览器样本，并单列未验证的真实 embedding。

## Planning verification

本次规划只运行 OpenSpec 状态/规格校验，不运行产品测试、模型调用或数据导入；Review 历史测试数字不作为本次通过数。

2026-11-17 实际执行：`openspec status --change devicetruth-pro-workbench-mvp` 显示 spec-driven、4/4 artifacts complete；`openspec validate devicetruth-pro-workbench-mvp --strict` 返回 valid，退出码 0。两项规划检查均通过，失败 0、错误 0、跳过 0；不折算为产品测试用例。业务测试本轮执行数 0，真实 PG/外部模型/embedding/浏览器/现场设备均未在本轮验收。全部实施任务保持未完成；原型与 V0.2 原始文件以及历史验收记录未修改。

人工审核后小修：解除前端对双域资料准备的依赖，将 QUEUED 修复提前至 D2–D3 并行，明确 D4 前真实端到端、D4 六项核心实测、D6 双域验收及 D7 功能冻结；任务数量和工时估算不变。修订后上述两条 OpenSpec 命令再次通过（状态 4/4，严格校验 valid）；本轮仍仅改规划，无业务源码修改、apply 或产品测试执行。
