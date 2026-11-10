---
status: complete
phase: 01-baseline-contracts
source: [01-01-SUMMARY.md, 01-02-SUMMARY.md, COVERAGE.md]
started: 2026-11-10
updated: 2026-11-10
---

## Current Test

[testing complete]

## Tests

### 1. 冷启动旧 CLI 并复现 B 场景
expected: CLI help 可读，scripted B 返回 COMPLETED/RESOLVED/SIMULATED，报告采用 DEMO-D-120B。
result: pass
source: user
reported: "第1项通过"
attempts: 用户首次操作将两条命令拼在一行，且代理的clean验证临时移除JAR，报Unable to access jarfile；首次不计通过。重新打包并提供显式JDK21单行命令后，用户明确回复“第1项通过”。保留这次人工确认，不因策略调整改写成代理代确认。

### 2. 继承场景与安全负例的结果确认
expected: 当前打包验证 A/B 各选对应规则，C 保留冲突不选规则，D 保留缺证，E 安全负例及越权工具被拒；按明确断言由代理自动核验。
result: pass
source: automated
coverage_id: P1-LEGACY
verification: 本轮实际mvn clean test/package的AcceptanceTest及python scripts/verify_cli.py；A/B分别A/B规则、C=CONFLICT、D=NEED_EVIDENCE，来源/状态/事件一致性断言通过，均为SIMULATED。
history: 原第2项曾发出人工提示但未收到确认；2026-11-10用户调整策略后，该提示由本轮实际自动验证取代，不冒称用户同意。

### 3. Phase 1 产品边界与设计集中确认
expected: 原文必填/资产可空，服务器控制身份/权限，设备工具只读；Phase1交付QUEUED存储切片，唯一循环选演进TaskOrchestrator。出口通过后提交Git并仅执行02-01知识接口；不将M0存储、模拟/原型结果称为通用Agent、真实现场或生产恢复。02-02/02-03不执行。
result: pass
source: user
reported: "上述产品边界与设计符合预期"
coverage_id: P1-PRODUCT
rationale: 仅确认产品交付边界和设计取舍，技术结果不再交给用户逐项验收；原有后续实施授权有效，无需重复申请权限。

### 4. 产品 API 保存自由原文和 QUEUED 任务
expected: 代理实际向product服务创建会话，提交无故障码/资产的中文原文得到202/QUEUED，GET原文和同runId一致，dataNature=SIMULATED，M0不自动诊断。
result: pass
source: automated
coverage_id: P1-PERSISTENCE
verification: 本轮真实PG PersistentConversationTest 8项、旧HTTP16项及打包双进程HTTP检查均通过；覆盖空库迁移、原文/emoji、可空资产、归属、事务/并发与严格JSON，不需要用户重复操作。

### 5. 重启后读取及非法输入拒绝
expected: 两次独立Java进程读回同会话/原文/QUEUED；重复请求键409，伪造身份/非法JSON400且无额外写入；跨归属、回滚与并发由代理实际测试核对。
result: pass
source: automated
coverage_id: P1-RESTART
verification: 本轮phase1-packaged-check.py与PersistentConversationTest实际通过；不可连接DB且product环境下CLI B仍RESOLVED；独立Review CR-01已修复复审clean，安全核验6/6关闭。真实PG不等于真实现场设备验证。

### 6. Runtime 原型自动复现
expected: clean后两候选六样例及负例重跑通过；输出固定摘要一致，保存代码/固定输入/复现命令，不声称已实现生产恢复。
result: pass
source: automated
coverage_id: P1-RUNTIME
verification: 本轮RuntimeComparisonTest实际执行，SHA256=830d11a9aa9a24cd002ca67e881a90c3928361d45ed8ff9752772d399c773aeb，与01-01历史固定输出一致。

## Summary

total: 6
passed: 6
issues: 0
pending: 0
skipped: 0

## Gaps

无用户报告的业务缺陷；首次JAR暂时缺失及恢复记录保留在第1项。独立Review的CR-01另记于01-REVIEW.md，不冒充用户报告。

## Acceptance policy migration

2026-11-10用户明确要求技术验收由代理自动执行，尽量每Phase一次集中人工确认。已按项目ACCEPTANCE.md和GSD原生SUMMARY coverage迁移，不删除已确认结果、不将未收到的回复写成用户通过。保留原第1–5项编号，并增加第6项Runtime自动证据；用户随后明确回复“上述产品边界与设计符合预期”，第3项集中确认已通过。JUnit154项与本文件6条验收记录分别统计；其中4条自动通过、2条真实用户确认。

## Session authorization

2026-11-10 用户授权 Phase 1 逐项用户验收、独立 Review、问题修复与阶段验证。
仅在出口全部满足后提交当前 Git，再执行 02-01；不执行 02-02/02-03。
自动测试和独立 Review 分别记录，不预填用户确认。
