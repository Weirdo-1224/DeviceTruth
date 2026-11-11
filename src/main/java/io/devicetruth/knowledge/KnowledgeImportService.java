package io.devicetruth.knowledge;

import io.devicetruth.core.contract.Checks;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MD/TXT 导入：校验 → 按章节切片 → 固定 profile 向量化 → 一个事务写入文件元数据、文档与切片。
 * 原始文件字节不写入数据库；path_key 由服务器生成。向量化在事务外完成，失败不留半成品。
 */
public final class KnowledgeImportService {
    public static final int MAX_DOCUMENT_BYTES = 1_000_000;
    private static final Set<String> MARKDOWN_MIME = Set.of("text/markdown", "text/x-markdown");
    private static final RowMapper<DocumentMetadata> DOCUMENT_MAPPER = (r, n) -> new DocumentMetadata(
            r.getObject("id", UUID.class), r.getString("title"), r.getString("doc_version"), r.getString("domain"),
            r.getString("device_model"), r.getInt("chunk_count"), r.getString("embedding_profile"),
            r.getString("embedding_model"), r.getInt("embedding_dimension"), r.getString("chunking_profile"),
            r.getString("data_nature"), r.getString("ingest_status"), r.getString("checksum"),
            r.getTimestamp("created_at").toInstant());

    /** 导入请求；服务端身份不在此结构内，由调用方以服务器 Bean 传入。 */
    public record ImportRequest(String title, String domain, String deviceModel, String version,
                                String filename, String mimeType, String content, String dataNature) {
        private static final Set<String> DATA_NATURES = Set.of("SIMULATED", "AUTHORIZED");

        public ImportRequest {
            title = Checks.text(title, "title");
            if (title.codePointCount(0, title.length()) > 500) throw new IllegalArgumentException("title too long");
            domain = domain == null || domain.isBlank() ? "general" : Checks.id(domain, "domain");
            deviceModel = deviceModel == null || deviceModel.isBlank() ? null : Checks.id(deviceModel, "deviceModel");
            version = Checks.id(version, "version");
            filename = Checks.text(filename, "filename");
            if (filename.length() > 256 || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
                    || filename.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("invalid filename");
            mimeType = Checks.text(mimeType, "mimeType").toLowerCase(Locale.ROOT);
            if (mimeType.length() > 128) throw new IllegalArgumentException("invalid mimeType");
            content = Checks.text(content, "content");
            if (content.codePointCount(0, content.length()) > 400_000)
                throw new IllegalArgumentException("content too long");
            dataNature = dataNature == null || dataNature.isBlank()
                    ? "SIMULATED" : dataNature.trim().toUpperCase(Locale.ROOT);
            if (!DATA_NATURES.contains(dataNature)) throw new IllegalArgumentException("invalid dataNature");
        }
    }

    /** 文档元数据视图：引用可据此还原文档版本与 profile；ingestStatus 暴露 NEEDS_REBUILD 隔离态。 */
    public record DocumentMetadata(UUID documentId, String title, String docVersion, String domain,
                                   String deviceModel, int chunkCount, String embeddingProfile,
                                   String embeddingModel, int embeddingDimension, String chunkingProfile,
                                   String dataNature, String ingestStatus, String checksum, Instant createdAt) {}

    public record ImportResult(DocumentMetadata document, boolean reused) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmbeddingClient embeddings;
    private final int maxChunkChars;

    public KnowledgeImportService(JdbcTemplate jdbc, TransactionTemplate transactions,
                                  EmbeddingClient embeddings, int maxChunkChars) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.embeddings = embeddings;
        this.maxChunkChars = maxChunkChars;
    }

    public ImportResult importDocument(String ownerId, String projectId, ImportRequest request) {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        boolean markdown;
        String mime = request.mimeType();
        if (MARKDOWN_MIME.contains(mime)) markdown = true;
        else if ("text/plain".equals(mime)) markdown = false;
        else throw new KnowledgeException("UNSUPPORTED_FILE_TYPE", 415);
        byte[] bytes = request.content().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_DOCUMENT_BYTES) throw new KnowledgeException("DOCUMENT_TOO_LARGE", 413);
        String checksum = sha256(bytes);
        // 维度先于复用判断：配置维度与索引列不符时必须失败，不能因为文档已存在而返回 reused=true。
        int dimension = KnowledgeIndexSchema.vectorDimension(jdbc);
        if (dimension != embeddings.profile().dimension())
            throw new KnowledgeException("EMBEDDING_DIMENSION_MISMATCH", 409);
        var existing = reusable(ownerId, projectId, checksum, request.version());
        if (existing != null) return new ImportResult(existing, true);
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.of(request.content(), markdown, maxChunkChars);
        if (chunks.isEmpty()) throw KnowledgeException.invalid("document has no indexable text");
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (KnowledgeChunker.Chunk chunk : chunks) vectors.add(embed(chunk.content(), dimension));
        return transactions.execute(status -> write(ownerId, projectId, request, mime, checksum, bytes.length,
                chunks, vectors));
    }

    /** 读取文档元数据；不存在与无权统一 404，不泄漏其他 owner/project 是否存在。 */
    public DocumentMetadata document(String ownerId, String projectId, UUID documentId) {
        var rows = jdbc.query("""
                SELECT id,title,doc_version,domain,device_model,chunk_count,embedding_profile,embedding_model,
                       embedding_dimension,chunking_profile,data_nature,ingest_status,checksum,created_at
                FROM dt_knowledge_document WHERE id=? AND owner_id=? AND project_id=?
                """, DOCUMENT_MAPPER, documentId, ownerId, projectId);
        if (rows.isEmpty()) throw KnowledgeException.notFoundOrDenied();
        return rows.getFirst();
    }

    private ImportResult write(String ownerId, String projectId, ImportRequest request, String mime, String checksum,
                               int byteSize, List<KnowledgeChunker.Chunk> chunks, List<float[]> vectors) {
        var existing = reusable(ownerId, projectId, checksum, request.version());
        if (existing != null) return new ImportResult(existing, true);
        UUID fileId = ensureUploadedFile(ownerId, projectId, request, mime, checksum, byteSize);
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO dt_knowledge_document(id,owner_id,project_id,title,domain,device_model,doc_version,
                    source_file_id,checksum,ingest_status,embedding_profile,embedding_model,embedding_dimension,
                    chunking_profile,chunk_count,data_nature,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,'READY',?,?,?,?,?,?,?)
                """, documentId, ownerId, projectId, request.title(), request.domain(), request.deviceModel(),
                request.version(), fileId, checksum, embeddings.profile().profileVersion(),
                embeddings.profile().model(), embeddings.profile().dimension(), KnowledgeChunker.PROFILE,
                chunks.size(), request.dataNature(), Timestamp.from(now));
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunker.Chunk chunk = chunks.get(index);
            jdbc.update("""
                    INSERT INTO dt_knowledge_chunk(id,document_id,owner_id,project_id,chunk_no,content,page_no,
                        section,embedding,embedding_profile)
                    VALUES (?,?,?,?,?,?,?,?,CAST(? AS vector),?)
                    """, UUID.randomUUID(), documentId, ownerId, projectId, chunk.chunkNo(), chunk.content(),
                    chunk.page(), chunk.section(), VectorLiteral.of(vectors.get(index)),
                    embeddings.profile().profileVersion());
        }
        return new ImportResult(document(ownerId, projectId, documentId), false);
    }

    private UUID ensureUploadedFile(String ownerId, String projectId, ImportRequest request, String mime,
                                    String checksum, int byteSize) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,
                    parse_status,created_at)
                VALUES (?,?,?,?,?,?,?,?,'PARSED',?)
                ON CONFLICT (owner_id,project_id,checksum) DO NOTHING
                """, id, ownerId, projectId, "knowledge/" + id, request.filename(), mime, checksum, byteSize,
                Timestamp.from(Instant.now()));
        return jdbc.queryForObject("SELECT id FROM dt_uploaded_file WHERE owner_id=? AND project_id=? AND checksum=?",
                UUID.class, ownerId, projectId, checksum);
    }

    private DocumentMetadata findDocument(String ownerId, String projectId, String checksum, String version) {
        var rows = jdbc.query("""
                SELECT id,title,doc_version,domain,device_model,chunk_count,embedding_profile,embedding_model,
                       embedding_dimension,chunking_profile,data_nature,ingest_status,checksum,created_at
                FROM dt_knowledge_document
                WHERE owner_id=? AND project_id=? AND checksum=? AND doc_version=?
                """, DOCUMENT_MAPPER, ownerId, projectId, checksum, version);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * 只有同一档案（供应商/模型/维度/修订编码进 profile）才允许复用；
     * 被隔离为 NEEDS_REBUILD 的历史向量（来源无法证明）一律要求用新版本重建，
     * 换 provider、换模型或换维度时明确拒绝，绝不静默返回旧文档。
     */
    private DocumentMetadata reusable(String ownerId, String projectId, String checksum, String version) {
        DocumentMetadata existing = findDocument(ownerId, projectId, checksum, version);
        if (existing == null) return null;
        if ("NEEDS_REBUILD".equals(existing.ingestStatus()))
            throw new KnowledgeException("DOCUMENT_NEEDS_REBUILD", 409);
        EmbeddingProfile profile = embeddings.profile();
        if (!profile.profileVersion().equals(existing.embeddingProfile())
                || !profile.model().equals(existing.embeddingModel())
                || profile.dimension() != existing.embeddingDimension())
            throw new KnowledgeException("EMBEDDING_PROFILE_CONFLICT", 409);
        return existing;
    }

    private float[] embed(String text, int dimension) {
        try {
            float[] vector = embeddings.embed(text);
            if (vector.length != dimension) throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
            for (float component : vector)
                if (!Float.isFinite(component)) throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID");
            return vector;
        } catch (EmbeddingException failure) {
            throw KnowledgeException.fromEmbedding(failure);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
