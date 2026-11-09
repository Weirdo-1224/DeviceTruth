# 新会话接续说明

> 2026-11-09，用户明确授权的 **01-01 已完成**；下一计划01-02尚未执行/未授权。STATE的execution_authorized=false针对后续实施，不抹去已完成的01-01授权记录。
> 禁止自动执行剩余12份计划或续跑旧S2；本轮没有commit/push/部署。

## 必须先读

1. 根AGENTS.md、.planning/STATE.md、PROJECT.md。
2. 指定V1.0 PostgreSQL设计（hash仍为C80A4C9292787B181DDB2406CAE4746B83F602993B22EAAA8C2C42AF5D698120）。
3. [01-01-SUMMARY](phases/01-baseline-contracts/01-01-SUMMARY.md)、[BASELINE](phases/01-baseline-contracts/BASELINE.md)、[CONTRACTS](phases/01-baseline-contracts/CONTRACTS.md)、[ADR-runtime](phases/01-baseline-contracts/ADR-runtime.md)、[RUNTIME-SPIKE](phases/01-baseline-contracts/RUNTIME-SPIKE.md)。
4. REQUIREMENTS/ROADMAP、目标PLAN和前置真实完成记录；旧spec/task/state仅理解legacy。

## 最新事实

- 实施起点HEAD a9607cf7b1631c221a9f9865036751642772e352；无Git提交，当前工作树保留本轮改动。不能覆盖或回滚这些成果。
- 主源码52文件不变，测试29文件。继承145项通过，新增RuntimeComparisonTest后全量146/0/0/0；最终package、verify_cli都退出0。
- 新JAR独立HTTP Main实际启动，/api/health、/actuator/health、index通过，已关进程；默认offline Router + openai Reasoner。
- 两候选6样例×2，连续两次clean成功且输出完全相同。选演进现有TaskOrchestrator，继续Model API，SDK候选仅测试，不引入生产双栈。
- cases.json与比较测试保留在src/test；target/spikes只是输出，可clean重建。所有原始日志在忽略的outputs/validation。
- 生产尚无PG/RAG/标准Skill/Vue/SSE/WAITING/持久恢复。原型JSON新实例续跑不等于跨进程/PG恢复。
- 契约固定原文必填/资产可空、服务器owner/project与权限、三动作、引用/风险、runId/pendingQuestionId/questionVersion补答关联、M1/M2界限。
- 旧模型私有文件兼容保留，新PG/embedding只服务端环境注入；不读取或打印真实私密值。
- 独立Review未进行；M0只完成01-01，阶段尚未完成。63需求主映射保持不变，TRUST-06全链路仍部分实现。

## 如何继续

用户只要求审阅时继续分析和修订；明确要求执行01-02时记录授权范围，再核对Git/环境/01-01证据及目标PLAN。01-02是PG持久会话/消息/QUEUED切片，不运行Agent worker。其他计划不因01-01完成而自动授权。

JDK先用VALIDATION/BASELINE中的进程级JAVA_HOME/PATH命令，避免默认Java17。不更改全局执行策略。01-02需要核实Docker/PG/pgvector版本与实际可用性，首次新增依赖核对官方API和缓存/网络。

.planning/validate_planning.py保留历史“仅规划”断言（未授权/0 SUMMARY/业务无新增），实施后不适用，不用它的结果判断本轮开发成败。按GSD结构/一致性、需求映射/链接/源码保护和真实Maven/CLI/PG测试分别核验，历史证据文件不覆盖。

每次完成后用通俗中文结合充电或配置冲突解释真实输入→服务→主循环→网关→引用/风险→结果，列出实际命令、结果与未验证项。独立Review和用户反馈按实际记录，不自动commit或推送。
