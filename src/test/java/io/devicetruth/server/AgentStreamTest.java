package io.devicetruth.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.GeneralAgentService.AgentTaskInput;
import io.devicetruth.application.agent.GeneralAgentService.RunView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.application.agent.RunEventStreamService;
import io.devicetruth.application.agent.SafeAgentEventViews;
import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.GeneralReasoner;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

/**
 * 05-01 契约 §6：/api/runs/{id}/events 历史回读与 /api/runs/{id}/stream SSE 实时流的集成验收。
 * 真实临时 PostgreSQL + 产品 Spring 装配 + scripted 推理器 + 确定性 embedding；
 * SSE 客户端用 JDK 内置 HttpClient 流式读取（不引新依赖）。
 * 覆盖：连接前历史全量回读、历史/实时交界不丢不重、Last-Event-ID 断线补齐按序不重复、
 * 游标语义与非法游标 400、跨身份 404、RUN_TERMINAL 终态收尾（COMPLETED/FAILED/CANCELLED）、
 * 公开事件字段白名单与敏感词、两个顺序连接模拟「页面读到断点断线、重连补齐各一次」的等价场景；
 * 复验轮（§8）追加：渐进持久事件在第二次模型调用完成前对历史端点与实时流双可见（§8.1）、
 * 全局流容量 503 隔离且普通 API 不受影响（§8.2）、多线程并发开关后容量完全回收（§8.3）、
 * data 显式空值经历史与 SSE 双路径省略（§8.4）、恢复重排删除被回收尝试的步骤事件与 attempt 落列（§8.1）。
 */
class AgentStreamTest {
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static ConfigurableApplicationContext app;
    private static ConfigurableApplicationContext otherOwnerApp;
    private static ConfigurableApplicationContext misconfiguredApp;
    private static ConfigurableApplicationContext limitedApp;
    private static TestRestTemplate rest;
    private static String base;
    private static String otherBase;
    private static String misconfiguredBase;
    private static String limitedBase;
    private static JdbcTemplate jdbc;

    private static final String QUESTION = "新能源汽车充电中断，没有故障码，应该先查什么资料？";
    private static final String REPLY = "直流快充，SOC 约 40%";

    /** 契约 §2 的 data 白名单表：历史端点与 SSE 共用同一映射，逐字段核对。 */
    private static final Map<String, Set<String>> ALLOWED_DATA_FIELDS = Map.ofEntries(
            Map.entry("RUN_QUEUED", Set.of("status")),
            Map.entry("RUN_RECOVERED", Set.of("attempts")),
            Map.entry("RUN_TOOL", Set.of("toolName", "success", "errorCode", "references",
                    "durationMs", "retryable")),
            Map.entry("RUN_MODEL", Set.of("model", "attempt", "outcome", "durationMs", "retryable",
                    "inputTokens", "outputTokens", "cost")),
            Map.entry("RUN_ANSWER", Set.of("summary", "category", "claims", "suggestions", "uncertainties",
                    "limitations", "references", "riskLevel", "safetyDisposition", "dataNature")),
            Map.entry("RUN_FAILED", Set.of("code")),
            Map.entry("RUN_CANCELLED", Set.of("phase")),
            Map.entry("RUN_LATE_RESPONSE", Set.of("status", "code")),
            Map.entry("RUN_WAITING", Set.of("questionVersion")));

    @BeforeAll static void start() throws Exception {
        databaseBaseUrl = requiredEnvironment("DEVICETRUTH_TEST_PG_BASE_URL");
        username = requiredEnvironment("DEVICETRUTH_TEST_PG_USERNAME");
        password = requiredEnvironment("DEVICETRUTH_TEST_PG_PASSWORD");
        if (!databaseBaseUrl.matches("jdbc:postgresql://[A-Za-z0-9.\\-\\[\\]:]+/"))
            throw new IllegalArgumentException("Test PG base URL must contain host/port and no database or credentials");
        databaseName = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(databaseBaseUrl + "postgres", username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        app = open("stream-owner-a", "stream-project", "scripted");
        otherOwnerApp = open("stream-owner-b", "stream-project", "scripted");
        misconfiguredApp = open("stream-owner-c", "stream-project-c", "unsupported-agent-reasoner");
        // 复验 §8.2 容量隔离：全局并发流上限压到 2，第三条流必须 503
        limitedApp = open("stream-owner-d", "stream-project", "scripted", "--devicetruth.stream.max-connections=2");
        base = base(app);
        otherBase = base(otherOwnerApp);
        misconfiguredBase = base(misconfiguredApp);
        limitedBase = base(limitedApp);
        rest = new TestRestTemplate();
        jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
        importManual();
    }

    @AfterAll static void stop() throws Exception {
        for (ConfigurableApplicationContext context : new ConfigurableApplicationContext[]{
                app, otherOwnerApp, misconfiguredApp, limitedApp})
            if (context != null) context.close();
        if (databaseName != null && databaseName.matches("devicetruth_test_[0-9a-f]{32}")) {
            try (var connection = java.sql.DriverManager.getConnection(databaseBaseUrl + "postgres", username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + databaseName);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test configuration: " + name);
        return value;
    }

    private static ConfigurableApplicationContext open(String owner, String project, String agentReasoner,
                                                       String... extra) {
        var arguments = new ArrayList<String>(List.of(
                "--spring.profiles.active=product", "--server.port=0", "--debug=false",
                "--logging.level.org.springframework=WARN", "--logging.level.org.springframework.jdbc=INFO",
                "--devicetruth.core.reasoner=scripted", "--devicetruth.agent.reasoner=" + agentReasoner,
                "--spring.datasource.url=" + databaseBaseUrl + databaseName,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=" + owner, "--devicetruth.product.project-id=" + project,
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.embedding.model=deterministic-char2",
                "--devicetruth.embedding.dimension=1024", "--devicetruth.embedding.profile-revision=1"));
        arguments.addAll(List.of(extra));
        return new SpringApplicationBuilder(DeviceTruthServerApplication.class)
                .run(arguments.toArray(String[]::new));
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static void importManual() throws Exception {
        var response = post(base + "/api/knowledge/documents", Map.of(
                "title", "充电系统检修手册（演示资料）", "domain", "automotive-demo", "version", "2026.1",
                "filename", "manual.md", "mimeType", "text/markdown",
                "content", Files.readString(Path.of("datasets/knowledge/charging-system-manual.md"),
                        StandardCharsets.UTF_8), "dataNature", "SIMULATED"), null);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
    }

    private static HttpHeaders jsonHeaders(UUID requestKey) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (requestKey != null) headers.set("Idempotency-Key", requestKey.toString());
        return headers;
    }

    private static ResponseEntity<String> post(String url, Object body, UUID requestKey) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders(requestKey)), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return Json.MAPPER.readTree(response.getBody());
    }

    private String conversation(String baseUrl) throws Exception {
        var response = post(baseUrl + "/api/conversations", Map.of(), null);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response).path("conversationId").asText();
    }

    private String submit(String baseUrl, String conversationId, String text) throws Exception {
        var response = post(baseUrl + "/api/conversations/" + conversationId + "/messages",
                Map.of("schemaVersion", 1, "userMessage", text), UUID.randomUUID());
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(202);
        return json(response).path("runId").asText();
    }

    private void reply(String baseUrl, String conversationId, String runId, JsonNode question) {
        var response = post(baseUrl + "/api/conversations/" + conversationId + "/messages", Map.of(
                "schemaVersion", 1, "userMessage", REPLY,
                "replyTo", Map.of("runId", runId,
                        "pendingQuestionId", question.path("pendingQuestionId").asText(),
                        "questionVersion", question.path("questionVersion").asInt())), UUID.randomUUID());
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(202);
    }

    private JsonNode run(String baseUrl, String runId) throws Exception {
        var response = rest.getForEntity(baseUrl + "/api/runs/" + runId, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response);
    }

    private JsonNode await(String baseUrl, String runId, String expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = run(baseUrl, runId);
            if (expected.equals(last.path("status").asText())) return last;
            Thread.sleep(100);
        }
        fail("run " + runId + " 未在 " + timeoutMs + "ms 内到达 " + expected + "，最后状态 "
                + (last == null ? "null" : last.path("status").asText()));
        return last;
    }

    /** 完整两轮 scripted 流程，返回 COMPLETED 的 RunView（含 runId/conversationId）。 */
    private JsonNode completedFlow(String baseUrl) throws Exception {
        String conversationId = conversation(baseUrl);
        String runId = submit(baseUrl, conversationId, QUESTION);
        JsonNode waiting = await(baseUrl, runId, "WAITING_FOR_USER", 20_000);
        reply(baseUrl, conversationId, runId, waiting.path("pendingQuestion"));
        JsonNode completed = await(baseUrl, runId, "COMPLETED", 20_000);
        return completed;
    }

    private JsonNode eventsJson(String baseUrl, String runId, String after) throws Exception {
        String path = baseUrl + "/api/runs/" + runId + "/events" + (after == null ? "" : "?after=" + after);
        var response = rest.getForEntity(path, String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return Json.MAPPER.readTree(response.getBody());
    }

    private static String streamUrl(String baseUrl, String runId) {
        return baseUrl + "/api/runs/" + runId + "/stream";
    }

    // ---------- 断言辅助 ----------

    private static long seqOf(SseFrame frame) {
        try {
            return Json.MAPPER.readTree(frame.data()).path("seq").asLong();
        } catch (Exception e) {
            throw new AssertionError("事件 data 不是合法 JSON：" + frame.data());
        }
    }

    private static String terminalStatus(SseFrame frame) {
        try {
            return Json.MAPPER.readTree(frame.data()).path("status").asText();
        } catch (Exception e) {
            throw new AssertionError("RUN_TERMINAL data 不是合法 JSON：" + frame.data());
        }
    }

    /** 持久事件帧（排除 RUN_TERMINAL：其 data 只有 status，没有 seq）。 */
    private static List<SseFrame> persistentFrames(List<SseFrame> frames) {
        return frames.stream().filter(frame -> !"RUN_TERMINAL".equals(frame.event())).toList();
    }

    /** 持久事件帧的 seq 必须严格递增且互不重复。 */
    private static void assertOrderedUnique(List<SseFrame> frames) {
        List<Long> seqs = persistentFrames(frames).stream().map(AgentStreamTest::seqOf).toList();
        assertThat(seqs).as("事件 seq 必须按序且不重复：%s", seqs).isSorted();
        assertThat(seqs).doesNotHaveDuplicates();
    }

    /** 持久事件帧 seq 必须恰好为 1..expected 各一次，且 SSE id 等于持久 seq（契约 §3 线格式）。 */
    private static void assertExactlySeqsOneToN(List<SseFrame> frames, long expected) {
        List<SseFrame> events = persistentFrames(frames);
        assertOrderedUnique(events);
        assertThat(events.stream().map(AgentStreamTest::seqOf).toList())
                .containsExactlyElementsOf(LongStream.rangeClosed(1, expected).boxed().toList());
        for (SseFrame frame : events)
            assertThat(frame.id()).as("SSE id 必须等于持久 seq").isEqualTo(String.valueOf(seqOf(frame)));
    }

    // ---------- 契约 §6 验收用例 ----------

    /** §6.1：连接建立前已存在的历史事件，SSE 从头（无 Last-Event-ID）全量回读，随后 RUN_TERMINAL 收尾。 */
    @Test void historyBeforeConnectIsFullyReplayedThenTerminalEndsStream() throws Exception {
        JsonNode completed = completedFlow(base);
        String runId = completed.path("runId").asText();
        JsonNode history = eventsJson(base, runId, null);
        int n = history.size();
        assertThat(n).as("两轮 scripted 流程至少产生 QUEUED/TOOL/MODEL/ANSWER 四类事件").isGreaterThanOrEqualTo(4);
        List<String> expectedTypes = new ArrayList<>();
        history.forEach(node -> expectedTypes.add(node.path("type").asText()));

        try (SseClient client = new SseClient(streamUrl(base, runId), null)) {
            assertThat(client.status).isEqualTo(200);
            assertThat(client.contentType).contains("text/event-stream");
            SseFrame terminal = client.awaitTerminal(15_000);
            assertThat(terminalStatus(terminal)).isEqualTo("COMPLETED");
            assertThat(terminal.id()).as("RUN_TERMINAL 无 id（契约 §3）").isNull();
            client.awaitEof(10_000);

            List<SseFrame> events = persistentFrames(client.runEventFrames());
            assertThat(events).hasSize(n);
            assertExactlySeqsOneToN(events, n);
            List<String> frameTypes = events.stream().map(SseFrame::event).toList();
            assertThat(frameTypes).containsExactlyElementsOf(expectedTypes);
            // 终态帧是最后一条：其后不再有任何事件帧
            assertThat(client.runEventFrames().get(client.runEventFrames().size() - 1).event())
                    .isEqualTo("RUN_TERMINAL");
        }
    }

    /** §6.2：连接建立后历史先补齐；订阅期间新事件实时到达（交界不丢不重）；WAITING 期间心跳保活。 */
    @Test void liveEventsArriveAfterSubscribeAndBoundaryHasNoGapOrDuplicate() throws Exception {
        String conversationId = conversation(base);
        String runId = submit(base, conversationId, QUESTION);
        JsonNode waiting = await(base, runId, "WAITING_FOR_USER", 20_000);
        long s = eventsJson(base, runId, null).size();
        assertThat(s).isGreaterThanOrEqualTo(2);

        try (SseClient client = new SseClient(streamUrl(base, runId), null)) {
            assertThat(client.status).isEqualTo(200);
            // 连接即补齐历史（seq 1..s），此时 run 尚未推进，帧数恰好是历史量
            client.awaitSeq(s, 10_000);
            List<SseFrame> initial = client.runEventFrames();
            assertExactlySeqsOneToN(initial, s);

            // WAITING 期间没有新事件：15 秒级心跳至少出现一次，证明流保持且未提前收尾
            client.awaitHeartbeat(20_000);

            // 补答 → 同一连接内实时收到剩余事件，与历史段拼接后恰好 1..N 各一次
            reply(base, conversationId, runId, waiting.path("pendingQuestion"));
            SseFrame terminal = client.awaitTerminal(20_000);
            assertThat(terminalStatus(terminal)).isEqualTo("COMPLETED");
            client.awaitEof(10_000);
            long n = eventsJson(base, runId, null).size();
            assertThat(n).isGreaterThan(s);
            assertExactlySeqsOneToN(client.runEventFrames(), n);
        }
    }

    /**
     * §6.3 + §6.7：连接一读到断点 seq=S 后断开；断线期间补答推进 run 写入新事件
     * （浏览器场景「读到第 12 条断开、写入 13/14」的等价物）；连接二带 Last-Event-ID=S
     * 重连，恰好依序补齐 S+1..N 各一次，两连接合并视角无重复无遗漏。
     */
    @Test void reconnectWithLastEventIdBackfillsMissedEventsInOrderExactlyOnce() throws Exception {
        String conversationId = conversation(base);
        String runId = submit(base, conversationId, QUESTION);
        JsonNode waiting = await(base, runId, "WAITING_FOR_USER", 20_000);
        long s = eventsJson(base, runId, null).size();

        // 连接一：从头读，收到 1..S 后断开（模拟页面断线时 EventSource 持有 Last-Event-ID=S）
        SseClient first = new SseClient(streamUrl(base, runId), null);
        assertThat(first.status).isEqualTo(200);
        first.awaitSeq(s, 10_000);
        assertExactlySeqsOneToN(first.runEventFrames(), s);
        first.close();

        // 断线期间服务端继续推进：补答产生后续事件（至少 2 条：检索/模型 + 答案）
        reply(base, conversationId, runId, waiting.path("pendingQuestion"));
        await(base, runId, "COMPLETED", 20_000);
        long n = eventsJson(base, runId, null).size();
        assertThat(n - s).as("断线期间应至少新增 2 条事件").isGreaterThanOrEqualTo(2);

        // 连接二：Last-Event-ID=S 重连 → 只补齐 S+1..N，按序、各一次，随后 RUN_TERMINAL 收尾
        try (SseClient second = new SseClient(streamUrl(base, runId), String.valueOf(s))) {
            assertThat(second.status).isEqualTo(200);
            SseFrame terminal = second.awaitTerminal(15_000);
            assertThat(terminalStatus(terminal)).isEqualTo("COMPLETED");
            second.awaitEof(10_000);
            List<SseFrame> backfilled = persistentFrames(second.runEventFrames());
            assertThat(backfilled.stream().map(AgentStreamTest::seqOf).toList())
                    .containsExactlyElementsOf(LongStream.rangeClosed(s + 1, n).boxed().toList());
            assertOrderedUnique(backfilled);
        }

        // 合并视角：连接一（1..S）+ 连接二（S+1..N）= 全量各一次，等价页面「补齐且不重复」
        List<Long> combined = new ArrayList<>();
        persistentFrames(first.runEventFrames()).forEach(frame -> combined.add(seqOf(frame)));
        try (SseClient verify = new SseClient(streamUrl(base, runId), null)) {
            // 终态后再连只回读历史：确认 DB 全量确实为 1..N
            verify.awaitTerminal(15_000);
            List<SseFrame> all = persistentFrames(verify.runEventFrames());
            assertThat(all).hasSize((int) n);
            assertExactlySeqsOneToN(all, n);
        }
        assertThat(combined).containsExactlyElementsOf(LongStream.rangeClosed(1, s).boxed().toList());
    }

    /** §6.4：历史端点游标语义（after=N 取 seq>N）+ 非法游标 400 + 跨身份/不存在 404。 */
    @Test void historyCursorSemanticsInvalidCursorAndCrossIdentityFailures() throws Exception {
        JsonNode completed = completedFlow(base);
        String runId = completed.path("runId").asText();
        long n = eventsJson(base, runId, null).size();
        assertThat(n).isGreaterThanOrEqualTo(4);

        // 游标语义：after=N → seq>N；after=0 → 全量；无 after → 全量
        JsonNode tail = eventsJson(base, runId, String.valueOf(n - 1));
        assertThat(tail.size()).isEqualTo(1);
        assertThat(tail.get(0).path("seq").asLong()).isEqualTo(n);
        assertThat(eventsJson(base, runId, String.valueOf(n)).size()).isZero();
        assertThat(eventsJson(base, runId, "0").size()).isEqualTo((int) n);
        // 顺序严格递增
        List<Long> seqs = new ArrayList<>();
        eventsJson(base, runId, null).forEach(node -> seqs.add(node.path("seq").asLong()));
        assertThat(seqs).isSorted().doesNotHaveDuplicates();

        // 非法游标：非数字与负数 → 400 INVALID_REQUEST
        for (String bad : new String[] {"abc", "-1", "1.5"}) {
            var response = rest.getForEntity(base + "/api/runs/" + runId + "/events?after=" + bad, String.class);
            assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(400);
            assertThat(response.getBody()).contains("INVALID_REQUEST");
        }

        // SSE 非法游标同样在连接建立前 400（JSON 错误体）
        for (String bad : new String[] {"abc", "-1"}) {
            try (SseClient client = new SseClient(streamUrl(base, runId), bad)) {
                assertThat(client.status).isEqualTo(400);
                assertThat(client.errorBody()).contains("INVALID_REQUEST");
            }
        }

        // 跨身份：历史端点与 SSE 均 404 NOT_FOUND_OR_DENIED，不泄露 run 存在性
        var deniedHistory = rest.getForEntity(otherBase + "/api/runs/" + runId + "/events", String.class);
        assertThat(deniedHistory.getStatusCode().value()).isEqualTo(404);
        assertThat(deniedHistory.getBody()).contains("NOT_FOUND_OR_DENIED");
        try (SseClient denied = new SseClient(streamUrl(otherBase, runId), null)) {
            assertThat(denied.status).isEqualTo(404);
            assertThat(denied.contentType).contains("application/json");
            assertThat(denied.errorBody()).contains("NOT_FOUND_OR_DENIED");
        }

        // 不存在的 run：两个端点统一 404
        String unknown = UUID.randomUUID().toString();
        var unknownHistory = rest.getForEntity(base + "/api/runs/" + unknown + "/events", String.class);
        assertThat(unknownHistory.getStatusCode().value()).isEqualTo(404);
        try (SseClient unknownStream = new SseClient(streamUrl(base, unknown), null)) {
            assertThat(unknownStream.status).isEqualTo(404);
        }
    }

    /** §6.5：三种终态都在最后一条持久事件后发送 RUN_TERMINAL 并正常结束响应体。 */
    @Test void terminalFrameEndsStreamForCompletedFailedAndCancelled() throws Exception {
        // COMPLETED：完整两轮流程
        JsonNode completed = completedFlow(base);
        String completedRunId = completed.path("runId").asText();
        try (SseClient client = new SseClient(streamUrl(base, completedRunId), null)) {
            assertThat(terminalStatus(client.awaitTerminal(15_000))).isEqualTo("COMPLETED");
            client.awaitEof(10_000);
            assertThat(client.runEventFrames().stream().map(SseFrame::event))
                    .contains("RUN_QUEUED", "RUN_TOOL", "RUN_ANSWER");
        }

        // FAILED：模型配置缺失的 run，原因码经白名单透出
        String failedConversation = conversation(misconfiguredBase);
        String failedRunId = submit(misconfiguredBase, failedConversation, QUESTION);
        await(misconfiguredBase, failedRunId, "FAILED", 20_000);
        try (SseClient client = new SseClient(streamUrl(misconfiguredBase, failedRunId), null)) {
            assertThat(terminalStatus(client.awaitTerminal(15_000))).isEqualTo("FAILED");
            client.awaitEof(10_000);
            List<SseFrame> events = client.runEventFrames();
            assertThat(events.stream().map(SseFrame::event)).contains("RUN_QUEUED", "RUN_FAILED");
            SseFrame failed = events.stream().filter(frame -> frame.event().equals("RUN_FAILED")).findFirst().orElseThrow();
            assertThat(Json.MAPPER.readTree(failed.data()).path("data").path("code").asText())
                    .isEqualTo("MODEL_CONFIGURATION_MISSING");
        }

        // CANCELLED：WAITING 期间取消，立即迁移终态
        String cancelledConversation = conversation(base);
        String cancelledRunId = submit(base, cancelledConversation, QUESTION);
        await(base, cancelledRunId, "WAITING_FOR_USER", 20_000);
        var cancelled = rest.postForEntity(base + "/api/runs/" + cancelledRunId + "/cancel", null, String.class);
        assertThat(cancelled.getStatusCode().value()).as(cancelled.getBody()).isEqualTo(200);
        assertThat(Json.MAPPER.readTree(cancelled.getBody()).path("status").asText()).isEqualTo("CANCELLED");
        try (SseClient client = new SseClient(streamUrl(base, cancelledRunId), null)) {
            assertThat(terminalStatus(client.awaitTerminal(15_000))).isEqualTo("CANCELLED");
            client.awaitEof(10_000);
            List<SseFrame> events = client.runEventFrames();
            assertThat(events.stream().map(SseFrame::event)).contains("RUN_CANCELLED");
            SseFrame cancel = events.stream().filter(frame -> frame.event().equals("RUN_CANCELLED")).findFirst().orElseThrow();
            assertThat(Json.MAPPER.readTree(cancel.data()).path("data").path("phase").asText()).isEqualTo("IMMEDIATE");
        }
    }

    /** §6.6：公开事件（历史端点与 SSE 两路）不含敏感词，顶层字段固定，data 严格不超出契约 §2 白名单。 */
    @Test void publicEventsStayWithinWhitelistAndContainNoSecrets() throws Exception {
        JsonNode completed = completedFlow(base);
        String runId = completed.path("runId").asText();

        List<String> payloads = new ArrayList<>();
        List<JsonNode> eventNodes = new ArrayList<>();
        eventsJson(base, runId, null).forEach(node -> {
            eventNodes.add(node);
            payloads.add(node.toString());
        });
        try (SseClient client = new SseClient(streamUrl(base, runId), null)) {
            client.awaitTerminal(15_000);
            for (SseFrame frame : client.runEventFrames()) {
                if ("RUN_TERMINAL".equals(frame.event())) continue;
                payloads.add(frame.data());
            }
        }

        // 敏感词（大小写不敏感）：Prompt/密钥/异常堆栈/调用链等绝不出现
        for (String payload : payloads) {
            String lower = payload.toLowerCase();
            assertThat(lower).as("事件 JSON 含敏感词：%s", payload)
                    .doesNotContain("prompt", "secret", "apikey", "password", "authorization",
                            "bearer", "stack", "trace", "\"key\"", "\"payload\"");
        }

        // 顶层字段固定 + data 字段白名单（RUN_TERMINAL 无 data 字段，不在此列）
        for (JsonNode event : eventNodes) {
            Set<String> topFields = new java.util.HashSet<>();
            event.fieldNames().forEachRemaining(topFields::add);
            assertThat(topFields).containsExactlyInAnyOrder("seq", "type", "recordedAt", "data");
            assertThat(event.path("seq").isNumber()).isTrue();
            assertThat(event.path("type").isTextual()).isTrue();
            assertThat(Instant.parse(event.path("recordedAt").asText())).isNotNull();
            String type = event.path("type").asText();
            Set<String> allowed = ALLOWED_DATA_FIELDS.getOrDefault(type, Set.of());
            List<String> actualFields = new ArrayList<>();
            event.path("data").properties().forEach(entry -> actualFields.add(entry.getKey()));
            assertThat(actualFields).as("%s 的 data 字段必须不超出契约 §2 白名单", type)
                    .isSubsetOf(allowed);
        }
        // scripted 流程必然覆盖的答案事件：data 非空且 claims/references 为数组
        JsonNode answer = null;
        for (JsonNode event : eventNodes)
            if ("RUN_ANSWER".equals(event.path("type").asText())) answer = event;
        assertThat(answer).as("COMPLETED run 必须有 RUN_ANSWER 事件").isNotNull();
        assertThat(answer.path("data").path("category").isTextual()).isTrue();
        assertThat(answer.path("data").path("claims").isArray()).isTrue();
    }

    /** §3：单 run 活跃订阅有界（默认 16），超出 409 STREAM_CAPACITY；事件推进后断开订阅会被清理。 */
    @Test void concurrentSubscriptionsAreBoundedAndCleanedUpAfterDisconnect() throws Exception {
        String conversationId = conversation(base);
        String runId = submit(base, conversationId, QUESTION);
        await(base, runId, "WAITING_FOR_USER", 20_000);

        List<SseClient> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                SseClient client = new SseClient(streamUrl(base, runId), null);
                assertThat(client.status).as("第 %d 个订阅应成功", i + 1).isEqualTo(200);
                clients.add(client);
            }
            try (SseClient overflow = new SseClient(streamUrl(base, runId), null)) {
                assertThat(overflow.status).isEqualTo(409);
                assertThat(overflow.errorBody()).contains("STREAM_CAPACITY");
            }
        } finally {
            for (SseClient client : clients) client.close();
        }

        // 断开后订阅必须被清理：推进 run 事件唤醒所有死连接，随后新订阅恢复 200
        reply(base, conversationId, runId, run(base, runId).path("pendingQuestion"));
        await(base, runId, "COMPLETED", 20_000);
        long deadline = System.currentTimeMillis() + 30_000;
        int status = 409;
        while (System.currentTimeMillis() < deadline) {
            try (SseClient probe = new SseClient(streamUrl(base, runId), null)) {
                status = probe.status;
                if (status == 200) break;
            }
            Thread.sleep(500);
        }
        assertThat(status).as("断开的订阅清理后应恢复可用").isEqualTo(200);
    }

    // ---------- 复验 §8 验收用例（05-01 复验轮） ----------

    private static final String FAKE_PROBE_TOOL = "fake_probe";
    private static boolean fakeProbeRegistered;

    private static CapabilityGateway agentGateway() {
        return app.getBean("agentCapabilityGateway", CapabilityGateway.class);
    }

    /** 向共享网关注册验收用假只读工具（只注册一次；scripted 推理器按名选工具，不受影响）。 */
    private static synchronized void ensureFakeProbeTool() {
        if (fakeProbeRegistered) return;
        ToolSpec spec = new ToolSpec(FAKE_PROBE_TOOL, "验收专用假只读工具", "acceptance", true,
                Map.of("type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query"),
                        "additionalProperties", false),
                "ProbeOutcome{status:String}");
        agentGateway().registerTool(spec, (arguments, context) -> ToolOutcome.ok(UUID.randomUUID().toString(),
                FAKE_PROBE_TOOL, Map.of("status", "ok"),
                List.of(new Reference(UUID.randomUUID().toString(), "KNOWLEDGE_DOCUMENT",
                        "fake-probe-document", "1.0", UUID.randomUUID().toString(),
                        "假资料（验收专用）", null, "验收章节", "假资料摘录，仅用于验收", "SIMULATED")),
                "SIMULATED"));
        fakeProbeRegistered = true;
    }

    /**
     * 复验 §8.1 渐进可见性：自定义推理器首次 propose 调假工具，第二次 propose 阻塞在 latch。
     * 在释放第二次模型调用之前：(a) 历史端点已能看到执行中的 RUN_TOOL（持久、有序，attempt=1）；
     * (b) 同一时刻新开的 SSE 连接能收到该 RUN_TOOL 帧。释放后 ANSWER 正常收口为 COMPLETED。
     */
    @Test void progressiveToolEventIsVisibleOnHistoryAndLiveStreamBeforeSecondModelCall() throws Exception {
        ensureFakeProbeTool();
        ServerIdentity identity = app.getBean(ServerIdentity.class);
        var dataSource = app.getBean(HikariDataSource.class);
        var streamService = app.getBean(RunEventStreamService.class);
        var proposes = new AtomicInteger();
        var secondCallEntered = new CountDownLatch(1);
        var releaseSecondCall = new CountDownLatch(1);
        GeneralReasoner progressive = new GeneralReasoner() {
            @Override public String name() { return "progressive-visibility"; }
            @Override public ActionProposal propose(Context context) {
                if (proposes.incrementAndGet() == 1)
                    return new ActionProposal.CallTool(FAKE_PROBE_TOOL,
                            Map.of("query", "渐进可见性"), "验收：先调用假工具取回引用");
                secondCallEntered.countDown();
                try {
                    if (!releaseSecondCall.await(60, TimeUnit.SECONDS))
                        throw new IllegalStateException("等待释放 latch 超时");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return new ActionProposal.Answer("KNOWLEDGE",
                        "渐进持久化验收：工具事件已在执行中落库并被实时订阅收到。",
                        List.of(), List.of("无实际建议"), List.of("验收答案"), List.of(), List.of());
            }
        };
        var service = new GeneralAgentService(new ConversationRepository(jdbc), new AgentRunRepository(jdbc),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), identity,
                agentGateway(), () -> progressive, 1, 1, true,
                app.getBean(io.devicetruth.knowledge.FileUploadService.class), 60, 3,
                app.getBean(io.devicetruth.skill.SkillRuntime.class), streamService::publish);
        try {
            UUID conversationId = service.create().conversationId();
            RunView accepted = service.submit(conversationId, UUID.randomUUID(),
                    new AgentTaskInput(1, "渐进持久化验收问题", null, null, null, null, null));
            UUID runId = accepted.runId();
            assertThat(secondCallEntered.await(20, TimeUnit.SECONDS))
                    .as("第二次模型调用应已到达并阻塞在 latch").isTrue();

            // (a) 释放第二次模型调用之前：历史端点已能看到执行中的 RUN_TOOL 事件
            long deadline = System.currentTimeMillis() + 15_000;
            JsonNode toolEvent = null;
            while (System.currentTimeMillis() < deadline && toolEvent == null) {
                for (JsonNode node : eventsJson(base, runId.toString(), null))
                    if ("RUN_TOOL".equals(node.path("type").asText())
                            && FAKE_PROBE_TOOL.equals(node.path("data").path("toolName").asText())) {
                        toolEvent = node;
                        break;
                    }
                if (toolEvent == null) Thread.sleep(100);
            }
            assertThat(toolEvent).as("第二次模型调用完成前历史端点应已可见 RUN_TOOL").isNotNull();
            assertThat(toolEvent.path("seq").asLong()).isEqualTo(2);
            assertThat(toolEvent.path("data").path("success").asBoolean()).isTrue();
            assertThat(toolEvent.path("data").path("references").asInt()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT attempt FROM dt_run_event WHERE run_id=? AND type='RUN_TOOL'",
                    Integer.class, runId)).as("首次执行的步骤事件 attempt 必须为 1").isEqualTo(1);

            // (b) 同一时刻新开 SSE 连接：订阅即回放历史，应实时收到该 RUN_TOOL 帧
            try (SseClient live = new SseClient(streamUrl(base, runId.toString()), null)) {
                assertThat(live.status).isEqualTo(200);
                live.awaitSeq(2, 15_000);
                SseFrame toolFrame = live.runEventFrames().stream()
                        .filter(frame -> "RUN_TOOL".equals(frame.event())).findFirst().orElseThrow();
                assertThat(Json.MAPPER.readTree(toolFrame.data()).path("data").path("toolName").asText())
                        .isEqualTo(FAKE_PROBE_TOOL);

                // 释放第二次模型调用 → ANSWER → COMPLETED；同一连接实时收到 RUN_ANSWER 与 RUN_TERMINAL
                releaseSecondCall.countDown();
                await(base, runId.toString(), "COMPLETED", 20_000);
                assertThat(terminalStatus(live.awaitTerminal(20_000))).isEqualTo("COMPLETED");
                live.awaitEof(10_000);
                assertExactlySeqsOneToN(live.runEventFrames(), 3);
                List<String> types = live.runEventFrames().stream().map(SseFrame::event).toList();
                assertThat(types).containsExactly("RUN_QUEUED", "RUN_TOOL", "RUN_ANSWER", "RUN_TERMINAL");
            }
            assertThat(run(base, runId.toString()).path("status").asText()).isEqualTo("COMPLETED");
        } finally {
            releaseSecondCall.countDown();
            service.shutdown();
        }
    }

    /** 复验 §8.2 容量隔离：全局上限 2 时装配下，两个 WAITING run 各挂一条流后第三条必须 503 STREAM_CAPACITY。 */
    @Test void globalStreamCapacityRejectsThirdConnectionButProductApiStaysAvailable() throws Exception {
        String cid1 = conversation(limitedBase);
        String cid2 = conversation(limitedBase);
        String runId1 = submit(limitedBase, cid1, QUESTION);
        String runId2 = submit(limitedBase, cid2, QUESTION);
        await(limitedBase, runId1, "WAITING_FOR_USER", 20_000);
        await(limitedBase, runId2, "WAITING_FOR_USER", 20_000);

        SseClient first = new SseClient(streamUrl(limitedBase, runId1), null);
        SseClient second = new SseClient(streamUrl(limitedBase, runId2), null);
        try {
            assertThat(first.status).as("第一条流应成功").isEqualTo(200);
            assertThat(second.status).as("第二条流应成功").isEqualTo(200);

            // 全局容量已满：第三条流（即使挂在未满单 run 上）必须在提交响应前 503
            try (SseClient third = new SseClient(streamUrl(limitedBase, runId1), null)) {
                assertThat(third.status).isEqualTo(503);
                assertThat(third.errorBody()).contains("STREAM_CAPACITY");
            }

            // 容量隔离：普通 HTTP 端点完全不受影响
            assertThat(eventsJson(limitedBase, runId1, null).size()).isGreaterThanOrEqualTo(2);
            assertThat(eventsJson(limitedBase, runId2, null).size()).isGreaterThanOrEqualTo(2);
            reply(limitedBase, cid1, runId1, run(limitedBase, runId1).path("pendingQuestion"));
            var cancelled = rest.postForEntity(limitedBase + "/api/runs/" + runId2 + "/cancel", null, String.class);
            assertThat(cancelled.getStatusCode().value()).as(cancelled.getBody()).isEqualTo(200);
            assertThat(Json.MAPPER.readTree(cancelled.getBody()).path("status").asText()).isEqualTo("CANCELLED");
            await(limitedBase, runId1, "COMPLETED", 20_000);
            await(limitedBase, runId2, "CANCELLED", 20_000);
        } finally {
            first.close();
            second.close();
        }
    }

    /**
     * 复验 §8.3 并发回收：多线程并发开/关若干流连接（跨 3 个 run、聚合在途超过单 run 上限 16、
     * 未超全局 64），再打满单 run 边界 16 条后全部关闭，等 emitter 清理后再开新连接成功——
     * 证明订阅登记与全局计数完全回收、无泄漏。
     */
    @Test void concurrentOpenCloseFullyReclaimsStreamCapacityAcrossRuns() throws Exception {
        List<String> runIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String cid = conversation(base);
            String rid = submit(base, cid, QUESTION);
            await(base, rid, "WAITING_FOR_USER", 20_000);
            runIds.add(rid);
        }

        // 第一阶段：6 线程并发开/关搅动 30 次（每次持有几十毫秒，制造 add/remove 竞态）
        int threads = 6, rounds = 5;
        var start = new CountDownLatch(1);
        List<String> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int offset = t;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int round = 0; round < rounds; round++) {
                    String rid = runIds.get((offset + round) % runIds.size());
                    try (SseClient client = new SseClient(streamUrl(base, rid), null)) {
                        if (client.status != 200)
                            failures.add("thread=" + offset + " round=" + round + " status=" + client.status);
                        Thread.sleep(60);
                    } catch (Exception e) {
                        failures.add("thread=" + offset + " round=" + round + " " + e.getClass().getSimpleName());
                    }
                }
            }, "stream-churn-" + t);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        for (Thread worker : workers) worker.join(60_000);
        assertThat(workers).as("搅动线程应全部结束").allMatch(worker -> !worker.isAlive());
        assertThat(failures).as("并发开关不应出现失败连接").isEmpty();

        // 第二阶段：3 个 run 各挂 6 条（聚合 18 > 单 run 上限 16，仍远低于全局 64）全部成功——
        // 证明跨 run 的聚合容量只受全局上限约束。注意：客户端 close 后服务端要经「心跳写失败」
        // 感知断开（首个写可能落进 OS 缓冲，最坏两个心跳周期约 30s），上一阶段关闭的死订阅
        // 可能仍占额度，因此用重试吸收清理窗口；单 run 边界的精确 409/回收证明放在第三阶段。
        List<SseClient> bulk = openBulkWithRetry(runIds, 6, 90_000);
        for (SseClient client : bulk) client.close();

        // 第三阶段：打满单 run 边界（16 条，第 17 条必须 409）后全部关闭，再重开必须全部 200——
        // 若订阅集合/全局计数泄漏，重开会拿到 409/503（能重新打满即证明容量完全回收）。
        List<SseClient> full = openUntilFull(runIds.get(0), 16);
        try {
            try (SseClient overflow = new SseClient(streamUrl(base, runIds.get(0)), null)) {
                assertThat(overflow.status).isEqualTo(409);
                assertThat(overflow.errorBody()).contains("STREAM_CAPACITY");
            }
        } finally {
            for (SseClient client : full) client.close();
        }
        // 全部关闭、等心跳清理死订阅后重新打满：任何泄漏都会让这里的打开失败
        List<SseClient> reopened = openUntilFull(runIds.get(0), 16);
        for (SseClient client : reopened) client.close();
    }

    /**
     * 为多 run 各打开 perRun 条流连接（聚合 opened = runIds × perRun）；若中途遇到 409/503
     * （上一轮断开的订阅还没被心跳感知清理），关闭已开的连接并等待重试。
     * 全部 200 才返回——限额判定与计数在多 run 并发打开下保持一致。
     */
    private List<SseClient> openBulkWithRetry(List<String> runIds, int perRun, long budgetMs) throws Exception {
        long deadline = System.currentTimeMillis() + budgetMs;
        List<SseClient> opened = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            opened = new ArrayList<>();
            boolean complete = true;
            outer:
            for (String rid : runIds)
                for (int i = 0; i < perRun; i++) {
                    SseClient client = new SseClient(streamUrl(base, rid), null);
                    opened.add(client);
                    if (client.status != 200) {
                        complete = false;
                        break outer;
                    }
                }
            if (complete) return opened;
            for (SseClient client : opened) client.close();
            // 死订阅由心跳写失败感知清理（一个心跳周期 15s）：等待整批清理后再重试，
            // 避免重试流量制造新死订阅导致容量永远无法收敛
            Thread.sleep(16_000);
        }
        fail(budgetMs + "ms 内无法为 " + runIds.size() + " 个 run 各打开 " + perRun + " 条流（容量未完全回收）");
        return opened;
    }

    /**
     * 保留成功连接，仅重试被拒绝的连接，避免在等待服务端发现断开时反复制造新断开。
     */
    private List<SseClient> openUntilFull(String runId, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        List<SseClient> opened = new ArrayList<>();
        try {
            while (opened.size() < expected && System.currentTimeMillis() < deadline) {
                SseClient client = new SseClient(streamUrl(base, runId), null);
                if (client.status == 200) {
                    opened.add(client);
                } else {
                    try (client) {
                        assertThat(client.status).isIn(409, 503);
                    }
                    Thread.sleep(500);
                }
            }
            assertThat(opened).as("60s 内为 run %s 完整回收并重新打开容量", runId).hasSize(expected);
            return opened;
        } finally {
            if (opened.size() != expected) {
                for (SseClient client : opened) client.close();
            }
        }
    }

    /** 复验 §8.4 空值双路径：data 显式含 durationMs=null 的 RUN_MODEL 事件，历史端点与 SSE 都正常送达、
     *  不抛异常，且公开 data 中不含 durationMs 键（空值省略）。 */
    @Test void nullDurationModelEventSurvivesBothPublicPathsWithKeyOmitted() throws Exception {
        JsonNode completed = completedFlow(base);
        String runId = completed.path("runId").asText();
        UUID runUuid = UUID.fromString(runId);
        UUID conversationUuid = UUID.fromString(completed.path("conversationId").asText());
        long before = eventsJson(base, runId, null).size();

        ServerIdentity identity = app.getBean(ServerIdentity.class);
        var runsRepo = new AgentRunRepository(jdbc);
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "null-duration-probe");
        payload.put("attempt", 1);
        payload.put("outcome", "OK");
        payload.put("durationMs", null);
        payload.put("retryable", false);
        runsRepo.appendEvent(identity, runUuid, conversationUuid, "RUN_MODEL", payload, Instant.now());

        // 路径一：历史端点正常 200，公开 data 省略空值键、保留其余字段
        JsonNode tail = eventsJson(base, runId, String.valueOf(before));
        assertThat(tail.size()).isEqualTo(1);
        JsonNode event = tail.get(0);
        assertThat(event.path("type").asText()).isEqualTo("RUN_MODEL");
        JsonNode data = event.path("data");
        assertThat(data.path("model").asText()).isEqualTo("null-duration-probe");
        assertThat(data.path("outcome").asText()).isEqualTo("OK");
        assertThat(data.has("durationMs")).as("空值字段必须在公开视图中省略").isFalse();

        // 路径二：SSE 回放同一事件也不抛异常、同样省略空值键
        try (SseClient client = new SseClient(streamUrl(base, runId), null)) {
            client.awaitSeq(before + 1, 15_000);
            SseFrame frame = client.runEventFrames().stream()
                    .filter(candidate -> candidate.event().equals("RUN_MODEL"))
                    .filter(candidate -> {
                        try {
                            return Json.MAPPER.readTree(candidate.data()).path("data").path("model")
                                    .asText().equals("null-duration-probe");
                        } catch (Exception e) {
                            throw new AssertionError("SSE 帧不是合法 JSON：" + candidate.data());
                        }
                    })
                    .findFirst().orElseThrow();
            JsonNode sseData = Json.MAPPER.readTree(frame.data()).path("data");
            assertThat(sseData.has("durationMs")).as("SSE 公开视图同样必须省略空值字段").isFalse();
            assertThat(sseData.path("attempt").asInt()).isEqualTo(1);
            client.awaitTerminal(15_000);
        }
    }

    /** 回收后保留客户端已见的尾部事件，新的恢复事件从旧游标之后继续。 */
    @Test void recoveryPreservesPreviouslyDeliveredCursorAndStampsStepsPerAttempt() throws Exception {
        ServerIdentity identity = app.getBean(ServerIdentity.class);
        var conversations = new ConversationRepository(jdbc);
        var runsRepo = new AgentRunRepository(jdbc);
        Instant now = Instant.now();
        var conversation = conversations.create(identity, now);
        UUID messageId = conversations.append(identity, conversation.conversationId(), UUID.randomUUID(),
                "恢复去重验收消息", null, now, now);
        var enqueued = runsRepo.enqueue(identity, conversation.conversationId(), messageId, now, now);
        UUID runId = enqueued.runId();
        var record = runsRepo.load(identity, runId, false);
        assertThat(record.status()).isEqualTo("QUEUED");

        // 领取 RUNNING：attempts 0→1；再手工把租约置为过期，构造崩溃遗留现场
        assertThat(runsRepo.markRunning(identity, record, "recovery-test-worker",
                now.minusSeconds(30), Instant.now())).isEqualTo(1);
        var running = runsRepo.load(identity, runId, false);
        assertThat(running.attempts()).isEqualTo(1);

        // 旧执行尝试的步骤事件位于尾部，客户端已收到它们的 SSE id。
        Map<String, Object> toolPayload = new HashMap<>();
        toolPayload.put("toolName", FAKE_PROBE_TOOL);
        toolPayload.put("success", true);
        toolPayload.put("references", 1);
        toolPayload.put("durationMs", 3L);
        runsRepo.appendEvent(identity, runId, conversation.conversationId(), "RUN_TOOL", toolPayload,
                Instant.now(), 1);
        Map<String, Object> modelPayload = new HashMap<>();
        modelPayload.put("model", "recovery-probe");
        modelPayload.put("attempt", 1);
        modelPayload.put("outcome", "OK");
        modelPayload.put("durationMs", null);
        runsRepo.appendEvent(identity, runId, conversation.conversationId(), "RUN_MODEL", modelPayload,
                Instant.now(), 1);
        runsRepo.appendEvent(identity, runId, conversation.conversationId(), "RUN_TOOL", toolPayload,
                Instant.now(), 1);
        long lastDelivered = runsRepo.latestSeq(identity, runId);

        var expired = new AgentRunRepository.ExpiredRun(runId, conversation.conversationId(),
                running.version(), 1);
        assertThat(runsRepo.requeueRecovered(identity, expired, Instant.now())).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? "
                + "AND type IN ('RUN_TOOL','RUN_MODEL')", Integer.class, runId))
                .as("已经发布的步骤事件必须保留").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_RECOVERED' "
                + "AND safe_payload->>'attempts'='1'", Integer.class, runId)).isEqualTo(1);
        assertThat(runsRepo.load(identity, runId, false).status()).isEqualTo("QUEUED");
        var view = runsRepo.eventsAfter(identity, runId, 0, 100);
        assertThat(view).extracting(SafeAgentEventViews.PublicEvent::type)
                .containsExactly("RUN_QUEUED", "RUN_TOOL", "RUN_MODEL", "RUN_TOOL", "RUN_RECOVERED");
        assertThat(runsRepo.eventsAfter(identity, runId, lastDelivered, 100)).singleElement()
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo("RUN_RECOVERED");
                    assertThat(event.seq()).isEqualTo(lastDelivered + 1);
                });

        // 正常完成 run 的 attempt 语义：首次执行的步骤事件 attempt=1，第二轮为 2；生命周期事件恒为 0
        JsonNode done = completedFlow(base);
        UUID doneId = UUID.fromString(done.path("runId").asText());
        Long waitingSeq = jdbc.queryForObject(
                "SELECT min(seq) FROM dt_run_event WHERE run_id=? AND type='RUN_WAITING'", Long.class, doneId);
        assertThat(waitingSeq).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type IN "
                        + "('RUN_TOOL','RUN_MODEL') AND seq<? AND attempt=1", Integer.class, doneId, waitingSeq))
                .as("等待补充之前的步骤事件必须全部属于首次执行（attempt=1）")
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type IN "
                        + "('RUN_TOOL','RUN_MODEL') AND seq<?", Integer.class, doneId, waitingSeq));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type IN "
                        + "('RUN_TOOL','RUN_MODEL') AND seq>? AND attempt=2", Integer.class, doneId, waitingSeq))
                .as("补答后的步骤事件必须记为第二次执行（attempt=2）")
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type IN "
                        + "('RUN_TOOL','RUN_MODEL') AND seq>?", Integer.class, doneId, waitingSeq));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? "
                        + "AND type IN ('RUN_QUEUED','RUN_WAITING','RUN_ANSWER') AND attempt<>0",
                Integer.class, doneId)).as("生命周期事件 attempt 必须为 0").isZero();
    }

    // ---------- SSE 测试客户端（JDK 内置 HttpClient，无新依赖） ----------

    private record SseFrame(String id, String event, String data) {}

    private static final class SseClient implements AutoCloseable {
        private static final HttpClient HTTP = HttpClient.newHttpClient();
        private final List<SseFrame> frames = java.util.Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch eof = new CountDownLatch(1);
        private final int status;
        private final String contentType;
        private final String errorBody;
        private final InputStream body;
        private final Thread thread;

        SseClient(String url, String lastEventId) throws IOException, InterruptedException {
            var builder = HttpRequest.newBuilder(URI.create(url)).header("Accept", "text/event-stream");
            if (lastEventId != null) builder.header("Last-Event-ID", lastEventId);
            HttpResponse<InputStream> response =
                    HTTP.send(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            this.status = response.statusCode();
            this.contentType = response.headers().firstValue("Content-Type").orElse("");
            if (status != 200) {
                this.errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                this.body = null;
                this.thread = null;
                eof.countDown();
                return;
            }
            this.errorBody = "";
            this.body = response.body();
            this.thread = new Thread(this::readLoop, "agent-stream-test-reader");
            thread.setDaemon(true);
            thread.start();
        }

        int status() { return status; }
        String contentType() { return contentType; }
        String errorBody() { return errorBody; }

        private void readLoop() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                String id = null;
                String event = null;
                StringBuilder data = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (data != null) frames.add(new SseFrame(id, event == null ? "message" : event, data.toString()));
                        id = null;
                        event = null;
                        data = null;
                        continue;
                    }
                    if (line.startsWith(":")) {
                        frames.add(new SseFrame(null, "heartbeat", line));
                        continue;
                    }
                    int colon = line.indexOf(':');
                    String field = colon < 0 ? line : line.substring(0, colon);
                    String value = colon < 0 ? "" : line.substring(colon + 1);
                    if (value.startsWith(" ")) value = value.substring(1);
                    switch (field) {
                        case "id" -> id = value;
                        case "event" -> event = value;
                        case "data" -> data = data == null ? new StringBuilder(value) : data.append('\n').append(value);
                        default -> { }
                    }
                }
            } catch (IOException ignored) {
                // 客户端 close() 主动断开：读循环安静结束
            } finally {
                eof.countDown();
            }
        }

        /** RUN_* 事件帧快照（排除心跳）。 */
        List<SseFrame> runEventFrames() {
            synchronized (frames) {
                return frames.stream().filter(frame -> frame.event().startsWith("RUN_")).toList();
            }
        }

        SseFrame awaitTerminal(long timeoutMs) throws InterruptedException {
            return awaitFrame(frame -> "RUN_TERMINAL".equals(frame.event()), timeoutMs, "RUN_TERMINAL");
        }

        void awaitSeq(long seq, long timeoutMs) throws InterruptedException {
            awaitFrame(frame -> !"RUN_TERMINAL".equals(frame.event()) && seqOf(frame) == seq, timeoutMs, "seq=" + seq);
        }

        SseFrame awaitHeartbeat(long timeoutMs) throws InterruptedException {
            return awaitFrame(frame -> "heartbeat".equals(frame.event()), timeoutMs, "心跳");
        }

        private SseFrame awaitFrame(java.util.function.Predicate<SseFrame> predicate,
                                    long timeoutMs, String desc) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                synchronized (frames) {
                    for (SseFrame frame : frames)
                        if (predicate.test(frame)) return frame;
                }
                if (eof.getCount() == 0) break;
                Thread.sleep(25);
            }
            fail("等待 " + desc + " 超时（" + timeoutMs + "ms），已收到帧：" + runEventFrames());
            return null;
        }

        /** 终态后服务端正常结束响应体：读循环应观察到 EOF。 */
        void awaitEof(long timeoutMs) throws InterruptedException {
            assertThat(eof.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .as("终态后流应在 %dms 内正常结束", timeoutMs).isTrue();
        }

        @Override public void close() throws IOException {
            if (body != null) body.close();
        }
    }
}
