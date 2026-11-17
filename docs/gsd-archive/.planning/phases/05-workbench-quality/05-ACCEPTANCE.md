# Phase 5 阶段验收（完整工作台、质量与交付）

> 2026-11-13。三个计划（05-01、05-02、05-03）均已实施并技术验收；本文件汇总测试统计、关键问题与未验证项，供审阅。
> 详细证据见各计划 SUMMARY、`ACCEPTANCE-RESULTS.md` 与 `outputs/validation/`。

## 1. 本轮实际执行的测试统计（按类别分别统计，不相加）

### Java（真实临时 PostgreSQL，`mvn -o test`，本轮批次）

- **总计：360 项，通过 359，失败 0，错误 0，跳过 1**（跳过为历史既有项；`mvn -o test` EXIT=0，BUILD SUCCESS）。
- 关键类：`AgentStreamTest` 12/12（05-01 复验新增 5 项：渐进可见性、全局容量隔离、并发回收、空值双路径、恢复去重）、`AnswerGuardTest` 25/25（安全四项正负例）、`GeneralAgentFlowTest` 19/19（含危险追问负例）、`KnowledgeImportTest` 8/8（PDF 明确状态）、`GeneralAgentApiTest` 7/7、`AgentLifecycleTest` 17/17。

### 前端（Vitest + vue-tsc）

- 单测 **36/36**；`npm run build` 通过。

### 浏览器端到端（真实 Chrome + 打包产品 + 真实 PG）

- `scripts/verify_sse_browser.py` 五场景全 PASS：A 实时时间线（含 RUN_WAITING）、B 断线按 `Last-Event-ID` 续传补齐且各一次、C 会话切换隔离、D 无 run 会话清理、E 永久失败后刷新走历史回读。

### 评测矩阵（`scripts/verify_v1_e2e.py`，冻结 14 例）

- 离线（scripted + deterministic，隔离库）：PASS 6、COVERED_BY_JUNIT 4、NOT_RUN 4、FAIL 0。
- 在线（DeepSeek + DashScope 真实供应商，仅发送冻结语料）：**PASS 9、COVERED_BY_JUNIT 4、FAIL 1**（E2E-07 真实模型首轮直接完成而未追问，多轮同 run 边界未满足，如实记录、未改断言）。

### 旧 CLI 与打包

- `python scripts/verify_cli.py` 全通过（A–D、缺密钥 FAILED 不降级、私有配置）。
- `mvn -q -o -DskipTests package` 成功。

### 部署与恢复（Compose + 真实 PG + 临时卷）

- `scripts/verify_deployment.ps1`（**2026-11-14 完整批次 deployment-20261114014426，result.json status=PASS**）：镜像构建 → compose 起 PG/后端/前端（`/api/health` 200、nginx `/api` 200）→ 手册导入 201 → 产品链路（会话→WAITING→补答→COMPLETED，引用 1）→ 重启 backend+postgres 后会话/引用一致 → `pg_dump`→`devicetruth_restore` 6 张表行数逐一相等（dt_agent_run=1、dt_run_event=8、dt_message=4、dt_evidence_reference=1、dt_uploaded_file=1、dt_knowledge_document=1）→ 篡改 flyway 校验和后启动失败且 40s 健康探测全失败 → forward-fix 指向恢复库后服务恢复、旧会话/引用可读。
- 演练中修复 4 个环境问题：镜像缺 skills/datasets、容器只绑回环、postgres profile、PowerShell 编码；失败与复验记录保留在本轮对话与 drill.log。
- 结果与日志：`outputs/validation/deployment-20261114014426/`。

## 2. 关键问题与修复（保留失败与复验历史）

| # | 问题 | 处置 |
| --- | --- | --- |
| 1 | 05-01 审查 P1×2/P2×4：实时进度延迟、SSE 占请求线程、订阅计数竞态、空值 NPE、onerror 未绑定、无 run 会话残留 | 全部结构性修复；契约 §8；`AgentStreamTest` 12/12、前端 36/36、浏览器 A–E PASS |
| 2 | SSE 错误体走 `@ExceptionHandler` 时内容协商 406 升级 500 | 控制器手写 JSON 错误体（复验实测 6 项用例红→绿） |
| 3 | 并发容量用例被心跳感知窗口自我堵塞 | 用例改单连接探针排水 + 16s 退避；产品心跳语义不变 |
| 4 | 02-02 复审保留的 P1×3/P2×1 | AnswerGuard 逐子句 + 完成时态断言 + 否定软化 + 资质边界名词性列举；ASK_USER 追问过同一安全边界；25/19 项测试 |
| 5 | PDF 无明确状态 | 上传登记后 `OCR_REQUIRED / PDF_NOT_SUPPORTED`，无正文/页码/文档，重试保持 |
| 6 | 部署镜像缺 `skills/`、`datasets/` 导致 SkillRuntime 启动失败 | Dockerfile 复制两个目录（演练第一轮实测暴露） |
| 7 | 容器内 Tomcat 只绑回环（`server.address=127.0.0.1` 开发默认）导致端口映射/nginx 不可达 | compose 显式 `SERVER_ADDRESS=0.0.0.0`，保留本地开发默认 |
| 8 | compose 中 postgres 的 profile 不随 `product` 启用 | profiles 合并为 `[standalone-db, product]` |
| 9 | 部署演练脚本 PowerShell 5.1 以 ANSI 读无 BOM 脚本导致中文截断解析失败 | 脚本改 UTF-8 BOM；演练补手册导入（scripted 流程需要可检索语料） |

## 3. 未验证项（NOT_RUN，附阻塞条件与影响）

1. **多人共享部署的真实认证**：当前为服务器固定本地身份；没有身份提供方、认证装配与跨身份 HTTP 负例。影响：只能作为同机演示部署证据，不能宣称多人权限模型可用。
2. **文件卷跨实例恢复**：演练对命名卷做了打包命令与文档记录，但未在第二套实例上重新挂载验证；数据库恢复与重启持久已实测。
3. **PDF/OCR/版面解析**：只提供明确状态；没有正文抽取、页码定位能力。
4. **SSE 更快断线感知与订阅压测**：断线由心跳写失败感知（最长约两个 15s 周期）；未做大规模并发压力测试。
5. **真实设备/现场核验**：评估数据均为 SIMULATED；系统当前没有把知识资料升级为现场事实的通道。
6. **资产冲突变体（三来源冲突、时点切换）**：产品 HTTP 无资产快照导入接口，由 JUnit 变体用例覆盖（COVERED_BY_JUNIT），未在浏览器/脚本层重放。

## 4. 阶段出口

- 三个计划的 must-haves 均有本轮实测证据；无 FAIL 遗留的自动化用例（在线 E2E-07 为产品行为边界记录，属已知现象）。
- 需人工判断的事项：① 多人共享部署是否在本轮之后排期接入真实认证；② E2E-07 的“首轮必须追问”是否作为产品硬约束（当前模型可直接作答）。两项在 UAT 集中确认。
