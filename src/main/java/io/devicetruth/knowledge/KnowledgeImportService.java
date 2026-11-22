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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MD/TXT 导入：校验 → 按章节切片 → 固定 profile 向量化 → 一个事务写入文件关联、文档与切片。
 * 文件代表一次内容/版本请求；同内容同版本可复用文档，但每个上传文件都要有关联和真实原始字节。
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
    private java.util.function.Consumer<UUID> managedGuard=id->{};
    private java.util.function.Consumer<UUID> managedSuccess=id->{};
    public void managedAuthorization(java.util.function.Consumer<UUID> guard,java.util.function.Consumer<UUID> success) {managedGuard=guard;managedSuccess=success;}
    private ImportResult managedWrite(UUID file,java.util.function.Supplier<ImportResult> write) {
        if(file!=null)managedGuard.accept(file);
        var result=write.get();if(file!=null)managedSuccess.accept(file);return result;
    }


    public KnowledgeImportService(JdbcTemplate jdbc, TransactionTemplate transactions,
                                  EmbeddingClient embeddings, int maxChunkChars) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.embeddings = embeddings;
        this.maxChunkChars = maxChunkChars;
    }

    public ImportResult importDocument(String ownerId, String projectId, ImportRequest request) {
        String mime = request.mimeType();
        if (!MARKDOWN_MIME.contains(mime) && !"text/plain".equals(mime))
            throw new KnowledgeException("UNSUPPORTED_FILE_TYPE", 415);
        return importBytes(ownerId, projectId, request, request.content().getBytes(StandardCharsets.UTF_8), null);
    }

    /** Frozen professional corpora reuse this module's JDBC, transactions and production embedding client. */
    public KnowledgeCorpusService corpus() {
        return new KnowledgeCorpusService(jdbc, transactions, embeddings);
    }

    /** 兼容不指定文件的上传调用；新上传应传入自己的 fileId，保证文档关联和 PARSED 同事务完成。 */
    public ImportResult importMarkdown(String ownerId, String projectId, ImportRequest request, byte[] bytes) {
        if (!MARKDOWN_MIME.contains(request.mimeType())) throw new KnowledgeException("UNSUPPORTED_FILE_TYPE", 415);
        return importBytes(ownerId, projectId, request, bytes, null);
    }

    public ImportResult importMarkdown(String ownerId, String projectId, ImportRequest request, byte[] bytes,
                                       UUID sourceFileId) {
        if (!MARKDOWN_MIME.contains(request.mimeType())) throw new KnowledgeException("UNSUPPORTED_FILE_TYPE", 415);
        Checks.required(sourceFileId, "sourceFileId");
        return importBytes(ownerId, projectId, request, bytes, sourceFileId);
    }

    public ImportResult importTextFile(String owner,String project,ImportRequest request,byte[] bytes,UUID file) {
        String text;
        try {text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
        catch(java.nio.charset.CharacterCodingException e) {throw KnowledgeException.invalid("Invalid UTF-8");}
        return importBytes(owner,project,new ImportRequest(request.title(),request.domain(),request.deviceModel(),request.version(),request.filename(),"text/plain",text,request.dataNature()),bytes,file);
    }

    private ImportResult importBytes(String ownerId, String projectId, ImportRequest request, byte[] bytes,
                                     UUID sourceFileId) {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        if (bytes.length == 0) throw KnowledgeException.invalid("document has no indexable text");
        if (bytes.length > MAX_DOCUMENT_BYTES) throw new KnowledgeException("DOCUMENT_TOO_LARGE", 413);
        if(sourceFileId!=null)managedGuard.accept(sourceFileId);
        String checksum = sha256(bytes);
        // 维度先于复用判断：配置维度与索引列不符时必须失败，不能因为文档已存在而返回 reused=true。
        int dimension = KnowledgeIndexSchema.vectorDimension(jdbc);
        if (dimension != embeddings.profile().dimension())
            throw new KnowledgeException("EMBEDDING_DIMENSION_MISMATCH", 409);
        var existing = reusable(ownerId, projectId, checksum, request);
        if (existing != null) {
            if (sourceFileId == null && isKnowledge(existing.documentId())) return new ImportResult(existing,true);
            if (sourceFileId == null) return transactions.execute(status -> reuseForFile(ownerId,projectId,
                    ensureUploadedFile(ownerId,projectId,request,checksum,bytes.length),existing));
            return transactions.execute(status -> managedWrite(sourceFileId,()->reuseForFile(ownerId, projectId, sourceFileId, existing)));
        }
        List<KnowledgeChunker.Chunk> chunks = MARKDOWN_MIME.contains(request.mimeType())
                ? new DocumentParser().parseMarkdown(bytes, maxChunkChars)
                : KnowledgeChunker.of(request.content(), false, maxChunkChars);
        if (chunks.isEmpty()) throw KnowledgeException.invalid("document has no indexable text");
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (KnowledgeChunker.Chunk chunk : chunks) {if(sourceFileId!=null)managedGuard.accept(sourceFileId);vectors.add(embed(chunk.content(), dimension));}
        return transactions.execute(status -> managedWrite(sourceFileId,()->write(ownerId, projectId, request, checksum,
                bytes.length, chunks, vectors, sourceFileId)));
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

    private ImportResult write(String ownerId, String projectId, ImportRequest request, String checksum,
                               int byteSize, List<KnowledgeChunker.Chunk> chunks, List<float[]> vectors,
                               UUID sourceFileId) {
        var existing = reusable(ownerId, projectId, checksum, request);
        if (existing != null) {
            if (sourceFileId == null && isKnowledge(existing.documentId())) return new ImportResult(existing,true);
            if (sourceFileId == null) return reuseForFile(ownerId,projectId,
                    ensureUploadedFile(ownerId,projectId,request,checksum,byteSize),existing);
            return reuseForFile(ownerId, projectId, sourceFileId, existing);
        }
        UUID fileId = sourceFileId == null ? ensureUploadedFile(ownerId, projectId, request, checksum, byteSize)
                : sourceFileId;
        KnowledgeVisibilityGuard.lockScope(jdbc,ownerId,projectId,false);
        boolean disabled=jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE owner_id=? AND project_id=? AND checksum=? AND availability='DISABLED'",Long.class,ownerId,projectId,checksum)>0;
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO dt_knowledge_document(id,owner_id,project_id,title,domain,device_model,doc_version,
                    source_file_id,checksum,ingest_status,embedding_profile,embedding_model,embedding_dimension,
                    chunking_profile,chunk_count,data_nature,created_at,availability)
                VALUES (?,?,?,?,?,?,?,?,?,'READY',?,?,?,?,?,?,?,?)
                """, documentId, ownerId, projectId, request.title(), request.domain(), request.deviceModel(),
                request.version(), fileId, checksum, embeddings.profile().profileVersion(),
                embeddings.profile().model(), embeddings.profile().dimension(), KnowledgeChunker.PROFILE,
                chunks.size(), request.dataNature(), Timestamp.from(now),disabled?"DISABLED":"ENABLED");
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
        linkAndMarkParsed(ownerId, projectId, fileId, documentId);
        return new ImportResult(document(ownerId, projectId, documentId), false);
    }

    private ImportResult reuseForFile(String ownerId, String projectId, UUID fileId, DocumentMetadata existing) {
        linkAndMarkParsed(ownerId, projectId, fileId, existing.documentId());
        return new ImportResult(existing, true);
    }

    private void linkAndMarkParsed(String ownerId, String projectId, UUID fileId, UUID documentId) {
        jdbc.update("""
                INSERT INTO dt_knowledge_document_file(file_id,document_id,owner_id,project_id)
                VALUES (?,?,?,?) ON CONFLICT(file_id) DO NOTHING
                """, fileId, documentId, ownerId, projectId);
        if(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document_file WHERE file_id=? AND document_id=? AND owner_id=? AND project_id=?",Long.class,fileId,documentId,ownerId,projectId)!=1)
            throw new KnowledgeException("DOCUMENT_FILE_VERSION_CONFLICT",409);
        int updated = jdbc.update("""
                UPDATE dt_uploaded_file SET parse_status='PARSED',parse_error=NULL
                WHERE id=? AND owner_id=? AND project_id=?
                """, fileId, ownerId, projectId);
        if (updated != 1) throw KnowledgeException.notFoundOrDenied();
    }

    private boolean isKnowledge(UUID document) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM dt_knowledge_document_file l JOIN dt_uploaded_file f ON f.id=l.file_id WHERE l.document_id=? AND f.usage_scope='SPACE_KNOWLEDGE')",Boolean.class,document));
    }
    private UUID ensureUploadedFile(String ownerId, String projectId, ImportRequest request,
                                    String checksum, int byteSize) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,
                    parse_status,created_at,import_title,import_domain,import_device_model,import_version,
                    import_data_nature)
                VALUES (?,?,?,?,?,?,?,?,'PROCESSING',?,?,?,?,?,?)
                """, id, ownerId, projectId, "knowledge/" + id, request.filename(), request.mimeType(), checksum,
                byteSize, Timestamp.from(Instant.now()), request.title(), request.domain(), request.deviceModel(),
                request.version(), request.dataNature());
        return id;
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

    /** 同内容同版本只有元数据和 embedding 档案都一致才复用；冲突必须显式失败。 */
    private DocumentMetadata reusable(String ownerId, String projectId, String checksum, ImportRequest request) {
        DocumentMetadata existing = findDocument(ownerId, projectId, checksum, request.version());
        if (existing == null) return null;
        if (!existing.title().equals(request.title()) || !existing.domain().equals(request.domain())
                || !Objects.equals(existing.deviceModel(), request.deviceModel())
                || !existing.dataNature().equals(request.dataNature()))
            throw new KnowledgeException("DOCUMENT_METADATA_CONFLICT", 409);
        if ("NEEDS_REBUILD".equals(existing.ingestStatus()))
            throw new KnowledgeException("DOCUMENT_NEEDS_REBUILD", 409);
        if (!"READY".equals(existing.ingestStatus()))
            throw new KnowledgeException("DOCUMENT_NOT_REUSABLE", 409);
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
