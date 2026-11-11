package io.devicetruth.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.server.DeviceTruthServerApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
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
 * 真实 PostgreSQL + pgvector 下的知识导入/检索/引用合同。
 * embedding 使用确定性字符向量（仅证明合同，不证明语义效果）；真实供应商验收由 02-03 执行。
 */
class KnowledgeSearchTest {
    private static final String EMBEDDING_PROFILE = "deterministic:deterministic-char2@1024-cosine-r1";
    private static final String MANUAL_TITLE = "充电系统检修手册（演示资料）";
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
        app = open("owner-a", "project-a", 1024);
        otherApp = open("owner-b", "project-a", 1024);
        base = base(app);
        otherBase = base(otherApp);
        rest = new TestRestTemplate();
        jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
    }

    @AfterAll static void stop() throws Exception {
        if (app != null) app.close();
        if (otherApp != null) otherApp.close();
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

    private static ConfigurableApplicationContext open(String owner, String project, int dimension) {
        return openWith(owner, project, EmbeddingProfile.PROVIDER_DETERMINISTIC, dimension);
    }

    private static ConfigurableApplicationContext openWith(String owner, String project, String provider,
                                                           int dimension) {
        List<String> arguments = new java.util.ArrayList<>(List.of(
                "--spring.profiles.active=product", "--server.port=0", "--debug=false",
                "--logging.level.org.springframework=WARN", "--logging.level.org.springframework.jdbc=INFO",
                "--devicetruth.core.reasoner=scripted",
                "--spring.datasource.url=" + databaseBaseUrl + databaseName,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=" + owner, "--devicetruth.product.project-id=" + project,
                // 本用例只验证导入/检索/引用合同，Agent worker 关闭，避免并发检索干扰引用归属断言。
                "--devicetruth.agent.worker-enabled=false",
                "--devicetruth.embedding.provider=" + provider,
                "--devicetruth.embedding.dimension=" + dimension,
                "--devicetruth.embedding.profile-revision=1"));
        if (EmbeddingProfile.PROVIDER_DETERMINISTIC.equals(provider))
            arguments.add("--devicetruth.embedding.model=deterministic-char2");
        return new SpringApplicationBuilder(DeviceTruthServerApplication.class)
                .run(arguments.toArray(String[]::new));
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static HttpHeaders jsonHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> post(String url, Object body) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private static String manualText() throws Exception {
        return Files.readString(Path.of("datasets/knowledge/charging-system-manual.md"), StandardCharsets.UTF_8);
    }

    private static final String TXT_MANUAL = """
            充电系统定期维护说明（演示资料，SIMULATED）

            直流快充中断时，先读取充电桩故障日志并记录中止时间，再确认中止方是桩侧还是车侧。

            若电池温度过高，电池管理系统会请求降低充电功率并最终中止充电。

            交流慢充无法启动时，检查充电枪锁止状态与交流充电桩的授权记录。
            """;

    private String runId(String baseUrl) throws Exception {
        var headers = jsonHeaders();
        var conversation = rest.exchange(baseUrl + "/api/conversations", HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers), String.class);
        assertThat(conversation.getStatusCode().value()).isEqualTo(201);
        String conversationId = Json.MAPPER.readTree(conversation.getBody()).path("conversationId").asText();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var accepted = rest.exchange(baseUrl + "/api/conversations/" + conversationId + "/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("schemaVersion", 1, "userMessage", "充电中断但没有故障码，先查哪些资料？"),
                        headers), String.class);
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        return Json.MAPPER.readTree(accepted.getBody()).path("runId").asText();
    }

    private static ToolContext context(String runId, String assetId) {
        return new ToolContext("owner-a", "project-a", null, UUID.fromString(runId), assetId, Instant.now(), null,
                Set.of(KnowledgeTools.SEARCH, KnowledgeTools.EXCERPT));
    }

    private JsonNode importDocument(String baseUrl, String title, String mimeType, String content, String version)
            throws Exception {
        var response = post(baseUrl + "/api/knowledge/documents", Map.of(
                "title", title, "domain", "automotive-demo", "version", version,
                "filename", mimeType.equals("text/plain") ? "manual.txt" : "manual.md",
                "mimeType", mimeType, "content", content));
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return Json.MAPPER.readTree(response.getBody()).path("document");
    }

    private JsonNode manual() throws Exception {
        return importDocument(base, MANUAL_TITLE, "text/markdown", manualText(), "2026.1");
    }

    private JsonNode search(String baseUrl, String runId, String query, Map<String, Object> filters, Integer limit)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId);
        body.put("query", query);
        if (filters != null) body.put("filters", filters);
        if (limit != null) body.put("limit", limit);
        var response = post(baseUrl + "/api/knowledge/search", body);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return Json.MAPPER.readTree(response.getBody());
    }

    @Test void markdownManualImportsChunksWithSectionsVersionAndVectors() throws Exception {
        var document = manual();
        String documentId = document.path("documentId").asText();
        UUID id = UUID.fromString(documentId);
        assertThat(document.path("embeddingProfile").asText()).isEqualTo(EMBEDDING_PROFILE);
        assertThat(document.path("embeddingDimension").asInt()).isEqualTo(1024);
        assertThat(document.path("chunkingProfile").asText()).isEqualTo(KnowledgeChunker.PROFILE);
        assertThat(document.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(document.path("ingestStatus").asText()).isEqualTo("READY");
        int chunkCount = document.path("chunkCount").asInt();
        assertThat(chunkCount).isGreaterThanOrEqualTo(4);

        var detail = rest.getForEntity(base + "/api/knowledge/documents/" + documentId, String.class);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(Json.MAPPER.readTree(detail.getBody())).isEqualTo(document);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM dt_knowledge_chunk WHERE document_id=? AND vector_dims(embedding)=1024",
                Integer.class, id)).isEqualTo(chunkCount);
        List<String> sections = jdbc.queryForList(
                "SELECT section FROM dt_knowledge_chunk WHERE document_id=? ORDER BY chunk_no", String.class, id);
        assertThat(sections).hasSize(chunkCount).allMatch(section -> section != null && !section.isBlank());
        assertThat(sections).anyMatch(section -> section.contains("直流快充中断"));
        assertThat(sections).anyMatch(section -> section.contains("交流慢充无法启动"));

        assertThat(jdbc.queryForObject("SELECT doc_version FROM dt_knowledge_document WHERE id=?", String.class, id))
                .isEqualTo("2026.1");
        assertThat(jdbc.queryForObject("SELECT data_nature FROM dt_knowledge_document WHERE id=?", String.class, id))
                .isEqualTo("SIMULATED");
        assertThat(jdbc.queryForObject("""
                SELECT f.checksum FROM dt_uploaded_file f JOIN dt_knowledge_document d ON d.source_file_id=f.id
                WHERE d.id=?
                """, String.class, id)).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(jdbc.queryForObject("""
                SELECT f.path_key FROM dt_uploaded_file f JOIN dt_knowledge_document d ON d.source_file_id=f.id
                WHERE d.id=?
                """, String.class, id)).startsWith("knowledge/");
        // 原始文件不写入数据库：文档表没有正文/大 JSONB 列，切片长度受控。
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name='dt_knowledge_document' AND (data_type='jsonb' OR column_name='content')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT max(char_length(content)) FROM dt_knowledge_chunk WHERE document_id=?", Integer.class, id))
                .isLessThanOrEqualTo(480);

        // 相同内容 + 相同版本重复导入不新增文档或切片，返回 reused=true。
        var again = post(base + "/api/knowledge/documents", Map.of(
                "title", MANUAL_TITLE, "domain", "automotive-demo", "version", "2026.1",
                "filename", "manual.md", "mimeType", "text/markdown", "content", manualText()));
        assertThat(again.getStatusCode().value()).isEqualTo(201);
        assertThat(Json.MAPPER.readTree(again.getBody()).path("reused").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE checksum=("
                + "SELECT checksum FROM dt_knowledge_document WHERE id=?)", Integer.class, id)).isEqualTo(1);
    }

    @Test void txtManualImportsAndChargingQueryHitsLocatableSection() throws Exception {
        var document = importDocument(base, "充电系统定期维护说明", "text/plain", TXT_MANUAL, "2026.1");
        String documentId = document.path("documentId").asText();
        String runId = runId(base);
        var result = search(base, runId, "直流快充中断时电池温度过高会降低充电功率吗", null, 5);
        assertThat(result.path("empty").asBoolean()).isFalse();
        var hits = result.path("hits");
        assertThat(hits.size()).isGreaterThan(0);
        JsonNode matched = null;
        for (JsonNode hit : hits) if (hit.path("documentId").asText().equals(documentId)) matched = hit;
        assertThat(matched).as("TXT 手册必须被该语义查询命中").isNotNull();
        assertThat(matched.path("similarity").asDouble()).isGreaterThanOrEqualTo(0.15);
        assertThat(matched.path("page").isNull()).isTrue();
        assertThat(matched.path("section").asText()).isNotBlank();
        assertThat(matched.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(UUID.fromString(matched.path("referenceId").asText())).isNotNull();

        // documentId 过滤在候选集过滤：断言只返回本文档且首选就是它。
        var filtered = search(base, runId, "直流快充中断时电池温度过高会降低充电功率吗",
                Map.of("documentId", documentId), 5);
        assertThat(filtered.path("hits").get(0).path("documentId").asText()).isEqualTo(documentId);
        for (JsonNode hit : filtered.path("hits")) {
            assertThat(hit.path("documentId").asText()).isEqualTo(documentId);
        }
    }

    @Test void configuredDimensionMustMatchIndexColumn() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document", Integer.class);
        try (var mismatched = open("owner-a", "project-a", 1536)) {
            String mismatchedBase = base(mismatched);
            var rejected = post(mismatchedBase + "/api/knowledge/documents", Map.of(
                    "title", "维度不符的导入", "domain", "automotive-demo", "version", "2026.1",
                    "filename", "dimension.md", "mimeType", "text/markdown",
                    "content", "# 维度检查\n\n这段内容不应被写入。"));
            assertThat(rejected.getStatusCode().value()).isEqualTo(409);
            assertThat(rejected.getBody()).contains("EMBEDDING_DIMENSION_MISMATCH");
            // 检索同样先校验维度：错误维度不能拿 768 维查询去比对 1024 维索引。
            String mismatchedRun = runId(mismatchedBase);
            var rejectedSearch = post(mismatchedBase + "/api/knowledge/search",
                    Map.of("runId", mismatchedRun, "query", "直流快充中断"));
            assertThat(rejectedSearch.getStatusCode().value()).isEqualTo(409);
            assertThat(rejectedSearch.getBody()).contains("EMBEDDING_DIMENSION_MISMATCH");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document", Integer.class)).isEqualTo(before);

        // 数据库列维度是第二道闸：绕过服务层直接写错误维度同样失败。
        var document = manual();
        UUID documentId = UUID.fromString(document.path("documentId").asText());
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO dt_knowledge_chunk(id,document_id,owner_id,project_id,chunk_no,content,section,embedding,
                    embedding_profile)
                VALUES (?,?,'owner-a','project-a',999,'错误维度', 'section', CAST('[1,2,3]' AS vector), ?)
                """, UUID.randomUUID(), documentId, EMBEDDING_PROFILE)).isInstanceOf(DataAccessException.class);
    }

    @Test void embeddingFailureDoesNotLeavePartialDocument() {
        var dataSource = app.getBean(HikariDataSource.class);
        var profile = EmbeddingProfile.of("deterministic", "", "", "deterministic-char2", 1024,
                "1", 1000, false);
        var stable = new DeterministicEmbeddingClient(profile);
        var failing = new EmbeddingClient() {
            private int calls;
            @Override public EmbeddingProfile profile() { return profile; }
            @Override public float[] embed(String text) {
                if (++calls == 2) throw new EmbeddingException("EMBEDDING_UNAVAILABLE");
                return stable.embed(text);
            }
        };
        var service = new KnowledgeImportService(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), failing, 120);
        var request = new KnowledgeImportService.ImportRequest("回滚检查手册", "automotive-demo", null, "2026.1",
                "rollback.md", "text/markdown",
                "# 回滚检查\n\n" + "充电中断排查段落。".repeat(40) + "\n\n" + "直流快充排查段落。".repeat(40),
                "SIMULATED");
        assertThatThrownBy(() -> service.importDocument("owner-a", "project-a", request))
                .isInstanceOf(KnowledgeException.class)
                .hasMessageContaining("EMBEDDING_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE title='回滚检查手册'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_chunk WHERE content LIKE '充电中断排查段落%'",
                Integer.class)).isZero();
    }

    @Test void chineseQueryAndNestedFiltersFollowStrongTypedToolSchema() throws Exception {
        manual();
        var tools = app.getBean(KnowledgeTools.class);
        String runId = runId(base);
        var outcome = tools.search(Map.of(
                "query", "  直流 快充 中断，充电桩日志  ", "filters", Map.of("domain", "automotive-demo"),
                "limit", 3), context(runId, "CAR-B"));
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.hits()).isNotEmpty();
        assertThat(outcome.hits().getFirst().similarity()).isGreaterThanOrEqualTo(0.15);
        assertThat(jdbc.queryForObject("SELECT asset_id FROM dt_evidence_reference WHERE id=?", String.class,
                outcome.hits().getFirst().referenceId())).isEqualTo("CAR-B");

        var typed = tools.search(new KnowledgeTools.SearchRequest("直流快充中断", null, null), context(runId, null));
        assertThat(typed.success()).isTrue();
        assertThat(typed.hits()).isNotEmpty();

        assertThat(tools.search(Map.of("query", "充电", "ownerId", "owner-b"), context(runId, null)).error().code())
                .isEqualTo("INVALID_ARGUMENTS");
        assertThat(tools.search(Map.of("query", "充电", "filters", Map.of("permission", "ALL")),
                context(runId, null)).error().code()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(tools.search(Map.of("query", "充电", "filters", Map.of("documentId", "not-a-uuid")),
                context(runId, null)).error().code()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(tools.search(Map.of("query", "充电", "limit", 0), context(runId, null)).error().code())
                .isEqualTo("INVALID_ARGUMENTS");
        assertThat(tools.search(Map.of("query", " \n "), context(runId, null)).error().code())
                .isEqualTo("INVALID_ARGUMENTS");
        var noTools = new ToolContext("owner-a", "project-a", null, UUID.fromString(runId), null, Instant.now(), null,
                Set.of());
        assertThat(tools.search(Map.of("query", "充电"), noTools).error().code()).isEqualTo("TOOL_NOT_ALLOWED");
    }

    @Test void descriptorsUseNaturalLanguageQueryAndStrictIdentifiers() throws Exception {
        manual();
        var descriptors = app.getBean(KnowledgeTools.class).descriptors();
        assertThat(descriptors).extracting(KnowledgeTools.ToolDescriptor::name)
                .containsExactly(KnowledgeTools.SEARCH, KnowledgeTools.EXCERPT);
        assertThat(descriptors).allMatch(KnowledgeTools.ToolDescriptor::readOnly);
        @SuppressWarnings("unchecked")
        Map<String, Object> searchProperties = (Map<String, Object>) descriptors.get(0).inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) searchProperties.get("query");
        assertThat(query).containsEntry("type", "string").doesNotContainKey("pattern");
        assertThat(query.get("description").toString()).contains("中文");
        @SuppressWarnings("unchecked")
        Map<String, Object> filters = (Map<String, Object>) searchProperties.get("filters");
        assertThat(filters).containsEntry("additionalProperties", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> filterProperties = (Map<String, Object>) filters.get("properties");
        assertThat(filterProperties).containsOnlyKeys("domain", "deviceModel", "documentId");
        @SuppressWarnings("unchecked")
        Map<String, Object> excerptProperties =
                (Map<String, Object>) descriptors.get(1).inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> documentId = (Map<String, Object>) excerptProperties.get("documentId");
        assertThat(documentId).containsEntry("format", "uuid");
        assertThat(descriptors.get(1).inputSchema().get("required")).isEqualTo(List.of("documentId", "chunkId"));

        // legacy 网关目录仍只有 5 个结构化能力：知识工具接入 Runtime 是 02-02 的交付。
        assertThat(app.getBean(io.devicetruth.core.capability.CapabilityGateway.class).catalog())
                .extracting(io.devicetruth.core.capability.CapabilityTypes.Descriptor::id)
                .doesNotContain(KnowledgeTools.SEARCH, KnowledgeTools.EXCERPT);
    }

    @Test void otherOwnerOrProjectDocumentsNeverReachCandidatesOrText() throws Exception {
        String secret = "电池热失控处置：owner-b 专属资料，记录热失控预警阈值与现场处置步骤。";
        var otherDocument = importDocument(otherBase, "owner-b 专属手册", "text/plain", secret, "2026.1");
        String otherDocumentId = otherDocument.path("documentId").asText();
        String otherRun = runId(otherBase);
        var otherSearch = search(otherBase, otherRun, "电池热失控预警阈值与处置步骤", null, 5);
        assertThat(otherSearch.path("hits").size()).isGreaterThan(0);
        String otherReference = otherSearch.path("hits").get(0).path("referenceId").asText();
        String otherChunk = otherSearch.path("hits").get(0).path("chunkId").asText();

        String runId = runId(base);
        var isolated = search(base, runId, "电池热失控预警阈值与处置步骤", null, 5);
        assertThat(isolated.path("empty").asBoolean()).isTrue();
        assertThat(isolated.path("hits").size()).isZero();
        assertThat(isolated.toString()).doesNotContain("热失控").doesNotContain(secret);

        assertThat(rest.getForEntity(base + "/api/knowledge/documents/" + otherDocumentId, String.class)
                .getStatusCode().value()).isEqualTo(404);
        var denied = rest.getForEntity(base + "/api/knowledge/references/" + otherReference + "?runId=" + runId,
                String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);
        assertThat(denied.getBody()).contains("NOT_FOUND_OR_DENIED").doesNotContain("热失控");

        // 复合外键阻止把其他 owner 的切片登记成当前身份 run 的引用。
        UUID otherRunId = UUID.fromString(otherRun);
        UUID otherConversation = jdbc.queryForObject("SELECT conversation_id FROM dt_agent_run WHERE id=?",
                UUID.class, otherRunId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO dt_evidence_reference(id,run_id,conversation_id,owner_id,project_id,source_type,
                    source_id,source_version,chunk_id,retrieved_at,data_nature)
                VALUES (?,?,?,'owner-a','project-a','KNOWLEDGE_DOCUMENT',?,?,?,now(),'SIMULATED')
                """, UUID.randomUUID(), otherRunId, otherConversation, otherDocumentId, "2026.1",
                UUID.fromString(otherChunk))).isInstanceOf(DataAccessException.class);
    }

    @Test void fabricatedOrUncollectedChunkCannotBeOpened() throws Exception {
        var document = manual();
        UUID documentId = UUID.fromString(document.path("documentId").asText());
        UUID notCollected = jdbc.queryForObject("""
                SELECT id FROM dt_knowledge_chunk WHERE document_id=? AND chunk_no=1
                """, UUID.class, documentId);
        String runId = runId(base);
        var tools = app.getBean(KnowledgeTools.class);
        var context = context(runId, null);

        var random = tools.excerpt(Map.of("documentId", UUID.randomUUID().toString(),
                "chunkId", UUID.randomUUID().toString()), context);
        assertThat(random.success()).isFalse();
        assertThat(random.error().code()).isEqualTo("NOT_FOUND_OR_DENIED");

        // 本 owner 可读但本 run 未取回：拒绝并标记 INVALID_CITATION，不泄漏正文。
        var uncollected = tools.excerpt(Map.of("documentId", documentId.toString(),
                "chunkId", notCollected.toString()), context);
        assertThat(uncollected.success()).isFalse();
        assertThat(uncollected.error().code()).isEqualTo("INVALID_CITATION");
        assertThat(uncollected.excerpt()).isNull();

        // 伪造 documentId 与真实 chunkId 组合同样拒绝。
        var forged = tools.excerpt(Map.of("documentId", UUID.randomUUID().toString(),
                "chunkId", notCollected.toString()), context);
        assertThat(forged.success()).isFalse();
        assertThat(forged.error().code()).isEqualTo("NOT_FOUND_OR_DENIED");

        var searched = tools.search(Map.of("query", "直流快充中断"), context);
        assertThat(searched.success()).isTrue();
        var hit = searched.hits().getFirst();
        var excerpt = tools.excerpt(Map.of("documentId", hit.documentId().toString(),
                "chunkId", hit.chunkId().toString()), context);
        assertThat(excerpt.success()).isTrue();
        assertThat(excerpt.excerpt().content()).isEqualTo(hit.content());
        assertThat(excerpt.excerpt().documentVersion()).isEqualTo(hit.documentVersion());
    }

    @Test void searchRegistersReferenceAndExcerptRebuildsOriginalSection() throws Exception {
        manual();
        String runId = runId(base);
        var tools = app.getBean(KnowledgeTools.class);
        var outcome = tools.search(Map.of("query", "直流快充中断 充电桩日志"), context(runId, null));
        assertThat(outcome.success()).isTrue();
        var hit = outcome.hits().getFirst();
        UUID referenceId = hit.referenceId();

        var row = jdbc.queryForMap("SELECT * FROM dt_evidence_reference WHERE id=?", referenceId);
        assertThat(row.get("run_id")).isEqualTo(UUID.fromString(runId));
        assertThat(row.get("source_type")).isEqualTo("KNOWLEDGE_DOCUMENT");
        assertThat(row.get("source_id")).isEqualTo(hit.documentId().toString());
        assertThat(row.get("source_version")).isEqualTo(hit.documentVersion());
        assertThat(row.get("chunk_id")).isEqualTo(hit.chunkId());
        assertThat(row.get("citation_section")).isEqualTo(hit.section());
        assertThat(row.get("data_nature")).isEqualTo("SIMULATED");

        int references = references(runId);
        var repeated = tools.search(Map.of("query", "直流快充中断 充电桩日志"), context(runId, null));
        assertThat(repeated.hits().getFirst().referenceId()).isEqualTo(referenceId);
        assertThat(references(runId)).isEqualTo(references);

        var response = rest.getForEntity(base + "/api/knowledge/references/" + referenceId + "?runId=" + runId,
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("content").asText()).isEqualTo(hit.content());
        assertThat(body.path("section").asText()).isEqualTo(hit.section());
        assertThat(body.path("documentVersion").asText()).isEqualTo(hit.documentVersion());
        assertThat(body.path("referenceId").asText()).isEqualTo(referenceId.toString());

        String anotherRun = runId(base);
        var wrongRun = rest.getForEntity(base + "/api/knowledge/references/" + referenceId + "?runId=" + anotherRun,
                String.class);
        assertThat(wrongRun.getStatusCode().value()).isEqualTo(409);
        assertThat(wrongRun.getBody()).contains("INVALID_CITATION");
        assertThat(rest.getForEntity(base + "/api/knowledge/references/" + UUID.randomUUID() + "?runId=" + runId,
                String.class).getStatusCode().value()).isEqualTo(404);
    }

    @Test void emptySearchReturnsNoHitsAndRegistersNothing() throws Exception {
        manual();
        String runId = runId(base);
        var tools = app.getBean(KnowledgeTools.class);
        int before = references(runId);
        var outcome = tools.search(Map.of("query", "咖啡机 滤网 清洁日程"), context(runId, null));
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.empty()).isTrue();
        assertThat(outcome.hits()).isEmpty();
        assertThat(references(runId)).isEqualTo(before);
        var response = search(base, runId, "咖啡机 滤网 清洁日程", null, 5);
        assertThat(response.path("empty").asBoolean()).isTrue();
        assertThat(response.path("hits").size()).isZero();
    }

    @Test void switchingOnlyProviderCannotSilentlyReuseImportedDocument() throws Exception {
        manual();
        int before = jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document", Integer.class);
        try (var openAi = openWith("owner-a", "project-a", EmbeddingProfile.PROVIDER_OPENAI, 1024)) {
            var response = post(base(openAi) + "/api/knowledge/documents", Map.of(
                    "title", MANUAL_TITLE, "domain", "automotive-demo", "version", "2026.1",
                    "filename", "manual.md", "mimeType", "text/markdown", "content", manualText()));
            // 档案（provider/model/维度/修订）不同：必须明确拒绝，不能返回旧文档的 reused=true。
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody()).contains("EMBEDDING_PROFILE_CONFLICT")
                    .doesNotContain("DASHSCOPE");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document", Integer.class)).isEqualTo(before);
    }

    @Test void reimportWithNewProfileOrDimensionIsRejectedInsteadOfReused() {
        String content = "# 档案隔离检查\n\n" + "充电中断独立排查段落。".repeat(40) + "\n\n"
                + "直流快充独立排查段落。".repeat(40);
        var request = new KnowledgeImportService.ImportRequest("档案隔离检查手册", "automotive-demo", null, "2026.1",
                "profile-check.md", "text/markdown", content, "SIMULATED");
        var baseProfile = EmbeddingProfile.of("deterministic", "", "", "deterministic-char2", 1024, "1", 1000, false);
        var service = importer(new DeterministicEmbeddingClient(baseProfile), 200);
        assertThat(service.importDocument("owner-a", "project-a", request).reused()).isFalse();
        int documents = jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE title='档案隔离检查手册'",
                Integer.class);
        assertThat(service.importDocument("owner-a", "project-a", request).reused()).isTrue();

        // 同模型但档案修订不同：标签不同，不能复用。
        var revisionTwo = EmbeddingProfile.of("deterministic", "", "", "deterministic-char2", 1024, "2", 1000, false);
        assertThatThrownBy(() -> importer(new DeterministicEmbeddingClient(revisionTwo), 200)
                .importDocument("owner-a", "project-a", request))
                .isInstanceOf(KnowledgeException.class)
                .hasMessageContaining("EMBEDDING_PROFILE_CONFLICT");

        // 同维度不同模型：标签不同，不能复用。
        var otherModel = EmbeddingProfile.of("deterministic", "", "", "deterministic-other", 1024, "1", 1000, false);
        assertThatThrownBy(() -> importer(new DeterministicEmbeddingClient(otherModel), 200)
                .importDocument("owner-a", "project-a", request))
                .isInstanceOf(KnowledgeException.class)
                .hasMessageContaining("EMBEDDING_PROFILE_CONFLICT");

        // 维度与索引列不符：在复用判断之前失败，不能被“文档已存在”掩盖。
        var wrongDimension = EmbeddingProfile.of("deterministic", "", "", "deterministic-char2", 768, "1", 1000, false);
        assertThatThrownBy(() -> importer(new DeterministicEmbeddingClient(wrongDimension), 200)
                .importDocument("owner-a", "project-a", request))
                .isInstanceOf(KnowledgeException.class)
                .hasMessageContaining("EMBEDDING_DIMENSION_MISMATCH");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE title='档案隔离检查手册'",
                Integer.class)).isEqualTo(documents);
    }

    @Test void searchNeverTrustsProfileLabelAlone() throws Exception {
        var client = app.getBean(EmbeddingClient.class);
        EmbeddingProfile profile = client.profile();
        String unique = "档案标签伪造检查：仅标签相同但模型字段不同的切片不应被召回。";
        String vector = VectorLiteral.of(client.embed(unique));
        UUID forgedDocument = insertDocument(profile.profileVersion(), "forged-model", 1024, unique, vector);
        UUID legitDocument = insertDocument(profile.profileVersion(), profile.model(), 1024, unique + "（合法）",
                VectorLiteral.of(client.embed(unique + "（合法）")));

        String runId = runId(base);
        var result = search(base, runId, unique, null, 5);
        for (JsonNode hit : result.path("hits")) {
            assertThat(hit.path("documentId").asText()).isNotEqualTo(forgedDocument.toString());
        }
        var legit = search(base, runId, unique + "（合法）", null, 5);
        assertThat(legit.path("hits").size()).isGreaterThan(0);
        assertThat(legit.path("hits").get(0).path("documentId").asText()).isEqualTo(legitDocument.toString());
    }

    private KnowledgeImportService importer(EmbeddingClient client, int maxChunkChars) {
        return new KnowledgeImportService(jdbc, app.getBean(TransactionTemplate.class), client, maxChunkChars);
    }

    /** 直接写一条测试文档：用于证明检索同时核对持久化的模型/维度，而不只看可配置的标签。 */
    private UUID insertDocument(String profileLabel, String model, int dimension, String content, String vector) {
        UUID fileId = UUID.randomUUID();
        String checksum = (UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "")).substring(0, 64);
        jdbc.update("""
                INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,
                    parse_status,created_at)
                VALUES (?,'owner-a','project-a',?,'label-check.md','text/markdown',?,?, 'PARSED', now())
                """, fileId, "knowledge/" + fileId, checksum, content.getBytes(StandardCharsets.UTF_8).length);
        UUID documentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dt_knowledge_document(id,owner_id,project_id,title,domain,doc_version,source_file_id,
                    checksum,ingest_status,embedding_profile,embedding_model,embedding_dimension,chunking_profile,
                    chunk_count,data_nature,created_at)
                VALUES (?,'owner-a','project-a','标签检查手册','automotive-demo','2026.1',?,?,'READY',?,?,?,?,1,
                    'SIMULATED',now())
                """, documentId, fileId, checksum, profileLabel, model, dimension, KnowledgeChunker.PROFILE);
        jdbc.update("""
                INSERT INTO dt_knowledge_chunk(id,document_id,owner_id,project_id,chunk_no,content,section,embedding,
                    embedding_profile)
                VALUES (?,?,'owner-a','project-a',1,?,'标签检查',CAST(? AS vector),?)
                """, UUID.randomUUID(), documentId, content, vector, profileLabel);
        return documentId;
    }

    private int references(String runId) {
        return jdbc.queryForObject("SELECT count(*) FROM dt_evidence_reference WHERE run_id=?",
                Integer.class, UUID.fromString(runId));
    }
}
