# 验收记录：stabilize-pro-workbench-interactions（R0-A）

日期：2026-11-18。制品：仓库根目录构建出的 `frontend/dist`（Vite 8.3.7 / Vue 3.5.45 / vitest 5.1.0）。
口径：协议替身、真实 PG、外部模型/embedding、演示资料与现场数据分别标注，未执行的环境一律记未验证。

## 1. 执行结果汇总

| 检查项 | 命令 | 结果 |
| --- | --- | --- |
| 前端单测 | `npm --prefix frontend run test -- --run` | 6 文件 / **88 通过**、0 失败、0 错误、0 跳过 |
| 前端类型与构建 | `npm --prefix frontend run build` | `vue-tsc --noEmit` 通过；`dist/index.html` + `index-*.js`（127.55 kB，gzip 47.50 kB）+ `index-*.css`（25.45 kB） |
| 页面走查（协议替身） | `python outputs/validation/pro-mock/mock_server.py 8099` + `python scripts/verify_pro_workbench_mock.py http://127.0.0.1:8099` | **56 通过**、0 失败（截图 `outputs/validation/pro-mock/{desktop-1440,tablet-1000,mobile-390}.png`） |
| 离线 Java 核心验收 | `mvn -q '-Dtest=AcceptanceTest' test` | 5 通过、0 失败、0 错误、0 跳过 |
| 后端合同测试 | `mvn -q '-Dtest=AgentLifecycleTest,AgentStreamTest,ResourceAuthorizationTest' test` | **NOT_RUN**：三例均因缺少 `DEVICETRUTH_TEST_PG_BASE_URL` 报错（本机无 PostgreSQL / Docker 未运行） |
| 真实服务恢复 | `python scripts/verify_browser_recovery.py --browser-path ...` | **NOT_RUN**（同上，需真实 PG） |
| 真实 SSE 浏览器 | `python scripts/verify_sse_browser.py` | **NOT_RUN**（同上） |
| 旧专业页脚本 | `python scripts/verify_pro_page_31_32.py` | **NOT_RUN**：依赖端口 8081 与固定历史 Run ID 的既有产品环境，本机不可用；已按新选择器更新，未实跑 |
| 原型图逐项视觉对照 | — | **未验证**：本轮无法读取图片素材，改为核对 CSS 变量与浏览器计算样式 |
| 外部模型 / embedding | — | 未调用（本 Change 不涉及模型调用） |

Java 环境：`mvn -version` 实测 JVM 为 **17.0.16**（仓库内 `.tools/jdk-21.0.13.0.2+1` 为 21）；上述 Java 命令在该 JVM 上执行通过。

## 2. 前端测试覆盖（88 项）

- `api.test.ts`：原 60 项 + 新增 4 项——onChange 快照反映新增事件与终态、断流后再次 follow 不重复追加 seq、SSE 先于轮询的 WAITING 回读、`waitForRun` 取消抛 `RUN_WAIT_CANCELLED` 且不再请求。
- `diagnosisViewModel.test.ts`：原 12 项 + 新增 4 项——短预览码点边界截断、原文不被改写（含否定/安全用语）、KNOWLEDGE/POSSIBLE_CAUSES 类别与 certainty、空 claims 保留摘要与限制。
- 新增 `runTargets.test.ts`（6 项）：查看轮/活动目标分离、历史+活动组合、多未决轮不自动选目标、许可条件矩阵、多个未决轮暂停发送与取消、显式活动目标回落。
- 新增 `scope.test.ts`（3 项）：A→B→A 代次校验、同轮版本合并与不回退、合并式回读调度。
- 新增 `pageScenarios.test.ts`（11 项，协议样本）：所看轮资产/时点、A→B→A 迟到响应、历史+活动 WAITING 的取消与补答目标、末条消息等于待答正文时仍走 replyTo、旧版本拒绝保留草稿且不新建普通消息、SSE 等待通知回读与 seq 去重、取消轮引用被拒时不编造内容、深入调查载荷（所看判断原文+合法 contextRefs+所看资产、不带 asOf）、同文档不同章节逐条保留、会话切换丢弃旧请求、资产按所看轮 asOf 读取、Skill 失败归一为 null、近期调查不额外逐条读详情。

说明：仓库未安装 `jsdom`/`@vue/test-utils`，本轮未引入新依赖，因此组件断言以「页面同一套 api.ts 合同函数 + 同一套许可判定 + 构建制品浏览器走查」组合覆盖；真实 DOM 键盘交互在页面走查中验证。

## 3. 浏览器走查（协议替身，56 项）

替身 `outputs/validation/pro-mock/mock_server.py` 只模拟 HTTP 合同（三会话、完成/失败/取消/等待轮、来源读取的 error/403/合并 404 分支），**不代表真实服务端持久化、授权或 SSE 能力**。覆盖：

- 5.3：桌面侧栏常驻、服务端更新时间倒序、当前会话标记、新调查入口、无需打开抽屉；1000/390px 侧栏收起、顶部入口可见、页面无横向溢出。
- 1.1/1.2：默认查看服务端末位轮、选第 1 轮后目标卡与判断原文切换、顶栏标明「所看第 1 轮」、输入下拉资产不改变所看轮资产。
- 5.1/5.2：导航显示 certainty 与短预览（长句尾部被截）、中央保留原文、无常驻第三列、风险默认可见、完整结果默认收起可展开/收起、切换焦点零 POST。
- 4.1/4.2：三种终态来源入口、直接引用与全部登记来源分区、模态抽屉字段/章节、焦点进入、Tab 与 Shift+Tab 留在对话框、Esc 关闭、关闭后焦点回到触发项、读取失败重试入口、合并 404 提示且不显示旧轮内容。
- 1.3/2.1/2.2：待答轮时深入调查与普通发送禁用、取消可见、补答携带 replyTo 三元组与当前版本、不落入普通发送。
- 5.3：真实会话切换后任务卡更新且新会话标为当前。
- 控制台：无页面脚本异常（替身对 `/skill`、`/stream` 的 404 属替代实现限制，已单独过滤并说明）。

## 4. 实施中发现并修复的问题（属本 Change 范围）

1. `App.vue` 多个 `immediate` watch 早于后续 `const` 初始化执行，浏览器报 `Cannot access before initialization`，页面整块不渲染。修复：把状态标签、格式化器、作用域守卫等常量上移到所有 `immediate` watch 之前。
2. 成功轮只展示 `result.references`，未覆盖「本轮已登记来源多于答案引用」的要求。修复：先展示答案引用，再以 `/references` 逐条按 referenceId 合并（读取失败时保留已有引用）。
3. 失败/取消轮没有来源入口（来源区块只在完成分支）。修复：来源区块上移到终态共用区。
4. 模态抽屉在首个元素上 Shift+Tab 会落到 `body`。修复：显式 Tab/Shift+Tab 首尾循环。
5. 协议替身自身缺陷（静态目录定位错误、控制端点被静态托管吞掉）已修正，与产品代码无关。

## 5. 与旧 Change 的关系

`devicetruth-pro-workbench-mvp` 未归档，其 14/16 任务与既有验收及失败/修复记录保持原样，本次未改动其文件。本轮改写的 Playwright 选择器（`.evidence-drawer` → `dialog.evidence-modal`、`.evidence-panel` → 按需挂载的 `.pro-drawer`、`.history-list` → `.pro-sidebar`、「运行记录/调查依据」按钮 → 「查看调查记录」入口）只影响脚本，未删除任何既有断言，新增断言见 `verify_pro_page_31_32.py`、`verify_sse_browser.py`、`verify_browser_recovery.py` 的改动。

## 6. 未验证项（不得视为通过）

- 真实 PostgreSQL 下的持久化：刷新后同轮补答、运行状态与引用持久化、SSE 续传与 `Last-Event-ID`、取消与 409 回读的真实服务端行为。
- `AgentStreamTest` / `AgentLifecycleTest` / `ResourceAuthorizationTest` 未实跑，后端合同本轮**未被重新核验**（未改动任何后端代码）。
- 三个浏览器脚本（`verify_browser_recovery.py`、`verify_sse_browser.py`、`verify_pro_page_31_32.py`）已按新布局更新选择器，但**均未实跑**，其可执行性未证明。
- 原型图逐项视觉对照与 1440px 截图人工比对：本轮模型无法读取图片，仅核对 CSS 变量（任务卡 `#f3f1ff`、焦点面板 `#ffffff`、主操作 `#6c5ce7`、选中态 `#f1efff`、正文 `#17213d`）与浏览器计算样式。
- 真实模型、真实 embedding、现场数据与演示资料（汽车/工业历史样本）的实际内容核对。

## 7. 独立归档前复验（2026-11-18）

**结论：暂不归档。** 保留原始验收和修复历史，不改产品代码或任务勾选。OpenSpec 报告 15/15 已勾选、4/4 规划产物齐备；6.1 的真实库及既有恢复验收未执行，勾选不等于有通过证据。三个 delta 的 9 条新增要求均有实现入口，已逐条对照 26 个场景；场景覆盖未完整验证。

| 需求 | 代码证据及结论 |
| --- | --- |
| 待答正文始终可见 | `frontend/src/App.vue:929`；单待答正文在最新消息重复且查看历史时可见。 |
| 补答对应持久问题 | `frontend/src/App.vue:669`；保留三元组和过期冲突路径；真实服务同轮续办、拒绝及草稿回读未验证。 |
| 查看轮与活动轮分别定位 | `frontend/src/App.vue:122`、`frontend/src/App.vue:1050` 将资产、时点和快照绑定到所看轮；SSE 目标和旧响应仍有偏差。 |
| 操作遵守目标与状态 | `frontend/src/runTargets.ts:34`、`frontend/src/App.vue:642`；深入调查和许可条件存在，多未决轮的目标选择有缺口。 |
| 事件触发持久状态校准 | `frontend/src/App.vue:296`、`frontend/src/App.vue:350`；响应式快照和单轮版本判定存在，活动订阅与会话回读不符合设计 §4。 |
| 单焦点工作区 | `frontend/src/components/ProfessionalDiagnosisPage.vue:167` 保留焦点原文与按需结果，直接引用分组不跟随焦点。 |
| 浅色风格与近期调查 | `frontend/src/pro.css:19`、`frontend/src/App.vue:864` 实现主题和桌面列表；窄屏入口未接通。 |
| 诊断与用户陈述语义 | `frontend/src/diagnosisViewModel.ts:175`、`frontend/src/App.vue:1036` 保留原文、引用和用户报告；实际工业/汽车历史未复验。 |
| 终态登记来源可检查 | `frontend/src/components/ProfessionalDiagnosisPage.vue:110`、`frontend/src/components/EvidenceModal.vue:51` 提供通用入口和焦点管理，拒绝后清理不完整。 |

**CRITICAL（1）：** `tasks.md:33` 的 6.1 缺少真实 PostgreSQL、既有恢复脚本和双域历史样本验收。修复后用隔离 product、测试 PostgreSQL、scripted reasoner 和 deterministic embedding 实跑并补证；此前不要将其视为完成。

**WARNING（6，均以最新构建在 Chrome 中复现）：**

1. `frontend/src/App.vue:306`：打开历史轮会关闭活动 SSE，并转订阅历史轮。将 SSE 固定到活动目标，所看历史仅按查看代次读取事件。
2. `frontend/src/App.vue:375`：v2 待答后返回 v1 执行快照，待答卡消失。会话按版本合并消息，逐 Run 合并版本，并对跨会话写回校验请求代次。
3. `frontend/src/App.vue:567`、`frontend/src/runTargets.ts:34`：选择两个 WAITING 中的第二轮后，补答和取消仍不可用。显式选择应设置实际操作目标，普通发送继续禁用。
4. `frontend/src/App.vue:182`：403 后提示无权访问却仍保留该成功轮的来源按钮。拒绝或合并 404 应撤下引用、关闭详情、阻断重复入口。
5. `frontend/src/components/ProfessionalDiagnosisPage.vue:55`：两条判断各引用一章，切到第二条后仍将两章都标为直接引用。按 `selectedClaim.referenceIds` 分组，其他来源留在登记全集。
6. `frontend/src/pro.css:223`、`frontend/src/App.vue:997`：唯一近期调查列表在窄屏隐藏，1000px 和 390px 的记录抽屉均无会话列表。复用列表及加载/空/失败反馈，并测试实际会话切换。

**SUGGESTION（2）：** `frontend/src/App.vue:865` 的新调查按钮复用紫蓝主按钮样式，并避免在焦点区之前铺满全部来源；`scripts/verify_pro_workbench_mock.py:3` 的替身放入版本化目录，`scripts/verify_pro_page_31_32.py:6` 改用脚本创建或显式传入的测试会话。

| 复跑命令或检查 | 总数 / 通过 / 失败 / 错误 / 跳过 |
| --- | --- |
| `openspec validate stabilize-pro-workbench-interactions --strict` | 1 / 1 / 0 / 0 / 0 |
| `npm --prefix frontend run test -- --run` | 88 / 88 / 0 / 0 / 0（6 文件） |
| `npm --prefix frontend run build` | 1 / 1 / 0 / 0 / 0；`index-CS7vN9Px.js`、`index-B63B38sp.css` |
| `mvn -q '-Dtest=AcceptanceTest' test` | 5 / 5 / 0 / 0 / 0；Maven 3.9.9，JDK 21.0.13.0.2 |
| Playwright/Chrome 受控响应复验 | 15 / 7 / 8 / 0 / 0；排除重复来源断言和没有构造历史轮的首版 SSE 探针。通过：三宽度无横向溢出、模态焦点进入、Shift+Tab 与 Esc 回焦、历史中仍可见单待答正文。 |
| `scripts/verify_pro_workbench_mock.py` | 首次导航 `ERR_CONNECTION_TIMED_OUT`，1 次运行错误，执行断言 0；原 56 项通过记录不算本次复跑。 |

截图和观测保存在 `outputs/validation/pro-workbench-verify-20261118/`。1440px 实测截图对照原型：浅紫任务卡、白色圆角焦点、紫蓝底部操作及选中态、常驻侧栏符合主要方向，新调查按钮和来源优先级不同。三宽度截图均为协议样本，不是双域真实历史数据或用户视觉确认。

**Not verified：** 测试 PG 未配置，Docker Desktop daemon 未运行；`AgentLifecycleTest`、`AgentStreamTest`、`ResourceAuthorizationTest`、`verify_browser_recovery.py`、`verify_sse_browser.py`、`verify_pro_page_31_32.py` 未实跑。真实库刷新后同轮补答、引用持久化、取消竞争和原生 SSE `Last-Event-ID` 未验证；三类终态通知及乱序回归、旧触发项消失后回焦、真实制品上的旧补答冲突及草稿、Markdown/Skill、工业和汽车历史也未完整实测。未调用真实外部模型或 embedding，未使用现场数据。

## 8. 修复后最终验收（2026-11-18）

**结果：六类复验缺陷关闭，6.1 通过，OpenSpec 15/15；按要求不归档。** 本节更新 §7 的结论，历史结果及未验证项原样保留。未修改服务端接口、数据库结构或旧 Change 记录。

| 原问题 | 修复及回归 |
| --- | --- |
| 浏览历史切断活动 SSE | 实时订阅使用独立活动目标；历史轮只读历史事件。等待通知按 seq 去重，三种终态通知触发持久回读，保持历史固定选择。 |
| 旧快照覆盖待答和消息 | 按会话版本保护消息，逐 Run 合并版本并保留尚未进入快照的新轮。会话代次拦截切换前的轮询和 SSE 回读。 |
| 多未决轮无法操作 | 显式选择未决轮后，取消和补答指向明确的 Run；普通发送及深入调查仍禁用。浏览器验证第二轮的三元组、409 回读和草稿保留。 |
| 拒绝后仍露出来源 | 登记接口是可展开来源的唯一输入；403 和合并 404 撤下引用及相关详情，不从结果字段回填。 |
| 焦点直接引用错误 | 按选中判断的 referenceId 分组，同一文档不同章节仍逐条可打开，无直接关系的来源保留在全集。 |
| 窄屏不能切换近期会话 | 桌面和窄屏抽屉使用同一个 `RecentInvestigations` 组件。1000px、390px 实际从顶栏进抽屉、检查当前标记并切换。 |

隔离验收使用本机缓存的 `pgvector/pgvector:pg16`，仅绑定 `127.0.0.1:55439`，实际为 PostgreSQL **16.15**、pgvector **0.8.7**。测试进程用 `DEVICETRUTH_TEST_PG_*` 指向该容器；为复用固定设备 ID 的用例分配新 `DEVICETRUTH_EVAL_DATABASE`，不用既有产品库。product 用新 JAR、scripted reasoner、deterministic embedding 启动，结束后停止本次临时容器。Chrome 使用每套脚本自己的本地端口和会话。

| 最终执行命令 | 总数 / 通过 / 失败 / 错误 / 跳过 | 证据 |
| --- | --- | --- |
| `openspec validate stabilize-pro-workbench-interactions --strict` | 1 / 1 / 0 / 0 / 0 | 严格校验 |
| `npm --prefix frontend run test -- --run` | 89 / 89 / 0 / 0 / 0 | 6 个 Vitest 文件 |
| `npm --prefix frontend run build` | 1 / 1 / 0 / 0 / 0 | `index-Cf3OvaBF.js`、`index-pN6_Vg40.css` |
| `mvn -q '-DskipTests' package` | 1 / 1 / 0 / 0 / 0 | product JAR；测试另列 |
| `mvn -q '-Dtest=AgentLifecycleTest,AgentStreamTest,ResourceAuthorizationTest,DualDomainAgentTest,AcceptanceTest' test` | 47 / 47 / 0 / 0 / 0 | 真 PG 42 项（17 + 12 + 9 + 4），离线 5 项；`target/surefire-reports/` |
| `python scripts/verify_pro_workbench_mock.py` | 78 / 78 / 0 / 0 / 0 | Chrome + 协议替身，`outputs/validation/pro-workbench-mock-afef8911/` |
| `python scripts/verify_browser_recovery.py --browser-path 'C:/Program Files/Google/Chrome/Application/chrome.exe'` | 1 / 1 / 0 / 0 / 0 | 真实 PG，`outputs/validation/browser-recovery-971ff067af09478cbab8977d8b4983e3/result.json` |
| `python scripts/verify_sse_browser.py` | 6 / 6 / 0 / 0 / 0 | 真实 PG，`outputs/validation/sse-browser-691d27c1e19d/result.json` |
| `python scripts/verify_pro_page_31_32.py` | 2 / 2 / 0 / 0 / 0 | 真实 PG 双域，`outputs/validation/pro-page-dual-610de3bd/result.json` |

- **持久恢复：** 追问在刷新、关闭浏览器和重启 product 后保持同一问题；补答后仍只有原 Run，完成状态和来源在再次刷新后可回看。真实 SSE 断开于 seq 5，原生 EventSource 带 `Last-Event-ID: 5`，最终时间线为 1–7 且无重复。SSE 的 Markdown 导入达 `PARSED`、关联 CAR-B 和取消场景通过。
- **双域：** 用既有演示材料与设备夹具新建隔离库历史，汽车为 `POSSIBLE_CAUSES`、工业为 `HUMAN_REVIEW`，各为 HIGH、各有 3 条登记来源及匹配 Skill。切判断／来源／记录不产生 POST；工业深入调查使用所看轮资产、选中原文及合法引用，创建新轮，不发送旧 `asOf`。
- **协议替身及视觉：** 可重放旧快照、重复事件、三个终态、多个待答、成功轮的拒绝以及失败／取消带引用。检查 1440/1000/390px 截图和键盘焦点，对照原型的紫蓝操作、浅色任务卡、焦点面板与侧栏。全量来源仍位于焦点前方，这是视觉密度建议，不是六项修复的失败。
- **失败／修复历史：** 首轮 SSE 浏览器发现窄屏动态挂载报错，改为复用组件；后续脚本修复关闭抽屉的旧缓存标记、重复「新调查」的定位及选择设备的过期定位。双域首跑发现题干未命中既有汽车 Skill，重跑同库又遇到设备夹具重复键；改用技能能识别的既有场景语义及空测试库，并按实际 `contextRefs` 对象数组断言。上述失败不计入最终通过数。
- **未验证边界：** 未调用外部真实模型或 embedding，未使用现场数据或现有产品库的历史 Run。多未决、旧版补答 409、来源拒绝和三种终态乱序由浏览器协议替身注入；不能用它们替代真实数据库持久化、SSE 或服务端授权的独立测试。未进行用户视觉确认。
