package io.devicetruth.application.agent;

import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.AgentRunRepository.RunRecord;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput.PendingQuestion;
import io.devicetruth.core.contract.Checks;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.core.runtime.TaskOrchestrator;
import io.devicetruth.core.runtime.TaskOrchestrator.AgentRunOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 产品入口的通用 Agent 服务：校验/授权 → 原子写消息与任务 → 有界 worker 执行唯一主循环。
 * 每轮 worker 只做一件事：从 PG 重建上下文、调用 TaskOrchestrator.runAgent、把结果原子写回。
 * ASK_USER 提交后立即释放 worker（不阻塞等待用户）；补答会以同 run 重新入队。
 */
public final class GeneralAgentService {
    private static final Logger LOG = LoggerFactory.getLogger(GeneralAgentService.class);

    public record ServerIdentity(String ownerId, String projectId) {
        public ServerIdentity { Checks.id(ownerId, "ownerId"); Checks.id(projectId, "projectId"); }
    }
    /** 线上合同（schemaVersion=1）；身份字段不允许出现在请求体里。 */
    public record AgentTaskInput(Integer schemaVersion, String userMessage, String selectedAssetId,
                                 List<Map<String, Object>> attachments, List<Map<String, Object>> contextRefs,
                                 Instant asOf, Map<String, Object> replyTo) {}
    public record ConversationView(UUID conversationId, long version, Instant createdAt, Instant updatedAt) {}
    public record MessageView(UUID messageId, long seq, String role, String content, String selectedAssetId,
                              Instant requestedAt, Instant asOf) {}
    /** 运行视图：待答问题与最终答案都从持久层读回，刷新页面不会丢状态。 */
    public record RunView(UUID runId, UUID conversationId, UUID goalMessageId, String status, long version,
                          Instant requestedAt, Instant asOf, String dataNature,
                          PendingQuestion pendingQuestion, AgentResult result, String failureCode) {}
    public record ConversationDetail(ConversationView conversation, List<MessageView> messages, List<RunView> runs) {}
    public static final class RequestFailure extends RuntimeException {
        private final String code;
        private final int status;
        public RequestFailure(String code, int status) { super(code); this.code = code; this.status = status; }
        public String code() { return code; }
        public int status() { return status; }
    }

    private static final Set<String> REPLY_KEYS = Set.of("runId", "pendingQuestionId", "questionVersion");

    private final ConversationRepository conversations;
    private final AgentRunRepository runs;
    private final TransactionTemplate transactions;
    private final ServerIdentity identity;
    private final CapabilityGateway gateway;
    private final Supplier<GeneralReasoner> reasoners;
    private final TaskOrchestrator orchestrator;
    private final boolean workerEnabled;
    private final ThreadPoolExecutor executor;
    private final io.devicetruth.knowledge.FileUploadService files;

    public GeneralAgentService(ConversationRepository conversations, AgentRunRepository runs,
                               TransactionTemplate transactions, ServerIdentity identity,
                               CapabilityGateway gateway, Supplier<GeneralReasoner> reasoners,
                               int maxConcurrentRuns, int maxQueue, boolean workerEnabled) {
        this(conversations, runs, transactions, identity, gateway, reasoners, maxConcurrentRuns, maxQueue,
                workerEnabled, null);
    }

    public GeneralAgentService(ConversationRepository conversations, AgentRunRepository runs,
                               TransactionTemplate transactions, ServerIdentity identity,
                               CapabilityGateway gateway, Supplier<GeneralReasoner> reasoners,
                               int maxConcurrentRuns, int maxQueue, boolean workerEnabled,
                               io.devicetruth.knowledge.FileUploadService files) {
        if (maxConcurrentRuns < 1 || maxQueue < 0) throw new IllegalArgumentException("Worker limits must be positive");
        this.conversations = conversations;
        this.runs = runs;
        this.transactions = transactions;
        this.identity = identity;
        this.gateway = gateway;
        this.reasoners = reasoners;
        this.orchestrator = new TaskOrchestrator(gateway);
        this.workerEnabled = workerEnabled;
        this.files = files;
        AtomicInteger seq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(maxConcurrentRuns, maxConcurrentRuns, 30, TimeUnit.SECONDS,
                // 容量 0 表示“占满即拒绝”，用 SynchronousQueue 表达，而不是把配置悄悄改成 1。
                maxQueue == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(maxQueue), runnable -> {
                    Thread thread = new Thread(runnable, "devicetruth-agent-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public ConversationView create() { return conversations.create(identity, Instant.now()); }
    public List<ConversationView> list() { return conversations.list(identity); }

    public ConversationDetail read(UUID conversationId) {
        return transactions.execute(status -> {
            // 与提交共用行锁，避免把旧版本与刚提交的消息拼成一次读取结果。
            var conversation = conversations.require(identity, conversationId, true);
            return new ConversationDetail(conversation, conversations.messages(identity, conversationId), runs.list(identity, conversationId));
        });
    }

    public RunView run(UUID runId) { return runs.require(identity, runId); }

    /**
     * 启动时接手上一个进程留下的全部 QUEUED 任务（M0 的正常队列交接），不设条数上限。
     * 只扫 QUEUED：RUNNING 遗留属于崩溃恢复，需要租约/CAS（M2），这里不冒充可恢复。
     * 执行器拒绝时由 dispatch 明确置 FAILED/QUEUE_REJECTED，不会留下幽灵任务。
     */
    public void recoverQueued() {
        if (!workerEnabled) return;
        List<UUID> queued;
        try {
            queued = runs.queued(identity);
        } catch (RuntimeException e) {
            LOG.error("Agent queue recovery scan failed: {}", e.getClass().getSimpleName());
            return;
        }
        for (UUID runId : queued) dispatch(runId);
        if (!queued.isEmpty()) LOG.info("Agent queue recovery dispatched {} queued runs", queued.size());
    }

    /** 本 run 已取回的知识引用；读取仍按归属过滤。 */
    public List<Reference> references(UUID runId) {
        runs.require(identity, runId);
        return runs.references(identity, runId);
    }

    public List<AgentRunRepository.ToolEvent> toolEvents(UUID runId) {
        runs.require(identity, runId);
        return runs.toolEvents(identity, runId);
    }

    /**
     * 提交一条消息：普通消息开启新任务；带 replyTo 三元组的消息是对当前 run 待答问题的补答，
     * 关联不成立时返回 REPLY_TARGET_REQUIRED/STALE_OR_UNRELATED_REPLY，绝不静默新建 run。
     */
    public RunView submit(UUID conversationId, UUID requestKey, AgentTaskInput input) {
        if (requestKey == null || input == null || input.userMessage() == null || input.userMessage().isBlank()
                || input.userMessage().codePointCount(0, input.userMessage().length()) > 8000
                || input.userMessage().indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid message");
        if (!Integer.valueOf(1).equals(input.schemaVersion())) throw new RequestFailure("SCHEMA_VERSION_UNSUPPORTED", 400);
        if (input.selectedAssetId() != null) Checks.id(input.selectedAssetId(), "selectedAssetId");
        List<Map<String, Object>> attachments = validatedAttachments(input.attachments());
        List<Map<String, Object>> contextRefs = validatedContextRefs(input.contextRefs());
        if (input.replyTo() != null) return reply(conversationId, requestKey, input, attachments, contextRefs);

        Instant now = Instant.now();
        Instant asOf = input.asOf() == null ? now : input.asOf();
        RunView run = transactions.execute(status -> {
            conversations.require(identity, conversationId, true);
            if (conversations.hasRequest(identity, conversationId, requestKey))
                throw new RequestFailure("DUPLICATE_REQUEST", 409);
            UUID messageId = conversations.appendWithReferences(identity, conversationId, requestKey,
                    input.userMessage(), input.selectedAssetId(), now, asOf, attachments, contextRefs);
            RunView created = runs.enqueue(identity, conversationId, messageId, now, asOf);
            importContextRefs(contextRefs, created.runId());
            return created;
        });
        dispatch(run.runId());
        return run;
    }

    /** 补答：同一事务里校验归属/等待状态/问题匹配，写消息、消费问题并把 run 重新入队。 */
    private RunView reply(UUID conversationId, UUID requestKey, AgentTaskInput input,
                          List<Map<String, Object>> attachments, List<Map<String, Object>> contextRefs) {
        var target = replyTarget(input.replyTo());
        Instant now = Instant.now();
        Instant asOf = input.asOf() == null ? now : input.asOf();
        RunView run = transactions.execute(status -> {
            conversations.require(identity, conversationId, true);
            RunRecord record = runs.load(identity, target.runId(), true);
            if (!record.conversationId().equals(conversationId)
                    || !"WAITING_FOR_USER".equals(record.status())
                    || !target.pendingQuestionId().equals(record.pendingQuestionId())
                    || target.questionVersion() != record.questionVersion())
                throw new RequestFailure("STALE_OR_UNRELATED_REPLY", 409);
            if (conversations.hasRequest(identity, conversationId, requestKey))
                throw new RequestFailure("DUPLICATE_REQUEST", 409);
            conversations.appendReplyWithReferences(identity, conversationId, requestKey, input.userMessage(),
                    input.selectedAssetId(), now, asOf, record.runId(), record.pendingQuestionId(),
                    record.questionVersion(), attachments, contextRefs);
            if (runs.queueReply(identity, record, target.pendingQuestionId(), target.questionVersion(), now) != 1)
                throw new RequestFailure("STALE_OR_UNRELATED_REPLY", 409);
            importContextRefs(contextRefs, record.runId());
            return runs.require(identity, record.runId());
        });
        dispatch(run.runId());
        return run;
    }

    private List<Map<String, Object>> validatedAttachments(List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        if (files == null) throw new RequestFailure("REFERENCES_NOT_AVAILABLE", 400);
        return files.readyAttachments(identity.ownerId(), identity.projectId(), attachments);
    }

    private static List<Map<String, Object>> validatedContextRefs(List<Map<String, Object>> contextRefs) {
        if (contextRefs == null || contextRefs.isEmpty()) return List.of();
        if (contextRefs.size() > 10) throw new RequestFailure("INVALID_CONTEXT_REFERENCE", 400);
        List<Map<String, Object>> validated = new java.util.ArrayList<>();
        for (Map<String, Object> reference : contextRefs) {
            if (reference == null || !reference.keySet().equals(Set.of("referenceId")))
                throw new RequestFailure("INVALID_CONTEXT_REFERENCE", 400);
            try {
                validated.add(Map.of("referenceId", UUID.fromString(String.valueOf(reference.get("referenceId"))).toString()));
            } catch (RuntimeException e) {
                throw new RequestFailure("INVALID_CONTEXT_REFERENCE", 400);
            }
        }
        return List.copyOf(validated);
    }

    private void importContextRefs(List<Map<String, Object>> contextRefs, UUID runId) {
        for (Map<String, Object> reference : contextRefs)
            runs.importReference(identity, UUID.fromString(String.valueOf(reference.get("referenceId"))), runId);
    }

    /** 三元组缺字段或类型不合法统一按 REPLY_TARGET_REQUIRED 拒绝，不猜测关联目标。 */
    private static AgentTaskInputTarget replyTarget(Map<String, Object> replyTo) {
        for (String key : replyTo.keySet()) if (!REPLY_KEYS.contains(key)) throw replyRequired();
        Object runId = replyTo.get("runId");
        Object questionId = replyTo.get("pendingQuestionId");
        Object version = replyTo.get("questionVersion");
        if (runId == null || questionId == null || version == null) throw replyRequired();
        try {
            int questionVersion = version instanceof Number number && number.doubleValue() == number.intValue()
                    ? number.intValue() : Integer.parseInt(String.valueOf(version));
            return new AgentTaskInputTarget(UUID.fromString(String.valueOf(runId)),
                    UUID.fromString(String.valueOf(questionId)), questionVersion);
        } catch (RuntimeException e) {
            throw replyRequired();
        }
    }

    private static RequestFailure replyRequired() { return new RequestFailure("REPLY_TARGET_REQUIRED", 400); }

    private record AgentTaskInputTarget(UUID runId, UUID pendingQuestionId, int questionVersion) {}

    /**
     * 交给有界执行器；被拒绝时立刻把 run 置为 FAILED/QUEUE_REJECTED，
     * 绝不留下永远 QUEUED 的幽灵任务。worker 关闭时（M0 持久化模式）只保留 QUEUED。
     */
    private void dispatch(UUID runId) {
        if (!workerEnabled) return;
        try {
            executor.execute(() -> executeRun(runId));
        } catch (RejectedExecutionException e) {
            LOG.error("Agent queue rejected: runId={}", runId);
            failQuietly(runId, "QUEUE_REJECTED");
        }
    }

    /** worker 主体：QUEUED→RUNNING（条件更新防重复接手）→ 重建上下文 → 唯一主循环 → 原子写回。 */
    private void executeRun(UUID runId) {
        RunRecord run;
        try {
            run = runs.load(identity, runId, false);
            if (!"QUEUED".equals(run.status())) return;
            if (runs.markRunning(identity, run, Instant.now()) != 1) return;
        } catch (RuntimeException e) {
            LOG.error("Agent run claim failed: runId={} type={}", runId, e.getClass().getSimpleName());
            return;
        }
        AgentRunOutcome outcome;
        try {
            var messages = conversations.runContextMessages(identity, runId, run.goalMessageId()).stream()
                    .map(message -> new io.devicetruth.core.contract.AgentTaskInput.Message(message.role(),
                            message.content(), message.attachments()))
                    .toList();
            var input = new io.devicetruth.core.contract.AgentTaskInput(identity.ownerId(), identity.projectId(),
                    run.conversationId(), runId, run.goalMessageId(), run.goalText(), messages,
                    run.selectedAssetId(), run.requestedAt(), run.asOf(), run.version(), run.stepsUsed(),
                    run.maxSteps());
            var toolContext = new ToolContext(identity.ownerId(), identity.projectId(), run.conversationId(), runId,
                    run.selectedAssetId(), run.requestedAt(), run.asOf(), gateway.toolNames());
            GeneralReasoner reasoner;
            try {
                reasoner = reasoners.get();
            } catch (RuntimeException e) {
                // 服务端模型配置错误：FAILED + 稳定错误码，不降级到 scripted
                LOG.error("Agent reasoner creation failed: runId={} type={}", runId, e.getClass().getSimpleName());
                failQuietly(runId, "MODEL_CONFIGURATION_MISSING");
                return;
            }
            outcome = orchestrator.runAgent(input, gateway, toolContext, runs.references(identity, runId), reasoner);
        } catch (RuntimeException e) {
            LOG.error("Agent run failed: runId={} type={}", runId, e.getClass().getSimpleName());
            failQuietly(runId, "AGENT_RUN_FAILURE");
            return;
        }
        try {
            persist(run, outcome);
        } catch (RuntimeException e) {
            // 落库失败（唯一约束、连接中断等）必须变成可观察的 FAILED，不能把 run 留在 RUNNING
            LOG.error("Agent run persistence failed: runId={} type={}", runId, e.getClass().getSimpleName());
            failQuietly(runId, "PERSIST_FAILURE");
        }
    }

    /**
     * 一次运行的结果在一个事务里落库：助手消息、run 状态、预算步数与白名单事件。
     * 加锁顺序固定为“会话行 → run 行”，与提交/补答入口一致，避免与并发补答互相等待。
     */
    private void persist(RunRecord run, AgentRunOutcome outcome) {
        Instant now = Instant.now();
        transactions.execute(status -> {
            if (outcome.status() == TaskOrchestrator.AgentStatus.WAITING_FOR_USER) {
                var question = outcome.question().withVersion(run.questionVersion() + 1);
                conversations.appendAssistant(identity, run.conversationId(), run.runId(), question.question(),
                        now, run.asOf());
                for (var event : outcome.events())
                    if ("TOOL_RESULT".equals(event.type()))
                        runs.appendEvent(identity, run.runId(), run.conversationId(), "RUN_TOOL", event.data(), now);
                runs.waitForUser(identity, run, question, outcome.stepsUsed(), outcome.references().size(), now);
            } else if (outcome.status() == TaskOrchestrator.AgentStatus.COMPLETED) {
                conversations.appendAssistant(identity, run.conversationId(), run.runId(),
                        outcome.answer().summary(), now, run.asOf());
                for (var event : outcome.events())
                    if ("TOOL_RESULT".equals(event.type()))
                        runs.appendEvent(identity, run.runId(), run.conversationId(), "RUN_TOOL", event.data(), now);
                runs.complete(identity, run, outcome.answer(), outcome.stepsUsed(), now);
            } else {
                for (var event : outcome.events())
                    if ("TOOL_RESULT".equals(event.type()))
                        runs.appendEvent(identity, run.runId(), run.conversationId(), "RUN_TOOL", event.data(), now);
                runs.fail(identity, run, outcome.stopReason(), outcome.stepsUsed(), now);
            }
            return null;
        });
    }

    private void failQuietly(UUID runId, String code) {
        try {
            transactions.execute(status -> {
                RunRecord record = runs.load(identity, runId, true);
                runs.fail(identity, record, code, record.stepsUsed(), Instant.now());
                return null;
            });
        } catch (RuntimeException e) {
            LOG.error("Agent run failure marking failed: runId={} type={}", runId, e.getClass().getSimpleName());
        }
    }

    /** 关闭执行器（仅测试/停机用），不阻塞等待结果。 */
    public void shutdown() { executor.shutdownNow(); }

    public static RequestFailure denied() { return new RequestFailure("NOT_FOUND_OR_DENIED", 404); }
}
