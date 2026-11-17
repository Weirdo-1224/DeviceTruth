# 工作台参考图复刻验收

2026-11-14 在 Windows、系统 Chrome、Java 21 和 PostgreSQL 16.15 上执行。产品成功路径使用现有 scripted 推理器与确定性向量，资料来自仓库 Markdown 和资产夹具。失败路径使用本地 HTTP 模型协议替身返回无效动作，产品服务真实重试并持久化 `MODEL_OUTPUT_INVALID_AFTER_RETRY`。

## 视觉

| 视口 | 左栏 | 中栏 | 右栏 |
| --- | ---: | ---: | ---: |
| 参考图 1586×992 | 279 | 876 | 431 |
| 1440×900 | 253 | 795 | 392 |
| 1920×1080 | 338 | 1060 | 522 |

- 首轮发现多轮对话和报告重复展示结论，报告首屏露出过少。
- 第二轮将结论集中到报告，保留可展开的前序对话，压缩附件状态与完整记录入口。对比后将关键资料、人工核验和建议行动放在报告前部，长篇事实依据可展开。
- 最终复拍空白、运行中、等待补充、完成、失败及滚动后的报告。1440×900 的长报告可在中栏滚动，证据栏和输入区保持可用。375×812 的导航、证据抽屉和移动端引用跳转由 Chrome 回归覆盖。

截图目录为 `outputs/validation/ui-reference/`，包含 `round-1`、`round-2` 和 `final`。最终双尺寸截图使用系统 Chrome 以设备像素比 1 采集。

## 命令与结果

| 命令 | 总数 | 通过 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `npm --prefix frontend run test -- --run` | 36 | 36 | 0 | 0 | 0 |
| `mvn -o -q test` | 360 | 359 | 0 | 0 | 1 |
| `python scripts/verify_sse_browser.py` | 6 | 6 | 0 | 0 | 0 |

- `npm --prefix frontend run build` 通过 TypeScript 检查与 Vite 打包。
- `python scripts/verify_cli.py` 通过 A–D、帮助、非法参数、缺凭据和本地协议替身配置优先级。
- SSE 回归覆盖实时事件、从 `Last-Event-ID: 5` 补齐且不重复、切换会话、清理旧时间线、HTTP 409 后刷新回读、上传、资产对照、取消和提交失败提示。原始结果为 `outputs/validation/sse-browser-965143c8d742/result.json`。前一轮 A–E 通过，F 因复用测试库的资产夹具记录 ID 冲突失败，在全新测试库复验 6/6。
- 另用双分辨率截图脚本核验等待补答、完成后展开引用与事实、重新打开历史及刷新。运行中和终态失败由真实 API 驱动并采图。
- Maven 跳过的用例是 Windows 当前权限无法创建符号链接的 `SkillRegistryTest` 用例。真实外部模型、真实外部向量服务和现场设备未在本轮复验。

## 输入快捷键补验

主输入和补答输入支持 Enter 提交、Shift+Enter 换行，中文输入法组合事件期间保留默认按键行为。系统 Chrome 验证回车发起调查、回车补答、Shift+Enter 换行与模拟输入法组合事件。前端构建及 36 项单测通过，浏览器 A–F 6/6，原始结果为 `outputs/validation/sse-browser-dd845bdee3f4/result.json`。
