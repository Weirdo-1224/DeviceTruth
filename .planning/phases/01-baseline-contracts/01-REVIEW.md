---
phase: 01-baseline-contracts
reviewed: 2026-11-10T14:18:00Z
re_reviewed: 2026-11-10T14:29:00Z
depth: standard
diff_base: a9607cf7b1631c221a9f9865036751642772e352
files_reviewed: 21
files_reviewed_list:
  - .gitignore
  - .env.example
  - README.md
  - pom.xml
  - compose.yaml
  - src/main/java/io/devicetruth/app/DeviceTruthApplication.java
  - src/main/java/io/devicetruth/server/DeviceTruthServerApplication.java
  - src/main/java/io/devicetruth/server/ServerConfiguration.java
  - src/main/java/io/devicetruth/server/ProductConfiguration.java
  - src/main/java/io/devicetruth/api/AgentConversationController.java
  - src/main/java/io/devicetruth/application/agent/GeneralAgentService.java
  - src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java
  - src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java
  - src/main/java/io/devicetruth/adapter/Json.java
  - src/main/resources/application.properties
  - src/main/resources/application-product.properties
  - src/main/resources/db/migration/V1__agent_conversations.sql
  - src/test/java/io/devicetruth/server/PersistentConversationTest.java
  - src/test/java/io/devicetruth/server/ServerHttpTest.java
  - src/test/java/io/devicetruth/spike/RuntimeComparisonTest.java
  - src/test/resources/runtime-comparison/cases.json
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
  necessity: 0
initial_findings:
  critical: 1
  warning: 0
  info: 0
  total: 1
resolved_findings: [CR-01]
unresolved_findings: []
status: clean
---

# Phase 1 独立代码审查

## Summary

独立审阅了 01-01 的 Runtime 比较测试、01-02 的产品 HTTP 持久化切片及配置/迁移，共 21 份源码、测试或运行配置文件；其中 `Json.java`、`application.properties` 为未修改的调用链依据。范围包含指定基点后的 Phase 1 提交以及当前未提交、未跟踪实现。另逐项对照两份 PLAN/SUMMARY、CONTRACTS、ADR、RUNTIME-SPIKE、COVERAGE 与 V1.0 PostgreSQL 设计相关章节。

初审发现 1 项必须修复的输入协议问题 CR-01。主代理随后以真实 PG HTTP 测试复现、修复并执行回归；本独立代理针对修复与测试差异完成窄范围复审，确认该问题已解决，当前未解决发现数为 0，最终状态为 `clean`。frontmatter 的 `findings` 表示当前未解决数，`initial_findings` 保留初审问题数，下面保留完整发现历史。

消息/run/event 使用同一事务与会话行锁；仓储查询均带服务器 owner/project；复合外键防止跨归属连接；错误响应未透传 SQL 或异常正文。初审未发现其他可证实缺陷。独立 Review 通过不代替逐项用户验收或完整阶段出口核验。

M0 明确只保存 QUEUED，没有 worker、RAG、生产真实认证或完整幂等重放；这些既定后续范围不作为缺陷。Runtime 测试的 JSON 重建仅证明原型，不当作生产持久恢复通过。未发现有证据支持的 necessity 类问题。

## Narrative Findings (AI reviewer)

### CR-01：BLOCKER — 产品 HTTP 请求没有使用项目的严格 JSON 解析规则

**Status:** RESOLVED，2026-11-10 窄范围独立复审确认。以下 Issue 为修复前的历史事实。

**File:** `src/main/java/io/devicetruth/api/AgentConversationController.java:40`

**Supporting files:** `src/main/java/io/devicetruth/application/agent/GeneralAgentService.java:17`、`src/main/java/io/devicetruth/application/agent/GeneralAgentService.java:62`、`src/main/resources/application.properties:3`、`src/main/java/io/devicetruth/adapter/Json.java:18`、`src/test/java/io/devicetruth/server/PersistentConversationTest.java:224`。

**Severity:** BLOCKER（错误行为：非法协议输入可被转换后持久化，原始用户输入语义被改变）。

**Issue:** Controller 的 `@RequestBody AgentTaskInput` 交给 Spring MVC 自动配置的 Jackson mapper。仓库没有向 MVC 注册 `Json.MAPPER` 或等价的严格 Jackson 配置；`application.properties` 只开启未知字段拒绝。项目原有 `Json.MAPPER` 则另外开启重复键检测、尾随 token 拒绝，并关闭浮点转整数和数字/布尔转字符串。`GeneralAgentService.submit()` 拿到 DTO 后只检查已经转换出的 `Integer/String` 值，无法恢复或识别原始 JSON token 类型与重复字段。

因此下列请求在默认 MVC mapper 下会被错误解读：

```json
{"schemaVersion":1.5,"userMessage":123}
{"schemaVersion":"1","userMessage":true}
{"schemaVersion":1,"userMessage":"第一条原文","userMessage":"第二条原文"}
```

浮点 `1.5` 可被截断成支持的版本 `1`，数值/布尔正文可变为字符串，重复 `userMessage` 只保留最后一个。一个合法对象后跟第二个 JSON 值也缺少项目统一的尾随 token 拒绝配置。最终可能出现 202 和持久消息，而这些输入应在任何写入之前返回 400。

**Contract:** CONTRACTS §1 明确 `schemaVersion: 1`、`userMessage: string`、用户原文原样保存，§7 要求非法请求返回 HTTP 400；01-02-T2 要求先验证后原子保存，不允许静默改写/丢弃请求信息。原有严格解析器也已把这些边界作为项目明确约定。

**Fix:** 在独立 HTTP 服务的装配入口为 MVC 注册 `Json.MAPPER.copy()`，或通过统一 Jackson 配置完整应用同样的重复键、尾随 token 和标量类型规则。不要只在 service 中增加值检查，因为转换和重复字段覆盖发生在 DTO 构造之前。修复应复用现有严格策略，避免另外维护一套规则；同时跑旧 HTTP 回归以确认共享 mapper 变更影响。

**Required verification:** 在 `PersistentConversationTest` 新增**原始 JSON 字符串**的 HTTP 请求回归，覆盖整数/布尔 `userMessage`、字符串/浮点 `schemaVersion`、重复字段、尾随 JSON；逐项断言 400，以及消息数、run 数、会话版本未增加。保留有效中文、空格/换行、emoji 和 8000 码点边界的成功测试。当前测试 helper 多数发送 Java `Map`，无法生成重复键且未覆盖上述 token 类型，所以既有 7 项不能证明该边界。

**Initial evidence and limits:** 初审完成 Controller→MVC mapper 配置→DTO→service 的静态调用链核对，并对照项目严格 mapper 的实际配置。初审期间尝试用 JShell 对 Spring builder 进行独立探测，但本机 JShell 进程未成功执行探针；该尝试**不计为复现通过**。后续主代理执行的真实复现、修复与回归见下一节，不覆盖或抹去初审验证局限。

## CR-01 修复与独立复审

**复现证据：** 主代理先添加原始 JSON HTTP 回归，在修复前运行真实 PG 测试；`outputs/validation/phase1-cr01-before.log` 明确记录 `{"schemaVersion":1,"userMessage":123}` 预期 400、实际 202，1 项测试失败。独立代理已读取该失败日志，原始缺陷得到实际触发验证。

**修复代码：** `src/main/java/io/devicetruth/server/ServerConfiguration.java:39` 新增 `@Bean ObjectMapper httpObjectMapper() { return Json.MAPPER.copy(); }`。这让 Spring MVC 使用现有严格 mapper 的副本，保留重复键检测、尾随 token 拒绝、浮点/标量转换限制及原有 JavaTime 配置，修复发生在请求 DTO 构造之前，未另建解析规则或改变持久化事务。

**回归测试：** `src/test/java/io/devicetruth/server/PersistentConversationTest.java:219` 新增 `malformedJsonIsRejectedWithoutPersistingCoercedOrAmbiguousInput`。测试通过 `String` 请求体发送原始 JSON，覆盖数字/布尔正文、字符串/浮点 schemaVersion、重复 userMessage 与尾随 JSON 六种输入；每一种都断言 HTTP 400、`INVALID_REQUEST`，并立即读回确认 messages/runs 为空且会话 version 为 0。已有有效原文、Unicode/emoji、8000 码点及真实 PG 重开测试保留。另补消息 GET 成功和非空 contextRefs 拒绝，未削弱原断言。

**独立核验：** 本次只读取上述两个代码文件的修复/测试差异及实际日志/XML，未重跑全部 Phase 1 审查。已核对：

- `outputs/validation/phase1-cr01-after.log` 的修复后运行记录；当前 Surefire XML 中 `PersistentConversationTest` 为 8/0/0/0，旧 `ServerHttpTest` 为 16/0/0/0。
- 当前 `target/surefire-reports/TEST-*.xml` 合计 154 tests、0 failures、0 errors、0 skipped。主代理记录 `mvn -q clean test` 与 `mvn -q package` 均成功；本独立代理核对现存 XML 和日志，不冒称自己重新执行这两条命令。
- `target/cli-verification.json` 保留旧 CLI 验证结果；`outputs/validation/phase1-packaged-persistence.json` 明确记录新 JAR 跨进程原文保留、同一 run 重启后仍 QUEUED、无数据库 CLI 为 RESOLVED、服务进程已停及凭据扫描通过。这些由主代理执行，仅作修复影响的旁证。

**最终结论：** CR-01 的实际失败样例、共享解析器修复及六类 HTTP/事务负例形成闭环；未发现该修复引入的新问题。初审 1 项 BLOCKER 已解决，当前 0 项未解决，独立 Review `clean`。后续逐项用户验收与阶段出口状态由主代理按实际确认维护。

## Verification Scope

- 独立代理在初审和复审中均未运行 Maven、启动数据库、读取真实模型私有配置或输出凭据；实际测试由主代理执行，复审核对了其代码与现存结果。
- 独立代理没有修改业务源码、迁移、测试、规划状态或 Git；仅创建并更新本报告。
- 初审 `issues_found`，窄范围修复后复审 `clean`；用户验收与阶段关闭由主代理另行核验，不能用本报告代填用户确认。

_Reviewer: 独立 gsd-code-reviewer；日期：2026-11-10（Asia/Shanghai）。_
