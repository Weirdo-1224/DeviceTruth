# 本轮收尾状态（2026-11-20）

## 范围整理已完成

当前任务改为5项收尾，不再用原15项混合衡量普通审核改造。原任务逐字保存在prior-task-ledger.md。规程/现场与人员前提/工作台规程联合验收迁入独立add-procedure-grounded-guidance，保持全部未完成，不冒称实现。两变更严格规格校验均通过，不自动执行工作台第六组。

## 真实PG专项

RunRecoveryTest新增v2复核/修正阶段的CANCEL/REVOKE/RESTART六组合，使用正式账号作用域、真实成员表撤权、原Run检查点/租约与预算，模型为协议替身。目标断言：取消/撤权不采纳迟到回复、恢复不重复不确定调用、预算不减、无RUN_ANSWER。第一轮AccountService包路径不正确产生1错误（guard-closeout-pg.log），修正后六组合执行通过（guard-closeout-pg-fixed.log）。后续强化撤权原因码/预算断言并增加成功终态重启不重复采纳用例；最终结果见本轮交付补录。

## 独立验收服务及实际页面结论

真实product服务位于http://127.0.0.1:55442 ，使用当前源码、frontend/dist、deepseek-flash、deterministic embedding和54339随机测试库devicetruth_test_87286ba0ee68460b8ed64eaef038c7c9，不访问54329产品库。启动入口为AuthenticatedRealModelTest显式acceptance-hold模式，常规测试默认不启用；质量断言完成后保留至启动后2小时或outputs/guard-acceptance.stop出现，再自动关闭服务和清理随机库。test-only登录页outputs/guard-acceptance-web/login.html通过正常CSRF/login建立隔离验收会话，不绕过认证。非生产登录页面，不纳入产品交付。

真实模型Run 87ed257c-0a7b-476e-bbfd-07f2497b6160 的持久摘要、3项判断、低风险建议、整体审核状态、模拟标记与来源已在实际浏览器核读；报告按钮打开同一持久Run的报告，截图outputs/guard-closeout-report.png。先前正常/风险两例的真模证据继续有效，本服务只新增正常问答，不把历史结果或健康探针当模型调用证据。

实际继续提交出现ACCESS_DENIED，草稿保留，未启动新Run。代码核对frontend/src/api.ts未携带CSRF token；普通消息也未接expectedConversationVersion，认证/空间客户端仍属于add-authenticated-team-workbench第6组。此为真正的端到端阻塞，不是Guard拒绝，未关闭CSRF、伪装写请求或静默降级。第6组完成CSRF/会话版本/登录空间接线后重跑真实浏览器提交；当前3.1保持未完成，不归档本变更。页面和报告读取已通过不能替代提交交互通过。

---

# 规划与验收记录

## 当前状态：2026-11-20 按用户确认简化为整体交付审核v2

先同步proposal/design/specs/tasks，重新打开旧2.2/2.3，再实现并验证v2，最后按实际证据重新勾选2.2/2.3。其余规程、前提、专项PG生命周期和第六组联合验收任务没有缩掉，整份change未完成。以下旧v1实施和失败记录保留为历史，不作为新版必须逐项配引用的验收依据。

### 实现与合同替换

- 新ANSWER：程序先检查引用子集/资产归属及存量CONFIRMED现场核验含义；StructuredAgentReasoner.reviewDelivery一次整体复核，仅返回实质issues，不生成逐句nature/supports。低风险调查建议、一般解释、缺口和限制不强制配引用；新版不按关键词否决。原关键词风险提示/legacy内部入口仍保留。
- issues只允许已存在位置、稳定原因码及已取得引用；显式空issues表示无实质问题，缺字段/格式错误/越界来源不算通过。有问题时至多一次correctAnswer，随后程序硬校验，不再第二次模型复核。修正成功正常交付，语义整改由该次修正负责，不宣称程序独立核验了修正后全部语义。
- AnswerReviewProgress新增可空protocolVersion，缺省1兼容旧检查点；新Structured入口为2，旧内部推理器缺省1。v2已预占的REVIEWING或CORRECTING崩溃恢复不重复不确定调用，有限/失败收口；取消/授权/迟到/原Run预算仍执行。旧v1恢复和构造保持原合同。
- 公开result.review.version=2，units/decisions为空，不回显草稿或内部reason；事件只暴露位置/原因码/计数。前端与报告显示整体审核状态，历史v1仍可读，不为显示再次请求模型。

### 真实模型：正常问答与风险反例均通过

命令 `mvn -q '-Dtest=AuthenticatedRealModelTest' '-Ddevicetruth.test.real-agent=true' test`，2项：通过2、失败0、错误0、跳过0；日志outputs/simple-real.log。实际deepseek-flash、真实54339隔离随机PG、deterministic embedding、SIMULATED手册，无产品库或现场作业验证。

- 正常问答 Run a72066fd-fb53-4228-b408-0f36f93d3dc1：非有限KNOWLEDGE，保留3条手册判断及2项建议；“核对日志时区/时钟偏差”为明确标注的通用建议而非手册规定，不被强制配引用。一次整体复核2455ms、一次修正3017ms，无第二次复核；质量断言通过。
- 风险反例 Run 4c31b8c6-9132-4c53-aaf6-b4e535dc0390：非有限KNOWLEDGE，保留有效手册内容，明确无现场记录不能认定控制器损坏；一次复核635ms、无修正，质量断言通过。

已人工核读上述实际最终答案和调用轨迹；报告、model-events和有界隔离样本均在原忽略目录按Run ID保存。两例只证明当前知识问答/无依据确诊反例，5.1包含真实规程指导验收，因此仍不整体勾选。

### 自动回归与失败历史

- 新DeliveryReviewTest 6项覆盖无逐句引用也能回答、一次复核/一次修正上限、无第二次复核、伪造来源/位置和缺失issues拒绝、v2持久JSON恢复不重做不确定调用、取消、复核/修正失败不交付问题草稿。
- 最终核心命令 `mvn -q '-Dtest=DeliveryReviewTest,AnswerReviewContractTest,AnswerReviewLifecycleTest,AnswerSemanticReviewTest,AnswerGuardTest,AnswerEvidenceResolverTest' test`：65项，62通过、0失败、0错误、3跳过。日志outputs/simple-final-core.log。v1生命周期测试显式使用旧适配器，v2由独立新测试覆盖，不把旧断言偷换成新协议通过。
- 受影响PG与安全套件AgentLifecycleTest/RunRecoveryTest/AgentReportToolTest/ScopedAgentRuntimeTest/SafePublicFieldsTest：55项，53通过、0失败、0错误、2跳过，使用真实隔离PG和协议替身，日志outputs/simple-regression.log；不冒充新复核阶段全部PG故障专项完成。
- 前端 `npm run test -- --run`：106通过、0失败/错误/跳过；`npm run build`类型检查及构建通过。新增v2实际Vue SSR渲染测试，历史v1与旧结果测试保留。日志outputs/simple-frontend-test.log、simple-frontend-build.log；未取得新的浏览器视觉验收。
- 新测试初轮构造少传stepsUsed导致编译失败，补齐后运行；恢复夹具起始步数与检查点不一致导致1错误，修正夹具。新增CONFIRMED硬校验最初误套到旧入口导致旧测试4失败/1错误，修复为显式协议版本选择后最终核心套件通过；不放宽原安全断言。失败过程日志保留，不计通过。
- OpenSpec严格校验、apply回读和diff --check通过。当前2/15完成；未归档、部署、提交或推送。

## 2026-11-20 引文排版兼容修复与真实复验

AnswerEvidenceResolver 先精确匹配，失败后只归一化空白：连续空白归为单空格，中文字符/中文标点相邻处省略空白，ASCII词/数字边界保留。保留字符对应的原始位置，找到后恢复连续原文；StructuredAgentReasoner 返回 resolved.units，AnswerGuard 最终采纳也重新使用恢复后的单元，避免只放宽校验却持久化模型改写的引文。没有模糊匹配、同义替换、改数字/标点或跳过正文。

新增2个回归方法：中文换行/CRLF/制表符兼容且最终公开review保存实际原文；改电压/单位/型号、数字合并、否定删除、跨正文拼接仍拒绝。既有未知来源、对象/时点、模拟升级核验等反例保留。

`mvn -q '-Dtest=AnswerEvidenceResolverTest,AnswerSemanticReviewTest,AnswerGuardTest,AnswerReviewLifecycleTest,AnswerReviewContractTest' test`：59项，56通过、0失败、0错误、3跳过，退出0，日志outputs/guard-layout-offline.log。

`mvn -q '-Dtest=AuthenticatedRealModelTest' '-Ddevicetruth.test.real-agent=true' test`：2项，1通过、1失败、0错误、0跳过。deepseek-flash、54339随机真实PG、deterministic embedding、SIMULATED手册，日志outputs/guard-layout-real.log。不使用产品库，不修改质量断言。

- 正常问答 Run 1d6c7972-a88c-4784-8560-99a29f2fba26：摘要、3条手册判断及对应有据建议全部保留，无UNSUPPORTED_QUOTE；两次复核3336/3254ms。仍limited，唯一被移除的是模型修正后仍保留的无依据时间核对建议，规则CURRENT_DEVICE_EVIDENCE_REQUIRED（语义模型亦标PENDING/UNSUPPORTED）；严格非有限断言仍失败。这次失败已不是换行误拦，不应通过放宽来源规则解决。
- 风险反例 Run df305a8d-f60d-4c12-a3e4-d20bc255505e：KNOWLEDGE、非有限，保留3条有效判断，未确诊当前控制器损坏，质量测试通过；复核3247ms。检查两份实际报告中的知识支持片段均为各自原文的精确子串。

OpenSpec严格校验与diff --check通过。本轮完成引文排版缺陷修复，正常场景完整质量仍未全通过，5.1不勾选；下一剩余点为定向修正应实际删除已经判定无依据的建议，不反复生成同一缺证内容。未部署、提交或推送，未重跑浏览器或全量回归。

## 2026-11-20 超时根因已定位并修复：DeepSeek Flash 默认思考耗尽单步期限

在隔离测试 ReviewTransportDiagnosticTest 中还原原正式提问、完整草稿和来源，直接观察流式响应，只记录时间及字符数，不保存/展示 reasoning 正文或凭据。原设置 HTTP 200 在 651 ms 返回，但 45018 ms 内正文 0 字符、reasoning_content 27691 字符、7424 个数据块，最终由测试截止关闭。故该样本不是网络首响应迟滞、JSON 解析卡死或空白生成；供应商持续思考但未进入最终正文。

同一输入仅增加 thinking.type=disabled：HTTP 200 为 556 ms，首正文 951 ms，总计 5253 ms 正常 stop/DONE，正文3457字符、reasoning 0。记录分别为 outputs/validation/authenticated-real-model/full-review-transport.json 与 full-review-transport-disabled.json；对应命令为 `mvn -q '-Dtest=ReviewTransportDiagnosticTest' '-Ddevicetruth.test.review-transport=true' test`，对照另加 `'-Ddevicetruth.test.disable-thinking=true'`，两次诊断各1项通过；诊断通过仅指采集完成。

AgentScopeStep 现在对精确模型名 deepseek-flash 的受控 JSON 单步显式发送 thinking.type=disabled，复用 SDK additionalBodyParam；不改变其他模型，不加等待上限、不加重试、不改安全合同。该行为覆盖提案/复核/修正，属于明确的 Flash 调用模式调整。官方依据：https://api-docs.deepseek.com/guides/thinking_mode/ 。新增本机 HTTP 协议测试核对 Flash 实际请求含 disabled、其他模型不携带该供应商参数，且仍非流式。

真实 product 隔离随机 PG（54339）、deepseek-flash、deterministic embedding、SIMULATED 手册回归：AuthenticatedRealModelTest 总2项，通过1、失败1、错误0、跳过0。日志 outputs/guard-thinking-fixed-real.log。

- 正常问答 Run dae215e7-ef4f-469d-8089-6646efb64f1a：提案/两次复核/一次修正均完成，复核4544/3859 ms。仍为 limited KNOWLEDGE：模型复核引用不是原文连续片段，summary及两条claim被UNSUPPORTED_QUOTE移除，依赖建议同步删除；严格非有限断言失败，不将其计为质量通过。
- 风险反例 Run 505306d1-5464-4f35-8c82-cd89b25e6621：实际复核完成，保留手册有效判断，未采纳用户要求捏造的当前控制器故障；现有质量断言通过，limited仍保留（1条限制引文不成立）。不以其通过抵消正常问答失败。

超时修复已由真实完整调查验证；下一个独立问题是复核支持片段的准确定位，须保留严格原文与来源校验修复，不能靠宽松字符串匹配放行。5.1仍未完成。

定向回归命令 `mvn -q '-Dtest=AgentScopeStepTest,AnswerSemanticReviewTest,AnswerReviewLifecycleTest,AnswerGuardTest,AnswerEvidenceResolverTest,AnswerReviewContractTest' test`：经环回权限审批重跑，65项，62通过、0失败、0错误、3跳过。此前受限环境同命令为2个HTTP替身初始化错误，日志guard-thinking-offline.log；批准后的有效结果见guard-thinking-offline-approved.log。OpenSpec严格校验与diff --check通过。未运行浏览器/前端/全量PG，不提交或推送。

## 2026-11-20 完整草稿独立重放：尚未解决超时

新增显式 opt-in `AnswerSemanticReviewTest.replayCompleteInvestigationReview`，从本机既有隔离模拟样本 `24d4fd8a-303b-4159-ae84-2a9c04939626` 读取完整 ANSWER 和已登记来源，使用新建真实供应商连接单独执行一次 reviewAnswer。命令：`mvn -q '-Dtest=AnswerSemanticReviewTest#replayCompleteInvestigationReview' '-Ddevicetruth.test.review-replay=true' test`。没有设置此开关时跳过；需要原忽略目录样本，不能当作可移植离线回归。任务输入使用测试的“查版本”上下文，而非原始正式消息，因此只复用了完整草稿/来源，不能宣称精确重放原请求。

原协议重放 1 项错误：MODEL_TRANSPORT_OR_TIMEOUT，45629 ms；日志 outputs/guard-full-replay.log。说明不经过 PG、worker、checkpoint 和先前模型连接复用仍可触发该问题，缩小了排查范围；由于任务上下文不同，尚未证明原超时的唯一原因。

尝试宿主登记来源片段、模型仅返回 evidenceIds 的线协议，保持同一完整草稿、原 45 秒与一次调用：仍 1 项错误，45594 ms，日志 outputs/guard-span-replay.log。该候选没有解决问题，已撤回全部生产改动及其专属测试，不交付为修复。此结果也不足以将问题简单归因于重复输出引文。旧断言与安全规则没有放宽，5.1 保持未完成。

撤回后执行 `mvn -q '-Dtest=AnswerSemanticReviewTest,AnswerReviewLifecycleTest' test`：16 项，13 通过、0 失败、0 错误、3 跳过（2 项 opt-in 外部模型测试及1项完整草稿重放）。本轮没有重新运行完整调查正常/风险质量测试，不计通过。下一定位应先用原正式消息还原相同输入并取得有界的供应商请求阶段/输出进度证据，区分首响应等待与持续生成；不继续原样重试或增大超时。

## 当前状态：2026-11-19 恢复推进（以下历史记录按当时阶段理解）

本变更已经实施，不再处于仅规划阶段。工作台第 4、5 组已交付；第 6 组只阻塞最终消费和联合浏览器验收。原 Run 草案缺失不作为实施前置。当前 2.2、2.3 已按定向协议/主循环证据完成，其余任务依原完整验收条件保留。

### 恢复回归与最新候选

- 原 `guard-runtime-pg-clean.log` 的 61 项保留为失败历史：58 通过、1 失败、0 错误、2 跳过。失败是重启后伪造引用的原因码断言，不删除。
- 当前源码重新编译后，单独执行 `mvn -q '-Dtest=RunRecoveryTest#fabricatedCitationAfterRestartIsRejectedAndLimitedResultHasNoForgery' test`：1 项通过，失败/错误/跳过均 0。仍严格断言 INVALID_CITATION、无伪造结论、一次最终采纳和同码降级事件，没有放宽断言或改原因码。旧失败在当前候选不再复现，尚未单独证明是何种旧构建产物导致。
- 新增 `AnswerSemanticReviewTest.fabricatedReviewSourceIsLocalizedAsInvalidCitation`：验证复核模型伪造来源时宿主返回 REMOVED/INVALID_CITATION，而不是仅报解析失败。该类 6 项通过。
- 受影响统一回归：`mvn -q '-Dtest=AnswerReviewContractTest,AnswerEvidenceResolverTest,AnswerSemanticReviewTest,AnswerReviewLifecycleTest,AnswerGuardTest,SafePublicFieldsTest,AgentToolFeedbackTest,AgentLifecycleTest,RunRecoveryTest,AgentStreamTest,ScopedAgentRuntimeTest,AgentReportToolTest' test` 退出 0。总数 156，通过 153、失败 0、错误 0、跳过 3。日志 `outputs/guard-realign-regression.log`。其中 AnswerReviewLifecycleTest 7 项覆盖两次复核/一次修正上限、成功修正、失败/预算不足局部保留、取消、检查点恢复和总超时；真实 PG 套件使用协议替身，不冒充真实模型质量。3 个跳过项不计通过。
- 首次受限环境访问 54339 失败（1 项错误），经执行权限审批后在同一隔离测试端口运行成功。Docker 只读核对容器端口；不访问 54329 产品数据库、不停止用户服务。
- 基于既有真模样本的长摘要及重复断言，缩短生成协议要求：摘要短结论、原子断言、简单问答优先最多三项下一步、已有证据可答即答，不为同一缺口反复检索。明确文档/历史/用户记录与现场核验的区别；保留原单次 45 秒与 Run 剩余时间上限，不通过加超时或扩大预算求通过。
- `AuthenticatedRealModelTest` 新增无依据当前故障反例；正常场景增加逐项复核、有效断言/最终引用、非有限 KNOWLEDGE 断言。真模不再仅凭链路断言显示通过。实际真模执行结果见下续录。

### 当前运行链路与下一交付

正式输入/已授权材料快照进入 StructuredAgentReasoner 生成草稿；TaskOrchestrator 预占原步数并保存复核阶段；reviewAnswer 一次批量返回逐项判定，AnswerEvidenceResolver 重验实际引用/片段/来源；必要时一次 correctAnswer 后再复核，AnswerGuard.acceptReviewed 删除问题及依赖并重建摘要，原 Run 落库、结果组件/报告读取同一持久结果。

规程/结构化前提（3.1–3.3）、完整新阶段 PG 专项（4.1）、规程组件（4.3）及最终联合验收仍缺；本轮回归通过不代表整个 change 完成。后续依次完成这些内容，不回到仅规划状态，也不将历史旧样本归因作为整体等待条件。

### 本轮真模质量与隔离定位（保留失败，不视为关闭 5.1）

所有调用使用已配置 deepseek-flash，完整 HTTP 场景使用 54339 的随机真实 PG、deterministic embedding 和 SIMULATED 手册，无现场数据。新增严格质量断言后，`AuthenticatedRealModelTest` 两项运行：0 通过、2 失败、0 错误、0 跳过，日志 `outputs/guard-realign-real-model.log`。

- 正常问答 Run `b1ba7df7-ae23-46c4-ac85-c79802659156`：取得 2 条来源，6 次模型尝试，复核等待 45010 ms 后 MODEL_TRANSPORT_OR_TIMEOUT；最终无复核记录、无有效断言，质量失败。
- 无依据当前故障反例 Run `2f469015-c18b-4f0e-a908-12ab1664fa6f`：取得 3 条来源，6 次模型尝试，复核等待 45011 ms 后同码有限结果；没有放行伪造确诊，但有效手册内容也没保留下来，质量失败。
- 在生成协议增加明确的短摘要/关键断言数量目标及短引文目标后，仅复验正常问答一次：Run `24d4fd8a-303b-4159-ae84-2a9c04939626`，4 次模型尝试，复核仍等待 45005 ms；1 项失败。实际草稿仍有 13 个可见单元，压缩提示不足以解决问题。日志 `outputs/guard-realign-bounded-answer.log`，不继续原样重复请求。

新增两个 opt-in 单次真实供应商测试，不经 HTTP/PG 或调查生成，均保持原 45 秒上限：

| 隔离配置 | 实际结果 | 输入/输出 Token |
|---|---|---|
| 单条文档事实（含不可信原文指令反例） | 1 项通过；VALID_SEMANTIC_REVIEW，2513 ms | 796 / 199 |
| 固定 8 单元资料答案，含摘要/3断言/2建议/不确定项/限制 | 1 项通过；12326 ms，宿主最终采纳3断言且非有限 | 1107 / 2685 |

命令分别为 `mvn -q '-Dtest=AnswerSemanticReviewTest#realProviderReviewsOneDocumentFactWithinExistingDeadline' '-Ddevicetruth.test.real-agent=true' test` 和 `mvn -q '-Dtest=AnswerSemanticReviewTest#realProviderReviewsRepresentativeKnowledgeBatchWithinExistingDeadline' '-Ddevicetruth.test.real-agent=true' test`。记录在 `single-unit-review-attempts.json`、`knowledge-batch-review-attempts.json`，均位于原忽略目录；费用未知。这些是固定模拟协议夹具，不能抵消完整调查的三次失败，也不证明现场作业有效。

下一决定性工作：在已保存的完整调查输入/草稿边界上区分批量规模、复合摘要和缺证陈述对复核成本的影响，再选择有数据依据的协议修正；不直接增加等待上限、不拆成无界调用、不以短夹具代替正常产品问答。已有实现/PG回归可独立继续，5.1 保持未完成。

最终协议提示词与测试代码的定向回归：`mvn -q '-Dtest=AnswerSemanticReviewTest,AnswerReviewLifecycleTest,AnswerReviewContractTest,AnswerEvidenceResolverTest,AnswerGuardTest,AgentToolFeedbackTest,SafePublicFieldsTest' test` 退出 0，97 项：94 通过、0 失败、0 错误、3 跳过（2 个本次新增 opt-in 复核真模测试及原 1 个 opt-in 项）。此批与前面的 156 项有重叠，不相加；两个 opt-in 测试的真实执行已在上表单独记录。日志 `outputs/guard-realign-final-core.log`。`openspec validate improve-evidence-aware-answer-guard --strict` 与 `git -c core.fsmonitor=false diff --check` 均退出 0。OpenSpec apply 已回读为 2/15 完成、13 剩余，状态 ready。本轮未重新运行前端/CLI、未取得浏览器验收，不归档、不部署、不提交或推送。

## 2026-11-19 继续实施：主循环接线与结果组件（未完成验收）

TaskOrchestrator 已接入真实推理器的批量复核与一次定向修正；AnswerReviewProgress 随原 execution_checkpoint 保存阶段、至多两次复核/一次修正、原草案及已复核版本。每次调用先沿 reserveStep 预占原步数，再保存阶段，返回后复核中止与授权；CORRECTING 崩溃恢复不重复调用，保留之前已验证的独立内容。内部检查点草案不进入 RunView、公开事件或报告。

AnswerGuard.acceptReviewed 重验来源并按依赖闭包清理，移除摘要时从保留判断确定性重建摘要；文档明确事实投影 SUPPORTED，用户报告不升级独立核验。最终引用取保留单元实际使用集合，已取得来源仍留原 Run。局部结果写 PARTIAL_ANSWER 事件及 review.limited。旧内部/离线推理器未声明 supportsAnswerReview 时保留原合同。规程结构尚未实现，具体控制动作仍暂停为 PROCEDURE_REQUIRED；不能将此阶段视为整个 Guard 变更交付。

批量复核协议改为宿主先固定所有单元 ID，模型只回 assessments，不重复草稿正文；宿主拒绝新增/遗漏/重复 ID。复核与修正均复用原供应商连接、Token 审计、剩余时间与取消/撤权回调。原型版复核响应重复长正文，已替换，不保留第二套自治循环。

新增 AnswerReviewLifecycleTest 4 项，通过 4、失败 0、错误 0、跳过 0：修正失败保留文档/删除故障及建议、预算不足局部收口、持久 JSON 恢复不重做修正、复核等待中取消。AnswerSemanticReviewTest 5 项随紧凑协议迁移通过；另执行 AnswerGuardTest/AgentToolFeedbackTest 等定向测试通过。尚缺真实 PG 上的新阶段故障/竞争/恢复断言，不能仅凭内存替身勾选 4.1。

前端新增 AnswerReviewPanel 与 answerReviewView，已接既有专业结果页和打印报告，不重排空间导航。区分文档、用户、历史、推断、核验来源和保留/待确认状态，拒绝项原文不展示。旧结果显示没有新版逐项记录，不再次调用模型。`npm run test -- --run`：105 项通过、失败 0、错误 0、跳过 0；`npm run build` 类型检查及构建通过。包括 4 项新增 ViewModel/真实 Vue SSR 组件渲染测试。规程/前提展示尚未完成，不勾选 4.3。

### 本轮真模与技术失败记录

- Run 906405a7-8d26-4a0e-b9f7-859acb99ea2f：deepseek-flash，先激活 Skill，随后两次 INVALID_JSON_OR_SCHEMA，未取得来源，有限结果 MODEL_OUTPUT_INVALID_AFTER_RETRY。JUnit 1 项失败（原来源数断言未放宽）。
- 为定位模型协议增加仅显式安装的 ProtocolObserver；生产无默认观察者。AuthenticatedRealModelTest 的 TestConfiguration 才安装采样，最多 12 条、每条最多 65536 字符，不采输入/Prompt/凭据。样本只有本机隔离模拟夹具响应，位于忽略目录 outputs/validation/authenticated-real-model/sample-<runId>.json；仅本地维护者用于本次定位，不公开/不提交/不用于真实数据。保留至本变更验收定位结束，再清理此次样本；公开报告继续不含草案。
- Run fa1feeda-5617-4943-aaab-0d1714d5d2a5：真实调用完成调查并进入 REVIEWING，但语义复核 45 秒超时，有限结果 MODEL_TRANSPORT_OR_TIMEOUT；链路 JUnit 1 项通过，答案质量未通过。
- 紧凑复核协议后 Run 481e738d-9ed3-406e-8e33-752111c84bb3：已取得 3 条来源，但在后续模型提案阶段 45 秒超时，尚未到复核，有限结果 MODEL_TRANSPORT_OR_TIMEOUT；链路 JUnit 1 项通过，答案质量未通过。不得归因于紧凑复核输出，也不以继续重复同请求求通过。
- 三次仅真实隔离 PG、外部 deepseek-flash、deterministic embedding、SIMULATED 手册；不代表真实规程/现场作业。日志分别为 outputs/guard-integrated-real-model.log、guard-observed-real-model.log、guard-compact-review-real-model.log；Run 报告逐 ID 保留，原失败历史未删除。
- `mvn -q '-Dtest=AgentLifecycleTest,RunRecoveryTest,AgentStreamTest,ScopedAgentRuntimeTest,AgentReportToolTest' test` 实际 20 项：通过 17、失败 0、错误 3、跳过 0。三个套件初始化遇到已有的 Unresolved compilation problems，生成测试类无法解析 AuthenticatedProductTestSupport；并非数据库缺失，Flyway 在随机库实际执行到 V23。应 clean 重建后重跑同一受影响集合，尚未执行，不能计通过。日志 outputs/guard-runtime-pg.log。

### 浏览器检查被用户中止

为独立组件视觉检查启动单独 Vite 127.0.0.1:5187，临时样例 frontend/.guard-component-preview.html，明确模拟展示，不连接 product 后端。IAB 访问本机端口超时，改在本机浏览器新标签打开；用户按物理 Escape 中止计算机操作后停止，未取得组件实际截图或点击结果，不计视觉验收，不算第 5.4 联合验收。未停止用户原有服务；该临时 Vite 进程及样例仍在，后续收尾应关闭本次专属 session 并移除本次临时文件，不操作其他进程。

当前任务仍不提前勾选；下一工作为修复上述生成产物并完成真实 PG 复核生命周期验证，继续规程/结构化前提及组件，最后重新判定 1.1–5.2 实际完成情况。5.3–5.4 保持未完成，无 Git 提交/推送、部署或产品库写入。

## 2026-11-19 apply 定向基线与材料缺口

已恢复 OpenSpec ready 状态（0/15）、最新提交 7ff00ac、Maven 3.9.9 / Java 21.0.13。新增构造配对复现及 contract-ledger.md，未修改产品 Guard 或放宽安全断言。

实际执行 `mvn -q '-Dtest=AnswerGuardTest' test` 退出 0；详细总数见本次交付。`git -c core.fsmonitor=false diff --check` 退出 0。本次仅离线 JUnit、模拟证据，无 PG、外部模型、前端或现场验收。

原报告确实不含被拒绝的草案，本地按原 Run ID 和 real-model 文件名查找仅找到该报告；原随机数据库已按既有记录删除。当前可定位构造样例中的误拦分支，但不能判定原 Run 的实际拒绝分支。此前将此材料缺口列为全局暂停条件，是错误的依赖判断：它只限制旧样本归因，不阻塞协议、实现和已授权的新真模复现。继续实施不需要用户再次授权，也不伪称恢复了原样本。

在该次暂停点，1.2–5.2 尚未实施；以下恢复实施记录覆盖其后新增工作。5.3–5.4 继续等待工作台第六组，不归档本变更。

### 恢复实施：分支定位、合同与宿主校验底座

- AnswerGuard.Verdict 保留旧构造，增加结构化 issues（字段位置、稳定规则）；原拒绝分支分解为类别、certainty、确诊措辞、设备故障措辞，放行标准尚未放宽。
- TaskOrchestrator 将拒绝位置交给原 progressWriter，GeneralAgentService 保存 RUN_ANSWER_REVIEW；公开白名单只有 code/unitIds/rules，不带原文、草稿、Prompt 或密钥。
- AgentResult 追加可空 review，ActionProposal.Answer 追加 reviewVersion/units，原构造与缺省 JSON 保留。AnswerReview 实现来源种类、单元位置、支持片段、依赖、处置与版本校验，拒绝循环、缺失/重复单元及越界复核来源。未把新增字段当作模型自报通过。
- AnswerEvidenceResolver 解析本执行段已取得引用和正式 USER 消息；拒绝不存在来源、伪造片段、对象/时点越界及把模拟文档或用户记录升级成独立核验。新协议在 AnswerGuard 先经过这些硬校验；旧协议继续原路径。依赖闭包方法已通过直接/间接依赖反例，尚未接最终答案局部重建。
- 这些是部分实施，不代表 1.2 或 2.1 全部完成：结构化前提输入、完整语义复核/修正、规程、生命周期预算和前端尚待接通。任务未提前勾选。

### 新真模定向复现（仍为失败质量证据）

已授权检查 Docker：dt-workbench-migration-test 实际绑定 127.0.0.1:54339；未使用 54329 产品库。使用 dt_test、任意非空 trust 测试密码及随机测试库。

命令：设置既定 DEVICETRUTH_TEST_PG_* 后执行 `mvn -q '-Dtest=AuthenticatedRealModelTest' '-Ddevicetruth.test.real-agent=true' test`。首次 1 项失败：夹具缺第四组新增的 expectedConversationVersion，返回 409 CONVERSATION_VERSION_CONFLICT；添加初始版本 0，不改服务端规则。第二次 1 项错误：生成类出现 Unresolved compilation problems；执行 `mvn -q clean '-Dtest=AuthenticatedRealModelTest' '-Ddevicetruth.test.real-agent=true' test` 重建后通过。三次日志分别为 outputs/guard-baseline-real-model.log、guard-baseline-real-model-fixed.log、guard-baseline-real-model-clean.log，均忽略不提交。

最终 JUnit 1 项，通过 1、失败 0、错误 0、跳过 0，仅链路/授权通过。真实模型 deepseek-flash，Run 39e9c6d9-e5e4-4090-b599-c76b4aa075b4，5 次模型尝试、3 条来源、activeDurationMs=26939、inputTokens=25548、outputTokens=4818；费用未知。数据库真实 PG，embedding deterministic，资料 SIMULATED。结果仍为 HUMAN_REVIEW/limited，正常知识答案质量未通过。

本次第一次拒绝定位为 LEGACY_CONFIRMATION_WORDING，位置 summary、uncertainties.1；第二次同规则，位置 summary、suggestions.4、uncertainties.2。报告保存在 outputs/validation/authenticated-real-model/report-39e9c6d9-e5e4-4090-b599-c76b4aa075b4.json。报告仍不含原始草案，因此只能定位分支与字段，不能仅据此判定各句是否正确；保留原 Run 的不可归因事实。

### 当前候选定向离线回归

`mvn -q '-Dtest=AnswerReviewContractTest,AnswerEvidenceResolverTest,AnswerGuardTest,SafePublicFieldsTest,AgentToolFeedbackTest' test`：总数 82，通过 81、失败 0、错误 0、跳过 1。

| 类 | 总数 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| AnswerReviewContractTest | 4 | 4 | 0 | 0 | 0 |
| AnswerEvidenceResolverTest | 5 | 5 | 0 | 0 | 0 |
| AnswerGuardTest | 32 | 32 | 0 | 0 | 0 |
| SafePublicFieldsTest | 6 | 6 | 0 | 0 | 0 |
| AgentToolFeedbackTest | 35 | 34 | 0 | 0 | 1 |

跳过项为 opt-in 真实模型测试，不计通过。以上离线套件与单独真模测试分开记录。`git -c core.fsmonitor=false diff --check` 通过。尚未做完整 PG 生命周期回归、CLI、前端、浏览器或真实规程指导验收；未部署、认领、提交或推送。

### 批量语义复核适配器（尚未接运行主循环）

StructuredAgentReasoner.reviewAnswer 复用原供应商连接，单次批量输出、无内部格式重试；输入原文置于 JSON 数据而非协议指令。调用前和返回后检查剩余时间及取消/撤权；记录实际尝试、耗时和供应商 Token，不接受迟到复核。宿主再次校验全部可见字段、引用与原文，硬拒绝优先于模型 RETAINED，自报通过但没有证据 ID 的单元回到 PENDING，随后清理依赖闭包。

新增 AnswerSemanticReviewTest 5 项：覆盖不可信原文隔离、自报核验不能升级模拟文档、非法输出无内部重试、预算/撤权/迟到、伪造片段与越界单元。首轮 5 项通过 4、失败 1（非法 ID 夹具的字符串替换未实际改到字段）；改用 JSON 节点直接修改字段，保留原反例，5/5 通过。

最终统一命令 `mvn -q '-Dtest=AnswerSemanticReviewTest,AnswerReviewContractTest,AnswerEvidenceResolverTest,AnswerGuardTest,SafePublicFieldsTest,AgentToolFeedbackTest' test`：87 项，通过 86、失败 0、错误 0、跳过 1。此记录取代上表同类候选，不相加。随后 diff --check 通过。

当前运行主循环只消费了新硬边界校验与拒绝定位，尚未调用 reviewAnswer。下一实施点是将复核次数/修正次数/阶段写入原 checkpoint，在原 reserveStep 和剩余时间合同内调用该方法，并接一次定向修正及最终结果重建；之后继续规程、结构化前提、PG 生命周期与组件。不能据适配器单测宣称 2.2 或 4.1 全部完成。原样本归因缺失不再列为实施阻塞；无需用户重复授权。

2026-11-19，阶段：完成用户边界讨论并创建独立规划，尚未实施。

## 已确认的产品决定

1. 区分资料事实明确与当前设备确诊，不以一个现场核验布尔值限制所有陈述。
2. 优先局部处理问题及依赖，保留独立成立内容；不机械添加“可能”，不能可靠拆分才整份处理。
3. 开放有适用规程支持的高风险具体指导，而非遇到操作词一律拒绝。
4. 没有适用规程不得由模型补写高风险流程。
5. 信息不完整可以查阅规程，但不能包装成当前设备已可执行。
6. 用户现场记录可支持强度相称的判断，不要求必须系统独立采集；来源性质保留。
7. 首版人员/现场前提可由用户明确确认，不另建资质平台；未知/否定前提不能默认为满足。
8. 关键词仅定位疑点，语义按具体陈述复核，权限/引用/来源硬边界保留。

## 既有真实证据

真实模型 `deepseek-flash` 的 Run `c96da39d-263f-4b35-84f8-c30fdab28ab0`：5 次模型尝试、3 次成功工具调用、4 条来源，最终 COMPLETED/HUMAN_REVIEW，原因为 UNCONFIRMED_FINDING。此为问题证据，不是本优化的通过证据；没有原草案定位前，不认定为已证实误拦。原脱敏报告位于 `outputs/validation/authenticated-real-model/`，完整补验记录在工作台 change 的 acceptance.md。

## 执行顺序与责任

推荐第四组 → 第五组 → 本变更的服务端/协议/组件 → 第六组消费 → 联合验收关闭本变更。第四、五组 owner 保持原 Run/材料合同，Guard owner 实施本方案与组件；第六组 owner 负责页面组合和实际消费，不并发改同一执行状态机。所有阶段需各自实际验收，不自动执行或勾选别的 change。无需等工作台 33 项全部完成才开始 Guard。

本次只创建 proposal、三份 specs、design 和 tasks，未修改 Guard、提示词、运行时、前端或数据库。未运行新的业务测试/外部模型调用，不部署、不认领真实数据。任务全部未完成；规划结构检查结果在交付时记录。

## 实际规划检查

- `openspec status --change improve-evidence-aware-answer-guard`：4/4 工件存在，proposal/specs/design/tasks 完整。
- `openspec validate improve-evidence-aware-answer-guard --strict`：valid，退出 0。
- `openspec instructions apply --change improve-evidence-aware-answer-guard --json`：15 项实施任务，完成 0，剩余 15，CLI 状态 ready；ready 表示工件齐备，不代表第四/五组前置交付或实施授权已完成。

两项规划完整性/结构检查通过；业务测试执行 0，不将它们折算为产品验收。


## 2026-11-20 收尾补录：回归与交付边界

受影响回归 `mvn -q '-Dtest=AcceptanceTest,DeliveryReviewTest,RunRecoveryTest,AgentLifecycleTest,SafePublicFieldsTest,AgentStreamTest,AgentReportToolTest,ScopedAgentRuntimeTest,AnswerGuardTest,AnswerEvidenceResolverTest,AnswerReviewContractTest,AnswerSemanticReviewTest,AnswerReviewLifecycleTest' test` 退出0，138项，133通过、0失败、0错误、5跳过。该批RunRecoveryTest为23项（含六组合专项），后续专项报告覆盖XML但本次结果按执行时报告保留，不与其他批相加。日志outputs/guard-closeout-regression.log。

`mvn -q package '-DskipTests'`退出0；打包步骤未重复测试，不单独计通过项。`python scripts/verify_cli.py`退出0，核对真实打包JAR的help、A–D持久报告、无效参数、缺密钥明确失败不降级及本地HTTP私有配置读取。报告target/cli-verification.json，日志outputs/guard-closeout-cli.log。CLI数据/推理为scripted离线与HTTP替身，不冒充外部真实模型。前端106项与构建结果复用，前端源码此后未修改。

最终强化PG专项新增了真实失败原因码和所有干预算例预算单调断言，并单独验证成功v2重启不重复采纳。首次强化误用RunRecord.failureCode不存在访问器；第二次误以为failure_code为列，实际它来自RUN_FAILED事件子查询。两次分别保留guard-closeout-pg-final.log、guard-closeout-pg-verified.log（各2项中1错误），修正测试读取真实事件，不删除或放宽原因码断言；最终结果见guard-closeout-pg-final-ok.log。

当前范围5项，前三类技术收尾已完成；3.1真实前端提交未通过，保留未完成，不归档。具体接线归属add-authenticated-team-workbench第6组：CSRF、expectedConversationVersion、正式登录和空间客户端，不能用关闭后端安全校验代替。独立服务仅提供本轮真实结果/报告读取验证，不冒称已是完整可提交的新工作台；原8080/8081及产品库未更新。

最终强化专项退出0，2项通过、0失败、0错误、0跳过：一项覆盖六种阶段/干预组合，另一项验证成功终态重启后模型总调用仍2次、预算不变、RUN_ANSWER恰好1次。当前apply回读4/5完成，1项前端提交验收未完成。
