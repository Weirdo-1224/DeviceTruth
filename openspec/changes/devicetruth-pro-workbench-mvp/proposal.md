# Proposal

## Why

现有 Agent 已能自主取证、持久化答案并接收补答，但现有报告页缺少围绕某个判断选择、查看证据、再次调查的专业工作流。比赛仅剩一周，按 V0.2 计划把已有能力接成一张卡片式专业工作台，工业和汽车共用。

## What Changes

- 新增专业交互式诊断工作台的判断选择、详情和证据抽屉，支持深入调查、补充信息与多轮结果查看；确定性 ViewModel 映射现有 AgentResult，不增加模型调用。
- 接通“选中判断 → 深入调查 → 同会话新 Run → 新结果”，扩展前端消息封装传 contextRefs；复用原有 ASK_USER 补答恢复。
- 复用 SSE、轮询、取消和会话回读，补齐逐轮查看及异常状态呈现；修复 P1-04 的周期 QUEUED 补派（本变更 P0）。
- P1 可选：RUN_TOOL 安全参数摘要、P1-03 RAG 事务错误路径修复。P1-05 仅记录末步结果提交前崩溃风险，不重构存储架构。
- 不做个人模式、登录、多租户、管理后台、关系画布、Vue Flow、完整 Case 系统、CanvasComposer、Case/Canvas 表或第二套 Runtime；不实现跨 Run 判断实体匹配、自动结论冲突合并或完整动态推理图谱，不新增 Agent 协议或扩展 AgentResult。

## Capabilities

### New Capabilities

- `professional-diagnosis`：专业判断、结构化结果、引用与来源语义。
- `investigation-continuation`：深入调查、现场补充与 ASK_USER 补答分流。
- `investigation-recovery`：运行进度、失败/取消、刷新历史恢复和滞留队列恢复。

### Modified Capabilities

无。当前 `openspec list --specs` 无主规格；上述新增规格描述专业模式增量及复用边界，不代表后端已有能力需要重建。

## Impact

前端主要改动 `App.vue`、`api.ts` 和现有报告/证据组件，新增轻量 ViewModel 及专业页组合组件；后端 P0 仅调整 GeneralAgentService、RunRecoveryService 和必要的仓储查询及回归测试。沿用 REST/SSE、PG 表、TaskOrchestrator、GeneralReasoner、GeneralAgentService、AgentResult、SkillRuntime 和 RAG，无新依赖和新 API。

成功标准：D1 确认 API 合同、运行环境及至少一份可读取的真实持久 Run，前端立即启动，双域真实资料并行准备；D2–D3 并行修复 QUEUED 恢复。D4 前完成一条真实模型结合真实授权资料的端到端调查；D4 实测选择已有判断、深入调查新建 Run、授权引用继承、新旧结果查看、补充信息新建 Run 及 ASK_USER 恢复原 Run。模型未输出候选原因时安全展示现有结果并保留普通文字继续调查入口，不伪造候选。D6 完成工业与汽车双域验收，D7 功能冻结；刷新可读历史与状态。模拟观测始终明确标示，用户陈述不升级为核验事实；模拟/协议替身测试不得记作真实模型与真实资料验收。
