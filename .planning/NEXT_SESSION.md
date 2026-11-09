# 新会话接续说明

> 当前只完成规划，**REVIEW_PENDING / execution_authorized=false**。新会话不能把存在 PLAN 当作已授权执行。
> 这是 V1.0 新规划入口；旧 state 里的“下一步 S2”是历史，不是本轮工作队列。

## 必须先读

1. 根 AGENTS.md 与 .planning/STATE.md。
2. .planning/PROJECT.md 与 docs/DeviceTruth_V1.0_通用设备检修Agent_完整产品设计方案_PostgreSQL.md。
3. docs/DeviceTruth_GSD_规划审查报告.md、PLAN-REVIEW、GAP-ANALYSIS、REQUIREMENTS、ROADMAP；按需要读 codebase 地图。
4. VALIDATION / INGEST-CONFLICTS，识别已验证事实、历史记录与未定选择。
5. 若明确授权开发，再读目标 PLAN、依赖 SUMMARY 与当前源码；按照仓库要求阅读旧 spec/task/state 理解legacy契约，不能用其覆盖新版目标。

## 如何继续

- 用户还在审查：只讨论/修改规划，保持全部开发任务未开始。
- 用户明确批准并要求实施：把批准范围/日期写 STATE，重核当前 Git/设计摘要；从 Phase1的01-01开始。不是直接执行全部13计划，更不能回旧S2任务。
- 用户只批准部分阶段：执行范围仅限该阶段；后续是否推进按用户授权与阶段出口判断。
- 任何继续均保护用户新增设计文件、未提交改动与私有配置；不自动commit、push或创建生产资源。

## 当前已知事实

代码HEAD b7befa08f702675d05dcc9560cf7eb87e4b778c2。52主Java/28测试Java，前轮145项离线PASS（本轮未重跑）；无PG/RAG/标准Skill/Vue/SSE/持久恢复实现。原生HTML、Actuator、默认offline+openai已入库。详见baseline.json与VALIDATION。

开发环境先设置当前进程JDK21（VALIDATION有可复制命令）；不要改全局执行策略。首次引新依赖需要真实验证网络/缓存/官方API与兼容版本。

## 本次修订的接续边界

- 保留五阶段/63需求/13计划/37任务，全未开始；仅 AGENT-03 主交付迁到 02-02。
- M1 同 run 基本补证；M2 检查点/崩溃恢复/租约CAS/幂等并发。E2E-07-basic 与 E2E-07-restart 分层留证。
- 01-01 原型保留测试与固定输入，02-03 增真实 embedding/模型及12个固定检索样本；这些验证均待执行。
- 跨阶段入口显式依赖前阶段末计划；禁止跳过阶段出口。
- 本轮仅运行规划结构/一致性校验；详细命令及结果见 VALIDATION 和 evidence/planning-validation.json。

## 后续更新纪律

- 每项计划真实完成才写SUMMARY，记录变更、执行命令/退出码、结果、未验证项；更新STATE/ROADMAP及需求追踪。
- 新增协议/迁移/风险变化先写决策/影响，不能静默削弱旧Guard或删除测试；legacy与新行为分路径测试。
- 在线模型、PG集成、UI和旧CLI分开报告。模型缺配置/额度时保留环境状态；不能拿fake model代替在线质量。
- 每次开发完成必须用通俗中文结合充电问题或工厂冲突，解释消息→服务→主循环→工具→来源/风险检查→持久结果的真实链路。
- Review报告只证明实际审查范围；本轮PLAN-REVIEW为自检，未有独立Review PASS。

## 可复制的继续审查提示

```text
继续审查 DeviceTruth V1.0 规划。先读 AGENTS.md、.planning/STATE.md、
.planning/PROJECT.md 与指定 PostgreSQL 产品设计，再读差距矩阵和路线图。
本次仍只分析规划，不能执行 PLAN 或修改业务代码。
以当前代码证据核对建议，旧版仅作历史背景。
```

明确批准开发时可说明“批准规划，开始 Phase 1 的 01-01”；接续会话仍需先核对STATE和当前工作树，不继承旧文档的自动开发指令。

