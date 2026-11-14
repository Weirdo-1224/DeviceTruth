-- 只保存有界执行上下文。旧 Run 使用现有预算、授权引用与 Skill 记录恢复。
ALTER TABLE dt_agent_run
    ADD COLUMN execution_checkpoint JSONB,
    ADD COLUMN active_accounted_at TIMESTAMPTZ;

ALTER TABLE dt_run_skill ADD COLUMN active_reference_name VARCHAR(128);
