---
phase: 01-baseline-contracts
plan: "01"
subsystem: contracts-runtime-baseline
tags: [baseline, contracts, runtime-spike, java21]
requires: []
provides:
  - "当前 legacy CLI/HTTP 与安全回归证据"
  - "自由输入、权限、风险、引用、等待与恢复最小契约"
  - "固定版本 SDK 与演进循环的可重跑比较及唯一 Runtime ADR"
affects: [01-02, 02-01, 02-02, 03-02, 03-03]
tech-stack:
  added: []
  patterns: [single-controlled-loop, server-owned-permissions]
key-files:
  created:
    - .planning/phases/01-baseline-contracts/BASELINE.md
    - .planning/phases/01-baseline-contracts/CONTRACTS.md
    - .planning/phases/01-baseline-contracts/RUNTIME-SPIKE.md
    - .planning/phases/01-baseline-contracts/ADR-runtime.md
    - src/test/java/io/devicetruth/spike/RuntimeComparisonTest.java
    - src/test/resources/runtime-comparison/cases.json
  modified:
    - .gitignore
    - README.md
    - .planning/PROJECT.md
key-decisions:
  - "以现有 TaskOrchestrator 演进为唯一产品主循环，继续复用 AgentScope Model API"
  - "保留旧模型私有配置兼容，新 PG/embedding 仅服务端环境注入"
requirements-completed: [BASE-01, BASE-02]
requirements-partial: [TRUST-06]
completed: "2026-11-09"
review_status: independent_review_not_run
---

# 01-01 完成记录

**本计划三项任务已执行并完成：复核当前基线、冻结通用契约、实测两种 Runtime 后记录唯一方向。** 用户只授权这一份计划；01-02 及后续12份未执行，M0 阶段出口未完成。起点 HEAD `a9607cf7b1631c221a9f9865036751642772e352`，开始工作树干净；未提交 Git、未部署。

## 交付及验证

| 任务 | 实际交付 | 结果 |
|---|---|---|
| T1 基线 | [BASELINE](BASELINE.md)、README 更新 | JDK/HEAD/设计摘要、旧 CLI/HTTP/Actuator、模拟性质、配置策略与当前结果已记录 |
| T2 契约 | [CONTRACTS](CONTRACTS.md) | 自由原文必填/资产可空；服务端身份授权；三动作；来源时间/风险/错误；同run补答三元组；事务/读取；M1/M2边界与累计预算/schema/legacy明确 |
| T3 原型/选型 | [RUNTIME-SPIKE](RUNTIME-SPIKE.md)、[ADR](ADR-runtime.md)、保留测试/JSON | 两候选真实编译运行；六样例×两候选；两次clean成功输出完全一致；选演进现有循环，无生产双栈 |

实际命令（均在当前进程设置 JAVA_HOME/PATH 到 `.tools/jdk-21.0.13.0.2+1`；详细命令在 BASELINE/RUNTIME-SPIKE）：

| 命令 | 退出码 | 结果 |
|---|---|---|
| mvn -version | 0 | JDK21.0.13.0.2 / Maven3.9.9 |
| mvn -q test（修改前） | 0 | 145/0/0/0，28 suites |
| mvn -q package（修改前） | 0 | 重新构建 CLI JAR |
| python scripts/verify_cli.py（首次新 JAR） | 0 | A/B/C/D、参数、缺Key、假Key本机HTTP私有文件合同通过 |
| mvn -q '-Dtest=RuntimeComparisonTest' clean test（首轮） | 1 | SDK fake 工具参数适配失败，未计通过 |
| mvn -q '-Dtest=RuntimeComparisonTest' test（两轮定位） | 1 / 1 | 定位 SDK raw content 与参数校验边界 |
| mvn -q '-Dtest=RuntimeComparisonTest' clean test（修正后连续两次） | 0 / 0 | 输出字节完全一致；每轮1测试方法，12次比较执行 |
| mvn -q test（最终） | 0 | **146 tests / 0 failures / 0 errors / 0 skipped，29 suites** |
| mvn -q package（最终） | 0 | 同上146项通过；CLI Start-Class、私有文件未打包确认 |
| python scripts/verify_cli.py（最终 JAR） | 0 | A=RESOLVED/A规则、B=RESOLVED/B规则、C=CONFLICT、D=NEED_EVIDENCE；E由真实运行的继承测试覆盖 |
| 独立 HTTP Main + Python localhost GET 断言 | 0 | /api/health=UP/SIMULATED，/actuator/health=UP，index=200；进程已关闭 |
| GSD verify plan-structure / validate consistency / roadmap analyze / state-snapshot | 0 | 结构/一致性无错误警告；1 SUMMARY、13计划、进度8%、完成阶段0，下一计划01-02 |
| git diff --check；单独Python链接/UTF-8/需求/源码hash核验 | 0 | 63需求各有唯一主计划，文档链接/编码有效，原95份源码hash与指定设计hash未变 |

新原型正向两个候选均使用4次模型调用、2次实际工具读取；未知工具、写工具、跨资产、非法参数均零handler读取；伪造引用不能完成回答。等待后新建实例，旧问题/引用/步数保留；错run/问题/版本及重复补答被拒绝。固定输出 SHA256 `830d11a9aa9a24cd002ca67e881a90c3928361d45ed8ff9752772d399c773aeb`。

## 关键代码与实际运行链路

以“充电经常中断，没有故障码”为例，固定 cases.json 提供相同用户问题和模拟资料。RuntimeComparisonTest.fakeModel 只读取模型实际收到的上下文，先提出读资料动作；Journal.call 把动作交生产 CapabilityGateway.execute，网关校验已注册/只读/参数/资产后才读取固定 Observation 并记入引用。Journal.propose 接收追问后记 WAITING，执行方法返回；测试将 snapshot 编成JSON，再创建新 Journal/Model，SDK候选也新建Agent。用户回答“直流快充”通过 reply 的 runId/问题/版本校验后进入同run QUEUED/RUNNING，旧引用和已用步数继续保留；第二次资料读取后 ANSWER 必须只引用已取得的ID才能完成。

演进候选由 AgentScopeStep.complete + 测试内单步 while 驱动；SDK候选由 ReActAgent.call 自己循环，Toolkit bridge 与 Hook 接入同一网关和状态检查。它们是可比较测试原型，未给现有生产 TaskOrchestrator 增加通用等待恢复功能。

真实现有 CAR-B CLI 仍是 DeviceTruthApplication.execute→JSON与规则→TaskOrchestrator.run→Reasoner.propose→Gateway→EvidenceManager重建当前配置/失效旧依赖→StateManager提交→DecisionGuard验证B规则→RunReportWriter输出。HTTP经ConversationController/Flow/DiagnosisRunService后进入同一旧Core；原型没有接入这个生产HTTP链。

## 决策、偏差与接续

- 选择演进现有循环：两候选都能工作，但本次SDK桥接/Hook仍需保留业务边界，适配代码更多。后续02-02的TaskOrchestrator文件清单/key_link已与选择一致，无需新增第二Runtime类。
- 未变更生产源码、pom、数据或规则；原型放测试目录，运行输出留忽略的target/outputs。未新增依赖或读取真实私密值。
- `.gitignore` 追加 `outputs/validation/`，确保本轮比较JSON与运行日志不会作为源文件提交；没有扩大忽略其他源码。
- 原型首次失败后补齐SDK工具块raw content，保留失败记录，未放宽断言；与目标无范围偏移。
- TRUST-06 本计划所需策略与旧配置脱敏回归完成；新PG/embedding/上传端到端实现仍属部分实现，REQUIREMENTS未虚报完成。
- 已同步STATE/ROADMAP/PROJECT/接续入口。独立Review未执行，自检/离线测试不称独立PASS。旧规划校验脚本有“全部未实施/无SUMMARY/业务目录无新增”的历史断言，本轮不运行它冒充适用校验；采用GSD结构/一致性和单独链接/范围核验。

未验证：外部真实模型、真实embedding/RAG、PG/pgvector、数据库原子性、生产自由目标API/WAITING、跨进程恢复/租约/CAS/幂等/取消、Vue/SSE与完整V1.0 E2E。下一步为01-02，须在新的实施授权范围内进行。
