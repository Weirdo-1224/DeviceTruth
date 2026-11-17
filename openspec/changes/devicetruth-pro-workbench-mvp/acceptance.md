# devicetruth-pro-workbench-mvp 验收记录

按 tasks.md 要求保留本变更的逐批验收记录；只记录实际执行的内容，未执行项如实记阻塞或未验证。

## 第 1 批（2026-11-17）：任务 1.1 D1 启动基线核验

### 环境与进程

- `mvn -version`：Apache Maven 3.9.9，Java 21.0.13.0.2（Microsoft，JDK 21 符合要求）。
- product 进程：docker compose 栈 `devicetruth-postgres-1`（healthy，127.0.0.1:54329）、`devicetruth-preview-backend-1`（127.0.0.1:8080）、`devicetruth-preview-frontend-1`（127.0.0.1:8081）。
  - 处置记录：preview-backend 原在 `devicetruth-preview_default` 网络，解析不到 `postgres` 主机名（postgres 在 `devicetruth_default`），Flyway 取连接失败而崩溃重启。已执行 `docker network connect devicetruth_default devicetruth-preview-backend-1`，随后后端健康检查通过、`/api/health` 正常。该处置只改容器网络归属，未改镜像与数据。
- PG/Flyway：`docker exec ... psql` 回读 `flyway_schema_history`，V1–V17 全部 success，最新 V17（agent execution checkpoint）；PG 16 + pgvector 运行中。
- 样本模型与数据性质：容器 `DEVICETRUTH_AGENT_REASONER=openai`（RUN_MODEL=openai，事件显示实际模型 `deepseek-flash`）；`DEVICETRUTH_EMBEDDING_PROVIDER=deterministic`（确定性向量替身，非真实 embedding）；身份 `local-owner/local-project`。本次回读的样本 Run `dataNature=SIMULATED`（模拟观测），属系统实际持久记录，不代表真实模型验收、不代表真实现场。

### API 合同回读（受权会话/Run，真实持久数据）

- `GET /api/health` → `{"status":"UP","dataNature":"SIMULATED"}`（健康探针仅辅助，未代替 Run 回读）。
- `GET /api/conversations` → 会话列表合同核对通过。
- `GET /api/conversations/{id}` → `conversation/messages/runs` 合同核对通过。
- 样本 Run `ca1ae174-37ff-4dff-be31-38bdbc6b33b0` / `227954ee-dcab-41c0-99a9-60f671ed8097`：COMPLETED，`category=POSSIBLE_CAUSES`，5 条 claims（含 `certainty=SUPPORTED`），9 条 references，`riskLevel=HIGH`，`safetyDisposition=REQUIRES_QUALIFIED_PERSONNEL`，`dataNature=SIMULATED` → AgentResult 合同核对通过（`category` / `claims[{text,referenceIds,certainty}]` / `references[{referenceId,sourceType,sourceId,sourceVersion,chunkId,title,page,section,excerpt,dataNature}]` / `summary` / `suggestions` / `uncertainties` / `limitations` / `riskLevel` / `safetyDisposition` / `dataNature`）。
- 样本 Run `9cc4c503-8345-47c9-a82f-3f3c545fe9de` / `8c8dc6ce-4fd3-4aef-879b-5a3e64473acf`：`category=KNOWLEDGE`、2 条 claims、0 引用；同会话另一 Run `a819fd66-6904-460c-98be-da91951b4006`：POSSIBLE_CAUSES、3 条 claims、0 引用（空引用结果形状）。
- 样本 Run `a6ebf642-7c6d-43bf-bf76-6e91a58e1f00` / `7ab91316-ad84-429d-917d-65fff3b044b3`：`status=WAITING_FOR_USER`，`pendingQuestion` 含 `pendingQuestionId/questionVersion/question/requestedFields`。
- `GET /api/runs/{id}/references` → 9 条引用，字段与合同一致（含 `page=null` 的缺失定位形状）。
- `GET /api/runs/{id}/events?after=0` → seq 递增（1..17），`RUN_TOOL`/`RUN_MODEL`/`RUN_ANSWER` 均为白名单安全字段（工具名、成败、耗时、引用计数；模型名、attempt、outcome、token）。
- `GET /api/runs/{id}/skill` → 200 `{skillId:"automotive-diagnosis",version:"1.0.0",devices,tasks}`。
- 产品错误合同沿用 `code/message/retryable/traceId`（api.ts 已有对应类型），未凭文档增加 requestId 字段。

### 未验证项（如实记录）

- 任务 1.2 双域（工业 SC-0245-B、汽车 CAR-B）真实授权资料：未开始。
- 真实 embedding：当前为 deterministic 替身，未验证。
- 真模 + 真实授权资料端到端调查：D4 里程碑，未执行。
- 真机/现场接入：未执行。
- ASK_USER 协议替身路径：未执行。

### 测试计数

本轮仅环境与 API 合同核验：业务测试执行 0（通过 0 / 失败 0 / 错误 0 / 跳过 0）。

## 第 1 批（2026-11-17）：任务 2.1 ViewModel 映射

### 修改文件

- 新增 `frontend/src/diagnosisViewModel.ts`：纯函数 `buildDiagnosisViewModel({run, goalMessage, references, events})`，按 design §2 把持久 Run 投影为界面对象。要点：仅 `category=POSSIBLE_CAUSES` 时判断区标题为“候选原因”（保留全部 claims 原顺序，含 SUPPORTED，不按 certainty 错分业务类别）；KNOWLEDGE→资料断言、CONFIRMED_FINDING→服务端核验结论、HUMAN_REVIEW→待人工核验；certainty 标签为 可能/资料支持/服务端已核验；claim 引用按 ID 与已取得引用连接，缺失给“引用暂不可用”、无引用给“当前无已关联引用”，不创建虚构来源；summary/suggestions/uncertainties/limitations 原样映射并标注“本轮”内容（roundFieldsNote），不生成概率、不推导已执行检查、不做判断级建议关联；riskLevel/safetyDisposition/dataNature 保留服务端值，AUTHORIZED 附“不代表已完成现场核验”提示；引用视图原样映射 title/sourceType/sourceId/sourceVersion/chunkId/page/section/excerpt/dataNature，缺页码不伪造、未知来源类型给“其他来源”安全标签、无 downloadUrl；事件仅映射 RUN_TOOL/RUN_MODEL 白名单安全字段（工具名/成败/耗时/引用计数、模型名/attempt/outcome/耗时），缺失不补写；选中键 `runId:claimIndex` 仅标识当前快照。
- 修改 `frontend/src/components/DiagnosisReport.vue`：语义修正——候选原因/资料断言分类改由 ViewModel 按 category 决定（原实现按 certainty===POSSIBLE 分类，会把 POSSIBLE_CAUSES 中 SUPPORTED 的判断错列到“已知事实”）；无引用判断显示引用说明；CONFIRMED 标签由“已核实”改为“服务端已核验”。
- 修改 `frontend/src/App.vue`：向 DiagnosisReport 传入 `:run="run"`（仅传参，无其他改动）。
- 新增 `frontend/src/diagnosisViewModel.test.ts`：10 条映射回归。

### 测试与构建（实际执行）

- `npm run test -- --run`：2 个文件通过，46/46 通过（新增 diagnosisViewModel 10 条 + 原 api.test.ts 36 条），失败 0、错误 0、跳过 0。
- `npm run build`（vue-tsc --noEmit + vite build）：通过。
- 覆盖验收点：POSSIBLE_CAUSES+SUPPORTED、KNOWLEDGE+POSSIBLE、空 claims（不伪造候选、保留摘要/风险/本轮建议）、缺引用（当前无已关联引用/引用暂不可用）、模拟/授权混合（逐引用 dataNature 保留）、选中键、事件白名单映射、run 为 null 不虚构。

### 数据性质与未验证项

- ViewModel 测试样本为手写模拟数据，不计入真模/真实资料验收；映射形状已按 1.1 回读的真实持久 Run（227954ee-…，POSSIBLE_CAUSES/5 claims/9 references/SIMULATED）合同核对。
- 真实模型端到端、双域真实授权资料、真实 embedding、浏览器实测：均未执行（属 1.2/2.2 及后续批次）。
- 页面骨架 `ProfessionalDiagnosisPage.vue`（任务 2.2）未开发，按批次范围停止。
