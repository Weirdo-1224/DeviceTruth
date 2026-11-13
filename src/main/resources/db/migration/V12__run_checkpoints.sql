-- 03-02：跨进程稳定检查点与崩溃恢复。
-- 执行租约（lease）让 RUNNING 任务可被判活：worker 领取/续租，新进程只回收租约已过期的遗留 RUNNING，
-- 绝不在旧 worker 可能仍活着时重复执行；attempts 记录领取次数，恢复重调度有界。
-- WAITING_FOR_USER 不持租约（worker 已释放），QUEUED 等待调度同样不持租约。
-- 只前进迁移：旧行 lease 为空，恢复扫描把“无租约的 RUNNING”视为可回收的崩溃遗留。
ALTER TABLE dt_agent_run
    ADD COLUMN lease_owner VARCHAR(64),
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD COLUMN lease_heartbeat_at TIMESTAMPTZ,
    ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0);
