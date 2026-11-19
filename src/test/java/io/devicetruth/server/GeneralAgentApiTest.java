package io.devicetruth.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * 02-02-T2：真实 PostgreSQL + HTTP 下的两轮同一 run 互动（E2E-07-basic）。
 * 使用产品装配与离线确定性 Agent 推理器；embedding 为确定性向量，故只证明
 * 「提交 → 追问（WAITING，释放线程）→ 补答（同 run 重新入队）→ 再检索 → 带引用答案」这条链路，
 * 不代表真实供应商的语义检索质量或跨进程恢复。
 */
class GeneralAgentApiTest {
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static ConfigurableApplicationContext app;
    private static ConfigurableApplicationContext otherOwnerApp;
    private static ConfigurableApplicationContext misconfiguredApp;
    private static TestRestTemplate rest;
    private static String base;
    private static String otherBase;
    private static String misconfiguredBase;
    private static JdbcTemplate jdbc;
    private static final String QUESTION_HINT = "交流慢充还是直流快充";

    @BeforeAll static void start() throws Exception {
        databaseBaseUrl = requiredEnvironment("DEVICETRUTH_TEST_PG_BASE_URL");
        username = requiredEnvironment("DEVICETRUTH_TEST_PG_USERNAME");
        password = requiredEnvironment("DEVICETRUTH_TEST_PG_PASSWORD");
        if (!databaseBaseUrl.matches("jdbc:postgresql://[A-Za-z0-9.\\-\\[\\]:]+/"))
            throw new IllegalArgumentException("Test PG base URL must contain host/port and no database or credentials");
        databaseName = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(databaseBaseUrl + "postgres", username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        app = open("owner-a", "project-a", "scripted");
        otherOwnerApp = open("owner-b", "project-a", "scripted");
        misconfiguredApp = open("owner-c", "project-c", "unsupported-agent-reasoner");
        base = base(app);
        otherBase = base(otherOwnerApp);
        misconfiguredBase = base(misconfiguredApp);
        rest = io.devicetruth.server.AuthenticatedProductTestSupport.client(app, otherOwnerApp, misconfiguredApp);
        jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
        importManual();
    }

    @AfterAll static void stop() throws Exception {
        for (ConfigurableApplicationContext context : List.of(app, otherOwnerApp, misconfiguredApp))
            if (context != null) context.close();
        if (databaseName != null && databaseName.matches("devicetruth_test_[0-9a-f]{32}")) {
            try (var connection = java.sql.DriverManager.getConnection(databaseBaseUrl + "postgres", username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + databaseName);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test configuration: " + name);
        return value;
    }

    private static ConfigurableApplicationContext open(String owner, String project, String agentReasoner) {
        return open(owner, project, agentReasoner, true);
    }

    private static ConfigurableApplicationContext open(String owner, String project, String agentReasoner,
                                                       boolean workerEnabled) {
        var context = new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--devicetruth.auth.cookie-secure=false", "--server.port=0", "--debug=false",
                "--logging.level.org.springframework=WARN", "--logging.level.org.springframework.jdbc=INFO",
                "--devicetruth.core.reasoner=scripted", "--devicetruth.agent.reasoner=" + agentReasoner,
                "--devicetruth.agent.worker-enabled=" + workerEnabled,
                "--spring.datasource.url=" + databaseBaseUrl + databaseName,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=" + owner, "--devicetruth.product.project-id=" + project,
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.embedding.model=deterministic-char2",
                "--devicetruth.embedding.dimension=1024", "--devicetruth.embedding.profile-revision=1");
        AuthenticatedProductTestSupport.register(context);
        return context;
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static void importManual() throws Exception {
        var response = post(base + "/api/knowledge/documents", Map.of(
                "title", "充电系统检修手册（演示资料）", "domain", "automotive-demo", "version", "2026.1",
                "filename", "manual.md", "mimeType", "text/markdown",
                "content", Files.readString(Path.of("datasets/knowledge/charging-system-manual.md"),
                        StandardCharsets.UTF_8)), null);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
    }

    private static HttpHeaders jsonHeaders(UUID requestKey) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (requestKey != null) headers.set("Idempotency-Key", requestKey.toString());
        return headers;
    }

    private static ResponseEntity<String> post(String url, Object body, UUID requestKey) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders(requestKey)), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return Json.MAPPER.readTree(response.getBody());
    }

    private String conversation(String baseUrl) throws Exception {
        var response = post(baseUrl + "/api/conversations", Map.of(), null);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response).path("conversationId").asText();
    }

    private JsonNode submit(String baseUrl, String conversationId, String text) throws Exception {
        var response = post(baseUrl + "/api/conversations/" + conversationId + "/messages",
                Map.of("schemaVersion", 1, "userMessage", text), UUID.randomUUID());
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(202);
        return json(response);
    }

    private ResponseEntity<String> reply(String baseUrl, String conversationId, String text, Map<String, Object> replyTo) {
        return post(baseUrl + "/api/conversations/" + conversationId + "/messages",
                Map.of("schemaVersion", 1, "userMessage", text, "replyTo", replyTo), UUID.randomUUID());
    }

    private JsonNode run(String baseUrl, String runId) throws Exception {
        var response = rest.getForEntity(baseUrl + "/api/runs/" + runId, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response);
    }

    private JsonNode detail(String baseUrl, String conversationId) throws Exception {
        var response = rest.getForEntity(baseUrl + "/api/conversations/" + conversationId, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response);
    }

    private JsonNode await(String baseUrl, String runId, String expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = run(baseUrl, runId);
            if (expected.equals(last.path("status").asText())) return last;
            Thread.sleep(100);
        }
        fail("run " + runId + " 未在 " + timeoutMs + "ms 内到达 " + expected + "，最后状态 "
                + (last == null ? "null" : last.path("status").asText()));
        return last;
    }

    @Test void twoRoundChargingConversationCompletesInTheSameRunWithCitations() throws Exception {
        JsonNode completed = twoRoundFlow();
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        // E2E-07-basic 可重复：同一流程再来一遍，终态与 run 数量必须一致
        JsonNode repeated = twoRoundFlow();
        assertThat(repeated.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(repeated.path("runId").asText()).isNotEqualTo(completed.path("runId").asText());
        assertThat(repeated.path("result").path("references").size()).isPositive();
    }

    /** 完整两轮：无故障码的充电问题 → 追问 → 补答 → 同一 run 再检索并给出带引用答案。 */
    private JsonNode twoRoundFlow() throws Exception {
        String conversationId = conversation(base);
        var accepted = submit(base, conversationId, "新能源汽车充电中断，没有故障码，应该先查什么资料？");
        String runId = accepted.path("runId").asText();
        assertThat(accepted.path("status").asText()).isEqualTo("QUEUED");

        // 第一轮：追问并释放 worker，run 进入 WAITING_FOR_USER
        JsonNode waiting = await(base, runId, "WAITING_FOR_USER", 20_000);
        var question = waiting.path("pendingQuestion");
        assertThat(question.isNull()).isFalse();
        assertThat(question.path("questionVersion").asInt()).isEqualTo(1);
        assertThat(question.path("question").asText()).contains(QUESTION_HINT);
        assertThat(UUID.fromString(question.path("pendingQuestionId").asText())).isNotNull();
        assertThat(waiting.path("result").isNull()).isTrue();

        var afterQuestion = detail(base, conversationId);
        assertThat(afterQuestion.path("messages").size()).isEqualTo(2);
        assertThat(afterQuestion.path("messages").get(0).path("role").asText()).isEqualTo("USER");
        assertThat(afterQuestion.path("messages").get(1).path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(afterQuestion.path("messages").get(1).path("content").asText()).contains(QUESTION_HINT);
        assertThat(afterQuestion.path("runs").size()).isEqualTo(1);
        // 第一轮已经真实检索过：引用可从持久层读回，并且带章节定位
        var references = Json.MAPPER.readTree(rest.getForObject(base + "/api/runs/" + runId + "/references", String.class));
        assertThat(references.size()).isPositive();
        assertThat(references.get(0).path("title").asText()).contains("充电系统检修手册");
        assertThat(references.get(0).path("sourceType").asText()).isEqualTo("KNOWLEDGE_DOCUMENT");
        assertThat(references.get(0).path("dataNature").asText()).isEqualTo("SIMULATED");

        // 第二轮：客户端带 runId/pendingQuestionId/questionVersion 补答，仍在同一个 run 上继续
        UUID replyKey = UUID.randomUUID();
        var replied = post(base + "/api/conversations/" + conversationId + "/messages", Map.of(
                "schemaVersion", 1, "userMessage", "直流快充，大约在 SOC 40% 时中断。",
                "replyTo", Map.of("runId", runId, "pendingQuestionId",
                        question.path("pendingQuestionId").asText(), "questionVersion", 1)), replyKey);
        assertThat(replied.getStatusCode().value()).as(replied.getBody()).isEqualTo(202);
        var replyRun = json(replied);
        assertThat(replyRun.path("runId").asText()).isEqualTo(runId);
        assertThat(replyRun.path("status").asText()).isEqualTo("QUEUED");

        JsonNode completed = await(base, runId, "COMPLETED", 20_000);
        assertThat(completed.path("pendingQuestion").isNull()).isTrue();
        var result = completed.path("result");
        assertThat(result.path("summary").asText()).isNotBlank();
        assertThat(result.path("category").asText()).isIn("KNOWLEDGE", "POSSIBLE_CAUSES", "HUMAN_REVIEW");
        assertThat(result.path("riskLevel").asText()).isIn("MEDIUM", "HIGH");
        assertThat(result.path("references").size()).isPositive();
        assertThat(result.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(result.path("claims").size()).isPositive();
        for (JsonNode claim : result.path("claims"))
            assertThat(claim.path("referenceIds").size()).isPositive();
        // 引用只来自本 run 已取回集合
        var collected = Json.MAPPER.readTree(rest.getForObject(base + "/api/runs/" + runId + "/references", String.class));
        List<String> collectedIds = new java.util.ArrayList<>();
        collected.forEach(node -> collectedIds.add(node.path("referenceId").asText()));
        for (JsonNode reference : result.path("references"))
            assertThat(collectedIds).contains(reference.path("referenceId").asText());

        var finalDetail = detail(base, conversationId);
        assertThat(finalDetail.path("messages").size()).isEqualTo(4);
        assertThat(finalDetail.path("messages").get(2).path("role").asText()).isEqualTo("USER");
        assertThat(finalDetail.path("messages").get(2).path("content").asText()).contains("直流快充");
        assertThat(finalDetail.path("messages").get(3).path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(finalDetail.path("runs").size()).isEqualTo(1);

        UUID runUuid = UUID.fromString(runId);
        // 补答消息与问题关联、run 归属都落在数据库里，不是只存在于内存
        assertThat(jdbc.queryForObject("SELECT reply_to_run_id FROM dt_message WHERE request_key=?",
                UUID.class, replyKey)).isEqualTo(runUuid);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_message WHERE run_id=? AND role='ASSISTANT'",
                Integer.class, runUuid)).isEqualTo(2);
        // 两轮各检索一次：公开事件只含验收所需的白名单工具摘要
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_TOOL'",
                Integer.class, runUuid)).isGreaterThanOrEqualTo(2);
        var toolEvents = Json.MAPPER.readTree(rest.getForObject(base + "/api/runs/" + runId + "/tool-events", String.class));
        assertThat(toolEvents.size()).isGreaterThanOrEqualTo(2);
        assertThat(toolEvents).allSatisfy(event -> {
            assertThat(event.path("toolName").asText()).isIn("search_knowledge", "activate_skill", "get_observations");
            assertThat(event.path("success").asBoolean()).isTrue();
            if ("search_knowledge".equals(event.path("toolName").asText()))
                assertThat(event.path("referenceCount").asInt()).isPositive();
            assertThat(event.has("payload")).isFalse();
        });
        assertThat(jdbc.queryForObject("SELECT (budget->'used'->>'steps')::int FROM dt_agent_run WHERE id=?",
                Integer.class, runUuid)).isGreaterThanOrEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_agent_run WHERE conversation_id=?",
                Integer.class, UUID.fromString(conversationId))).isEqualTo(1);
        return completed;
    }

    @Test void mismatchedOrConsumedReplyIsRejectedWithoutOpeningAnotherRun() throws Exception {
        String conversationId = conversation(base);
        String runId = submit(base, conversationId, "充电中断而且没有故障码，先看什么？").path("runId").asText();
        JsonNode waiting = await(base, runId, "WAITING_FOR_USER", 20_000);
        String questionId = waiting.path("pendingQuestion").path("pendingQuestionId").asText();
        int before = detail(base, conversationId).path("messages").size();

        // 版本不符：问题已被替换或过期，必须拒绝且状态不变
        var staleVersion = reply(base, conversationId, "直流快充", Map.of("runId", runId,
                "pendingQuestionId", questionId, "questionVersion", 99));
        assertThat(staleVersion.getStatusCode().value()).isEqualTo(409);
        assertThat(staleVersion.getBody()).contains("STALE_OR_UNRELATED_REPLY");
        // 问题 ID 不符
        var staleQuestion = reply(base, conversationId, "直流快充", Map.of("runId", runId,
                "pendingQuestionId", UUID.randomUUID().toString(), "questionVersion", 1));
        assertThat(staleQuestion.getStatusCode().value()).isEqualTo(409);
        // 三元组不完整：缺 questionVersion
        var incomplete = reply(base, conversationId, "直流快充", Map.of("runId", runId,
                "pendingQuestionId", questionId));
        assertThat(incomplete.getStatusCode().value()).isEqualTo(400);
        assertThat(incomplete.getBody()).contains("REPLY_TARGET_REQUIRED");
        // 多了未知关联字段
        var extra = reply(base, conversationId, "直流快充", Map.of("runId", runId,
                "pendingQuestionId", questionId, "questionVersion", 1, "ownerId", "owner-b"));
        assertThat(extra.getStatusCode().value()).isEqualTo(400);
        assertThat(extra.getBody()).contains("REPLY_TARGET_REQUIRED");
        // 错 run：不属于本会话（这里用真实存在的另一个会话的 run）
        String otherConversation = conversation(base);
        var otherRun = submit(base, otherConversation, "另一个会话的问题").path("runId").asText();
        var wrongRun = reply(base, conversationId, "直流快充", Map.of("runId", otherRun,
                "pendingQuestionId", questionId, "questionVersion", 1));
        assertThat(wrongRun.getStatusCode().value()).isEqualTo(409);
        assertThat(wrongRun.getBody()).contains("STALE_OR_UNRELATED_REPLY");
        // 所有拒绝都没有写消息、没有新建 run
        assertThat(detail(base, conversationId).path("messages").size()).isEqualTo(before);
        assertThat(detail(base, conversationId).path("runs").size()).isEqualTo(1);
        assertThat(run(base, runId).path("status").asText()).isEqualTo("WAITING_FOR_USER");

        // 正常补答后再次补答同一问题：已被消费，必须 409，不新建 run
        var accepted = reply(base, conversationId, "直流快充，SOC 约 35%", Map.of("runId", runId,
                "pendingQuestionId", questionId, "questionVersion", 1));
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        await(base, runId, "COMPLETED", 20_000);
        var consumed = reply(base, conversationId, "再补一句", Map.of("runId", runId,
                "pendingQuestionId", questionId, "questionVersion", 1));
        assertThat(consumed.getStatusCode().value()).isEqualTo(409);
        assertThat(consumed.getBody()).contains("STALE_OR_UNRELATED_REPLY");
        assertThat(detail(base, conversationId).path("runs").size()).isEqualTo(1);
    }

    @Test void otherOwnerCannotReadRunsOrReplyToThem() throws Exception {
        String conversationId = conversation(base);
        String runId = submit(base, conversationId, "本项目独有：充电中断问题").path("runId").asText();
        JsonNode waiting = await(base, runId, "WAITING_FOR_USER", 20_000);
        String questionId = waiting.path("pendingQuestion").path("pendingQuestionId").asText();

        var denied = rest.getForEntity(otherBase + "/api/runs/" + runId, String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);
        assertThat(denied.getBody()).contains("NOT_FOUND_OR_DENIED").doesNotContain("本项目独有");
        var deniedReferences = rest.getForEntity(otherBase + "/api/runs/" + runId + "/references", String.class);
        assertThat(deniedReferences.getStatusCode().value()).isEqualTo(404);
        var deniedTools = rest.getForEntity(otherBase + "/api/runs/" + runId + "/tool-events", String.class);
        assertThat(deniedTools.getStatusCode().value()).isEqualTo(404);
        var deniedConversation = rest.getForEntity(otherBase + "/api/conversations/" + conversationId, String.class);
        assertThat(deniedConversation.getStatusCode().value()).isEqualTo(404);
        var deniedReply = reply(otherBase, conversationId, "越权补答", Map.of("runId", runId,
                "pendingQuestionId", questionId, "questionVersion", 1));
        assertThat(deniedReply.getStatusCode().value()).isEqualTo(404);
        assertThat(run(base, runId).path("status").asText()).isEqualTo("WAITING_FOR_USER");

        // 本项目内正常补答仍可完成，说明拒绝没有破坏原 run
        assertThat(reply(base, conversationId, "直流快充", Map.of("runId", runId,
                "pendingQuestionId", questionId, "questionVersion", 1)).getStatusCode().value()).isEqualTo(202);
        await(base, runId, "COMPLETED", 20_000);
    }

    @Test void unsupportedReasonerConfigurationFailsTheRunInsteadOfAnswering() throws Exception {
        String conversationId = conversation(misconfiguredBase);
        String runId = submit(misconfiguredBase, conversationId, "模型配置错误时不能返回答案").path("runId").asText();
        JsonNode failed = await(misconfiguredBase, runId, "FAILED", 20_000);
        assertThat(failed.path("result").isNull()).isTrue();
        assertThat(failed.path("pendingQuestion").isNull()).isTrue();
        // 失败原因码必须对前端可见，而不是只有“执行失败”
        assertThat(failed.path("failureCode").asText()).isEqualTo("MODEL_CONFIGURATION_MISSING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_FAILED' "
                + "AND safe_payload->>'code'='MODEL_CONFIGURATION_MISSING'", Integer.class, UUID.fromString(runId)))
                .isEqualTo(1);
        var saved = detail(misconfiguredBase, conversationId);
        assertThat(saved.path("messages").size()).isEqualTo(1);
        assertThat(saved.path("messages").get(0).path("role").asText()).isEqualTo("USER");
    }

    @Test void queuedRunFromPreviousProcessIsHandedOffAtStartup() throws Exception {
        // 1) worker 关闭时提交：消息与 QUEUED 任务正常落库，但不会被推进
        String conversationId;
        String runId;
        try (var offline = open("owner-d", "project-d", "scripted", false)) {
            String offlineBase = base(offline);
            conversationId = conversation(offlineBase);
            runId = submit(offlineBase, conversationId, "充电中断，没有故障码，需要先查资料").path("runId").asText();
            assertThat(run(offlineBase, runId).path("status").asText()).isEqualTo("QUEUED");
        }
        // 2) 产品 worker 启动：启动扫描必须接手遗留 QUEUED，而不是永远排队
        try (var online = open("owner-d", "project-d", "scripted", true)) {
            String onlineBase = base(online);
            JsonNode handedOff = await(onlineBase, runId, "WAITING_FOR_USER", 20_000);
            assertThat(handedOff.path("pendingQuestion").isNull()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_run_event WHERE run_id=? AND type='RUN_QUEUED'",
                    Integer.class, UUID.fromString(runId))).isEqualTo(1);
            assertThat(detail(onlineBase, conversationId).path("messages").size()).isEqualTo(2);
        }
    }

    @Test void queuedRunsBeyondOldRecoveryPageAreAllHandedOffOrFailed() throws Exception {
        // 1) worker 关闭时积压 105 条 QUEUED，超过旧实现 100 条的启动扫描上限
        String conversationId;
        try (var offline = open("owner-e", "project-e", "scripted", false)) {
            String offlineBase = base(offline);
            conversationId = conversation(offlineBase);
            for (int i = 0; i < 105; i++)
                submit(offlineBase, conversationId, "积压任务 " + i + "：充电中断，没有故障码，需要先查资料");
            assertThat(queuedCount("owner-e")).isEqualTo(105);
        }
        // 2) 启动扫描必须覆盖全部遗留：默认 2 并发 + 队列 10，装不下的必须明确
        //    FAILED/QUEUE_REJECTED；任何情况下都不能留下永远 QUEUED 的幽灵任务。
        try (var online = open("owner-e", "project-e", "scripted", true)) {
            long deadline = System.currentTimeMillis() + 60_000;
            // 先等 QUEUED 清空，再等在途 RUNNING 落地：中途快照里 run 可能既非 WAITING 也非 FAILED
            while (activeCount("owner-e") != 0 && System.currentTimeMillis() < deadline) Thread.sleep(200);
            assertThat(queuedCount("owner-e")).as("启动接手后仍有遗留 QUEUED").isZero();
            assertThat(activeCount("owner-e")).as("接手后在途任务未落地").isZero();

            int waiting = jdbc.queryForObject("SELECT count(*) FROM dt_agent_run WHERE owner_id='owner-e' "
                    + "AND status='WAITING_FOR_USER'", Integer.class);
            int rejected = jdbc.queryForObject("SELECT count(*) FROM dt_agent_run r WHERE owner_id='owner-e' "
                    + "AND status='FAILED' AND (SELECT e.safe_payload->>'code' FROM dt_run_event e "
                    + "WHERE e.run_id=r.id AND e.type='RUN_FAILED' ORDER BY e.seq DESC LIMIT 1)='QUEUE_REJECTED'",
                    Integer.class);
            // 全部被接手（WAITING）或明确失败（QUEUE_REJECTED），且容量不足确实触发了显式拒绝
            assertThat(waiting + rejected).isEqualTo(105);
            assertThat(waiting).isPositive();
            assertThat(rejected).isPositive();
            //  handoff 语义不变：被接手的 run 已推进到带待答问题的 WAITING_FOR_USER
            String firstRun = jdbc.queryForObject("SELECT id::text FROM dt_agent_run WHERE owner_id='owner-e' "
                    + "AND status='WAITING_FOR_USER' ORDER BY created_at LIMIT 1", String.class);
            JsonNode run = run(base(online), firstRun);
            assertThat(run.path("pendingQuestion").isNull()).isFalse();
            assertThat(detail(base(online), conversationId).path("messages").size())
                    .isEqualTo(105 + waiting);
        }
    }

    private int queuedCount(String ownerId) {
        return jdbc.queryForObject("SELECT count(*) FROM dt_agent_run WHERE owner_id=? AND status='QUEUED'",
                Integer.class, ownerId);
    }

    /** 仍在推进中的任务数：QUEUED 或在途 RUNNING，归零才说明接手动作全部落地。 */
    private int activeCount(String ownerId) {
        return jdbc.queryForObject("SELECT count(*) FROM dt_agent_run WHERE owner_id=? "
                + "AND status IN ('QUEUED','RUNNING')", Integer.class, ownerId);
    }

    @Test void malformedBodyOrUnknownRunStaysRejected() throws Exception {
        String conversationId = conversation(base);
        String path = base + "/api/conversations/" + conversationId + "/messages";
        // 原生 JSON 类型错误仍是 400，不被宽松转换吃掉
        var numeric = post(path, "{\"schemaVersion\":1,\"userMessage\":123,\"replyTo\":{\"runId\":\"x\"}}", UUID.randomUUID());
        assertThat(numeric.getStatusCode().value()).isEqualTo(400);
        assertThat(numeric.getBody()).contains("INVALID_REQUEST");
        // 关联字段类型错误 → REPLY_TARGET_REQUIRED，而不是 500
        var badUuid = reply(base, conversationId, "充电中断", Map.of("runId", "not-a-uuid",
                "pendingQuestionId", "nope", "questionVersion", 1));
        assertThat(badUuid.getStatusCode().value()).isEqualTo(400);
        assertThat(badUuid.getBody()).contains("REPLY_TARGET_REQUIRED");
        // 不存在的 run 一律 404，不区分“不存在”和“无权”
        var unknown = rest.getForEntity(base + "/api/runs/" + UUID.randomUUID(), String.class);
        assertThat(unknown.getStatusCode().value()).isEqualTo(404);
        assertThat(detail(base, conversationId).path("messages").size()).isZero();
        assertThat(detail(base, conversationId).path("runs").size()).isZero();
    }
}
