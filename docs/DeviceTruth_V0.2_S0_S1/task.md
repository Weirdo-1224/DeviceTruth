# DeviceTruth V0.2｜S0/S1 分会话实施任务（Task）

> 初始状态：**全部 TODO，未经真实运行或 Review**。每轮只推进一项；Review PASS 后才能改 DONE。
>
> 每一项由 DeepSeek 实施或完成文档审计，Codex 独立 Review。**不默认允许模型执行 commit/push/reset/clean。**

## 0. 状态和审查规约

状态只允许 `TODO / IN_PROGRESS / REVIEW_PENDING / CHANGES_REQUIRED / DONE / BLOCKED`。

- `TODO → IN_PROGRESS`：开始本轮任务，记录基线 Git 状态。
- `IN_PROGRESS → REVIEW_PENDING`：开发者完成自测，交付具体文件/命令/结果，启动 Codex Review。
- `REVIEW_PENDING → DONE`：Codex 报告为 PASS，P0/P1 全部关闭；可记录 P2。
- `REVIEW_PENDING → CHANGES_REQUIRED`：P0/P1 需要修复，进入同任务修复和复审；**不新开下一任务**。
- `BLOCKED`：环境/事实障碍阻断关键验收，报告原因、证据和可替代验证；不得标 DONE。

每轮结束仅更新本轮状态、下一轮 ID、已验证事实和未解决问题；`state.md` 是唯一跨会话进度快照。`reviews/Rxx.md` 是证据记录，不要在 `state.md` 复制全部内容。

## 1. 总任务看板

| ID | 阶段 | 会话任务 | 依赖 | 状态 | Codex 报告 |
|---|---|---|---|---|---|
| R01 | S0 | Git/源码/测试/CLI 基线核对 | 无 | TODO | reviews/R01.md（未生成） |
| R02 | S0 | 契约核实、规格冻结、跨会话接力 | R01 PASS | TODO | reviews/R02.md（未生成） |
| R03 | S1 | 双 Main + Server 可启动 + Catalog | R02 PASS | TODO | reviews/R03.md（未生成） |
| R04 | S1 | Session + TaskRouter（四路由） | R03 PASS | TODO | reviews/R04.md（未生成） |
| R05 | S1 | QueryService + Goal→Core 请求映射 | R04 PASS | TODO | reviews/R05.md（未生成） |
| R06 | S1 | RunService/ReasonerFactory/GET 状态事件 | R05 PASS | TODO | reviews/R06.md（未生成） |
| R07 | S1 | 端到端验收与文档交付 | R06 PASS | TODO | reviews/R07.md（未生成） |

### 通用开发边界

- 每轮先验证前置任务确实为 DONE、`state.md` 和 Git 工作树一致；异常先记录，不自行假设已完成。
- 本轮直接修改范围以当轮任务为准；修改其他关联代码必须说明依赖和最小必要性。
- 跑实际测试并记录命令/退出码/日志位置，不能用“预期通过”替代。
- Codex 必须审查实际 diff、依赖路径和真实测试，不得仅依据 DeepSeek 的总结打 PASS。
- P0/P1 修复由 DeepSeek 处理，Codex 复审；P2 登记不阻塞，除非影响安全或正确性。

## 2. R01｜S0：当前仓库事实核对

**目标**：防止在不存在的方法、错误启动方式、历史测试假设上开发。仅只读审计 + 运行现有测试；**禁止写 Java 业务源码**。

**需要读取**：根 README/AGENTS（如存在）、pom.xml、V0.1 spec/task/state、当前真实源码与 datasets、原修订方案。

**必须完成**：

1. 记录工作树是否干净、分支、commit、tag、修改/未跟踪文件；不清理用户内容。
2. 核对 JDK/Maven 真实环境、依赖版本、CLI Main 的 `WebApplicationType.NONE`/Context 退出和现有打包配置。
3. 核对 `TaskOrchestrator.run()` 输入、返回、内部 StateManager、RunReport/result、事件生命周期。
4. 核对 Reasoner 各实现的可变状态及构造方式。
5. 核对 `AssetRepository/RuleRepository/CapabilityGateway` 真正签名、JSON fixture 与 scenes。
6. 核对 `DiagnosisRequest.asOf` 语义、CAR-B 证据有效期；列出现有 ActionProposal/Hypothesis/Guard 行为与已知缺口。
7. **实际执行** `java -version`、`mvn -version`、`mvn test`、`mvn package`，以真实 jar 文件/README 方式跑 CLI A/B/C/D（至少可核对真实命令与输出），E 只跑现有负例测试，不能伪造 `--scenario=E`。
8. 检查实际测试项数量，与“历史 42 项”区分；失败时写出可重现障碍。

**允许写**：`S0_AUDIT.md`，必要时写文档性审计笔记。禁止业务编码、直接改变既有技术契约。

**交付**：填充 `S0_AUDIT.md` 的源码表、命令、结果、风险和候选回退点（未授权不创建 commit）。

**Codex Review**：所有事实必须能追溯到真实文件/方法/行、真实命令/输出；审计报告不可把历史结论当今天的验证。**通过判据**：能据此进行 R02 的设计冻结；环境 blocker 已准确标明。

## 3. R02｜S0：技术契约冻结

**目标**：将 R01 事实用于修订并冻结规格；不写业务代码。重大架构取舍请用户确认。

**必须完成**：

1. 对 `spec.md` 的全部 `[HISTORICAL]`/待验证条目，以 R01 事实核对，修正不一致接口；不凭空制造 Repository 方法。
2. 冻结**独立 CLI / Server Main**启动与 Spring Boot Maven mainClass 选择方案，记可执行验证命令或 S1 必跑验证项。
3. 冻结 Catalog 的资产/症状/固定 asOf，区分 `requestedAt` 和 `diagnosisAsOf`。
4. 冻结 QUERY 两类只读 Repository 查询、不伪造 DiagnosisRequest；DIAGNOSE 必须 Catalog+Rule 双重校验。
5. 冻结 `diagnosisId/coreTaskId/reportPath`、`ExecutionStatus/Core RunStatus/Outcome`、POST 202/QUEUED、GET 最终事件、白名单 DTO。
6. 冻结每任务独立 Reasoner、离线/在线模型路径、线程池/缓存上限和 loopback/非鉴权边界。
7. **将 Hypothesis 因果调查缺口明确登记到 S2，不在 S1 实现**；保留 V0.1 Guard。
8. 对 `task.md/state.md` 与验收测试矩阵作必要契约调整，形成下一会话明确入口。

**允许写**：`spec.md/task.md/state.md` 和设计变更记录；不改业务源码。`spec.md` 在 Codex PASS、重大问题经用户裁决后由 DRAFT 变 FROZEN。

**Codex Review**：方案与实际源码是否一致、能否实现、是否越界、是否错误宣称已有能力；重点复核所有“历史审查修订”的 18 条已知偏差。

**通过判据**：S0_AUDIT 可追溯；核心契约确切；S1 不需要靠猜测 API 开发；CLI 保护和测试可实施。此后每轮改规格必须写变更说明并经用户批准。

## 4. R03｜S1：独立 Server Main 与演示 Catalog

**目标**：Web 可以持续提供 HTTP，同时原 CLI JAR 的 `java -jar` 不回退；Catalog 能对已存在的真实模拟 fixture 提供安全映射。

**必须实现**：

1. 只添加必要 Spring Web 依赖，新建独立 `DeviceTruthServerApplication`，不得复用 CLI 的单次关闭逻辑。
2. Maven 插件显式确定**原 CLI 为默认 fat JAR Main**；通过实际执行确认 CLI 仍会退出。
3. Server 用在当前项目上**已真实验证**的命令启动，绑定 loopback（开发配置）；验证健康检查（或稳定端点）以及持续运行。
4. `SupportedScenarioCatalog` 从真实 `scenarios.json` 和已核实配置得到 `assetId/domain/symptomCode/diagnosisAsOf`；不可虚构 `domainOf/listSymptoms`。
5. 未知 SN-0245 拒绝映射到 CAR-B；CAR-B 的模拟业务时间一致。

**建议修改**：`pom.xml`、server 配置、Catalog 模块、相关 tests、README；可能需要依赖装配最小调整。

**测试/验收**：CLI A/B/C/D/原测试回归、Server 稳定运行、Catalog 已知/未知映射及 CAR-B 时间正确。对应 TC-08/TC-13/TC-18/TC-19 的相关部分。

**Codex Review**：双 Main 生命周期、扫描范围、jar mainClass、真实启动日志、可回退性；不为了 Web 改坏原 CLI。

## 5. R04｜S1：会话与四类路由

**目标**：通过会话 API 接收中文消息，程序验证四种路由，不接入完整故障调查。

**必须实现**：

1. `POST /api/sessions` 返回 `201` 和合法 UUID；`GET /api/sessions/{id}/messages` 可读。
2. 有界线程安全会话存储；资产上下文只复用已明确且经 Catalog 核实的 assetId；`sessionId` 非认证。
3. `POST /api/sessions/{id}/messages` 的 RouteDecision 仅 `QUERY/DIAGNOSE/CLARIFY/UNSUPPORTED`；结构化 DTO、入参验证、模型输出程序校验。
4. 离线受限 Stub/确定性 Router 和在线真实 LLM Router 分离；在线配置只能服务端设置；不可冒充离线路由为中文模型理解。
5. 未指定“这台设备”且无上下文→CLARIFY；不支持设备/危险请求→UNSUPPORTED；不能编造 Controller 答案。
6. 与 R05/R06 尚未实现的处理路径连接时可以明确返回 `NOT_IMPLEMENTED` 测试态，不得伪造真实 QUERY/DIAGNOSE 结果；在最终 R07 前必须完整联通。

**测试/验收**：`201/200`、四路由、上下文、非法 ID、模型不可用、容量限制、session 隔离；在线路由须实际可用才标通过。对应 TC-01/TC-07/TC-08/TC-09/TC-19/TC-20 的相关部分。

**Codex Review**：Router 只是分类不是另一套 Agent；Catalog 边界、会话线程安全、未知/缺失信息准确处理。

## 6. R05｜S1：事实查询 + Core 请求适配

**目标**：受限 QUERY 返回有来源的事实；自然语言 DIAGNOSE 被正确、审慎映射到既有 Core 契约。

**必须实现**：

1. `QueryService` 仅支持 `ASSET_PROFILE` / `MAINTENANCE_HISTORY`；通过实际 `AssetRepository.profile/history` 查询，不为事实查询伪造 DiagnosisRequest。
2. 检查 session/assetId 与返回记录资产一致；`sources` 正确包含 `evidenceId/sourceRef/sourceType`；`dataNature=SIMULATED`。
3. 出厂记录、维修历史与“当前实际装机事实”语义严格区分；不能从出厂记录直接给出当前确定配置。
4. `DiagnosisGoal` 保留原始 message 和 normalizedGoal；Catalog 固定模拟 asOf、RuleRepository 确认 symptom 规则；转换旧 `DiagnosisRequest`。
5. 症状映射是有限别名，不把所有自然语言故障都匹配 `COMM_TIMEOUT`；未知 Asset/Rule 走 CLARIFY/UNSUPPORTED。
6. 如果 R06 未完成，诊断交付仅可验证 Goal→Request；不可伪装诊断已执行。

**测试/验收**：真实 profile/history 查询、来源字段、当前配置不能确定时的安全表达、固定业务时间、未知故障。对应 TC-02/TC-03/TC-08/TC-13/TC-14/TC-15。

**Codex Review**：Repo 和 Gateway 边界、真实 fixture 数据、时间源、源 ID 严格对应、没有硬编码答复。

## 7. R06｜S1：异步运行、结果与结束后事件

**目标**：中文诊断经 R04/R05 的 Route/Goal 进入原 `TaskOrchestrator`，有真实异步状态与结果。

**必须实现**：

1. `ReasonerFactory.create()` 每任务独立创建 Scripted/OpenAI/Qwen Reasoner（以当前实现为准）。
2. `DiagnosisRunService` 有界执行器、队列拒绝、`diagnosisId` 公共 ID 和 Core/Report 内部 ID 对照。
3. 接受诊断 POST 固定 `202 Accepted + Location + QUEUED`；GET 查询实际执行状态。**Core RunStatus FAILED 不可包装为 COMPLETED**。
4. 真实读取 RunReport 的 `outcome/stepsUsed/stopReason/supportingEvidenceIds`（存在则按真实字段）；未结束时为 null/不出现。
5. `GET /api/diagnoses/{id}/events?sessionId=...`：运行中不可用；完成时按真实事件顺序提供白名单 `RunEventView`，不泄漏 Prompt/Context/密钥/绝对文件路径。
6. 运行/会话归属校验，不匹配 404。正确处理失败、超时、队列满、服务端模型配置失败，无永久 QUEUED 幽灵任务。
7. 保留原 CLI 和 Guard/安全负例行为，不实现实时推流或中途补证。

**测试/验收**：真实 Core 调用可在日志/报告中证明；双并发状态不串案；Core FAILED→API FAILED；202 竞态稳定；事件安全。对应 TC-04/TC-05/TC-06/TC-10/TC-11/TC-16/TC-17。

**Codex Review**：重点读异步状态机、Reasoner 实例作用域、错误映射、事件来源/过滤。不要仅凭 Controller 单测宣告全链通过。

## 8. R07｜S1：端到端验收与文档收敛

**目标**：按权威修订方案 §7 的 **TC-01~TC-20** 完整复核，不再增加需求。

**必须完成**：

1. 运行单测/打包/CLI A/B/C/D 与 E 现有负例、安全约束回归；记录本轮实际测试数，不以历史 42 代替。
2. 本地真实 HTTP：创建会话、QUERY 两类、DIAGNOSE、CLARIFY、UNSUPPORTED、GET status/events/messages。
3. 校验请求与诊断业务时间分离；CAR-B 不会因提交时间改变证据有效性；全部响应有 SIMULATED 语义。
4. 校验未知资产、未知症状、不安全动作、并发串案、排队拒绝、模型失败、来源准确性、事件脱敏。
5. 仅在真实模型 API 可用的环境下执行 online Router + online Reasoner；不可用则记录 `BLOCKED_BY_ENV`、具体障碍、离线测试范围，不宣称在线 PASS。
6. 填写 `S1_ACCEPTANCE_REPORT.md` 中 TC-01~TC-20 逐项结果、请求响应、命令、输出/日志、修改文件、遗留问题。
7. 更新根 README 的**真实可运行**启动/API 命令，并记录当前 Java 调用链便于用户理解。
8. 最终结果明确区分“已有 HTTP+受控 Core”与“尚未实现完整 Hypothesis O-H-E-D / 多轮恢复 / 双域 Skill / Vue”。

**Codex Review**：审查整个 S0/S1 的架构一致性与**真实集成证据**；拒绝静态编造的 API 响应、虚假的在线通过。针对 P0/P1 要求修复复测。无在线模型条件时可以注明 S1 离线验收通过、在线验收阻塞，不能混为全量通过。

## 9. 测试编号映射（原方案 TC-01~TC-20）

| 原测试 | 主要会话 | 核心验证 |
|---|---|---|
| TC-01 | R04,R07 | Session 创建/查询 |
| TC-02 | R05,R07 | profile 事实与真实来源 |
| TC-03 | R05,R07 | history 事实不编造 |
| TC-04 | R06,R07 | DIAGNOSE 真正调用 Core |
| TC-05 | R06,R07 | 202/QUEUED/Location + GET |
| TC-06 | R06,R07 | 结束后真实事件 |
| TC-07 | R04,R07 | 无当前设备 CLARIFY |
| TC-08 | R03,R04,R05,R07 | 未登记 SN-0245 不混用证据 |
| TC-09 | R04,R05,R07 | 未支持/危险操作明确拒绝 |
| TC-10 | R06,R07 | 并发隔离及 session 归属 |
| TC-11 | R06,R07 | 模型/Core 失败映射 |
| TC-12 | R01,R03,R07 | CLI A/B/C/D + E 负例 |
| TC-13 | R03,R05,R07 | requestedAt 与 asOf |
| TC-14 | R05,R07 | 出厂历史≠当前确认状态 |
| TC-15 | R05,R07 | evidenceId/sourceRef/SIMULATED |
| TC-16 | R06,R07 | 脱敏与内部路径保护 |
| TC-17 | R06,R07 | 容量拒绝、无幽灵任务 |
| TC-18 | R03,R07 | Server/CLI 双入口 |
| TC-19 | R04,R06,R07 | 无 Key 离线链路 |
| TC-20 | R04,R06,R07 | 在线 Router + Core 模型（可用时） |

## 10. 不属于 S0/S1 的后续任务（禁止提前编码）

- **S2**：多轮会话、状态持久化/补证/暂停/恢复；建立候选 Hypothesis、支持/反驳证据、SUPPORTED/INVALIDATED、因果结论依赖的非平凡故障闭环。
- **S3**：标准 `SKILL.md`、工厂 `SC-0245-B`、新能源车专业场景、双域数据与工具接入。
- **S4**：Vue 产品工作台；如需运行时可视化，届时再评估 Event Sink/SSE。
