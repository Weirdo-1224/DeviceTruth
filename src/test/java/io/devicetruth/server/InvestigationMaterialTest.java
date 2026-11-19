package io.devicetruth.server;
import io.devicetruth.application.agent.*;
import io.devicetruth.adapter.persistence.*;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.reasoning.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
class InvestigationMaterialTest {
    private final AccountWorkspaceMigrationTest fixture=new AccountWorkspaceMigrationTest();
    private JdbcTemplate jdbc;private TransactionTemplate tx;private AgentRunRepository runs;private ConversationRepository conversations;
    private io.devicetruth.knowledge.FileUploadService files;
    private io.devicetruth.knowledge.KnowledgeImportService imports;
    private io.devicetruth.knowledge.KnowledgeSearchService searches;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path storage;
    private AccessScope a,b;private final List<GeneralAgentService> services=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        fixture.createDatabase();var source=new DriverManagerDataSource(fixture.base+fixture.database,fixture.user,fixture.password);
        Flyway.configure().dataSource(source).load().migrate();jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));runs=new AgentRunRepository(jdbc);conversations=new ConversationRepository(jdbc);
        var accounts=new AccountService(jdbc,tx,new ProductAuthConfiguration().accountPasswordEncoder());var scopes=new WorkspaceScopeResolver(jdbc);
        a=scopes.resolve(accounts.register("a@example.com","long-password-123","甲").userId(),null);
        b=scopes.resolve(accounts.register("b@example.com","long-password-123","乙").userId(),null);
    }
    @BeforeEach void materials() {
        var embedding=new io.devicetruth.knowledge.DeterministicEmbeddingClient(io.devicetruth.knowledge.EmbeddingProfile.of("deterministic","","","deterministic-char2",1024,"1",1000,false));
        imports=new io.devicetruth.knowledge.KnowledgeImportService(jdbc,tx,embedding,1000);
        files=new io.devicetruth.knowledge.FileUploadService(jdbc,imports,storage,1,10);
        searches=new io.devicetruth.knowledge.KnowledgeSearchService(jdbc,tx,embedding,10,0);
    }
    @AfterEach void closeFiles() {if(files!=null)files.close();}
    @AfterEach void cleanup() throws Exception {services.forEach(GeneralAgentService::shutdown);fixture.dropDatabase();}
    private GeneralAgentService service(boolean enabled,java.util.function.Supplier<GeneralReasoner> reasoner) {
        var root=new GeneralAgentService(conversations,runs,tx,new GeneralAgentService.ServerIdentity("legacy","legacy"),new CapabilityGateway(),reasoner,2,10,enabled,null,1,3);
        services.add(root);return root;
    }
    private GeneralAgentService.AgentTaskInput input(String text){return new GeneralAgentService.AgentTaskInput(1,text,null,List.of(),List.of(),null,null);}
    private GeneralReasoner ask(){return new GeneralReasoner(){public String name(){return "test";}public ActionProposal propose(Context c){return new ActionProposal.AskUser("请确认现象",List.of("detail"));}};}
    private GeneralAgentService.RunView await(GeneralAgentService service,UUID id,String status) throws Exception {
        for(int i=0;i<200;i++){var run=service.run(id);if(run.status().equals(status))return run;Thread.sleep(25);}throw new AssertionError("Run did not reach "+status);
    }
    private io.devicetruth.knowledge.FileUploadService.FileView upload(AccessScope scope,UUID conversation,String name,String text) throws Exception {
        var f=files.uploadAttachment(scope,conversation,new org.springframework.mock.web.MockMultipartFile("file",name,name.endsWith(".txt")?"text/plain":"text/markdown",text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),new io.devicetruth.knowledge.FileUploadService.ImportMetadata(name,"general",null,"1","SIMULATED"));
        for(int i=0;i<100;i++){f=files.require(scope.storageIdentity().ownerId(),scope.storageIdentity().projectId(),f.fileId());if(!List.of("UPLOADED","PROCESSING").contains(f.parseStatus()))return f;Thread.sleep(30);}throw new AssertionError("Import timeout");
    }
    private io.devicetruth.core.capability.ToolContext context(GeneralAgentService.RunView run) {
        return new io.devicetruth.core.capability.ToolContext(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),run.conversationId(),run.runId(),null,run.requestedAt(),run.asOf(),Set.of("search_knowledge","get_document_excerpt"));
    }
    @Test void attachmentsAreFrozenPerRunAndOnlyExplicitPublicationCrossesInvestigations() throws Exception {
        var aa=service(false,this::ask).forScope(a);var ca=aa.create().conversationId();var cb=aa.create().conversationId();
        var file=upload(a,ca,"检查.txt","控制信号 ABC-123 尚未检查");assertThat(file.parseStatus()).isEqualTo("PARSED");
        assertThat(file.uploadedByUserId()).isEqualTo(a.actorUserId());assertThat(file.usageScope()).isEqualTo("CONVERSATION_ATTACHMENT");
        assertThat(aa.read(ca).runs()).isEmpty();assertThat(files.content(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),file.fileId()).content()).contains("尚未检查");
        var ra=aa.submit(ca,UUID.randomUUID(),input("分析"));var rb=aa.submit(cb,UUID.randomUUID(),input("其他调查"));
        for(var channel:io.devicetruth.knowledge.KnowledgeSearchService.SearchChannel.values()) {
            assertThat(searches.searchChannel(context(ra),"ABC-123",null,10,channel).hits()).isNotEmpty();
            assertThat(searches.searchChannel(context(rb),"ABC-123",null,10,channel).hits()).isEmpty();
        }
        var hit=searches.search(context(ra),"ABC-123",null,10).hits().getFirst();
        assertThatThrownBy(()->searches.excerpt(context(rb),hit.documentId(),hit.chunkId())).isInstanceOf(io.devicetruth.knowledge.KnowledgeException.class);
        assertThatThrownBy(()->tx.execute(status->runs.importReference(a.storageIdentity(),hit.referenceId(),rb.runId()))).isInstanceOf(GeneralAgentService.RequestFailure.class);
        var late=upload(a,ca,"新增.md","# NEW-999\n后来新增信息");
        assertThat(searches.search(context(ra),"NEW-999",new io.devicetruth.knowledge.KnowledgeSearchService.KnowledgeFilters(null,null,late.documentId()),10).hits()).isEmpty();
        var p=files.publish(a,file.fileId(),"检查记录","仅适用 ABC-123，未现场核实");
        assertThat(files.publish(a,file.fileId(),"重复","重复").publicationId()).isEqualTo(p.publicationId());
        assertThat(searches.search(context(rb),"ABC-123",null,10).hits()).allSatisfy(h->assertThat(h.dataNature()).isEqualTo("SIMULATED"));
        assertThat(searches.search(context(rb),"ABC-123",null,10).hits()).isNotEmpty();
        UUID inherited=tx.execute(status->runs.importReference(a.storageIdentity(),hit.referenceId(),rb.runId()));
        assertThat(inherited).isNotNull();
        aa.cancel(ra.runId());
        var gateway=new CapabilityGateway();new AgentReportTools(runs,conversations).registerInto(gateway);
        var historical=new io.devicetruth.core.capability.ToolContext(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),ca,ra.runId(),null,ra.requestedAt(),ra.asOf(),Set.of("generate_diagnosis_report"));
        var report=gateway.executeTool(historical,"generate_diagnosis_report",Map.of());
        assertThat(report.success()).isTrue();assertThat(report.references()).anySatisfy(ref->{assertThat(ref.sourceVersion()).isEqualTo(hit.documentVersion());assertThat(ref.dataNature()).isEqualTo("SIMULATED");});
        assertThat(searches.excerpt(context(ra),hit.documentId(),hit.chunkId()).documentVersion()).isEqualTo(hit.documentVersion());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_MODEL'",Integer.class,ra.runId())).isZero();
        var next=aa.submit(ca,UUID.randomUUID(),input("确认新增材料"));
        assertThat(searches.search(context(next),"NEW-999",new io.devicetruth.knowledge.KnowledgeSearchService.KnowledgeFilters(null,null,late.documentId()),10).hits()).isNotEmpty();
        assertThat(files.knowledge(a,0,20)).hasSize(1);assertThat(files.attachments(a,ca,0,20)).hasSize(2);
        assertThatThrownBy(()->files.attachments(b,ca,0,20)).isInstanceOf(GeneralAgentService.RequestFailure.class);
        assertThatThrownBy(()->files.requireAttachmentConversation(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),cb,List.of(Map.of("fileId",file.fileId().toString())))).isInstanceOf(io.devicetruth.knowledge.KnowledgeException.class);
    }
    @Test void pdfAndReadonlyDoNotPublishOrCreateRunsAndLegacyImportRemainsKnowledge() throws Exception {
        var aa=service(false,this::ask).forScope(a);var ca=aa.create().conversationId();
        var pdf=files.uploadAttachment(a,ca,new org.springframework.mock.web.MockMultipartFile("file","test.pdf","application/pdf","%PDF".getBytes()),new io.devicetruth.knowledge.FileUploadService.ImportMetadata("PDF","general",null,"1","SIMULATED"));
        assertThat(pdf.parseStatus()).isEqualTo("OCR_REQUIRED");
        assertThatThrownBy(()->files.publish(a,pdf.fileId(),"PDF","当前设备")).isInstanceOf(io.devicetruth.knowledge.KnowledgeException.class);
        assertThatThrownBy(()->files.content(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),pdf.fileId())).isInstanceOf(io.devicetruth.knowledge.KnowledgeException.class);
        var legacy=files.upload(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),new org.springframework.mock.web.MockMultipartFile("file","legacy.md","text/markdown","# LEG-001\n旧入口知识".getBytes(java.nio.charset.StandardCharsets.UTF_8)),new io.devicetruth.knowledge.FileUploadService.ImportMetadata("旧知识","general",null,"1","SIMULATED"));
        for(int i=0;i<100&&!files.require(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),legacy.fileId()).parseStatus().equals("PARSED");i++)Thread.sleep(30);
        assertThat(files.require(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),legacy.fileId()).usageScope()).isEqualTo("SPACE_KNOWLEDGE");
        assertThat(files.require(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),legacy.fileId()).uploadedByUserId()).isNull();
        var run=aa.submit(ca,UUID.randomUUID(),input("分析"));assertThat(searches.search(context(run),"LEG-001",null,10).hits()).isNotEmpty();
        jdbc.update("UPDATE dt_workspace_member SET role='VIEWER' WHERE workspace_id=?",a.workspaceId());
        assertThatThrownBy(()->upload(a,ca,"拒绝.md","拒绝")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->files.publish(a,pdf.fileId(),"拒绝","拒绝")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(aa.read(ca).runs()).hasSize(1);
    }    @Test void concurrentPublicationIsIdempotentAndFailedImportCanRetryWithoutPromotion() throws Exception {
        var team=new WorkspaceService(jdbc,tx).createTeam(a.actorUserId(),"资料团队").workspaceId();
        jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'COLLABORATOR')",team,b.actorUserId());
        var resolver=new WorkspaceScopeResolver(jdbc);a=resolver.resolve(a.actorUserId(),team);b=resolver.resolve(b.actorUserId(),team);
        var aa=service(false,this::ask).forScope(a);var ca=aa.create().conversationId();
        var file=upload(a,ca,"手册.md","# ABC-456\n模拟检查记录");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{gate.await();return files.publish(a,file.fileId(),"手册","ABC-456");});
            var two=pool.submit(()->{gate.await();return files.publish(b,file.fileId(),"手册","ABC-456");});
            gate.countDown();assertThat(one.get().publicationId()).isEqualTo(two.get().publicationId());
        } finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_knowledge_publication",Integer.class)).isEqualTo(1);
        var publication=files.publish(b,file.fileId(),"不改写原发布","新范围");
        assertThat(publication.originalAuthorUserId()).isEqualTo(a.actorUserId());assertThat(publication.title()).isEqualTo("手册");
        jdbc.update("UPDATE dt_knowledge_document SET ingest_status='NEEDS_REBUILD' WHERE id=?",file.documentId());
        assertThatThrownBy(()->files.publish(a,file.fileId(),"手册","ABC-456")).isInstanceOf(io.devicetruth.knowledge.KnowledgeException.class);
        assertThat(files.knowledge(a,0,20)).isEmpty();
        jdbc.update("UPDATE dt_knowledge_document SET ingest_status='READY' WHERE id=?",file.documentId());
        assertThat(files.knowledge(a,0,20)).hasSize(1);
        var failed=upload(a,ca,"empty.md","   ");assertThat(failed.parseStatus()).isEqualTo("FAILED");
        assertThatThrownBy(()->files.publish(a,failed.fileId(),"空记录","设备")).isInstanceOf(io.devicetruth.knowledge.KnowledgeException.class);
        files.retry(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),failed.fileId());
        for(int i=0;i<100&&List.of("UPLOADED","PROCESSING").contains(files.require(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),failed.fileId()).parseStatus());i++)Thread.sleep(20);
        assertThat(files.require(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),failed.fileId()).parseStatus()).isEqualTo("FAILED");
        assertThat(aa.read(ca).runs()).isEmpty();
    }
    @Test void identicalContentAcrossSpacesDoesNotLeakAndExplicitImportKeepsKnowledgeMeaning() throws Exception {
        var root=service(false,this::ask);var aa=root.forScope(a);var bb=root.forScope(b);
        var ca=aa.create().conversationId();var cb=bb.create().conversationId();
        String text="# SAME-001\n未核实的模拟记录";
        var fa=upload(a,ca,"same.md",text);var fb=upload(b,cb,"same.md",text);
        var ra=aa.submit(ca,UUID.randomUUID(),input("分析"));
        assertThat(searches.search(context(ra),"SAME-001",new io.devicetruth.knowledge.KnowledgeSearchService.KnowledgeFilters(null,null,fb.documentId()),10).hits()).isEmpty();
        assertThatThrownBy(()->files.content(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),fb.fileId())).isInstanceOf(io.devicetruth.knowledge.KnowledgeException.class);
        assertThat(fa.documentId()).isNotEqualTo(fb.documentId());
        var other=aa.create().conversationId();var rb=aa.submit(other,UUID.randomUUID(),input("其他调查"));
        assertThat(searches.search(context(rb),"SAME-001",null,10).hits()).isEmpty();
        var imported=imports.importDocument(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),new io.devicetruth.knowledge.KnowledgeImportService.ImportRequest("same.md","general",null,"1","same.md","text/markdown",text,"SIMULATED"));
        assertThat(imported.document().documentId()).isEqualTo(fa.documentId());
        assertThat(searches.search(context(rb),"SAME-001",null,10).hits()).isNotEmpty();
        assertThat(files.require(a.storageIdentity().ownerId(),a.storageIdentity().projectId(),fa.fileId()).usageScope()).isEqualTo("CONVERSATION_ATTACHMENT");
    }

}
