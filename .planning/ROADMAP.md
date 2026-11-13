# Roadmap: DeviceTruth V1.0

> **2026-11-12 用户决定带遗留项关闭 Phase 2：** 正文安全修复与反例回归、PDF/OCR/页码能力以及浏览器端到端验收转后续，纳入Phase 5质量交付前完成；未验证的内容不标测试通过，详见02-ACCEPTANCE与02-03-SUMMARY。

> 以下2026-11-12较早的待审、未关闭及缺密钥状态为历史记录，以上述关闭决定和实际验收记录为准。

> 2026-11-12 用户认可02-03检索与多轮修复并接受 Review；正文安全延后处理，必须保留危险建议、无现场依据确诊、追问内容风险及误拒负例并在后续质量阶段修复和验证，不能记为安全通过。PDF及浏览器验收仍未完成。

> 2026-11-12 真实供应商测试：单轮引用链路实测成功但正文安全 NOT_RUN；真实 embedding 无关查询非空、多轮未追问导致对应验收 FAIL。02-03 保持未完成，详见其 SUMMARY。

> 2026-11-12：用户接受02-03 Review；无资产正文安全和真实供应商验收仍未完成，02-03未计入完成。详见[SUMMARY](phases/02-open-rag/02-03-SUMMARY.md)。

> **Phase1已关闭；02-02已提交，02-03待审查。** 共5个阶段、13个计划、37项任务；当前完成4个计划/12项任务、1个阶段。02-03按用户决定以Markdown上传替代PDF，开发与自测已结束；真实供应商缺key、PDF与浏览器NOT_RUN，不能视为计划完成。02-02提交为08d8ea0，02-03未commit/push。已接受的问题见[02-02复审](phases/02-open-rag/02-02-REVIEW.md)。
> GSD phase 从 1 编号，产品 milestone 从 M0 命名，避免与旧 V0.2 的 M0–M3 混用。
> 这是 brownfield 演进，复用现有单体/网关/模型/证据/测试；阶段成功以可演示行为与证据判断。

## Phases

- [x] **Phase 1: 基线与通用契约（M0）** — 在保留旧回归的前提下，明确新契约和唯一主循环，再让自由消息/排队任务进入 PostgreSQL。 (completed 2026-11-10)
- [x] **Phase 2: 自由对话与知识检索（M1）** — 从 Vue 提交无故障码问题，由 Agent 检索知识、追问并在同一 run 接收补答后继续产出引用结果。 (completed 2026-11-12)
- [ ] **Phase 3: 设备事实与持久连续调查（M2）** — 把同一任务的补证、资产时序查询、刷新/重启恢复、预算和取消贯通为完整调查。
- [ ] **Phase 4: 标准 Skill 与双域验证（M3）** — 通过标准指引与领域数据，在不复制 Runtime 的情况下处理汽车充电与工业 SC-0245-B。
- [ ] **Phase 5: 完整工作台、质量与交付（M4）** — 在正确持久事件之上实现实时进度、可读报告与完整评测，再完成可恢复部署。

### Phase 1: 基线与通用契约

**Milestone:** M0
**Goal:** 在保留旧回归的前提下，明确新契约和唯一主循环，再让自由消息/排队任务进入 PostgreSQL。
**Depends on:** 无；实施前须用户审查通过本规划
**Requirements:** BASE-01, BASE-02, STORE-01, STORE-02, STORE-05, TRUST-06, WEB-06
**Success Criteria:**

1. 旧 CLI A/B/C/D 与 E 安全负例在当前基线可复现。
2. 通用输入、引用、风险、状态和权限契约明确；Runtime 比较保留测试代码/固定输入/复现命令/对比表，清理 target 后仍可重跑，仅选一套主循环；不冒称 PG 恢复已验证。
3. 新 API 可保存/读回会话、消息与 QUEUED run，空库可迁移、CLI 无数据库仍能运行。M0 仅持久化契约演示，没有 Agent worker，不宣称可执行产品。

**Plans:** 2/2 plans complete（独立Review、自动验收及集中产品确认通过；阶段已关闭）

- [x] [01-01-PLAN.md](phases/01-baseline-contracts/01-01-PLAN.md) — 核对继承基线、通用契约与 Runtime 取舍；依赖 无；wave 1；[完成证据](phases/01-baseline-contracts/01-01-SUMMARY.md)
- [x] [01-02-PLAN.md](phases/01-baseline-contracts/01-02-PLAN.md) — PG持久会话与排队任务切片；依赖01-01；wave 2；[完成证据](phases/01-baseline-contracts/01-02-SUMMARY.md)

**用户验收前置（2026-11-10）：** [COVERAGE](phases/01-baseline-contracts/COVERAGE.md)已补齐并通过覆盖／链接／源码证据检查，本次针对性回归51/0/0/0；逐项用户验收尚未进行。该检查不代替独立Review，不更改阶段完成状态。

**本轮验收进度（2026-11-10）：** 独立[Review](phases/01-baseline-contracts/01-REVIEW.md)初审1个BLOCKER，真实HTTP/PG复现后修复并独立复审clean。全量clean test/package均154/0/0/0，打包CLI与新JAR跨进程原文/QUEUED读回通过，见[验证证据](phases/01-baseline-contracts/01-VALIDATION.md)。[01-UAT](phases/01-baseline-contracts/01-UAT.md)为逐项用户确认记录，尚未完成，不关闭阶段。

**验收策略调整（2026-11-10）：** 按[ACCEPTANCE](ACCEPTANCE.md)及GSD原生coverage将技术项转为代理自动执行/断言并留证；用户已通过的CLI B保留。策略调整后package再次154/0/0/0、打包PG重启再次通过；UAT仅保留一次产品/设计集中确认，见[简洁验收报告](phases/01-baseline-contracts/01-ACCEPTANCE.md)。未确认前不关闭阶段，02-01不提前执行。

**最终出口 / 边界：** 最新154项/打包/CLI/PG重启全部通过；独立Review clean、安全6/6关闭、VERIFICATION passed（6/6），用户集中确认已收到，原生phase uat-passed --require-verification通过并关闭Phase1。没有Agent worker、真实RAG或WAITING执行恢复。下一步按已有授权提交Git并仅执行02-01；上方逐项提示等为此前验收过程历史。

### Phase 2: 自由对话与知识检索

**Milestone:** M1
**Goal:** 从 Vue 提交无故障码问题，由 Agent 检索知识、追问并在同一 run 接收补答后继续产出引用结果。
**Depends on:** Phase 1
**Requirements:** ENTRY-01, ENTRY-02, ENTRY-03, ENTRY-04, ENTRY-05, AGENT-01, AGENT-02, AGENT-03, AGENT-05, AGENT-08, STORE-03, KNOW-01, KNOW-02, KNOW-03, KNOW-04, KNOW-05, KNOW-06, KNOW-07, KNOW-08, TOOL-01, TOOL-02, TRUST-01, TRUST-05, WEB-01, WEB-04, EVAL-02
**Success Criteria:**

1. 知识 MD/TXT/PDF 导入、版本/章节/页码引用和权限过滤可闭环。
2. 无资产、无故障码的充电问题可进入 Agent；低风险回答不依赖旧全量调查 Guard。
3. Vue 以 runId/pendingQuestionId/questionVersion 补答；WAITING 释放线程，持久读回必要消息/引用后同 run 再检索或作答，至少两轮互动可重跑（E2E-07-basic）。M0 QUEUED 的正常调度交接与模型/队列失败状态可验证。
4. 冻结 12 个中文/部件号/跨权/错引小样本；正向6例 Hit@5 至少5/6、部件号2/2命中，跨权泄漏和伪造引用接受均0；向量/精确/混合逐例比较。真实 embedding→PG→召回、真实 LLM→注册工具→引用结果、基本补证及 UI 分别留证；缺环境单列，不用 fake 替代。

**Plans:** 3/3 plans complete（用户决定带遗留项关闭；正文安全、PDF及浏览器验收转后续）

- [x] 02-01-PLAN.md — [知识导入、权限检索与引用](phases/02-open-rag/02-01-PLAN.md)；2026-11-11复审通过；依赖01-02，wave 1
- [x] 02-02-PLAN.md — [自由目标Agent、同run补证与Vue](phases/02-open-rag/02-02-PLAN.md)；按用户决定带[已知问题](phases/02-open-rag/02-02-REVIEW.md)通过；依赖01-01/02-01，wave 2
- [x] 02-03-PLAN.md — [Markdown上传、混合检索及真实供应商验收](phases/02-open-rag/02-03-PLAN.md)；用户接受现有范围；真实供应商链路已实测，正文安全、PDF及浏览器验收保留；依赖02-01/02-02，wave 3

**边界 / 风险：** M1 交付单进程正常路径的最小补证，不交付崩溃恢复、租约/CAS、完整幂等/并发；后者仍属 M2。02-03仅完成Markdown与确定性混合检索合同，PDF未实现，真实embedding/模型缺key，均保留未验收项；M4完整质量与规模评测不缩减。

### Phase 3: 设备事实与持久连续调查

**Milestone:** M2
**Goal:** 把同一任务的补证、资产时序查询、刷新/重启恢复、预算和取消贯通为完整调查。
**Depends on:** Phase 2
**Requirements:** AGENT-04, AGENT-06, AGENT-07, DATA-01, DATA-02, DATA-03, STORE-04, TOOL-03, TOOL-04, TRUST-02, TRUST-04, WEB-02, OPS-01, OPS-03
**Success Criteria:**

1. 查询具体资产配置/维修有来源和时点，缺现场核验不确诊；权限覆盖所有资源。
2. 在 M1 补证协议上强化跨进程检查点、租约/CAS、证据重验；重启保留等待和已提交上下文，崩溃运行可恢复或明确失败（E2E-07-restart 与 E2E-08）。
3. 有界调度、请求幂等、取消/总超时和用量审计可验证；Vue 能读历史并继续。

**Plans:** 3/3 plans executed（均待独立 Review 与阶段验收）
- [x] 03-01-PLAN.md
- [x] 03-02-PLAN.md
- [x] 03-03-PLAN.md

- [x] [03-01-PLAN.md](phases/03-durable-investigation/03-01-PLAN.md) — 带来源与时点的设备记录及全链路授权；依赖 02-03；wave 1；[完成证据](phases/03-durable-investigation/03-01-SUMMARY.md)
- [x] [03-02-PLAN.md](phases/03-durable-investigation/03-02-PLAN.md) — 补证可靠性强化、检查点恢复与历史会话闭环；依赖 03-01, 02-03；wave 2；[完成证据](phases/03-durable-investigation/03-02-SUMMARY.md)
- [x] [03-03-PLAN.md](phases/03-durable-investigation/03-03-PLAN.md) — 幂等、取消、总预算和故障可观测；依赖 03-02；wave 3；[完成证据](phases/03-durable-investigation/03-03-SUMMARY.md)

**边界 / 风险：** 事务/异步调度边界、重复恢复、迟到响应及用户声明可信级别必须测试。

### Phase 4: 标准 Skill 与双域验证

**Milestone:** M3
**Goal:** 通过标准指引与领域数据，在不复制 Runtime 的情况下处理汽车充电与工业 SC-0245-B。
**Depends on:** Phase 3
**Requirements:** DATA-04, DATA-05, DATA-06, SKILL-01, SKILL-02, SKILL-03, SKILL-04
**Success Criteria:**

1. 发现/读取两个领域 SKILL.md，并记录版本和服务器工具绑定。
2. Skill/上传/知识里的恶意指令不能扩权，原文不等于系统指令。
3. 同一 Runtime 处理双域，工厂 BOM/改造/旧模板/现场来源区分明确，真实模型双域验证另列。

**Plans:** 2 plans（均已编写待审任务书；未执行）

- [ ] [04-01-PLAN.md](phases/04-skills-dual-domain/04-01-PLAN.md) — 标准 Skill 发现、激活并接入唯一 Runtime/工具权限；依赖 03-03；wave 1
- [ ] [04-02-PLAN.md](phases/04-skills-dual-domain/04-02-PLAN.md) — 汽车与工厂配置冲突双域产品验收；依赖 04-01, 03-03；wave 2

**边界 / 风险：** 假工业域测试不算完整产品；来源等级和模板适用性不能偷换成当前现场事实。

### Phase 5: 完整工作台、质量与交付

**Milestone:** M4
**Goal:** 在正确持久事件之上实现实时进度、可读报告与完整评测，再完成可恢复部署。
**Depends on:** Phase 4
**Requirements:** STORE-06, TRUST-03, WEB-03, WEB-05, OPS-02, OPS-04, EVAL-01, EVAL-03, EVAL-04
**Success Criteria:**

1. SSE白名单事件可实时展示/断线续传，历史以PG为准。
2. 报告有事实/可能性/建议/人工核验与引用；14个E2E逐例留证并有检索/效率/质量指标。
3. Compose启动产品，数据库/文件持久；备份恢复与迁移失败处理有实测，旧CLI可独立运行。

**Plans:** 3 plans（均已编写待审任务书；未执行）

- [ ] [05-01-PLAN.md](phases/05-workbench-quality/05-01-PLAN.md) — 持久事件驱动的实时 SSE 与安全历史；依赖 04-02；wave 1
- [ ] [05-02-PLAN.md](phases/05-workbench-quality/05-02-PLAN.md) — 完整工作台、评测矩阵与检索质量验证；收口前处理延期正文安全、PDF和浏览器E2E；依赖 05-01；wave 2
- [ ] [05-03-PLAN.md](phases/05-workbench-quality/05-03-PLAN.md) — 可启动交付、备份恢复与运维说明；依赖 05-02；wave 3

**边界 / 风险：** 在线评测/多人授权/数据量性能不能用离线成功替代；不为MVP引入额外平台。

## 开发顺序与阶段出口

同阶段按依赖与 wave 串行推进；parallelization 当前关闭。跨阶段依赖是交付能力依赖，不要求机械读取所有前序 SUMMARY。Phase 2 的前提含 Phase 1 全部出口，不能只有数据库启动；Phase 3→4→5同理。

机器依赖落实阶段出口：02-01→01-02，03-01→02-03，04-01→03-03，05-01→04-02；各阶段内部链串行到最后一个计划。单独启动 PLAN 仍须确认其前置实际完成及阶段出口证据，存在 PLAN 不代表完成。所有跨阶段边均向前，无循环。

每一阶段：任务实现 → 针对性验证 → 离线/CLI回归及本阶段PG/UI/在线项 → 独立Review → 更新STATE/ROADMAP。在线受环境限制时单列，不能把缺失在线验收改写成通过或强行标产品完成。现有145项是保护旧行为的起点，V1.0不得靠删旧测试过关。

从M0即考虑授权/数据归属/事务，从M1第一次检索即做过滤；M4是补全流式与质量评测，不是最后才补基础安全。Vue从M1存在；标准Skill在双域验证前实现，不先建Skill市场。

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|---|---|---|---|---|
| 1. 基线与通用契约 | M0 | 2/2 | Complete    | 2026-11-10 |
| 2. 自由对话与知识检索 | M1 | 3/3 | Complete    | 2026-11-12 |
| 3. 设备事实与持久连续调查 | M2 | 3/3 | Executed（待独立Review与阶段验收） | - |
| 4. 标准 Skill 与双域验证 | M3 | 0/2 | Not started（待审） | - |
| 5. 完整工作台、质量与交付 | M4 | 0/3 | Not started（待审） | - |

完成计划8/13，完成阶段2/5。03-01 已复审通过，03-02/03-03 已执行并提交，Phase 03 三个计划全部执行完毕；待独立 Review 与阶段验收（真实模型/embedding、浏览器演示未验证）。正文安全、PDF及浏览器验收未通过，仍需后续处理。

2026-11-13: 03-03 executed（幂等/取消/总时限/错误观测；329/0/0/1，verify_cli 与 investigation 套件 PASS）；8/13 plans, Phase 03 3/3 executed. Phase 03 awaits independent review & phase acceptance. Next: review phase 03, then 04-01（未授权，待审）.
