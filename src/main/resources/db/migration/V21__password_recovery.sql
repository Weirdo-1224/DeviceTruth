CREATE TABLE dt_password_reset (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES dt_account(id),
    token_hash CHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'ACCEPTED_BY_PROVIDER', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX dt_password_reset_user ON dt_password_reset(user_id);
