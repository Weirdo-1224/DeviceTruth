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

/** M2 真实 HTTP 验收：QUERY 两类查询、来源字段、双时间、未知资产不混用证据。 */
@SpringBootTest(classes = DeviceTruthServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueryHttpTest {
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

    @Test void profileQueryReturns200WithRealFactorySource() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "CAR-B 的出厂控制器型号？");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("route").asText()).isEqualTo("QUERY");
        assertThat(body.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(body.path("assetId").asText()).isEqualTo("CAR-B");
        assertThat(body.path("requestedAt").asText()).isNotBlank();

        var query = body.path("query");
        assertThat(query.path("scope").asText()).isEqualTo("ASSET_PROFILE");
        assertThat(query.path("assetId").asText()).isEqualTo("CAR-B");
        assertThat(query.path("dataNature").asText()).isEqualTo("SIMULATED");
        assertThat(query.path("answer").asText()).contains("D-120A");
        assertThat(query.path("limitations").size()).isGreaterThan(0);
        var source = query.path("sources").get(0);
        assertThat(source.path("evidenceId").asText()).isEqualTo("CAR-B-FACTORY");
        assertThat(source.path("sourceRef").asText()).isEqualTo("simulated:CAR-B/FACTORY");
        assertThat(source.path("sourceType").asText()).isEqualTo("FACTORY");
        assertThat(source.path("dataNature").asText()).isEqualTo("SIMULATED");
        // 响应中不得出现内部路径或诊断结论字段。
        assertThat(response.getBody()).doesNotContain("reportPath", "stopReason", "diagnosisSummary");
    }

    @Test void historyQueryReturnsReplacementRecordsOnly() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "CAR-B 发生过哪些更换？");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var query = Json.MAPPER.readTree(response.getBody()).path("query");
        assertThat(query.path("scope").asText()).isEqualTo("MAINTENANCE_HISTORY");
        assertThat(query.path("sources").size()).isEqualTo(1);
        assertThat(query.path("sources").get(0).path("evidenceId").asText()).isEqualTo("CAR-B-REPLACE");
        assertThat(query.path("sources").get(0).path("sourceType").asText()).isEqualTo("REPLACEMENT");
        assertThat(query.path("answer").asText()).contains("不等于当前现场确认");
    }

    @Test void queryRecordsUserAndSystemMessages() throws Exception {
        UUID sessionId = createSession();
        assertThat(send(sessionId, "CAR-C 的出厂配置是什么？").getStatusCode().value()).isEqualTo(200);
        var messages = rest.getForEntity("/api/sessions/" + sessionId + "/messages", String.class);
        var list = Json.MAPPER.readTree(messages.getBody()).path("messages");
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).path("role").asText()).isEqualTo("USER");
        assertThat(list.get(0).path("route").asText()).isEqualTo("QUERY");
        assertThat(list.get(1).path("role").asText()).isEqualTo("SYSTEM");
        assertThat(list.get(1).path("content").asText()).contains("D-120A");
    }

    @Test void queryAboutUnregisteredAssetIsUnsupportedAndNeverReusesCarBEvidence() throws Exception {
        UUID sessionId = createSession();
        assertThat(send(sessionId, "CAR-B 帮我看看").getStatusCode().value()).isEqualTo(200);
        var response = send(sessionId, "SN-0245 的出厂配置是什么？");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = Json.MAPPER.readTree(response.getBody());
        assertThat(body.path("route").asText()).isEqualTo("UNSUPPORTED");
        assertThat(body.path("query").isNull()).isTrue();
        assertThat(response.getBody()).doesNotContain("CAR-B-FACTORY", "simulated:CAR-B");
    }

    @Test void emptyHistorySaysNoRecordsInsteadOfInventing() throws Exception {
        UUID sessionId = createSession();
        var response = send(sessionId, "CAR-A 发生过哪些更换？");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var query = Json.MAPPER.readTree(response.getBody()).path("query");
        assertThat(query.path("sources").size()).isZero();
        assertThat(query.path("answer").asText()).contains("没有维修/更换类模拟记录");
    }
}