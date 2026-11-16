# Skill 工作手册规范化

依据用户指定的 `/Users/chiye/Downloads/DeviceTruth_工作手册_v2.0/DeviceTruth_工作手册_v2.0_真实数据驱动版.md` 第5章及 `模板/skill-starter/`，规范化项目中八个产品 Skill。外部工作手册、桌面原包、业务数据和模型私密配置未修改；`skills/automotive-demo/rules.json` 继续作为旧 CLI 回归规则，不伪装成产品 Skill。

## 规范与实现

| 手册条款 | 本次处理 |
|---|---|
| §5.2：严格九键 frontmatter | 统一使用 `name, description, version, devices, tasks, tools, evidence, risk, citation`；标量和两空格块列表，名称等于目录名 |
| §5.2：编码、大小、平铺文件 | UTF-8无BOM；入口≤128KB；description≤1000字符；references≤32篇/包、每篇≤256KB，无子目录 |
| §5.2/§5.4：五份标准参考 | 每包均有 `domain-knowledge.md`、`investigation-guide.md`、`evidence-requirements.md`、`decision-rules.md`、`safety-guidelines.md`，入口说明何时按需读取 |
| §5.4：内容分工 | 领域边界/术语、动态调查分支、判断所需证据、规则类型/适用范围/例外/来源、风险与人工责任分别组织；保留原专项矩阵作为补充，不为凑文件重复同一正文 |
| §5.3：工具约束 | 仅声明已有六个领域只读工具中的所需子集，Carrier不声明模拟观测；配置attribute必填、原文必须先检索、Skill不运行脚本或SQL |
| §5.5：泛化与来源 | 不写入具体资产结论、测试题或参考答案；保留实际字段候选，区分当前Run证据与Skill方法。开发台账 `docs/skill-rule-sources.csv` 记录24条规则指针及摘要 |
| §5.2/§5.5：版本与启用 | 八包分别升patch版本，同步product默认安装配置并重启后端；授权由现有启动流程产生，不手工编辑授权表 |

五份标准参考之外，保留每包的运行契约、字段映射、来源/时点边界，以及已有专项矩阵；最终八个包共82篇reference。旧汽车/工厂包也在本次统一，新增按需参考并将固定顺序改为动态分支。

规则台账中的 `PROJECT_POLICY` 表示项目方法约束，`ORIGINAL_GUIDANCE` 表示沿用原包调查建议，旧包条目标记 `LEGACY_SIMULATED_GUIDANCE`。来源路径和章节指针已经核对；技术条款的专业复核人仍为 `UNASSIGNED`，没有据此声称完成厂商原文审核。需引用的技术原文仍应通过知识库工具取得，Skill文件不能冒充回答引用。

## 当前版本

| Skill | 规范化前 | 当前 |
|---|---|---|
| automotive-diagnosis | 1.0.0 | 1.0.1 |
| factory-equipment | 1.0.0 | 1.0.1 |
| ev-charging-triage | 0.2.3 | 0.2.4 |
| ev-maintenance-inspection | 0.2.1 | 0.2.2 |
| ev-powertrain-safety-investigation | 0.2.2 | 0.2.3 |
| industrial-configuration-investigation | 0.2.2 | 0.2.3 |
| industrial-controller-fault-triage | 0.4.2 | 0.4.3 |
| carrier-rtu-evidence-triage | 0.1.1 | 0.1.2 |

规范化前完整文件与摘要在 `outputs/validation/skill-normalization-20261114/before/`、`before-manifest.json`，工作手册与模板摘要在 `handbook-sources.json`。旧运行记录与旧版本授权均保留。当前运行时每个ID只加载一个目录版本，已激活旧版本的待补答Run不能自动迁移；不要为了续跑旧任务修改其版本或摘要。旧任务需恢复匹配旧包的独立环境，新任务新建会话使用当前版本。

## 自检与维护

在项目根目录使用JDK21：

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -o -q -Dtest=SkillRegistryTest,SkillContextLoaderTest test
/usr/libexec/java_home -v 21 --exec java -cp target/classes scripts/ValidateSkills.java
```

`ValidateSkills.java` 直接调用产品的 `SkillRegistry` / `SkillContextLoader`，不是另一套宽松YAML解析器。它额外检查五类参考存在且在入口可发现、reference平铺、链接有效且位于包内、工具白名单与默认启用版本一致。支持在命令末尾传入另一份完整项目的skills目录：`... scripts/ValidateSkills.java /path/to/project/skills`。

`scripts/adapt_domain_skills.py` + `scripts/skill_standards.py` 是八包的生成源，重新执行需要原桌面包和工作手册。脚本会覆盖其管理的文件，应先在副本验证；本次在临时目录重建并逐字节对照，规范化结果可重复生成。以后修改内容需同步生成源、升版本、更新安装配置并重新验证，不能用旧生成脚本覆盖当前规范。

运行链路保持原样：用户提出问题，模型根据已启用目录选中专项；加载正文、按需读参考，随后只读查询设备记录或检索原文；工具登记本Run引用，答案经既有守卫校验后显示。规范化未修改Java业务实现或增加工具权限。

## 验证范围

本次证据统一在 `outputs/validation/skill-normalization-20261114/`。`format-validation.log` 为实际八包/82篇加载结果；`validator-negative-and-rebuild.json` 记录临时目录中的缺参考、嵌套目录、行内列表、未注册工具、BOM五种错误均被拒绝，以及重建一致性。未改变正在运行的包来制造负例。

| 实际执行 | 结果 |
|---|---|
| `mvn -o -q -Dtest=SkillRegistryTest,SkillContextLoaderTest test` | 27项通过，真实产品解析器与按需加载检查 |
| `mvn -o -q test`，JDK21、隔离PG测试变量 | 394项，391通过、0失败、0错误、3跳过；`maven-result.json`、`maven-tests.log` |
| 六条自然语言问题，真实DeepSeek、本机业务记录 | 首批5/6；供电例选对0.2.3并读取告警观测后发生 `MODEL_OUTPUT_INVALID_AFTER_RETRY`，失败批次保留于 `online-initial-batch/` |
| 仅对供电例做一次针对性复验 | 1/1通过，未修改包、未放宽校验、未换模型；`online-summary-selected.json` |
| 当前版本逐例最新证据汇总 | 6/6，66/66检查；五例完成回答、Carrier缺身份而追问；`online-latest-evidence.json`。不是单批6/6，不声称模型格式波动已经消除 |
| 安装、原件和服务核对 | 八个精确版本enabled=true；43份桌面原件、24条规则来源摘要、旧CLI规则校验一致；前端5173/后端8080均200；`installation-and-sources-final.json` |

Maven三个跳过项是未打开 `devicetruth.test.real-agent` 开关的可选真实模型反馈/补答恢复测试，与本次另行执行的六条业务回归分别统计。没有用本次在线样本代替被跳过测试。两个旧Carrier待补答Run均来自前次自动验证，仍保留0.1.1绑定，具体ID见 `pre-upgrade-waiting-runs.json`；本次没有强改旧Run版本或取消历史任务。

真实模型与模拟设备记录的回归、真实本地PostgreSQL集成测试分别记录；本次格式规范化不等同于真实现场检修验收、手册知识库导入或Skill增益A/B评估。知识库尚未建设且当前缺embedding密钥；该已有缺口没有用假数据掩盖。Phase5原UAT状态保持不变，未commit/push。
