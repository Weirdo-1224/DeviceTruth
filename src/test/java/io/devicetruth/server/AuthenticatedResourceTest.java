package io.devicetruth.server;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import io.devicetruth.adapter.Json;
import io.devicetruth.application.agent.*;
import static org.assertj.core.api.Assertions.*;
class AuthenticatedResourceTest {
    private final AccountWorkspaceMigrationTest fixture=new AccountWorkspaceMigrationTest();
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path filesRoot;
    private ConfigurableApplicationContext app;private String root;private HttpClient a,b;private JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        fixture.createDatabase();app=new SpringApplicationBuilder(DeviceTruthServerApplication.class).run("--spring.profiles.active=product","--server.port=0","--devicetruth.auth.cookie-secure=false","--spring.datasource.url="+fixture.base+fixture.database,"--spring.datasource.username="+fixture.user,"--spring.datasource.password="+fixture.password,"--devicetruth.agent.worker-enabled=false","--devicetruth.agent.reasoner=scripted","--devicetruth.embedding.provider=deterministic","--devicetruth.files.root="+filesRoot);
        root="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();jdbc=new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));a=client();b=client();
        for(var entry:Map.of(a,"a@example.com",b,"b@example.com").entrySet()) assertThat(post(entry.getKey(),"/api/auth/register",Map.of("email",entry.getValue(),"password","long-password-123","displayName","工程师")).statusCode()).isEqualTo(200);
    }
    @AfterEach void cleanup() throws Exception {if(app!=null)app.close();fixture.dropDatabase();}
    private HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
    private HttpResponse<String> get(HttpClient c,String path)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(root+path)).build(),HttpResponse.BodyHandlers.ofString());}
    private String csrf(HttpClient c)throws Exception{return Json.MAPPER.readTree(get(c,"/api/auth/csrf").body()).get("token").asText();}
    private HttpResponse<String> post(HttpClient c,String path,Object body)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(root+path)).header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf(c)).header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build(),HttpResponse.BodyHandlers.ofString());}
    @Test void everyResourceRouteRejectsOtherAccountAndReadonlyWrites() throws Exception {
        String conversation=Json.MAPPER.readTree(post(a,"/api/conversations",Map.of()).body()).get("conversationId").asText();
        String run=Json.MAPPER.readTree(post(a,"/api/conversations/"+conversation+"/messages",Map.of("schemaVersion",1,"userMessage","排查故障")).body()).get("runId").asText();
        for(String path:List.of("/api/conversations/"+conversation,"/api/conversations/"+conversation+"/messages","/api/runs/"+run,"/api/runs/"+run+"/events","/api/runs/"+run+"/references","/api/runs/"+run+"/tool-events","/api/runs/"+run+"/model-events","/api/runs/"+run+"/skill")) assertThat(get(b,path).statusCode()).as(path).isEqualTo(404);
        assertThat(get(b,"/api/conversations").body()).isEqualTo("[]");
        assertThat(post(b,"/api/runs/"+run+"/cancel",Map.of()).statusCode()).isEqualTo(404);
        UUID user=UUID.fromString(Json.MAPPER.readTree(get(a,"/api/auth/me").body()).get("userId").asText());
        var scope=new WorkspaceScopeResolver(jdbc).resolve(user,null);String owner=scope.storageIdentity().ownerId(),project=scope.storageIdentity().projectId();
        UUID file=UUID.randomUUID();jdbc.update("INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,parse_status,created_at) VALUES (?,?,?,?,'secret.md','text/markdown',?,10,'PARSED',now())",file,owner,project,"test/"+file,"a".repeat(64));
        assertThat(get(b,"/api/files/"+file).statusCode()).isEqualTo(404);assertThat(post(b,"/api/files/"+file+"/retry",Map.of()).statusCode()).isEqualTo(404);
        UUID doc=UUID.randomUUID();jdbc.update("INSERT INTO dt_knowledge_document(id,owner_id,project_id,title,domain,doc_version,source_file_id,checksum,ingest_status,embedding_profile,embedding_model,embedding_dimension,chunking_profile,chunk_count,data_nature,created_at) VALUES (?,?,?,'private','general','1',?,?,'READY','test','test',1024,'test',1,'SIMULATED',now())",doc,owner,project,file,"b".repeat(64));
        assertThat(get(b,"/api/knowledge/documents/"+doc).statusCode()).isEqualTo(404);
        assertThat(get(b,"/api/knowledge/references/"+UUID.randomUUID()+"?runId="+run).statusCode()).isEqualTo(404);
        assertThat(post(b,"/api/knowledge/search",Map.of("runId",run,"query","private")).statusCode()).isEqualTo(404);
        jdbc.update("UPDATE dt_workspace_member SET role='VIEWER' WHERE workspace_id=?",scope.workspaceId());
        assertThat(post(a,"/api/conversations",Map.of()).statusCode()).isEqualTo(403);
        assertThat(post(a,"/api/conversations/"+conversation+"/messages",Map.of("schemaVersion",1,"userMessage","禁止写入")).statusCode()).isEqualTo(403);
        assertThat(post(a,"/api/knowledge/search",Map.of("runId",run,"query","private")).statusCode()).isEqualTo(403);
    }
    @Test void oldStreamStopsAfterLogoutBeforeNextBusinessBatch() throws Exception { verifyStream(false); }
    @Test void invitedNewAccountRegistersThenAcceptsThroughRealHttpWithoutReplacingPersonalSpace() throws Exception {
        UUID actor=UUID.fromString(Json.MAPPER.readTree(get(a,"/api/auth/me").body()).get("userId").asText());
        UUID team=app.getBean(WorkspaceService.class).createTeam(actor,"邀请团队").workspaceId();
        String[] token=new String[1];
        var source=app.getBean(com.zaxxer.hikari.HikariDataSource.class);
        var service=new TeamInvitationService(jdbc,new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source)),app.getBean(WorkspaceService.class),new TeamInvitationService.Mail(){public boolean available(){return true;}public void send(String email,String value){token[0]=value;}});
        service.create(actor,team,"invited@example.com",AccessScope.Role.COLLABORATOR);
        assertThat(post(a,"/api/invitations/accept",Map.of("token",token[0])).statusCode()).isEqualTo(403);
        var newcomer=client();
        assertThat(post(newcomer,"/api/auth/register",Map.of("email","invited@example.com","password","long-password-123","displayName","受邀人")).statusCode()).isEqualTo(200);
        assertThat(Json.MAPPER.readTree(get(newcomer,"/api/workspaces").body()).size()).isEqualTo(1);
        var accepted=post(newcomer,"/api/invitations/accept",Map.of("token",token[0]));assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(Json.MAPPER.readTree(get(newcomer,"/api/workspaces").body()).size()).isEqualTo(2);
        assertThat(post(newcomer,"/api/invitations/accept",Map.of("token",token[0])).statusCode()).isEqualTo(409);
        assertThat(get(a,"/api/workspaces/"+team+"/invitations").body()).contains("ACCEPTED").doesNotContain(token[0],"token_hash");
    }
    @Test void oldStreamStopsAfterMembershipRemovalBeforeNextBusinessBatch() throws Exception { verifyStream(true); }
    private void verifyStream(boolean removed) throws Exception {
        String conversation=Json.MAPPER.readTree(post(a,"/api/conversations",Map.of()).body()).get("conversationId").asText();
        String run=Json.MAPPER.readTree(post(a,"/api/conversations/"+conversation+"/messages",Map.of("schemaVersion",1,"userMessage","等待")).body()).get("runId").asText();
        var stream=a.send(HttpRequest.newBuilder(URI.create(root+"/api/runs/"+run+"/stream")).build(),HttpResponse.BodyHandlers.ofInputStream());assertThat(stream.statusCode()).isEqualTo(200);
        var reader=new java.io.BufferedReader(new java.io.InputStreamReader(stream.body()));
        // 先读取已授权历史，退出后再制造事件以唤醒既有连接。
        String line;while((line=reader.readLine())!=null&&!line.isBlank()){}
        var scopes=new WorkspaceScopeResolver(jdbc);UUID actor=jdbc.queryForObject("SELECT id FROM dt_account WHERE email='a@example.com'",UUID.class);var scope=scopes.resolve(actor,null);
        if(removed) jdbc.update("DELETE FROM dt_workspace_member WHERE workspace_id=? AND user_id=?",scope.workspaceId(),actor);
        else assertThat(post(a,"/api/auth/logout",Map.of()).statusCode()).isEqualTo(200);
        new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc).appendEvent(scope.storageIdentity(),UUID.fromString(run),UUID.fromString(conversation),"RUN_PHASE",Map.of("phase","REVOKED_SECRET"),java.time.Instant.now());
        app.getBean(RunEventStreamService.class).publish(UUID.fromString(run));
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var remainder=executor.submit(()->{StringBuilder text=new StringBuilder();for(String value;(value=reader.readLine())!=null;)text.append(value);return text.toString();});
            assertThat(remainder.get(5,java.util.concurrent.TimeUnit.SECONDS)).doesNotContain("REVOKED_SECRET");
        }finally{stream.body().close();}
    }    @Test void discussionHttpUsesRealAuthorAndExplicitQuoteWithoutImplicitRun() throws Exception {
        String id=Json.MAPPER.readTree(post(a,"/api/conversations",Map.of()).body()).path("conversationId").asText();
        String path="/api/conversations/"+id;
        var response=post(a,path+"/discussions",Map.of("content","怀疑信号，尚未检查"));
        assertThat(response.statusCode()).isEqualTo(201);
        var discussion=Json.MAPPER.readTree(response.body());
        assertThat(discussion.path("authorUserId").asText()).isEqualTo(Json.MAPPER.readTree(get(a,"/api/auth/me").body()).path("userId").asText());
        assertThat(Json.MAPPER.readTree(get(a,path).body()).path("runs").size()).isZero();
        assertThat(get(b,path+"/discussions").statusCode()).isEqualTo(404);
        assertThat(post(a,path+"/discussions",Map.of("content","伪造","authorName","其他人")).statusCode()).isEqualTo(400);
        assertThat(get(a,path+"/discussions?limit=1").body()).contains("怀疑信号");
        var quote=Map.of("discussionId",discussion.path("discussionId").asText(),"version",1);
        var run=post(a,path+"/messages",Map.of("schemaVersion",1,"userMessage","请分析","contextRefs",List.of(),"discussionQuotes",List.of(quote)));
        assertThat(run.statusCode()).isEqualTo(202);
        assertThat(Json.MAPPER.readTree(get(a,path).body()).path("messages").get(0).path("discussionQuotes").get(0).path("dataNature").asText()).isEqualTo("USER_REPORTED_UNVERIFIED");
    }
    @Test void investigationAttachmentHttpKeepsOriginalAndRequiresExplicitPublication() throws Exception {
        String id=Json.MAPPER.readTree(post(a,"/api/conversations",Map.of()).body()).path("conversationId").asText();
        String path="/api/conversations/"+id;
        String boundary="dt-test-boundary";
        String multipart="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"note.txt\"\r\nContent-Type: text/plain\r\n\r\nABC-789 未现场核实\r\n--"+boundary+"--\r\n";
        var request=HttpRequest.newBuilder(URI.create(root+path+"/attachments")).header("X-CSRF-TOKEN",csrf(a)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(multipart)).build();
        var uploaded=a.send(request,HttpResponse.BodyHandlers.ofString());assertThat(uploaded.statusCode()).as(uploaded.body()).isEqualTo(202);
        String file=Json.MAPPER.readTree(uploaded.body()).path("fileId").asText();
        for(int i=0;i<100&&!Json.MAPPER.readTree(get(a,"/api/files/"+file).body()).path("parseStatus").asText().equals("PARSED");i++)Thread.sleep(30);
        assertThat(get(a,path+"/attachments").body()).contains(file,"CONVERSATION_ATTACHMENT");
        assertThat(get(b,"/api/files/"+file+"/content").statusCode()).isEqualTo(404);
        assertThat(get(a,"/api/files/"+file+"/content").body()).contains("未现场核实");
        assertThat(Json.MAPPER.readTree(get(a,"/api/knowledge/documents").body()).size()).isZero();
        assertThat(post(a,"/api/files/"+file+"/publications",Map.of("title","检查记录","applicability","ABC-789，未现场核实")).statusCode()).isEqualTo(200);
        assertThat(Json.MAPPER.readTree(get(a,"/api/knowledge/documents").body()).size()).isEqualTo(1);
        assertThat(Json.MAPPER.readTree(get(a,path).body()).path("runs").size()).isZero();
        UUID actor=UUID.fromString(Json.MAPPER.readTree(get(a,"/api/auth/me").body()).path("userId").asText());
        jdbc.update("UPDATE dt_workspace_member SET role='VIEWER' WHERE user_id=?",actor);
        assertThat(post(a,"/api/files/"+file+"/publications",Map.of("title","不能发布","applicability","设备")).statusCode()).isEqualTo(403);
    }

}
