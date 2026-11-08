# DeviceTruth V0.2｜跨会话状态快照（State）

> 此文件只记录**最新有效状态**；每个里程碑通过 Codex Review 后更新。不要复制聊天记录、完整测试日志或长篇 Review。
>
> 当前快照由文档包生成，**所有任务尚未开始、尚未 Review**。

## 1. 当前总体状态

- 计划：S0 → S1；后续 S2 → S3 → S4，不调整。
- 当前阶段：**S0**。
- 下一待执行里程碑：**M0**（先审计仓库/源码/测试/CLI 真实基线，再依据事实冻结契约）。
- 文档规格状态：**DRAFT**（M0 前半段核查、后半段冻结）。
- 当前 Review：**无**。
- 当前开发基线 commit：**待 M0 正式记录**。
- 当前工作树安全状态：**存在本次流程合并与 VS Code JDK 21 配置改动；M0 开始时须记录精确 diff，不得当作业务开发改动**。
- 是否完成 S1：**否**。

## 2. 里程碑进度

| ID | 状态 | Review | 交付摘要 |
|---|---|---|---|
| M0 | TODO | 未执行 | 待 S0_AUDIT + spec 冻结 |
| M1 | TODO | 未执行 | 待双入口/Catalog/Session/Router |
| M2 | TODO | 未执行 | 待 Query/Goal→Core |
| M3 | TODO | 未执行 | 待异步 Run/Events + S1 最终验收 |

## 3. 已确定的**设计意图**（并非本轮已验证代码事实）

- 保留 V0.1 自研 `TaskOrchestrator`/Guard/证据机制，不使用 ReActAgent 替代循环。
- S1 用 HTTP 进入自然语言路由，再分别执行受限 QUERY / DIAGNOSE；CLARIFY 和 UNSUPPORTED 不伪造执行。
- CLI 默认 JAR Main 保留；Web 单独 Main。
- 模拟数据/业务时间与请求时间分离；每任务独立 Reasoner；最终事件结束后可读。
- S2 再做 Hypothesis 专业调查闭环、补证恢复；S3/S4 不提前。

## 4. 当前已核实代码/测试事实

- 2026-11-08 流程合并前预检：项目内 JDK `21.0.13.0.2` 可用，Maven 3.9.9 在显式 `JAVA_HOME` 下使用 Java 21；`mvn -q clean test` 实测 42 项通过、0 失败/错误/跳过。
- `.vscode/settings.json` 已为本项目新建终端设置项目内 JDK 21；这只解决开发终端环境，不替代 M0 对完整源码、CLI、打包和模型路径的正式审计。
- 原修订方案的其他历史线索仍必须由 M0 前半段逐条核查。

## 5. 阻塞与待决策事项

- [ ] M0 正式记录 JDK/Maven、CLI、Model API、Git 基线与完整测试证据；预检结果不能代替审计报告。
- [ ] 核对现有 Git 工作树与可回退点；将流程文档和 `.vscode` 配置与后续业务 diff 分开记录，未授权不提交、覆盖或清理。
- [ ] M0 冻结 Server 启动命令、容量拒绝状态码、具体数据接口/DTO。
- [ ] 每个里程碑 PASS 后由用户决定是否创建本地 checkpoint commit；无 checkpoint 时必须保存完整补丁与文件哈希。

## 6. 下一里程碑交接卡（开发者开始工作时首先读取）

```text
MILESTONE_ID: M0
PHASE: S0
ROLE: 开发者连续完成事实审计与契约冻结；Codex 使用独立上下文做一次 M0 Review
READ_FIRST:
  - 本文 state.md
  - task.md 中 M0
  - 改造实施方案_修订版.md
  - spec.md（DRAFT）
  - 项目根 README/AGENTS.md 与 V0.1 文档（如存在）
MUST_PRODUCE:
  - S0_AUDIT.md（基于真实源码+真实命令）
  - 基于审计事实修订的 spec 冻结候选与 task/state
  - M0 交付摘要（审计发现、契约决定与实际测试结果）
REVIEW:
  - Codex → reviews/M0.md
DON'T:
  - 不写 Java 业务代码
  - 不改 Guard/Orchestrator
  - 不在审计完成前根据方案猜测并冻结接口
  - 不发起未经授权的 git reset/clean/commit/push
```

## 7. 本文更新规则

每次 Codex Review `PASS` 后，由交接步骤统一更新：里程碑状态、Review 文件路径、有效事实、遗留 P2/阻塞、下一个 `MILESTONE_ID` 和下一轮需读文件；然后在用户授权下建立本地 checkpoint。`CHANGES_REQUIRED` 时保持同一个里程碑，不推进。**任何 Review 报告只证明其真实审查过的范围**。
