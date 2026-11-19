# Proposal

## Why

专业工作台当前存在三类影响任务闭环的问题：补答后实际排查设备与页面上下文可能不一致；结果首屏重复且无法区分候选原因与背景观测；历史会话难以辨认，报告入口也没有形成可打印、可交付的报告。需要统一服务端事实表达并重组前端任务流程，而不是仅靠样式调整掩盖问题。

## What Changes

- 为 Run 增加权威的本轮排查对象及确认状态，贯通任务卡、设备快照、深入调查、历史列表和报告；无法唯一确定时明确标记，不根据最后一次工具调用猜测。
- 扩展结构化判断的业务类型与目标相关性，兼容缺少新字段的历史结果；结果首屏按短结论、下一步、直接相关候选原因和直接证据组织，其他观测与完整原文按需查看。
- 新增带标题、设备、真实状态、待补答标记和轮次的会话摘要查询，支持关键词、设备、状态筛选与游标分页；原会话列表合同保留兼容。
- “新调查”先保留为本地草稿，首次提交时再创建服务端会话；已有空会话只在发现列表中隐藏，不删除审计数据。
- 将报告入口改为独立报告视图，直接复用持久 Run、统一排查对象与已登记引用，不再次调用模型；提供打印及浏览器“另存为 PDF”。
- 保持 legacy 链路、只读能力、归属校验、证据语义及 SIMULATED/未现场核验声明不变；不新增公开分享、物理删除或第三方 PDF 依赖。

## Capabilities

### New Capabilities

- `investigation-subject-context`: 定义本轮实际排查对象的确认、变更、歧义表达及其在各产品视图中的一致使用。
- `diagnosis-result-presentation`: 定义结构化判断类型、相关性、旧结果兼容及结论—行动—证据的结果呈现方式。
- `conversation-discovery`: 定义可搜索、筛选、分页且状态真实的会话摘要，以及空草稿与历史会话的展示规则。
- `diagnosis-report-delivery`: 定义由持久调查数据形成独立、可打印诊断报告的内容与交付行为。

### Modified Capabilities

无；主规格目录当前没有已登记能力。既有变更目录中的专业工作台规格作为历史设计约束参考，不在本变更中直接改写。

## Impact

- 服务端：`AgentResult`、`GeneralAgentService`、`AgentRunRepository`、`ConversationRepository`、`StructuredAgentReasoner`、`AnswerGuard` 及必要的运行持久化迁移。
- API：Run/结果增加向后兼容字段；新增会话摘要分页查询，既有列表、消息、Run、引用与取消合同继续使用。
- 前端：`api.ts`、`diagnosisViewModel.ts`、`App.vue`、`ProfessionalDiagnosisPage.vue`、判断列表/详情、近期调查、`DiagnosisReport.vue` 与打印/响应式样式。
- 测试：Java 合同、仓储、归属与恢复回归；前端 ViewModel/API/组件合同测试；桌面与移动端真实 product、PostgreSQL、真实模型端到端验收。
- 不新增运行时第三方依赖，不改变模型供应商、embedding、数据库数据性质或权限边界。
