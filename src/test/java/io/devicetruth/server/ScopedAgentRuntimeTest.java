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
class ScopedAgentRuntimeTest {
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
    @Test void twoScopesShareWorkerButPersistDistinctActorsAndRecoverAfterRestart() throws Exception {
        var disabled=service(false,this::ask);var aa=disabled.forScope(a);var bb=disabled.forScope(b);
        UUID ca=aa.create().conversationId(),cb=bb.create().conversationId();
        var ra=aa.submit(ca,UUID.randomUUID(),input("甲的任务"));var rb=bb.submit(cb,UUID.randomUUID(),input("乙的任务"));
        assertThat(aa.list()).hasSize(1);assertThat(bb.list()).hasSize(1);assertThatThrownBy(()->bb.run(ra.runId())).isInstanceOf(GeneralAgentService.RequestFailure.class);
        var restarted=service(true,this::ask);restarted.recoverQueued();
        await(restarted.forScope(a),ra.runId(),"WAITING_FOR_USER");await(restarted.forScope(b),rb.runId(),"WAITING_FOR_USER");
        assertThat(runs.persistedScope(ra.runId()).actorUserId()).isEqualTo(a.actorUserId());assertThat(runs.persistedScope(rb.runId()).actorUserId()).isEqualTo(b.actorUserId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_input_segment",Long.class)).isEqualTo(2);
    }
    @Test void revokedActorCannotAdoptLateModelResult() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var root=service(true,()->new GeneralReasoner(){public String name(){return "blocked-test";}public ActionProposal propose(Context c){entered.countDown();try{release.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new ActionProposal.AskUser("迟到问题",List.of("detail"));}});
        var scoped=root.forScope(a);var run=scoped.submit(scoped.create().conversationId(),UUID.randomUUID(),input("等待模型"));
        assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();jdbc.update("UPDATE dt_workspace_member SET role='VIEWER' WHERE workspace_id=?",a.workspaceId());release.countDown();
        var terminal=await(scoped,run.runId(),"FAILED");assertThat(terminal.failureCode()).isEqualTo("AUTHORIZATION_REVOKED");assertThat(terminal.pendingQuestion()).isNull();
    }
    @Test void cursorsPreserveMicrosecondsAndReadLegacyMillisecondTokens() {
        var scoped=service(false,this::ask).forScope(a);
        for(int i=0;i<3;i++) scoped.submit(scoped.create().conversationId(),UUID.randomUUID(),input("微秒分页"+i));
        jdbc.update("UPDATE dt_conversation SET updated_at='2026-11-20T00:00:00.123456Z' WHERE workspace_id=?",a.workspaceId());
        var first=scoped.summaries(null,null,null,null,2);
        var second=scoped.summaries(null,null,null,first.nextCursor(),2);
        assertThat(first.items()).hasSize(2);assertThat(second.items()).hasSize(1);
        assertThat(second.items().getFirst().conversationId()).isNotIn(first.items().stream().map(GeneralAgentService.ConversationSummary::conversationId).toList());
        jdbc.update("UPDATE dt_conversation SET updated_at='2026-11-20T00:00:00.123Z' WHERE workspace_id=?",a.workspaceId());
        first=scoped.summaries(null,null,null,null,2);
        var last=first.items().getLast();
        String legacy="0:"+last.updatedAt().toEpochMilli()+":"+last.conversationId();
        String inner=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String outer=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(io.devicetruth.adapter.Json.write(java.util.Map.of("workspace",a.workspaceId().toString(),"query","","asset","","status","","cursor",inner)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(scoped.summaries(null,null,null,outer,2).items()).hasSize(1);
    }
    @Test void cursorsAndSkillInstallationsRemainBoundToStorageScope() {
        var root=service(false,this::ask);var aa=root.forScope(a);var bb=root.forScope(b);
        for(int i=0;i<2;i++)aa.submit(aa.create().conversationId(),UUID.randomUUID(),input("任务"+i));
        String cursor=aa.summaries(null,null,null,null,1).nextCursor();assertThat(cursor).isNotNull();
        assertThat(aa.summaries(null,null,null,cursor,1).items()).hasSize(1);
        assertThatThrownBy(()->bb.summaries(null,null,null,cursor,1)).hasMessageContaining("INVALID_CURSOR");
        assertThatThrownBy(()->aa.summaries("其他筛选",null,null,cursor,1)).hasMessageContaining("INVALID_CURSOR");
        var repository=new SkillInstallationRepository(jdbc);
        var runtime=new io.devicetruth.skill.SkillRuntime(io.devicetruth.skill.SkillRegistry.load(java.nio.file.Path.of("skills")),repository,
                a.storageIdentity(),Set.of("get_asset"),List.of("automotive-diagnosis@1.0.1"));
        runtime.forIdentity(b.storageIdentity());
        assertThat(repository.enabled(a.storageIdentity())).hasSize(1);assertThat(repository.enabled(b.storageIdentity())).hasSize(1);
        jdbc.update("UPDATE dt_skill_installation SET enabled=false WHERE owner_id=?",a.storageIdentity().ownerId());
        runtime.forIdentity(b.storageIdentity()).forIdentity(a.storageIdentity());
        assertThat(repository.enabled(a.storageIdentity())).isEmpty();assertThat(repository.enabled(b.storageIdentity())).hasSize(1);
    }
    @Test void authenticatedStartupDoesNotResumeUnclaimedLegacyRunsButInternalEntryStillWorks() throws Exception {
        var disabled=service(false,this::ask);
        var legacy=disabled.submit(disabled.create().conversationId(),UUID.randomUUID(),input("未认领旧任务"));
        var scoped=disabled.forScope(a);var current=scoped.submit(scoped.create().conversationId(),UUID.randomUUID(),input("已绑定任务"));
        var product=service(true,this::ask);product.recoverAuthenticatedQueued();
        await(product.forScope(a),current.runId(),"WAITING_FOR_USER");
        assertThat(product.run(legacy.runId()).status()).isEqualTo("QUEUED");
        var maintenance=service(true,this::ask);maintenance.recoverQueued();
        await(maintenance,legacy.runId(),"WAITING_FOR_USER");
    }
    @Test void standardSkillSelectionIsPinnedSeparatelyForEachScope() throws Exception {
        var legacy=new GeneralAgentService.ServerIdentity("legacy","legacy");
        var skills=new io.devicetruth.skill.SkillRuntime(io.devicetruth.skill.SkillRegistry.load(java.nio.file.Path.of("skills")),
                new SkillInstallationRepository(jdbc),legacy,Set.of(),List.of("automotive-diagnosis@1.0.1"));
        var root=new GeneralAgentService(conversations,runs,tx,legacy,new CapabilityGateway(),()->new GeneralReasoner(){
            public String name(){return "standard-skill-test";}
            public ActionProposal propose(Context context){return context.activeSkill()==null
                    ?new ActionProposal.CallTool("activate_skill",Map.of("skillId","automotive-diagnosis"),"读取指引")
                    :new ActionProposal.AskUser("请补充现场信息",List.of("detail"));}
        },2,10,true,null,1,3,skills);services.add(root);
        var aa=root.forScope(a);var bb=root.forScope(b);
        var ra=aa.submit(aa.create().conversationId(),UUID.randomUUID(),input("车辆排查"));
        var rb=bb.submit(bb.create().conversationId(),UUID.randomUUID(),input("其他车辆排查"));
        await(aa,ra.runId(),"WAITING_FOR_USER");await(bb,rb.runId(),"WAITING_FOR_USER");
        assertThat(aa.runSkill(ra.runId()).skillId()).isEqualTo("automotive-diagnosis");
        assertThat(bb.runSkill(rb.runId()).version()).isEqualTo("1.0.1");
        assertThatThrownBy(()->bb.runSkill(ra.runId())).isInstanceOf(GeneralAgentService.RequestFailure.class);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT owner_id) FROM dt_run_skill",Long.class)).isEqualTo(2);
    }
}
