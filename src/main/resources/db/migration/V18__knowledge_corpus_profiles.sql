-- Additive corpus ingestion on existing documents, chunks, vectors and Run references.
CREATE TABLE dt_knowledge_release (
    id UUID PRIMARY KEY, owner_id VARCHAR(128) NOT NULL, project_id VARCHAR(128) NOT NULL,
    kb_release VARCHAR(128) NOT NULL, index_profile VARCHAR(128) NOT NULL,
    profile_configuration JSONB NOT NULL, embedding_profile VARCHAR(128) NOT NULL,
    corpus_checksum CHAR(64) NOT NULL, expected_documents INTEGER NOT NULL CHECK(expected_documents > 0),
    expected_chunks INTEGER NOT NULL CHECK(expected_chunks > 0),
    status VARCHAR(16) NOT NULL CHECK(status IN ('BUILDING','FAILED','READY')),
    active BOOLEAN NOT NULL DEFAULT FALSE, is_test BOOLEAN NOT NULL DEFAULT FALSE,
    last_error VARCHAR(64), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(id,owner_id,project_id), UNIQUE(owner_id,project_id,kb_release,index_profile,embedding_profile),
    CHECK(NOT active OR (status='READY' AND NOT is_test))
);
CREATE UNIQUE INDEX dt_knowledge_one_active_release ON dt_knowledge_release(owner_id,project_id) WHERE active;
ALTER TABLE dt_knowledge_document ADD COLUMN release_id UUID,
    ADD COLUMN knowledge_id VARCHAR(128), ADD COLUMN original_text TEXT,
    ADD COLUMN original_checksum CHAR(64), ADD COLUMN normalized_metadata JSONB, ADD COLUMN source_nature VARCHAR(32);
ALTER TABLE dt_knowledge_document ADD CONSTRAINT dt_knowledge_document_release_scope
    FOREIGN KEY(release_id,owner_id,project_id) REFERENCES dt_knowledge_release(id,owner_id,project_id);
ALTER TABLE dt_knowledge_document ADD CONSTRAINT dt_knowledge_document_release_identity UNIQUE(id,release_id,owner_id,project_id);
CREATE UNIQUE INDEX dt_knowledge_unit_in_release ON dt_knowledge_document(release_id,knowledge_id) WHERE release_id IS NOT NULL;
CREATE TABLE dt_knowledge_parent (
    id UUID PRIMARY KEY, document_id UUID NOT NULL, release_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL, project_id VARCHAR(128) NOT NULL,
    content TEXT NOT NULL, content_checksum CHAR(64) NOT NULL,
    UNIQUE(id,document_id,release_id,owner_id,project_id),
    FOREIGN KEY(document_id,release_id,owner_id,project_id) REFERENCES dt_knowledge_document(id,release_id,owner_id,project_id)
);
ALTER TABLE dt_knowledge_chunk ADD COLUMN release_id UUID, ADD COLUMN parent_id UUID,
    ADD COLUMN embedding_text TEXT, ADD COLUMN content_checksum CHAR(64), ADD COLUMN embedding_text_checksum CHAR(64),
    ADD COLUMN body_start INTEGER, ADD COLUMN body_end INTEGER, ADD COLUMN previous_chunk_id UUID, ADD COLUMN next_chunk_id UUID,
    ADD COLUMN source_locations JSONB, ADD COLUMN chunking_profile VARCHAR(128), ADD COLUMN context_profile VARCHAR(16),
    ADD COLUMN content_tokens INTEGER, ADD COLUMN embedding_tokens INTEGER;
ALTER TABLE dt_knowledge_chunk ADD CONSTRAINT dt_knowledge_child_parent_scope
    FOREIGN KEY(parent_id,document_id,release_id,owner_id,project_id) REFERENCES dt_knowledge_parent(id,document_id,release_id,owner_id,project_id);
ALTER TABLE dt_knowledge_chunk ADD CONSTRAINT dt_knowledge_child_fields CHECK(release_id IS NULL OR
    (parent_id IS NOT NULL AND embedding_text IS NOT NULL AND body_start >= 0 AND body_end > body_start
     AND content_checksum IS NOT NULL AND embedding_text_checksum IS NOT NULL AND chunking_profile IS NOT NULL
     AND context_profile IN ('NONE','TITLE','METADATA') AND content_tokens > 0 AND embedding_tokens > 0));
CREATE INDEX dt_knowledge_chunk_release_scope ON dt_knowledge_chunk(owner_id,project_id,release_id);
CREATE TABLE dt_knowledge_source (
    source_id VARCHAR(128) NOT NULL, release_id UUID NOT NULL, owner_id VARCHAR(128) NOT NULL, project_id VARCHAR(128) NOT NULL,
    source_record JSONB NOT NULL, PRIMARY KEY(source_id,release_id,owner_id,project_id),
    FOREIGN KEY(release_id,owner_id,project_id) REFERENCES dt_knowledge_release(id,owner_id,project_id)
);
CREATE TABLE dt_knowledge_document_source (
    document_id UUID NOT NULL, source_id VARCHAR(128) NOT NULL, release_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL, project_id VARCHAR(128) NOT NULL, source_locations JSONB NOT NULL,
    PRIMARY KEY(document_id,source_id),
    FOREIGN KEY(document_id,release_id,owner_id,project_id) REFERENCES dt_knowledge_document(id,release_id,owner_id,project_id),
    FOREIGN KEY(source_id,release_id,owner_id,project_id) REFERENCES dt_knowledge_source(source_id,release_id,owner_id,project_id)
);
CREATE TABLE dt_knowledge_model (
    document_id UUID NOT NULL, owner_id VARCHAR(128) NOT NULL, project_id VARCHAR(128) NOT NULL,
    model_key VARCHAR(128) NOT NULL, model_text TEXT NOT NULL, PRIMARY KEY(document_id,model_key),
    FOREIGN KEY(document_id,owner_id,project_id) REFERENCES dt_knowledge_document(id,owner_id,project_id)
);
CREATE TABLE dt_knowledge_embedding_cache (
    owner_id VARCHAR(128) NOT NULL, project_id VARCHAR(128) NOT NULL, config_checksum CHAR(64) NOT NULL,
    input_checksum CHAR(64) NOT NULL, embedding_profile VARCHAR(128) NOT NULL,
    embedding vector(1024) NOT NULL, is_test BOOLEAN NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(owner_id,project_id,config_checksum,input_checksum)
);
CREATE TABLE dt_knowledge_import_item (
    release_id UUID NOT NULL, knowledge_id VARCHAR(128) NOT NULL, owner_id VARCHAR(128) NOT NULL, project_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','FAILED','READY')), last_error VARCHAR(64),
    PRIMARY KEY(release_id,knowledge_id),
    FOREIGN KEY(release_id,owner_id,project_id) REFERENCES dt_knowledge_release(id,owner_id,project_id)
);
