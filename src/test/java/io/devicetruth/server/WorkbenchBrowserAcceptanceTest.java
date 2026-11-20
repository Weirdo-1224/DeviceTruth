package io.devicetruth.server;

import io.devicetruth.adapter.Json;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.WorkspaceScopeResolver;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.*;

/** 显式 opt-in 的第六组浏览器宿主。真 PG/真推理；仅邮件传输替身，不计真实收件。 */
class WorkbenchBrowserAcceptanceTest {
    static final Path OUT = Path.of("outputs/validation/workbench-group6");
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class MailTransport {
        @Bean @Primary PasswordRecoveryService.ResetMailSender capturedMailTransport() {
            var transport = new JavaMailSenderImpl() {
                @Override public void send(SimpleMailMessage message) {
                    try {
                        Files.createDirectories(OUT.resolve("mailbox"));
                        Files.writeString(OUT.resolve("mailbox").resolve(message.getTo()[0].replaceAll("[^A-Za-z0-9.-]", "_") + ".txt"), message.getText());
                    } catch (java.io.IOException e) { throw new IllegalStateException("TEST_MAIL_WRITE_FAILED"); }
                }
            };
            transport.setHost("isolated-mail-transport.invalid");
            return new SmtpResetMailSender(transport, "test@example.com", "https://isolated.invalid/");
        }
    }
    @Test void resumeIsolatedBrowserCandidate() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.workbench-browser"));
        var metadata=Json.MAPPER.readTree(Files.readString(OUT.resolve("service.json")));
        String database=metadata.path("database").asText();
        assertThat(database).matches("devicetruth_test_[0-9a-f]{32}");
        assertThat(System.getenv("DEVICETRUTH_TEST_PG_BASE_URL")).isEqualTo("jdbc:postgresql://127.0.0.1:54339/");
        Path stop=OUT.resolve("resume-stop-"+database);
        Files.copy(OUT.resolve("service.json"),OUT.resolve("original-service.json"),StandardCopyOption.REPLACE_EXISTING);
        try(var app=new SpringApplicationBuilder(DeviceTruthServerApplication.class,MailTransport.class).run(
                "--spring.profiles.active=product","--server.port=0","--server.address=127.0.0.1",
                "--spring.datasource.url="+System.getenv("DEVICETRUTH_TEST_PG_BASE_URL")+database,
                "--spring.datasource.username="+System.getenv("DEVICETRUTH_TEST_PG_USERNAME"),"--spring.datasource.password="+System.getenv("DEVICETRUTH_TEST_PG_PASSWORD"),
                "--devicetruth.auth.cookie-secure=false","--devicetruth.auth.rate-limit=200","--devicetruth.agent.worker-enabled=true","--devicetruth.agent.reasoner=openai",
                "--devicetruth.embedding.provider=deterministic","--devicetruth.files.root="+OUT.resolve("files").toAbsolutePath(),
                "--spring.web.resources.static-locations="+Path.of("frontend/dist").toAbsolutePath().toUri())) {
            var updated=(com.fasterxml.jackson.databind.node.ObjectNode)metadata;
            updated.put("url","http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort());updated.put("stopFile",stop.toAbsolutePath().toString());
            Files.writeString(OUT.resolve("service.json"),Json.write(updated));
            long deadline=System.nanoTime()+Duration.ofHours(1).toNanos();
            while(!Files.exists(stop)&&System.nanoTime()<deadline)Thread.sleep(500);
            assertThat(Files.exists(stop)).isTrue();
            var jdbc=new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
            Files.writeString(OUT.resolve("persistent-runs.json"),Json.write(jdbc.queryForList("SELECT id,conversation_id,status,version FROM dt_agent_run ORDER BY created_at")));
        }
    }
    @Test void hostRealProductForGroupSixBrowserAcceptance() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.workbench-browser"));
        var fixture = new AccountWorkspaceMigrationTest(); fixture.createDatabase();
        Files.createDirectories(OUT);
        Path stop = OUT.resolve("stop-" + fixture.database);
        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class, MailTransport.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + fixture.base + fixture.database,
                "--spring.datasource.username=" + fixture.user, "--spring.datasource.password=" + fixture.password,
                "--devicetruth.auth.cookie-secure=false", "--devicetruth.auth.rate-limit=200",
                "--devicetruth.agent.worker-enabled=true", "--devicetruth.agent.reasoner=openai",
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.agent.max-active-duration-ms=180000",
                "--devicetruth.files.root=" + OUT.resolve("files").toAbsolutePath(),
                "--spring.web.resources.static-locations=" + Path.of("frontend/dist").toAbsolutePath().toUri())) {
            var accounts = app.getBean(AccountService.class);
            UUID admin = accounts.register("admin@example.com", "isolated-browser-password-123", "管理员陈工").userId();
            UUID colleague = accounts.register("colleague@example.com", "isolated-browser-password-123", "协作李工").userId();
            UUID viewer = accounts.register("viewer@example.com", "isolated-browser-password-123", "只读王工").userId();
            var teams = app.getBean(WorkspaceService.class);
            UUID team = teams.createTeam(admin, "检修协作验收团队").workspaceId();
            var jdbc = new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
            jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'COLLABORATOR'),(?,?,'VIEWER')", team,colleague,team,viewer);
            var general = app.getBean(GeneralAgentService.class);
            var scope = new WorkspaceScopeResolver(jdbc).resolve(admin,team);
            // 空调查用于讨论/上传验收；无模型答案或来源伪造。
            UUID conversation = general.forScope(scope).create().conversationId();
            String url = "http://127.0.0.1:" + ((WebServerApplicationContext)app).getWebServer().getPort();
            Files.writeString(OUT.resolve("service.json"), Json.write(Map.of("url",url,"database",fixture.database,
                    "workspaceId",team,"conversationId",conversation,"admin",admin,"colleague",colleague,"viewer",viewer,"stopFile",stop.toAbsolutePath().toString())));
            long deadline = System.nanoTime()+Duration.ofHours(2).toNanos();
            while(!Files.exists(stop) && System.nanoTime()<deadline) Thread.sleep(500);
            assertThat(Files.exists(stop)).as("验收宿主必须显式正常收尾").isTrue();
            var runs = jdbc.queryForList("SELECT id,conversation_id,status,version FROM dt_agent_run ORDER BY created_at");
            Files.writeString(OUT.resolve("persistent-runs.json"),Json.write(runs));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE type='RUN_MODEL'",Long.class)).as("浏览器实际真模型事件").isPositive();
        } finally { fixture.dropDatabase(); }
    }
}
