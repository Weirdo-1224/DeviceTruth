package io.devicetruth.application.agent;

import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.AgentRunRepository.ExpiredRun;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动对账（03-02）：把上一个进程留下的任务按 PG 持久检查点交接给当前进程。
 * QUEUED 直接重新调度；RUNNING 只在执行租约已过期时回收——或按检查点恢复重新入队，
 * 或超出恢复上限时明确 FAILED；租约仍有效的 RUNNING 属于活着的 worker，绝不重复接手；
 * WAITING_FOR_USER 不持租约、原样保留。全部迁移用 version + 租约过期条件 CAS 提交，
 * 两个进程同时启动对账也不会把同一 run 回收两次。
 */
public final class RunRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(RunRecoveryService.class);

    /** 调度出口：由 GeneralAgentService 提供，回收成功的 run 重新进入有界 worker。 */
    @FunctionalInterface
    public interface RunDispatcher { void dispatch(UUID runId); }

    public record RecoveryReport(int queuedDispatched, int runningResumed, int runningFailed) {
        public int resumedTotal() { return queuedDispatched + runningResumed + runningFailed; }
    }

    private final AgentRunRepository runs;
    private final int maxRecoveryAttempts;

    public RunRecoveryService(AgentRunRepository runs, int maxRecoveryAttempts) {
        if (maxRecoveryAttempts < 1) throw new IllegalArgumentException("maxRecoveryAttempts must be positive");
        this.runs = runs;
        this.maxRecoveryAttempts = maxRecoveryAttempts;
    }

    /** 启动时执行一次全量对账；worker 关闭的进程不调度任何任务（M0 只保留 QUEUED 合同）。 */
    public RecoveryReport reconcile(ServerIdentity identity, boolean workerEnabled, RunDispatcher dispatcher) {
        if (!workerEnabled) return new RecoveryReport(0, 0, 0);
        Instant now = Instant.now();
        int queued = 0, resumed = 0, failed = 0;
        List<UUID> queuedRuns;
        try {
            queuedRuns = runs.queued(identity);
        } catch (RuntimeException e) {
            LOG.error("Agent queue recovery scan failed: {}", e.getClass().getSimpleName());
            return new RecoveryReport(0, 0, 0);
        }
        for (UUID runId : queuedRuns) {
            try {
                dispatcher.dispatch(runId);
                queued++;
            } catch (RuntimeException e) {
                LOG.error("Agent queue recovery dispatch failed: runId={} type={}", runId, e.getClass().getSimpleName());
            }
        }
        List<ExpiredRun> expired;
        try {
            expired = runs.runningWithExpiredLease(identity, now);
        } catch (RuntimeException e) {
            LOG.error("Agent crashed-run recovery scan failed: {}", e.getClass().getSimpleName());
            return new RecoveryReport(queued, 0, 0);
        }
        for (ExpiredRun run : expired) {
            try {
                if (run.attempts() >= maxRecoveryAttempts) {
                    // 只读工具的重试有界：同一 run 崩溃超过上限后明确失败，禁止永久 RUNNING。
                    if (runs.failRecovered(identity, run, "RECOVERY_ATTEMPT_LIMIT", Instant.now()) == 1) failed++;
                    continue;
                }
                if (runs.requeueRecovered(identity, run, Instant.now()) == 1) {
                    dispatcher.dispatch(run.runId());
                    resumed++;
                }
            } catch (RuntimeException e) {
                LOG.error("Agent crashed-run recovery failed: runId={} type={}", run.runId(),
                        e.getClass().getSimpleName());
            }
        }
        if (queued > 0 || resumed > 0 || failed > 0)
            LOG.info("Agent startup reconciliation: queuedDispatched={} runningResumed={} runningFailed={}",
                    queued, resumed, failed);
        return new RecoveryReport(queued, resumed, failed);
    }
}
