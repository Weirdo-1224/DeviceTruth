# DeviceTruth V0.2｜S0 真实仓库审计报告

> 状态：**NOT_STARTED（待 M0 前半段填写，不代表已审计）**
>
> 仅写亲自查看的真实文件/代码/命令输出。原方案的 42 项测试通过属于历史报告。

## A. 仓库与安全基线

| 项目 | 实际结果 | 证据命令/路径 |
|---|---|---|
| 仓库根目录 | 待核实 | `pwd` 或 PowerShell `Get-Location` |
| 当前分支 | 待核实 | `git branch --show-current` |
| 当前 HEAD commit | 待核实 | `git rev-parse HEAD` |
| 现有 tag | 待核实 | `git tag --list` |
| 工作树已修改文件 | 待核实 | `git status --short` |
| 未跟踪文件 | 待核实 | `git status --short` |
| 安全回退依据 | 待核实 | 不自动 commit/reset/clean |

## B. 环境与基线测试

| 测试 | 命令（按实际仓库替换） | 退出码/通过数 | 证据/阻塞 |
|---|---|---|---|
| Java | `java -version` | 未运行 | 待填 |
| Maven | `mvn -version` | 未运行 | 待填 |
| 单测 | `mvn test` | 未运行 | 待填 |
| 打包 | `mvn package` | 未运行 | 待填 |
| CLI A | 实际 JAR + `--scenario=A --reasoner=scripted` | 未运行 | 待填 |
| CLI B | 实际 JAR + `--scenario=B --reasoner=scripted` | 未运行 | 待填 |
| CLI C | 实际 JAR + `--scenario=C --reasoner=scripted` | 未运行 | 待填 |
| CLI D | 实际 JAR + `--scenario=D --reasoner=scripted` | 未运行 | 待填 |
| E 相关负例 | **现有测试类方法，不是 CLI E** | 未运行 | 待填 |

> 请记录命令执行日期、JDK/Maven 版本、测试报告路径、失败堆栈摘要。不要用先前报告的“42项通过”填充今天的结果。

## C. 真实源码契约（每行需填写类路径/方法/签名）

| 核对项 | 实际签名或行为 | 文件位置（路径/行） | 与方案是否冲突 |
|---|---|---|---|
| CLI Main、NONE、退出 | 待审 | 待填 | 待判定 |
| pom.xml 依赖/boot-plugin Main | 待审 | 待填 | 待判定 |
| TaskOrchestrator.run() | 待审 | 待填 | 待判定 |
| RunReport/DiagnosisResult/RunStatus | 待审 | 待填 | 待判定 |
| StateManager 创建位置 | 待审 | 待填 | 待判定 |
| Reasoner 子类可变字段和实例化 | 待审 | 待填 | 待判定 |
| AssetRepository.profile/history | 待审 | 待填 | 待判定 |
| RuleRepository.query | 待审 | 待填 | 待判定 |
| CapabilityGateway.execute | 待审 | 待填 | 待判定 |
| DiagnosisRequest/asOf | 待审 | 待填 | 待判定 |
| Event 类型与 RunEvent.data | 待审 | 待填 | 待判定 |
| ActionProposal/Hypothesis/Guard | 待审 | 待填 | 待判定 |
| 实际 scenarios.json 和 fixture | 待审 | 待填 | 待判定 |

## D. 数据与时间语义

- `CAR-B / COMM_TIMEOUT` 是否真实存在：**待核实**。
- 固定模拟业务时间、现场证据 `observedAt/validTo`：**待核实**。
- A/B/C/D 的 `assetId / domain / symptomCode / diagnosisAsOf`：**待逐场景列出**。
- 证据 `evidenceId/sourceRef/sourceType` 的真实字段结构：**待核实**。
- `CAR-B` 与工业 `SC-0245-B` **不可混用**。

## E. 假设调查缺口

- 是否存在 `PROPOSE_HYPOTHESIS`：待确认源码位置。
- `SUPPORTED` 到达路径（如存在）：待确认源码与真实轨迹。
- `INVALIDATED` 规则：待确认。
- 为什么 S1 不修复、S2 如何验收：记录实际证据，不做超范围重构。

## F. 启动方案与后续契约待决策

1. 保留旧 CLI fat JAR 主类：待验证。
2. Server 独立 Main 推荐启动命令：待确定并在 M1 实际验证。
3. Repository 缺少的 API：列真实方法，不新增虚构声明。
4. 运行/Session 限制：待 M0 后半段冻结。
5. 风险/疑点及最小修复建议：待填。

## G. M0 审计交付与 Codex Review

- DeepSeek 自查是否完成：**否**。
- Codex Review：`reviews/M0.md`（待生成；同时审查审计事实与契约冻结）。
- M0 状态：**TODO**。
- 可以进入 M0 契约冻结子阶段：**否，等待本报告由真实命令和源码证据填充**。
- 可以进入 M1：**否，等待 M0 完整交付和独立 Review PASS**。
