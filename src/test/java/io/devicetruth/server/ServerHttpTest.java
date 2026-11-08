package io.devicetruth.server;

import io.devicetruth.adapter.Json;
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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DeviceTruthServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerHttpTest {
    @Autowired TestRestTemplate rest;

    private ResponseEntity<String> postJson(String path, String json) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private UUID createSession() throws Exception {
        var response = rest.exchange("/api/sessions", HttpMethod.POST, HttpEntity.EMPTY, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return UUID.fromString(Json.MAPPER.readTree(response.getBody()).path("sessionId").asText());
    }

    private ResponseEntity<String> send(UUID sessionId, String content, String assetId) throws Exception {
        var body = Json.MAPPER.writeValueAsString(new java.util.LinkedHashMap<String, String>() {{
            put("content", content);
            if (assetId != null) put("assetId", assetId);
        }});
        return postJson("/api/sessions/" + sessionId + "/messages", body);
    }

    @Test void healthEndpointIsUpAndMarkedSimulated() throws Exception {
        var response = rest.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.path("dataNature").asText()).isEqualTo("SIMULATED");
    }

    @Test void createSessionReturns201WithRealUuid() throws Exception {
        var response = rest.exchange("/api/sessions", HttpMethod.POST, HttpEntity.EMPTY, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThatCodeDoesNotThrow(() -> UUID.fromString(body.path("sessionId").asText()));
        assertThat(body.path("createdAt").asText()).isNotBlank();
        assertThat(body.path("dataNature").asText()).isEqualTo("SIMULATED");
    }

    @Test void newSessionHasEmptyMessageList() throws Exception {
        UUID sessionId = createSession();
        var response = rest.getForEntity("/api/sessions/" + sessionId + "/messages", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("sessionId").asText()).isEqualTo(sessionId.toString());
        assertThat(body.path("messages").isArray()).isTrue();
        assertThat(body.path("messages").size()).isZero();
        assertThat(body.path("dataNature").asText()).isEqualTo("SIMULATED");
    }

    @Test void clarifyReturns200AndRecordsUserAndSystemMessages() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "帮我看看这台设备", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("route").asText()).isEqualTo("CLARIFY");
        assertThat(body.path("reply").asText()).isNotBlank();
        assertThat(body.path("dataNature").asText()).isEqualTo("SIMULATED");

        var messages = rest.getForEntity("/api/sessions/" + sessionId + "/messages", String.class);
        var list = Json.MAPPER.readTree(messages.getBody()).path("messages");
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).path("role").asText()).isEqualTo("USER");
        assertThat(list.get(0).path("route").asText()).isEqualTo("CLARIFY");
        assertThat(list.get(1).path("role").asText()).isEqualTo("SYSTEM");
        assertThat(list.get(1).path("route").asText()).isEqualTo("CLARIFY");
    }

    @Test void assetIdHintIsValidatedAndEchoedWithoutFakingEvidence() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "帮我看看", "CAR-B");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("route").asText()).isEqualTo("CLARIFY");
        assertThat(body.path("assetId").asText()).isEqualTo("CAR-B");
        assertThat(body.has("evidence")).isFalse();
    }

    @Test void unregisteredAssetIsExplicitlyUnsupported() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "SN-0245 通信超时，帮我诊断", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("route").asText()).isEqualTo("UNSUPPORTED");
        assertThat(body.path("assetId").isNull()).isTrue();
        assertThat(body.path("reply").asText()).contains("未登记");
    }

    @Test void dangerousOperationIsExplicitlyUnsupported() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "帮我重启 CAR-B", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(Json.MAPPER.readTree(response.getBody()).path("route").asText()).isEqualTo("UNSUPPORTED");
    }

    @Test void diagnoseStaysNotImplementedAndPersistsNoMessages() throws Exception {
        // M2 只完成 Goal→Request 映射；诊断执行归 M3，HTTP 仍须明确 501 且不写会话。
        UUID sessionId = createSession();
        var diagnose = send(sessionId, "CAR-B 通信超时，帮我诊断", null);
        assertThat(diagnose.getStatusCode().value()).isEqualTo(501);
        assertThat(Json.MAPPER.readTree(diagnose.getBody()).path("code").asText()).isEqualTo("NOT_IMPLEMENTED");

        var messages = rest.getForEntity("/api/sessions/" + sessionId + "/messages", String.class);
        assertThat(Json.MAPPER.readTree(messages.getBody()).path("messages").size()).isZero();
    }

    @Test void explicitUnknownAssetWithConfirmedContextIsUnsupportedNotDiagnosed() throws Exception {
        UUID sessionId = createSession();
        assertThat(send(sessionId, "CAR-B 帮我看看", null).getStatusCode().value()).isEqualTo(200);
        var response = send(sessionId, "CAR-Z 通信超时", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("route").asText()).isEqualTo("UNSUPPORTED");
        assertThat(body.path("reply").asText()).contains("未登记");
    }

    @Test void multipleAssetsRequireClarificationNotDiagnose() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "CAR-B 和 CAR-C 通信超时", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("route").asText()).isEqualTo("CLARIFY");
        assertThat(body.path("reply").asText()).contains("多个设备");
    }

    @Test void invalidContentIs400() throws Exception {
        UUID sessionId = createSession();
        var blank = postJson("/api/sessions/" + sessionId + "/messages", "{\"content\":\"   \"}");
        assertThat(blank.getStatusCode().value()).isEqualTo(400);
        assertThat(Json.MAPPER.readTree(blank.getBody()).path("code").asText()).isEqualTo("INVALID_REQUEST");

        var tooLong = send(sessionId, "a".repeat(2001), null);
        assertThat(tooLong.getStatusCode().value()).isEqualTo(400);
        assertThat(Json.MAPPER.readTree(tooLong.getBody()).path("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test void invalidAssetIdSyntaxIs400() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "通信超时", "bad id!");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(Json.MAPPER.readTree(response.getBody()).path("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test void malformedJsonIs400() {
        UUID sessionId = UUID.randomUUID();
        var response = postJson("/api/sessions/" + sessionId + "/messages", "{\"content\":");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test void unknownSessionIs404ForReadAndWrite() throws Exception {
        UUID unknown = UUID.randomUUID();
        assertThat(rest.getForEntity("/api/sessions/" + unknown + "/messages", String.class)
                .getStatusCode().value()).isEqualTo(404);
        var post = postJson("/api/sessions/" + unknown + "/messages", "{\"content\":\"帮我看看\"}");
        assertThat(post.getStatusCode().value()).isEqualTo(404);
        assertThat(Json.MAPPER.readTree(post.getBody()).path("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test void malformedSessionIdIs400() {
        var response = rest.getForEntity("/api/sessions/not-a-uuid/messages", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test void clientCannotInjectRouterModeOrModelSettings() throws Exception {
        UUID sessionId = createSession();
        var response = postJson("/api/sessions/" + sessionId + "/messages",
                "{\"content\":\"帮我看看这台设备\",\"reasoner\":\"online\",\"provider\":\"openai\","
                        + "\"apiKey\":\"leak\",\"baseUrl\":\"http://example.invalid\",\"model\":\"x\"}");
        // 未知字段被严格拒绝（fail-on-unknown-properties=true），客户端无法切换模式或注入模型配置。
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(Json.MAPPER.readTree(response.getBody()).path("code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody()).doesNotContain("leak");
    }

    private static void assertThatCodeDoesNotThrow(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            throw new AssertionError("sessionId is not a valid UUID", e);
        }
    }
}