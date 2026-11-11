-- 知识导入、向量索引与可定位引用。
-- 归属（owner_id/project_id）在所有表上冗余，并通过复合外键保证跨owner/project无法串接；
-- 授权过滤发生在召回之前，引用读取再按 run 已取集合校验。
-- embedding 索引维度固定为 1024（默认 profile text-embedding-v4@1024-cosine-v1）。
-- 换模型/维度必须新增迁移独立重建索引，不能在同一索引里混写不同维度。原始文件字节不写入数据库。

CREATE TABLE dt_uploaded_file (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    path_key VARCHAR(256) NOT NULL UNIQUE,
    original_name VARCHAR(256) NOT NULL,
    mime VARCHAR(128) NOT NULL,
    checksum CHAR(64) NOT NULL CHECK (checksum ~ '^[0-9a-f]{64}$'),
    byte_size INTEGER NOT NULL CHECK (byte_size > 0 AND byte_size <= 1000000),
    parse_status VARCHAR(16) NOT NULL CHECK (parse_status IN ('PARSED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (owner_id, project_id, checksum),
    UNIQUE (id, owner_id, project_id)
);

CREATE TABLE dt_knowledge_document (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0 AND char_length(title) <= 500),
    domain VARCHAR(64) NOT NULL,
    device_model VARCHAR(128),
    doc_version VARCHAR(64) NOT NULL,
    source_file_id UUID NOT NULL,
    checksum CHAR(64) NOT NULL CHECK (checksum ~ '^[0-9a-f]{64}$'),
    ingest_status VARCHAR(16) NOT NULL CHECK (ingest_status IN ('READY', 'FAILED')),
    embedding_profile VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    embedding_dimension INTEGER NOT NULL CHECK (embedding_dimension > 0),
    chunking_profile VARCHAR(64) NOT NULL,
    chunk_count INTEGER NOT NULL CHECK (chunk_count > 0),
    data_nature VARCHAR(16) NOT NULL CHECK (data_nature IN ('SIMULATED', 'AUTHORIZED')),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (owner_id, project_id, checksum, doc_version),
    UNIQUE (id, owner_id, project_id),
    FOREIGN KEY (source_file_id, owner_id, project_id)
        REFERENCES dt_uploaded_file(id, owner_id, project_id)
);
CREATE INDEX dt_knowledge_document_scope ON dt_knowledge_document(owner_id, project_id, domain, device_model);

CREATE TABLE dt_knowledge_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    chunk_no INTEGER NOT NULL CHECK (chunk_no > 0),
    content TEXT NOT NULL CHECK (length(btrim(content)) > 0 AND char_length(content) <= 4000),
    page_no INTEGER CHECK (page_no > 0),
    section TEXT,
    embedding vector(1024) NOT NULL,
    embedding_profile VARCHAR(128) NOT NULL,
    UNIQUE (document_id, chunk_no),
    UNIQUE (id, owner_id, project_id),
    FOREIGN KEY (document_id, owner_id, project_id)
        REFERENCES dt_knowledge_document(id, owner_id, project_id)
);
CREATE INDEX dt_knowledge_chunk_scope ON dt_knowledge_chunk(owner_id, project_id, embedding_profile);
CREATE INDEX dt_knowledge_chunk_embedding ON dt_knowledge_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE dt_evidence_reference (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN
        ('KNOWLEDGE_DOCUMENT', 'FACTORY', 'MAINTENANCE', 'CONFIGURATION', 'FIELD_OBSERVATION', 'USER_STATEMENT')),
    source_id VARCHAR(128) NOT NULL,
    source_version VARCHAR(64) NOT NULL,
    chunk_id UUID,
    asset_id VARCHAR(128),
    as_of TIMESTAMPTZ,
    citation_page INTEGER CHECK (citation_page > 0),
    citation_section TEXT,
    retrieved_at TIMESTAMPTZ NOT NULL,
    data_nature VARCHAR(16) NOT NULL CHECK (data_nature IN ('SIMULATED', 'AUTHORIZED')),
    schema_version INTEGER NOT NULL DEFAULT 1 CHECK (schema_version = 1),
    UNIQUE (run_id, owner_id, project_id, chunk_id),
    CHECK (source_type <> 'KNOWLEDGE_DOCUMENT' OR chunk_id IS NOT NULL),
    FOREIGN KEY (run_id, conversation_id, owner_id, project_id)
        REFERENCES dt_agent_run(id, conversation_id, owner_id, project_id),
    FOREIGN KEY (chunk_id, owner_id, project_id)
        REFERENCES dt_knowledge_chunk(id, owner_id, project_id)
);
CREATE INDEX dt_evidence_reference_run ON dt_evidence_reference(run_id, retrieved_at);
