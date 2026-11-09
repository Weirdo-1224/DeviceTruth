# 规划修订摘要与自检记录

> 日期：2026-11-09（Asia/Shanghai）。按 docs/DeviceTruth_GSD_规划审查报告.md 修订，仅修改 .planning 内规划及其校验证据。保留五阶段、63 项需求、13 份 PLAN、37 个任务；全部未开始。
> 当前仍为 planning / review_pending / execution_authorized=false。本文件是修订说明及自检，不是开发 SUMMARY，也不是独立 Review PASS。

## 审查意见与修改位置

| 审查项 | 修订内容 | 主要位置与未来验证 |
|---|---|---|
| P0-1 最小多轮前移 | AGENT-03 主交付由03-02迁至02-02；问题/消息/引用持久化后，以runId/pendingQuestionId/questionVersion补答，同run继续检索/回答；等待释放线程 | 01-01契约、02-02-T2/T3；GeneralAgentFlowTest、GeneralAgentApiTest、前端api.test；02-03真实模型多轮验收 |
| M1/M2 分界 | M1仅单进程正常补证，M2复用协议补稳定检查点、崩溃恢复、租约/CAS、证据重验及完整幂等/并发 | 03-02/03-03；E2E-07-basic与E2E-07-restart分层，原E2E编号及14例全集不变 |
| P0-2 知识工具接入 | 02-01实现KnowledgeTools强类型接口/服务端Bean；02-02在CapabilityGateway注册、目录进入Reasoner、Runtime实际调用 | ServerConfiguration、CapabilityGateway、ToolContext、GeneralAgentService均列清；从Spring/API到真实PG工具的集成测试 |
| P0-2 Skill接入 | Registry/Loader显式装配进GeneralAgentService，发现→按需激活→上下文→唯一Runtime；工具目录与执行均受服务器/Skill权限交集约束 | 04-01新增SkillRuntimeIntegrationTest及服务/运行/网关/装配/持久化文件；04-02补GeneralAgentService/Loader和观测注册，双域同API验收 |
| P1-1 原型可复现 | 保留测试源码、固定cases、环境/命令/输入hash、两候选对比表与脱敏事件；target只存输出 | 01-01-T3；RuntimeComparisonTest、固定测试资源、RUNTIME-SPIKE、ADR；清理target后可重跑，不宣称PG恢复已验证 |
| P1-2/P1-3 早期效果 | M1真实embedding适配/维度/PG向量读回/召回，与真实LLM调用链独立留证；冻结12小样本后再调参 | 02-01真实适配，02-03固定语料/EmbeddingIntegrationTest/verify_general_agent.py；M4仍保留正式14E2E和规模/效率评测 |
| P1-4 前端构建 | 明列lockfile、index/main/env、tsconfig、Vite配置、构建和测试依赖及API合同测试 | 02-02-T3；先生成提交lockfile再npm ci/test/build；本轮不生成这些业务产物 |
| P1-5 执行依赖 | 03-01显式依赖02-03，04-01显式依赖03-03，其余阶段入口也指向前阶段末计划 | ROADMAP与全部相关depends_on一致；验证存在性/DAG/同阶段wave/阶段出口 |
| P1-6 M0队列 | M0仅持久化契约演示；M1交接正常QUEUED调度/失败；M2负责崩溃遗留队列 | 01-02/02-02/03-02；不能把QUEUED等同可执行产品 |
| P1-7 身份验收 | 双身份授权合同与真实认证分开；本地Demo不证明多人认证 | 03-01服务层负例；05-03对外多人模式仅在身份适配与HTTP验证后验收，否则NOT_RUN |
| P2 清单精简 | 按每个任务实际责任缩小files/read_first；用迁移、核心服务、测试代替pom作为代表产物 | 13份PLAN检查任务files并集=files_modified，key_links起点有对应修改文件；明确现有方法与拟新增类 |

## 调整后的 M0 与 M1 实施路径（尚未执行）

M0：01-01先复核旧CLI/安全基线，冻结通用输入、动作、引用、身份和最小补答契约；运行保留的两候选比较测试后选唯一Runtime。01-02再引入产品profile、PG/Flyway、会话/原始消息/run/event事务与显式Spring装配。此时只演示保存和读回QUEUED，不承诺Agent执行。

M1：02-01先把MD/TXT→切片→embedding→PG→授权知识接口打通；02-02注册知识工具、让GeneralAgentService调度唯一循环，并接入可构建Vue。用户问“充电中断无故障码”，Agent问“交流还是直流”，用户答“直流快充”，服务将回复关联原问题和同run，读回消息/必要引用后再检索并给出引用结果。02-03补PDF/附件接入与混合检索，冻结小样本、运行真实embedding和真实LLM/多轮验证。M2随后处理跨进程恢复与竞争可靠性。

12例由中文语义4、精确部件号2、跨owner/project2、空检索1、旧/冲突版本1、错引2组成。正向6例Hit@5至少5/6、精确部件号2/2命中，跨权泄漏/错引接受均为0；逐例、逐通道保留结果。阈值仅用于最小样本验收，不能代替M4整体质量；变更样本/阈值须保留版本及理由。

## 本轮实际校验与边界

实际执行 `python .planning/validate_planning.py`，调用已安装 @opengsd/gsd-core 1.20.1 的13次 `verify plan-structure`、`validate consistency`、`roadmap analyze`、`state-snapshot`。需求唯一主映射、阶段/PLAN一致、依赖无环、wave、阶段出口、任务文件并集、key_links起点、文档链接与UTF-8、未授权/0进度检查均通过。完整命令/退出码在 evidence/planning-validation.json。

未执行任何开发PLAN、Runtime原型、业务测试/打包、依赖安装、PG迁移/容器、前端构建、真实LLM或embedding请求。前轮145项离线基线仅作历史实测，本轮不重新宣称通过。结构检查不替代语义独立Review和实际效果验收；待用户审查修订，不直接进入M0。
