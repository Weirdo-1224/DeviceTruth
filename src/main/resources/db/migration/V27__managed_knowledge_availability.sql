ALTER TABLE dt_knowledge_document ADD COLUMN availability VARCHAR(16) NOT NULL DEFAULT 'ENABLED' CHECK(availability IN ('ENABLED','DISABLED')),
    ADD COLUMN management_revision BIGINT NOT NULL DEFAULT 0 CHECK(management_revision>=0),
    ADD COLUMN management_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TABLE dt_admin_import (
    file_id UUID PRIMARY KEY REFERENCES dt_uploaded_file(id), workspace_id UUID NOT NULL REFERENCES dt_workspace(id),
    actor_user_id UUID NOT NULL REFERENCES dt_account(id), actor_status_version BIGINT NOT NULL,
    source_id VARCHAR(128) NOT NULL, license_code VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0, request_id UUID NOT NULL,
    import_lease_owner VARCHAR(64),import_lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

ALTER TABLE dt_uploaded_file ADD COLUMN uploaded_by_status_version BIGINT;
