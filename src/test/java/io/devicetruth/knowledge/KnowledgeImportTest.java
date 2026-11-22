package io.devicetruth.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.server.DeviceTruthServerApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;

/** 真实 PG 下的 Markdown 上传、原文件存储、失败状态、附件与历史引用授权回归。 */
class KnowledgeImportTest {
    @TempDir static Path fileRoot;
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static ConfigurableApplicationContext app;
    private static ConfigurableApplicationContext otherApp;
    private static ConfigurableApplicationContext otherProjectApp;
    private static ConfigurableApplicationContext otherProfileApp;
    private static TestRestTemplate rest;
    private static String base;
    private static String otherBase;
    private static String otherProjectBase;
    private static String otherProfileBase;
    private static JdbcTemplate jdbc;

    @BeforeAll static void start() throws Exception {
        databaseBaseUrl = requiredEnvironment("DEVICETRUTH_TEST_PG_BASE_URL");
        username = requiredEnvironment("DEVICETRUTH_TEST_PG_USERNAME");
        password = requiredEnvironment("DEVICETRUTH_TEST_PG_PASSWORD");
        databaseName = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(databaseBaseUrl + "postgres", username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        app = open("owner-a", "project-a", "1");
        otherApp = open("owner-b", "project-a", "1");
        otherProjectApp = open("owner-a", "project-b", "1");
        otherProfileApp = open("owner-a", "project-a", "2");
        base = base(app);
        otherBase = base(otherApp);
        otherProjectBase = base(otherProjectApp);
        otherProfileBase = base(otherProfileApp);
        rest = io.devicetruth.server.AuthenticatedProductTestSupport.client(app, otherApp, otherProjectApp, otherProfileApp);
        jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
    }

    @AfterAll static void stop() throws Exception {
        for (var context : List.of(app, otherApp, otherProjectApp, otherProfileApp)) if (context != null) context.close();
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

    private static ConfigurableApplicationContext open(String owner, String project, String profileRevision) {
        return new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--devicetruth.auth.cookie-secure=false", "--server.port=0", "--debug=false",
                "--logging.level.org.springframework=WARN", "--devicetruth.core.reasoner=scripted",
                "--devicetruth.agent.worker-enabled=false",
                "--spring.datasource.url=" + databaseBaseUrl + databaseName,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=" + owner, "--devicetruth.product.project-id=" + project,
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.embedding.dimension=1024",
                "--devicetruth.embedding.profile-revision=" + profileRevision,
                "--devicetruth.files.root=" + fileRoot.resolve(owner).toAbsolutePath());
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static ByteArrayResource file(String filename, byte[] bytes) {
        return new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        };
    }

    private static ResponseEntity<String> upload(String baseUrl, String filename, byte[] bytes, String mime) {
        return upload(baseUrl, filename, bytes, mime, "上传充电手册", "automotive-demo", "2026.2");
    }

    private static ResponseEntity<String> upload(String baseUrl, String filename, byte[] bytes, String mime,
                                                  String title, String domain, String version) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // RestTemplate 按 ByteArrayResource 推断 application/octet-stream；逐项改成服务端允许的声明 MIME。
        var multipart = new LinkedMultiValueMap<String, HttpEntity<?>>();
        multipart.add("file", new HttpEntity<>(file(filename, bytes), partHeaders(mime)));
        multipart.add("title", new HttpEntity<>(title, partHeaders("text/plain")));
        multipart.add("domain", new HttpEntity<>(domain, partHeaders("text/plain")));
        multipart.add("version", new HttpEntity<>(version, partHeaders("text/plain")));
        return rest.exchange(baseUrl + "/api/files", HttpMethod.POST,
                new HttpEntity<>(multipart, headers), String.class);
    }

    private static HttpHeaders partHeaders(String mime) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mime));
        return headers;
    }

    private static JsonNode json(ResponseEntity<String> response) throws Exception {
        return Json.MAPPER.readTree(response.getBody());
    }

    private JsonNode fileView(String baseUrl, String fileId) throws Exception {
        var response = rest.getForEntity(baseUrl + "/api/files/" + fileId, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response);
    }

    private JsonNode awaitFile(String baseUrl, String fileId, String status) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = fileView(baseUrl, fileId);
            if (status.equals(last.path("parseStatus").asText())) return last;
            Thread.sleep(50);
        }
        fail("文件未到达 " + status + "，最后状态=" + (last == null ? "null" : last.toString()));
        return last;
    }

    private String run(String baseUrl, String text, Map<String, Object> extra) throws Exception {
        var created = rest.postForEntity(baseUrl + "/api/conversations", Map.of(), String.class);
        String conversationId = json(created).path("conversationId").asText();
        Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("schemaVersion", 1);
        message.put("userMessage", text);
        if (extra != null) message.putAll(extra);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var accepted = rest.exchange(baseUrl + "/api/conversations/" + conversationId + "/messages", HttpMethod.POST,
                new HttpEntity<>(message, headers), String.class);
        assertThat(accepted.getStatusCode().value()).as(accepted.getBody()).isEqualTo(202);
        return json(accepted).path("runId").asText();
    }

    private JsonNode search(String runId, String query) throws Exception {
        var response = rest.postForEntity(base + "/api/knowledge/search",
                Map.of("runId", runId, "query", query, "limit", 5), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return json(response);
    }

    @Test void markdownUploadStoresControlledBytesAndSearchableSection() throws Exception {
        String content = "# 直流快充中断处置\n\n充电中止时先读取桩侧日志并记录中止时间。\n\n"
                + "## 表格上下文\n\n| 检查项 | 记录 |\n|---|---|\n| 中止方 | 桩侧或车侧 |\n";
        var accepted = upload(base, "charging.md", content.getBytes(StandardCharsets.UTF_8), "text/markdown");
        assertThat(accepted.getStatusCode().value()).as(accepted.getBody()).isEqualTo(202);
        String fileId = json(accepted).path("fileId").asText();
        JsonNode ready = awaitFile(base, fileId, "PARSED");
        assertThat(ready.path("failureCode").isNull()).isTrue();
        assertThat(ready.path("documentId").asText()).isNotBlank();
        assertThat(ready.path("documentVersion").asText()).isEqualTo("2026.2");
        String pathKey = jdbc.queryForObject("SELECT path_key FROM dt_uploaded_file WHERE id=?",
                String.class, UUID.fromString(fileId));
        Path stored = fileRoot.resolve("owner-a").resolve(pathKey).normalize();
        assertThat(stored).exists();
        assertThat(Files.readString(stored)).isEqualTo(content);
        assertThat(stored).startsWith(fileRoot.resolve("owner-a").toAbsolutePath());

        String runId = run(base, "请依据上传资料说明直流快充中断先检查什么", null);
        var result = search(runId, "充电中止时先读取桩侧日志并记录中止时间");
        assertThat(result.path("hits").size()).isPositive();
        var hit = result.path("hits").get(0);
        assertThat(hit.path("documentId").asText()).isEqualTo(ready.path("documentId").asText());
        assertThat(hit.path("section").asText()).contains("直流快充中断处置");
        var excerpt = rest.getForEntity(base + "/api/knowledge/references/" + hit.path("referenceId").asText()
                + "?runId=" + runId, String.class);
        assertThat(excerpt.getStatusCode().value()).isEqualTo(200);
        assertThat(json(excerpt).path("content").asText()).contains("桩侧日志");
    }

    @Test void invalidPathTypeAndEncodingNeverBecomeReadyDocuments() throws Exception {
        var traversal = upload(base, "../escape.md", "# 标题\n正文".getBytes(StandardCharsets.UTF_8), "text/markdown");
        assertThat(traversal.getStatusCode().value()).isEqualTo(400);
        assertThat(fileRoot.resolve("owner-a").resolve("escape.md")).doesNotExist();

        // 05-02 延期项：PDF 接受登记但返回明确状态 OCR_REQUIRED/PDF_NOT_SUPPORTED，
        // 不解析、不切片、不产生正文或页码，绝不伪造内容
        var pdf = upload(base, "manual.pdf", "%PDF-fake".getBytes(StandardCharsets.UTF_8), "application/pdf");
        assertThat(pdf.getStatusCode().value()).isEqualTo(202);
        String pdfId = json(pdf).path("fileId").asText();
        JsonNode pdfView = awaitFile(base, pdfId, "OCR_REQUIRED");
        assertThat(pdfView.path("failureCode").asText()).isEqualTo("PDF_NOT_SUPPORTED");
        assertThat(pdfView.path("documentId").isNull()).isTrue();
        assertThat(pdfView.path("mime").asText()).isEqualTo("application/pdf");
        // 重试保持明确状态，不进入无限循环，也不产生文档
        var pdfRetry = rest.postForEntity(base + "/api/files/" + pdfId + "/retry", null, String.class);
        assertThat(pdfRetry.getStatusCode().value()).isEqualTo(202);
        assertThat(awaitFile(base, pdfId, "OCR_REQUIRED").path("documentId").isNull()).isTrue();
        // 其它不支持类型仍是 415
        var exe = upload(base, "tool.exe", new byte[] {1, 2, 3}, "application/octet-stream");
        assertThat(exe.getStatusCode().value()).isEqualTo(415);
        assertThat(exe.getBody()).contains("UNSUPPORTED_FILE_TYPE");

        var invalid = upload(base, "broken.md", new byte[] {(byte) 0xc3, 0x28}, "text/markdown");
        assertThat(invalid.getStatusCode().value()).isEqualTo(202);
        String fileId = json(invalid).path("fileId").asText();
        JsonNode failed = awaitFile(base, fileId, "FAILED");
        assertThat(failed.path("failureCode").asText()).isEqualTo("INVALID_TEXT_ENCODING");
        assertThat(failed.path("documentId").isNull()).isTrue();
        var retry = rest.postForEntity(base + "/api/files/" + fileId + "/retry", null, String.class);
        assertThat(retry.getStatusCode().value()).isEqualTo(202);
        awaitFile(base, fileId, "FAILED");
    }

    @Test void attachmentsAndHistoricalReferencesAreScopedAndPersisted() throws Exception {
        String content = "# 附件检索范围\n\n部件 CP-900 的绝缘检查必须记录测量条件。\n";
        String fileId = json(upload(base, "attachment.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown")).path("fileId").asText();
        JsonNode ready = awaitFile(base, fileId, "PARSED");

        assertThat(rest.getForEntity(otherBase + "/api/files/" + fileId, String.class).getStatusCode().value())
                .isEqualTo(404);
        var crossOwner = rest.exchange(otherBase + "/api/conversations", HttpMethod.POST,
                new HttpEntity<>(Map.of()), String.class);
        String otherConversation = json(crossOwner).path("conversationId").asText();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var denied = rest.exchange(otherBase + "/api/conversations/" + otherConversation + "/messages",
                HttpMethod.POST, new HttpEntity<>(Map.of("schemaVersion", 1, "userMessage", "读取附件",
                        "attachments", List.of(Map.of("fileId", fileId))), headers), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);

        String firstRun = run(base, "结合附件说明 CP-900 检查", Map.of("attachments",
                List.of(Map.of("fileId", fileId))));
        String storedRefs = jdbc.queryForObject("SELECT attachment_refs::text FROM dt_message m "
                + "JOIN dt_agent_run r ON r.goal_message_id=m.id WHERE r.id=?", String.class, UUID.fromString(firstRun));
        assertThat(storedRefs).contains(ready.path("documentId").asText()).doesNotContain("owner-a");
        var found = search(firstRun, "部件 CP-900 的绝缘检查必须记录测量条件");
        String referenceId = found.path("hits").get(0).path("referenceId").asText();
        String secondRun = run(base, "继续使用上一份证据", Map.of("contextRefs",
                List.of(Map.of("referenceId", referenceId))));
        var imported = Json.MAPPER.readTree(rest.getForObject(base + "/api/runs/" + secondRun + "/references", String.class));
        assertThat(imported.size()).isEqualTo(1);
        assertThat(imported.get(0).path("sourceId").asText()).isEqualTo(ready.path("documentId").asText());
    }

    @Test void blankMarkdownFailsInsteadOfPretendingImportCompleted() throws Exception {
        var blank = upload(base, "empty.md", " \n\t\n".getBytes(StandardCharsets.UTF_8), "text/markdown");
        String emptyId = json(blank).path("fileId").asText();
        JsonNode failed = awaitFile(base, emptyId, "FAILED");
        assertThat(failed.path("documentId").isNull()).isTrue();
    }

    @Test void legacyImportThenSameUploadLinksDocumentAndRealBytes() throws Exception {
        String content = "# 旧入口兼容\n\nLEGACY-UPLOAD-410 的章节用于补答附件检索。\n";
        JsonNode legacy = importDocument(base, "旧入口同内容手册", "2090.1", content);
        UUID oldFileId = jdbc.queryForObject("SELECT source_file_id FROM dt_knowledge_document WHERE id=?",
                UUID.class, UUID.fromString(legacy.path("documentId").asText()));
        jdbc.update("UPDATE dt_uploaded_file SET import_version=NULL WHERE id=?", oldFileId);
        String oldPath = jdbc.queryForObject("SELECT path_key FROM dt_uploaded_file WHERE id=?", String.class, oldFileId);
        assertThat(fileRoot.resolve("owner-a").resolve(oldPath)).doesNotExist();

        var accepted = upload(base, "legacy-reupload.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", "旧入口同内容手册", "automotive-demo", "2090.1");
        String fileId = json(accepted).path("fileId").asText();
        JsonNode ready = awaitFile(base, fileId, "PARSED");
        assertThat(fileId).isNotEqualTo(oldFileId.toString());
        assertThat(ready.path("documentId").asText()).isEqualTo(legacy.path("documentId").asText());
        String pathKey = jdbc.queryForObject("SELECT path_key FROM dt_uploaded_file WHERE id=?",
                String.class, UUID.fromString(fileId));
        Path stored = fileRoot.resolve("owner-a").resolve(pathKey);
        assertThat(stored).exists();
        assertThat(Files.readString(stored)).isEqualTo(content);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document_file WHERE file_id=? AND document_id=?",
                Integer.class, UUID.fromString(fileId), UUID.fromString(legacy.path("documentId").asText()))).isEqualTo(1);

        String runId = run(base, "请在补答时使用这份旧入口资料", Map.of("attachments",
                List.of(Map.of("fileId", fileId))));
        String saved = jdbc.queryForObject("SELECT attachment_refs::text FROM dt_message m "
                + "JOIN dt_agent_run r ON r.goal_message_id=m.id WHERE r.id=?", String.class, UUID.fromString(runId));
        assertThat(saved).contains(legacy.path("documentId").asText());
        assertThat(search(runId, "LEGACY-UPLOAD-410").path("hits").get(0).path("documentId").asText())
                .isEqualTo(legacy.path("documentId").asText());
    }

    @Test void sameContentSupportsVersionsAndRepeatUploadsShareOnlyTheDocument() throws Exception {
        String content = "# 多版本合同\n\nVERSION-FILE-620 同内容可以对应不同版本。\n";
        String title = "多版本文件合同";
        String firstId = json(upload(base, "version.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", title, "automotive-demo", "2091.1")).path("fileId").asText();
        JsonNode first = awaitFile(base, firstId, "PARSED");
        String repeatedId = json(upload(base, "version-repeat.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", title, "automotive-demo", "2091.1")).path("fileId").asText();
        JsonNode repeated = awaitFile(base, repeatedId, "PARSED");
        assertThat(repeatedId).isNotEqualTo(firstId);
        assertThat(repeated.path("documentId").asText()).isEqualTo(first.path("documentId").asText());

        String nextId = json(upload(base, "version-next.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", title, "automotive-demo", "2091.2")).path("fileId").asText();
        JsonNode next = awaitFile(base, nextId, "PARSED");
        assertThat(next.path("documentId").asText()).isNotEqualTo(first.path("documentId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE owner_id='owner-a' "
                + "AND project_id='project-a' AND checksum=?", Integer.class, first.path("checksum").asText())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document_file df "
                + "JOIN dt_uploaded_file f ON f.id=df.file_id WHERE f.checksum=?", Integer.class,
                first.path("checksum").asText())).isEqualTo(3);
        for (String fileId : List.of(firstId, repeatedId, nextId)) {
            String pathKey = jdbc.queryForObject("SELECT path_key FROM dt_uploaded_file WHERE id=?",
                    String.class, UUID.fromString(fileId));
            assertThat(fileRoot.resolve("owner-a").resolve(pathKey)).exists();
        }
    }

    @Test void failedImportLeavesNoReadyDocumentAndRetryUsesStoredBytes() throws Exception {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_knowledge_document_insert() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'private-test-document-failure'; END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_knowledge_document_insert BEFORE INSERT ON dt_knowledge_document "
                + "FOR EACH ROW EXECUTE FUNCTION fail_knowledge_document_insert()");
        String content = "# 失败重试\n\nRETRY-BYTES-930 必须读取已保存原文件。\n";
        String fileId;
        try {
            fileId = json(upload(base, "retry.md", content.getBytes(StandardCharsets.UTF_8),
                    "text/markdown", "失败重试手册", "automotive-demo", "2092.1")).path("fileId").asText();
            JsonNode failed = awaitFile(base, fileId, "FAILED");
            assertThat(failed.path("failureCode").asText()).isEqualTo("IMPORT_FAILED");
            assertThat(failed.path("documentId").isNull()).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document d "
                    + "JOIN dt_uploaded_file f ON f.checksum=d.checksum AND f.owner_id=d.owner_id "
                    + "AND f.project_id=d.project_id WHERE f.id=?", Integer.class, UUID.fromString(fileId))).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document_file WHERE file_id=?",
                    Integer.class, UUID.fromString(fileId))).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_knowledge_document_insert ON dt_knowledge_document");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_knowledge_document_insert()");
        }
        var retry = rest.postForEntity(base + "/api/files/" + fileId + "/retry", null, String.class);
        assertThat(retry.getStatusCode().value()).isEqualTo(202);
        JsonNode ready = awaitFile(base, fileId, "PARSED");
        assertThat(ready.path("documentId").asText()).isNotBlank();
        String pathKey = jdbc.queryForObject("SELECT path_key FROM dt_uploaded_file WHERE id=?",
                String.class, UUID.fromString(fileId));
        assertThat(Files.readString(fileRoot.resolve("owner-a").resolve(pathKey))).isEqualTo(content);
    }

    @Test void metadataAndEmbeddingConflictsFailWithoutHidingScopeIsolation() throws Exception {
        String content = "# 冲突合同\n\nPROFILE-CONFLICT-280 不得静默复用。\n";
        String title = "冲突检查手册";
        String mainId = json(upload(base, "conflict.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", title, "automotive-demo", "2093.1")).path("fileId").asText();
        JsonNode main = awaitFile(base, mainId, "PARSED");

        String metadataId = json(upload(base, "metadata-conflict.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", "不同标题", "automotive-demo", "2093.1")).path("fileId").asText();
        JsonNode metadata = awaitFile(base, metadataId, "FAILED");
        assertThat(metadata.path("failureCode").asText()).isEqualTo("DOCUMENT_METADATA_CONFLICT");
        assertThat(metadata.path("documentId").isNull()).isTrue();
        String profileId = json(upload(otherProfileBase, "profile-conflict.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", title, "automotive-demo", "2093.1")).path("fileId").asText();
        JsonNode profile = awaitFile(otherProfileBase, profileId, "FAILED");
        assertThat(profile.path("failureCode").asText()).isEqualTo("EMBEDDING_PROFILE_CONFLICT");
        assertThat(profile.path("documentId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document_file WHERE file_id IN (?,?)",
                Integer.class, UUID.fromString(metadataId), UUID.fromString(profileId))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE owner_id='owner-a' "
                + "AND project_id='project-a' AND checksum=?", Integer.class, main.path("checksum").asText())).isEqualTo(1);

        String otherOwnerId = json(upload(otherBase, "other-owner.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", title, "automotive-demo", "2093.1")).path("fileId").asText();
        String otherProjectId = json(upload(otherProjectBase, "other-project.md", content.getBytes(StandardCharsets.UTF_8),
                "text/markdown", title, "automotive-demo", "2093.1")).path("fileId").asText();
        awaitFile(otherBase, otherOwnerId, "PARSED");
        awaitFile(otherProjectBase, otherProjectId, "PARSED");
        assertThat(rest.getForEntity(otherBase + "/api/files/" + mainId, String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(rest.getForEntity(otherProjectBase + "/api/files/" + mainId, String.class).getStatusCode().value()).isEqualTo(404);
    }

    private JsonNode importDocument(String baseUrl, String title, String version, String content) throws Exception {
        var response = rest.postForEntity(baseUrl + "/api/knowledge/documents", Map.of(
                "title", title, "domain", "automotive-demo", "version", version,
                "filename", "legacy.md", "mimeType", "text/markdown", "content", content,
                "dataNature", "SIMULATED"), String.class);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
        return json(response).path("document");
    }
}
