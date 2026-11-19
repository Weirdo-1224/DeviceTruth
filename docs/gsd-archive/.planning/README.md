# DeviceTruth V1.0 规划审查入口

> **最新状态：Phase1已完成。** UAT6/6、独立Review clean、安全核验及VERIFICATION6/6 passed，用户集中确认已保存，原生阶段出口已通过并关闭M0。先提交当前Git，随后仅执行02-01；不执行02-02/02-03。下方待确认/未关闭语句保留为验收历史。

> **验收策略已更新（2026-11-10）：** 见[项目验收策略](ACCEPTANCE.md)。技术测试和明确预期场景由代理实际执行，用GSD1.20.1原生SUMMARY coverage自动分类并写UAT；每Phase尽量只集中一次产品/业务/设计确认，保留既有结果与失败修复历史，不修改全局GSD。Phase1最新简洁结果见[01-ACCEPTANCE](phases/01-baseline-contracts/01-ACCEPTANCE.md)，用户CLI B确认已保留，剩余人工事项合为一项。

> 分析起始：2026-11-08；最新验证：2026-11-10（Asia/Shanghai）｜状态：**01-01/01-02已完成，独立Review通过，逐项用户验收进行中；出口后提交Git并仅执行02-01已获授权**
> 当前HEAD：`dbb8b2e567db68a1b8d78a07bbd3b7a03c245bb0`。01-02代码与状态保留在工作树，未提交Git；按用户指定复用interview-postgres，在独立devicetruth库保存会话/消息/QUEUED。全量153项、打包、CLI与新JAR跨进程读取通过，见[VALIDATION](VALIDATION.md)。

最新交付见[01-02-SUMMARY](phases/01-baseline-contracts/01-02-SUMMARY.md)，原型局限仍见[RUNTIME-SPIKE](phases/01-baseline-contracts/RUNTIME-SPIKE.md)。M0计划2/2完成，独立Review/阶段出口仍待核验；尚无Agent worker或RAG。下方初始规划审查记录保留为历史背景。

2026-11-10本轮：[独立Review及复审](phases/01-baseline-contracts/01-REVIEW.md)最终clean，HTTP JSON输入缺陷已修复；[阶段验证证据](phases/01-baseline-contracts/01-VALIDATION.md)记录154项全量回归、打包、CLI和真实PG进程重启通过。[用户验收](phases/01-baseline-contracts/01-UAT.md)尚待逐项确认；Git未提交、02-01未执行，不运行02-02/02-03。本段为最新状态，下方旧授权边界只作历史背景。

2026-11-10已补齐并检查[Phase 01 API／SDK覆盖表](phases/01-baseline-contracts/COVERAGE.md)：区分已接入、仅原型、后续接入及明确不采用，关联实现与测试；本次针对性回归51/0/0/0。继续逐项用户验收前先读该表；文档检查不替代独立Review、用户确认或后续实施授权。

本次针对性修订摘要见 [规划自检与修订对照](PLAN-REVIEW.md)。建议先读 [差距审查](GAP-ANALYSIS.md)，再看 [路线图](ROADMAP.md) 和 [逐项需求](REQUIREMENTS.md)。详细开发任务从 [阶段阅读目录](phases/README.md) 进入。当前进度与交接分别在 [STATE.md](STATE.md)、[NEXT_SESSION.md](NEXT_SESSION.md)。

## 文档导航

| 文件 | 用途 |
|---|---|
| [PROJECT.md](PROJECT.md) | 目标、范围、决策优先级与复用原则 |
| [REQUIREMENTS.md](REQUIREMENTS.md) | 63 项能力的四态判断、源码依据、验收目标与阶段/任务映射 |
| [GAP-ANALYSIS.md](GAP-ANALYSIS.md) | 当前真实链路、模块保留/改造/新增、数据库迁移影响及 E2E 对照 |
| [ROADMAP.md](ROADMAP.md) | 5 个阶段，对应新版 M0–M4，共 13 个待执行计划 |
| [INGEST-CONFLICTS.md](INGEST-CONFLICTS.md) | 新旧设计的优先级归并及待实证选择 |
| [VALIDATION.md](VALIDATION.md) | 本轮实测结果、未来测试分层与未验证项 |
| [Phase 01 COVERAGE.md](phases/01-baseline-contracts/COVERAGE.md) | M0 API／SDK接入边界、不采用理由、实现及测试证据，用户验收前置 |
| [PLAN-REVIEW.md](PLAN-REVIEW.md) | 本轮规划自检；不等同独立代码 Review 或用户批准 |
| [codebase/](codebase/) | 技术栈、集成、架构、结构、规范、测试、关注点七份地图 |
| [intel/SYNTHESIS.md](intel/SYNTHESIS.md) | GSD 文档归并摘要及输入来源 |
| [evidence/baseline.json](evidence/baseline.json) | 不含凭据的源码/设计摘要与 145 项测试汇总 |

## 使用的 GSD 能力与执行边界

已读取已安装的 `gsd-ingest-docs`、`gsd-map-codebase`、`gsd-plan-phase` 的技能说明，读取 ingest/map 工作流及 GSD state/config/roadmap/plan 模板；实际调用 GSD Core `runtime-identity` 和 `init ingest-docs`，确认版本 **1.20.1**、首次初始化、仓库根目录正确。

本轮按用户已指定的需求文档、优先级、路线图与持久状态目标，采用这些能力的**当前会话内分析与规划适配**：显式来源清单 → 文档归并/冲突 → 七类地图 → 需求追踪 → 阶段与任务 → 目标反向检查。未声称完整执行 ingest 自动审批/提交工作流，未启动执行器、未派发子代理、未通过独立 planner/checker 审查。用户要求“先交文档审查”优先于技能的自动提交或后续执行建议。

`config.json`保持interactive、关闭auto_advance和并行执行；**配置不是权限系统**。01-01/01-02已获授权并完成，STATE/NEXT_SESSION记录后续计划未授权的边界，不自动推进。

