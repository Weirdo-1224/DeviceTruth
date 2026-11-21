-- 帐本先于执行接入部署；未配置/未对账不得宣传为已执行配额。
CREATE TABLE dt_quota_policy (
    dimension VARCHAR(16) NOT NULL CHECK(dimension IN ('ACCOUNT','WORKSPACE','PLATFORM')),
    resource VARCHAR(32) NOT NULL CHECK(resource IN ('RUN_CREATE','ACTIVE_RUN','UNRESOLVED_RUN','UPLOAD_BYTES','IMPORT_JOB','RETRIEVAL','EMBEDDING_TOKENS','SSE_CONNECTION','STORAGE_BYTES')),
    configured_limit BIGINT NOT NULL CHECK(configured_limit >= 0),
    period_kind VARCHAR(16) NOT NULL CHECK(period_kind IN ('DAY','OCCUPANCY','PERSISTENT')),
    timezone VARCHAR(64) NOT NULL, policy_version VARCHAR(64) NOT NULL,
    PRIMARY KEY(dimension,resource)
);
CREATE TABLE dt_quota_reservation (
    reservation_key UUID NOT NULL, dimension VARCHAR(16) NOT NULL,
    subject_id VARCHAR(36) NOT NULL, resource VARCHAR(32) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL, amount BIGINT NOT NULL CHECK(amount >= 0),
    payload_digest CHAR(64) NOT NULL CHECK(payload_digest ~ '^[0-9a-f]{64}$'),
    run_id UUID REFERENCES dt_agent_run(id),
    state VARCHAR(16) NOT NULL CHECK(state IN ('RESERVED','CONSUMED','RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(reservation_key,dimension,subject_id,resource),
    FOREIGN KEY(dimension,resource) REFERENCES dt_quota_policy(dimension,resource)
);
CREATE INDEX dt_quota_subject ON dt_quota_reservation(dimension,subject_id,resource,period_start,state);
CREATE TABLE dt_quota_reconciliation (
    dimension VARCHAR(16) NOT NULL, subject_id VARCHAR(36) NOT NULL, resource VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK(state IN ('CURRENT','REQUIRED','UNKNOWN')),
    measured_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(dimension,subject_id,resource)
);
