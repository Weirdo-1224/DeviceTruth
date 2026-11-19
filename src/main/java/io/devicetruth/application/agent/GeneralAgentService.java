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
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.runtime.TaskOrchestrator;
import io.devicetruth.core.runtime.AgentExecutionCheckpoint;
import io.devicetruth.core.runtime.TaskOrchestrator.AgentRunOutcome;
import io.devicetruth.skill.SkillException;
import io.devicetruth.skill.SkillRuntime;
import java.math.BigDecimal;
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
                                 Instant asOf, Map<String, Object> replyTo,
                                 List<Map<String,Object>> discussionQuotes, Long expectedConversationVersion) {
        public AgentTaskInput(Integer schemaVersion,String userMessage,String selectedAssetId,
                List<Map<String,Object>> attachments,List<Map<String,Object>> contextRefs,Instant asOf,Map<String,Object> replyTo) {
            this(schemaVersion,userMessage,selectedAssetId,attachments,contextRefs,asOf,replyTo,null,null);
        }
    }
    public record ConversationView(UUID conversationId, long version, Instant createdAt, Instant updatedAt) {}
    public record ConversationSummary(UUID conversationId, String title, String investigatedAssetId,
                                      String latestRunStatus, int activeRunCount, int waitingRunCount,
                                      int runCount, Instant updatedAt) {}
    public record ConversationSummaryPage(List<ConversationSummary> items, String nextCursor) {}
    public record MessageView(UUID messageId, long seq, String role, String content, String selectedAssetId,
                              Instant requestedAt, Instant asOf, UUID actorUserId, List<Map<String,Object>> discussionQuotes) {
        public MessageView(UUID messageId,long seq,String role,String content,String selectedAssetId,Instant requestedAt,Instant asOf) {
            this(messageId,seq,role,content,selectedAssetId,requestedAt,asOf,null,List.of());
        }
    }
    /** 运行视图：待答问题与最终答案都从持久层读回，刷新页面不会丢状态。 */
    public record RunView(UUID runId, UUID conversationId, UUID goalMessageId, String status, long version,
                          Instant requestedAt, Instant asOf, String investigatedAssetId, String assetResolution,
                          String changedFromAssetId, String dataNature,
                          PendingQuestion pendingQuestion, AgentResult result, String failureCode,
                          Long activeDurationMs, String usageStatus,
                          Long inputTokens, Long outputTokens, BigDecimal cost,
                          Long roundNumber, UUID initiatedByUserId, UUID executionActorUserId) {
        public RunView(UUID runId,UUID conversationId,UUID goalMessageId,String status,long version,
                Instant requestedAt,Instant asOf,String investigatedAssetId,String assetResolution,
                String changedFromAssetId,String dataNature,PendingQuestion pendingQuestion,AgentResult result,
                String failureCode,Long activeDurationMs,String usageStatus,Long inputTokens,Long outputTokens,BigDecimal cost) {
            this(runId,conversationId,goalMessageId,status,version,requestedAt,asOf,investigatedAssetId,assetResolution,
                changedFromAssetId,dataNature,pendingQuestion,result,failureCode,activeDurationMs,usageStatus,
                inputTokens,outputTokens,cost,null,null,null);
        }
    }
    public record ConversationDetail(ConversationView conversation, List<MessageView> messages, List<RunView> runs) {}
    public static final class RequestFailure extends RuntimeException {
        private final String code;
        private final int status;
        public RequestFailure(String code, int status) { super(code); this.code = code; this.status = status; }
        public String code() { return code; }
        public int status() { return status; }
    }

    private static final Set<String> REPLY_KEYS = Set.of("runId", "pendingQuestionId", "questionVersion");
    /** 单次历史读取上限：超出由调用方用游标分批；Repository 内部再按 500 分批查库。 */
    private static final int EVENT_HISTORY_LIMIT = 5000;
    /** 周期 QUEUED 补派的默认有界扫描条数；启动全量对账不受此限制。 */
    static final int DEFAULT_RECOVERY_SCAN_LIMIT = 50;

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
    private final SkillRuntime skills;
    /** 本进程实例的 worker 标识：租约按它判定持有者，崩溃后由新进程在租约过期后回收。 */
    private final String workerId;
    /** 事件落库事务提交后的内存通知钩子（先提交后发布）；缺省无操作。 */
    private final java.util.function.Consumer<UUID> eventNotifier;
    private final java.time.Duration leaseDuration;
    private final java.util.concurrent.atomic.AtomicBoolean recoveryScheduled =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final RunRecoveryService recovery;
    private final java.util.concurrent.ScheduledExecutorService heartbeats;
    /** 进程内待执行集合：周期扫描与提交路径并发时，不会向 executor 重复堆同一个 run。 */
    private final Set<UUID> pendingDispatch;

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
        this(conversations, runs, transactions, identity, gateway, reasoners, maxConcurrentRuns, maxQueue,
                workerEnabled, files, 60, 3);
    }

    public GeneralAgentService(ConversationRepository conversations, AgentRunRepository runs,
                               TransactionTemplate transactions, ServerIdentity identity,
                               CapabilityGateway gateway, Supplier<GeneralReasoner> reasoners,
                               int maxConcurrentRuns, int maxQueue, boolean workerEnabled,
                               io.devicetruth.knowledge.FileUploadService files, long leaseSeconds,
                               int maxRecoveryAttempts) {
        this(conversations, runs, transactions, identity, gateway, reasoners, maxConcurrentRuns, maxQueue,
                workerEnabled, files, leaseSeconds, maxRecoveryAttempts, null);
    }

    public GeneralAgentService(ConversationRepository conversations, AgentRunRepository runs,
                               TransactionTemplate transactions, ServerIdentity identity,
                               CapabilityGateway gateway, Supplier<GeneralReasoner> reasoners,
                               int maxConcurrentRuns, int maxQueue, boolean workerEnabled,
                               io.devicetruth.knowledge.FileUploadService files, long leaseSeconds,
                               int maxRecoveryAttempts, SkillRuntime skills) {
        this(conversations, runs, transactions, identity, gateway, reasoners, maxConcurrentRuns, maxQueue,
                workerEnabled, files, leaseSeconds, maxRecoveryAttempts, skills, runId -> {});
    }

    /**
     * eventNotifier 在事件落库事务提交后被调用（先提交后发布），由 RunEventStreamService
     * 用内存通知唤醒实时订阅；缺省为无操作，测试与 legacy 装配不受影响。
     */
    public GeneralAgentService(ConversationRepository conversations, AgentRunRepository runs,
                               TransactionTemplate transactions, ServerIdentity identity,
                               CapabilityGateway gateway, Supplier<GeneralReasoner> reasoners,
                               int maxConcurrentRuns, int maxQueue, boolean workerEnabled,
                               io.devicetruth.knowledge.FileUploadService files, long leaseSeconds,
                               int maxRecoveryAttempts, SkillRuntime skills,
                               java.util.function.Consumer<UUID> eventNotifier) {
        this(conversations, runs, transactions, identity, gateway, reasoners, maxConcurrentRuns, maxQueue,
                workerEnabled, files, leaseSeconds, maxRecoveryAttempts, skills, eventNotifier,
                DEFAULT_RECOVERY_SCAN_LIMIT);
    }

    /**
     * recoveryScanLimit：周期 QUEUED 补派的有界扫描条数（P1-04），防止周期任务无限全扫；
     * 启动对账 reconcile 仍走全量 queued()，不受此限制。
     */
    public GeneralAgentService(ConversationRepository conversations, AgentRunRepository runs,
                               TransactionTemplate transactions, ServerIdentity identity,
                               CapabilityGateway gateway, Supplier<GeneralReasoner> reasoners,
                               int maxConcurrentRuns, int maxQueue, boolean workerEnabled,
                               io.devicetruth.knowledge.FileUploadService files, long leaseSeconds,
                               int maxRecoveryAttempts, SkillRuntime skills,
                               java.util.function.Consumer<UUID> eventNotifier, int recoveryScanLimit) {
        if (maxConcurrentRuns < 1 || maxQueue < 0) throw new IllegalArgumentException("Worker limits must be positive");
        if (leaseSeconds < 1) throw new IllegalArgumentException("leaseSeconds must be positive");
        this.accessScope=null;
        this.pendingDispatch=java.util.concurrent.ConcurrentHashMap.newKeySet();
        this.eventNotifier = eventNotifier;
        this.conversations = conversations;
        this.runs = runs;
        this.transactions = transactions;
        this.identity = identity;
        this.gateway = gateway;
        this.reasoners = reasoners;
        this.orchestrator = new TaskOrchestrator(gateway);
        this.workerEnabled = workerEnabled;
        this.files = files;
        this.skills = skills;
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.leaseDuration = java.time.Duration.ofSeconds(leaseSeconds);
        this.recovery = new RunRecoveryService(runs, maxRecoveryAttempts, recoveryScanLimit);
        AtomicInteger seq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(maxConcurrentRuns, maxConcurrentRuns, 30, TimeUnit.SECONDS,
                // 容量 0 表示“占满即拒绝”，用 SynchronousQueue 表达，而不是把配置悄悄改成 1。
                maxQueue == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(maxQueue), runnable -> {
                    Thread thread = new Thread(runnable, "devicetruth-agent-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        AtomicInteger heartbeatSeq = new AtomicInteger();
        this.heartbeats = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "devicetruth-lease-" + heartbeatSeq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private final AccessScope accessScope;
    private boolean authenticatedRecoveryOnly;
    private GeneralAgentService(GeneralAgentService root, ServerIdentity identity, AccessScope scope) {
        this.pendingDispatch=root.pendingDispatch;
        this.conversations=root.conversations; this.runs=root.runs; this.transactions=root.transactions;
        this.identity=identity; this.accessScope=scope; this.gateway=root.gateway; this.reasoners=root.reasoners;
        this.orchestrator=root.orchestrator; this.workerEnabled=root.workerEnabled; this.executor=root.executor;
        this.files=root.files; this.skills=root.skills==null?null:root.skills.forIdentity(identity);
        this.workerId=root.workerId; this.eventNotifier=root.eventNotifier; this.leaseDuration=root.leaseDuration;
        this.recovery=root.recovery; this.heartbeats=root.heartbeats;
    }
    public GeneralAgentService forScope(AccessScope scope) {
        return new GeneralAgentService(this, scope.storageIdentity(), scope);
    }
    private GeneralAgentService executionService(UUID runId) {
        AccessScope persisted=runs.persistedScope(runId);
        return persisted==null?this:forScope(persisted);
    }
    private void requireWrite() {
        if(accessScope!=null && !runs.canWrite(accessScope)) throw new RequestFailure("AUTHORIZATION_REVOKED",403);
    }
    public ConversationView create() {
        requireWrite();
        return transactions.execute(status -> {
            var created=conversations.create(identity, Instant.now());
            if(accessScope!=null) runs.bindConversation(created.conversationId(),accessScope);
            return created;
        });
    }
    public ConversationRepository.Discussion addDiscussion(UUID conversationId, String content) {
        requireWrite();
        if(accessScope==null) throw new RequestFailure("AUTHENTICATION_REQUIRED",401);
        if(content==null || content.isBlank() || content.codePointCount(0,content.length())>8000 || content.indexOf('\0')>=0)
            throw new IllegalArgumentException("Invalid discussion");
        return transactions.execute(status -> {
            conversations.require(identity,conversationId,true);
            if(!runs.lockWriteAuthorization(accessScope)) throw new RequestFailure("AUTHORIZATION_REVOKED",403);
            return conversations.addDiscussion(identity,conversationId,accessScope.actorUserId(),content);
        });
    }
    public ConversationRepository.DiscussionPage discussions(UUID conversationId, long after, int limit) {
        if(after<0 || limit<1 || limit>100) throw new IllegalArgumentException("Invalid pagination");
        return conversations.discussions(identity,conversationId,after,limit);
    }

    public List<ConversationView> list() { return conversations.list(identity); }

    public ConversationSummaryPage summaries(String query, String assetId, String status, String cursor, Integer limit) {
        if (query != null && (query.codePointCount(0, query.length()) > 200 || query.indexOf('\0') >= 0))
            throw new RequestFailure("INVALID_QUERY", 400);
        if (assetId != null) Checks.id(assetId, "assetId");
        if (status != null && !Set.of("ACTIVE", "QUEUED", "RUNNING", "WAITING_FOR_USER", "COMPLETED",
                "FAILED", "CANCELLED").contains(status)) throw new RequestFailure("INVALID_STATUS", 400);
        int pageSize = limit == null ? 20 : limit;
        if (pageSize < 1 || pageSize > 50) throw new RequestFailure("INVALID_PAGE_SIZE", 400);
        if(accessScope!=null && cursor!=null) {
            try {
                var token=io.devicetruth.adapter.Json.MAPPER.readTree(java.util.Base64.getUrlDecoder().decode(cursor));
                if(!token.path("workspace").asText().equals(accessScope.workspaceId().toString())
                    || !token.path("query").asText().equals(java.util.Objects.toString(query,""))
                    || !token.path("asset").asText().equals(java.util.Objects.toString(assetId,""))
                    || !token.path("status").asText().equals(java.util.Objects.toString(status,""))) throw new IllegalArgumentException();
                cursor=token.path("cursor").asText();
            } catch(Exception e) {throw new RequestFailure("INVALID_CURSOR",400);}
        }
        var decoded = decodeCursor(cursor);
        var page = conversations.summaries(identity, query, assetId, status, decoded, pageSize);
        String next=page.nextCursor()==null?null:encodeCursor(page.nextCursor());
        if(next!=null && accessScope!=null) next=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            io.devicetruth.adapter.Json.write(Map.of("workspace",accessScope.workspaceId().toString(),"query",java.util.Objects.toString(query,""),"asset",java.util.Objects.toString(assetId,""),"status",java.util.Objects.toString(status,""),"cursor",next)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new ConversationSummaryPage(page.items(),next);
    }

    private static ConversationRepository.SummaryCursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded = new String(java.util.Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            int waiting = Integer.parseInt(parts[0]);
            if (parts.length != 3 || waiting < 0 || waiting > 1) throw new IllegalArgumentException();
            return new ConversationRepository.SummaryCursor(waiting,
                    Instant.ofEpochMilli(Long.parseLong(parts[1])), UUID.fromString(parts[2]));
        } catch (RuntimeException e) {
            throw new RequestFailure("INVALID_CURSOR", 400);
        }
    }

    private static String encodeCursor(ConversationRepository.SummaryCursor cursor) {
        String value = cursor.waitingPriority() + ":" + cursor.updatedAt().toEpochMilli() + ":" + cursor.conversationId();
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public ConversationDetail read(UUID conversationId) {
        return transactions.execute(status -> {
            // 与提交共用行锁，避免把旧版本与刚提交的消息拼成一次读取结果。
            var conversation = conversations.require(identity, conversationId, true);
            return new ConversationDetail(conversation, conversations.messages(identity, conversationId), runs.list(identity, conversationId));
        });
    }

    public RunView run(UUID runId) { return runs.require(identity, runId); }

    public SkillRuntime.SelectionView runSkill(UUID runId) {
        runs.require(identity, runId);
        var selected = skills == null ? null : skills.selection(runId);
        if (selected == null) throw new RequestFailure("SKILL_NOT_SELECTED", 404);
        return selected;
    }

    /**
     * 取消（03-03）：QUEUED/WAITING_FOR_USER 立即迁移终态 CANCELLED；
     * RUNNING 只打取消标记，由持有租约的 worker 在每步之间协作式收尾——
     * 已产生的模型响应只记 RUN_LATE_RESPONSE 事件，绝不覆盖终态。
     * 终态 run 再取消返回 409 RUN_ALREADY_TERMINAL；不存在/跨身份统一 404。
     */
    public RunView cancel(UUID runId) {
        requireWrite();
        RunView view = transactions.execute(status -> {
            runs.load(identity, runId, true);
            if(accessScope!=null) runs.requireCancelActor(runId,accessScope);
            if (runs.requestCancel(identity, runId, Instant.now()) != 1)
                throw new RequestFailure("RUN_ALREADY_TERMINAL", 409);
            if(accessScope!=null) runs.recordCancellation(runId,accessScope);
            return runs.require(identity, runId);
        });
        // 事务已提交：RUN_CANCELLED 等事件落库后才允许发布内存通知（先提交后发布）。
        notifyEvents(runId);
        return view;
    }

    /**
     * 启动时由 RunRecoveryService 对账上一个进程留下的任务：QUEUED 直接重新调度；
     * 租约已过期的遗留 RUNNING 按持久检查点恢复（RUNNING → QUEUED → 重新调度），
     * 超出恢复上限的明确 FAILED；WAITING_FOR_USER 与租约仍有效的 RUNNING 原样保留。
     */
    public void recoverAuthenticatedQueued() {
        authenticatedRecoveryOnly = true;
        recoverQueued();
    }

    public void recoverQueued() {
        for(var storage: runs.recoveryIdentities(authenticatedRecoveryOnly ? null : identity)) {
            var scoped=new GeneralAgentService(this,storage,null);
            recovery.reconcile(storage,workerEnabled,scoped::dispatch);
        }
        // 周期任务（P1-04 修复）：过期 RUNNING 回收 + 有界滞留 QUEUED 补派。
        // 必须整体捕获异常——scheduleWithFixedDelay 遇到未捕获异常会永久取消后续执行。
        if (workerEnabled && recoveryScheduled.compareAndSet(false, true))
            heartbeats.scheduleWithFixedDelay(() -> {
                try {
                    for(var storage: runs.recoveryIdentities(authenticatedRecoveryOnly ? null : identity)) {
                        var scoped=new GeneralAgentService(this,storage,null);
                        recovery.reconcileExpired(storage,scoped::dispatch);
                        recovery.reconcileQueued(storage,scoped::dispatch);
                    }
                } catch (RuntimeException e) {
                    LOG.error("Agent periodic recovery failed: {}", e.getClass().getSimpleName());
                }
            }, leaseDuration.toMillis(), leaseDuration.toMillis(), TimeUnit.MILLISECONDS);
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

    /** 模型尝试审计：版本、重试次数、结果码与单次耗时；供 OPS-03 故障观测。 */
    public List<AgentRunRepository.ModelEvent> modelEvents(UUID runId) {
        runs.require(identity, runId);
        return runs.modelEvents(identity, runId);
    }

    /** 公开事件历史（05-01）：白名单映射 + 有界分批；先 runs.require 授权，不存在/跨身份 404。 */
    public List<SafeAgentEventViews.PublicEvent> events(UUID runId, long afterSeq) {
        if (afterSeq < 0) throw new IllegalArgumentException("afterSeq must be >= 0");
        runs.require(identity, runId);
        return runs.eventsAfter(identity, runId, afterSeq, EVENT_HISTORY_LIMIT);
    }

    /**
     * 提交一条消息：普通消息开启新任务；带 replyTo 三元组的消息是对当前 run 待答问题的补答，
     * 关联不成立时返回 REPLY_TARGET_REQUIRED/STALE_OR_UNRELATED_REPLY，绝不静默新建 run。
     */
    public RunView submit(UUID conversationId, UUID requestKey, AgentTaskInput input) {
        requireWrite();
        if (requestKey == null || input == null || input.userMessage() == null || input.userMessage().isBlank()
                || input.userMessage().codePointCount(0, input.userMessage().length()) > 8000
                || input.userMessage().indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid message");
        if (!Integer.valueOf(1).equals(input.schemaVersion())) throw new RequestFailure("SCHEMA_VERSION_UNSUPPORTED", 400);
        if (input.selectedAssetId() != null) Checks.id(input.selectedAssetId(), "selectedAssetId");
        List<Map<String, Object>> attachments = input.attachments();
        List<Map<String, Object>> contextRefs = validatedContextRefs(input.contextRefs());
        if (input.replyTo() != null) return reply(conversationId, requestKey, input, attachments, contextRefs);

        Instant now = Instant.now();
        Instant asOf = input.asOf() == null ? now : input.asOf();
        RunView run = transactions.execute(status -> {
            var currentConversation = conversations.require(identity, conversationId, true);
            if (conversations.hasRequest(identity, conversationId, requestKey))
                throw new RequestFailure("DUPLICATE_REQUEST", 409);
            if(accessScope!=null) {
                if(!runs.lockWriteAuthorization(accessScope)) throw new RequestFailure("AUTHORIZATION_REVOKED",403);
                conversations.checkSubmission(identity,conversationId,currentConversation.version(),input.expectedConversationVersion());
            }
            var quotes=conversations.resolveQuotes(identity,conversationId,input.discussionQuotes());
            UUID messageId = conversations.appendWithReferences(identity, conversationId, requestKey,
                    input.userMessage(), input.selectedAssetId(), now, asOf, validatedAttachments(conversationId,attachments), contextRefs);
            conversations.saveQuotes(messageId,quotes);
            RunView created = runs.enqueue(identity, conversationId, messageId, now, asOf);
            if(accessScope!=null) runs.bindRun(created.runId(), messageId, accessScope);
            importContextRefs(contextRefs, created.runId());
            return runs.require(identity,created.runId());
        });
        notifyEvents(run.runId());
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
            var quotes=conversations.resolveQuotes(identity,conversationId,input.discussionQuotes());
            UUID replyMessage=conversations.appendReplyWithReferences(identity, conversationId, requestKey, input.userMessage(),
                    input.selectedAssetId(), now, asOf, record.runId(), record.pendingQuestionId(),
                    record.questionVersion(), validatedAttachments(conversationId,attachments), contextRefs);
            conversations.saveQuotes(replyMessage,quotes);
            if(accessScope!=null && !runs.lockWriteAuthorization(accessScope)) throw new RequestFailure("AUTHORIZATION_REVOKED",403);
            if (input.selectedAssetId() != null)
                runs.confirmSelectedSubject(identity, record.runId(), input.selectedAssetId(),
                        input.selectedAssetId().equals(record.selectedAssetId())
                                ? record.changedFromAssetId() : record.selectedAssetId());
            if (runs.queueReply(identity, record, target.pendingQuestionId(), target.questionVersion(), now) != 1)
                throw new RequestFailure("STALE_OR_UNRELATED_REPLY", 409);
            if(accessScope!=null) runs.bindReply(record.runId(), requestKey, accessScope);
            importContextRefs(contextRefs, record.runId());
            return runs.require(identity, record.runId());
        });
        notifyEvents(run.runId());
        dispatch(run.runId());
        return run;
    }

    private List<Map<String, Object>> validatedAttachments(UUID conversationId,List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        if (files == null) throw new RequestFailure("REFERENCES_NOT_AVAILABLE", 400);
        var resolved=files.readyAttachments(identity.ownerId(), identity.projectId(), attachments);
        files.requireAttachmentConversation(identity.ownerId(),identity.projectId(),conversationId,attachments);
        return resolved;
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
     * 进程内待执行去重：周期补派与提交并发时同一 run 只会进入 executor 一次，
     * 任务开始/被拒绝时释放，领取失败的 run 留在 QUEUED 由下一周期重试。
     */
    private void dispatch(UUID runId) {
        if (!workerEnabled) return;
        if (!pendingDispatch.add(runId)) return;
        try {
            executor.execute(() -> {
                pendingDispatch.remove(runId);
                executionService(runId).executeRun(runId);
            });
        } catch (RejectedExecutionException e) {
            pendingDispatch.remove(runId);
            LOG.error("Agent queue rejected: runId={}", runId);
            failQuietly(runId, "QUEUE_REJECTED");
        }
    }

    /** worker 主体：QUEUED→RUNNING（条件更新+租约领取防重复接手）→ 重建上下文 → 唯一主循环 → 原子写回。 */
    private void executeRun(UUID runId) {
        RunRecord run;
        try {
            run = runs.load(identity, runId, false);
            if (!"QUEUED".equals(run.status())) return;
            if(accessScope!=null && !runs.canWrite(accessScope)) { failQuietly(runId,"AUTHORIZATION_REVOKED"); return; }
            // 领取前累计执行时长已耗尽时直接 FAILED；执行中的有限结果收口由主循环与 persist 处理
            if (run.activeDurationMs() >= run.maxActiveDurationMs()) {
                failQuietly(runId, "TOTAL_TIMEOUT");
                return;
            }
            // 领取执行租约：租约期内其他进程不会回收这个 RUNNING；崩溃后由新进程在过期后对账。
            if (runs.markRunning(identity, run, workerId, Instant.now().plus(leaseDuration), Instant.now()) != 1)
                return;
        } catch (RuntimeException e) {
            LOG.error("Agent run claim failed: runId={} type={}", runId, e.getClass().getSimpleName());
            return;
        }
        // 长任务运行期间周期性续租；worker 存活时租约不过期，崩溃后租约自然失效供启动对账回收。
        java.util.concurrent.ScheduledFuture<?> heartbeat = heartbeats.scheduleWithFixedDelay(() -> {
            try {
                runs.heartbeat(identity, runId, workerId, run.version() + 1, Instant.now().plus(leaseDuration), Instant.now());
            } catch (RuntimeException e) {
                LOG.error("Agent lease heartbeat failed: runId={} type={}", runId, e.getClass().getSimpleName());
            }
        }, leaseDuration.toMillis() / 3, leaseDuration.toMillis() / 3, TimeUnit.MILLISECONDS);
        long segmentStartNanos = System.nanoTime();
        try {
            AgentRunOutcome outcome = doRun(run, segmentStartNanos);
            if (outcome == null) return; // doRun 内已按稳定错误码置 FAILED，无需再提交结果
            try {
                persist(run, outcome, segmentStartNanos);
            } catch (SkillException e) {
                failClaimedQuietly(run, e.code());
            } catch (RuntimeException e) {
                // 落库失败（唯一约束、连接中断等）必须变成可观察的 FAILED，不能把 run 留在 RUNNING
                LOG.error("Agent run persistence failed: runId={} type={}", runId, e.getClass().getSimpleName());
                failClaimedQuietly(run, "PERSIST_FAILURE");
            }
        } finally {
            heartbeat.cancel(false);
        }
    }

    /**
     * 每步之间的协作式中止检查：取消标记优先，其次总时限（累计执行时长，不含等待）。
     * 返回 null 继续执行，否则为稳定收尾码（CANCELLED / TOTAL_TIMEOUT）。
     */
    private java.util.function.Supplier<String> abortCheck(RunRecord run, long segmentStartNanos) {
        return () -> {
            String stop = runs.claimStopCode(identity, run.runId(), run.version() + 1);
            if (stop != null) return stop;
            long totalActiveMs = run.activeDurationMs() + (System.nanoTime() - segmentStartNanos) / 1_000_000;
            return totalActiveMs >= run.maxActiveDurationMs() ? "TOTAL_TIMEOUT" : null;
        };
    }

    /** 从 PG 重建上下文并执行唯一主循环；上下文全部来自持久层，新进程也能从同一检查点继续。 */
    private AgentRunOutcome doRun(RunRecord run, long segmentStartNanos) {
        UUID runId = run.runId();
        try {
            var messages = conversations.executionMessages(identity, runId, run.goalMessageId()).stream()
                    .map(message -> new io.devicetruth.core.contract.AgentTaskInput.Message(message.role(),
                            message.content(), message.attachments(),message.discussionQuotes()))
                    .toList();
            var history = conversations.recentConversationHistory(identity, run.conversationId(), run.goalMessageId())
                    .stream().map(message -> new io.devicetruth.core.contract.AgentTaskInput.Message(
                            message.role(), message.content())).toList();
            var input = new io.devicetruth.core.contract.AgentTaskInput(identity.ownerId(), identity.projectId(),
                    run.conversationId(), runId, run.goalMessageId(), run.goalText(), messages,
                    run.selectedAssetId(), run.requestedAt(), run.asOf(), run.version(), run.stepsUsed(),
                    run.maxSteps());
            var toolContext = new ToolContext(identity.ownerId(), identity.projectId(), run.conversationId(), runId,
                    run.selectedAssetId(), run.requestedAt(), run.asOf(), gateway.toolNames(),
                    accessScope==null?null:accessScope.actorUserId(),accessScope==null?null:accessScope.workspaceId());
            GeneralReasoner reasoner;
            try {
                reasoner = reasoners.get();
            } catch (RuntimeException e) {
                // 服务端模型配置错误：FAILED + 稳定错误码，不降级到 scripted
                LOG.error("Agent reasoner creation failed: runId={} type={}", runId, e.getClass().getSimpleName());
                failClaimedQuietly(run, "MODEL_CONFIGURATION_MISSING");
                return null;
            }
            var references = runs.references(identity, runId);
            var checkpoint = runs.checkpoint(identity, runId);
            var session = skills == null ? null : skills.open(gateway, toolContext, run.version() + 1);
            return orchestrator.runAgent(input, session == null ? gateway : session.gateway(),
                    toolContext, references, reasoner, abortCheck(run, segmentStartNanos),
                    session == null ? () -> new TaskOrchestrator.SkillStep(toolContext, List.of(), null)
                            : session::step,
                    progressWriter(run), history, checkpoint,
                    step -> writeCheckpoint(run, () -> requireUpdated(
                            runs.reserveStep(identity, runId, step, totalActiveMs(run, segmentStartNanos)))),
                    action -> persistToolStep(run, action, segmentStartNanos),
                    saved -> writeCheckpoint(run, () -> requireUpdated(
                            runs.saveCheckpoint(identity, saved, totalActiveMs(run, segmentStartNanos), false))),
                    () -> Math.max(0, run.maxActiveDurationMs() - totalActiveMs(run, segmentStartNanos)),
                    Math.min(30_000L, run.maxActiveDurationMs() / 4));
        } catch (CheckpointFailure e) {
            LOG.error("Agent execution checkpoint failed: runId={} type={}", runId,
                    e.getCause().getClass().getSimpleName());
            failClaimedQuietly(run, accessScope!=null && !runs.canWrite(accessScope)?"AUTHORIZATION_REVOKED":"CHECKPOINT_PERSIST_FAILURE");
            return null;
        } catch (SkillException e) {
            failClaimedQuietly(run, e.code());
            return null;
        } catch (RuntimeException e) {
            LOG.error("Agent run failed: runId={} type={}", runId, e.getClass().getSimpleName());
            failClaimedQuietly(run, "AGENT_RUN_FAILURE");
            return null;
        }
    }

    /**
     * 一次运行的结果在一个事务里落库：助手消息、run 状态、预算步数与白名单事件。
     * 加锁顺序固定为“会话行 → run 行”，与提交/补答入口一致，避免与并发补答互相等待。
     * 在锁内重查取消和租约。仍持租约的迟到模型响应记录 RUN_LATE_RESPONSE，失去租约时放弃写入。
     */
    private void persist(RunRecord run, AgentRunOutcome outcome, long segmentStartNanos) {
        transactions.execute(status -> {
            conversations.require(identity, run.conversationId(), true);
            if (!lockClaim(run)) return null;
            RunRecord current = runs.load(identity, run.runId(), true);
            boolean hasAssistantResponse = outcome.status() == TaskOrchestrator.AgentStatus.WAITING_FOR_USER
                    || outcome.status() == TaskOrchestrator.AgentStatus.COMPLETED;
            if (!current.cancelRequested() && hasAssistantResponse && skills != null
                    && totalActiveMs(run, segmentStartNanos) < current.maxActiveDurationMs())
                skills.verifyActiveGrant(run.runId());
            if (!lockClaim(run)) return null;

            long totalMs = totalActiveMs(run, segmentStartNanos);
            long segmentMs = Math.max(0, totalMs - current.activeDurationMs());
            boolean timedOut = totalMs >= current.maxActiveDurationMs();
            boolean hasLateResponse = outcome.events().stream().anyMatch(event -> "RUN_LATE_RESPONSE".equals(event.type()));
            Instant now = Instant.now();
            if (current.cancelRequested() || hasLateResponse || (timedOut && hasAssistantResponse)) {
                String code = current.cancelRequested() ? "CANCELLED" : "TOTAL_TIMEOUT";
                runs.appendEvent(identity, run.runId(), run.conversationId(), "RUN_LATE_RESPONSE",
                        Map.of("status", current.cancelRequested() ? "CANCELLED" : "FAILED", "code", code), now);
            }
            if (accessScope!=null && !runs.lockWriteAuthorization(accessScope)) {
                runs.fail(identity,current,"AUTHORIZATION_REVOKED",outcome.stepsUsed(),segmentMs,0,0,now);
            } else if (current.cancelRequested()) {
                runs.cancelRunning(identity, current, outcome.stepsUsed(), segmentMs, 0, 0, now);
            } else if (timedOut && !outcome.limited()) {
                runs.fail(identity, current, "TOTAL_TIMEOUT", outcome.stepsUsed(), segmentMs, 0, 0, now);
            } else if (outcome.status() == TaskOrchestrator.AgentStatus.WAITING_FOR_USER) {
                var question = outcome.question().withVersion(run.questionVersion() + 1);
                conversations.appendAssistant(identity, run.conversationId(), run.runId(), question.question(),
                        now, run.asOf());
                requireUpdated(runs.waitForUser(identity, current, question, outcome.stepsUsed(),
                        outcome.references().size(), segmentMs, 0, 0, now));
                runs.appendEvent(identity, run.runId(), run.conversationId(), "RUN_WAITING",
                        Map.of("questionVersion", question.questionVersion()), now);
            } else if (outcome.status() == TaskOrchestrator.AgentStatus.COMPLETED) {
                conversations.appendAssistant(identity, run.conversationId(), run.runId(),
                        outcome.answer().summary(), now, run.asOf());
                requireUpdated(runs.complete(identity, current, outcome.answer(), outcome.stepsUsed(), segmentMs,
                        0, 0, now));
            } else {
                runs.fail(identity, current, outcome.stopReason(), outcome.stepsUsed(), segmentMs, 0, 0, now);
            }
            return null;
        });
        notifyEvents(run.runId());
    }

    private boolean lockClaim(RunRecord run) {
        return runs.lockClaim(identity, run.runId(), run.version() + 1, run.attempts() + 1, workerId);
    }

    private static long totalActiveMs(RunRecord run, long segmentStartNanos) {
        return run.activeDurationMs() + (System.nanoTime() - segmentStartNanos) / 1_000_000;
    }

    private static void requireUpdated(int updated) {
        if (updated != 1) throw new IllegalStateException("Run update rejected");
    }

    private void writeCheckpoint(RunRecord run, Runnable write) {
        try {
            transactions.executeWithoutResult(status -> {
                if (!lockClaim(run)) throw new IllegalStateException("Run claim lost");
                write.run();
            });
        } catch (RuntimeException e) {
            throw new CheckpointFailure(e);
        }
    }

    /** 工具登记的 PG 引用、Skill 及检查点共享事务。 */
    private TaskOrchestrator.ToolStep persistToolStep(RunRecord run,
            java.util.function.Supplier<TaskOrchestrator.ToolStep> action, long segmentStartNanos) {
        TaskOrchestrator.ToolStep completed;
        try {
            completed = transactions.execute(status -> {
                if(accessScope!=null && !runs.lockWriteAuthorization(accessScope)) throw new IllegalStateException("AUTHORIZATION_REVOKED");
                String stop = runs.claimStopCode(identity, run.runId(), run.version() + 1);
                if (stop != null) throw new IllegalStateException(stop);
                Object savepoint = status.createSavepoint();
                var step = action.get();
                if(accessScope!=null && !runs.canWrite(accessScope)) throw new IllegalStateException("AUTHORIZATION_REVOKED");
                if (!Boolean.TRUE.equals(step.audit().get("success"))) status.rollbackToSavepoint(savepoint);
                status.releaseSavepoint(savepoint);
                if (!lockClaim(run)) throw new IllegalStateException("Run claim expired during tool");
                AgentExecutionCheckpoint checkpoint = step.checkpoint();
                requireUpdated(runs.saveCheckpoint(identity, checkpoint, totalActiveMs(run, segmentStartNanos), true));
                return step;
            });
        } catch (RuntimeException e) {
            throw new CheckpointFailure(e);
        }
        // 审计失败不会撤回已提交的检查点，也不会触发工具重放。
        appendProgress(run, "RUN_TOOL", completed.audit());
        return completed;
    }

    private static final class CheckpointFailure extends RuntimeException {
        private CheckpointFailure(RuntimeException cause) { super(cause); }
    }

    private java.util.function.Consumer<TaskOrchestrator.RunEvent> progressWriter(RunRecord run) {
        return event -> {
            if ("MODEL_ATTEMPT".equals(event.type()))
                appendProgress(run, "RUN_MODEL", modelEventPayload(event.data()));
            if ("RUN_PHASE".equals(event.type()) || "RUN_DEGRADED".equals(event.type()))
                appendProgress(run, event.type(), event.data());
        };
    }

    private void appendProgress(RunRecord run, String type, Map<String, Object> payload) {
        try {
            transactions.executeWithoutResult(status -> {
                if (!lockClaim(run)) return;
                runs.appendEvent(identity, run.runId(), run.conversationId(), type, payload, Instant.now(),
                        run.attempts() + 1);
                if ("RUN_MODEL".equals(type)) runs.updateModelUsage(identity, run.runId());
            });
            notifyEvents(run.runId());
        } catch (RuntimeException e) {
            LOG.error("Agent progress event persistence failed: runId={} type={}",
                    run.runId(), e.getClass().getSimpleName());
        }
    }

    /** RUN_MODEL 白名单载荷：模型版本、第几次尝试、结果码与单次耗时；原始输出与 Prompt 不落库。 */
    private static Map<String, Object> modelEventPayload(Map<String, Object> data) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("model", String.valueOf(data.getOrDefault("model", "unknown")));
        Object attempt = data.get("attempt");
        if (attempt instanceof Reasoner.Attempt record) {
            payload.put("attempt", record.number());
            payload.put("outcome", record.outcome());
            payload.put("durationMs", record.durationMs());
            payload.put("retryable", record.retryable());
            if (record.usage() != null) {
                if (record.usage().inputTokens() != null) payload.put("inputTokens", record.usage().inputTokens());
                if (record.usage().outputTokens() != null) payload.put("outputTokens", record.usage().outputTokens());
                if (record.usage().cost() != null) payload.put("cost", record.usage().cost());
            }
        }
        return payload;
    }

    private void failQuietly(UUID runId, String code) {
        failQuietly(runId, code, null);
    }

    private void failClaimedQuietly(RunRecord claimed, String code) {
        failQuietly(claimed.runId(), code, claimed);
    }

    private void failQuietly(UUID runId, String code, RunRecord claimed) {
        try {
            transactions.execute(status -> {
                if (claimed != null && !lockClaim(claimed)) return null;
                RunRecord record = runs.load(identity, runId, true);
                if (claimed != null || "QUEUED".equals(record.status())) {
                    if (record.cancelRequested())
                        runs.cancelRunning(identity, record, record.stepsUsed(), 0, 0, 0, Instant.now());
                    else
                        runs.fail(identity, record, code, record.stepsUsed(), 0, 0, 0, Instant.now());
                }
                return null;
            });
            notifyEvents(runId);
        } catch (RuntimeException e) {
            LOG.error("Agent run failure marking failed: runId={} type={}", runId, e.getClass().getSimpleName());
        }
    }

    /** 内存通知只做唤醒，异常不得影响主流程；漏唤醒由 SSE 侧低频兜底补读覆盖。 */
    private void notifyEvents(UUID runId) {
        try {
            eventNotifier.accept(runId);
        } catch (RuntimeException e) {
            LOG.error("Agent event notification failed: runId={} type={}", runId, e.getClass().getSimpleName());
        }
    }

    /** 关闭执行器与心跳（仅测试/停机用），不阻塞等待结果。 */
    public void shutdown() {
        executor.shutdownNow();
        heartbeats.shutdownNow();
    }

    public static RequestFailure denied() { return new RequestFailure("NOT_FOUND_OR_DENIED", 404); }
}
