package io.devicetruth.knowledge;

import io.devicetruth.core.capability.ToolContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;

/** Real PG locks; latches select the interleaving, pg_locks proves writer waiting. No timed sleeps. */
class ReleaseConcurrencyTest {
    static final String OWNER="concurrency-owner",PROJECT="concurrency-project";
    static String base,user,password,database;static DriverManagerDataSource ds;static JdbcTemplate jdbc;
    static TransactionTemplate tx;static EmbeddingClient vectors;static KnowledgeCorpusService.Plan p,q;
    @TempDir Path temp;
    static class HookJdbc extends JdbcTemplate {
        Consumer<String> before=s->{};Consumer<String> after=s->{};Runnable beforeExclusive=()->{};
        HookJdbc(){super(ds);}
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args) {
            before.accept(sql);var rows=super.query(sql,mapper,args);after.accept(sql);return rows;
        }
        @Override public <T> T queryForObject(String sql,Class<T> type,Object... args) {
            if(sql.equals("SELECT pg_advisory_xact_lock(?)"))beforeExclusive.run();
            return super.queryForObject(sql,type,args);
        }
    }
    @BeforeAll static void start()throws Exception {
        base=System.getenv("DEVICETRUTH_TEST_PG_BASE_URL");user=System.getenv("DEVICETRUTH_TEST_PG_USERNAME");password=System.getenv("DEVICETRUTH_TEST_PG_PASSWORD");
        Assumptions.assumeTrue(base!=null&&user!=null&&password!=null,"isolated real PG required");
        database="devicetruth_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=java.sql.DriverManager.getConnection(base+"postgres",user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+database);}
        ds=new DriverManagerDataSource(base+database,user,password);jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Flyway.configure().dataSource(ds).load().migrate();
        vectors=new EmbeddingClient(){public EmbeddingProfile profile(){return EmbeddingProfile.of("openai","http://127.0.0.1","","contract-offline-vector",1024,"1",1000,false);}public float[] embed(String text){
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).as("provider work precedes final locks").isFalse();
            float[] v=new float[1024];v[0]=1;return v;
        }};
        var docs=List.of(HybridRetrievalIntegrationTest.doc("CONC-A","GENERAL","PROFINET diagnostic P0A94 original evidence"));
        var profile=new KnowledgeIndexProfile("E3",512,KnowledgeIndexProfile.Context.METADATA);
        p=KnowledgeCorpusService.prepare(OWNER,PROJECT,"concurrency-v1",profile,vectors.profile().profileVersion(),docs);
        q=KnowledgeCorpusService.prepare(OWNER,PROJECT,"concurrency-v2",profile,vectors.profile().profileVersion(),docs);
        // Production importer exercised against a loopback protocol stub; no test-release flag bypass.
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/embeddings",exchange->{
            var input=io.devicetruth.adapter.Json.MAPPER.readTree(exchange.getRequestBody()).path("input");
            var response=io.devicetruth.adapter.Json.MAPPER.createObjectNode();var data=response.putArray("data");
            for(int i=0;i<input.size();i++){var item=data.addObject();item.put("index",i);var v=item.putArray("embedding");for(int j=0;j<1024;j++)v.add(j==0?1.0:0.0);}
            response.putObject("usage").put("total_tokens",10);byte[] bytes=io.devicetruth.adapter.Json.write(response).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);try(var body=exchange.getResponseBody()){body.write(bytes);}
        });server.start();
        try {
            var ep=EmbeddingProfile.of("openai","http://127.0.0.1:"+server.getAddress().getPort(),"local-contract-key","contract-offline-vector",1024,"1",1000,false);
            var service=new KnowledgeCorpusService(jdbc,tx,new OpenAiCompatibleEmbeddingClient(ep));service.importPlan(p);service.importPlan(q);
        }finally{server.stop(0);}
    }
    @AfterAll static void stop()throws Exception {if(database!=null)try(var c=java.sql.DriverManager.getConnection(base+"postgres",user,password);var s=c.createStatement()){s.execute("DROP DATABASE "+database+" WITH (FORCE)");}}
    @BeforeEach void activate(){new KnowledgeCorpusService(jdbc,tx,vectors).activate(p.releaseId(),OWNER,PROJECT);}
    ToolContext ctx() {
        UUID c=UUID.randomUUID(),m=UUID.randomUUID(),r=UUID.randomUUID();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,created_at,updated_at) VALUES (?,?,?,now(),now())",c,OWNER,PROJECT);
        jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) VALUES (?,?,?,?,1,'USER','CONCURRENCY_TEST',now(),now())",m,c,OWNER,PROJECT);
        jdbc.update("INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,budget,created_at,updated_at,round_number) VALUES (?,?,?,?,?,'COMPLETED',now(),now(),'{}',now(),now(),1)",r,c,OWNER,PROJECT,m);
        return new ToolContext(OWNER,PROJECT,c,r,null,Instant.now(),null,Set.of(KnowledgeTools.SEARCH,KnowledgeTools.EXCERPT));
    }
    int refs(ToolContext c){return jdbc.queryForObject("SELECT count(*) FROM dt_evidence_reference WHERE run_id=?",Integer.class,c.runId());}
    static void await(CountDownLatch latch){try{if(!latch.await(15,TimeUnit.SECONDS))throw new AssertionError("latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    static boolean snapshotSql(String sql){return sql.contains("AS metadata")&&sql.contains("ORDER BY c.id");}
    KnowledgeSearchService.SearchOutcome search(String mode,HookJdbc hooked,ToolContext ctx) {
        var index=new LuceneBm25Index(temp);
        // Build under the currently authorized snapshot before selecting the interleaving.
        // Tests call buildIndex separately so this phase never rebuilds after a switch.
        var h=new HybridRetrievalService(hooked,tx,vectors,index,HybridRetrievalService.Configuration.DEFAULT);
        var service=new KnowledgeSearchService(hooked,tx,vectors,5,.45).withHybrid(h,HybridRetrievalService.Mode.LEGACY);
        if(!mode.equals("RERANK"))return service.searchMode(ctx,"PROFINET",KnowledgeSearchService.KnowledgeFilters.NONE,5,HybridRetrievalService.Mode.valueOf(mode));
        RerankerClient scorer=new RerankerClient(){public String configurationKey(){return "offline-contract";}public Response score(Request request){
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var scores=new ArrayList<Score>();for(int i=0;i<request.documents().size();i++)scores.add(new Score(i,request.documents().get(i).chunkId(),.8));
            return new Response(scores,"offline",0L,0,0,false);
        }};
        var enhanced=new EnhancedRetrievalService(hooked,h,new RerankingService(scorer),new ContextRecoveryService());
        return service.searchEnhanced(ctx,"PROFINET",KnowledgeSearchService.KnowledgeFilters.NONE,enhanced,
                new EnhancedRetrievalService.Options(true,"",EnhancedRetrievalService.Range.FULL_POOL,
                        new ContextRecoveryService.Options(ContextRecoveryService.Strategy.NONE,5,20000,20000))).evidence();
    }
    void buildIndex(ToolContext c){new HybridRetrievalService(jdbc,tx,vectors,new LuceneBm25Index(temp),HybridRetrievalService.Configuration.DEFAULT).build(c,KnowledgeSearchService.KnowledgeFilters.NONE);}
    @ParameterizedTest @ValueSource(strings={"LEGACY","DENSE","BM25","HYBRID_DUAL","HYBRID_TRIPLE","RERANK"})
    void rollbackDuringRetrievalRejectsAllPathsAndRegistersNothing(String mode)throws Exception {
        var c=ctx();buildIndex(c);var loaded=new CountDownLatch(1);var resume=new CountDownLatch(1);var hooked=new HookJdbc();var once=new AtomicBoolean();
        hooked.after=sql->{if((mode.equals("LEGACY")?sql.contains("AS similarity")&&sql.contains("ORDER BY c.embedding"):snapshotSql(sql))&&once.compareAndSet(false,true)){loaded.countDown();await(resume);}};
        var executor=Executors.newSingleThreadExecutor();
        try {
            var result=executor.submit(()->search(mode,hooked,c));await(loaded);
            new KnowledgeCorpusService(jdbc,tx,vectors).deactivate(p.releaseId(),OWNER,PROJECT);resume.countDown();
            assertThatThrownBy(()->result.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasRootCauseMessage("KNOWLEDGE_SNAPSHOT_CHANGED");
            assertThat(refs(c)).isZero();assertThat(new ScopedKnowledgeStore(jdbc,vectors.profile()).load(c,KnowledgeSearchService.KnowledgeFilters.NONE).children()).isEmpty();
        }finally{resume.countDown();executor.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"LEGACY","DENSE","BM25","HYBRID_DUAL","RERANK"})
    void rollbackBetweenValidationAndRegistrationWaitsThenPreservesHistory(String mode)throws Exception {
        var c=ctx();buildIndex(c);var validated=new CountDownLatch(1);var resume=new CountDownLatch(1);var hook=new HookJdbc();var loads=new AtomicInteger();
        hook.after=sql->{if(snapshotSql(sql)&&loads.incrementAndGet()==2){validated.countDown();await(resume);}};
        var writer=new HookJdbc();var writerAttempt=new CountDownLatch(1);var writerPid=new AtomicInteger();
        writer.beforeExclusive=()->{writerPid.set(writer.queryForObject("SELECT pg_backend_pid()",Integer.class));writerAttempt.countDown();};
        var executor=Executors.newFixedThreadPool(2);
        try {
            var read=executor.submit(()->search(mode,hook,c));await(validated);
            var rollback=executor.submit(()->new KnowledgeCorpusService(writer,tx,vectors).deactivate(p.releaseId(),OWNER,PROJECT));await(writerAttempt);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
            while(System.nanoTime()<deadline&&!waiting)waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=? AND locktype='advisory' AND NOT granted)",Boolean.class,writerPid.get());
            assertThat(waiting).as("PostgreSQL confirms exclusive writer queued behind final shared lock").isTrue();
            assertThat(refs(c)).isZero();resume.countDown();
            var result=read.get(15,TimeUnit.SECONDS);rollback.get(15,TimeUnit.SECONDS);
            assertThat(result.hits()).hasSize(1);assertThat(refs(c)).isEqualTo(1);
            assertThat(new ScopedKnowledgeStore(jdbc,vectors.profile()).load(c,KnowledgeSearchService.KnowledgeFilters.NONE).children()).isEmpty();
            var service=new KnowledgeSearchService(jdbc,tx,vectors,5,.45);
            assertThat(service.excerptByReference(c,result.hits().getFirst().referenceId()).content()).contains("original evidence");
        }finally{resume.countDown();executor.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"LEGACY","DENSE","BM25","HYBRID_DUAL","RERANK"})
    void releaseSwitchDuringRetrievalRejectsStaleCandidates(String mode)throws Exception {
        var c=ctx();buildIndex(c);var loaded=new CountDownLatch(1);var resume=new CountDownLatch(1);var hook=new HookJdbc();var once=new AtomicBoolean();
        hook.after=sql->{if((mode.equals("LEGACY")?sql.contains("AS similarity")&&sql.contains("ORDER BY c.embedding"):snapshotSql(sql))&&once.compareAndSet(false,true)){loaded.countDown();await(resume);}};
        var executor=Executors.newSingleThreadExecutor();
        try {
            var read=executor.submit(()->search(mode,hook,c));await(loaded);new KnowledgeCorpusService(jdbc,tx,vectors).activate(q.releaseId(),OWNER,PROJECT);resume.countDown();
            assertThatThrownBy(()->read.get(15,TimeUnit.SECONDS)).hasRootCauseMessage("KNOWLEDGE_SNAPSHOT_CHANGED");assertThat(refs(c)).isZero();
            assertThat(new ScopedKnowledgeStore(jdbc,vectors.profile()).load(c,KnowledgeSearchService.KnowledgeFilters.NONE).children()).allMatch(x->x.releaseId().equals(q.releaseId()));
        }finally{resume.countDown();executor.shutdownNow();}
    }
    @Test void concurrentActivationsSerializeAndKeepOneActiveRelease()throws Exception {
        var barrier=new CyclicBarrier(3);var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->{barrier.await();new KnowledgeCorpusService(jdbc,tx,vectors).activate(p.releaseId(),OWNER,PROJECT);return true;});
            var second=executor.submit(()->{barrier.await();new KnowledgeCorpusService(jdbc,tx,vectors).activate(q.releaseId(),OWNER,PROJECT);return true;});
            barrier.await(15,TimeUnit.SECONDS);assertThat(first.get(15,TimeUnit.SECONDS)).isTrue();assertThat(second.get(15,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_release WHERE owner_id=? AND project_id=? AND active",Integer.class,OWNER,PROJECT)).isEqualTo(1);
        }finally{executor.shutdownNow();}
    }
    @Test void legacyCandidateCannotCarryTransientProvenanceAfterMetadataAba() {
        var c=ctx();var hooked=new HookJdbc();var changed=new AtomicBoolean();var restore=new AtomicBoolean();
        String original=jdbc.queryForObject("SELECT data_nature FROM dt_knowledge_document WHERE release_id=?",String.class,p.releaseId());
        hooked.before=sql->{if(sql.contains("AS similarity")&&changed.compareAndSet(false,true)){
            jdbc.update("UPDATE dt_knowledge_document SET data_nature='SIMULATED' WHERE release_id=?",p.releaseId());restore.set(true);
        }};
        hooked.after=sql->{if(sql.contains("AS similarity")&&restore.compareAndSet(true,false))jdbc.update("UPDATE dt_knowledge_document SET data_nature=? WHERE release_id=?",original,p.releaseId());};
        assertThatThrownBy(()->new KnowledgeSearchService(hooked,tx,vectors,5,.45).searchChannel(c,"PROFINET",KnowledgeSearchService.KnowledgeFilters.NONE,5,KnowledgeSearchService.SearchChannel.VECTOR)).hasMessage("KNOWLEDGE_SNAPSHOT_CHANGED");assertThat(refs(c)).isZero();
    }
    @Test void crossProjectCannotSwitchReleaseOrRegisterReferences() {
        assertThatThrownBy(()->new KnowledgeCorpusService(jdbc,tx,vectors).deactivate(p.releaseId(),OWNER,"foreign-project")).hasMessage("NOT_FOUND_OR_DENIED");
        var c=ctx();var wrong=new ToolContext(OWNER,"foreign-project",c.conversationId(),c.runId(),null,Instant.now(),null,c.allowedTools());
        var h=new HybridRetrievalService(jdbc,tx,vectors,new LuceneBm25Index(temp),HybridRetrievalService.Configuration.DEFAULT);
        assertThatThrownBy(()->new KnowledgeSearchService(jdbc,tx,vectors,5,.45).withHybrid(h,HybridRetrievalService.Mode.DENSE).search(wrong,"PROFINET",KnowledgeSearchService.KnowledgeFilters.NONE,5)).hasMessage("NOT_FOUND_OR_DENIED");
        assertThat(refs(c)).isZero();
    }
}
