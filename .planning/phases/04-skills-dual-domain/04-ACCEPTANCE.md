# Phase 04 独立子任务验收

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
