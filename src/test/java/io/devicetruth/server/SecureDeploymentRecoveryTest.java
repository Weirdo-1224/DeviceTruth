package io.devicetruth.server;

import io.devicetruth.adapter.Json;
import io.devicetruth.app.DeviceTruthApplication;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class SecureDeploymentRecoveryTest {
    private static final String STORE_PASSWORD = "isolated-test-keystore";
    private static final String ACCOUNT_PASSWORD = "isolated-recovery-password";
    private final AccountWorkspaceMigrationTest fixture = new AccountWorkspaceMigrationTest();
    private String restored;
    @TempDir Path temporary;

    @BeforeEach void createSource() throws Exception { fixture.createDatabase(); }
    @AfterEach void cleanup() throws Exception {
        try (var connection = DriverManager.getConnection(fixture.base + "postgres", fixture.user, fixture.password);
             var statement = connection.createStatement()) {
            if (restored != null) statement.execute("DROP DATABASE IF EXISTS " + restored);
        }
        fixture.dropDatabase();
    }

    @Test void matchingDatabaseFilesProgramAndSessionRestoreOverHttps() throws Exception {
        var source = new DriverManagerDataSource(fixture.base + fixture.database, fixture.user, fixture.password);
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        new AccountService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)),
                new ProductAuthConfiguration().accountPasswordEncoder())
                .register("recovery@example.com", ACCOUNT_PASSWORD, "恢复验收员");
        Path certificate = certificate();
        Path sourceFiles = temporary.resolve("source-files");
        Path restoredFiles = temporary.resolve("restored-files");
        Files.createDirectories(sourceFiles);
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).sslContext(trustAll()).connectTimeout(Duration.ofSeconds(5)).build();
        UUID completed;
        UUID waiting;
        UUID file;
        Map<String, Long> sourceCounts;
        String sessionCookie;
        try (var app = product(fixture.database, sourceFiles, certificate)) {
            String root = root(app);
            String csrf = csrf(client, root);
            var login = send(client, root, "/api/auth/login", Json.write(Map.of(
                    "email", "recovery@example.com", "password", ACCOUNT_PASSWORD)), csrf, Map.of(), "POST");
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(login.headers().allValues("set-cookie").toString()).contains("Secure", "HttpOnly", "SameSite=Lax");
            sessionCookie = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("DTSESSION"))
                    .findFirst().orElseThrow().getValue();
            csrf = csrf(client, root);
            var imported = send(client, root, "/api/knowledge/documents", Json.write(Map.of(
                    "title", "隔离恢复手册", "domain", "automotive-demo", "version", "v1", "filename", "recovery.md",
                    "mimeType", "text/markdown", "content", "# 充电系统\n直流快充中断时先检查充电连接和车辆提示。",
                    "dataNature", "SIMULATED")), csrf, Map.of(), "POST");
            assertThat(imported.statusCode()).isEqualTo(201);
            completed = runToWaiting(client, root, csrf, "新能源汽车充电中断，没有故障码，先查什么资料？");
            var pending = run(client, root, completed);
            var reply = Json.write(Map.of("schemaVersion", 1, "userMessage", "直流快充，SOC约40%", "replyTo", Map.of(
                    "runId", completed.toString(), "pendingQuestionId", pending.path("pendingQuestion").path("pendingQuestionId").asText(),
                    "questionVersion", pending.path("pendingQuestion").path("questionVersion").asInt())));
            var accepted = send(client, root, "/api/conversations/" + pending.path("conversationId").asText() + "/messages",
                    reply, csrf, Map.of("Idempotency-Key", UUID.randomUUID().toString()), "POST");
            assertThat(accepted.statusCode()).isEqualTo(202);
            awaitStatus(client, root, completed, "COMPLETED");
            waiting = runToWaiting(client, root, csrf, "另一项充电中断调查");
            file = jdbc.queryForObject("SELECT id FROM dt_uploaded_file ORDER BY created_at DESC LIMIT 1", UUID.class);
            Files.createDirectories(sourceFiles.resolve("knowledge"));
            Files.writeString(sourceFiles.resolve("knowledge/" + file),
                    "# 充电系统\n直流快充中断时先检查充电连接和车辆提示。");
            sourceCounts = counts(jdbc);
        }

        restored = "devicetruth_test_restore_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(fixture.base + "postgres", fixture.user, fixture.password);
             var statement = connection.createStatement()) {
            statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='" + fixture.database + "' AND pid<>pg_backend_pid()");
            statement.execute("CREATE DATABASE " + restored + " TEMPLATE " + fixture.database);
        }
        copyFiles(sourceFiles, restoredFiles);
        try (var app = product(restored, restoredFiles, certificate)) {
            String root = root(app);
            var restoredJdbc = new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
            assertThat(counts(restoredJdbc)).isEqualTo(sourceCounts);
            assertThat(send(client, root, "/api/auth/me", null, null, Map.of(), "GET").statusCode()).isEqualTo(200);
            assertThat(run(client, root, completed).path("status").asText()).isEqualTo("COMPLETED");
            assertThat(run(client, root, waiting).path("status").asText()).isEqualTo("WAITING_FOR_USER");
            var references = send(client, root, "/api/runs/" + completed + "/references", null, null, Map.of(), "GET");
            assertThat(references.statusCode()).isEqualTo(200);
            assertThat(Json.MAPPER.readTree(references.body()).size()).isEqualTo(restoredJdbc.queryForObject(
                    "SELECT count(*) FROM dt_evidence_reference WHERE run_id=? AND source_type IN ('KNOWLEDGE_DOCUMENT','CONFIGURATION','FIELD_OBSERVATION')",
                    Long.class, completed).intValue());
            var content = send(client, root, "/api/files/" + file + "/content", null, null, Map.of(), "GET");
            assertThat(content.statusCode()).isEqualTo(200);
            assertThat(content.body()).contains("直流快充");
            assertThat(send(client, root, "/api/auth/csrf", null, null, Map.of(), "GET").statusCode()).isEqualTo(200);
            String conversation = run(client, root, waiting).path("conversationId").asText();
            var noCsrf = send(client, root, "/api/conversations/" + conversation + "/discussions",
                    Json.write(Map.of("content", "不允许写入")), null, Map.of(), "POST");
            assertThat(noCsrf.statusCode()).isEqualTo(403);
            var withCsrf = send(client, root, "/api/conversations/" + conversation + "/discussions",
                    Json.write(Map.of("content", "恢复后授权写入")), csrf(client, root), Map.of(), "POST");
            assertThat(withCsrf.statusCode()).isEqualTo(201);
            var anonymous = HttpClient.newBuilder().sslContext(trustAll()).build();
            assertThat(send(anonymous, root, "/api/conversations/" + conversation, null, null, Map.of(), "GET").statusCode())
                    .isEqualTo(401);
            var replay = HttpClient.newBuilder().sslContext(trustAll()).build();
            var oldSession = send(replay, root, "/api/auth/me", null, null, Map.of("Cookie", "DTSESSION=" + sessionCookie), "GET");
            assertThat(oldSession.statusCode()).isEqualTo(200);
            assertThat(restoredJdbc.queryForObject("SELECT count(*) FROM spring_session", Long.class)).isEqualTo(sourceCounts.get("spring_session"));
            assertThat(app.getBean(PasswordRecoveryService.ResetMailSender.class).available()).isTrue();
        }
        assertThat(DeviceTruthApplication.execute(new String[]{"--help"})).isZero();
    }

    @Test void legacyEntryRemainsIndependentFromProductAuthentication() throws Exception {
        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=legacy", "--server.port=0", "--server.address=127.0.0.1")) {
            String root = root(app).replace("https:", "http:");
            var client = HttpClient.newHttpClient();
            assertThat(send(client, root, "/api/health", null, null, Map.of(), "GET").statusCode()).isEqualTo(200);
            var session = send(client, root, "/api/sessions", "{}", null, Map.of("Content-Type", "application/json"), "POST");
            assertThat(session.statusCode()).isEqualTo(201);
            String id = Json.MAPPER.readTree(session.body()).path("sessionId").asText();
            assertThat(send(client, root, "/api/sessions/" + id + "/messages", null, null, Map.of(), "GET").statusCode())
                    .isEqualTo(200);
        }
    }

    private SpringApplicationBuilder productBuilder(Path certificate) {
        return new SpringApplicationBuilder(DeviceTruthServerApplication.class)
                .properties("server.ssl.enabled=true", "server.ssl.key-store=" + certificate.toAbsolutePath(),
                        "server.ssl.key-store-password=" + STORE_PASSWORD, "server.ssl.key-store-type=PKCS12");
    }

    private org.springframework.context.ConfigurableApplicationContext product(String database, Path files, Path certificate) {
        return productBuilder(certificate).run("--spring.profiles.active=product", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + fixture.base + database, "--spring.datasource.username=" + fixture.user,
                "--spring.datasource.password=" + fixture.password, "--devicetruth.auth.cookie-secure=true",
                "--devicetruth.agent.worker-enabled=true", "--devicetruth.agent.reasoner=scripted",
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.files.root=" + files.toAbsolutePath(),
                "--devicetruth.mail.host=127.0.0.1", "--devicetruth.mail.port=2525",
                "--devicetruth.mail.from=recovery@example.invalid", "--devicetruth.mail.public-url=https://127.0.0.1/");
    }

    private UUID runToWaiting(HttpClient client, String root, String csrf, String question) throws Exception {
        var created = send(client, root, "/api/conversations", "{}", csrf, Map.of(), "POST");
        assertThat(created.statusCode()).isEqualTo(201);
        String conversation = Json.MAPPER.readTree(created.body()).path("conversationId").asText();
        var accepted = send(client, root, "/api/conversations/" + conversation + "/messages",
                Json.write(Map.of("schemaVersion", 1, "userMessage", question)), csrf,
                Map.of("Idempotency-Key", UUID.randomUUID().toString()), "POST");
        assertThat(accepted.statusCode()).isEqualTo(202);
        UUID run = UUID.fromString(Json.MAPPER.readTree(accepted.body()).path("runId").asText());
        awaitStatus(client, root, run, "WAITING_FOR_USER");
        return run;
    }

    private static void awaitStatus(HttpClient client, String root, UUID run, String status) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (run(client, root, run).path("status").asText().equals(status)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Run did not reach " + status);
    }

    private static com.fasterxml.jackson.databind.JsonNode run(HttpClient client, String root, UUID run) throws Exception {
        var response = send(client, root, "/api/runs/" + run, null, null, Map.of(), "GET");
        assertThat(response.statusCode()).isEqualTo(200);
        return Json.MAPPER.readTree(response.body());
    }

    private static String csrf(HttpClient client, String root) throws Exception {
        return Json.MAPPER.readTree(send(client, root, "/api/auth/csrf", null, null, Map.of(), "GET").body()).path("token").asText();
    }

    private static String root(org.springframework.context.ConfigurableApplicationContext app) {
        return "https://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
    }

    private static HttpResponse<String> send(HttpClient client, String root, String path, String body, String csrf,
            Map<String, String> headers, String method) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(root + path)).timeout(Duration.ofSeconds(15));
        headers.forEach(builder::header);
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null && !headers.containsKey("Content-Type")) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Long> counts(JdbcTemplate jdbc) {
        Map<String, Long> result = new HashMap<>();
        for (String table : java.util.List.of("dt_conversation", "dt_message", "dt_agent_run", "dt_run_event",
                "dt_evidence_reference", "dt_uploaded_file", "dt_knowledge_document", "spring_session"))
            result.put(table, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class));
        return result;
    }

    private Path certificate() throws Exception {
        Path certificate = temporary.resolve("isolated.p12");
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool").toString();
        var process = new ProcessBuilder(executable, "-genkeypair", "-alias", "isolated", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=DNS:localhost,IP:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", certificate.toString(), "-storepass", STORE_PASSWORD)
                .redirectErrorStream(true).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(new String(process.getInputStream().readAllBytes())).isZero();
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

    private static void copyFiles(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
