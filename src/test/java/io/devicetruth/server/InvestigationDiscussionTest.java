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
class InvestigationDiscussionTest {
    private final AccountWorkspaceMigrationTest fixture=new AccountWorkspaceMigrationTest();
    private JdbcTemplate jdbc;private TransactionTemplate tx;private AgentRunRepository runs;private ConversationRepository conversations;
    private AccessScope a,b;private final List<GeneralAgentService> services=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        fixture.createDatabase();var source=new DriverManagerDataSource(fixture.base+fixture.database,fixture.user,fixture.password);
        Flyway.configure().dataSource(source).load().migrate();jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));runs=new AgentRunRepository(jdbc);conversations=new ConversationRepository(jdbc);
        var accounts=new AccountService(jdbc,tx,new ProductAuthConfiguration().accountPasswordEncoder());var scopes=new WorkspaceScopeResolver(jdbc);
        a=scopes.resolve(accounts.register("a@example.com","long-password-123","甲").userId(),null);
        b=scopes.resolve(accounts.register("b@example.com","long-password-123","乙").userId(),null);
    }
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
    @Test void discussionsAreImmutablePagedAndNeverImplicitModelInput() throws Exception {
        var root=service(false,this::ask);var scoped=root.forScope(a);var id=scoped.create().conversationId();
        var first=scoped.addDiscussion(id,"怀疑控制信号，尚未检查");
        var second=scoped.addDiscussion(id,"安排明天检查");
        assertThat(first.authorUserId()).isEqualTo(a.actorUserId());assertThat(first.authorName()).isEqualTo("甲");
        assertThat(first.createdAt()).isNotNull();assertThat(first.version()).isEqualTo(1);
        assertThat(scoped.read(id).messages()).isEmpty();assertThat(scoped.read(id).runs()).isEmpty();
        var page=scoped.discussions(id,0,1);assertThat(page.items()).containsExactly(first);
        assertThat(scoped.discussions(id,page.nextCursor(),1).items()).containsExactly(second);
        assertThatThrownBy(()->root.forScope(b).discussions(id,0,20)).isInstanceOf(GeneralAgentService.RequestFailure.class);
        var run=scoped.submit(id,UUID.randomUUID(),input("正式输入"));
        assertThat(conversations.executionMessages(a.storageIdentity(),run.runId(),run.goalMessageId()).toString()).doesNotContain(first.content(),second.content());
        jdbc.update("UPDATE dt_workspace_member SET role='VIEWER' WHERE workspace_id=?",a.workspaceId());
        assertThatThrownBy(()->scoped.addDiscussion(id,"不能写")).hasMessageContaining("AUTHORIZATION_REVOKED");
        assertThat(scoped.discussions(id,0,20).items()).hasSize(2);
    }
    @Test void quotesAreServerResolvedAndFrozen() {
        var root=service(false,this::ask);var scoped=root.forScope(a);var id=scoped.create().conversationId();
        var d=scoped.addDiscussion(id,"未检查的猜测");
        var quote=Map.<String,Object>of("discussionId",d.discussionId().toString(),"version",1);
        var input=new GeneralAgentService.AgentTaskInput(1,"请分析",null,List.of(),List.of(),null,null,List.of(quote),0L);
        var run=scoped.submit(id,UUID.randomUUID(),input);
        scoped.addDiscussion(id,"后来新增不能注入");
        var messages=conversations.executionMessages(a.storageIdentity(),run.runId(),run.goalMessageId());
        assertThat(messages.toString()).contains(d.content(),"USER_REPORTED_UNVERIFIED","甲").doesNotContain("后来新增不能注入");
        assertThat(scoped.references(run.runId())).isEmpty();
        var other=scoped.create().conversationId();
        assertThatThrownBy(()->scoped.submit(other,UUID.randomUUID(),input)).isInstanceOf(GeneralAgentService.RequestFailure.class);
        assertThat(scoped.read(other).runs()).isEmpty();
        scoped.cancel(run.runId());
        long version=scoped.read(id).conversation().version();
        var stale=new GeneralAgentService.AgentTaskInput(1,"分析",null,List.of(),List.of(),null,null,
            List.of(Map.of("discussionId",d.discussionId().toString(),"version",2)),version);
        assertThatThrownBy(()->scoped.submit(id,UUID.randomUUID(),stale)).hasMessageContaining("DISCUSSION_VERSION_CONFLICT");
        var forged=new GeneralAgentService.AgentTaskInput(1,"分析",null,List.of(),List.of(),null,null,
            List.of(Map.of("discussionId",d.discussionId().toString(),"version",1,"authorName","伪造")),version);
        assertThatThrownBy(()->scoped.submit(id,UUID.randomUUID(),forged)).isInstanceOf(IllegalArgumentException.class);
        assertThat(scoped.read(id).messages().getFirst().discussionQuotes()).hasSize(1);

    }    private void team() {
        var workspace=new WorkspaceService(jdbc,tx).createTeam(a.actorUserId(),"共同调查").workspaceId();
        jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'COLLABORATOR')",workspace,b.actorUserId());
        var resolver=new WorkspaceScopeResolver(jdbc);a=resolver.resolve(a.actorUserId(),workspace);b=resolver.resolve(b.actorUserId(),workspace);
    }
    private GeneralAgentService.AgentTaskInput versioned(long version) {
        return new GeneralAgentService.AgentTaskInput(1,"共同输入",null,List.of(),List.of(),null,null,null,version);
    }
    @Test void concurrentSubmissionsAreSerialAndCancellationDoesNotReuseRound() throws Exception {
        team();var aa=service(false,this::ask).forScope(a);var bb=service(false,this::ask).forScope(b);
        var id=aa.create().conversationId();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        UUID keyA=UUID.randomUUID(),keyB=UUID.randomUUID();
        try {
            var one=pool.submit(()->{gate.await();try{aa.submit(id,keyA,versioned(0));return "OK";}catch(GeneralAgentService.RequestFailure e){return e.code();}});
            var two=pool.submit(()->{gate.await();try{bb.submit(id,keyB,versioned(0));return "OK";}catch(GeneralAgentService.RequestFailure e){return e.code();}});
            gate.countDown();assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder("OK","CONVERSATION_VERSION_CONFLICT");
        } finally {pool.shutdownNow();}
        var run=aa.read(id).runs().getFirst();assertThat(run.roundNumber()).isEqualTo(1);
        var winner=run.initiatedByUserId().equals(a.actorUserId())?aa:bb;
        var key=run.initiatedByUserId().equals(a.actorUserId())?keyA:keyB;
        assertThatThrownBy(()->winner.submit(id,key,versioned(0))).hasMessageContaining("DUPLICATE_REQUEST");
        if(run.initiatedByUserId().equals(a.actorUserId())) assertThatThrownBy(()->bb.cancel(run.runId())).hasMessageContaining("CANCEL_NOT_ALLOWED");
        aa.cancel(run.runId());
        assertThat(aa.read(id).messages()).hasSize(1);
        var next=aa.submit(id,UUID.randomUUID(),versioned(aa.read(id).conversation().version()));
        assertThat(next.roundNumber()).isEqualTo(2);
        assertThatThrownBy(()->bb.cancel(next.runId())).hasMessageContaining("CANCEL_NOT_ALLOWED");
        assertThatThrownBy(()->aa.submit(id,UUID.randomUUID(),versioned(aa.read(id).conversation().version())))
            .hasMessageContaining("CONVERSATION_BUSY");
        assertThat(aa.events(run.runId(),0)).anySatisfy(e->{assertThat(e.type()).isEqualTo("RUN_CANCEL_REQUESTED");assertThat(e.actorUserId()).isEqualTo(a.actorUserId());});
    }
    @Test void competingRepliesResumeSameRunAndPreserveSegmentActorOnRestart() throws Exception {
        team();var root=service(true,this::ask);var aa=root.forScope(a);var bb=root.forScope(b);
        var id=aa.create().conversationId();var initial=aa.submit(id,UUID.randomUUID(),versioned(0));
        var waiting=await(aa,initial.runId(),"WAITING_FOR_USER");
        assertThatThrownBy(()->aa.submit(id,UUID.randomUUID(),versioned(aa.read(id).conversation().version())))
            .hasMessageContaining("CONVERSATION_BUSY");
        var question=waiting.pendingQuestion();
        var reply=new GeneralAgentService.AgentTaskInput(1,"补答",null,List.of(),List.of(),null,
            Map.of("runId",waiting.runId().toString(),"pendingQuestionId",question.pendingQuestionId().toString(),"questionVersion",question.questionVersion()));
        var disabled=service(false,this::ask);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{gate.await();try{disabled.forScope(a).submit(id,UUID.randomUUID(),reply);return "OK";}catch(GeneralAgentService.RequestFailure e){return e.code();}});
            var two=pool.submit(()->{gate.await();try{disabled.forScope(b).submit(id,UUID.randomUUID(),reply);return "OK";}catch(GeneralAgentService.RequestFailure e){return e.code();}});
            gate.countDown();assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder("OK","STALE_OR_UNRELATED_REPLY");
        } finally {pool.shutdownNow();}
        assertThat(aa.read(id).runs()).hasSize(1);
        var actor=runs.persistedScope(initial.runId()).actorUserId();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_input_segment WHERE run_id=?",Integer.class,initial.runId())).isEqualTo(2);
        service(true,this::ask).recoverAuthenticatedQueued();
        var resumed=await(aa,initial.runId(),"WAITING_FOR_USER");
        assertThat(resumed.asOf()).isEqualTo(initial.asOf());assertThat(resumed.executionActorUserId()).isEqualTo(actor);
    }
    @Test void executionUsesPersistedAttachmentVersionsAndQuotesAfterRestart() throws Exception {
        var root=service(false,this::ask);var aa=root.forScope(a);var id=aa.create().conversationId();
        UUID run=tx.execute(status->{
            var message=conversations.appendWithReferences(a.storageIdentity(),id,UUID.randomUUID(),"正式输入",null,
                java.time.Instant.now(),java.time.Instant.now(),List.of(Map.of("documentId",UUID.randomUUID().toString(),"version","v1")),List.of());
            var queued=runs.enqueue(a.storageIdentity(),id,message,java.time.Instant.now(),java.time.Instant.now());
            runs.bindRun(queued.runId(),message,a);return queued.runId();
        });
        // 反例：活表附件元数据后续变化，恢复必须读取已确认段，而不是重新选择最新版本。
        jdbc.update("UPDATE dt_message SET attachment_refs='[{\"version\":\"v2\"}]'::jsonb WHERE conversation_id=?",id);
        aa.addDiscussion(id,"运行后新增讨论");
        var captured=new java.util.concurrent.atomic.AtomicReference<GeneralReasoner.Context>();
        var restarted=service(true,()->new GeneralReasoner(){public String name(){return "snapshot-capture";}
            public ActionProposal propose(Context context){captured.set(context);return new ActionProposal.AskUser("请确认",List.of("detail"));}});
        restarted.recoverAuthenticatedQueued();await(aa,run,"WAITING_FOR_USER");
        assertThat(captured.get().messages().toString()).contains("v1").doesNotContain("v2","运行后新增讨论");
    }

}

