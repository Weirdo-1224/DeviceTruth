# 验收记录

## 当前进度

任务 1.1–5.3 共 17/17 已完成。本文只登记实际运行结果；协议替身、真实数据库、外部真实模型和真实现场分别表述。

## 已执行验证

| 范围 | 命令或场景 | 结果 |
| --- | --- | --- |
| V18 与持久会话 | 临时独立 PG 上 `mvn -q clean '-Dtest=PersistentConversationTest' test`（当时 9 项） | 9 项通过，0 失败/错误/跳过；覆盖迁移、重启、初始对象和旧行回退 |
| 补答与 worker 恢复 | `GeneralAgentFlowTest#replyAssetSelectionSurvivesWorkerRestartAndReachesToolContext` | 1 项通过；结构化补答变更后模型与工具均得到新对象，重复旧补答被拒绝 |
| 答案对象与判断合同 | `AnswerGuardTest,GeneralAgentFlowTest` | 31 + 21 项通过，0 失败/错误/跳过；覆盖对象冲突/歧义、判断分类和旧 JSON UNKNOWN |
| 会话摘要真实 PG | `PersistentConversationTest#conversationSummariesSupportStablePaginationFiltersAndOwnership` | 1 项通过；22 个非空会话两页无重复，覆盖标题、设备、待答优先、筛选、参数与归属隔离 |
| 前端全部离线测试 | `cd frontend && npm run test -- --run` | 9 个文件、101 项通过，0 失败/跳过 |
| 前端类型检查 | `cd frontend && npm run typecheck` | 通过 |
| 后端最终全量 | `mvn -q test`（临时独立 PG） | 51 个 suite、408 项，0 失败/错误，4 跳过；legacy、恢复、归属和引用合同均通过 |
| 前端最终全量与构建 | `npm run test -- --run && npm run build` | 9 个文件、101 项全通过；`vue-tsc` 与 Vite 生产构建通过 |
| 真实模型对象场景 | `deepseek-flash`、真实 product/PG，先问资产再结构化补答 | Run `d198919a-c3f8-4f39-8755-941dba9e25a7` 最终 COMPLETED，`EV-DEMO-017/CONFIRMED`，3 个直接候选与 3 个上下文判断；任务卡和报告一致 |
| 真实模型多资产/无资产 | 两次 `get_asset`；不选初始设备 | 多资产 COMPLETED 且 `AMBIGUOUS`，未擅自选主设备；无初始资产显示未确认 |
| 桌面与移动浏览器 | 完成、待补答、失败及旧 Run | 结论和下一步先于判断列表；旧判断进入 UNKNOWN，不成为默认候选 |
| 独立报告 | 打开待补答报告并返回工作台 | 只展示真实状态与待答问题，不生成完成结论；返回正常 |
| Chromium 打印 | 无头 Chrome 模拟 print 媒体并 `Page.printToPDF` | 生成 4 页 PDF，含 12 条来源、长摘要、风险与 SIMULATED 声明；顶栏/按钮隐藏，未新增 Run |
| 电脑端完整链路 | 新草稿→上传 Markdown→提交→待补答→完成→报告/打印 | Run `993e225d-9343-4019-86e1-41bada74fef0` 完成；空草稿不建会话，第二次 Enter 被禁用状态阻止，刷新后恢复；生成 `draft-attachment-report.pdf` 并核对正文 |

打印证据：`outputs/validation/improve-pro-workbench-task-loop/completed-report.pdf`、`completed-report-utf8.txt`、`draft-attachment-report.pdf` 与 `draft-attachment-report-utf8.txt`。测试使用临时独立 PostgreSQL 容器和随机数据库；报告数据为 SIMULATED，不代表真实现场。

## 失败与修复历史

- 文档默认 PG 密码与当前实例不匹配，第一次 `PersistentConversationTest` 初始化失败；随后改用已有镜像启动临时独立容器，不读取私有配置、不触碰业务库。
- 一次增量编译产物出现构造器不一致，`clean` 重编译后恢复；后续关键回归均重新编译执行。
- 补答探针初次缺少工具 `purpose`，协议按预期拒绝；修正测试动作后通过，未放宽协议。
- 会话摘要初版 CTE/聚合连接返回空行，逐段对照后改为单查询窗口函数；游标又暴露别名不可用于 WHERE 的错误，修正后两页、筛选与归属测试通过。
- 内置浏览器阻止打印对话框；改用本机无头 Chromium 实际生成 PDF，未将阻止的对话框记为打印通过。
- 真模首次因 `KNOWLEDGE + CAUSE` 被过严校验拒绝，修正为只拒绝规格规定的矛盾组合，`AnswerGuardTest` 后重跑通过；未放宽引用、风险或安全门禁。
- 一次资产核验 Run 因持续检索超过 120 秒得到 TOTAL_TIMEOUT，另一次附件 Run 因不安全建议得到 UNSAFE_ANSWER，均保留为失败记录；随后用明确边界的结构化补答与安全候选场景完成验证，没有将失败 Run 算作通过。

## 范围与未验证项

- 按用户后续要求，不再追加手机端检查；此前已有移动浏览器结果保留，不作为本轮遗漏项。
- 真实现场设备、实车状态和实际操作步骤未验证；所有资产与观测仍按 SIMULATED 表述。
- 临时 PG 容器、无头 Chrome、浏览器标签和调试文件已清理；保留 PDF、文本和本记录作为验收证据。
