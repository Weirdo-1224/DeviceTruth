-- 历史作者与未认领空间保持 NULL；不改旧入口的写入前提。
ALTER TABLE dt_workspace ADD CONSTRAINT dt_workspace_storage_identity UNIQUE (id, owner_id, project_id);
ALTER TABLE dt_conversation
    ADD COLUMN workspace_id UUID,
    ADD COLUMN created_by_user_id UUID REFERENCES dt_account(id),
    ADD CONSTRAINT dt_conversation_workspace FOREIGN KEY (workspace_id, owner_id, project_id)
        REFERENCES dt_workspace(id, owner_id, project_id);
ALTER TABLE dt_message ADD COLUMN actor_user_id UUID REFERENCES dt_account(id),
    ADD COLUMN discussion_quotes JSONB NOT NULL DEFAULT '[]';
ALTER TABLE dt_agent_run
    ADD COLUMN initiated_by_user_id UUID REFERENCES dt_account(id),
    ADD COLUMN execution_actor_user_id UUID REFERENCES dt_account(id),
    ADD COLUMN input_snapshot JSONB;
ALTER TABLE dt_run_event ADD COLUMN actor_user_id UUID REFERENCES dt_account(id);
ALTER TABLE dt_uploaded_file
    ADD COLUMN uploaded_by_user_id UUID REFERENCES dt_account(id),
    ADD COLUMN conversation_id UUID,
    ADD COLUMN usage_scope VARCHAR(32) NOT NULL DEFAULT 'SPACE_KNOWLEDGE'
        CHECK (usage_scope IN ('SPACE_KNOWLEDGE', 'CONVERSATION_ATTACHMENT')),
    ADD CONSTRAINT dt_file_conversation FOREIGN KEY (conversation_id, owner_id, project_id)
        REFERENCES dt_conversation(id, owner_id, project_id),
    ADD CONSTRAINT dt_file_attachment_scope CHECK
        ((usage_scope = 'CONVERSATION_ATTACHMENT') = (conversation_id IS NOT NULL));

CREATE TABLE dt_investigation_discussion (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    author_user_id UUID NOT NULL REFERENCES dt_account(id),
    version INTEGER NOT NULL DEFAULT 1 CHECK (version = 1),
    seq BIGINT NOT NULL CHECK (seq > 0),
    content TEXT NOT NULL CHECK (length(btrim(content)) > 0 AND char_length(content) <= 8000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id, owner_id, project_id) REFERENCES dt_conversation(id, owner_id, project_id),
    UNIQUE (conversation_id, seq)
);

CREATE TABLE dt_run_input_segment (
    run_id UUID NOT NULL,
    segment_no INTEGER NOT NULL CHECK (segment_no > 0),
    conversation_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    actor_user_id UUID REFERENCES dt_account(id),
    input_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, segment_no),
    FOREIGN KEY (run_id, conversation_id, owner_id, project_id)
        REFERENCES dt_agent_run(id, conversation_id, owner_id, project_id)
);

CREATE TABLE dt_knowledge_publication (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    document_id UUID NOT NULL,
    published_by_user_id UUID NOT NULL REFERENCES dt_account(id),
    original_author_user_id UUID REFERENCES dt_account(id),
    title VARCHAR(500) NOT NULL CHECK (length(btrim(title)) > 0),
    applicability VARCHAR(2000) NOT NULL CHECK (length(btrim(applicability)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workspace_id, document_id),
    FOREIGN KEY (workspace_id, owner_id, project_id) REFERENCES dt_workspace(id, owner_id, project_id),
    FOREIGN KEY (document_id, owner_id, project_id) REFERENCES dt_knowledge_document(id, owner_id, project_id)
);
