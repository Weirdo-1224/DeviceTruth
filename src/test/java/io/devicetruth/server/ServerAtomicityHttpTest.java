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
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.assertThat;

/** 复现 Review M1-P1-04：奇数容量下被拒绝的交互不得留下半条 USER 消息。 */
@SpringBootTest(classes = DeviceTruthServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"devicetruth.session.max-sessions=2",
                "devicetruth.session.max-messages-per-session=3", "spring.profiles.active=legacy"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ServerAtomicityHttpTest {
    @Autowired TestRestTemplate rest;

    private UUID createSession() throws Exception {
        var response = rest.exchange("/api/sessions", HttpMethod.POST, HttpEntity.EMPTY, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return UUID.fromString(Json.MAPPER.readTree(response.getBody()).path("sessionId").asText());
    }

    private ResponseEntity<String> send(UUID sessionId, String content) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = "{\"content\":\"" + content + "\"}";
        return rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    @Test void rejectedInteractionDoesNotLeaveHalfMessage() throws Exception {
        UUID sessionId = createSession();
        assertThat(send(sessionId, "CAR-B 帮我看看").getStatusCode().value()).isEqualTo(200);
        var rejected = send(sessionId, "CAR-C 帮我看看");
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);

        var messages = rest.getForEntity("/api/sessions/" + sessionId + "/messages", String.class);
        var list = Json.MAPPER.readTree(messages.getBody()).path("messages");
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).path("role").asText()).isEqualTo("USER");
        assertThat(list.get(1).path("role").asText()).isEqualTo("SYSTEM");
        assertThat(messages.getBody()).doesNotContain("CAR-C");
    }

    @Test void rejectedQueryDoesNotLeaveHalfMessage() throws Exception {
        UUID sessionId = createSession();
        assertThat(send(sessionId, "CAR-B 帮我看看").getStatusCode().value()).isEqualTo(200);
        var rejected = send(sessionId, "CAR-C 的出厂配置是什么？");
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(Json.MAPPER.readTree(rejected.getBody()).path("code").asText())
                .isEqualTo("SESSION_CAPACITY_EXCEEDED");

        var messages = rest.getForEntity("/api/sessions/" + sessionId + "/messages", String.class);
        var list = Json.MAPPER.readTree(messages.getBody()).path("messages");
        assertThat(list.size()).isEqualTo(2);
        assertThat(messages.getBody()).doesNotContain("CAR-C");
    }
}