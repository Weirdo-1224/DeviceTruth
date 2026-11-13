-- 05-01 复验 §8.1：事件归属执行尝试号。
-- 步骤事件（RUN_TOOL/RUN_MODEL）记 worker 领取 RUNNING 时 attempts+1 后的当前尝试号；
-- 生命周期事件（RUN_QUEUED/RUN_RECOVERED/RUN_ANSWER/RUN_FAILED/RUN_CANCELLED/RUN_LATE_RESPONSE/RUN_WAITING）记 0。
-- 历史行保留 DEFAULT 1（均为第一次执行产生）；恢复重排按 run_id+attempt 删除被回收尝试的步骤事件。
ALTER TABLE dt_run_event ADD COLUMN attempt INT NOT NULL DEFAULT 1;
CREATE INDEX dt_run_event_run_attempt ON dt_run_event(run_id, attempt);
