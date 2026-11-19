# Proposal

## Why

现有 AnswerGuard 把“资料事实明确”“当前设备故障确诊”和“危险执行指导”混用为整份答案的拒绝条件。2026-11-19 真模 Run `c96da39d-263f-4b35-84f8-c30fdab28ab0` 已成功检索资料，却因 UNCONFIRMED_FINDING 退成 HUMAN_REVIEW；审计未保留具体触发项，尚不能断言这次是模型越界还是误拦。用户已通过逐项讨论确认新的产品边界，需要系统性落实而非删词放行。

## What Changes

- 区分资料事实、用户报告、历史记录、当前设备推断与独立核验；判断强度与证据匹配，不再以全局 fieldVerified 或 CONFIRMED 一刀切。
- 保留独立成立内容，定向修正/移除问题陈述及其依赖建议；不能可靠拆分时才整份拒绝，不机械添加“可能”掩盖依据不足。
- 关键词仅定位疑点，不能单独否决；权限、引用归属、来源真实性仍为硬约束，模型自报分类不构成放行依据。
- **BREAKING**：在适用规程与关键前提有依据时允许高风险具体作业指导，替换当前“除禁止/资质声明外一律禁止操作步骤”的产品合同；无规程不得由模型补写流程。工具仍只读。
- 区分规程查阅与当前设备执行指导，保留型号、版本、前提、顺序、警告、停止条件及原文定位；未知/冲突前提阻止受影响的执行指导，不阻止查阅。
- 用户现场记录可支持明确的有条件判断；人员资质/现场状态首版以用户明确确认作为来源，不升级为系统核验、不另建资质平台、空间角色不等于专业资质。
- 增加可定位、脱敏的判定依据与有界修正证据，验证正反例和真实模型答案可用性，不以 COMPLETED 或来源数代替质量通过。

## Capabilities

### New Capabilities

- `evidence-aware-answer-review`：陈述层证据、确定性、局部修复与语义复核。
- `procedure-grounded-guidance`：有规程依据的查阅/执行指导及前提确认。
- `answer-review-observability`：脱敏可定位的复核记录、恢复与质量验收。

### Modified Capabilities

无。主 specs 当前为空，既有 delta 与实现需承接；本变更明确替换旧 Guard 合同，不恢复早期分类组合整份拒绝规则。

## Impact

涉及 AgentResult/ActionProposal/AgentTaskInput、StructuredAgentReasoner、AnswerGuard、TaskOrchestrator、执行段快照与恢复、事件/报告安全投影、前端结果 ViewModel/报告和对应回归。保持原入口、存量结果、schemaVersion/引用合同、唯一主循环、预算及空间授权。模型之外的语义复核方案在 design 中设有界策略，不引入新 Agent 平台或无界重试。

推荐执行顺序：`add-authenticated-team-workbench` 第 4 组 → 第 5 组 → 本变更 → 第 6 组。先具备输入确认快照和材料范围/版本，再修改 Guard；在工作台前端接线前交付稳定的结果/确认合同。当前只创建规划，不授权实施、不自动勾选其他变更。
