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
    private final int queuedScanLimit;

    public RunRecoveryService(AgentRunRepository runs, int maxRecoveryAttempts) {
        this(runs, maxRecoveryAttempts, 50);
    }

    public RunRecoveryService(AgentRunRepository runs, int maxRecoveryAttempts, int queuedScanLimit) {
        if (maxRecoveryAttempts < 1) throw new IllegalArgumentException("maxRecoveryAttempts must be positive");
        if (queuedScanLimit < 1) throw new IllegalArgumentException("queuedScanLimit must be positive");
        this.runs = runs;
        this.maxRecoveryAttempts = maxRecoveryAttempts;
        this.queuedScanLimit = queuedScanLimit;
    }

    /**
     * 周期 QUEUED 补派（P1-04 修复）：有界扫描滞留 QUEUED 并重新交给调度器。
     * 与启动全量对账同规则：累计执行时长超限的明确 FAILED/TOTAL_TIMEOUT，不重置预算；
     * 单个 run 调度失败只记日志不中断扫描，由下一周期重试——一次临时领取失败无需重启进程。
     */
    public int reconcileQueued(ServerIdentity identity, RunDispatcher dispatcher) {
        List<UUID> queuedRuns;
        try {
            queuedRuns = runs.queuedPage(identity, queuedScanLimit);
        } catch (RuntimeException e) {
            LOG.error("Agent periodic queued scan failed: {}", e.getClass().getSimpleName());
            return 0;
        }
        int dispatched = 0;
        for (UUID runId : queuedRuns) {
            try {
                var record = runs.load(identity, runId, false);
                if (record.activeDurationMs() >= record.maxActiveDurationMs()) {
                    if (runs.fail(identity, record, "TOTAL_TIMEOUT", record.stepsUsed(), 0, 0, 0, Instant.now()) == 1)
                        LOG.info("Agent periodic queued run timed out: runId={}", runId);
                    continue;
                }
                dispatcher.dispatch(runId);
                dispatched++;
            } catch (RuntimeException e) {
                LOG.error("Agent periodic queued dispatch failed: runId={} type={}", runId,
                        e.getClass().getSimpleName());
            }
        }
        return dispatched;
    }

    /** 启动时执行一次全量对账；worker 关闭的进程不调度任何任务（M0 只保留 QUEUED 合同）。 */
    public RecoveryReport reconcile(ServerIdentity identity, boolean workerEnabled, RunDispatcher dispatcher) {
        if (!workerEnabled) return new RecoveryReport(0, 0, 0);
        int queued = 0, failed = 0;
        List<UUID> queuedRuns;
        try {
            queuedRuns = runs.queued(identity);
        } catch (RuntimeException e) {
            LOG.error("Agent queue recovery scan failed: {}", e.getClass().getSimpleName());
            return new RecoveryReport(0, 0, 0);
        }
        for (UUID runId : queuedRuns) {
            try {
                // 累计执行时长已超限的遗留 QUEUED 不再调度：明确 FAILED/TOTAL_TIMEOUT，绝不留幽灵任务
                var record = runs.load(identity, runId, false);
                if (record.activeDurationMs() >= record.maxActiveDurationMs()) {
                    if (runs.fail(identity, record, "TOTAL_TIMEOUT", record.stepsUsed(), 0, 0, 0, Instant.now()) == 1)
                        failed++;
                    continue;
                }
                dispatcher.dispatch(runId);
                queued++;
            } catch (RuntimeException e) {
                LOG.error("Agent queue recovery dispatch failed: runId={} type={}", runId, e.getClass().getSimpleName());
            }
        }
        var recovered = reconcileExpired(identity, dispatcher);
        return new RecoveryReport(queued, recovered.runningResumed(), failed + recovered.runningFailed());
    }

    RecoveryReport reconcileExpired(ServerIdentity identity, RunDispatcher dispatcher) {
        Instant now = Instant.now();
        int resumed = 0, failed = 0;
        List<ExpiredRun> expired;
        try {
            expired = runs.runningWithExpiredLease(identity, now);
        } catch (RuntimeException e) {
            LOG.error("Agent crashed-run recovery scan failed: {}", e.getClass().getSimpleName());
            return new RecoveryReport(0, 0, 0);
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
        if (resumed > 0 || failed > 0)
            LOG.info("Agent lease reconciliation: runningResumed={} runningFailed={}", resumed, failed);
        return new RecoveryReport(0, resumed, failed);
    }
}
