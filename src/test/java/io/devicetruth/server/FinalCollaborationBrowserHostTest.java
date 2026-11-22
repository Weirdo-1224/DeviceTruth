package io.devicetruth.server;

import io.devicetruth.adapter.Json;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.WorkspaceScopeResolver;
import io.devicetruth.knowledge.KnowledgeImportService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class FinalCollaborationBrowserHostTest {
    private static final Path OUT = Path.of("outputs/validation/workbench-group8");
    private static final String PASSWORD = "isolated-browser-password";

    @Test void hostRealModelForTwoIndependentBrowserAccounts() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.final-browser"));
        var fixture = new AccountWorkspaceMigrationTest();
        fixture.createDatabase();
        Files.createDirectories(OUT);
        Path stop = OUT.resolve("browser-stop.txt");
        Files.deleteIfExists(stop);
        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--server.address=0.0.0.0",
                "--spring.datasource.url=" + fixture.base + fixture.database, "--spring.datasource.username=" + fixture.user,
                "--spring.datasource.password=" + fixture.password, "--devicetruth.auth.cookie-secure=false",
                "--devicetruth.auth.rate-limit=200", "--devicetruth.agent.worker-enabled=true",
                "--devicetruth.agent.reasoner=openai", "--devicetruth.agent.max-active-duration-ms=180000",
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.files.root=" + OUT.resolve("files"),
                "--spring.web.resources.static-locations=" + Path.of("frontend/dist").toAbsolutePath().toUri())) {
            var accounts = app.getBean(AccountService.class);
            UUID admin = accounts.register("admin@example.com", PASSWORD, "管理员陈工").userId();
            UUID colleague = accounts.register("colleague@example.com", PASSWORD, "协作李工").userId();
            UUID team = app.getBean(WorkspaceService.class).createTeam(admin, "最终协作验收团队").workspaceId();
            var jdbc = new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
            jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'COLLABORATOR')", team, colleague);
            var scope = new WorkspaceScopeResolver(jdbc).resolve(admin, team);
            String manual = Files.readString(Path.of("datasets/knowledge/charging-system-manual.md"));
            app.getBean(KnowledgeImportService.class).importDocument(scope.storageIdentity().ownerId(), scope.storageIdentity().projectId(),
                    new KnowledgeImportService.ImportRequest("新能源汽车充电系统检修手册（演示资料）", "automotive-demo", null,
                            "2026.1", "charging-system-manual.md", "text/markdown", manual, "SIMULATED"));
            int port = ((WebServerApplicationContext) app).getWebServer().getPort();
            var service = app.getBean(GeneralAgentService.class);
            Files.writeString(OUT.resolve("browser-service.json"), Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "adminUrl", "http://127.0.0.1:" + port, "colleagueUrl", "http://localhost:" + port,
                    "workspaceId", team, "admin", admin, "colleague", colleague, "password", PASSWORD,
                    "database", fixture.database, "stopFile", stop.toAbsolutePath().toString())));
            long deadline = System.nanoTime() + Duration.ofHours(2).toNanos();
            while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(500);
            assertThat(Files.exists(stop)).as("最终浏览器宿主必须显式收尾").isTrue();
            var scoped = service.forScope(scope);
            var runs = jdbc.queryForList("SELECT id FROM dt_agent_run ORDER BY created_at,id", UUID.class);
            var evidence = new ArrayList<Map<String, Object>>();
            for (UUID run : runs) {
                var item = new LinkedHashMap<String, Object>();
                item.put("run", scoped.run(run));
                item.put("models", scoped.modelEvents(run));
                item.put("references", scoped.references(run));
                item.put("events", scoped.events(run, 0));
                evidence.add(item);
            }
            Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve("browser-real-runs.json").toFile(), evidence);
            var conversations = jdbc.queryForList("SELECT id FROM dt_conversation ORDER BY created_at,id", UUID.class);
            var inputs = new ArrayList<Map<String, Object>>();
            for (UUID conversation : conversations) {
                inputs.add(Map.of("conversationId", conversation, "detail", scoped.read(conversation),
                        "discussions", scoped.discussions(conversation, 0, 100)));
            }
            Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve("browser-inputs.json").toFile(), inputs);
            var models = jdbc.queryForList("SELECT DISTINCT safe_payload->>'model' FROM dt_run_event WHERE type='RUN_MODEL'", String.class);
            Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve("browser-environment.json").toFile(), Map.of(
                    "reasoner", "openai", "models", models, "database", "REAL_ISOLATED_PG_16",
                    "embedding", "deterministic", "materials", "SIMULATED", "fieldData", false,
                    "existingProductServiceTouched", false));
            assertThat(models).isNotEmpty().allMatch(name -> name != null && !name.isBlank() && !name.equals("scripted"));
            assertThat(runs.size()).isGreaterThanOrEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_investigation_discussion", Long.class)).isPositive();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_message WHERE discussion_quotes<>'[]'::jsonb", Long.class)).isPositive();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_input_segment GROUP BY run_id HAVING count(*)>1", Long.class)).isNotNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_evidence_reference", Long.class)).isPositive();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_agent_run WHERE status='CANCELLED'", Long.class)).isPositive();
        } finally { fixture.dropDatabase(); }
    }
}
