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
import io.devicetruth.application.agent.RunRecoveryService;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.Repositories.AssetComponent;
import io.devicetruth.core.capability.Repositories.AssetConfigurationRecord;
import io.devicetruth.core.capability.Repositories.AssetIdentity;
import io.devicetruth.core.capability.Repositories.AssetRegistryRepository;
import io.devicetruth.core.capability.Repositories.AssetSnapshot;
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

    @Test void longAssetReferenceIsIdenticalAfterReplyAndWorkerRestart() throws Exception {
        assertLongAssetReference(false);
    }

    @Test void realModelReadsLongAssetReferenceAfterReplyAndWorkerRestart() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.real-agent"));
        assertLongAssetReference(true);
    }

    @Test void realModelUsesReplyToProposeNewSearch() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.real-agent"));
        var model = io.devicetruth.adapter.AgentModelConnections.openai();
        var first = service(null);
        UUID conversationId;
        RunView waiting;
        try {
            conversationId = first.create().conversationId();
            var accepted = first.submit(conversationId, UUID.randomUUID(), input("新能源汽车充电中断，怎么核对资料？"));
            waiting = awaitQuestion(first, accepted.runId(), 1);
            assertThat(first.toolEvents(accepted.runId())).anyMatch(event ->
                    event.success() && event.toolName().equals("search_knowledge"));
        } finally {
            first.shutdown();
        }
        var newSearch = new AtomicBoolean();
        var resumed = new GeneralReasoner() {
            @Override public String name() { return model.name(); }
            @Override public ActionProposal propose(Context context) {
                assertThat(context.lastUserMessage()).contains("直流快充");
                boolean searched = newSearch.get();
                String goal = searched ? "直接 ANSWER，用 KNOWLEDGE 类别写一句模拟资料说明，所有数组留空。"
                        : "模拟验收，请提议一次 CALL_TOOL，toolName 为 search_knowledge，参数仅 query=直流快充。";
                var guided = new Context(goal,
                        List.of(new io.devicetruth.core.contract.AgentTaskInput.Message("USER",
                                context.lastUserMessage() + "\n" + goal)), context.tools(), context.references(),
                        context.attemptedTools(), context.stepsUsed(), context.maxSteps(), context.feedback(),
                        context.availableSkills(), context.activeSkill(), context.selectedAssetId(), context.asOf(),
                        context.toolResults(), context.conversationHistory());
                var action = model.propose(guided);
                if (action instanceof ActionProposal.CallTool call && call.toolName().equals("search_knowledge")
                        && String.valueOf(call.arguments().get("query")).contains("直流快充")) newSearch.set(true);
                return action;
            }
        };
        var second = service(resumed);
        try {
            long prior = second.toolEvents(waiting.runId()).getLast().sequence();
            second.submit(conversationId, UUID.randomUUID(), replyInput(
                    "现场补充：直流快充。", waiting));
            assertThat(awaitTerminal(second, waiting.runId(), "COMPLETED"))
                    .as(() -> second.run(waiting.runId()).failureCode()).isEqualTo("COMPLETED");
            assertThat(newSearch).isTrue();
            assertThat(second.toolEvents(waiting.runId())).anyMatch(event -> event.sequence() > prior
                    && event.toolName().equals("search_knowledge") && event.success());
        } finally {
            second.shutdown();
        }
    }

    private void assertLongAssetReference(boolean online) throws Exception {
        String asset = "TARGET-A-ASSET";
        String attribute = "moduleVersion" + "X".repeat(110);
        String value = "V".repeat(512);
        Instant date = Instant.parse("2025-06-01T00:00:00Z");
        app.getBean(AssetRegistryRepository.class).importSnapshot(new AssetSnapshot(
                new AssetIdentity(asset, IDENTITY.ownerId(), IDENTITY.projectId()),
                List.of(new AssetComponent(asset, "CTRL", "CTRL")),
                List.of(new AssetConfigurationRecord("target-a-fact", asset, "CTRL", attribute, value,
                        "FACTORY", "fixture-a", "fixture", "SIMULATED", "DOCUMENTED", 1, date, null, date, null)),
                List.of()));
        var call = new ActionProposal.CallTool("get_configuration", Map.of("attribute", attribute), "读取模拟台账");
        var original = new java.util.concurrent.atomic.AtomicReference<io.devicetruth.core.contract.AgentResult.Reference>();
        var scripted = new GeneralReasoner() {
            @Override public String name() { return "target-a-before-restart"; }
            @Override public ActionProposal propose(Context context) {
                if (context.toolResults().isEmpty()) return call;
                assertThat(context.references()).hasSize(1);
                original.set(context.references().getFirst());
                return new ActionProposal.AskUser("现场铭牌是否已经核对？", List.of("铭牌核验情况"));
            }
        };
        UUID conversationId;
        RunView waiting;
        var first = service(scripted);
        try {
            conversationId = first.create().conversationId();
            var accepted = first.submit(conversationId, UUID.randomUUID(), new AgentTaskInput(1,
                    "核对模拟台账与现场铭牌", asset, null, null, null, null));
            waiting = awaitQuestion(first, accepted.runId(), 1);
            assertThat(original.get().excerpt()).hasSizeGreaterThan(600).contains("verification=DOCUMENTED", value);
            assertThat(runs.references(IDENTITY, waiting.runId())).containsExactly(original.get());
        } finally {
            first.shutdown();
        }
        var model = online ? io.devicetruth.adapter.AgentModelConnections.openai() : null;
        var resumed = new GeneralReasoner() {
            @Override public String name() { return "target-a-after-restart"; }
            @Override public ActionProposal propose(Context context) {
                assertThat(context.references()).containsExactly(original.get());
                assertThat(context.lastUserMessage()).contains("未核对");
                return online ? model.propose(context) : answer("铭牌未核对。");
            }
        };
        var second = service(resumed);
        try {
            second.submit(conversationId, UUID.randomUUID(),
                    replyInput("现场铭牌尚未核对。请按已有台账回答并明确只是资料记载，直接 ANSWER。", waiting));
            assertThat(awaitTerminal(second, waiting.runId(), "COMPLETED"))
                    .as(() -> second.run(waiting.runId()).failureCode()).isEqualTo("COMPLETED");
        } finally {
            second.shutdown();
        }
    }

    @Test void nextRunReadsOnlyCompletedHistoryAndDoesNotImportOldReferences() throws Exception {
        var contexts = new java.util.concurrent.LinkedBlockingQueue<GeneralReasoner.Context>();
        var service = service(new GeneralReasoner() {
            @Override public String name() { return "history-probe"; }
            @Override public ActionProposal propose(Context context) {
                if (context.goalText().equals("检查 XLP-200") && !context.attempted("search_knowledge"))
                    return search();
                if (context.goalText().equals("仍在调查"))
                    return new ActionProposal.AskUser("最近更换过控制器吗？", List.of("controller"));
                contexts.add(context);
                return answer(context.goalText().equals("检查 XLP-200") ? "第二种可能是通信异常。" : "请核对维修记录。");
            }
        });
        try {
            UUID conversation = service.create().conversationId();
            var first = service.submit(conversation, UUID.randomUUID(), input("检查 XLP-200"));
            assertThat(awaitTerminal(service, first.runId(), "COMPLETED")).isEqualTo("COMPLETED");
            assertThat(contexts.remove().references()).isNotEmpty();
            var unrelated = service.submit(conversation, UUID.randomUUID(), input("仍在调查"));
            awaitQuestion(service, unrelated.runId(), 1);

            String goal = "第二种原因怎么确认？";
            var second = service.submit(conversation, UUID.randomUUID(), input(goal));
            assertThat(awaitTerminal(service, second.runId(), "COMPLETED")).isEqualTo("COMPLETED");
            var followUp = contexts.remove();
            assertThat(followUp.goalText()).isEqualTo(goal);
            assertThat(followUp.messages()).extracting(io.devicetruth.core.contract.AgentTaskInput.Message::content)
                    .containsExactly(goal);
            assertThat(followUp.conversationHistory())
                    .extracting(io.devicetruth.core.contract.AgentTaskInput.Message::content)
                    .containsExactly("检查 XLP-200", "第二种可能是通信异常。");
            assertThat(followUp.references()).isEmpty();

            var record = runs.load(IDENTITY, second.runId(), false);
            for (var other : List.of(new ServerIdentity("owner-b", "project-a"),
                    new ServerIdentity("owner-a", "project-b"))) {
                assertThat(conversations.recentConversationHistory(other, conversation, record.goalMessageId()))
                        .isEmpty();
                assertThatThrownBy(() -> runs.checkpoint(other, first.runId()))
                        .isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
            }

            UUID another = service.create().conversationId();
            var independent = service.submit(another, UUID.randomUUID(), input(goal));
            assertThat(awaitTerminal(service, independent.runId(), "COMPLETED")).isEqualTo("COMPLETED");
            assertThat(contexts.remove().conversationHistory()).isEmpty();
            assertThat(conversations.recentConversationHistory(IDENTITY, another, record.goalMessageId())).isEmpty();
        } finally {
            service.shutdown();
        }
    }

    @Test void historyIsBoundedByMessageCountAndCodePoints() throws Exception {
        var service = service(new GeneralReasoner() {
            @Override public String name() { return "history-budget"; }
            @Override public ActionProposal propose(Context context) { return answer("答 " + context.goalText()); }
        });
        try {
            UUID conversation = service.create().conversationId();
            for (int index = 1; index <= 8; index++) {
                var run = service.submit(conversation, UUID.randomUUID(), input("目标 " + index));
                assertThat(awaitTerminal(service, run.runId(), "COMPLETED")).isEqualTo("COMPLETED");
            }
            var run = service.submit(conversation, UUID.randomUUID(), input("设备".repeat(3500)));
            assertThat(awaitTerminal(service, run.runId(), "COMPLETED")).isEqualTo("COMPLETED");
            var record = runs.load(IDENTITY, run.runId(), false);
            var shortHistory = conversations.recentConversationHistory(IDENTITY, conversation, record.goalMessageId());
            assertThat(shortHistory).extracting(ConversationRepository.ContextMessage::content).hasSize(12)
                    .startsWith("目标 3", "答 目标 3").endsWith("目标 8", "答 目标 8");
            var afterLongRun = service.submit(conversation, UUID.randomUUID(), input("新的目标"));
            var next = runs.load(IDENTITY, afterLongRun.runId(), false);
            var longHistory = conversations.recentConversationHistory(IDENTITY, conversation, next.goalMessageId());
            assertThat(longHistory).singleElement().satisfies(message -> {
                assertThat(message.content().codePointCount(0, message.content().length())).isEqualTo(6000);
                assertThat(message.role()).isEqualTo("ASSISTANT");
            });
            assertThat(awaitTerminal(service, afterLongRun.runId(), "COMPLETED")).isEqualTo("COMPLETED");
        } finally {
            service.shutdown();
        }
    }

    private static ActionProposal search() {
        return new ActionProposal.CallTool("search_knowledge", Map.of("query", "充电中断"), "查询手册");
    }

    private static ActionProposal answer(String summary) {
        return new ActionProposal.Answer("KNOWLEDGE", summary, List.of(), List.of(), List.of(), List.of(), List.of());
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
            var checkpoint = runs.checkpoint(IDENTITY, accepted.runId());
            assertThat(checkpoint.toolResults()).isNotEmpty();
            assertThat(checkpoint.successfulCalls()).isNotEmpty();
            var resumeContext = new java.util.concurrent.atomic.AtomicReference<GeneralReasoner.Context>();
            var second = service(new GeneralReasoner() {
                @Override public String name() { return "reply-probe"; }
                @Override public ActionProposal propose(Context context) {
                    resumeContext.set(context);
                    return answer("已收到补充信息。");
                }
            });
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
                var restored = resumeContext.get();
                assertThat(restored.goalText()).isEqualTo("新能源汽车充电中断，没有故障码，先查什么资料？");
                assertThat(restored.lastUserMessage()).isEqualTo("直流快充，SOC 约 40%");
                assertThat(restored.askedUser()).isTrue();
                assertThat(restored.stepsUsed()).isEqualTo(stepsAtWaiting);
                assertThat(restored.references()).isNotEmpty();
                assertThat(restored.toolResults()).isEqualTo(checkpoint.toolResults());
                assertThat(restored.attemptedTools()).isEqualTo(checkpoint.attemptedTools());
                second.recoverQueued();
                assertThat(second.events(accepted.runId(), 0).stream().filter(event -> event.type().equals("RUN_ANSWER")))
                        .hasSize(1);
                assertThat(second.read(conversation).messages()).hasSize(messagesAtWaiting + 2);
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
        runs.appendEvent(IDENTITY, runId, runs.load(IDENTITY, runId, false).conversationId(),
                "RUN_MODEL", java.util.Map.of("result", "PRE_CRASH"), java.time.Instant.now(), 1);
        long publishedSeq = jdbc.queryForObject(
                "SELECT max(seq) FROM dt_run_event WHERE run_id=?", Long.class, runId);
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
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND seq=? AND type='RUN_MODEL' AND attempt=1",
                    Integer.class, runId, publishedSeq)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT seq FROM dt_run_event WHERE run_id=? AND type='RUN_RECOVERED'",
                    Long.class, runId)).isGreaterThan(publishedSeq);
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

    @Test void fabricatedCitationAfterRestartIsRejectedAndLimitedResultHasNoForgery() throws Exception {        var first = service(null);
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
        // 重启后模型伪造引用：守卫仍拒绝，最终只交付不含伪造断言的有限结果
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
            awaitTerminal(second, waiting.runId(), "COMPLETED");
            assertThat(second.run(waiting.runId()).failureCode()).isNull();
            assertThat(second.run(waiting.runId()).result().summary()).doesNotContain("已确诊");
            assertThat(second.run(waiting.runId()).result().claims()).isEmpty();
            assertThat(second.run(waiting.runId()).result().limitations()).anyMatch(item -> item.contains("INVALID_CITATION"));
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_ANSWER'",
                    Integer.class, waiting.runId())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_DEGRADED'"
                            + " AND safe_payload->>'code'='INVALID_CITATION'", Integer.class, waiting.runId())).isEqualTo(1);
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
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var countedGateway = gateway.copyToolCatalog();
        var searchSpec = gateway.toolCatalog(null).stream().filter(spec -> spec.name().equals("search_knowledge"))
                .findFirst().orElseThrow();
        var probeGateway = new CapabilityGateway();
        probeGateway.registerTool(searchSpec, (arguments, context) -> {
            calls.incrementAndGet();
            return countedGateway.executeTool(context, searchSpec.name(), arguments);
        });
        var crashed = new GeneralAgentService(conversations, runs, transactions, IDENTITY, probeGateway,
                () -> stuckAfterSearch, 2, 4, true, null, LEASE_SECONDS, 3);
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

        var persisted = runs.checkpoint(IDENTITY, runId);
        assertThat(persisted.step()).isEqualTo(1);
        assertThat(persisted.successfulCalls()).hasSize(1);
        assertThat(persisted.toolResults()).hasSize(1);
        var observed = new java.util.concurrent.LinkedBlockingQueue<GeneralReasoner.Context>();
        GeneralReasoner next = new GeneralReasoner() {
            @Override public String name() { return "recovered-probe"; }
            @Override public ActionProposal propose(Context context) {
                observed.add(context);
                if (observed.size() == 1) return search();
                return new ActionProposal.AskUser("最近更换过控制器吗？", List.of("controller"));
            }
        };
        var recovered = new GeneralAgentService(conversations, runs, transactions, IDENTITY, probeGateway,
                () -> next, 2, 4, true, null, LEASE_SECONDS, 3);
        try {
            recovered.recoverQueued();
            assertThat(awaitTerminal(recovered, runId, "WAITING_FOR_USER")).isEqualTo("WAITING_FOR_USER");
            var context = observed.remove();
            assertThat(context.toolResults()).isEqualTo(persisted.toolResults());
            assertThat(context.attemptedTools()).containsExactly("search_knowledge");
            assertThat(context.stepsUsed()).isGreaterThanOrEqualTo(persisted.step());
            assertThat(observed.remove().feedback()).contains("结果仍可使用");
            assertThat(calls).hasValue(1);
            var events = recovered.events(runId, 0);
            assertThat(events.stream().map(event -> event.seq()).toList()).containsExactly(1L, 2L, 3L, 4L, 5L);
            assertThat(recovered.events(runId, 2)).containsExactlyElementsOf(events.subList(2, events.size()));
            assertThat(recovered.toolEvents(runId)).extracting(AgentRunRepository.ToolEvent::errorCode)
                    .anyMatch("DUPLICATE_CALL"::equals);
            var current = runs.load(IDENTITY, runId, false);
            assertThat(current.attempts()).isEqualTo(2);
            assertThat(current.activeDurationMs()).isPositive();
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

    @Test void expiredWorkerRollsBackToolWritesBeforeCheckpointAndNewWorkerCanRetry() throws Exception {
        var insideTool = new CountDownLatch(1);
        var finishTool = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var probeGateway = new CapabilityGateway();
        var searchSpec = gateway.toolCatalog(null).stream().filter(spec -> spec.name().equals("search_knowledge"))
                .findFirst().orElseThrow();
        probeGateway.registerTool(searchSpec, (arguments, context) -> {
            int call = calls.incrementAndGet();
            var result = gateway.executeTool(context, searchSpec.name(), arguments);
            if (call == 1) {
                insideTool.countDown();
                while (finishTool.getCount() != 0) {
                    Thread.interrupted();
                    LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
                }
            }
            return result;
        });
        var stale = new GeneralAgentService(conversations, runs, transactions, IDENTITY, probeGateway,
                () -> new GeneralReasoner() {
                    @Override public String name() { return "stale-tool"; }
                    @Override public ActionProposal propose(Context context) { return search(); }
                }, 1, 2, true, null, LEASE_SECONDS, 3);
        var resumedContext = new java.util.concurrent.atomic.AtomicReference<GeneralReasoner.Context>();
        var fresh = new GeneralAgentService(conversations, runs, transactions, IDENTITY, probeGateway,
                () -> new GeneralReasoner() {
                    @Override public String name() { return "retry-tool"; }
                    @Override public ActionProposal propose(Context context) {
                        if (!context.attempted("search_knowledge")) {
                            resumedContext.set(context);
                            return search();
                        }
                        return answer("已检查手册。");
                    }
                }, 1, 2, true, null, LEASE_SECONDS, 3);
        try {
            UUID conversation = stale.create().conversationId();
            UUID runId = stale.submit(conversation, UUID.randomUUID(), input("充电中断")).runId();
            assertThat(insideTool.await(10, TimeUnit.SECONDS)).isTrue();
            stale.shutdown();
            assertThat(runs.checkpoint(IDENTITY, runId)).isNull();
            assertThat(stale.references(runId)).isEmpty();
            assertThat(runs.load(IDENTITY, runId, false).stepsUsed()).isEqualTo(1);
            // 新服务先启动，定期扫描应在旧租约过期后回收。
            fresh.recoverQueued();
            for (int poll = 0; poll < 200 && calls.get() < 2; poll++) Thread.sleep(25);
            assertThat(calls).hasValue(2);
            finishTool.countDown();
            assertThat(awaitTerminal(fresh, runId, "COMPLETED")).isEqualTo("COMPLETED");
            assertThat(resumedContext.get().stepsUsed()).isEqualTo(1);
            assertThat(resumedContext.get().toolResults()).isEmpty();
            assertThat(resumedContext.get().references()).isEmpty();
            var end = runs.load(IDENTITY, runId, false);
            assertThat(end.stepsUsed()).isEqualTo(3);
            assertThat(end.attempts()).isEqualTo(2);
            assertThat(end.activeDurationMs()).isGreaterThanOrEqualTo(1000);
            assertThat(runs.checkpoint(IDENTITY, runId).step()).isEqualTo(2);
            assertThat(fresh.toolEvents(runId)).singleElement().satisfies(event -> assertThat(event.success()).isTrue());
            assertThat(fresh.events(runId, 0)).extracting(event -> event.type())
                    .containsExactly("RUN_QUEUED", "RUN_RECOVERED", "RUN_TOOL", "RUN_ANSWER");
        } finally {
            finishTool.countDown();
            stale.shutdown();
            fresh.shutdown();
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

    // ---------- 6.1/6.2（P1-04 修复）：周期有界 QUEUED 补派、进程内去重、异常后继续 ----------

    /** worker 关闭的提交端：只产生 QUEUED，不调度（M0 合同）。 */
    private GeneralAgentService disabledService() {
        return new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                ScriptedAgentReasoner::new, 2, 4, false, null, LEASE_SECONDS, 3);
    }

    @Test void strandedQueuedRunIsDispatchedByPeriodicRecoveryWithoutRestart() throws Exception {
        // worker 关闭的进程提交后 run 滞留 QUEUED；另一个 worker 进程的周期补派在若干租约周期内接手，
        // 全程不重启进程、不再调用 recoverQueued
        var disabled = disabledService();
        var worker = service(null);
        try {
            worker.recoverQueued(); // 此刻没有任何滞留 QUEUED，启动对账为空转
            UUID conversation = disabled.create().conversationId();
            RunView accepted = disabled.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            assertThat(disabled.run(accepted.runId()).status()).isEqualTo("QUEUED");

            assertThat(awaitTerminal(worker, accepted.runId(), "WAITING_FOR_USER")).isEqualTo("WAITING_FOR_USER");
            assertThat(worker.run(accepted.runId()).pendingQuestion()).isNotNull();
        } finally {
            worker.shutdown();
            disabled.shutdown();
        }
    }

    @Test void transientDispatchFailureKeepsRunQueuedAndLaterScanDispatchesIt() throws Exception {
        // 一次临时领取/调度失败（调度器抛异常）：run 保持 QUEUED，下一次周期扫描照常补派，无需重启
        var disabled = disabledService();
        try {
            UUID conversation = disabled.create().conversationId();
            RunView accepted = disabled.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            var recovery = new RunRecoveryService(runs, 3, 2);
            var dispatched = new java.util.concurrent.atomic.AtomicInteger();
            int failed = recovery.reconcileQueued(IDENTITY, runId -> {
                throw new IllegalStateException("temporary claim failure");
            });
            assertThat(failed).isZero();
            assertThat(disabled.run(accepted.runId()).status()).isEqualTo("QUEUED");

            // 下一个周期（同一进程）扫描成功补派
            int retried = recovery.reconcileQueued(IDENTITY, runId -> dispatched.incrementAndGet());
            assertThat(retried).isEqualTo(1);
            assertThat(dispatched).hasValue(1);

            // 补派后由 worker 真正执行：run 可达 WAITING，证明失败没有把它卡死
            var worker = service(null);
            try {
                worker.recoverQueued();
                assertThat(awaitTerminal(worker, accepted.runId(), "WAITING_FOR_USER")).isEqualTo("WAITING_FOR_USER");
            } finally {
                worker.shutdown();
            }
        } finally {
            disabled.shutdown();
        }
    }

    @Test void periodicRecoverySurvivesScanExceptionAndContinuesNextCycle() throws Exception {
        // 真实 PG 故障注入：把 dt_agent_run 临时改名一个周期，周期扫描必然抛异常；
        // 修复点：异常被捕获，scheduleWithFixedDelay 的后续周期不被永久取消
        var disabled = disabledService();
        var worker = service(null);
        try {
            worker.recoverQueued();
            UUID conversation = disabled.create().conversationId();
            RunView accepted = disabled.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            assertThat(disabled.run(accepted.runId()).status()).isEqualTo("QUEUED");

            jdbc.execute("ALTER TABLE dt_agent_run RENAME TO dt_agent_run_sabotage");
            try {
                Thread.sleep(LEASE_SECONDS * 1000 * 2 + 800);
            } finally {
                jdbc.execute("ALTER TABLE dt_agent_run_sabotage RENAME TO dt_agent_run");
            }
            // 表已恢复：无需重启，后续周期自动补派滞留 QUEUED
            assertThat(awaitTerminal(worker, accepted.runId(), "WAITING_FOR_USER")).isEqualTo("WAITING_FOR_USER");
        } finally {
            try {
                jdbc.execute("ALTER TABLE dt_agent_run_sabotage RENAME TO dt_agent_run");
            } catch (RuntimeException ignored) {
                // 表名已恢复或测试中途失败：尽力还原，避免污染其他用例
            }
            worker.shutdown();
            disabled.shutdown();
        }
    }

    @Test void workerDisabledProcessNeverDispatchesQueuedRunsEvenPeriodically() throws Exception {
        // M0 合同：worker 关闭的进程即使启动了 recoverQueued，也只保留 QUEUED，周期任务不调度
        var disabled = disabledService();
        try {
            disabled.recoverQueued();
            UUID conversation = disabled.create().conversationId();
            RunView accepted = disabled.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            Thread.sleep(LEASE_SECONDS * 1000 * 2 + 500);
            assertThat(disabled.run(accepted.runId()).status()).isEqualTo("QUEUED");
        } finally {
            disabled.shutdown();
        }
    }

    @Test void concurrentRecoveryClaimsStrandedRunOnlyOnce() throws Exception {
        // 多服务抢占：两个 worker 进程同时补派同一条滞留 QUEUED，version CAS 只允许一次有效领取
        var disabled = disabledService();
        UUID runId;
        UUID conversation;
        try {
            conversation = disabled.create().conversationId();
            runId = disabled.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？")).runId();
        } finally {
            disabled.shutdown();
        }
        var first = service(null);
        var second = service(null);
        try {
            var firstThread = new Thread(first::recoverQueued);
            var secondThread = new Thread(second::recoverQueued);
            firstThread.start();
            secondThread.start();
            firstThread.join(20_000);
            secondThread.join(20_000);
            awaitQuestion(first, runId, 1);
            assertThat(runs.load(IDENTITY, runId, false).attempts()).isEqualTo(1);
            // 没有双份执行：目标用户消息仍只有一条（提交路径写入的那条）
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_message WHERE conversation_id=? AND role='USER'",
                    Integer.class, conversation)).isEqualTo(1);
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }

    @Test void periodicQueuedScanIsBoundedAndDrainsOverCycles() throws Exception {
        // 有界扫描：扫描上限 2，三个滞留 QUEUED 分两个周期补派，进程内去重后同一 run 不重复入队
        var disabled = disabledService();
        var runIds = new java.util.ArrayList<UUID>();
        try {
            for (int index = 0; index < 3; index++) {
                UUID conversation = disabled.create().conversationId();
                runIds.add(disabled.submit(conversation, UUID.randomUUID(),
                        input("充电中断，没有故障码 第" + (index + 1) + "问")).runId());
            }
            var recovery = new RunRecoveryService(runs, 3, 2);
            // 模拟真实领取：调度成功即由“忙碌 worker”认领，下周期不再出现在 QUEUED 扫描里
            RunRecoveryService.RunDispatcher claimOnly = runId -> {
                var record = runs.load(IDENTITY, runId, false);
                assertThat(runs.markRunning(IDENTITY, record, "probe-worker",
                        Instant.now().plusSeconds(LEASE_SECONDS * 4), Instant.now())).isEqualTo(1);
            };
            assertThat(recovery.reconcileQueued(IDENTITY, claimOnly)).isEqualTo(2);
            assertThat(recovery.reconcileQueued(IDENTITY, claimOnly)).isEqualTo(1);
            assertThat(recovery.reconcileQueued(IDENTITY, claimOnly)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM dt_agent_run WHERE owner_id='owner-a' AND project_id='project-a'"
                            + " AND status='QUEUED'", Integer.class)).isZero();
        } finally {
            // 清理：把被认领的 run 明确置 FAILED，避免遗留 RUNNING 干扰其他用例的对账断言
            for (UUID runId : runIds) {
                var record = runs.load(IDENTITY, runId, false);
                if ("RUNNING".equals(record.status()))
                    runs.fail(IDENTITY, record, "TOTAL_TIMEOUT", record.stepsUsed(), 0, 0, 0, Instant.now());
            }
            disabled.shutdown();
        }
    }

    @Test void periodicDispatchNeverResetsBudget() throws Exception {
        // 预算不重置：滞留 QUEUED 已消耗的步数与累计执行时长，在周期补派执行后保持单调累计
        var disabled = disabledService();
        UUID runId;
        try {
            UUID conversation = disabled.create().conversationId();
            runId = disabled.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？")).runId();
        } finally {
            disabled.shutdown();
        }
        // 制造“执行过半被留下”的遗留：已用 2 步、累计执行 1500ms
        jdbc.update("""
                UPDATE dt_agent_run SET active_duration_ms=1500,
                    budget=jsonb_set(budget,'{used,steps}','2'::jsonb, true)
                WHERE id=? AND owner_id='owner-a' AND project_id='project-a'
                """, runId);

        var worker = service(null);
        try {
            worker.recoverQueued();
            assertThat(awaitTerminal(worker, runId, "WAITING_FOR_USER")).isEqualTo("WAITING_FOR_USER");
            var record = runs.load(IDENTITY, runId, false);
            assertThat(record.stepsUsed()).isGreaterThanOrEqualTo(2);
            assertThat(record.activeDurationMs()).isGreaterThanOrEqualTo(1500);
        } finally {
            worker.shutdown();
        }
    }
}
