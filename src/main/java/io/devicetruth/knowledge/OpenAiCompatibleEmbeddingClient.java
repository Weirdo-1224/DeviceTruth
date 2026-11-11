package io.devicetruth.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * OpenAI 兼容 /embeddings 适配（DashScope compatible-mode、OpenAI 或自建网关）。
 * 密钥与地址只来自服务端配置；异常与日志不携带正文、密钥或供应商响应。
 */
public final class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private final EmbeddingProfile profile;
    private final HttpClient http;

    public OpenAiCompatibleEmbeddingClient(EmbeddingProfile profile) {
        this.profile = profile;
        this.http = HttpClient.newBuilder().connectTimeout(profile.timeout()).build();
    }

    @Override public EmbeddingProfile profile() { return profile; }

    @Override public float[] embed(String text) {
        if (profile.apiKey().isBlank()) throw new EmbeddingException("EMBEDDING_CONFIGURATION_MISSING");
        if (profile.model().isBlank()) throw new EmbeddingException("EMBEDDING_CONFIGURATION_MISSING");
        var request = HttpRequest.newBuilder(endpoint())
                .timeout(profile.timeout())
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + profile.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload(text), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("EMBEDDING_UNAVAILABLE");
        } catch (IOException e) {
            // 消息只保留固定码，避免把 URL 或网络细节带进日志与公开错误。
            throw new EmbeddingException("EMBEDDING_UNAVAILABLE");
        }
        if (response.statusCode() != 200) throw new EmbeddingException("EMBEDDING_UNAVAILABLE");
        return parse(response.body());
    }

    private URI endpoint() {
        String base = profile.baseUrl();
        if (!base.startsWith("http://") && !base.startsWith("https://"))
            throw new EmbeddingException("EMBEDDING_CONFIGURATION_MISSING");
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        try {
            return URI.create(base + "/embeddings");
        } catch (IllegalArgumentException e) {
            throw new EmbeddingException("EMBEDDING_CONFIGURATION_MISSING");
        }
    }

    private String payload(String text) {
        var body = Json.MAPPER.createObjectNode();
        body.put("model", profile.model());
        body.putArray("input").add(text);
        // 不发送 encoding_format：DashScope 兼容模式与部分网关并不接受该 OpenAI 专有参数，
        // 默认返回即 float 数组，保持与千问 text-embedding-v4 的最小公共协议。
        if (profile.sendDimensions()) body.put("dimensions", profile.dimension());
        return Json.write(body);
    }

    private float[] parse(String responseBody) {
        JsonNode embedding;
        try {
            embedding = Json.MAPPER.readTree(responseBody).path("data").path(0).path("embedding");
        } catch (IOException e) {
            throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID");
        }
        if (!embedding.isArray() || embedding.isEmpty()) throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID");
        if (embedding.size() != profile.dimension()) throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            JsonNode value = embedding.get(i);
            if (!value.isNumber()) throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID");
            vector[i] = (float) value.asDouble();
        }
        return vector;
    }
}
