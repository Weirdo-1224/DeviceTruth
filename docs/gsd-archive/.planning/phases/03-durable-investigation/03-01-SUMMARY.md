---
phase: 03-durable-investigation
plan: "01"
subsystem: asset-facts
tags: [postgresql, asset-registry, field-verification, authorization, citations]
requires:
  - phase: 02-open-rag/02-03
    provides: Markdown 上传、混合检索与补答附件上下文
provides:
  - 带来源、有效期与核实状态的设备身份/组件/配置/维修结构化记录（V8）
  - get_asset / get_configuration / get_maintenance_history 三工具与只读 HTTP API
  - 按 owner/project 的全链路授权与跨归属统一 404
  - 资产事实引用持久化（摘要可核对、挂实际查询资产与时点）
affects: [skills-dual-domain, workbench-quality]
actuals:
  tokens: 0
  tasks: 3
  commits: 0
tech-stack:
  added: []
  patterns:
    - 文档记载与现场核验在数据库 CHECK 层分离，用户口述不可升级为已核实
    - 确认范围按 组件+属性 分组判定，局部确认不扩大为整体确认
    - 导入幂等键细化到事实身份（来源+组件+属性/操作+生效时点）
key-files:
  created:
    - src/main/resources/db/migration/V8__asset_registry_history.sql
    - src/main/resources/db/migration/V9__asset_fact_reference_unbound.sql
    - src/main/resources/db/migration/V10__asset_fact_record_identity.sql
    - src/main/java/io/devicetruth/adapter/persistence/AssetRegistryRepository.java
    - src/main/java/io/devicetruth/adapter/persistence/AssetFactReferenceWriter.java
    - src/main/java/io/devicetruth/application/agent/AssetTools.java
    - src/main/java/io/devicetruth/application/agent/AssetFactService.java
    - src/main/java/io/devicetruth/application/agent/AssetSnapshotImporter.java
    - src/main/java/io/devicetruth/application/agent/AssetFixtureSeeder.java
    - src/main/java/io/devicetruth/core/contract/AssetFacts.java
    - src/main/java/io/devicetruth/api/AssetController.java
    - src/test/java/io/devicetruth/application/AssetToolTest.java
    - src/test/java/io/devicetruth/server/ResourceAuthorizationTest.java
  modified:
    - src/main/java/io/devicetruth/core/capability/Repositories.java
    - src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java
    - src/main/java/io/devicetruth/server/ServerConfiguration.java
    - src/main/java/io/devicetruth/server/ProductConfiguration.java
key-decisions:
  - FIELD_VERIFIED 只能来自 FIELD_CONFIRMATION 且必须带 observed_at，由数据库 CHECK 强制
  - 工具参数解析失败直接拒绝，绝不退化为默认资产/默认时点的替代查询
  - 引用登记保存事实摘要并挂实际查询的资产与时点，审计可核对
patterns-established:
  - 多态引用表按 source_type 区分完整性来源，跨类型外键在多态表上不可表达时以 CHECK+应用层保证
requirements-completed: []
coverage:
  - id: D1
    description: 设备身份/组件主数据与带来源、有效期、核实状态的配置/维修记录（DATA-01/02/03）
    requirement: DATA-01
    verification:
      - kind: integration
        ref: src/test/java/io/devicetruth/application/AssetToolTest.java#21 real PostgreSQL tests
        status: pass
    human_judgment: false
  - id: D2
    description: 资料记载不等于当前装机：仅时点内 FIELD_VERIFIED 可确认，确认范围按 组件+属性 分组（DATA-02/03、TRUST-02）
    requirement: DATA-03
    verification:
      - kind: integration
        ref: AssetToolTest#latestDocumentWithoutFieldVerificationStaysUnconfirmed, fieldConfirmationAtValidTimeIsTheOnlyConfirmedPath, mixedVerificationAcrossComponentsKeepsChecksForUnverifiedGroups, conflictingFieldVerificationsAreNotConfirmed
        status: pass
    human_judgment: false
  - id: D3
    description: 三工具经网关授权调用，参数解析失败拒绝且不执行替代查询；NOT_FOUND/DENIED/INVALID 语义分明（TOOL-03）
    requirement: TOOL-03
    verification:
      - kind: integration
        ref: AssetToolTest#invalidToolArgumentsAreRejectedWithoutQueryingRegistry, invalidArgumentsAndUnauthorizedToolAreRejectedDistinctly, crossOwnerAssetIsIndistinguishableFromMissing
        status: pass
    human_judgment: false
  - id: D4
    description: 双身份全链路授权：跨归属统一 404，客户端无法扩大范围（TRUST-04）
    requirement: TRUST-04
    verification:
      - kind: integration
        ref: src/test/java/io/devicetruth/server/ResourceAuthorizationTest.java#8 real PostgreSQL HTTP tests
        status: pass
    human_judgment: false
  - id: D5
    description: 资产事实引用与配置历史身份：引用按事实记录区分且摘要可核对；同来源多事实/多历史版本不丢（TRUST-02）
    requirement: TRUST-02
    verification:
      - kind: integration
        ref: AssetToolTest#referencesPersistFactExcerptAndActualQueryScope, sameSourceCanCarryMultipleFactsAndReimportIsStable, sameSourceSameTimeMultiFactsRegisterDistinctReferences, sameSourceSameAttributeHistoryIsKeptAndQueryFollowsValidity, toolsAreRegisteredAndCallableThroughTheGateway
        status: pass
    human_judgment: false
  - id: D6
    description: Query-time scoped references preserve both business times and remain idempotent
    requirement: TRUST-02
    verification:
      - kind: integration
        ref: AssetToolTest#configurationReferencesKeepEachQueryTimeAndRemainIdempotent,maintenanceReferencesKeepEachQueryTimeAndRemainIdempotent; 2026-11-13 mvn -o -q test; 278/0/0/0
        status: pass
    human_judgment: false
duration: 0
completed: 2026-11-13
status: complete
---

> Latest 2026-11-13: PASS. Final acceptance below supersedes historical pending statements.

# 03-01 SUMMARY — 设备结构化记录与查询

**状态：** 二轮复审修复后待审查（首轮 P1×4+P2×1、次轮 P1×2 均已修复并补回归）
**日期：** 2026-11-13
**基线 HEAD：** c1b65b7（Phase 2 已关闭）
**需求：** DATA-01 / DATA-02 / DATA-03 / TOOL-03 / TRUST-02 / TRUST-04

> 本文件只记录实际执行并通过的验证。未执行或环境不满足的项目明确标记 NOT_RUN，不做模拟通过。

## 0. 独立 Review 结论与修复（2026-11-13）

### 0.1 首轮 Review（不通过，P1×4 + P2×1）——已修复

| 问题 | 修复 |
| --- | --- |
| P1 工具参数错误被吞后执行默认查询 | `configurationOutcome/maintenanceOutcome` 解析失败直接返回 `INVALID_ARGUMENTS` 失败，不再用全 null 请求重查 |
| P1 引用无事实摘要、挂上下文默认资产/时点 | V10 加 `citation_excerpt` 列；登记改挂 outcome 的实际资产与时点；`references()` 读回落到摘要 |
| P1 任一核实记录即整体确认 | 确认/冲突按 组件+属性 分组：全部组核实且无核实内冲突才 FIELD_CONFIRMED，未确认组逐个保留待核验要求 |
| P1 幂等键过粗静默丢同一来源的多条事实 | V10 唯一键细化：配置=(来源+组件+属性)，维修=(来源+组件+操作+生效时点) |
| P2 SUMMARY 无 frontmatter/coverage、需求编号不对应 | 本文件按 GSD 约定补齐，coverage 映射到 DATA-01/02/03、TOOL-03、TRUST-02/04 |

### 0.2 次轮 Review（仍不通过，P1×2）——已修复

| 问题 | 修复 |
| --- | --- |
| P1 引用身份按 来源+记录时间 查重：同一来源同一时间的多条事实合并为一条引用，摘要串用；跨资产/时点查询复用旧引用 | 引用身份改为**事实记录 ID**（`chunk_id` 派生自 `asset-fact:{recordId}`，查重按 `chunk_id`）；同 run 重复查询同一事实复用同一条引用保持幂等；登记/查重/派生/回读四处一致 |
| P1 配置唯一键仍缺生效时点：同来源同属性的历史版本继续被 `ON CONFLICT` 丢弃 | V11 把配置唯一键扩为 (来源+组件+属性+`valid_from`)；按约束定义定位 V10 约束（PG 自动命名截断不硬编码名字）；仓储 ON CONFLICT 目标同步 |

次轮新增回归：`sameSourceSameTimeMultiFactsRegisterDistinctReferences`（同来源同记录时间的配置×2+维修×1 → 3 条独立引用，正文各自对应，重复查询幂等）、`sameSourceSameAttributeHistoryIsKeptAndQueryFollowsValidity`（同来源同属性两条历史版本全保留，不同时点查询返回对应值，重复导入稳定）。

## 1. 交付内容

### 1.1 数据层

| 文件 | 说明 |
| --- | --- |
| `V8__asset_registry_history.sql` | 新增 `dt_asset` / `dt_asset_component` / `dt_asset_configuration` / `dt_asset_maintenance` 四张表，归属冗余 + 复合外键防跨归属串接，全部带 CHECK 约束 |
| `V9__asset_fact_reference_unbound.sql` | 删除 `dt_evidence_reference.chunk_id` 复合外键（资产事实引用无真实知识切片） |
| `V10__asset_fact_record_identity.sql` | 引用表加 `citation_excerpt`；配置/维修唯一键细化（首轮回复） |
| `V11__asset_configuration_validity_identity.sql` | 配置唯一键加入 `valid_from`，历史版本不再被静默丢弃（次轮回复） |

关键设计取舍：
- `verification_status` 区分 `DOCUMENTED` / `FIELD_VERIFIED`，并有 CHECK 强制 `FIELD_VERIFIED` 必须来自 `FIELD_CONFIRMATION` 且带 `observed_at`——**数据库层面保证“用户口述”“资料记载”不可能被写成现场核实**。
- `valid_from` / `valid_to` 表达业务时点有效性，`recorded_at` 表达入系统时刻，`observed_at` 仅现场核验存在。
- 导入采用 `ON CONFLICT DO NOTHING` 的插入式快照，**不删除、不改写既有来源记录**。

### 1.2 领域与能力层

| 文件 | 说明 |
| --- | --- |
| `core/contract/AssetFacts.java` | 三个工具的公开协议（schemaVersion=1）：`AssetView` / `ConfigurationEntry` / `MaintenanceEntry` 与对应 Outcome |
| `core/capability/Repositories.java` | 新增 `AssetRegistryRepository` 接口与快照 record |
| `application/agent/AssetTools.java` | `get_asset` / `get_configuration` / `get_maintenance_history` 的强类型实现与网关注册 |
| `application/agent/AssetFactService.java` | 服务层查询与组合视图，API 与工具共用同一时点口径 |
| `application/agent/AssetSnapshotImporter.java` | 只读夹具 → 结构化快照的映射，`SIMULATED` 与来源原样保留 |
| `application/agent/AssetFixtureSeeder.java` | 幂等夹具导入，**默认关闭**，仅 `devicetruth.assets.seed-fixtures=true` 时写库 |

### 1.3 接入层

| 文件 | 说明 |
| --- | --- |
| `adapter/persistence/AssetRegistryRepository.java` | PG 实现，全部查询参数化并带 owner/project 条件 |
| `adapter/persistence/AssetFactReferenceWriter.java` | 把查询到的记录登记为当前 run 的可审计 `CONFIGURATION` 引用（含事实摘要与实际查询范围） |
| `api/AssetController.java` | `GET /api/assets`、`/api/assets/{id}`、`/configuration`、`/maintenance` |
| `adapter/persistence/AgentRunRepository.java` | `references()` 扩展支持 `CONFIGURATION` 来源（LEFT JOIN + 摘要回落） |
| `server/ServerConfiguration.java` | `agentCapabilityGateway` 登记资产工具 |
| `server/ProductConfiguration.java` | 资产仓储、引用写入器、工具、服务、Seeder 的 Bean 装配 |

### 1.4 测试

| 文件 | 用例数 |
| --- | --- |
| `AssetToolTest` | 23 |
| `ResourceAuthorizationTest`（T3 / E2E-09） | 8 |

## 2. 需求覆盖

| 需求 | 实现 | 验证 |
| --- | --- | --- |
| DATA-01 身份/组件主数据 | `AssetView`、`get_asset`、V8 表 | `assetQueryReturnsIdentityAndComponentsOnly` |
| DATA-02 配置/维修结构化查询 | `dt_asset_configuration/maintenance` + 时点过滤 | `recordsOutsideValidityWindowAreNotReturned`、`maintenanceHistoryRespectsTimePoint` |
| DATA-03 有效期/记录/观测时间与来源类型 | `valid_from/valid_to/recorded_at/observed_at` + `verification_status` | `fixtureImportKeepsIdentitySourceAndIsSimulated`、`userStatementIsDocumentedNotFieldVerified` |
| TOOL-03 错误语义 | `ASSET_NOT_FOUND`/`PERMISSION_DENIED`/`INVALID_ARGUMENTS` + retryable | `invalidArgumentsAndUnauthorizedToolAreRejectedDistinctly`、`invalidToolArgumentsAreRejectedWithoutQueryingRegistry` |
| TRUST-02 引用完整性 | 引用含事实摘要与实际范围；确认按组判定 | `referencesPersistFactExcerptAndActualQueryScope`、`conflictingFieldVerificationsAreNotConfirmed` |
| TRUST-04 双身份授权 | 跨归属统一 404；请求体/查询参数不可扩大范围 | `ResourceAuthorizationTest` 8 项 |

## 3. 测试执行结果

### 3.1 定向测试

```
mvn -Dtest=AssetToolTest test
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
mvn -Dtest=ResourceAuthorizationTest test
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

真实 PostgreSQL：`postgres 16.15`（Docker `interview-postgres`），每类测试自建随机库、结束 `DROP DATABASE`。

### 3.2 全量回归

```
mvn test
Tests run: 276, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

（276 = 二轮复审修复后轮次：274 + sameSourceSameTimeMultiFactsRegisterDistinctReferences、sameSourceSameAttributeHistoryIsKeptAndQueryFollowsValidity 新增 2 项；迁移基线 V1–V11。）

### 3.3 打包与 CLI

```
mvn -DskipTests package   → target/devicetruth-core-0.1.0.jar
python scripts/verify_cli.py
```
A/B/C/D 四场景结果符合预期（A=RESOLVED、B=RESOLVED、C=CONFLICT、D=NEED_EVIDENCE），缺密钥 FAILED 报告、私有配置文件、非法参数均 PASS。

### 3.4 NOT_RUN

| 项 | 原因 |
| --- | --- |
| 真实 embedding 供应商（DashScope） | 03-01 不依赖 embedding，未执行 |
| 真实在线模型（Qwen） | 03-01 工具链路验证用 scripted/协议替身，未执行 |
| 真实现场设备数据 | 只有模拟夹具，夹具保持 `SIMULATED` |

## 4. 开发过程中确认并修复的缺陷

1. **引用登记外键阻塞（本计划真实根因）**：`dt_evidence_reference.chunk_id` 外键指向知识切片表，资产事实引用插入必然违反外键；异常被 `AssetTools` 广谱 catch 吞掉后表现为"时点过滤全空 + 跨归属看似成功"，曾误导排查走向时区方向。经对照实验（同一 JVM 内 Timestamp.from / setObject(OffsetDateTime) / 显式 SQL 类型三种绑定路径全部命中，驱动级行为正确）排除时区与绑定理论后定位。**修复为 V9 迁移删除该外键**（CHECK"知识引用必须有 chunk_id"保留），并补 `configuration()/maintenance()` 存在性校验（越权/不存在统一 `ASSET_NOT_FOUND`）。绑定统一使用显式 UTC `OffsetDateTime`，语义清晰（与 `Timestamp.from` 等价，均无时区偏移）。
2. **组合视图 asOf 返回 null**：改为返回实际生效时点。
3. **资产视图配置恒为空**：`attribute` 必填约束位置错误。修正为：**模型工具入口必须提供 attribute**，服务层组合视图传 null 表示全部字段。
4. **API 查询整体失败**：API 只读查询没有真实 run，引用登记失败导致查询报错。新增 `runBound(context)`，仅真实 run 登记引用。
5. **`PersistentConversationTest` 迁移断言**：V8–V10 新增后由 7 更新为 10。
6. **推理器来源优先级反转（全量回归 5 项失败的真因，非本计划引入但阻塞回归）**：`agentReasonerName` 原把本地私有文件排在 Spring 显式属性之前，导致 `GeneralAgentApiTest` 显式指定的 `scripted` 被工作目录 `devicetruth.local.properties` 的 `openai` 覆盖，测试实际调用真实模型，脚本化流程断言全部超时。经单独复跑可 100% 复现（排除"多类并行争抢 PG"的猜测）。修复为 **显式属性 > 环境变量 > 私有文件 > 默认值**，属性打包缺省改留空，演示场景（不带命令行参数）从私有文件取模型的行为不变。

## 5. 未决事项

- 本计划**未标记 DONE**，未 commit、未 push，待第二轮独立 Review。
- `AssetFixtureSeeder` 默认关闭，生产环境导入真实资产数据仍需运维显式开启或另行提供导入入口。

## Final fix and review - 2026-11-13

Verdict: PASS for PLAN 03-01. Previous review failures above are preserved as history.
Reference identity now includes fact record ID, actual asset ID and query asOf. Registration
and readback share one key function. Different business times retain independent references;
repeated identical queries reuse the existing reference. No schema migration is required.

Actual final run: `mvn -o -q test`, exit 0; 278 tests, 278 passed, 0 failures/errors/skips.
AssetToolTest: 25 passed (including configuration and maintenance query-time regressions).
Real PostgreSQL 16.15, isolated random databases, V1-V11 migrations.
Initial attempt failed with 16 errors because the helper was missing; fixed before the final run.
`clean test` then failed deleting the target root; classes/reports were removed and a fresh
`test` build completed successfully. These failed attempts do not count as passing evidence.

Requirement scope: structured asset queries and server-fixed owner/project authorization verified.
Full requirement rows remain partial where industrial datasets, recordedAt boundary semantics,
DATA_STALE/AMBIGUOUS, real authentication or wider trust coverage still require later acceptance.
Phase 03 is not complete; 03-02/03-03 have not been executed.
External models, real field data and browser E2E were not rerun. Deferred Phase 02 safety risks remain.

Final package and CLI: `mvn -o -q -DskipTests package` exit 0; `python scripts/verify_cli.py` exit 0, A/B/C/D and negative checks PASS. Native `state advance-plan`: advanced=true, previous_plan=1, current_plan=2.
