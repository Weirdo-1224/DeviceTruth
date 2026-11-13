-- 03-01 第二轮复审修复（P1）：配置事实身份遗漏生效时点。
-- 同一来源记载同一组件属性在不同时间的变化（不同 valid_from）是不同历史事实，
-- V10 的唯一键 (来源+组件+属性) 会把后导入的历史版本静默丢弃（ON CONFLICT DO NOTHING）。
-- 按约束定义定位 V10 自动命名的唯一键（名字随 PG 标识符截断可能变化，不用硬编码），
-- 替换为包含 valid_from 的事实身份；重复导入同一事实仍然幂等，历史记录不丢、不改写。

DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'dt_asset_configuration'::regclass
      AND contype = 'u'
      AND pg_get_constraintdef(oid) LIKE
          'UNIQUE (asset_id, owner_id, project_id, source_id, component_id, attribute)%';
    IF cname IS NULL THEN
        RAISE EXCEPTION 'V10 configuration unique constraint not found';
    END IF;
    EXECUTE format('ALTER TABLE dt_asset_configuration DROP CONSTRAINT %I', cname);
END $$;

ALTER TABLE dt_asset_configuration
    ADD UNIQUE (asset_id, owner_id, project_id, source_id, component_id, attribute, valid_from);
