# V0.3 专业工作台验收 · 2026-11-18

本轮仅实施专业端，普通端和管理端未改、未验收。界面不新增路由、权限或后端能力。截图为 Chrome（DPR 1）渲染真实 Vue 构建，数据来自隔离协议替身；不是现场设备，也不是模型在线测试截图。

## 截图与区域差异

- [1600×1000 首屏](pro-1600x1000.png) · [同尺寸全页（含输入及时间线）](pro-1600x1000-full.png)
- [1366×768 首屏](pro-1366x768.png)
- [390×844 首屏](pro-390x844.png) · [同尺寸全页](pro-390x844-full.png)

| 区域 | 对照 V0.3 与结果 |
| --- | --- |
| 外侧导航及主体 | 1600 下外壳 1530px、外侧栏 222px、主区左右 28px；白色品牌顶栏约 70px。无预览条、模拟身份徽标；导航转到真实区域，左侧仍显示已有会话。 |
| 调查方向、焦点、证据 | 1600 下约 216 / 770 / 236px，间距 14px，三卡靠顶。1366 仍为三列；1360 证据移到中栏下方。右栏按焦点直接引用、其他登记来源、Run 状态组织；真实设备时点快照仍可打开。无可确定的同字段证据时不伪造双值对照。 |
| 标题、卡片和状态色 | 任务、焦点、建议、来源分级，标签区分所看轮、确定性、风险和数据性质。紫色主要用于操作和焦点，绿/橙/红区分完成、待核验与失败或高风险；状态同时有文字。 |
| 输入与折叠过程 | 输入与焦点在同一中栏、过程折叠在其下，附件为统一按钮，仍可键盘操作。保留自由提问和真实补答；长夹具比原型多了完整原文、轮级限制和真实引用字段，1600 首屏只到输入卡上缘，1366 需下滚；全页可见。390 顶栏有「继续调查」直达输入/追问。 |
| 折行、历史、报告 | 390 单列且不横溢；判断原文、时间及来源折行。历史、Run 选择、取消和证据详情未隐藏；全文展开保留，不提供没有接口的导出或归档。 |

## 执行结果

最新前端制品：`index-PljZKFXQ.js`、`index-rc-wPLJE.css`。计数顺序：总数 / 通过 / 失败 / 错误 / 跳过。

| 命令 / 环境 | 结果 |
| --- | --- |
| `npm --prefix frontend run test -- --run` | 89 / 89 / 0 / 0 / 0 |
| `npm --prefix frontend run build` | 1 / 1 / 0 / 0 / 0 |
| `python -u scripts/verify_pro_workbench_mock.py` | 97 / 97 / 0 / 0 / 0；切焦点和模式零 POST、观察与深入请求区分、补答三元组、引用详情回焦、无控制台异常；1600/1366/1360/390 断点与无横溢。原始证据 `outputs/validation/pro-workbench-mock-daf75cf9/`。 |
| `mvn -q '-Dtest=AgentLifecycleTest,AgentStreamTest,ResourceAuthorizationTest,DualDomainAgentTest,AssetToolTest,GeneralAgentApiTest,AgentReportToolTest,AcceptanceTest' test` | 83 / 83 / 0 / 0 / 0，JDK 21.0.13.0.2，Maven 3.9.9。78 项产品/真实 PG，5 项离线核心。首次沙箱未连通本机 PG（12 项中 7 个初始化错误），授权后通过。 |
| `python -u scripts/verify_sse_browser.py` | 6 / 6 / 0 / 0 / 0；真实 PG + scripted + deterministic：实时、断线续传、切换隔离、空会话、永久断流回历史、上传/资产/取消。首跑 A–E 通过，F 因共用库资产种子 `DuplicateKeyException` 无法选资产；新建隔离库后全过。记录 `outputs/validation/sse-browser-e905b86a983f/result.json`。 |
| `python -u scripts/verify_browser_recovery.py --browser-path 'C:/Program Files/Google/Chrome/Application/chrome.exe'` | 1 / 1 / 0 / 0 / 0；真实 PG + scripted，刷新/重启后同 Run 补答，引用和历史复原。 |
| `python -u scripts/verify_pro_page_31_32.py` | 2 / 2 / 0 / 0 / 0；真实 PG + scripted，工业和汽车两域的风险、引用、Skill、轨迹及显式深入提交。 |
| Playwright inline：启动隔离 product，打开新构建，提交一条模拟办公风扇问题；回读 Run、model-events 及 events | 1 / 1 / 0 / 0 / 0：真实 `deepseek-flash`，PostgreSQL，deterministic embedding，`SIMULATED`。首次结构化输出无效，服务端重试后经工具调用并完成；轨迹含 3 条 `RUN_MODEL`、`RUN_TOOL`、`RUN_ANSWER`，记录 `outputs/validation/v03-real-model-fcc735ff/result.json`。 |

协议首跑有 1 个测试断言误把「关闭但挂载的 dialog」算成已打开；改为检验可见性后 97 项全过。没有真实现场、外部 embedding 或真实模型补答/焦点深入专项的验证；补答和焦点深入的结果来自上述可重复 product/PG 回归。

## 关键改动文件

- `frontend/src/App.vue`：把统一输入、补答和时间线接进中栏。深入调查预填当前焦点、沿用所看轮资产和引用；观察以未核实的用户消息新开 Run，不写设备事实；待答仍恢复同一 Run。
- `frontend/src/components/ProfessionalDiagnosisPage.vue`、`ClaimDetail.vue`、`RecentInvestigations.vue`：三栏骨架、焦点引用卡和工作空间导航；报告全文按需展开。
- `frontend/src/design.css`、`pro.css`：品牌和状态样式、断点、附件按钮与键盘焦点。
- `scripts/verify_pro_workbench_mock.py`、`scripts/verify_pro_page_31_32.py`：新增布局/模式回归，真实双域脚本按新的「选择后确认发送」时序验证。
