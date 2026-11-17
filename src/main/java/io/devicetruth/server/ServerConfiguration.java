package io.devicetruth.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.application.agent.ObservationTools;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.Repositories.AssetRegistryRepository;
import io.devicetruth.knowledge.EmbeddingClient;
import io.devicetruth.knowledge.EmbeddingProfile;
import io.devicetruth.knowledge.KnowledgeImportService;
import io.devicetruth.knowledge.KnowledgeSearchService;
import io.devicetruth.knowledge.KnowledgeTools;
import io.devicetruth.knowledge.DeterministicEmbeddingClient;
import io.devicetruth.knowledge.OpenAiCompatibleEmbeddingClient;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Server 侧集中装配：只扫描新增 api 包，core/adapter/app 不新增 Spring 组件。
 * 共享 Jackson 边界无条件装配；产品 Bean 限 {@code product} profile；
 * legacy 诊断栈已移至 {@link LegacyConfiguration}（仅 {@code legacy} profile）。
 */
@Configuration
@Import(ProductConfiguration.class)
@ComponentScan(basePackages = "io.devicetruth.api")
public class ServerConfiguration {
    /** HTTP 与模型协议共用严格 JSON 边界，避免转换或覆盖用户原文。 */
    @Bean ObjectMapper httpObjectMapper() { return Json.MAPPER.copy(); }

    /**
     * 产品 Agent 的网关：登记知识与设备事实工具（注册即服务器授权）。
     * legacy 资产能力留在 LegacyConfiguration 的 standard 网关，避免无资产问答被 legacy 的必填 assetId 语义污染。
     * 设备事实工具只读，且资产归属在仓储层按服务器身份校验，模型不能借参数扩大范围。
     */
    @Bean
    @Profile("product")
    CapabilityGateway agentCapabilityGateway(KnowledgeTools knowledgeTools,
            io.devicetruth.application.agent.AssetTools assetTools,
            io.devicetruth.application.agent.AgentReportTools reportTools, ObservationTools observations) {
        var gateway = new CapabilityGateway();
        knowledgeTools.registerInto(gateway);
        assetTools.registerInto(gateway);
        reportTools.registerInto(gateway);
        observations.registerInto(gateway);
        return gateway;
    }

    @Bean
    @Profile("product")
    ObservationTools observationTools(AssetRegistryRepository assets, HikariDataSource source) {
        return new ObservationTools(assets, new JdbcTemplate(source));
    }

    /** 通用调查报告工具：聚合当前 run 的持久白名单事实（引用/工具轨迹/预算/待答与答案摘要）。 */
    @Bean
    @Profile("product")
    io.devicetruth.application.agent.AgentReportTools agentReportTools(HikariDataSource source) {
        var jdbc = new JdbcTemplate(source);
        return new io.devicetruth.application.agent.AgentReportTools(
                new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc),
                new io.devicetruth.adapter.persistence.ConversationRepository(jdbc));
    }

    /**
     * embedding 档案只来自服务端环境：模型、维度与 profile 版本共同决定索引能否复用；
     * 换模型必须新增迁移独立重建，不能与旧 profile 的向量混查。
     */
    @Bean
    @Profile("product")
    EmbeddingProfile productEmbeddingProfile(Environment environment) {
        return productEmbeddingProfile(environment, io.devicetruth.adapter.ModelConfiguration.local());
    }

    EmbeddingProfile productEmbeddingProfile(Environment environment, java.util.function.Function<String, String> local) {
        String provider = embeddingSetting(environment, local, "PROVIDER", "provider", EmbeddingProfile.PROVIDER_OPENAI);
        String model = embeddingSetting(environment, local, "MODEL", "model", "text-embedding-v4");
        // 离线确定性向量必须使用自己的模型名：即便配置误设真实模型，也不能与真实供应商共用档案标签。
        if (EmbeddingProfile.PROVIDER_DETERMINISTIC.equalsIgnoreCase(provider)) model = "deterministic-char2";
        int dimension = Integer.parseInt(embeddingSetting(environment, local, "DIMENSION", "dimension", "1024"));
        String revision = embeddingSetting(environment, local, "PROFILE_REVISION", "profile-revision", EmbeddingProfile.DEFAULT_REVISION);
        // 专用变量优先；为空时复用千问 DashScope 的既有服务端密钥，避免为同一供应商维护两份凭据。
        String apiKey = embeddingSetting(environment, local, "API_KEY", "api-key", "");
        if (apiKey.isBlank()) {
            String shared = local.apply("DASHSCOPE_API_KEY");
            apiKey = shared == null || shared.isBlank() ? environment.getProperty("DASHSCOPE_API_KEY", "") : shared;
        }
        return EmbeddingProfile.of(provider, embeddingSetting(environment, local, "BASE_URL", "base-url",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                apiKey, model, dimension, revision,
                Long.parseLong(embeddingSetting(environment, local, "TIMEOUT_MS", "timeout-ms", "15000")),
                Boolean.parseBoolean(embeddingSetting(environment, local, "SEND_DIMENSIONS", "send-dimensions", "true")));
    }

    private static String embeddingSetting(Environment environment, java.util.function.Function<String, String> local,
                                           String suffix, String property, String fallback) {
        String value = environment.getProperty("DEVICETRUTH_EMBEDDING_" + suffix);
        if (value == null || value.isBlank()) value = local.apply("DEVICETRUTH_EMBEDDING_" + suffix);
        return value == null || value.isBlank()
                ? environment.getProperty("devicetruth.embedding." + property, fallback) : value.trim();
    }

    /** deterministic 仅供离线合同测试；真实供应商验收由 02-03 单独执行。 */
    @Bean
    @Profile("product")
    EmbeddingClient productEmbeddingClient(EmbeddingProfile profile) {
        return profile.deterministic()
                ? new DeterministicEmbeddingClient(profile) : new OpenAiCompatibleEmbeddingClient(profile);
    }

    @Bean
    @Profile("product")
    JdbcTemplate knowledgeJdbc(HikariDataSource source) { return new JdbcTemplate(source); }

    @Bean
    @Profile("product")
    TransactionTemplate knowledgeTransactions(HikariDataSource source) {
        return new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Bean
    @Profile("product")
    KnowledgeImportService knowledgeImportService(JdbcTemplate knowledgeJdbc,
            TransactionTemplate knowledgeTransactions, EmbeddingClient productEmbeddingClient,
            Environment environment) {
        return new KnowledgeImportService(knowledgeJdbc, knowledgeTransactions, productEmbeddingClient,
                Integer.parseInt(environment.getProperty("devicetruth.knowledge.max-chunk-chars", "480")));
    }

    @Bean(destroyMethod = "close")
    @Profile("product")
    io.devicetruth.knowledge.FileUploadService fileUploadService(JdbcTemplate knowledgeJdbc,
            KnowledgeImportService knowledgeImportService, Environment environment) {
        return new io.devicetruth.knowledge.FileUploadService(knowledgeJdbc, knowledgeImportService,
                Path.of(environment.getProperty("devicetruth.files.root", "outputs/files")),
                Integer.parseInt(environment.getProperty("devicetruth.files.max-concurrent-imports", "1")),
                Integer.parseInt(environment.getProperty("devicetruth.files.max-queue", "4")));
    }

    @Bean
    @Profile("product")
    KnowledgeSearchService knowledgeSearchService(JdbcTemplate knowledgeJdbc,
            TransactionTemplate knowledgeTransactions, EmbeddingClient productEmbeddingClient,
            Environment environment) {
        return new KnowledgeSearchService(knowledgeJdbc, knowledgeTransactions, productEmbeddingClient,
                Integer.parseInt(environment.getProperty("devicetruth.knowledge.max-chunks", "5")),
                Double.parseDouble(environment.getProperty("devicetruth.knowledge.min-similarity", "0.45")));
    }

    /** 服务端知识工具合同；02-02 起注册进产品 Agent 网关（agentCapabilityGateway）。 */
    @Bean
    @Profile("product")
    KnowledgeTools knowledgeTools(KnowledgeSearchService knowledgeSearchService) {
        return new KnowledgeTools(knowledgeSearchService);
    }
}
