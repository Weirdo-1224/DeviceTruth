package io.devicetruth.application;

import com.zaxxer.hikari.HikariDataSource;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * 03-03-T1：幂等、容量、取消与总时限的真实 PG 验证。
 * 覆盖：重复提交不重复执行、队列拒绝不留幽灵任务、QUEUED/WAITING/RUNNING 三态取消、
 * 终态再取消 409、取消后迟到模型响应只记录不提交、累计执行时长超限明确 FAILED/TOTAL_TIMEOUT。
 */
class AgentLifecycleTest {
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
                // 生命周期用例自行驱动 service 实例；应用内 worker 关闭，避免异步运行干扰状态断言
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
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var response = new org.springframework.boot.test.web.client.TestRestTemplate().exchange(
                base + "/api/knowledge/documents", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(Map.of(
                        "title", "充电系统检修手册（演示资料）", "domain", "automotive-demo", "version", "2026.1",
                        "filename", "manual.md", "mimeType", "text/markdown",
                        "content", Files.readString(Path.of(MANUAL), StandardCharsets.UTF_8)), headers), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
    }

    private GeneralAgentService service(int maxConcurrent, int maxQueue, GeneralReasoner probe) {
        return new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                probe == null ? ScriptedAgentReasoner::new : () -> probe, maxConcurrent, maxQueue, true, null,
                LEASE_SECONDS, 3);
    }

    private static String awaitTerminal(GeneralAgentService service, UUID runId, String status) throws Exception {
        String current = "";
        for (int attempt = 0; attempt < 300; attempt++) {
            current = service.run(runId).status();
            if (status.equals(current) || "FAILED".equals(current) || "CANCELLED".equals(current)) return current;
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

    /** 占住 worker 的“卡死模型”：首次调用起保持 park，release 后转交 scripted 正常推进。 */
    private static GeneralReasoner stuckReasoner(AtomicBoolean release) {
        return new GeneralReasoner() {
            private final ScriptedAgentReasoner scripted = new ScriptedAgentReasoner();
            private boolean parked = true;
            @Override public String name() { return "stuck-then-scripted"; }
            @Override public ActionProposal propose(Context context) {
                if (parked) {
                    while (!release.get()) {
                        Thread.interrupted();
                        LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
                    }
                    parked = false;
                }
                return scripted.propose(context);
            }
        };
    }

    @Test void duplicateRequestKeyIsRejectedAndDoesNotDuplicateRun() throws Exception {
        var service = service(1, 1, null);
        try {
            UUID conversation = service.create().conversationId();
            UUID requestKey = UUID.randomUUID();
            // “直流快充”带有关键细节，scripted 检索后直接作答，run 到达 COMPLETED
            RunView accepted = service.submit(conversation, requestKey,
                    input("新能源汽车直流快充中断，没有故障码，先查什么资料？"));
            assertThatThrownBy(() -> service.submit(conversation, requestKey, input("同键重复提交")))
                    .isInstanceOf(GeneralAgentService.RequestFailure.class)
                    .hasMessageContaining("DUPLICATE_REQUEST");
            awaitTerminal(service, accepted.runId(), "COMPLETED");
            // 同一会话只有一个 run、一条目标消息：重复提交没有重复执行任务
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE conversation_id=?", Integer.class, conversation))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_message WHERE conversation_id=? AND role='USER' AND run_id IS NULL",
                    Integer.class, conversation)).isEqualTo(1);
        } finally {
            service.shutdown();
        }
    }

    @Test void queueRejectionMarksFailedWithoutGhostRun() throws Exception {
        var release = new AtomicBoolean(false);
        var busy = service(1, 0, stuckReasoner(release));
        try {
            UUID conversation = busy.create().conversationId();
            UUID firstRun = busy.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？")).runId();
            for (int attempt = 0; attempt < 200 && !"RUNNING".equals(busy.run(firstRun).status()); attempt++)
                Thread.sleep(25);
            assertThat(busy.run(firstRun).status()).isEqualTo("RUNNING");
            // 容量 0：第二个任务被有界队列拒绝，明确 FAILED/QUEUE_REJECTED，绝不永远 QUEUED
            RunView rejected = busy.submit(conversation, UUID.randomUUID(), input("占满后再提交"));
            assertThat(awaitTerminal(busy, rejected.runId(), "FAILED")).isEqualTo("FAILED");
            assertThat(busy.run(rejected.runId()).failureCode()).isEqualTo("QUEUE_REJECTED");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE id=? AND status IN ('QUEUED','RUNNING')",
                    Integer.class, rejected.runId())).isZero();
        } finally {
            release.set(true);
            busy.shutdown();
        }
    }

    @Test void cancelQueuedRunWhileWorkerBusyStaysCancelledAfterRecovery() throws Exception {
        var release = new AtomicBoolean(false);
        var queuedHolder = new java.util.concurrent.atomic.AtomicReference<UUID>();
        var first = service(1, 2, stuckReasoner(release));
        try {
            UUID conversation = first.create().conversationId();
            UUID busyRun = first.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？")).runId();
            for (int attempt = 0; attempt < 200 && !"RUNNING".equals(first.run(busyRun).status()); attempt++)
                Thread.sleep(25);
            assertThat(first.run(busyRun).status()).isEqualTo("RUNNING");
            UUID queuedRun = first.submit(conversation, UUID.randomUUID(), input("排队等待的任务")).runId();
            assertThat(first.run(queuedRun).status()).isEqualTo("QUEUED");
            queuedHolder.set(queuedRun);

            // QUEUED 不持租约：取消立即终态，恢复对账绝不再调度它
            RunView cancelled = first.cancel(queuedRun);
            assertThat(cancelled.status()).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_CANCELLED'",
                    Integer.class, queuedRun)).isEqualTo(1);
        } finally {
            release.set(true);
            first.shutdown();
        }
        var second = service(1, 2, null);
        try {
            second.recoverQueued();
            Thread.sleep(300);
            UUID queuedRun = queuedHolder.get();
            assertThat(second.run(queuedRun).status()).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE id=? AND status IN ('QUEUED','RUNNING')",
                    Integer.class, queuedRun)).isZero();
        } finally {
            second.shutdown();
        }
    }

    @Test void cancelWaitingRunTerminatesAndReplyIsRejected() throws Exception {
        var service = service(1, 1, null);
        try {
            UUID conversation = service.create().conversationId();
            RunView accepted = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            RunView waiting = awaitQuestion(service, accepted.runId(), 1);
            int messagesBefore = service.read(conversation).messages().size();

            RunView cancelled = service.cancel(waiting.runId());
            assertThat(cancelled.status()).isEqualTo("CANCELLED");
            assertThat(cancelled.pendingQuestion()).isNull();
            // 终态后的补答按稳定 409 拒绝，且不落任何消息
            assertThatThrownBy(() -> service.submit(conversation, UUID.randomUUID(),
                    replyInput("直流快充，SOC 约 40%", waiting)))
                    .isInstanceOf(GeneralAgentService.RequestFailure.class)
                    .hasMessageContaining("STALE_OR_UNRELATED_REPLY");
            assertThat(service.read(conversation).messages().size()).isEqualTo(messagesBefore);
            // 再取消返回 409 稳定错误码
            assertThatThrownBy(() -> service.cancel(waiting.runId()))
                    .isInstanceOf(GeneralAgentService.RequestFailure.class)
                    .hasMessageContaining("RUN_ALREADY_TERMINAL");
        } finally {
            service.shutdown();
        }
    }

    @Test void cancelCompletedRunIsRejectedAsTerminal() throws Exception {
        var service = service(1, 1, null);
        try {
            UUID conversation = service.create().conversationId();
            RunView accepted = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车直流快充中断，没有故障码，先查什么资料？"));
            awaitTerminal(service, accepted.runId(), "COMPLETED");
            assertThatThrownBy(() -> service.cancel(accepted.runId()))
                    .isInstanceOf(GeneralAgentService.RequestFailure.class)
                    .hasMessageContaining("RUN_ALREADY_TERMINAL");
            // 终态不被取消改动
            assertThat(service.run(accepted.runId()).status()).isEqualTo("COMPLETED");
        } finally {
            service.shutdown();
        }
    }

    @Test void cancelRunningRunDiscardsLateModelResponse() throws Exception {
        var release = new AtomicBoolean(false);
        var service = service(1, 1, stuckReasoner(release));
        UUID runId;
        try {
            UUID conversation = service.create().conversationId();
            runId = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？")).runId();
            for (int attempt = 0; attempt < 200 && !"RUNNING".equals(service.run(runId).status()); attempt++)
                Thread.sleep(25);
            assertThat(service.run(runId).status()).isEqualTo("RUNNING");
            // RUNNING 只打取消标记，状态仍 RUNNING；worker 收尾后才是 CANCELLED
            assertThat(service.cancel(runId).status()).isEqualTo("RUNNING");
            release.set(true);
            assertThat(awaitTerminal(service, runId, "CANCELLED")).isEqualTo("CANCELLED");
        } finally {
            release.set(true);
            service.shutdown();
        }
        RunView finalView = runs.require(IDENTITY, runId);
        assertThat(finalView.status()).isEqualTo("CANCELLED");
        assertThat(finalView.result()).isNull();
        // 迟到的模型响应只记录不提交：没有答案事件，迟到响应与取消各记一次
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_ANSWER'", Integer.class, runId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_LATE_RESPONSE'",
                Integer.class, runId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_CANCELLED'", Integer.class, runId))
                .isEqualTo(1);
        // 助手消息（追问/答案）不随取消落地：只有用户目标一条
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dt_message WHERE conversation_id=(SELECT conversation_id FROM dt_agent_run WHERE id=?) AND role='ASSISTANT'",
                Integer.class, runId)).isZero();
    }

    @Test void totalActiveDurationTerminatesSlowRunWithoutGhost() throws Exception {
        // 总时限收紧到 100ms：每步耗时 ~250ms 的模型在第二步前被总时限收尾
        var shortRuns = new AgentRunRepository(jdbc, 100);
        GeneralReasoner slow = new GeneralReasoner() {
            @Override public String name() { return "slow-model"; }
            @Override public ActionProposal propose(Context context) {
                LockSupport.parkNanos(Duration.ofMillis(250).toNanos());
                return new ActionProposal.CallTool("search_knowledge", Map.of("query", "充电中断"), "慢速检索");
            }
        };
        var service = new GeneralAgentService(conversations, shortRuns, transactions, IDENTITY, gateway,
                () -> slow, 1, 1, true, null, LEASE_SECONDS, 3);
        try {
            UUID conversation = service.create().conversationId();
            UUID runId = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？")).runId();
            assertThat(awaitTerminal(service, runId, "FAILED")).isEqualTo("FAILED");
            assertThat(service.run(runId).failureCode()).isEqualTo("TOTAL_TIMEOUT");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE id=? AND status IN ('QUEUED','RUNNING')",
                    Integer.class, runId)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_FAILED'"
                            + " AND safe_payload->>'code'='TOTAL_TIMEOUT'", Integer.class, runId)).isEqualTo(1);
            // 超时的 run 不会被启动对账复活
            service.recoverQueued();
            Thread.sleep(200);
            assertThat(service.run(runId).status()).isEqualTo("FAILED");
        } finally {
            service.shutdown();
        }
    }

    @Test void exhaustedQueuedRunFailsOnRecoveryInsteadOfDispatching() throws Exception {
        // 直接写入“执行时长已超限”的遗留 QUEUED（等价于进程消失前已累计超时）：
        // 上限写在行内 budget.limits，对账按行判定，与 repository 默认值无关。
        UUID runId = transactions.execute(status -> {
            var conversation = conversations.create(IDENTITY, Instant.now());
            var messageId = conversations.append(IDENTITY, conversation.conversationId(), UUID.randomUUID(),
                    "充电中断，没有故障码", null, Instant.now(), Instant.now());
            return runs.enqueue(IDENTITY, conversation.conversationId(), messageId, Instant.now(), Instant.now())
                    .runId();
        });
        jdbc.update("""
                UPDATE dt_agent_run SET active_duration_ms=100,
                    budget=jsonb_set(budget,'{limits,maxActiveDurationMs}','100',true)
                WHERE id=?
                """, runId);
        var service = service(1, 1, null);
        try {
            service.recoverQueued();
            assertThat(service.run(runId).status()).isEqualTo("FAILED");
            assertThat(service.run(runId).failureCode()).isEqualTo("TOTAL_TIMEOUT");
        } finally {
            service.shutdown();
        }
    }

    @Test void usageDurationAndToolCountsAreAuditableWithoutFabricatedNumbers() throws Exception {
        var service = service(1, 1, null);
        try {
            UUID conversation = service.create().conversationId();
            UUID runId = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车直流快充中断，没有故障码，先查什么资料？")).runId();
            awaitTerminal(service, runId, "COMPLETED");
            RunView view = service.run(runId);
            assertThat(view.result()).isNotNull();
            // 耗时与用量口径：累计执行时长>0，供应商无 usage 时 UNKNOWN，Token/成本为 null
            assertThat(view.activeDurationMs()).isNotNull().isPositive();
            assertThat(view.usageStatus()).isEqualTo("UNKNOWN");
            // 工具事件可审计：单次耗时、成功标志与引用计数；技术字段不泄漏 Prompt/原文
            var toolEvents = service.toolEvents(runId);
            assertThat(toolEvents).isNotEmpty();
            assertThat(toolEvents).allSatisfy(event -> {
                assertThat(event.durationMs()).isGreaterThanOrEqualTo(0);
                assertThat(event.toolName()).isNotBlank();
            });
            assertThat(toolEvents).anySatisfy(event -> {
                assertThat(event.success()).isTrue();
                assertThat(event.referenceCount()).isPositive();
            });
            // 预算回写与事件一致：steps/toolCalls/modelAttempts 与持久事件对得上，inputTokens 未编造
            var budget = jdbc.queryForMap(
                    "SELECT budget->'used' AS used FROM dt_agent_run WHERE id=?", runId).get("used");
            String used = String.valueOf(budget);
            int runToolEvents = jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_TOOL'", Integer.class, runId);
            assertThat(used).contains("\"toolCalls\": " + runToolEvents);
            assertThat(used).contains("\"modelAttempts\": 0"); // scripted 无模型尝试，记 0 而不是编造
            assertThat(used).contains("\"inputTokens\": null");
            assertThat(used).contains("\"cost\": null");
            assertThat(jdbc.queryForObject(
                    "SELECT (budget->'used'->>'steps')::int FROM dt_agent_run WHERE id=?", Integer.class, runId))
                    .isEqualTo(runs.load(IDENTITY, runId, false).stepsUsed());
            assertThat(jdbc.queryForObject(
                    "SELECT started_at IS NOT NULL AND finished_at IS NOT NULL FROM dt_agent_run WHERE id=?",
                    Boolean.class, runId)).isTrue();
        } finally {
            service.shutdown();
        }
    }

    @Test void modelAttemptsAreRecordedWithBoundedRetryAndDuration() throws Exception {
        // 恒输出非法 JSON 的模型：重试上限 2 次后明确 FAILED，每次尝试的耗时/结果码/版本可审计
        StructuredAgentReasoner.Completion invalid = (system, input) -> "这不是合法 JSON";
        var service = service(1, 1, new StructuredAgentReasoner("probe-model", invalid));
        try {
            UUID conversation = service.create().conversationId();
            UUID runId = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车直流快充中断，没有故障码，先查什么资料？")).runId();
            assertThat(awaitTerminal(service, runId, "FAILED")).isEqualTo("FAILED");
            assertThat(service.run(runId).failureCode()).isEqualTo("MODEL_OUTPUT_INVALID_AFTER_RETRY");
            assertThat(service.run(runId).result()).isNull();
            var modelEvents = service.modelEvents(runId);
            assertThat(modelEvents).hasSize(2); // 有界重试：恰好两次尝试
            assertThat(modelEvents).extracting(AgentRunRepository.ModelEvent::attempt).containsExactly(1, 2);
            assertThat(modelEvents).allSatisfy(event -> {
                assertThat(event.model()).isEqualTo("probe-model");
                assertThat(event.outcome()).isEqualTo("INVALID_JSON_OR_SCHEMA");
                assertThat(event.durationMs()).isGreaterThanOrEqualTo(0);
            });
            // 技术错误不伪装成证据不足：失败事件带稳定码，没有答案事件
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_FAILED'"
                            + " AND safe_payload->>'code'='MODEL_OUTPUT_INVALID_AFTER_RETRY'", Integer.class, runId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_ANSWER'", Integer.class, runId))
                    .isZero();
        } finally {
            service.shutdown();
        }
    }

    @Test void transientToolErrorIsRecordedAndRunStillCompletes() throws Exception {
        // 瞬时工具错误：第一步调未注册工具（失败入审计），第二步如实作答；错误不伪装成成功证据
        GeneralReasoner retrying = new GeneralReasoner() {
            private boolean attemptedUnknown;
            @Override public String name() { return "tool-error-probe"; }
            @Override public ActionProposal propose(Context context) {
                if (!attemptedUnknown) {
                    attemptedUnknown = true;
                    return new ActionProposal.CallTool("not_a_registered_tool", Map.of(), "探测未注册工具");
                }
                return new ActionProposal.Answer("KNOWLEDGE",
                        "已授权资料中没有可引用的依据，无法给出有来源的结论。",
                        List.of(), List.of("补充故障现象后再检索"), List.of("当前资料库缺少直接相关的章节"),
                        List.of("知识库未命中时不得编造文档、页码或结论"), List.of());
            }
        };
        var service = service(1, 1, retrying);
        try {
            UUID conversation = service.create().conversationId();
            UUID runId = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车直流快充中断，没有故障码，先查什么资料？")).runId();
            assertThat(awaitTerminal(service, runId, "COMPLETED")).isEqualTo("COMPLETED");
            var toolEvents = service.toolEvents(runId);
            assertThat(toolEvents).hasSize(1);
            assertThat(toolEvents.getFirst().success()).isFalse();
            assertThat(toolEvents.getFirst().errorCode()).isEqualTo("UNKNOWN_TOOL");
            // 空检索答案没有伪造引用：守卫放行且引用为空
            assertThat(service.run(runId).result().references()).isEmpty();
        } finally {
            service.shutdown();
        }
    }

    @Test void modelTransportFailureIsRecordedAsStableCodeNotEvidenceLack() throws Exception {
        GeneralReasoner failing = new GeneralReasoner() {
            @Override public String name() { return "failing-model"; }
            @Override public ActionProposal propose(Context context) {
                throw new io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure(
                        "MODEL_TRANSPORT_OR_TIMEOUT");
            }
        };
        var service = service(1, 1, failing);
        try {
            UUID conversation = service.create().conversationId();
            UUID runId = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车直流快充中断，没有故障码，先查什么资料？")).runId();
            assertThat(awaitTerminal(service, runId, "FAILED")).isEqualTo("FAILED");
            RunView failed = service.run(runId);
            assertThat(failed.failureCode()).isEqualTo("MODEL_TRANSPORT_OR_TIMEOUT");
            assertThat(failed.result()).isNull();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_FAILED'"
                            + " AND safe_payload->>'code'='MODEL_TRANSPORT_OR_TIMEOUT'", Integer.class, runId))
                    .isEqualTo(1);
        } finally {
            service.shutdown();
        }
    }
}
