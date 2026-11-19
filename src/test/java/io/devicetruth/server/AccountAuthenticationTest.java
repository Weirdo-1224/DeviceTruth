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
            String oldCookie = cookie(cookies);
            var registered = send(client, root, "/api/auth/register", body, csrf);
            assertThat(registered.statusCode()).isEqualTo(200);
            assertThat(registered.body()).contains("person@example.com").doesNotContain("password", "pbkdf2");
            assertThat(cookie(cookies)).isNotEqualTo(oldCookie);
            assertThat(send(client, root, "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_account", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM dt_account", String.class)).startsWith("{pbkdf2}").doesNotContain("long-password");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name IS NOT NULL", Long.class)).isEqualTo(1);
            assertThat(send(client, root, "/api/auth/register", body, token(client, root)).statusCode()).isEqualTo(409);
            assertThat(send(client, root, "/api/auth/logout", "{}", token(client, root)).statusCode()).isEqualTo(200);
            assertThat(send(client, root, "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            String login = "{\"email\":\"person@example.com\",\"password\":\"wrong-password-123\"}";
            assertThat(send(client, root, "/api/auth/login", login, token(client, root)).statusCode()).isEqualTo(401);
            assertThat(send(client, root, "/api/auth/login", login.replace("wrong-password-123", "long-password-123"), token(client, root)).statusCode()).isEqualTo(200);
            // 尚未贯通 scope 的资源不允许新账号访问固定身份资料。
            assertThat(send(client, root, "/api/conversations", null, null).statusCode()).isEqualTo(403);
        }
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
