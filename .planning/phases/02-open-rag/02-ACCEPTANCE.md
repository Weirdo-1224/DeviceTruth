---
phase: 02-open-rag
status: complete_with_deferred_items
updated: 2026-11-11
---

# Phase 2 简洁验收报告

## 2026-11-12 用户决定带遗留项关闭

用户明确要求按现有范围关闭Phase 2：正文安全（02-02遗留3项P1和1项P2）、PDF/OCR/页码、浏览器实际E2E转后续，未完成及未测试不得记为通过。真实embedding原固定集三通道正向6/6、部件号2/2、定位6/6、必需负例全通过；真实DeepSeek同run追问、补答、补答后检索和章节引用已执行，整体安全验收NOT_RUN。此次最终修复真实PG针对性JUnit 33项：33通过、0失败、0错误、0跳过；Python 13项：13通过、0失败、0错误、0跳过。上一轮全量JUnit 241/0/0/0并非此次重新执行。原失败与修复历史见02-03-SUMMARY。

## 02-03 本轮（2026-11-11，独立Review修复后待复审）

用户决定以Markdown上传替代PDF。独立Review的补答附件上下文、旧导入/新上传关联和验收误判三项P1已修复，SUMMARY完成口径P2已修正。真实PG全量40 suites共241项，241通过/0失败/0错误/0跳过；`EmbeddingIntegrationTest`未显式opt-in，报告0项，真实embedding计NOT_RUN而非PASS。`GeneralAgentFlowTest`18项、`KnowledgeImportTest`8项、`KnowledgeSearchTest`14项、`KnowledgeUpgradeMigrationTest`2项均真实PG通过。Python脚本判定10/10通过，前端15/15及构建、打包与CLI通过。

固定12样本确定性合同实跑：VECTOR/KEYWORD/HYBRID正向均6/6、定位6/6，混合部件号2/2；版本、跨owner/project、空结果和伪造引用全部进入最终门槛并通过。三个真实供应商命令均退出2，缺embedding key，`BLOCKED_BY_ENV`；真实LLM与真实embedding没有通过证据。PDF、浏览器E2E保持NOT_RUN。PLAN未标DONE、完成计划数未增加、ROADMAP未推进，继续等待独立Review。

以下为02-01历史验收记录，保留不覆盖。

02-01独立复审通过；Phase2仅完成1/3计划，整体3/13计划、1/5阶段。02-02/02-03未执行、未授权，阶段不关闭。

| 本轮统计 | 总数 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| JUnit（35 suites） | 185 | 185 | 0 | 0 | 0 |
| 其中真实PG知识/升级/会话（已含于185） | 22 | 22 | 0 | 0 | 0 |
| 独立升级探针断言（不计入JUnit） | 9 | 9 | 0 | 0 | 0 |

打包成功；CLI脚本全部通过。原P1（含V3历史污染）及两项P2关闭，无未解决P0/P1/P2。原始失败与修复过程保留于SUMMARY及02-REVIEW。

实际链路：知识入口→服务器身份→MD/TXT切片→embedding→PG事务写入→按归属/当前档案/READY召回→本run引用登记→受权原文读取。历史不可信向量隔离后须新版本重建；旧引用仍可读取旧原文，不据此确认现场设备事实。

未验证：真实千问embedding/模型效果、PDF/混合检索、Agent/答案引用校验/Vue、真实文件字节存储、规模性能、M2恢复与真实现场。确定性/HTTP协议替身、真实PG和真实外部模型分别记录。

仅保留一次Phase2产品/设计集中确认，当前不要求用户重复操作技术测试，也不将待确认记为通过。见02-UAT与02-VALIDATION。
