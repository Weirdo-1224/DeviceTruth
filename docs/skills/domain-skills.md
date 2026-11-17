# 领域 Skill 原包适配与启用

2026-11-14，按用户授权，将桌面“工业设备”目录中的六个独立领域 Skill 适配到 DeviceTruth 产品运行时。原包中另外三份 EV 包为重复副本，没有重复安装。桌面原件不修改。随后按用户指定工作手册规范化全部八包，包括原有汽车/工厂包；当前规范和验收见 [Skill规范化](skill-normalization.md)，下方“验证与修复记录”为首次适配历史。

## 当前包与用途

| 产品包 | 版本 | 处理的问题 |
|---|---|---|
| [ev-charging-triage](../../skills/ev-charging-triage/SKILL.md) | 0.2.4 | 插枪充不上电、充电中断、灯态、预约、车桩授权 |
| [ev-maintenance-inspection](../../skills/ev-maintenance-inspection/SKILL.md) | 0.2.2 | 胎压、轮胎、制动及耗材维护记录 |
| [ev-powertrain-safety-investigation](../../skills/ev-powertrain-safety-investigation/SKILL.md) | 0.2.3 | 低压供电、ICCU、动力受限、电池风险与召回适用性 |
| [industrial-configuration-investigation](../../skills/industrial-configuration-investigation/SKILL.md) | 0.2.3 | 型号、固件、技改和更换记录，采购前核对 |
| [industrial-controller-fault-triage](../../skills/industrial-controller-fault-triage/SKILL.md) | 0.4.3 | PLC、远程 I/O、更换后的通信和诊断结果 |
| [carrier-rtu-evidence-triage](../../skills/carrier-rtu-evidence-triage/SKILL.md) | 0.1.2 | Carrier 48TF 登记身份、工单、过滤器改造与验收证据 |
| [automotive-diagnosis](../../skills/automotive-diagnosis/SKILL.md) | 1.0.1 | 汽车充电与车载通信交叉线索、故障码的综合调查 |
| [factory-equipment](../../skills/factory-equipment/SKILL.md) | 1.0.1 | 产线停机与节拍异常的跨部件综合调查 |

前三包来自 `DeviceTruth_EV_v0.1_领域数据与Skills/02_领域Skills`；工业两包来自 `DeviceTruth_Industrial_v0.6_数据扩充版/02_领域Skills`；Carrier 包来自该工业目录的 `08_真实领域交接_2026-11-14/skill`。文件级出处、原件 SHA256、适配版本与替换/省略项见 [domain-skill-sources.json](domain-skill-sources.json)，共 43 份原始文件。

## 适配内容

- 按项目解析器规范写入九个 frontmatter 字段。登记名称、适用设备、任务、工具、风险与引用要求，让模型可以按自然语言问题选择专项。
- 将旧工具和独立调查 JSON 协议替换为产品已有的只读工具及 `ANSWER` 协议；原输出 schema/template 不作为运行依赖。Skill 不执行外部脚本、不生成 SQL、不增加设备控制权限。
- 补齐数据库实际属性/信号映射，例如配置用 `installed_part`，观测用完整的 `PN-01.io_data_exchange`。字段选择由助手完成，用户无需输入内部名称。
- 每包包含 `runtime-contract.md`、`data-access.md`、`evidence-guidance.md`，并保留适用的原始调查矩阵。正文按需指向参考资料；原包来源编号不能冒充本次会话引用。
- 明确一次 Run 只激活一个 Skill，不能承诺自动切换专项；模拟记录、公开文档快照、维修声明和现场核实分别解释。Carrier 不声明模拟观测工具，避免把其他模拟设备的数据当作真实机组测量。

`scripts/adapt_domain_skills.py` 与 `scripts/skill_standards.py` 可重建全部八个规范化包及来源清单。重新执行前应审阅现有项目包，因为脚本会覆盖其管理的文件；外部原件只读。

## 启用与实际链路

`application-product.properties` 的默认安装配置已加入六个当前版本。后端启动时通过现有 `SkillRuntime` / `SkillInstallationRepository.installDefault` 完成版本授权，数据库中的已有停用和权限收缩不会被覆盖。显式设置 `DEVICETRUTH_SKILLS_INSTALLATIONS` 的其他部署需要自行包含所需版本。

用户提问 → `GeneralAgentService` 创建 Run → 模型按已启用目录选择并激活专项 → `SkillContextLoader` 加载正文及按需参考 → `TaskOrchestrator` 经只读网关查设备、配置、维护和观测 → 工具登记本 Run 引用 → 模型组织答案 → `AnswerGuard` 核对引用、风险及确诊边界 → 工作台展示。

例如“换过远程 I/O 后画面不更新，通信模块坏了吗？”会选中工业控制器调查，比较工程期望名、在线名、数据交换和维护记录；历史窗口曾恢复不代表现在现场已恢复，更不能直接判定模块损坏。

目录中共有八个当前 Skill。数据库还保留适配验证期间的旧版本授权及运行审计；发现和激活目录按当前注册版本匹配，不能把授权表行数当作当前可用 Skill 数。原有历史会话不删除。

## 验证与修复记录

验证证据保存在 `outputs/validation/domain-skills-20261114/`。真实外部模型、真实本地 PostgreSQL、模拟业务记录与离线规则测试分别记录；没有进行真实设备现场检修验证。

原始验证轮次保留在 `round1/`、`round2/`、`round3/`。首轮六个专项均选对，但充电回答被拒绝；配置和通信场景还存在猜错字段、将单次空查询扩大解释的问题，已补实际字段与查询范围说明。后续一次供电回答没有取得已有告警观测，已补正文中的信号映射与查询要求。

独立诊断发现既有回答校验中的两类误判：`充不上电` 含 `上电` 子串；`该车登记型号为…`、`该车充电状态为…` 被普通系词误当作故障结论。`AnswerGuard` 只对充电失败症状消除该子串，并让设备故障判断使用故障/因果词；新增正反回归，真实上电、强制复位、拆装和无证据故障断言仍拒绝。独立诊断的重放不是原始失败草稿，不能将每次模型失败都归为误判；临时诊断服务已停止。

最终执行与结果：

| 验证 | 实际结果与证据 |
|---|---|
| JDK 21，`mvn -o -q test`，提供隔离 PG 测试环境变量 | 394 项，391 通过、0 失败、0 错误、3 跳过；`maven-adapted-final-result.json` / `maven-adapted-final-tests.log` |
| `python3 -B scripts/verify_domain_skills.py` | 最后一整批 4/6，通过之外的充电、Carrier 均为 `MODEL_OUTPUT_INVALID_AFTER_RETRY`，Carrier 当次在激活前失败；完整批次保留在 `round4/` |
| `python3 -B scripts/verify_domain_skills.py --case charging --case carrier` | 对上述两例做一次针对性复验，2/2 通过；没有更换模型或降级 scripted |
| 汇总当前版本各例最新证据 | 六个专项均已正确激活；五例完成回答，Carrier 缺少设备身份而追问；66/66 检查通过，见 `final-online-evidence.json`。这不是单批 6/6，也不代表已消除模型偶发格式错误 |
| 原件、安装与服务 | 43 份原件摘要一致；六个精确版本 enabled=true；注册器加载八个 Skill / 41 篇参考；前端5173、后端8080均200，见 `installation-and-sources-final.json` |

三个 Maven 跳过项为未启用 `devicetruth.test.real-agent` 的可选模型测试：`AgentToolFeedbackTest.realModelProposalsExerciseReuseRefreshAndBoundedSummary`、`RunRecoveryTest.realModelReadsLongAssetReferenceAfterReplyAndWorkerRestart`、`RunRecoveryTest.realModelUsesReplyToProposeNewSearch`。本任务另行执行的六场景真实模型验证不能替代这三项。首轮离线依赖不齐导致的 Maven 失败日志和中间轮次也保留。

人工对照了最终答案与已取回记录：充电为历史红灯常亮/STOPPED、未查到维修且不能确诊 OBC；胎压维护为左前轮250→290 kPa；ICCU 为 `UNKNOWN_NO_REAL_VIN` 的模拟适用性响应；配置引用 D-120A 登记和 D-120B 更换声明；通信引用命名、数据交换及 HMI 更新的历史窗口；Carrier 未绑定设备时没有读取任意其他资产。

## 当前知识库边界

当前本机知识文档/片段尚未建设，embedding 密钥缺失，检索返回 `EMBEDDING_CONFIGURATION_MISSING`。本次完成 Skill 适配和结构化数据取证接入，没有导入手册、伪造检索成功或用假向量替代真实服务。Skill 遇到不可重试的配置错误应继续利用可用设备记录，并说明无法取得适用技术原文。知识库建设仍是后续独立任务。

Phase 5 原有两项 UAT 决策状态保持不变；本次没有提交、推送或生产部署。
