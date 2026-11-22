package io.devicetruth.server;

import io.devicetruth.adapter.Json;
import io.devicetruth.app.LegacyOwnershipClaimTool;
import io.devicetruth.application.maintenance.LegacyOwnershipClaimService;
import io.devicetruth.application.maintenance.LegacyOwnershipClaimService.Request;
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
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

class LegacyOwnershipClaimTest {
    private final AccountWorkspaceMigrationTest fixture = new AccountWorkspaceMigrationTest();
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private LegacyOwnershipClaimService claims;
    private UUID admin;
    private UUID conversation;
    private UUID waitingRun;
    private UUID pendingQuestion;
    private UUID file;
    @TempDir Path temporary;

    @BeforeEach void setUp() throws Exception {
        fixture.createDatabase();
        source = new DriverManagerDataSource(fixture.base + fixture.database, fixture.user, fixture.password);
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        claims = new LegacyOwnershipClaimService(source);
        admin = new AccountService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)),
                new ProductAuthConfiguration().accountPasswordEncoder())
                .register("maintainer@example.com", "isolated-maintainer-password", "维护员").userId();
    }

    @AfterEach void cleanup() throws Exception {
        fixture.dropDatabase();
    }

    @Test void dryRunAndClaimPreserveAllLegacyRowsAndAllowWaitingReply() throws Exception {
        seedLegacy(true);
        Path manifestPath = temporary.resolve("dry-run.json");
        int exit = LegacyOwnershipClaimTool.execute(new String[]{"--owner-id=legacy-owner", "--project-id=legacy-project",
                "--admin-user-id=" + admin, "--team-name=旧资料认领团队", "--files-root=" + filesRoot(),
                "--output=" + manifestPath}, Map.of("SPRING_DATASOURCE_URL", fixture.base + fixture.database,
                "SPRING_DATASOURCE_USERNAME", fixture.user, "SPRING_DATASOURCE_PASSWORD", fixture.password));
        assertThat(exit).isZero();
        var dryRun = Json.MAPPER.readValue(Files.readString(manifestPath), LegacyOwnershipClaimService.Manifest.class);
        assertThat(dryRun.valid()).isTrue();
        assertThat(dryRun.tableCounts()).containsEntry("dt_agent_run", 3L).containsEntry("dt_run_event", 3L)
                .containsEntry("dt_uploaded_file", 1L).containsEntry("dt_asset", 1L)
                .containsEntry("dt_run_skill", 1L).containsEntry("dt_run_skill_reference", 1L);
        assertThat(dryRun.activeRuns()).hasSize(3);
        assertThat(dryRun.unknownAuthorCounts().get("runExecutionActors")).isEqualTo(3L);
        assertThat(dryRun.files()).singleElement().satisfies(check -> assertThat(check.status()).isEqualTo("OK"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace WHERE kind='TEAM'", Long.class)).isZero();

        var claimed = claims.claim(request());
        assertThat(claimed.workspaceId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace_member WHERE workspace_id=? AND user_id=? AND role='ADMIN'",
                Long.class, claimed.workspaceId(), admin)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT workspace_id FROM dt_conversation WHERE id=?", UUID.class, conversation))
                .isEqualTo(claimed.workspaceId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_message WHERE actor_user_id IS NOT NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_agent_run WHERE initiated_by_user_id IS NOT NULL OR execution_actor_user_id IS NOT NULL",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT table_counts->>'dt_agent_run' FROM dt_legacy_ownership_claim WHERE workspace_id=?",
                String.class, claimed.workspaceId())).isEqualTo("3");
        assertThat(jdbc.queryForObject("SELECT table_counts->>'dt_run_skill_reference' FROM dt_legacy_ownership_claim WHERE workspace_id=?",
                String.class, claimed.workspaceId())).isEqualTo("1");
        assertThatThrownBy(() -> claims.claim(request())).hasMessageContaining("ALREADY_CLAIMED");

        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--devicetruth.auth.cookie-secure=false",
                "--spring.datasource.url=" + fixture.base + fixture.database, "--spring.datasource.username=" + fixture.user,
                "--spring.datasource.password=" + fixture.password, "--devicetruth.agent.worker-enabled=false",
                "--devicetruth.agent.reasoner=scripted", "--devicetruth.embedding.provider=deterministic",
                "--devicetruth.files.root=" + filesRoot())) {
            String root = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var client = HttpClient.newBuilder().cookieHandler(cookies).build();
            String csrf = csrf(client, root);
            var login = send(client, root, "/api/auth/login", Json.write(Map.of("email", "maintainer@example.com",
                    "password", "isolated-maintainer-password")), csrf, Map.of(), null);
            assertThat(login.statusCode()).isEqualTo(200);
            csrf = csrf(client, root);
            var detail = send(client, root, "/api/conversations/" + conversation, null, null, Map.of(), null);
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(Json.MAPPER.readTree(detail.body()).path("runs").size()).isEqualTo(3);
            var waiting = send(client, root, "/api/runs/" + waitingRun, null, null, Map.of(), null);
            assertThat(waiting.statusCode()).isEqualTo(200);
            assertThat(Json.MAPPER.readTree(waiting.body()).path("status").asText()).isEqualTo("WAITING_FOR_USER");
            var original = Json.MAPPER.readTree(waiting.body());
            var reply = Json.write(Map.of("schemaVersion", 1, "userMessage", "现场确认后继续", "replyTo", Map.of(
                    "runId", waitingRun.toString(), "pendingQuestionId", pendingQuestion.toString(), "questionVersion", 1)));
            var accepted = send(client, root, "/api/conversations/" + conversation + "/messages", reply, csrf,
                    Map.of("Idempotency-Key", UUID.randomUUID().toString()), null);
            assertThat(accepted.statusCode()).isEqualTo(202);
            assertThat(Json.MAPPER.readTree(accepted.body()).path("runId").asText()).isEqualTo(waitingRun.toString());
            assertThat(Json.MAPPER.readTree(accepted.body()).path("asOf")).isEqualTo(original.path("asOf"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_agent_run", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT execution_actor_user_id FROM dt_agent_run WHERE id=?", UUID.class, waitingRun))
                    .isEqualTo(admin);
            assertThat(jdbc.queryForObject("SELECT initiated_by_user_id FROM dt_agent_run WHERE id=?", UUID.class, waitingRun)).isNull();
            var content = send(client, root, "/api/files/" + file + "/content", null, null, Map.of(), null);
            assertThat(content.statusCode()).isEqualTo(200);
            assertThat(content.body()).contains("历史检查记录");
        }
    }

    @Test void invalidTargetAndMissingFilesNeverCreateMembership() throws Exception {
        seedLegacy(false);
        var missingAdmin = new Request("legacy-owner", "legacy-project", UUID.randomUUID(), "错误目标", filesRoot());
        assertThat(claims.dryRun(missingAdmin).problems()).contains("ADMIN_NOT_FOUND", "FILE_MISSING");
        assertThatThrownBy(() -> claims.claim(missingAdmin)).hasMessageContaining("CLAIM_PRECHECK_FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace WHERE kind='TEAM'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT workspace_id FROM dt_conversation WHERE id=?", UUID.class, conversation)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_legacy_ownership_claim", Long.class)).isZero();
    }

    @Test void failureDuringBindingRollsBackWorkspaceMembershipAndAudit() throws Exception {
        seedLegacy(true);
        jdbc.execute("""
                CREATE FUNCTION reject_legacy_claim() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'INJECTED_CLAIM_FAILURE'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_legacy_claim BEFORE UPDATE OF workspace_id ON dt_conversation
                FOR EACH ROW EXECUTE FUNCTION reject_legacy_claim()
                """);
        assertThatThrownBy(() -> claims.claim(request())).hasMessageContaining("INJECTED_CLAIM_FAILURE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace WHERE kind='TEAM'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace_member WHERE user_id=?", Long.class, admin)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_legacy_ownership_claim", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT workspace_id FROM dt_conversation WHERE id=?", UUID.class, conversation)).isNull();
    }

    private Request request() {
        return new Request("legacy-owner", "legacy-project", admin, "旧资料认领团队", filesRoot());
    }

    private Path filesRoot() { return temporary.resolve("files"); }

    private void seedLegacy(boolean writeFile) throws Exception {
        Files.createDirectories(filesRoot().resolve("knowledge"));
        conversation = UUID.randomUUID();
        waitingRun = UUID.randomUUID();
        pendingQuestion = UUID.randomUUID();
        file = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,created_at,updated_at) VALUES (?,'legacy-owner','legacy-project',now(),now())", conversation);
        for (int i = 1; i <= 3; i++) {
            UUID message = UUID.randomUUID();
            UUID run = i == 3 ? waitingRun : UUID.randomUUID();
            jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) VALUES (?,?,'legacy-owner','legacy-project',?,'USER','历史输入',now(),now())", message, conversation, i);
            String pending = Json.write(new io.devicetruth.core.contract.AgentTaskInput.PendingQuestion(
                    pendingQuestion, 1, "请确认现场情况", java.util.List.of("detail"), Instant.now()));
            jdbc.update("""
                    INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,
                        pending_question_id,question_version,pending_question,budget,created_at,updated_at,round_number)
                    VALUES (?,?,'legacy-owner','legacy-project',?,?,now(),now(),?,?,CAST(? AS jsonb),'{"limits":{"maxActiveDurationMs":120000}}',now(),now(),?)
                    """, run, conversation, message, i == 3 ? "WAITING_FOR_USER" : "QUEUED",
                    i == 3 ? pendingQuestion : null, i == 3 ? 1 : 0, i == 3 ? pending : null, i);
            jdbc.update("INSERT INTO dt_run_event(run_id,seq,conversation_id,owner_id,project_id,type,recorded_at,safe_payload) VALUES (?,1,?,'legacy-owner','legacy-project','RUN_QUEUED',now(),'{}')", run, conversation);
        }
        jdbc.update("INSERT INTO dt_skill_installation(owner_id,project_id,skill_id,version,allowed_tools) VALUES ('legacy-owner','legacy-project','legacy-skill','1.0.0','{}')");
        jdbc.update("""
                INSERT INTO dt_run_skill(run_id,conversation_id,owner_id,project_id,skill_id,version,devices,tasks,allowed_tools,document_digest)
                VALUES (?,?,'legacy-owner','legacy-project','legacy-skill','1.0.0','{}','{}','{}',repeat('a',64))
                """, waitingRun, conversation);
        jdbc.update("INSERT INTO dt_run_skill_reference(run_id,name,content_digest) VALUES (?,'rules.md',repeat('b',64))", waitingRun);
        byte[] bytes = "历史检查记录".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        jdbc.update("""
                INSERT INTO dt_uploaded_file(id,owner_id,project_id,path_key,original_name,mime,checksum,byte_size,parse_status,created_at)
                VALUES (?,'legacy-owner','legacy-project',?,'legacy.md','text/markdown',?,?,'PARSED',now())
                """, file, "knowledge/" + file, checksum, bytes.length);
        jdbc.update("INSERT INTO dt_asset(id,owner_id,project_id,created_at) VALUES ('LEGACY-ASSET','legacy-owner','legacy-project',now())");
        if (writeFile) Files.write(filesRoot().resolve("knowledge/" + file), bytes);
    }

    private static String csrf(HttpClient client, String root) throws Exception {
        var response = send(client, root, "/api/auth/csrf", null, null, Map.of(), null);
        return Json.MAPPER.readTree(response.body()).path("token").asText();
    }

    private static HttpResponse<String> send(HttpClient client, String root, String path, String body, String csrf,
            Map<String, String> headers, String method) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(root + path));
        headers.forEach(builder::header);
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json")
                .method(method == null ? "POST" : method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
