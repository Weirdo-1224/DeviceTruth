# DeviceTruth V0.2｜跨会话状态快照（State）

> 此文件只记录**最新有效状态**；每轮通过 Codex Review 后更新。不要复制聊天记录、完整测试日志或长篇 Review。
>
> 当前快照由文档包生成，**所有任务尚未开始、尚未 Review**。

## 1. 当前总体状态

- 计划：S0 → S1；后续 S2 → S3 → S4，不调整。
- 当前阶段：**S0**。
- 下一待执行任务：**R01**（仓库/源码/测试/CLI 真实基线审计）。
- 文档规格状态：**DRAFT**（R01 核查、R02 冻结）。
- 当前 Review：**无**。
- 本轮开发基线 commit：**未读取仓库，待 R01 填写**。
- 当前工作树安全状态：**未知，待 R01 填写**。
- 是否完成 S1：**否**。

## 2. 任务进度

| ID | 状态 | Review | 交付摘要 |
|---|---|---|---|
| R01 | TODO | 未执行 | 待 S0_AUDIT |
| R02 | TODO | 未执行 | 待 spec 冻结 |
| R03 | TODO | 未执行 | 待双入口/Catalog |
| R04 | TODO | 未执行 | 待 Session/Router |
| R05 | TODO | 未执行 | 待 Query/Goal |
| R06 | TODO | 未执行 | 待 Run/Events |
| R07 | TODO | 未执行 | 待 S1 最终验收 |

## 3. 已确定的**设计意图**（并非本轮已验证代码事实）

- 保留 V0.1 自研 `TaskOrchestrator`/Guard/证据机制，不使用 ReActAgent 替代循环。
- S1 用 HTTP 进入自然语言路由，再分别执行受限 QUERY / DIAGNOSE；CLARIFY 和 UNSUPPORTED 不伪造执行。
- CLI 默认 JAR Main 保留；Web 单独 Main。
- 模拟数据/业务时间与请求时间分离；每任务独立 Reasoner；最终事件结束后可读。
- S2 再做 Hypothesis 专业调查闭环、补证恢复；S3/S4 不提前。

## 4. 本轮已核实代码/测试事实

**无。尚未连接当前 DeviceTruth 源码仓库。** 原修订方案的历史线索必须由 R01 逐条核查。

## 5. 阻塞与待决策事项

- [ ] 核对当前 JDK/Maven/CLI/Model API 实际情况（R01）。
- [ ] 核对现有 Git 工作树与可回退点；未授权不提交、覆盖或清理（R01）。
- [ ] R02 确认并冻结 Server 启动命令、容量拒绝状态码、具体数据接口/DTO。

## 6. 下一会话交接卡（开新 DeepSeek 会话首先读取）

```text
TASK_ID: R01
PHASE: S0
ROLE: DeepSeek 负责只读事实审计与执行已有测试；Codex 负责独立 Review
READ_FIRST:
  - 本文 state.md
  - task.md 中 R01
  - 改造实施方案_修订版.md
  - spec.md（DRAFT）
  - 项目根 README/AGENTS.md 与 V0.1 文档（如存在）
MUST_PRODUCE:
  - S0_AUDIT.md（基于真实源码+真实命令）
  - 开发交付摘要（审计发现与实际测试结果）
REVIEW:
  - Codex → reviews/R01.md
DON'T:
  - 不写 Java 业务代码
  - 不改 Guard/Orchestrator
  - 不发起未经授权的 git reset/clean/commit/push
```

## 7. 本文更新规则

每次 Codex Review `PASS` 后，只更新：任务状态、Review 文件路径、有效事实、遗留 P2/阻塞、下一个 `TASK_ID` 和下一轮需读文件；更新完后可切换新会话。`CHANGES_REQUIRED` 时保持同一个任务 ID，不推进下一会话。**任何 Review 报告只证明其真实审查过的范围**。
