# datasets/v1 双域样本（汽车充电 + 工厂 SC-0245-B）

本目录是 04-02-T1 的资料与样本交付区：为 `04-skills-dual-domain` 的汽车充电案例和工厂 SC-0245-B 配置冲突案例
提供**领域资料、模拟样本和预期结果说明**。全部内容为 `SIMULATED`，不涉及真实设备、真实客户或真实工单。

本轮只交付数据与说明；运行时接线、`V5__asset_observation.sql` 迁移、`get_observations` 工具与 `DualDomainAgentTest`
都留给 04-02-T2/T3，本目录不含 Java 代码、SQL 或测试。

## 1 目录与职责

| 路径 | 类型 | 职责 |
| --- | --- | --- |
| `../skills/automotive-diagnosis/references/charging-troubleshooting.md` | 资料（Skill reference） | 汽车充电问题的排查顺序、证据优先级、检索提示、引用与安全规范；不预先给结论 |
| `../skills/factory-equipment/references/controller-maintenance.md` | 资料（Skill reference） | 工厂控制器配置核对：四类记录各能证明什么、时点判断顺序、冲突处理；不预先判装机 |
| `automotive/charging-case.json` | 样本 | 无故障码充电故事：对话轮次、必答追问、期望检索与引用、期望答案边界 |
| `factory/sc-0245-b.json` | 样本（主案例） | 出厂 D-120A → 改造 D-120B → 站点旧模板仍写 D-120A，从未现场核验 |
| `factory/variants/sc-0245-b-three-source-conflict.json` | 样本（变体） | 改造后未回写失效，出厂与改造记录同级冲突 |
| `factory/variants/sc-0245-b-field-verified.json` | 样本（变体） | 有一次带有效期的现场核验（D-120B） |
| `factory/variants/sc-0245-b-field-contradiction.json` | 样本（变体） | 两次现场核验互相矛盾（D-120A / D-120B） |
| `expected/factory-sc-0245-b.json` | 预期结果 | 每个变体、每个查询时点应得到的记录、装机状态、冲突与引用 |

三类内容的边界与 `SKILL-03` 对应：**Skill 给指引、知识给文档、结构化工具给时点事实**。
两份 reference 不含任何设备的实际答案；样本本身不含分析措辞；预期结果只描述工具事实与答案边界。

## 2 数据性质与时间语义

样本沿用 03-01 的 `Dataset` / `EvidenceRecord` 合同（`dataNature` 只接受 `SIMULATED`），字段语义：

| 字段 | 含义 | 本样本中的用法 |
| --- | --- | --- |
| `effectiveAt` | 业务生效时间 | 出厂配置生效日、改造完工日、模板启用日 |
| `recordedAt` | 记录写入系统的时刻 | 略晚于业务时间，用于区分"业务时间"和"记录时间" |
| `observedAt` | 现场观测时间（仅现场核验） | 两条现场核验各自的实际观测时刻 |
| `validTo` | 记录失效时刻（空=未声明失效） | 出厂配置在改造生效时失效；现场核验有 6 个月有效期 |
| `authority` | 来源权威级别 | 出厂/改造/现场核验=10，站点组态模板=5 |

映射进结构化事实后：

- `validFrom = observedAt != null ? observedAt : effectiveAt`（与 `AssetSnapshotImporter` 一致）；
- `verificationStatus` 只由来源类型决定：`FIELD_CONFIRMATION` 且有 `observedAt` 才是 `FIELD_VERIFIED`，其余一律 `DOCUMENTED`；
- 时点查询口径（`AssetRegistryRepository`）：`valid_from <= asOf AND (valid_to IS NULL OR valid_to > asOf)`，**`recorded_at` 不参与过滤**；
- 装机状态（`AssetTools.installation`）：每个 `组件+属性` 分组都要有有效 `FIELD_VERIFIED` 且最高权威取值唯一，才是 `FIELD_CONFIRMED`；
- 冲突（`AssetTools.conflict`）：分组内有核验记录时只看核验记录，否则看全部记录；最高权威取值多于一个即 `CONFLICT`。

## 3 关键设计取舍

1. **出厂记录在改造生效时置 `validTo`**（主案例）：出厂配置在改造完工后不再描述现实生效区间；
   "出厂时是 D-120A"作为历史事实仍可追溯，但查询当前时点不会把它和改造记录并列成冲突。
2. **旧模板保留可见但权威更低**：模板 `authority=5`、`validTo` 为空。这样它在"当前"时点仍被返回（体现"最新资料仍写旧型号"），
   但不会因为更晚修改就覆盖改造记录。若把模板权威提到 10，就会与改造记录同级冲突——这由变体 2 表达。
3. **变体 2 让出厂记录 `validTo` 为空**：模拟"改造后没回写出厂记录失效"这一常见现场数据问题，
   于是出厂与改造两条最高权威记录同时有效、取值分叉，得到 `CONFLICT`。
4. **现场核验带有效期**：`observedAt=2026-11-10T01:15:00Z`、`validTo=2027-05-10T00:00:00Z`，
   有效期内 `FIELD_CONFIRMED`，过期后必须重新核验。
5. **不把设备型号写成资产事实**：`XLP-200` 只出现在资料适用范围里。若新增 `model` 属性记录，
   由于"每个分组都需确认"，没有对应现场核验时整体 `installationStatus` 会一直是 `UNCONFIRMED`，会掩盖真正要验证的 CTRL-01 结论。

## 4 变体不得同时导入

四个工厂样本是**同一资产 `SC-0245-B` 的四种不同证据集合**，不是同一次导入的四份数据。
导入时每个变体要用独立的 `projectId`（例如 `dual-domain-base` / `dual-domain-conflict` / `dual-domain-field` / `dual-domain-contra`），
否则同一 `(asset_id, source_id, valid_from)` 的事实会互相覆盖或冲突。

## 5 各查询时点应得到的事实与引用

引用形态（真实 run 内由 `get_configuration` 登记）：`sourceType=CONFIGURATION`、`sourceId`=证据记录 ID、
`sourceVersion`=该记录 `recordedAt`、`title`=`配置记录 CTRL-01.moduleVersion`、
`excerpt`=`attribute=moduleVersion;value=…;source=…;verification=…;validFrom=…[;validTo=…]`。
`referenceId`/`chunkId` 由事实记录 ID + 资产 + 时点派生，不写死；回读接口 `GET /api/runs/{id}/references`。
无 run 的只读 HTTP 查询不登记引用（只返回事实列表）。

### 5.1 主案例 `factory/sc-0245-b.json`（现场核验缺失）

| 时点（`asOf`） | 有效记录 | 装机状态 | 冲突 | 应引用 |
| --- | --- | --- | --- | --- |
| 2024-06-01 | `SC0245B-BOM-2024` D-120A（出厂，DOCUMENTED） | UNCONFIRMED | NONE | 1 条（出厂记录） |
| 2025-09-14T23:59:59Z | `SC0245B-BOM-2024` D-120A | UNCONFIRMED | NONE | 1 条 |
| 2025-09-15T00:00:00Z | `SC0245B-RETROFIT-2025` D-120B（改造，DOCUMENTED） | UNCONFIRMED | NONE | 1 条 |
| 2025-10-01 | `SC0245B-RETROFIT-2025` D-120B | UNCONFIRMED | NONE | 1 条 |
| **2026-11-13（当前）** | `SC0245B-RETROFIT-2025` D-120B（authority 10）+ `SC0245B-TEMPLATE-2026` D-120A（模板，authority 5） | **UNCONFIRMED** | NONE | 2 条（改造 + 模板） |
| 2027-01-01 | 同上两条 | UNCONFIRMED | NONE | 2 条 |

当前时点的答案边界：必须能区分"最新文档记录是 D-120B""旧模板仍写 D-120A 但不代表装机""缺少现场核验"三件事，
不得出现"已确认""已确诊""当前装机为""已经更换为"；`limitations` 由服务器追加"请求中的资产编号尚未经服务器核验"。

维修历史（当前时点）：`SC0245B-RETROFIT-2025`、`REPLACEMENT`、`before=null`、`after=D-120B`、`DOCUMENTED`，
引用 `title=维修记录 CTRL-01.REPLACEMENT`；改造前时点返回空列表。

### 5.2 变体 `three-source-conflict`（三源同级冲突）

| 时点 | 有效记录 | 装机状态 | 冲突 |
| --- | --- | --- | --- |
| 2024-06-01 | 出厂 D-120A | UNCONFIRMED | NONE |
| 2025-10-01 | 出厂 D-120A + 改造 D-120B（均 authority 10） | UNCONFIRMED | **CONFLICT** |
| **2026-11-13（当前）** | 出厂 D-120A + 改造 D-120B + 模板 D-120A | **UNCONFIRMED** | **CONFLICT** |

答案边界：必须显式说明记录之间存在冲突、无法判定，不得按"最新记录"挑一条当结论；必须要求现场核验。

### 5.3 变体 `field-verified`（现场核验在有效期内）

| 时点 | 有效记录 | 装机状态 | 冲突 | 说明 |
| --- | --- | --- | --- | --- |
| 2026-11-09 | 改造 D-120B + 模板 D-120A | UNCONFIRMED | NONE | 核验尚未发生 |
| 2026-11-10T02:00Z | + `SC0245B-FIELD-2026-11` D-120B（FIELD_VERIFIED） | **FIELD_CONFIRMED** | NONE | 引用含 `validTo=2027-05-10T00:00:00Z` |
| **2026-11-13（当前）** | 同上 | **FIELD_CONFIRMED** | NONE | 无需现场核验提示 |
| 2027-05-10T00:00Z | 核验记录失效（右开区间） | UNCONFIRMED | NONE | 有效期边界 |
| 2027-06-01 | 改造 + 模板 | UNCONFIRMED | NONE | 必须重新核验 |

答案层限制：当前 `TaskOrchestrator` 以三参 `AnswerGuard.review(...)` 调用，`fieldVerified` 恒为 `false`，
因此即使工具返回 `FIELD_CONFIRMED`，答案层仍不得出现 `CONFIRMED_FINDING` 或确诊表述。
把服务器判定的现场核验传入答案守卫属于 04-02 集成范围，本样本只固定"工具事实应为 FIELD_CONFIRMED"。

### 5.4 变体 `field-contradiction`（两次核验矛盾）

| 时点 | 有效记录 | 装机状态 | 冲突 |
| --- | --- | --- | --- |
| 2026-11-09 | 改造 D-120B + 模板 D-120A | UNCONFIRMED | NONE |
| 2026-11-10T01:17Z | + `SC0245B-FIELD-2026-11-A` D-120A（只有第一条核验在有效时间内） | FIELD_CONFIRMED | NONE |
| **2026-11-13（当前）** | + `…-A` D-120A + `…-B` D-120B（两条核验） | **UNCONFIRMED** | **CONFLICT** |

`2026-11-10T01:17Z` 是口径边界示例：查询只按业务有效时间过滤，`recorded_at`（02:05 / 03:40）不参与过滤，
所以该时点能看到"当时还没被记录"的核验。结论应理解为"这条核验声称覆盖该时刻"，而不是"当时已经记录在案"。
这一点是 03-01 的既定口径，本目录只把它显式记录，未修改运行时。

### 5.5 负例

| 场景 | 期望 |
| --- | --- |
| `get_configuration` 不带 `attribute` | `INVALID_ARGUMENTS`，不退化为默认查询 |
| 查询 `CAR-B`（未在本归属登记） | `ASSET_NOT_FOUND`，不串读 legacy 夹具 |
| 查询未登记属性 `firmwareVersion` | 空列表 + 1 条现场核验提示，不编造取值 |
| `get_asset`（`SC-0245-B`） | 组件 `[CTRL-01]`，`dataNature=SIMULATED` |

### 5.6 汽车案例 `automotive/charging-case.json`

- `selectedAssetId=null`：没有资产也能进入调查，不使用 legacy `CAR-A…CAR-D` 夹具，也不复用 `CAR-B` 场景。
- 有故障码不是入场条件：`goalText` 必须被接受，不得返回"缺少故障码"类拒绝。
- 结论前必须追问：至少覆盖"直流/交流"和"是否只在特定充电桩"两个主题，最多两个问题。
- 至少调用一次 `search_knowledge`；引用必须是本 run 实际取回的 `referenceId`，可经
  `GET /api/knowledge/references/{referenceId}?runId=` 读回原文，主引用章节为手册"3 直流快充中断"。
- 答案类别只能是 `KNOWLEDGE` / `POSSIBLE_CAUSES`；不得出现"已确诊""已确认故障""该车的故障原因是"。
- 未接入车辆运行数据时必须明确写出"尚未读取到"，不能默认已经读过。
- 涉及高压语义时风险等级由服务器判 `HIGH`，只放行禁止性提示或"必须具备资质人员执行"的边界声明。

手册版本：`datasets/knowledge/charging-system-manual.md` 自称文档版本 2026.1；实际上传版本由运维填写，
验收核对章节内容与定位，不绑定某个版本号。

## 6 验证记录

本轮实际执行（2026-11-13，Windows + `.tools/jdk-21.0.13.0.2+1`）：

| 项 | 命令/方式 | 结果 |
| --- | --- | --- |
| JSON 可解析且符合 `Dataset`/`EvidenceRecord` 合同 | 用仓库真实 `Json.MAPPER` + `JsonRepositories.Dataset` 反序列化四个工厂样本 | 通过（严格模式无未知字段、无时间倒挂、无越界 `validTo`） |
| 时点事实矩阵 | 用真实 `AssetSnapshotImporter.fromEvidence` + `AssetTools` + `CapabilityGateway.executeTool`（内存仓储按 SQL 同口径过滤）逐点比对 `expected/factory-sc-0245-b.json` | **149 项断言 PASS / 0 FAIL**（退出码 0） |
| 引用形态 | 比对 `title` / `sourceVersion` / `excerpt` 与 `AssetTools.summary` 实际输出 | 全部一致 |
| 负例 | 经 `CapabilityGateway.executeTool` 实跑 | 三个负例均符合期望错误码/空结果 |
| 一次性核对程序 | `/tmp/ddcheck/.../DualDomainSampleCheck.java`（未入库） | 04-02-T3 的 `verify_general_agent.py --suite=dual-domain` 将取代它 |

未验证（NOT_RUN）：

- 真实 PostgreSQL 导入与 `GET /api/runs/{id}/references` 回读；
- `SC-0245-B` 进入产品网关后的真实 HTTP/run 链路（现有 `AssetSnapshotImporter.fixtureFolder` 只接受单字母夹具，
  新加载路径属于 04-02-T2）；
- 真实模型下的 `expectedAnswer` 全部项（措辞、是否显式说明冲突、是否请求现场核验）；
- 真实 embedding 检索与 `expected` 中的知识引用章节命中；
- `asset_observation` 观测样本（DATA-05）未预置：表结构与 `get_observations` 参数/quality 枚举由 04-02-T2 决定，
  提前固定格式会与其冲突；
- 浏览器/前端展示。

## 7 与 04-02-PLAN 的偏差

| 项 | 计划 | 实际 | 原因 |
| --- | --- | --- | --- |
| 工厂变体 | 计划只列 `datasets/v1/factory/sc-0245-b.json` | 主案例 + 3 个变体文件 | 计划要求"现场缺失与冲突做变体"，单文件无法同时表达 |
| 预期结果 | 计划未单列文件 | 新增 `expected/factory-sc-0245-b.json` | 需要机器可读的时点事实与引用矩阵，供 T3 套件直接消费 |
| Skill 指引 | 计划把 `SKILL.md` 与 references 放在 04-01 | 本轮只改两份 reference，未改 `SKILL.md`、未新增 reference 文件 | `SKILL.md` 与 references 清单属于 04-01 交付；`SkillRegistryTest` 断言 references 清单恰好两个文件 |
| 运行时/迁移/工具 | 04-02-T2 | 未改动 | 用户明确把运行时、数据库迁移和工具接入留给 04-02 集成 |

## 8 已知边界（不属本轮修复范围）

- `AssetSnapshotImporter.fixtureFolder` 只支持 `CAR-A…D` / 单字母夹具；`SC-0245-B` 需要新的夹具加载路径。
- `AssetSnapshotImporter` 不推导 `beforeValue`，改造记录的 `before` 为 `null`；需要"改造前取值"时要在导入映射里补齐。
- `TaskOrchestrator` 目前以三参 `AnswerGuard.review` 调用，`fieldVerified` 恒为 `false`。
- `recorded_at` 不参与时点过滤：早于记录时间的时点仍能看到该事实（见 5.4 口径边界）。
