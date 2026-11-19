-- 保留历史全部尝试；取消不回收轮号。并发分配由既有会话行锁保护。
ALTER TABLE dt_agent_run ADD COLUMN round_number BIGINT;
WITH numbered AS (SELECT id,row_number() OVER(PARTITION BY conversation_id ORDER BY created_at,id) AS n FROM dt_agent_run)
UPDATE dt_agent_run r SET round_number=n.n FROM numbered n WHERE n.id=r.id;
ALTER TABLE dt_agent_run ALTER COLUMN round_number SET NOT NULL;
CREATE UNIQUE INDEX dt_run_round_number ON dt_agent_run(conversation_id,round_number);
