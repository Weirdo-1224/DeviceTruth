# DeviceTruth V1.0 规划审查入口

> 分析起始：2026-11-08；交付：2026-11-09（Asia/Shanghai）｜状态：**REVIEW_PENDING，仅规划，未获实施授权**
> 代码基线：`b7befa08f702675d05dcc9560cf7eb87e4b778c2`。本轮不修改业务源码、测试、依赖或数据库。

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
| [PLAN-REVIEW.md](PLAN-REVIEW.md) | 本轮规划自检；不等同独立代码 Review 或用户批准 |
| [codebase/](codebase/) | 技术栈、集成、架构、结构、规范、测试、关注点七份地图 |
| [intel/SYNTHESIS.md](intel/SYNTHESIS.md) | GSD 文档归并摘要及输入来源 |
| [evidence/baseline.json](evidence/baseline.json) | 不含凭据的源码/设计摘要与 145 项测试汇总 |

## 使用的 GSD 能力与执行边界

已读取已安装的 `gsd-ingest-docs`、`gsd-map-codebase`、`gsd-plan-phase` 的技能说明，读取 ingest/map 工作流及 GSD state/config/roadmap/plan 模板；实际调用 GSD Core `runtime-identity` 和 `init ingest-docs`，确认版本 **1.20.1**、首次初始化、仓库根目录正确。

本轮按用户已指定的需求文档、优先级、路线图与持久状态目标，采用这些能力的**当前会话内分析与规划适配**：显式来源清单 → 文档归并/冲突 → 七类地图 → 需求追踪 → 阶段与任务 → 目标反向检查。未声称完整执行 ingest 自动审批/提交工作流，未启动执行器、未派发子代理、未通过独立 planner/checker 审查。用户要求“先交文档审查”优先于技能的自动提交或后续执行建议。

`config.json` 保持 interactive、关闭 auto_advance 和并行执行；**配置不是权限系统**。真正的本轮边界由用户指令和 STATE/NEXT_SESSION 共同记录：审查前只可继续分析、修订规划。

