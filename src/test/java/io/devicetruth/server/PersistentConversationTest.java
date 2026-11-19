package io.devicetruth.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;

/** 使用明确配置的真实PG，每轮创建独立测试库，绝不在已有业务库内清表。 */
class PersistentConversationTest {
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static ConfigurableApplicationContext app;
    private static TestRestTemplate rest;
    private static String base;
    private static JdbcTemplate jdbc;

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
        app = open("owner-a", "project-a");
        base = base(app);
        rest = AuthenticatedProductTestSupport.client(app);
        jdbc = new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
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

    private static ConfigurableApplicationContext open(String owner, String project) {
        var opened = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--devicetruth.auth.cookie-secure=false", "--server.port=0", "--debug=false",
                "--logging.level.org.springframework=WARN", "--logging.level.org.springframework.jdbc=INFO",
                "--devicetruth.core.reasoner=scripted", "--spring.datasource.url=" + databaseBaseUrl + databaseName,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=" + owner, "--devicetruth.product.project-id=" + project,
                // 本用例只验证 M0 的持久化合同（消息/QUEUED/不变量），不启动 Agent worker，避免状态被异步推进。
                "--devicetruth.agent.worker-enabled=false");
        AuthenticatedProductTestSupport.register(opened);
        return opened;
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private ResponseEntity<String> post(String url, Object body, UUID key) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) headers.set("Idempotency-Key", key.toString());
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String conversation() throws Exception {
        var response = post(base + "/api/conversations", Map.of(), null);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return Json.MAPPER.readTree(response.getBody()).path("conversationId").asText();
    }

    private Map<String, Object> input(String text) { return Map.of("schemaVersion", 1, "userMessage", text); }
    private JsonNode detail(String id) throws Exception {
        var response = rest.getForEntity(base + "/api/conversations/" + id, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return Json.MAPPER.readTree(response.getBody());
    }

    @Test void freeMessageIsAtomicQueuedAndSurvivesServiceRestart() throws Exception {
        String id = conversation();
        String original = "  新能源汽车充电中断，没有故障码。\n先查什么资料？ 🔋  ";
        var accepted = post(base + "/api/conversations/" + id + "/messages", input(original), UUID.randomUUID());
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        var run = Json.MAPPER.readTree(accepted.getBody());
        String runId = run.path("runId").asText();
        assertThat(run.path("status").asText()).isEqualTo("QUEUED");
        assertThat(run.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(run.path("investigatedAssetId").isNull()).isTrue();
        assertThat(run.path("assetResolution").asText()).isEqualTo("UNCONFIRMED");
        assertThat(accepted.getHeaders().getLocation().toString()).isEqualTo("/api/runs/" + runId);
        var before = detail(id);
        assertThat(before.path("messages").size()).isEqualTo(1);
        assertThat(before.path("messages").get(0).path("content").asText()).isEqualTo(original);
        assertThat(before.path("messages").get(0).path("selectedAssetId").isNull()).isTrue();
        assertThat(before.path("messages").get(0).path("asOf")).isEqualTo(before.path("messages").get(0).path("requestedAt"));
        assertThat(before.path("conversation").path("version").asInt()).isEqualTo(1);
        assertThat(Json.MAPPER.readTree(rest.getForObject(base + "/api/conversations/" + id + "/messages", String.class)))
                .isEqualTo(before.path("messages"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dt_run_event WHERE run_id=? AND type='RUN_QUEUED'", Integer.class, UUID.fromString(runId))).isEqualTo(1);
        var migrations = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(migrations).contains("1", "13", "16", "18");
        app.close();
        app = open("owner-a", "project-a");
        base = base(app);
        jdbc = new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
        assertThat(detail(id)).isEqualTo(before);
        assertThat(Json.MAPPER.readTree(rest.getForObject(base + "/api/runs/" + runId, String.class))).isEqualTo(run);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).containsExactlyElementsOf(migrations);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class)).isEqualTo("0.8.7");
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("16.15");
    }

    @Test void selectedAssetSurvivesAndLegacyRowsUseConservativeFallback() throws Exception {
        String id = conversation();
        var accepted = post(base + "/api/conversations/" + id + "/messages",
                Map.of("schemaVersion", 1, "userMessage", "核查选定设备", "selectedAssetId", "EV-DEMO-017"), UUID.randomUUID());
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        String runId = Json.MAPPER.readTree(accepted.getBody()).path("runId").asText();
        var selected = Json.MAPPER.readTree(rest.getForObject(base + "/api/runs/" + runId, String.class));
        assertThat(selected.path("investigatedAssetId").asText()).isEqualTo("EV-DEMO-017");
        assertThat(selected.path("assetResolution").asText()).isEqualTo("SELECTED");

        jdbc.update("UPDATE dt_agent_run SET subject_asset_id=NULL,subject_status='UNCONFIRMED' WHERE id=?",
                UUID.fromString(runId));
        var legacy = Json.MAPPER.readTree(rest.getForObject(base + "/api/runs/" + runId, String.class));
        assertThat(legacy.path("investigatedAssetId").asText()).isEqualTo("EV-DEMO-017");
        assertThat(legacy.path("assetResolution").asText()).isEqualTo("SELECTED");
        assertThat(legacy.path("changedFromAssetId").isNull()).isTrue();
    }

    @Test void conversationSummariesSupportStablePaginationFiltersAndOwnership() throws Exception {
        String empty = conversation();
        var ids = new java.util.ArrayList<String>();
        String waitingRun = null;
        for (int i = 0; i < 22; i++) {
            String id = conversation();
            ids.add(id);
            var accepted = post(base + "/api/conversations/" + id + "/messages",
                    Map.of("schemaVersion", 1, "userMessage", "分页目标 " + i + " TARGET-" + i + " 🔋", "selectedAssetId", "EV-DEMO-017"),
                    UUID.randomUUID());
            assertThat(accepted.getStatusCode().value()).isEqualTo(202);
            if (i == 0) waitingRun = Json.MAPPER.readTree(accepted.getBody()).path("runId").asText();
        }
        var direct = new io.devicetruth.adapter.persistence.ConversationRepository(jdbc).summaries(
                new io.devicetruth.application.agent.GeneralAgentService.ServerIdentity("owner-a", "project-a"),
                null, null, null, null, 20);
        assertThat(direct.items()).hasSize(20);
        var firstResponse = rest.getForEntity(base + "/api/conversations/summaries?limit=20&q=TARGET-", String.class);
        assertThat(firstResponse.getStatusCode()).as(firstResponse.getBody()).isEqualTo(HttpStatus.OK);
        var first = Json.MAPPER.readTree(firstResponse.getBody());
        assertThat(first.path("items").size()).isEqualTo(20);
        assertThat(first.path("nextCursor").asText()).isNotBlank();
        assertThat(first.path("items").get(0).path("title").asText()).contains("分页目标");
        assertThat(first.path("items").get(0).path("investigatedAssetId").asText()).isEqualTo("EV-DEMO-017");
        assertThat(first.path("items").get(0).path("activeRunCount").asInt()).isEqualTo(1);
        assertThat(first.path("items").get(0).path("runCount").asInt()).isEqualTo(1);
        assertThat(first.path("items").toString()).doesNotContain(empty);

        String cursor = first.path("nextCursor").asText();
        var second = Json.MAPPER.readTree(rest.getForObject(
                base + "/api/conversations/summaries?limit=20&q=TARGET-&cursor=" + cursor, String.class));
        assertThat(second.path("items").size()).isEqualTo(2);
        assertThat(second.path("nextCursor").isNull()).isTrue();
        var allIds = new java.util.HashSet<String>();
        for (var page : List.of(first, second)) for (var item : page.path("items"))
            assertThat(allIds.add(item.path("conversationId").asText())).isTrue();
        assertThat(allIds).hasSize(22);

        var searched = Json.MAPPER.readTree(rest.getForObject(
                base + "/api/conversations/summaries?q=TARGET-21&assetId=EV-DEMO-017", String.class));
        assertThat(searched.path("items").size()).isEqualTo(1);
        assertThat(searched.path("items").get(0).path("title").asText()).contains("分页目标 21");

        UUID questionId = UUID.randomUUID();
        jdbc.update("UPDATE dt_agent_run SET status='WAITING_FOR_USER',pending_question_id=?,question_version=1,"
                        + "pending_question=CAST(? AS jsonb) WHERE id=?", questionId,
                "{\"pendingQuestionId\":\"" + questionId + "\",\"questionVersion\":1,\"question\":\"请选择设备\","
                        + "\"requestedFields\":[\"assetId\"],\"askedAt\":\"2026-11-18T00:00:00Z\"}", UUID.fromString(waitingRun));
        var waiting = Json.MAPPER.readTree(rest.getForObject(
                base + "/api/conversations/summaries?status=WAITING_FOR_USER", String.class));
        assertThat(waiting.path("items").size()).isEqualTo(1);
        assertThat(waiting.path("items").get(0).path("waitingRunCount").asInt()).isEqualTo(1);
        var prioritized = Json.MAPPER.readTree(rest.getForObject(base + "/api/conversations/summaries?limit=1", String.class));
        assertThat(prioritized.path("items").get(0).path("conversationId").asText()).isEqualTo(ids.get(0));
        assertThat(rest.getForEntity(base + "/api/conversations/summaries?limit=51", String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity(base + "/api/conversations/summaries?cursor=bad", String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForObject(base + "/api/conversations", String.class)).contains(empty);

        try (var other = open("owner-b", "project-a")) {
            var isolated = Json.MAPPER.readTree(rest.getForObject(base(other) + "/api/conversations/summaries", String.class));
            assertThat(isolated.path("items").isEmpty()).isTrue();
        }
    }

    @Test void duplicateSubmissionDoesNotCreateAnotherMessageOrRun() throws Exception {
        String id = conversation();
        UUID key = UUID.randomUUID();
        String path = base + "/api/conversations/" + id + "/messages";
        assertThat(post(path, input("充电中断"), key).getStatusCode().value()).isEqualTo(202);
        var duplicate = post(path, input("另一个正文也不能复用请求键"), key);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(duplicate.getBody()).contains("DUPLICATE_REQUEST");
        assertThat(detail(id).path("messages").size()).isEqualTo(1);
        assertThat(detail(id).path("runs").size()).isEqualTo(1);
    }

    @Test void concurrentRequestsKeepOneRunPerKeyAndConsecutiveMessageSequence() throws Exception {
        String id = conversation();
        String path = base + "/api/conversations/" + id + "/messages";
        UUID key = UUID.randomUUID();
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> post(path, input("并发原文一"), key));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> post(path, input("并发原文二"), key));
        assertThat(List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS).getStatusCode().value(),
                second.get(15, java.util.concurrent.TimeUnit.SECONDS).getStatusCode().value()))
                .containsExactlyInAnyOrder(202, 409);
        assertThat(post(path, input("下一条消息"), UUID.randomUUID()).getStatusCode().value()).isEqualTo(202);
        var saved = detail(id);
        assertThat(saved.path("messages").size()).isEqualTo(2);
        assertThat(saved.path("messages").get(0).path("seq").asInt()).isEqualTo(1);
        assertThat(saved.path("messages").get(1).path("seq").asInt()).isEqualTo(2);
        assertThat(saved.path("runs").size()).isEqualTo(2);
        assertThat(saved.path("conversation").path("version").asInt()).isEqualTo(2);
    }

    @Test void eventFailureRollsBackMessageRunAndConversationVersion() throws Exception {
        String id = conversation();
        jdbc.execute("""
                CREATE FUNCTION fail_test_event() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'private-test-database-failure'; END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_test_event BEFORE INSERT ON dt_run_event FOR EACH ROW EXECUTE FUNCTION fail_test_event()");
        try {
            var failure = post(base + "/api/conversations/" + id + "/messages", input("回滚检查"), UUID.randomUUID());
            assertThat(failure.getStatusCode().value()).isEqualTo(500);
            assertThat(failure.getBody()).doesNotContain("private-test-database-failure", "jdbc:");
            assertThat(failure.getBody().contains(password)).as("Response must not contain the database password").isFalse();
            var saved = detail(id);
            assertThat(saved.path("messages").size()).isZero();
            assertThat(saved.path("runs").size()).isZero();
            assertThat(saved.path("conversation").path("version").asInt()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_test_event ON dt_run_event");
            jdbc.execute("DROP FUNCTION fail_test_event()");
        }
        assertThat(post(base + "/api/conversations/" + id + "/messages", input("重试成功"), UUID.randomUUID()).getStatusCode().value()).isEqualTo(202);
        assertThat(detail(id).path("messages").get(0).path("seq").asInt()).isEqualTo(1);
    }

    @Test void anotherOwnerOrProjectCannotReadOrWriteAndBodyCannotChangeIdentity() throws Exception {
        String id = conversation();
        var accepted = post(base + "/api/conversations/" + id + "/messages", input("本项目独有原文"), UUID.randomUUID());
        String runId = Json.MAPPER.readTree(accepted.getBody()).path("runId").asText();
        for (var identity : List.of(List.of("owner-b", "project-a"), List.of("owner-a", "project-b"))) {
            try (var other = open(identity.get(0), identity.get(1))) {
                String otherBase = base(other);
                for (String path : List.of("/api/conversations/" + id, "/api/conversations/" + id + "/messages", "/api/runs/" + runId)) {
                    var denied = rest.getForEntity(otherBase + path, String.class);
                    assertThat(denied.getStatusCode().value()).isEqualTo(404);
                    assertThat(denied.getBody()).contains("NOT_FOUND_OR_DENIED").doesNotContain("本项目独有原文");
                }
                assertThat(post(otherBase + "/api/conversations/" + id + "/messages", input("越权提交"), UUID.randomUUID()).getStatusCode().value()).isEqualTo(404);
                assertThat(rest.getForObject(otherBase + "/api/conversations", String.class)).isEqualTo("[]");
            }
        }
        for (String field : List.of("ownerId", "projectId", "permissions", "requestedAt")) {
            var spoof = post(base + "/api/conversations/" + id + "/messages",
                    Map.of("schemaVersion", 1, "userMessage", "不能改变身份", field, "forged"), UUID.randomUUID());
            assertThat(spoof.getStatusCode().value()).isEqualTo(400);
        }
        assertThat(detail(id).path("messages").size()).isEqualTo(1);
    }

    @Test void databaseRejectsCrossOwnerLinksAndDuplicateEventSequence() throws Exception {
        String id = conversation();
        post(base + "/api/conversations/" + id + "/messages", input("数据库约束"), UUID.randomUUID());
        UUID conversationId = UUID.fromString(id);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of)
                VALUES (?,?,'forged-owner','project-a',2,'USER','invalid',now(),now())
                """, UUID.randomUUID(), conversationId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO dt_run_event SELECT * FROM dt_run_event WHERE conversation_id=?
                """, conversationId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(detail(id).path("messages").size()).isEqualTo(1);
    }

    @Test void malformedJsonIsRejectedWithoutPersistingCoercedOrAmbiguousInput() throws Exception {
        String id = conversation();
        String path = base + "/api/conversations/" + id + "/messages";
        for (String body : List.of(
                "{\"schemaVersion\":1,\"userMessage\":123}",
                "{\"schemaVersion\":1,\"userMessage\":true}",
                "{\"schemaVersion\":\"1\",\"userMessage\":\"充电中断\"}",
                "{\"schemaVersion\":1.5,\"userMessage\":\"充电中断\"}",
                "{\"schemaVersion\":1,\"userMessage\":\"第一条\",\"userMessage\":\"第二条\"}",
                "{\"schemaVersion\":1,\"userMessage\":\"充电中断\"} {}")) {
            var rejected = post(path, body, UUID.randomUUID());
            assertThat(rejected.getStatusCode().value()).as("Raw JSON: %s", body).isEqualTo(400);
            assertThat(rejected.getBody()).contains("INVALID_REQUEST");
            var saved = detail(id);
            assertThat(saved.path("messages").size()).isZero();
            assertThat(saved.path("runs").size()).isZero();
            assertThat(saved.path("conversation").path("version").asInt()).isZero();
        }
    }

    @Test void invalidOrUnavailableInputIsRejectedBeforeAnyWrite() throws Exception {
        String id = conversation();
        String path = base + "/api/conversations/" + id + "/messages";
        assertThat(post(path, input("a".repeat(8001)), UUID.randomUUID()).getStatusCode().value()).isEqualTo(400);
        assertThat(post(path, input(" \n\t"), UUID.randomUUID()).getStatusCode().value()).isEqualTo(400);
        assertThat(post(path, input("缺少请求键"), null).getStatusCode().value()).isEqualTo(400);
        assertThat(post(path, Map.of("schemaVersion", 2, "userMessage", "bad version"), UUID.randomUUID()).getBody()).contains("SCHEMA_VERSION_UNSUPPORTED");
        assertThat(post(path, Map.of("schemaVersion", 1, "userMessage", "文件引用非法", "attachments", List.of(Map.of("fileId", "x"))), UUID.randomUUID()).getBody()).contains("INVALID_ATTACHMENT");
        assertThat(post(path, Map.of("schemaVersion", 1, "userMessage", "上下文引用非法", "contextRefs", List.of(Map.of("kind", "DOCUMENT", "id", "x"))), UUID.randomUUID()).getBody()).contains("INVALID_CONTEXT_REFERENCE");
        // 02-02 起补答已经支持；三元组不完整时按 REPLY_TARGET_REQUIRED 拒绝，仍不写消息、不建 run
        var incompleteReply = post(path, Map.of("schemaVersion", 1, "userMessage", "补答缺关联字段",
                "replyTo", Map.of("runId", "x")), UUID.randomUUID());
        assertThat(incompleteReply.getStatusCode().value()).isEqualTo(400);
        assertThat(incompleteReply.getBody()).contains("REPLY_TARGET_REQUIRED");
        assertThat(detail(id).path("messages").size()).isZero();
        // 按Unicode码点而非UTF-16长度计数，边界上的中文和emoji原样保存。
        String boundary = "🔋".repeat(8000);
        assertThat(post(path, input(boundary), UUID.randomUUID()).getStatusCode().value()).isEqualTo(202);
        assertThat(detail(id).path("messages").get(0).path("content").asText()).isEqualTo(boundary);
    }
}
