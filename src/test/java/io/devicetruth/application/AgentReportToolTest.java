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
import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.server.DeviceTruthServerApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * 03-02-T3：generate_diagnosis_report 只读报告工具的真实 PG 验证。
 * 覆盖：注册与目录合同、调查中/待答时聚合持久事实、白名单键不泄漏推理输入、跨身份拒绝。
 */
class AgentReportToolTest {
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

    private ToolContext context(UUID runId, UUID conversationId) {
        return new ToolContext(IDENTITY.ownerId(), IDENTITY.projectId(), conversationId, runId, null,
                Instant.now(), null, gateway.toolNames());
    }

    @Test void reportToolIsRegisteredReadOnlyWithEmptyParameterSchema() {
        var catalog = gateway.toolCatalog(context(UUID.randomUUID(), UUID.randomUUID()));
        assertThat(catalog).extracting(ToolSpec::name)
                .contains("search_knowledge", "get_document_excerpt",
                        "get_asset", "get_configuration", "get_maintenance_history", "generate_diagnosis_report");
        ToolSpec report = catalog.stream().filter(spec -> "generate_diagnosis_report".equals(spec.name()))
                .findFirst().orElseThrow();
        assertThat(report.readOnly()).isTrue();
        assertThat(report.requiredParameters()).isEmpty();
        assertThat(report.allowedParameters()).isEmpty();
    }

    @Test void waitingRunReportAggregatesPersistedFactsWithWhitelistKeysOnly() throws Exception {
        var service = new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                io.devicetruth.core.reasoning.ScriptedAgentReasoner::new, 1, 2, true, null, 60, 3);
        UUID runId;
        try {
            UUID conversation = service.create().conversationId();
            RunView accepted = service.submit(conversation, UUID.randomUUID(),
                    new AgentTaskInput(1, "新能源汽车充电中断，没有故障码，先查什么资料？", null, null, null, null, null));
            runId = accepted.runId();
            for (int attempt = 0; attempt < 300; attempt++) {
                if ("WAITING_FOR_USER".equals(service.run(runId).status())) break;
                if ("FAILED".equals(service.run(runId).status())) fail("run 失败：" + service.run(runId).failureCode());
                Thread.sleep(50);
            }
            assertThat(service.run(runId).status()).isEqualTo("WAITING_FOR_USER");
        } finally {
            service.shutdown();
        }
        var record = runs.load(IDENTITY, runId, false);
        ToolOutcome outcome = gateway.executeTool(context(runId, record.conversationId()),
                "generate_diagnosis_report", Map.of());
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.error()).isNull();
        assertThat(outcome.references()).isNotEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) outcome.data();
        // 白名单键：推理输入、模型原文、密钥与内部配置不进入报告
        assertThat(report.keySet()).containsExactlyInAnyOrder("runId", "conversationId", "status", "dataNature",
                "stepsUsed", "maxSteps", "attempts", "userMessages", "assistantMessages",
                "pendingQuestion", "references", "toolEvents");
        assertThat(report.get("runId")).isEqualTo(runId.toString());
        assertThat(report.get("status")).isEqualTo("WAITING_FOR_USER");
        assertThat(report.get("userMessages")).isEqualTo(1L);
        assertThat(report.get("assistantMessages")).isEqualTo(1L);
        assertThat((int) report.get("stepsUsed")).isPositive();
        @SuppressWarnings("unchecked")
        Map<String, Object> question = (Map<String, Object>) report.get("pendingQuestion");
        assertThat(question.get("questionVersion")).isEqualTo(1);
        assertThat(String.valueOf(question.get("question"))).contains("交流慢充");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> references = (List<Map<String, Object>>) report.get("references");
        assertThat(references).isNotEmpty().allSatisfy(reference ->
                assertThat(reference.keySet()).containsExactlyInAnyOrder("referenceId", "sourceType", "sourceId",
                        "sourceVersion", "title", "section", "page", "dataNature", "excerpt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolEvents = (List<Map<String, Object>>) report.get("toolEvents");
        assertThat(toolEvents).extracting(event -> event.get("toolName")).contains("search_knowledge");
    }

    @Test void modelCanCallReportToolMidInvestigationAndFinishWithCitations() throws Exception {
        StructuredAgentReasoner.Completion reporter = (system, context) -> {
            try {
                var node = Json.MAPPER.readTree(context);
                if (node.path("references").isEmpty())
                    return "{\"type\":\"CALL_TOOL\",\"toolName\":\"search_knowledge\","
                            + "\"arguments\":{\"query\":\"充电中断 排查顺序\"},\"purpose\":\"检索已授权手册\"}";
                if (!node.path("attemptedTools").toString().contains("generate_diagnosis_report"))
                    return "{\"type\":\"CALL_TOOL\",\"toolName\":\"generate_diagnosis_report\",\"arguments\":{},"
                            + "\"purpose\":\"生成调查报告核对当前进度\"}";
                String referenceId = node.path("references").get(0).path("referenceId").asText();
                return "{\"type\":\"ANSWER\",\"category\":\"POSSIBLE_CAUSES\","
                        + "\"summary\":\"资料与调查报告一致：先收集桩侧日志再核对车型配置。\","
                        + "\"claims\":[{\"text\":\"先收集桩侧日志\",\"referenceIds\":[\"" + referenceId
                        + "\"],\"certainty\":\"SUPPORTED\"}],\"suggestions\":[\"记录中断时间\"],"
                        + "\"uncertainties\":[\"资料版本需按车型核对\"],\"limitations\":[\"不代表本车当前配置\"],"
                        + "\"referenceIds\":[\"" + referenceId + "\"]}";
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        var service = new GeneralAgentService(conversations, runs, transactions, IDENTITY, gateway,
                () -> new StructuredAgentReasoner("reporter", reporter), 1, 2, true, null, 60, 3);
        try {
            UUID conversation = service.create().conversationId();
            RunView accepted = service.submit(conversation, UUID.randomUUID(),
                    new AgentTaskInput(1, "新能源汽车充电中断，没有故障码，先查什么资料？", null, null, null, null, null));
            for (int attempt = 0; attempt < 300; attempt++) {
                if ("COMPLETED".equals(service.run(accepted.runId()).status())) break;
                if ("FAILED".equals(service.run(accepted.runId()).status()))
                    fail("run 失败：" + service.run(accepted.runId()).failureCode());
                Thread.sleep(50);
            }
            assertThat(service.run(accepted.runId()).status()).isEqualTo("COMPLETED");
            assertThat(service.run(accepted.runId()).result().references()).isNotEmpty();
            // 工具轨迹可审计：检索与报告工具都留了白名单事件
            assertThat(runs.toolEvents(IDENTITY, accepted.runId())).extracting(AgentRunRepository.ToolEvent::toolName)
                    .contains("search_knowledge", "generate_diagnosis_report");
        } finally {
            service.shutdown();
        }
    }

    @Test void reportToolDeniesCrossIdentityRun() throws Exception {
        UUID runId = transactions.execute(status -> {
            var conversation = conversations.create(IDENTITY, Instant.now());
            var messageId = conversations.append(IDENTITY, conversation.conversationId(), UUID.randomUUID(),
                    "充电中断，没有故障码", null, Instant.now(), Instant.now());
            return runs.enqueue(IDENTITY, conversation.conversationId(), messageId, Instant.now(), Instant.now()).runId();
        });
        var record = runs.load(IDENTITY, runId, false);
        var foreign = new ToolContext("owner-b", IDENTITY.projectId(), record.conversationId(), runId, null,
                Instant.now(), null, gateway.toolNames());
        ToolOutcome outcome = gateway.executeTool(foreign, "generate_diagnosis_report", Map.of());
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error().code()).isEqualTo("REPORT_UNAVAILABLE");
        assertThat(outcome.references()).isEmpty();
    }
}
