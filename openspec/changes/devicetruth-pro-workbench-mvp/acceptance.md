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

## 第 2 批（2026-11-17）：任务 2.2 专业页骨架

### 修改文件

- 新增 `frontend/src/components/ProfessionalDiagnosisPage.vue`：专业诊断工作台单页（无路由框架、无全局 store）。顶部任务区渲染目标消息问题、selectedAssetId（无设备显示“未关联”）、状态、风险、dataNature、领域包 Skill 及版本；判断区按 ViewModel 的 claimsTitle（候选原因/资料断言/服务端核验结论/待人工核验）列出全部判断，含 certainty 标签与真实引用连接，无引用显示“当前无已关联引用/引用暂不可用”；中央详情区渲染本轮摘要、受控安全提示、本轮建议/不确定点/限制（标注“本轮”）与本轮已取回资料清单；底部状态条提示普通文字继续调查沿用 App 输入框。FAILED/CANCELLED 走安全展示分支。无个人模式切换、无关系图入口、无深入调查按钮（属 5.1）。
- 修改 `frontend/src/App.vue`：报告区由 DiagnosisReport 换成 ProfessionalDiagnosisPage（传入 run/goalMessage/references/skill/failureText，toggle-reference 复用现有依据面板）；会话入口、SSE 时间线、补答、取消、输入框全部沿用，未建第二套请求/订阅控制器。DiagnosisReport.vue 保留在仓库（后续批次可复用摘要/风险显示），当前不再被 App 引用。

### 测试与构建（实际执行）

- `npm run test -- --run`：46/46 通过（无新增失败）。
- `npm run build`（vue-tsc --noEmit + vite build）：通过。
- 部署：`docker compose build frontend && docker compose --profile product up -d frontend`，8081 服务新包（index-BpDyvKRL.js）核验一致。
- 浏览器核验（Playwright + Chrome，脚本 `scripts/verify_pro_page_22.py`，截图存 `outputs/validation/pro-page-2.2*.png`）：
  - 真实 Run ca1ae174…/227954ee…（COMPLETED/POSSIBLE_CAUSES/5 claims）：顶部任务区、候选原因（5）、本轮摘要、本轮建议/不确定点/限制、设备 chip、状态、已取回资料清单全部渲染；无“个人模式”“关系图”“深入调查”假入口——12/12 断言通过。
  - 会话 9cc4c503…（最新 Run 为 POSSIBLE_CAUSES/3 claims/0 引用）：无引用判断显示“当前无已关联引用”，普通文字继续调查入口（输入框 placeholder 实际存在）保留。
- 限制：真实样本中“最新 Run 为 KNOWLEDGE/空 claims”的浏览器场景不存在（KNOWLEDGE 轮 8c8dc6ce… 是会话首轮回，非最新轮），该分支由 ViewModel 单测覆盖（KNOWLEDGE→资料断言、空 claims 不伪造候选），浏览器级证据留待 7.1 双域验收时以新 Run 补拍。

## 第 2 批（2026-11-17）：任务 1.2 双域资料准备

### 资料导入（现有 `/api/files` 通道，未建新导入器）

经 `POST /api/files`（multipart，title/version/dataNature=AUTHORIZED）导入 4 份授权资料并异步解析全部 PARSED：

| 域 | 文件 | 登记标题 | documentId | 切片 |
|---|---|---|---|---|
| 汽车 | `skills/automotive-diagnosis/references/charging-troubleshooting.md` | 充电系统故障排查指南（automotive-diagnosis） | 3603a786-…-b3eebad05cc9 | 8 |
| 汽车 | `datasets/knowledge/charging-system-manual.md` | 充电系统维修手册 | 3b8ec73f-…-8a28f01e9481 | 6 |
| 工业 | `skills/factory-equipment/references/controller-maintenance.md` | PLC控制器维护手册（factory-equipment） | ea79a213-…-ce16a613364b | 9 |
| 工业 | `skills/factory-equipment/references/domain-knowledge.md` | 产线控制领域知识（factory-equipment） | cebf52f7-…-09b5c27adfce | 4 |

`GET /api/knowledge/documents/{id}` 回读确认 title/docVersion=1.0/dataNature=AUTHORIZED/切片数，归属 owner=local-owner 范围。

### 端到端调查（真实模型 openai→deepseek-flash，RUN_MODEL=deepseek-flash）

- **汽车 EV-DEMO-004**：会话 c43d3372-…，Run `0de8aaaa-7337-4622-a9e2-a4eee6258f7a` COMPLETED，POSSIBLE_CAUSES/4 claims。Skill=automotive-diagnosis v1.0.0；工具 activate_skill→get_asset→search_knowledge→get_document_excerpt→get_observations，无工具错误。引用 10 条：1 条 KNOWLEDGE_DOCUMENT《充电系统维修手册》AUTHORIZED（章节定位“…/ 2 交流慢充无法启动”）+ 9 条 SIMULATED 模拟观测（明确标示）。
- **工业 SN-0245**（首次 Run a6200c43-… FAILED，原因码 MODEL_OUTPUT_INVALID_AFTER_RETRY，如实记录）：
  - 重试 Run `0de22846-7dcb-43fa-b529-fb48f1abd3fd` COMPLETED，HUMAN_REVIEW/5 claims。Skill=factory-equipment v1.0.0；工具 activate_skill→get_asset→get_configuration→get_maintenance_history→search_knowledge→read_skill_reference→get_configuration→get_observations。引用 4 条：含 KNOWLEDGE_DOCUMENT《PLC控制器维护手册（factory-equipment）》AUTHORIZED（章节定位“控制器配置与维护要点（指引资料）”）。
  - 另一重试 Run 2fd6d931-… COMPLETED，POSSIBLE_CAUSES/4 claims；模型明确报告“多轮检索未命中《控制器维护手册》”，仅引用 SIMULATED 台账/观测，未伪造手册引用。

### 核对结论与未验证项

- Skill 自动选择正确（两域各命中对应领域包）；RAG 命中可登记引用并带章节定位；模拟观测标记保持 SIMULATED，与授权渠道文档分列；答案整体 dataNature=SIMULATED 如实保留。
- **重要更正与口径确认（2026-11-17）**：上述 4 份资料为仓库既有的**项目自制演示手册**，文件自身标注“SIMULATED（演示资料，不能作为真实维修依据）”。上传通道的 dataNature=AUTHORIZED 仅表示 owner 授权入库的渠道语义。**经用户确认，比赛接受演示知识库口径**：1.2 按“真实模型 + 演示知识库授权资料”验收通过（任务已勾选），真实厂商授权资料不再作为本变更验收条件；演示资料不得冒充真实资料对外宣称。
- **真实 embedding 未验证**：当前为 deterministic 替身，工业域查询出现跨域噪声（充电手册命中工业查询，见 0de22846 引用及 2fd6d931 的未命中报告），相关性与真实向量库效果的差距单列未验证。
- 命名说明：任务书“SC-0245-B/CAR-B”对应数据集 fixtures 名；实际种子资产 ID 为 SN-0245（工业）与 EV-DEMO-004（汽车），本批按实际资产执行。
- 未连接真机；ASK_USER 触发与否取决于模型，本批两轮均未触发（不伪造）。

## 第 3 批（2026-11-17）：任务 3.1 判断聚焦 + 3.2 证据抽屉

### 修改文件

- 新增 `frontend/src/claimSelection.ts`：判断选中纯状态模块（`createClaimSelection`：select/reset/sync），只改变查看焦点，无 fetch/模型调用面；Run 或结果替换时 sync 重置并默认选中第一条，无判断为 null，越界回退。
- 新增 `frontend/src/components/ClaimList.vue`：判断列表（类别标题 + 条数），点击仅 emit select。
- 新增 `frontend/src/components/ClaimDetail.vue`：选中判断原文、确定性标签、按 ID 连接的真实引用按钮（打开证据抽屉）、无引用说明（当前无已关联引用/引用暂不可用）。
- 新增 `frontend/src/components/EvidenceDrawer.vue`：右侧抽屉展示引用真实字段（标题、来源类型+受控标签、来源 ID、来源版本、切片、定位[章节或页码，缺失显示“未提供页码或章节”]、数据性质、摘录），附 AUTHORIZED/FIELD_OBSERVATION 语义提示；点击遮罩或关闭按钮收起，不改变判断选中（回到原焦点）。
- 修改 `frontend/src/components/ProfessionalDiagnosisPage.vue`：判断区/详情区改用 ClaimList+ClaimDetail，选中状态经 claimSelection；证据抽屉页面内自管；FAILED/CANCELLED 分支新增“本轮已取回证据”列表（可开抽屉）。
- 修改 `frontend/src/App.vue`：引用加载改为 watch——有结果用 result.references；失败/取消轮无结果时经 `readReferences` 回读已登记证据（迟到响应按 runId 丢弃）；不再向页面转发 toggle-reference（抽屉内聚）。

### 测试与构建（实际执行）

- 新增 `frontend/src/claimSelection.test.ts` 5 条：初始/sync 默认第一条/无判断 null、切换两条判断只改下标、数量收缩越界回退、判断-引用按序连接（含选中键）、缺引用“引用暂不可用”不造虚构来源。
- `npm run test -- --run`：3 文件 **51/51 通过**（原 46 + 新增 5）。`npm run build` 通过；frontend 镜像重建部署 8081。
- 浏览器核验（`scripts/verify_pro_page_31_32.py`，Playwright + Chrome，22/22 断言通过，截图 `outputs/validation/pro-page-3.1-3.2.png`、`pro-page-3.2-failed.png`）：
  - 3.1：类别标题“候选原因（5）”、默认选中第一条、点击第二/第一条详情切换与还原、**交互期间 POST 数=0**、无引用判断显示说明。
  - 3.2：抽屉打开含 来源类型/来源 ID/来源版本/切片/数据性质/摘录 字段；缺页码展示实际章节、无伪造页码；关闭后判断焦点保持；开关抽屉期间 POST 数=0。
  - 失败轮（会话 9641d9ab…/Run a6200c43…，FAILED）：安全失败卡片 + “本轮已取回证据”区（readReferences 生效，4 条已登记证据），证据可开抽屉、字段完整。

### 未验证项

- 用户自述（MessageView.content）不出现在引用与抽屉中（结构性保证：抽屉只渲染服务端引用），未单独造浏览器用例；留给 5.2/7.1 与补答流一并覆盖。
- 运行中/排队轮抽屉复用路径未在浏览器实测（当前页面仅终态渲染），失败轮已实测，覆盖 3.2 验收要点。

## 第 4 批（2026-11-17）：任务 4.1 状态同步 + 4.2 历史恢复

### 修改文件

- `frontend/src/components/ProfessionalDiagnosisPage.vue`：新增 QUEUED/RUNNING/WAITING_FOR_USER 的“调查进行中”状态卡（状态/数据性质/领域包 chips 与分流提示，区别于终态报告）；**修复第 3 批重写时丢失的“本轮摘要”结论块**（恢复 `.conclusion/.summary`，verify_pro_page_22.py 的既有断言重新成为守卫）。
- `frontend/src/App.vue`：
  - 4.2 轮次表：`runs[]` 保留 readConversation 全部 runs；`run` 改为计算值（`pinnedRunId` 固定查看轮 ?? 最新轮）；`upsertRun` 按 runId 覆盖/追加；刷新只在未固定或固定轮次失效时跟随最新，**刷新不强制覆盖主动选择**；新提交/补答清除固定并切到新轮；打开会话/新调查重置固定。
  - 活动轮与查看轮分离：取消按钮、`pendingQuestion`、补答三元组一律指向最新轮（`latestRun`）；查看历史旧轮不解除等待态约束、不误指向旧轮补答；历史轮只读。
  - 4.1 流收尾回读：`timeline.terminalStatus` 变化即 `refresh()`，SSE 只作通知，Run/消息/引用以 PG 回读为准。
  - 新增 `.run-picker` 轮次选择 chips（≥2 轮显示）。
- `scripts/verify_browser_recovery.py`：选择器适配专业页（`.pro-page .summary`、`.claim-detail .citation-ref`、`.evidence-drawer .drawer-excerpt`、`.conversation-link`、“新调查”）；修正消息折叠（补答进“查看前序对话”，改附着断言）、抽屉遮挡（先关闭再点新调查）、历史条目动态后缀（按标题文本过滤）。
- `frontend/src/api.test.ts`：新增 2 条——轮询在 CANCELLED 停止（取消是终态）；`readReferences` 失败/取消轮证据回读合同（URL 与字段原样）。

### 测试与构建（实际执行）

- `npm run test -- --run`：3 文件 **53/53**；`npm run build` 通过；frontend 镜像重建部署。
- Java 复用测试（临时隔离 PG `dt-test-pg:54331`，独立 schema 重建 Flyway V1–V17）：`mvn -q '-Dtest=AgentLifecycleTest,AgentStreamTest' test` → **17/17 + 12/12，失败 0 错误 0**。
- `scripts/verify_browser_recovery.py` 适配后实跑：**PASS**（`outputs/validation/browser-recovery-7968463c…/result.json`）——同 Run 补答、WAITING 跨刷新与跨浏览器+服务器重启存活、答案与引用刷新后存活、会话切换后历史恢复。限制：脚本自带独立 product 服务（scripted reasoner + deterministic embedding，协议替身路径），非真模。
- 4.1/4.2 浏览器核验（`scripts/verify_pro_page_41_42.py`，真模 deepseek-flash）：**11/11 断言通过**（`outputs/validation/pro-41-42-final3.log`、截图 `pro-page-4.1-4.2.png`）——轮次选择器多轮渲染且默认 active 最新轮；切第 1 轮显示 KNOWLEDGE 结果、选中态跟随；**刷新按钮不强制覆盖主动选择**；提交后“调查进行中”状态卡与终态报告区别明确；**执行中刷新恢复跟踪**；本轮 Run 真模进入 WAITING 追问时用既有补答路径沿同一 Run 恢复（轮次数不增加），流收尾后经服务器回读显示本轮摘要，新结果无旧轮内容残留。

### 未验证项

- 断流/重复 seq/旧事件隔离由 api.test.ts 与 timeline 现有 36+2 条回归覆盖，浏览器层未做断流注入（记录为单测覆盖）。
- 本地会话定位失效重选路径沿用 2.2 既有实现，本批未重复实测。

## 第 5 批（2026-11-17）：任务 5.1 深入调查 + 5.2 补答分流（D4 六项核心证据）

### 修改文件

- `frontend/src/api.ts`：`sendMessage` 兼容扩展第 6 个可选参数 `contextRefs`（每项仅 `{referenceId}`，无 `replyTo`，不传时请求体与旧合同一致）；新增纯函数 `selectContextRefs`（保持原顺序去重、上限 10、省略计数）与 `CONTEXT_REF_LIMIT`。
- `frontend/src/diagnosisViewModel.ts`：新增 `buildInvestigationMessage(claimText, category)`，按 design §3 生成用户可见正文（仅 POSSIBLE_CAUSES 称“候选判断（尚未确诊）”，其余类别统称“判断”）。
- `frontend/src/components/ProfessionalDiagnosisPage.vue`：完成轮新增“深入调查选中判断”按钮；继承清单=所选判断引用 ∩ 本轮已取得引用，按钮旁明示《标题》清单与省略数量，无引用也可调查；未选中/提交中/非完成态禁用，选择动作零提交。
- `frontend/src/App.vue`：新增 `investigateClaim`（组正文→sendMessage 无 replyTo 开新 Run、保留目标消息 selectedAssetId、切新轮且旧轮可回看）；`activityActive`（QUEUED/RUNNING）保留草稿但禁用发送与深入调查；补答遇 `STALE_OR_UNRELATED_REPLY/REPLY_TARGET_REQUIRED` 保留草稿、回读当前问题，不转普通新消息。
- `src/test/java/io/devicetruth/server/ResourceAuthorizationTest.java`：新增正向用例——继承引用在新 Run 以新引用 ID 重新登记（sourceId 一致、referenceId 全新）、旧 Run 引用不变、跨身份 404。
- `scripts/verify_browser_recovery.py`：抽出 `serve_frontend`（供复用），并修复代理绑定旧 server 的缺陷（服务重启后页面请求打到已关闭实例，改为 `lambda` 动态取当前实例）。
- 新增 `scripts/verify_investigation_roundtrip.py`：D4 真模浏览器验收脚本（六项核心证据自动化）。

### 测试与构建（实际执行）

- `npm run test -- --run`：3 文件 **60/60**（新增 9 项：contextRefs 请求体/无 replyTo/旧合同兼容/去重/上限/省略/空清单/深入调查正文口径）；`npm run build` 通过。
- `mvn -q '-Dtest=ResourceAuthorizationTest,AgentLifecycleTest,AgentStreamTest' test`（真实测试 PG）：**38 项，失败 0/错误 0/跳过 0**。
- D4 真模浏览器验收（`verify_investigation_roundtrip.py`，隔离库 `devicetruth_d4_eval`，真模=DeepSeek）两轮均 PASS：
  - 确定性 embedding 轮（`investigation-roundtrip-bd6b5331`）：run1 3 判断 1 引用 COMPLETED；选择判断前后 Run 数恒为 1（零提交）；深入调查新 runId、继承来源、引用 ID 全新；旧轮回看摘要一致；现场补充作为用户报告开第 3 Run；**真模实际触发 ASK_USER**，三元组补答恢复同一 runId、Run 总数不变、补答后 COMPLETED。
  - 真实 embedding 轮（`investigation-roundtrip-fca4680d`，官方 DashScope text-embedding-v4/1024 维）：同流程 PASS（run1 5 判断 5 引用，语义召回优于确定性替身）；本轮 ASK_USER 未触发（采用上一轮真模触发证据）。
- `verify_browser_recovery.py` 重构后回归：PASS（协议替身路径：同 Run 补答、刷新/重启恢复、历史回看）。

### 数据性质与未验证项

- 真模=DeepSeek（RUN_MODEL 经事件回读）；演示知识库口径按 1.2 修订执行；模拟观测 SIMULATED 标记保持。
- 环境更正：`.env` 的 `DASHSCOPE_API_KEY` 为旧失效密钥（网关仅 /models 只读，chat/embeddings 一律 400）；服务器实际读 `devicetruth.local.properties` 的新官方密钥，真实 embedding 已实测通过。`--reasoner=qwen` 路径待 `.env` 同步新密钥后重验。

## 第 6 批（2026-11-17）：任务 6.1/6.2 QUEUED 周期补派（P1-04 修复）

### 修改文件

- `AgentRunRepository.queuedPage(identity, limit)`：与启动全量 `queued()` 同语义的有界候选（created_at,id 排序 + LIMIT），供周期扫描。
- `RunRecoveryService`：新增 `reconcileQueued`（周期补派）——扫描层与单 run 调度层各自捕获异常（一次失败只记日志，下一周期重试）；TOTAL_TIMEOUT 判定沿用启动对账规则；不重置预算。
- `GeneralAgentService`：`pendingDispatch` 进程内待执行去重集合（任务开始/被拒绝时释放；不替代 markRunning 的 version CAS）；周期任务合并“过期 RUNNING 回收 + QUEUED 补派”并整体捕获异常（`scheduleWithFixedDelay` 遇未捕获异常会永久取消后续执行，这是本次修复的关键点）；新增 `recoveryScanLimit`（默认 50）。
- `ProductConfiguration`：注入 `devicetruth.agent.recovery-scan-limit`（默认 50）。

### 测试（真实测试 PG）

- `mvn -q '-Dtest=RunRecoveryTest' test`：**22 项，失败 0/错误 0/跳过 2**（真模用例离线跳过）。新增 8 项：周期补派接手滞留 QUEUED（不重启、不再调 recoverQueued）；临时调度失败保持 QUEUED 且下一周期补派；**真实 PG 故障注入**（`dt_agent_run` 临时改名一个租约周期）后周期继续；worker 关闭不调度（M0 合同）；双进程抢占仅一次有效领取（attempts=1、目标消息不重复）；有界扫描（上限 2，三个 QUEUED 三轮排空）；预算不重置（预置 steps=2/activeDuration=1500 后补派执行，终值单调不减）。
- `mvn -q '-Dtest=RunRecoveryTest,AgentLifecycleTest,AgentStreamTest' test`（6.2 规定命令）：合计 **51 项，失败 0/错误 0/跳过 2**；过期 RUNNING 恢复、WAITING/终态不被误执行等原回归继续通过。

### 可靠性说明

- 启动全量对账合同不变（`reconcile` 仍走无界 `queued()``）；执行资格最终由 `markRunning` 的 version CAS + 租约保证，`pendingDispatch` 只防重复入队。
- 不重派 WAITING/终态（SQL 只扫 `status='QUEUED'`），不重置预算（补派路径不写 budget）。
- 未新增消息队列、状态机或运行时框架。

### 未验证项

- `--reasoner=qwen` 在线验收待 `.env` 同步新密钥。
- 7.1 双域演示、7.2 冻结、8.x P1 可选：未开始。

## 第 7.1 批（2026-11-17）：任务 7.1 双域集成验收

### 复用回归（真实测试 PG）

- `mvn -q '-Dtest=DualDomainAgentTest,SkillRuntimeIntegrationTest,AgentToolFeedbackTest,KnowledgeSearchTest' test`：DualDomain 4/4、SkillRuntime 3/3、KnowledgeSearch 15/15、AgentToolFeedback 全类通过，失败 0/错误 0。
- 真模定向 `mvn -q '-Ddevicetruth.test.real-agent=true' '-Dtest=RunRecoveryTest#realModelUsesReplyToProposeNewSearch+realModelReadsLongAssetReferenceAfterReplyAndWorkerRestart,AgentToolFeedbackTest#realModelProposalsExerciseReuseRefreshAndBoundedSummary' test`（DeepSeek）：3/3 通过。

### 双域真模浏览器演示（`scripts/verify_dual_domain_demo.py`，隔离全新库，真实 embedding）

- 演示知识库 4 份（automotive-diagnosis×2、factory-equipment×2，渠道 AUTHORIZED、文件自身 SIMULATED 演示标注，1.2 修订口径）；夹具 SC-0245-B/CAR-B；真模 DeepSeek。
- **汽车域** Run `ba681900-…` COMPLETED：6 判断 5 引用；Skill=ev-charging-triage；工具 activate_skill→read_skill_reference→search_knowledge；**真模触发 ASK_USER**，页面问题区补答沿同一 Run 恢复后完成。
- **工业域** Run `28d1322b-…` COMPLETED：5 判断 4 引用；Skill=industrial-configuration-investigation；工具 activate_skill→get_asset→get_configuration→get_maintenance_history→get_observations→read_skill_reference（设备工具实际执行）；关联资产 SC-0245-B。
- 浏览器覆盖：刷新后结果与引用仍在（refreshSurvives）；执行中取消轮 Run `7dff1d97-…` CANCELLED（取消卡片展示）；ASK_USER 协议替身路径由 `verify_browser_recovery.py` 本会话 PASS 单列。
- result.json：`outputs/validation/dual-domain-2d6809f5/`。

### D4 六项核心证据复核

第 5 批 `investigation-roundtrip-bd6b5331`（含真模 ASK_USER 触发与同 Run 补答、深入调查新 Run 与引用继承）+ 本批双域结果共同满足：两域成功结果、真实后续调查、同 Run 补答。

### 数据性质分列与未验证项

- 真模=DeepSeek（RUN_MODEL 经事件回读）；真实 embedding=text-embedding-v4 已实测；模拟观测保持 SIMULATED（工业域 dataNature=SIMULATED，汽车域答案 dataNature=AUTHORIZED 来自授权渠道演示资料）；协议替身（scripted+deterministic）仅用于恢复回归，不冒充真模。
- 未连接真机，不声称现场验收；`.env` 旧 DashScope 密钥待同步（`--reasoner=qwen` 未重验）。
- 遗留风险记录：P1-05 未提交末步结果窗口仍在（仅冒烟，见 design §6）；P1-03 RAG 事务错误路径未修（8.2 可选）。
