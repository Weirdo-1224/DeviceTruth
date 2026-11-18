# Tasks

## 1. 查看轮与活动目标（F08）

- [ ] 1.1 在 `frontend/src/App.vue` 拆分所看 Run 和非终态活动目标，保留服务端排序与历史固定选择，增加两个状态标记及定位入口。组件测试以完成历史轮和 QUEUED/RUNNING/WAITING 活动轮组合验证操作目标；混合终态与多未决样本可逐轮定位，刷新不覆盖固定选择。
- [ ] 1.2 在 `App.vue` 将设备快照绑定到所看轮的 goalMessageId、资产和 asOf，按查看作用域重挂载既有 `EvidenceComparison.vue`；给所看轮 Skill、焦点和调查计划加归属校验。组件测试覆盖不同资产、同资产异时点、输入下拉值不影响历史、A→B→A 迟到响应和缺目标消息的空态。
- [ ] 1.3 在 `App.vue` 和 `ProfessionalDiagnosisPage.vue` 对齐取消、深入调查、普通提交及 Enter 的许可条件。组件测试核对取消只发给明确的活动 Run、409 后回读、未决轮期间保留普通草稿且无新轮 POST、已完成历史的深入调查使用所看判断/资产及现有合法 contextRefs，现有时点语义可辨认。

## 2. 持续可见的追问与同轮补答（F01，依赖 1）

- [ ] 2.1 在 `App.vue` 的主工作台渲染待答轮的真实 pendingQuestion 正文和字段，去掉按末条消息去重的隐藏条件，增加待答轮定位，现场观察入口按状态聚焦正确输入。组件测试覆盖末条消息等于正文且抽屉关闭、刷新、所看轮为历史与窄屏长问题。
- [ ] 2.2 在 `App.vue` 对齐既有 `replyToQuestion` 三元组、草稿和版本冲突分支。扩充 `api.test.ts` 中必要的合同断言并做组件测试，验证有效补答保持 runId 与轮次数，旧版本拒绝后保留草稿并回读新问题，Enter 与按钮都不会落入普通发送。

## 3. SSE 触发持久回读（F04，依赖 1；与 2 联验）

- [ ] 3.1 在 `App.vue` 接入 timeline.onChange 的响应式快照，实时跟踪活动目标并读取所看历史轮事件；局部调整 `timeline.ts`／`api.ts`（如控制器错误接入或等待集合命名需要）。扩展 `api.test.ts` 的事件回放并在实际 Vue 页面验证：下一轮轮询被延迟时，RUN_WAITING 与三种 RUN_TERMINAL 都及时触发持久回读；重复 seq 不多出时间线记录或等待回读，流数据不会直接改写持久状态。
- [ ] 3.2 在 `App.vue` 保护并发回读、轮询与视图切换，保留 Last-Event-ID、历史游标、generation、同轮恢复与有界轮询。组件测试注入断流、晚到的旧版本、会话切换、等待后补答和轮询超时，验证历史事件隔离、权威状态不回退、非业务失败可重同步。复用 `AgentStreamTest` 和 `AgentLifecycleTest` 核对后端合同。

## 4. 终态来源与详情（F02，依赖 1；与 5 共用页面）

- [ ] 4.1 在 `App.vue` 与 `diagnosisViewModel.ts` 为所看终态轮读取已登记来源，按 referenceId 绑定查看代次，分开直接引用与全集。用组件测试和视图映射单测覆盖三种终态、答案未引用的登记来源、同文档不同章节、缺失关联 ID、空列表、公开拒绝、合并 404、网络/服务异常后重试及切轮无旧证据。
- [ ] 4.2 在 `ProfessionalDiagnosisPage.vue`、`ClaimDetail.vue`、`EvidenceDrawer.vue` 和 `pro.css` 挂载分支共用的证据模态抽屉，保留真实标题、版本、位置、摘录和 dataNature。复用 `scripts/verify_pro_page_31_32.py` 的详情路径和新增组件断言验证三种终态、窄屏、Esc/可见关闭、Tab/Shift+Tab 与焦点返回，缺字段与拒绝态无伪造信息。复用 `ResourceAuthorizationTest` 确认证据读取仍受现有授权约束。

## 5. 单焦点工作区与结果语义（依赖 1、4 的数据与抽屉）

- [ ] 5.1 在 `diagnosisViewModel.ts`、`ClaimList.vue`、`ClaimDetail.vue` 生成确定性短预览与类别，中央保留选中全文和关联来源。扩充 `diagnosisViewModel.test.ts`／`claimSelection.test.ts` 验证原文及 ID 未被改写、预览安全截取、KNOWLEDGE/POSSIBLE_CAUSES 与 certainty、缺引用与空判断；组件测试用请求记录验证切焦点零 POST。
- [ ] 5.2 在 `ProfessionalDiagnosisPage.vue` 与 `pro.css` 调整为窄导航加主区，窄屏单列，移除常驻证据列；在 `App.vue` 与专业页保留可达历史、自由输入、完整结果、风险与原始 limitations，并用现有消息合同标明用户报告性质。组件测试比较展开前后结果全文和安全提示，核对建议只属于本轮、无虚构概率，空判断仍可用自由文字。桌面和窄屏验证主要控件可见、可点击、无页面级横向溢出。
- [ ] 5.3 在 `App.vue` 与 `pro.css` 复用现有会话数据与切换操作，桌面常驻左侧「近期调查」和「新调查」，窄屏从顶部记录入口展开。组件测试在 1440/1000/390px 核对入口、服务端顺序、当前会话标记、真实会话切换、加载/空/失败反馈和无额外逐条详情请求。此项只依赖第 1 组的会话切换和状态绑定。
- [ ] 5.4 在 `pro.css`、`App.vue` 和专业组件局部样式应用原型浅色紫蓝主题（依赖 5.2、5.3）。为顶部浅紫任务卡、白色圆角焦点面板、紫蓝主操作及选中态统一样式，核对证据抽屉、输入、追问和风险的可读性。用最新制品的 1440px 截图与原型逐项对照，同时保留 1000px、390px 截图核对布局收纳和清晰可见的键盘焦点。

## 6. 最新制品端到端回归（依赖 1–5）

- [ ] 6.1 构建最新 Vue 制品，执行下表的组件/浏览器场景和既有恢复脚本。用隔离 product、真实测试 PostgreSQL 及 scripted reasoner/deterministic embedding 验证刷新后同轮补答、运行状态与引用确实持久化；并用协议样本在实际 Vue 组件注入乱序、错误和带引用的取消场景。用既有工业/汽车历史样本核对分类、风险、来源、记录与深入调查入口；回归自由输入、Markdown 导入状态和 Skill/时间线展示。
- [ ] 6.2 在本 Change 的验收记录汇总前端构建、测试和浏览器实际命令、总数/通过/失败/错误/跳过、构建版本、证据及未验证项。对照旧 Change 保留已有验收和失败/修复记录，独立标记协议替身、真实 PG、外部模型/embedding、演示资料与现场数据口径；缺任一执行环境按未验证记录。

## Traceability

来源为 [V0.3 产品方案](<D:/download/DeviceTruth_商业化产品方案_V0.3_修订包/DeviceTruth_商业化产品设计方案_V0.3.md>) §§3、7–9、[V0.3 增量路线](<D:/download/DeviceTruth_商业化产品方案_V0.3_修订包/DeviceTruth_增量开发路线_V0.3.md>) §3 R0-A 及 [V0.2 设计审查](../../../docs/reviews/DEVICETRUTH_V02_PRODUCT_DESIGN_REVIEW.md) V02-04/05/07/08。

| R0-A 需求 / V0.3 验收 | Delta | 任务 | 最小组件或浏览器场景 |
| --- | --- | --- | --- |
| F01 / R0-01 | `investigation-continuation` | 2、6 | WAITING 末条消息重复 → 刷新 → 切历史仍见正文 → 校验三元组和同 Run → 注入旧版本冲突 |
| F02 / R0-02 | `professional-diagnosis` | 4、6 | 对三种终态逐条开来源；成功 `AgentResult` 外来源、同文档不同章节、失败/取消有引用、空/拒绝/异常；1440px、1000px、390px，键盘开关回焦 |
| F04 / R0-07 | `investigation-recovery` | 3、6 | 延迟普通轮询，先送 WAITING/终态事件；回放重复 seq、验证续接游标；断流时轮询推进，异常无假 FAILED |
| F08 / R0-04、R0-05 的展示部分 | `investigation-recovery` | 1、3、6 | 历史资产与业务时点不跟随输入，切轮无旧来源或快照，另标活动目标；动作检查实际请求的 Run ID |
| 单焦点及安全语义 / R0-09 的本地呈现部分 | `professional-diagnosis` | 5、6 | 工业与汽车样本，查看选中全文、结果按需展开、风险和原始限制默认可读、无 claims 时仍可输入 |
| 已确认的专业端原型风格 / V0.3 §§3.2、7.1–7.2 | `professional-diagnosis` | 5.3、5.4、6 | 1440px 最新制品对照原型检查浅色紫蓝、任务卡、白色焦点面板和常驻近期调查；1000px/390px 验证同一历史列表可按需打开 |
| 已通过功能的增量回归 | 三份增量规格 | 6 | 记录恢复、同轮补答、已有深入调查和合法引用、取消、Skill、Markdown 与模拟标签 |

R0-03、R0-04 的服务端并发约束、R0-05 的新轮时点继承、R0-06 的引用复制和回执/持久化归 R0-B。R0-08 的故障窗口归 R0-D。R0-09 的模型风格、输出质量对照归 R0-C。本表验收的是 R0-A。

## Verification commands

实施时在仓库根目录执行以下命令。组件场景使用 Vite 构建的实际 Vue 页面，在 `verify_pro_page_31_32.py` 扩展可重复的受控响应场景，避免把固定历史 Run ID 当作必须存在的测试数据。真实服务集成使用同一制品，明确区分协议样本与真实 PG。保留恢复脚本的专业页入口与持久恢复断言，更新受布局影响的选择器。复用 `verify_sse_browser.py` 的流式代理验证真实 EventSource 续传。

```powershell
npm --prefix frontend run test -- --run
npm --prefix frontend run build
mvn -version
mvn -q '-Dtest=AgentLifecycleTest,AgentStreamTest,ResourceAuthorizationTest' test
python scripts/verify_browser_recovery.py --browser-path 'C:/Program Files/Google/Chrome/Application/chrome.exe'
python scripts/verify_sse_browser.py
```

## Source files

- `frontend/src/App.vue`、`frontend/src/components/ProfessionalDiagnosisPage.vue`、`frontend/src/components/ClaimList.vue`、`frontend/src/components/ClaimDetail.vue`、`frontend/src/components/EvidenceDrawer.vue`、`frontend/src/diagnosisViewModel.ts`、`frontend/src/timeline.ts` 和 `frontend/src/pro.css`。对 `frontend/src/api.ts` 的预计调整限于前端等待语义命名或已有跟踪接入，HTTP 合同沿用。
- `frontend/src/diagnosisViewModel.test.ts`、`frontend/src/claimSelection.test.ts`、`frontend/src/api.test.ts`、`scripts/verify_pro_page_31_32.py`、`scripts/verify_browser_recovery.py`，以及因布局或活动订阅语义变化需要调整断言时的 `scripts/verify_sse_browser.py`。复用现有 Java 测试，设备组件和资产 API 通过 App 的参数绑定复用。

## Planning verification

2026-11-18 执行 `openspec status --change stabilize-pro-workbench-interactions` 和 `openspec validate stabilize-pro-workbench-interactions --strict`。规划检查 2 项，通过 2、失败 0、错误 0、跳过 0，状态为 4/4 产物齐备。实施任务 0/13。产品测试执行 0 项（通过 0、失败 0、错误 0、跳过 0），当前浏览器行为、数据库集成、外部模型、embedding 和现场能力均未在本轮重验。

2026-11-18 用户确认以原型浅色紫蓝风格和桌面常驻「近期调查」作为交付要求。补充规格和 5.3、5.4 后，上述两项规划检查复核通过（4/4 产物齐备，失败 0、错误 0、跳过 0），实施任务 0/15。产品测试执行 0 项，最新制品视觉与交互待实施验收。
