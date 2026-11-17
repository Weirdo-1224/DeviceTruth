# Phase 04 独立子任务验收

## 2026-11-13 产品集成补验

04-01 产品链路、04-02 离线双域和真实供应商样本均已验收。

- V14 保存服务端安装、run 选中版本及参考资料摘要。模型可按需提议激活和读取，产品执行前核对许可，使用网关和 Skill 许可的交集。
- V15 存储独立的模拟观测。网关按归属、资产、时点及质量筛选，模拟数据不充当现场确认。
- 打包 JAR 与真实 PostgreSQL 运行 `verify_general_agent.py --suite=dual-domain --embedding=deterministic --reasoner=scripted`，退出 0。汽车 9 项、工厂 8 项脚本断言均通过。汽车无故障码进入、同 run 追问与补答并读回手册章节。工厂区分出厂、改造和旧模板来源与有效期，保留现场待核验。两域均核对实际 Skill 版本、工具调用、引用归属和未读现场数据说明。证据在 `outputs/validation/general-agent/dual-domain-79d78641c33d4c2385abb570c52181a7/result.json`。
- 最终 `mvn -B -o package` 343 项，342 通过、0 失败、0 错误、1 跳过。Skill 集成 3/3，双域 4/4，含观测引用回读、质量和时点过滤、跨归属拒绝、四组工厂样本与只读边界。符号链接越界因 Windows 权限跳过。
- 集成期间修复验收请求字段、旧迁移计数和观测引用插入占位符。最新打包与全部本地验证结果见 `outputs/validation/phase34/package-verified.log`。
- 在线验收历史。首轮汽车遗漏观测调用，工厂判定因同源维修记录覆盖了配置引用而失败。第二轮工厂全通过，汽车追问未满足两个指定主题。第三轮汽车全通过，工厂答案明确写出 `UNCONFIRMED` 且台账不等于现场装机，但旧脚本未识别该表述。脚本分别增加同源引用与未确认表述的正反例，线上补答明确要求核查观测，首轮明确说明缺少哪两个信息。离线仍使用固定初始问题。资料及观测均标为 `SIMULATED`。
- 最终在线批次使用 DeepSeek 与 DashScope，两个任务都完成并报告 Token。汽车 9/9，工厂原判定 7/8。唯一失败项是脚本未识别“不能确认控制器当前实际固件版本”，保留原始 `FAIL`，补充正反例后复核同一份工厂答案，最终汽车 9/9、工厂 8/8。保留同目录 `result.json` 和 `rechecked-result.json`，路径 `outputs/validation/general-agent/dual-domain-a59565ea791b4dae8df33811914f8895/`。汽车 20,640 / 2,897 Token，工厂 23,443 / 4,653 Token，费用均未报告。Python 脚本回归 15/15。线上问题显式要求两个追问主题，补答显式要求先检索手册、再核查观测。原始自由输入的行为波动保留在前述记录。

以下为集成前独立子任务的验收历史。

2026-11-13（北京时间）。本轮验收 Skill 文件发现与读取、汽车与工厂模拟样本。基线提交为 `cf9fd49`，测试包含当前尚未提交的 Skill 与样本文件。源码、测试、资料及构建产物在 `outputs/phase04-local-fce429a5/` 的隔离副本中运行。结果不作为整份 PLAN 或阶段的完成记录。

| 范围 | 总数 | 通过 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 04-01：SkillRegistryTest、SkillContextLoaderTest（JUnit） | 26 | 25 | 0 | 0 | 1 |
| 04-02：工厂样本离线断言（四种样本） | 149 | 149 | 0 | 0 | 0 |
| 04-02：汽车样本静态核对（四组） | 4 | 4 | 0 | 0 | 0 |

- **Skill 发现与读取：已验证部分通过。** 在隔离副本运行 `mvn -B -o -s <仓库>/.mvn/settings.xml -Dmaven.repo.local=<仓库>/.m2/repository -Dtest=SkillRegistryTest,SkillContextLoaderTest test`，退出码 0，原始记录见 `skill-tests.log`。覆盖两个领域 Skill 及参考资料读取、旧规则目录排除、格式和大小限制、无效引用名等。符号链接越界测试因当前 Windows 环境无法创建符号链接而跳过，记为未验证。
- **工厂样本：离线通过。** 用项目 JDK 21 编译并运行 `io.devicetruth.application.agent.DualDomainSampleCheck`，退出码 0，原始记录见 `factory-sample.log`。程序使用仓库实际的 JSON 合同、资产导入映射、资产工具和网关，仓储使用内存替身。核对改造前后、核验有效期边界、多来源冲突、引用摘要和三个负例。测试未登记或回读真实 run 引用。
- **汽车样本：静态通过。** 用 PowerShell 解析 JSON，核对两轮模拟输入、Skill 标识与文件、手册标题与版本以及三处章节。退出码 0。未实跑产品 API、追问、检索或答案。

首次 Maven 命令因隔离目录缺少相对路径的 Maven settings 未启动测试，显式传入仓库 settings 后成功。工厂程序首次用到 PATH 中的 JDK 17，改用项目 JDK 21 后编译成功，补正 PowerShell 的 JVM 参数引号后实跑通过。

待集成验证：Skill 激活、版本持久化及恢复权限重验；真实 PostgreSQL 导入与引用登记回读；观测工具、双域产品 HTTP、真实模型与 embedding。状态保持按原计划推进。
