package io.devicetruth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.StructuredAgentReasoner;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.GeneralAgentService.AgentTaskInput;
import io.devicetruth.application.agent.GeneralAgentService.RunView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.ToolError;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.core.reasoning.ScriptedAgentReasoner;
import io.devicetruth.core.runtime.TaskOrchestrator;
import io.devicetruth.core.runtime.TaskOrchestrator.AgentRunOutcome;
import io.devicetruth.core.runtime.TaskOrchestrator.AgentStatus;
import io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure;
import io.devicetruth.server.DeviceTruthServerApplication;
import io.devicetruth.server.ServerConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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
 * 02-02-T1：真实 Spring 装配 + 真实 PostgreSQL + pgvector 下的通用 Agent 协议闭环。
 * 这里不 mock KnowledgeSearchService：工具目录来自真实网关注册，检索走真实 SQL，
 * 引用由真实 run 登记；“模型”用确定性补全函数输出合法协议 JSON，用来证明
 * “目录 → 提议 → 网关 → PG 片段 → 引用 → 答案”这条链路，而不是证明语义检索质量。
 */
class GeneralAgentFlowTest {
    @org.junit.jupiter.api.io.TempDir static Path fileRoot;
    private static final String MANUAL = "datasets/knowledge/charging-system-manual.md";
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static ConfigurableApplicationContext app;
    private static TestRestTemplate rest;
    private static String base;
    private static JdbcTemplate jdbc;
    private static HikariDataSource dataSource;
    private static CapabilityGateway gateway;
    private static AgentRunRepository runs;
    private static ConversationRepository conversations;
    private static TransactionTemplate transactions;
    private static io.devicetruth.knowledge.FileUploadService files;
    private static final ServerIdentity IDENTITY = new ServerIdentity("owner-a", "project-a");

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
                // 协议链路由测试直接驱动，worker 关闭以免异步运行并发登记引用
                "--devicetruth.agent.worker-enabled=false");
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
        rest = new TestRestTemplate();
        dataSource = app.getBean(HikariDataSource.class);
        jdbc = new JdbcTemplate(dataSource);
        gateway = app.getBean("agentCapabilityGateway", CapabilityGateway.class);
        runs = new AgentRunRepository(jdbc);
        conversations = new ConversationRepository(jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        files = app.getBean(io.devicetruth.knowledge.FileUploadService.class);
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
        var response = rest.exchange(base + "/api/knowledge/documents", HttpMethod.POST, new HttpEntity<>(Map.of(
                "title", "充电系统检修手册（演示资料）", "domain", "automotive-demo", "version", "2026.1",
                "filename", "manual.md", "mimeType", "text/markdown",
                "content", Files.readString(Path.of(MANUAL), StandardCharsets.UTF_8)), headers), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
    }

    /** 以 QUEUED run 为起点（worker 关闭），返回真实持久化的运行记录。 */
    private AgentRunRepository.RunRecord queuedRun(String text) {
        var conversation = conversations.create(IDENTITY, Instant.now());
        RunView enqueued = transactions.execute(status -> {
            conversations.require(IDENTITY, conversation.conversationId(), true);
            var messageId = conversations.append(IDENTITY, conversation.conversationId(), UUID.randomUUID(), text,
                    null, Instant.now(), Instant.now());
            return runs.enqueue(IDENTITY, conversation.conversationId(), messageId, Instant.now(), Instant.now());
        });
        return runs.load(IDENTITY, enqueued.runId(), false);
    }

    private io.devicetruth.core.contract.AgentTaskInput task(AgentRunRepository.RunRecord run) {
        var messages = conversations.runMessages(IDENTITY, run.runId(), run.goalMessageId()).stream()
                .map(message -> new io.devicetruth.core.contract.AgentTaskInput.Message(message.role(),
                        message.content()))
                .toList();
        return new io.devicetruth.core.contract.AgentTaskInput(IDENTITY.ownerId(), IDENTITY.projectId(),
                run.conversationId(), run.runId(), run.goalMessageId(), run.goalText(), messages,
                run.selectedAssetId(), run.requestedAt(), run.asOf(), run.version(), run.stepsUsed(), run.maxSteps());
    }

    private ToolContext context(AgentRunRepository.RunRecord run) {
        return new ToolContext(IDENTITY.ownerId(), IDENTITY.projectId(), run.conversationId(), run.runId(),
                run.selectedAssetId(), run.requestedAt(), run.asOf(), gateway.toolNames());
    }

    /** 确定性“模型”：从真实上下文 JSON 读工具与引用，输出合法协议动作。 */
    private static StructuredAgentReasoner.Completion model(Function<JsonNode, String> responder) {
        return (system, input) -> {
            try {
                return responder.apply(Json.MAPPER.readTree(input));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private static String searchCall(String query) {
        return "{\"type\":\"CALL_TOOL\",\"toolName\":\"search_knowledge\",\"arguments\":{\"query\":\""
                + query + "\",\"filters\":{\"domain\":\"automotive-demo\"},\"limit\":3},"
                + "\"purpose\":\"检索已授权手册\"}";
    }

    private AgentRunOutcome run(AgentRunRepository.RunRecord run, StructuredAgentReasoner.Completion completion) {
        return new TaskOrchestrator(gateway).runAgent(task(run), gateway, context(run), runs.references(IDENTITY, run.runId()),
                new StructuredAgentReasoner("deterministic-model", completion));
    }

    @Test void registeredCatalogIsExposedToTheModelWithKnowledgeSchemas() {
        var catalog = gateway.toolCatalog(context(queuedRun("目录检查")));
        assertThat(catalog).extracting(ToolSpec::name)
                .contains("search_knowledge", "get_document_excerpt", "get_asset", "get_configuration",
                        "get_maintenance_history", "get_observations", "generate_diagnosis_report");
        assertThat(catalog).allMatch(ToolSpec::readOnly);
        ToolSpec search = catalog.stream().filter(spec -> "search_knowledge".equals(spec.name())).findFirst().orElseThrow();
        assertThat(search.requiredParameters()).containsExactly("query");
        assertThat(search.allowedParameters()).containsExactlyInAnyOrder("query", "filters", "limit");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) search.inputSchema().get("properties");
        assertThat(properties).containsKey("filters");
        // 未授权上下文看不到任何工具，模型不可能调用未授权的工具名
        var denied = new ToolContext(IDENTITY.ownerId(), IDENTITY.projectId(), null, UUID.randomUUID(), null,
                Instant.now(), null, java.util.Set.of());
        assertThat(gateway.toolCatalog(denied)).isEmpty();
    }

    @Test void protocolModelSearchesThroughGatewayAndTheRunRegistersReferences() {
        var run = queuedRun("新能源汽车充电中断，没有故障码，先查什么资料？");
        // 第一次：真正按目录里的工具名提交嵌套参数；第二次：线索不足先追问
        var outcome = run(run, model(context -> context.path("references").isEmpty()
                ? searchCall("充电中断 没有故障码 排查顺序")
                : "{\"type\":\"ASK_USER\",\"question\":\"这次充电中断发生在交流慢充还是直流快充？\","
                        + "\"requestedFields\":[\"充电方式\"]}"));

        assertThat(outcome.status()).isEqualTo(AgentStatus.WAITING_FOR_USER);
        assertThat(outcome.question().question()).contains("交流慢充", "直流快充");
        assertThat(outcome.stepsUsed()).isEqualTo(2);
        assertThat(outcome.events()).extracting(TaskOrchestrator.RunEvent::type)
                .contains("TOOL_RESULT", "QUESTION_ASKED");

        // 引用确实写进了 PG，并带文档版本与章节定位，且属于本 run 的归属
        var stored = runs.references(IDENTITY, run.runId());
        assertThat(stored).isNotEmpty();
        assertThat(stored).allSatisfy(reference -> {
            assertThat(reference.title()).contains("充电系统检修手册");
            assertThat(reference.sourceVersion()).isEqualTo("2026.1");
            assertThat(reference.sourceType()).isEqualTo("KNOWLEDGE_DOCUMENT");
            assertThat(reference.dataNature()).isEqualTo("SIMULATED");
            assertThat(reference.section()).isNotBlank();
            assertThat(reference.excerpt()).isNotBlank();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_evidence_reference WHERE run_id=? AND owner_id='owner-a'",
                Integer.class, run.runId())).isPositive();
        assertThat(outcome.references()).hasSameSizeAs(stored).allMatch(reference -> reference.chunkId() != null);
    }

    @Test void answerCitesOnlyReferencesCollectedByTheSameRun() {
        var run = queuedRun("直流快充中断时应该先看哪些资料？");
        var outcome = run(run, model(context -> {
            var references = context.path("references");
            if (references.isEmpty()) return searchCall("直流快充 中断 充电桩 日志");
            String referenceId = references.get(0).path("referenceId").asText();
            return "{\"type\":\"ANSWER\",\"category\":\"POSSIBLE_CAUSES\","
                    + "\"summary\":\"资料建议先读取充电桩故障日志，再确认中止方是桩侧还是车侧。\","
                    + "\"claims\":[{\"text\":\"先读取充电桩故障日志\",\"referenceIds\":[\"" + referenceId
                    + "\"],\"certainty\":\"SUPPORTED\"}],\"suggestions\":[\"记录中断时间与SOC\"],"
                    + "\"uncertainties\":[\"资料版本需按车型核对\"],\"limitations\":[\"不代表本车当前配置\"],"
                    + "\"referenceIds\":[\"" + referenceId + "\"]}";
        }));

        assertThat(outcome.status()).isEqualTo(AgentStatus.COMPLETED);
        var answer = outcome.answer();
        assertThat(answer.category()).isEqualTo(io.devicetruth.core.contract.AgentResult.Category.POSSIBLE_CAUSES);
        assertThat(answer.riskLevel()).isEqualTo(io.devicetruth.core.contract.AgentResult.RiskLevel.MEDIUM);
        assertThat(answer.references()).isNotEmpty();
        assertThat(answer.referenceIds()).allMatch(id -> runs.references(IDENTITY, run.runId()).stream()
                .anyMatch(reference -> reference.referenceId().equals(id)));
        assertThat(answer.dataNature()).isEqualTo("SIMULATED");
        assertThat(answer.summary()).doesNotContain("已确诊");
    }

    @Test void fabricatedCitationIsRejectedAndNeverStoredAsAnAnswer() {
        var run = queuedRun("充电中断是否需要更换充电控制器？");
        var outcome = run(run, model(context -> "{\"type\":\"ANSWER\",\"category\":\"CONFIRMED_FINDING\","
                + "\"summary\":\"已确诊为充电控制器损坏，可以直接更换。\","
                + "\"claims\":[{\"text\":\"控制器损坏\",\"referenceIds\":[\"" + UUID.randomUUID()
                + "\"],\"certainty\":\"CONFIRMED\"}],\"suggestions\":[],\"uncertainties\":[],\"limitations\":[],"
                + "\"referenceIds\":[\"" + UUID.randomUUID() + "\"]}"));

        assertThat(outcome.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(outcome.stopReason()).isEqualTo("INVALID_CITATION");
        assertThat(outcome.answer()).isNull();
        // 有界修正：第一次拒绝后要求重答，第二次仍伪造才失败
        long rejected = outcome.events().stream().filter(event -> "ANSWER_REJECTED".equals(event.type())).count();
        assertThat(rejected).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_ANSWER'",
                Integer.class, run.runId())).isZero();
    }

    @Test void unknownToolIsRejectedWithoutExecutingAndWithoutLeakingData() {
        var run = queuedRun("用不存在的工具检索资料");
        var outcome = run(run, model(context -> context.path("stepsUsed").asInt() == 0
                ? "{\"type\":\"CALL_TOOL\",\"toolName\":\"search_web\",\"arguments\":{\"query\":\"充电中断\"},"
                        + "\"purpose\":\"越权尝试\"}"
                : "{\"type\":\"ANSWER\",\"category\":\"KNOWLEDGE\",\"summary\":\"没有可引用的资料。\","
                        + "\"claims\":[],\"suggestions\":[\"补充资料后再检索\"],\"uncertainties\":[],"
                        + "\"limitations\":[\"未检索到资料\"],\"referenceIds\":[]}"));

        assertThat(outcome.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(outcome.answer().references()).isEmpty();
        var toolEvent = outcome.events().stream().filter(event -> "TOOL_RESULT".equals(event.type())).findFirst()
                .orElseThrow();
        assertThat(toolEvent.data()).containsEntry("success", false).containsEntry("errorCode", "UNKNOWN_TOOL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_evidence_reference WHERE run_id=?", Integer.class,
                run.runId())).isZero();
    }

    @Test void nestedArgumentsAndAuthorizationAreEnforcedByTheGateway() {
        var run = queuedRun("嵌套参数检查");
        var context = context(run);
        // 嵌套 filters 的类型错误由强类型解析拒绝，而不是被当成合法查询
        ToolOutcome badFilters = gateway.executeTool(context, "search_knowledge",
                Map.of("query", "充电中断", "filters", Map.of("domain", List.of("a"))));
        assertThat(badFilters.success()).isFalse();
        assertThat(badFilters.error().code()).isEqualTo("INVALID_ARGUMENTS");
        // 顶层未知参数直接拒绝
        assertThat(gateway.executeTool(context, "search_knowledge",
                Map.of("query", "充电中断", "ownerId", "owner-b")).error().code()).isEqualTo("INVALID_ARGUMENTS");
        // 未授权上下文调用已注册工具同样拒绝，且不产生引用
        var denied = new ToolContext(IDENTITY.ownerId(), IDENTITY.projectId(), run.conversationId(), run.runId(),
                null, run.requestedAt(), run.asOf(), java.util.Set.of());
        assertThat(gateway.executeTool(denied, "search_knowledge", Map.of("query", "充电中断")).error().code())
                .isEqualTo("TOOL_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_evidence_reference WHERE run_id=?", Integer.class,
                run.runId())).isZero();
    }

    @Test void modelFailureEndsRunAsFailedWithoutAnswer() {
        var run = queuedRun("模型失败检查");
        var outcome = run(run, (system, input) -> {
            throw new ReasonerFailure("MODEL_TRANSPORT_OR_TIMEOUT");
        });
        assertThat(outcome.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(outcome.stopReason()).isEqualTo("MODEL_TRANSPORT_OR_TIMEOUT");
        assertThat(outcome.answer()).isNull();
        assertThat(outcome.events()).extracting(TaskOrchestrator.RunEvent::type)
                .contains("MODEL_ATTEMPT", "RUN_FAILED");
    }

    @Test void rejectedDispatchFailsTheRunInsteadOfLeavingItQueued() throws Exception {
        var identity = new ServerIdentity("owner-a", "project-a");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        GeneralReasoner blocking = new GeneralReasoner() {
            @Override public String name() { return "blocking"; }
            @Override public ActionProposal propose(Context context) {
                entered.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new ReasonerFailure("BLOCKED_FOR_TEST");
            }
        };
        var service = new GeneralAgentService(conversations, runs, transactions, identity, gateway,
                () -> blocking, 1, 0, true);
        try {
            var conversation = service.create().conversationId();
            RunView first = service.submit(conversation, UUID.randomUUID(), input("第一条消息占满唯一 worker"));
            assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
            // 队列容量 0：第二次调度被拒，必须立即 FAILED/QUEUE_REJECTED，不能永远 QUEUED
            RunView rejected = service.submit(conversation, UUID.randomUUID(), input("第二条消息应被拒绝调度"));
            var saved = service.run(rejected.runId());
            assertThat(saved.status()).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_FAILED'
                      AND safe_payload->>'code'='QUEUE_REJECTED'
                    """, Integer.class, rejected.runId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_message WHERE conversation_id=? AND role='ASSISTANT'",
                    Integer.class, conversation)).isZero();
            // 被占用 worker 的那条也必须以 FAILED 结束，而不是停在 RUNNING
            release.countDown();
            String status = "";
            for (int attempt = 0; attempt < 100 && !"FAILED".equals(status); attempt++) {
                status = service.run(first.runId()).status();
                if (!"FAILED".equals(status)) Thread.sleep(100);
            }
            assertThat(status).isEqualTo("FAILED");
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    @Test void waitingForUserReleasesTheWorkerSlotForOtherRuns() throws Exception {
        var identity = new ServerIdentity("owner-a", "project-a");
        // 单 worker + 队列容量 0：只要等待态仍占用线程，第二次提交就会被拒
        var service = new GeneralAgentService(conversations, runs, transactions, identity, gateway,
                ScriptedAgentReasoner::new, 1, 0, true);
        try {
            var conversation = service.create().conversationId();
            RunView first = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            String firstStatus = awaitStatus(service, first.runId());
            assertThat(firstStatus).isEqualTo("WAITING_FOR_USER");
            assertThat(service.run(first.runId()).pendingQuestion()).isNotNull();

            // 线程已释放：第二个任务能立即被接手，而不是 QUEUE_REJECTED
            RunView second = service.submit(conversation, UUID.randomUUID(),
                    input("交流慢充无法启动，先看哪些资料？"));
            assertThat(awaitStatus(service, second.runId())).isIn("WAITING_FOR_USER", "COMPLETED");
            // 第一个 run 的问题没有被覆盖，仍然是同一条待答问题
            assertThat(service.run(first.runId()).status()).isEqualTo("WAITING_FOR_USER");
        } finally {
            service.shutdown();
        }
    }

    private static String awaitStatus(GeneralAgentService service, UUID runId) throws Exception {
        String status = "";
        for (int attempt = 0; attempt < 200; attempt++) {
            status = service.run(runId).status();
            if ("WAITING_FOR_USER".equals(status) || "COMPLETED".equals(status) || "FAILED".equals(status)) return status;
            Thread.sleep(100);
        }
        return status;
    }

    @Test void unverifiedConfirmedFindingIsRefusedAndCorrectedToPossibleCauses() {
        var run = queuedRun("直流快充中断，是否需要直接更换充电控制器？");
        var outcome = run(run, model(context -> {
            var references = context.path("references");
            if (references.isEmpty()) return searchCall("直流快充 中断 充电桩 日志");
            String referenceId = references.get(0).path("referenceId").asText();
            if (context.path("feedback").isNull())
                return "{\"type\":\"ANSWER\",\"category\":\"CONFIRMED_FINDING\","
                        + "\"summary\":\"已确诊为充电控制器损坏，可以直接更换。\","
                        + "\"claims\":[{\"text\":\"充电控制器损坏\",\"referenceIds\":[\"" + referenceId
                        + "\"],\"certainty\":\"CONFIRMED\"}],\"suggestions\":[\"更换充电控制器\"],"
                        + "\"uncertainties\":[],\"limitations\":[],\"referenceIds\":[\"" + referenceId + "\"]}";
            // 被拒绝后按协议改成“可能原因”，不再声称确诊
            return "{\"type\":\"ANSWER\",\"category\":\"POSSIBLE_CAUSES\","
                    + "\"summary\":\"资料列出的常见原因是通信超时与温度限值，不能据此确诊。\","
                    + "\"claims\":[{\"text\":\"通信超时是资料记载的常见原因\",\"referenceIds\":[\"" + referenceId
                    + "\"],\"certainty\":\"SUPPORTED\"}],\"suggestions\":[\"读取充电桩故障日志\"],"
                    + "\"uncertainties\":[\"需要现场数据才能判断中止方\"],\"limitations\":[\"缺少现场核验\"],"
                    + "\"referenceIds\":[\"" + referenceId + "\"]}";
        }));

        assertThat(outcome.status()).isEqualTo(AgentStatus.COMPLETED);
        var answer = outcome.answer();
        assertThat(answer.category()).isEqualTo(io.devicetruth.core.contract.AgentResult.Category.POSSIBLE_CAUSES);
        assertThat(answer.claims()).noneMatch(claim ->
                claim.certainty() == io.devicetruth.core.contract.AgentResult.Certainty.CONFIRMED);
        assertThat(answer.summary()).doesNotContain("已确诊");
        assertThat(outcome.events()).anyMatch(event -> "ANSWER_REJECTED".equals(event.type())
                && "UNCONFIRMED_FINDING".equals(String.valueOf(event.data().get("code"))));
    }

    @Test void insistingOnConfirmedFindingFailsTheRun() {
        var run = queuedRun("充电中断是否需要更换控制器？");
        var outcome = run(run, model(context -> {
            var references = context.path("references");
            if (references.isEmpty()) return searchCall("充电中断 控制器 更换");
            String referenceId = references.get(0).path("referenceId").asText();
            return "{\"type\":\"ANSWER\",\"category\":\"CONFIRMED_FINDING\",\"summary\":\"已确诊为控制器损坏。\","
                    + "\"claims\":[{\"text\":\"控制器损坏\",\"referenceIds\":[\"" + referenceId
                    + "\"],\"certainty\":\"CONFIRMED\"}],\"suggestions\":[],\"uncertainties\":[],"
                    + "\"limitations\":[],\"referenceIds\":[\"" + referenceId + "\"]}";
        }));

        assertThat(outcome.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(outcome.stopReason()).isEqualTo("UNCONFIRMED_FINDING");
        assertThat(outcome.answer()).isNull();
    }

    @Test void dangerousControlAdviceIsRefusedEvenWhenTheModelInsists() {
        var run = queuedRun("充电中断怎样快速恢复充电？");
        var outcome = run(run, model(context -> {
            var references = context.path("references");
            if (references.isEmpty()) return searchCall("充电中断 恢复充电");
            String referenceId = references.get(0).path("referenceId").asText();
            return "{\"type\":\"ANSWER\",\"category\":\"KNOWLEDGE\","
                    + "\"summary\":\"将高压互锁端子短接即可继续充电。\","
                    + "\"claims\":[{\"text\":\"将高压互锁端子短接\",\"referenceIds\":[\"" + referenceId
                    + "\"],\"certainty\":\"SUPPORTED\"}],\"suggestions\":[\"先上电再观察\"],"
                    + "\"uncertainties\":[],\"limitations\":[],\"referenceIds\":[\"" + referenceId + "\"]}";
        }));

        assertThat(outcome.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(outcome.stopReason()).isEqualTo("UNSAFE_ANSWER");
        assertThat(outcome.answer()).isNull();
    }

    @Test void deviceDefectAssertionWithoutFieldEvidenceIsRefusedAndCorrectable() {
        var run = queuedRun("这辆车的充电中断是什么原因？");
        var outcome = run(run, model(context -> {
            var references = context.path("references");
            if (references.isEmpty()) return searchCall("充电中断 原因 控制器");
            String referenceId = references.get(0).path("referenceId").asText();
            if (context.path("feedback").isNull())
                // 没有“确诊”字样，但把资料当成眼前车辆的现场结论：守卫必须拒绝
                return "{\"type\":\"ANSWER\",\"category\":\"KNOWLEDGE\","
                        + "\"summary\":\"该车辆的故障原因是充电控制器损坏。\","
                        + "\"claims\":[{\"text\":\"充电控制器损坏\",\"referenceIds\":[\"" + referenceId
                        + "\"],\"certainty\":\"SUPPORTED\"}],\"suggestions\":[],"
                        + "\"uncertainties\":[],\"limitations\":[],\"referenceIds\":[\"" + referenceId + "\"]}";
            // 修正为带引用的可能原因：引用与限制保留，正常完成
            return "{\"type\":\"ANSWER\",\"category\":\"POSSIBLE_CAUSES\","
                    + "\"summary\":\"控制器异常可能导致充电中断，尚未确诊。\","
                    + "\"claims\":[{\"text\":\"控制器异常可能导致中断\",\"referenceIds\":[\"" + referenceId
                    + "\"],\"certainty\":\"POSSIBLE\"}],\"suggestions\":[\"读取充电桩故障日志\"],"
                    + "\"uncertainties\":[\"需要现场数据才能确认\"],\"limitations\":[\"缺少现场核验\"],"
                    + "\"referenceIds\":[\"" + referenceId + "\"]}";
        }));

        assertThat(outcome.status()).isEqualTo(AgentStatus.COMPLETED);
        var answer = outcome.answer();
        assertThat(answer.category()).isEqualTo(io.devicetruth.core.contract.AgentResult.Category.POSSIBLE_CAUSES);
        // 最终返回内容不得把资料结论包装成当前车辆的确定故障
        assertThat(answer.summary()).doesNotContain("该车辆").doesNotContain("故障原因");
        assertThat(answer.claims()).allMatch(claim -> claim.certainty()
                == io.devicetruth.core.contract.AgentResult.Certainty.POSSIBLE);
        assertThat(answer.referenceIds()).isNotEmpty();
        assertThat(answer.limitations()).anyMatch(text -> text.contains("缺少现场核验"));
        assertThat(outcome.events()).anyMatch(event -> "ANSWER_REJECTED".equals(event.type())
                && "UNCONFIRMED_FINDING".equals(String.valueOf(event.data().get("code"))));
    }

    @Test void insistingOnDeviceDefectAssertionFailsTheRun() {
        var run = queuedRun("这台设备的充电故障原因是什么？");
        var outcome = run(run, model(context -> {
            var references = context.path("references");
            if (references.isEmpty()) return searchCall("充电 故障 控制器");
            String referenceId = references.get(0).path("referenceId").asText();
            return "{\"type\":\"ANSWER\",\"category\":\"POSSIBLE_CAUSES\","
                    + "\"summary\":\"该设备的故障原因是充电控制器损坏。\","
                    + "\"claims\":[{\"text\":\"控制器损坏\",\"referenceIds\":[\"" + referenceId
                    + "\"],\"certainty\":\"SUPPORTED\"}],\"suggestions\":[],"
                    + "\"uncertainties\":[],\"limitations\":[],\"referenceIds\":[\"" + referenceId + "\"]}";
        }));

        assertThat(outcome.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(outcome.stopReason()).isEqualTo("UNCONFIRMED_FINDING");
        assertThat(outcome.answer()).isNull();
    }

    @Test void concurrentRunsKeepUniqueMessageSequenceAndTerminalState() throws Exception {
        var identity = new ServerIdentity("owner-a", "project-a");
        var service = new GeneralAgentService(conversations, runs, transactions, identity, gateway,
                ScriptedAgentReasoner::new, 2, 8, true);
        try {
            var conversation = service.create().conversationId();
            var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> service.submit(conversation,
                    UUID.randomUUID(), input("充电中断，没有故障码，先查什么资料？")));
            var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> service.submit(conversation,
                    UUID.randomUUID(), input("交流慢充无法启动，先查哪些资料？")));
            RunView a = first.get(30, TimeUnit.SECONDS);
            RunView b = second.get(30, TimeUnit.SECONDS);
            assertThat(awaitStatus(service, a.runId())).isIn("WAITING_FOR_USER", "COMPLETED");
            assertThat(awaitStatus(service, b.runId())).isIn("WAITING_FOR_USER", "COMPLETED");

            // 消息序号由会话行锁分配：并发写入后仍然连续、唯一、无空洞
            var detail = service.read(conversation);
            var seqs = detail.messages().stream().map(message -> message.seq()).toList();
            assertThat(seqs).hasSize(4).doesNotHaveDuplicates().containsExactly(1L, 2L, 3L, 4L);
            assertThat(detail.messages()).allMatch(message -> !message.content().isBlank());
            assertThat(service.read(conversation).runs()).allMatch(run ->
                    !"RUNNING".equals(run.status()) && !"QUEUED".equals(run.status()));
        } finally {
            service.shutdown();
        }
    }

    @Test void persistenceFailureMarksTheRunFailedInsteadOfLeavingItRunning() throws Exception {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_assistant_message() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.role = 'ASSISTANT' THEN RAISE EXCEPTION 'private-test-persist-failure'; END IF;
                      RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_assistant_message BEFORE INSERT ON dt_message "
                + "FOR EACH ROW EXECUTE FUNCTION fail_assistant_message()");
        var identity = new ServerIdentity("owner-a", "project-a");
        var service = new GeneralAgentService(conversations, runs, transactions, identity, gateway,
                ScriptedAgentReasoner::new, 1, 2, true);
        try {
            var conversation = service.create().conversationId();
            RunView run = service.submit(conversation, UUID.randomUUID(), input("充电中断，没有故障码，先查资料"));
            assertThat(awaitStatus(service, run.runId())).isEqualTo("FAILED");
            // 落库失败必须变成可观察的失败，不能停留在 RUNNING
            assertThat(service.run(run.runId()).failureCode()).isEqualTo("PERSIST_FAILURE");
            assertThat(jdbc.queryForObject("SELECT status FROM dt_agent_run WHERE id=?", String.class, run.runId()))
                    .isEqualTo("FAILED");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_assistant_message ON dt_message");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_assistant_message()");
            service.shutdown();
        }
    }

    @Test void replyAttachmentsStayInMessageOrderAndCanBeSearched() throws Exception {
        var firstFile = uploaded("补答附件甲", "REPLY-A-731");
        var secondFile = uploaded("补答附件乙", "REPLY-B-947");
        var contexts = new java.util.concurrent.CopyOnWriteArrayList<JsonNode>();
        var service = new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                () -> new StructuredAgentReasoner("attachment-capture", model(context -> {
                    contexts.add(context);
                    int userCount = userCount(context);
                    if (userCount == 1) return ask("请提供第一份补答资料");
                    if (userCount == 2) {
                        if (context.path("attemptedTools").isEmpty()) return searchCall("REPLY-A-731");
                        return ask("请提供第二份补答资料");
                    }
                    if (context.path("toolResults").size() == 1) return searchCall("REPLY-B-947");
                    return answerWith(referenceFor(context, secondFile.documentId()));
                })), 1, 2, true, files);
        try {
            UUID conversation = service.create().conversationId();
            RunView accepted = service.submit(conversation, UUID.randomUUID(), input("首轮没有附件，请追问"));
            RunView waiting = awaitQuestion(service, accepted.runId(), 1);
            service.submit(conversation, UUID.randomUUID(), replyInput("第一份补答", firstFile.fileId(), waiting));
            RunView waitingAgain = awaitQuestion(service, accepted.runId(), 2);
            service.submit(conversation, UUID.randomUUID(), replyInput("第二份补答", secondFile.fileId(), waitingAgain));
            RunView completed = awaitTerminal(service, accepted.runId(), "COMPLETED");

            JsonNode firstReply = contextWithUserCount(contexts, 2);
            assertThat(firstReply.path("messages")).hasSize(3);
            assertThat(firstReply.path("messages").get(0).path("attachments")).isEmpty();
            assertAttachment(firstReply.path("messages").get(2), firstFile);
            JsonNode secondReply = contextWithUserCount(contexts, 3);
            assertThat(secondReply.path("messages")).hasSize(5);
            assertAttachment(secondReply.path("messages").get(2), firstFile);
            assertAttachment(secondReply.path("messages").get(4), secondFile);
            assertThat(secondReply.path("messages").get(4).path("content").asText())
                    .isEqualTo("第二份补答").doesNotContain(secondFile.documentId().toString());
            assertThat(service.references(accepted.runId())).anyMatch(reference ->
                    reference.sourceId().equals(firstFile.documentId().toString()));
            assertThat(completed.result().references()).anyMatch(reference ->
                    reference.sourceId().equals(secondFile.documentId().toString()));
        } finally {
            service.shutdown();
        }
    }

    @Test void goalAndReplyAttachmentsKeepTheirAssociationAndCrossScopeReplyWritesNothing() throws Exception {
        var goalFile = uploaded("目标附件", "GOAL-A-520");
        var replyFile = uploaded("补答附件", "REPLY-C-820");
        var contexts = new java.util.concurrent.CopyOnWriteArrayList<JsonNode>();
        var service = new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                () -> new StructuredAgentReasoner("attachment-capture", model(context -> {
                    contexts.add(context);
                    if (userCount(context) == 1) {
                        if (context.path("attemptedTools").isEmpty()) return searchCall("GOAL-A-520");
                        return ask("请补充附件");
                    }
                    if (context.path("toolResults").size() == 1) return searchCall("REPLY-C-820");
                    return answerWith(referenceFor(context, replyFile.documentId()));
                })), 1, 2, true, files);
        var otherOwner = new GeneralAgentService(conversations, runs, transactions,
                new ServerIdentity("owner-b", "project-a"), gateway, ScriptedAgentReasoner::new,
                1, 0, false, files);
        var otherProject = new GeneralAgentService(conversations, runs, transactions,
                new ServerIdentity("owner-a", "project-b"), gateway, ScriptedAgentReasoner::new,
                1, 0, false, files);
        try {
            UUID conversation = service.create().conversationId();
            RunView accepted = service.submit(conversation, UUID.randomUUID(), input("目标消息带附件", goalFile.fileId()));
            RunView waiting = awaitQuestion(service, accepted.runId(), 1);
            int messagesBefore = service.read(conversation).messages().size();
            int runsBefore = service.read(conversation).runs().size();
            assertThatThrownBy(() -> otherOwner.submit(conversation, UUID.randomUUID(),
                    replyInput("跨 owner 补答", replyFile.fileId(), waiting)))
                    .hasMessageContaining("NOT_FOUND_OR_DENIED");
            assertThatThrownBy(() -> otherProject.submit(conversation, UUID.randomUUID(),
                    replyInput("跨 project 补答", replyFile.fileId(), waiting)))
                    .hasMessageContaining("NOT_FOUND_OR_DENIED");
            assertThat(service.read(conversation).messages()).hasSize(messagesBefore);
            assertThat(service.read(conversation).runs()).hasSize(runsBefore);
            assertThat(service.run(accepted.runId()).status()).isEqualTo("WAITING_FOR_USER");

            service.submit(conversation, UUID.randomUUID(), replyInput("正常补答", replyFile.fileId(), waiting));
            awaitTerminal(service, accepted.runId(), "COMPLETED");
            JsonNode reply = contextWithUserCount(contexts, 2);
            assertAttachment(reply.path("messages").get(0), goalFile);
            assertAttachment(reply.path("messages").get(2), replyFile);
        } finally {
            otherProject.shutdown();
            otherOwner.shutdown();
            service.shutdown();
        }
    }

    private io.devicetruth.knowledge.FileUploadService.FileView uploaded(String title, String marker) throws Exception {
        String content = "# " + title + "\n\n" + marker + " 的排查必须记录桩侧日志。\n";
        var accepted = files.upload(IDENTITY.ownerId(), IDENTITY.projectId(),
                new org.springframework.mock.web.MockMultipartFile("file", marker + ".md", "text/markdown",
                        content.getBytes(StandardCharsets.UTF_8)),
                new io.devicetruth.knowledge.FileUploadService.ImportMetadata(title, "automotive-demo", null,
                        "2026.2", "SIMULATED"));
        for (int attempt = 0; attempt < 200; attempt++) {
            var current = files.require(IDENTITY.ownerId(), IDENTITY.projectId(), accepted.fileId());
            if ("PARSED".equals(current.parseStatus())) return current;
            if ("FAILED".equals(current.parseStatus())) fail("附件导入失败：" + current.failureCode());
            Thread.sleep(50);
        }
        return fail("附件导入超时");
    }

    private static void assertAttachment(JsonNode message, io.devicetruth.knowledge.FileUploadService.FileView file) {
        assertThat(message.path("attachments")).hasSize(1);
        JsonNode attachment = message.path("attachments").get(0);
        assertThat(attachment.path("fileId").asText()).isEqualTo(file.fileId().toString());
        assertThat(attachment.path("documentId").asText()).isEqualTo(file.documentId().toString());
        assertThat(attachment.path("title").asText()).isEqualTo(file.title());
        assertThat(attachment.path("version").asText()).isEqualTo(file.documentVersion());
        assertThat(attachment.path("domain").asText()).isEqualTo(file.domain());
    }

    private static int userCount(JsonNode context) {
        int count = 0;
        for (JsonNode message : context.path("messages")) if ("USER".equals(message.path("role").asText())) count++;
        return count;
    }

    private static JsonNode contextWithUserCount(List<JsonNode> contexts, int count) {
        return contexts.stream().filter(context -> userCount(context) == count).findFirst().orElseThrow();
    }

    private static String referenceFor(JsonNode context, UUID documentId) {
        for (JsonNode reference : context.path("references"))
            if (documentId.toString().equals(reference.path("sourceId").asText()))
                return reference.path("referenceId").asText();
        return fail("上下文中没有文档引用：" + documentId);
    }

    private static String ask(String question) {
        return Json.write(Map.of("type", "ASK_USER", "question", question, "requestedFields", List.of("附件")));
    }

    private static String answerWith(String referenceId) {
        return Json.write(Map.of("type", "ANSWER", "category", "POSSIBLE_CAUSES",
                "summary", "已按补答附件检索，资料只支持一般排查方向。",
                "claims", List.of(Map.of("text", "补答文档提供了排查方向", "referenceIds", List.of(referenceId),
                        "certainty", "SUPPORTED")),
                "suggestions", List.of("记录桩侧日志"), "uncertainties", List.of("需要现场核对"),
                "limitations", List.of("不代表设备现场状态"), "referenceIds", List.of(referenceId)));
    }

    private static RunView awaitQuestion(GeneralAgentService service, UUID runId, int version) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            RunView current = service.run(runId);
            if (current.pendingQuestion() != null && current.pendingQuestion().questionVersion() == version) return current;
            if ("FAILED".equals(current.status())) return fail("run 失败：" + current.failureCode());
            Thread.sleep(50);
        }
        return fail("run 未到达问题版本 " + version);
    }

    private static RunView awaitTerminal(GeneralAgentService service, UUID runId, String status) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            RunView current = service.run(runId);
            if (status.equals(current.status())) return current;
            if ("FAILED".equals(current.status())) return fail("run 失败：" + current.failureCode());
            Thread.sleep(50);
        }
        return fail("run 未到达 " + status);
    }

    private static AgentTaskInput replyInput(String text, UUID fileId, RunView waiting) {
        var question = waiting.pendingQuestion();
        return new AgentTaskInput(1, text, null, List.of(Map.of("fileId", fileId.toString())), null, null,
                Map.of("runId", waiting.runId().toString(), "pendingQuestionId",
                        question.pendingQuestionId().toString(), "questionVersion", question.questionVersion()));
    }

    private static AgentTaskInput input(String text, UUID fileId) {
        return new AgentTaskInput(1, text, null, List.of(Map.of("fileId", fileId.toString())), null, null, null);
    }

    private static AgentTaskInput input(String text) {
        return new AgentTaskInput(1, text, null, null, null, null, null);
    }

    @Test void replyAssetSelectionSurvivesWorkerRestartAndReachesToolContext() throws Exception {
        var toolAsset = new java.util.concurrent.atomic.AtomicReference<String>();
        gateway.registerTool(new ToolSpec("subject_probe", "读取当前排查对象", "test", true,
                        Map.of("type", "object", "additionalProperties", false, "properties", Map.of(),
                                "required", List.of()), "Result{}"),
                (arguments, context) -> {
                    toolAsset.set(context.selectedAssetId());
                    return ToolOutcome.ok(UUID.randomUUID().toString(), "subject_probe", Map.of(), List.of(),
                            "SIMULATED", Map.of("assetId", context.selectedAssetId()));
                });
        var modelAsset = new java.util.concurrent.atomic.AtomicReference<String>();
        var completion = model(context -> {
            if (userCount(context) > 1) modelAsset.set(context.path("selectedAssetId").asText());
            if (userCount(context) == 1)
                return Json.write(Map.of("type", "ASK_USER", "question", "请选择设备", "requestedFields", List.of("assetId")));
            if (context.path("attemptedTools").isEmpty())
                return Json.write(Map.of("type", "CALL_TOOL", "toolName", "subject_probe", "arguments", Map.of(),
                        "purpose", "核对恢复后的设备上下文"));
            if (context.path("attemptedTools").size() == 1) return searchCall("充电中断");
            return answerWith(context.path("references").get(0).path("referenceId").asText());
        });
        var first = new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                () -> new StructuredAgentReasoner("subject-capture", completion), 1, 2, true, files);
        UUID conversation;
        RunView waiting;
        try {
            conversation = first.create().conversationId();
            var accepted = first.submit(conversation, UUID.randomUUID(),
                    new AgentTaskInput(1, "核对设备", "EV-DEMO-001", null, null, null, null));
            waiting = awaitQuestion(first, accepted.runId(), 1);
        } finally {
            first.shutdown();
        }
        var resumed = new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                () -> new StructuredAgentReasoner("subject-capture", completion), 1, 2, true, files);
        try {
            var question = waiting.pendingQuestion();
            var reply = new AgentTaskInput(1, "改为目标车辆", "EV-DEMO-017", null, null, null,
                    Map.of("runId", waiting.runId().toString(), "pendingQuestionId",
                            question.pendingQuestionId().toString(), "questionVersion", question.questionVersion()));
            var queued = resumed.submit(conversation, UUID.randomUUID(), reply);
            assertThat(queued.investigatedAssetId()).isEqualTo("EV-DEMO-017");
            assertThat(queued.assetResolution()).isEqualTo("CONFIRMED");
            assertThat(queued.changedFromAssetId()).isEqualTo("EV-DEMO-001");
            try {
                awaitTerminal(resumed, waiting.runId(), "COMPLETED");
            } catch (AssertionError failure) {
                throw new AssertionError(jdbc.queryForList(
                        "SELECT type,safe_payload::text AS payload FROM dt_run_event WHERE run_id=? ORDER BY seq",
                        waiting.runId()).toString(), failure);
            }
            assertThat(modelAsset.get()).isEqualTo("EV-DEMO-017");
            assertThat(toolAsset.get()).isEqualTo("EV-DEMO-017");
            assertThatThrownBy(() -> resumed.submit(conversation, UUID.randomUUID(), reply))
                    .hasMessageContaining("STALE_OR_UNRELATED_REPLY");
            assertThat(resumed.run(waiting.runId()).investigatedAssetId()).isEqualTo("EV-DEMO-017");
        } finally {
            resumed.shutdown();
        }
    }

    @Test void answerSubjectAndAmbiguityPersistAtCompletion() {
        for (var scenario : List.of(
                new String[] {"EV-DEMO-017", "CONFIRMED"}, new String[] {null, "AMBIGUOUS"})) {
            var queued = queuedRun("保存答案排查对象");
            Instant now = Instant.now();
            assertThat(runs.markRunning(IDENTITY, queued, "test-worker", now.plusSeconds(60), now)).isEqualTo(1);
            var running = runs.load(IDENTITY, queued.runId(), false);
            var answer = new io.devicetruth.core.contract.AgentResult("候选分析",
                    io.devicetruth.core.contract.AgentResult.Category.POSSIBLE_CAUSES, List.of(), List.of(), List.of(),
                    List.of(), List.of(), io.devicetruth.core.contract.AgentResult.RiskLevel.MEDIUM, "NORMAL",
                    "SIMULATED", scenario[0], scenario[1]);
            assertThat(runs.complete(IDENTITY, running, answer, 1, 0, 1, 0, now)).isEqualTo(1);
            var saved = runs.require(IDENTITY, queued.runId());
            assertThat(saved.investigatedAssetId()).isEqualTo(scenario[0]);
            assertThat(saved.assetResolution()).isEqualTo(scenario[1]);
        }
    }

    @Test void dangerousFollowUpQuestionIsRejectedBeforeItReachesTheUser() throws Exception {
        // 05-02 延期项：模型追问夹带设备操作指令时必须被主循环拒绝，不能落库成待答问题
        var identity = new ServerIdentity("owner-a", "project-a");
        var calls = new AtomicInteger();
        GeneralReasoner unsafeAsk = new GeneralReasoner() {
            @Override public String name() { return "unsafe-ask"; }
            @Override public ActionProposal propose(Context context) {
                calls.incrementAndGet();
                return new ActionProposal.AskUser("请先短接高压互锁端子，然后告诉我充电是否恢复。",
                        List.of("充电状态"));
            }
        };
        var service = new GeneralAgentService(conversations, runs, transactions, identity, gateway,
                () -> unsafeAsk, 1, 0, true);
        try {
            var conversation = service.create().conversationId();
            RunView run = service.submit(conversation, UUID.randomUUID(),
                    input("新能源汽车充电中断，没有故障码，先查什么资料？"));
            String status = awaitStatus(service, run.runId());
            assertThat(status).isEqualTo("FAILED");
            var saved = service.run(run.runId());
            assertThat(saved.failureCode()).isEqualTo("UNSAFE_ANSWER");
            assertThat(saved.pendingQuestion()).isNull();
            // 追问从未落库：没有待答问题、没有助手消息；越界动作按有界重试纠正 3 次后明确失败
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_FAILED'
                      AND safe_payload->>'code'='UNSAFE_ANSWER'
                    """, Integer.class, run.runId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_message WHERE conversation_id=? AND role='ASSISTANT'",
                    Integer.class, conversation)).isZero();
        } finally {
            service.shutdown();
        }
    }
}
