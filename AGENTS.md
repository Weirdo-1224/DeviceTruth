# 仓库贡献指南

## 项目结构与核心模块

DeviceTruth 使用 Java 21、Spring Boot 和 Maven，提供非 Web 模式的 CLI。源码位于 `src/main/java/io/devicetruth/`：

- `app/`：`DeviceTruthApplication` 负责启动和装配，`CliOptions` 解析参数。
- `core/reasoning/`：`Reasoner` 根据状态提出 `ActionProposal`，`ScriptedReasoner` 支持离线推理。
- `core/runtime/`：`TaskOrchestrator` 控制单步循环、执行预算和事件记录。
- `core/capability/`：`CapabilityGateway` 注册只读能力，校验参数和访问范围。
- `core/evidence/`、`core/state/`、`core/decision/`：`EvidenceManager` 重建事实并使旧依赖失效，`StateManager` 提交状态版本，`DecisionGuard` 校验决策。
- `adapter/`：`JsonRepositories` 读取数据，`QwenReasoner`、`AgentScopeStep` 对接模型，`RunReportWriter` 保存报告。

测试位于 `src/test/java/io/devicetruth/`；日志配置位于 `src/main/resources/`。`datasets/` 保存模拟场景和资料，`skills/automotive-demo/rules.json` 保存规则，两者随 JAR 打包。

## 构建、运行与测试命令

在仓库根目录使用 JDK 21、Maven 3.9+；先用 `mvn -version` 核对实际 JDK。`.mvn/maven.config` 将依赖缓存放在 `.m2/repository`。

| 命令 | 用途 |
| --- | --- |
| `mvn -q test` | 运行全部离线测试 |
| `mvn -q '-Dtest=AcceptanceTest' test` | 单独运行核心验收测试 |
| `mvn -q package` | 执行测试并生成可运行 JAR |
| `java -jar target/devicetruth-core-0.1.0.jar --help` | 查看 CLI 参数 |
| `java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted` | 离线运行场景 B，无需密钥 |
| `java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=qwen` | 使用真实 Qwen 运行场景 B |
| `python scripts/verify_cli.py` | 核验打包后 CLI 的 A–D 场景及报告 |
| `python scripts/verify_online.py` | 核验真实 Qwen 调用、场景 B 结果和运行轨迹 |

两个脚本需要 Python 3 和最新 JAR；在线运行还需要 `DASHSCOPE_API_KEY`、网络和模型额度。修改数据或规则后重新打包。报告保存在 `outputs/runs/<runId>/`。

## 实际运行链路与架构边界

`DeviceTruthApplication.execute()` 解析参数、读取场景和 JSON 数据，装配网关及推理器，然后调用 `TaskOrchestrator.run()`。每轮读取状态，由 `Reasoner.propose()` 提出一个动作；需要查询时，经 `CapabilityGateway` 读取资料，再由 `EvidenceManager` 校验证据、重建事实；提出决策时，由 `DecisionGuard` 核验。`StateManager` 提交本轮状态后继续循环或结束，最后由 `RunReportWriter` 写出结果、状态、证据和事件。

模型只能提议动作，不能自行调用工具或修改事实。所有能力保持只读，保留 `SIMULATED` 标记；密钥只通过环境变量提供，不写入源码、日志或报告。

## 编码与测试规范

使用 UTF-8、四空格 Java 缩进，沿用周边风格和中文注释，避免无关格式调整。类型使用 `PascalCase`，方法和变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`；优先使用不可变记录和明确契约。当前未配置格式化或静态检查插件。

测试使用 JUnit 5 和 AssertJ，类名以 `Test` 结尾，方法名描述行为，例如 `casesABCDProduceEvidenceConstrainedOutcomes`。修改契约、证据处理或决策逻辑时补充回归测试。当前没有覆盖率数值门槛；离线测试与真实模型验收分别报告，无法验证时不得标记通过。

## 提交与协作要求

现有 Git 历史仅有 `init` 和中文摘要的 `docs:` 提交；后续使用简短、范围明确的提交信息，例如 `fix: 修复过期证据校验`。PR 说明行为变化、关联问题或规格任务，并列出验证命令及结果；涉及 CLI 时附脱敏报告片段。

开发前阅读 `docs/DeviceTruth_Core_V0.1_Spec_Pack/` 下的 `spec.md`、`task.md`、`state.md`，按实际验证更新进度。生成报告、依赖缓存和凭据不提交。

**后续每次开发完成时，必须用通俗中文解释关键代码及实际运行链路**：说明改了什么、关键类和方法分别负责什么、输入如何经过各模块形成输出，并给出实际执行的命令、结果及未验证项。结合具体场景说明，不只罗列类名或抽象术语。
