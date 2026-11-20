# Proposal

## Why

现有 AnswerGuard 把“资料事实明确”“当前设备故障确诊”和“危险执行指导”混用为整份答案的拒绝条件。2026-11-19 真模 Run `c96da39d-263f-4b35-84f8-c30fdab28ab0` 已成功检索资料，却因 UNCONFIRMED_FINDING 退成 HUMAN_REVIEW；审计未保留具体触发项，尚不能断言这次是模型越界还是误拦。用户已通过逐项讨论确认新的产品边界，需要系统性落实而非删词放行。

## What Changes

- 最终 ANSWER 整体复核关键结论、引用依据和高风险具体建议；不再逐句分类、强制每项配引用。
- 程序守授权来源、引用真实性、对象及运行生命周期；模型检查来源支持关系、事实越界和高风险指导依据。
- 一般解释、低风险调查建议、补充信息请求、缺口和限制无需强制引文，不得冒充手册规定或现场事实。
- 关键词不作为新版答案放行/拒绝条件；旧内部入口仍保留兼容行为。
- 一次整体复核，发现实质问题时一次定向修正，随后程序硬校验；取消、迟到、授权、步数和总时间合同不变。
- 修正成功可正常交付，不因发生修正自动 limited；复核失败或关键问题未解决不得伪装完整成功。
- 规程适用性、结构化前提及工作台联合指导验收迁入add-procedure-grounded-guidance，保留原需求与未完成状态。

## Capabilities

### New Capabilities

- `evidence-aware-answer-review`：陈述层证据、确定性、局部修复与语义复核。
规程能力迁入独立变更`add-procedure-grounded-guidance`，不作为本变更完成条件。
- `answer-review-observability`：脱敏可定位的复核记录、恢复与质量验收。

### Modified Capabilities

无。主 specs 当前为空，既有 delta 与实现需承接；本变更明确替换旧 Guard 合同，不恢复早期分类组合整份拒绝规则。

## Impact

涉及 AgentResult/ActionProposal/AgentTaskInput、StructuredAgentReasoner、AnswerGuard、TaskOrchestrator、执行段快照与恢复、事件/报告安全投影、前端结果 ViewModel/报告和对应回归。保持原入口、存量结果、schemaVersion/引用合同、唯一主循环、预算及空间授权。模型之外的语义复核方案在 design 中设有界策略，不引入新 Agent 平台或无界重试。

推荐执行顺序：`add-authenticated-team-workbench` 第 4 组 → 第 5 组 → 本变更 → 第 6 组。先具备输入确认快照和材料范围/版本，再修改 Guard；在工作台前端接线前交付稳定的结果/确认合同。已授权实施，不自动勾选其他变更。
