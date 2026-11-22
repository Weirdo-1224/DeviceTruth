package io.devicetruth.admin.application;

import static io.devicetruth.admin.application.AdminService.*;
import io.devicetruth.admin.api.*;
import io.devicetruth.admin.persistence.AdminAudit;
import io.devicetruth.knowledge.*;
import java.nio.file.Path;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 复用正式上传/解析/向量化；管理 actor 不冒充目标空间成员。 */
public final class AdminKnowledgeOperations {
    private final AdminService service;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final FileUploadService files;
    private final AdminAudit audit;
    private final Path corpusRoot;
    private volatile List<KnowledgeCorpus.Document> corpus;
    private final AdminPages pages;
    public AdminKnowledgeOperations(AdminService service,JdbcTemplate jdbc,TransactionTemplate transactions,FileUploadService files,
            KnowledgeImportService imports,Path corpusRoot,AdminPages pages) {
        this.service=service;this.jdbc=jdbc;this.transactions=transactions;this.files=files;this.corpusRoot=corpusRoot;this.audit=new AdminAudit(jdbc);this.pages=pages;
        service.knowledgeReadiness(this::ready);
        files.managedAuthorization(this::authorizeFile,this::failed);
        imports.managedAuthorization(this::authorizeFile,this::succeeded);
        files.enableManagedRecovery();
    }
    public boolean ready() {try{return !corpus().isEmpty();}catch(RuntimeException e){return false;}}
    private List<KnowledgeCorpus.Document> corpus() {
        if(corpus==null) synchronized(this) {if(corpus==null)corpus=KnowledgeCorpus.load(corpusRoot);}
        return corpus;
    }
    public List<Map<String,Object>> sources(UUID actor,UUID workspace,String header) {
        service.authorization.workspace(actor,workspace,"KNOWLEDGE_READ",header);
        if(!ready())throw new AdminFailure(503,"INTEGRATION_NOT_READY");
        var sources=new TreeMap<String,Map<String,Object>>();
        for(var doc:corpus()) for(var source:doc.sources().entrySet()) sources.putIfAbsent(source.getKey(),map("sourceId",source.getKey(),"displayLabel",source.getValue().get("title"),
                "sourceNature",source.getValue().getOrDefault("data_nature","UNKNOWN"),"accessNature","EXISTING_PROJECT_RESEARCH","allowedLicenseCodes",List.of(KnowledgeCorpus.AUTHORIZATION_BASIS)));
        return List.copyOf(sources.values());
    }
    public AdminPages.Page<Map<String,Object>> sourcesPage(UUID actor,UUID workspace,String header,Map<String,String> query) {
        AdminPages.validate(query,"workspaceId");var context=service.authorization.require(actor);
        var pos=pages.position("registered-sources/"+workspace,context,query);
        var values=sources(actor,workspace,header).stream().filter(s->pos.lastId()==null || s.get("sourceId").toString().compareTo(pos.lastId())>0).limit(AdminPages.limit(query)+1L).toList();
        return service.page("registered-sources/"+workspace,context,query,pos,values,null,"sourceId");
    }
    public Map<String,Object> upload(UUID actor,UUID request,UUID workspace,String header,MultipartFile file,Map<String,String> input) {
        service.authorization.workspace(actor,workspace,"KNOWLEDGE_MANAGE",header);
        if(!ready())throw new AdminFailure(503,"INTEGRATION_NOT_READY");
        if(file==null || file.isEmpty())throw AdminFailure.input();
        if(file.getSize()>KnowledgeImportService.MAX_DOCUMENT_BYTES)throw new AdminFailure(413,"FILE_TOO_LARGE");
        if(!Set.of("text/markdown","text/x-markdown").contains(String.valueOf(file.getContentType())))throw new AdminFailure(415,"UNSUPPORTED_MEDIA_TYPE");
        if(!KnowledgeCorpus.AUTHORIZATION_BASIS.equals(input.get("licenseCode")))throw AdminFailure.input();
        if(input.containsKey("models"))try {
            Object values=io.devicetruth.adapter.Json.MAPPER.readValue(input.get("models"),Object.class);
            if(!(values instanceof List<?> models) || models.size()>20 || models.stream().anyMatch(m->!(m instanceof String value) || value.length()>128))throw AdminFailure.input();
        }catch(java.io.IOException e){throw AdminFailure.input();}
        byte[] bytes;try{bytes=file.getBytes();}catch(java.io.IOException e){throw new AdminFailure(503,"FILE_STORAGE_UNAVAILABLE");}
        var document=corpus().stream().filter(d->d.originalChecksum().equals(KnowledgeCorpus.hash(bytes)) && d.sourceIds().contains(input.get("sourceId"))).findFirst()
                .orElseThrow(()->new AdminFailure(400,"SOURCE_DOCUMENT_NOT_REGISTERED"));
        String title=input.getOrDefault("title",document.title()),version=input.getOrDefault("version","admin-2026.1");
        var metadata=new FileUploadService.ImportMetadata(title,document.domain(),document.deviceModels().isEmpty()?null:KnowledgeCorpus.modelKey(document.deviceModels().getFirst()),version,KnowledgeCorpus.ACCESS_NATURE);
        return transactions.execute(tx->{
            service.authorization.lock(actor);var target=service.authorization.workspace(actor,workspace,"KNOWLEDGE_MANAGE",header);
            KnowledgeVisibilityGuard.lockScope(jdbc,target.owner(),target.project(),true);
            var existing=jdbc.queryForList("SELECT id FROM dt_uploaded_file WHERE owner_id=? AND project_id=? AND checksum=? ORDER BY created_at,id FOR UPDATE",UUID.class,target.owner(),target.project(),KnowledgeCorpus.hash(bytes));
            FileUploadService.FileView accepted;
            if(existing.isEmpty()) accepted=files.upload(target.owner(),target.project(),file,metadata);
            else accepted=files.require(target.owner(),target.project(),existing.getFirst());
            if(!existing.isEmpty() && Set.of("UPLOADED","PROCESSING").contains(accepted.parseStatus()) && jdbc.queryForObject("SELECT count(*) FROM dt_admin_import WHERE file_id=?",Long.class,accepted.fileId())==0)throw new AdminFailure(409,"RETRY_NOT_ALLOWED");
            if(jdbc.queryForObject("SELECT count(*) FROM dt_uploaded_file WHERE id=? AND usage_scope='SPACE_KNOWLEDGE'",Long.class,accepted.fileId())!=1)throw AdminFailure.missing();
            long epoch=jdbc.queryForObject("SELECT status_version FROM dt_account WHERE id=?",Long.class,actor);
            jdbc.update("INSERT INTO dt_admin_import(file_id,workspace_id,actor_user_id,actor_status_version,source_id,license_code,request_id) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",accepted.fileId(),workspace,actor,epoch,input.get("sourceId"),input.get("licenseCode"),request);
            UUID auditId=audit.append(actor,null,workspace,"IMPORT_FILE",accepted.fileId().toString(),"KNOWLEDGE_IMPORT_ACCEPT",existing.isEmpty()?"SUCCESS":"NO_CHANGE","OPS_REQUEST",null,request,null,map("status",accepted.parseStatus(),"revision",0));
            return accepted(accepted.fileId(),workspace,accepted.parseStatus(),auditId);
        });
    }
    public Map<String,Object> retry(UUID actor,UUID request,UUID workspace,String header,UUID file,long revision,String reason) {
        AdminService.reason(reason);
        return transactions.execute(tx->{
            service.authorization.lock(actor);var target=service.authorization.workspace(actor,workspace,"KNOWLEDGE_MANAGE",header);
            var rows=jdbc.queryForList("SELECT revision FROM dt_admin_import WHERE file_id=? AND workspace_id=? FOR UPDATE",file,workspace);
            if(rows.isEmpty())throw AdminFailure.missing();long current=((Number)rows.getFirst().get("revision")).longValue();
            if(current!=revision)throw new AdminFailure(409,"STATE_VERSION_CONFLICT",Map.of("currentVersion",current));
            var old=files.require(target.owner(),target.project(),file);if(!old.parseStatus().equals("FAILED"))throw new AdminFailure(409,"RETRY_NOT_ALLOWED");
            long epoch=jdbc.queryForObject("SELECT status_version FROM dt_account WHERE id=?",Long.class,actor);
            jdbc.update("UPDATE dt_admin_import SET actor_user_id=?,actor_status_version=?,request_id=?,revision=revision+1,updated_at=clock_timestamp() WHERE file_id=?",actor,epoch,request,file);
            var accepted=files.retryManaged(target.owner(),target.project(),file);
            UUID auditId=audit.append(actor,null,workspace,"IMPORT_FILE",file.toString(),"KNOWLEDGE_RETRY_ACCEPT","SUCCESS",reason,null,request,map("status","FAILED","revision",current),map("status",accepted.parseStatus(),"revision",current+1));
            return accepted(file,workspace,accepted.parseStatus(),auditId);
        });
    }
    public Map<String,Object> status(UUID actor,UUID request,UUID workspace,String header,UUID document,String status,long revision,String reason) {
        reason(reason);AdminPages.choice(status,"ENABLED","DISABLED");if(status==null || revision<0)throw AdminFailure.input();
        return transactions.execute(tx->{
            service.authorization.lock(actor);var target=service.authorization.workspace(actor,workspace,"KNOWLEDGE_MANAGE",header);
            KnowledgeVisibilityGuard.lockScope(jdbc,target.owner(),target.project(),true);
            var row=service.one(new Query("SELECT d.id,d.availability,d.management_revision,d.ingest_status,d.source_nature,d.data_nature,d.normalized_metadata->>'verification_status' verification_status,f.usage_scope,d.release_id,r.active,EXISTS(SELECT 1 FROM dt_knowledge_publication p WHERE p.document_id=d.id AND p.workspace_id=?) published FROM dt_knowledge_document d JOIN dt_uploaded_file f ON f.id=d.source_file_id LEFT JOIN dt_knowledge_release r ON r.id=d.release_id WHERE d.id=? AND d.owner_id=? AND d.project_id=? FOR UPDATE OF d",workspace,document,target.owner(),target.project()));
            if(!"SPACE_KNOWLEDGE".equals(row.get("usageScope")) && !Boolean.TRUE.equals(row.get("published")))throw AdminFailure.missing();
            long current=((Number)row.get("managementRevision")).longValue();if(current!=revision)throw new AdminFailure(409,"STATE_VERSION_CONFLICT",Map.of("currentVersion",current));
            boolean changed=!status.equals(row.get("availability"));
            if(status.equals("ENABLED")) {
                boolean validSource="SIMULATED".equals(row.get("dataNature")) || Set.of("REAL","PROJECT_RULE").contains(String.valueOf(row.get("sourceNature"))) && "VERIFIED".equals(row.get("verificationStatus"));
                String blocking=!"READY".equals(row.get("ingestStatus"))?"PROCESSING_NOT_READY":!validSource?"SOURCE_NOT_VALID":row.get("releaseId")!=null && !Boolean.TRUE.equals(row.get("active"))?"RELEASE_INACTIVE":null;
                if(blocking!=null)throw new AdminFailure(409,"KNOWLEDGE_NOT_RESTORABLE",Map.of("blockingCode",blocking));
            }
            if(changed)jdbc.update("UPDATE dt_knowledge_document SET availability=?,management_revision=management_revision+1,management_updated_at=clock_timestamp() WHERE id=?",status,document);
            UUID auditId=audit.append(actor,null,workspace,"KNOWLEDGE_DOCUMENT",document.toString(),"KNOWLEDGE_STATUS",changed?"SUCCESS":"NO_CHANGE",reason,null,request,map("status",row.get("availability"),"revision",current),map("status",status,"revision",changed?current+1:current));
            return map("document",new AdminKnowledgeViews(service,pages).document(actor,workspace,header,document).get("document"),"changed",changed,"auditId",auditId);
        });
    }
    private static Map<String,Object> accepted(UUID file,UUID workspace,String status,UUID auditId) {return map("fileId",file,"workspaceId",workspace,"processingStatus",status,"statusUrl","/api/admin/knowledge/files/"+file+"?workspaceId="+workspace,"auditId",auditId);}
    private void authorizeFile(UUID file) {
        var rows=jdbc.queryForList("SELECT actor_user_id,actor_status_version,workspace_id,source_id FROM dt_admin_import WHERE file_id=?"+(TransactionSynchronizationManager.isActualTransactionActive()?" FOR SHARE":""),file);
        if(rows.isEmpty()) {authorizeProductFile(file);return;}var row=rows.getFirst();UUID actor=(UUID)row.get("actor_user_id");
        try {
            if(!files.managedLeaseValid(file))throw AdminFailure.missing();
            if(TransactionSynchronizationManager.isActualTransactionActive())service.authorization.lock(actor);
            service.authorization.workspace(actor,(UUID)row.get("workspace_id"),"KNOWLEDGE_MANAGE",null);
            if(jdbc.queryForObject("SELECT status_version FROM dt_account WHERE id=?",Long.class,actor)!=((Number)row.get("actor_status_version")).longValue())throw AdminFailure.missing();
            if(corpus().stream().noneMatch(d->d.sourceIds().contains(row.get("source_id"))))throw AdminFailure.missing();
        } catch(RuntimeException e) {throw new KnowledgeException("IMPORT_AUTHORIZATION_REVOKED",403);}
    }
    private void authorizeProductFile(UUID file) {
        var rows=jdbc.queryForList("SELECT f.uploaded_by_user_id,f.uploaded_by_status_version,w.id workspace_id FROM dt_uploaded_file f JOIN dt_workspace w ON w.owner_id=f.owner_id AND w.project_id=f.project_id WHERE f.id=? AND f.uploaded_by_user_id IS NOT NULL",file);
        if(rows.isEmpty())return;var row=rows.getFirst();UUID actor=(UUID)row.get("uploaded_by_user_id"),workspace=(UUID)row.get("workspace_id");
        String sql="SELECT a.id FROM dt_account a JOIN dt_workspace_member m ON m.user_id=a.id WHERE a.id=? AND a.status='ACTIVE' AND a.status_version=? AND m.workspace_id=? AND m.role IN ('ADMIN','COLLABORATOR')";
        if(TransactionSynchronizationManager.isActualTransactionActive())sql+=" FOR SHARE OF a,m";
        if(jdbc.queryForList(sql,actor,row.get("uploaded_by_status_version"),workspace).isEmpty())throw new KnowledgeException("IMPORT_AUTHORIZATION_REVOKED",403);
    }
    private void succeeded(UUID file) {
        var rows=jdbc.queryForList("SELECT m.actor_user_id,m.workspace_id,m.request_id,f.checksum FROM dt_admin_import m JOIN dt_uploaded_file f ON f.id=m.file_id WHERE m.file_id=?",file);
        if(rows.isEmpty())return;var row=rows.getFirst();
        var document=corpus().stream().filter(d->d.originalChecksum().equals(row.get("checksum"))).findFirst().orElseThrow();
        jdbc.update("UPDATE dt_knowledge_document SET source_nature=COALESCE(source_nature,?),normalized_metadata=COALESCE(normalized_metadata,?::jsonb) WHERE id IN (SELECT document_id FROM dt_knowledge_document_file WHERE file_id=?)",document.sourceNature(),io.devicetruth.adapter.Json.write(document.metadata("managed-existing-corpus")),file);
        for(String model:document.deviceModels())jdbc.update("INSERT INTO dt_knowledge_model(document_id,owner_id,project_id,model_key,model_text) SELECT document_id,owner_id,project_id,?,? FROM dt_knowledge_document_file WHERE file_id=? ON CONFLICT DO NOTHING",KnowledgeCorpus.modelKey(model),model,file);
        audit.append((UUID)row.get("actor_user_id"),null,(UUID)row.get("workspace_id"),"IMPORT_FILE",file.toString(),"KNOWLEDGE_IMPORT_PROCESS","SUCCESS",null,null,(UUID)row.get("request_id"),null,Map.of("status","PARSED"));
    }
    private void failed(UUID file,String code) {
        try {var rows=jdbc.queryForList("SELECT actor_user_id,workspace_id,request_id FROM dt_admin_import WHERE file_id=?",file);if(rows.isEmpty())return;var row=rows.getFirst();
            audit.append((UUID)row.get("actor_user_id"),null,(UUID)row.get("workspace_id"),"IMPORT_FILE",file.toString(),"KNOWLEDGE_IMPORT_PROCESS","FAILURE",null,"IMPORT_PROCESS_FAILED",(UUID)row.get("request_id"),null,null);
        }catch(RuntimeException ignored){}
    }
}
