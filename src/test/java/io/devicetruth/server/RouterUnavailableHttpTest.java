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
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"devicetruth.router.mode=online", "devicetruth.core.reasoner=offline-unsupported",
                "spring.profiles.active=legacy"})
class RouterUnavailableHttpTest {
    @Autowired TestRestTemplate rest;

    @Test void missingModelConfigurationReturns503WithStableCode() throws Exception {
        var created = rest.exchange("/api/sessions", HttpMethod.POST, HttpEntity.EMPTY, String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        UUID sessionId = UUID.fromString(Json.MAPPER.readTree(created.getBody()).path("sessionId").asText());

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"帮我看看这台设备\"}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(Json.MAPPER.readTree(response.getBody()).path("code").asText()).isEqualTo("ROUTER_UNAVAILABLE");
        assertThat(response.getBody()).doesNotContain("DASHSCOPE", "OPENAI", "apiKey");

        var messages = rest.getForEntity("/api/sessions/" + sessionId + "/messages", String.class);
        assertThat(Json.MAPPER.readTree(messages.getBody()).path("messages").size()).isZero();
    }

    @Test void serverStillServesHealthWhenRouterIsMisconfigured() throws Exception {
        var response = rest.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(Json.MAPPER.readTree(response.getBody()).path("status").asText()).isEqualTo("UP");
    }
}