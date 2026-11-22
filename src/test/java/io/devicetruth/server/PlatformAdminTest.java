package io.devicetruth.server;

import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.admin.application.*;
import io.devicetruth.admin.api.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** 隔离真实 PG + HTTP/Cookie/CSRF；协议测试不访问外部付费服务。 */
class PlatformAdminTest extends AccountWorkspaceMigrationTest {
    private static final String PASSWORD="admin-fixture-password-123";
    private org.springframework.context.ConfigurableApplicationContext app() {
        return new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product","--server.port=0","--devicetruth.auth.cookie-secure=false",
                "--spring.datasource.url="+base+database,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
                "--devicetruth.agent.worker-enabled=false","--devicetruth.agent.reasoner=scripted","--devicetruth.embedding.provider=deterministic");
    }
    private static class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        final String root;
        Browser(String root){this.root=root;}
        HttpResponse<String> send(String method,String path,Object body,String header) throws Exception {
            var request=HttpRequest.newBuilder(URI.create(root+path));
            if(header!=null)request.header("X-Workspace-ID",header);
            if(!method.equals("GET")) {
                var csrf=client.send(HttpRequest.newBuilder(URI.create(root+(path.startsWith("/api/admin/")?"/api/admin/auth/csrf":"/api/auth/csrf"))).build(),HttpResponse.BodyHandlers.ofString());
                request.header("X-CSRF-TOKEN",Json.MAPPER.readTree(csrf.body()).get("token").asText());
                request.header("Content-Type","application/json");
            }
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(Json.write(body)));
            return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
        void loginAdmin(String email) throws Exception {assertThat(send("POST","/api/admin/auth/login",Map.of("email",email,"password",PASSWORD),null).statusCode()).isEqualTo(200);}
        void login(String email) throws Exception {assertThat(send("POST","/api/auth/login",Map.of("email",email,"password",PASSWORD),null).statusCode()).isEqualTo(200);}
    }
    @Test void realHttpPermissionProjectionPagingAndControlledCreation() throws Exception {
        try(var app=app()) {
            var jdbc=new JdbcTemplate(app.getBean(HikariDataSource.class));var tx=new TransactionTemplate(new DataSourceTransactionManager(app.getBean(HikariDataSource.class)));
            var accounts=app.getBean(AccountService.class);var admin=legacyAdminFixture(accounts,"admin@example.com",PASSWORD,"运营");var ordinary=accounts.register("ordinary@example.com",PASSWORD,"普通用户");
            UUID workspace=jdbc.queryForObject("SELECT id FROM dt_workspace WHERE personal_user_id=?",UUID.class,ordinary.userId());
            var maintenance=new AdminMaintenance(jdbc,tx);String root="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
            var browser=new Browser(root);
            assertThat(browser.send("GET","/api/admin/access",null,null).statusCode()).isEqualTo(401);
            browser.login("ordinary@example.com");app.getBean(WorkspaceService.class).createTeam(ordinary.userId(),"团队管理员仍非平台管理员");assertThat(browser.send("GET","/api/admin/users",null,null).statusCode()).isEqualTo(401);
            assertThat(browser.send("POST","/api/admin/auth/login",Map.of("email","admin@example.com","password",PASSWORD),null).statusCode()).isEqualTo(403);
            maintenance.platform("test-operator",admin.userId(),true,true,"OPS_REQUEST");browser.loginAdmin("admin@example.com");
            assertThat(browser.send("GET","/api/admin/access",null,null).body()).contains("PLATFORM_ADMIN","QUOTA_ENFORCEMENT");
            assertThat(browser.send("GET","/api/admin/workspaces",null,null).body()).contains("\"items\":[]");
            assertThat(browser.send("GET","/api/admin/knowledge/documents?workspaceId="+workspace,null,null).statusCode()).isEqualTo(404);
            maintenance.grant("test-operator",admin.userId(),workspace,"KNOWLEDGE_READ",true,"PERMISSION_CHANGE");
            var docs=browser.send("GET","/api/admin/knowledge/documents?workspaceId="+workspace,null,null);
            assertThat(docs.statusCode()).isEqualTo(200);assertThat(docs.headers().firstValue("X-Request-ID")).isPresent();
            assertThat(browser.send("GET","/api/admin/knowledge/documents?workspaceId="+workspace,null,UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
            assertThat(browser.send("GET","/api/admin/users",null,workspace.toString()).statusCode()).isEqualTo(400);
            assertThat(browser.send("GET","/api/admin/users?raw=true",null,null).statusCode()).isEqualTo(400);
            assertThat(browser.send("GET","/api/admin/users?limit=101",null,null).statusCode()).isEqualTo(400);
            var first=Json.MAPPER.readTree(browser.send("GET","/api/admin/users?limit=1",null,null).body());String cursor=URLEncoder.encode(first.get("nextCursor").asText(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(browser.send("GET","/api/admin/users?limit=1&cursor="+cursor,null,null).statusCode()).isEqualTo(200);
            assertThat(browser.send("GET","/api/admin/users?limit=1&status=ACTIVE&cursor="+cursor,null,null).body()).contains("INVALID_CURSOR");
            maintenance.grant("test-operator",admin.userId(),workspace,"SKILL_READ",true,"PERMISSION_CHANGE");
            assertThat(browser.send("GET","/api/admin/users?limit=1&cursor="+cursor,null,null).body()).contains("INVALID_CURSOR");
            String secret="SECRET_PASSWORD_ADMIN_PROBE_991";
            var created=browser.send("POST","/api/admin/users",Map.of("email","new@example.com","displayName","新账号","initialPassword",secret),null);
            assertThat(created.statusCode()).isEqualTo(201);assertThat(created.body()).doesNotContain(secret,"password_hash");
            assertThat(browser.send("GET","/api/admin/auth/me",null,null).body()).contains(admin.userId().toString());
            assertThat(browser.send("GET","/api/auth/me",null,null).body()).contains(ordinary.userId().toString());
            assertThat(browser.send("POST","/api/admin/users",Map.of("email","new@example.com","displayName","重复","initialPassword",secret),null).statusCode()).isEqualTo(409);
            assertThat(browser.send("POST","/api/admin/users",Map.of("email","evil@example.com","displayName","伪造","initialPassword",secret,"platformRole","PLATFORM_ADMIN"),null).statusCode()).isEqualTo(400);
            assertThat(browser.send("GET","/api/admin/skills",null,null).statusCode()).isEqualTo(200);
            assertThat(browser.send("GET","/api/admin/skills/automotive-diagnosis",null,null).statusCode()).isEqualTo(200);
            assertThat(browser.send("GET","/api/admin/skills/automotive-diagnosis/references/missing.md",null,null).statusCode()).isEqualTo(404);
            assertThat(browser.send("GET","/api/admin/system/health",null,null).body()).contains("UNKNOWN").doesNotContain("jdbc:","password",secret);
            assertThat(browser.send("GET","/api/admin/audit-events",null,null).body()).contains("USER_CREATE").doesNotContain(secret);
            assertThat(browser.send("GET","/api/admin/usage/summary?from=2026-01-01T00:00:00Z&to=2026-12-01T00:00:00Z",null,null).body()).contains("INVALID_TIME_RANGE");
            assertThat(browser.send("GET","/api/admin/quotas?dimension=PLATFORM",null,null).body()).contains("UNCONFIGURED","\"remaining\":null");
            assertThat(browser.send("PATCH","/api/admin/users/"+ordinary.userId()+"/status",Map.of("status","ACTIVE","expectedVersion",0,"reasonCode","OPS_REQUEST"),null).statusCode()).isEqualTo(200);
            // 给产品内容放探针；管理侧运行查询不能读取这些列。
            UUID conversation=UUID.randomUUID(),message=UUID.randomUUID(),run=UUID.randomUUID();String probe="PRIVATE_CONTENT_PROBE_888";
            jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,workspace_id,created_at,updated_at) SELECT ?,owner_id,project_id,id,now(),now() FROM dt_workspace WHERE id=?",conversation,workspace);
            jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) SELECT ?,id,owner_id,project_id,1,'USER',?,now(),now() FROM dt_conversation WHERE id=?",message,probe,conversation);
            jdbc.update("INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,budget,context_summary,created_at,updated_at,round_number) SELECT ?,id,owner_id,project_id,?,'QUEUED',now(),now(),'{}',?,now(),now(),1 FROM dt_conversation WHERE id=?",run,message,probe,conversation);
            jdbc.update("INSERT INTO dt_run_event(run_id,conversation_id,owner_id,project_id,seq,type,recorded_at,safe_payload) SELECT id,conversation_id,owner_id,project_id,1,'RUN_MODEL',now(),?::jsonb FROM dt_agent_run WHERE id=?",Json.write(Map.of("model","test-model","outcome","VALID_PROPOSAL","inputTokens",10,"cost","0.12","prompt",probe)),run);
            jdbc.update("INSERT INTO dt_run_event(run_id,conversation_id,owner_id,project_id,seq,type,recorded_at,safe_payload) SELECT id,conversation_id,owner_id,project_id,2,'RUN_MODEL',now(),?::jsonb FROM dt_agent_run WHERE id=?",Json.write(Map.of("model","test-model","outcome",probe,"providerError",probe)),run);
            jdbc.update("INSERT INTO dt_run_event(run_id,conversation_id,owner_id,project_id,seq,type,recorded_at,safe_payload) SELECT id,conversation_id,owner_id,project_id,3,'RUN_TOOL',now(),?::jsonb FROM dt_agent_run WHERE id=?",Json.write(Map.of("toolName","search_knowledge","success",false,"errorCode",probe,"arguments",probe)),run);
            jdbc.update("UPDATE dt_agent_run SET status='WAITING_FOR_USER',pending_question_id=?,question_version=1,pending_question=?::jsonb WHERE id=?",UUID.randomUUID(),Json.write(Map.of("question",probe)),run);
            jdbc.update("INSERT INTO dt_run_input_segment(run_id,segment_no,conversation_id,owner_id,project_id,actor_user_id,input_snapshot) SELECT id,1,conversation_id,owner_id,project_id,?,?::jsonb FROM dt_agent_run WHERE id=?",admin.userId(),Json.write(Map.of("content",probe)),run);
            jdbc.update("INSERT INTO dt_run_input_segment(run_id,segment_no,conversation_id,owner_id,project_id,actor_user_id,input_snapshot) SELECT id,2,conversation_id,owner_id,project_id,?,?::jsonb FROM dt_agent_run WHERE id=?",ordinary.userId(),Json.write(Map.of("content",probe)),run);
            for(String path:List.of("/runs","/runs/"+run,"/runs/"+run+"/attempts?kind=MODEL","/runs/"+run+"/attempts?kind=TOOL","/usage/summary")) {
                var response=browser.send("GET","/api/admin"+path,null,null);assertThat(response.statusCode()).as(path+response.body()).isEqualTo(200);assertThat(response.body()).doesNotContain(probe,"contextSummary","goalText","prompt");
            }
            jdbc.update("INSERT INTO dt_run_event(run_id,conversation_id,owner_id,project_id,seq,type,recorded_at,safe_payload) SELECT id,conversation_id,owner_id,project_id,4,'RUN_MODEL',now(),?::jsonb FROM dt_agent_run WHERE id=?",Json.write(Map.of("model","test-model","outcome","MODEL_TRANSPORT_OR_TIMEOUT","providerError",probe)),run);
            var health=Json.MAPPER.readTree(browser.send("GET","/api/admin/system/health",null,null).body());
            for(var component:health.get("components"))if(component.get("name").asText().equals("RECENT_MODEL_CALLS"))assertThat(component.get("metrics").get("recentFailureCount").asInt()).isEqualTo(1);
            var usage=Json.MAPPER.readTree(browser.send("GET","/api/admin/usage/summary",null,null).body());
            assertThat(usage.get("runCount").asInt()).isEqualTo(1);assertThat(usage.get("usage").get("status").asText()).isEqualTo("PARTIAL");
            assertThat(usage.get("statusCounts").get("WAITING_FOR_USER").asInt()).isEqualTo(1);assertThat(usage.get("unattributedAttemptCount").asInt()).isEqualTo(3);
            assertThat(usage.get("unknownCurrencyCostCount").asInt()).isEqualTo(1);assertThat(usage.get("amountBuckets").isEmpty()).isTrue();
            var adminOnly=new Browser(root);adminOnly.loginAdmin("admin@example.com");assertThat(adminOnly.send("GET","/api/runs/"+run,null,null).statusCode()).isEqualTo(401);
            jdbc.update("UPDATE dt_account SET account_type='ADMIN' WHERE id=?",ordinary.userId());maintenance.platform("test-operator",ordinary.userId(),true,false,"OPS_REQUEST");
            String currentCursor=URLEncoder.encode(Json.MAPPER.readTree(browser.send("GET","/api/admin/users?limit=1",null,null).body()).get("nextCursor").asText(),java.nio.charset.StandardCharsets.UTF_8);
            var otherAdmin=new Browser(root);otherAdmin.loginAdmin("ordinary@example.com");assertThat(otherAdmin.send("GET","/api/admin/users?limit=1&cursor="+currentCursor,null,null).body()).contains("INVALID_CURSOR");
            maintenance.platform("test-operator",admin.userId(),false,false,"PERMISSION_CHANGE");
            assertThat(browser.send("GET","/api/admin/access",null,null).statusCode()).isEqualTo(403);
        }
    }
    @Test void disabledActorCannotAdoptLateModelProposalOrInvokeAnotherTool() throws Exception {
        try(var app=app()) {
            var source=app.getBean(HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var accounts=app.getBean(AccountService.class);var admin=legacyAdminFixture(accounts,"late-admin@example.com",PASSWORD,"运营");var actor=accounts.register("late-actor@example.com",PASSWORD,"执行者");
            new AdminMaintenance(jdbc,tx).platform("test",admin.userId(),true,true,"OPS_REQUEST");
            var scope=new io.devicetruth.application.agent.WorkspaceScopeResolver(jdbc).resolve(actor.userId(),null);
            var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);
            var reasoner=new io.devicetruth.core.reasoning.GeneralReasoner() {
                public String name(){return "blocking-protocol-fixture";}
                public io.devicetruth.core.reasoning.ActionProposal propose(Context context){entered.countDown();try{finish.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new io.devicetruth.core.reasoning.ActionProposal.AskUser("LATE_PRIVATE_PROPOSAL",List.of("state"));}
            };
            var repository=new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc);
            var worker=new io.devicetruth.application.agent.GeneralAgentService(new io.devicetruth.adapter.persistence.ConversationRepository(jdbc),repository,tx,scope.storageIdentity(),new io.devicetruth.core.capability.CapabilityGateway(),()->reasoner,1,2,true);
            try {
                var scoped=worker.forScope(scope);var conversation=scoped.create();
                var run=scoped.submit(conversation.conversationId(),UUID.randomUUID(),new io.devicetruth.application.agent.GeneralAgentService.AgentTaskInput(1,"模拟排查",null,null,null,null,null));
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();app.getBean(AdminService.class).userStatus(admin.userId(),UUID.randomUUID(),actor.userId(),"DISABLED",0,"OPS_REQUEST");finish.countDown();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                while(System.nanoTime()<deadline && !jdbc.queryForObject("SELECT status FROM dt_agent_run WHERE id=?",String.class,run.runId()).equals("FAILED"))Thread.sleep(20);
                assertThat(jdbc.queryForObject("SELECT status FROM dt_agent_run WHERE id=?",String.class,run.runId())).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type IN ('RUN_WAITING','RUN_TOOL','RUN_ANSWER')",Long.class,run.runId())).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_message WHERE conversation_id=? AND role='ASSISTANT'",Long.class,conversation.conversationId())).isZero();
            } finally {finish.countDown();worker.shutdown();}
        }
    }
    @Test void disabledAccountClosesExistingStreamBeforeNextOutput() throws Exception {verifyDisabledStream(false);}
    @Test void disabledAccountQuietStreamClosesWithinHeartbeatTolerance() throws Exception {verifyDisabledStream(true);}
    private void verifyDisabledStream(boolean quiet) throws Exception {
        try(var app=app()) {
            var source=app.getBean(HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var accounts=app.getBean(AccountService.class);var admin=legacyAdminFixture(accounts,"stream-admin@example.com",PASSWORD,"运营");var actor=accounts.register("stream-actor@example.com",PASSWORD,"执行者");
            new AdminMaintenance(jdbc,tx).platform("test",admin.userId(),true,true,"OPS_REQUEST");
            var scope=new io.devicetruth.application.agent.WorkspaceScopeResolver(jdbc).resolve(actor.userId(),null);UUID run=seedRun(jdbc,scope.workspaceId());
            jdbc.update("UPDATE dt_agent_run SET execution_actor_user_id=? WHERE id=?",actor.userId(),run);
            UUID conversation=jdbc.queryForObject("SELECT conversation_id FROM dt_agent_run WHERE id=?",UUID.class,run);
            var repository=new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc);repository.appendEvent(scope.storageIdentity(),run,conversation,"RUN_PHASE",Map.of("phase","INVESTIGATING"),java.time.Instant.now());
            var browser=new Browser("http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort());browser.login("stream-actor@example.com");
            var response=browser.client.send(HttpRequest.newBuilder(URI.create(browser.root+"/api/runs/"+run+"/stream")).build(),HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).isEqualTo(200);var reader=new java.io.BufferedReader(new java.io.InputStreamReader(response.body()));
            String line;while((line=reader.readLine())!=null && !line.isBlank()){}
            app.getBean(AdminService.class).userStatus(admin.userId(),UUID.randomUUID(),actor.userId(),"DISABLED",0,"OPS_REQUEST");
            if(!quiet) {
                repository.appendEvent(scope.storageIdentity(),run,conversation,"RUN_PHASE",Map.of("phase","REVOKED_SECRET_STREAM"),java.time.Instant.now());
                app.getBean(io.devicetruth.application.agent.RunEventStreamService.class).publish(run);
            }
            try(var pool=Executors.newSingleThreadExecutor()) {
                var rest=pool.submit(()->{var text=new StringBuilder();String value;while((value=reader.readLine())!=null)text.append(value);return text.toString();});
                assertThat(rest.get(quiet?17:5,TimeUnit.SECONDS)).doesNotContain("REVOKED_SECRET_STREAM");
            } finally {response.body().close();}
        }
    }
    @Test void upgradeFromV24PreservesExistingRowsAndUnknownAuthors() {
        org.flywaydb.core.Flyway.configure().dataSource(base+database,user,password).target("24").load().migrate();
        var jdbc=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(base+database,user,password));
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name) VALUES (?,'existing@example.com','preserved-hash','原账号')",id);
        org.flywaydb.core.Flyway.configure().dataSource(base+database,user,password).load().migrate();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM dt_account WHERE id=?",String.class,id)).isEqualTo("preserved-hash");
        assertThat(jdbc.queryForObject("SELECT status FROM dt_account WHERE id=?",String.class,id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_platform_admin",Long.class)).isZero();
    }
    private UUID seedRun(JdbcTemplate jdbc,UUID workspace) {
        UUID conversation=UUID.randomUUID(),message=UUID.randomUUID(),run=UUID.randomUUID();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,workspace_id,created_at,updated_at) SELECT ?,owner_id,project_id,id,now(),now() FROM dt_workspace WHERE id=?",conversation,workspace);
        jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) SELECT ?,id,owner_id,project_id,1,'USER','模拟排查',now(),now() FROM dt_conversation WHERE id=?",message,conversation);
        jdbc.update("INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,budget,lease_expires_at,created_at,updated_at,round_number) SELECT ?,id,owner_id,project_id,?,'RUNNING',now(),now(),'{}',now()+interval '5 minutes',now(),now(),1 FROM dt_conversation WHERE id=?",run,message,conversation);
        return run;
    }
    @Test void skillSelectionAndDisableShareTheSameInstallationLock() throws Exception {
        try(var app=app()) {
            var source=app.getBean(HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var actor=legacyAdminFixture(app.getBean(AccountService.class),"skill-admin@example.com",PASSWORD,"运营");
            UUID workspace=jdbc.queryForObject("SELECT id FROM dt_workspace WHERE personal_user_id=?",UUID.class,actor.userId());
            var maintenance=new AdminMaintenance(jdbc,tx);maintenance.platform("test",actor.userId(),true,true,"OPS_REQUEST");maintenance.grant("test",actor.userId(),workspace,"SKILL_MANAGE",true,"OPS_REQUEST");
            var service=app.getBean(AdminService.class);var runtime=app.getBean(io.devicetruth.skill.SkillRuntime.class);var meta=runtime.registry().skills().getFirst();
            service.install(actor.userId(),UUID.randomUUID(),workspace,null,meta.name(),meta.version(),"OPS_REQUEST");
            assertThatThrownBy(()->service.install(actor.userId(),UUID.randomUUID(),workspace,null,meta.name(),meta.version(),"OPS_REQUEST")).hasMessage("INSTALLATION_ALREADY_EXISTS");
            var identity=new io.devicetruth.application.agent.WorkspaceScopeResolver(jdbc).resolve(actor.userId(),workspace).storageIdentity();
            var repository=new io.devicetruth.adapter.persistence.SkillInstallationRepository(jdbc);
            var tools=new TreeSet<>(runtime.serverTools());tools.retainAll(meta.tools());
            var selection=new io.devicetruth.adapter.persistence.SkillInstallationRepository.Selection(meta.name(),meta.version(),meta.devices(),meta.tasks(),tools,"a".repeat(64));
            UUID run=seedRun(jdbc,workspace);var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
            try {
                var choose=pool.submit(()->tx.execute(t->{boolean result=repository.select(identity,run,0,selection);entered.countDown();try{finish.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}return result;}));
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                var disable=pool.submit(()->{try{service.skillStatus(actor.userId(),UUID.randomUUID(),workspace,null,meta.name(),meta.version(),false,0,"OPS_REQUEST");return "SUCCESS";}catch(AdminFailure e){return e.code;}});
                finish.countDown();assertThat(choose.get()).isTrue();assertThat(disable.get()).isEqualTo("SKILL_HAS_UNRESOLVED_RUNS");
                // 选中后进入恢复队列，仍阻止停用。
                jdbc.update("UPDATE dt_agent_run SET status='QUEUED' WHERE id=?",run);
                assertThatThrownBy(()->service.skillStatus(actor.userId(),UUID.randomUUID(),workspace,null,meta.name(),meta.version(),false,0,"OPS_REQUEST")).hasMessage("SKILL_HAS_UNRESOLVED_RUNS");
                jdbc.update("UPDATE dt_agent_run SET status='COMPLETED' WHERE id=?",run);
                UUID later=seedRun(jdbc,workspace);var disabled=new CountDownLatch(1);var commitDisable=new CountDownLatch(1);var selectionStarted=new CountDownLatch(1);
                var disableFirst=pool.submit(()->tx.execute(t->{service.skillStatus(actor.userId(),UUID.randomUUID(),workspace,null,meta.name(),meta.version(),false,0,"OPS_REQUEST");disabled.countDown();try{commitDisable.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}return true;}));
                assertThat(disabled.await(5,TimeUnit.SECONDS)).isTrue();
                var selectLater=pool.submit(()->{selectionStarted.countDown();return tx.execute(t->repository.select(identity,later,0,selection));});
                assertThat(selectionStarted.await(5,TimeUnit.SECONDS)).isTrue();commitDisable.countDown();assertThat((Boolean)disableFirst.get()).isTrue();assertThat((Boolean)selectLater.get()).isFalse();
                repository.installDefault(identity,meta.name(),meta.version(),Set.of("search_knowledge"));
                assertThat(jdbc.queryForObject("SELECT enabled FROM dt_skill_installation WHERE owner_id=? AND project_id=? AND skill_id=? AND version=?",Boolean.class,identity.ownerId(),identity.projectId(),meta.name(),meta.version())).isFalse();
                assertThatThrownBy(()->service.skillStatus(actor.userId(),UUID.randomUUID(),workspace,null,meta.name(),meta.version(),true,0,"OPS_REQUEST")).hasMessage("STATE_VERSION_CONFLICT");
                jdbc.update("INSERT INTO dt_skill_installation(owner_id,project_id,skill_id,version,allowed_tools) VALUES (?,?,?,'9.9.9',?)",identity.ownerId(),identity.projectId(),meta.name(),new String[]{"unregistered_tool"});
                assertThat(service.installations(actor.userId(),workspace,null,Map.of()).items()).anySatisfy(row->{assertThat(row.get("version")).isEqualTo("9.9.9");assertThat(row.get("availability")).isEqualTo("VERSION_MISMATCH");});
                assertThatThrownBy(()->service.skillStatus(actor.userId(),UUID.randomUUID(),workspace,null,meta.name(),"9.9.9",true,0,"OPS_REQUEST")).hasMessage("SKILL_VERSION_UNAVAILABLE");
            } finally {finish.countDown();pool.shutdownNow();}
        }
    }
    @Test void accountEpochRevokesOldCookieAndWaitingRunEvenAfterImmediateRestore() throws Exception {
        try(var app=app()) {
            var source=app.getBean(HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var accounts=app.getBean(AccountService.class);var admin=legacyAdminFixture(accounts,"epoch-admin@example.com",PASSWORD,"运营");var actor=accounts.register("epoch-actor@example.com",PASSWORD,"执行者");
            var maintenance=new AdminMaintenance(jdbc,tx);maintenance.platform("test",admin.userId(),true,true,"OPS_REQUEST");
            var service=app.getBean(AdminService.class);var scope=new io.devicetruth.application.agent.WorkspaceScopeResolver(jdbc).resolve(actor.userId(),null);
            UUID run=seedRun(jdbc,scope.workspaceId());jdbc.update("UPDATE dt_agent_run SET execution_actor_user_id=?,status='WAITING_FOR_USER',pending_question_id=?,question_version=1,pending_question=?::jsonb WHERE id=?",actor.userId(),UUID.randomUUID(),"{\"question\":\"模拟问题\"}",run);
            String root="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();var browser=new Browser(root);browser.login("epoch-actor@example.com");
            service.userStatus(admin.userId(),UUID.randomUUID(),actor.userId(),"DISABLED",0,"OPS_REQUEST");
            assertThat(browser.send("GET","/api/auth/me",null,null).statusCode()).isEqualTo(401);
            service.userStatus(admin.userId(),UUID.randomUUID(),actor.userId(),"ACTIVE",1,"ACCOUNT_RECOVERY");
            var repository=new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc);assertThat(repository.canWrite(repository.persistedScope(run))).isFalse();
            app.getBean(io.devicetruth.application.agent.GeneralAgentService.class).recoverAuthenticatedQueued();
            assertThat(jdbc.queryForObject("SELECT status FROM dt_agent_run WHERE id=?",String.class,run)).isEqualTo("FAILED");
            browser.login("epoch-actor@example.com");assertThat(browser.send("GET","/api/auth/me",null,null).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT status FROM dt_agent_run WHERE id=?",String.class,run)).isEqualTo("FAILED");
        }
    }
    @Test void statusVersionLastAdminConcurrencyAndAuditFailureAreAtomic() throws Exception {
        try(var app=app()) {
            var jdbc=new JdbcTemplate(app.getBean(HikariDataSource.class));var tx=new TransactionTemplate(new DataSourceTransactionManager(app.getBean(HikariDataSource.class)));
            var accounts=app.getBean(AccountService.class);var a=legacyAdminFixture(accounts,"first@example.com",PASSWORD,"甲");var b=legacyAdminFixture(accounts,"second@example.com",PASSWORD,"乙");
            var service=app.getBean(AdminService.class);var maintenance=new AdminMaintenance(jdbc,tx);
            maintenance.platform("test",a.userId(),true,true,"OPS_REQUEST");maintenance.platform("test",b.userId(),true,false,"OPS_REQUEST");
            var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
            try {
                var futures=List.of(pool.submit(()->{barrier.await();try{maintenance.platform("test",a.userId(),false,false,"OPS_REQUEST");return "SUCCESS";}catch(AdminFailure e){return e.code;}}),
                        pool.submit(()->{barrier.await();try{maintenance.platform("test",b.userId(),false,false,"OPS_REQUEST");return "SUCCESS";}catch(AdminFailure e){return e.code;}}));
                assertThat(List.of(futures.get(0).get(),futures.get(1).get())).containsExactlyInAnyOrder("SUCCESS","LAST_PLATFORM_ADMIN");
            } finally {pool.shutdownNow();}
            UUID actor=jdbc.queryForObject("SELECT user_id FROM dt_platform_admin",UUID.class);UUID target=actor.equals(a.userId())?b.userId():a.userId();
            assertThatThrownBy(()->service.userStatus(actor,UUID.randomUUID(),actor,"DISABLED",0,"OPS_REQUEST")).isInstanceOf(AdminFailure.class).hasMessage("LAST_PLATFORM_ADMIN");
            var changed=service.userStatus(actor,UUID.randomUUID(),target,"DISABLED",0,"SECURITY_RESPONSE");assertThat(changed.get("changed")).isEqualTo(true);
            assertThatThrownBy(()->accounts.authenticate(actor.equals(a.userId())?"second@example.com":"first@example.com",PASSWORD)).hasMessageContaining("INVALID_CREDENTIALS");
            assertThatThrownBy(()->service.userStatus(actor,UUID.randomUUID(),target,"ACTIVE",0,"ACCOUNT_RECOVERY")).hasMessage("STATE_VERSION_CONFLICT");
            assertThat(service.userStatus(actor,UUID.randomUUID(),target,"DISABLED",1,"OPS_REQUEST").get("changed")).isEqualTo(false);
            service.userStatus(actor,UUID.randomUUID(),target,"ACTIVE",1,"ACCOUNT_RECOVERY");
            jdbc.execute("CREATE FUNCTION reject_admin_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'SECRET_DB_ERROR_PROBE'; END $$");
            jdbc.execute("CREATE TRIGGER reject_audit BEFORE INSERT ON dt_admin_audit FOR EACH ROW EXECUTE FUNCTION reject_admin_audit()");
            assertThatThrownBy(()->service.createUser(actor,UUID.randomUUID(),"rollback@example.com","回滚",PASSWORD)).hasMessage("ADMIN_AUDIT_UNAVAILABLE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_account WHERE email='rollback@example.com'",Long.class)).isZero();
            assertThatThrownBy(()->service.userStatus(actor,UUID.randomUUID(),target,"DISABLED",2,"OPS_REQUEST")).hasMessage("ADMIN_AUDIT_UNAVAILABLE");
            assertThat(jdbc.queryForObject("SELECT status FROM dt_account WHERE id=?",String.class,target)).isEqualTo("ACTIVE");
        }
    }
}
