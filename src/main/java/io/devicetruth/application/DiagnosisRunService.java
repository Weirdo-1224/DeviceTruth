package io.devicetruth.application;

import io.devicetruth.adapter.RunReportWriter;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.contract.Models.Budget;
import io.devicetruth.core.contract.Models.DiagnosisRequest;
import io.devicetruth.core.decision.DecisionGuard;
import io.devicetruth.core.evidence.EvidenceManager;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.runtime.TaskOrchestrator;
import io.devicetruth.core.runtime.TaskOrchestrator.RunReport;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 有界异步诊断运行：每任务独立推理器，真实调用既有 TaskOrchestrator。
 * 三个 ID 严格分离：diagnosisId 是唯一公开 ID，coreTaskId 只取 report.state().taskId()，
 * reportPath 是 RunReportWriter 的独立目录且只在内部保存。
 */
public final class DiagnosisRunService {
    private static final Logger LOG = LoggerFactory.getLogger(DiagnosisRunService.class);
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    /** 一次运行的快照：提交时 QUEUED，工作线程置 RUNNING，结束后置 COMPLETED/FAILED。 */
    public static final class RunSnapshot {
        private final UUID diagnosisId;
        private final UUID sessionId;
        private final Instant requestedAt;
        private final Instant diagnosisAsOf;
        private final Instant createdAt;
        private final Object lock = new Object();
        private volatile ExecutionStatus executionStatus = ExecutionStatus.QUEUED;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String coreTaskId;
        private volatile Path reportPath;
        private volatile RunReport report;
        private volatile String errorCode;
        private volatile List<TaskOrchestrator.RunEvent> events = List.of();

        private RunSnapshot(UUID diagnosisId, UUID sessionId, Instant requestedAt, Instant diagnosisAsOf) {
            this.diagnosisId = diagnosisId;
            this.sessionId = sessionId;
            this.requestedAt = requestedAt;
            this.diagnosisAsOf = diagnosisAsOf;
            this.createdAt = Instant.now();
        }

        public UUID diagnosisId() { return diagnosisId; }
        public UUID sessionId() { return sessionId; }
        public Instant requestedAt() { return requestedAt; }
        public Instant diagnosisAsOf() { return diagnosisAsOf; }
        public Instant createdAt() { return createdAt; }
        public ExecutionStatus executionStatus() { return executionStatus; }
        public Instant startedAt() { synchronized (lock) { return startedAt; } }
        public Instant completedAt() { synchronized (lock) { return completedAt; } }
        public String coreTaskId() { return coreTaskId; }
        /** 仅内部使用，绝不进入任何 HTTP 响应。 */
        public Path reportPath() { return reportPath; }
        public RunReport report() { return report; }
        public String errorCode() { return errorCode; }

        public boolean finished() {
            return executionStatus == ExecutionStatus.COMPLETED || executionStatus == ExecutionStatus.FAILED;
        }

        /** 事件只在运行结束后可读；FAILED 且没有报告时仍然不可读。 */
        public boolean eventsAvailable() { return finished() && !events.isEmpty(); }

        public List<TaskOrchestrator.RunEvent> events() { return events; }

        private void markRunning() {
            synchronized (lock) {
                startedAt = Instant.now();
                executionStatus = ExecutionStatus.RUNNING;
            }
        }

        private void markCompleted(RunReport report, Path reportPath) {
            synchronized (lock) {
                this.report = report;
                this.reportPath = reportPath;
                this.coreTaskId = report.state().taskId();
                this.events = report.events();
                // 只有 Core 自己判定 runStatus=FAILED 才是执行失败；Future 正常返回并不代表诊断成功。
                if (report.result().runStatus() == io.devicetruth.core.contract.Models.RunStatus.FAILED) {
                    this.errorCode = SafePublicFields.errorCode(report.result().stopReason());
                    this.executionStatus = ExecutionStatus.FAILED;
                } else {
                    this.executionStatus = ExecutionStatus.COMPLETED;
                }
                this.completedAt = Instant.now();
            }
        }

        private void markFailed(String errorCode) {
            synchronized (lock) {
                this.errorCode = SafePublicFields.errorCode(errorCode);
                this.executionStatus = ExecutionStatus.FAILED;
                this.completedAt = Instant.now();
            }
        }
    }

    /** 接受任务的结果：只有公共 diagnosisId 与固定 QUEUED 状态。 */
    public record Accepted(UUID diagnosisId, ExecutionStatus executionStatus) {}

    private final ConversationService conversations;
    private final CapabilityGateway gateway;
    private final ReasonerFactory reasonerFactory;
    private final Budget budget;
    private final RunReportWriter reportWriter;
    private final Path outputRoot;
    private final int maxStoredRuns;
    private final ThreadPoolExecutor executor;
    private final Map<UUID, RunSnapshot> runs = new LinkedHashMap<>();

    public DiagnosisRunService(ConversationService conversations,
                               CapabilityGateway gateway, ReasonerFactory reasonerFactory, Budget budget,
                               RunReportWriter reportWriter, Path outputRoot,
                               int maxConcurrentRuns, int maxQueue, int maxStoredRuns) {
        if (maxConcurrentRuns < 1 || maxQueue < 0 || maxStoredRuns < 1)
            throw new IllegalArgumentException("Run limits must be positive");
        this.conversations = conversations;
        this.gateway = gateway;
        this.reasonerFactory = reasonerFactory;
        this.budget = budget;
        this.reportWriter = reportWriter;
        this.outputRoot = outputRoot;
        this.maxStoredRuns = maxStoredRuns;
        AtomicInteger seq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(maxConcurrentRuns, maxConcurrentRuns, 30, TimeUnit.SECONDS,
                // 容量 0 表示"占满即拒绝"，用 SynchronousQueue 表达，而不是把配置悄悄改成 1。
                maxQueue == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(maxQueue), runnable -> {
                    Thread thread = new Thread(runnable, "devicetruth-run-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * 接受一次诊断：登记 QUEUED → 交给有界执行器。执行器拒绝时立即移除注册项并抛
     * RunCapacityExceededException，绝不留下永远 QUEUED 的幽灵任务。
     * 调用方必须先完成会话侧（容量预检 + USER/SYSTEM 原子写入），失败请求不得留下半条消息。
     */
    public Accepted submit(DiagnosisGoal goal, UUID sessionId) {
        conversations.require(sessionId);
        UUID diagnosisId = UUID.randomUUID();
        var snapshot = new RunSnapshot(diagnosisId, sessionId, goal.requestedAt(), goal.diagnosisAsOf());
        synchronized (runs) {
            purgeFinishedLocked();
            runs.put(diagnosisId, snapshot);
        }
        // 目标里的资产/域/症状在登记前已由 DiagnosisApplicationService 校验过，这里只做契约映射。
        DiagnosisRequest request = goal.toRequest();
        try {
            executor.execute(() -> execute(snapshot, request));
        } catch (RejectedExecutionException e) {
            synchronized (runs) { runs.remove(diagnosisId); }
            throw new ApplicationExceptions.RunCapacityExceededException();
        }
        return new Accepted(diagnosisId, ExecutionStatus.QUEUED);
    }

    private void execute(RunSnapshot snapshot, DiagnosisRequest request) {
        snapshot.markRunning();
        Reasoner reasoner;
        try {
            reasoner = reasonerFactory.create();
        } catch (RuntimeException e) {
            // 服务端模型配置错误：FAILED + 稳定错误码，不留下 QUEUED，也不伪装成诊断成功。
            LOG.error("Reasoner creation failed: diagnosisId={} type={}", snapshot.diagnosisId(), e.getClass().getSimpleName());
            snapshot.markFailed("MODEL_CONFIGURATION_UNREADABLE");
            return;
        }
        RunReport report;
        try {
            report = new TaskOrchestrator(gateway, new EvidenceManager(), new DecisionGuard())
                    .run(request, budget, reasoner);
        } catch (RuntimeException e) {
            LOG.error("Diagnosis run failed: diagnosisId={} type={}", snapshot.diagnosisId(), e.getClass().getSimpleName());
            snapshot.markFailed("REASONER_FAILURE");
            return;
        }
        Path path = null;
        try {
            // reportPath 与 coreTaskId 是不同来源的 UUID，必须分别保存。
            path = reportWriter.write(outputRoot, report);
        } catch (Exception e) {
            LOG.error("Run report write failed: diagnosisId={} type={}", snapshot.diagnosisId(), e.getClass().getSimpleName());
            if (report.result().runStatus() == io.devicetruth.core.contract.Models.RunStatus.FAILED) {
                snapshot.markFailed(report.result().stopReason());
            } else {
                snapshot.markFailed("REPORT_WRITE_FAILED");
            }
            return;
        }
        snapshot.markCompleted(report, path);
        LOG.info("Diagnosis finished: diagnosisId={} coreTaskId={} status={} outcome={} steps={}",
                snapshot.diagnosisId(), report.state().taskId(), snapshot.executionStatus(),
                report.result().outcome(), report.result().stepsUsed());
    }

    /** 读取归属匹配的运行快照；不存在与不属于该会话一律抛 SessionNotFoundException（HTTP 404，不区分）。 */
    public RunSnapshot require(UUID diagnosisId, UUID sessionId) {
        conversations.require(sessionId);
        RunSnapshot snapshot;
        synchronized (runs) { snapshot = diagnosisId == null ? null : runs.get(diagnosisId); }
        if (snapshot == null || !snapshot.sessionId().equals(sessionId))
            throw new ApplicationExceptions.SessionNotFoundException();
        return snapshot;
    }

    public int storedRuns() {
        synchronized (runs) { return runs.size(); }
    }

    /** 超出上限时只清理已结束的记录，运行中的绝不能丢。 */
    private void purgeFinishedLocked() {
        if (runs.size() < maxStoredRuns) return;
        var finished = new ArrayList<UUID>();
        for (var entry : runs.entrySet()) if (entry.getValue().finished()) finished.add(entry.getKey());
        var iterator = finished.iterator();
        while (runs.size() >= maxStoredRuns && iterator.hasNext()) runs.remove(iterator.next());
    }

    /** 关闭执行器（仅测试/停机用），不阻塞等待结果。 */
    public void shutdown() { executor.shutdownNow(); }
}