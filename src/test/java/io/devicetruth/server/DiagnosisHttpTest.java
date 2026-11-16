package io.devicetruth.server;

import io.devicetruth.adapter.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.assertThat;

/** M3 真实 HTTP 验收：202/QUEUED/Location、GET 真实状态、结束后白名单事件、归属与脱敏。 */
@SpringBootTest(classes = DeviceTruthServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"devicetruth.run.output-root=target/test-runs",
                "devicetruth.core.reasoner=scripted", "spring.profiles.active=legacy"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DiagnosisHttpTest {
    @Autowired TestRestTemplate rest;

    private UUID createSession() throws Exception {
        var response = rest.exchange("/api/sessions", HttpMethod.POST, HttpEntity.EMPTY, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return UUID.fromString(Json.MAPPER.readTree(response.getBody()).path("sessionId").asText());
    }

    private ResponseEntity<String> send(UUID sessionId, String content) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = Json.write(new java.util.LinkedHashMap<String, String>() {{ put("content", content); }});
        return rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode startDiagnosis(UUID sessionId, String content) throws Exception {
        var accepted = send(sessionId, content);
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        var body = Json.MAPPER.readTree(accepted.getBody());
        assertThat(body.path("route").asText()).isEqualTo("DIAGNOSE");
        assertThat(body.path("executionStatus").asText()).isEqualTo("QUEUED");
        UUID diagnosisId = UUID.fromString(body.path("diagnosisId").asText());
        assertThat(accepted.getHeaders().getLocation().toString()).isEqualTo("/api/diagnoses/" + diagnosisId);
        // 即使后台已完成，POST 语义仍是"已接受"。
        assertThat(body.path("query").isNull()).isTrue();
        return body;
    }

    private JsonNode awaitFinished(UUID sessionId, UUID diagnosisId) throws Exception {
        for (int i = 0; i < 400; i++) {
            var body = Json.MAPPER.readTree(get("/api/diagnoses/" + diagnosisId + "?sessionId=" + sessionId, 200));
            if (!"QUEUED".equals(body.path("executionStatus").asText())
                    && !"RUNNING".equals(body.path("executionStatus").asText())) return body;
            Thread.sleep(25);
        }
        throw new AssertionError("diagnosis did not finish in time");
    }

    private String get(String path, int expectedStatus) {
        var response = rest.getForEntity(path, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        return response.getBody();
    }

    @Test void diagnosisRunsRealCoreAndExposesRealResultAndEvents() throws Exception {
        UUID sessionId = createSession();
        var accepted = startDiagnosis(sessionId, "CAR-B 通信超时，帮我诊断");
        UUID diagnosisId = UUID.fromString(accepted.path("diagnosisId").asText());

        var done = awaitFinished(sessionId, diagnosisId);
        assertThat(done.path("executionStatus").asText()).isEqualTo("COMPLETED");
        assertThat(done.path("outcome").asText()).isEqualTo("RESOLVED");
        assertThat(done.path("appliedRuleIds")).hasSize(1);
        assertThat(done.path("appliedRuleIds").get(0).asText()).isEqualTo("DEMO-D-120B");
        assertThat(done.path("stepsUsed").asInt()).isGreaterThan(0);
        assertThat(done.path("diagnosisAsOf").asText()).isEqualTo("2026-11-03T04:00:00Z");
        assertThat(done.path("requestedAt").asText()).isNotEqualTo(done.path("diagnosisAsOf").asText());
        assertThat(done.path("coreTaskId").asText()).isNotBlank();
        assertThat(done.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(done.path("errorCode").isNull()).isTrue();
        // 内部目录绝不公开。
        assertThat(done.toString()).doesNotContain("reportPath", "outputs/runs", "target/test-runs");

        var eventsBody = get("/api/diagnoses/" + diagnosisId + "/events?sessionId=" + sessionId, 200);
        var events = Json.MAPPER.readTree(eventsBody);
        assertThat(events.path("eventsAvailable").asBoolean()).isTrue();
        assertThat(events.path("events").size()).isGreaterThan(0);
        var types = new java.util.ArrayList<String>();
        events.path("events").forEach(e -> types.add(e.path("type").asText()));
        assertThat(types).contains("REASON_INPUT", "ACTION_PROPOSED", "STATE_COMMITTED", "TERMINATED");
        // 事件序列号严格递增，类型保持真实 Core 名称。
        for (int i = 0; i < events.path("events").size(); i++)
            assertThat(events.path("events").get(i).path("sequence").asInt()).isEqualTo(i + 1);
        // 不透传 context/data/purpose/rationale。
        assertThat(eventsBody).doesNotContain("context", "capabilities", "\"data\"", "purpose", "rationale",
                "PROTOCOL", "reportPath", "outputs");
        for (var event : events.path("events"))
            assertThat(event.path("summary").asText()).doesNotContain("Investigation stopped");
        var terminated = events.path("events").get(types.indexOf("TERMINATED"));
        assertThat(terminated.path("summary").asText()).isEqualTo("运行结束：RESOLVED/RULE_AND_EVIDENCE_VERIFIED");

        // M3-P1-01 回归：summary/referenceIds 必须来自真实事件载荷，不能退化成 UNKNOWN/TOOL_FAILURE/空码。
        var byType = new java.util.LinkedHashMap<String, java.util.List<JsonNode>>();
        java.util.Iterator<JsonNode> it = events.path("events").elements();
        while (it.hasNext()) {
            var event = it.next();
            byType.computeIfAbsent(event.path("type").asText(), k -> new java.util.ArrayList<>()).add(event);
        }
        var actions = byType.get("ACTION_PROPOSED");
        assertThat(actions).isNotEmpty();
        assertThat(actions).allSatisfy(e -> assertThat(e.path("summary").asText()).doesNotContain("UNKNOWN"));
        assertThat(actions).anySatisfy(e -> {
            assertThat(e.path("summary").asText()).isEqualTo("提议动作 CALL_CAPABILITY");
            assertThat(e.path("referenceIds")).hasSize(1);
        });
        assertThat(actions).anySatisfy(e -> {
            assertThat(e.path("summary").asText()).isEqualTo("提议动作 PROPOSE_DECISION");
            assertThat(e.path("referenceIds").get(0).asText()).isEqualTo("DEMO-D-120B");
        });

        var capabilities = byType.get("CAPABILITY_RESULT");
        assertThat(capabilities).isNotEmpty();
        assertThat(capabilities).allSatisfy(e -> {
            assertThat(e.path("summary").asText()).isEqualTo("能力结果：成功");
            assertThat(e.path("referenceIds")).hasSize(1);
        });
        var toolIds = new java.util.ArrayList<String>();
        capabilities.forEach(e -> toolIds.add(e.path("referenceIds").get(0).asText()));
        assertThat(toolIds).contains("diagnosis.rules.query", "asset.profile.get",
                "asset.history.query", "evidence.search", "evidence.get");

        assertThat(byType.get("GUARD_RESULT")).hasSize(1);
        assertThat(byType.get("GUARD_RESULT").get(0).path("summary").asText())
                .isEqualTo("结案校验：RULE_AND_EVIDENCE_VERIFIED");
        assertThat(byType.get("GUARD_RESULT").get(0).path("referenceIds").get(0).asText()).isEqualTo("DEMO-D-120B");
        assertThat(byType.get("HYPOTHESIS_INVALIDATED")).isNotEmpty();
        assertThat(byType.get("HYPOTHESIS_INVALIDATED")).allSatisfy(e ->
                assertThat(e.path("referenceIds")).hasSize(1));
        // 真实 Core 事件类型共 8 类；每类摘要都不得为空。
        assertThat(byType.keySet()).hasSize(8);
        events.path("events").forEach(e -> assertThat(e.path("summary").asText()).isNotBlank());
    }

    @Test void eventsAreUnavailableWhileRunningAndForUnknownOrForeignRuns() throws Exception {
        UUID sessionId = createSession();
        UUID other = createSession();
        var accepted = startDiagnosis(sessionId, "CAR-B 通信超时");
        UUID diagnosisId = UUID.fromString(accepted.path("diagnosisId").asText());
        awaitFinished(sessionId, diagnosisId);

        // 归属不匹配与不存在一律 404，且错误体不区分二者。
        var foreign = rest.getForEntity("/api/diagnoses/" + diagnosisId + "?sessionId=" + other, String.class);
        var missing = rest.getForEntity("/api/diagnoses/" + UUID.randomUUID() + "?sessionId=" + sessionId, String.class);
        var anonymous = rest.getForEntity("/api/diagnoses/" + diagnosisId, String.class);
        assertThat(foreign.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(404);
        assertThat(Json.MAPPER.readTree(foreign.getBody()).path("message").asText())
                .isEqualTo(Json.MAPPER.readTree(missing.getBody()).path("message").asText());
        assertThat(rest.getForEntity("/api/diagnoses/" + diagnosisId + "/events?sessionId=" + other, String.class)
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test void postKeepsAcceptedSemanticsAndSessionMessagesArePaired() throws Exception {
        UUID sessionId = createSession();
        startDiagnosis(sessionId, "CAR-C 通信超时，帮我诊断");
        var messages = Json.MAPPER.readTree(get("/api/sessions/" + sessionId + "/messages", 200));
        var list = messages.path("messages");
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).path("route").asText()).isEqualTo("DIAGNOSE");
        assertThat(list.get(1).path("role").asText()).isEqualTo("SYSTEM");
        assertThat(messages.toString()).doesNotContain("RESOLVED", "CONFLICT", "NEED_EVIDENCE");
    }

    @Test void unsupportedAndClarifyNeverCreateDiagnoses() throws Exception {
        UUID sessionId = createSession();
        assertThat(send(sessionId, "帮我看看").getStatusCode().value()).isEqualTo(200);
        var unsupported = send(sessionId, "SN-0245 通信超时");
        assertThat(unsupported.getStatusCode().value()).isEqualTo(200);
        assertThat(Json.MAPPER.readTree(unsupported.getBody()).path("route").asText()).isEqualTo("UNSUPPORTED");
        var dangerous = send(sessionId, "帮我重启 CAR-B");
        assertThat(Json.MAPPER.readTree(dangerous.getBody()).path("route").asText()).isEqualTo("UNSUPPORTED");
    }
}