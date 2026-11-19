# Phase 5 VERIFICATION（must-haves 对照）

| 计划 must-have | 证据 | 结论 |
| --- | --- | --- |
| 05-01：运行中能看到真实检索/工具/等待进度 | `AgentStreamTest.progressiveToolEventIsVisibleOnHistoryAndLiveStreamBeforeSecondModelCall`（阻塞第二次模型调用前，历史端点与 SSE 均已见 RUN_TOOL）；浏览器场景 A（实时 1..8，含 RUN_WAITING） | 通过 |
| 05-01：断线后按 seq 恢复历史并去重 | `AgentStreamTest.reconnectWithLastEventIdBackfillsMissedEventsInOrderExactlyOnce`（`Last-Event-ID=5` 补齐 6..8 各一次）；浏览器场景 B（代理侧取证 `Last-Event-ID: 5`） | 通过 |
| 05-01：实时和历史事件均不暴露 Prompt/密钥/原始异常 | `SafeAgentEventViews` 白名单逐字段过滤 + null 省略；`AgentStreamTest.publicEventsStayWithinWhitelistAndContainNoSecrets`；前端 onerror 只上报错误对象不展示正文 | 通过 |
| 05-01：不占普通请求线程、容量与清理 | SseEmitter + 专用有界线程池；全局 503、单 run 409、TTL；`globalStreamCapacityRejectsThirdConnectionButProductApiStaysAvailable`、`concurrentOpenCloseFullyReclaimsStreamCapacityAcrossRuns` | 通过 |
| 05-02：用户能查看事实/可能原因/建议/人工事项和来源对照 | `DiagnosisReport.vue`、`EvidenceComparison.vue` + 36/36 前端测试；`verify_v1_e2e.py` E2E-05/09/10 资产接口实测 | 通过 |
| 05-02：14 个 E2E 有逐项结果与复现证据（含 E2E-07 分层记录） | `ACCEPTANCE-RESULTS.md`：离线 PASS6/COVERED4/NOT_RUN4；在线 PASS9/COVERED4/FAIL1（E2E-07 真实模型直接作答） | 通过（含如实 FAIL 记录） |
| 05-02：检索与 Agent 质量/效率指标能在固定语料上比较 | `v1-cases.json` 冻结 + 离线/在线两批同脚本执行、逐例证据；检索版本/章节断言（E2E-02） | 通过（质量指标以用例状态计，不以 RESOLVED 比例代替） |
| 05-03：Compose 可启动 PG/后端/前端产品并保存历史 | `verify_deployment.ps1`：容器健康、产品链路完成、重启后会话/引用一致 | 通过 |
| 05-03：服务重启与独立备份恢复后引用/报告仍可读 | 备份→独立库 6 表行数一致；forward-fix 指向恢复库后旧会话与引用可读 | 通过（文件卷跨实例恢复 NOT_RUN） |
| 05-03：CLI/Server 启动入口和多人部署权限边界清楚 | README/operations.md 入口说明；operations.md §5 明确固定本地身份与多人 NOT_RUN | 通过（多人模式 NOT_RUN，见 UAT） |

## 阶段出口判断

- 技术缺口：无未修复的自动化失败（在线 E2E-07 为产品行为边界差异，记录而非掩盖）。
- 人工确认：见 [05-UAT](05-UAT.md) 两项；在得到回复前，VERIFICATION 保持 `human_needed`。
