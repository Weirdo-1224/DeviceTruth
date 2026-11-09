# 归并摘要

## 需求来源

唯一当前产品输入是 V1.0 PostgreSQL 设计，共 591 行、Draft.1、2026-11-08；显式 manifest 仅纳入这一份 PRD，优先级由用户指定。章节 §1–§13 及 14 个 E2E 已映射到 REQUIREMENTS / GAP / ROADMAP。§14 的建议被落实为本次差距审查和后续 M0–M4，而非一次性重写。

旧 V0.1 spec/task/state 已阅读作为核心契约与历史验收背景；V0.2 spec/task/state 的关键边界及 CURRENT_PROJECT_STATUS 用于定位现有应用链。历史文档中的“唯一/冻结”字样保持原文，不在本轮改写，也不覆盖新版。

## 当前代码事实

52 个主 Java 文件、28 个测试文件；Maven 单模块、两入口、5 个只读工具、4 个汽车 JSON 资产与 2 条规则。PG/pgvector、知识导入、标准 Skill、Vue、SSE、持久暂停恢复均未形成实现。原生 HTML 演示页已提交，不等同 Vue。

## 已指定方向

自由任务、单 Agent、PG 与 pgvector 同实例、知识/事实分流、SKILL.md、复用旧组件、按风险保护、Vue 早接入（D-01–08）。具体 API/DDL 在原文是建议，不误冻结成已实现协议。

## 规划推导

13 个计划组成 5 阶段；Phase 1 = M0；Phase 2 = M1；Phase 3 = M2；Phase 4 = M3；Phase 5 = M4。先保存自由任务，再以手册检索+早期 Vue+同run基本补证形成可用片段，随后补资产/跨进程恢复，再双域 Skill，最后 SSE/完整评测/部署。

## 验证与审批

GSD Core 1.20.1 identity/init 已实际调用；工作在本会话内完成，没有派发执行器。145 项离线测试本轮 PASS；没有新产品或真实模型验证。当前规划待用户审查；STATE 维持 planning，所有开发计划未开始。

