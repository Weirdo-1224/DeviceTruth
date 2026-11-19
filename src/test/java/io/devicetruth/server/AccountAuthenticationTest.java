package io.devicetruth.server;

import java.net.*;
import java.net.http.*;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** 继承隔离建库夹具；真实 HTTP/Cookie + JDBC Session，不调用外部模型。 */
class AccountAuthenticationTest extends AccountWorkspaceMigrationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void registrationLoginRotationRefreshLogoutAndCsrf() throws Exception {
        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--devicetruth.auth.cookie-secure=false",
                "--spring.datasource.url=" + base + database, "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password, "--devicetruth.agent.worker-enabled=false",
                "--devicetruth.agent.reasoner=scripted", "--devicetruth.embedding.provider=deterministic")) {
            String root = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var client = HttpClient.newBuilder().cookieHandler(cookies).build();
            assertThat(send(client, root, "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            assertThat(send(client, root, "/api/conversations", null, null).statusCode()).isEqualTo(401);
            assertThat(send(client, root, "/api/health", null, null).statusCode()).isEqualTo(200);
            String body = "{\"email\":\" Person@Example.com \",\"password\":\"long-password-123\",\"displayName\":\"工程师\"}";
            assertThat(send(client, root, "/api/auth/register", body, null).statusCode()).isEqualTo(403);
            String csrf = token(client, root);
            var knownRecovery = send(client, root, "/api/auth/password-reset-requests", "{\"email\":\"person@example.com\"}", csrf);
            var unknownRecovery = send(client, root, "/api/auth/password-reset-requests", "{\"email\":\"unknown@example.com\"}", csrf);
            assertThat(knownRecovery.statusCode()).isEqualTo(503);
            assertThat(knownRecovery.body()).isEqualTo(unknownRecovery.body()).contains("MAIL_UNAVAILABLE");
            var invalidReset = send(client, root, "/api/auth/password-resets",
                    "{\"token\":\"invalid-secret\",\"password\":\"replacement-password-456\",\"confirmPassword\":\"replacement-password-456\"}", csrf);
            assertThat(invalidReset.statusCode()).isEqualTo(400);
            assertThat(invalidReset.body()).doesNotContain("invalid-secret", "replacement-password");
            assertThat(send(client, root, "/api/auth/register", body.replace("long-password-123", "short"), csrf).statusCode()).isEqualTo(400);
            String oldCookie = cookie(cookies);
            var registered = send(client, root, "/api/auth/register", body, csrf);
            assertThat(registered.statusCode()).isEqualTo(200);
            assertThat(registered.body()).contains("person@example.com").doesNotContain("password", "pbkdf2");
            assertThat(cookie(cookies)).isNotEqualTo(oldCookie);
            assertThat(registered.headers().allValues("set-cookie").toString()).contains("HttpOnly", "SameSite=Lax");
            assertThat(send(client, root, "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_account", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM dt_account", String.class)).startsWith("{pbkdf2}").doesNotContain("long-password");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name IS NOT NULL", Long.class)).isEqualTo(1);
            assertThat(send(client, root, "/api/auth/register", body, token(client, root)).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace", Long.class)).isEqualTo(1);
            String sessionBeforeLogout = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("DTSESSION"))
                    .findFirst().orElseThrow().getValue();
            assertThat(send(client, root, "/api/auth/logout", "{}", token(client, root)).statusCode()).isEqualTo(200);
            assertThat(send(client, root, "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            var replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(root + "/api/auth/me"))
                    .header("Cookie", "DTSESSION=" + sessionBeforeLogout).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(replay.statusCode()).isEqualTo(401);
            String login = "{\"email\":\"person@example.com\",\"password\":\"wrong-password-123\"}";
            assertThat(send(client, root, "/api/auth/login", login, token(client, root)).statusCode()).isEqualTo(401);
            assertThat(send(client, root, "/api/auth/login", login.replace("wrong-password-123", "long-password-123"), token(client, root)).statusCode()).isEqualTo(200);
            // 尚未贯通 scope 的资源不允许新账号访问固定身份资料。
            assertThat(send(client, root, "/api/conversations", null, null).statusCode()).isEqualTo(200);
            var limiter = app.getBean(AuthRateLimiter.class);
            for (int i = 0; i < 18; i++) limiter.check("password-reset-request", "127.0.0.1", "limited@example.com");
            var limited = send(client, root, "/api/auth/password-reset-requests", "{\"email\":\"limited@example.com\"}", token(client, root));
            assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(limited.body()).contains("AUTH_RATE_LIMITED").doesNotContain("limited@example.com");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_password_reset", Long.class)).isZero();
            var workspaces = send(client, root, "/api/workspaces", null, null);
            assertThat(workspaces.statusCode()).isEqualTo(200);
            assertThat(workspaces.body()).contains("PERSONAL").doesNotContain("ownerId", "projectId");
            var team = send(client, root, "/api/workspaces", "{\"name\":\"维修团队\"}", token(client, root));
            assertThat(team.statusCode()).isEqualTo(200);
            String teamId = json.readTree(team.body()).get("workspaceId").asText();
            assertThat(send(client, root, "/api/workspaces/" + teamId + "/members", null, null).body()).contains("person@example.com");
            var mismatched = client.send(HttpRequest.newBuilder(URI.create(root + "/api/workspaces/" + teamId + "/members"))
                    .header("X-Workspace-ID", java.util.UUID.randomUUID().toString()).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(mismatched.statusCode()).isEqualTo(404);
            var renamed = client.send(HttpRequest.newBuilder(URI.create(root + "/api/workspaces/" + teamId))
                    .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token(client, root))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"name\":\"团队新名称\"}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(renamed.statusCode()).isEqualTo(200);
            String actor = json.readTree(registered.body()).get("userId").asText();
            var lastAdmin = client.send(HttpRequest.newBuilder(URI.create(root + "/api/workspaces/" + teamId + "/members/" + actor))
                    .header("X-CSRF-TOKEN", token(client, root)).DELETE().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(lastAdmin.statusCode()).isEqualTo(409);
            assertThat(lastAdmin.body()).contains("LAST_WORKSPACE_ADMIN");
            assertThat(send(client, root, "/api/workspaces", "{\"name\":\"伪造团队\",\"role\":\"ADMIN\"}", token(client, root)).statusCode()).isEqualTo(400);
        }
    }
    @Test void healthOnlyStartupHasNoLoginOrSessionInfrastructure() throws Exception {
        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run("--server.port=0")) {
            String root = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
            assertThat(send(HttpClient.newHttpClient(), root, "/api/health", null, null).statusCode()).isEqualTo(200);
            assertThat(app.getBeansOfType(AccountService.class)).isEmpty();
            assertThat(app.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class)).isEmpty();
        }
        assertThat(io.devicetruth.app.DeviceTruthApplication.execute(new String[]{"--help"})).isZero();
    }
    private String token(HttpClient client, String root) throws Exception {
        return json.readTree(send(client, root, "/api/auth/csrf", null, null).body()).get("token").asText();
    }
    private String cookie(CookieManager cookies) { return cookies.getCookieStore().getCookies().toString(); }
    private HttpResponse<String> send(HttpClient client, String root, String path, String body, String csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(root + path));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
