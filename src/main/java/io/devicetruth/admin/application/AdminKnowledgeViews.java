package io.devicetruth.admin.application;

import static io.devicetruth.admin.application.AdminService.*;
import io.devicetruth.admin.api.*;
import io.devicetruth.admin.api.AdminPages.*;
import io.devicetruth.admin.authorization.AdminAuthorization;
import java.sql.Timestamp;
import java.util.*;

/** 仅空间资料元数据；调查附件必须已有显式发布。 */
public final class AdminKnowledgeViews {
    private final AdminService service;
    private final AdminPages pages;
    public AdminKnowledgeViews(AdminService service,AdminPages pages){this.service=service;this.pages=pages;}
    private static final String DOCUMENT_SQL="""
            SELECT d.id document_id,w.id workspace_id,d.title,d.domain,
              COALESCE((SELECT array_agg(km.model_text ORDER BY km.model_key) FROM dt_knowledge_model km WHERE km.document_id=d.id),CASE WHEN d.device_model IS NULL THEN ARRAY[]::text[] ELSE ARRAY[d.device_model] END) models,
              d.doc_version version,COALESCE(d.source_nature,CASE WHEN d.data_nature='SIMULATED' THEN 'SIMULATED' ELSE 'UNKNOWN' END) source_nature,d.data_nature access_nature,
              d.ingest_status,d.availability,d.management_revision revision,'WORKSPACE_KNOWLEDGE' material_scope,
              CASE WHEN p.id IS NOT NULL THEN 'PUBLISHED' WHEN f.usage_scope='SPACE_KNOWLEDGE' THEN 'WORKSPACE_KNOWLEDGE' ELSE 'UNPUBLISHED' END publication_status,
              k.active release_active,d.created_at,d.management_updated_at updated_at
            FROM dt_knowledge_document d JOIN dt_workspace w ON w.owner_id=d.owner_id AND w.project_id=d.project_id
            JOIN dt_uploaded_file f ON f.id=d.source_file_id LEFT JOIN dt_knowledge_publication p ON p.document_id=d.id AND p.workspace_id=w.id
            LEFT JOIN dt_knowledge_release k ON k.id=d.release_id
            WHERE w.id=? AND (f.usage_scope='SPACE_KNOWLEDGE' OR p.id IS NOT NULL)
            """;
    public Page<Map<String,Object>> documents(UUID actor,UUID workspace,String header,Map<String,String> query) {
        AdminPages.validate(query,"workspaceId","q","availability","ingestStatus");service.authorization.workspace(actor,workspace,"KNOWLEDGE_READ",header);
        AdminPages.choice(query.get("availability"),"ENABLED","DISABLED");AdminPages.choice(query.get("ingestStatus"),"READY","FAILED","NEEDS_REBUILD","STAGED");
        var context=service.authorization.require(actor);String endpoint="documents/"+workspace;Position pos=pages.position(endpoint,context,query);
        var sql=new Query(DOCUMENT_SQL+" AND d.created_at<=?",workspace,Timestamp.from(pos.snapshotAt()));
        String q=search(query);if(q!=null)sql.add(" AND position(? in lower(d.title))>0",q);
        if(query.containsKey("ingestStatus"))sql.add(" AND d.ingest_status=?",query.get("ingestStatus"));
        if(query.containsKey("availability"))sql.add(" AND d.availability=?",query.get("availability"));
        descending(sql,pos,"d.created_at","d.id");sql.add(" ORDER BY d.created_at DESC,d.id DESC LIMIT ?",AdminPages.limit(query)+1);
        return service.page(endpoint,context,query,pos,service.rows(sql),"createdAt","documentId");
    }
    private static final String SECTION_SQL="""
            WITH ordered AS (SELECT id,section,chunk_no,CASE WHEN lag(section) OVER(ORDER BY chunk_no) IS DISTINCT FROM section THEN 1 ELSE 0 END boundary
                FROM dt_knowledge_chunk WHERE document_id=?),
            numbered AS (SELECT *,sum(boundary) OVER(ORDER BY chunk_no) section_group FROM ordered)
            SELECT (array_agg(id ORDER BY chunk_no))[1] section_id,max(section) heading,min(chunk_no) ordinal,count(*) chunk_count
            FROM numbered GROUP BY section_group
            """;
    public Map<String,Object> document(UUID actor,UUID workspace,String header,UUID id) {
        service.authorization.workspace(actor,workspace,"KNOWLEDGE_READ",header);
        var document=service.one(new Query(DOCUMENT_SQL+" AND d.id=?",workspace,id));
        var sections=service.rows(new Query(SECTION_SQL+" ORDER BY ordinal,section_id LIMIT 101",id));
        long count=((Number)service.one(new Query("SELECT count(*) n FROM dt_knowledge_chunk WHERE document_id=?",id)).get("n")).longValue();
        long sectionCount=((Number)service.one(new Query("SELECT count(*) n FROM ("+SECTION_SQL+") sections",id)).get("n")).longValue();
        var files=service.rows(new Query(FILE_SQL+" AND df.document_id=? ORDER BY COALESCE(m.created_at,f.created_at) DESC,f.id DESC LIMIT 1",workspace,id));
        return map("document",document,"file",files.isEmpty()?null:files.getFirst(),"sectionCount",sectionCount,"chunkCount",count,"sections",sections.subList(0,Math.min(100,sections.size())),"sectionsComplete",sections.size()<=100);
    }
    public Page<Map<String,Object>> sections(UUID actor,UUID workspace,String header,UUID id,Map<String,String> query) {
        AdminPages.validate(query,"workspaceId");service.authorization.workspace(actor,workspace,"KNOWLEDGE_READ",header);
        service.one(new Query(DOCUMENT_SQL+" AND d.id=?",workspace,id));
        var context=service.authorization.require(actor);String endpoint="sections/"+workspace+"/"+id;Position pos=pages.position(endpoint,context,query);
        var sql=new Query("SELECT * FROM ("+SECTION_SQL+") sections WHERE TRUE",id);
        if(pos.lastId()!=null)sql.add(" AND ordinal>?",Integer.parseInt(pos.lastId()));
        sql.add(" ORDER BY ordinal,section_id LIMIT ?",AdminPages.limit(query)+1);
        return service.page(endpoint,context,query,pos,service.rows(sql),null,"ordinal");
    }
    private static final String FILE_SQL="""
            SELECT f.id file_id,w.id workspace_id,f.parse_status processing_status,df.document_id,
              CASE WHEN f.parse_error IN ('IMPORT_AUTHORIZATION_REVOKED','IMPORT_QUEUE_REJECTED','INVALID_TEXT_ENCODING','DOCUMENT_TOO_LARGE','EMBEDDING_TIMEOUT','IMPORT_FAILED') THEN f.parse_error
                WHEN f.parse_error IS NULL THEN NULL ELSE 'IMPORT_FAILED' END error_code,
              (m.file_id IS NOT NULL AND f.parse_status='FAILED') retryable,COALESCE(m.revision,0) revision,
              COALESCE(m.created_at,f.created_at) created_at,COALESCE(m.updated_at,f.created_at) updated_at,
              CASE WHEN m.file_id IS NULL THEN 'CORPUS_IMPORT' ELSE 'ADMIN_UPLOAD' END import_origin
            FROM dt_uploaded_file f JOIN dt_workspace w ON w.owner_id=f.owner_id AND w.project_id=f.project_id
            LEFT JOIN dt_admin_import m ON m.file_id=f.id AND m.workspace_id=w.id
            LEFT JOIN dt_knowledge_document_file df ON df.file_id=f.id
            WHERE w.id=? AND f.usage_scope='SPACE_KNOWLEDGE' AND (m.file_id IS NOT NULL OR EXISTS (
              SELECT 1 FROM dt_knowledge_document d JOIN dt_knowledge_import_item i ON i.release_id=d.release_id
                AND i.knowledge_id=d.knowledge_id AND i.owner_id=d.owner_id AND i.project_id=d.project_id
              WHERE d.id=df.document_id AND d.source_file_id=f.id AND d.owner_id=f.owner_id AND d.project_id=f.project_id))
            """;
    public Map<String,Object> file(UUID actor,UUID workspace,String header,UUID file) {
        service.authorization.workspace(actor,workspace,"KNOWLEDGE_READ",header);
        return service.one(new Query(FILE_SQL+" AND f.id=?",workspace,file));
    }
    public Page<Map<String,Object>> emptyManaged(UUID actor,UUID workspace,String header,Map<String,String> query,String kind) {
        if(kind.equals("files")){AdminPages.validate(query,"workspaceId","processingStatus");AdminPages.choice(query.get("processingStatus"),"UPLOADED","PROCESSING","PARSED","FAILED","OCR_REQUIRED");}
        else AdminPages.validate(query,"workspaceId");
        service.authorization.workspace(actor,workspace,"KNOWLEDGE_READ",header);
        var context=service.authorization.require(actor);Position pos=pages.position(kind+"/"+workspace,context,query);
        if(kind.equals("sources")) {
            var target=service.authorization.workspace(actor,workspace,"KNOWLEDGE_READ",header);
            var sql=new Query("SELECT DISTINCT s.source_id,COALESCE(s.source_record->>'title',s.source_id) display_label,COALESCE(s.source_record->>'document_type','UNKNOWN') source_nature,'EXISTING_PROJECT_RESEARCH' access_nature,ARRAY['EXISTING_PROJECT_RESEARCH']::text[] allowed_license_codes FROM dt_knowledge_source s JOIN dt_knowledge_release r ON r.id=s.release_id WHERE s.owner_id=? AND s.project_id=? AND r.created_at<=?",target.owner(),target.project(),Timestamp.from(pos.snapshotAt()));
            if(pos.lastId()!=null)sql.add(" AND s.source_id>?",pos.lastId());
            sql.add(" ORDER BY s.source_id LIMIT ?",AdminPages.limit(query)+1);
            return service.page(kind+"/"+workspace,context,query,pos,service.rows(sql),null,"sourceId");
        }
        var sql=new Query(FILE_SQL+" AND COALESCE(m.created_at,f.created_at)<=?",workspace,Timestamp.from(pos.snapshotAt()));
        if(query.containsKey("processingStatus"))sql.add(" AND f.parse_status=?",query.get("processingStatus"));
        descending(sql,pos,"COALESCE(m.created_at,f.created_at)","f.id");sql.add(" ORDER BY COALESCE(m.created_at,f.created_at) DESC,f.id DESC LIMIT ?",AdminPages.limit(query)+1);
        return service.page(kind+"/"+workspace,context,query,pos,service.rows(sql),"createdAt","fileId");
    }
}
