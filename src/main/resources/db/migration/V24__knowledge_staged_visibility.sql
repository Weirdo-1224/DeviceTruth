-- Older readers filter only document READY. STAGED prevents unactivated corpora leaking to them.
ALTER TABLE dt_knowledge_document DROP CONSTRAINT dt_knowledge_document_ingest_status_check;
ALTER TABLE dt_knowledge_document ADD CONSTRAINT dt_knowledge_document_ingest_status_check
    CHECK (ingest_status IN ('READY','FAILED','NEEDS_REBUILD','STAGED'));
