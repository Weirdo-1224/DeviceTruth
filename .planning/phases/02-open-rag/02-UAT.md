---
status: complete
phase: 02-open-rag
source: [02-01-SUMMARY.md]
started: 2026-11-11
updated: 2026-11-11
---

# Phase 2 UAT（阶段未结束）

## Current Test

02-01自动技术项已完成；02-02/02-03尚未执行、未获授权。产品与设计仅保留一次阶段级集中确认，当前不发出逐项技术提示，不预填用户通过。

## Tests

### 1. MD/TXT 手册可导入 PG：章节切片、文档版本、checksum与服务器路径键齐全，原始文件字节不入库
expected: MD/TXT 手册可导入 PG：章节切片、文档版本、checksum与服务器路径键齐全，原始文件字节不入库
result: pass
source: automated
coverage_id: K1
verification: KnowledgeSearchTest#markdownManualImportsChunksWithSectionsVersionAndVectors（真实PG16.15/pgvector0.8.7随机库，本轮执行）；KnowledgeSearchTest#txtManualImportsAndChargingQueryHitsLocatableSection（本轮执行）；KnowledgeChunkerTest（5项：200码点长单行/上限80、换行计数、补充Unicode、章节定位、非法上限）

### 2. 固定 embedding profile：数据库维度不符明确失败，换模型需独立重建；离线适配合同可验证
expected: 固定 embedding profile：数据库维度不符明确失败，换模型需独立重建；离线适配合同可验证
result: pass
source: automated
coverage_id: K2
verification: KnowledgeSearchTest#configuredDimensionMustMatchIndexColumn（409 EMBEDDING_DIMENSION_MISMATCH + DB层拒绝错误维度）；EmbeddingClientTest（6项：确定性稳定性、本机HTTP替身请求/响应、维度/传输/缺少配置错误码）；KnowledgeConfigurationTest（6项：默认千问/DashScope、密钥优先级、显式模型维度修订、deterministic独立档案、档案各分量变化）；KnowledgeSearchTest#switchingOnlyProviderCannotSilentlyReuseImportedDocument、#reimportWithNewProfileOrDimensionIsRejectedInsteadOfReused、#searchNeverTrustsProfileLabelAlone

### 3. 权限过滤先于候选与原文读取：其他owner/project文档不进入候选、不进入响应、不能登记引用
expected: 权限过滤先于候选与原文读取：其他owner/project文档不进入候选、不进入响应、不能登记引用
result: pass
source: automated
coverage_id: K3
verification: KnowledgeSearchTest#otherOwnerOrProjectDocumentsNeverReachCandidatesOrText（本轮执行）

### 4. 工具合同强类型：中文空格query合法，filters为受限嵌套对象，标识字段严格校验，未授权工具拒绝
expected: 工具合同强类型：中文空格query合法，filters为受限嵌套对象，标识字段严格校验，未授权工具拒绝
result: pass
source: automated
coverage_id: K4
verification: KnowledgeSearchTest#chineseQueryAndNestedFiltersFollowStrongTypedToolSchema；KnowledgeSearchTest#descriptorsUseNaturalLanguageQueryAndStrictIdentifiers

### 5. 引用可定位且可重建：检索登记documentId/version/chunk/page或section，本run外读取被拒绝，空检索不登记
expected: 引用可定位且可重建：检索登记documentId/version/chunk/page或section，本run外读取被拒绝，空检索不登记
result: pass
source: automated
coverage_id: K5
verification: KnowledgeSearchTest#searchRegistersReferenceAndExcerptRebuildsOriginalSection；KnowledgeSearchTest#fabricatedOrUncollectedChunkCannotBeOpened；KnowledgeSearchTest#emptySearchReturnsNoHitsAndRegistersNothing

### 6. 打包服务应用V4后旧版明确要求重建，新版重建后检索与原文读取可用（开发自测记录）
expected: 打包服务应用V4后旧版明确要求重建，新版重建后检索与原文读取可用（开发自测记录）
result: pass
source: automated
coverage_id: K6
verification: 本轮打包JAR（PropertiesLauncher/product/deterministic，18080）：Flyway applied 1 migration→v4；旧版2026.1重导409 DOCUMENT_NEEDS_REBUILD；新版2026.2重建201/READY后search命中、excerpt逐字一致；隔离前引用仍可读（2026.1/274字）。此前同一冒烟另覆盖空查询0命中、PDF 415、错run 409/随机引用404；mvn -o test 185/0/0/0；mvn -o -DskipTests package 成功；python scripts/verify_cli.py 全通过

### 7. Review复现的档案隔离/复用/切片上限缺陷已回归：provider切换、换档案、换模型、错维度、同标签不同模型、长单行
expected: Review复现的档案隔离/复用/切片上限缺陷已回归：provider切换、换档案、换模型、错维度、同标签不同模型、长单行
result: pass
source: automated
coverage_id: K8
verification: KnowledgeSearchTest（13项，含切换provider/换档案修订/换模型/错维度/同标签不同模型）；KnowledgeChunkerTest 5项 + KnowledgeConfigurationTest 6项；打包JAR临时库探针：provider切换409 PROFILE_CONFLICT、768维409 DIMENSION_MISMATCH、r2 409 PROFILE_CONFLICT、上限80实测3片/最长80码点

### 8. 真实PG升级回归：V2旧缺陷向量经V3/V4后被隔离为NEEDS_REBUILD，不被真实档案复用或召回，正文与历史引用可读
expected: 真实PG升级回归：V2旧缺陷向量经V3/V4后被隔离为NEEDS_REBUILD，不被真实档案复用或召回，正文与历史引用可读
result: pass
source: automated
coverage_id: K9
verification: KnowledgeUpgradeMigrationTest（Flyway target=2植入“离线算法+真实模型名”旧数据→迁移至v4→NEEDS_REBUILD/legacy-unverified、重导409且embedding零调用、真实档案0候选、历史引用可读、新版本重建可检索）；2026-11-11独立复审 ExistingV3UpgradeProbe：先在V2登记历史引用，再迁至V3确认错误档案，升级V4；旧版409且零embedding、旧向量不召回、迁移前引用仍可读、新版重建可检索、再次migrate执行0迁移且新版仍READY；outputs/validation/02-01-rereview-v4/existing-v3-probe.log；打包JAR在既有devicetruth库：V4应用后旧版2026.1重导409、2026.2重建可检索、隔离前引用仍可读（2026.1/274字）

### 9. Phase 2 产品与设计集中确认
expected: 在后续计划实际完成后集中确认知识/现场事实分流、引用、低风险回答及补证交互和交付边界；真实模型/embedding、PDF、UI分别取实际证据，未验证不冒称通过。
result: pass
source: user
note: 2026-11-12 用户明确要求按当前已实施范围结束并关闭Phase 2；正文安全、PDF及浏览器验收延后。此确认不作为相关技术测试通过证据。
coverage_id: K7
rationale: 产品/业务/设计判断留到阶段出口一次确认，不随每个PLAN重复询问；本轮Review通过不代替用户确认。

## Summary

total: 9
passed: 9
issues: 0
pending: 0
skipped: 0

这里统计的是coverage验收条目，不是JUnit用例数。真实JUnit为185/0/0/0，额外PG升级探针9断言单列。后续计划尚未交付的测试将在实际完成后追加；当前9项不代表Phase2全部验收范围。

## History

首轮独立Review P1 1项/P2 2项，首次复审仍有V3历史污染P1，均未放行；本轮V4隔离与独立升级探针通过后才关闭问题。原始失败/修复记录保留在02-01-SUMMARY和02-REVIEW，不删除历史，不冒称任何新的用户确认。
