-- 修复 V3 的供应商推断缺陷：旧向量无法证明真实来源，一律隔离为“需重建”。
-- V3 曾按模型名前缀推断 provider，可能把离线算法生成的向量改标成 openai 档案；
-- 本迁移不信任任何历史标签：把既有知识向量统一改为 legacy-unverified 档案并标记 NEEDS_REBUILD。
-- 正文、切片与历史引用全部保留；重建方式是用新的文档版本重新导入同一内容。

ALTER TABLE dt_knowledge_document DROP CONSTRAINT dt_knowledge_document_ingest_status_check;
ALTER TABLE dt_knowledge_document ADD CONSTRAINT dt_knowledge_document_ingest_status_check
    CHECK (ingest_status IN ('READY', 'FAILED', 'NEEDS_REBUILD'));

UPDATE dt_knowledge_chunk c
SET embedding_profile = 'legacy-unverified:@' || d.embedding_dimension || '-cosine-r0'
FROM dt_knowledge_document d
WHERE c.document_id = d.id;

UPDATE dt_knowledge_document
SET ingest_status = 'NEEDS_REBUILD',
    embedding_profile = 'legacy-unverified:@' || embedding_dimension || '-cosine-r0';
