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

@SpringBootTest(classes = DeviceTruthServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"devicetruth.session.max-sessions=2",
                "devicetruth.session.max-messages-per-session=2"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ServerCapacityHttpTest {
    @Autowired TestRestTemplate rest;

    private ResponseEntity<String> create() {
        return rest.exchange("/api/sessions", HttpMethod.POST, HttpEntity.EMPTY, String.class);
    }

    private ResponseEntity<String> send(UUID sessionId, String content) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = "{\"content\":\"" + content + "\"}";
        return rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    @Test void thirdSessionIsRejectedWith429() throws Exception {
        assertThat(create().getStatusCode().value()).isEqualTo(201);
        assertThat(create().getStatusCode().value()).isEqualTo(201);
        var rejected = create();
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(Json.MAPPER.readTree(rejected.getBody()).path("code").asText())
                .isEqualTo("SESSION_CAPACITY_EXCEEDED");
    }

    @Test void messageBeyondSessionLimitIsRejectedWith429WithoutPartialWrite() throws Exception {
        UUID sessionId = UUID.fromString(Json.MAPPER.readTree(create().getBody())
                .path("sessionId").asText());
        assertThat(send(sessionId, "帮我看看这台设备").getStatusCode().value()).isEqualTo(200);
        var rejected = send(sessionId, "帮我看看这台设备");
        assertThat(rejected.getStatusCode().value()).isEqualTo(429);
        assertThat(Json.MAPPER.readTree(rejected.getBody()).path("code").asText())
                .isEqualTo("SESSION_CAPACITY_EXCEEDED");

        var messages = rest.getForEntity("/api/sessions/" + sessionId + "/messages", String.class);
        var list = Json.MAPPER.readTree(messages.getBody()).path("messages");
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(1).path("role").asText()).isEqualTo("SYSTEM");
    }
}