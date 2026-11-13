---
gsd_state_version: "1.0"
milestone: v1.0
current_phase: 03
current_phase_name: 设备事实与持久连续调查
current_plan: Not started
status: executing
stopped_at: Phase 2 complete, ready to execute Phase 03
last_updated: "2026-11-12T12:47:35.495Z"
last_activity: 2026-11-12
last_activity_desc: Phase 2 complete, transitioned to Phase 03
state_head: 08d8ea0b0aa82f3f1c093935a86381273422e30a
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 13
  completed_plans: 5
  percent: 20
milestone_name: 通用设备检修 Agent
total_phases: 5
total_plans_in_phase: 3
review_status: phase_2_plan_03_user_accepted
execution_authorized: false
last_execution_authorization: 2026-11-11用户明确授权修复02-03独立Review问题，保留既有修改且不自动commit/push；仅修复与记录，不推进完成状态或后续计划。
---

# Project State

> **2026-11-12 关闭结果：** 用户明确要求带遗留项关闭Phase 2；原生 `phase uat-passed 2 --require-verification` 与 `phase complete 2` 均成功，GSD当前Phase 03、5/13计划、2/5阶段。正文安全、PDF和浏览器验收转后续，不作技术通过。原生命令误将部分需求标Complete，已按实际状态恢复Pending；原生 `state update-progress` 显示20%，但 `progress json` 以5/13计算为38%。

> **2026-11-12 用户决定：** 用户认可检索与多轮修复并通过本轮 Review，正文安全后续解决，安全风险与NOT_RUN不变。PDF、浏览器及阶段出口未完成；原生 GSD `state advance-plan` 返回 `advanced=false`（02-03 SUMMARY 尚为 blocked），因此未声称计划或阶段已经推进。

> **2026-11-12 检索与补答修复：** 真实embedding测量后默认向量门槛调整至0.45，低覆盖关键词排除通用词误命中；原固定集三通道正向6/6、部件号2/2、定位6/6、负例全通过，补充7个负例21次查询为空（其中灯光样本用于规则调整）。真实DeepSeek在更明确的缺失信息场景中同run追问→补答→补答后检索→引用成功；整体仍因正文安全NOT_RUN。真实PG针对性33/0/0/0，脚本13/13。02-03与Phase2状态未推进。

> **2026-11-12 真实供应商实测：** 用户配置密钥后，在独立临时 PostgreSQL 库执行真实 DashScope embedding 和 DeepSeek 测试，测试库已清理。固定检索正向 6/6、部件号 2/2，但无关查询 VECTOR/HYBRID 非空，整组 FAIL；单轮真实模型→工具→上传文档答案引用及章节定位已通过，正文安全 NOT_RUN，套件整体 NOT_RUN；多轮模型直接完成而未追问，FAIL。详见 02-03-SUMMARY。GSD 计划和阶段状态未推进。

> 2026-11-12：用户授权统一本地模型配置，embedding 和 Agent 模型设置可使用既有私有文件；真实供应商验收尚须实际运行，02-03 和 Phase2 状态不变。

## Project Reference

See: .planning/PROJECT.md (updated 2026-11-09)

**Core value:** 自由设备问题能开启有来源、可补证、可恢复的可信调查。
**Current focus:** Phase 2按用户决定带遗留项关闭，GSD已进入Phase 03；下一计划03-01尚未实施。真实embedding检索与DeepSeek同run补答链路已实测；正文安全3项P1及1项P2、PDF/OCR及浏览器端到端验收转后续，未标技术通过。

## Current Position

Phase: 03 of 5 (设备事实与持久连续调查)
Current Plan: Not started
Total Plans in Phase: 3
Plan: 3 of 3
Status: Ready to execute
Last activity: 2026-11-12 — Phase 2 complete, transitioned to Phase 03

Progress: [██░░░░░░░░] 20%

## 02-02 本轮事实（2026-11-11）

> **最新独立复审与用户决定：** 用户明确要求有问题记录后直接通过；本计划已由 GSD 原生 `state advance-plan` 从 2 推进至 3。独立复审保留 3 项 P1（答案危险操作、无现场证据确定结论、追问绕过安全检查）和 1 项 P2（否定性确诊说明误拒），均未修复；详见 [02-02-REVIEW](phases/02-open-rag/02-02-REVIEW.md)。独立复跑真实PG全量 229/0/0/0、前端测试12/12和构建通过；两个独立反例仍被放行，不能宣称安全保护通过。02-03 未授权、Phase2未关闭。以下修复轮与待审描述均为历史。

> **审查修复轮 3（剩余 P1×2，待复审）：** “注意风险/验电后”等弱安全词放行危险操作、无现场证据输出确定性设备故障结论，两项均改为结构性关闭：`AnswerGuard` 含动作子句仅放行禁止性提示或资质边界声明（白名单式，弱安全词不再豁免），无 `fieldVerified` 时设备指代+确定性结论的断言一律拒绝，高风险答案附服务端受控安全提示；`TaskOrchestrator` 拒绝反馈附修正指引，有界重试后 FAILED 不变。`AnswerGuardTest` 21 项、`GeneralAgentFlowTest` 16 项、全量 **229/0/0/0**，打包与 `verify_cli.py` 通过。详见 [02-02-SUMMARY](phases/02-open-rag/02-02-SUMMARY.md) 的“审查修复轮 3”表。

> **审查修复轮 2：** 复审提出 P1×3（确诊正文绕过守卫、安全词同句放行且 uncertainties/limitations 未检查、遗留 QUEUED 超 100 条留幽灵任务）已全部修复并补测试：`AnswerGuard` 新增正文级确诊表述检查与逐子句危险动作检查（转折/冒号切分），`AgentRunRepository.queued` 去掉 100 条上限；`AnswerGuardTest` 16 项、`GeneralAgentApiTest` 7 项（含 105 条遗留队列接手用例）、全量 **222/0/0/0**，打包与 `verify_cli.py` 通过。真实模型/embedding/浏览器 E2E 仍未验证。详见 [02-02-SUMMARY](phases/02-open-rag/02-02-SUMMARY.md) 的“审查修复轮 2”表。

> **审查修复轮：** 独立审查提出 P1×5（无核验仍可确诊、危险建议绕过、混合资料标错 AUTHORIZED、M0 遗留队列未交接、并发写入卡 RUNNING）与 P2×2（claim 独有引用丢失、页面只显示“执行失败”）后，已全部修复并补测试；`GeneralAgentApiTest` 6 项、`GeneralAgentFlowTest` 14 项、`AnswerGuardTest` 10 项、前端 12 项，全量 **215/0/0/0**。`fieldVerified` 在 M1 恒为 false，本阶段不存在合法确诊路径；崩溃遗留 RUNNING 仍留给 M2。详见 [02-02-SUMMARY](phases/02-open-rag/02-02-SUMMARY.md) 的“审查修复轮”表。

- 新增 `V5__agent_run_message_link.sql`（`dt_message.run_id` 可空列 + 复合外键 + CHECK），补答后可按 run 精确重建消息序列；既有库实测从 V4 只应用 1 个迁移升级到 V5。
- 通用协议：`ActionProposal` 追加 `CALL_TOOL`/`ASK_USER`/`ANSWER`，legacy 五动作保留；新合同 `AgentTaskInput`/`AgentResult`；`GeneralReasoner`（含 attemptedTools/feedback）与 `ScriptedAgentReasoner`、`StructuredAgentReasoner`、`AgentModelConnections`。
- 唯一主循环演进：`TaskOrchestrator.runAgent(...)` 与 legacy `run(...)` 同类共存；工具经 `CapabilityGateway.executeTool`（未知/未授权/顶层参数错误一律拒绝，嵌套由工具强类型解析），答案经 `AnswerGuard`（引用子集、风险由服务器判定、无资产不得确诊）。
- 产品装配：`agentCapabilityGateway` 只注册知识工具，legacy `capabilityGateway` 保留并 `@Primary`；`GeneralAgentService` 有界 worker、每 run 新推理器、`devicetruth.agent.*` 配置（reasoner/worker-enabled/max-concurrent-runs/max-queue）。
- 持久化与 API：`AgentRunRepository` 状态机（条件更新防重复接手）、待答问题、引用读取、白名单事件；`ConversationRepository` 助手/补答消息与 `runMessages`；`POST .../messages` 支持 `replyTo` 三元组，`GET /api/runs/{id}` 返回 `pendingQuestion`/`result`，新增 `GET /api/runs/{id}/references`。
- 前端 `frontend/`：Vue3.5.45 + Vite8.3.7 + Vitest5.1.0 + TS5.9.3 + vue-tsc3.3.14，`api.ts` 冻结两个合同与安全轮询，`App.vue` 展示消息/状态/追问/答案/引用。
- 实际执行：`mvn -o test` 215/0/0/0（35 suites）、`mvn -o -q -DskipTests package`成功、`python scripts/verify_cli.py`全通过；新增 `GeneralAgentFlowTest` 14/0/0/0、`GeneralAgentApiTest` 6/0/0/0、`AnswerGuardTest` 10/0/0/0；独立 HTTP 进程在既有 `devicetruth` 库完成 V4→V5 升级并跑通两轮（导入201→提交202→WAITING→补答202同run→COMPLETED，错版本补答409且runs=1）；前端 `npm ci`/`npm run test -- --run` 12/0/0/0/`npm run build` 均通过。
- 未验证（NOT_RUN）：真实千问/OpenAI 通用 Agent 调用、真实 embedding 与向量质量、浏览器人工演示 E2E-01/02/03 与 E2E-07-basic、PDF/混合检索、跨进程恢复与租约/CAS（M2）、并发补答与规模性能。
- 未 commit/push；`status: blocked`、`review_status: pending`；未标记 PLAN 完成、未改 ROADMAP 阶段态。详见[02-02-SUMMARY](phases/02-open-rag/02-02-SUMMARY.md)。
 [██░░░░░░░░]

## 02-01 本轮事实（2026-11-10）

> **2026-11-11独立复审更新：** 原P1（含V3错误改标）与两项P2全部关闭，02-01完成。实际复跑185/0/0/0（35 suites），打包/CLI通过；真实PG ExistingV3UpgradeProbe 9断言通过，覆盖迁移前引用、旧版拒绝、新版重建及再次启动不重新隔离。下方自测与blocked记录保留为此前历史，当前结果以[02-REVIEW](phases/02-open-rag/02-REVIEW.md)和[02-VALIDATION](phases/02-open-rag/02-VALIDATION.md)为准。

- 新增`V2__knowledge_sources.sql`（uploaded_file/knowledge_document/knowledge_chunk/evidence_reference，`vector(1024)`+HNSW）、`V3__knowledge_embedding_identity.sql`（旧档案标签归一化）及`io.devicetruth.knowledge`包、`ToolContext`、`KnowledgeController`；`ServerConfiguration`仅在product profile装配知识Bean。真实embedding固定千问`text-embedding-v4`（DashScope compatible-mode），档案标识为`provider:model@dimension-cosine-r<revision>`，专用密钥为空时复用`DASHSCOPE_API_KEY`。
- 独立Review首轮P0 0/P1 1/P2 2均已修复；复审又发现V3按模型名推断供应商会把旧离线向量改标为真实档案，已新增V4将所有修复前向量隔离为`NEEDS_REBUILD`并补真实PG升级回归（不静默复用、不进入候选、正文/历史引用可读、新版本重建）。切片按配置上限切分、SUMMARY改`blocked`使GSD原生统计恢复2/13。
- 实际执行：`mvn -o test` 185/0/0/0（原154+新增31）、`mvn -o -DskipTests package`成功；`KnowledgeSearchTest`13/0/0/0；`KnowledgeChunkerTest`5/0/0/0；`EmbeddingClientTest`6/0/0/0；`KnowledgeConfigurationTest`6/0/0/0；`KnowledgeUpgradeMigrationTest`1/0/0/0（真实PG升级）；`python scripts/verify_cli.py`全通过；打包JAR在既有`devicetruth`库应用V4：旧版重导409 `DOCUMENT_NEEDS_REBUILD`、2026.2重建后可检索、隔离前引用仍可读；打包探针验证provider切换/换档案/错维度均409、切片上限80实测最长80。
- 未验证（NOT_RUN）：真实千问embedding调用与真实向量召回、PDF/混合检索、知识工具接入Runtime/Agent/Vue、真实文件存储与版本重试、规模性能。
- 未commit/push，未改ROADMAP阶段状态；`status: blocked`（待复审），`review_status: changes_requested`。Git Bash 下`PATH`需用`/d/...`形式，否则`D:`冒号会把路径截断、`java`仍为JDK17（已写入README）。详见[02-01-SUMMARY](phases/02-open-rag/02-01-SUMMARY.md)。

## Performance Metrics

- Total plans completed: 4/13（12/37任务）；完成阶段1/5。原生STATE进度采用阶段口径，02-03未计入完成。
- M0已关闭；M1的02-01/02-02已完成并提交，02-03独立Review修复后自测、待复审。
- 当前HEAD为08d8ea0（02-02提交）；02-03实现与交付记录保留在工作树，未commit/push。最新全量测试241/0/0/0。

## Accumulated Context

### Decisions

- 验收策略更新（2026-11-10）：技术测试/CLI A–E/状态/安全由代理实际执行；仅产品/业务/设计或无法自动判断事项集中确认，尽量每Phase一次。按ACCEPTANCE.md使用原生SUMMARY coverage，不修改全局GSD，不以历史报告或用户确认替代未执行测试。
- 最新授权（2026-11-10）覆盖Phase 1验收、独立Review、修复与复验；出口全部满足后提交当前Git，随后仅执行02-01。02-02/02-03明确不执行。下列旧授权记录保留为历史，不覆盖本条。
- 需求基准为指定V1.0-Draft.1 PostgreSQL设计；旧V0.1/V0.2仅作legacy回归，不续跑旧S2。
- 01-01实施时用户明确要求执行该计划，覆盖此前仅规划限制；当时授权仅该计划，不含后续实施、部署或Git提交。
- 01-02实施时用户指令为“开始执行下一项计划”，承接已核验的01-01，仅授权01-02实施；不自动提交Git，不推进02-01等后续计划。
- 2026-11-10用户要求先补齐Phase 01 COVERAGE并通过检查再逐项用户验收；本轮仅文档与针对性验证，不新增业务实现或后续计划授权。
- 用户随后明确指定使用interview-postgres；复用PG16.15/pgvector0.8.7，仅新建独立devicetruth及随机测试库，不在interview_guide执行DDL/DML。不再拉取新PG镜像。
- 新PG连接凭据仅内存/服务端环境使用；ProductConfiguration只在HTTP product装配，默认legacy HTTP/CLI不创建数据源。M0仅QUEUED，尚无Agent worker。
- 选择演进TaskOrchestrator为唯一产品循环，保留AgentScope Model API；见01-01 ADR-runtime。
- 原型两候选均通过6样例，连续两次clean输出相同；这是测试原型，生产通用循环未改造。
- 通用原文必填、资产可空，CALL_TOOL/ASK_USER/ANSWER、owner/project由服务器确定；见CONTRACTS。
- M1同run正常补答；M2才验收跨进程检查点、租约/CAS、幂等并发、累计预算可靠性。
- 保留旧模型私有文件兼容：文件非空值优先、环境补缺；新PG/embedding只服务端环境注入。
- 单体/单Agent/只读设备，PG与pgvector同实例，Vue从M1接入；未添加生产双栈。

### Pending Todos

- 用户第1项及集中回复“上述产品边界与设计符合预期”均已保存；01-UAT为6/6通过，阶段验证6/6 passed，Phase1已原生关闭。
- 02-01已复审通过并提交（e9aed33）；02-02带已知问题通过并提交（08d8ea0）。
- 02-03独立Review的3项P1与1项P2已修复并自测，继续等待独立复审；真实模型/真实embedding、PDF与浏览器演示保持NOT_RUN，混合检索仅确定性合同通过。

### Blockers/Concerns

- Phase1已关闭、02-01/02-02已完成；02-03待复审，不勾Phase2完成。
- Maven实际使用JDK21.0.13.0.2/Maven3.9.9，但直接java命令实测仍为17；用户命令必须显式使用仓库.tools内JDK21，不改全局设置。验收期间不再clean，避免临时移除JAR。
- Docker Desktop Linux引擎实际可用，管道npipe:////./pipe/dockerDesktopLinuxEngine；interview-postgres已启动，映射5432。最初PG17拉取失败后已停止并按用户指令改用既有实例，历史失败保留。
- 真实PG/V7迁移、目标/补答附件、文件版本关联与混合检索合同已验证；真实模型与真实embedding缺key、浏览器人工演示、Agent执行恢复未验收。
- 本地固定身份仅可证明服务层授权合同；共享部署前需真实认证。
- V5增加`dt_message.run_id`；V6追加受控文件导入状态与元数据；V7解除文件checksum唯一约束、拆分旧多版本文件并增加文件—文档关联，均为只前进迁移，不删除历史文档或引用。

## Verification Snapshot

2026-11-10最终出口：用户集中确认已收到并保存；独立gsd-verifier报告passed，6/6 must-haves，UAT6/6；原生phase uat-passed 1 --require-verification返回passed=true/blockers=[]/stale_check_indeterminate=false。phase complete 1已实际关闭阶段并进入02。原生命令附带历史文档解析警告：把SUMMARY表格里的python命令当文件路径、未识别中文需求表为注册表；实际命令文件/63需求均已人工核对，非缺失实现，需求四态与主映射未被命令改写。

2026-11-10策略落实：实际runtime-identity确认GSD1.20.1；核对verify-work、summary coverage模板、coverage.cjs和配置schema，未修改全局文件或增加假配置键。原生分类由legacy变为4自动/1人工，schema无错；5个正负分类探针全通过。策略调整后实际mvn -q package仍154/0/0/0，打包双进程PG重启再次通过（新会话a16084b9-4d82-43ad-804e-0242bc64e934，run402452c7-f871-4325-bbbd-0a4d31910896），原文保留、无DB CLI B通过；未声称真实现场。用户第1项“通过”已保存，原第2项人工提示被新策略取代，不冒称收到回复。最新简洁报告见01-ACCEPTANCE，阶段待集中确认。

2026-11-10本轮：CR-01修复前真实PG HTTP回归失败（数字userMessage返回202，预期400）；HTTP装配复用Json.MAPPER.copy()后，六类原始非法JSON均400且消息/run/version不增长。针对性PG+旧HTTP通过；随后mvn -q clean test和mvn -q package均154/0/0/0（30 suites，PG8、旧HTTP16），python scripts/verify_cli.py全通过。新JAR两次独立Java进程原文和同run QUEUED读回一致，无DB/product环境CLI B仍RESOLVED/B规则。Runtime输出SHA256与旧固定值一致。独立代理初审1个BLOCKER、复审clean，未解决0。原业务库public表17、随机测试库0、Flyway成功记录1；Compose config通过。详细证据见01-VALIDATION及01-REVIEW；01-UAT为用户确认唯一记录。未运行外部模型/embedding/UI，02-01未执行，Git未提交。

2026-11-10门禁补验：初次GSD检查因中文表头未被识别而报空矩阵；补规范三列表后识别38项能力（21纳入／17明确不接入），passed=true、block=false。重跑文档检查通过，129个本地链接／36处测试方法引用有效，109份业务文件摘要未变；git diff --check退出0。此次没有新Maven/PG/CLI/UI执行，检查脚本中的51项为已有Surefire结果；未创建UAT或预填用户确认，独立Review仍待进行。

2026-11-10：COVERAGE覆盖11条产品API、10类SDK、3类DB取舍和7项阶段需求；覆盖／方法／链接／hash／业务文件保护检查通过，11 suites共51/0/0/0。本次未配置PG环境，未重跑真实PG、全量test/package、CLI、外部模型或UI；下列153项等为2026-11-09历史结果，非本次复测。

01-01原146项保留，01-02新增7项；mvn -q test与mvn -q package均153/0/0/0（30 suites），真实PG专项7/0/0/0。
新JAR实际启动/关闭/重新启动，原文/同一run的QUEUED持久读回一致；不可连接DB且product环境下legacy CLI B仍RESOLVED/B规则；verify_cli全部通过。
interview_guide public表数量基线17；本轮仅在独立devicetruth/随机测试库执行DDL/DML，随机测试库已清理。Java验收进程已关闭，用户指定PG容器保留运行。
未改JSON数据、规则、模型适配或Runtime决策逻辑；ServerHttpTest显式scripted，不发外部LLM。凭据扫描通过；详见01-02-SUMMARY和VALIDATION。

## Session Continuity

Last session: 2026-11-11 (Asia/Shanghai)
Stopped at: Phase 2 complete, ready to execute Phase 03
Resume file: .planning/NEXT_SESSION.md
Read first: AGENTS.md → STATE → 02-03-SUMMARY → 02-03-PLAN → 02-02-REVIEW → PROJECT → ROADMAP
