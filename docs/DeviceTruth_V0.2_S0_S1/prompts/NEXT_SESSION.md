# 本轮 Review PASS 后的交接更新（发给 Codex 或由负责人手动执行）

```text
当前 DeviceTruth V0.2 的 reviews/RXX.md 已得到 Codex 最终 PASS。

请只更新以下开发文档，不修改 Java 源码、pom.xml 或测试：
1. task.md：将 RXX 置 DONE；下一任务仍为 TODO，不能提前标完成。
2. state.md：记录 RXX 通过、实际验证事实与仍在的 P2 / 环境 BLOCKED_BY_ENV；将下一会话交接卡 TASK_ID 调整为下一个未完成任务，并列出下一轮仅读取的关键文件。
3. 如 R02 通过：将 spec.md 从 DRAFT 改为 FROZEN，但若存在重大架构分歧必须先征得用户同意，并在变更表记录。
4. 如 R07 通过：将 S1_ACCEPTANCE_REPORT.md 的结论与实际测试矩阵保持一致；不要将未验证 online 项目当 PASS。

只做文档状态交接，不新增需求，不自行 Git commit/push/reset/clean。

更新后输出三段：
- 已完成并核实的 RXX 交付事实。
- 当前尚未解决的风险和限制。
- 下一个新 DeepSeek 会话的 TASK_ID、目标、必须读取文件。

切记 state.md 只保存精简快照，不复制长 Review 内容。只有 Review PASS 才推进 TASK_ID；如果仅 CHANGES_REQUIRED 则不得执行本提示词。
```
