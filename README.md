# DeviceTruth Agent Core V0.1

DeviceTruth 是可运行的 Java 单 Agent Core。每步读取独立的结构化状态，由 Reasoner 提出一个动作；Gateway 执行至多一个只读能力；Evidence Manager 重建事实并使旧依赖失效；State Manager 提交新版本；Decision Guard 决定是否允许结论。模型不能修改事实、写证据或自行执行工具。

**资料与规则均为 `SIMULATED`，仅用于工程验收，不能指导实际车辆或工业设备维护。** 离线验收及 OpenAI 兼容接口的真实场景 B 已通过（6 步、DEMO-D-120B，共 2 次独立运行）；全部 MUST 任务已完成，整体 DONE。千问 DashScope 适配路径尚未被真实执行过，属已知残留风险，记录在 state.md 的设计决定 16，不是阻塞项。最新命令、报告路径与进度见 [state.md](docs/DeviceTruth_Core_V0.1_Spec_Pack/state.md)。

技术版本锁定为 Java 21、Maven 3.9+、Spring Boot 3.5.16、AgentScope Java 2.0.4。AgentScope 使用 `agentscope-core`、DashScope 和 OpenAI 模型扩展，不引入 harness 或另一个编排框架。版本依据 Maven Central 元数据与官方源码验证，见 [API Spike](docs/research/agentscope-spike.md)。新增扩展首次构建需要下载依赖。

## 启动与构建

在已有 `DeviceTruth` 根目录运行，不要创建第二层同名目录。需要 JDK 21 和 Maven 3.9+；首次 Maven 构建需要下载依赖，以后可用已缓存依赖离线运行。`.mvn/maven.config` 将依赖缓存限制到本项目 `.m2/repository`，不改用户全局 Maven 设置。运行 JAR 不需要 Python；两个验收辅助脚本需要 Python 3。

Windows PowerShell：本工作区已下载微软 JDK 21 到 `.tools/`。以下只改变当前终端进程，不安装全局组件，也不修改 PowerShell 执行策略。

```powershell
Set-Location D:\Java_Project\DeviceTruth
$env:JAVA_HOME = (Get-ChildItem -Directory .\.tools -Filter 'jdk-21*' | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
mvn -version
mvn -q test
mvn -q package
java -jar target/devicetruth-core-0.1.0.jar --help
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted
python scripts/verify_cli.py
```

在其他电脑上，先从 JDK 厂商官方渠道安装/解压 JDK 21，再将 `JAVA_HOME` 指向实际目录；`.tools/`、`.m2/` 和 `target/` 均不作为源文件交付。若 `scripts/use-java21.ps1` 被执行策略拒绝，使用上面的内联命令，无需更改策略。

bash（在已经安装 JDK 21 的 Linux/macOS 环境）：

```bash
cd /path/to/DeviceTruth
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
mvn -q test
mvn -q package
java -jar target/devicetruth-core-0.1.0.jar --help
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted
python3 scripts/verify_cli.py
```

无 Key 的 `scripted` 模式不会调用外部 LLM。规则和 fixtures 会打入 JAR；修改 JSON 后需重新打包。演示时间固定为 `datasets/scenarios.json` 中的 `2026-11-03T04:00:00Z`，确保复现不受系统日期影响。

## CLI 与运行产物

```text
--help / -h
--scenario=A|B|C|D             默认 A
--reasoner=scripted|qwen|openai 默认 scripted
--max-steps=12                 范围 1..200，包含假设和决策等全部动作
--max-repeated-actions=2       至少 1，不得大于 max-steps
```

采用 `--name=value`；未知参数、重复参数、非法预算或模式返回退出码 2。正常诊断结果（包括缺证、冲突、转人工）返回 0；模型或技术失败返回 2。减少总步数时请相应设置重复预算，例如 `--max-steps=1 --max-repeated-actions=1`。

每次实际诊断打印 `report=<绝对路径>`，并在 `outputs/runs/<runId>/` 写入：

| 文件 | 内容 |
|---|---|
| `result.json` | 结果、规则、引用、缺口、终止原因、步数、状态版本、reasoner、SIMULATED 标记 |
| `final_state.json` | 事实、假设与失效原因、证据需求、候选规则依赖、动作历史和预算 |
| `evidence.json` | 与状态分开保存的原始记录，含资产、来源、有效时间、记录时间和完整性 |
| `events.jsonl` | 输入快照、模型尝试、动作、工具结果、Guard 判定、版本提交和终止 |

离线 CLI 的实际结果：

| 案例 | 结果 | 最终规则 | 步数 |
|---|---|---|---|
| A：稳定配置 | `RESOLVED` | `DEMO-D-120A` | 7 |
| B：历史更换 | `RESOLVED` | `DEMO-D-120B` | 7 |
| C：当前同级证据冲突 | `CONFLICT` | 无 | 7 |
| D：缺少现场确认 | `NEED_EVIDENCE` | 无 | 6 |

例如 B 的 `result.json` 包含：

```json
{
  "outcome": "RESOLVED",
  "assetId": "CAR-B",
  "appliedRuleIds": ["DEMO-D-120B"],
  "supportingEvidenceIds": ["CAR-B-FIELD-1"],
  "stepsUsed": 7,
  "stateVersion": 7,
  "reasoner": "scripted",
  "dataNature": "SIMULATED",
  "runStatus": "COMPLETED"
}
```

这是节选；实际结果还有任务 ID、asOf、缺口、警告和停止原因。C 不选择任一版本规则，D 不把维修记录当成当前现场确认。`selectedRuleId` 可以表示待门禁通过的候选；只有 `result.appliedRuleIds` 表示已经核验采用的规则。

## 本地私有模型配置（推荐）

项目根目录的 `devicetruth.local.properties` 已加入 Git 忽略规则，不打进 JAR。当前工作区已生成该文件；新克隆项目可从 [配置模板](devicetruth.local.properties.example) 复制一份。用 UTF-8 保存，在等号后直接填写值，不加引号：

```properties
OPENAI_API_KEY=在这里填 DeepSeek API Key
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-flash

# 使用千问时填写这一项
DASHSCOPE_API_KEY=
```

DeepSeek 地址和模型名已预填，只需补 `OPENAI_API_KEY`；其他兼容服务按其要求修改地址与模型。然后从项目根目录运行，无需设置密钥环境变量：

```powershell
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=openai
python scripts/verify_online.py --reasoner=openai
```

千问填写 `DASHSCOPE_API_KEY` 后，使用 `--reasoner=qwen`。配置读取优先级为：**私有文件中的非空值 → 环境变量**；文件不存在或字段留空时保留环境变量用法。文件按进程的当前工作目录查找，修改后重启运行即可，无需重新打包。配置加载只发生在在线模式首次推理时，离线 scripted 不读取此文件；无法读取或解析时输出 `MODEL_CONFIGURATION_UNREADABLE`，不输出文件内容。

2026-11-05 已使用实际填写的私有配置执行 `python scripts/verify_online.py --reasoner=openai`，B 以 `RESOLVED` 采用 `DEMO-D-120B`，6 次模型提议、5 次受控能力查询、6 次状态提交和 1 次 Guard 放行；报告位于 `outputs/runs/684b5a60-b3d3-4a7e-ae28-87af7b248d6b/`。这是外部真实模型调用，独立于本机 HTTP 替身测试。调查缺口在工具刷新后持续保留；过滤查询不会被当成全资产调查完成，历史原始记录按程序重建的事实时间区间解释。

## 真实 Qwen 模式

在上述私有文件中填写 `DASHSCOPE_API_KEY`，也可通过自己的凭据管理方式在当前进程配置同名环境变量。密钥不写入源码、日志或报告；默认模型为 DashScope `qwen-plus`。

PowerShell：

```powershell
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=qwen
python scripts/verify_online.py
```

bash：

```bash
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=qwen
python3 scripts/verify_online.py
```

`verify_online.py` 会真实调用模型并核验 B 的规则与轨迹，需要 Key、网络和模型额度。它不属于 `mvn test`，不能用离线替身测试替代。无 Key 时脚本明确报告 `BLOCKED_BY_ENV`，不尝试模型调用。直接以 qwen 启动时，缺 Key 会写 `FAILED` 报告，`outcome=null`、`stopReason=DASHSCOPE_API_KEY_MISSING`，不会切换为 scripted。

一个提议采用一次 `Model.stream(messages, emptyTools, options)` 订阅。SDK 自动重试被明确限制为 1 次尝试，调用超时 45 秒；非法 JSON/协议最多额外重试 1 次，仍非法则 `FAILED`。工具只在外层执行。`ReActAgent.call()` 是内部完整循环，不能视作这里的一步；源码依据见 API Spike。

## OpenAI 兼容协议模式（可接 DeepSeek）

使用 `--reasoner=openai`，通过私有文件或环境变量配置以下三个同名字段，均为必填：

| 配置字段 / 环境变量 | 用途 | DeepSeek 示例 |
|---|---|---|
| `OPENAI_API_KEY` | 该服务的 API Key | 使用 DeepSeek Key |
| `OPENAI_BASE_URL` | API 基础地址，包含服务要求的路径前缀 | `https://api.deepseek.com` |
| `OPENAI_MODEL` | 服务支持的模型 ID | `deepseek-flash`，以服务当前提供的模型为准 |

接口为 `POST <OPENAI_BASE_URL>/chat/completions`，会处理基础地址末尾的斜杠。填写基础地址，不要填写完整的 `/chat/completions` 地址；若服务要求 `/v1`，将 `/v1` 写入基础地址，例如 `https://api.openai.com/v1`。使用 Bearer 认证、非流式请求和 `response_format={"type":"json_object"}`；所选服务与模型需支持这些 Chat Completions 字段。这里不使用 Responses API 或模型自动执行工具。

填写私有文件后直接运行即可。以下环境变量方式仍可使用；如果已经配置 `DEEPSEEK_API_KEY`，可按以下方式映射（私有文件的非空值优先）。PowerShell（先按上文设置 JDK 21）：

```powershell
$env:OPENAI_API_KEY = $env:DEEPSEEK_API_KEY
$env:OPENAI_BASE_URL = 'https://api.deepseek.com'
$env:OPENAI_MODEL = 'deepseek-flash'
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=openai
python scripts/verify_online.py --reasoner=openai
```

bash：

```bash
export OPENAI_API_KEY="${DEEPSEEK_API_KEY:?请先配置 DeepSeek Key，或直接配置 OPENAI_API_KEY}"
export OPENAI_BASE_URL=https://api.deepseek.com
export OPENAI_MODEL=deepseek-flash
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=openai
python3 scripts/verify_online.py --reasoner=openai
```

千问仍使用 `--reasoner=qwen`、`DASHSCOPE_API_KEY` 和 `qwen-plus`，与这三个变量互不替代。`verify_online.py` 不带参数时继续验收千问，也支持显式 `--reasoner=qwen`。两种模式分别记录 `reasoner=qwen/openai`，均沿用 45 秒超时、每次请求一次尝试、非法动作最多纠正一次和程序端证据门禁。

缺少配置时，直接运行 JAR 会生成 `FAILED` 报告、`outcome=null`，错误码分别为 `OPENAI_API_KEY_MISSING`、`OPENAI_BASE_URL_MISSING`、`OPENAI_MODEL_MISSING`；非法地址为 `OPENAI_CONFIGURATION_INVALID`。地址只允许 HTTP(S)，不接受 URL 内凭据、查询参数或片段；配置值和服务端错误正文不写入报告。在线脚本由 JAR 统一读取私有文件及环境变量，配置缺失/不可读/非法时根据失败报告返回 `BLOCKED_BY_ENV`，不发模型请求。协议依据见 [DeepSeek 接入文档](https://api-docs.deepseek.com/) 和 [JSON 输出要求](https://api-docs.deepseek.com/guides/json_mode/)。

## 结构、证据和替换接口

下表路径以 `src/main/java/io/devicetruth/` 为基准：

| 位置 | 责任 |
|---|---|
| `core/reasoning/` | 五类 ActionProposal、不可写状态的 Reasoner、可见状态驱动的 ScriptedReasoner |
| `core/runtime/TaskOrchestrator.java` | 单步循环、重复/总步数预算、提交次序和可追踪终止 |
| `core/state/` | 不可变快照、版本检查、原子提交与不可改写的证据存储 |
| `core/evidence/` | 去重、归属和完整性校验、时间重建、冲突、依赖失效 |
| `core/decision/` | 有限条件与证据门禁，输出程序核验过的规则建议 |
| `core/capability/` | 注册/发现、schema/强类型校验、只读执行、结构化错误及三个 Repository 接口 |
| `adapter/JsonRepositories.java` | JSON 适配，可替换成数据库/API 实现 |
| `adapter/AgentScopeStep.java` | AgentScope DashScope / OpenAI Chat Completions 单步模型调用 |
| `adapter/StructuredReasoner.java`、`QwenReasoner.java`、`OpenAiReasoner.java` | 共用动作协议、严格解析及有限重试，分别装配模型配置 |
| `adapter/ModelConfiguration.java` | 首次模型调用时读取工作目录中的私有配置，环境变量补缺；加载失败只返回稳定错误码 |

`skills/automotive-demo/rules.json` 是轻量业务规则包，不是 AgentScope 标准 SKILL.md 加载器。`datasets/fixtures/` 存 A–D 虚构资料，不含预填诊断答案。

工具为 `asset.profile.get`、`asset.history.query`、`evidence.search`、`evidence.get`、`diagnosis.rules.query`。Core 经 Gateway 获取数据；模型不能传入文件路径。越权资产、未知记录、非只读能力、额外/错误参数均拒绝，工具异常为结构化错误。Guard 要求完成资产历史查询、无过滤的证据发现和已发现记录获取，避免只看第一条确认就结束。

事实由记录的 `payload.subject/predicate/value` 映射。规则仅支持 `EQ`、`IN` 和所需证据类型。当前事实采用通用来源语义：`FIELD_CONFIRMATION` 优先于 `REPLACEMENT` 与出厂记录；同类取明确的 `authority`；同级重叠且取值不同保留 `CONTESTED`，不会按 `recordedAt` 随意覆盖。更换事件关闭前一配置的事实区间。有效区间为 `[validFrom, validTo)`；未来有效或在 asOf 后才记录的证据不支持当时结论。依赖事实/证据被替代、争议或时间排除后，保留原假设并记录失效原因。

跨领域替换只需提供 Repository、字段、规则并向 Gateway 注册；`ExtensibilityTest` 用 PLC、不同组件/属性和值与 `IN` 条件，通过同一 Core。V0.1 不提供完整工业产品或动态 DSL。每条记录当前承载一个字段断言；状态存于内存并落盘，不支持跨进程恢复执行。

## 验证、排错与交接

`mvn -q test` (42 tests) 覆盖五类动作、JSON/ID/时间校验、Gateway、单步提交、预算、证据修订、Guard、A/B/C/D/E、模型协议替身、数据扰动和工业契约。新增本机 HTTP 替身测试检查 OpenAI 兼容请求的路径/认证/JSON、配置错误、有限重试、HTTP 失败脱敏，以及 B 经真实 SDK 和 Core 的完整链路；这些不是外部真实模型验收。扰动测试真实写出和读取临时 JSON，改变 B 的确认值/移除确认/注入冲突，Core 不变。`scripts/verify_cli.py` 另行启动打包 JAR，检查输出文件、事件序号、两种在线模式的缺 Key 失败，以及无环境变量时从私有文件连接本机模拟接口（不访问外部服务），汇总到 `target/cli-verification.json`。

- `release version 21 not supported`：检查 `mvn -version` 的 Java 路径，确保当前终端 JAVA_HOME 和 PATH 均指向 21。
- Maven 首次下载失败：确认可访问 Maven Central；不要把依赖下载失败写成测试通过。已缓存后可执行 `mvn -o -q test`。
- PowerShell 若拆分 Java `-D` 参数，请整体引用，如 `java '-Djava.io.tmpdir=target' -jar ...`。测试 JVM 已自动将临时文件限制在项目 `target/`。
- `MODEL_TRANSPORT_OR_TIMEOUT`：查模型尝试和技术失败代码，核验 Key、网络及服务；不记录凭据或原始异常详情。
- `NEED_EVIDENCE`：查缺口和 stopReason；预算/重复动作耗尽会保留原因。工具持续失败且无进展时可 `ESCALATE`。
- `CONFLICT`：需补充能够裁决的证据，版本专属规则不能绕过同级冲突。

下一会话先完整读 [spec.md](docs/DeviceTruth_Core_V0.1_Spec_Pack/spec.md)、[task.md](docs/DeviceTruth_Core_V0.1_Spec_Pack/task.md)、[state.md](docs/DeviceTruth_Core_V0.1_Spec_Pack/state.md)。三份文件统一保存在 `docs/DeviceTruth_Core_V0.1_Spec_Pack/`，仅维护这一套；构建与运行仍在项目根目录执行。待办是在线真实模型验收：配置所选服务环境后运行 `verify_online.py --reasoner=qwen` 或 `--reasoner=openai`，核验轨迹并分别记录 T07/T12、DoD 和 state；没有该证据不能宣告整体 DONE。

后续可选方向为数据库/API Repository、更多领域适配和任务恢复；V0.1 没有实现 UI、多 Agent、知识库、MCP 平台或设备控制。
