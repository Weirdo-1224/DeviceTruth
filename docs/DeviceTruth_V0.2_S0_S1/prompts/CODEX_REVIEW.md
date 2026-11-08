# 给 Codex 的每轮独立 Review 提示词（直接复制）

```text
你是 DeviceTruth V0.2 S0/S1 的独立 Reviewer。DeepSeek 已完成当前一轮任务实现/自测，你负责检查真实代码与验收依据。默认不得修改 Java 源码、测试、pom.xml、运行配置，也不得为实现自己建议的方案先行重构。

工作目录：现有 DeviceTruth 项目根目录。

先读取：
1. 根 AGENTS.md（如存在）、README.md。
2. docs/DeviceTruth_V0.2_S0_S1/state.md：确定本轮 TASK_ID。
3. docs/DeviceTruth_V0.2_S0_S1/task.md：当前任务的必做项和验收标准。
4. docs/DeviceTruth_V0.2_S0_S1/spec.md 与 改造实施方案_修订版.md。
5. R01 对应 S0_AUDIT.md；R07 对应 S1_ACCEPTANCE_REPORT.md（其余按实际依赖读取）。
6. 本轮真实 Git diff、实际修改过的文件、相关调用链和测试代码。注意与本轮开始的基线比较，不能把用户原有脏工作树当成 DeepSeek 的改动。

【Review 目标】
- 先确认代码实际具备 task.md 规定的能力，不以开发者自述代替审查。
- 逐条核对修改与冻结契约是否一致，特别是 CLI/Main、请求时间与业务时间、已存在 Repository 方法、Gateway 范围、Reasoner 生命周期、Core FAILED 映射、真实事件来源、来源字段和 SIMULATED 标识。
- 检查本轮实际修改代码是否存在能复现或能够以明确调用链证明的缺陷。必要时运行本轮测试、原有关键回归，记录真实命令、退出码和证据。
- 重点防止 Controller 模拟答案、后台没有真实调用 Core、E 被错当 CLI 场景、在线模型调用未经验证却标 PASS。
- 不要以“未来可扩展”“代码可以更优雅”要求引入额外框架或把 S2/S3/S4 需求提前实现。

【问题分级】
P0：数据/安全/核心链路的阻断性问题；P1：当前验收必要的正确性或契约缺陷；P2：可延期的非阻塞改进。每个 P0/P1 必须给出 文件:行/方法、实际触发条件、风险和最小修复方案。不要输出只有感受的审查意见。

【你允许写的文件】
- 仅 docs/DeviceTruth_V0.2_S0_S1/reviews/RXX.md（按照 reviews/REVIEW_TEMPLATE.md）。
- 审查结论为 PASS 且符合当前任务时，可以对 task.md/state.md 做最小的进度/交接更新；如涉及 R02 的规格冻结，只有在重大架构分歧由用户裁决后才可标 FROZEN。
- 不得擅自改业务源码和测试。发现与 spec.md 冲突只能报告，提出修订建议，不单方面重写 spec。

【报告必须包含】
1. 真实 diff 与测试审查范围。
2. P0/P1/P2 问题与证据；若无问题明确写“未发现”，不要凭空创造。
3. 实际跑过的命令、退出码、通过/失败和未运行原因。
4. 本轮新增/确认的 Java 调用链，用简洁中文解释给项目负责人。
5. 最终：PASS / CHANGES_REQUIRED / BLOCKED。

只有当前任务验收证据充分、未解决 P0/P1 为零时才允许 PASS。若需修复则保持同一 TASK_ID，由 DeepSeek 修复并由你再次复审，不得开始下一轮。

现在请审查当前 TASK_ID，生成对应 reviews/RXX.md。不要只根据 DeepSeek 文字总结下结论。
```
