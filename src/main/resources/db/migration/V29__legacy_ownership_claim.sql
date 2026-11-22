CREATE TABLE dt_legacy_ownership_claim (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL UNIQUE,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    claimed_by_user_id UUID NOT NULL REFERENCES dt_account(id),
    table_counts JSONB NOT NULL,
    unknown_author_counts JSONB NOT NULL,
    active_runs JSONB NOT NULL,
    files JSONB NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_id, project_id),
    FOREIGN KEY (workspace_id, owner_id, project_id)
        REFERENCES dt_workspace(id, owner_id, project_id)
);
