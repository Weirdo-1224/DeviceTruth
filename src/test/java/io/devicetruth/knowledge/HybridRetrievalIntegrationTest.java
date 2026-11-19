package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.capability.ToolContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real PG/pgvector + real Lucene; synthetic vectors prove contracts only. */
class HybridRetrievalIntegrationTest {
    static String base,user,password,database;static JdbcTemplate jdbc;static TransactionTemplate tx;
    static EmbeddingClient vectors;static KnowledgeCorpusService.Plan p,otherRelease,otherProject;
    static final AtomicInteger calls=new AtomicInteger();
    @TempDir Path temp;
    @BeforeAll static void start() throws Exception {
        base=System.getenv("DEVICETRUTH_TEST_PG_BASE_URL");user=System.getenv("DEVICETRUTH_TEST_PG_USERNAME");password=System.getenv("DEVICETRUTH_TEST_PG_PASSWORD");
        Assumptions.assumeTrue(base!=null&&user!=null&&password!=null,"real isolated PG required");
        database="devicetruth_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=java.sql.DriverManager.getConnection(base+"postgres",user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+database);}
        var ds=new DriverManagerDataSource(base+database,user,password);jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Flyway.configure().dataSource(ds).load().migrate();
        var deterministic=new DeterministicEmbeddingClient(EmbeddingProfile.of("deterministic","","","deterministic-char2",1024,"1",1000,false));
        vectors=new EmbeddingClient(){public EmbeddingProfile profile(){return deterministic.profile();}public float[] embed(String text){calls.incrementAndGet();return deterministic.embed(text);}};
        List<KnowledgeCorpus.Document> docs=List.of(doc("TEST-A","CPU 1214C","PROFINET P0A94 过程数据有效性诊断"),
                doc("TEST-B","CPU 1215C","PROFINET P0A95 固件配置"),doc("TEST-G","GENERAL","通用文档解释 PROFINET 数据记录"));
        p=plan("project","v1",docs);otherRelease=plan("project","v2",docs);otherProject=plan("other-project","v1",docs);
        var service=new KnowledgeCorpusService(jdbc,tx,vectors,true);service.importPlan(p);service.importPlan(otherRelease);service.importPlan(otherProject);
        // Typed verified conditions are contract fixtures, never claims about the frozen corpus.
        jdbc.update("UPDATE dt_knowledge_document SET normalized_metadata=normalized_metadata || '{\"verified_applicability\":{\"firmware_versions\":[\"4.6\"]}}'::jsonb WHERE knowledge_id='TEST-A'");
    }
    static KnowledgeCorpusService.Plan plan(String project,String release,List<KnowledgeCorpus.Document> docs){return KnowledgeCorpusService.prepare("owner",project,release,new KnowledgeIndexProfile("E3",512,KnowledgeIndexProfile.Context.METADATA),vectors.profile().profileVersion(),docs);}
    static KnowledgeCorpus.Document doc(String id,String model,String body) {
        String text="---\nknowledge_id: "+id+"\ntitle: 合同测试手册\ndomain: industrial\ncategory: fault_diagnosis\nmanufacturer: Siemens\ndevice_model: [\""+model+"\"]\ncomponent: [PLC]\napplicable_versions: [unknown]\nsource_ids: [CONTRACT]\nsource_locations:\n  - source_id: CONTRACT\n    section: contract\ntrust_level: A\nverification_status: VERIFIED\ndata_nature: REAL\nrelated_skills: [factory-equipment]\nkeywords: [PROFINET]\nsource_type: MANUAL\nscope_level: "+(model.equals("GENERAL")?"GENERAL":"MODEL")+"\n---\n# 合同测试\n\n"+body;
        return KnowledgeCorpus.parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),KnowledgeCorpus.hash(text),Map.of("CONTRACT",Map.of("document_version","unknown")));
    }
    @AfterAll static void stop() throws Exception {if(database!=null)try(var c=java.sql.DriverManager.getConnection(base+"postgres",user,password);var s=c.createStatement()){s.execute("DROP DATABASE "+database+" WITH (FORCE)");}}
    ToolContext context(String owner,String project) {
        UUID conversation=UUID.randomUUID(),message=UUID.randomUUID(),run=UUID.randomUUID();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,created_at,updated_at) VALUES (?,?,?,now(),now())",conversation,owner,project);
        jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) VALUES (?,?,?,?,1,'USER','CONTRACT_TEST',now(),now())",message,conversation,owner,project);
        jdbc.update("INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,budget,created_at,updated_at) VALUES (?,?,?,?,?,'COMPLETED',now(),now(),'{}',now(),now())",run,conversation,owner,project,message);
        return new ToolContext(owner,project,conversation,run,null,Instant.now(),null,Set.of(KnowledgeTools.SEARCH,KnowledgeTools.EXCERPT));
    }
    KnowledgeSearchService.KnowledgeFilters filters(KnowledgeCorpusService.Plan p){return new KnowledgeSearchService.KnowledgeFilters(null,null,null,p.releaseId(),p.indexProfile().id());}
    HybridRetrievalService hybrid(EmbeddingClient client,LuceneBm25Index index){return new HybridRetrievalService(jdbc,tx,client,index,HybridRetrievalService.Configuration.DEFAULT);}
    KnowledgeSearchService search(EmbeddingClient client,HybridRetrievalService h){return new KnowledgeSearchService(jdbc,tx,client,10,0.45).withHybrid(h,HybridRetrievalService.Mode.LEGACY);}
    EmbeddingClient unavailable(){return new EmbeddingClient(){public EmbeddingProfile profile(){return vectors.profile();}public float[] embed(String t){throw new EmbeddingException("EMBEDDING_UNAVAILABLE");}};}
    @Test void lexicalAndIdentifierDoNotCallEmbeddingsDuringOutage() {
        var ctx=context("owner","project");var h=hybrid(unavailable(),new LuceneBm25Index(temp));h.build(ctx,filters(p));var service=search(unavailable(),h);int before=calls.get();
        for(var mode:List.of(HybridRetrievalService.Mode.BM25,HybridRetrievalService.Mode.IDENTIFIER)) {
            var result=service.searchMode(ctx,"P0A94 PROFINET",filters(p),3,mode);
            assertThat(result.empty()).isFalse();assertThat(result.trace().failures()).isEmpty();
            assertThat(result.hits()).allMatch(x->x.denseScore()==null && x.denseRank()==null);
        }
        assertThat(calls.get()).isEqualTo(before);
    }
    @Test void tripleDegradesExplicitlyWithoutRegisteringIntermediateCandidates() {
        var ctx=context("owner","project");var h=hybrid(unavailable(),new LuceneBm25Index(temp));h.build(ctx,filters(p));
        var result=search(unavailable(),h).searchMode(ctx,"P0A94 PROFINET",filters(p),1,HybridRetrievalService.Mode.HYBRID_TRIPLE);
        assertThat(result.hits()).hasSize(1);assertThat(result.trace().failures()).containsEntry("DENSE","EMBEDDING_UNAVAILABLE");
        assertThat(result.trace().actualChannels()).containsExactly("BM25","IDENTIFIER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_evidence_reference WHERE run_id=?",Integer.class,ctx.runId())).isEqualTo(1);
    }
    @Test void finalCitationRetainsExactOriginalContentAndSourceLocations() {
        var ctx=context("owner","project");var h=hybrid(vectors,new LuceneBm25Index(temp));h.build(ctx,filters(p));var service=search(vectors,h);
        var result=service.searchMode(ctx,"P0A94",filters(p),1,HybridRetrievalService.Mode.IDENTIFIER);var hit=result.hits().getFirst();
        assertThat(hit.content()).isEqualTo(jdbc.queryForObject("SELECT content FROM dt_knowledge_chunk WHERE id=?",String.class,hit.chunkId()));
        assertThat(hit.sourceIds()).containsExactly("CONTRACT");assertThat(hit.sourceLocations().get(0).path("section").asText()).isEqualTo("contract");
        assertThat(service.excerptByReference(ctx,hit.referenceId()).content()).isEqualTo(hit.content());
        assertThat(hit.matchedIdentifiers()).extracting(IdentifierExtractor.Identifier::value).contains("P0A94");
        assertThat(hit.denseScore()).isNull();assertThat(hit.rrfScore()).isCloseTo(1.0/61,within(1e-10));
    }
    @Test void releaseAndProjectIsolationAndInactiveDefault() {
        var ctx=context("owner","project");var h=hybrid(unavailable(),new LuceneBm25Index(temp));h.build(ctx,filters(p));
        assertThat(h.retrieve(ctx,"PROFINET",filters(p),10,HybridRetrievalService.Mode.BM25).candidates()).allMatch(c->c.child().releaseId().equals(p.releaseId()));
        assertThat(h.retrieve(ctx,"PROFINET",KnowledgeSearchService.KnowledgeFilters.NONE,10,HybridRetrievalService.Mode.BM25).candidates()).isEmpty();
        var other=context("owner","other-project");assertThat(h.retrieve(other,"PROFINET",filters(p),10,HybridRetrievalService.Mode.BM25).candidates()).isEmpty();
        var bad=new ToolContext("stranger","project",ctx.conversationId(),ctx.runId(),null,Instant.now(),null,Set.of(KnowledgeTools.SEARCH));
        assertThatThrownBy(()->search(unavailable(),h).searchMode(bad,"PROFINET",filters(p),5,HybridRetrievalService.Mode.BM25)).hasMessage("NOT_FOUND_OR_DENIED");
    }
    @Test void modelVersionExclusionHappensBeforeCandidateTruncation() {
        var ctx=context("owner","project");var h=hybrid(vectors,new LuceneBm25Index(temp));h.build(ctx,filters(p));
        var f=new KnowledgeSearchService.KnowledgeFilters(null,null,null,p.releaseId(),p.indexProfile().id(),new ApplicabilityPolicy.Target("CPU 1214C",null,"Siemens","4.7",null,null,null));
        for(var mode:List.of(HybridRetrievalService.Mode.BM25,HybridRetrievalService.Mode.IDENTIFIER,HybridRetrievalService.Mode.HYBRID_TRIPLE))
            assertThat(h.retrieve(ctx,"PROFINET P0A94",f,10,mode).candidates()).allMatch(c->c.child().knowledgeId().equals("TEST-G"));
    }
    @Test void unknownModelAndMissingFirmwareNeverBecomeVerifiedApplicable() {
        var ctx=context("owner","project");var h=hybrid(vectors,new LuceneBm25Index(temp));h.build(ctx,filters(p));
        var unknown=h.retrieve(ctx,"P0A94",filters(p),10,HybridRetrievalService.Mode.IDENTIFIER);
        assertThat(unknown.candidates()).isNotEmpty().allMatch(c->!c.applicability().verifiedApplicable());
    }
    @Test void queryCacheReusesVectorsAndSeparatesOwnerProjectAndProfile() {
        var ctx=context("cache-owner","cache-project");var cache=new QueryVectorCache(jdbc,vectors);int before=calls.get();
        String query="QUERY_CACHE_CONTRACT_"+UUID.randomUUID();var a=cache.get(ctx,query);a[0]=999;
        assertThat(cache.get(ctx,query)[0]).isNotEqualTo(999);assertThat(calls.get()).isEqualTo(before+1);
        assertThat(new QueryVectorCache(jdbc,vectors).get(ctx,query)[0]).isNotEqualTo(999);assertThat(calls.get()).isEqualTo(before+1);
        cache.get(context("cache-owner","different-project"),query);assertThat(calls.get()).isEqualTo(before+2);
        var different=new DeterministicEmbeddingClient(EmbeddingProfile.of("deterministic","","","deterministic-char2",1024,"2",1000,false));
        new QueryVectorCache(jdbc,different).get(ctx,query);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT embedding_profile) FROM dt_knowledge_embedding_cache WHERE owner_id='cache-owner'",Integer.class)).isEqualTo(2);
    }
    @Test void exactAndFilteredHnswAgreeForContractCorpus() {
        var ctx=context("owner","project");var index=new LuceneBm25Index(temp);var exact=hybrid(vectors,index);exact.build(ctx,filters(p));
        var cfg=HybridRetrievalService.Configuration.DEFAULT;
        var hnsw=new HybridRetrievalService(jdbc,tx,vectors,index,new HybridRetrievalService.Configuration(20,60,1,1,1,0.45,HybridRetrievalService.DenseSearch.HNSW,false));
        var a=exact.retrieve(ctx,"PROFINET",filters(p),10,HybridRetrievalService.Mode.DENSE);
        var b=hnsw.retrieve(ctx,"PROFINET",filters(p),10,HybridRetrievalService.Mode.DENSE);
        assertThat(b.candidates()).extracting(c->c.child().id()).containsExactlyInAnyOrderElementsOf(a.candidates().stream().map(c->c.child().id()).toList());
    }
    @Test void staleIndexCannotServeAfterPostgresChangesAndRebuildRecovers() {
        var ctx=context("owner","project");var h=hybrid(vectors,new LuceneBm25Index(temp));h.build(ctx,filters(p));
        tx.executeWithoutResult(status->{
            jdbc.update("UPDATE dt_knowledge_document SET title='changed contract title' WHERE id=?",p.documents().getFirst().documentId());
            var missing=h.retrieve(ctx,"PROFINET",filters(p),5,HybridRetrievalService.Mode.BM25);assertThat(missing.candidates()).isEmpty();
            assertThat(missing.trace().failures()).containsEntry("BM25","LUCENE_INDEX_NOT_READY");
            assertThat(h.build(ctx,filters(p)).integrity()).isTrue();assertThat(h.retrieve(ctx,"PROFINET",filters(p),5,HybridRetrievalService.Mode.BM25).candidates()).isNotEmpty();status.setRollbackOnly();
        });
    }
    @Test void explicitQueryModelAndFirmwareDriveMandatoryApplicabilityBeforeFusion() {
        var ctx=context("owner","project");var h=hybrid(unavailable(),new LuceneBm25Index(temp));h.build(ctx,filters(p));
        var conflict=h.retrieve(ctx,"CPU 1214C FW4.7 PROFINET",filters(p),10,HybridRetrievalService.Mode.BM25);
        assertThat(conflict.candidates()).allMatch(c->!c.child().knowledgeId().equals("TEST-A") && !c.child().knowledgeId().equals("TEST-B"));
        var matching=h.retrieve(ctx,"CPU 1214C FW4.6 PROFINET",filters(p),10,HybridRetrievalService.Mode.BM25);
        assertThat(matching.candidates()).anyMatch(c->c.child().knowledgeId().equals("TEST-A"));
        assertThat(matching.candidates()).noneMatch(c->c.child().knowledgeId().equals("TEST-B"));
    }
    @Test void toolsParseModesAndRejectUnknownApplicabilityFields() {
        var ctx=context("owner","project");var h=hybrid(unavailable(),new LuceneBm25Index(temp));h.build(ctx,filters(p));var tools=new KnowledgeTools(search(unavailable(),h));
        var result=tools.search(Map.of("query","P0A94","mode","IDENTIFIER","filters",Map.of("releaseId",p.releaseId().toString())),ctx);
        assertThat(result.success()).isTrue();assertThat(result.hits()).isNotEmpty();
        assertThat(tools.search(Map.of("query","P0A94","filters",Map.of("applicability",Map.of("assumeAllVersions","true"))),ctx).success()).isFalse();
    }
}
