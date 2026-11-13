package io.devicetruth.application;

import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.StructuredAgentReasoner;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.GeneralAgentService.AgentTaskInput;
import io.devicetruth.application.agent.GeneralAgentService.RunView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.core.reasoning.ScriptedAgentReasoner;
import io.devicetruth.server.DeviceTruthServerApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * 03-02-T1：跨进程稳定检查点与补证协议强化的真实 PG 验证。
 * 每个用例自建独立会话，覆盖：进程重启后的 WAITING 保留与同 run 续跑、
 * 崩溃遗留 RUNNING 的租约回收与检查点重建、并发补答只推进一次、
 * 旧版本/跨身份/伪造引用负例在重启后仍然成立。
 */
class RunRecoveryTest {
    @org.junit.jupiter.api.io.TempDir static Path fileRoot;
    private static final String MANUAL = "datasets/knowledge/charging-system-manual.md";
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static ConfigurableApplicationContext app;
    private static String base;
    private static JdbcTemplate jdbc;
    private static HikariDataSource dataSource;
    private static CapabilityGateway gateway;
    private static AgentRunRepository runs;
    private static ConversationRepository conversations;
    private static TransactionTemplate transactions;
    private static final ServerIdentity IDENTITY = new ServerIdentity("owner-a", "project-a");
    private static final long LEASE_SECONDS = 2;

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
        app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--debug=false",
                "--logging.level.org.springframework=WARN", "--logging.level.org.springframework.jdbc=INFO",
                "--devicetruth.core.reasoner=scripted",
                "--spring.datasource.url=" + databaseBaseUrl + databaseName,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=owner-a", "--devicetruth.product.project-id=project-a",
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.embedding.model=deterministic-char2",
                "--devicetruth.embedding.dimension=1024", "--devicetruth.embedding.profile-revision=1",
                "--devicetruth.files.root=" + fileRoot.toAbsolutePath(),
                // 恢复用例自行驱动 service 实例；应用内 worker 关闭，避免异步运行干扰检查点断言
                "--devicetruth.agent.worker-enabled=false");
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
        dataSource = app.getBean(HikariDataSource.class);
        jdbc = new JdbcTemplate(dataSource);
        gateway = app.getBean("agentCapabilityGateway", CapabilityGateway.class);
        runs = new AgentRunRepository(jdbc);
        conversations = new ConversationRepository(jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        importManual();
    }

    @AfterAll static void stop() throws Exception {
        if (app != null) app.close();
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

    private static void importManual() throws Exception {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = new TestRestTemplate().exchange(base + "/api/knowledge/documents", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "title", "充电系统检修手册（演示资料）", "domain", "automotive-demo", "version", "2026.1",
                        "filename", "manual.md", "mimeType", "text/markdown",
                        "content", Files.readString(Path.of(MANUAL), StandardCharsets.UTF_8)), headers), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
    }

    private GeneralAgentService service(GeneralReasoner singleUseProbe) {
        // probe 参数仅用于需要定制推理器的用例；默认 scripted 走完整“检索→追问→作答”链路
        return new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                singleUseProbe == null ? ScriptedAgentReasoner::new : () -> singleUseProbe,
                2, 4, true, null, LEASE_SECONDS, 3);
    }

    private static String awaitTerminal(GeneralAgentService service, UUID runId, String status) throws Exception {
        String current = "";
        for (int attempt = 0; attempt < 300; attempt++) {
            current = service.run(runId).status();
            if (status.equals(current) || "FAILED".equals(current)) return current;
            Thread.sleep(50);
        }
        return fail("run 未到达 " + status + "，当前 " + current);
    }

    private static RunView awaitQuestion(GeneralAgentService service, UUID runId, int version) throws Exception {
        for (int attempt = 0; attempt < 300; attempt++) {
            RunView current = service.run(runId);
            if (current.pendingQuestion() != null && current.pendingQuestion().questionVersion() == version)
                return current;
            if ("FAILED".equals(current.status())) return fail("run 失败：" + current.failureCode());
            Thread.sleep(50);
        }
        return fail("run 未到达问题版本 " + version);
    }

    private static AgentTaskInput input(String text) {
        return new AgentTaskInput(1, text, null, null, null, null, null);
    }

    private static AgentTaskInput replyInput(String text, RunView waiting) {
        var question = waiting.pendingQuestion();
        return new AgentTaskInput(1, text, null, null, null, null,
                Map.of("runId", waiting.runId().toString(), "pendingQuestionId",
                        question.pendingQuestionId().toString(), "questionVersion", question.questionVersion()));
    }

    @Test void waitingRunSurvivesProcessRestartAndContinuesOnSameRun() throws Exception {
        var first = service(null);
        try {
            UUID conversation = first.create().conversationId();
            RunView accepted = first.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            RunView waiting = awaitQuestion(first, accepted.runId(), 1);
            assertThat(first.run(accepted.runId()).status()).isEqualTo("WAITING_FOR_USER");
            assertThat(first.references(accepted.runId())).isNotEmpty();
            int stepsAtWaiting = runs.load(IDENTITY, accepted.runId(), false).stepsUsed();
            assertThat(stepsAtWaiting).isPositive();
            int messagesAtWaiting = first.read(conversation).messages().size();

            // “旧进程消失”：第一个 service 被丢弃，心跳随之中断；新进程从同一 PG 接手
            first.shutdown();
            var second = service(null);
            try {
                second.recoverQueued();
                // WAITING 不持租约，绝不被对账重新调度；问题与版本原样保留
                RunView preserved = second.run(accepted.runId());
                assertThat(preserved.status()).isEqualTo("WAITING_FOR_USER");
                assertThat(preserved.pendingQuestion().questionVersion()).isEqualTo(1);
                assertThat(second.references(accepted.runId())).isNotEmpty();
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_RECOVERED'
                        """, Integer.class, accepted.runId())).isZero();

                // 补答沿同一个 run 继续：预算从上次提交的检查点累计，来源引用不丢。
                // scripted 策略在补答后“检索→作答”，本轮以 COMPLETED 结束。
                RunView acceptedReply = second.submit(conversation, UUID.randomUUID(),
                        replyInput("直流快充，SOC 约 40%", waiting));
                assertThat(acceptedReply.runId()).isEqualTo(accepted.runId());
                awaitTerminal(second, accepted.runId(), "COMPLETED");
                assertThat(second.run(accepted.runId()).result()).isNotNull();
                assertThat(runs.load(IDENTITY, accepted.runId(), false).stepsUsed())
                        .isGreaterThan(stepsAtWaiting);
                assertThat(second.references(accepted.runId())).isNotEmpty();
                assertThat(second.read(conversation).messages().size()).isGreaterThan(messagesAtWaiting);
                // 同一 run 没有被重建：goal 消息仍只有一条
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM dt_message WHERE conversation_id=? AND role='USER' AND run_id IS NULL",
                        Integer.class, conversation)).isEqualTo(1);
            } finally {
                second.shutdown();
            }
        } finally {
            first.shutdown();
        }
    }

    @Test void crashedRunningRunIsReclaimedByNewProcessAndResumesFromCheckpoint() throws Exception {
        var release = new AtomicBoolean(false);
        // 模拟崩溃中的 worker：propose 永不返回，service 实例被整体丢弃（线程随之泄漏为 daemon）
        GeneralReasoner stuck = new GeneralReasoner() {
            @Override public String name() { return "stuck"; }
            @Override public ActionProposal propose(Context context) {
                while (!release.get()) {
                    Thread.interrupted(); // 清掉中断标记，保持 park 阻塞，避免空转
                    LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
                }
                throw new io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure("RELEASED_AFTER_CRASH");
            }
        };
        var crashed = service(stuck);
        UUID runId;
        try {
            UUID conversation = crashed.create().conversationId();
            runId = crashed.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？")).runId();
            // 等待旧 worker 领取 RUNNING（租约在其手中）
            for (int attempt = 0; attempt < 200; attempt++) {
                if ("RUNNING".equals(crashed.run(runId).status())) break;
                Thread.sleep(25);
            }
            assertThat(crashed.run(runId).status()).isEqualTo("RUNNING");
        } finally {
            crashed.shutdown(); // 心跳停止，worker 线程被遗弃：lease 过期后即为崩溃遗留
        }
        // 租约尚未过期时不得回收
        var early = service(null);
        try {
            early.recoverQueued();
            assertThat(early.run(runId).status()).isEqualTo("RUNNING");
        } finally {
            early.shutdown();
        }
        Thread.sleep(LEASE_SECONDS * 1000 + 500);

        // 新进程对账：崩溃 RUNNING 按持久检查点回收并重新调度，scripted 继续检索→追问
        var recovered = service(null);
        try {
            recovered.recoverQueued();
            // 恢复调度是异步的：等到 run 重新到达待答（v1）再做后续断言
            RunView resumed = awaitQuestion(recovered, runId, 1);
            assertThat(resumed.status()).isEqualTo("WAITING_FOR_USER");
            // 尝试次数可审计：崩溃前领取 1 次，恢复后重新领取共 2 次
            assertThat(runs.load(IDENTITY, runId, false).attempts()).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT safe_payload->>'attempts' FROM dt_run_event WHERE run_id=? AND type='RUN_RECOVERED'",
                    String.class, runId)).isEqualTo("1");
            // 同一 run、同一目标消息：恢复没有重建会话或复制目标
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE id=?", Integer.class, runId)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_message WHERE id=(SELECT goal_message_id FROM dt_agent_run WHERE id=?)",
                    Integer.class, runId)).isEqualTo(1);
            // 来源不丢：恢复后检索重新登记了可引用资料
            assertThat(recovered.references(runId)).isNotEmpty();
        } finally {
            recovered.shutdown();
            release.set(true); // 用例结束后再放走泄漏的 worker 线程
        }
    }

    @Test void crashedRunBeyondRecoveryLimitFailsExplicitlyInsteadOfStayingRunning() throws Exception {
        // 直接写入“崩溃遗留”状态：RUNNING 且租约已过期（等价于进程在运行中途消失）
        UUID runId = transactions.execute(status -> {
            var conversation = conversations.create(IDENTITY, Instant.now());
            var messageId = conversations.append(IDENTITY, conversation.conversationId(), UUID.randomUUID(),
                    "充电中断，没有故障码", null, Instant.now(), Instant.now());
            return runs.enqueue(IDENTITY, conversation.conversationId(), messageId, Instant.now(), Instant.now()).runId();
        });
        var record = runs.load(IDENTITY, runId, false);
        assertThat(runs.markRunning(IDENTITY, record, "dead-worker", Instant.now().minusSeconds(1), Instant.now()))
                .isEqualTo(1);
        // 恢复上限 1：第一次崩溃后不再重调度，明确 FAILED
        var service = new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                ScriptedAgentReasoner::new, 1, 0, true, null, LEASE_SECONDS, 1);
        try {
            service.recoverQueued();
            RunView failed = service.run(runId);
            assertThat(failed.status()).isEqualTo("FAILED");
            assertThat(failed.failureCode()).isEqualTo("RECOVERY_ATTEMPT_LIMIT");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_FAILED'"
                            + " AND safe_payload->>'code'='RECOVERY_ATTEMPT_LIMIT'", Integer.class, runId))
                    .isEqualTo(1);
        } finally {
            service.shutdown();
        }
    }

    @Test void concurrentRepliesAdvanceTheQuestionOnlyOnce() throws Exception {
        var service = service(null);
        try {
            UUID conversation = service.create().conversationId();
            RunView accepted = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            RunView waiting = awaitQuestion(service, accepted.runId(), 1);
            int messagesBefore = service.read(conversation).messages().size();

            // 两个线程同时补答同一版本：只有一个能消费问题，另一个必须 409，不能双份推进
            var winner = new java.util.concurrent.atomic.AtomicReference<RunView>();
            var loserError = new java.util.concurrent.atomic.AtomicReference<Exception>();
            var first = new Thread(() -> {
                try {
                    winner.set(service.submit(conversation, UUID.randomUUID(),
                            replyInput("直流快充，SOC 约 40%", waiting)));
                } catch (Exception e) {
                    loserError.set(e);
                }
            });
            var second = new Thread(() -> {
                try {
                    winner.set(service.submit(conversation, UUID.randomUUID(),
                            replyInput("直流快充，SOC 约 40%", waiting)));
                } catch (Exception e) {
                    loserError.set(e);
                }
            });
            first.start();
            second.start();
            first.join(20_000);
            second.join(20_000);
            assertThat(winner.get()).isNotNull();
            assertThat(loserError.get()).isInstanceOf(GeneralAgentService.RequestFailure.class)
                    .hasMessageContaining("STALE_OR_UNRELATED_REPLY");

            // 只消费了一次：补答消息只有一条，且被丢弃的并发补答没有双份推进
            awaitTerminal(service, accepted.runId(), "COMPLETED");
            assertThat(service.run(accepted.runId()).result()).isNotNull();
            assertThat(service.read(conversation).messages().size()).isEqualTo(messagesBefore + 2); // 补答 + 本轮作答
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_message WHERE conversation_id=? AND reply_to_question_id IS NOT NULL",
                    Integer.class, conversation)).isEqualTo(1);
        } finally {
            service.shutdown();
        }
    }

    @Test void staleVersionAndCrossIdentityRepliesAreRejectedAfterRestart() throws Exception {
        var first = service(null);
        UUID conversation;
        RunView waiting;
        try {
            conversation = first.create().conversationId();
            RunView accepted = first.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            waiting = awaitQuestion(first, accepted.runId(), 1);
        } finally {
            first.shutdown();
        }
        var second = service(null);
        try {
            var question = waiting.pendingQuestion();
            // 旧版本补答：版本已被消费/推进后，旧 questionVersion 一律 409
            assertThatThrownBy(() -> second.submit(conversation, UUID.randomUUID(),
                    new AgentTaskInput(1, "旧版本补答", null, null, null, null,
                            Map.of("runId", waiting.runId().toString(), "pendingQuestionId",
                                    question.pendingQuestionId().toString(), "questionVersion", 99))))
                    .isInstanceOf(GeneralAgentService.RequestFailure.class)
                    .hasMessageContaining("STALE_OR_UNRELATED_REPLY");
            // 跨身份补答：不区分“不存在”与“无权”，统一 404，且不写任何消息
            var other = new GeneralAgentService(conversations, runs, transactions,
                    new ServerIdentity("owner-b", "project-a"), gateway, ScriptedAgentReasoner::new, 1, 0, true,
                    null, LEASE_SECONDS, 3);
            try {
                assertThatThrownBy(() -> other.submit(conversation, UUID.randomUUID(),
                        replyInput("越权补答", waiting)))
                        .isInstanceOf(GeneralAgentService.RequestFailure.class)
                        .hasMessageContaining("NOT_FOUND_OR_DENIED");
            } finally {
                other.shutdown();
            }
            RunView unchanged = second.run(waiting.runId());
            assertThat(unchanged.status()).isEqualTo("WAITING_FOR_USER");
            assertThat(unchanged.pendingQuestion().questionVersion()).isEqualTo(1);
            assertThat(second.read(conversation).messages()).hasSize(2); // 目标 + 追问，无补答落库
        } finally {
            second.shutdown();
        }
    }

    @Test void fabricatedCitationAfterRestartIsStillRejected() throws Exception {        var first = service(null);
        UUID conversation;
        RunView waiting;
        try {
            conversation = first.create().conversationId();
            RunView accepted = first.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            waiting = awaitQuestion(first, accepted.runId(), 1);
        } finally {
            first.shutdown();
        }
        // 重启后模型伪造引用：守卫在恢复后的同一 run 上仍然拒绝，答案不落地
        StructuredAgentReasoner.Completion fabricating = (system, context) -> {
            try {
                if (Json.MAPPER.readTree(context).path("references").isEmpty())
                    return "{\"type\":\"CALL_TOOL\",\"toolName\":\"search_knowledge\",\"arguments\":{\"query\":\"充电中断\"}}";
                return "{\"type\":\"ANSWER\",\"category\":\"POSSIBLE_CAUSES\",\"summary\":\"已确诊。\","
                        + "\"claims\":[{\"text\":\"伪造断言\",\"referenceIds\":[\"" + UUID.randomUUID()
                        + "\"],\"certainty\":\"SUPPORTED\"}],\"suggestions\":[],\"uncertainties\":[],"
                        + "\"limitations\":[],\"referenceIds\":[\"" + UUID.randomUUID() + "\"]}";
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        var second = new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                () -> new StructuredAgentReasoner("fabricator", fabricating), 1, 2, true, null, LEASE_SECONDS, 3);
        try {
            second.submit(conversation, UUID.randomUUID(), replyInput("直流快充，SOC 约 40%", waiting));
            awaitTerminal(second, waiting.runId(), "FAILED");
            assertThat(second.run(waiting.runId()).failureCode()).isEqualTo("INVALID_CITATION");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_ANSWER'",
                    Integer.class, waiting.runId())).isZero();
        } finally {
            second.shutdown();
        }
    }

    @Test void crashAfterToolCheckpointResumesWithoutDuplicatingReferencesOrMessages() throws Exception {
        var release = new AtomicBoolean(false);
        // 工具完成后的故障：检索（引用检查点已提交）成功，propose 下一步前进程消失
        GeneralReasoner stuckAfterSearch = new GeneralReasoner() {
            private boolean searched;
            @Override public String name() { return "stuck-after-search"; }
            @Override public ActionProposal propose(Context context) {
                if (!searched) {
                    searched = true;
                    return new ActionProposal.CallTool("search_knowledge",
                            Map.of("query", "充电中断"), "检索已授权手册");
                }
                while (!release.get()) {
                    Thread.interrupted();
                    LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
                }
                throw new io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure("RELEASED_AFTER_CRASH");
            }
        };
        var crashed = service(stuckAfterSearch);
        UUID runId;
        try {
            UUID conversation = crashed.create().conversationId();
            runId = crashed.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？")).runId();
            // 等检索检查点落库（引用可见）且 run 仍卡 RUNNING
            for (int attempt = 0; attempt < 200; attempt++) {
                if (!crashed.references(runId).isEmpty()
                        && "RUNNING".equals(crashed.run(runId).status())) break;
                Thread.sleep(25);
            }
            assertThat(crashed.references(runId)).isNotEmpty();
            assertThat(crashed.run(runId).status()).isEqualTo("RUNNING");
        } finally {
            crashed.shutdown();
        }
        int referencesBefore = jdbc.queryForObject(
                "SELECT count(*) FROM dt_evidence_reference WHERE run_id=?", Integer.class, runId);
        Thread.sleep(LEASE_SECONDS * 1000 + 500);

        // 新进程恢复：重建上下文后引用按 (run_id,chunk_id) 去重，不会因重跑而复制
        var recovered = service(null);
        try {
            recovered.recoverQueued();
            assertThat(awaitTerminal(recovered, runId, "WAITING_FOR_USER")).isEqualTo("WAITING_FOR_USER");
            assertThat(runs.load(IDENTITY, runId, false).attempts()).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_evidence_reference WHERE run_id=?", Integer.class, runId))
                    .isEqualTo(referencesBefore);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_message WHERE id=(SELECT goal_message_id FROM dt_agent_run WHERE id=?)",
                    Integer.class, runId)).isEqualTo(1);
            // 只有一个终态方向：恢复后 run 到达 WAITING，没有遗留 QUEUED/RUNNING 的中间态
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE id=? AND status IN ('QUEUED','RUNNING')",
                    Integer.class, runId)).isZero();
        } finally {
            recovered.shutdown();
            release.set(true);
        }
    }

    @Test void concurrentReconciliationReclaimsCrashedRunOnlyOnce() throws Exception {
        // 直接写入崩溃遗留：RUNNING 且租约过期
        UUID runId = transactions.execute(status -> {
            var conversation = conversations.create(IDENTITY, Instant.now());
            var messageId = conversations.append(IDENTITY, conversation.conversationId(), UUID.randomUUID(),
                    "充电中断，没有故障码", null, Instant.now(), Instant.now());
            return runs.enqueue(IDENTITY, conversation.conversationId(), messageId, Instant.now(), Instant.now()).runId();
        });
        var record = runs.load(IDENTITY, runId, false);
        assertThat(runs.markRunning(IDENTITY, record, "dead-worker", Instant.now().minusSeconds(1), Instant.now()))
                .isEqualTo(1);
        Thread.sleep(LEASE_SECONDS * 1000 + 500);

        // 两个进程同时启动对账：回收 CAS 只允许一个成功，不能双份重新调度
        var first = service(null);
        var second = service(null);
        try {
            var firstThread = new Thread(() -> first.recoverQueued());
            var secondThread = new Thread(() -> second.recoverQueued());
            firstThread.start();
            secondThread.start();
            firstThread.join(20_000);
            secondThread.join(20_000);
            awaitQuestion(first, runId, 1);
            assertThat(runs.load(IDENTITY, runId, false).attempts()).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_RECOVERED'",
                    Integer.class, runId)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE id=? AND status IN ('QUEUED','RUNNING')",
                    Integer.class, runId)).isZero();
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }

    @Test void startupReconciliationLeavesNoOrphanedQueuedRuns() throws Exception {
        // 直接写入 3 条 QUEUED（模拟上一个进程在调度前消失），历史结果也必须可读取
        var runIds = new java.util.ArrayList<UUID>();
        transactions.execute(status -> {
            for (int index = 0; index < 3; index++) {
                var conversation = conversations.create(IDENTITY, Instant.now());
                var messageId = conversations.append(IDENTITY, conversation.conversationId(), UUID.randomUUID(),
                        "充电中断，没有故障码 第" + (index + 1) + "问", null, Instant.now(), Instant.now());
                runIds.add(runs.enqueue(IDENTITY, conversation.conversationId(), messageId,
                        Instant.now(), Instant.now()).runId());
            }
            return null;
        });
        var service = service(null);
        try {
            service.recoverQueued();
            for (UUID runId : runIds) {
                assertThat(awaitTerminal(service, runId, "WAITING_FOR_USER")).isEqualTo("WAITING_FOR_USER");
                // 历史结果可读取：每条 run 的目标消息、待答问题都在
                assertThat(service.run(runId).pendingQuestion()).isNotNull();
            }
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE owner_id='owner-a' AND project_id='project-a'"
                            + " AND status IN ('QUEUED','RUNNING')", Integer.class)).isZero();
        } finally {
            service.shutdown();
        }
    }
}
