package io.devicetruth.knowledge;

import io.devicetruth.core.capability.ToolContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * 真实 PG 升级回归：V2 旧缺陷产生的“离线算法 + 真实模型名”向量，
 * 经过 V3/V4 后必须被隔离为 NEEDS_REBUILD，不得被真实档案复用或召回；
 * 正文与历史引用保留可读，重建需要新的文档版本。
 */
class KnowledgeUpgradeMigrationTest {
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static String databaseName;
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll static void createDatabase() throws Exception {
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
        dataSource = new DriverManagerDataSource(databaseBaseUrl + databaseName, username, password);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll static void dropDatabase() throws Exception {
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

    @Test void legacyVectorsAreQuarantinedAndNeverReusedOrRecalled() throws Exception {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate().target(MigrationVersion.fromVersion("2")).load().migrate();
        LegacyFixture legacy = plantLegacyVulnerableDocument();
        migrate().load().migrate();

        // V3 曾按模型名推断 provider；V4 必须把这类来源无法证明的向量隔离为需重建。
        assertThat(jdbc.queryForObject("SELECT ingest_status FROM dt_knowledge_document WHERE id=?",
                String.class, legacy.documentId())).isEqualTo("NEEDS_REBUILD");
        String documentProfile = jdbc.queryForObject(
                "SELECT embedding_profile FROM dt_knowledge_document WHERE id=?", String.class, legacy.documentId());
        assertThat(documentProfile).isEqualTo("legacy-unverified:@1024-cosine-r0")
                .doesNotStartWith("openai:").doesNotStartWith("deterministic:");
        assertThat(jdbc.queryForObject(
                "SELECT embedding_profile FROM dt_knowledge_chunk WHERE document_id=?", String.class,
                legacy.documentId())).isEqualTo(documentProfile);
        assertThat(jdbc.queryForObject("SELECT content FROM dt_knowledge_chunk WHERE document_id=?",
                String.class, legacy.documentId())).isEqualTo(legacy.chunkContent());
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success", String.class))
                .contains("4", "7", "15", "16");

        var embeddingCalls = new java.util.concurrent.atomic.AtomicInteger();
        EmbeddingProfile realProfile = EmbeddingProfile.of("openai", "http://127.0.0.1:1", "test-key",
                "text-embedding-v4", 1024, "1", 1000, true);
        var deterministic = new DeterministicEmbeddingClient(EmbeddingProfile.of("deterministic", "", "",
                "deterministic-char2", 1024, "1", 1000, false));
        EmbeddingClient fakeReal = new EmbeddingClient() {
            @Override public EmbeddingProfile profile() { return realProfile; }
            @Override public float[] embed(String text) {
                embeddingCalls.incrementAndGet();
                return deterministic.embed(text);
            }
        };
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var importer = new KnowledgeImportService(jdbc, transactions, fakeReal, 480);
        var request = new KnowledgeImportService.ImportRequest("历史档案", "automotive-demo", null, "2026.1",
                "legacy.md", "text/markdown", legacy.importContent(), "SIMULATED");

        // 真实配置重导相同内容与版本：必须要求重建，且完全不调用 embedding。
        assertThatThrownBy(() -> importer.importDocument("owner-a", "project-a", request))
                .isInstanceOf(KnowledgeException.class)
                .hasMessageContaining("DOCUMENT_NEEDS_REBUILD");
        assertThat(embeddingCalls.get()).isZero();

        // 真实档案的候选过滤：即使查询向量与被隔离切片完全一致，也不能召回。
        var searcher = new KnowledgeSearchService(jdbc, transactions, fakeReal, 5, 0.15);
        ToolContext context = new ToolContext("owner-a", "project-a", null, legacy.runId(), null, Instant.now(),
                null, Set.of(KnowledgeTools.SEARCH, KnowledgeTools.EXCERPT));
        var outcome = searcher.search(context, legacy.chunkContent(), KnowledgeSearchService.KnowledgeFilters.NONE, 5);
        assertThat(outcome.empty()).isTrue();
        assertThat(outcome.hits()).isEmpty();

        // 正文与历史引用保留可读：直接凭引用打开原章节不受隔离状态影响。
        UUID referenceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dt_evidence_reference(id,run_id,conversation_id,owner_id,project_id,source_type,
                    source_id,source_version,chunk_id,citation_section,retrieved_at,data_nature)
                VALUES (?,?,?,'owner-a','project-a','KNOWLEDGE_DOCUMENT',?,?,?,'历史章节',now(),'SIMULATED')
                """, referenceId, legacy.runId(), legacy.conversationId(), legacy.documentId().toString(), "2026.1",
                legacy.chunkId());
        var excerpt = searcher.excerptByReference(context, referenceId);
        assertThat(excerpt.content()).isEqualTo(legacy.chunkContent());
        assertThat(searcher.excerpt(context, legacy.documentId(), legacy.chunkId()).content())
                .isEqualTo(legacy.chunkContent());

        // 重建路径：用新的文档版本重新导入同一内容后即可检索，旧版本仍保持隔离。
        var rebuilt = importer.importDocument("owner-a", "project-a", new KnowledgeImportService.ImportRequest(
                "历史档案", "automotive-demo", null, "2026.2", "legacy.md", "text/markdown",
                legacy.importContent(), "SIMULATED"));
        assertThat(rebuilt.reused()).isFalse();
        assertThat(rebuilt.document().ingestStatus()).isEqualTo("READY");
        var rebuiltSearch = searcher.search(context, legacy.chunkContent(),
                KnowledgeSearchService.KnowledgeFilters.NONE, 5);
        assertThat(rebuiltSearch.empty()).isFalse();
        assertThat(rebuiltSearch.hits().getFirst().documentId()).isEqualTo(rebuilt.document().documentId());
        assertThat(jdbc.queryForObject("SELECT ingest_status FROM dt_knowledge_document WHERE id=?",
                String.class, legacy.documentId())).isEqualTo("NEEDS_REBUILD");
    }

    @Test void legacyMultiVersionFileIsSplitBeforeDocumentLinksAreBackfilled() {
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrate().target(MigrationVersion.fromVersion("2")).load().migrate();
        UUID fileId = UUID.randomUUID();
        UUID firstDocument = UUID.randomUUID();
        UUID secondDocument = UUID.randomUUID();
        String checksum = sha256("same bytes with two versions".getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,
                    parse_status,created_at)
                VALUES (?,'owner-a','project-a',?,'legacy.md','text/markdown',?,17,'PARSED',now())
                """, fileId, "knowledge/" + fileId, checksum);
        for (UUID documentId : List.of(firstDocument, secondDocument)) {
            String version = documentId.equals(firstDocument) ? "2025.1" : "2026.2";
            jdbc.update("""
                    INSERT INTO dt_knowledge_document(id,owner_id,project_id,title,domain,doc_version,source_file_id,
                        checksum,ingest_status,embedding_profile,embedding_model,embedding_dimension,chunking_profile,
                        chunk_count,data_nature,created_at)
                    VALUES (?,'owner-a','project-a','多版本历史','general',?,?,?,'READY',
                        'text-embedding-v4@1024-cosine-v1','text-embedding-v4',1024,?,1,'SIMULATED',now())
                    """, documentId, version, fileId, checksum, KnowledgeChunker.PROFILE);
        }

        migrate().load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_uploaded_file WHERE checksum=?",
                Integer.class, checksum)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT source_file_id) FROM dt_knowledge_document WHERE checksum=?",
                Integer.class, checksum)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document_file df "
                + "JOIN dt_knowledge_document d ON d.id=df.document_id WHERE d.checksum=?",
                Integer.class, checksum)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_uploaded_file WHERE checksum=? "
                + "AND import_version IS NOT NULL", Integer.class, checksum)).isEqualTo(2);
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration migrate() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }

    private record LegacyFixture(UUID documentId, UUID chunkId, UUID conversationId, UUID runId,
                                 String importContent, String chunkContent) {}

    private LegacyFixture plantLegacyVulnerableDocument() {
        String chunkContent = "旧档案历史段落：充电中断排查。";
        String importContent = "# 历史档案\n\n" + chunkContent;
        String checksum = sha256(importContent.getBytes(StandardCharsets.UTF_8));
        UUID fileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,created_at,updated_at) VALUES (?,?,?,?,?)",
                conversationId, "owner-a", "project-a", Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of)
                VALUES (?,?,?,?,1,'USER','历史消息',?,?)
                """, messageId, conversationId, "owner-a", "project-a", Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,
                    as_of,budget,created_at,updated_at)
                VALUES (?,?,?,?,?,'QUEUED',?,?,?::jsonb,?,?)
                """, runId, conversationId, "owner-a", "project-a", messageId, Timestamp.from(now), Timestamp.from(now),
                "{\"limits\":{\"maxSteps\":12,\"maxRepeatedActions\":2,\"maxActiveDurationMs\":120000},"
                        + "\"used\":{\"steps\":0,\"modelAttempts\":0,\"toolCalls\":0,\"activeDurationMs\":0},"
                        + "\"usageStatus\":\"UNKNOWN\"}", Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,
                    parse_status,created_at)
                VALUES (?,'owner-a','project-a',?,'legacy.md','text/markdown',?,?,'PARSED',?)
                """, fileId, "knowledge/" + fileId, checksum, importContent.getBytes(StandardCharsets.UTF_8).length,
                Timestamp.from(now));
        // 旧缺陷数据：算法是离线确定性向量，却记录了真实模型名与旧标签。
        jdbc.update("""
                INSERT INTO dt_knowledge_document(id,owner_id,project_id,title,domain,doc_version,source_file_id,
                    checksum,ingest_status,embedding_profile,embedding_model,embedding_dimension,chunking_profile,
                    chunk_count,data_nature,created_at)
                VALUES (?,'owner-a','project-a','历史档案','automotive-demo','2026.1',?,?,'READY',
                    'text-embedding-v4@1024-cosine-v1','text-embedding-v4',1024,?,1,'SIMULATED',?)
                """, documentId, fileId, checksum, KnowledgeChunker.PROFILE, Timestamp.from(now));
        var offline = new DeterministicEmbeddingClient(EmbeddingProfile.of("deterministic", "", "",
                "deterministic-char2", 1024, "1", 1000, false));
        jdbc.update("""
                INSERT INTO dt_knowledge_chunk(id,document_id,owner_id,project_id,chunk_no,content,section,embedding,
                    embedding_profile)
                VALUES (?,?,'owner-a','project-a',1,?,'历史章节',CAST(? AS vector),'text-embedding-v4@1024-cosine-v1')
                """, chunkId, documentId, chunkContent, VectorLiteral.of(offline.embed(chunkContent)));
        return new LegacyFixture(documentId, chunkId, conversationId, runId, importContent, chunkContent);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
