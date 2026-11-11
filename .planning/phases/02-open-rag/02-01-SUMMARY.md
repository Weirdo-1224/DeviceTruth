---
phase: 02-open-rag
plan: "01"
subsystem: knowledge-retrieval
tags: [postgresql, pgvector, embedding, rag, authorization, citation]

requires:
  - phase: 01-baseline-contracts
    provides: "V1会话/消息/QUEUED任务/事件表、product profile装配、服务器固定身份与复合外键所有权模型"

provides:
  - "V2迁移：上传文件元数据、知识文档/切片（vector(1024)+HNSW）、证据引用"
  - "MD/TXT 章节切片导入与固定 embedding profile 向量化（事务写入、同内容去重）"
  - "受 owner/project/领域/型号约束的向量召回，候选过滤先于排序"
  - "search_knowledge / get_document_excerpt 强类型工具合同与JSON Schema"
  - "检索即登记 evidence_reference；原文读取只允许本run已取集合"

affects: ["02-02 Runtime工具注册与Agent接入", "02-03 PDF/混合检索与真实embedding验收", "03-01 设备事实引用", "05-02 检索质量评测"]

actuals:
  tokens: 24000
  tasks: 3
  commits: 0

tech-stack:
  added: []
  patterns:
    - "向量文本字面量 + CAST(? AS vector)，不引入额外JDBC扩展"
    - "数据库列维度是索引维度唯一事实来源（format_type 解析），配置不符前置失败"
    - "引用登记 ON CONFLICT (run_id,owner,project,chunk) 幂等，重复检索复用 referenceId"

key-files:
  created:
    - src/main/resources/db/migration/V2__knowledge_sources.sql
    - src/main/resources/db/migration/V3__knowledge_embedding_identity.sql
    - src/main/resources/db/migration/V4__knowledge_legacy_quarantine.sql
    - src/main/java/io/devicetruth/knowledge/KnowledgeIndexSchema.java
    - src/main/java/io/devicetruth/knowledge/KnowledgeImportService.java
    - src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java
    - src/main/java/io/devicetruth/knowledge/KnowledgeTools.java
    - src/main/java/io/devicetruth/knowledge/KnowledgeChunker.java
    - src/main/java/io/devicetruth/knowledge/EmbeddingClient.java
    - src/main/java/io/devicetruth/knowledge/EmbeddingProfile.java
    - src/main/java/io/devicetruth/knowledge/EmbeddingException.java
    - src/main/java/io/devicetruth/knowledge/OpenAiCompatibleEmbeddingClient.java
    - src/main/java/io/devicetruth/knowledge/DeterministicEmbeddingClient.java
    - src/main/java/io/devicetruth/knowledge/KnowledgeException.java
    - src/main/java/io/devicetruth/knowledge/VectorLiteral.java
    - src/main/java/io/devicetruth/core/capability/ToolContext.java
    - src/main/java/io/devicetruth/api/KnowledgeController.java
    - src/test/java/io/devicetruth/knowledge/KnowledgeSearchTest.java
    - src/test/java/io/devicetruth/knowledge/KnowledgeChunkerTest.java
    - src/test/java/io/devicetruth/knowledge/KnowledgeUpgradeMigrationTest.java
    - src/test/java/io/devicetruth/knowledge/EmbeddingClientTest.java
    - src/test/java/io/devicetruth/server/KnowledgeConfigurationTest.java
    - datasets/knowledge/charging-system-manual.md
  modified:
    - src/main/java/io/devicetruth/server/ServerConfiguration.java
    - src/main/resources/application-product.properties
    - .env.example
    - src/test/java/io/devicetruth/server/PersistentConversationTest.java
    - README.md

key-decisions:
  - "embedding配置不复用 legacy ModelConfiguration 私有文件：CONTRACTS §8 要求新PG/embedding只服务端环境注入"
  - "真实embedding固定千问 text-embedding-v4（DashScope compatible-mode，1024维），专用密钥为空时复用 DASHSCOPE_API_KEY；不发送 encoding_format"
  - "知识工具描述符保留在 KnowledgeTools；CapabilityGateway/Runtime 注册按计划留给02-02"
  - "V2 固定 vector(1024) 列并建余弦HNSW索引；换模型/维度必须新迁移独立重建"
  - "V3按模型名推断供应商不可靠；V4不信任任何历史标签，将修复前向量隔离为 NEEDS_REBUILD，保留正文与引用，用新版本重建"
  - "原文读取以“本run引用集合”为唯一入口，未取回返回INVALID_CITATION，越权统一404"

patterns-established:
  - "导入：校验→切章→向量化（事务外）→单事务写文件/文档/切片，失败零残片"
  - "检索：SQL先按owner/project（及可选过滤）限定候选，再按余弦距离排序与阈值过滤"
  - "引用：检索结果立即登记 evidence_reference，并携带 documentId/version/chunk/page|section/asset/asOf"

requirements-completed: ["KNOW-05", "TOOL-02"]
requirements-partial: ["STORE-03", "KNOW-03", "KNOW-06"]

coverage:
  - id: K1
    description: "MD/TXT 手册可导入 PG：章节切片、文档版本、checksum与服务器路径键齐全，原始文件字节不入库"
    requirement: STORE-03
    verification:
      - kind: integration
        ref: "KnowledgeSearchTest#markdownManualImportsChunksWithSectionsVersionAndVectors（真实PG16.15/pgvector0.8.7随机库，本轮执行）"
        status: pass
      - kind: integration
        ref: "KnowledgeSearchTest#txtManualImportsAndChargingQueryHitsLocatableSection（本轮执行）"
        status: pass
      - kind: unit
        ref: "KnowledgeChunkerTest（5项：200码点长单行/上限80、换行计数、补充Unicode、章节定位、非法上限）"
        status: pass
    human_judgment: false
  - id: K2
    description: "固定 embedding profile：数据库维度不符明确失败，换模型需独立重建；离线适配合同可验证"
    requirement: KNOW-03
    verification:
      - kind: integration
        ref: "KnowledgeSearchTest#configuredDimensionMustMatchIndexColumn（409 EMBEDDING_DIMENSION_MISMATCH + DB层拒绝错误维度）"
        status: pass
      - kind: unit
        ref: "EmbeddingClientTest（6项：确定性稳定性、本机HTTP替身请求/响应、维度/传输/缺少配置错误码）"
        status: pass
      - kind: unit
        ref: "KnowledgeConfigurationTest（6项：默认千问/DashScope、密钥优先级、显式模型维度修订、deterministic独立档案、档案各分量变化）"
        status: pass
      - kind: integration
        ref: "KnowledgeSearchTest#switchingOnlyProviderCannotSilentlyReuseImportedDocument、#reimportWithNewProfileOrDimensionIsRejectedInsteadOfReused、#searchNeverTrustsProfileLabelAlone"
        status: pass
    human_judgment: false
  - id: K3
    description: "权限过滤先于候选与原文读取：其他owner/project文档不进入候选、不进入响应、不能登记引用"
    requirement: KNOW-05
    verification:
      - kind: integration
        ref: "KnowledgeSearchTest#otherOwnerOrProjectDocumentsNeverReachCandidatesOrText（本轮执行）"
        status: pass
    human_judgment: false
  - id: K4
    description: "工具合同强类型：中文空格query合法，filters为受限嵌套对象，标识字段严格校验，未授权工具拒绝"
    requirement: TOOL-02
    verification:
      - kind: integration
        ref: "KnowledgeSearchTest#chineseQueryAndNestedFiltersFollowStrongTypedToolSchema"
        status: pass
      - kind: integration
        ref: "KnowledgeSearchTest#descriptorsUseNaturalLanguageQueryAndStrictIdentifiers"
        status: pass
    human_judgment: false
  - id: K5
    description: "引用可定位且可重建：检索登记documentId/version/chunk/page或section，本run外读取被拒绝，空检索不登记"
    requirement: KNOW-06
    verification:
      - kind: integration
        ref: "KnowledgeSearchTest#searchRegistersReferenceAndExcerptRebuildsOriginalSection"
        status: pass
      - kind: integration
        ref: "KnowledgeSearchTest#fabricatedOrUncollectedChunkCannotBeOpened"
        status: pass
      - kind: integration
        ref: "KnowledgeSearchTest#emptySearchReturnsNoHitsAndRegistersNothing"
        status: pass
    human_judgment: false
  - id: K6
    description: "打包服务应用V4后旧版明确要求重建，新版重建后检索与原文读取可用（开发自测记录）"
    requirement: STORE-03
    verification:
      - kind: e2e
        ref: "本轮打包JAR（PropertiesLauncher/product/deterministic，18080）：Flyway applied 1 migration→v4；旧版2026.1重导409 DOCUMENT_NEEDS_REBUILD；新版2026.2重建201/READY后search命中、excerpt逐字一致；隔离前引用仍可读（2026.1/274字）。此前同一冒烟另覆盖空查询0命中、PDF 415、错run 409/随机引用404"
        status: pass
      - kind: unit
        ref: "mvn -o test 185/0/0/0；mvn -o -DskipTests package 成功；python scripts/verify_cli.py 全通过"
        status: pass
    human_judgment: false
  - id: K8
    description: "Review复现的档案隔离/复用/切片上限缺陷已回归：provider切换、换档案、换模型、错维度、同标签不同模型、长单行"
    requirement: KNOW-03
    verification:
      - kind: integration
        ref: "KnowledgeSearchTest（13项，含切换provider/换档案修订/换模型/错维度/同标签不同模型）"
        status: pass
      - kind: unit
        ref: "KnowledgeChunkerTest 5项 + KnowledgeConfigurationTest 6项"
        status: pass
      - kind: e2e
        ref: "打包JAR临时库探针：provider切换409 PROFILE_CONFLICT、768维409 DIMENSION_MISMATCH、r2 409 PROFILE_CONFLICT、上限80实测3片/最长80码点"
        status: pass
    human_judgment: false
  - id: K9
    description: "真实PG升级回归：V2旧缺陷向量经V3/V4后被隔离为NEEDS_REBUILD，不被真实档案复用或召回，正文与历史引用可读"
    requirement: KNOW-03
    verification:
      - kind: integration
        ref: "KnowledgeUpgradeMigrationTest（Flyway target=2植入“离线算法+真实模型名”旧数据→迁移至v4→NEEDS_REBUILD/legacy-unverified、重导409且embedding零调用、真实档案0候选、历史引用可读、新版本重建可检索）"
        status: pass
      - kind: integration
        ref: "2026-11-11独立复审 ExistingV3UpgradeProbe：先在V2登记历史引用，再迁至V3确认错误档案，升级V4；旧版409且零embedding、旧向量不召回、迁移前引用仍可读、新版重建可检索、再次migrate执行0迁移且新版仍READY；outputs/validation/02-01-rereview-v4/existing-v3-probe.log"
        status: pass
      - kind: e2e
        ref: "打包JAR在既有devicetruth库：V4应用后旧版2026.1重导409、2026.2重建可检索、隔离前引用仍可读（2026.1/274字）"
        status: pass
    human_judgment: false
  - id: K7
    description: "Phase2一次产品与设计集中确认：知识与现场事实分流、引用与补证交互、真实embedding/模型/PDF/UI交付边界；待后续计划完成后集中确认"
    requirement: KNOW-05
    verification: []
    human_judgment: true
    rationale: "产品与设计判断保留一次Phase级确认；当前02-01Review不代替用户确认，也不逐PLAN重复询问。"

metrics:
  duration: 约2小时（含真实PG集成与冒烟）
  completed: 2026-11-10
status: complete
review_status: passed
reviewed_at: 2026-11-11
review_ref: 02-REVIEW.md
---

# 02-01 导入知识并提供带权限的检索接口（2026-11-11独立复审通过）

## 最新独立复审

- 本轮实际执行`mvn -o test`：185/0/0/0，35 suites；真实PG知识13、升级1、持久会话8项全部通过。
- 本轮实际执行`mvn -o -DskipTests package`及 python scripts/verify_cli.py，打包成功且CLI全部通过；打包没有重复运行测试。
- 额外真实PG探针验证已有V3库升级V4、迁移前历史引用保留及重建后重复启动，9个明确断言全部通过；临时库已清理。它使用确定性协议替身，不代表真实千问效果。
- 原P1（含V3历史污染）及两项P2全部关闭；当前P0/P1/P2未解决数均0。记录见[02-REVIEW](02-REVIEW.md)、[02-VALIDATION](02-VALIDATION.md)、[02-ACCEPTANCE](02-ACCEPTANCE.md)。
- 完成的是02-01；Phase2仍缺02-02/02-03和阶段级集中产品确认。原始自测、失败、退回与修复历史保留在下方。
- STORE-03仍缺受控原文件存储，KNOW-03仍缺02-03真实供应商效果验收，KNOW-06仍缺02-02模型答案引用校验；按部分实现追踪，不因本PLAN通过而标全能力完成。

**交付结果：一份MD/TXT手册可在真实PG中切片、向量化并被授权范围内的查询命中，返回的每个片段都能凭引用打开原章节与文档版本。**

## Performance

- 任务：3/3实施完成；独立Review及两轮复审已完成，原P1/P2全部关闭；阶段级集中确认尚未进行。
- 本轮执行时间：2026-11-10 23:10–23:30（Asia/Shanghai）。
- 新增源码/测试约2250行（含测试约750行），修改约190行；未提交Git。

## 实际执行的命令与结果

| 命令 | 结果 |
|---|---|
| `mvn -o -DskipTests compile` | 成功 |
| `mvn -o -Dtest=KnowledgeChunkerTest,EmbeddingClientTest,KnowledgeConfigurationTest test` | 17/0/0/0（离线：切片上限5、embedding适配6、配置6） |
| `mvn -o -Dtest=KnowledgeSearchTest test` | 13/0/0/0（真实PG 16.15 / pgvector 0.8.7，自建随机`devicetruth_test_*`库并在结束时删除） |
| `mvn -o test`（含`DEVICETRUTH_TEST_PG_*`） | 185/0/0/0（原154 + 新增31；复审修复后复跑） |
| `mvn -o -DskipTests package` | 成功，生成`target/devicetruth-core-0.1.0.jar`（全量测试结果取自上一行独立执行） |
| python scripts/verify_cli.py | A/B/C/D、缺密钥不降级、私有文件读取全部PASS |
| 打包JAR真实服务冒烟（product + 既有`devicetruth`库 + deterministic profile，18080） | Flyway 应用`V4`（now at v4）；旧版2026.1重导409 `DOCUMENT_NEEDS_REBUILD`（不调用embedding）；用2026.2重建后READY，检索命中2026.2、引用原文逐字一致；隔离前历史引用仍可读（2026.1/274字） |
| 打包JAR评审探针（临时库，结束后删除） | 仅切provider→409 `EMBEDDING_PROFILE_CONFLICT`；配置768维→导入/检索409 `EMBEDDING_DIMENSION_MISMATCH`；同维度换档案修订(r2)→409 `EMBEDDING_PROFILE_CONFLICT`；`max-chunk-chars=80`导入200码点单行→3片、最长80码点 |

`mvn -o`仅为复用本地缓存；命令语义与README一致。测试库在结束时确认无残留。

## 追加修订（用户指定千问 text-embedding-v4）

- **配置决策**：真实 embedding 固定为**千问 `text-embedding-v4`**（DashScope compatible-mode，默认 1024 维、cosine、档案 `openai:text-embedding-v4@1024-cosine-r1`）。`DEVICETRUTH_EMBEDDING_API_KEY` 为空时复用既有 `DASHSCOPE_API_KEY`，不要求为同一供应商维护两份密钥；专用密钥优先。代码默认 base-url 也改为 DashScope 兼容端点，避免只靠 properties 才能工作。
- **协议收敛**：请求体不再发送 OpenAI 专有的 `encoding_format`（DashScope 兼容模式与部分网关不接受），只发 `model/input` 与可选的 `dimensions`，保持最小公共协议。
- **缺密钥 fail-closed 实测**：用默认千问配置（无任何密钥）启动打包服务并导入**新内容**，返回 503 `EMBEDDING_CONFIGURATION_MISSING`，数据库该标题文档数为 0——不会静默退回确定性向量、也不留半成品。
- **测试**：新增 `KnowledgeConfigurationTest`（5项）覆盖默认值、密钥优先级、显式模型/维度与 deterministic 兜底；`EmbeddingClientTest` 增加"不发送 encoding_format"断言；全量 175/0/0/0。
- **仍 NOT_RUN**：真实千问 API 调用与真实向量召回（本机 `DASHSCOPE_API_KEY`/`DEVICETRUTH_EMBEDDING_API_KEY` 均未设置，且不读取 `devicetruth.local.properties`）。上述 fail-closed 冒烟只证明配置与错误边界，**不证明千问的语义效果**。

## 独立Review修复（2026-11-10，交回修复）

Review 结论：P0 0 项、P1 1 项、P2 2 项；仅 P1 阻止通过。三项均已复现并修复。

### P1 embedding 档案隔离与复用校验

- **档案身份结构化**：`EmbeddingProfile.profileVersion()` 改为 `provider:model@dimension-cosine-r<revision>`，供应商/模型/维度/修订任一变化都产生新标签（如 `openai:text-embedding-v4@1024-cosine-r1` 与 `deterministic:deterministic-char2@1024-cosine-r1`）。
- **离线向量独立档案**：`provider=deterministic` 时强制模型为 `deterministic-char2`，即使配置误写真实模型名也不会与千问共用标签（`KnowledgeConfigurationTest#deterministicProviderNeverSharesRealEmbeddingProfile`）。
- **复用前校验**：导入先校验配置维度与数据库列，再按 checksum+版本查找；只有 profile/model/维度三者全部一致才 `reused=true`，否则 409 `EMBEDDING_PROFILE_CONFLICT`（`KnowledgeImportService#reusable`）。维度错误在复用判断**之前**失败。
- **检索不只信任标签**：召回 SQL 同时过滤 `c.embedding_profile = d.embedding_profile`、`d.embedding_model` 与 `d.embedding_dimension`，并先校验查询维度与索引列一致；伪造标签或同标签不同模型的切片不会进入候选（`KnowledgeSearchTest#searchNeverTrustsProfileLabelAlone`）。
- **既有数据的正确处置（复审 P1）**：V3 按模型名推断 provider 不可靠——旧漏洞会产生“离线算法 + 真实模型名”，V3 会把这类向量改标为 `openai:` 并被真实配置复用/召回。V4 因此**不信任任何历史标签**：所有修复前写入的文档置 `ingest_status=NEEDS_REBUILD`、档案改为 `legacy-unverified:@<dim>-cosine-r0`，正文/切片/历史引用保留，不重算向量。真实配置重导同版本返回 409 `DOCUMENT_NEEDS_REBUILD`（不调用 embedding），检索因 `READY` 过滤不进入候选；用新的文档版本重建后才恢复可检索。
- **回归覆盖**（Review要求4种）：仅切 provider、同内容换档案修订、同内容换模型、同内容换错误维度；另有同标签不同模型的检索隔离。

### P2-1 切片上限

`KnowledgeChunker` 改为按 `maxChars`（码点）切分：长单行切到刚好填满当前块，段落换行也计入长度，超长不再突破配置上限；不得再用固定 4000 兜底。新增 `KnowledgeChunkerTest`（5项）：200码点单行/上限80、换行计数、补充 Unicode（emoji）、Markdown章节定位、非法上限。打包探针实测 `max-chunk-chars=80` + 200码点单行 → 3片、最长80码点。

### P2-2 GSD 完成统计误计

`02-01-SUMMARY.md` 的 `status` 从 `awaiting_review` 改为安装版支持的 `blocked`。原生命令实测：

- `gsd-tools phase-plan-index 02 --raw` → `02-01.has_summary=false`，`incomplete=["02-01","02-02","02-03"]`。
- `gsd-tools planning inspect --raw` → `completed_plans: 2/13 (15%)`，与 STATE 一致。

历史证据与修复过程保留在本 SUMMARY，不删除、不改写为通过。

## 关键实现与运行链路（通俗说明）

1. **导入**：`POST /api/knowledge/documents` 进入 `KnowledgeController`，服务器身份来自Bean（body不能提供owner/project）。`KnowledgeImportService` 先按MIME与大小校验、算SHA-256；`KnowledgeIndexSchema.vectorDimension()` 读 `dt_knowledge_chunk.embedding` 的实际列类型（`vector(1024)`），与配置维度不一致直接409；再按 checksum+版本判断是否可复用（档案必须完全一致）。随后 `KnowledgeChunker` 把Markdown按标题层级切成章节块（纯文本用段序位），逐块调用 `EmbeddingClient`；向量化在事务外完成，最后在一个事务里写 `dt_uploaded_file`、`dt_knowledge_document`、`dt_knowledge_chunk`。
2. **检索**：`search_knowledge` 由 `KnowledgeTools` 做参数白名单与强类型校验（`query` 允许中文/空格；`filters` 仅 `domain/deviceModel/documentId`），再交给 `KnowledgeSearchService`。SQL先用 `owner_id/project_id`（及可选过滤）限定候选，再用 `embedding <=> CAST(? AS vector)` 排序并按 `minSimilarity` 过滤；空结果就是空结果，不编造片段。
3. **引用**：每个返回片段在 `dt_evidence_reference` 登记 `documentId、source_version、chunk_id、page/section、asset_id(可空)、run的asOf、dataNature`，`ON CONFLICT` 保证同一run重复检索复用同一个 `referenceId`。`get_document_excerpt` 只查“本run已登记”的引用：本run未取回→`INVALID_CITATION`，其他owner/project或伪造ID→404，不泄漏正文或存在性。
4. **装配**：`ServerConfiguration` 在product profile下显式装配 `EmbeddingProfile/EmbeddingClient/KnowledgeImportService/KnowledgeSearchService/KnowledgeTools`，`KnowledgeController` 由组件扫描注入。`CapabilityGateway` 目录仍是5个结构化能力——知识工具接入Runtime明确留给02-02。

用充电场景说明实际输入→输出：把 `datasets/knowledge/charging-system-manual.md`（SIMULATED）导入后，在某个QUEUED run下问“直流快充中断时应该先看什么资料？”，召回命中“3 直流快充中断”章节，返回 `referenceId` 与文档版本；再凭该引用读回同一段原文（179字），可以引用但**不能**据此断言“这台车当前装了D-120B”——那是配置记录与现场核验的事。

## 与PLAN的偏差（均已在实现中说明理由）

1. `pom.xml` 未改：无需新增依赖（JDK `HttpClient` + SQL级`CAST(? AS vector)`）。
2. `ModelConfiguration` 未改：CONTRACTS §8 明确新embedding配置**不得**复用legacy私有文件加载器；新增 `EmbeddingProfile` 只读服务端环境。
3. `CapabilityTypes` 未改：02-01 只交付“可注入的服务端工具合同”，Gateway/Runtime注册是02-02-T1的明确范围；工具描述符放在 `KnowledgeTools`，并新增 `core/capability/ToolContext`。
4. 额外新增 `KnowledgeException/EmbeddingProfile/EmbeddingException/VectorLiteral/KnowledgeChunker/EmbeddingClientTest` 与演示语料 `datasets/knowledge/charging-system-manual.md`（任务1“首批可信演示语料”所需）。
5. `PersistentConversationTest` 的“成功迁移数=1”断言更新为“=2且失败迁移=0”：V2是真实新增迁移，断言标准未降低，另加了失败迁移为0的检查。

## 失败与修复记录

1. **集成测试初次失败**：`searchRegistersReferenceAndExcerptRebuildsOriginalSection` 断言“首选命中必为Markdown手册”，但同库已导入的TXT手册对同一查询得分更高。修正为断言“实际首选命中的documentId/version与登记行一致”，断言强度未降低（仍逐字段核对登记与原文）。
2. **全量测试初次失败**：`PersistentConversationTest` 断言迁移成功数偏小，V2/V3是真实新增迁移。更新为3且失败数为0，断言标准未降低。
3. **冒烟脚本首次启动失败**：直接`java`实为JDK17。根因是 Git Bash 的 `PATH` 以 `:` 分隔，而 `export PATH="$JAVA_HOME/bin:$PATH"` 里的 `D:\...`/`D:/...` 冒号把路径截断，JDK21 的 `bin` 从未真正进入 PATH（`mvn` 因只读`JAVA_HOME`不受影响）。改用 `/d/Java_Project/.../bin` 形式并核对`java -version`为21后成功；README已补 Git Bash 写法与核对步骤，未修改全局环境。
4. **独立Review P1（已修复）**：离线与真实向量共用档案、复用绕过维度检查、检索只信标签。修复见“独立Review修复”小节；回归与打包探针均通过。
5. **独立Review P2-1（已修复）**：切片未遵守配置上限。改为按码点切分并计入换行，新增5项切片测试与打包探针。
6. **独立Review P2-2（已修复）**：SUMMARY的`awaiting_review`被GSD计为完成。改为`blocked`；`phase-plan-index 02`恢复`has_summary=false`/`incomplete`包含02-01，`planning inspect`恢复2/13。
7. **复审 P1（已修复）**：V3按模型名推断供应商，会把“离线算法+真实模型名”的旧向量改标为真实档案并允许复用/召回。V4改为不信任任何历史标签、统一隔离为`NEEDS_REBUILD`，并新增真实PG升级回归`KnowledgeUpgradeMigrationTest`（含“真实配置重导不静默复用、不进入候选、原文与引用可读、新版本重建”）。

## 未验证项（NOT_RUN，不得表述为通过）

- 真实embedding供应商→PG向量→召回的在线验收：**NOT_RUN**（02-03-T3必跑；本机无`DASHSCOPE_API_KEY`）。默认千问路径已验证"缺密钥503且零残留"，但这只是错误边界，不是在线效果验收。
- PDF解析、页码定位、混合/关键词检索与12样本Hit@5评测：**NOT_RUN**（02-03）。
- 知识工具注册进 `CapabilityGateway`、Agent自由问答、同run补答、Vue：**NOT_RUN**（02-02）。
- 真实文件字节存储、版本升级/撤回与导入失败重试：**NOT_RUN**（KNOW-07，02-03）。
- 规模性能（HNSW在大量切片与过滤下的召回率）：**NOT_RUN**；本次仅6个切片。
- 跨进程恢复、并发幂等：**NOT_RUN**（M2）。

## 当前状态

- `status: complete`、`review_status: passed`，仅02-01通过；GSD原生统计及推进结果见02-VALIDATION。此前`blocked`及2/13记录为退回修复期间的历史。
- Phase2保持进行中，产品确认集中到阶段出口；02-02/02-03仍未获授权，不执行。未commit/push。

## Self-Check: PASSED

本轮实际185项测试及打包/CLI、独立PG升级探针通过；文件已核对。用户默认不自动commit/push，因此没有为审查记录创建提交，也不伪造任务commit。
