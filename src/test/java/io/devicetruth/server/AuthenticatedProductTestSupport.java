package io.devicetruth.server;

import com.zaxxer.hikari.HikariDataSource;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import io.devicetruth.adapter.Json;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;

/** 将原固定身份 HTTP 夹具迁移到真实认证；只允许随机隔离库，绝不用于运行库认领。 */
public final class AuthenticatedProductTestSupport {
    private record Session(HttpClient client,CookieManager cookies,JdbcTemplate jdbc,ServerIdentity identity) {}
    private static final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private static final String PASSWORD="isolated-fixture-password-123";
    public static TestRestTemplate client(ConfigurableApplicationContext... apps) {
        for(var app:apps) register(app);
        var rest=new TestRestTemplate();
        rest.getRestTemplate().getInterceptors().add((request,body,execution)->{
            String root=origin(request.getURI());Session session=sessions.get(root);
            if(session==null)throw new IllegalStateException("Unregistered isolated test server");
            // 原内部入口生成的历史夹具显式认领到测试账号，作者仍为未知。
            session.jdbc().update("UPDATE dt_conversation c SET workspace_id=w.id FROM dt_workspace w WHERE c.workspace_id IS NULL AND c.owner_id=? AND c.project_id=? AND w.owner_id=c.owner_id AND w.project_id=c.project_id",session.identity().ownerId(),session.identity().projectId());
            if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod().name())) request.getHeaders().set("X-CSRF-TOKEN",token(root,session));
            request.getHeaders().set("Cookie",cookie(request.getURI(),session));
            var response=execution.execute(request,body);
            session.cookies().put(request.getURI(),response.getHeaders());return response;
        });return rest;
    }
    public static synchronized void register(ConfigurableApplicationContext app) {
        String root="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
        if(sessions.containsKey(root))return;
        var source=app.getBean(HikariDataSource.class);
        if(!source.getJdbcUrl().contains("/devicetruth_test_"))throw new IllegalStateException("Isolated test database required");
        var jdbc=new JdbcTemplate(source);var identity=app.getBean(ServerIdentity.class);
        var emails=jdbc.queryForList("SELECT a.email FROM dt_workspace w JOIN dt_account a ON a.id=w.personal_user_id WHERE w.owner_id=? AND w.project_id=?",String.class,identity.ownerId(),identity.projectId());
        String email;
        if(emails.isEmpty()) {
            email=UUID.randomUUID()+"@fixture.example.com";
            var account=app.getBean(AccountService.class).register(email,PASSWORD,"测试账号");
            jdbc.update("UPDATE dt_workspace SET owner_id=?,project_id=? WHERE personal_user_id=?",identity.ownerId(),identity.projectId(),account.userId());
        }else email=emails.getFirst();
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var http=HttpClient.newBuilder().cookieHandler(cookies).build();
        var session=new Session(http,cookies,jdbc,identity);sessions.put(root,session);
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create(root+"/api/auth/login")).header("Content-Type","application/json").header("X-CSRF-TOKEN",token(root,session)).POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of("email",email,"password",PASSWORD)))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException("Fixture login failed: "+response.statusCode());
        }catch(Exception e){throw new IllegalStateException("Fixture login failed",e);}
    }
    public static String cookie(String url) {
        var uri=URI.create(url);var session=sessions.get(origin(uri));
        if(session==null)throw new IllegalStateException("Unregistered stream server");return cookie(uri,session);
    }
    private static String cookie(URI uri,Session session) {try{return String.join("; ",session.cookies().get(uri,Map.of()).getOrDefault("Cookie",List.of()));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String token(String root,Session session) {
        try{return Json.MAPPER.readTree(session.client().send(HttpRequest.newBuilder(URI.create(root+"/api/auth/csrf")).build(),HttpResponse.BodyHandlers.ofString()).body()).get("token").asText();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static String origin(URI uri){return uri.getScheme()+"://"+uri.getAuthority();}
}
