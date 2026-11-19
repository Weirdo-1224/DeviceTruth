# 需关注的问题

1. **入口主动排斥新版主故事**：RouteValidator 拒绝“充电”，Catalog 和症状规则双门槛；优先改新产品入口，保留 legacy 行为。
2. **用户目标丢失**：DiagnosisGoal.toRequest 去掉原文；要让原始问题、历史摘要和补充消息进入模型。
3. **工具参数不可通用**：所有字段 Checks.id，不可直接注册自然语言 query / filters；需逐工具 schema。
4. **追问不可继续**：RequestInformation 直接 COMPLETED；本地状态/队列无法重启恢复；必须持久检查点和调度对账。
5. **全局化旧 Guard**：规则 + 全量调查保护不能强加普通知识问答；风险策略拆分时仍守住关键配置与高风险边界。
6. **公开信息不能简单放开**：旧 SafePublicFields 抹掉追问/自由答案；新增用户结果 DTO，内部事件继续白名单。
7. **内存归属不等于权限**：sessionId 不是用户身份。PG 的 owner/project 与 RAG 过滤必须从首批数据起存在；多人部署前接认证。
8. **报告快照增长**：REASON_INPUT 包含全状态，长期调查会增长；摘要/上下文窗口与审计保留要分离，成本未知不能编造。
9. **事实来源可扩展性**：EvidenceManager 来源等级写 FIELD_CONFIRMATION/REPLACEMENT；工业记录需定义来源与核实状态，不能机械把改造=现场。
10. **环境与历史漂移**：默认 Java17、.ps1 执行策略；默认 HTTP offline+openai；Actuator 已入库；旧工作树描述已失效。
11. **新依赖尚未验证**：Docker daemon、PG/pgvector、embedding 提供商、PDF 解析器、中文检索质量未验证。按 phase 实测，不先加新基础设施。
12. **避免重复编排**：Runtime 选型只做小原型比较，选定后保留一套主循环；兼容 Adapter 不等于长期双 Core。

