-- 消息与运行的可追溯关联。
-- 助手消息（追问/答案）与用户补答都必须指向所属 run，补答后才能用 PG 精确重建“本 run 的消息序列”，
-- 不会把同一会话里其他 run 的消息混进上下文。goal 消息在 run 创建前写入，因此允许 run_id 为空，
-- 由 dt_agent_run.goal_message_id 定位。列为可空，历史数据不需要回填。
ALTER TABLE dt_message ADD COLUMN run_id UUID;

ALTER TABLE dt_message
    ADD CONSTRAINT dt_message_run_fk FOREIGN KEY (run_id, conversation_id, owner_id, project_id)
    REFERENCES dt_agent_run(id, conversation_id, owner_id, project_id);

-- 用户消息只能作为“对同 run 问题的补答”挂到 run 上；不能把普通新任务消息错挂到别的 run。
ALTER TABLE dt_message
    ADD CONSTRAINT dt_message_run_link CHECK (run_id IS NULL OR role = 'ASSISTANT' OR run_id = reply_to_run_id);

CREATE INDEX dt_message_run_seq ON dt_message(run_id, seq) WHERE run_id IS NOT NULL;
