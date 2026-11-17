---
phase: "01"
slug: baseline-contracts
status: complete
nyquist_compliant: false
wave_0_complete: true
created: 2026-11-10
---

# Phase 1 本轮验证证据（2026-11-10）

> **策略调整后的最新执行：** 依用户新要求与[项目策略](../../ACCEPTANCE.md)，技术项由代理自动运行并记录；CLI B及产品/设计集中确认均已由用户明确认可。实际再次执行`mvn -q package`，30 suites共154/0/0/0；随后`python scripts/verify_cli.py`全部通过，另以新日志批次`phase1-policy-*`实际重跑打包JAR双进程PG/无DB CLI/凭据扫描并通过，没有只引用先前报告。新会话a16084b9-4d82-43ad-804e-0242bc64e934、run402452c7-f871-4325-bbbd-0a4d31910896；Maven日志phase1-policy-package.log。原生coverage分类4自动/1人工且无schema错误，5个分类器正负探针通过（pass、fail、unknown、人工判断、非法布尔字段）。JUnit/探针/UAT分别计数；简洁报告见[01-ACCEPTANCE](01-ACCEPTANCE.md)。下方详细执行历史保留，不冒称又执行了一次clean。

本记录对应用户授权的Phase 1逐项验收、独立Review与修复复验。自动验证已通过，独立Review最终clean；用户已明确回复“上述产品边界与设计符合预期”，[01-UAT](01-UAT.md)6/6通过。正式阶段关闭仍须由主代理完成VERIFICATION及原生出口检查。Git提交和02-01实施以全部出口满足为前提；02-02/02-03不在授权范围。

## 独立Review与修复

[01-REVIEW](01-REVIEW.md)由独立gsd-code-reviewer代理完成，覆盖21份源码/测试/运行配置，并对照Phase 1计划、契约及产品设计。初审1项BLOCKER（CR-01），无其他已证实问题。

Spring MVC默认mapper会把数字正文转为字符串。新增真实HTTP/PG测试发送`{"schemaVersion":1,"userMessage":123}`，修复前返回202（预期400），测试失败。`ServerConfiguration.httpObjectMapper()`现在返回现有`Json.MAPPER.copy()`，在DTO构造前拒绝隐式类型转换、重复字段和尾随JSON；未改模型配置、网关、设备事实或诊断规则。

`PersistentConversationTest.malformedJsonIsRejectedWithoutPersistingCoercedOrAmbiguousInput`使用原始JSON依次覆盖数字/布尔正文、字符串/浮点版本、重复字段和尾随对象，逐项断言400与消息/run/会话版本均零。原有中文/换行/空格/emoji/8000码点成功断言保留；同时补了GET消息成功响应和非空contextRefs拒绝的断言。独立代理复审代码、失败日志及当前Surefire，确认CR-01已解决，最终未解决发现0。

## 实际命令与结果

Maven实际使用Microsoft JDK21.0.13.0.2、Maven3.9.9。直接`java`实测仍是Java17，因此手工操作必须显式使用`.tools/jdk-21.0.13.0.2+1/bin/java.exe`，不能以Maven的JDK推断终端java。

数据库复用既有`interview-postgres`（PG16.15/pgvector0.8.7）。先启动本机Docker Desktop及该容器，没有拉镜像。PG凭据只在内存中读取并注入子进程环境；每轮真实测试创建自己的随机`devicetruth_test_*`库，结束后删除自身测试库，未在`interview_guide`写入。

| 命令/检查 | 结果 | 证据与限制 |
|---|---|---|
| `mvn -version` | 0 | JDK21.0.13.0.2 / Maven3.9.9 |
| `mvn -q '-Dtest=PersistentConversationTest#malformedJsonIsRejectedWithoutPersistingCoercedOrAmbiguousInput' test`（修复前） | 1 | 1项失败；预期400，实际202，真实HTTP→PG复现 |
| `mvn -q '-Dtest=PersistentConversationTest,ServerHttpTest' test`（修复后） | 0 | PG8项、旧HTTP16项，均通过 |
| `mvn -q clean test` | 0 | 30 suites，154 tests / 0 failures / 0 errors / 0 skipped |
| `mvn -q package` | 0 | 再次154/0/0/0，生成当前JAR |
| `python scripts/verify_cli.py` | 0 | help、A/B/C/D、非法参数、两模型缺Key不降级、私有文件本机HTTP合同；无外部模型调用 |
| `python outputs/validation/phase1-packaged-check.py` | 0 | 两次独立Java进程重启读同一原文和QUEUED run，health正常；不可连DB且product环境下CLI B仍成功；凭据扫描通过 |
| `python outputs/validation/phase1-environment-check.py` | 0 | 原业务库public表17，随机测试库0，DeviceTruth Flyway成功记录1；Compose config通过，不启动第二PG |
| `verify phase-completeness 1` | complete=true | 2 plans / 2 summaries，无缺失 |
| `verify artifacts`（01-01、01-02） | 全部通过 | 分别4/4与3/3产物存在且非占位；不代替行为测试 |
| `check api-coverage-verify-pre` | passed=true/block=false | 38项，21 INTEGRATE、17 OPT-OUT |
| `validate consistency` | passed=true | 无错误/警告 |
| `git diff --check` | 0 | 最终文档更新后仍须再核验 |

Maven实际由忽略目录`outputs/validation/phase1-run.py`包装执行，将容器凭据仅注入子进程测试环境并保存日志。该脚本不提交；永久回归资产保留在src/test，手工配置环境的复现方式见根README。旧`01-02-*`日志与历史记录未覆盖，本轮使用`phase1-*`日志。

Runtime原型在clean后重新生成`target/spikes/runtime-comparison.json`，SHA256仍为`830d11a9aa9a24cd002ca67e881a90c3928361d45ed8ff9752772d399c773aeb`；与01-01历史两轮结果完全相同。两候选六样例仍为测试原型，不宣称生产WAITING或执行恢复。

## CLI与跨进程结果

| 场景 | 结果 | 规则 | 步数/事件 |
|---|---|---|---|
| A | RESOLVED | DEMO-D-120A | 7 / 34 |
| B | RESOLVED | DEMO-D-120B | 7 / 34 |
| C | CONFLICT | 无 | 7 / 35 |
| D | NEED_EVIDENCE | 无 | 6 / 29 |
| E安全负例 | AcceptanceTest通过 | 不允许未知工具/伪造证据结案 | 无CLI场景E |

## Test Infrastructure / Per-Task Validation Map

框架为JUnit5/AssertJ，Maven Surefire读取`target/surefire-reports/TEST-*.xml`；真实PG测试必须配置DEVICETRUTH_TEST_PG_BASE_URL/USERNAME/PASSWORD。新旧测试共30个suite，所有154项均实际执行，没有跳过PG冒充离线通过。

| Task ID | Plan / Wave | Requirement | Test / Evidence | Type | Status |
|---|---|---|---|---|---|
| 01-01-T1 | 01-01 / 1 | BASE-02 | AcceptanceTest、ServerHttpTest、verify_cli.py及打包健康检查 | automated | COVERED |
| 01-01-T2 | 01-01 / 1 | TRUST-06（本阶段契约） | CONTRACTS经独立Review；UAT第3项用户明确回复“上述产品边界与设计符合预期” | manual-only | CONFIRMED |
| 01-01-T3 | 01-01 / 1 | BASE-01 | RuntimeComparisonTest两候选六样例、clean后摘要一致；AgentScopeStepTest/模型HTTP合同 | automated | COVERED |
| 01-02-T1 | 01-02 / 2 | STORE-01、TRUST-06（PG配置边界） | 真实PG迁移/重复启动、独立库、打包凭据检查、Compose config | automated | COVERED |
| 01-02-T2 | 01-02 / 2 | STORE-02、WEB-06（M0六路由） | PersistentConversationTest共8项，含消息/run/event原子性、严格JSON、归属、重复并发、GET消息 | automated | COVERED |
| 01-02-T3 | 01-02 / 2 | STORE-05、BASE-02 | PG服务重开、打包JAR双进程、无DB CLI B、CLI A–D/E继承测试 | automated | COVERED |

人工项不是新增业务缺陷，也不为文字合同编写镜像测试；按用户新策略集中确认，现已收到明确认可。WEB-06/TRUST-06的完整产品能力仍为部分实现，本表只覆盖M0本阶段切片，不把后续实现缺口虚报完成。覆盖构成为5项自动行为证据、1项已确认的人工设计判断；保留nyquist_compliant=false以明确并非全部自动覆盖，不将用户认可伪装成自动测试；当前没有待确认项。

## Validation Audit

| Metric | Value |
|---|---|
| Gaps found | 1（初审CR-01，已修复） |
| Resolved | 1（真实HTTP/PG回归及独立复审） |
| Escalated | 0 |
| Manual-only pending | 0（UAT第3项已由用户明确认可，仍属manual-only） |

跨进程会话`b1f3ddfa-c13d-4103-81ac-9f4f9cdd9c61`，run `ab89dbc1-17c1-4d83-a495-1c7882ec328d`；第二进程读回同一原文/QUEUED状态。无DB的CLI B报告`89867e3d-7a2d-4a5b-9252-3a74f2369421`。自动验收Java进程均已关闭，PG容器保留运行。

## 运行链路与阶段边界

例如用户输入“充电经常中断，没有故障码，先看哪些资料？”：HTTP严格mapper先确认JSON结构/类型；`AgentConversationController.submit`接收DTO；`GeneralAgentService.submit`校验原文、schema、可空资产及服务器会话归属，然后在同一事务锁会话行。`ConversationRepository.append`原样存文本并递增会话版本，`AgentRunRepository.enqueue`写QUEUED与首事件；任一失败整体回滚。GET从PG读回，因此Java进程退出后仍保留。当前没有worker，不能产生知识答案。

旧CAR-B经CLI的`DeviceTruthApplication.execute`装配JSON仓储和scripted推理器，再由`TaskOrchestrator.run`逐步接收动作，经Gateway只读取证、EvidenceManager重建事实、DecisionGuard核验B规则，StateManager提交快照，RunReportWriter写模拟报告。新mapper仅加强HTTP边界，不改变此循环。

GSD `verify key-links 01-02-PLAN`的名称扫描未识别Controller→service→Repository的间接链路及Flyway约定发现SQL文件，返回2条未验证。已按实际调用手工追踪并由真实HTTP/PG/迁移/重启测试验证，不将该启发式扫描虚报PASS；阶段验证报告保留这一限制。

## 尚待确认与未验证

- 用户第1项CLI B明确通过，首次clean临时移除JAR及修复历史保留。依新策略，技术项自动记录；第3项集中确认亦已收到“上述产品边界与设计符合预期”，UAT共6条全部通过，不要求用户重复技术操作。
- 外部真实LLM、真实embedding/RAG、通用Agent/WAITING、Vue/SSE、跨进程执行恢复及生产真实认证未在本阶段验证。
- Git尚未提交；Phase 1尚未关闭，02-01尚未开始，02-02/02-03不执行。
