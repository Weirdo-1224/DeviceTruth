package io.devicetruth.application.agent;

import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.application.agent.GeneralAgentService.RequestFailure;
import io.devicetruth.application.agent.GeneralAgentService.RunView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 05-01 契约 §3/§4 + 复验 §8.2/§8.3：run 事件 SSE 实时流（SseEmitter 异步版）。
 * 校验（400/404/409/全局 503）全部在提交响应前完成；通过后返回 SseEmitter，
 * relay 跑在专用有界线程池上，不占用 Tomcat 请求线程。单实例内存通知 + 低频兜底补读，
 * 不引 Redis/MQ。建立顺序严格为「先读历史 → 再订阅 → 再补读订阅点之后的所有 seq」，
 * 此后每次唤醒都从 lastSent 游标重新读库追加，因此天然至少一次、按 seq 升序、不丢不重；
 * 订阅期间只做短查询，不持数据库事务/行锁。
 * 订阅登记与容量判定共享同一把锁：单一 Map&lt;runId, Set&lt;Subscription&gt;&gt;，
 * add/remove/size 与全局并发计数都在锁内完成（§8.3）。
 */
public final class RunEventStreamService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RunEventStreamService.class);
    private static final long HEARTBEAT_MILLIS = 15_000;
    private static final Object SIGNAL = new Object();
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "FAILED", "CANCELLED");
    /** 每轮追加上限：历史端点本身有界（5000），超过由 Last-Event-ID 游标续传。 */
    private static final int CATCH_UP_LIMIT = 5000;
    private static final int DEFAULT_MAX_CONNECTIONS = 64;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(60);

    private final AgentRunRepository runs;
    private final ServerIdentity identity;
    private final int maxSubscriptionsPerRun;
    private final int maxConnections;
    private final Duration ttl;
    private final ExecutorService relayExecutor;
    private final boolean ownsExecutor;
    /** §8.3：订阅状态原子化——登记表与全局计数共用这一把锁。 */
    private final Object lock = new Object();
    private final Map<UUID, Set<Subscription>> subscribers = new HashMap<>();
    private int totalSubscriptions;

    public RunEventStreamService(AgentRunRepository runs, ServerIdentity identity, int maxSubscriptionsPerRun) {
        this(runs, identity, maxSubscriptionsPerRun, DEFAULT_MAX_CONNECTIONS, DEFAULT_TTL, null);
    }

    /**
     * @param maxSubscriptionsPerRun 单 run 活跃订阅上限（超出 409 STREAM_CAPACITY）
     * @param maxConnections 全局并发流上限（超出 503 STREAM_CAPACITY），relay 线程池容量同值
     * @param ttl 单连接期限，到期由 emitter timeout 回调统一清理
     * @param relayExecutor relay 专用有界线程池；null 时自建并随服务关闭
     */
    public RunEventStreamService(AgentRunRepository runs, ServerIdentity identity, int maxSubscriptionsPerRun,
                                 int maxConnections, Duration ttl, ExecutorService relayExecutor) {
        if (maxSubscriptionsPerRun < 1) throw new IllegalArgumentException("maxSubscriptionsPerRun must be positive");
        if (maxConnections < 1) throw new IllegalArgumentException("maxConnections must be positive");
        if (ttl == null || ttl.isNegative() || ttl.isZero())
            throw new IllegalArgumentException("ttl must be positive");
        this.runs = runs;
        this.identity = identity;
        this.maxSubscriptionsPerRun = maxSubscriptionsPerRun;
        this.maxConnections = maxConnections;
        this.ttl = ttl;
        if (relayExecutor != null) {
            this.relayExecutor = relayExecutor;
            this.ownsExecutor = false;
        } else {
            AtomicInteger seq = new AtomicInteger();
            this.relayExecutor = java.util.concurrent.Executors.newFixedThreadPool(maxConnections, runnable -> {
                Thread thread = new Thread(runnable, "devicetruth-stream-" + seq.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            this.ownsExecutor = true;
        }
    }

    /** 内存通知入口：GeneralAgentService 在事件事务提交后调用（先提交后发布），只负责唤醒。 */
    public void publish(UUID runId) {
        List<Subscription> targets;
        synchronized (lock) {
            Set<Subscription> set = subscribers.get(runId);
            if (set == null || set.isEmpty()) return;
            targets = List.copyOf(set);
        }
        for (Subscription subscription : targets) subscription.wake();
    }

    /**
     * SSE 连接建立（复验 §8.2）：非法游标 400、run 不存在/跨身份 404、单 run 超限 409、
     * 全局容量超限 503——全部在返回 SseEmitter（即提交响应）之前完成；
     * 通过后订阅登记并把 relay 交给专用线程池。终态/断开/IOException/超时都走同一清理路径。
     */
    public SseEmitter open(UUID runId, String lastEventId) {
        long cursor = parseCursor(lastEventId);
        runs.require(identity, runId);
        var emitter = new SseEmitter(ttl.toMillis());
        Subscription subscription = subscribe(runId, emitter);
        emitter.onCompletion(() -> cleanup(subscription));
        emitter.onTimeout(() -> cleanup(subscription));
        emitter.onError(throwable -> cleanup(subscription));
        try {
            relayExecutor.execute(() -> relay(runId, cursor, subscription));
        } catch (RuntimeException e) {
            // 线程池饱和（与全局容量判定的竞态窗口）：回收订阅并按 503 拒绝
            cleanup(subscription);
            throw new RequestFailure("STREAM_CAPACITY", 503);
        }
        return emitter;
    }

    /** 转播主循环：每轮先按游标补齐新事件，再判终态；未终态则等待通知或 15s 心跳（兼兜底补读）。 */
    private void relay(UUID runId, long cursor, Subscription subscription) {
        long lastSent = cursor;
        try {
            while (!subscription.closed.get()) {
                // 首次循环=订阅点补读（含订阅瞬间错过的交界事件），此后=实时追加/兜底补读。
                lastSent = sendNewEvents(subscription, runId, lastSent);
                RunView current = runs.require(identity, runId);
                if (TERMINAL_STATUSES.contains(current.status())) {
                    // 终态判定后再补一轮，收窄“判终态后到达的迟到事件”（如 CANCELLED 后的 RUN_LATE_RESPONSE）丢失窗口。
                    lastSent = sendNewEvents(subscription, runId, lastSent);
                    if (runs.latestSeq(identity, runId) <= lastSent) {
                        sendTerminal(subscription, current.status());
                        return;
                    }
                }
                Object signal;
                try {
                    signal = subscription.poll(HEARTBEAT_MILLIS);
                } catch (InterruptedException e) {
                    // 停机/池回收中断：放弃转播，走统一清理
                    Thread.currentThread().interrupt();
                    return;
                }
                if (subscription.closed.get()) return;
                if (signal == null)
                    subscription.emitter.send(SseEmitter.event().comment("heartbeat"));
            }
        } catch (IOException | IllegalStateException e) {
            // 典型为客户端断开/代理切断/emitter 已被超时回收：连接已提交，无法改状态码，只做清理。
            LOG.warn("Run event stream aborted: runId={} type={}", runId, e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            LOG.error("Run event stream failed: runId={} type={}", runId, e.getClass().getSimpleName());
        } finally {
            cleanup(subscription);
        }
    }

    /** 统一清理路径（§8.2）：订阅摘除 + 全局计数回减（锁内）+ emitter complete，幂等。 */
    private void cleanup(Subscription subscription) {
        if (!subscription.closed.compareAndSet(false, true)) return;
        unsubscribe(subscription);
        subscription.wake();
        try {
            subscription.emitter.complete();
        } catch (RuntimeException ignored) {
            // 容器已因超时/断开回收响应时 complete 可能拒绝，清理语义不受影响
        }
    }

    /** 从 lastSent 之后分批读库并逐条发送，返回新的游标（没有新事件时不变）。 */
    private long sendNewEvents(Subscription subscription, UUID runId, long lastSent) throws IOException {
        while (!subscription.closed.get()) {
            List<SafeAgentEventViews.PublicEvent> batch =
                    runs.eventsAfter(identity, runId, lastSent, CATCH_UP_LIMIT);
            if (batch.isEmpty()) return lastSent;
            for (SafeAgentEventViews.PublicEvent event : batch) {
                if (subscription.closed.get()) return lastSent;
                // 契约 §3 线格式：id=持久 seq，event=类型，data=公开事件 JSON，空行收尾。
                subscription.emitter.send(SseEmitter.event()
                        .id(String.valueOf(event.seq()))
                        .name(event.type())
                        .data(Json.write(event)));
                lastSent = event.seq();
            }
            if (batch.size() < CATCH_UP_LIMIT) return lastSent;
        }
        return lastSent;
    }

    /** 终态收尾：最后一条持久事件之后发 RUN_TERMINAL（无 id），随后服务端正常结束响应体。 */
    private static void sendTerminal(Subscription subscription, String status) throws IOException {
        subscription.emitter.send(SseEmitter.event()
                .name("RUN_TERMINAL")
                .data(Json.write(Map.of("status", status))));
    }

    /** 连接建立前校验：缺省游标 0=从头补历史；非数字或 <0 一律 400 INVALID_REQUEST。 */
    private static long parseCursor(String lastEventId) {
        if (lastEventId == null) return 0;
        long value;
        try {
            value = Long.parseLong(lastEventId.trim());
        } catch (RuntimeException e) {
            throw new RequestFailure("INVALID_REQUEST", 400);
        }
        if (value < 0) throw new RequestFailure("INVALID_REQUEST", 400);
        return value;
    }

    /** 订阅登记与容量判定在同一锁内：单 run 超限 409，全局超限 503。 */
    private Subscription subscribe(UUID runId, SseEmitter emitter) {
        var subscription = new Subscription(runId, emitter);
        synchronized (lock) {
            Set<Subscription> set = subscribers.get(runId);
            if (set != null && set.size() >= maxSubscriptionsPerRun)
                throw new RequestFailure("STREAM_CAPACITY", 409);
            if (totalSubscriptions >= maxConnections) throw new RequestFailure("STREAM_CAPACITY", 503);
            if (set == null) {
                set = new HashSet<>();
                subscribers.put(runId, set);
            }
            set.add(subscription);
            totalSubscriptions++;
        }
        return subscription;
    }

    /** 锁内摘除订阅并回减全局计数；与 subscribe 共用同一把锁，计数与列表永不分家。 */
    private void unsubscribe(Subscription subscription) {
        synchronized (lock) {
            Set<Subscription> set = subscribers.get(subscription.runId);
            if (set != null && set.remove(subscription)) {
                totalSubscriptions--;
                if (set.isEmpty()) subscribers.remove(subscription.runId, set);
            }
        }
    }

    /** 停机时释放自建 relay 线程池（外部注入的池由装配方自行管理）。 */
    public void shutdown() {
        if (ownsExecutor) relayExecutor.shutdownNow();
    }

    /** 单订阅：runId + emitter + 唤醒通道（容量 1，通知风暴折叠为一次信号；漏唤醒由心跳兜底补读覆盖）。 */
    private static final class Subscription {
        private final UUID runId;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ArrayBlockingQueue<Object> signals = new ArrayBlockingQueue<>(1);

        Subscription(UUID runId, SseEmitter emitter) {
            this.runId = runId;
            this.emitter = emitter;
        }

        void wake() { signals.offer(SIGNAL); }

        Object poll(long millis) throws InterruptedException { return signals.poll(millis, TimeUnit.MILLISECONDS); }
    }
}
