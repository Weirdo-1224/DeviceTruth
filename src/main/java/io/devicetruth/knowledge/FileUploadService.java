package io.devicetruth.knowledge;

import io.devicetruth.application.agent.GeneralAgentService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.multipart.MultipartFile;

/** Markdown 原文件受控保存、异步导入与附件授权；原文件名永不参与服务器路径生成。 */
public final class FileUploadService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(FileUploadService.class);
    private static final Set<String> MARKDOWN_MIME = Set.of("text/markdown", "text/x-markdown");
    private static final RowMapper<FileView> MAPPER = (r, n) -> new FileView(
            r.getObject("id", UUID.class), r.getString("original_name"), r.getString("mime"),
            r.getString("checksum"), r.getInt("byte_size"), r.getString("parse_status"),
            r.getString("parse_error"), r.getObject("document_id", UUID.class),
            r.getString("title"), r.getString("doc_version"), r.getString("domain"),
            r.getTimestamp("created_at").toInstant());
    private static final String VIEW_SQL = """
            SELECT f.id,f.original_name,f.mime,f.checksum,f.byte_size,f.parse_status,f.parse_error,
                   d.id AS document_id,d.title,d.doc_version,d.domain,f.created_at
            FROM dt_uploaded_file f
            LEFT JOIN dt_knowledge_document_file df ON df.file_id=f.id AND df.owner_id=f.owner_id
              AND df.project_id=f.project_id
            LEFT JOIN dt_knowledge_document d ON d.id=df.document_id AND d.owner_id=df.owner_id
              AND d.project_id=df.project_id
            WHERE f.owner_id=? AND f.project_id=?
            """;

    public record ImportMetadata(String title, String domain, String deviceModel, String version, String dataNature) {}
    public record FileView(UUID fileId, String originalName, String mime, String checksum, int byteSize,
                           String parseStatus, String failureCode, UUID documentId, String title,
                           String documentVersion, String domain, Instant createdAt) {}

    private final JdbcTemplate jdbc;
    private final KnowledgeImportService imports;
    private final Path storageRoot;
    private final ThreadPoolExecutor executor;

    public FileUploadService(JdbcTemplate jdbc, KnowledgeImportService imports, Path storageRoot,
                             int maxConcurrentImports, int maxQueue) {
        if (maxConcurrentImports < 1 || maxQueue < 0) throw new IllegalArgumentException("Import limits out of range");
        this.jdbc = jdbc;
        this.imports = imports;
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException("FILE_STORAGE_UNAVAILABLE", e);
        }
        this.executor = new ThreadPoolExecutor(maxConcurrentImports, maxConcurrentImports, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, maxQueue)), runnable -> {
                    Thread thread = new Thread(runnable, "devicetruth-import-" + System.nanoTime());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public FileView upload(String ownerId, String projectId, MultipartFile file, ImportMetadata metadata) {
        if (file == null || file.isEmpty()) throw KnowledgeException.invalid("file is required");
        String filename = file.getOriginalFilename();
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean pdf = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        if (filename == null || (!pdf && (!filename.toLowerCase(Locale.ROOT).endsWith(".md")
                || !MARKDOWN_MIME.contains(mime))))
            throw new KnowledgeException("UNSUPPORTED_FILE_TYPE", 415);
        if (file.getSize() > KnowledgeImportService.MAX_DOCUMENT_BYTES)
            throw new KnowledgeException("DOCUMENT_TOO_LARGE", 413);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new KnowledgeException("FILE_STORAGE_UNAVAILABLE", 503);
        }
        if (bytes.length == 0) throw KnowledgeException.invalid("file is required");
        var request = new KnowledgeImportService.ImportRequest(
                metadata.title() == null || metadata.title().isBlank() ? filename : metadata.title(),
                metadata.domain(), metadata.deviceModel(), metadata.version(), filename, "text/markdown",
                "uploaded markdown", metadata.dataNature());
        String checksum = sha256(bytes);
        UUID id = UUID.randomUUID();
        Path target = resolve("knowledge/" + id);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new KnowledgeException("FILE_STORAGE_UNAVAILABLE", 503);
        }
        try {
            jdbc.update("""
                    INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,
                        parse_status,created_at,import_title,import_domain,import_device_model,import_version,
                        import_data_nature)
                    VALUES (?,?,?,?,?,?,?,?,'UPLOADED',?,?,?,?,?,?)
                    """, id, ownerId, projectId, "knowledge/" + id, request.filename(), request.mimeType(), checksum,
                    bytes.length, Timestamp.from(Instant.now()), request.title(), request.domain(),
                    request.deviceModel(), request.version(), request.dataNature());
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw e;
        }
        if (pdf) {
            // PDF 明确状态：保留文件记录供界面展示与审计，但不解析、不切片、不产生页码；
            // 需要 OCR/版面解析能力后才可能转成可检索文档（05-02 记录为未实现能力，绝不伪造正文）。
            jdbc.update("UPDATE dt_uploaded_file SET parse_status='OCR_REQUIRED',parse_error='PDF_NOT_SUPPORTED',"
                    + "mime='application/pdf' WHERE id=? AND owner_id=? AND project_id=?", id, ownerId, projectId);
            return require(ownerId, projectId, id);
        }
        dispatch(ownerId, projectId, id);
        return require(ownerId, projectId, id);
    }

    public FileView require(String ownerId, String projectId, UUID fileId) {
        var rows = jdbc.query(VIEW_SQL + " AND f.id=?", MAPPER, ownerId, projectId, fileId);
        if (rows.isEmpty()) throw KnowledgeException.notFoundOrDenied();
        return rows.getFirst();
    }

    public FileView retry(String ownerId, String projectId, UUID fileId) {
        FileView file = require(ownerId, projectId, fileId);
        if (!Set.of("FAILED", "OCR_REQUIRED").contains(file.parseStatus()))
            throw new KnowledgeException("IMPORT_NOT_RETRYABLE", 409);
        jdbc.update("UPDATE dt_uploaded_file SET parse_status='UPLOADED',parse_error=NULL WHERE id=? AND owner_id=? AND project_id=?",
                fileId, ownerId, projectId);
        dispatch(ownerId, projectId, fileId);
        return require(ownerId, projectId, fileId);
    }

    /** 附件只能引用当前归属且已完成导入的 Markdown 文档，返回服务端生成的规范元数据。 */
    public List<Map<String, Object>> readyAttachments(String ownerId, String projectId,
                                                       List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        if (attachments.size() > 5) throw new GeneralAgentService.RequestFailure("INVALID_ATTACHMENT", 400);
        List<Map<String, Object>> validated = new ArrayList<>();
        for (Map<String, Object> attachment : attachments) {
            if (attachment == null || !attachment.keySet().equals(Set.of("fileId")))
                throw new GeneralAgentService.RequestFailure("INVALID_ATTACHMENT", 400);
            UUID fileId;
            try { fileId = UUID.fromString(String.valueOf(attachment.get("fileId"))); }
            catch (RuntimeException e) { throw new GeneralAgentService.RequestFailure("INVALID_ATTACHMENT", 400); }
            FileView file = require(ownerId, projectId, fileId);
            if (!"PARSED".equals(file.parseStatus()) || file.documentId() == null)
                throw new GeneralAgentService.RequestFailure("ATTACHMENT_NOT_READY", 409);
            validated.add(Map.of("fileId", file.fileId().toString(), "documentId", file.documentId().toString(),
                    "title", file.title(), "version", file.documentVersion(), "domain", file.domain()));
        }
        return List.copyOf(validated);
    }

    private void dispatch(String ownerId, String projectId, UUID fileId) {
        try {
            executor.execute(() -> process(ownerId, projectId, fileId));
        } catch (RejectedExecutionException e) {
            fail(ownerId, projectId, fileId, "IMPORT_QUEUE_REJECTED");
        }
    }

    private void process(String ownerId, String projectId, UUID fileId) {
        try {
            var rows = jdbc.queryForList("SELECT path_key,import_title,import_domain,import_device_model,"
                    + "import_version,import_data_nature,original_name FROM dt_uploaded_file "
                    + "WHERE id=? AND owner_id=? AND project_id=? AND parse_status IN ('UPLOADED','PROCESSING')",
                fileId, ownerId, projectId);
            if (rows.isEmpty()) return;
            var row = rows.getFirst();
            jdbc.update("UPDATE dt_uploaded_file SET parse_status='PROCESSING',parse_error=NULL "
                    + "WHERE id=? AND owner_id=? AND project_id=?", fileId, ownerId, projectId);
            String originalName = String.valueOf(row.get("original_name"));
            if (!originalName.toLowerCase(Locale.ROOT).endsWith(".md")) {
                // 防御：PDF 等未支持格式不得进入 Markdown 解析，绝不生成伪造正文或页码
                fail(ownerId, projectId, fileId, "PDF_NOT_SUPPORTED");
                return;
            }
            byte[] bytes = Files.readAllBytes(resolve((String) row.get("path_key")));
            var request = new KnowledgeImportService.ImportRequest((String) row.get("import_title"),
                    (String) row.get("import_domain"), (String) row.get("import_device_model"),
                    (String) row.get("import_version"), (String) row.get("original_name"), "text/markdown",
                    "uploaded markdown", (String) row.get("import_data_nature"));
            imports.importMarkdown(ownerId, projectId, request, bytes, fileId);
        } catch (KnowledgeException e) {
            fail(ownerId, projectId, fileId, e.code());
        } catch (IOException | RuntimeException e) {
            LOG.error("Markdown import failed: fileId={} type={}", fileId, e.getClass().getSimpleName());
            fail(ownerId, projectId, fileId, "IMPORT_FAILED");
        }
    }

    private void fail(String ownerId, String projectId, UUID fileId, String code) {
        // PDF 等“需要 OCR/版面解析”的明确状态与普通解析失败分开：重试后仍保持该状态，不产生正文/页码
        boolean requiresOcr = "OCR_REQUIRED".equals(code) || "PDF_NOT_SUPPORTED".equals(code);
        jdbc.update("UPDATE dt_uploaded_file SET parse_status=?,parse_error=? WHERE id=? AND owner_id=? AND project_id=?",
                requiresOcr ? "OCR_REQUIRED" : "FAILED", code, fileId, ownerId, projectId);
    }

    private Path resolve(String pathKey) {
        Path resolved = storageRoot.resolve(pathKey).normalize();
        if (!resolved.startsWith(storageRoot)) throw new KnowledgeException("INVALID_FILE_PATH", 400);
        return resolved;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    @Override public void close() { executor.shutdownNow(); }
}
