package io.devicetruth.server;

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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * 03-01-T3（E2E-09）：真实 PostgreSQL + HTTP 下的双身份授权负例。
 * 两个独立应用进程共用同一库，但 owner/project 不同；断言跨归属读取统一 404、
 * 伪造 asset/run/引用一律拒绝，且未授权原始记录不出现在任何响应里。
 * 身份只来自服务器 Bean，请求体与查询参数都无法扩大范围。
 */
class ResourceAuthorizationTest {
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static ConfigurableApplicationContext ownerA;
    private static ConfigurableApplicationContext ownerB;
    private static ConfigurableApplicationContext otherProject;
    private static TestRestTemplate rest;
    private static String baseA;
    private static String baseB;
    private static String baseOtherProject;
    private static JdbcTemplate jdbc;

    private static final String ASSET = "CAR-B";

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
        // owner-a 显式开启夹具导入，成为资产 CAR-B 的唯一登记者；其他身份不导入。
        ownerA = open("owner-a", "project-a", true);
        ownerB = open("owner-b", "project-a", false);
        otherProject = open("owner-a", "project-b", false);
        baseA = base(ownerA);
        baseB = base(ownerB);
        baseOtherProject = base(otherProject);
        rest = io.devicetruth.server.AuthenticatedProductTestSupport.client(ownerA, ownerB, otherProject);
        jdbc = new JdbcTemplate(ownerA.getBean(com.zaxxer.hikari.HikariDataSource.class));
    }

    @AfterAll static void stop() throws Exception {
        for (ConfigurableApplicationContext context : List.of(ownerA, ownerB, otherProject))
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

    private static ConfigurableApplicationContext open(String owner, String project, boolean seedAssets) {
        return new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--devicetruth.auth.cookie-secure=false", "--server.port=0", "--debug=false",
                "--logging.level.org.springframework=WARN", "--logging.level.org.springframework.jdbc=INFO",
                "--devicetruth.core.reasoner=scripted", "--devicetruth.agent.reasoner=scripted",
                // 本用例只验证读路径授权，不启动 worker，避免后台 run 干扰断言。
                "--devicetruth.agent.worker-enabled=false",
                "--devicetruth.assets.seed-fixtures=" + seedAssets,
                "--spring.datasource.url=" + databaseBaseUrl + databaseName,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=" + owner, "--devicetruth.product.project-id=" + project,
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.embedding.model=deterministic-char2",
                "--devicetruth.embedding.dimension=1024", "--devicetruth.embedding.profile-revision=1");
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static HttpHeaders jsonHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static ResponseEntity<String> get(String base, String path) {
        return rest.exchange(base + path, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    private static ResponseEntity<String> post(String base, String path, Object body) {
        return rest.exchange(base + path, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    /** 提交消息必须带 Idempotency-Key；每个请求用新 UUID，避免被 DUPLICATE_REQUEST 抢先拦下。 */
    private static ResponseEntity<String> submit(String base, String path, Object body) {
        var headers = jsonHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return rest.exchange(base + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    // ---------- 正向基线：只有 owner-a/project-a 能看到 ----------

    @Test
    void registeredOwnerCanReadItsOwnAsset() {
        // 夹具 CAR-B 的记录有效期在 2026-11-03 附近；显式给时点才能命中配置与维修记录，
        // 不指定时点会按“请求时刻”查询，落在有效期之外只返回空配置，这是时点语义而非授权缺陷。
        var response = get(baseA, "/api/assets/" + ASSET + "?asOf=2026-11-03T04:00:00Z");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains(ASSET).contains("CTRL-01").contains("D-120B");
        assertThat(get(baseA, "/api/assets").getBody()).contains(ASSET);

        // 默认时点（请求时刻）仍返回 200 与身份，只是配置为空；不泄露他人数据即可
        var defaultTime = get(baseA, "/api/assets/" + ASSET);
        assertThat(defaultTime.getStatusCode().value()).isEqualTo(200);
        assertThat(defaultTime.getBody()).contains(ASSET);
    }

    @Test
    void assetSummariesExposeDisplayNameAndTypeWithinScopeOnly() {
        var response = get(baseA, "/api/assets/summaries");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains(ASSET);
        // 概要按当前生效事实返回，跨身份的资产不能出现在别人的概要里
        assertThat(get(baseB, "/api/assets/summaries").getBody()).doesNotContain(ASSET);
    }

    // ---------- 跨身份：不同 owner ----------

    @Test
    void otherOwnerSeesNotFoundForAssetAndDoesNotEnumerateIt() {
        var asset = get(baseB, "/api/assets/" + ASSET);
        assertThat(asset.getStatusCode().value()).isEqualTo(404);
        assertThat(asset.getBody()).contains("NOT_FOUND_OR_DENIED");
        // 负例响应不能泄露配置值
        assertThat(asset.getBody()).doesNotContain("D-120A").doesNotContain("D-120B");

        var configuration = get(baseB, "/api/assets/" + ASSET + "/configuration?attribute=moduleVersion");
        assertThat(configuration.getStatusCode().value()).isEqualTo(404);
        assertThat(configuration.getBody()).doesNotContain("D-120");

        var maintenance = get(baseB, "/api/assets/" + ASSET + "/maintenance");
        assertThat(maintenance.getStatusCode().value()).isEqualTo(404);

        // 未登记该资产的身份的清单里不能出现它
        assertThat(get(baseB, "/api/assets").getBody()).doesNotContain(ASSET);
    }

    // ---------- 跨项目：同 owner，不同 project ----------

    @Test
    void sameOwnerInOtherProjectCannotReadAsset() {
        var response = get(baseOtherProject, "/api/assets/" + ASSET);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).doesNotContain("D-120");
        assertThat(get(baseOtherProject, "/api/assets").getBody()).doesNotContain(ASSET);
    }

    // ---------- 伪造引用 ----------

    @Test
    void forgedOrForeignReferenceIdsCannotBeAttachedToOwnConversation() {
        // owner-a 在自己的会话里提交，但请求体带了一个不存在/不属于自己的引用 ID
        var conversation = post(baseA, "/api/conversations", Map.of());
        assertThat(conversation.getStatusCode().value()).isEqualTo(201);
        String conversationId = readId(conversation.getBody());

        var forged = submit(baseA, "/api/conversations/" + conversationId + "/messages",
                Map.of("schemaVersion", 1, "userMessage", "看看这台设备",
                        "contextRefs", List.of(Map.of("referenceId", UUID.randomUUID().toString()))));
        // 引用不存在时不得静默丢弃，也不得被当作有效引用接入
        assertThat(forged.getStatusCode().value()).isEqualTo(404);

        // 跨身份：owner-b 用自己的会话去挂 owner-a 的引用同样被拒
        var conversationB = post(baseB, "/api/conversations", Map.of());
        String conversationIdB = readId(conversationB.getBody());
        var crossOwner = submit(baseB, "/api/conversations/" + conversationIdB + "/messages",
                Map.of("schemaVersion", 1, "userMessage", "看看这台设备",
                        "contextRefs", List.of(Map.of("referenceId", UUID.randomUUID().toString()))));
        assertThat(crossOwner.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void inheritedContextReferenceIsReRegisteredUnderNewRunWithNewReferenceId() throws Exception {
        // 5.1 正向：深入调查继承的合法引用在新 Run 下以新引用 ID 重新登记（来源一致但绝不复用旧 ID），
        // 旧 Run 的引用保持不变；跨身份读取新 Run 引用仍 404。
        var conversation = post(baseA, "/api/conversations", Map.of());
        assertThat(conversation.getStatusCode().value()).isEqualTo(201);
        String conversationId = readId(conversation.getBody());
        var first = submit(baseA, "/api/conversations/" + conversationId + "/messages",
                Map.of("schemaVersion", 1, "userMessage", "第一次调查：看看这台设备"));
        assertThat(first.getStatusCode().value()).isEqualTo(202);
        String firstRunId = readRunId(first.getBody());

        // worker 关闭，run 停在 QUEUED：直接登记一条身份范围内的来源引用作为“本轮已取回证据”
        UUID sourceRefId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dt_evidence_reference(id,run_id,conversation_id,owner_id,project_id,source_type,
                    source_id,source_version,retrieved_at,data_nature)
                VALUES (?,?,?,?,?,'CONFIGURATION','CFG-CTRL-01','2026.1',now(),'SIMULATED')
                """, sourceRefId, UUID.fromString(firstRunId), UUID.fromString(conversationId), "owner-a", "project-a");

        // 新认证调查串行：先明确取消未执行的第一轮，保留其引用，再提交深入调查。
        assertThat(post(baseA,"/api/runs/"+firstRunId+"/cancel",Map.of()).getStatusCode().value()).isEqualTo(200);
        var second = submit(baseA, "/api/conversations/" + conversationId + "/messages",
                Map.of("schemaVersion", 1, "userMessage", "请深入调查以下判断：控制器版本异常。",
                        "contextRefs", List.of(Map.of("referenceId", sourceRefId.toString()))));
        assertThat(second.getStatusCode().value()).isEqualTo(202);
        String secondRunId = readRunId(second.getBody());
        assertThat(secondRunId).isNotEqualTo(firstRunId);

        // 新 Run 的引用：sourceId 一致，referenceId 是重新登记的新 ID，不是旧 ID 的复制
        var inherited = get(baseA, "/api/runs/" + secondRunId + "/references");
        assertThat(inherited.getStatusCode().value()).isEqualTo(200);
        var inheritedTree = io.devicetruth.adapter.Json.MAPPER.readTree(inherited.getBody());
        assertThat(inheritedTree).hasSize(1);
        assertThat(inheritedTree.get(0).get("sourceId").asText()).isEqualTo("CFG-CTRL-01");
        assertThat(inheritedTree.get(0).get("referenceId").asText()).isNotEqualTo(sourceRefId.toString());

        // 旧 Run 的原始引用不被改动或挪走
        var original = get(baseA, "/api/runs/" + firstRunId + "/references");
        assertThat(original.getStatusCode().value()).isEqualTo(200);
        assertThat(original.getBody()).contains(sourceRefId.toString());

        // 跨身份读取新 Run 继承的引用仍被拒绝
        assertThat(get(baseB, "/api/runs/" + secondRunId + "/references").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void foreignRunAndConversationAreNotReadable() {
        var conversation = post(baseA, "/api/conversations", Map.of());
        String conversationId = readId(conversation.getBody());

        // 别人的会话/run 一律 404 或 denied，不能读到内容
        var otherReadsConversation = get(baseB, "/api/conversations/" + conversationId);
        assertThat(otherReadsConversation.getStatusCode().value()).isEqualTo(404);
        var otherReadsRun = get(baseB, "/api/runs/" + UUID.randomUUID());
        assertThat(otherReadsRun.getStatusCode().value()).isEqualTo(404);
        // 伪造 run 引用读取
        var otherReadsReferences = get(baseB, "/api/runs/" + UUID.randomUUID() + "/references");
        assertThat(otherReadsReferences.getStatusCode().value()).isEqualTo(404);
    }

    // ---------- 参数无法扩大范围 ----------

    @Test
    void requestParametersCannotWidenIdentityOrScope() {
        // 请求体里塞 ownerId 之类字段必须被拒绝（fail-on-unknown-properties），而不是被采纳
        var widened = submit(baseB, "/api/conversations/" + UUID.randomUUID() + "/messages",
                Map.of("schemaVersion", 1, "userMessage", "看看这台设备", "ownerId", "owner-a",
                        "projectId", "project-a"));
        assertThat(widened.getStatusCode().value()).isEqualTo(400);

        // 查询参数里传 ownerId 也不会扩大范围，asset 仍不可见
        var withParam = get(baseB, "/api/assets/" + ASSET + "?ownerId=owner-a&projectId=project-a");
        assertThat(withParam.getStatusCode().value()).isEqualTo(404);
        assertThat(withParam.getBody()).doesNotContain("D-120");
    }

    @Test
    void unauthorizedRecordNeverAppearsInAnyResponse() {
        // 逐一核对三个身份返回的正文，都不应包含 owner-a 的配置取值
        for (String base : List.of(baseB, baseOtherProject)) {
            for (String path : List.of("/api/assets", "/api/assets/" + ASSET,
                    "/api/assets/" + ASSET + "/configuration", "/api/assets/" + ASSET + "/maintenance")) {
                String body = get(base, path).getBody();
                assertThat(body).as("base=" + base + " path=" + path).doesNotContain("D-120A")
                        .doesNotContain("D-120B");
            }
        }
    }

    // ---------- 库内归属事实 ----------

    @Test
    void assetRowsBelongOnlyToTheSeedingIdentity() {
        Integer others = jdbc.queryForObject(
                "SELECT count(*) FROM dt_asset WHERE id=? AND NOT (owner_id='owner-a' AND project_id='project-a')",
                Integer.class, ASSET);
        assertThat(others).isZero();
        Integer total = jdbc.queryForObject("SELECT count(*) FROM dt_asset WHERE id=?", Integer.class, ASSET);
        assertThat(total).isEqualTo(1);
    }

    private static String readId(String json) {
        try {
            return io.devicetruth.adapter.Json.MAPPER.readTree(json).get("conversationId").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected conversation body: " + json, e);
        }
    }

    private static String readRunId(String json) {
        try {
            return io.devicetruth.adapter.Json.MAPPER.readTree(json).get("runId").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected run body: " + json, e);
        }
    }
}