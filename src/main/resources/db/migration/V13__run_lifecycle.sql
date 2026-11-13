-- 03-03：幂等、取消、总时限与用量观测。
-- cancel_requested_at：取消标记。QUEUED/WAITING_FOR_USER 收到取消立即迁移终态 CANCELLED；
-- RUNNING 由持有租约的 worker 在每步之间协作式检查，只记录不提交迟到响应。
-- started_at/finished_at：最近一次执行段的墙钟起止（worker 释放后 finished_at 保留最后一段结束时刻）。
-- active_duration_ms：累计执行时长（不含等待用户的时间）；恢复重跑继续累计，不重置。
-- 只前进迁移：旧行三列均为空/0，语义与“从未取消、尚无执行段”一致。
ALTER TABLE dt_agent_run
    ADD COLUMN cancel_requested_at TIMESTAMPTZ,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN finished_at TIMESTAMPTZ,
    ADD COLUMN active_duration_ms BIGINT NOT NULL DEFAULT 0 CHECK (active_duration_ms >= 0);
