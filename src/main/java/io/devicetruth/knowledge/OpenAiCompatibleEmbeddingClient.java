package io.devicetruth.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Production embeddings: bounded batches/retries, no credentials/vendor bodies in errors. */
public final class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    public interface RequestObserver {
        default void beforeAttempt(String requestId, int attempt, List<String> texts) {}
        default void afterAttempt(String requestId, int attempt, Long providerTokens, Integer httpStatus) {}
    }
    public record Usage(long requests, long retries, long successfulRequests, long inputTokens) {}
    private final EmbeddingProfile profile;
    private final HttpClient http;
    private final RequestObserver observer;
    private final AtomicLong requests = new AtomicLong(), retries = new AtomicLong();
    private final AtomicLong successes = new AtomicLong(), tokens = new AtomicLong();
    public OpenAiCompatibleEmbeddingClient(EmbeddingProfile profile) {
        this(profile, new RequestObserver() {});
    }
    public OpenAiCompatibleEmbeddingClient(EmbeddingProfile profile, RequestObserver observer) {
        this.profile = profile;
        this.observer = java.util.Objects.requireNonNull(observer);
        this.http = HttpClient.newBuilder().connectTimeout(profile.timeout()).build();
    }
    @Override public EmbeddingProfile profile() { return profile; }
    public Usage usage() { return new Usage(requests.get(), retries.get(), successes.get(), tokens.get()); }
    @Override public float[] embed(String text) { return embedBatch(List.of(text)).getFirst(); }
    @Override public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.size() > 10) throw new EmbeddingException("EMBEDDING_BATCH_SIZE_INVALID");
        if (texts.stream().anyMatch(t -> t == null || t.isBlank())) throw new EmbeddingException("EMBEDDING_INPUT_EMPTY");
        if (profile.apiKey().isBlank()) throw new EmbeddingException("EMBEDDING_CONFIGURATION_MISSING");
        var body = Json.MAPPER.createObjectNode(); body.put("model", profile.model());
        var inputs = body.putArray("input"); texts.forEach(inputs::add);
        if (profile.sendDimensions()) body.put("dimensions", profile.dimension());
        var request = HttpRequest.newBuilder(endpoint()).timeout(profile.timeout())
                .header("Content-Type", "application/json; charset=UTF-8").header("Authorization", "Bearer " + profile.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8)).build();
        String requestId = java.util.UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 3; attempt++) {
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                throw new EmbeddingException("EMBEDDING_EXTERNAL_CALL_IN_TRANSACTION");
            observer.beforeAttempt(requestId, attempt, texts);
            requests.incrementAndGet();
            try {
                var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                Long reportedTokens = null;
                if (response.statusCode() == 200) {
                    try {
                        var usage = Json.MAPPER.readTree(response.body()).path("usage").path("total_tokens");
                        if (usage.isIntegralNumber() && usage.asLong() >= 0) reportedTokens = usage.asLong();
                    } catch (Exception ignored) { /* Unknown usage retains the budget reservation. */ }
                }
                observer.afterAttempt(requestId, attempt, reportedTokens, response.statusCode());
                if (response.statusCode() == 200) {
                    var vectors = parse(response.body(), texts.size()); successes.incrementAndGet(); return vectors;
                }
                if ((response.statusCode() != 429 && response.statusCode() < 500) || attempt == 2)
                    throw new EmbeddingException(response.statusCode() == 429 ? "EMBEDDING_RATE_LIMITED" : "EMBEDDING_UNAVAILABLE");
                long delay = 250L << attempt;
                try { delay = Math.max(delay, Math.min(2000, Long.parseLong(response.headers().firstValue("Retry-After").orElse("0"))*1000)); }
                catch (NumberFormatException ignored) { }
                retries.incrementAndGet(); pause(delay);
            } catch (IOException e) {
                observer.afterAttempt(requestId, attempt, null, null);
                if (attempt == 2) throw new EmbeddingException("EMBEDDING_UNAVAILABLE");
                retries.incrementAndGet(); pause(250L << attempt);
            } catch (InterruptedException e) {
                observer.afterAttempt(requestId, attempt, null, null);
                Thread.currentThread().interrupt(); throw new EmbeddingException("EMBEDDING_UNAVAILABLE");
            }
        }
        throw new EmbeddingException("EMBEDDING_UNAVAILABLE");
    }
    private static void pause(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new EmbeddingException("EMBEDDING_UNAVAILABLE"); }
    }
    private URI endpoint() {
        String base = profile.baseUrl();
        if (!base.startsWith("http://") && !base.startsWith("https://")) throw new EmbeddingException("EMBEDDING_CONFIGURATION_MISSING");
        while (base.endsWith("/")) base = base.substring(0, base.length()-1);
        try { return URI.create(base + "/embeddings"); }
        catch (IllegalArgumentException e) { throw new EmbeddingException("EMBEDDING_CONFIGURATION_MISSING"); }
    }
    private List<float[]> parse(String response, int size) {
        JsonNode root;
        try { root = Json.MAPPER.readTree(response); }
        catch (IOException e) { throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID"); }
        var data = root.path("data");
        if (!data.isArray() || data.size() != size) throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID");
        var result = new ArrayList<float[]>(java.util.Collections.nCopies(size, null));
        for (int n = 0; n < size; n++) {
            var entry = data.get(n);
            int index = entry.has("index") ? entry.path("index").asInt(-1) : (size == 1 ? 0 : -1);
            if (index < 0 || index >= size || result.get(index) != null) throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID");
            var vector = entry.path("embedding");
            if (!vector.isArray() || vector.size() != profile.dimension()) throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
            float[] values = new float[profile.dimension()];
            for (int i = 0; i < values.length; i++) {
                if (!vector.get(i).isNumber()) throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID");
                values[i] = (float) vector.get(i).asDouble();
            }
            EmbeddingVectors.validate(values, profile.dimension()); result.set(index, values);
        }
        tokens.addAndGet(Math.max(0, root.path("usage").path("total_tokens").asLong(0)));
        return List.copyOf(result);
    }
}
