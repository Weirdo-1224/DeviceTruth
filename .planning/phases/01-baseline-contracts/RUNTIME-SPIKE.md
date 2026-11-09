# 同链路 Runtime 比较实测

日期：2026-11-09（Asia/Shanghai）。代码起点 `a9607cf7b1631c221a9f9865036751642772e352`。本计划只新增测试夹具；没有给生产入口引入第二套循环。

## 可复现资产与环境

- 测试：[RuntimeComparisonTest.java](../../../src/test/java/io/devicetruth/spike/RuntimeComparisonTest.java)。
- 固定输入：[cases.json](../../../src/test/resources/runtime-comparison/cases.json)。
- 两份资产均位于测试目录，不进入生产 JAR 的业务实现。
- Java：Microsoft 21.0.13.0.2；Maven 3.9.9；Windows 11 amd64；Spring Boot 3.5.16；AgentScope Core/DashScope/OpenAI 2.0.4。未改 pom、未新增依赖。
- 输入 SHA256：`74192b906d4d4cc81c838039503037a34c3876fbd4eb038cbfc6e035e2601277`。
- 实际加载的 Core JAR SHA256：`032a90cb1510b30f52f344a8e6a1047fa969314feb97c43c3da753e0505617a5`。
- 本机固定版本源码包 SHA256：`1f9865d2caf9d5d3a7e709563445d9fa6b3285e5cf9a7ea4dbb3e29cdf4d1689`；位于 `docs/research/agentscope-core-2.0.4-sources.jar`（已有调研资产）。使用源码包及 `javap` 核对 Builder、Hook、Toolkit、ToolUseBlock、ToolCallParam 的实际 API，候选经 Maven 编译运行。
- 官方发布源码入口：[Maven Central 2.0.4 sources](https://repo.maven.apache.org/maven2/io/agentscope/agentscope-core/2.0.4/agentscope-core-2.0.4-sources.jar)、[2.0.4 release](https://github.com/agentscope-ai/agentscope-java/releases/tag/v2.0.4)。网上 main 文档仅辅助发现 API，不用它替代本机固定版本验证。

```powershell
$taskJdk = Get-ChildItem -Directory .tools -Filter 'jdk-21*' | Select-Object -First 1
$env:JAVA_HOME = $taskJdk.FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -version
mvn -q '-Dtest=RuntimeComparisonTest' clean test
# 可选：将第一次输出复制到 outputs/validation，再 clean 重跑比较
Copy-Item -LiteralPath target/spikes/runtime-comparison.json -Destination outputs/validation/01-01-spike-first.json
mvn -q '-Dtest=RuntimeComparisonTest' clean test
Get-FileHash target/spikes/runtime-comparison.json -Algorithm SHA256
Get-FileHash outputs/validation/01-01-spike-first.json -Algorithm SHA256
```

实际两次成功命令均退出 **0**。各运行 1 个 JUnit 测试方法，内部遍历 6 样例 × 2 候选共 12 次比较执行；两次输出字节完全相同，SHA256 都为 `830d11a9aa9a24cd002ca67e881a90c3928361d45ed8ff9752772d399c773aeb`。输出为 `target/spikes/runtime-comparison.json`，可删除后重新生成，保留源码/夹具不依赖 target。

## 两个候选怎样运行

**A：evolved-loop。** 在测试中抽取 TaskOrchestrator 的“读取快照→AgentScopeStep 单动作→服务器执行→更新状态”的控制模式，替换成最小 CALL_TOOL/ASK_USER/ANSWER 协议。使用生产 `AgentScopeStep.complete`（工具列表为空）和生产 `CapabilityGateway.execute`。这是演进方向的小原型，**没有直接给现有 TaskOrchestrator.run 增加等待/恢复功能**。

**B：agentscope-react。** 真正构造 `ReActAgent`、`Toolkit` 和 `AgentTool`，通过 SDK 原生 ToolUseBlock 执行工具。每个执行轮次只有一次 `agent.call`，没有外层逐动作循环。PostReasoning Hook 将提议交给同一个 Journal 做动作/引用边界，ASK_USER 后 `stopAgent` 返回，PostActing Hook 处理工具失败终止。新建 Agent/Model 后从同一 JSON 快照续跑。只注册测试工具，关闭 meta/task/动态 Skill/Skill 脚本，模型和工具单次尝试上限都为 1。

两者共用同一个无游标 fake Model 决策函数：只读取真正传给模型的消息/工具结果，不读取隐藏期望答案或 Journal。第一步查模拟资料，信息不足时追问“交流慢充还是直流快充”，补答后再次检索再答复。夹具答案为模拟工程文字，不提供真实维修规程。

服务端 Journal 负责引用集合、问题关联、累计步数与安全事件；它是两候选共享的测试业务边界，不是第三套 Agent 决策循环。工具返回带 SIMULATED 和来源 ID 的固定 Observation，不查询向量库。

## 结果与断言

| 样例 | A | B | 每候选模型调用 / 实际读取 handler 次数 |
|---|---|---|---|
| charging-resume | COMPLETED | COMPLETED | 4 / 2 |
| unknown-tool | FAILED:UNKNOWN_CAPABILITY | 相同 | 1 / 0 |
| write-tool | FAILED:NOT_READ_ONLY | 相同 | 1 / 0；写 handler 若触达直接 AssertionError |
| cross-asset | FAILED:ASSET_DENIED | 相同 | 1 / 0 |
| invalid-arguments | FAILED:INVALID_ARGUMENTS | 相同 | 1 / 0 |
| forged-citation | FAILED:INVALID_CITATION | 相同 | 4 / 2 |

成功轨迹（固定事件；SDK UUID、时间戳不进入该安全轨迹）：

```text
RUNNING
PROPOSED:CALL_TOOL -> TOOL:OK -> REFERENCE:MANUAL-V1-SECTION-2
PROPOSED:ASK_USER -> WAITING_FOR_USER -> EXECUTION_RELEASED
CONTEXT_REBUILT -> REPLY_ACCEPTED -> QUEUED -> RUNNING
PROPOSED:CALL_TOOL -> TOOL:OK -> REFERENCE:MANUAL-V1-SECTION-3
PROPOSED:ANSWER -> COMPLETED
```

断言包括：两候选事件完全一致；每次推理至多一个工具动作；模型订阅数等于已提交动作数，没有 maxIters 导致的额外总结调用；等待时执行调用已经返回；JSON 序列化后创建不同 Journal 实例并新建模型/SDK Agent；runId、原始问题、旧引用及累计步数保留；补答后总步数从 2 增到 4；错误 runId/问题 ID/版本及二次消费被拒绝；引用必须已通过工具取回；未知/写/跨资产/参数负例没有执行读取 handler。

## 改动规模和安全复用比较

按当前保留测试文件的连续代码区间计数（含空行/注释，用于理解适配规模，不当性能指标）：

| 比较项 | 演进旧循环方向 | AgentScope ReAct 方向 |
|---|---|---|
| 候选特有调度代码 | execute 内约 9 行（while、complete、propose、call） | execute 内约 55 行（Toolkit bridge、Hook、Builder/call） |
| 模型接口 | 直接复用 AgentScopeStep；SDK 不掌握工具执行 | fake Model 同决策，转为原生工具块；需 SDK 工具桥接 |
| Gateway 注册/只读/资产/参数检查 | 直接复用生产 Gateway | bridge 仍调用同一生产 Gateway；未知/缺参动作在 Hook 先交 Gateway 拒绝 |
| 引用、等待问题、累计预算 | 同一测试 Journal | 同一 Journal，不能省去应用业务状态 |
| 循环唯一性 | 单一 while 控制动作 | SDK 控制动作；外部只启动/重建执行轮次 |
| 旧 EvidenceManager/DecisionGuard | 可留在 legacy 策略分支，当前回归保护 | 仍需业务适配，SDK 不替代这些规则/事实检查 |
| 原型中持久恢复 | JSON 快照重建实例 | JSON 快照重建实例，未采用 SDK stateStore |

整个测试文件 354 行；固定输入、fake Model 和 Journal 为共用部分。这个比较只支持“当前链路接入成本”的判断，不能推广为整个 SDK、流式处理或未来功能的性能优劣结论。Hook 在本机 API 标为 deprecated，当前能编译运行；若未来选 SDK 需要另做 Middleware/流式事件的迁移评估。

## 失败记录与修正

首次 `clean test` 编译成功但 SDK 分支未完成：fake ToolUseBlock 只填 input，未填原始 content；SDK 累积工具调用时生成 `{}` 的 content，参数校验报缺 `queryKey`，重复六次后触发额外总结调用。第二轮诊断确认 Gateway handler 没有执行。修正为 ToolUseBlock 同时提供 input 与完整 JSON content，并让未知/缺参负例在 Hook 经 Gateway 拒绝，避免 SDK 先吞掉应用错误语义。

未删除失败标准或放宽事件断言；修正后两次 `clean test` 成功。失败/诊断原始日志留在忽略目录 `outputs/validation/01-01-spike-first.log`、`01-01-spike-debug.log`、`01-01-spike-debug2.log`；可复现成功证据以上述源码、固定输入、hash 和命令为准。

## 局限

- 测试只是协议/调用控制比较；不是实际 RAG、真实模型质量、生产 WAITING API 或数据库原子性验收。
- 旧 Gateway scope 强制 DiagnosisRequest，测试桥接使用 SPIKE-CAR 与 queryKey 标识；无资产自由中文检索是 CONTRACTS 约定的后续改造，不能从此桥接宣称已经支持。
- 同进程 JSON 重建不证明 PostgreSQL 检查点、跨进程崩溃恢复、租约/CAS、幂等/竞争、取消或过期证据重验。
- 原型没有测全部风险策略、SSE/流式、SDK stateStore；正式产品验收仍按 01-02 至 05-03 分层进行。

选择与后续文件影响见 [ADR-runtime.md](ADR-runtime.md)。
