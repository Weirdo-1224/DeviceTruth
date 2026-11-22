package io.devicetruth.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.devicetruth.admin.application.AdminMaintenance;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class RealmAuthenticationTest extends AccountWorkspaceMigrationTest {
    private static final String PASSWORD = "isolated-password-123";
    private final ObjectMapper json = new ObjectMapper();

    @Test void twoRealmsKeepIdentityCsrfLogoutAndCookieReplayIndependent() throws Exception {
        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--devicetruth.auth.cookie-secure=false",
                "--spring.datasource.url="+base+database, "--spring.datasource.username="+user,
                "--spring.datasource.password="+password, "--devicetruth.agent.worker-enabled=false",
                "--devicetruth.agent.reasoner=scripted", "--devicetruth.embedding.provider=deterministic")) {
            var source = app.getBean(com.zaxxer.hikari.HikariDataSource.class);
            var jdbc = new JdbcTemplate(source);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
            var accounts = app.getBean(AccountService.class);
            var business = accounts.register("user@example.com",PASSWORD,"业务");
            var maintenance = new AdminMaintenance(jdbc,tx);
            var admin = maintenance.createAdmin("test","admin@example.com","管理",PASSWORD);
            maintenance.platform("test",admin.userId(),true,true,"OPS_REQUEST");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace_member WHERE user_id=?",Long.class,admin.userId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace WHERE personal_user_id=?",Long.class,admin.userId())).isZero();
            assertThatThrownBy(()->maintenance.createAdmin("test","admin@example.com","重复",PASSWORD))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(jdbc.queryForObject("SELECT after_state::text FROM dt_admin_audit WHERE action='ADMIN_CREATE'",String.class))
                    .contains("ADMIN").doesNotContain(PASSWORD,"password");
            assertThatThrownBy(()->accounts.authenticate("admin@example.com",PASSWORD)).isInstanceOf(AccountService.RealmMismatch.class);
            assertThatThrownBy(()->accounts.authenticate("user@example.com",PASSWORD,LoginRealm.ADMIN)).isInstanceOf(AccountService.RealmMismatch.class);
            String root="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
            var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
            var client=HttpClient.newBuilder().cookieHandler(cookies).build();
            String userToken=token(client,root,false), adminToken=token(client,root,true);
            assertThat(userToken).isNotEqualTo(adminToken);
            String anonymousUserId=cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("DTSESSION")).findFirst().orElseThrow().getValue();
            assertThat(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(root+"/api/admin/auth/csrf"))
                    .header("Cookie","DTADMINSESSION="+anonymousUserId).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(post(client,root,"/api/admin/auth/login",Map.of("email","admin@example.com","password",PASSWORD),userToken).statusCode()).isEqualTo(403);
            assertThat(post(client,root,"/api/admin/auth/login",Map.of("email","user@example.com","password",PASSWORD),adminToken).statusCode()).isEqualTo(409);
            assertThat(post(client,root,"/api/admin/auth/login",Map.of("email","user@example.com","password","wrong-password-123"),adminToken).statusCode()).isEqualTo(401);
            assertThat(post(client,root,"/api/auth/login",Map.of("email","user@example.com","password",PASSWORD),userToken).statusCode()).isEqualTo(200);
            jdbc.update("DELETE FROM spring_session_attributes WHERE attribute_name=? AND session_primary_id IN (SELECT primary_id FROM spring_session WHERE principal_name=?)",
                    LoginRealm.SESSION_ATTRIBUTE,business.userId().toString());
            assertThat(get(client,root,"/api/auth/me").statusCode()).isEqualTo(200);
            assertThat(get(client,root,"/api/admin/access").statusCode()).isEqualTo(401);
            assertThat(post(client,root,"/api/admin/auth/login",Map.of("email","admin@example.com","password",PASSWORD),adminToken).statusCode()).isEqualTo(200);
            assertThat(get(client,root,"/api/auth/me").body()).contains(business.userId().toString());
            assertThat(get(client,root,"/api/admin/auth/me").body()).contains(admin.userId().toString());
            assertThat(get(client,root,"/api/admin/access").statusCode()).isEqualTo(200);
            String userId=cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("DTSESSION")).findFirst().orElseThrow().getValue();
            String adminId=cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("DTADMINSESSION")).findFirst().orElseThrow().getValue();
            var plain=HttpClient.newHttpClient();
            assertThat(plain.send(HttpRequest.newBuilder(URI.create(root+"/api/admin/access")).header("Cookie","DTADMINSESSION="+userId).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(plain.send(HttpRequest.newBuilder(URI.create(root+"/api/auth/me")).header("Cookie","DTSESSION="+adminId).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            // 与两端认证状态无关，匿名 CSRF 会话也不能更名跨域复用。
            assertThat(post(client,root,"/api/admin/auth/logout",Map.of(),token(client,root,false)).statusCode()).isEqualTo(403);
            assertThat(post(client,root,"/api/admin/auth/logout",Map.of(),token(client,root,true)).statusCode()).isEqualTo(200);
            assertThat(get(client,root,"/api/admin/access").statusCode()).isEqualTo(401);
            assertThat(get(client,root,"/api/auth/me").body()).contains(business.userId().toString());
            assertThat(post(client,root,"/api/admin/auth/login",Map.of("email","admin@example.com","password",PASSWORD),token(client,root,true)).statusCode()).isEqualTo(200);
            assertThat(post(client,root,"/api/auth/logout",Map.of(),token(client,root,false)).statusCode()).isEqualTo(200);
            assertThat(get(client,root,"/api/admin/access").statusCode()).isEqualTo(200);
            jdbc.update("DELETE FROM dt_platform_admin WHERE user_id=?",admin.userId());
            assertThat(get(client,root,"/api/admin/access").statusCode()).isEqualTo(403);
        }
    }

    @Test void v29UpgradePreservesUserAndPasswordAndDoesNotPromoteLegacyRole() {
        var ds=new DriverManagerDataSource(base+database,user,password);
        Flyway.configure().dataSource(ds).target("29").load().migrate();
        var jdbc=new JdbcTemplate(ds); UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name) VALUES (?,'legacy@example.com','original-hash','原账号')",id);
        jdbc.update("INSERT INTO dt_platform_admin VALUES (?)",id);
        UUID workspace=UUID.randomUUID(), conversation=UUID.randomUUID(), message=UUID.randomUUID(), run=UUID.randomUUID(), reference=UUID.randomUUID();
        jdbc.update("INSERT INTO dt_workspace(id,kind,name,personal_user_id,owner_id,project_id) VALUES (?,'PERSONAL','原空间',?,'original','project')",workspace,id);
        jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'ADMIN')",workspace,id);
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,workspace_id,created_at,updated_at) VALUES (?,'original','project',?,now(),now())",conversation,workspace);
        jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) VALUES (?,?,'original','project',1,'USER','原问题',now(),now())",message,conversation);
        jdbc.update("INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,budget,created_at,updated_at,round_number) VALUES (?,?,'original','project',?,'COMPLETED',now(),now(),'{}',now(),now(),1)",run,conversation,message);
        jdbc.update("INSERT INTO dt_evidence_reference(id,run_id,conversation_id,owner_id,project_id,source_type,source_id,source_version,retrieved_at,data_nature) VALUES (?,?,?,'original','project','USER_STATEMENT','old-source','1',now(),'SIMULATED')",reference,run,conversation);
        var original=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("dt_workspace_member","dt_conversation","dt_agent_run","dt_evidence_reference"))
            original.put(table,jdbc.queryForList("SELECT * FROM "+table));
        Flyway.configure().dataSource(ds).load().migrate();
        assertThat(jdbc.queryForMap("SELECT account_type,password_hash FROM dt_account WHERE id=?",id))
                .containsEntry("account_type","USER").containsEntry("password_hash","original-hash");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_platform_admin WHERE user_id=?",Long.class,id)).isEqualTo(1);
        original.forEach((table,rows)->assertThat(jdbc.queryForList("SELECT * FROM "+table)).isEqualTo(rows));
    }
    private String token(HttpClient c,String root,boolean admin) throws Exception {
        return json.readTree(get(c,root,admin?"/api/admin/auth/csrf":"/api/auth/csrf").body()).path("token").asText();
    }
    private HttpResponse<String> get(HttpClient c,String root,String path) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(root+path)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient c,String root,String path,Map<String,?> body,String csrf) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(root+path)).header("X-CSRF-TOKEN",csrf).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
}
