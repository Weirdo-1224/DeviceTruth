---
phase: 01-baseline-contracts
verified: 2026-11-10T15:04:00Z
status: passed
score: 6/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
human_verification: []
covered_files:
  - .env.example
  - .gitignore
  - .planning/ACCEPTANCE.md
  - .planning/phases/01-baseline-contracts/01-01-PLAN.md
  - .planning/phases/01-baseline-contracts/01-01-SUMMARY.md
  - .planning/phases/01-baseline-contracts/01-02-PLAN.md
  - .planning/phases/01-baseline-contracts/01-02-SUMMARY.md
  - .planning/phases/01-baseline-contracts/01-ACCEPTANCE.md
  - .planning/phases/01-baseline-contracts/01-REVIEW.md
  - .planning/phases/01-baseline-contracts/01-SECURITY.md
  - .planning/phases/01-baseline-contracts/01-VALIDATION.md
  - .planning/phases/01-baseline-contracts/ADR-runtime.md
  - .planning/phases/01-baseline-contracts/CONTRACTS.md
  - .planning/phases/01-baseline-contracts/COVERAGE.md
  - .planning/phases/01-baseline-contracts/RUNTIME-SPIKE.md
  - AGENTS.md
  - README.md
  - compose.yaml
  - pom.xml
  - src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java
  - src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java
  - src/main/java/io/devicetruth/api/AgentConversationController.java
  - src/main/java/io/devicetruth/app/DeviceTruthApplication.java
  - src/main/java/io/devicetruth/application/agent/GeneralAgentService.java
  - src/main/java/io/devicetruth/server/DeviceTruthServerApplication.java
  - src/main/java/io/devicetruth/server/ProductConfiguration.java
  - src/main/java/io/devicetruth/server/ServerConfiguration.java
  - src/main/resources/application-product.properties
  - src/main/resources/db/migration/V1__agent_conversations.sql
  - src/test/java/io/devicetruth/server/PersistentConversationTest.java
  - src/test/java/io/devicetruth/server/ServerHttpTest.java
  - src/test/java/io/devicetruth/spike/RuntimeComparisonTest.java
  - src/test/resources/runtime-comparison/cases.json
covered_digest: "v4:sha256:e4afca34d2efb1cbe864df34f3ab05c7258db42202a9e05d2bc5225c812934e4"
---

# Phase 1：基线与通用契约 — 阶段目标验证

**目标：** 在保留旧回归的前提下，明确新契约和唯一主循环，再让自由消息/排队任务进入 PostgreSQL。

**结论：passed，6/6技术must-haves有实现及行为证据，未解决阻断问题0。** 用户已先后明确回复“第1项通过”和“上述产品边界与设计符合预期”；产品/设计集中确认已完成，当前无额外人工验收项。正式阶段关闭、Git提交及02-01启动由主代理执行原生出口检查后处理，不执行02-02/02-03。

## Goal Achievement

### Observable Truths

由ROADMAP的3条成功标准及两PLAN的truths合并去重，按可独立核验的行为分为以下6项；没有以PLAN削减ROADMAP范围。

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | 旧CLI A/B/C/D及E安全负例保持可复现 | VERIFIED | 当前AcceptanceTest 5/0/0/0；`target/cli-verification.json`实际A/B各对应A/B规则、C冲突无规则、D缺证无规则；CLI脚本检查真实打包报告与事件。用户CLI B确认保留 |
| 2 | 自由输入、身份权限、引用、风险及状态契约明确 | VERIFIED | 直接核对CONTRACTS的输入/三动作/来源时间/风险表/同run补答三元组/事务/配置/M1与M2边界；当前DTO/service按M0支持子集落地，未支持引用与reply整体拒绝。用户已集中认可产品边界与设计 |
| 3 | Runtime比较可重跑并只选择一个生产主循环方向 | VERIFIED | RuntimeComparisonTest真正调用两候选，固定cases及明确状态/事件/读取次数断言；当前Surefire 1/0/0/0（内部6样例×2候选）；clean后输出SHA256与固定值一致。ADR选择演进TaskOrchestrator，ReActAgent仅在test原型，无第二生产循环 |
| 4 | 新API原子保存并按服务器归属读回原文、可空资产和QUEUED run | VERIFIED | Controller→GeneralAgentService→两个JdbcTemplate仓储已实际追踪；submit同一事务行锁/请求键/append/enqueue。PG8项覆盖原文/emoji、GET消息、重复并发、归属、复合外键、事件失败回滚及六类严格JSON，无占位数据返回 |
| 5 | 空库可迁移，服务/进程重启后同会话/原文/run不丢 | VERIFIED | ProductConfiguration以Flyway migrate先于service装配，V1建表/约束/vector扩展；PG测试创建随机空库并重开服务，成功迁移记录1。最新打包脚本明确停止首Java进程、启动第二进程并对整个会话/run作相等断言；最新摘要原文保留且同run仍QUEUED |
| 6 | legacy CLI不依赖PG，M0不启动通用Agent worker | VERIFIED | 两Main排除DataSource/Flyway自动装配，ProductConfiguration仅HTTP product装配；CLI保持JSON仓储→原TaskOrchestrator。最新打包检查设置不可连接DB和product环境仍B/RESOLVED；AgentRunRepository仅enqueue/read，无执行调度，QUEUED响应不冒称答案 |

**Score：6/6；behavior_unverified：0；无override。** 技术行为由本轮已执行且实施文件未变的行为证据支撑，不要求用户重复技术操作。新产品尚无通用执行能力是本阶段明确边界，不是以“以后实现”掩盖本阶段必须交付的缺陷。

### Required Artifacts

| 产物 | 核验结果 |
|---|---|
| RuntimeComparisonTest.java / cases.json | 实际解析同一固定夹具，构建evolved-loop和ReAct候选；包含重建、重复/错误补答、未知/写/跨资产工具及伪造引用断言；不是只比较自生快照 |
| RUNTIME-SPIKE.md / ADR-runtime.md | 保留环境、复现命令、对比表及局限；唯一方向明确，生产改造归02-02 |
| V1__agent_conversations.sql | 存在且含真实DDL、vector扩展、owner/project复合外键、请求键/消息及事件序号唯一约束 |
| ConversationRepository.java | 真实JDBC INSERT/SELECT/UPDATE，参数绑定与归属条件，参与service事务和会话行锁 |
| PersistentConversationTest.java | 实际启动Spring HTTP、连接明确配置的真实PG、创建/删除独立随机库；无跳过代替通过 |

两PLAN的must_haves.artifacts共7项均存在、实质实现且连接到相应调用/验证链。其余Controller/service/配置/run仓储为支持链，一并核对。

### Key Link Verification

| Link | 实际调用/连接 | Status |
|---|---|---|
| CONTRACTS→ADR | 合同引用ADR，ADR按合同三动作、权限、等待与唯一循环作取舍 | WIRED |
| RuntimeComparisonTest→cases.json | getResourceAsStream读取固定路径，遍历fixture.cases并对每个候选执行断言 | WIRED |
| ADR→RUNTIME-SPIKE | ADR直接引用比较证据、复现与取舍，原型结果未称生产恢复 | WIRED |
| Controller→Repository | submit/read/list调用GeneralAgentService；后者经事务调用ConversationRepository/AgentRunRepository，真实HTTP/PG测试贯穿 | WIRED（间接调用） |
| Repository→V1迁移 | Repository使用V1表与字段；ProductConfiguration.productFlyway按classpath:db/migration发现V1，service有DependsOn迁移。空库HTTP测试实际贯穿 | WIRED（Flyway约定发现） |

GSD `verify key-links`名称扫描对01-02的后两条曾返回未验证，原因是没有直接类名/SQL文件名引用；本报告没有把该扫描虚报通过。上述源码调用链和真实空库迁移/HTTP/重启行为证明实际连接，自动名称扫描的局限不构成未连接实现。

### Data-Flow Trace

| 公开值/行为 | 数据来源 | Result |
|---|---|---|
| 会话/消息列表、详情 | 服务器身份→参数化SELECT dt_conversation/dt_message→RowMapper→DTO | FLOWING，PG真实记录 |
| POST消息与run、GET run | DTO→service校验/事务→INSERT消息/run/event→SELECT dt_agent_run | FLOWING，QUEUED为实际持久状态 |
| 旧CLI结果/来源/事件 | 打包SIMULATED JSON→Gateway→EvidenceManager/Guard→Core报告 | FLOWING，明确为模拟数据，非现场事实 |

## Requirements Coverage

| ID | Source Plan | 本阶段判定 | 实现/证据及边界 |
|---|---|---|---|
| BASE-01 | 01-01 | SATISFIED | Java21/Boot/AgentScope版本与连接复用；Runtime候选实测及唯一ADR，不升级/新增生产Agent栈 |
| BASE-02 | 01-01 | SATISFIED | 当前AcceptanceTest、打包CLI A–D及E安全负例，来源/状态/事件断言 |
| STORE-01 | 01-02 | SATISFIED | PG16.15/vector0.8.7、Flyway/Hikari/JDBC、独立空库迁移和重复启动；CLI隔离 |
| STORE-02 | 01-02 | SATISFIED（M0切片） | conversation/message/QUEUED run/首事件PG原子保存及读回；执行中工具/WAITING/结果持久化属后续 |
| STORE-05 | 01-02 | SATISFIED | 参数化SQL、复合外键、请求键/消息/event序号唯一及归属/状态索引；真实PG并发/约束/回滚负例。该需求是数据库约束，不误称为完整执行恢复 |
| TRUST-06 | 01-01 / 阶段合同 | SATISFIED（本阶段） | 保留旧私有模型配置兼容，PG环境注入，严格HTTP边界及安全错误；上传隔离/embedding/共享认证仍为完整需求部分实现，未升级为全部完成 |
| WEB-06 | 01-02 | SATISFIED（本阶段） | conversations/runs六路由与旧HTTP16项兼容；files/assets/Agent执行/SSE仍后续，不虚报完整产品API |

7个阶段需求均有归属与证据，无遗失需求。后续完整能力限制明确，与ROADMAP本阶段M0交付边界一致。

## Behavioral Evidence / Spot-Checks

本验证代理直接读取了实现、当前Surefire XML、CLI结果、打包脚本真实步骤及最新运行摘要；没有以SUMMARY的通过文字代替证据，没有重跑Maven或启动额外服务。

| 检查 | 核验结果 | 执行归属 |
|---|---|---|
| 当前30个Surefire suite XML汇总 | 154 tests / 0 failures / 0 errors / 0 skipped；PG8、旧HTTP16、Acceptance5、Runtime1均绿 | 主代理实际执行phase1-policy-package；验证代理读取XML独立汇总 |
| CLI帮助 | 显式仓库JDK21运行当前JAR --help，退出0，显示参数/SIMULATED | 验证代理实际执行 |
| Runtime输出 | SHA256=830d11a9aa9a24cd002ca67e881a90c3928361d45ed8ff9752772d399c773aeb | 验证代理读取当前文件计算，与固定值相同 |
| CLI A–D | A/B对应规则，C冲突、D缺证，步数7/7/7/6，事件34/34/35/29 | 主代理实际脚本；验证代理读取当前target/cli-verification.json |
| 打包双进程PG | 会话a16084b9-4d82-43ad-804e-0242bc64e934、run402452c7-f871-4325-bbbd-0a4d31910896，同原文/同run QUEUED；无DB CLI RESOLVED；凭据扫描通过 | 主代理实际phase1-policy-packaged-check；验证代理核脚本停止/重启/相等断言及摘要 |
| 独立Review与安全 | CR-01先真实复现202、修复后六类非法JSON400且无写入，独立复审clean；STRIDE6/6 CLOSED、open0 | 两独立审计代理结果及当前代码/测试对照 |

本阶段未声明probe-*.sh，无遗漏声明探针。原生coverage分类4自动/1人工和5个分类器保护探针只证明验收分类机制，未混入JUnit154项或替代业务测试。

### Test Quality Audit

| Test File | Linked Req | Active | Skipped | Circular | Assertion Level | Verdict |
|---|---|---:|---:|---|---|---|
| AcceptanceTest | BASE-02 | 5 | 0 | 无 | Value/Behavioral：规则、证据、结果及安全负例 | PASS |
| RuntimeComparisonTest | BASE-01 | 1（6样例×2候选） | 0 | 无；固定输入/预期事件，非由本轮输出反写期望 | Behavioral：重建、累计步数、问题关联、读取次数与失败轨迹 | PASS |
| PersistentConversationTest | STORE-01/02/05、TRUST-06、WEB-06 | 8 | 0 | 无 | Behavioral：HTTP→PG→重开、归属/并发/回滚/非法JSON无写入 | PASS |
| ServerHttpTest | BASE-02/WEB-06 | 16 | 0 | 无 | Value/Behavioral：旧接口状态与内容 | PASS |

未发现需求仅由禁用/跳过测试支撑、循环生成期望或只断言存在/200的问题。M0明确模拟数据的原型不用于证明真实语义检索、真实模型质量或现场效果。

### Decision Coverage / Anti-Patterns

原生decision-coverage查询返回skip：No CONTEXT.md - nothing to check。该启发式跳过不影响人工核对CONTRACTS/ADR；未声称检查了不存在的CONTEXT决策。

本轮实现/关键测试未发现未跟踪的TBD/FIXME/XXX或禁用测试阻断标记；未发现支撑本阶段truth的空实现、孤立Bean或静态假返回。UI在本阶段没有新增/修改，无UI-SPEC，不将未做UI视觉审查列为M0技术缺陷，也不宣称UI已验收。

## Human Verification / Exit

无待确认项。01-UAT的技术项有本轮自动证据；第1项真实人工通过和最初JAR暂缺/恢复历史保留。第3项已由用户明确回复“上述产品边界与设计符合预期”，现6/6通过。

01-VALIDATION保留nyquist_compliant=false，仅表示5项自动任务与1项已完成的人工设计认可并非全部自动覆盖；该布尔值不等于尚有技术测试缺口，不能以伪造自动测试使其变true。正式出口应读取UAT、VERIFICATION、Review、安全及实际验证结果，而非仅凭某一布尔值关闭或阻塞阶段。

没有本阶段待修复gap。外部真实LLM、真实embedding/RAG、通用Agent/WAITING、Vue/SSE、跨进程执行恢复、共享部署真实认证及现场设备效果均未在此阶段验证，保持既定后续范围；不影响本阶段已明确的存储/契约目标。

covered_files与covered_digest由原生`query verification.fingerprint`生成，包含两PLAN/SUMMARY、实施/测试/config及当前策略和审计证据；不包含会继续记录的UAT、忽略运行输出或报告自身。任何覆盖输入变更后须刷新指纹，不能沿用本摘要冒充新代码已验证。

_Verifier：独立gsd-verifier；本报告未修改业务代码、STATE/ROADMAP，未提交Git。_
