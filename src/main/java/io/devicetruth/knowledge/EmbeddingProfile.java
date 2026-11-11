package io.devicetruth.knowledge;

import io.devicetruth.core.contract.Checks;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * 固定的 embedding 档案：供应商、模型、维度、度量与档案修订共同组成唯一标识。
 * 任何一项变化都产生新档案，必须独立重建索引；离线确定性向量与真实供应商向量
 * 因供应商前缀不同而永不共用标签，禁止混查。
 */
public record EmbeddingProfile(String provider, String baseUrl, String apiKey, String model,
                               int dimension, String revision, Duration timeout,
                               boolean sendDimensions) {
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_DETERMINISTIC = "deterministic";
    public static final String DEFAULT_REVISION = "1";
    private static final Set<String> PROVIDERS = Set.of(PROVIDER_OPENAI, PROVIDER_DETERMINISTIC);

    public EmbeddingProfile {
        provider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(provider))
            throw new IllegalArgumentException("devicetruth.embedding.provider must be openai or deterministic");
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = Checks.text(model, "model");
        revision = Checks.text(revision, "revision");
        if (!revision.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,31}"))
            throw new IllegalArgumentException("embedding profile revision is invalid");
        if (dimension < 1 || dimension > 4096) throw new IllegalArgumentException("embedding dimension out of range");
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.toSeconds() > 120)
            throw new IllegalArgumentException("embedding timeout out of range");
    }

    /** 索引度量固定 cosine；更换度量同样需要新档案。 */
    public String metric() { return "cosine"; }

    public boolean deterministic() { return PROVIDER_DETERMINISTIC.equals(provider); }

    /**
     * 唯一档案标识：把供应商、模型、维度与修订都编码进去。
     * 只切换 provider 或模型时标签必然变化，不能被当作同一向量空间复用。
     */
    public String profileVersion() {
        return provider + ":" + model + "@" + dimension + "-" + metric() + "-r" + revision;
    }

    public static EmbeddingProfile of(String provider, String baseUrl, String apiKey, String model,
                                      int dimension, String revision, long timeoutMs,
                                      boolean sendDimensions) {
        return new EmbeddingProfile(provider, baseUrl, apiKey, model, dimension, revision,
                Duration.ofMillis(timeoutMs), sendDimensions);
    }
}
