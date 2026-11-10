# Project: DeviceTruth V1.0

## What This Is

把现有可回归的受限诊断内核，演进为面向汽车及工厂设备的通用检修 Agent。接收自由文本、按需检索知识与结构化设备记录、追问用户、输出带来源与风险边界的结果，并保存可恢复的调查历史。

## Core Value

没有预定义故障码也能开始可信调查；每个关键结论能追溯资料来源、资产和业务时点，资料不足时允许解释不确定性或请求补充。

## Current State

- Java主源码57个文件、测试源码30个文件；Java21、Spring Boot3.5.16、AgentScope2.0.4、Maven单模块，新增JDBC/Flyway/Hikari，未升级框架。
- CLI 和独立 HTTP Main 已有；4 个汽车 JSON fixture、2 条模拟规则；原生 HTML 演示页。
- 2026-11-10独立Review发现并修复HTTP JSON宽松转换；全量test/package均154/0/0/0，CLI A–D及E安全负例保留，独立复审clean。product HTTP已接真实PG会话/消息/QUEUED切片；无RAG、标准Skill、Vue或Agent暂停恢复实现。
- 能力矩阵原盘点为4/28/25/6；01-02新增STORE-01/02/05实现后为7/28/22/6（STORE-02限定M0切片，WEB-06仍部分）。这是需求条目分布，不是完成率。
- V1.0已完成01-01/01-02（2/13）及Phase1（1/5）；独立Review、安全和阶段验证通过，用户集中确认已收到，M0已原生关闭。下一步按授权提交Git并仅执行02-01；其余计划未执行，02-02/02-03明确不执行。

## Source of Truth

1. 用户最新指令（2026-11-10）：继续Phase 1逐项用户验收、独立Review及阶段验证，发现问题修复复验；出口全部满足后提交当前Git并直接执行02-01，暂不执行02-02/02-03。独立复审已clean，用户确认尚待完成；未提交Git、未执行02-01。数据库继续复用指定interview-postgres。01-01成果已收录于dbb8b2e，01-02及本轮修复保留在工作树。五阶段/63需求保持。
2. [V1.0-Draft.1 PostgreSQL 产品设计](../docs/DeviceTruth_V1.0_通用设备检修Agent_完整产品设计方案_PostgreSQL.md)，SHA256 `C80A4C9292787B181DDB2406CAE4746B83F602993B22EAAA8C2C42AF5D698120`。
3. 本规划为从设计与源码推导的**待审开发方案**。实现现状以源码及实际测试为准；规划建议不冒称源码事实。
4. V0.1、V0.2 spec/task/state、CURRENT_PROJECT_STATUS 仅作历史与 legacy 行为资料。它们的“唯一基线”“先补 Hypothesis”“UI 后置”等旧阶段约束不覆盖新版主路径。
5. `AGENTS.md` 继续规定编码风格、只读设备边界、测试真实性与中文链路解释。01-01 已冻结配置策略（CONTRACTS §8）：旧模型私有文件兼容保留，新 PG/embedding 仅服务端环境注入；没有读取私密值或修改配置实现。

## Scope

- M0–M4 全部纳入本轮路线图：通用入口、单 Agent、PG/pgvector、Flyway、知识导入与混合检索、设备时序数据、多轮恢复、标准 Skill、双域案例、早期 Vue、SSE、报告、评测、Compose。
- 先 PDF/MD/TXT，先模拟或授权上传资料；文件本地受控目录。
- 不纳入：任意脚本/SQL 执行、设备控制、多 Agent 产品编排、Kafka/Redis、微服务、知识图谱、独立向量库、完整工单/库存/IoT 平台。
- DOCX、OCR、语音/图像理解、真实 OBD/PLC/IoT 接入为后续范围；扫描件要明确不能解析，不伪造成功。

## Key Decisions

| 编号 | 决策 | 依据与状态 |
|---|---|---|
| D-01 | 自由目标入口，故障码/资产可选 | 新版已指定方向；实现尚缺 |
| D-02 | 产品仅一套单 Agent 主循环 | 新版锁定；旧结构化路径作兼容策略/适配 |
| D-03 | 同一 PostgreSQL 承载业务、运行与 pgvector | 新版锁定；版本/维度在 01-01/02-01 验证后钉定 |
| D-04 | 知识走 RAG，资产现状走时序结构化查询 | 新版锁定；引用不等于当前现场事实 |
| D-05 | SKILL.md 为领域指引，工具由服务器授权 | 新版锁定；不执行任意 Skill 脚本 |
| D-06 | 优先复用网关、模型连接、证据校验、事件白名单 | 新版锁定；必要时改造旧协议 |
| D-07 | 风险分级结束策略；设备控制默认不可执行 | 新版锁定；旧 Guard 保留为 legacy 策略 |
| D-08 | M1 接入 Vue，M4 再完善工作台 | 新版锁定；不是到最后才做前端 |
| P-01 | 选择演进现有 TaskOrchestrator，唯一产品调度循环 | 01-01 两候选六样例、两次 clean 重跑一致；见 [ADR](phases/01-baseline-contracts/ADR-runtime.md)；生产改造在 02-02 |
| P-02 | 采用Spring JDBC + Flyway + Hikari，统一DB访问方案 | 01-02真实PG验证；复用interview-postgres的PG16.15/pgvector0.8.7，CLI默认不装配DB |
| P-03 | M0 建 owner/project 上下文与约束，首次检索即做权限过滤 | 拆解建议；本地 Demo 可固定服务器身份，多人部署前接真实认证 |
| P-04 | M1 交付同一 run 最小两轮补证；M2 强化完整恢复与可靠性 | 02-02 主交付 AGENT-03；runId/pendingQuestionId/questionVersion 关联与持久消息/引用读取先行；03-02/03-03 复用协议补跨进程检查点、租约/CAS、幂等/并发 |
| P-05 | 保留 JSON fixture / CLI；新产品入口不走 Catalog 准入 | 新版 §4.1/§9；双路径共用组件，禁止互相套循环 |
| P-06 | Runtime 原型必须保留可复现证据 | 01-01 保留比较测试、固定输入、复现命令/环境、对比表和安全轨迹；target 仅存输出，ADR 不代表已验证 PG 恢复 |
| P-07 | M1 即验证最小真实 RAG 效果 | 02-03 冻结 12 个中文/部件号/权限/错引样本，真实 embedding→PG→召回与真实模型→工具→引用分别留证；M4 全 14 E2E/规模/效率评测不缩减 |

## 交付与集成边界（2026-11-09 修订）

- M0 的新 API 只演示持久会话、消息与 QUEUED run；不启动 Agent worker，不作为可执行产品交付。M1 接手正常队列调度及失败状态，M2 处理崩溃遗留队列。
- 02-01 提供 KnowledgeTools 强类型接口与 Spring Bean，02-02 才在 CapabilityGateway 注册并将真实目录交给唯一 Runtime。实际入口是现有 ServerConfiguration 的显式 Bean 装配，不能依赖仅扫描 api 包自动发现新服务。
- 04-01 经 GeneralAgentService→SkillContextLoader 激活已授权 Skill 并写入 Reasoner 上下文，目录/执行权限均取注册工具、服务器授权及 Skill 绑定交集；04-02 接入模拟观测并验证双域同一 API/Runtime。最终类名如被 01-01 ADR 调整，先同步 PLAN 文件/测试清单。
- 本地固定身份只证明服务层双身份授权合同；共享部署真实认证在 05-03 接入后才能验收。在线 LLM、真实 embedding、PG、离线合同与 UI 各自记录，缺环境不冒称通过。

## Constraints

不能让模型直写权威设备事实、决定自己的权限、自由生成 SQL 或绕过注册工具。数据库写入仅用于应用业务/审计，不意味着授权设备写操作。新回答允许用户可读的推理摘要与追问，但不得公开内部 Prompt、密钥或堆栈。

## Workflow

2026-11-10验收策略按[ACCEPTANCE](ACCEPTANCE.md)执行：技术项由代理实际运行并填写原生SUMMARY coverage及UAT source=automated；保留已有用户确认，只将产品/业务/设计或确实无法自动判断事项集中一次确认。保持GSD原记录、修复复验与阶段出口，不修改全局GSD，不用自动推进开关代替测试。当前Phase1第1项用户通过已保留，剩余技术项自动验证，产品边界/Runtime取舍合为一次集中确认。

用户已授权并完成01-01/01-02，最新交付见[01-02-SUMMARY](phases/01-baseline-contracts/01-02-SUMMARY.md)。其余计划仍需明确授权；M0独立Review待进行。后续按真实证据检查阶段出口，不以历史在线模型或本次持久化冒称完整Agent通过。

