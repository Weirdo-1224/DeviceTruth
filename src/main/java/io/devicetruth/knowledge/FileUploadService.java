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
            r.getTimestamp("created_at").toInstant(),r.getObject("uploaded_by_user_id",UUID.class),r.getObject("conversation_id",UUID.class),r.getString("usage_scope"),r.getString("import_data_nature"));
    private static final String VIEW_SQL = """
            SELECT f.id,f.original_name,f.mime,f.checksum,f.byte_size,f.parse_status,f.parse_error,
                   d.id AS document_id,d.title,d.doc_version,d.domain,f.created_at,f.uploaded_by_user_id,f.conversation_id,f.usage_scope,f.import_data_nature
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
                           String documentVersion, String domain, Instant createdAt,UUID uploadedByUserId,UUID conversationId,String usageScope,String dataNature) {}

    private final JdbcTemplate jdbc;
    private final KnowledgeImportService imports;
    private final Path storageRoot;
    private final ThreadPoolExecutor executor;
    private final String importWorker="import-"+UUID.randomUUID();
    private final Set<UUID> dispatched=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> redispatchRequested=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Object dispatchLock=new Object();
    private final java.util.concurrent.ScheduledExecutorService managedRecovery=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"managed-import-recovery");t.setDaemon(true);return t;});
    private final java.util.concurrent.atomic.AtomicBoolean recoveryStarted=new java.util.concurrent.atomic.AtomicBoolean();
    public boolean managedLeaseValid(UUID file) {
        return jdbc.queryForObject("SELECT count(*) FROM dt_admin_import WHERE file_id=? AND import_lease_owner=? AND import_lease_until>clock_timestamp()",Long.class,file,importWorker)==1;
    }
    public void recoverManagedImports() {
        jdbc.query("SELECT f.id,f.owner_id,f.project_id FROM dt_uploaded_file f JOIN dt_admin_import m ON m.file_id=f.id WHERE f.parse_status IN ('UPLOADED','PROCESSING') AND (m.import_lease_until IS NULL OR m.import_lease_until<clock_timestamp()) ORDER BY m.created_at,f.id LIMIT 100",(rs,n)->{dispatch(rs.getString("owner_id"),rs.getString("project_id"),rs.getObject("id",UUID.class));return 0;});
    }
    public void enableManagedRecovery() {
        if(recoveryStarted.compareAndSet(false,true))managedRecovery.scheduleWithFixedDelay(()->{try{recoverManagedImports();}catch(RuntimeException e){LOG.error("Managed import recovery failed: {}",e.getClass().getSimpleName());}},1,10,TimeUnit.SECONDS);
    }

    private java.util.function.Consumer<UUID> managedGuard = id -> {};
    private java.util.function.BiConsumer<UUID,String> managedCompletion = (id,code) -> {};
    public void managedAuthorization(java.util.function.Consumer<UUID> guard,java.util.function.BiConsumer<UUID,String> completion) {
        managedGuard=java.util.Objects.requireNonNull(guard);managedCompletion=java.util.Objects.requireNonNull(completion);
    }
    private void afterCommit(Runnable action) {
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){action.run();}});
        else action.run();
    }


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
        return upload(ownerId,projectId,file,metadata,null,null);
    }
    public FileView uploadAttachment(io.devicetruth.application.agent.AccessScope scope,UUID conversation,
            MultipartFile file,ImportMetadata metadata) {
        return transactions().execute(status->{
            new io.devicetruth.adapter.persistence.ConversationRepository(jdbc).require(scope.storageIdentity(),conversation,true);
            new io.devicetruth.application.agent.WorkspaceScopeResolver(jdbc).requireWrite(scope);
            if(!new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc).lockWriteAuthorization(scope))
                throw new GeneralAgentService.RequestFailure("AUTHORIZATION_REVOKED",403);
            return upload(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),file,metadata,conversation,scope.actorUserId());
        });
    }
    public FileView uploadForActor(String ownerId,String projectId,MultipartFile file,ImportMetadata metadata,UUID actor) {
        return transactions().execute(t->{lockActiveActor(actor);return upload(ownerId,projectId,file,metadata,null,actor);});
    }
    private void lockActiveActor(UUID actor) {
        if(jdbc.queryForList("SELECT id FROM dt_account WHERE id=? AND status='ACTIVE' FOR SHARE",UUID.class,actor).isEmpty())throw new KnowledgeException("AUTHORIZATION_REVOKED",403);
    }
    private FileView upload(String ownerId,String projectId,MultipartFile file,ImportMetadata metadata,UUID conversation,UUID actor) {
        if (file == null || file.isEmpty()) throw KnowledgeException.invalid("file is required");
        String filename = file.getOriginalFilename();
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean txt=filename!=null && filename.toLowerCase(Locale.ROOT).endsWith(".txt") && mime.equals("text/plain");
        boolean pdf = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        if (filename == null || (!pdf && !txt && (!filename.toLowerCase(Locale.ROOT).endsWith(".md")
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
                        import_data_nature,conversation_id,uploaded_by_user_id,usage_scope,uploaded_by_status_version)
                    VALUES (?,?,?,?,?,?,?,?,'UPLOADED',?,?,?,?,?,?,?,?,?,?)
                    """, id, ownerId, projectId, "knowledge/" + id, request.filename(), request.mimeType(), checksum,
                    bytes.length, Timestamp.from(Instant.now()), request.title(), request.domain(),
                    request.deviceModel(), request.version(), request.dataNature(), conversation, actor,
                    conversation == null ? "SPACE_KNOWLEDGE" : "CONVERSATION_ATTACHMENT",
                    actor == null ? null : jdbc.queryForObject("SELECT status_version FROM dt_account WHERE id=?", Long.class, actor));
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw e;
        }
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCompletion(int completion){if(completion==STATUS_ROLLED_BACK)try{Files.deleteIfExists(target);}catch(IOException ignored){}}
            });
        if (pdf) {
            // PDF 明确状态：保留文件记录供界面展示与审计，但不解析、不切片、不产生页码；
            // 需要 OCR/版面解析能力后才可能转成可检索文档（05-02 记录为未实现能力，绝不伪造正文）。
            jdbc.update("UPDATE dt_uploaded_file SET parse_status='OCR_REQUIRED',parse_error='PDF_NOT_SUPPORTED',"
                    + "mime='application/pdf' WHERE id=? AND owner_id=? AND project_id=?", id, ownerId, projectId);
            return require(ownerId, projectId, id);
        }
        afterCommit(() -> dispatch(ownerId, projectId, id));
        return require(ownerId, projectId, id);
    }

    public FileView require(String ownerId, String projectId, UUID fileId) {
        var rows = jdbc.query(VIEW_SQL + " AND f.id=?", MAPPER, ownerId, projectId, fileId);
        if (rows.isEmpty()) throw KnowledgeException.notFoundOrDenied();
        return rows.getFirst();
    }

    public FileView retry(String ownerId, String projectId, UUID fileId) {
        if(jdbc.queryForObject("SELECT count(*) FROM dt_admin_import WHERE file_id=?",Long.class,fileId)>0)throw KnowledgeException.notFoundOrDenied();
        return retryManaged(ownerId,projectId,fileId);
    }
    public FileView retryForActor(String ownerId,String projectId,UUID fileId,UUID actor) {
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
        return tx.execute(t->{
            lockActiveActor(actor);
            if(jdbc.queryForObject("SELECT count(*) FROM dt_admin_import WHERE file_id=?",Long.class,fileId)>0)throw KnowledgeException.notFoundOrDenied();
            jdbc.queryForList("SELECT id FROM dt_uploaded_file WHERE id=? AND owner_id=? AND project_id=? FOR UPDATE",fileId,ownerId,projectId);
            var result=retryManaged(ownerId,projectId,fileId);
            jdbc.update("UPDATE dt_uploaded_file SET uploaded_by_user_id=?,uploaded_by_status_version=(SELECT status_version FROM dt_account WHERE id=?) WHERE id=?",actor,actor,fileId);return result;
        });
    }
    public FileView retryManaged(String ownerId, String projectId, UUID fileId) {
        FileView file = require(ownerId, projectId, fileId);
        if (!Set.of("FAILED", "OCR_REQUIRED").contains(file.parseStatus()))
            throw new KnowledgeException("IMPORT_NOT_RETRYABLE", 409);
        jdbc.update("UPDATE dt_uploaded_file SET parse_status='UPLOADED',parse_error=NULL WHERE id=? AND owner_id=? AND project_id=?",
                fileId, ownerId, projectId);
        afterCommit(() -> dispatch(ownerId, projectId, fileId));
        return require(ownerId, projectId, fileId);
    }

    /** 校验附件归属、导入状态和启停；私有附件无需发布，调查归属由 requireAttachmentConversation 校验。 */
    public List<Map<String, Object>> readyAttachments(String ownerId, String projectId,
                                                       List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) KnowledgeVisibilityGuard.lockScope(jdbc,ownerId,projectId,false);
        if (attachments.size() > 5) throw new GeneralAgentService.RequestFailure("INVALID_ATTACHMENT", 400);
        List<Map<String, Object>> validated = new ArrayList<>();
        for (Map<String, Object> attachment : attachments) {
            if (attachment == null || !attachment.keySet().equals(Set.of("fileId")))
                throw new GeneralAgentService.RequestFailure("INVALID_ATTACHMENT", 400);
            UUID fileId;
            try { fileId = UUID.fromString(String.valueOf(attachment.get("fileId"))); }
            catch (RuntimeException e) { throw new GeneralAgentService.RequestFailure("INVALID_ATTACHMENT", 400); }
            FileView file = require(ownerId, projectId, fileId);
            if (!"PARSED".equals(file.parseStatus()) || file.documentId() == null ||
                    jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document d WHERE d.id=? AND d.availability='ENABLED' AND d.ingest_status='READY'",Long.class,file.documentId())!=1)
                throw new GeneralAgentService.RequestFailure("ATTACHMENT_NOT_READY", 409);
            validated.add(Map.of("fileId", file.fileId().toString(), "documentId", file.documentId().toString(),
                    "title", file.title(), "version", file.documentVersion(), "domain", file.domain()));
        }
        return List.copyOf(validated);
    }

    public List<FileView> attachments(io.devicetruth.application.agent.AccessScope scope,UUID conversation,int offset,int limit) {
        new io.devicetruth.adapter.persistence.ConversationRepository(jdbc).require(scope.storageIdentity(),conversation,false);
        if(offset<0 || limit<1 || limit>100) throw new IllegalArgumentException("Invalid pagination");
        return jdbc.query(VIEW_SQL+" AND f.conversation_id=? ORDER BY f.created_at,f.id LIMIT ? OFFSET ?",MAPPER,
            scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),conversation,limit,offset);
    }
    public record FileContent(UUID fileId,UUID uploadedByUserId,UUID conversationId,String usageScope,String dataNature,String content) {}
    public FileContent content(String owner,String project,UUID file) {
        var view=require(owner,project,file);
        if(view.mime().equals("application/pdf")) throw new KnowledgeException("PDF_NOT_SUPPORTED",415);
        var row=jdbc.queryForMap("SELECT path_key,uploaded_by_user_id,conversation_id,usage_scope,import_data_nature FROM dt_uploaded_file WHERE id=? AND owner_id=? AND project_id=?",file,owner,project);
        try {
            String content=java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(resolve((String)row.get("path_key"))))).toString();
            return new FileContent(file,(UUID)row.get("uploaded_by_user_id"),(UUID)row.get("conversation_id"),(String)row.get("usage_scope"),(String)row.get("import_data_nature"),content);
        } catch(IOException e) {throw new KnowledgeException("FILE_CONTENT_UNAVAILABLE",503);}
    }

    public void requireAttachmentConversation(String owner,String project,UUID conversation,List<Map<String,Object>> attachments) {
        if(attachments==null) return;
        for(var attachment:attachments) {
            var rows=jdbc.queryForList("SELECT conversation_id FROM dt_uploaded_file WHERE id=? AND owner_id=? AND project_id=?",UUID.class,UUID.fromString(String.valueOf(attachment.get("fileId"))),owner,project);
            if(rows.isEmpty() || (rows.getFirst()!=null && !rows.getFirst().equals(conversation))) throw KnowledgeException.notFoundOrDenied();
        }
    }
    public record Publication(UUID publicationId,UUID documentId,String title,String applicability,
            UUID publishedByUserId,UUID originalAuthorUserId,String documentVersion,String dataNature,Instant createdAt) {}
    private org.springframework.transaction.support.TransactionTemplate transactions() {
        return new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public Publication publish(io.devicetruth.application.agent.AccessScope scope,UUID file,String title,String applicability) {
        if(title==null || title.isBlank() || title.length()>500 || applicability==null || applicability.isBlank() || applicability.length()>2000)
            throw new IllegalArgumentException("Title and applicability required");
        return transactions().execute(status->{
            var resolver=new io.devicetruth.application.agent.WorkspaceScopeResolver(jdbc);resolver.requireWrite(scope);
            jdbc.queryForObject("SELECT id FROM dt_workspace WHERE id=? FOR UPDATE",UUID.class,scope.workspaceId());
            resolver.requireWrite(scope);
            if(!new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc).lockWriteAuthorization(scope))
                throw new GeneralAgentService.RequestFailure("AUTHORIZATION_REVOKED",403);
            var f=require(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),file);
            if(!"CONVERSATION_ATTACHMENT".equals(f.usageScope())) throw new KnowledgeException("ATTACHMENT_REQUIRED",409);
            if(!"PARSED".equals(f.parseStatus()) || f.documentId()==null) throw new KnowledgeException("ATTACHMENT_NOT_READY",409);
            var doc=imports.document(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),f.documentId());
            if(!"READY".equals(doc.ingestStatus()) || doc.chunkCount()<1) throw new KnowledgeException("DOCUMENT_NOT_READY",409);
            jdbc.update("""
                INSERT INTO dt_knowledge_publication(id,workspace_id,owner_id,project_id,document_id,published_by_user_id,original_author_user_id,title,applicability)
                VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(workspace_id,document_id) DO NOTHING
                """,UUID.randomUUID(),scope.workspaceId(),scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),f.documentId(),scope.actorUserId(),f.uploadedByUserId(),title.strip(),applicability.strip());
            return jdbc.queryForObject("SELECT * FROM dt_knowledge_publication WHERE workspace_id=? AND document_id=?",
                (r,n)->new Publication(r.getObject("id",UUID.class),f.documentId(),r.getString("title"),r.getString("applicability"),r.getObject("published_by_user_id",UUID.class),r.getObject("original_author_user_id",UUID.class),doc.docVersion(),doc.dataNature(),r.getTimestamp("created_at").toInstant()),scope.workspaceId(),f.documentId());
        });
    }
    public record KnowledgeDocument(UUID documentId,String title,String documentVersion,String dataNature,String ingestStatus,
            String applicability,UUID publishedByUserId,UUID originalAuthorUserId,Instant publishedAt) {}
    public List<KnowledgeDocument> knowledge(io.devicetruth.application.agent.AccessScope scope,int offset,int limit) {
        if(offset<0 || limit<1 || limit>100) throw new IllegalArgumentException("Invalid pagination");
        return jdbc.query("""
            SELECT d.id AS document_id,COALESCE(p.title,d.title) AS title,d.doc_version,d.data_nature,d.ingest_status,
                p.applicability,p.published_by_user_id,p.original_author_user_id,p.created_at AS published_at
            FROM dt_knowledge_document d LEFT JOIN dt_knowledge_publication p ON p.document_id=d.id
            WHERE d.owner_id=? AND d.project_id=? AND d.ingest_status='READY' AND
                (p.id IS NOT NULL OR EXISTS(SELECT 1 FROM dt_knowledge_document_file l JOIN dt_uploaded_file f ON f.id=l.file_id
                    WHERE l.document_id=d.id AND f.usage_scope='SPACE_KNOWLEDGE'))
            ORDER BY d.created_at,d.id LIMIT ? OFFSET ?
            """,(r,n)->new KnowledgeDocument(r.getObject("document_id",UUID.class),r.getString("title"),r.getString("doc_version"),r.getString("data_nature"),r.getString("ingest_status"),r.getString("applicability"),r.getObject("published_by_user_id",UUID.class),r.getObject("original_author_user_id",UUID.class),r.getTimestamp("published_at")==null?null:r.getTimestamp("published_at").toInstant()),scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),limit,offset);
    }

    private void dispatch(String ownerId, String projectId, UUID fileId) {
        synchronized(dispatchLock){if(!dispatched.add(fileId)){redispatchRequested.add(fileId);return;}}
        try {
            executor.execute(() -> {try{process(ownerId,projectId,fileId);}finally{
                boolean again;synchronized(dispatchLock){dispatched.remove(fileId);again=redispatchRequested.remove(fileId);}
                if(again && jdbc.queryForObject("SELECT count(*) FROM dt_uploaded_file WHERE id=? AND parse_status IN ('UPLOADED','PROCESSING')",Long.class,fileId)>0)dispatch(ownerId,projectId,fileId);
            }});
        } catch (RejectedExecutionException e) {
            synchronized(dispatchLock){dispatched.remove(fileId);redispatchRequested.remove(fileId);}
            fail(ownerId, projectId, fileId, "IMPORT_QUEUE_REJECTED");
        }
    }

    private void process(String ownerId, String projectId, UUID fileId) {
        boolean managed=jdbc.queryForObject("SELECT count(*) FROM dt_admin_import WHERE file_id=?",Long.class,fileId)>0;
        java.util.concurrent.ScheduledFuture<?> heartbeat=null;
        if(managed) {
            if(jdbc.update("UPDATE dt_admin_import SET import_lease_owner=?,import_lease_until=clock_timestamp()+interval '60 seconds' WHERE file_id=? AND (import_lease_until IS NULL OR import_lease_until<clock_timestamp())",importWorker,fileId)!=1)return;
            heartbeat=managedRecovery.scheduleWithFixedDelay(()->{try{jdbc.update("UPDATE dt_admin_import SET import_lease_until=clock_timestamp()+interval '60 seconds' WHERE file_id=? AND import_lease_owner=? AND import_lease_until>clock_timestamp()",fileId,importWorker);}catch(RuntimeException e){LOG.error("Managed import heartbeat failed: {}",e.getClass().getSimpleName());}},15,15,TimeUnit.SECONDS);
        }
        try {
            managedGuard.accept(fileId);
            var rows = jdbc.queryForList("SELECT path_key,import_title,import_domain,import_device_model,"
                    + "import_version,import_data_nature,original_name FROM dt_uploaded_file "
                    + "WHERE id=? AND owner_id=? AND project_id=? AND parse_status IN ('UPLOADED','PROCESSING')",
                fileId, ownerId, projectId);
            if (rows.isEmpty()) return;
            var row = rows.getFirst();
            jdbc.update("UPDATE dt_uploaded_file SET parse_status='PROCESSING',parse_error=NULL "
                    + "WHERE id=? AND owner_id=? AND project_id=?", fileId, ownerId, projectId);
            String originalName = String.valueOf(row.get("original_name"));
            if (!originalName.toLowerCase(Locale.ROOT).endsWith(".md") && !originalName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                // 防御：PDF 等未支持格式不得进入 Markdown 解析，绝不生成伪造正文或页码
                fail(ownerId, projectId, fileId, "PDF_NOT_SUPPORTED");
                return;
            }
            byte[] bytes = Files.readAllBytes(resolve((String) row.get("path_key")));
            var request = new KnowledgeImportService.ImportRequest((String) row.get("import_title"),
                    (String) row.get("import_domain"), (String) row.get("import_device_model"),
                    (String) row.get("import_version"), (String) row.get("original_name"), "text/markdown",
                    "uploaded markdown", (String) row.get("import_data_nature"));
            if(originalName.toLowerCase(Locale.ROOT).endsWith(".txt")) imports.importTextFile(ownerId,projectId,request,bytes,fileId);
            else imports.importMarkdown(ownerId, projectId, request, bytes, fileId);
        } catch (KnowledgeException e) {
            fail(ownerId, projectId, fileId, e.code());
        } catch (IOException | RuntimeException e) {
            LOG.error("Markdown import failed: fileId={} type={}", fileId, e.getClass().getSimpleName());
            fail(ownerId, projectId, fileId, "IMPORT_FAILED");
        } finally {
            if(heartbeat!=null)heartbeat.cancel(false);
            if(managed)jdbc.update("UPDATE dt_admin_import SET import_lease_owner=NULL,import_lease_until=NULL,updated_at=clock_timestamp() WHERE file_id=? AND import_lease_owner=?",fileId,importWorker);
        }
    }

    private void fail(String ownerId, String projectId, UUID fileId, String code) {
        var owners=jdbc.queryForList("SELECT import_lease_owner FROM dt_admin_import WHERE file_id=?",String.class,fileId);
        if(!owners.isEmpty() && owners.getFirst()!=null && !owners.getFirst().equals(importWorker))return;
        if(jdbc.queryForObject("SELECT count(*) FROM dt_uploaded_file WHERE id=? AND parse_status IN ('UPLOADED','PROCESSING')",Long.class,fileId)==0)return;
        managedCompletion.accept(fileId,code);
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

    @Override public void close() { executor.shutdownNow();managedRecovery.shutdownNow(); }
}
