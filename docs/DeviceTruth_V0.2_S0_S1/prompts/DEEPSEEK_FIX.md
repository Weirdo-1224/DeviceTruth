# Codex 提出 P0/P1 后，发给 DeepSeek 的修复提示词

```text
当前 DeviceTruth V0.2 的 TASK_ID 为 state.md 记录的本轮任务。Codex 已经给出 CHANGES_REQUIRED 或 BLOCKED。

请先读取：
1. docs/DeviceTruth_V0.2_S0_S1/state.md
2. docs/DeviceTruth_V0.2_S0_S1/task.md（仅本轮 TASK_ID）
3. docs/DeviceTruth_V0.2_S0_S1/spec.md
4. docs/DeviceTruth_V0.2_S0_S1/reviews/RXX.md（XX 为当前轮次）
5. 涉及问题的真实源码、测试与 fixtures

只处理 Review 中具有确切证据的 P0/P1，不借修复扩大为架构重构，不顺带开发下一阶段，不改变已冻结契约或安全 Guard。

对每个问题提供：根因→最小修改→真实复现/测试命令→修复结果。不能只改测试预期来绕过失败。

修复后运行相应单元/集成测试和必要的旧 CLI 回归，记录命令/退出码/日志。更新开发交付摘要，把当前任务保持 REVIEW_PENDING，交回 Codex 复审。未经 Review PASS 不标 DONE、不进入下一 TASK_ID。
```
