# DeviceTruth 领域 Skill 接入能力专项审查报告

- 审查日期：2026-11-27
- 审查方式：静态源码审查（未运行、未修改代码、未动数据库）
- 依据：当前仓库 `src/main/java/io/devicetruth/` 实际源码、`src/main/resources/` 配置与 Flyway 迁移、`skills/` 现有示例

> 说明：本文所有结论均以源码为准；凡标注"待运行验证"的条目，静态代码可确认存在但运行效果未经实机验证。

---

## 1. 总体结论

**当前 DeviceTruth 已具备接入标准领域 Skill 的完整能力，且已经实际接入运行。**

系统并非"计划支持 Skill"，而是已经实现了从 Skill 目录发现、严格 frontmatter 解析、安装授权（PostgreSQL 持久化）、run 级激活、references 按需读取、到把 Skill 内容注入推理器上下文的完整闭环，并已有两个内置 Skill（`automotive-diagnosis@1.0.0`、`factory-equipment@1.0.0`）随默认配置安装启用。

**支持程度与主要限制：**

- Skill 是纯 Markdown 领域指引（"怎么查"），通过 `activate_skill`/`read_skill_reference` 两个系统工具进入 Agent 上下文；**不需要修改任何 Java 源码即可新增 Skill**。
- 一个 run 只能激活一个 Skill，且激活后不可切换；Skill 只能**缩小**工具范围，不能扩大权限。
- Skill 的 `scripts/` 目录**不会被加载执行**（无脚本执行机制），只能作为交付物存放，实际运行不使用。
- 新增 Skill 的启用目前**只能通过服务端配置**（`devicetruth.skills.installations`）在启动时写入数据库，没有运行时管理 API；新增/升级 Skill 后需要重启服务。
- 设备事实工具（配置、维修史、观测）当前数据源以模拟/夹具数据为主（`data_nature=SIMULATED`），真实现场接入尚未实现。
- 知识库 RAG 已完整实现（向量+关键词 RRF 混合召回、原文片段按 run 授权读取），但只支持 Markdown/纯文本上传导入，PDF 未实现。

**最关键的阻塞项（对两位 Skill 开发者而言）：**

1. **新增 Skill 必须主开发者改一行服务端配置**（`devicetruth.skills.installations` 增加 `skillId@version`）才会被启用，Skill 作者无法自行启用。
2. **无脚本执行能力**：如果领域 Skill 依赖 `scripts/` 里的可执行逻辑（如备件兼容性计算脚本），当前系统不会运行它，需要把逻辑改写成 references 中的规则文本 + 现有工具查询，或等主开发者补能力。
3. **备件兼容性等"领域规则查询"没有专用工具**：现有工具只能查设备档案/配置/维修史/观测/知识库，兼容性规则应放进 RAG 知识库（作为文档）或 Skill references（作为指引），不能动态计算。

---

## 2. 系统实际调用链

```
用户输入（HTTP /api/conversations ... AgentConversationController）
  → GeneralAgentService：持久化 run（QUEUED→RUNNING），重建消息与引用上下文
  → SkillRuntime.Session（skills.open(...)）
       │  每次 step 重算：已安装 Skill 目录 → availableSkills
       │  已选 Skill → ActiveSkill（正文+evidence/risk/citation+references 清单）
       │  注入 activate_skill / read_skill_reference 两个工具
  → TaskOrchestrator.runAgent(...)（唯一主循环，TaskOrchestrator.java:248）
       │  每轮组装 GeneralReasoner.Context（含 availableSkills / activeSkill / tools / references）
       │  → GeneralReasoner.propose() 提出恰好一个动作
            · StructuredAgentReasoner（qwen/openai，StructuredAgentReasoner.java:23 协议提示词）
            · ScriptedAgentReasoner（scripted 默认，ScriptedAgentReasoner.java:27）
       │  动作校验后由主机执行（模型永远不能自己调工具）
       │    CALL_TOOL → CapabilityGateway.executeTool → 各工具实现 → PG 只读查询
       │    ASK_USER  → run 转 WAITING_FOR_USER，前端补答后续跑
       │    ANSWER    → AnswerGuard 校验（引用子集/风险/无资产不得确诊）
  → 结果/事件落 PG，SSE 推送前端
```

**各环节实现状态：**

| 环节 | 状态 | 依据 |
| --- | --- | --- |
| 用户请求 → run 状态机 | 已实现 | `GeneralAgentService`（QUEUED→RUNNING→WAITING/COMPLETED/FAILED/CANCELLED） |
| Skill 发现/注册 | 已实现 | `SkillRegistry.load`（SkillRegistry.java:39） |
| Skill 安装授权（PG 持久化） | 已实现 | `SkillInstallationRepository.installDefault`、`dt_skill_installation`（V14） |
| Skill 激活（模型或脚本触发） | 已实现 | `SkillRuntime.Session.activate`（SkillRuntime.java:117） |
| references 按需读取 | 已实现 | `SkillContextLoader.readReference`（SkillContextLoader.java:38） |
| Skill 内容注入推理上下文 | 已实现 | `GeneralReasoner.Context.activeSkill/availableSkills`（GeneralReasoner.java:42） |
| Reasoner 使用 Skill 指导调查 | 已实现 | `StructuredAgentReasoner` 协议第 28–30 行；`ScriptedAgentReasoner.java:28-33` 按 task 关键词自动激活 |
| Tool → 数据源 | 已实现（只读，模拟数据为主） | 见第 4 节 |
| RAG 检索 | 已实现 | `KnowledgeSearchService` 向量+关键词 RRF（KnowledgeSearchService.java:106-143） |
| Skill 脚本执行 | **未实现** | 全仓库无 Skill 脚本加载/执行代码；`SkillRegistry` 只登记 SKILL.md 与 references/*.md |
| 运行时 Skill 管理 API（安装/停用/热更新） | **未实现** | 仅启动时 `installDefault`；无 Controller 暴露安装接口 |

---

## 3. Skill 能力审查矩阵

| 能力 | 实现状态 | 源码依据 | 存在问题 | 是否阻塞接入 |
| --- | --- | --- | --- | --- |
| Skill 目录发现（根目录下一层子目录） | 已实现 | `SkillRegistry.load` SkillRegistry.java:50-77 | 只扫描一层；名称必须等于目录名 | 否 |
| 标准 `SKILL.md` frontmatter 解析 | 已实现（严格 YAML 子集） | `SkillManifestParser` SkillManifestParser.java:135 | 不支持注释/锚点/嵌套/多行块；9 个必填键；列表每键 ≤32 项、每项 ≤300 字符；description ≤1000 字符 | 否（但格式必须严格遵守） |
| 多 Skill 共存 | 已实现 | `skills/automotive-diagnosis`、`skills/factory-equipment` | — | 否 |
| Skill 选择与切换 | 部分实现 | `SkillRuntime.activate` SkillRuntime.java:117-135 | 每 run 只能激活一个，激活后不可换 | 否（设计如此） |
| 安装/授权持久化 | 已实现 | `dt_skill_installation`（V14）、`SkillInstallationRepository.installDefault` | 只有启动时按配置安装，无运行时管理 API；改安装需重启 | 轻微（需主开发者配合改配置） |
| references/ 按需读取 | 已实现 | `SkillContextLoader.readReference` SkillContextLoader.java:38 | 仅平铺 `.md`、文件名限 `[a-z0-9][a-z0-9._-]{0,127}`、≤32 篇、每篇 ≤256KB；不支持子目录 | 否 |
| Skill 正文进入模型上下文 | 已实现 | `SkillRuntime.Session.step` SkillRuntime.java:95-115 → `GeneralReasoner.Context` | 正文限 128KB；内容定位为"不可信领域指引" | 否 |
| Skill 声明工具并缩小权限 | 已实现 | SkillRuntime.java:126-129（四重交集：安装许可 ∩ 服务器上限 ∩ metadata.tools ∩ 会话基线） | Skill 声明的工具必须在 `devicetruth.skills.allowed-tools` 名单内，否则激活后不可用 | 是（新工具需主开发者加白名单） |
| Skill 动态指导 Reasoner | 已实现 | `StructuredAgentReasoner` 协议 StructuredAgentReasoner.java:28-30 | 依赖模型遵守提示词；scripted 离线推理器只按关键词匹配激活 | 否 |
| 按任务自动选 Skill（scripted） | 部分实现 | ScriptedAgentReasoner.java:28-33 | 仅关键词包含匹配，较粗糙；qwen/openai 模式下由模型自主 activate_skill | 否 |
| 新增 Skill 免 Java 改动 | 已实现 | Skill 工具（activate/read_reference）已通用注册 | 仅配置项 `devicetruth.skills.installations` 需追加 | 否 |
| Skill 版本管理与变更检测 | 已实现 | `dt_run_skill.document_digest` SHA-256（SkillRuntime.java:176-187）；文件被改动则 `SKILL_CHANGED` 拒绝 | 版本号变化需同步改安装配置 | 否 |
| Skill 内容作为答案引用来源 | 部分实现 | `ActiveSkill` 进上下文；`GET /api/runs/{id}/skill`（AgentConversationController.java:54） | Skill 内容本身不可作为 answer 的 referenceId；可引用来源仍是工具登记的事实引用 | 否 |
| scripts/ 执行 | **未实现** | 无对应代码 | `scripts/` 目录在发现阶段被完全忽略，不报错也不加载 | 视 Skill 设计而定（见第 6 节 B） |
| 多轮追问（ASK_USER/补答） | 已实现 | `TaskOrchestrator.runAgent` WAITING_FOR_USER 语义 | — | 否 |
| 开放式问题调查（非预定义场景） | 已实现 | 通用三动作协议（CALL_TOOL/ASK_USER/ANSWER）；非场景驱动 | scripted 模式行为是写死的演示策略，真实开放调查需 qwen/openai 模型 | 否（注意默认 reasoner） |
| 领域规则/备件兼容专用工具 | **未实现** | 无此工具 | 只能经 RAG 文档或 references 文本承载 | 是（若需要结构化计算） |

---

## 4. 现有 Tool 接口清单

所有工具都注册在 `agentCapabilityGateway`（ServerConfiguration.java:103-114），全部只读，模型只能调用目录中列出的工具。基线授权 = 网关全部工具名（GeneralAgentService.java:433-434）；Skill 激活后取交集缩小。

| 工具名 | Java 实现 | 输入 | 输出 | 权限限制 | 状态 |
| --- | --- | --- | --- | --- | --- |
| `search_knowledge` | `KnowledgeTools.search`（knowledge/KnowledgeTools.java:69） | `query`(必填,≤2000字符), `filters{domain,deviceModel,documentId}`, `limit`1-50 | `hits[{referenceId,documentId,version,chunkId,title,section,similarity,...}]`，并登记 KNOWLEDGE_DOCUMENT 引用 | 归属过滤在召回前；只能查本 owner/project | 已实现（RAG 混合召回） |
| `get_document_excerpt` | `KnowledgeTools.excerpt`（KnowledgeTools.java:81） | `documentId`,`chunkId`（UUID） | 原文片段（截断 2000 字符）+ 引用 | **只能读本 run 已通过 search 取回的片段** | 已实现 |
| `get_asset` | `AssetTools.asset`（application/agent/AssetTools.java:90） | `assetId?`（缺省用 run 选定资产） | 资产身份+组件清单+dataNature | 越权/未登记统一 ASSET_NOT_FOUND | 已实现（PG） |
| `get_configuration` | `AssetTools.configuration`（AssetTools.java:112） | `attribute`(必填), `assetId?`, `componentId?`, `asOf?` | 配置记录（来源/有效期/核实状态/authority）、installationStatus、conflict、requiredChecks | 必须指明 attribute；无现场核验记录时一律 UNCONFIRMED | 已实现（PG，V8 表） |
| `get_maintenance_history` | `AssetTools.maintenance`（AssetTools.java:142） | `assetId?`, `componentId?`, `asOf?` | 维修/更换记录（before/after、来源、核实状态） | 同上归属校验 | 已实现（PG，V8 表） |
| `get_observations` | `ObservationTools.query`（application/agent/ObservationTools.java:56） | `assetId?`, `signal?`, `from?`, `asOf?`, `quality?` | 模拟观测条目列表；无数据时明确"未读取到现场数据" | 只读已导入模拟观测，不连接真实设备；时间范围不得超出 run 的 asOf | 已实现（**数据为 SIMULATED**） |
| `generate_diagnosis_report` | `AgentReportTools.reportOutcome`（application/agent/AgentReportTools.java:46） | 无参数 | 本 run 的聚合报告（状态/步数/引用/工具轨迹/待答/答案摘要） | 只读本 run 持久化白名单视图 | 已实现 |
| `activate_skill` | `SkillRuntime.Session`（skill/SkillRuntime.java:87） | `skillId` | 选中 Skill（持久化到 `dt_run_skill`） | 只能选已安装启用版本；每 run 一次 | 已实现 |
| `read_skill_reference` | 同上（SkillRuntime.java:89） | `name` | 单篇 reference 全文 | 仅已选 Skill 的已登记文件；内容摘要钉入 `dt_run_skill_reference` | 已实现 |

**对领域 Skill 的适用性：**

- `industrial-configuration-investigation`（技改追溯、备件兼容）：`get_configuration` + `get_maintenance_history` 完全对口（来源类型含 FACTORY/REPLACEMENT/CONFIGURATION，有效期区间、冲突判定都已实现）；备件兼容性无专用工具，规则应写成知识库文档（RAG 可查）或 references 决策规则。
- `ev-charging-triage`（充电异常）：`get_observations`（充电信号）、`search_knowledge`（充电手册）、Skill 排查指引，全部可用；注意观测数据是模拟的。

---

## 5. 数据与知识库接入现状

**PostgreSQL（Flyway V1–V16，`product` profile 启动时迁移）真实存储：**

- `dt_agent_conversation` / `dt_agent_run` / 消息、事件、检查点（V1、V5、V12、V13、V16）：会话与 run 全生命周期。
- `dt_uploaded_file` / `dt_knowledge_document` / `dt_knowledge_chunk`（V2、V4、V6、V7）：上传的 Markdown/TXT 原文切块、向量（1024 维 cosine，profile 化，换模型必须新迁移重建索引）。
- `dt_asset` / `dt_asset_component` / `dt_asset_configuration` / `dt_asset_maintenance`（V8、V10、V11）：设备身份、组件、配置与维修结构化历史，含 source_type、valid_from/to、verification_status、authority、data_nature。
- `dt_asset_observation`（V15）：模拟观测数据。
- `dt_run_reference` / `dt_run_skill` / `dt_run_skill_reference`（V9、V14）：run 级可审计引用与 Skill 选择。
- `dt_skill_installation`（V14）：Skill 安装授权。

**RAG：已实现。** `KnowledgeImportService` 做 MD/TXT 章节切片与向量化落库；`KnowledgeSearchService` 做归属过滤 + 向量/关键词 + RRF 融合（KnowledgeSearchService.java:106-143）；`EmbeddingClient` 支持真实供应商或离线确定性向量。PDF **未实现**。

**追溯与引用机制：已实现。** 每条设备事实查询在 run 内登记引用（`AssetTools.registerReferences`，AssetTools.java:263-282），知识检索自动登记 KNOWLEDGE_DOCUMENT 引用；答案必须引用这些已登记 referenceId（AnswerGuard 校验，core/decision/AnswerGuard.java:78）；Skill 选择与 reference 读取也带 SHA-256 摘要钉存。

**仍依赖静态/模拟数据的部分：**

- 设备主数据默认**不导入**，需显式 `devicetruth.assets.seed-fixtures=true` 才从打包夹具（默认 `CAR-B`）灌入（ProductConfiguration.java:167-175）。
- `get_observations` 只读模拟观测，无真实设备接入。
- 默认 `data_nature=SIMULATED`，真实现场核验（FIELD_VERIFIED）数据通路已定义但依赖真实录入。
- legacy CLI 诊断链路（`datasets/` JSON、`ScriptedReasoner`）与 Skill/通用 Agent 是两条独立链路，Skill 只作用于产品 Agent。

**新增领域知识/设备数据的步骤：**

1. 文档类：走 `POST /api/files` 上传 Markdown（≤1MB）→ 异步导入切片向量化 → 即可被 `search_knowledge` 检索。这是 Skill 开发者可以自助完成的部分。
2. 设备结构化数据：需要写入 `dt_asset*` 系列表（SQL 或后续可能的导入 API，目前无设备数据导入 API，仅夹具 seeder）。

---

## 6. 领域 Skill 开发规范建议

### A. 当前系统已经支持、可以直接采用的规范

1. **目录位置**：`<skills 根>/<skill-id>/`，skill-id 必须是小写字母数字连字符、且与目录名完全一致（SkillMetadata.java:18、SkillRegistry.java:72）。仓库默认根为工作目录下的 `skills/`（`devicetruth.skills.root`，可用 `DEVICETRUTH_SKILLS_ROOT` 覆盖）。**注意**：Skill 从文件系统加载（pom.xml:32 虽把 skills 打进 JAR，但运行时不读 JAR 内资源），JAR 部署时必须通过环境变量指向真实目录。

2. **目录结构**（可直接加载）：
   ```text
   industrial-configuration-investigation/
   ├── SKILL.md            # 必填，≤128KB，UTF-8 无 BOM
   └── references/          # 可选，平铺 .md，≤32 篇，每篇 ≤256KB
       ├── domain-knowledge.md
       ├── investigation-guide.md
       ├── evidence-requirements.md
       ├── decision-rules.md
       └── safety-guidelines.md
   ```
   `scripts/` 目录可以存在（不会被报错），但**当前不会被加载执行**——不要在里面放运行必需逻辑。

3. **SKILL.md frontmatter 必填 9 个键**（SkillManifestParser.java:30-33），只允许这些键，只支持"顶层标量 + 两空格缩进的 `- ` 列表"的 YAML 子集：
   - `name`、`description`(≤1000字符)、`version`（`x.y.z` 三段数字）
   - `devices:`、`tasks:`（各至少 1 项，≤32 项，每项 ≤300 字符）——scripted 模式按 tasks 关键词匹配自动激活
   - `tools:`（声明要用的工具，只能从上表已注册工具中选）
   - `evidence:`、`risk:`、`citation:`（各至少 1 项）
   - 禁止：注释、制表符、嵌套结构、多行块、行内列表（必须写成块序列）。

4. **正文（frontmatter 之后）**：纯 Markdown 领域指引，定位是"怎么查"而非"结论"。系统将其作为不可信材料注入模型上下文，并提供 `read_skill_reference` 让模型按需读 references。

5. **工具使用约定**：Skill 正文和 references 里应直接写明可调用的工具名、参数约定（如 `get_configuration` 必须传 `attribute`；`get_document_excerpt` 必须先用 `search_knowledge` 拿到 documentId/chunkId），并强调系统已有约束：无 FIELD_VERIFIED 记录不得确认当前装机、用户口述不等于现场核实、观测数据是 SIMULATED。

6. **版本纪律**：任何内容/元数据变更都应升 version；激活时系统对 SKILL.md 做 SHA-256 钉存，运行期改动会被拒绝（`SKILL_CHANGED`）。

7. **本地验证（无需 Java 改动）**：
   - 静态自检：frontmatter 按上述子集严格校验；文件名规范；目录名=name。
   - 把目录放进 `skills/` 后让主开发者把 `skill-id@version` 加入 `devicetruth.skills.installations`（或 `DEVICETRUTH_SKILLS_INSTALLATIONS`），启动后 `GET /api/runs/{id}/skill` 应返回选中版本；scripted 模式下用与 `tasks` 匹配的问题文本发起 run 应自动激活。
   - 知识类内容可先用 `POST /api/files` 上传对应手册 MD 验证检索效果。

### B. 当前尚未支持、需要主开发者补齐后才能采用的规范

1. **Skill 脚本执行**（`scripts/`）：当前完全不加载。若需要确定性计算（如备件兼容性矩阵查询），现阶段替代方案：
   - 把兼容规则写成知识库文档（可检索、可引用），或
   - 写成 references 中的决策规则文本，由模型按规则推理（确定性较弱），或
   - 等主开发者实现"Skill 只读数据文件 + 专用工具"（见第 7 节 P1）。
2. **Skill 自助启用**：没有运行时安装 API，必须由主开发者改配置 + 重启。建议主开发者补一个安装管理接口（P1）。
3. **新增领域工具**（如 `get_spare_parts_compatibility`）：需要主开发者写 Java 工具并注册进 `agentCapabilityGateway`，同时加入 `devicetruth.skills.allowed-tools` 白名单。Skill 作者可以提出工具需求规格（名称、参数 schema、输出 schema、数据源表），但不能自行实现。
4. **真实设备观测接入**：`get_observations` 目前是模拟数据；真实现场数据接入需要新的采集通道（P2，超出本次 Skill 接入范围）。
5. **一个 run 多 Skill / Skill 切换**：当前不支持，如调查需要跨领域（如充电问题又涉及产线设备），需在 Skill 正文中写清楚"超出范围时建议另起会话"。

**内容归属原则（Skill vs PG vs RAG）：**

- 进 **SKILL.md/references**：调查流程、排查顺序、证据要求、安全边界、引用规范、决策规则、任务适用范围——即"指导模型怎么查"的内容。
- 进 **RAG 知识库**：设备手册、安装说明、技改方案、备件兼容表等"可引用的资料原文"——需要被 `search_knowledge` 检索并作为 answer 引用来源的内容。
- 进 **PostgreSQL**：设备结构化主数据（只有主开发者/运维能写）。

---

## 7. 最小接入改造清单

结论先行：**标准 Skill 目录本身不需要任何 Java 改造即可接入**；以下清单针对"接入体验与已知缺口"。

### P0（必须，否则新 Skill 无法启用）

| 项 | 模块 | 工作量 | 风险 | 依赖 |
| --- | --- | --- | --- | --- |
| 把两个新 Skill 的 `skillId@version` 追加进 `devicetruth.skills.installations` 默认配置 | `application-product.properties` | 几分钟 | 无 | Skill 目录交付 |
| 核对 Skill `tools:` 声明 ⊆ `devicetruth.skills.allowed-tools`；如需 `generate_diagnosis_report` 或其他未列工具则补进白名单 | 同上 | 几分钟 | 扩大工具面需安全评估 | 同上 |

### P1（建议，显著提升 Skill 交付效率）

| 项 | 模块 | 工作量 | 风险 | 依赖 |
| --- | --- | --- | --- | --- |
| Skill 安装/启用的管理 API（或至少文档化"改配置+重启"流程） | `api/` + `SkillInstallationRepository` | 0.5–1 天 | 安装授权是安全面，需保持 fail-closed | 无 |
| 文档化 Skill frontmatter 校验工具（如脚本或测试钩子），让 Skill 作者提交前自检 | `scripts/` | 0.5 天 | 无 | 无 |
| 若两个 Skill 确实需要确定性领域计算（如备件兼容矩阵），定义并实现一个只读领域工具 | `application/agent/` + 网关 + 白名单 | 2–5 天 | 新工具=新攻击面，需参数强校验 | 需求规格来自 Skill 作者 |

### P2（后续）

| 项 | 模块 | 说明 |
| --- | --- | --- |
| Skill 热加载/版本热更新 | `skill/` | 当前启动时一次性 `SkillRegistry.load`，改动需重启 |
| 一个 run 跨 Skill 或激活后切换 | `SkillRuntime` | 涉及授权交集重算与审计，需谨慎设计 |
| PDF 知识导入 | `knowledge/` | 已有明确缺口记录 |

---

## 8. 给领域 Skill 开发者的交接说明

**1. 我现在可以开始开发什么？**
现在就可以开始写两个 Skill 的全部内容：`industrial-configuration-investigation` 和 `ev-charging-triage` 的 SKILL.md 与 references。系统的 Skill 加载、激活、工具调用、知识库检索链路都已就绪并在运行，不需要等主开发者改代码——只有最后"启用"一步需要主开发者加一行配置。

**2. 目录和文件格式**
放到仓库 `skills/` 下，目录名就是 Skill 名（小写字母+数字+连字符），目录里必须有 `SKILL.md`（UTF-8、无 BOM、≤128KB），可选 `references/` 放平铺的 `.md` 文件（文件名小写字母数字和 `._-`，≤32 篇、每篇 ≤256KB）。SKILL.md 开头是 frontmatter，格式严格：

```markdown
---
name: industrial-configuration-investigation
description: 工业设备配置调查、技改追溯与备件兼容分析的调查指引……
version: 1.0.0
devices:
  - 产线 PLC 控制柜
tasks:
  - 技改配置追溯
tools:
  - search_knowledge
  - get_document_excerpt
  - get_asset
  - get_configuration
  - get_maintenance_history
  - get_observations
evidence:
  - 现场确认的配置与铭牌记录
risk:
  - 带电作业必须由具备资质的人员执行
citation:
  - 引用必须给出文档标题与章节，不得编造记录时间或结论
---

# 正文：调查流程、排查顺序、证据要求……
```

注意：frontmatter 只支持这种简单键值+两空格缩进列表，不要写注释、制表符、嵌套或行内 `[]` 以外的写法；`devices/tasks/evidence/risk/citation` 每项至少一条。参考现成的 `skills/automotive-diagnosis/SKILL.md` 照抄结构最稳妥。

**3. 领域知识、规则和流程怎么组织？**
- SKILL.md 正文写"调查流程"：先查什么、再查什么、什么时候向用户追问、什么情况必须建议现场确认。
- references/ 按需拆分：调查指南、证据要求、决策规则（如备件兼容判断规则）、安全规范。模型会按需一篇篇读，不必全塞进正文。
- 需要被检索、被引用的资料原文（手册、技改方案、兼容表）：整理成 Markdown 单独交付（可上传知识库），不要塞进 references——references 是"指引"，知识库文档才是"可引用证据"。

**4. 能依赖系统提供哪些工具？**
`search_knowledge`（知识库检索）、`get_document_excerpt`（读检索到的原文片段，必须先 search）、`get_asset`（设备身份与组件）、`get_configuration`（按 attribute 查配置，含来源/有效期/冲突）、`get_maintenance_history`（维修/更换史）、`get_observations`（模拟观测）、`generate_diagnosis_report`（run 聚合报告）。Skill 激活后还能用 `activate_skill`（系统自己处理）和 `read_skill_reference`（读你的 references）。

**5. 哪些功能暂时不能用？**
- `scripts/` 里的脚本不会被执行——不要依赖任何脚本逻辑。
- 没有"备件兼容性查询"专用工具，兼容规则目前只能以文本规则（references）或可检索文档（知识库）形式提供。
- 观测数据是模拟的，答案里不能声称代表真实实时状态。
- 一个会话 run 只能激活一个 Skill，不能中途切换。
- 你自己无法启用新 Skill，必须主开发者在服务端配置里登记。

**6. 交付什么？**
- `skills/<skill-id>/SKILL.md` + `references/*.md`（放入仓库或直接交付目录）。
- 需要入库的知识文档（Markdown 文件清单及建议的 domain/deviceModel 标签）。
- （可选）如果认为必须有新的结构化工具（如备件兼容查询），写一份工具规格：名称、参数 schema、返回 schema、数据从哪来。
- 注明建议启用的版本号（如 `industrial-configuration-investigation@1.0.0`），主开发者把它加入 `devicetruth.skills.installations` 即完成集成，无需改 Java。

**7. 如何独立验证交付质量？**
- 格式自检：对照第 6 节 A 的 frontmatter 约束逐条核对（可用仓库中现有 Skill 作对照）。
- 让主开发者启用后，用一个与你的 `tasks` 关键词匹配的问题发起一次 run（scripted 模式会自动激活 Skill），用 `GET /api/runs/{id}/skill` 确认版本与适用范围返回正确。
- 用 qwen/openai 模型模式跑一次完整调查，检查：模型是否按你的 references 顺序调查、答案引用是否都来自工具取回的记录、是否遵守了你的 risk/citation 约束。
- 知识文档先用 `POST /api/files` 上传，再用 `search_knowledge` 相关查询验证能被召回到。

---

*本报告基于静态源码审查，未执行运行验证；标注"已实现"表示代码路径完整存在，实际运行表现以第 8 节第 7 条的验证步骤为准。*
