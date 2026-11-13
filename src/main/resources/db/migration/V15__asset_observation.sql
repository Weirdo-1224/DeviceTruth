-- 04-02 观测演示数据独立于资产现场核验，不参与当前装机确认。
CREATE TABLE dt_asset_observation (
    asset_id VARCHAR(128) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    project_id VARCHAR(128) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    signal VARCHAR(128) NOT NULL CHECK (length(btrim(signal)) > 0),
    value VARCHAR(256) NOT NULL CHECK (length(btrim(value)) > 0),
    unit VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL CHECK (length(btrim(source_id)) > 0),
    quality VARCHAR(16) NOT NULL CHECK (quality IN ('GOOD', 'UNCERTAIN', 'BAD')),
    data_nature VARCHAR(16) NOT NULL DEFAULT 'SIMULATED' CHECK (data_nature = 'SIMULATED'),
    PRIMARY KEY (asset_id, owner_id, project_id, signal, observed_at, source_id),
    FOREIGN KEY (asset_id, owner_id, project_id) REFERENCES dt_asset(id, owner_id, project_id)
);
CREATE INDEX dt_asset_observation_scope_time
    ON dt_asset_observation(owner_id, project_id, asset_id, observed_at DESC);
