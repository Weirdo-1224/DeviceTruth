# Phase 5 验收结果汇总（05-02 评测矩阵 + 05-01 复验）

> 记录日期 2026-11-13。所有结果均为本轮实际执行；未执行项单列，不以历史报告冒充。
> 原始证据：`outputs/validation/v1-e2e-*`（评测）、`outputs/validation/sse-browser-*`（SSE/浏览器）、`outputs/validation/deployment-*`（部署演练）。

## 1. 14 例 E2E 矩阵（`scripts/verify_v1_e2e.py`，读冻结 v1-cases）

### 离线口径（scripted 推理器 + deterministic embedding + 隔离临时库，SIMULATED 数据）

批次：`outputs/validation/v1-e2e-7ed09d998f82/results.json`（E2E-07 边界澄清后重跑，脚本退出码 0）

| 用例 | 状态 | 证据要点 |
| --- | --- | --- |
| E2E-01 无故障码入口 | PASS | 入口接受自由描述，进入 WAITING（追问），不拒答、不确诊 |
| E2E-02 普通知识问答 | PASS | 5 条引用，定位含“直流快充中断”章节且版本为 2026.2 |
| E2E-03 设备信息不足 | PASS | 未确认设备时不输出确诊 |
| E2E-04 已知 CAR-B 模拟场景 | PASS | 产品 run 完成，数据性质 SIMULATED；CLI B 由 verify_cli 覆盖 |
| E2E-05 工业多来源冲突 | COVERED_BY_JUNIT | 默认夹具多来源与未确认经 HTTP 核对；三来源冲突变体由 DualDomainAgentTest 导入变体断言（HTTP 无快照导入接口） |
| E2E-06 上传资料与 PDF | NOT_RUN | PDF 分支实测 PASS（202→OCR_REQUIRED/PDF_NOT_SUPPORTED、documentId null）；Markdown 2026.2 版本定位依赖真实语义检索，离线确定性向量未命中 |
| E2E-07 多轮补证 | PASS | 分支 WAITING_THEN_REPLY：首次 WAITING，补答沿用同一 run 到 COMPLETED，引用 4 条可回读（边界澄清见 UAT） |
| E2E-08 取消/恢复/重启 | COVERED_BY_JUNIT | AgentLifecycleTest（取消/超时不复活）、RunRecoveryTest（租约回收/RUN_RECOVERED） |
| E2E-09 跨身份授权 | PASS | 另一 owner 读资产/run 均 404，不暴露存在性 |
| E2E-10 时点与现场核验 | COVERED_BY_JUNIT | 变体 queryPoints 断言 CONFLICT→NONE、FIELD_CONFIRMED（DualDomainAgentTest） |
| E2E-11 模型/工具故障 | COVERED_BY_JUNIT | 错误原因码稳定、无原始异常/栈字段（GeneralAgentApiTest/FlowTest） |
| E2E-12 高风险边界 | NOT_RUN | 离线 scripted 固定知识模板无法产生高风险答案；拒绝路径由 AnswerGuardTest 25 项覆盖 |
| E2E-13 双域按 run 选 Skill | NOT_RUN | 离线 scripted 追问态未选第二个 Skill；双域选择由 DualDomainAgentTest 与 04-02 dual-domain 套件覆盖 |
| E2E-14 真实模型完整链路 | NOT_RUN | 需在线供应商（见在线段） |

离线汇总：PASS 6、COVERED_BY_JUNIT 4、NOT_RUN 4、FAIL 0。

### 在线口径（真实供应商，仅发送冻结评测语料，2026-11-13 实跑）

批次：`outputs/validation/v1-e2e-81d4d43797d2/results.json`（embedding=real / reasoner=openai，DeepSeek `deepseek-flash` + DashScope `text-embedding-v4`）
汇总：**PASS 9、COVERED_BY_JUNIT 4、FAIL 1**

| 用例 | 状态 | 说明 |
| --- | --- | --- |
| E2E-01 | PASS | 真实模型按入口接受自由描述并追问，不确诊 |
| E2E-02 | PASS | 真实语义检索命中 2026.2 直流快充中断章节（引用 5 条） |
| E2E-03 | PASS | 缺设备信息时追问（asked=true），不输出确诊 |
| E2E-04 | PASS | CAR-B 模拟场景 run 进入追问/继续链路 |
| E2E-06 | PASS | Markdown 版本定位 + PDF 明确状态（OCR_REQUIRED、documentId null）双分支 |
| E2E-07 | **FAIL** | 真实模型首轮直接完成（COMPLETED），没有产生 WAITING_FOR_USER/追问；多轮同 run 边界未满足（历史轮次已记录同类现象，不因此改断言） |
| E2E-09 | PASS | 跨身份 404 |
| E2E-12 | PASS | 高风险答案 risk=HIGH、disposition=REQUIRES_QUALIFIED_PERSONNEL，无操作步骤 |
| E2E-13 | PASS | 同一会话不同 run 分别选中 automotive-diagnosis / factory-equipment |
| E2E-14 | PASS | 完整链路形成结构化答案并可回读引用（数据性质 SIMULATED） |

未在在线批次覆盖的 05/08/10/11 为 JUnit 变体/状态机断言（COVERED_BY_JUNIT），原因见离线段。

## 2. 05-01 复验轮（SSE/历史/时间线）

| 验收 | 结果 | 证据 |
| --- | --- | --- |
| AgentStreamTest（渐进可见性/容量隔离/并发回收/空值双路径/恢复去重 + 原 7 项） | 12/12 | `mvn -o '-Dtest=AgentStreamTest' test` |
| AnswerGuardTest | 25/25 | `mvn -o '-Dtest=AnswerGuardTest' test` |
| GeneralAgentFlowTest | 19/19 | 含危险追问负例 |
| KnowledgeImportTest | 8/8 | 含 PDF 明确状态 |
| 浏览器 A–E 场景 | 全 PASS | `outputs/validation/sse-browser-*`（A 实时含 RUN_WAITING；B 断点 5 重连补齐 1..8；C/D/E 隔离与永久失败） |
| 前端单测/构建 | 36/36 + build | Vitest / vue-tsc |

## 3. 在线 E2E 记录（待本轮命令返回后如实填写）

- 供应商：DeepSeek（`OPENAI_BASE_URL=https://api.deepseek.com`，模型 `deepseek-flash`）+ DashScope embedding（`text-embedding-v4`）。
- 发送内容：仅 `datasets/eval/v1-cases.json` 的冻结评测语句与固定语料，无个人信息；密钥只存在于本机环境/私有配置，未写入仓库或报告。
- 批次目录：`outputs/validation/v1-e2e-*`（在线段单独一批）。

## 4. 未验证项（保留）

- 真实设备/现场核验：全部为模拟数据（SIMULATED），不构成现场事实。
- 多人共享部署认证：服务器固定本地身份，真实认证与跨身份 HTTP 负例未实现（05-03 记录 NOT_RUN）。
- 订阅容量压测、SSE 更快断线感知（心跳语义）、PDF OCR/版面解析：未实现。
