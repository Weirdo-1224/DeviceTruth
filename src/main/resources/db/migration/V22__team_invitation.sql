CREATE TABLE dt_team_invitation (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES dt_workspace(id),
    email VARCHAR(254) NOT NULL CHECK (email=lower(btrim(email))),
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN','COLLABORATOR','VIEWER')),
    created_by_user_id UUID NOT NULL REFERENCES dt_account(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','REVOKED')),
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (delivery_status IN ('PENDING','ACCEPTED_BY_PROVIDER','FAILED')),
    accepted_by_user_id UUID REFERENCES dt_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX dt_invitation_workspace ON dt_team_invitation(workspace_id,created_at,id);
