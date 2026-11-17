# DeviceTruth Agent Runtime 定向修复 A

日期 2026-11-14（Asia/Shanghai）。范围为独立审查的 P1-01、P1-02、P2-01、P2-02。复用 R2 的 V17 检查点与现有 PostgreSQL 表。运行预算、租约、事件及回答安全守卫沿用现有实现。

## 修改前后

| 项目 | 修改前 | 修改后与入口 |
| --- | --- | --- |
| P1-01 工具复用与刷新 | 同参数成功签名持续到 Run 结束，即使摘要已丢、报告已过时、Skill 已换篇 | `TaskOrchestrator.runAgent`、`reusableSignature`、`canReuse` 将复用和现有工具摘要关联。通用工具窗口内的完全相同成功调用返回 DUPLICATE_CALL，淘汰后可再取，失败仍可由模型重试。资产查询和报告在补答推进调查时重新取数；其他工具成功后报告失效。当前 Skill 资料仍在上下文时复用，切换后可再打开先前资料。仍按原有 maxSteps 扣步。 |
| P1-02 补答后新检索 | 恢复后的 attemptedTools 含 SEARCH，默认离线推理器跳过所有后续搜索 | `ScriptedAgentReasoner.propose/search` 用当前目标与补答构造查询，只判断该次查询是否已在现有工具记录中尝试。旧查询继续可见。相同查询仍受去重保护。 |
| P2-01 摘要关键字段 | 普通 Map 的遍历顺序决定先保留哪个字段，整项省略无标记 | `TaskOrchestrator.boundedToolResult` 固定公共字段、核验、冲突、来源、必查项的分配顺序。引用 ID 按完整 ID 保留，超限附标记；单值最多 1,000、单条状态键值合计最多 1,600、窗口最多 8 条且合计最多 6,000。截断计入标记，避开 UTF-16 代理对边界，整字段省略显示 `omittedFields`。 |
| P2-02 资产引用一致 | 工具读完整资产摘录，恢复查询对资产摘录再截 600 字，可能切掉核验状态 | `AssetTools.summary` 把来源、核验和有效期放在可变长取值之前。`AssetFactReferenceWriter` 的读写已使用同一条持久化摘录，`AgentRunRepository.references` 对资产、观测直接回读该值，保留知识引用原有边界。旧记录按已保存的完整摘录恢复。 |

`ActionProposal.CallTool.signature` 复用 JSON 的键排序对参数取 SHA-256。签名和摘要一起写入现有检查点 JSON，`GeneralReasoner.Context.modelView` 与 `StructuredAgentReasoner.propose` 在发送模型输入前移除签名。旧检查点仍能读；旧摘要缺少签名时首次同参调用可以重新取数，之后按新规则复用。

## 实际运行链路

以充电中断为例，Worker 第一次查项目手册，工具摘要和引用随检查点提交。Agent 追问快充还是慢充，用户补答“直流快充”后，Worker 从同一 Run 读回原问题、补答、已扣步骤和旧证据。默认脚本根据“原目标 + 直流快充”构造查询。签名与首次查询不同，网关执行第二次检索，之后答案和 SSE 事件按既有方式持久化。真实模型路径使用同一网关和循环，但是否主动发起新检索仍由供应商提案决定。对长资产台账，Worker 重建前后读到的引用身份和完整摘录保持一致。

## 测试证据

使用 Maven 3.9.9、JDK 21.0.13.0.2、一次性 PostgreSQL 16.15/pgvector；回归中向测试进程注入独立数据库连接，临时 Maven POM 仅改变构建目录，避开现有应用使用的 `target` 文件。模型验收使用仓库已有 OpenAI 兼容配置，只发送模拟夹具。测试中定向引导模型产生动作，工具执行结果和断言来自实际 Runtime。

| 命令 | 总数 | 通过 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `mvn -o -B -f target-a-offline.pom.xml test` | 389 | 385 | 0 | 0 | 4 |
| `mvn -o -B -f target-a-acceptance.pom.xml test -Ddevicetruth.test.real-agent=true "-Dtest=RunRecoveryTest#realModelUsesReplyToProposeNewSearch+realModelReadsLongAssetReferenceAfterReplyAndWorkerRestart,AgentToolFeedbackTest#realModelProposalsExerciseReuseRefreshAndBoundedSummary"` | 3 | 3 | 0 | 0 | 0 |

- `AgentToolFeedbackTest` 验证旧摘要淘汰后可再取、报告在新证据后刷新、Skill 参考资料 A → B → A、立即重复被拦及长字段有限且优先级固定。真实模型用例让供应商生成实际提案，工具使用可计数的模拟实现，并断言报告处理器执行两次、重复提案产生 DUPLICATE_CALL、模型请求所用上下文保留关键标记。
- `AgentStreamTest.reconnectWithLastEventIdBackfillsMissedEventsInOrderExactlyOnce` 走实际 HTTP 提交与补答、默认离线推理器及真实数据库，断言 Run 新增成功检索事件和最终答案事件，SSE 从游标补齐每条一次。
- `RunRecoveryTest` 对 600 字以上的模拟资产摘录比较工具首次取回、仓储读取以及新 Worker 补答后的完整引用，包括来源、核验状态、长取值和身份。两个真实模型用例分别验证供应商在收到重载引用后完成同一 Run，以及收到直流快充补答后提出新的检索并有成功工具事件。上述重启是新建 Worker 服务对象并重用 PG，不是独立 JVM 强杀。
- 离线全量和真实模型定向命令分别退出 0。离线跳过的 4 项包括另行执行的 3 项真实模型测试及受 Windows 权限限制的 Skill 符号链接测试。Surefire 明细在 `target/targeted-a-offline/surefire-reports` 和 `target/targeted-a-acceptance/surefire-reports`。两个临时 POM 和数据库容器已清理。
- 早期全量一轮发现将配置摘要改取最后一条会破坏工厂冲突结论，已撤回该无关行为调整。真实模型补答前两次没有按预期发起新查询，之后将定向任务和补答合并成一次明确请求，保留真实模型做动作决策。最终线上 3/3 只对应该引导场景。真实 embedding、真实设备与浏览器未运行。

## 遗留风险与范围

- 复用针对当前已知调查进度。动态工具在补答后重新取数，报告在其他工具成功后重新生成，同一查询窗口内没有主动跟踪外部数据版本更新。
- 初次真实模型补答验收中，模型直接回答。之后的定向引导验收只证明收到补答后能发出新查询，执行和历史复用规则正确，无法保证任意自然语言目标都优先检索。
- 满窗口后用旧参数再次读取受既有 Run 总步数约束。保留当前部署的 R2 事务和恢复缺口，未处理审查中的 P1-03 至 P1-05、RAG 原文自身的 Unicode 截断及全量引用提示预算。
- 业务和测试修改位于下列路径。现有 R1/R2 工作树改动与本次修改重叠，`git diff` 对重叠文件显示合并差异。

| 类型 | 文件 |
| --- | --- |
| Runtime、协议 | `TaskOrchestrator.java`、`ActionProposal.java`、`GeneralReasoner.java`、`ScriptedAgentReasoner.java`、`StructuredAgentReasoner.java` |
| 资产 | `AssetTools.java`、`AgentRunRepository.java` |
| 测试 | `AgentToolFeedbackTest.java`、`RunRecoveryTest.java`、`AgentStreamTest.java` |
| 文档 | 本报告、`docs/status/CURRENT_PROJECT_STATUS.md` |
