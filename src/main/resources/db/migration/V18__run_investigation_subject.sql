ALTER TABLE dt_agent_run
    ADD COLUMN subject_asset_id VARCHAR(128),
    ADD COLUMN subject_status VARCHAR(16) NOT NULL DEFAULT 'UNCONFIRMED'
        CHECK (subject_status IN ('SELECTED', 'CONFIRMED', 'AMBIGUOUS', 'UNCONFIRMED')),
    ADD COLUMN subject_changed_from VARCHAR(128);

UPDATE dt_agent_run r
SET subject_asset_id = m.selected_asset_id,
    subject_status = CASE WHEN m.selected_asset_id IS NULL THEN 'UNCONFIRMED' ELSE 'SELECTED' END
FROM dt_message m
WHERE m.id = r.goal_message_id;
