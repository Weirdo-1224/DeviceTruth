# DeviceTruth 专业模式 MVP 开发后全局审查

审查日期：2026-11-17（Asia/Shanghai）。审查版本：`6d23cd2`。审查类型：Post-Implementation Review，仅审查、测试和生成报告，不修改业务源码，不执行 Apply，不归档变更。

## 1. 执行摘要与总体结论

**总体结论：CONDITIONALLY READY。** 已具备比赛演示所需的通用 Agent 后端、专业工作台数据投影、显式后续调查、引用继承、同 Run 补答和持久恢复基础。专业模式接的是实际产品 API 与唯一 Agent Runtime，不是靠前端静态 fixture 生成诊断。历史产物证明真模和真实 embedding 曾完成双域调查及后续调查；但最新布局提交之后存在可由源码直接确认的交互回归，不能将此前的“冻结通过”照搬为当前版本的全功能通过。

本轮真实执行的验证结果：

- 后端全量真实测试 PG 回归：51 类、402 项，**通过 398，失败 0，错误 0，跳过 4**，退出码 0。包含 API、权限、生命周期、恢复、Skill、知识检索和核心离线测试。
- 前端 Vitest：3 文件、60 项，**通过 60，失败 0，跳过 0**；`vue-tsc --noEmit` 和 Vite 生产构建通过。
- 产品 API 只读回读：读取 53 个会话的列表，并实际取得一份 COMPLETED Run，5 条判断、4 条引用，事件含 RUN_MODEL/RUN_TOOL/RUN_ANSWER。该回读证明现有服务能返回持久数据，不证明运行容器就是当前 HEAD 镜像。
- OpenSpec 严格校验通过，任务清单登记 P0 14/14、P1 0/2。本审查按任务内所有验收条件重新评估：**P0 COMPLETE 8、PARTIAL 6；P1 NOT_IMPLEMENTED 2**。这不是“只做了八个功能”，而是六项任务仍有重要边界缺口，不能无条件称为全部完成。
- 用户明确要求“不用做本地浏览器审查”，本轮未执行浏览器脚本、页面视觉验收或最新布局的端到端点击验证；没有本轮截图。此前浏览器产物仅作为历史证据。

当前没有证据支持将任何问题列为全局 P0 阻断；主要 P1 为：ASK_USER 正文隐藏、失败/取消轮证据无法展开、提交响应丢失后的重复开轮风险，以及原有 RAG 事务失败和末步提交崩溃窗口。正常网络、成功完成轮、桌面宽屏条件下具备演示基础；要对完整异常闭环作出交付承诺，应先处理前三项前端问题。

### 审查依据及版本范围

已核对 `README.md`、`AGENTS.md`、`openspec/config.yaml`、本变更的 proposal/design/tasks/acceptance 和三份 delta spec，以及：

- `docs/reviews/DEVICETRUTH_BACKEND_MVP_READINESS_REVIEW.md`：前次后端基线。
- `docs/DEVICETRUTH_PRO_MODE_MVP_DEVELOPMENT_PLAN_V0_2.md`：比赛范围和开发计划。
- `docs/DeviceTruth_Core_V0.1_Spec_Pack/{spec,task,state}.md`：legacy 原则与回归背景，不覆盖当前 OpenSpec。
- Git `1478225..6d23cd2`：覆盖第一批 ViewModel（`82b739c`）及后续专业工作台开发。只以 `41a4e2b` 为起点会漏掉 ViewModel 初次实现。
- `4af3eb2` 为 QUEUED 修复；`7a94419` 为后续调查/引用继承；`672fae0` 为双域历史验收；`4de7614` 为冻结记录；**`6d23cd2` 在冻结之后又改了布局与交互**。

变更仍位于 `openspec/changes/devicetruth-pro-workbench-mvp/`，未归档。OpenSpec 的 planning complete 表示四个规划制品齐备，不代表产品功能完成。

## 2. OpenSpec 任务完成情况

### 2.1 统计口径

任务共 16 项：P0 14 项（1.1–7.2）、P1 2 项（8.1–8.2）。三份 spec 共 **10 个 Requirement、27 个 Scenario**：professional-diagnosis 3/9、investigation-continuation 3/9、investigation-recovery 4/9。没有 REMOVED/MODIFIED/RENAMED 条目。

`openspec instructions apply --change devicetruth-pro-workbench-mvp --json` 返回登记完成 14/16（87.5%），P0 登记完成率 100%。本审查严格 COMPLETE 为 8/14 P0（57.1%）、全部任务 8/16（50%），另有六项 P0 部分完成；百分比仅表示整项验收状态，不代表产品可用性，也不代表完成工作量。

COMPLETE 表示实现与已有验证支持；PARTIAL 表示存在重要缺口；NOT_IMPLEMENTED 表示未实施；UNVERIFIED 表示证据不足。当前任务层面没有整项完全不可核查的 UNVERIFIED，但所有涉及最新页面点击/视觉的子检查均未在本轮复验。

### 2.2 全部任务实现矩阵

表内 Java 路径默认以 `src/main/java/io/devicetruth/` 为根，前端以 `frontend/src/` 为根；测试均位于对应测试目录。历史证据和本轮测试分别注明。

| 功能/任务 | OpenSpec 要求 | 实际实现 | 代码位置 | 测试证据 | 结论 |
|---|---|---|---|---|---|
| P0 1.1 启动基线 | JDK21、产品 API、持久 Run 回读 | 本轮 JDK21/Maven 核验、运行服务只读回读成功 | `api/AgentConversationController.java:29`；`application/agent/GeneralAgentService.java` | 本轮 API 回读记录；真实 PG 全量回归 | COMPLETE |
| P0 1.2 双域演示资料 | 修订后允许项目自制演示手册，真模取证，性质明确 | 历史双域真模/真实 embedding 产物存在；资料为演示手册，非真实厂商手册 | `knowledge/KnowledgeImportService.java`；`scripts/verify_dual_domain_demo.py` | 历史 `dual-domain-2d6809f5/result.json`、acceptance 第5/7.1批 | COMPLETE |
| P0 2.1 ViewModel | category/claims/引用/风险原义映射 | 纯函数确定性投影，不调用模型、不生成概率 | `diagnosisViewModel.ts:29`、`:204`；`components/DiagnosisReport.vue` | 本轮 diagnosisViewModel 12/12 | COMPLETE |
| P0 2.2 专业单页骨架 | 真实 Run 驱动、无候选也可普通输入 | App 接专业页，三栏与底部输入；无 router/store 新依赖 | `main.ts:5`；`App.vue:538`；`components/ProfessionalDiagnosisPage.vue:176` | 本轮构建通过；历史骨架浏览器验收；最新视觉未测 | COMPLETE |
| P0 3.1 判断选择 | 只切查看焦点、正确关联引用 | 选择由局部状态和纯映射完成；建议标“本轮” | `claimSelection.ts:18`；`components/ClaimDetail.vue:30` | 本轮 claimSelection 5/5、映射回归 | COMPLETE |
| P0 3.2 证据详情 | 成功/失败轮均可查看完整来源与摘录 | 成功轮详情存在；失败/取消轮有按钮但详情组件未渲染，窄屏详情栏被隐藏 | `components/ProfessionalDiagnosisPage.vue:129`、`:225`；`pro.css:199` | 本轮 API 引用回读、API 单测通过；源码发现回归 F02 | PARTIAL |
| P0 4.1 状态同步 | SSE 收尾/异常回读，旧事件隔离 | timeline seq/generation 和轮询存在；App terminal watch 依赖非响应式快照，不能按注释触发 | `App.vue:165`；`timeline.ts:65`；`api.ts:340` | 本轮 AgentStream 12/12、生命周期17/17、前端 API 测试；见 F04 | PARTIAL |
| P0 4.2 历史恢复 | 全 runs、默认最新、可看旧轮 | runs/pinnedRunId、localStorage 会话定位和服务端回读存在 | `App.vue:45`、`:249`、`:624` | RunRecovery、历史有界用例；历史 browser-recovery 产物；最新页面未复验 | COMPLETE |
| P0 5.1 深入调查 | 新 Run、合法引用、响应丢失重试同键 | 正常提交链路和重新登记引用已实现；失败后未保存逻辑请求键，未先回读就可再次发送 | `App.vue:394`；`api.ts:464`；`GeneralAgentService.java:358` | 本轮 ResourceAuthorization 9/9；历史两份 roundtrip；F03 | PARTIAL |
| P0 5.2 现场补充/补答分流 | 普通补充开新轮，三元组补答原轮、问题可见 | 两种协议正确分流；最新模板把常见 ASK_USER 问题正文隐藏，影响实际补答 | `App.vue:368`、`:427`、`:544` | 本轮生命周期/恢复/前端合同通过；历史同 Run 真模补答；F01 | PARTIAL |
| P0 6.1 周期 QUEUED 补派 | 有界扫描、异常后继续、worker关闭不派 | 已实现周期补派、进程内去重、异常捕获和 CAS 领取 | `GeneralAgentService.java:237`、`:390`；`RunRecoveryService.java:51` | 本轮 RunRecovery 22 项，20通过/2真模跳过；含数据库表改名故障注入 | COMPLETE |
| P0 6.2 恢复边界回归 | 抢占/预算/扫描上限/租约不回归 | 有针对性用例，预算不重置 | `application/RunRecoveryTest.java:921`–`:1087`（测试源码） | 本轮恢复+生命周期+SSE共51项：49通过/2跳过 | COMPLETE |
| P0 7.1 双域完整集成 | 同页双域全流程、刷新/失败/取消/补答 | 历史真模产物成立；最新布局的补答/失败证据存在回归，不能沿用全通过结论 | `scripts/verify_dual_domain_demo.py`；`App.vue`；专业页 | 历史双域/往返 PASS；本轮后端测试；最新浏览器未执行 | PARTIAL |
| P0 7.2 演示冻结 | 全量回归、风险披露、冻结后不加功能 | 全量复验通过、风险披露存在；冻结后又有布局提交且未覆盖组件交互回归 | `acceptance.md` 第7.2批；Git `4de7614..6d23cd2` | 本轮398/402实际通过、60/60前端；不能覆盖F01/F02/F03 | PARTIAL |
| P1 8.1 安全参数摘要 | 合同键限长脱敏、purpose安全公开 | 未实施；RUN_TOOL仍是工具名/成败/计数等既有字段 | `core/runtime/TaskOrchestrator.java:333`；`SafeAgentEventViews.java` | 无本项新增回归；明确延期 | NOT_IMPLEMENTED |
| P1 8.2 RAG登记事务修复 | 失败登记回滚且失败检查点可提交 | 内层 REQUIRED 模板仍存在；未实施最小修复 | `knowledge/KnowledgeSearchService.java:150`；`GeneralAgentService.java:592` | 正常检索15/15不等于本故障路径已修 | NOT_IMPLEMENTED |

不存在“打了勾但整项源码完全不存在”的证据；存在六项“主要功能实现但验收条件未全部满足”的已勾任务。此次不修改这些勾选，不改写历史记录。

### 2.3 全部 Requirement 矩阵

| 功能/需求 | OpenSpec 要求 | 实际实现 | 代码位置 | 测试证据 | 结论 |
|---|---|---|---|---|---|
| 结构化诊断保持原有语义 | category决定列表、空claims安全展示、保留风险 | 按类别映射全部claims，不按certainty改类，空列表说明存在 | `diagnosisViewModel.ts:29`、`:204`；专业页摘要分支 | 映射12项、本轮构建 | COMPLETE |
| 判断选择只改变查看焦点 | 无提交、只显示关联来源、本轮建议不伪关联 | 本地选中下标，引用按ID关联 | `claimSelection.ts:18`；`ClaimDetail.vue:30` | 选择5项及API合同、历史零提交产物 | COMPLETE |
| 证据详情区分来源与数据性质 | 真实来源/定位/摘录、可关闭、用户报告不冒充事实 | 字段合同正确，但失败/取消和≤1100px访问详情缺口 | `EvidenceDrawer.vue:19`；专业页`:225`；`pro.css:199` | 映射/来源API；F02源码证据 | PARTIAL |
| 深入调查创建后续轮次 | 新Run、保留旧轮、防重、响应丢失同键 | 正常路径成立；异常重试缺同键和先查历史 | `App.vue:394`；`api.ts:464` | 历史roundtrip、本轮API；F03 | PARTIAL |
| 继承引用遵守真实授权范围 | 当前关联引用去重≤10、服务端授权、新ID | selectContextRefs+importReference，旧引用不直接贴新答案 | `api.ts:447`；`AgentRunRepository.java:545` | 本轮ResourceAuthorization 9/9、上下文引用请求测试 | COMPLETE |
| 现场补充与待答问题分流 | 用户报告新轮、三元组原轮、执行中禁并发 | 协议实现正确，常见追问正文在页面被隐藏 | `App.vue:368`、`:427`、`:544` | 本轮生命周期/恢复；F01 | PARTIAL |
| 运行进度与服务端状态一致 | seq去重、迟到隔离、流结束回读 | 控制器隔离正确；App监听非响应式快照使流收尾回读落空，轮询兜底 | `timeline.ts`；`App.vue:167` | API/SSE测试；本轮Vue最小响应式验证 | PARTIAL |
| 失败和取消可观察 | 安全失败说明、取消确认、历史证据仍可访问 | 失败码/取消状态正确，证据详情访问退化 | 专业页`:129`、`:144`；`App.vue:200` | 后端取消/失败/SSE回归；F02 | PARTIAL |
| 刷新恢复会话与逐轮结果 | 服务端权威、所有轮次、活动轮/等待轮恢复 | readConversation+track，历史固定不被refresh强制跳走 | `App.vue:249`、`:328`、`:469` | 本轮恢复测试、历史恢复PASS | COMPLETE |
| 暂时故障后滞留队列自动恢复 | 有界周期补派、异常继续、租约/CAS/预算 | 实现与专用故障注入回归吻合 | `RunRecoveryService.java:51`；`AgentRunRepository.java:137` | 本轮RunRecovery故障注入/抢占/预算 | COMPLETE |

共5项 COMPLETE、5项 PARTIAL。27个Scenario均已对照其所属实现分支；不宣称27个最新页面场景逐个实际点击通过。已识别的场景缺口为响应丢失重试、失败/取消证据访问、追问正文可见与SSE收尾联动；窄屏是来源详情的额外访问缺口。

### 2.4 范围一致性与占位检查

没有新增 Canvas API、Case/Canvas 表、AgentResult 字段、新调查指令协议、数据库迁移、SSE事件类型或第二套Runtime。`RUN_TOOL` purpose/参数摘要仍未落地。新增三栏常态证据区、调查记录抽屉、独立 `pro.css` 是最新布局调整，与早期“可关闭证据抽屉”方案有形态差异，仍在专业单页范围内；是否达到期望视觉不在本轮评价范围。

源码扫描没有发现本次核心产品路径的 TODO/FIXME 占位；输入框placeholder是提示文字，不是假数据。`api.test.ts` 的 mock fetch、scripted reasoner、deterministic embedding是明确测试/离线机制；模拟观测与演示手册是已声明资料边界，不能据此称已接真机。旧 `DiagnosisReport.vue` 保留且修过语义，当前 App 不再使用，不构成第二套运行控制器。

规格口径存在残留：1.2已按用户确认改为演示知识库，而proposal/design、7.1和部分“完成条件”仍写真实授权资料、模拟不能替代真实资料。应传播同一修订口径，保留早先退回记录；不应重新把真实厂商资料当成用户已经取消的阻塞条件。

## 3. 前端页面和组件完成情况

### 3.1 入口与实际组件树

入口是 `frontend/index.html` → `frontend/src/main.ts:5` 的 `createApp(App)` → App。Vite本地开发或部署站点根路径 `/`，没有 Vue Router、专业模式独立路由或模式切换。部署示例前端端口8081，开发代理API到8080；legacy `/index.html` 不应当作产品专业页验收。

```text
App.vue（会话/Run/提交/补答/上传/轮询/SSE协调）
├─ 顶栏：任务标题、当前查看轮状态、Skill、取消/刷新/调查记录
├─ ProfessionalDiagnosisPage.vue
│  ├─ FAILED / CANCELLED / 活动等待 / 空结果状态分支
│  └─ 成功结构化结果三栏
│     ├─ ClaimList.vue → 本地选中
│     ├─ ClaimDetail.vue → toggle-reference
│     └─ EvidenceDrawer.vue → 来源/摘录/关闭
├─ ASK_USER问题区（当前活动轮）
├─ 底部操作栏：深入调查、普通输入、附件、设备选择
└─ 调查记录抽屉（默认关闭）
   ├─ 会话列表、全部Run选择、目标消息和耗时
   ├─ 持久消息列表
   ├─ RunTimeline.vue
   ├─ EvidenceComparison.vue（资产配置快照）
   └─ 调查依据来源清单（文本，无证据详情点击联动）
```

`UiIcon.vue` 在各区复用。没有为每个区域新建组件或引入全局store，结构整体适合MVP；App仍负责较多异步协调，当前问题应局部修复，无需以此为理由重构Core。

### 3.2 数据来源与展示语义

| 页面内容 | 实际来源 | 展示方式与边界 |
|---|---|---|
| 摘要/判断 | `GET /api/runs/{id}` 的 `result` | buildDiagnosisViewModel原文映射，无模型二次加工 |
| 候选原因标题 | `result.category` | 仅POSSIBLE_CAUSES；KNOWLEDGE为资料断言，CONFIRMED_FINDING为服务端核验结论，HUMAN_REVIEW为人工核验 |
| certainty | 每条claim.certainty | 可能/资料支持/服务端已核验，不推导概率 |
| 关联证据 | claim.referenceIds与可读references按ID连接 | 缺ID提示不可用，无引用不造来源 |
| 本轮建议/不确定点/限制 | result三个数组 | 本轮统一展示，无逐判断关联，无“建议已执行”推断 |
| 风险/安全处置 | riskLevel/safetyDisposition | 风险徽章及受控提示，未自行提升结论确定性 |
| 来源与数据性质 | 每条Reference的sourceType/dataNature | AUTHORIZED仅授权渠道；SIMULATED明确模拟；FIELD_OBSERVATION不保证真机 |
| 活动状态/Skill | RunView、`/skill`、事件与轮询 | 已实现，Skill读取以runId隔离 |
| 用户现场信息 | 持久Message.content | 普通用户消息，不自动变成核验证据；最新页面消息主要位于调查记录抽屉 |
| 资产快照 | EvidenceComparison的资产API | 独立查询，不能作为claim自动关联或自动冲突合并的证据 |

本轮未发现前端编造故障概率、现场测量值、文档下载地址或诊断结论。测试fixture仅在测试文件中；产品页面数据由API驱动。**真实API/真实持久Run、真实模型、真实embedding、真实厂商资料、真实设备现场是五个独立层级，不能相互替代。**

### 3.3 UI质量核查界限

本轮按用户要求不做本地浏览器审查，未实际评价桌面排版、像素、对比度、滚动遮挡或按钮真实点击。以下仅为确定源码行为：

- 三栏、顶部任务与底部协作符合专业工作台结构；运行、空结果、失败、取消各有状态分支。
- ≤1100px时CSS直接隐藏唯一证据详情栏，调查记录抽屉只有来源摘要，没有替代摘录详情路径（F02）。
- ASK_USER问题正文通常与最后助手消息相同，模板抑制其显示，而消息现已放入默认关闭抽屉（F01）。
- 用户固定看已完成历史轮、最新轮仍在活动或等待时，深入调查按钮可能显示可用，但方法入口会拒绝动作；不会因此绕过后端，却会形成无反馈操作（F08）。

## 4. Agent Core 与后端功能现状

### 4.1 自主决策能力及本轮Core改动

产品仍使用 `TaskOrchestrator.runAgent`（`:260`完整重载）作为唯一主循环。每步根据消息、历史、引用、预算、Skill及工具反馈构造 `GeneralReasoner.Context`，调用 `reasoner.propose`：

- CALL_TOOL：`:333` 经 `CapabilityGateway.executeTool` 验参、授权、只读控制；成功结果进入引用集合和有界 `toolResults`（`:356`），随后进入下一步Context。
- ASK_USER：`:370` 返回等待outcome，由应用服务落库问题，worker退出；补答后从PG检查点重建同Run。
- ANSWER：`:397` 经AnswerGuard校验引用/风险/资产条件，返回完成outcome；应用层原子写回。

Skill由 `SkillRuntime.open/step` 提供可选包、活动指引和工具上限；RAG由KnowledgeTools调用KnowledgeSearchService。真实供应商连接沿用StructuredAgentReasoner/AgentModelConnections。未发现按工业或汽车硬编码一个新调查流程；脚本推理器仍仅是离线/确定性验证选项，不能当作真模自主调查证据。

本次范围内Core源码差异仅 `AgentToolTypes.java` 两处unchecked警告抑制；`TaskOrchestrator`、AgentResult、AnswerGuard和模型决策协议未因专业页改写。主要后端变更是应用调度与仓储查询：`GeneralAgentService`、`RunRecoveryService`、`AgentRunRepository`和`ProductConfiguration`的恢复扫描配置。

### 4.2 三项旧可靠性问题复核

| 原问题 | 当前状态 | 实现及验证 | 残留风险 |
|---|---|---|---|
| P1-03 RAG引用登记事务异常 | 未修复 | KnowledgeSearchService`:150`仍用TransactionTemplate，工具外层`:592`有savepoint；正常检索15项通过不覆盖rollback-only问题 | 登记SQL异常可使外层事务无法提交失败检查点，升级CHECKPOINT_PERSIST_FAILURE；本轮未专门注入此错误 |
| P1-04 QUEUED长期滞留 | 已实施并通过定向回归 | 周期过期RUNNING回收+有限QUEUED补派，pendingDispatch防重复排队，最终markRunning CAS；RunRecoveryTest`:921`起含真实PG表暂改名故障、异常后继续、抢占、关闭worker、有界排空与预算 | 不是无限可用承诺；数据库持续不可用仍不能推进，队列拒绝仍明确FAILED/QUEUE_REJECTED |
| P1-05 最终提交前崩溃丢结果 | 未修复，已声明延期 | executeRun`:433`先得内存outcome、`:436`后persist；工具检查点不保存最终答案待提交态 | 此窗口崩溃可能重推理或预算耗尽；普通重启/工具检查点恢复通过不证明最后答案必达；未做本窗口专项注入 |

周期扫描默认50条，启动reconcile仍为原全量行为。进程内pendingDispatch只是减少执行器重复项，执行资格最终由版本CAS和租约决定，不是分布式锁替代物。

### 4.3 新增能力与持久化范围

没有新增数据库迁移，本轮未调整已有V1–V17。V17的execution_checkpoint本来就存在，不应算作专业页新建的Case/Canvas存储。没有新增SSE事件；RUN_TOOL既有payload仍不包含安全参数摘要或purpose。参数摘要与RAG失败事务修复明确未开发，不应从时间线字段存在推断它们已完成。

## 5. 实际前后端调用链

### 5.1 首次调查（链路A）

以“工业设备配置是否与维修记录一致”为例：

1. `App.submit():368`取输入、附件和设备，调用 `api.sendMessage():464`；POST `/api/conversations/{id}/messages`，schemaVersion=1，无replyTo，UUID Idempotency-Key。
2. `AgentConversationController.submit():46`委派 `GeneralAgentService.submit():280`。事务锁会话、校验身份/附件/contextRefs、写用户消息和QUEUED Run，提交后dispatch。
3. `dispatch():390`进入有界executor；`executeRun():406`领取租约、启动心跳；`doRun():464`读取PG消息、历史、引用、checkpoint及Skill。
4. `TaskOrchestrator.runAgent():260`由模型每步提出动作，经Gateway取资产/配置/维修/模拟观测或Skill/RAG；`persistToolStep():592`把工具引用、Skill和检查点纳入事务。模型根据结果继续提议，非前端预设工具顺序。
5. `GeneralAgentService.persist():521`在事务中写助手消息、Run结果或待答问题和终态事件；持久结果由Run API读取。
6. `App.track():328`用waitForRun轮询更新runs；timeline读`/events`、订阅`/stream`；终态后refresh取得消息和全部Run。`ProfessionalDiagnosisPage`经ViewModel展示当前结果。

**本轮判定PARTIAL（整页链路）；后端协议层PASS。** 本轮执行了真实PG的API/生命周期/工具/Skill/检索测试和既有服务的只读回读，没有重新发起当前页面真模诊断；历史真模样本见第8节。不能将这些组合成一条本轮新跑的浏览器端到端记录。

### 5.2 判断深入调查（链路B）

`ClaimList`点击 → `selection.select`本地变更 → ProfessionalDiagnosisPage的investigation computed取所选claim引用交集 → plan事件向App提供payload和继承清单 → 用户点击底部按钮 → `investigateCurrent():394` → `buildInvestigationMessage`显式写入判断原文 → `sendMessage`无replyTo → 同会话新Run。

服务器 `validatedContextRefs():342`只接受referenceId且≤10；`importContextRefs():358` → `AgentRunRepository.importReference():545`在owner/project与目标Run范围内复制来源，生成新referenceId；不存在/跨身份拒绝。`doRun`读取重新登记的引用与有界会话历史。新轮正常走自主循环。App upsertRun并取消pinned，显示新轮；旧Run不被覆盖，仍可在run-picker选择。

本轮ResourceAuthorization正向用例确认新旧Run引用ID不同、来源一致、旧引用保留及跨身份404。模型历史只取先前COMPLETED轮，最多12条/6000码点，不是无限保留所有调查内容；选中判断原文与显式contextRefs因此很关键。

**本轮判定PARTIAL。** 正常继承协议PASS；历史确有真模浏览器往返PASS；最新页面未点击复验，响应丢失重试缺陷未解决。不能无条件声称当前所有点击及异常分支全跑通。

### 5.3 补充信息与恢复（链路C）

| 情况 | 前端事件与API | 后端处理 | 返回与页面更新 | 判定 |
|---|---|---|---|---|
| 已结束轮普通补充 | `submit`→sendMessage，无replyTo | submit创建新消息/新Run，读之前完成历史 | 新runId→upsertRun/track；旧结果保留 | 后端及请求合同PASS；整页本轮PARTIAL |
| WAITING_FOR_USER补答 | `answerQuestion():427`→replyToQuestion，当前runId/pendingQuestionId/questionVersion | `GeneralAgentService.reply():308`锁会话/Run，校验问题版本，写补答、恢复原Run并dispatch | 同runId，Run数不增加；track继续；冲突保留draft并refresh | 协议PASS，页面因F01为PARTIAL |

两条路径没有混淆。执行中普通提交与深入调查由最新活动轮约束；待答问题使用latestRun而不是当前浏览的历史轮。补答409不静默改普通消息。需要修的是问题可见性及请求不确定状态处理，不是重建补答协议。

### 5.4 失败、SSE与历史（链路D）

| 事件 | 前端→接口→后端→更新 | 本轮证据与结论 |
|---|---|---|
| 选择另一条判断 | 本地selection→无API→详情computed更新 | helper测试PASS；本轮未点击 |
| 看具体引用 | toggleReference→本地已取得Reference→EvidenceDrawer | 成功宽屏源码可用；失败/取消和窄屏F02，PARTIAL |
| 切历史轮 | pinnedRunId→引用/Skill/events按runId回读→当前页投影 | 后端历史回读/权限测试PASS，页面本轮未操作，PARTIAL |
| 工具失败 | Gateway失败outcome→有界反馈/检查点→下一模型步或失败 | AgentToolFeedback与生命周期PASS；RAG事务登记故障另列未修，PARTIAL |
| 模型失败 | propose抛ReasonerFailure→FAILED稳定码→Run API/describeFailure | 本轮后端/请求合同PASS；失败页视觉未测，PARTIAL |
| SSE短断 | 原生EventSource重连Last-Event-ID→服务端补读→seq去重 | 本轮控制器/API及AgentStream PASS；非浏览器断网，PARTIAL |
| SSE终止/永久错误 | timeline收尾→应触发Run/消息回读；轮询仍兜底 | F04的watch不能响应，PARTIAL |
| 浏览器刷新 | localStorage会话ID→readConversation→全runs/track/问题恢复 | 后端恢复PASS；历史browser-recovery产物PASS；本轮浏览器NOT_TESTED |
| 后端重启/租约恢复 | recoverQueued/reconcileExpired→CAS领取→checkpoint重建 | 本轮RunRecovery PASS；不覆盖末步outcome窗口，PARTIAL |
| 空claims/空结果 | ViewModel空数组→列表说明/摘要/普通输入 | 本轮映射PASS；本轮视觉NOT_TESTED |

## 6. 人机协作完整闭环分析

当前协作是“用户选定调查方向，Agent自主重新取证”，不是点击卡片就由前端改写诊断。用户能改变查看焦点、提交选中原文和来源、补充现场陈述、回复追问；Agent决定是否查资产、Skill、RAG或继续询问，最终输出结构化结果。

正常业务闭环有历史真实执行证据，尤其 `investigation-roundtrip-bd6b5331` 记录了选择零提交、新Run继承、新引用ID、旧轮可看、现场补充新轮，以及真模ASK_USER后同Run恢复。但历史样本类别为KNOWLEDGE，严格说验证的是“一般判断深入调查”，不能改称本轮模型一定输出故障候选原因。POSSIBLE_CAUSES渲染有实际持久样本合同与映射测试；不要求强迫真模返回该类别来完成演示。

薄弱处集中在异常和最新布局：补答问题可能不可见，失败证据入口失效，提交响应丢失时缺少“已发送但未确认”的前端状态。三者均可在现有组件和消息合同内局部修复，不需要新增Case模型、图谱或另一套Agent。

## 7. 数据来源、证据与持久化检查

### 7.1 数据关系与恢复边界

```text
Conversation（一次持续调查，owner/project范围）
├─ Message（用户提问、补答、助手摘要/问题，seq有序）
└─ Run（每个新提问一轮，goalMessageId指向用户消息）
   ├─ result/pendingQuestion/预算/状态/租约/version
   ├─ execution_checkpoint（有界工具反馈、已尝试工具等）
   ├─ EvidenceReference（属于本Run，来源版本/摘录/性质）
   ├─ RunEvent（模型/工具/终态的安全公开事件）
   └─ RunSkill（本轮选包/版本/活动reference）
```

PG中的消息、Run、Reference和检查点是恢复权威。浏览器localStorage仅存会话ID；选择焦点是局部状态。ASK_USER补答是同Run新增消息而非新Run；普通补充/深入调查新建Run。新旧结果独立保留，未实现跨轮claim实体匹配或冲突合并，符合范围。

工具引用、Skill及checkpoint共享工具事务；RUN_TOOL审计在工具事务提交后由appendProgress另外写入（`GeneralAgentService.java:613`附近），审计失败不会撤回检查点。因此“没有某条时间线事件”不必然证明工具没执行，不能凭事件的引用计数构造工具到特定referenceId的图。

### 7.2 资料真实性与当前回读

本轮只读回读Run `5304f5da-2dab-4239-84d3-9147f0d672f9`：COMPLETED、KNOWLEDGE、5 claims、4 references、SIMULATED，存在模型/工具/答案事件。结果摘要留在 `outputs/validation/post-implementation-review-20261117/api-readback.json`。此回读不生成新调查，不消费模型预算，不修改产品数据。

历史双域资料通过授权渠道导入，文件本身为项目自制演示手册；AUTHORIZED来源标签不等于厂商真实性或真实现场核验。历史工业域结果SIMULATED，汽车域结果AUTHORIZED，界面有授权不代表现场核验说明。真实设备连接、现场数据准确性及厂商资料合法授权实物验收均不在已完成证据内。

### 7.3 迁移与历史完整性

本轮全量测试在随机 `devicetruth_test_<UUID>` 测试库建库、Flyway迁移、执行后清理自身库；升级迁移测试也在全量套件内。V1–V17在这些测试环境可正常执行，无本轮新增迁移。未对现有产品库做升级、回滚、数据修复或重启演练。

刷新能恢复**已提交**的完整Run/结果/问题和已登记引用，不能恢复P1-05中尚在内存的末步答案。跨Run引用须显式继承并重新登记，不自动复制所有历史资料。源码还存在切换无结果Run时短暂保留上一轮references的显示风险，见F08。

## 8. 测试结果与覆盖缺口

### 8.1 本轮实际命令、环境和结果

环境：Windows 11；Maven 3.9.9；Microsoft JDK21.0.13.0.2；Node24.14.0；npm11.9.0；本机PG容器端口54329。PG用户名/密码从本机容器配置在进程内读取，只传测试环境变量，未输出或写入报告。每个PG测试类按现有框架使用独立随机数据库。

| 实际执行命令/操作 | 总数 | 通过 | 失败 | 错误 | 跳过 | 结果/限制 |
|---|---:|---:|---:|---:|---:|---|
| `mvn -version` | — | — | — | — | — | JDK21/Maven版本满足要求 |
| 第一次 `mvn -q test`（未注入PG测试变量） | 261 | 245 | 0 | 14 | 2 | 退出1；14类初始化缺DEVICETRUTH_TEST_PG_BASE_URL，非产品断言失败；保留失败历史 |
| 配置 `DEVICETRUTH_TEST_PG_BASE_URL/USERNAME/PASSWORD` 后 `mvn -q test` | 402 | 398 | 0 | 0 | 4 | 退出0；51类、真实PG、scripted/确定性embedding；包含编译/testCompile |
| `npm --prefix frontend run test -- --run` | 60 | 60 | 0 | 0 | 0 | Vitest3文件通过；fetch/EventSource替身和纯函数，不是组件点击 |
| `npm --prefix frontend run build` | — | — | — | — | — | 退出0；先vue-tsc --noEmit再Vite构建，40模块 |
| `openspec status --change devicetruth-pro-workbench-mvp --json` | — | — | — | — | — | 四规划制品齐备 |
| `openspec instructions apply --change devicetruth-pro-workbench-mvp --json` | — | — | — | — | — | 只读上下文，登记14/16；未执行Apply |
| `openspec validate devicetruth-pro-workbench-mvp --strict` | 1 | 1 | 0 | 0 | 0 | valid，退出0；不算产品测试 |
| Python urllib只读API回读 | — | — | — | — | — | 会话/Run/引用/事件可读；单份样本摘要已留存 |
| Node+Vue最小响应式核验 | 1 | 1 | 0 | 0 | 0 | 普通闭包snapshot改变terminalStatus后watch回调0次；验证F04机制，非新增项目测试 |

测试输出在忽略目录 `outputs/validation/post-implementation-review-20261117/`：`initial-test-summary.json`、`pg-test-summary.json`、`suite-counts.json`、`maven-pg.log`、`api-readback.json`。最终逐类XML位于 `target/surefire-reports/TEST-*.xml`。前端构建输出 `frontend/dist/`，本轮未部署。

402项中4项跳过逐一为：

1. `RunRecoveryTest.realModelReadsLongAssetReferenceAfterReplyAndWorkerRestart`：真模开关未启用。
2. `RunRecoveryTest.realModelUsesReplyToProposeNewSearch`：真模开关未启用。
3. `AgentToolFeedbackTest.realModelProposalsExerciseReuseRefreshAndBoundedSummary`：真模开关未启用。
4. `SkillRegistryTest.symlinkedSkillDirectoryEscapingRootIsRejected`：本Windows环境符号链接创建条件不满足。

不重复使用旧审查“总数等于通过数但另有跳过”的统计。acceptance第7.2批称4项全是真模跳过，与本轮实际跳过清单不符；本轮以XML为准，历史原文保留。

### 8.2 本轮重要子集（包含于402，不额外累加）

| 测试类 | 总数 | 通过 | 失败/错误 | 跳过 | 覆盖重点 |
|---|---:|---:|---|---:|---|
| RunRecoveryTest | 22 | 20 | 0/0 | 2 | 租约、重启、同Run补答、QUEUED故障注入、并发抢占、预算 |
| AgentLifecycleTest | 17 | 17 | 0/0 | 0 | 状态、补答/取消/技术失败边界 |
| AgentStreamTest | 12 | 12 | 0/0 | 0 | 事件历史/流、终态、游标、授权 |
| ResourceAuthorizationTest | 9 | 9 | 0/0 | 0 | owner/project、伪造引用拒绝、跨Run新ID继承 |
| GeneralAgentApiTest | 7 | 7 | 0/0 | 0 | 产品API与消息合同 |
| KnowledgeSearchTest | 15 | 15 | 0/0 | 0 | 检索/引用/归属；不证明P1-03失败事务可提交 |
| DualDomainAgentTest | 4 | 4 | 0/0 | 0 | 双域确定性产品协议 |
| SkillRuntimeIntegrationTest | 3 | 3 | 0/0 | 0 | Skill启用/工具绑定/持久化边界 |
| AgentToolFeedbackTest | 23 | 22 | 0/0 | 1 | 工具结果反馈、去重、刷新、有界摘要 |
| AcceptanceTest | 5 | 5 | 0/0 | 0 | legacy核心验收回归 |

### 8.3 历史实际产物与当前复验的区别

下列JSON文件已实际打开核对，并非只引用勾选或提交标题：

| 历史产物 | 内容 | 限制 |
|---|---|---|
| `outputs/validation/investigation-roundtrip-bd6b5331/result.json` | PASS；run1=4ebda1de…；新run2=1f56226b…；零提交选择/新引用ID/旧轮回看/现场补充/真模ASK_USER同Run补答 | openai真模，deterministic embedding，KNOWLEDGE一般判断；早于最新布局 |
| `outputs/validation/investigation-roundtrip-fca4680d/result.json` | PASS；run1=6b04bc39…；run2=c40daf85…；真实embedding、5引用、继承与旧轮回看 | ASK_USER未触发，此条不能当同Run补答真模证据；早于最新布局 |
| `outputs/validation/dual-domain-2d6809f5/result.json` | PASS；汽车ba681900…（6判断/5引用、ev-charging-triage、RAG、ASK_USER）；工业28d1322b…（5判断/4引用、industrial Skill、四设备工具）；刷新及取消轮7dff1d97… | 真模/真实embedding，演示资料；不是双域都调用了所有工具；早于最新布局 |
| `outputs/validation/browser-recovery-0b851eb662df4f5884cd30cb78a33d58/result.json` | PASS；WAITING跨刷新与服务重启，同Run补答、答案引用存活、切会话恢复 | 协议替身路径、历史版本；本轮没有重跑 |

历史acceptance还记载真模定向3/3；本轮未启用真实模型用例，不能把历史3项加入本轮通过数。未运行无约束在线调用，未重新调用真实embedding，未调用现场设备。本轮未执行package/JAR CLI脚本；编译由mvn test覆盖，legacy测试通过不等于打包后CLI重新验收。

现有前端测试没有挂载最新App/专业页组件，无法发现条件渲染隐藏、布局分支或按钮禁用联动问题。后端协议测试再多也不能代替这些检查。按用户要求本轮不添加测试基础设施或做浏览器复验。

## 9. P0/P1/P2 问题清单

优先级按比赛MVP影响定义；以下均未修复。本轮**P0=0、P1=5、P2=4**。工作量为局部修改加必要回归的粗估，不含等待环境/供应商时间。

### F01 · P1 · ASK_USER常见情况下看不到问题正文

- 描述/复现条件：Run等待用户，服务器把问题写为最后一条助手消息；`App.vue:545`仅当最后消息不同于pendingQuestion.question时显示问题正文。最新布局把会话消息移至默认关闭的调查记录抽屉，因此问题区通常只剩标题/requestedFields，用户未必知道要回答什么。
- 依据：`App.vue:544`–`:550`的条件渲染；消息在`:656`附近；`GeneralAgentService.persist:551`附近追加助手问题。
- 影响：所有普通ASK_USER补答体验，直接影响双向协作演示；数据和补答协议本身没有丢失。
- 最小修复：问题区始终展示pendingQuestion.question；若去重，仅在同一可见区域做。用当前模板挂载/渲染断言覆盖“最后助手消息等于问题”的常见情况。
- 工作量：0.5–1小时。影响本次交付：是；可临时打开调查记录查问题，故不列P0。

### F02 · P1 · 失败/取消轮及窄屏没有可达的证据详情

- 描述/复现条件：失败/取消Run已有references，点击“本轮已取回证据”。toggleReference更新openReferenceId，但唯一EvidenceDrawer在成功结果v-else分支内；相应分支没有详情组件。成功页宽度≤1100px时，`pro.css:199`隐藏该组件所在栏，也没有替代抽屉。
- 依据：`ProfessionalDiagnosisPage.vue:129`、`:144`、`:188`、`:224`–`:225`；`pro.css:197`–`:201`。App调查记录末尾仅输出title/sourceType/version/dataNature摘要，无摘录详情联动。
- 影响：失败后继续取证和证据追溯；窄屏成功轮详情也受影响。接口与持久证据仍在，使用API可降级查询。
- 最小修复：让可关闭详情在状态分支外共同渲染；窄屏提供实际可达的浮层/展开区。复用现有EvidenceDrawer，不重做来源模型。
- 工作量：1–2小时。影响本次交付：是，尤其展示失败恢复时。

### F03 · P1 · 提交响应丢失后再次操作可能重复创建Run

- 描述/复现条件：服务器已接受深入调查，但响应在客户端丢失。`investigateCurrent`捕获错误后只提示并释放busy，没有回读历史、保存待确认载荷或请求键；下一次点击传undefined给sendMessage，默认再生成UUID，服务器视作新请求。
- 依据：`App.vue:394`–`:418`；`api.ts:464`–`:491`；服务端`GeneralAgentService.java:294`仅对同requestKey拒绝重复（409 DUPLICATE_REQUEST），不是对同文本去重。普通submit也有类似风险。
- 影响：多轮历史重复、重复模型费用、错误解释为“又深入了一轮”；正常连续点击有busy保护，但不能覆盖响应丢失窗口。
- 最小修复：保留一次逻辑请求的键和不可变payload；错误后先回读会话确认是否已创建，未确认前不以新键重发。兼容服务端现有409去重合同，不必增加接口。
- 工作量：2–3小时含响应丢失回归。影响本次交付：是，网络不稳时最明显。

### F04 · P2 · SSE收尾回读监听未建立响应式依赖

- 描述/复现条件：timeline内部普通变量改变terminalStatus并通知listener；App的watch getter调用普通snapshot，不读取任何ref，Vue不会因snapshot内部变化重跑。
- 依据：`App.vue:165`–`:171`；`timeline.ts:65`快照返回普通对象。已有`syncTimelineView`只同步events/live；本轮Vue最小机制验证terminalStatus改为COMPLETED后回调0次。
- 影响：注释承诺的“SSE收尾即刷新消息/引用”不成立，主要靠track轮询兜底；已有权威轮询使其不升级为P0/P1。
- 最小修复：在onChange中同步terminalStatus为ref，并watch该ref；异常通知也明确触发必要回读。补App与控制器联动检查，避免只测控制器本身。
- 工作量：1小时。影响本次交付：延迟与状态收尾体验，正常演示有降级。

### F05 · P1 · P1-03 RAG登记异常仍可能破坏外层工具事务

- 描述/条件：登记引用SQL异常时，KnowledgeSearchService内层REQUIRED事务模板将外层事务标为rollback-only；外层savepoint回滚不足以恢复提交资格。
- 依据：`KnowledgeSearchService.java:150`；`GeneralAgentService.java:592`–`:610`；tasks8.2仍未勾，acceptance明确延期。本轮根据源码确认风险仍在，未新做该异常注入。
- 影响：工具失败不能作为可恢复反馈继续，可能使整Run CHECKPOINT_PERSIST_FAILURE。
- 最小修复：登记参与已有工具事务，独立调用仍保持本地原子事务；避免REQUIRES_NEW孤立提交。以真实PG引用登记错误验证失败引用回滚且失败检查点可提交。
- 工作量：3–5小时。影响本次交付：异常路径，正常检索可演示；本轮不声称发生频率。

### F06 · P1 · P1-05末步outcome未提交前崩溃窗口仍在

- 描述/条件：模型返回有效ASK_USER/ANSWER，worker在persist事务前崩溃；最终outcome仍只在内存。
- 依据：`GeneralAgentService.java:433`–`:436`、`:521`；`TaskOrchestrator.java:370`、`:397`终局返回。没有待提交答案暂存态；恢复检查点主要针对工具上下文。
- 影响：该答案/问题不能保证恢复，可能重新推理或步数耗尽；不影响已COMPLETED结果回读。
- 最小建议：本次准确披露并保留重试/历史入口；后续有此要求时再局部设计最终outcome可恢复提交，配精确崩溃点注入，避免先扩通用事件存储框架。
- 工作量：风险说明0.5小时；可靠修复约1–2天，需另评估。影响本次交付：低频故障窗口，已接受的延期范围，不阻塞正常比赛MVP。

### F07 · P2 · 资料口径与冻结记录没有同步到最新状态

- 描述/条件：tasks1.2接受演示资料，但proposal/design/7.1等仍写真实资料不得被模拟替代；tasks导言还称实施框全部未完成。7.2冻结后有新布局；跳过项说明也不符合本轮XML。
- 依据：`tasks.md:3`、`:8`、`:49`、Planning verification；proposal成功标准；design风险/迁移段；acceptance第7.2批。
- 影响：维护者可能误认为真实厂商资料已通过，或当前HEAD已做全页冻结验收。
- 最小修复：追加版本化的最终状态和口径勘误，传播用户已确认的演示资料边界；保留历史失败/退回，不覆盖旧记录。本轮报告已提供勘误，未改原文。
- 工作量：1小时。影响本次交付：说明材料可信度，非运行阻断。

### F08 · P2 · 查看历史时的活动约束与证据/资产上下文显示不完全一致

- 描述/条件：查看已完成旧轮时最新轮RUNNING/WAITING，子页仅按所查看run.status和busy算enabled，底部按钮可能可点；App方法另以activityTerminal/pendingQuestion拒绝，形成点击无反馈。切到FAILED/CANCELLED无结果轮，references在await readReferences前未立即清空，可能短暂显示上一轮来源。资产快照用底部selectedAssetId草稿而不是goalMessage设备，可能与历史Run不一致。
- 依据：`ProfessionalDiagnosisPage.vue:74`；`App.vue:394`、`:560`、`:93`–`:116`、`:674`附近EvidenceComparison绑定。
- 影响：历史查看和执行并存时的可理解性、来源上下文；服务端权限仍限制真实引用继承，未发现授权绕过。
- 最小修复：enabled纳入最新活动轮约束；切Run立即清空/标记引用loading并以请求generation隔离；资产快照绑定当前Run目标设备或明确标作草稿预览。
- 工作量：1–2小时含边界回归。影响本次交付：建议修，正常单轮演示可避开。

### F09 · P2 · RUN_TOOL安全摘要仍未实施

- 描述/条件：时间线可看工具名/成败/耗时/引用数，但不能回答“以什么安全关键词查了哪个目标”。原始CallTool有purpose/arguments不等于公开事件保存了它们。
- 依据：tasks8.1未勾；`TaskOrchestrator.java:333`审计构造；`SafeAgentEventViews.java`白名单。
- 影响：调查解释粒度，非基本运行或引用追溯阻断。
- 最小建议：有时间再按工具合同白名单限长、敏感值过滤、公开映射二次校验；不要直接透传arguments或额外调用模型生成purpose。
- 工作量：3小时。影响本次交付：否，已明确可选延期。

## 10. 当前产品可交付程度

| 维度 | 状态 | 依据 | 最重要的一个缺口 |
|---|---|---|---|
| Agent自主诊断 | 已实现且经离线/真实PG测试；有历史真模 | 唯一runAgent循环、工具反馈、双域真模产物 | 本轮未复验在线模型，输出类别和工具选择仍受模型影响 |
| 专业工作台可用性 | 已实现、部分交互需修复/联调 | 真实API投影、60前端单测、构建 | 最新布局的ASK_USER正文隐藏 |
| 证据追溯 | 后端已测、成功轮投影存在 | 引用授权、新ID继承、来源版本/摘录 | 失败/取消轮详情无法展开 |
| 双向协作 | 协议已测，最新页面部分可用 | 普通消息/三元组补答分流、历史真模同Run补答 | 问题可见性与异常提交确认状态 |
| 多轮及恢复 | 已实现并真实PG测试 | RunRecovery20通过、租约/CAS、周期QUEUED补派 | 末步提交前崩溃结果不保证恢复 |
| 工业场景 | 演示级具备基础 | 历史工业Skill、四设备工具执行、5判断4引用 | 观测是模拟，未做真实设备现场验证 |
| 汽车场景 | 演示级具备基础 | 汽车Skill、RAG、真模ASK_USER历史记录 | 演示手册不代表真实厂商资料与实际维修准确性 |
| 端到端稳定性 | CONDITIONALLY READY | 全量PG/前端通过，历史完整往返 | 最新布局缺组件级交互回归，响应丢失可能重复开轮 |

“已实现且经过测试”：后端生命周期、授权引用继承、检查点/QUEUED恢复、Skill/RAG正常路径，以及ViewModel/请求合同/时间线控制器。

“已实现但尚需联调”：最新布局下选中判断、抽屉入口、补答、取消和历史轮次的整页行为；当前运行镜像与HEAD的一致性；当前在线模型/embedding可用性。

“仅模拟或测试替身”：模拟观测、项目演示知识库、scripted推理和deterministic embedding对应的离线测试。专业页本身不是静态Mock，但不能把这些数据性质抹掉。

“尚未开发”：可选RUN_TOOL安全摘要和RAG失败事务修复；Case/Canvas/图谱、真实认证、PDF/真实设备接入等为本次范围外或后续方向，不据此无限抬高比赛验收门槛。

## 11. 下一步最小修复和完善计划

本轮审查到此为止，以下仅建议，不已执行、不自动开启Change：

1. **先恢复补答与证据可见性**：F01、F02。约1.5–3小时；在现有模板/组件内修复，覆盖最后消息等于追问、FAILED/CANCELLED有引用及窄屏替代详情。验收重点是用户实际能读问题和原文来源。
2. **补齐请求不确定状态**：F03。约2–3小时；同一逻辑提交保留键/载荷，先同步历史再决定是否重试，断响应仍只能产生一个调查轮次。
3. **修局部状态联动并重新冻结**：F04/F08，约2–3小时；同步响应式terminalStatus，统一最新活动轮按钮约束，清理切轮暂存来源，绑定正确设备上下文。随后复跑现有回归，补最小组件联动检查；浏览器复验仅在用户之后需要时安排。
4. **P1-03按剩余比赛时间决定**：F05约3–5小时，保持真实PG错误注入验收。若正常演示不触发可继续明确延期；不要将正常15条检索测试当专项修复证明。
5. **更新说明材料与演示脚本**：F07；注明自制演示手册、模拟观测、历史真模证据和当前版本。P1-05继续披露，F09安全摘要排后，不新增图谱或Case系统。

如果现在立即演示，最容易出问题的三个环节是：真模进入ASK_USER后用户看不到问题正文；失败/取消或窄屏点击证据无详情；网络中断丢失提交响应后再次操作开出重复Run。供应商连通/配额与当前部署版本同样尚未在本轮在线复验，应作为演示准备限制记录，不能凭历史在线成功认定现在一定可用。

本轮只新增本报告并生成忽略目录的测试产物；没有修复上述问题、修改业务代码、变更OpenSpec勾选、部署服务、重启产品进程或修改现有产品数据。总体判断为**有真实Agent与持久化闭环的可演示MVP，当前版本仍需少量交互修复才能宣称完整专业工作流稳定交付**。
