# DeviceTruth Agent Runtime R1 + R2 独立代码审查

审查日期 2026-11-14（Asia/Shanghai）。基线为 `66b046b`，R1 提交为 `8213cf1`，R2 为审查时的未提交工作树，含未跟踪的 `AgentExecutionCheckpoint.java` 和 V17 迁移。读过两轮修复报告、`CURRENT_PROJECT_STATUS.md`、关联源码与测试。以 `git diff 66b046b` 对照两轮实现，以 `git diff 8213cf1` 识别 R2 改动，并单独读取未跟踪文件。以下代码位置均指审查时的工作树。

交付前发现工作树另有并行的 Skill 视图前端及文档变更，晚于本轮取证，未纳入 R1/R2 判定，也未由本次审查改动。

## 1. 总体结论

**FAIL。R1 为 CONDITIONAL PASS，R2 为 FAIL。P0 0 项、P1 5 项、P2 6 项。暂不建议进入 R3。**

R1 的工具摘要确实进入下一次模型请求，已授权知识原文可沿同一引用 ID 扩读。工具去重仍会挡住合法刷新，摘要窗口也无法保证保留关键核验信息。R2 已实现归属内历史、补答上下文重建和已提交工具检查点的恢复，但默认离线推理器跳过了使用补答内容的合法新查询。已校验答案在最后一步遇到崩溃会丢失，恢复中的队列交接也可能挂起。

## 2. R1 验收表

| 项目 | 判定 | 实际代码与证据 |
| --- | --- | --- |
| 1. 工具结果进入下一轮 | 通过 | `TaskOrchestrator.runAgent:349–357, 297–300` 将成败、稳定错误码、引用信息和各工具的 `modelContext` 纳入 `Context.toolResults`。`StructuredAgentReasoner.propose:84–92` 序列化完整 Context。原始 `ToolOutcome.data` 保留在工具层，模型看到由工具挑选的字段。18 项反馈测试含实际补全输入 JSON 断言，资产组件、配置核验状态、维修摘要、命中、空结果与失败均有对应断言。 |
| 2. RAG 与原文扩展 | 主要路径通过 | `KnowledgeSearchService.excerpt:251–268` 要求当前 Run 已登记，且文档、切片、owner 和 project 均匹配。`KnowledgeTools.excerptOutcome:136–150` 使用同一 ID 写回较长摘录，`TaskOrchestrator.mergeReference:517–528` 仅接受身份相符的长摘录。默认导入切片为 480 字，扩读获取单个切片；原片长于短摘要时才增长。PG 测试覆盖取回授权原文与身份，扩读后再恢复的整链缺一条直接断言。Unicode 边界见 P2-05。 |
| 3. 重复调用保护 | 部分通过 | 键顺序不同的同参数成功调用被拦截，普通失败可原样重试，不同参数可执行。测试覆盖这三条。合法报告刷新、已替换的 Skill 原文及被淘汰摘要的补取仍会受阻，见 P1-01。 |
| 4. 上下文截断 | 部分通过 | 8 条／6,000 字符窗口和单条 1,600 字符边界实际生效。关键核验字段缺少固定优先级，旧摘要被整体淘汰，引用列表也独立于此预算。见 P2-01、P2-02、P2-05。 |
| 5. 数据权限、注入、泄漏 | 本地权限边界通过，模型行为有剩余风险 | `CapabilityGateway.executeTool:64–89` 强制目录、权限、只读及参数校验，`AnswerGuard.review:86–146` 校验引用和部分输出风险，`SafeAgentEventViews:14–32` 投影公开事件字段。未发现源码证实的跨归属读取或凭据进入事件。工具白名单控制字段，摘录、资料标题及历史正文仍可能含不可信指令或用户放入的敏感文本。配置外部模型时，获授权文本会随 Context 发给供应商，提示规则和引用校验无法证明生成内容未复述这些文本，见下文信任边界。 |

## 3. R2 验收表

| 项目 | 判定 | 实际代码与证据 |
| --- | --- | --- |
| 1. 新 Run 读取有界历史 | 通过 | `ConversationRepository.recentConversationHistory:127–159` 以目标消息序号为界，纳入此前已写最终助手消息的 COMPLETED Run，按时间顺序返回最近最多 12 条、合计 6,000 Unicode 码点。当前目标、Run 内消息和历史独立传入，历史仅带角色和正文。两条历史集成测试实跑通过。 |
| 2. 跨用户、项目及会话隔离 | 本地固定身份范围内通过 | 查询同时约束 owner、project、conversation，历史引用未自动导入新 Run。显式 `contextRefs` 仍走 `AgentRunRepository.importReference:534–551` 的授权逻辑。PG 集成验证隔离及伪造引用被拒。部署边界见下文。 |
| 3. ASK_USER 后恢复结果、引用和预算 | 部分通过 | `GeneralAgentService.doRun:429–466` 复载原目标、追问、补答、当前 Run 引用和检查点。补答消费与入队受问题 ID/版本约束，PG 验证重复补答仅有一项成功，Skill 参考资料重读时重新验证授权和摘要。默认离线推理漏掉用补答发起的新检索，部分长资产引用恢复后变短，见 P1-02、P2-02。 |
| 4. 崩溃／租约失效恢复 | 部分通过 | 已提交的工具结果、计步与成功签名能恢复。失效 Worker 在检查点提交前的数据库写入回滚，并发回收只获得一次领取。PG 证据来自 `RunRecoveryTest`。恢复调度、事务异常和心跳竞争仍有缺口，见 P1-03、P1-04、P2-03。 |
| 5. 工具、答案和事件幂等 | 部分通过 | 已提交工具步骤再次提议会被拦截。提交前的外部只读调用可能再次发生，现有测试直接断言了两次调用、一次成功写入。助手消息、COMPLETED 和 RUN_ANSWER 同事务并受租约与版本保护，未发现重复发布同一终态的路径。模型生成仍可重做或在末步丢失，独立 RUN_TOOL 审计可漏写，见 P1-05、P2-04。 |
| 6. 检查点与状态、步数一致 | 主要路径通过 | `reserveStep` 在模型请求前扣步，`saveCheckpoint` 只接受当前已预扣步数上递增的完成点。已扣步数大于检查点步数的差值可表示未提交的模型步骤。正常收尾扣除已结算时间。末步终态和异常时长结算见 P1-05、P2-06。 |
| 7. R1 回归 | 部分通过 | 18 项 R1 反馈测试全绿。同一 Run 的扩读正文已写回引用行并可重载。R2 将去重和尝试记录跨补答持久化，使 R1 合法刷新缺口跨执行段延续，并引入默认推理补答缺口。 |

**信任边界。** `ProductConfiguration.productServerIdentity:53–60` 从服务端配置生成单一身份，`AgentConversationController` 的列表和读取均使用该身份。隔离结论只适用于不同服务端身份以及同身份下不同 Conversation 的查询范围。多个自然人共用同一服务实例时，代码没有逐请求区分身份。此为既有本地部署约束。另见 `StructuredAgentReasoner.PROTOCOL:24–49` 与 `propose:84–92`，对历史、工具、RAG 和 Skill 的文字信任划分依赖模型遵循提示，网关与答案守卫负责权限与引用校验；目前证据覆盖的是这些程序化门禁，真实供应商上的注入抗性和授权文本复述仍待验证。

## 4. 问题清单

以下为源码确认缺陷，另行注明的为潜在风险。触发条件来自调用链推导，尚未为这些边界增加或执行专用复现用例。

### P0

0 项。

### P1

1. **P1-01｜R1/R2 去重误拦合法重新取数（源码确认）。** `TaskOrchestrator.runAgent:339–357` 按工具与参数的成功签名拦截，`trimToolResults:499–503` 淘汰摘要时不淘汰签名，恢复时又在 :279–283 载入签名。长调查中 `get_asset` 的无引用组件清单被滑窗丢弃后无法补取。另见 `AgentReportTools.reportOutcome:54–95`，报告的引用和步骤随调查变化，但无参的第二次报告仍被拦截；`SkillRuntime.readReference/verifiedReference:138–154` 只呈现当前资料，读 A、再读 B 后无法回读 A。**最小修复** 将普通去重效力限于结果仍可用且数据未变化的调用，报告和当前 Skill 资料按各自刷新语义判断，保留总步数上限。
2. **P1-02｜R2 默认离线推理器跳过补答后的新检索（源码确认）。** `ScriptedAgentReasoner.propose:42–48` 只在 `attempted(SEARCH)` 为假时检索，结合补答生成新 query 的代码在 `search:78–82`。首次搜索后追问，再提交“直流快充”等新线索，`TaskOrchestrator.runAgent:279–300` 恢复旧尝试列表，默认推理器直接使用旧引用回答。前后查询参数本应不同。`AgentStreamTest.reconnectWithLastEventIdBackfillsMissedEventsInOrderExactlyOnce:372–378` 随 R2 改为只期待答案，`GeneralAgentFlowTest:598,640` 用协议替身覆盖了另一次检索。**最小修复** 按当前用户消息轮次或当前规范化查询判断是否已检索，并验证同一 Run 补答后使用新 query，旧调用同参重复仍受保护。
3. **P1-03｜RAG 登记错误可使外层工具事务无法提交失败检查点（源码确认）。** `GeneralAgentService.persistToolStep:562–575` 在外层事务创建 JDBC savepoint。`KnowledgeSearchService.searchChannel:150–151` 的默认 REQUIRED 模板使用同一数据源（`ServerConfiguration.knowledgeTransactions:224–225`、`ProductConfiguration:93–97`）。引用登记在内层抛出数据库异常时，参与事务可先标记整个事务 rollback-only，工具边界随后把异常变成失败结果。外层回滚到 savepoint 无法清除全局标记，提交失败并由 `doRun:467–471` 以 CHECKPOINT_PERSIST_FAILURE 收尾，模型失去本来应收到的稳定工具失败反馈。**最小修复** 让登记复用现有 Worker 事务，由外层 savepoint 负责工具失败回滚，独立入口继续保留自己的事务。用引用写入失败验证无残留引用且能提交允许继续的失败摘要。
4. **P1-04｜恢复后可能一直停在 QUEUED（源码确认）。** `RunRecoveryService.reconcileExpired:86–87` 先提交 QUEUED，再交给内存线程池。进程在两者之间退出，或 `GeneralAgentService.executeRun:375–387` 在领取前遇到短暂数据库错误并返回，Run 仍是 QUEUED。`GeneralAgentService.recoverQueued:217–222` 的周期任务只扫描过期 RUNNING，正在运行的其他实例不会补派，需等下一次启动。**最小修复** 有界周期补派 QUEUED，避免同一进程重复堆积同一待派任务，保留数据库 CAS 作为领取门禁。
5. **P1-05｜末步答案或追问在终态提交前崩溃会永久丢失（源码确认）。** `TaskOrchestrator.runAgent:291–304, 388–395, 417–423` 先扣步，已过校验的追问或答案只返回内存。Worker 在 `GeneralAgentService.executeRun:399–402` 接到它后、`persist:487–525` 提交前退出，新 Worker 在尚有步数时可再次生成。若已占用最后一步，新 Worker 在调用模型前直接返回 BUDGET_EXHAUSTED，已得到的合法结果无法恢复。发布助手消息与终态仍然是原子的。**最小修复** 缩短校验到持久化间隔，为需要恢复的已校验结果保存同一步对应的待提交状态，并优先收口。对模型供应商已返回但尚无持久记录的窗口，明确其重试语义并测试末步。

### P2

1. **P2-01｜摘要限长可整项丢弃核验要求（源码确认）。** `TaskOrchestrator.boundedToolResult:461–483` 先写公共字段，再按 `ToolOutcome.modelContext` 迭代顺序填入；`AgentToolTypes.ToolOutcome:52` 的 `Map.copyOf` 不提供优先级。`AssetTools.configurationOutcome:240–249` 和 `requiredChecks:360–369` 可生成大量待核验要求，多值竞争 1,600 字符时会丢弃整项。其后滑窗还会遗忘无引用的成功信息。`StructuredAgentReasoner.propose:84` 会完整序列化所有可见引用，多次高命中查询的总提示体积不受工具摘要窗口约束，属于额外的容量风险。**最小修复** 优先保留核验状态、冲突和简短必查项，明确标记省略；模型可见引用正文使用独立预算，保留授权引用索引。
2. **P2-02｜恢复时可丢失资产引用尾部的核验状态（源码确认）。** `AssetFactReferenceWriter.read:117–132` 首次返回完整摘录，`AssetTools.summary:300–304` 把 `verification` 放在最长 512 字的 value 和来源之后，attribute 允许 128 字（`V8__asset_registry_history.sql:36–44`）。组合后超过 600 字的配置在补答或恢复时，经 `AgentRunRepository.references:419–440` 把非知识引用截到 600，可能连核验字段一起截掉。引用 ID 仍相同。**最小修复** 首次和重载使用同一份规范化摘录，把来源与核验状态放在可变长正文之前。
3. **P2-03｜恢复扫描可能拖住所有续租（潜在并发风险）。** `GeneralAgentService` 构造器 :166–171、`recoverQueued:217–222` 和 `executeRun:389–396` 共用单线程调度器。`RunRecoveryService.reconcileExpired:74–87` 的数据库等待若覆盖多个心跳周期，其他存活 Worker 也无法及时续租，可能产生额外回收、外部调用和失败。**最小修复** 隔离扫描与续租调度，给扫描数据库操作设置明确等待上限。当前行锁与版本仍可保证只有一个持久写入者。
4. **P2-04｜工具已提交但可缺失 RUN_TOOL 事件（源码确认，R2 报告已披露窗口）。** `GeneralAgentService.persistToolStep:562–579` 先提交引用与检查点，再在独立事务追加审计，`appendProgress:593–605` 失败仅记录日志。若两次提交间退出，恢复会依据签名防止重放，但审计永久缺一条，账本 toolCalls 仍增加，报告和 SSE 看到的工具轨迹不完整。已存在事件的序号保持有效。**最小修复** 若工具审计承担追溯和对账职责，将 RUN_TOOL 随检查点提交，并在提交后通知 SSE。
5. **P2-05｜截断可能损坏 Unicode，R2 会把截断结果写入引用行（源码确认）。** `KnowledgeTools.truncate:165–167` 和 `TaskOrchestrator.boundedToolResult:474–476` 按 UTF-16 单元截取。补充平面字符横跨 2,000 或 1,000 的边界时，结果只保留一半代理项。扩读还会经 `KnowledgeTools.excerptOutcome:142–144` 持久化，可能在序列化时被替换或遭拒。标记附加在 1,000 字符之后也超出声明的单值上限。**最小修复** 使用完整码点边界，并从单值预算内预留标记长度，覆盖边界字符的数据库往返。
6. **P2-06｜异常与恢复超限的终态时间不一致（源码确认）。** `AgentRunRepository.failRecoveredAtomic:238–248` 与 :216–228 的重排队不同，达到恢复上限时未结算最近一次崩溃段，也未清除 `active_accounted_at` 或写 `finished_at`。`GeneralAgentService.failQuietly:635–644` 持租约异常收尾仍传入零时间增量。慢工具后检查点写入失败或达到恢复次数上限时，活动预算可少记，结束时间可能为空或停留在前一段。**最小修复** 复用回收时间结算与终态时间字段更新，仍持租约的异常收尾按当前已结算值只补差额。

## 5. 本次实际测试

| 执行命令 | 总数 | 通过 | 失败 | 错误 | 跳过 | 说明 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `mvn -o -q clean test` | 0 | 0 | 0 | 0 | 0 | clean 插件遇到既有 JAR 文件锁，未进入测试。随后使用常规 test 生命周期，不停止占用进程。 |
| `mvn -o -q test -Dtest='AgentToolFeedbackTest'` | 18 | 18 | 0 | 0 | 0 | R1 定向反馈测试，退出码 0。以下全量也包含这 18 项。 |
| `mvn -o -B test`，注入临时 PG 的 `DEVICETRUTH_TEST_PG_*` | 381 | 380 | 0 | 0 | 1 | BUILD SUCCESS，退出码 0。Maven 3.9.9、JDK 21.0.13.0.2。复用本机 pgvector 镜像启动一次性 PostgreSQL 16.15 容器，各集成类使用测试库，Flyway V17 实际应用，结束后移除临时容器。 |

Surefire 本轮明细包括 `AgentToolFeedbackTest` 18、`GeneralAgentFlowTest` 19、`AgentLifecycleTest` 17、`RunRecoveryTest` 12、`KnowledgeSearchTest` 15、`SkillRuntimeIntegrationTest` 3、`ResourceAuthorizationTest` 8、`AgentStreamTest` 12、`AcceptanceTest` 5，全部通过。该表总数中唯一跳过的是 `SkillRegistryTest.symlinkedSkillDirectoryEscapingRootIsRejected`，当前 Windows 环境未能建符号链接。SSE 压测中客户端断连伴随现有异常日志，相关测试断言通过。

**未执行。** 真实外部 LLM、真实 embedding（其测试未启用，Surefire 对该类计 0 项）、真实设备／现场、独立 Java 进程强杀探针、CLI 打包脚本和浏览器验收。恢复测试的服务对象重建、租约协调和故障探针使用真实 PG，模型行为使用离线或本地协议替身。两轮修复报告里的外部调用或独立进程记录只作历史参考。问题清单中的触发条件没有计入测试通过数。

## 6. 架构评价

`TaskOrchestrator` 作为唯一循环，`GeneralAgentService` 管运行生命周期，仓储提供持久边界，职责划分成立。以 CAR-B 充电故障为例，主循环把检索结果中的已授权引用和摘要交给下一轮模型。追问发出后，服务一起保存助手消息及 WAITING_FOR_USER。补答带着问题版本入队，Worker 读取原目标、补充信息、已用预算与已取证据，才继续执行。当前断点落在默认离线推理器的选动作逻辑，它把“以前搜索过”当成“这条新补答也搜索过”。

V17 三列分别解决最近工具状态、崩溃时计时锚点、当前已展开 Skill 参考资料，均有独立用途。预算步数是预扣量，检查点步数是已提交量，两者都需要。既有 `context_summary` 只在 `AgentRunRepository.waitForUser:299` 写入，仓库业务代码未检索到读者，其中的计数已有其他来源，适合后续整理。当前复杂度集中在 `TaskOrchestrator.runAgent:261–269` 的多个持久回调和 `GeneralAgentService:546–605` 的相邻事务，各写入者依赖同样的租约和状态不变量。先修正现有事务边界，再评估是否有必要把这组回调收进一个持久边界。也可删除 `AssetTools.configurationOutcome:241–247` 中 `new LinkedHashMap<>(Map.of(...))` 的顺序暗示，直接定义真实字段优先级。

## 7. 下一步建议

- **R3 前修复 P1。** 回归至少覆盖补答产生新查询、成功摘要淘汰后的重取、RAG 登记异常回滚、回收已入队但未派发、末步答案已生成但提交中断。
- **优先补齐 P2-01、P2-02、P2-04。** 验证模型仍收到关键核验状态，长引用恢复前后含义一致。明确 RUN_TOOL 是必须完整的审计还是可缺失的进度提示，并据此决定事务边界。
- P2-03、P2-05、P2-06 可独立处理。字段和回调清理可延期。真实模型、真实设备与共享部署认证各有独立验收条件。
