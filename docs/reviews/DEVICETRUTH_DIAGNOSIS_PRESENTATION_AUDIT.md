# DeviceTruth 专业模式：诊断信息生成与前端展示专项审查

- 日期：2026-11-17
- 范围：产品 Agent 的 `AgentResult` 生成链路（提示词 + 服务端校验）与专业模式前端展示链路
- 性质：**只调研，未修改任何业务代码，未执行 OpenSpec Apply**
- 数据来源：运行中的产品库（`devicetruth-postgres-1`，`dt_run_event.RUN_ANSWER` 持久化载荷），全部案例为真实历史 Run，非模拟结果

---

## 1. Agent 信息生成链路及现有约束

### 1.1 生成链路

```
用户消息 → GeneralAgentService.submit → TaskOrchestrator.runAgent（每轮一个动作）
  → StructuredAgentReasoner.propose（PROTOCOL 提示词 + 上下文 JSON → 供应商补全）
  → ActionProposal.Answer 草稿 → AnswerGuard.review（服务端校验/改写）
  → AgentResult（持久化 + RUN_ANSWER 事件 + SSE 推送）
```

关键源码：

| 环节 | 位置 | 职责 |
|---|---|---|
| 协议提示词 | `src/main/java/io/devicetruth/adapter/StructuredAgentReasoner.java:24-52`（`PROTOCOL`） | 定义 ANSWER 的 JSON 结构：summary/claims/suggestions/uncertainties/limitations/referenceIds |
| 补全参数 | `src/main/java/io/devicetruth/adapter/AgentScopeStep.java:70-96` | temperature=0、JSON 输出模式、45s 超时、不重试（协议层由 StructuredAgentReasoner 最多重试 1 次） |
| 输出解析 | `StructuredAgentReasoner.java:101-118` | 仅校验"是合法三动作 JSON 且 ≤65536 字符"，无内容质量校验 |
| 合同校验 | `src/main/java/io/devicetruth/core/contract/AgentResult.java:24-66` | summary ≤8000 码点、claim.text ≤2000 码点、excerpt ≤2000 码点；**suggestions/uncertainties/limitations 条目数与单条长度均无上限** |
| 答案守卫 | `src/main/java/io/devicetruth/core/decision/AnswerGuard.java:86-148` | 引用子集校验、确诊/危险操作拦截、风险等级判定、**向 limitations 追加服务端条目** |
| 知识引用摘录 | `src/main/java/io/devicetruth/knowledge/KnowledgeTools.java:142,156,165-167` | search 命中摘录截 600 字，get_document_excerpt 截 2000 字 |
| 离线推理器 | `src/main/java/io/devicetruth/core/reasoning/ScriptedAgentReasoner.java:60-130` | claim.text 直接拼"《标题》章节：摘录前 120 字"，同样冗长 |

### 1.2 控制各字段内容与长度的现有机制

| 字段 | 提示词约束（PROTOCOL） | 校验约束 | 结论 |
|---|---|---|---|
| summary | 无长度/风格要求，示例仅写 `"回答正文"` | ≤8000 码点 | **事实上的长度上限是合同给的 8000，提示词完全没引导** |
| claims[].text | 要求绑定引用、区分 certainty | ≤2000 码点；SUPPORTED/CONFIRMED 必须挂引用 | 无"一条断言一句话"的引导 |
| suggestions | 无任何引导 | 仅 `Checks.list`（非 null） | **完全自由生长** |
| uncertainties / limitations | 无任何引导 | 仅 `Checks.list` | **完全自由生长**，且 AnswerGuard 会再追加 1-3 条服务端条目 |
| references | 禁止编造 | 必须是本 run 已取回子集 | 摘录长度由知识工具截断（600/2000） |

### 1.3 为什么容易输出大段文字、重复说明

从提示词和校验两方面都能定位到原因，且真实案例（§3）逐一验证：

1. **提示词只约束"能不能说"，不约束"怎么说"**。PROTOCOL 的 9 条规则全部是安全与引用约束（禁止编造引用、禁止确诊、禁止操作指令），没有任何关于简洁性、单条长度、去重的指令。temperature=0 + JSON 模式 + 无风格引导，模型自然把上下文里的长摘录改写进各字段。
2. **摘要与判断没有分工约定**。提示词没说明 summary 是"一段话结论"还是"详细说明"，模型在 summary 里复述 claims 内容（案例 A 的 summary 479 字，几乎覆盖全部 5 条 claim 的信息）。
3. **免责文本由三处独立产生，天然重复**：
   - 模型自己写一条"不提供高压操作步骤、须资质人员"类 limitation（PROTOCOL 规则 6 引导的结果）；
   - `AnswerGuard` 在高风险时再追加固定文案 `SERVER_SAFETY_NOTICE`（`AnswerGuard.java:44-45,140-141`）——**同一语义出现两次**（案例 A 的 limitations[2] 与 limitations[3] 就是近义重复）；
   - scripted 推理器的 `observationLimits` 又拼"尚未读取到现场数据"类条目。模型自报 limitations + 服务端追加 limitations 无去重（仅对 SERVER_SAFETY_NOTICE 本身做了 `contains` 幂等，语义级重复不管）。
4. **证据-判断混排**。上下文 `references` 里给模型的就是完整摘录（600-2000 字/条，`Context.modelView()` 原样下发，`GeneralReasoner.java:81-86`），模型倾向于把信号原始串（`signal=...;value=...;quality=UNCERTAIN`）抄进 claim.text（案例 B/C），而本该放在"证据详情"的引用摘录又在 `references[].excerpt` 里再出现一次。
5. **JSON 严格性负担反向助长冗长**。`responseFormat=jsonObject` + 单动作协议，模型要在一次输出里塞下所有字段；字段没有上限，就倾向"多写保险"。真实统计也印证：deepseek-flash 的 ANSWER 生成是 `INVALID_JSON_OR_SCHEMA` 重灾区（本审查抽查的 4 个真实 run 中 3 个发生过 ANSWER 阶段 JSON 重试）。

### 1.4 优化文字表达而不影响自主决策/证据/安全约束的确认

可以安全优化的边界：

- **提示词只动"风格与结构"维度**（长度上限、分工、去重、面向行动的句式），不动 PROTOCOL 的引用规则、确诊禁令、工具协议——这些由 `AnswerGuard` 在服务端兜底，模型输出风格变化不会放宽任何硬约束。
- **校验层补上限**（如单条 suggestion ≤200 字、summary ≤500 字）只影响"被接受的内容形态"，不影响动作选择自由；拒绝时已有有界重答机制（`TaskOrchestrator.runAgent` 的 rejections ≤1 次反馈通道，`TaskOrchestrator.java:397-416`）。
- **AnswerGuard 追加 limitation 的语义去重**是服务端行为，对模型完全透明。
- **scripted 推理器**的文案可以同步收紧，但它只影响离线演示，不影响真实模型链路。

---

## 2. 前端数据映射与组件展示链路

### 2.1 完整数据流

```
GET /api/runs/{id}              → RunView { status, pendingQuestion, result: AgentResult, failureCode, ... }
GET /api/runs/{id}/references   → Reference[]（失败/进行轮也能看证据）
GET /api/runs/{id}/events       → PublicEvent[]（白名单映射后的 RUN_TOOL/RUN_MODEL/...）
GET /api/conversations/{id}/messages → MessageView[]
        ↓
App.vue ── buildDiagnosisViewModel({run, goalMessage, references})  → DiagnosisViewModel（纯投影，前端/src/diagnosisViewModel.ts:205-235）
        ↓
ProfessionalDiagnosisPage.vue（三栏）┬─ ClaimList.vue      左栏：判断列表
                                   ├─ ClaimDetail.vue    中栏：判断详情 + 建议/不确定点/限制
                                   └─ EvidenceDrawer.vue 右栏：证据详情
RunTimeline.vue：调查过程时间线（独立于三栏）
```

### 2.2 各字段是"原文展示"还是"已整理"

| 界面位置 | 字段 | 处理方式 |
|---|---|---|
| 左栏判断列表 | `claim.text` | **原文全文**（`ClaimList.vue:29`），无截断——长判断直接把列表项撑高 |
| 左栏标签 | `claim.certainty` | 已整理：受控中文标签（"资料支持/可能/服务端已核验"，`diagnosisViewModel.ts:33-37`） |
| 中栏摘要 | `result.summary` | **原文全文**（`ProfessionalDiagnosisPage.vue:196`） |
| 中栏判断详情 | `claim.text` | **与左栏同一段文字再展示一次**（`ClaimDetail.vue:24`） |
| 中栏建议 | `suggestions[]` | **原文全文**，渲染为有序列表（`ProfessionalDiagnosisPage.vue:207-212`） |
| 中栏不确定点/限制 | `uncertainties[]`/`limitations[]` | **原文全文**，两个 `<ul>` 平铺（`ProfessionalDiagnosisPage.vue:213-219`） |
| 中栏风险/安全 | `riskLevel`/`safetyDisposition` | 已整理：色块 + 受控文案（`diagnosisViewModel.ts:142-148`） |
| 中栏引用卡 | `reference.title/section/excerpt` | 标题/版本/定位整理；`excerpt` 两行截断（`-webkit-line-clamp: 2`，`ClaimDetail.vue:72-74`）——**唯一做了折叠的地方** |
| 右栏证据详情 | `reference.*` 全字段 + `excerpt` 全文 | 原文全文（`EvidenceDrawer.vue:40-43`），仅在点击引用后出现 |
| 时间线 | 事件白名单字段 | 已整理：`describeEvent()` 拼中文短句（`RunTimeline.vue:68-130`） |

### 2.3 信息密度过高的具体原因

1. **同一段 claim.text 在左栏和中间栏各出现一次**（列表 + 详情），左栏 240-300px 窄栏里塞全文。
2. **summary、claims、suggestions、uncertainties、limitations 五块全部平铺**，只有 excerpt 有截断；uncertainties 与 limitations 语义接近（都是"不能说什么"），视觉上两栏重复。
3. **服务端追加的限制与模型自写的限制并排展示**，用户看到两条"须资质人员"文案（案例 A）。
4. **判断列表项没有行动指向层级**：一条 124 字的"排查顺序"和一条 54 字的"事实陈述"在列表里权重相同。
5. 证据摘录全文（最长 2000 字/条）只在右栏抽屉展示是合理的，但中栏引用卡两行截断后没有"展开"入口，用户必须跳到右栏才能看全。

### 2.4 组件复用结论

- **可原样复用**：`diagnosisViewModel.ts`（投影层加字段/加截断辅助函数即可）、`EvidenceDrawer.vue`（已是最合适的"全文归宿"）、`RunTimeline.vue`、`ClaimDetail.vue` 的引用卡结构。
- **需要调整**：`ClaimList.vue`（claim.text 需要截断/首行摘要）、`ProfessionalDiagnosisPage.vue`（summary 截断展开、uncertainties/limitations 折叠、服务端条目标注）、`diagnosisViewModel.ts`（增加展示用截断与"服务端生成"标记投影）。
- **不需要新增组件**，不需要改 API 合同——全部可在投影层和模板层完成。

---

## 3. 真实案例分析（来源：`dt_run_event.RUN_ANSWER`，均为 deepseek-flash 真实输出）

### 案例 A（新能源汽车，KNOWLEDGE）— run `5304f5da`

问题："新能源汽车充电中断，没有故障码，先查什么资料？"（2026-11-17 10:35，8 事件、1 次 JSON 重试）

| 字段 | 原始情况 |
|---|---|
| summary | 479 字，一段内用 ①②③ 讲完三条分支，信息与下方 claims 大面积重叠 |
| claims | 5 条（54-124 字/条，全部 SUPPORTED，共挂 6 个引用 ID） |
| suggestions | 3 条共 197 字 |
| uncertainties | 2 条共 82 字 |
| limitations | 4 条共 213 字，**其中第 3 条（模型自写"不提供高压部件操作步骤…"48 字）与第 4 条（AnswerGuard 追加的 SERVER_SAFETY_NOTICE 52 字）语义重复** |
| references | 4 条，excerpt 共 1291 字（含两张完整 Markdown 表格） |

问题定位：

- **默认应展示**：summary 前两句（"先查问题定位类资料…无故障码不构成拒绝调查的理由"）、5 条 claims（这是判断区本体）、风险 HIGH + 资质人员安全提示。
- **应折叠**：summary 的 ②③ 分支细节（已由 claim[2][3] 覆盖）；limitations 折叠为 2 条（合并语义重复后：SIMULATED 声明 + 资产未核验 + 资质人员提示）；excerpt 表格全文（已有右栏抽屉）。
- **重复/冗长**：summary↔claims 复述；"须资质人员"出现 3 次（summary 尾部、limitations[2]、limitations[3]）。
- **缺乏行动指向**：suggestions 第 1 条 73 字一口气列 6 个要记录的信息，没有编号和优先级。

精简展示示例（不改原意、不删证据，仅改呈现）：

> **摘要**：先查"问题定位类"资料而非故障码库；"无故障码"是资料列明的常见情况，不构成拒绝调查的理由。
> **判断（5）**：〔资料支持〕先确认充电方式/中止方/发生阶段等边界信息 →〔引 aecbd285〕…（列表只展示首行，详情进中栏）
> **建议核验**：1. 记录充电方式、中止方、发生阶段、SOC、环境温度… 2. 直流/交流分开记录时间点 3. 提供已登记资产编号可核对维修记录
> **限制（折叠，2）**：演示资料不能作为实车依据；高压检查须资质人员现场执行。

风险与安全保留方式：riskLevel=HIGH 色块、safetyDisposition 触发的受控安全提示、`SERVER_SAFETY_NOTICE` 原文保留 1 次（折叠区内），一条不删。

### 案例 B（工业设备 PLC，POSSIBLE_CAUSES）— run `2fd6d931`

问题："PLC控制柜 D-120A 运行中突然停机，error 灯闪烁，按维护手册排查可能原因。"（19 事件、1 次 JSON 重试）

| 字段 | 原始情况 |
|---|---|
| summary | 471 字：先解释"没检索到手册"约 150 字，再复述两条台账发现，再列一般原理方向——**把"没查到"写在结论首位，行动指向被稀释** |
| claims | 4 条（81-113 字），claim[0][1] 把信号原始串（`signal=CTRL-01.installed_part;value=D-120B;unit=;…`）抄进断言正文 |
| suggestions | 5 条共 262 字，**最有行动价值的一条（读诊断缓冲区拿报警代码）排在首位，但没有与"型号不一致"这一关键发现建立视觉关联** |
| uncertainties | 4 条共 138 字，其中 2 条与 limitations 语义重叠（"UNCERTAIN 不能视为核验"同时出现在 uncertainties[2] 和 claim[0] 尾部） |
| references | 2 条，excerpt 共 248 字（信号串原文） |

问题定位：

- **默认应展示**：claim[3]（口述 D-120A 与记录 D-120B 不一致——本轮最有价值的发现）、claim[2]（可能方向）、建议第 1 条（读报警代码）。
- **应折叠**：summary 的一般原理枚举（与 claim[2] 重复）；claim[0][1] 的信号串细节（右栏引用卡已有，断言只留"观测/维修记录显示登记值为 D-120B"）。
- **重复**：claim[0] 尾句"不构成现场核验"与 uncertainties[2] 同义；summary 与 claims 的 D-120B 事实重复 3 次。

精简展示示例：

> **摘要**：手册未命中，以下为一般原理层面可能方向；关键差异：口述 D-120A 与台账/观测中的 D-120B 不一致，排查前先核对铭牌与固件版本。
> **判断（4）**：…
> **建议核验**：1. 〔优先〕现场读取 CPU 诊断缓冲区与报警代码 2. 核对铭牌/固件与台账（D-120B）…
> **限制（折叠）**：SIMULATED 数据；未现场核验；资产编号未核验。

### 案例 C（工业设备 PLC，HUMAN_REVIEW）— run `0de22846`（补充印证）

问题："调查 SN-0245 产线 PLC 固件版本与维护记录，给出停机风险判断。"summary 302 字尚可，但 claims[3][4] 是把指引资料的原则转述成判断（42-63 字），与 claim[0][1] 的事实陈述混在一起；limitations 5 条中第 5 条又是 SERVER_SAFETY_NOTICE。印证：**原则性内容（"指引资料要求…"）不应与事实判断同级展示**。

### 案例 D（新能源汽车，scripted）— run `f83c33dd`（补充印证）

claim[0] 169 字 ="《标题》《章节》：摘录前 120 字…"，即"定位信息 + 原文搬运"当判断用——印证 scripted 的 `locate()+excerpt()` 拼接（`ScriptedAgentReasoner.java:117-129`）是"判断区字多"的另一来源，提示词优化需同步覆盖离线推理器。

---

## 4. 当前信息展示存在的主要问题（汇总）

**Agent 输出侧：**

1. summary 无长度与分工约定，普遍 300-480 字且复述 claims。
2. suggestions/uncertainties/limitations 无单条长度与条数上限。
3. 安全语义重复：模型自写资质提示 + AnswerGuard 追加 SERVER_SAFETY_NOTICE，语义级不去重（案例 A 实测重复）。
4. claim.text 混入信号原始串/资料原文（案例 B、D），判断与证据不分层。
5. ANSWER 一次性 JSON 输出负担重，deepseek-flash 频繁 `INVALID_JSON_OR_SCHEMA`（抽查 4 个真实 run 中 3 个在 ANSWER 阶段重试），与字段过多、无上限正相关。

**前端展示侧：**

6. claim.text 左栏全文 + 中栏再展示一次，窄栏被长文撑爆。
7. 五块内容（summary/claims/suggestions/uncertainties/limitations）全部平铺，无折叠——只有引用 excerpt 有两行截断。
8. uncertainties 与 limitations 语义重叠，视觉上像两个差不多的列表。
9. 服务端追加条目与模型条目无视觉区分，重复文案显得像系统出错。
10. 中栏引用卡截断后无就地展开入口。

---

## 5. Agent 输出提示词的可优化位置（只列位置与方向，不写实现）

1. **`StructuredAgentReasoner.PROTOCOL`（StructuredAgentReasoner.java:35-37）ANSWER 动作说明处**：
   - 给每个字段加"一句话职责"：summary=≤150 字的结论与最重要的一个行动指向；claims=每条一句话断言，不复述摘录原文；suggestions=可执行的第一步动作，≤40 字/条；uncertainties=只写影响结论成立的未知项；limitations=只写与本轮证据相关的限制（资质/模拟标记由服务端补充，模型不必重复）。
   - 增加条目数引导：claims 3-5 条、suggestions 2-4 条、uncertainties ≤3、limitations ≤2。
2. **PROTOCOL 规则区（同文件 :38-51）**：新增一条风格规则（"各字段不互相复述；引用摘录留在 references，不要抄进断言正文"），不触碰既有 9 条安全规则。
3. **`AnswerGuard.review`（AnswerGuard.java:131-141）**：对模型自写 limitation 做语义级去重——追加 SERVER_SAFETY_NOTICE 前检查是否已有含"资质/专业人员/不提供操作步骤"的条目；资产未核验条目同理。这是服务端行为，模型无感知。
4. **`AgentResult` 合同（AgentResult.java:53-60）**：可选补充硬上限（summary ≤500、单条 suggestion/uncertainty/limitation ≤200 码点），作为提示词失效时的兜底；需配合 `TaskOrchestrator` 既有重答反馈通道。
5. **`ScriptedAgentReasoner`（ScriptedAgentReasoner.java:117-129,60-81）**：离线推理器的 claim 拼接同步改为"一句话判断 + 引用 ID"，excerpt 不嵌入断言正文；`answer()` 的 limitations 与 AnswerGuard 追加项去重。

以上 1、2、5 只影响文字风格与结构；3、4 是服务端兜底，均不改变动作选择自由、引用校验与安全拦截（AnswerGuard 的拒绝逻辑一行不动）。

---

## 6. 前端信息筛选、折叠和重点展示的建议

1. **判断列表（ClaimList.vue）**：`claim.text` 投影时截首行（约 60 字）+ 省略号，全文留在中栏——改动集中在 `diagnosisViewModel.ts` 增加 `claim.textPreview`。
2. **摘要（ProfessionalDiagnosisPage.vue）**：超过约 200 字折叠为"展开全部"，默认展示首段。
3. **限制与不确定点合并为一个"边界与限制"折叠区**（ ProfessionalDiagnosisPage.vue:213-219 两个 round-field 合并），默认收起、显示条数徽标；`diagnosisViewModel.ts` 给 AnswerGuard 追加的条目投影 `serverGenerated: true` 标记（可按 SERVER_SAFETY_NOTICE 原文及固定文案匹配，属展示层判断），UI 上标注"系统提示"并与模型条目分组。
4. **建议区保持默认展开**（它是行动指向最强的区块），但把超过 3 条的收进折叠。
5. **中栏引用卡（ClaimDetail.vue）**：两行截断保留，增加就地"展开摘录"开关（或直接滚动到右栏抽屉，复用现有 toggle-reference 事件即可）。
6. **证据抽屉（EvidenceDrawer.vue）与 RunTimeline.vue 不动**。

全部建议均在投影层与模板层完成，不改 API 合同、不改后端。

---

## 7. 建议修改的文件及最小改造范围

| 文件 | 改动性质 | 工作量 |
|---|---|---|
| `src/main/java/io/devicetruth/adapter/StructuredAgentReasoner.java` | PROTOCOL 文本：ANSWER 各字段职责/长度/条数引导 + 1 条风格规则 | 提示词文案，约 10 行 |
| `src/main/java/io/devicetruth/core/decision/AnswerGuard.java` | `review()` 追加 limitation 前的语义去重 | 约 15 行 + 测试 |
| `src/main/java/io/devicetruth/core/contract/AgentResult.java` | （可选）summary/列表条目硬上限 | 约 5 行 + 测试 |
| `src/main/java/io/devicetruth/core/reasoning/ScriptedAgentReasoner.java` | 离线 claim 拼接精简、limitations 去重 | 约 15 行 |
| `frontend/src/diagnosisViewModel.ts` | textPreview 截断、serverGenerated 标记、限制区合并投影 | 约 30 行 + 单测 |
| `frontend/src/components/ClaimList.vue` | 列表项改用 textPreview | 1 行模板 + 样式 |
| `frontend/src/components/ProfessionalDiagnosisPage.vue` | 摘要折叠、限制区折叠与分组、建议超条折叠 | 模板约 20 行 |
| `frontend/src/components/ClaimDetail.vue` | 引用卡就地展开 | 约 10 行 |

回归验证建议：`mvn -q test`（AnswerGuard/AgentResult/协议解析测试）+ `cd frontend && npm run test -- --run && npm run build`；提示词变更后用真实 deepseek-flash 跑 2-3 个真实问题目检 ANSWER 一次通过率和字段长度分布。

---

## 附：验证记录

- 本审查源码依据：上文所列路径均逐行阅读；前端组件 `diagnosisViewModel.ts`、`ProfessionalDiagnosisPage.vue`、`ClaimList.vue`、`ClaimDetail.vue`、`EvidenceDrawer.vue`、`RunTimeline.vue` 全文阅读。
- 真实数据来源：`docker exec devicetruth-postgres-1 psql` 查询 `dt_agent_run`/`dt_run_event`，案例 A-D 的 RUN_ANSWER 载荷原文导出并逐字段统计长度（统计脚本输出见会话记录）。
- 未修改任何业务代码；未执行 OpenSpec Apply；临时起的 8082 scripted 服务已关闭。
- 未验证项：提示词优化的实际效果（需变更后真实模型回归）；不同模型（qwen-plus）的输出长度分布未采样。
