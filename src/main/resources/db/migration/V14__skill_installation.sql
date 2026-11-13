CREATE TABLE dt_skill_installation (
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    skill_id VARCHAR(64) NOT NULL,
    version TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    allowed_tools TEXT[] NOT NULL,
    PRIMARY KEY (owner_id, project_id, skill_id, version)
);

CREATE TABLE dt_run_skill (
    run_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    skill_id VARCHAR(64) NOT NULL,
    version TEXT NOT NULL,
    devices TEXT[] NOT NULL,
    tasks TEXT[] NOT NULL,
    allowed_tools TEXT[] NOT NULL,
    document_digest CHAR(64) NOT NULL,
    FOREIGN KEY (run_id, conversation_id, owner_id, project_id)
        REFERENCES dt_agent_run(id, conversation_id, owner_id, project_id),
    FOREIGN KEY (owner_id, project_id, skill_id, version)
        REFERENCES dt_skill_installation(owner_id, project_id, skill_id, version)
);

CREATE TABLE dt_run_skill_reference (
    run_id UUID NOT NULL REFERENCES dt_run_skill(run_id),
    name VARCHAR(128) NOT NULL,
    content_digest CHAR(64) NOT NULL,
    PRIMARY KEY (run_id, name)
);
