-- 03-01 复审修复（4 项 P1）。
-- 1) 资产事实引用必须保存事实摘要：引用持久化后可核对配置值/维修前后值与来源。
ALTER TABLE dt_evidence_reference ADD COLUMN citation_excerpt TEXT;

-- 2) 导入幂等键细化到“事实身份”：原唯一键 (asset,owner,project,source) 会把同一来源的
--    多条不同组件/属性/时点事实静默丢弃（ON CONFLICT DO NOTHING）。
--    新键按组件+属性（配置）/ 组件+操作+生效时点（维修）区分事实，同一来源可含多条，
--    重复导入同一事实仍然幂等。
ALTER TABLE dt_asset_configuration
    DROP CONSTRAINT dt_asset_configuration_asset_id_owner_id_project_id_source__key;
ALTER TABLE dt_asset_configuration
    ADD UNIQUE (asset_id, owner_id, project_id, source_id, component_id, attribute);

ALTER TABLE dt_asset_maintenance
    DROP CONSTRAINT dt_asset_maintenance_asset_id_owner_id_project_id_source_id_key;
ALTER TABLE dt_asset_maintenance
    ADD UNIQUE (asset_id, owner_id, project_id, source_id, component_id, operation, valid_from);
