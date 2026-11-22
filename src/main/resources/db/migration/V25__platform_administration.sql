-- V1–V24 不变；管理授权不会创建空间成员或认领历史资料。
ALTER TABLE dt_account ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE','DISABLED')),
    ADD COLUMN status_version BIGINT NOT NULL DEFAULT 0 CHECK(status_version >= 0),
    ADD COLUMN status_changed_at TIMESTAMPTZ,
    ADD COLUMN status_reason_code VARCHAR(32);
CREATE TABLE dt_admin_control (id INTEGER PRIMARY KEY CHECK(id=1), revision BIGINT NOT NULL DEFAULT 0);
INSERT INTO dt_admin_control(id) VALUES (1);
CREATE TABLE dt_platform_admin (user_id UUID PRIMARY KEY REFERENCES dt_account(id));
CREATE TABLE dt_admin_workspace_grant (
    user_id UUID NOT NULL REFERENCES dt_account(id), workspace_id UUID NOT NULL REFERENCES dt_workspace(id),
    capability VARCHAR(32) NOT NULL CHECK(capability IN ('KNOWLEDGE_READ','KNOWLEDGE_MANAGE','SKILL_READ','SKILL_MANAGE')),
    PRIMARY KEY(user_id,workspace_id,capability)
);
CREATE TABLE dt_admin_audit (
    id UUID PRIMARY KEY, occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actor_kind VARCHAR(16) NOT NULL CHECK(actor_kind IN ('ACCOUNT','MAINTENANCE')),
    actor_user_id UUID REFERENCES dt_account(id), operator_ref VARCHAR(100), workspace_id UUID REFERENCES dt_workspace(id),
    target_type VARCHAR(32) NOT NULL, target_id VARCHAR(128), action VARCHAR(64) NOT NULL,
    result VARCHAR(16) NOT NULL CHECK(result IN ('SUCCESS','NO_CHANGE','FAILURE','DENIED')),
    reason_code VARCHAR(32), error_code VARCHAR(64), request_id UUID NOT NULL,
    before_state JSONB, after_state JSONB
);
CREATE INDEX dt_admin_audit_time ON dt_admin_audit(occurred_at DESC,id DESC);
ALTER TABLE dt_skill_installation ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK(revision >= 0),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 执行 actor 固定到授权时的账号状态代次；恢复不能复活停用前的 Run。
ALTER TABLE dt_agent_run ADD COLUMN execution_actor_status_version BIGINT NOT NULL DEFAULT 0;
