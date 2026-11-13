package io.devicetruth.server;

import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.adapter.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.devicetruth.adapter.OpenAiReasoner;
import io.devicetruth.adapter.QwenReasoner;
import io.devicetruth.adapter.RunReportWriter;
import io.devicetruth.application.ConversationFlowService;
import io.devicetruth.application.ConversationService;
import io.devicetruth.application.DeterministicTaskRouter;
import io.devicetruth.application.DiagnosisApplicationService;
import io.devicetruth.application.DiagnosisRunService;
import io.devicetruth.application.LlmTaskRouter;
import io.devicetruth.application.QueryService;
import io.devicetruth.application.ReasonerFactory;
import io.devicetruth.application.RouteValidator;
import io.devicetruth.application.SupportedScenarioCatalog;
import io.devicetruth.application.TaskRouter;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.contract.Models.Budget;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.reasoning.ScriptedReasoner;
import io.devicetruth.knowledge.DeterministicEmbeddingClient;
import io.devicetruth.knowledge.EmbeddingClient;
import io.devicetruth.knowledge.EmbeddingProfile;
import io.devicetruth.knowledge.KnowledgeImportService;
import io.devicetruth.knowledge.KnowledgeSearchService;
import io.devicetruth.knowledge.KnowledgeTools;
import io.devicetruth.knowledge.OpenAiCompatibleEmbeddingClient;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Server 侧集中装配：只扫描新增 api 包，core/adapter/app 不新增 Spring 组件。 */
@Configuration
@Import(ProductConfiguration.class)
@ComponentScan(basePackages = "io.devicetruth.api")
public class ServerConfiguration {
    /** HTTP 与模型协议共用严格 JSON 边界，避免转换或覆盖用户原文。 */
    @Bean ObjectMapper httpObjectMapper() { return Json.MAPPER.copy(); }

    @Bean JsonRepositories jsonRepositories() throws IOException { return JsonRepositories.fromClasspath(); }

    @Bean SupportedScenarioCatalog supportedScenarioCatalog() throws IOException {
        return SupportedScenarioCatalog.fromClasspath();
    }

    @Bean ConversationService conversationService(
            @Value("${devicetruth.session.max-sessions:100}") int maxSessions,
            @Value("${devicetruth.session.max-messages-per-session:40}") int maxMessagesPerSession) {
        return new ConversationService(maxSessions, maxMessagesPerSession);
    }

    @Bean RouteValidator routeValidator(SupportedScenarioCatalog catalog, JsonRepositories repositories) {
        return new RouteValidator(catalog, repositories, repositories, repositories);
    }

    @Bean TaskRouter taskRouter(RouteValidator validator,
            @Value("${devicetruth.router.mode:offline}") String mode,
            @Value("${devicetruth.core.reasoner:scripted}") String reasoner) {
        if ("offline".equalsIgnoreCase(mode)) return new DeterministicTaskRouter(validator);
        if ("online".equalsIgnoreCase(mode)) return new LlmTaskRouter(new OnlineRouterCompletion(reasoner), validator);
        throw new IllegalArgumentException("devicetruth.router.mode must be offline or online");
    }

    /** 事实查询只依赖只读 AssetRepository；不构造诊断 Scope，也不经过 CapabilityGateway。 */
    @Bean QueryService queryService(JsonRepositories repositories) {
        return new QueryService(repositories);
    }

    @Bean DiagnosisApplicationService diagnosisApplicationService(SupportedScenarioCatalog catalog,
            RouteValidator validator) {
        return new DiagnosisApplicationService(catalog, validator);
    }

    /** 只读仓储与注册完成后不再修改的 Gateway 可以共享；Reasoner 绝不能注册成 Bean。 */
    @Bean
    @Primary
    CapabilityGateway capabilityGateway(JsonRepositories repositories) {
        return CapabilityGateway.standard(repositories, repositories, repositories);
    }

    /**
     * 产品 Agent 的网关：登记知识与设备事实工具（注册即服务器授权）。
     * legacy 资产能力仍留在上面的 standard 网关，避免无资产问答被 legacy 的必填 assetId 语义污染。
     * 设备事实工具只读，且资产归属在仓储层按服务器身份校验，模型不能借参数扩大范围。
     */
    @Bean
    @Profile("product")
    CapabilityGateway agentCapabilityGateway(KnowledgeTools knowledgeTools,
            io.devicetruth.application.agent.AssetTools assetTools) {
        var gateway = new CapabilityGateway();
        knowledgeTools.registerInto(gateway);
        assetTools.registerInto(gateway);
        return gateway;
    }

    /**
     * 只读仓储与注册完成后不再修改的 Gateway 可以共享；Reasoner 绝不能注册成 Bean，
     * 否则多个并发运行会共用可变的 attempts / 延迟初始化连接，这里只提供每任务新建工厂。
     * 未知的 reasoner 名称不让容器启动失败：诊断提交时 `create()` 抛 InitException，
     * 由应用层映射为稳定错误码，健康端点在模型配置错误时仍可用。
     */
    @Bean ReasonerFactory reasonerFactory(@Value("${devicetruth.core.reasoner:scripted}") String reasoner) {
        String name = reasoner == null ? "" : reasoner.trim().toLowerCase();
        Supplier<Reasoner> creator = switch (name) {
            case "qwen" -> QwenReasoner::new;
            case "openai" -> OpenAiReasoner::new;
            case "scripted" -> ScriptedReasoner::new;
            default -> () -> { throw new InitException("UNSUPPORTED_REASONER_CONFIGURATION"); };
        };
        return ReasonerFactory.of(name, creator);
    }

    /** 推理器构造阶段的稳定失败码（不携带配置值、路径或异常正文）。 */
    public static final class InitException extends RuntimeException {
        private final String code;
        public InitException(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }

    @Bean DiagnosisRunService diagnosisRunService(ConversationService conversations, SupportedScenarioCatalog catalog,
            CapabilityGateway gateway, ReasonerFactory reasonerFactory,
            @Value("${devicetruth.run.output-root:outputs/runs}") String outputRoot,
            @Value("${devicetruth.run.max-concurrent-runs:2}") int maxConcurrentRuns,
            @Value("${devicetruth.run.max-queue:10}") int maxQueue,
            @Value("${devicetruth.run.max-stored-runs:100}") int maxStoredRuns) {
        return new DiagnosisRunService(conversations, catalog, gateway, reasonerFactory, Budget.defaults(),
                new RunReportWriter(), Path.of(outputRoot), maxConcurrentRuns, maxQueue, maxStoredRuns);
    }

    @Bean ConversationFlowService conversationFlowService(ConversationService conversations, TaskRouter router,
            SupportedScenarioCatalog catalog, QueryService queries, DiagnosisApplicationService diagnoses,
            DiagnosisRunService runs) {
        return new ConversationFlowService(conversations, router, catalog, queries, diagnoses, runs);
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
