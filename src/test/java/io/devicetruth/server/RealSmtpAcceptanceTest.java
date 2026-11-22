package io.devicetruth.server;

import io.devicetruth.adapter.Json;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.*;

class RealSmtpAcceptanceTest {
    private static final String STORE_PASSWORD = "isolated-smtp-keystore";
    private static final String PASSWORD = "isolated-original-password";
    private static final String NEW_PASSWORD = "isolated-replacement-password";
    private static final Path OUT = Path.of("outputs/validation/workbench-group8");
    @TempDir Path temporary;

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class RealMail {
        private static CapturingJavaMailSender latest;
        private static final java.util.Properties LOCAL = loadLocal();
        @Bean @Primary PasswordRecoveryService.ResetMailSender capturedRealSmtp(Environment environment) {
            var sender = new CapturingJavaMailSender();
            latest = sender;
            sender.setHost(setting(environment, "devicetruth.mail.host", "DEVICETRUTH_SMTP_HOST"));
            sender.setPort(Integer.parseInt(setting(environment, "devicetruth.mail.port", "DEVICETRUTH_SMTP_PORT")));
            sender.setUsername(setting(environment, "devicetruth.mail.username", "DEVICETRUTH_SMTP_USERNAME"));
            sender.setPassword(setting(environment, "devicetruth.mail.password", "DEVICETRUTH_SMTP_PASSWORD"));
            sender.setDefaultEncoding("UTF-8");
            var properties = sender.getJavaMailProperties();
            properties.setProperty("mail.smtp.auth", Boolean.toString(!sender.getUsername().isBlank()));
            properties.setProperty("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.starttls.required", "true");
            properties.setProperty("mail.debug", "false");
            for (String timeout : List.of("connectiontimeout", "timeout", "writetimeout")) {
                String envName = switch (timeout) {
                    case "connectiontimeout" -> "DEVICETRUTH_SMTP_CONNECT_TIMEOUT_MS";
                    case "timeout" -> "DEVICETRUTH_SMTP_READ_TIMEOUT_MS";
                    default -> "DEVICETRUTH_SMTP_WRITE_TIMEOUT_MS";
                };
                String configured = setting(environment, "devicetruth.mail." + timeout + "-ms", envName);
                int value = configured.isBlank() ? 5000 : Integer.parseInt(configured);
                properties.setProperty("mail.smtp." + timeout, Integer.toString(value));
            }
            return new SmtpResetMailSender(sender, setting(environment, "devicetruth.mail.from", "DEVICETRUTH_SMTP_FROM"),
                    setting(environment, "devicetruth.mail.public-url", "DEVICETRUTH_PUBLIC_URL"));
        }
        static String setting(Environment environment, String key, String envName) {
            String value = environment.getProperty(key);
            if (value == null || value.isBlank()) value = LOCAL.getProperty(key);
            if (value == null || value.isBlank()) value = LOCAL.getProperty(envName);
            return value == null ? "" : value.trim();
        }
        private static java.util.Properties loadLocal() {
            var properties = new java.util.Properties();
            for (Path path : List.of(Path.of(".env"), Path.of("devicetruth.local.properties"))) {
                if (Files.isRegularFile(path)) try (var input = Files.newInputStream(path)) { properties.load(input); }
                catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
            }
            return properties;
        }
    }

    static final class CapturingJavaMailSender extends JavaMailSenderImpl {
        private final List<String> invitationTokens = new ArrayList<>();
        private final List<String> resetTokens = new ArrayList<>();
        private int accepted;
        @Override public void send(SimpleMailMessage message) {
            super.send(message);
            accepted++;
            String text = message.getText();
            if (text.contains("#invitation-token=")) invitationTokens.add(text.substring(text.lastIndexOf("#invitation-token=") + 18).trim());
            if (text.contains("#reset-token=")) resetTokens.add(text.substring(text.lastIndexOf("#reset-token=") + 13).trim());
        }
    }

    @Test void realSmtpAndIsolatedHttpsProtocols() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.real-smtp"));
        var fixture = new AccountWorkspaceMigrationTest();
        fixture.createDatabase();
        Path certificate = certificate();
        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class, RealMail.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.config.import=optional:file:./.env",
                "--spring.datasource.url=" + fixture.base + fixture.database, "--spring.datasource.username=" + fixture.user,
                "--spring.datasource.password=" + fixture.password, "--devicetruth.auth.cookie-secure=true",
                "--devicetruth.auth.rate-limit=200", "--devicetruth.agent.worker-enabled=false",
                "--devicetruth.agent.reasoner=scripted", "--devicetruth.embedding.provider=deterministic",
                "--devicetruth.files.root=" + temporary.resolve("files"),
                "--devicetruth.mail.public-url=https://isolated.invalid/",
                "--server.ssl.enabled=true", "--server.ssl.key-store=" + certificate,
                "--server.ssl.key-store-password=" + STORE_PASSWORD, "--server.ssl.key-store-type=PKCS12")) {
            var mail = RealMail.latest;
            String recipient = RealMail.setting(app.getEnvironment(), "devicetruth.test.mail.recipient", "DEVICETRUTH_TEST_MAIL_RECIPIENT");
            if (recipient.isBlank()) recipient = RealMail.setting(app.getEnvironment(), "devicetruth.mail.username", "DEVICETRUTH_SMTP_USERNAME");
            assertThat(recipient).as("真实收件测试默认使用已配置 SMTP 账号").contains("@");
            assertThat(app.getBean(PasswordRecoveryService.ResetMailSender.class).available()).isTrue();
            String root = "https://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
            var admin = session();
            var registeredAdmin = register(admin, root, "admin@example.com", "管理员");
            assertThat(registeredAdmin.headers().allValues("set-cookie").toString()).contains("Secure", "HttpOnly", "SameSite=Lax");
            var team = send(admin.client, root, "/api/workspaces", Json.write(Map.of("name", "SMTP隔离验收团队")),
                    csrf(admin.client, root), "POST");
            assertThat(team.statusCode()).isEqualTo(200);
            String workspace = Json.MAPPER.readTree(team.body()).path("workspaceId").asText();
            var invitation = send(admin.client, root, "/api/workspaces/" + workspace + "/invitations",
                    Json.write(Map.of("email", recipient, "role", "COLLABORATOR")), csrf(admin.client, root), "POST");
            assertThat(invitation.statusCode()).as(invitation.body()).isEqualTo(200);
            String invitationId = Json.MAPPER.readTree(invitation.body()).path("invitationId").asText();
            assertThat(Json.MAPPER.readTree(invitation.body()).path("deliveryStatus").asText()).isEqualTo("ACCEPTED_BY_PROVIDER");
            String firstToken = mail.invitationTokens.getLast();
            var resent = send(admin.client, root, "/api/workspaces/" + workspace + "/invitations/" + invitationId + "/resend",
                    "{}", csrf(admin.client, root), "POST");
            assertThat(resent.statusCode()).as(resent.body()).isEqualTo(200);
            String token = mail.invitationTokens.getLast();
            assertThat(token).isNotEqualTo(firstToken);
            assertThat(resent.body()).doesNotContain(token, firstToken);

            var wrong = session();
            register(wrong, root, "wrong@example.com", "错误账号");
            assertThat(send(wrong.client, root, "/api/invitations/accept", Json.write(Map.of("token", token)),
                    csrf(wrong.client, root), "POST").statusCode()).isEqualTo(403);
            var invited = session();
            register(invited, root, recipient, "受邀成员");
            var accepted = send(invited.client, root, "/api/invitations/accept", Json.write(Map.of("token", token)),
                    csrf(invited.client, root), "POST");
            assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
            assertThat(send(invited.client, root, "/api/invitations/accept", Json.write(Map.of("token", token)),
                    csrf(invited.client, root), "POST").statusCode()).isEqualTo(409);

            var revoked = send(admin.client, root, "/api/workspaces/" + workspace + "/invitations",
                    Json.write(Map.of("email", recipient, "role", "VIEWER")), csrf(admin.client, root), "POST");
            String revokedId = Json.MAPPER.readTree(revoked.body()).path("invitationId").asText();
            String revokedToken = mail.invitationTokens.getLast();
            assertThat(send(admin.client, root, "/api/workspaces/" + workspace + "/invitations/" + revokedId + "/revoke",
                    "{}", csrf(admin.client, root), "POST").statusCode()).isEqualTo(200);
            assertThat(send(invited.client, root, "/api/invitations/accept", Json.write(Map.of("token", revokedToken)),
                    csrf(invited.client, root), "POST").statusCode()).isEqualTo(409);
            var expired = send(admin.client, root, "/api/workspaces/" + workspace + "/invitations",
                    Json.write(Map.of("email", recipient, "role", "VIEWER")), csrf(admin.client, root), "POST");
            String expiredId = Json.MAPPER.readTree(expired.body()).path("invitationId").asText();
            String expiredToken = mail.invitationTokens.getLast();
            var jdbc = new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
            jdbc.update("UPDATE dt_team_invitation SET expires_at=now()-interval '1 second' WHERE id=?", UUID.fromString(expiredId));
            assertThat(send(invited.client, root, "/api/invitations/accept", Json.write(Map.of("token", expiredToken)),
                    csrf(invited.client, root), "POST").statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT role FROM dt_workspace_member WHERE workspace_id=? AND user_id=(SELECT id FROM dt_account WHERE email=?)",
                    String.class, UUID.fromString(workspace), recipient)).isEqualTo("COLLABORATOR");

            var resetOne = send(invited.client, root, "/api/auth/password-reset-requests", Json.write(Map.of("email", recipient)),
                    csrf(invited.client, root), "POST");
            assertThat(resetOne.statusCode()).isEqualTo(200);
            String firstReset = mail.resetTokens.getLast();
            var resetTwo = send(invited.client, root, "/api/auth/password-reset-requests", Json.write(Map.of("email", recipient)),
                    csrf(invited.client, root), "POST");
            assertThat(resetTwo.statusCode()).isEqualTo(200);
            String reset = mail.resetTokens.getLast();
            assertThat(reset).isNotEqualTo(firstReset);
            assertThat(resetTwo.body()).doesNotContain(reset, firstReset, PASSWORD, NEW_PASSWORD);
            assertThat(send(invited.client, root, "/api/auth/password-resets", Json.write(Map.of(
                    "token", reset, "password", NEW_PASSWORD, "confirmPassword", PASSWORD)), csrf(invited.client, root), "POST").statusCode())
                    .isEqualTo(400);
            var resetDone = send(invited.client, root, "/api/auth/password-resets", Json.write(Map.of(
                    "token", reset, "password", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)), csrf(invited.client, root), "POST");
            assertThat(resetDone.statusCode()).as(resetDone.body()).isEqualTo(200);
            assertThat(send(invited.client, root, "/api/auth/me", null, null, "GET").statusCode()).isEqualTo(401);
            assertThat(send(invited.client, root, "/api/auth/password-resets", Json.write(Map.of(
                    "token", reset, "password", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)), null, "POST").statusCode())
                    .isIn(400, 403);
            var fresh = session();
            assertThat(send(fresh.client, root, "/api/auth/login", Json.write(Map.of("email", recipient, "password", PASSWORD)),
                    csrf(fresh.client, root), "POST").statusCode()).isEqualTo(401);
            assertThat(send(fresh.client, root, "/api/auth/login", Json.write(Map.of("email", recipient, "password", NEW_PASSWORD)),
                    csrf(fresh.client, root), "POST").statusCode()).isEqualTo(200);
            assertThat(send(fresh.client, root, "/api/workspaces", Json.write(Map.of("name", "无CSRF")), null, "POST").statusCode())
                    .isEqualTo(403);
            var anonymous = HttpClient.newBuilder().sslContext(trustAll()).build();
            assertThat(send(anonymous, root, "/api/workspaces", null, null, "GET").statusCode()).isEqualTo(401);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_team_invitation WHERE token_hash IN (?,?,?,?)",
                    Long.class, firstToken, token, revokedToken, expiredToken)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_password_reset WHERE token_hash IN (?,?)",
                    Long.class, firstReset, reset)).isZero();
            assertThat(mail.accepted).isEqualTo(6);
            Files.createDirectories(OUT);
            var evidence = Map.ofEntries(Map.entry("status", "PASS"), Map.entry("smtpProviderAccepted", mail.accepted),
                    Map.entry("invitationDelivery", Json.MAPPER.readTree(resent.body()).path("deliveryStatus").asText()),
                    Map.entry("invitationResendRotated", true), Map.entry("passwordResetRotated", true),
                    Map.entry("wrongAccountRejected", true), Map.entry("revokedAndExpiredRejected", true),
                    Map.entry("oldSessionRevoked", true), Map.entry("httpsSecureCookieAndCsrf", true),
                    Map.entry("mailboxReceipt", "PENDING_USER_MANUAL_CHECK"), Map.entry("imapUsed", false),
                    Map.entry("database", "ISOLATED_RANDOM_PG"), Map.entry("rawSecretsIncluded", false));
            Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve("smtp-acceptance.json").toFile(), evidence);
        } finally { fixture.dropDatabase(); }
    }

    private record Session(HttpClient client) {}
    private static Session session() throws Exception {
        return new Session(HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .sslContext(trustAll()).connectTimeout(Duration.ofSeconds(10)).build());
    }

    private static HttpResponse<String> register(Session session, String root, String email, String name) throws Exception {
        var response = send(session.client, root, "/api/auth/register", Json.write(Map.of(
                "email", email, "password", PASSWORD, "displayName", name)), csrf(session.client, root), "POST");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response;
    }

    private static String csrf(HttpClient client, String root) throws Exception {
        return Json.MAPPER.readTree(send(client, root, "/api/auth/csrf", null, null, "GET").body()).path("token").asText();
    }

    private static HttpResponse<String> send(HttpClient client, String root, String path, String body, String csrf, String method) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(root + path)).timeout(Duration.ofSeconds(40));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Path certificate() throws Exception {
        Path certificate = temporary.resolve("smtp.p12");
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool").toString();
        var process = new ProcessBuilder(executable, "-genkeypair", "-alias", "isolated", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=DNS:localhost,IP:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", certificate.toString(), "-storepass", STORE_PASSWORD)
                .redirectErrorStream(true).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        return certificate;
    }

    private static SSLContext trustAll() throws Exception {
        var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            public void checkServerTrusted(X509Certificate[] chain, String authType) { }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, new SecureRandom());
        return context;
    }
}
