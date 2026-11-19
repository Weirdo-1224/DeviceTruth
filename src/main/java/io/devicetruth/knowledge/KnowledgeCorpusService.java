package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.contract.Checks;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Corpus adapter on the existing Knowledge storage and production EmbeddingClient. */
public final class KnowledgeCorpusService {
    public record Prepared(KnowledgeCorpus.Document document,UUID documentId,List<ContextualKnowledgeChunker.Child> children) {}
    public record Plan(UUID releaseId,String ownerId,String projectId,String kbRelease,KnowledgeIndexProfile indexProfile,
                       String embeddingProfile,String corpusChecksum,List<Prepared> documents,int chunks,long inputTokens) {}
    public record Result(UUID releaseId,int documents,int chunks,int reusedDocuments,long cacheHits,long cacheMisses,String status) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmbeddingClient embeddings;
    private final boolean testOnly;
    private long cacheHits,cacheMisses;
    private boolean stagedWrite;
    public KnowledgeCorpusService(JdbcTemplate jdbc,TransactionTemplate transactions,EmbeddingClient embeddings) {
        this(jdbc,transactions,embeddings,false);
    }
    /** Explicit opt-in for isolated contract tests; these releases can never become active. */
    public KnowledgeCorpusService(JdbcTemplate jdbc,TransactionTemplate transactions,EmbeddingClient embeddings,boolean testOnly) {
        this.jdbc=jdbc;this.transactions=transactions;this.embeddings=embeddings;this.testOnly=testOnly;
    }
    public static Plan prepare(String owner,String project,String kbRelease,KnowledgeIndexProfile profile,
                               String embeddingProfile,List<KnowledgeCorpus.Document> documents) {
        Checks.id(owner,"ownerId");Checks.id(project,"projectId");Checks.id(kbRelease,"kbRelease");
        if(documents.isEmpty() || documents.stream().map(KnowledgeCorpus.Document::knowledgeId).distinct().count()!=documents.size())
            throw KnowledgeCorpus.invalid("CORPUS_DOCUMENT_SET_INVALID");
        String scope=owner+"|"+project+"|"+kbRelease+"|"+profile.id()+"|"+embeddingProfile;
        UUID release=KnowledgeCorpus.stableId(scope);var prepared=new ArrayList<Prepared>();
        var chunker=new ContextualKnowledgeChunker();long tokens=0;int chunks=0;
        StringBuilder fingerprint=new StringBuilder();
        for(var d:documents.stream().sorted(java.util.Comparator.comparing(KnowledgeCorpus.Document::knowledgeId)).toList()) {
            var children=chunker.chunk(d,profile,scope);
            UUID id=KnowledgeCorpus.stableId(scope+"|document|"+d.knowledgeId()+"|"+d.originalChecksum());
            prepared.add(new Prepared(d,id,children));chunks+=children.size();tokens+=children.stream().mapToLong(ContextualKnowledgeChunker.Child::embeddingTokens).sum();
            fingerprint.append(d.originalChecksum()).append(KnowledgeCorpus.hash(Json.write(d.metadata(kbRelease)))).append(KnowledgeCorpus.hash(Json.write(d.sources())));
        }
        return new Plan(release,owner,project,kbRelease,profile,embeddingProfile,KnowledgeCorpus.hash(fingerprint.toString()),List.copyOf(prepared),chunks,tokens);
    }
    public Result importPlan(Plan plan) {
        return importPlan(plan, true);
    }
    /** Keep full imports BUILDING until the final acceptance transaction publishes READY. */
    public Result stagePlan(Plan plan) {
        return importPlan(plan, false);
    }
    private Result importPlan(Plan plan, boolean finishReady) {
        if(!plan.embeddingProfile().equals(embeddings.profile().profileVersion()))throw KnowledgeCorpus.invalid("IMPORT_EMBEDDING_PROFILE_MISMATCH");
        if(!testOnly && !(embeddings instanceof OpenAiCompatibleEmbeddingClient))throw KnowledgeCorpus.invalid("MOCK_EMBEDDING_NOT_ALLOWED");
        if(embeddings.profile().dimension()!=1024 || KnowledgeIndexSchema.vectorDimension(jdbc)!=1024)throw KnowledgeCorpus.invalid("EMBEDDING_DIMENSION_MISMATCH");
        cacheHits=0;cacheMisses=0;
        stagedWrite=!finishReady;
        // Hold a dedicated session lock while document/cache checkpoints use their own transactions.
        try(var connection=java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            long lock=plan.releaseId().getMostSignificantBits();
            try(var statement=connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1,lock);try(var rows=statement.executeQuery()) {rows.next();if(!rows.getBoolean(1))throw KnowledgeCorpus.invalid("RELEASE_IMPORT_BUSY");}
            }
            try{return importLocked(plan, finishReady);} finally {
                try(var statement=connection.prepareStatement("SELECT pg_advisory_unlock(?)")){statement.setLong(1,lock);statement.execute();}
            }
        } catch(KnowledgeException | EmbeddingException e){throw e;} catch(Exception e){throw new KnowledgeException("CORPUS_IMPORT_FAILED",500);}
    }
    private Result importLocked(Plan p, boolean finishReady) {
        Instant now=Instant.now();
        jdbc.update("""
            INSERT INTO dt_knowledge_release(id,owner_id,project_id,kb_release,index_profile,profile_configuration,
                embedding_profile,corpus_checksum,expected_documents,expected_chunks,status,is_test,created_at,updated_at)
            VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,'BUILDING',?,?,?) ON CONFLICT(id) DO NOTHING
            """,p.releaseId(),p.ownerId(),p.projectId(),p.kbRelease(),p.indexProfile().id(),Json.write(p.indexProfile()),p.embeddingProfile(),
                p.corpusChecksum(),p.documents().size(),p.chunks(),testOnly,Timestamp.from(now),Timestamp.from(now));
        var release=jdbc.queryForMap("SELECT corpus_checksum,is_test,status,active FROM dt_knowledge_release WHERE id=? AND owner_id=? AND project_id=?",p.releaseId(),p.ownerId(),p.projectId());
        if(!release.get("corpus_checksum").equals(p.corpusChecksum()) || !release.get("is_test").equals(testOnly))throw KnowledgeCorpus.invalid("RELEASE_CONTENT_CONFLICT");
        if("READY".equals(release.get("status"))) {
            verifyStored(p);
            return new Result(p.releaseId(),p.documents().size(),p.chunks(),p.documents().size(),0,0,Boolean.TRUE.equals(release.get("active"))?"READY_ACTIVE":"READY_INACTIVE");
        }
        int reused=0;
        jdbc.update("UPDATE dt_knowledge_release SET status='BUILDING',active=FALSE,last_error=NULL,updated_at=? WHERE id=?",Timestamp.from(now),p.releaseId());
        for(var prepared:p.documents()) {
            String kid=prepared.document().knowledgeId();
            jdbc.update("INSERT INTO dt_knowledge_import_item(release_id,knowledge_id,owner_id,project_id,status) VALUES (?,?,?,?,'PENDING') ON CONFLICT DO NOTHING",p.releaseId(),kid,p.ownerId(),p.projectId());
            var ready=jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE id=? AND release_id=? AND owner_id=? AND project_id=? AND ingest_status IN ('READY','STAGED')",Integer.class,prepared.documentId(),p.releaseId(),p.ownerId(),p.projectId());
            if(ready!=null && ready==1){reused++;continue;}
            try {
                var vectors=cachedVectors(p,prepared.children());
                transactions.executeWithoutResult(s->writeDocument(p,prepared,vectors));
            } catch(RuntimeException e) {
                String code=e instanceof EmbeddingException x?x.code():e instanceof KnowledgeException x?x.code():"IMPORT_STORAGE_FAILED";
                jdbc.update("UPDATE dt_knowledge_import_item SET status='FAILED',last_error=? WHERE release_id=? AND knowledge_id=?",code,p.releaseId(),kid);
                jdbc.update("UPDATE dt_knowledge_release SET status='FAILED',last_error=?,updated_at=? WHERE id=?",code,Timestamp.from(Instant.now()),p.releaseId());
                throw e;
            }
        }
        int count=jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE release_id=? AND ingest_status IN ('READY','STAGED')",Integer.class,p.releaseId());
        int chunks=jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_chunk WHERE release_id=?",Integer.class,p.releaseId());
        int parents=jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_parent WHERE release_id=?",Integer.class,p.releaseId());
        try {
            if(count!=p.documents().size() || chunks!=p.chunks() || parents!=count)throw new KnowledgeException("RELEASE_CONSISTENCY_FAILED",409);
            verifyStored(p);
        } catch(KnowledgeException e) {
            jdbc.update("UPDATE dt_knowledge_release SET status='FAILED',active=FALSE,last_error=?,updated_at=? WHERE id=?",e.code(),Timestamp.from(Instant.now()),p.releaseId());throw e;
        }
        if(finishReady) jdbc.update("UPDATE dt_knowledge_release SET status='READY',last_error=NULL,updated_at=? WHERE id=?",Timestamp.from(Instant.now()),p.releaseId());
        return new Result(p.releaseId(),count,chunks,reused,cacheHits,cacheMisses,finishReady?"READY_INACTIVE":"BUILDING_VALIDATED");
    }
    /** Call within a transaction that also runs final acceptance. This never activates a release. */
    public void markReady(Plan p) {
        transactions.executeWithoutResult(s -> {
            KnowledgeVisibilityGuard.lockScope(jdbc,p.ownerId(),p.projectId(),true);
            var release=jdbc.queryForMap("SELECT corpus_checksum FROM dt_knowledge_release WHERE id=? AND owner_id=? AND project_id=? FOR UPDATE",p.releaseId(),p.ownerId(),p.projectId());
            if(!p.corpusChecksum().equals(release.get("corpus_checksum")))throw new KnowledgeException("RELEASE_CONTENT_CONFLICT",409);
            int docs=jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE release_id=? AND ingest_status IN ('READY','STAGED')",Integer.class,p.releaseId());
            int chunks=jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_chunk WHERE release_id=?",Integer.class,p.releaseId());
            if(docs!=p.documents().size() || chunks!=p.chunks())throw new KnowledgeException("RELEASE_CONSISTENCY_FAILED",409);
            verifyStored(p);
            jdbc.update("UPDATE dt_knowledge_release SET status='READY',last_error=NULL,updated_at=? WHERE id=? AND owner_id=? AND project_id=?",Timestamp.from(Instant.now()),p.releaseId(),p.ownerId(),p.projectId());
        });
    }
    private void verifyStored(Plan p) {
        for(var item:p.documents()) {
            String original=jdbc.queryForObject("SELECT original_text FROM dt_knowledge_document WHERE id=? AND release_id=? AND owner_id=? AND project_id=?",String.class,item.documentId(),p.releaseId(),p.ownerId(),p.projectId());
            String parent=jdbc.queryForObject("SELECT content FROM dt_knowledge_parent WHERE document_id=? AND release_id=? AND owner_id=? AND project_id=?",String.class,item.documentId(),p.releaseId(),p.ownerId(),p.projectId());
            if(!KnowledgeCorpus.hash(original).equals(item.document().originalChecksum()) || !parent.equals(item.document().body()))throw new KnowledgeException("RELEASE_ORIGINAL_CONTENT_MISMATCH",409);
            try {
                String metadata=jdbc.queryForObject("SELECT normalized_metadata::text FROM dt_knowledge_document WHERE id=?",String.class,item.documentId());
                if(!Json.MAPPER.readTree(metadata).equals(Json.MAPPER.valueToTree(item.document().metadata(p.kbRelease()))))throw new KnowledgeException("RELEASE_METADATA_MISMATCH",409);
                var models=jdbc.queryForList("SELECT model_text FROM dt_knowledge_model WHERE document_id=? ORDER BY model_text",String.class,item.documentId());
                if(!models.equals(item.document().deviceModels().stream().sorted().toList()))throw new KnowledgeException("RELEASE_MODELS_MISMATCH",409);
                var links=jdbc.queryForList("SELECT ds.source_id,ds.source_locations::text AS locations,s.source_record::text AS record FROM dt_knowledge_document_source ds JOIN dt_knowledge_source s ON s.source_id=ds.source_id AND s.release_id=ds.release_id AND s.owner_id=ds.owner_id AND s.project_id=ds.project_id WHERE ds.document_id=?",item.documentId());
                for(var link:links) {
                    String sid=(String)link.get("source_id");
                    if(!Json.MAPPER.readTree((String)link.get("record")).equals(Json.MAPPER.valueToTree(item.document().sources().get(sid))) ||
                            !Json.MAPPER.readTree((String)link.get("locations")).equals(Json.MAPPER.valueToTree(item.document().sourceLocations().stream().filter(x->sid.equals(x.get("source_id"))).toList())))throw new KnowledgeException("RELEASE_SOURCE_METADATA_MISMATCH",409);
                }
            } catch(KnowledgeException e){throw e;} catch(Exception e){throw new KnowledgeException("RELEASE_METADATA_INVALID",409);}
            int sources=jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document_source WHERE document_id=?",Integer.class,item.documentId());
            if(sources!=item.document().sourceIds().size())throw new KnowledgeException("RELEASE_SOURCE_LINK_MISMATCH",409);
            for(var c:item.children()) {
                var rows=jdbc.queryForList("SELECT content,embedding_text,embedding_profile,parent_id,previous_chunk_id,next_chunk_id,body_start,body_end,content_checksum,embedding_text_checksum,chunking_profile,context_profile,source_locations::text AS locations,embedding::text AS vector FROM dt_knowledge_chunk WHERE id=? AND document_id=? AND release_id=? AND owner_id=? AND project_id=?",c.chunkId(),item.documentId(),p.releaseId(),p.ownerId(),p.projectId());
                if(rows.size()!=1)throw new KnowledgeException("RELEASE_CHILD_MISSING",409);
                var stored=rows.getFirst();
                if(!c.content().equals(stored.get("content")) || !c.embeddingText().equals(stored.get("embedding_text")) || !p.embeddingProfile().equals(stored.get("embedding_profile")) || !c.parentId().equals(stored.get("parent_id")) ||
                        !java.util.Objects.equals(c.previousChunkId(),stored.get("previous_chunk_id")) || !java.util.Objects.equals(c.nextChunkId(),stored.get("next_chunk_id")) || !Integer.valueOf(c.bodyStart()).equals(stored.get("body_start")) || !Integer.valueOf(c.bodyEnd()).equals(stored.get("body_end")))throw new KnowledgeException("RELEASE_CHILD_CONTENT_MISMATCH",409);
                if(!c.contentChecksum().equals(stored.get("content_checksum")) || !c.embeddingChecksum().equals(stored.get("embedding_text_checksum")) ||
                        !p.indexProfile().chunkingProfile().equals(stored.get("chunking_profile")) || !p.indexProfile().context().name().equals(stored.get("context_profile")))throw new KnowledgeException("RELEASE_CHILD_PROFILE_MISMATCH",409);
                try{
                    if(!Json.MAPPER.readTree((String)stored.get("locations")).equals(Json.MAPPER.valueToTree(item.document().sourceLocations())))throw new KnowledgeException("RELEASE_CHILD_SOURCES_MISMATCH",409);
                    EmbeddingVectors.validate(Json.MAPPER.readValue((String)stored.get("vector"),float[].class),1024);
                }
                catch(Exception e){throw new KnowledgeException("RELEASE_VECTOR_INVALID",409);}
            }
        }
    }
    /** Source provenance remains scoped to the existing server owner/project contract. */
    public Map<String,Object> provenance(UUID document,String owner,String project) {
        var rows=jdbc.queryForList("SELECT knowledge_id,release_id,normalized_metadata,source_nature,original_checksum FROM dt_knowledge_document WHERE id=? AND owner_id=? AND project_id=?",document,owner,project);
        if(rows.isEmpty())throw KnowledgeException.notFoundOrDenied();
        var result=new LinkedHashMap<String,Object>();result.putAll(rows.getFirst());
        result.put("sources",jdbc.queryForList("SELECT ds.source_id,ds.source_locations,s.source_record FROM dt_knowledge_document_source ds JOIN dt_knowledge_source s ON s.source_id=ds.source_id AND s.release_id=ds.release_id AND s.owner_id=ds.owner_id AND s.project_id=ds.project_id WHERE ds.document_id=? AND ds.owner_id=? AND ds.project_id=?",document,owner,project));
        // PGobject values are returned as parsed JSON, never opaque JDBC implementation objects.
        try {
            if(result.get("normalized_metadata")!=null)result.put("normalized_metadata",Json.MAPPER.readTree(result.get("normalized_metadata").toString()));
            for(var r:(List<Map<String,Object>>)result.get("sources")) {
                r.put("source_locations",Json.MAPPER.readTree(r.get("source_locations").toString()));r.put("source_record",Json.MAPPER.readTree(r.get("source_record").toString()));
            }
        } catch(Exception e){throw new KnowledgeException("PROVENANCE_DATA_INVALID",500);}
        return result;
    }
    private List<float[]> cachedVectors(Plan p,List<ContextualKnowledgeChunker.Child> children) {
        String config=KnowledgeCorpus.hash(embeddings.profile().profileVersion()+"|"+embeddings.profile().baseUrl()+"|"+embeddings.profile().sendDimensions()+"|test="+testOnly);
        var found=new LinkedHashMap<String,float[]>();var missing=new LinkedHashMap<String,String>();
        for(var child:children) {
            if(found.containsKey(child.embeddingChecksum()) || missing.containsKey(child.embeddingChecksum()))continue;
            var rows=jdbc.query("SELECT embedding::text FROM dt_knowledge_embedding_cache WHERE owner_id=? AND project_id=? AND config_checksum=? AND input_checksum=? AND embedding_profile=? AND is_test=?",
                    (r,n)->r.getString(1),p.ownerId(),p.projectId(),config,child.embeddingChecksum(),p.embeddingProfile(),testOnly);
            if(rows.isEmpty()){missing.put(child.embeddingChecksum(),child.embeddingText());cacheMisses++;}
            else {try{var v=Json.MAPPER.readValue(rows.getFirst(),float[].class);EmbeddingVectors.validate(v,1024);found.put(child.embeddingChecksum(),v);cacheHits++;}
                catch(Exception e){throw KnowledgeCorpus.invalid("EMBEDDING_CACHE_INVALID");}}
        }
        var pending=new ArrayList<>(missing.entrySet());
        for(int start=0;start<pending.size();start+=10) {
            var batch=pending.subList(start,Math.min(start+10,pending.size()));
            var vectors=embeddings.embedBatch(batch.stream().map(Map.Entry::getValue).toList());
            if(vectors.size()!=batch.size())throw new EmbeddingException("EMBEDDING_BATCH_RESPONSE_INVALID");
            for(int i=0;i<batch.size();i++)EmbeddingVectors.validate(vectors.get(i),1024);
            transactions.executeWithoutResult(status->{
                for(int i=0;i<batch.size();i++)jdbc.update("""
                    INSERT INTO dt_knowledge_embedding_cache(owner_id,project_id,config_checksum,input_checksum,embedding_profile,embedding,is_test,created_at)
                    VALUES (?,?,?,?,?,CAST(? AS vector),?,?) ON CONFLICT DO NOTHING
                    """,p.ownerId(),p.projectId(),config,batch.get(i).getKey(),p.embeddingProfile(),VectorLiteral.of(vectors.get(i)),testOnly,Timestamp.from(Instant.now()));
            });
            for(int i=0;i<batch.size();i++)found.put(batch.get(i).getKey(),vectors.get(i));
        }
        return children.stream().map(c->found.get(c.embeddingChecksum())).toList();
    }
    private void writeDocument(Plan p,Prepared item,List<float[]> vectors) {
        var d=item.document();var children=item.children();UUID doc=item.documentId(),file=KnowledgeCorpus.stableId(doc+"|file");
        String version="corpus-"+KnowledgeCorpus.hash(p.kbRelease()+"|"+p.indexProfile().id()+"|"+p.embeddingProfile()).substring(0,24);
        jdbc.update("""
            INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,parse_status,created_at)
            VALUES (?,?,?,?,?,'text/markdown',?,?,'PARSED',?)
            """,file,p.ownerId(),p.projectId(),"corpus-db/"+file,d.knowledgeId()+".md",d.originalChecksum(),d.originalText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,Timestamp.from(Instant.now()));
        jdbc.update("""
            INSERT INTO dt_knowledge_document(id,owner_id,project_id,title,domain,device_model,doc_version,source_file_id,checksum,ingest_status,
                embedding_profile,embedding_model,embedding_dimension,chunking_profile,chunk_count,data_nature,created_at,
                release_id,knowledge_id,original_text,original_checksum,normalized_metadata,source_nature)
            VALUES (?,?,?,?,?,?,?,?,?,?,?, ?,1024,?,?,'AUTHORIZED',?,?,?,?,?,CAST(? AS jsonb),?)
            """,doc,p.ownerId(),p.projectId(),d.title(),d.domain(),d.deviceModels().size()==1?KnowledgeCorpus.modelKey(d.deviceModels().getFirst()):null,
                version,file,d.originalChecksum(),stagedWrite?"STAGED":"READY",p.embeddingProfile(),embeddings.profile().model(),p.indexProfile().chunkingProfile(),children.size(),Timestamp.from(Instant.now()),
                p.releaseId(),d.knowledgeId(),d.originalText(),d.originalChecksum(),Json.write(d.metadata(p.kbRelease())),d.sourceNature());
        jdbc.update("INSERT INTO dt_knowledge_document_file(file_id,document_id,owner_id,project_id) VALUES (?,?,?,?)",file,doc,p.ownerId(),p.projectId());
        jdbc.update("INSERT INTO dt_knowledge_parent(id,document_id,release_id,owner_id,project_id,content,content_checksum) VALUES (?,?,?,?,?,?,?)",
                children.getFirst().parentId(),doc,p.releaseId(),p.ownerId(),p.projectId(),d.body(),KnowledgeCorpus.hash(d.body()));
        for(String model:d.deviceModels())jdbc.update("INSERT INTO dt_knowledge_model(document_id,owner_id,project_id,model_key,model_text) VALUES (?,?,?,?,?)",doc,p.ownerId(),p.projectId(),KnowledgeCorpus.modelKey(model),model);
        for(String sid:d.sourceIds()) {
            jdbc.update("INSERT INTO dt_knowledge_source(source_id,release_id,owner_id,project_id,source_record) VALUES (?,?,?,?,CAST(? AS jsonb)) ON CONFLICT DO NOTHING",sid,p.releaseId(),p.ownerId(),p.projectId(),Json.write(d.sources().get(sid)));
            jdbc.update("INSERT INTO dt_knowledge_document_source(document_id,source_id,release_id,owner_id,project_id,source_locations) VALUES (?,?,?,?,?,CAST(? AS jsonb))",doc,sid,p.releaseId(),p.ownerId(),p.projectId(),Json.write(d.sourceLocations().stream().filter(x->sid.equals(x.get("source_id"))).toList()));
        }
        for(int i=0;i<children.size();i++) {
            var c=children.get(i);
            jdbc.update("""
                INSERT INTO dt_knowledge_chunk(id,document_id,owner_id,project_id,chunk_no,content,page_no,section,embedding,embedding_profile,
                    release_id,parent_id,embedding_text,content_checksum,embedding_text_checksum,body_start,body_end,previous_chunk_id,next_chunk_id,
                    source_locations,chunking_profile,context_profile,content_tokens,embedding_tokens)
                VALUES (?,?,?,?,?,?,NULL,?,CAST(? AS vector),?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?)
                """,c.chunkId(),doc,p.ownerId(),p.projectId(),c.chunkNo(),c.content(),c.sectionPath(),VectorLiteral.of(vectors.get(i)),p.embeddingProfile(),p.releaseId(),c.parentId(),
                    c.embeddingText(),c.contentChecksum(),c.embeddingChecksum(),c.bodyStart(),c.bodyEnd(),c.previousChunkId(),c.nextChunkId(),Json.write(d.sourceLocations()),
                    p.indexProfile().chunkingProfile(),p.indexProfile().context().name(),c.contentTokens(),c.embeddingTokens());
        }
        jdbc.update("UPDATE dt_knowledge_import_item SET status='READY',last_error=NULL WHERE release_id=? AND knowledge_id=?",p.releaseId(),d.knowledgeId());
    }
    /** Never called by pilot. Whole READY release activation is an explicit operation. */
    public void activate(UUID release,String owner,String project) {
        transactions.executeWithoutResult(s->{
            KnowledgeVisibilityGuard.lockScope(jdbc,owner,project,true);
            var ready=jdbc.queryForList("SELECT id FROM dt_knowledge_release WHERE id=? AND owner_id=? AND project_id=? AND status='READY' AND NOT is_test FOR UPDATE",UUID.class,release,owner,project);
            if(ready.size()!=1)throw new KnowledgeException("RELEASE_NOT_ACTIVATABLE",409);
            jdbc.update("UPDATE dt_knowledge_document SET ingest_status='STAGED' WHERE owner_id=? AND project_id=? AND release_id IN (SELECT id FROM dt_knowledge_release WHERE owner_id=? AND project_id=? AND active) AND ingest_status='READY'",owner,project,owner,project);
            jdbc.update("UPDATE dt_knowledge_release SET active=FALSE WHERE owner_id=? AND project_id=? AND active",owner,project);
            jdbc.update("UPDATE dt_knowledge_document SET ingest_status='READY' WHERE release_id=? AND owner_id=? AND project_id=? AND ingest_status='STAGED'",release,owner,project);
            jdbc.update("UPDATE dt_knowledge_release SET active=TRUE WHERE id=? AND owner_id=? AND project_id=?",release,owner,project);
        });
    }
    /** Restore an inactive default scope without deleting any historical references. */
    public void deactivate(UUID release,String owner,String project) {
        transactions.executeWithoutResult(s->{
            KnowledgeVisibilityGuard.lockScope(jdbc,owner,project,true);
            if(jdbc.queryForList("SELECT id FROM dt_knowledge_release WHERE id=? AND owner_id=? AND project_id=? FOR UPDATE",release,owner,project).isEmpty())
                throw new KnowledgeException("NOT_FOUND_OR_DENIED",404);
            jdbc.update("UPDATE dt_knowledge_document SET ingest_status='STAGED' WHERE release_id=? AND owner_id=? AND project_id=? AND ingest_status='READY'",release,owner,project);
            jdbc.update("UPDATE dt_knowledge_release SET active=FALSE WHERE id=? AND owner_id=? AND project_id=?",release,owner,project);
        });
    }

}
