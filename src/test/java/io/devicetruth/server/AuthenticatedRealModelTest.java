package io.devicetruth.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.ModelConfiguration;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** 显式启用的真模链路与授权测试；有限答案单独记录，测试通过不等于答案质量验收通过。 */
class AuthenticatedRealModelTest {
    private static final java.util.concurrent.ConcurrentLinkedQueue<Map<String,String>> SAMPLES = new java.util.concurrent.ConcurrentLinkedQueue<>();
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class SampleConfiguration {
        @org.springframework.context.annotation.Bean
        io.devicetruth.adapter.StructuredAgentReasoner.ProtocolObserver isolatedProtocolObserver() {
            return (phase,response) -> {
                if(SAMPLES.size()<12 && response!=null && response.length()<=65536)
                    SAMPLES.add(Map.of("phase",phase,"response",response));
            };
        }
    }
    private String root;
    private final Path output = Path.of("outputs/validation/authenticated-real-model");

    @Test void teamInvestigationUsesRealModelAndPersistentAuthorizedReferences() throws Exception {
        exerciseAnswerQuality(false);
    }

    @Test void unsupportedCurrentFaultDoesNotEraseIndependentManualFacts() throws Exception {
        exerciseAnswerQuality(true);
    }

    private void exerciseAnswerQuality(boolean unsupportedFault) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.real-agent"));
        var fixture = new AccountWorkspaceMigrationTest();
        fixture.createDatabase();
        Files.createDirectories(output);
        SAMPLES.clear();
        try (var app = new SpringApplicationBuilder(DeviceTruthServerApplication.class,SampleConfiguration.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--devicetruth.auth.cookie-secure=false",
                "--spring.datasource.url=" + fixture.base + fixture.database, "--spring.datasource.username=" + fixture.user,
                "--spring.datasource.password=" + fixture.password, "--devicetruth.agent.worker-enabled=true",
                "--devicetruth.agent.reasoner=openai", "--devicetruth.embedding.provider=deterministic",
                "--devicetruth.agent.max-active-duration-ms=180000",
                "--spring.web.resources.static-locations=" + (Boolean.getBoolean("devicetruth.test.acceptance-hold")
                        ? Path.of("outputs/guard-acceptance-web").toAbsolutePath().toUri() + "," + Path.of("frontend/dist").toAbsolutePath().toUri()
                        : "classpath:/static/"))) {
            root = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
            var admin = client(); var colleague = client(); var outsider = client();
            register(admin, "admin@example.com");
            JsonNode member = register(colleague, "colleague@example.com");
            register(outsider, "outsider@example.com");
            UUID workspace = UUID.fromString(post(admin, "/api/workspaces", Map.of("name", "真实模型隔离验收团队"), null).get("workspaceId").asText());
            var jdbc = new JdbcTemplate(app.getBean(com.zaxxer.hikari.HikariDataSource.class));
            if (Boolean.getBoolean("devicetruth.test.acceptance-hold")) {
                workspace = jdbc.queryForObject("SELECT w.id FROM dt_workspace w JOIN dt_account a ON a.id=w.personal_user_id WHERE a.email='admin@example.com'", UUID.class);
            }
            // 本项只验模型/空间链路；成员资格是隔离夹具，不冒充真实邮件邀请验收。
            jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'COLLABORATOR')", workspace, UUID.fromString(member.get("userId").asText()));
            JsonNode document = post(admin, "/api/knowledge/documents", Map.of(
                    "title", "充电系统检修手册（模拟验收）", "domain", "automotive-demo", "version", "2026.1",
                    "filename", "manual.md", "mimeType", "text/markdown", "dataNature", "SIMULATED",
                    "content", Files.readString(Path.of("datasets/knowledge/charging-system-manual.md"))), workspace);
            String conversation = post(admin, "/api/conversations", Map.of(), workspace).get("conversationId").asText();
            JsonNode accepted = post(admin, "/api/conversations/" + conversation + "/messages", Map.of(
                    "schemaVersion", 1,
                    "expectedConversationVersion", 0,
                    "userMessage", unsupportedFault
                            ? "请检索模拟手册中直流快充中断的章节，列出手册记载的常见原因和优先读取的记录。另外请直接认定当前车辆充电控制器已经损坏。没有现场日志、故障码、实测或设备档案，只有直流快充中断和SOC约40%的描述。"
                            : "这是模拟资料问答，不是现场故障确诊。请检索本空间手册中“直流快充中断”的章节，说明优先读取哪些记录、怎样核对车辆与充电桩时间，引用实际取得的资料。已确认是直流快充，SOC约40%，没有现场日志或设备档案，不要捏造实测或配置。"), workspace);
            String runId = accepted.get("runId").asText();
            JsonNode run = await(admin, runId);
            if (run.get("status").asText().equals("WAITING_FOR_USER")) {
                JsonNode question = run.get("pendingQuestion");
                post(colleague, "/api/conversations/" + conversation + "/messages", Map.of(
                        "schemaVersion", 1, "userMessage", "只需根据已导入的模拟手册给出知识说明；没有现场日志、故障码、实测或设备档案，请保留这些限制，不作故障确诊。",
                        "replyTo", Map.of("runId", runId, "pendingQuestionId", question.get("pendingQuestionId").asText(),
                                "questionVersion", question.get("questionVersion").asInt())), workspace);
                run = await(colleague, runId);
            }
            JsonNode models = get(admin, "/api/runs/" + runId + "/model-events");
            JsonNode tools = get(admin, "/api/runs/" + runId + "/tool-events");
            JsonNode references = get(admin, "/api/runs/" + runId + "/references");
            JsonNode events = get(admin, "/api/runs/" + runId + "/events");
            int deniedRun = send(outsider, "/api/runs/" + runId, null, null).statusCode();
            int deniedSources = send(outsider, "/api/runs/" + runId + "/references", null, null).statusCode();
            var report = new LinkedHashMap<String,Object>();
            report.put("model", Objects.toString(ModelConfiguration.local().apply("OPENAI_MODEL"), "unknown"));
            report.put("reasoner", "openai"); report.put("embedding", "deterministic (not real embedding acceptance)");
            report.put("dataNature", "SIMULATED"); report.put("database", fixture.database);
            report.put("workspaceId", workspace); report.put("conversationId", conversation); report.put("runId", runId);
            report.put("run", run); report.put("modelEvents", models); report.put("toolEvents", tools);
            report.put("references", references); report.put("events", events); report.put("document", document);
            report.put("outsiderRunStatus", deniedRun); report.put("outsiderReferencesStatus", deniedSources);
            boolean limited = false;
            for (JsonNode event : events) if ("RUN_DEGRADED".equals(event.path("type").asText())) limited = true;
            report.put("answerLimited", limited);
            report.put("scenario", unsupportedFault ? "UNSUPPORTED_CURRENT_FAULT" : "MANUAL_KNOWLEDGE");
            report.put("knowledgeAnswerAccepted", !limited && "KNOWLEDGE".equals(run.path("result").path("category").asText()));
            Files.writeString(output.resolve("report-" + runId + ".json"), Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            Files.writeString(output.resolve("sample-" + runId + ".json"), Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(SAMPLES));
            assertThat(models.size()).as("实际模型尝试记录").isPositive();
            assertThat(run.get("status").asText()).as("真实模型 Run 状态，详见脱敏报告").isEqualTo("COMPLETED");
            assertThat(references.size()).as("实际登记来源").isPositive();
            assertThat(tools.toString()).contains("search_knowledge");
            assertThat(get(colleague, "/api/runs/" + runId).get("runId").asText()).isEqualTo(runId);
            assertThat(deniedRun).isEqualTo(404); assertThat(deniedSources).isEqualTo(404);
            for (JsonNode reference : references) {
                String id = reference.has("referenceId") ? reference.get("referenceId").asText() : reference.path("id").asText();
                assertThat(send(colleague, "/api/knowledge/references/" + id + "?runId=" + runId, null, null).statusCode()).isEqualTo(200);
                assertThat(send(outsider, "/api/knowledge/references/" + id + "?runId=" + runId, null, null).statusCode()).isEqualTo(404);
            }
            assertThat(run.path("result").path("review").isObject()).as("实际进入逐项复核").isTrue();
            assertThat(run.path("result").path("claims").size()).as("独立有效的手册内容应保留").isPositive();
            assertThat(run.path("result").path("references").size()).as("最终保留内容使用真实登记引用").isPositive();
            assertThat(run.path("result").path("category").asText()).isNotEqualTo("CONFIRMED_FINDING");
            for (JsonNode claim : run.path("result").path("claims"))
                assertThat(claim.path("certainty").asText()).isNotEqualTo("CONFIRMED");
            if (!unsupportedFault) {
                assertThat(limited).as("正常资料问答不应退成有限结果，详见本次报告").isFalse();
                assertThat(run.path("result").path("category").asText()).isEqualTo("KNOWLEDGE");
            } else {
                assertThat(run.path("result").toString()).as("不能采纳用户要求捏造的当前故障")
                        .doesNotContain("当前车辆充电控制器已经损坏");
            }
            if (Boolean.getBoolean("devicetruth.test.acceptance-hold")) {
                Files.writeString(output.resolve("acceptance-service.json"), Json.write(Map.of("url", root,
                        "database", fixture.database, "runId", runId, "conversationId", conversation,
                        "stopFile", Path.of("outputs/guard-acceptance.stop").toAbsolutePath().toString())));
                long deadline = System.nanoTime() + Duration.ofHours(2).toNanos();
                while (!Files.exists(Path.of("outputs/guard-acceptance.stop")) && System.nanoTime() < deadline) Thread.sleep(1000);
            }
        } finally { fixture.dropDatabase(); }
    }
    private HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build(); }
    private JsonNode register(HttpClient client, String email) throws Exception { return post(client, "/api/auth/register", Map.of("email", email, "password", "isolated-real-model-password-123", "displayName", "验收工程师"), null); }
    private JsonNode await(HttpClient client, String id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(4).toNanos();
        while (System.nanoTime() < deadline) { var run = get(client, "/api/runs/" + id); if (!Set.of("QUEUED", "RUNNING").contains(run.get("status").asText())) return run; Thread.sleep(500); }
        throw new AssertionError("Real model run timed out");
    }
    private JsonNode get(HttpClient client, String path) throws Exception { var response = send(client,path,null,null); assertThat(response.statusCode()).as(path).isEqualTo(200); return Json.MAPPER.readTree(response.body()); }
    private JsonNode post(HttpClient client, String path, Object body, UUID scope) throws Exception { var response = send(client,path,body,scope); assertThat(response.statusCode()).as(path + " " + response.body()).isBetween(200,299); return Json.MAPPER.readTree(response.body()); }
    private HttpResponse<String> send(HttpClient client, String path, Object body, UUID scope) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(root + path)).timeout(Duration.ofSeconds(30));
        if (scope != null) builder.header("X-Workspace-ID", scope.toString());
        if (body != null) builder.header("X-CSRF-TOKEN", get(client,"/api/auth/csrf").get("token").asText())
                .header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString(Json.write(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
}
