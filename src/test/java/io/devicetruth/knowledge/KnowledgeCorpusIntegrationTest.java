package io.devicetruth.knowledge;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real isolated PG migration/cache/checkpoint contracts; vectors explicitly CONTRACT_TEST. */
class KnowledgeCorpusIntegrationTest {
    static String base,user,password,database;
    static JdbcTemplate jdbc;static TransactionTemplate tx;static DriverManagerDataSource ds;
    static EmbeddingClient deterministic;static List<KnowledgeCorpus.Document> docs;static UUID legacy;
    static AtomicInteger scopes=new AtomicInteger();
    @BeforeAll static void start() throws Exception {
        base=System.getenv("DEVICETRUTH_TEST_PG_BASE_URL");user=System.getenv("DEVICETRUTH_TEST_PG_USERNAME");password=System.getenv("DEVICETRUTH_TEST_PG_PASSWORD");
        Assumptions.assumeTrue(base!=null && user!=null && password!=null,"isolated PostgreSQL configuration required");
        database="devicetruth_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=java.sql.DriverManager.getConnection(base+"postgres",user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+database);}
        ds=new DriverManagerDataSource(base+database,user,password);jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Flyway.configure().dataSource(ds).target("17").load().migrate();
        deterministic=new DeterministicEmbeddingClient(EmbeddingProfile.of("deterministic","","","deterministic-char2",1024,"1",1000,false));
        legacy=plantV17LegacyDocument();
        Flyway.configure().dataSource(ds).load().migrate();
        docs=KnowledgeCorpus.load(Path.of("datasets/knowledge")).subList(0,3);
    }
    /** V17 夹具只使用 V17 列；不能让当前导入器在未迁移数据库上忽略可用性约束。 */
    private static UUID plantV17LegacyDocument() {
        UUID file=UUID.randomUUID(),document=UUID.randomUUID();
        String content="旧证据必须保留。",checksum=KnowledgeCorpus.hash(content);
        jdbc.update("INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,parse_status,created_at) VALUES (?,'legacy-owner','legacy-project',?,'legacy.md','text/markdown',?,?,'PARSED',now())",file,"knowledge/"+file,checksum,content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        jdbc.update("INSERT INTO dt_knowledge_document(id,owner_id,project_id,title,domain,doc_version,source_file_id,checksum,ingest_status,embedding_profile,embedding_model,embedding_dimension,chunking_profile,chunk_count,data_nature,created_at) VALUES (?,'legacy-owner','legacy-project','旧手册','industrial','legacy-1',?,?,'READY',?,?,1024,?,1,'SIMULATED',now())",document,file,checksum,deterministic.profile().profileVersion(),deterministic.profile().model(),KnowledgeChunker.PROFILE);
        jdbc.update("INSERT INTO dt_knowledge_chunk(id,document_id,owner_id,project_id,chunk_no,content,section,embedding,embedding_profile) VALUES (?,?,'legacy-owner','legacy-project',1,?,'原文',?::vector,?)",UUID.randomUUID(),document,content,VectorLiteral.of(deterministic.embed(content)),deterministic.profile().profileVersion());
        jdbc.update("INSERT INTO dt_knowledge_document_file VALUES (?,?,'legacy-owner','legacy-project')",file,document);
        return document;
    }
    @AfterAll static void stop() throws Exception {
        if(database!=null)try(var c=java.sql.DriverManager.getConnection(base+"postgres",user,password);var s=c.createStatement()){s.execute("DROP DATABASE "+database+" WITH (FORCE)");}
    }
    static KnowledgeCorpusService.Plan plan(String owner,String project,String release,KnowledgeIndexProfile profile,List<KnowledgeCorpus.Document> subset){return KnowledgeCorpusService.prepare(owner,project,release,profile,deterministic.profile().profileVersion(),subset);}
    static KnowledgeIndexProfile e3(){return new KnowledgeIndexProfile("E3",512,KnowledgeIndexProfile.Context.METADATA);}
    static String owner(){return "test-owner-"+scopes.incrementAndGet();}
    @Test void stagedImportPublishesReadyOnlyWhenAcceptanceTransactionCommits() {
        var p=plan(owner(),"project","staged",e3(),docs);
        var service=new KnowledgeCorpusService(jdbc,tx,deterministic,true);
        assertThat(service.stagePlan(p).status()).isEqualTo("BUILDING_VALIDATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE release_id=? AND ingest_status='READY'",Integer.class,p.releaseId())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM dt_knowledge_release WHERE id=?",String.class,p.releaseId())).isEqualTo("BUILDING");
        assertThatThrownBy(()->tx.executeWithoutResult(s->{service.markReady(p);throw new IllegalStateException("acceptance failed");})).hasMessage("acceptance failed");
        assertThat(jdbc.queryForObject("SELECT status FROM dt_knowledge_release WHERE id=?",String.class,p.releaseId())).isEqualTo("BUILDING");
        service.markReady(p);
        assertThat(jdbc.queryForObject("SELECT status FROM dt_knowledge_release WHERE id=?",String.class,p.releaseId())).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT active FROM dt_knowledge_release WHERE id=?",Boolean.class,p.releaseId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE release_id=? AND ingest_status='STAGED'",Integer.class,p.releaseId())).isEqualTo(docs.size());
    }
    @Test void incrementalMigrationPreservesLegacyDocumentsAndVectors(){assertThat(jdbc.queryForObject("SELECT title FROM dt_knowledge_document WHERE id=?",String.class,legacy)).isEqualTo("旧手册");assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_chunk WHERE document_id=? AND vector_dims(embedding)=1024",Integer.class,legacy)).isEqualTo(1);}
    @Test void importsParentsSourcesAndOriginalEvidenceWithStableIdentity(){var p=plan(owner(),"project","kb-v1",e3(),docs);var service=new KnowledgeCorpusService(jdbc,tx,deterministic,true);var result=service.importPlan(p);assertThat(result.documents()).isEqualTo(3);assertThat(result.chunks()).isEqualTo(p.chunks());
        for(var item:p.documents()){assertThat(jdbc.queryForObject("SELECT original_text FROM dt_knowledge_document WHERE id=?",String.class,item.documentId())).isEqualTo(item.document().originalText());
            assertThat(jdbc.queryForObject("SELECT content FROM dt_knowledge_parent WHERE document_id=?",String.class,item.documentId())).isEqualTo(item.document().body());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document_source WHERE document_id=?",Integer.class,item.documentId())).isEqualTo(item.document().sourceIds().size());}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_chunk WHERE release_id=? AND content_checksum IS NOT NULL AND embedding_text_checksum IS NOT NULL AND vector_dims(embedding)=1024",Integer.class,p.releaseId())).isEqualTo(p.chunks());
        assertThatThrownBy(()->service.activate(p.releaseId(),p.ownerId(),p.projectId())).hasMessage("RELEASE_NOT_ACTIVATABLE");
    }
    @Test void repeatedImportReusesDocumentsAndCacheAcrossEquivalentInputs(){String owner=owner();var p=plan(owner,"project","release-a",e3(),docs);var service=new KnowledgeCorpusService(jdbc,tx,deterministic,true);service.importPlan(p);var second=service.importPlan(p);assertThat(second.reusedDocuments()).isEqualTo(3);assertThat(second.cacheMisses()).isZero();
        var other=plan(owner,"project","release-b",e3(),docs);var cached=service.importPlan(other);assertThat(cached.cacheMisses()).isZero();assertThat(cached.cacheHits()).isPositive();assertThat(other.documents().getFirst().children().getFirst().chunkId()).isNotEqualTo(p.documents().getFirst().children().getFirst().chunkId());}
    @Test void failureCheckpointsResumeWithoutPublishingPartialIndex(){var p=plan(owner(),"project","resume",new KnowledgeIndexProfile("E1",0,KnowledgeIndexProfile.Context.NONE),docs);var calls=new AtomicInteger();EmbeddingClient flaky=new EmbeddingClient(){public EmbeddingProfile profile(){return deterministic.profile();}public float[] embed(String t){if(calls.incrementAndGet()==2)throw new EmbeddingException("INJECTED_PROVIDER_FAILURE");return deterministic.embed(t);}};
        var service=new KnowledgeCorpusService(jdbc,tx,flaky,true);assertThatThrownBy(()->service.importPlan(p)).hasMessage("INJECTED_PROVIDER_FAILURE");
        assertThat(jdbc.queryForObject("SELECT status FROM dt_knowledge_release WHERE id=?",String.class,p.releaseId())).isEqualTo("FAILED");assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document WHERE release_id=?",Integer.class,p.releaseId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_document d JOIN dt_knowledge_release r ON r.id=d.release_id WHERE r.id=? AND r.status='READY'",Integer.class,p.releaseId())).isZero();
        var resumed=service.importPlan(p);assertThat(resumed.reusedDocuments()).isEqualTo(1);assertThat(resumed.documents()).isEqualTo(3);}
    @Test void distinctContextAndTokenProfilesCannotReuseIndexIdentity(){String owner=owner();var service=new KnowledgeCorpusService(jdbc,tx,deterministic,true);var p=plan(owner,"project","profile-test",e3(),docs);service.importPlan(p);var b=plan(owner,"project","profile-test",new KnowledgeIndexProfile("E2",256,KnowledgeIndexProfile.Context.NONE),docs);service.importPlan(b);assertThat(b.releaseId()).isNotEqualTo(p.releaseId());assertThat(jdbc.queryForObject("SELECT count(DISTINCT release_id) FROM dt_knowledge_document WHERE owner_id=?",Integer.class,owner)).isEqualTo(2);}
    @Test void changedReleaseMembershipIsRejected(){var p=plan(owner(),"project","frozen",e3(),docs);var service=new KnowledgeCorpusService(jdbc,tx,deterministic,true);service.importPlan(p);var changed=plan(p.ownerId(),p.projectId(),p.kbRelease(),e3(),docs.subList(0,2));assertThatThrownBy(()->service.importPlan(changed)).hasMessage("RELEASE_CONTENT_CONFLICT");}
    @Test void crossProjectCacheAndParentLinksAreIsolated(){String owner=owner();var service=new KnowledgeCorpusService(jdbc,tx,deterministic,true);var a=plan(owner,"project-a","v1",e3(),docs);var b=plan(owner,"project-b","v1",e3(),docs);service.importPlan(a);var imported=service.importPlan(b);assertThat(imported.cacheMisses()).isPositive();
        assertThatThrownBy(()->jdbc.update("UPDATE dt_knowledge_chunk SET parent_id=? WHERE id=?",a.documents().getFirst().children().getFirst().parentId(),b.documents().getFirst().children().getFirst().chunkId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
    @Test void productionImporterRejectsMockAndZeroVectorCannotReachStorage(){var p=plan(owner(),"project","fake",e3(),docs);assertThatThrownBy(()->new KnowledgeCorpusService(jdbc,tx,deterministic).importPlan(p)).hasMessage("MOCK_EMBEDDING_NOT_ALLOWED");
        EmbeddingClient zero=new EmbeddingClient(){public EmbeddingProfile profile(){return deterministic.profile();}public float[] embed(String t){return new float[1024];}};assertThatThrownBy(()->new KnowledgeCorpusService(jdbc,tx,zero,true).importPlan(p)).hasMessage("EMBEDDING_ZERO_VECTOR");assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_chunk WHERE release_id=?",Integer.class,p.releaseId())).isZero();}
    @Test void provenancePreservesOriginalNatureAndRejectsOtherProject(){var p=plan(owner(),"project","provenance",e3(),docs);var service=new KnowledgeCorpusService(jdbc,tx,deterministic,true);service.importPlan(p);var d=p.documents().getFirst();var provenance=service.provenance(d.documentId(),p.ownerId(),p.projectId());assertThat(provenance.get("source_nature")).isEqualTo(d.document().sourceNature());assertThat(provenance.get("original_checksum")).isEqualTo(d.document().originalChecksum());assertThat((List<?>)provenance.get("sources")).hasSize(d.document().sourceIds().size());assertThatThrownBy(()->service.provenance(d.documentId(),p.ownerId(),"other-project")).hasMessage("NOT_FOUND_OR_DENIED");}
    @Test void embeddingRevisionNeverReusesDifferentModelConfigurationCache(){String owner=owner();var a=plan(owner,"project","cache-profile",e3(),docs);new KnowledgeCorpusService(jdbc,tx,deterministic,true).importPlan(a);var other=new DeterministicEmbeddingClient(EmbeddingProfile.of("deterministic","","","deterministic-char2",1024,"2",1000,false));var b=KnowledgeCorpusService.prepare(owner,"project","cache-profile",e3(),other.profile().profileVersion(),docs);assertThat(new KnowledgeCorpusService(jdbc,tx,other,true).importPlan(b).cacheMisses()).isPositive();}
    @Test void inactiveReleaseRequiresExplicitReadySelectionAndRejectsWrongIndexAndProject(){var p=plan(owner(),"project","search-scope",e3(),docs);new KnowledgeCorpusService(jdbc,tx,deterministic,true).importPlan(p);UUID conversation=UUID.randomUUID(),message=UUID.randomUUID(),run=UUID.randomUUID();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,created_at,updated_at) VALUES (?,?,?,now(),now())",conversation,p.ownerId(),p.projectId());
        jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) VALUES (?,?,?,?,1,'USER','contract retrieval',now(),now())",message,conversation,p.ownerId(),p.projectId());
        jdbc.update("INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,budget,created_at,updated_at,round_number) VALUES (?,?,?,?,?,'COMPLETED',now(),now(),'{}',now(),now(),1)",run,conversation,p.ownerId(),p.projectId(),message);
        var ctx=new io.devicetruth.core.capability.ToolContext(p.ownerId(),p.projectId(),conversation,run,null,java.time.Instant.now(),null,java.util.Set.of("search_knowledge"));var search=new KnowledgeSearchService(jdbc,tx,deterministic,5,0);
        assertThat(search.searchChannel(ctx,"知识",KnowledgeSearchService.KnowledgeFilters.NONE,3,KnowledgeSearchService.SearchChannel.VECTOR).empty()).isTrue();
        var selected=new KnowledgeSearchService.KnowledgeFilters(null,null,null,p.releaseId(),p.indexProfile().id());assertThat(search.searchChannel(ctx,"知识",selected,3,KnowledgeSearchService.SearchChannel.VECTOR).empty()).isFalse();
        assertThat(search.searchChannel(ctx,"知识",new KnowledgeSearchService.KnowledgeFilters(null,null,null,p.releaseId(),"wrong-profile"),3,KnowledgeSearchService.SearchChannel.VECTOR).empty()).isTrue();
        jdbc.update("UPDATE dt_knowledge_release SET status='BUILDING' WHERE id=?",p.releaseId());assertThat(search.searchChannel(ctx,"知识",selected,3,KnowledgeSearchService.SearchChannel.VECTOR).empty()).isTrue();
        var other=new io.devicetruth.core.capability.ToolContext(p.ownerId(),"other-project",conversation,run,null,java.time.Instant.now(),null,java.util.Set.of("search_knowledge"));assertThatThrownBy(()->search.searchChannel(other,"知识",selected,3,KnowledgeSearchService.SearchChannel.VECTOR)).hasMessage("NOT_FOUND_OR_DENIED");
    }
}
