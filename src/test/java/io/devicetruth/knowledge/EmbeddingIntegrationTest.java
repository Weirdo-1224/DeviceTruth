package io.devicetruth.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.server.DeviceTruthServerApplication;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** 显式 opt-in 的真实 embedding→PG→召回测试；无开关时跳过且绝不回退确定性向量。 */
class EmbeddingIntegrationTest {
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static ConfigurableApplicationContext app;
    private static ConfigurableApplicationContext otherApp;
    private static TestRestTemplate rest;
    private static String base;
    private static String otherBase;
    private static JdbcTemplate jdbc;

    @BeforeAll static void start() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("devicetruth.test.real-embedding"), "真实 embedding 未显式启用：NOT_RUN");
        databaseBaseUrl = requiredEnvironment("DEVICETRUTH_TEST_PG_BASE_URL");
        username = requiredEnvironment("DEVICETRUTH_TEST_PG_USERNAME");
        password = requiredEnvironment("DEVICETRUTH_TEST_PG_PASSWORD");
        databaseName = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(databaseBaseUrl + "postgres", username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        app = open("owner-a");
        otherApp = open("owner-b");
        base = base(app);
        otherBase = base(otherApp);
        rest = io.devicetruth.server.AuthenticatedProductTestSupport.client(app, otherApp);
        jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
    }

    @AfterAll static void stop() throws Exception {
        if (app != null) app.close();
        if (otherApp != null) otherApp.close();
        if (databaseName != null) {
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

    private static ConfigurableApplicationContext open(String owner) {
        return new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--devicetruth.auth.cookie-secure=false", "--server.port=0", "--debug=false",
                "--logging.level.org.springframework=WARN", "--devicetruth.core.reasoner=scripted",
                "--devicetruth.agent.worker-enabled=false",
                "--spring.datasource.url=" + databaseBaseUrl + databaseName,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=" + owner, "--devicetruth.product.project-id=project-a",
                "--devicetruth.embedding.provider=openai",
                "--devicetruth.embedding.model=text-embedding-v4", "--devicetruth.embedding.dimension=1024");
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static JsonNode json(String body) throws Exception { return Json.MAPPER.readTree(body); }

    private JsonNode importDocument(String baseUrl, String document) throws Exception {
        var manifest = Json.MAPPER.readTree(Path.of("datasets/eval/m1-rag/manifest.json").toFile());
        var item = manifest.path("documents").path(document);
        String content = Files.readString(Path.of("datasets/eval/m1-rag").resolve(item.path("path").asText()));
        var response = rest.postForEntity(baseUrl + "/api/knowledge/documents", Map.of(
                "title", item.path("title").asText(), "domain", "automotive-demo", "version", "2026.2",
                "filename", Path.of(item.path("path").asText()).getFileName().toString(),
                "mimeType", item.path("mimeType").asText(), "content", content), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return json(response.getBody()).path("document");
    }

    private String run(String baseUrl) throws Exception {
        String conversation = rest.postForObject(baseUrl + "/api/conversations", Map.of(), String.class);
        String conversationId = json(conversation).path("conversationId").asText();
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var accepted = rest.exchange(baseUrl + "/api/conversations/" + conversationId + "/messages",
                org.springframework.http.HttpMethod.POST, new org.springframework.http.HttpEntity<>(
                        Map.of("schemaVersion", 1, "userMessage", "检索充电资料"), headers), String.class);
        return json(accepted.getBody()).path("runId").asText();
    }

    @Test void realEmbeddingPersistsDimensionAndRecallsWithPermissions() throws Exception {
        var charging = importDocument(base, "charging");
        var parts = importDocument(base, "parts");
        var otherParts = importDocument(otherBase, "parts");
        UUID chargingId = UUID.fromString(charging.path("documentId").asText());
        assertThat(charging.path("embeddingProfile").asText()).isEqualTo("openai:text-embedding-v4@1024-cosine-r1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_chunk WHERE document_id=? "
                + "AND vector_dims(embedding)=1024", Integer.class, chargingId))
                .isEqualTo(charging.path("chunkCount").asInt());

        String runId = run(base);
        var semantic = rest.postForObject(base + "/api/knowledge/search", Map.of("runId", runId,
                "query", "电池温度过高为什么会降低充电功率", "mode", "HYBRID", "limit", 5), String.class);
        assertThat(json(semantic).path("hits").toString()).contains(chargingId.toString());
        var part = rest.postForObject(base + "/api/knowledge/search", Map.of("runId", runId,
                "query", "CP-900", "mode", "HYBRID", "limit", 5), String.class);
        assertThat(json(part).path("hits").get(0).path("documentId").asText())
                .isEqualTo(parts.path("documentId").asText());

        String otherRun = run(otherBase);
        var isolated = rest.postForObject(base + "/api/knowledge/search", Map.of("runId", runId,
                "query", "JB-17", "filters", Map.of("documentId", otherParts.path("documentId").asText()),
                "limit", 5), String.class);
        assertThat(json(isolated).path("hits").isEmpty()).isTrue();
        assertThat(otherRun).isNotBlank();
    }
}
