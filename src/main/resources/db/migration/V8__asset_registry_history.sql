-- 设备/组件/配置/维修结构化主数据（03-01）。
-- 资产身份独立于证据 payload：本表只保存身份与归属，不复制证据内容，
-- 结构化记录通过 source_id 指向 dt_evidence_reference 中的来源条目。
-- 时点口径：valid_from/valid_to 是记录在适用业务时点上的有效性区间（valid_to 为空表示未失效），
-- recorded_at 是记录进入系统的时刻；observed_at 仅现场核验类记录存在。
-- verification_status 区分“资料记载/DOCUMENTED”与“现场已核实/FIELD_VERIFIED”，时间线值只表示时刻语义，
-- 是否已现场核验必须以 verification_status 判定。
-- 归属（owner_id/project_id）冗余在每张表并通过复合外键防止跨归属串接；所有查询参数化。

CREATE TABLE dt_asset (
    id VARCHAR(128) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, owner_id, project_id)
);

CREATE TABLE dt_asset_component (
    asset_id VARCHAR(128) NOT NULL,
    component_id VARCHAR(128) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    label VARCHAR(256) NOT NULL CHECK (length(btrim(label)) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (asset_id, component_id),
    UNIQUE (asset_id, component_id, owner_id, project_id),
    FOREIGN KEY (asset_id, owner_id, project_id) REFERENCES dt_asset(id, owner_id, project_id)
);
CREATE INDEX dt_asset_component_owner ON dt_asset_component(owner_id, project_id, asset_id);

CREATE TABLE dt_asset_configuration (
    id UUID PRIMARY KEY,
    asset_id VARCHAR(128) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    component_id VARCHAR(128) NOT NULL,
    attribute VARCHAR(128) NOT NULL,
    value VARCHAR(512) NOT NULL CHECK (length(btrim(value)) > 0),
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN
        ('FACTORY', 'REPLACEMENT', 'CONFIGURATION', 'FIELD_CONFIRMATION', 'FIELD_OBSERVATION', 'USER_STATEMENT')),
    source_id VARCHAR(128) NOT NULL,
    source_ref TEXT NOT NULL,
    data_nature VARCHAR(16) NOT NULL CHECK (data_nature IN ('SIMULATED', 'AUTHORIZED')),
    verification_status VARCHAR(16) NOT NULL CHECK (verification_status IN ('DOCUMENTED', 'FIELD_VERIFIED')),
    authority INTEGER NOT NULL CHECK (authority >= 0),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    recorded_at TIMESTAMPTZ NOT NULL,
    observed_at TIMESTAMPTZ,
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (recorded_at >= COALESCE(observed_at, valid_from)),
    CHECK (verification_status <> 'FIELD_VERIFIED' OR (source_type = 'FIELD_CONFIRMATION' AND observed_at IS NOT NULL)),
    UNIQUE (asset_id, owner_id, project_id, source_id),
    FOREIGN KEY (asset_id, owner_id, project_id) REFERENCES dt_asset(id, owner_id, project_id),
    FOREIGN KEY (asset_id, component_id, owner_id, project_id)
        REFERENCES dt_asset_component(asset_id, component_id, owner_id, project_id)
);
CREATE INDEX dt_asset_configuration_timeline
    ON dt_asset_configuration(owner_id, project_id, asset_id, component_id, valid_from);

CREATE TABLE dt_asset_maintenance (
    id UUID PRIMARY KEY,
    asset_id VARCHAR(128) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    component_id VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL CHECK (length(btrim(operation)) > 0),
    before_value VARCHAR(128),
    after_value VARCHAR(128),
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN
        ('FACTORY', 'REPLACEMENT', 'CONFIGURATION', 'FIELD_CONFIRMATION', 'FIELD_OBSERVATION', 'USER_STATEMENT')),
    source_id VARCHAR(128) NOT NULL,
    source_ref TEXT NOT NULL,
    data_nature VARCHAR(16) NOT NULL CHECK (data_nature IN ('SIMULATED', 'AUTHORIZED')),
    verification_status VARCHAR(16) NOT NULL CHECK (verification_status IN ('DOCUMENTED', 'FIELD_VERIFIED')),
    authority INTEGER NOT NULL CHECK (authority >= 0),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    recorded_at TIMESTAMPTZ NOT NULL,
    observed_at TIMESTAMPTZ,
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (recorded_at >= COALESCE(observed_at, valid_from)),
    CHECK (verification_status <> 'FIELD_VERIFIED' OR (source_type = 'FIELD_CONFIRMATION' AND observed_at IS NOT NULL)),
    UNIQUE (asset_id, owner_id, project_id, source_id),
    FOREIGN KEY (asset_id, owner_id, project_id) REFERENCES dt_asset(id, owner_id, project_id),
    FOREIGN KEY (asset_id, component_id, owner_id, project_id)
        REFERENCES dt_asset_component(asset_id, component_id, owner_id, project_id)
);
CREATE INDEX dt_asset_maintenance_timeline
    ON dt_asset_maintenance(owner_id, project_id, asset_id, component_id, valid_from);