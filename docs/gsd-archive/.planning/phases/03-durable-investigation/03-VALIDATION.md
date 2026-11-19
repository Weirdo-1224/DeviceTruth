---
phase: "03"
slug: "durable-investigation"
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-11-13"
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ（后端）、Vitest（前端 `frontend/src/api.test.ts`） |
| **Config file** | `pom.xml`（surefire）、`frontend/vite.config.ts` |
| **Quick run command** | `mvn -q '-Dtest=AgentLifecycleTest,RunRecoveryTest,AssetToolTest' test`（需 DEVICETRUTH_TEST_PG_* 环境） |
| **Full suite command** | `mvn -q test`（真实 PG）+ `cd frontend && npm run test -- --run` |
| **Estimated runtime** | ~2–4 分钟（全量，含真实 PG 每个测试类自建/自删随机库） |

---

## Sampling Rate

- **After every task commit:** 相关测试类定向运行 + 受影响套件
- **After every plan wave:** 全量 `mvn -q test`（编排侧合并门已两次实际执行：317/0/0/1、329/0/0/1）
- **Before `$gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 300 秒

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-全部 | 03-01 | 1 | DATA-01/02/03, TOOL-03, TRUST-02, TRUST-04 | integration（真实PG） | `mvn -q '-Dtest=AssetToolTest,ResourceAuthorizationTest' test` | ✅ | ✅ green（25 项 + 8 项，03-01 独立复审通过） |
| 03-02-T1 | 03-02 | 2 | AGENT-06, STORE-04 | integration | `mvn -q '-Dtest=RunRecoveryTest' test` | ✅ | ✅ green（9 项，崩溃对账/并发对账/无孤儿） |
| 03-02-T2 | 03-02 | 2 | AGENT-06, STORE-04 | integration | 同上 | ✅ | ✅ green |
| 03-02-T3 | 03-02 | 2 | TOOL-04, WEB-02 | integration + contract | `mvn -q '-Dtest=AgentReportToolTest' test`；`cd frontend && npm run test -- --run` | ✅ | ✅ green（4 项 + 前端 18/18） |
| 03-03-全部 | 03-03 | 3 | AGENT-04, OPS-01, OPS-03 | integration + CLI + HTTP 套件 | `mvn -q '-Dtest=AgentLifecycleTest' test`；`python scripts/verify_cli.py`；`python scripts/verify_general_agent.py --suite=investigation --embedding=deterministic --reasoner=scripted` | ✅ | ✅ green（15 项：12 原 + 3 Nyquist 缺口，ba7bf4f；CLI/investigation PASS） |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*AGENT-07（hypothesis 可选笔记）由 legacy E07/E10 证据承载，本阶段无新增专用测试，符合"可选笔记"边界。*

---

## Wave 0 Requirements

- 无需新增 Wave 0：既有基础设施（JUnit/Maven/Vitest + 真实 PG 测试基座）覆盖本阶段全部需求。

---

## Manual-Only Verifications

| Item | Requirement | Rationale |
|------|-------------|-----------|
| 浏览器两轮问答/刷新/重启人工演示 | WEB-02 | 03-02 SUMMARY 偏差 d：自动化仅覆盖到前端合同 + 构建 + 后端真实 HTTP 链路；浏览器交互体验为 D6 人工验收项（end-of-phase UAT） |
| 真实模型（qwen/openai）与真实 embedding 链路 | AGENT-04/AGENT-06/OPS-03 | 缺供应商 key/额度时 NOT_RUN，不降级冒充通过；保持 null/UNKNOWN 不编造 |
| PDF/OCR 文档导入 | TOOL-03/TOOL-04 | 明确转 Phase 5 质量交付阶段 |
| 真实多人认证 | TRUST-04 | 固定本地身份为单用户假设，05-03 接入后验收（SECURITY.md AR-03-01） |

---

## Validation Audit

| Audit Date | Gaps Found | Resolved | Escalated | Run By |
|------------|------------|----------|-----------|--------|
| 2026-11-13 | 1 组（AGENT-04：通用 Agent 路径步数预算 BUDGET_EXCEEDED 等无测试） | 1（AgentLifecycleTest 新增 3 项，15/15 绿，commit ba7bf4f） | 0 | gsd-nyquist-auditor（重建式审计，retroactive） |

*审计注记：三个 PLAN 均无 `<threat_model>` 块，安全侧按 retroactive-STRIDE 重建寄存器（见 03-SECURITY.md）；本表覆盖需求→测试交叉核对，抽查实现与断言一致而非仅信 SUMMARY 声明。*
