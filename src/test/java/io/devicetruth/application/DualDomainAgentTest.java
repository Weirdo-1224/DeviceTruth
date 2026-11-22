package io.devicetruth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.application.agent.AssetSnapshotImporter;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.Repositories.AssetRegistryRepository;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.server.DeviceTruthServerApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
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
import static org.assertj.core.api.Assertions.*;

/** 同一套产品 HTTP 入口、Spring 装配、PostgreSQL 与离线推理运行双领域样例。 */
class DualDomainAgentTest {
    private static final String OWNER = "dual-test";
    private static final String PROJECT = "dual-project";
    private static final String ASSET = "SC-0245-B";
    private static final String FACTORY_GOAL = "请做控制器固件核对。查询 moduleVersion，比较 2024-06-01 的出厂记录和当前改造、旧模板，说明现场核验情况。";
    private static ConfigurableApplicationContext app;
    private static String jdbcBase, username, password, database, base;
    private static TestRestTemplate HTTP;

    @BeforeAll static void setup() throws Exception {
        jdbcBase = required("DEVICETRUTH_TEST_PG_BASE_URL");
        username = required("DEVICETRUTH_TEST_PG_USERNAME");
        password = required("DEVICETRUTH_TEST_PG_PASSWORD");
        if (!jdbcBase.matches("jdbc:postgresql://[A-Za-z0-9.\\-\\[\\]:]+/"))
            throw new IllegalArgumentException("Test PG base URL must not contain a database or credentials");
        database = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        databaseCommand("CREATE DATABASE ");
        app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--devicetruth.auth.cookie-secure=false", "--server.port=0", "--debug=false",
                "--spring.datasource.url=" + jdbcBase + database, "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + password, "--devicetruth.agent.reasoner=scripted",
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.product.owner-id=" + OWNER,
                "--devicetruth.product.project-id=" + PROJECT, "--devicetruth.assets.seed-fixtures=true",
                "--devicetruth.assets.fixtures=" + ASSET);
        HTTP = io.devicetruth.server.AuthenticatedProductTestSupport.client(app);
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
        request(HttpMethod.POST, "/api/knowledge/documents", Map.of(
                "title", "充电系统检修手册", "domain", "automotive-demo", "version", "2026.1",
                "filename", "manual.md", "mimeType", "text/markdown", "dataNature", "SIMULATED",
                "content", Files.readString(Path.of("datasets/knowledge/charging-system-manual.md"))), 201);
    }

    @AfterAll static void cleanup() throws Exception {
        if (app != null) app.close();
        if (database != null && database.matches("devicetruth_test_[0-9a-f]{32}")) databaseCommand("DROP DATABASE IF EXISTS ");
    }

    @Test void sameProductApiUsesEachSkillAndPreservesSourcesAndMissingObservations() throws Exception {
        JsonNode fixture = Json.MAPPER.readTree(Path.of("datasets/v1/automotive/charging-case.json").toFile());
        String carConversation = conversation();
        String carRun = submit(carConversation, Map.of("userMessage", fixture.path("goalText").asText()));
        JsonNode waiting = await(carRun, "WAITING_FOR_USER");
        var question = waiting.path("pendingQuestion");
        assertThat(question.path("question").asText()).contains("交流慢充还是直流快充", "特定充电桩");
        assertThat(submit(carConversation, Map.of("userMessage", fixture.path("turns").get(1).path("content").asText(),
                "replyTo", Map.of("runId", carRun, "pendingQuestionId", question.path("pendingQuestionId").asText(),
                        "questionVersion", question.path("questionVersion").asInt())))).isEqualTo(carRun);
        JsonNode carAnswer = await(carRun, "COMPLETED").path("result");
        assertSkill(carRun, "automotive-diagnosis");
        assertThat(carAnswer.path("references").toString()).contains("直流快充中断");
        assertThat(carAnswer.path("limitations").toString()).contains("尚未读取到现场数据");
        assertThat(carAnswer.path("category").asText()).isNotEqualTo("CONFIRMED_FINDING");
        assertThat(carAnswer.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(get("/api/runs/" + carRun + "/tool-events").toString())
                .contains("search_knowledge", "get_observations");
        boolean directCurrentExcerpt = false;
        for (JsonNode reference : carAnswer.path("references")) {
            String refId = reference.path("referenceId").asText();
            var excerpt = get("/api/knowledge/references/" + refId + "?runId=" + carRun);
            assertThat(excerpt.path("content").asText()).isNotBlank();
            directCurrentExcerpt |= excerpt.path("section").asText().contains("直流快充中断");
        }
        assertThat(directCurrentExcerpt).isTrue();

        String factoryRun = submit(conversation(), Map.of("userMessage", FACTORY_GOAL, "selectedAssetId", ASSET,
                "asOf", "2026-11-13T08:00:00Z"));
        JsonNode factoryAnswer = await(factoryRun, "COMPLETED").path("result");
        assertSkill(factoryRun, "factory-equipment");
        assertThat(factoryAnswer.path("summary").asText()).contains("仍待现场核验");
        assertThat(factoryAnswer.path("limitations").toString()).contains("尚未读取到现场数据");
        assertThat(factoryAnswer.path("references").toString()).contains("D-120A", "D-120B", "source=FACTORY",
                "source=REPLACEMENT", "source=CONFIGURATION", "validFrom=2025-09-15");
        assertThat(factoryAnswer.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(get("/api/runs/" + factoryRun + "/tool-events").toString())
                .contains("get_configuration", "get_observations").doesNotContain("search_knowledge");
        String fetched = get("/api/runs/" + factoryRun + "/references").toString();
        for (JsonNode reference : factoryAnswer.path("references"))
            assertThat(fetched).contains(reference.path("referenceId").asText());
    }

    @Test void simulatedObservationsRespectTimeQualityAndOwnershipAndRemainCitable() throws Exception {
        var jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
        jdbc.update("""
                INSERT INTO dt_asset_observation(asset_id,owner_id,project_id,observed_at,signal,value,unit,source_id,quality)
                VALUES (?,?,?,'2026-11-13T07:00:00Z','cycleTime','17','s','sample-a','GOOD'),
                       (?,?,?,'2026-11-13T06:00:00Z','cycleTime','19','s','sample-b','BAD'),
                       (?,?,?,'2026-11-14T06:00:00Z','cycleTime','21','s','sample-future','GOOD')
                """, ASSET, OWNER, PROJECT, ASSET, OWNER, PROJECT, ASSET, OWNER, PROJECT);
        try {
            String conversation = conversation();
            String run = submit(conversation, Map.of("userMessage", FACTORY_GOAL, "selectedAssetId", ASSET,
                    "asOf", "2026-11-13T08:00:00Z"));
            var result = await(run, "COMPLETED").path("result");
            assertThat(result.path("summary").asText()).contains("仍待现场核验");
            assertThat(result.path("limitations").toString()).contains("已读取模拟观测");
            assertThat(result.path("references").toString()).contains("sample-a", "sample-b", "FIELD_OBSERVATION")
                    .doesNotContain("sample-future");
            assertThat(get("/api/runs/" + run + "/references").toString()).contains("sample-a", "quality=GOOD");

            var gateway = app.getBean("agentCapabilityGateway", CapabilityGateway.class);
            var scope = new ToolContext(OWNER, PROJECT, UUID.fromString(conversation), UUID.fromString(run), ASSET,
                    Instant.now(), Instant.parse("2026-11-13T08:00:00Z"), gateway.toolNames());
            var filtered = gateway.executeTool(scope, "get_observations", Map.of("signal", "cycleTime", "quality", "GOOD",
                    "from", "2026-11-13T07:00:00Z"));
            assertThat(filtered.success()).isTrue();
            assertThat(filtered.references()).singleElement().satisfies(ref -> assertThat(ref.sourceId()).isEqualTo("sample-a"));
            var noMatch = gateway.executeTool(scope, "get_observations", Map.of("quality", "UNCERTAIN"));
            assertThat(noMatch.modelContext()).containsEntry("status", "NO_MATCHING_DATA");
            var foreign = new ToolContext("other-owner", PROJECT, scope.conversationId(), scope.runId(), ASSET,
                    scope.requestedAt(), scope.asOf(), scope.allowedTools());
            assertThat(gateway.executeTool(foreign, "get_observations", Map.of()).error().code()).isEqualTo("ASSET_NOT_FOUND");
        } finally {
            jdbc.update("DELETE FROM dt_asset_observation WHERE owner_id=? AND project_id=? AND asset_id=?", OWNER, PROJECT, ASSET);
        }
    }

    @Test void factoryVariantsMatchSourceConflictAndFieldValidityAtEachTimePoint() throws Exception {
        var registry = app.getBean(AssetRegistryRepository.class);
        var jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
        var expectations = Json.MAPPER.readTree(Path.of("datasets/v1/expected/factory-sc-0245-b.json").toFile());
        assertThat(expectations.path("variants").size()).isEqualTo(4);
        try {
            for (JsonNode variant : expectations.path("variants")) {
                deleteFactoryFacts(jdbc);
                var fixture = JsonRepositories.readDataset(Path.of(variant.path("datasetPath").asText()));
                var snapshot = AssetSnapshotImporter.fromEvidence(OWNER, PROJECT, ASSET, fixture.records());
                registry.importSnapshot(snapshot);
                registry.importSnapshot(snapshot);
                for (JsonNode point : variant.path("queryPoints")) {
                    var actual = get("/api/assets/" + ASSET + "/configuration?componentId=CTRL-01&attribute=moduleVersion"
                            + "&asOf=" + point.path("asOf").asText());
                    var expected = point.path("expected");
                    assertThat(actual.path("installationStatus").asText()).as(point.path("id").asText())
                            .isEqualTo(expected.path("installationStatus").asText());
                    assertThat(actual.path("conflict").asText()).isEqualTo(expected.path("conflict").asText());
                    assertThat(actual.path("requiredChecks").size()).isEqualTo(expected.path("requiredChecks").asInt());
                    assertThat(actual.path("entries")).extracting(entry -> entry.path("sourceId").asText())
                            .containsExactlyInAnyOrderElementsOf(java.util.stream.StreamSupport
                                    .stream(expected.path("entries").spliterator(), false)
                                    .map(JsonNode::asText).toList());
                }
                if ("three-source-conflict".equals(variant.path("id").asText())) {
                    String run = submit(conversation(), Map.of("userMessage", FACTORY_GOAL, "selectedAssetId", ASSET,
                            "asOf", "2026-11-13T08:00:00Z"));
                    var answer = await(run, "COMPLETED").path("result");
                    assertSkill(run, "factory-equipment");
                    assertThat(answer.path("summary").asText()).contains("配置记录存在冲突", "仍待现场核验");
                    assertThat(answer.path("references").toString()).contains("source=FACTORY",
                            "source=REPLACEMENT", "source=CONFIGURATION");
                }
            }
        } finally {
            deleteFactoryFacts(jdbc);
            AssetSnapshotImporter.importFixture(registry, OWNER, PROJECT, ASSET);
        }
    }

    @Test void safetyBoundaryIsVisibleThroughProductAnswerAndCatalogIsReadOnly() throws Exception {
        String run = submit(conversation(), Map.of("userMessage", "高压设备检修由谁负责？只说明安全边界。"));
        JsonNode answer = await(run, "COMPLETED").path("result");
        assertThat(answer.path("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(answer.path("safetyDisposition").asText()).isEqualTo("REQUIRES_QUALIFIED_PERSONNEL");
        assertThat(answer.path("limitations").toString()).contains("本系统不提供具体操作步骤");
        var gateway = app.getBean("agentCapabilityGateway", CapabilityGateway.class);
        var scope = new ToolContext(OWNER, PROJECT, null, UUID.fromString(run), null,
                Instant.now(), null, gateway.toolNames());
        assertThat(gateway.toolCatalog(scope)).allMatch(spec -> spec.readOnly());
    }

    private static void deleteFactoryFacts(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM dt_asset_configuration WHERE owner_id=? AND project_id=? AND asset_id=?", OWNER, PROJECT, ASSET);
        jdbc.update("DELETE FROM dt_asset_maintenance WHERE owner_id=? AND project_id=? AND asset_id=?", OWNER, PROJECT, ASSET);
    }

    private static void assertSkill(String run, String skillId) throws Exception {
        var skill = get("/api/runs/" + run + "/skill");
        assertThat(skill.path("skillId").asText()).isEqualTo(skillId);
        assertThat(skill.path("version").asText()).isEqualTo(
                io.devicetruth.skill.SkillRegistry.load(Path.of("skills")).require(skillId).version());
    }

    private static String conversation() throws Exception {
        return request(HttpMethod.POST, "/api/conversations", Map.of(), 201).path("conversationId").asText();
    }

    private static String submit(String conversation, Map<String, Object> fields) throws Exception {
        var payload = new java.util.HashMap<>(fields);
        payload.put("schemaVersion", 1);
        return request(HttpMethod.POST, "/api/conversations/" + conversation + "/messages", payload, 202)
                .path("runId").asText();
    }

    private static JsonNode await(String run, String expected) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        JsonNode view;
        do {
            view = get("/api/runs/" + run);
            String status = view.path("status").asText();
            if (status.equals(expected)) return view;
            if (java.util.Set.of("FAILED", "COMPLETED", "CANCELLED", "WAITING_FOR_USER").contains(status)) break;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        fail("Expected run status " + expected + ", got " + view);
        return view;
    }

    private static JsonNode get(String path) throws Exception { return request(HttpMethod.GET, path, null, 200); }

    private static JsonNode request(HttpMethod method, String path, Object body, int status) throws Exception {
        var headers = new HttpHeaders();
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        if (path.endsWith("/messages")) headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var response = HTTP.exchange(base + path, method, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        return Json.MAPPER.readTree(response.getBody());
    }

    private static void databaseCommand(String operation) throws Exception {
        try (var connection = DriverManager.getConnection(jdbcBase + "postgres", username, password);
             var statement = connection.createStatement()) {
            statement.execute(operation + database);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test configuration: " + name);
        return value;
    }
}
