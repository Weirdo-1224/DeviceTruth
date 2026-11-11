package io.devicetruth.server;

import io.devicetruth.knowledge.EmbeddingProfile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

/**
 * embedding 服务端配置装配：默认使用千问 text-embedding-v4（DashScope compatible-mode），
 * 档案标签由 provider/model/维度/修订共同推导；离线确定性向量使用独立档案。
 */
class KnowledgeConfigurationTest {
    private final ServerConfiguration configuration = new ServerConfiguration();

    @Test void defaultsToQwenTextEmbeddingV4OnDashScope() {
        EmbeddingProfile profile = configuration.productEmbeddingProfile(new MockEnvironment());
        assertThat(profile.provider()).isEqualTo("openai");
        assertThat(profile.model()).isEqualTo("text-embedding-v4");
        assertThat(profile.dimension()).isEqualTo(1024);
        assertThat(profile.baseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertThat(profile.profileVersion()).isEqualTo("openai:text-embedding-v4@1024-cosine-r1");
        assertThat(profile.sendDimensions()).isTrue();
        assertThat(profile.apiKey()).isEmpty();
    }

    @Test void dashedScopeKeyFallsBackWhenDedicatedKeyIsBlank() {
        var environment = new MockEnvironment()
                .withProperty("devicetruth.embedding.api-key", "  ")
                .withProperty("DASHSCOPE_API_KEY", "dashscope-test-key");
        assertThat(configuration.productEmbeddingProfile(environment).apiKey()).isEqualTo("dashscope-test-key");
    }

    @Test void dedicatedEmbeddingKeyWinsOverDashScopeKey() {
        var environment = new MockEnvironment()
                .withProperty("devicetruth.embedding.api-key", "dedicated-key")
                .withProperty("DASHSCOPE_API_KEY", "dashscope-test-key");
        assertThat(configuration.productEmbeddingProfile(environment).apiKey()).isEqualTo("dedicated-key");
    }

    @Test void explicitModelDimensionAndRevisionAreHonoured() {
        var environment = new MockEnvironment()
                .withProperty("devicetruth.embedding.model", "text-embedding-v3")
                .withProperty("devicetruth.embedding.dimension", "768")
                .withProperty("devicetruth.embedding.profile-revision", "2")
                .withProperty("devicetruth.embedding.send-dimensions", "false");
        EmbeddingProfile profile = configuration.productEmbeddingProfile(environment);
        assertThat(profile.model()).isEqualTo("text-embedding-v3");
        assertThat(profile.dimension()).isEqualTo(768);
        assertThat(profile.profileVersion()).isEqualTo("openai:text-embedding-v3@768-cosine-r2");
        assertThat(profile.sendDimensions()).isFalse();
    }

    @Test void deterministicProviderNeverSharesRealEmbeddingProfile() {
        var environment = new MockEnvironment()
                .withProperty("devicetruth.embedding.provider", "deterministic")
                .withProperty("devicetruth.embedding.model", "text-embedding-v4");
        EmbeddingProfile deterministic = configuration.productEmbeddingProfile(environment);
        EmbeddingProfile real = configuration.productEmbeddingProfile(new MockEnvironment());
        assertThat(deterministic.model()).isEqualTo("deterministic-char2");
        assertThat(deterministic.deterministic()).isTrue();
        assertThat(deterministic.profileVersion())
                .isEqualTo("deterministic:deterministic-char2@1024-cosine-r1")
                .isNotEqualTo(real.profileVersion());
    }

    @Test void profileIdentityChangesWithEveryComponent() {
        EmbeddingProfile base = EmbeddingProfile.of("openai", "", "", "text-embedding-v4", 1024, "1", 1000, true);
        assertThat(EmbeddingProfile.of("deterministic", "", "", "text-embedding-v4", 1024, "1", 1000, true)
                .profileVersion()).isNotEqualTo(base.profileVersion());
        assertThat(EmbeddingProfile.of("openai", "", "", "text-embedding-v3", 1024, "1", 1000, true)
                .profileVersion()).isNotEqualTo(base.profileVersion());
        assertThat(EmbeddingProfile.of("openai", "", "", "text-embedding-v4", 768, "1", 1000, true)
                .profileVersion()).isNotEqualTo(base.profileVersion());
        assertThat(EmbeddingProfile.of("openai", "", "", "text-embedding-v4", 1024, "2", 1000, true)
                .profileVersion()).isNotEqualTo(base.profileVersion());
        assertThatThrownBy(() -> EmbeddingProfile.of("openai", "", "", "text-embedding-v4", 1024, "bad revision!", 1000, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
