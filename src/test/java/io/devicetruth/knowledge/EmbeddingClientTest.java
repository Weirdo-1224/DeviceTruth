package io.devicetruth.knowledge;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * 离线 embedding 合同：确定性客户端只证明接口与向量性质，
 * OpenAI 兼容适配用本机 HTTP 替身验证请求/响应与错误映射；真实供应商由 02-03 在线验收。
 */
class EmbeddingClientTest {
    private static EmbeddingProfile deterministicProfile(int dimension) {
        return EmbeddingProfile.of("deterministic", "", "", "deterministic-char2", dimension, "1", 1000, false);
    }

    private static EmbeddingProfile openAiProfile(String baseUrl, int dimension, String apiKey,
                                                  boolean sendDimensions) {
        return EmbeddingProfile.of("openai", baseUrl, apiKey, "text-embedding-v4", dimension, "1", 3000,
                sendDimensions);
    }

    @Test void deterministicClientIsStableNormalizedAndRanksRelatedTextHigher() {
        var client = new DeterministicEmbeddingClient(deterministicProfile(64));
        float[] first = client.embed("直流快充中断");
        assertThat(client.embed("直流快充中断")).containsExactly(first);
        assertThat(first).hasSize(64);
        double norm = 0;
        for (float component : first) norm += component * component;
        assertThat(Math.sqrt(norm)).isCloseTo(1d, within(1e-4));
        assertThat(cosine(first, client.embed("直流快充时中断，先读充电桩日志")))
                .isGreaterThan(cosine(first, client.embed("咖啡机滤网清洁")));
    }

    @Test void deterministicClientRejectsEmptyInput() {
        var client = new DeterministicEmbeddingClient(deterministicProfile(32));
        assertThatThrownBy(() -> client.embed(" \n\t "))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("EMBEDDING_INPUT_EMPTY");
    }

    @Test void openAiCompatibleClientSendsFixedProfileAndParsesVector() throws Exception {
        var captured = new AtomicReference<String>();
        var server = server(200, "{\"data\":[{\"embedding\":[0.1,0.2,0.3,0.4]}]}", captured);
        try {
            var client = new OpenAiCompatibleEmbeddingClient(
                    openAiProfile("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", 4, "test-key", false));
            assertThat(client.embed("直流快充中断")).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
            assertThat(captured.get()).contains("\"model\":\"text-embedding-v4\"")
                    .contains("\"input\":[\"直流快充中断\"]")
                    .doesNotContain("\"dimensions\"")
                    .doesNotContain("\"encoding_format\"")
                    .doesNotContain("test-key");
        } finally {
            server.stop(0);
        }
    }

    @Test void openAiCompatibleClientHonoursDimensionsParameter() throws Exception {
        var captured = new AtomicReference<String>();
        var server = server(200, "{\"data\":[{\"embedding\":[1,0]}]}", captured);
        try {
            var client = new OpenAiCompatibleEmbeddingClient(
                    openAiProfile("http://127.0.0.1:" + server.getAddress().getPort(), 2, "test-key", true));
            client.embed("充电");
            assertThat(captured.get()).contains("\"dimensions\":2");
        } finally {
            server.stop(0);
        }
    }

    @Test void dimensionMismatchAndTransportFailuresUseStableCodes() throws Exception {
        var captured = new AtomicReference<String>();
        var wrongDimension = server(200, "{\"data\":[{\"embedding\":[0.1,0.2]}]}", captured);
        try {
            var client = new OpenAiCompatibleEmbeddingClient(
                    openAiProfile("http://127.0.0.1:" + wrongDimension.getAddress().getPort(), 4, "key", false));
            assertThatThrownBy(() -> client.embed("充电"))
                    .isInstanceOf(EmbeddingException.class)
                    .hasMessageContaining("EMBEDDING_DIMENSION_MISMATCH");
        } finally {
            wrongDimension.stop(0);
        }

        var failing = server(503, "{\"error\":\"vendor detail must not leak\"}", captured);
        try {
            var client = new OpenAiCompatibleEmbeddingClient(
                    openAiProfile("http://127.0.0.1:" + failing.getAddress().getPort(), 4, "key", false));
            assertThatThrownBy(() -> client.embed("充电"))
                    .isInstanceOf(EmbeddingException.class)
                    .hasMessageContaining("EMBEDDING_UNAVAILABLE")
                    .hasMessageNotContaining("vendor detail");
        } finally {
            failing.stop(0);
        }

        var malformed = server(200, "{\"data\":[]}", captured);
        try {
            var client = new OpenAiCompatibleEmbeddingClient(
                    openAiProfile("http://127.0.0.1:" + malformed.getAddress().getPort(), 4, "key", false));
            assertThatThrownBy(() -> client.embed("充电"))
                    .isInstanceOf(EmbeddingException.class)
                    .hasMessageContaining("EMBEDDING_RESPONSE_INVALID");
        } finally {
            malformed.stop(0);
        }
    }

    @Test void missingKeyOrBaseUrlFailsWithoutNetworkAccess() {
        var missingKey = new OpenAiCompatibleEmbeddingClient(
                openAiProfile("http://127.0.0.1:1", 4, "", false));
        assertThatThrownBy(() -> missingKey.embed("充电"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("EMBEDDING_CONFIGURATION_MISSING");
        var missingBaseUrl = new OpenAiCompatibleEmbeddingClient(openAiProfile("", 4, "key", false));
        assertThatThrownBy(() -> missingBaseUrl.embed("充电"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("EMBEDDING_CONFIGURATION_MISSING");
    }

    private static HttpServer server(int status, String body, AtomicReference<String> captured) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        return server;
    }

    private static double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
