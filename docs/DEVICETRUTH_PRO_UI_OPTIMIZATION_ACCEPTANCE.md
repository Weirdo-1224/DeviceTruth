# 专业检修工作台 UI 优化验收

日期：2026-11-22（Asia/Shanghai）。依据 `DEVICETRUTH_PRO_UI_REDESIGN_AUDIT.md`，直接实施 T3/T4/T5，轻量调整 T1/T2；未生成新规划或接入 B 类能力。

## 交付内容

- 创建页：移除重复空状态，将设备、首次标题、正文、附件与提交集中到同一卡片；1280×720 下提交按钮在首屏。
- 执行概览：保留活动轮状态条，去掉重复执行占位卡；复用 App 已有 timelineEvents，紧凑显示最近四条事件，完整过程页保留。不添加 SSE、轮询或引用请求。
- 运行中草稿默认折叠，可手动展开；等待补答时自动展开问题、字段、输入与原 Run 补答按钮。
- 结果：真实 summary 成为主结论；结果类别、风险和数据性质可见；判断导航与详情相邻，本轮建议、仍需确认、适用边界分别展示。保留 HUMAN_REVIEW 投影与安全提示。
- 引用弹窗：摘录前置，版本/定位/技术标识折叠；Esc 与焦点恢复保留。整体复核没有逐项数据时不生成空列表。
- 修正 U10/U12/U13/U14：阶段按 phase 显示、原因码保留；活动轮号按活动 Run 的列表位置；正常跟踪称同步；未加载来源不显示为没有证据。判断引用计数不再累计其他判断对同一来源的使用次数。
- 蓝灰色语义变量、表单间距、按钮、卡片及标签统一；不引入新依赖。

## 实际验证

| 命令 | 最终结果 |
| --- | --- |
| `cd frontend; npm run test -- --run` | 13 文件，144 测试：144 通过、0 失败、0 错误、0 跳过 |
| `cd frontend; npm run build` | vue-tsc 类型检查及 Vite 构建通过 |
| `python scripts/verify_pro_ui_polish.py` | 本机无头 Chrome / HTTP 协议替身：15 项记录断言通过、0 失败；另执行页面可见性/文本断言 |
| `git -c core.fsmonitor=false diff --check` | 通过 |

浏览器核对：首屏主操作、去除重复空状态、summary 原文、导航位置、判断切换、引用摘录、Esc 焦点恢复、查看引用无写请求、报告打开/返回、真实事件阶段文字、空判断隐藏、草稿展开及运行中提交禁用、补答携带原 runId/questionId/version、941/600px 无横向溢出、无页面运行时异常。

已实际查看最终 initial、running-compact、waiting-question、completed、result-941 截图；截图与机器记录保存在 `outputs/validation/pro-ui-polish/`（不提交生成文件）。测试样本由脚本提供，不进入产品界面。

过程记录：新增引用计数测试首次因错误调用 ViewModel 构造函数出现 1 失败/143 通过及类型检查错误；改成既有 `{ run }` 合同后，最终 144 项全通过。受限环境下 Playwright 驱动启动未完成，结束本次检查进程后，经授权运行同一脚本完成浏览器验证。

## 运行链路与范围

例如提交充电中断问题：表单仍调用 App.submit，经现有创建会话、上传、sendMessage 和 Run 跟踪链路取得结果；界面不更改请求。App 将查看轮的 Run、诊断 ViewModel、已登记引用交给 ProfessionalDiagnosisPage；后者只负责摘要、判断分组与局部选择。RunTimeline 只读同一份事件数据；点引用由 App.openReference 打开 EvidenceModal，不产生模型调用。补答仍由 App.answerQuestion 发送原问题版本，取消仍等待持久状态回读。

Java、Agent Core、数据库、api.ts、timeline.ts、runTargets.ts、App.vue script 状态管理均未修改。保留原有两份未跟踪审计文档。未自动提交 Git。

未验证：本轮没有运行 product 真后端、真实 PostgreSQL、外部模型或现场设备；协议替身验证仅证明前端布局与交互，不替代真模联调。上传、取消及权限的完整真实服务端链路本轮未重跑，原有前端 API/权限/状态回归均通过。需要后端改造及 B 类能力均跳过。

## 2026-11-22 最终结构重构（取代上轮四条概览与展开式结果输入）

- 依据用户目标示意图直接重排：品牌/账号进入左侧壳；新建页集中输入画布，设备与标题并排；执行页突出真实对象/轮次/提交时间/累计用时和完整事件活动；完成页独立摘要区、判断/证据分栏、本轮建议与边界在后。执行及完成的补充区默认收起，待答及尚无 Run 的首提交流程展开。
- 复用现有 RunTimeline 全量事件（概览有界滚动），不再只取最近四条；不添加订阅/请求。App 新增的 revealComposer 仅打开原生 details 并聚焦，用于讨论引用、观察与深入调查；没有改变 Run 状态或请求处理。
- 修改范围扩至 WorkspaceShell、RecentInvestigations、ClaimDetail、UiIcon、DiagnosisReport 及既有专业组件/模板/CSS；报告也改为真实 summary 优先。搜索/筛选折叠但保留全部原选项。无新的 OpenSpec 或设计文档。
- 最终 `npm run test -- --run`：13 文件、147 项，147 通过、0 失败/错误/跳过；`npm run build`：类型检查及构建通过。
- 最终 `python scripts/verify_pro_ui_polish.py`：23 项记录断言通过、0 失败，另有页面文本/焦点断言。覆盖新建首屏/设备标题并排、完成后折叠/建议顺序、引用/报告/打印、执行草稿、等待补答、历史/活动轮分离、取消确认目标、团队讨论引用展开与焦点、941/600px 无横向溢出和页面运行时异常。生成打印样式 PDF，未调用系统打印机。
- 已查看真实浏览器渲染的新建、执行、完成、待答与窄屏截图；最终证据位于 `outputs/validation/pro-ui-final/`，上一轮 `pro-ui-polish/` 保留。样本为隔离协议替身，不进入运行中的产品服务。
- 检查中发现侧栏 footer 遮挡近期调查按钮、补答卡受旧 width:100% 加外边距造成横向溢出，已修复并用浏览器回归通过；新增测试样本漏填 review.units/decisions 曾导致类型检查失败，补齐真实类型后最终构建通过。
- 5173 前端和经其代理的 `/api/health` 均返回 200，保留用户正在使用的服务。本轮未执行真实大模型/真实数据库端到端调查；健康检查不代表真模验收。Java/API/Agent Runtime/认证、Run 追踪与状态机未改动。

## 2026-11-22 16:07 真实后端与大模型 UI 验收

- 使用客户端内置浏览器及用户自行登录的个人空间，从新版表单创建并提交“真实模型 UI 验收｜空压机高温停机”。使用当前 5173 前端和已配置真实后端；未使用协议替身、未启动 scripted，未操作桌面浏览器。场景明确标注非现场实测，未选择不存在的资产。
- Run：`48fcbad7-dc81-40f0-813e-f3cd82ef9bb7`。观察到 RUNNING 与真实排队事件，最终 COMPLETED；累计执行用时 10 秒。最终类别 KNOWLEDGE、风险 LOW、数据性质 SIMULATED；3 项判断、2 项建议、2 项不确定性、3 项限制、0 条已登记来源。
- 真实过程页记录 10 个事件：RUN_QUEUED → deepseek-flash VALID_PROPOSAL → 两次 search_knowledge 成功且各返回 0 引用 → deepseek-flash INVALID_JSON_OR_SCHEMA → 第 2 次 VALID_PROPOSAL → REVIEWING / ANSWER_EVIDENCE → VALID_SEMANTIC_REVIEW → RUN_ANSWER_REVIEW → RUN_ANSWER。模型格式失败及恢复均保留在界面，不隐瞒失败或当成整轮失败。
- 10 项 UI 检查通过：表单提交、运行中状态与事件、真实模型/工具轨迹、完成态、判断切换、来源 0 空态、完整结果展开、报告摘要与工作台一致、刷新恢复同一 Run/状态/摘要、完成后补充区默认收起。结果如实说明没有可用手册及资产记录，一般候选仍标“可能/未确诊”，未显示修复成功。
- 当前空间资产下拉无可选资产，检索无命中；因此本轮验证的是“真实模型＋真实后端＋资料不足的完成结果”，不构成有引用/有资产的专业诊断验收。引用详情弹窗、上传、取消、补答与系统打印本轮未执行；不把前轮协议替身覆盖代计为本轮真实通过。
- 发现轻微 UI 问题：来源抽屉中“本轮没有已登记来源。”重复显示两次；未改变数据，不阻断提交/事件/结果/报告。此次为验证任务，未修改源码。
- 证据：`outputs/validation/pro-ui-live-20261122/verification.json` 与提交、结果、真实事件过程、来源空态、报告、刷新恢复截图。运行只有约 10 秒，第一张提交后的截图已是终态，命名为 `02-first-observed.jpg`，不冒称执行中截图；运行中状态由当时的浏览器 AX 观察证明。部分全页截图超时后改保存视口截图，不影响业务验收。
- 本轮未重跑前端测试/构建（源码未改）；此前最终 147 项前端测试与构建证据仍适用。保留此次真实调查在用户个人空间，内置浏览器停在其结果页。

## 2026-11-22 登录入口视觉统一

- 仅修改 `frontend/src/AuthenticatedWorkbench.vue`：取消左右宣传分屏，改为 440px 居中登录面板，复用工作台蓝色 target 图标；找回密码位于密码标签旁，注册移到主按钮下方；注册/找回/重置分支共用容器，移除 Cookie 技术说明。保留字段校验、autocomplete、请求、CSRF、账号和空间逻辑。
- 实际比较 Git 原版与当前 `<script>`：除新增 UiIcon 导入外完全一致。登录仍经 submit → authenticate → loadSpaces → App；找回和重置仍由原 authAction 分支处理。
- `npm run test -- --run`：13 文件、147 项全通过，0 失败/错误/跳过；`npm run build` 类型检查和构建通过；`git diff --check` 通过。
- 使用内置浏览器的 127.0.0.1 真实前端/后端，独立于用户已登录的 localhost 会话；未退出用户账号。5 项布局/交互检查通过：未登录登录页、注册字段、找回密码字段、返回登录、375px 无横向溢出。实际截图与记录：`outputs/validation/auth-ui-20261122/`。未提交任何口令、注册、重置邮件或密码修改。
- 重置链接界面检查未通过：带布局预览用占位 token 的初次加载回到登录页。原有 onMounted 调用 currentAccount，401 通过 api.ts 的 devicetruth-auth-expired 触发 clearIdentity，将 mode 重置为 login；原版已有同一链路。本轮只改 UI，未扩展认证逻辑修复，也未将重置分支标为通过。

## 2026-11-22 新建调查宽屏留白调整

- 本次仅调整 workspace.css：新建页取消外层 1440px 与内层 900px 的双层居中，创建区最大 1280px，靠内容区左侧展开；桌面侧栏至创建卡间距 36px，顶部间距 32px。正文高度按视口在 164–240px 间伸缩，现有设备/标题、附件与按钮布局不变。其他 Run 状态及登录页未改。
- `npm run test -- --run`：147/147 通过，0 失败/错误/跳过；`npm run build` 与 `git diff --check` 通过。
- 内置浏览器、真实已登录页面检查：1920px 视口卡宽 1280px、距侧栏 36px、无横向溢出；1280×720 下主按钮仍在首屏；375px 无横向溢出。没有提交调查或额外模型调用。
- 测量和默认视口截图：`outputs/validation/create-width-20261122/`。1920px 超面板全页截图超时，未将裁切截图作为宽屏全貌证据；宽屏以实际 DOM 尺寸记录为准。

## 2026-11-22 侧栏拖拽调宽与滚动整理

- WorkspaceShell 新增 10px 命中区的竖向分隔条，原生 Pointer Capture 跟踪拖动；宽度限定 200–380px，并按窗口宽度为正文保留至少 520px。主内容由 CSS Grid 自动重排，不新增业务状态或请求。
- 双击/Enter 恢复默认 224px；左右键每次调整 16px，Home/End 到边界，Esc/pointercancel 取消本次拖动；释放、丢失捕获、窗口改变及卸载均清理拖动态。分隔条提供键盘焦点与 separator/ARIA 数值，拖动时禁用文本选择。
- sidebarSizing.ts 按账号保存 localStorage 数值偏好，刷新/空间切换可恢复；存储禁用时仍能调整。窄屏不提供拖拽，回到桌面保留原偏好。
- 侧栏顶部导航和底部账号固定，近期列表独立滚动，搜索筛选与加载更多保留可见；长标题最多两行且保留完整 title。极矮桌面（≤480px）使用单一外层滚动保障入口可达，不同时出现内外两条滚动区。
- `npm run test -- --run`：14 文件、155 项，155 通过、0 失败/错误/跳过（新增 8 项尺寸/偏好回归）；`npm run build` 类型检查/构建、`git diff --check` 通过。
- `python scripts/verify_pro_ui_polish.py`：39 项记录断言通过、0 失败。实际无头 Chrome 拖拽、键盘、Esc、边界、双击、刷新恢复、800px 正文留宽、20 条历史单滚动区、加载更多、600px 高度账号和列表可见、移动端隐藏分隔条均通过；原提交/补答/历史/取消确认等离线 UI 回归继续通过。
- 已查看 `outputs/validation/pro-ui-final/sidebar-resized.png`，机器结果同目录 result.json。浏览器测试使用现有本地协议夹具验证纯前端交互，不操作用户桌面，不代替或重跑真实模型验收。本轮没有新增真实调查，未修改 Java、API、App Run 状态或依赖。
