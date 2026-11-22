package io.devicetruth.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import io.devicetruth.core.capability.ToolContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL remains authoritative; each channel receives the same authorized snapshot. */
public final class ScopedKnowledgeStore {
    public record Child(UUID id, UUID documentId, UUID parentId, UUID releaseId, String knowledgeId,
                        int chunkNo, Integer page, String section, String title, String documentVersion,
                        String content, String dataNature, String domain, String deviceModel,
                        JsonNode metadata, JsonNode sourceLocations) {}
    public record Snapshot(String owner, String project, String embeddingProfile, String fingerprint, List<Child> children) {
        public Snapshot { children=List.copyOf(children); }
    }
    private final JdbcTemplate jdbc;
    private final EmbeddingProfile profile;
    public ScopedKnowledgeStore(JdbcTemplate jdbc,EmbeddingProfile profile){this.jdbc=jdbc;this.profile=profile;}
    public Snapshot load(ToolContext context,KnowledgeSearchService.KnowledgeFilters filters) {
        var f=filters==null?KnowledgeSearchService.KnowledgeFilters.NONE:filters;
        // Run 冻结的私有附件同样可检索；无 Run 的离线检索仍只读取空间知识和显式发布。
        String sql="""
            SELECT c.id,c.document_id,c.parent_id,c.release_id,d.knowledge_id,c.chunk_no,c.page_no,c.section,
              d.title,d.doc_version,c.content,d.data_nature,d.domain,d.device_model,
              COALESCE(d.normalized_metadata,'{}')::text AS metadata,COALESCE(c.source_locations,'[]')::text AS locations
            FROM dt_knowledge_chunk c JOIN dt_knowledge_document d
              ON d.id=c.document_id AND d.owner_id=c.owner_id AND d.project_id=c.project_id
            WHERE c.owner_id=? AND c.project_id=? AND c.embedding_profile=? AND d.embedding_profile=?
              AND d.embedding_model=? AND d.embedding_dimension=?
              AND c.release_id IS NOT DISTINCT FROM d.release_id
 AND d.availability='ENABLED'
              AND (EXISTS(SELECT 1 FROM dt_uploaded_file vf WHERE vf.id=d.source_file_id AND
                (vf.usage_scope='SPACE_KNOWLEDGE' OR EXISTS(SELECT 1 FROM dt_knowledge_publication vp
                  JOIN dt_workspace vw ON vw.id=vp.workspace_id WHERE vp.document_id=d.id AND vw.owner_id=d.owner_id AND vw.project_id=d.project_id)))
                OR %s)
              AND (d.ingest_status='READY' OR (d.ingest_status='STAGED' AND d.release_id IS NOT NULL))
              AND (d.release_id IS NULL OR EXISTS (SELECT 1 FROM dt_knowledge_release r
                WHERE r.id=d.release_id AND r.owner_id=d.owner_id AND r.project_id=d.project_id
                AND r.embedding_profile=d.embedding_profile AND r.status='READY'
            """.formatted(MaterialScope.allowed("?::uuid","d.id"));
        var args=new ArrayList<Object>(List.of(context.ownerId(),context.projectId(),profile.profileVersion(),profile.profileVersion(),profile.model(),profile.dimension()));
        args.add(context.runId());
        if(f.releaseId()==null)sql+=" AND r.active AND NOT r.is_test";
        sql+="))";
        if(f.releaseId()!=null){sql+=" AND d.release_id=?";args.add(f.releaseId());}
        if(f.indexProfile()!=null){sql+=" AND EXISTS(SELECT 1 FROM dt_knowledge_release r WHERE r.id=d.release_id AND r.owner_id=d.owner_id AND r.project_id=d.project_id AND r.index_profile=?)";args.add(f.indexProfile());}
        sql+=" ORDER BY c.id";
        var children=jdbc.query(sql,(r,n)->new Child(r.getObject("id",UUID.class),r.getObject("document_id",UUID.class),r.getObject("parent_id",UUID.class),r.getObject("release_id",UUID.class),r.getString("knowledge_id"),r.getInt("chunk_no"),r.getObject("page_no",Integer.class),r.getString("section"),r.getString("title"),r.getString("doc_version"),r.getString("content"),r.getString("data_nature"),r.getString("domain"),r.getString("device_model"),parse(r.getString("metadata")),parse(r.getString("locations"))),args.toArray());
        String fingerprint=KnowledgeCorpus.hash(Json.write(children));
        return new Snapshot(context.ownerId(),context.projectId(),profile.profileVersion(),fingerprint,children);
    }
    private static JsonNode parse(String s){try{return Json.MAPPER.readTree(s);}catch(Exception e){throw new KnowledgeException("KNOWLEDGE_METADATA_INVALID",409);}}
}
