CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE dt_conversation (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, owner_id, project_id)
);
CREATE INDEX dt_conversation_owner_updated ON dt_conversation(owner_id, project_id, updated_at DESC, id);

CREATE TABLE dt_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    seq BIGINT NOT NULL CHECK (seq > 0),
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL CHECK (length(btrim(content)) > 0 AND char_length(content) <= 8000),
    request_key UUID,
    selected_asset_id VARCHAR(128),
    requested_at TIMESTAMPTZ NOT NULL,
    as_of TIMESTAMPTZ NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1 CHECK (schema_version = 1),
    attachment_refs JSONB NOT NULL DEFAULT '[]',
    context_refs JSONB NOT NULL DEFAULT '[]',
    reply_to_run_id UUID,
    reply_to_question_id UUID,
    reply_to_question_version INTEGER,
    FOREIGN KEY (conversation_id, owner_id, project_id) REFERENCES dt_conversation(id, owner_id, project_id),
    UNIQUE (id, conversation_id, owner_id, project_id),
    UNIQUE (conversation_id, seq),
    UNIQUE (conversation_id, request_key)
);

CREATE TABLE dt_agent_run (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    goal_message_id UUID NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'WAITING_FOR_USER', 'COMPLETED', 'FAILED', 'CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    schema_version INTEGER NOT NULL DEFAULT 1 CHECK (schema_version = 1),
    requested_at TIMESTAMPTZ NOT NULL,
    as_of TIMESTAMPTZ NOT NULL,
    pending_question_id UUID,
    question_version INTEGER NOT NULL DEFAULT 0 CHECK (question_version >= 0),
    pending_question JSONB,
    context_summary TEXT NOT NULL DEFAULT '',
    tool_output_refs JSONB NOT NULL DEFAULT '[]',
    source_references JSONB NOT NULL DEFAULT '[]',
    budget JSONB NOT NULL,
    result_ref UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (goal_message_id, conversation_id, owner_id, project_id) REFERENCES dt_message(id, conversation_id, owner_id, project_id),
    UNIQUE (id, conversation_id, owner_id, project_id),
    CHECK ((status = 'WAITING_FOR_USER') = (pending_question_id IS NOT NULL AND question_version > 0 AND pending_question IS NOT NULL))
);
CREATE INDEX dt_run_conversation_created ON dt_agent_run(conversation_id, created_at, id);
CREATE INDEX dt_run_owner_status ON dt_agent_run(owner_id, project_id, status, created_at);

CREATE TABLE dt_run_event (
    run_id UUID NOT NULL,
    seq BIGINT NOT NULL CHECK (seq > 0),
    conversation_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    type VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    safe_payload JSONB NOT NULL,
    PRIMARY KEY (run_id, seq),
    FOREIGN KEY (run_id, conversation_id, owner_id, project_id) REFERENCES dt_agent_run(id, conversation_id, owner_id, project_id)
);
