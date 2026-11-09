# Project: DeviceTruth V1.0

## What This Is

把现有可回归的受限诊断内核，演进为面向汽车及工厂设备的通用检修 Agent。接收自由文本、按需检索知识与结构化设备记录、追问用户、输出带来源与风险边界的结果，并保存可恢复的调查历史。

## Core Value

没有预定义故障码也能开始可信调查；每个关键结论能追溯资料来源、资产和业务时点，资料不足时允许解释不确定性或请求补充。

## Current State

- Java 主源码 52 个文件、测试源码 28 个文件；Java 21、Spring Boot 3.5.16、AgentScope Java 2.0.4、Maven 单模块。
- CLI 和独立 HTTP Main 已有；4 个汽车 JSON fixture、2 条模拟规则；原生 HTML 演示页。
- 前轮规划记录的 JDK 21 离线测试为 145/0/0/0；本轮修订未重跑业务测试。当前无 PostgreSQL、RAG、标准 Skill、Vue、暂停恢复实现。
- 能力矩阵：已实现 4、部分实现 28、未实现 25、与设计冲突 6。这是需求条目分布，不是完成率。
- V1.0 开发进度为 0/13 个计划；本轮仅规划，待用户审查。

## Source of Truth

1. 本次用户指令：按 [GSD 规划审查报告](../docs/DeviceTruth_GSD_规划审查报告.md) 针对性修订；仅改规划，保持五阶段和 63 项需求，最终交修订摘要审查，不进入实现。
2. [V1.0-Draft.1 PostgreSQL 产品设计](../docs/DeviceTruth_V1.0_通用设备检修Agent_完整产品设计方案_PostgreSQL.md)，SHA256 `C80A4C9292787B181DDB2406CAE4746B83F602993B22EAAA8C2C42AF5D698120`。
3. 本规划为从设计与源码推导的**待审开发方案**。实现现状以源码及实际测试为准；规划建议不冒称源码事实。
4. V0.1、V0.2 spec/task/state、CURRENT_PROJECT_STATUS 仅作历史与 legacy 行为资料。它们的“唯一基线”“先补 Hypothesis”“UI 后置”等旧阶段约束不覆盖新版主路径。
5. `AGENTS.md` 继续规定编码风格、只读设备边界、测试真实性与中文链路解释。密钥配置文档漂移见冲突报告，本轮不修改配置。

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
| P-01 | 倾向演进现有 TaskOrchestrator，禁止先长期并建框架栈 | 待 01-01 同链路小原型比较后 ADR；本轮未做原型 |
| P-02 | 倾向 Spring JDBC + Flyway，统一一种 DB 访问方案 | 源于新版 §8；具体依赖与 SQL 在开发前核对官方资料 |
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

当前 `REVIEW_PENDING`。审查通过并得到明确实施指令后，先细化/核验 Phase 1，再按计划逐项执行、验证、独立 Review、更新 STATE。未执行不得生成伪 SUMMARY 或勾完成；历史在线模型通过不等于新产品通过。

