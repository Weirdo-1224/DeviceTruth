package io.devicetruth.server;

import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.AgentModelConnections;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.core.reasoning.ScriptedAgentReasoner;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅由独立HTTP入口导入；CLI和默认legacy服务不创建数据源或迁移。 */
@Configuration(proxyBeanMethods = false)
@Profile("product")
public class ProductConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource productDataSource(Environment environment) {
        var source = new HikariDataSource();
        source.setJdbcUrl(environment.getRequiredProperty("spring.datasource.url"));
        source.setUsername(environment.getRequiredProperty("spring.datasource.username"));
        source.setPassword(environment.getRequiredProperty("spring.datasource.password"));
        source.setMaximumPoolSize(4);
        source.setConnectionTimeout(5000);
        source.setPoolName("product-db");
        return source;
    }

    @Bean(initMethod = "migrate")
    Flyway productFlyway(HikariDataSource source) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .cleanDisabled(true).validateMigrationNaming(true).load();
    }

    /**
     * 服务器固定身份（本地单用户模式）。共享部署前必须替换为真实认证（05-03）；
     * 客户端与模型都不能提供或扩大 owner/project。
     */
    @Bean
    ServerIdentity productServerIdentity(Environment environment) {
        return new ServerIdentity(environment.getRequiredProperty("devicetruth.product.owner-id"),
                environment.getRequiredProperty("devicetruth.product.project-id"));
    }

    /**
     * 产品通用 Agent 服务：知识工具经 agentCapabilityGateway 进入唯一主循环，
     * 每 run 新建推理器实例，worker 有界；worker-enabled=false 时只保留 M0 的 QUEUED 持久化合同。
     * initMethod=recoverQueued 在装配完成后接手上一个进程遗留的 QUEUED 任务（M0 队列交接）。
     */
    @Bean(initMethod = "recoverQueued")
    @DependsOn("productFlyway")
    GeneralAgentService generalAgentService(HikariDataSource source, Environment environment,
            @Qualifier("agentCapabilityGateway") CapabilityGateway gateway,
            ServerIdentity identity,
            io.devicetruth.knowledge.FileUploadService fileUploadService,
            @Value("${devicetruth.agent.reasoner:scripted}") String reasonerName,
            @Value("${devicetruth.agent.max-concurrent-runs:2}") int maxConcurrentRuns,
            @Value("${devicetruth.agent.max-queue:10}") int maxQueue,
            @Value("${devicetruth.agent.worker-enabled:true}") boolean workerEnabled,
            @Value("${devicetruth.agent.lease-seconds:60}") long leaseSeconds,
            @Value("${devicetruth.agent.max-recovery-attempts:3}") int maxRecoveryAttempts) {
        var jdbc = new JdbcTemplate(source);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        return new GeneralAgentService(new ConversationRepository(jdbc),
                new AgentRunRepository(jdbc, environment.getProperty("devicetruth.agent.max-active-duration-ms",
                        Long.class, 120_000L)),
                transactions,
                identity, gateway, agentReasoners(agentReasonerName(environment, reasonerName)), maxConcurrentRuns,
                maxQueue, workerEnabled, fileUploadService, leaseSeconds, maxRecoveryAttempts);
    }

    /** 结构化设备主数据仓储：身份、组件、配置与维修历史（V8）。 */
    @Bean
    @DependsOn("productFlyway")
    io.devicetruth.core.capability.Repositories.AssetRegistryRepository assetRegistryRepository(
            HikariDataSource source) {
        var jdbc = new JdbcTemplate(source);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        return new io.devicetruth.adapter.persistence.AssetRegistryRepository(jdbc, transactions);
    }

    /** 资产事实引用登记：把本次查询取到的配置/维修记录写成当前 run 的可审计引用。 */
    @Bean
    @DependsOn("productFlyway")
    io.devicetruth.adapter.persistence.AssetFactReferenceWriter assetFactReferenceWriter(HikariDataSource source) {
        return new io.devicetruth.adapter.persistence.AssetFactReferenceWriter(new JdbcTemplate(source));
    }

    /** 设备事实工具：注册即服务器授权，由 agentCapabilityGateway 统一登记。 */
    @Bean
    io.devicetruth.application.agent.AssetTools assetTools(
            io.devicetruth.core.capability.Repositories.AssetRegistryRepository registry,
            io.devicetruth.application.agent.AssetTools.FactReferenceWriter references) {
        return new io.devicetruth.application.agent.AssetTools(registry, references);
    }

    /**
     * 设备主数据导入器（01-02 队列交接同类模式）：product 启动且显式开启时，
     * 把打包的模拟夹具幂等导入当前 owner/project 的设备主数据。
     * 默认关闭，只有运维显式配置 devicetruth.assets.seed-fixtures=true 才会写库；
     * 已存在的来源记录不会被改写，重复启动不产生重复行。
     */
    @Bean(initMethod = "seed")
    @DependsOn({"productFlyway", "assetRegistryRepository"})
    io.devicetruth.application.agent.AssetFixtureSeeder assetFixtureSeeder(
            io.devicetruth.core.capability.Repositories.AssetRegistryRepository registry,
            ServerIdentity identity, Environment environment,
            @Value("${devicetruth.assets.seed-fixtures:false}") boolean enabled,
            @Value("${devicetruth.assets.fixtures:CAR-B}") String fixtures) {
        return new io.devicetruth.application.agent.AssetFixtureSeeder(registry, identity, enabled, fixtures);
    }

    /** 设备事实只读 API 与工具共用的服务。 */
    @Bean
    @DependsOn("productFlyway")
    io.devicetruth.application.agent.AssetFactService assetFactService(HikariDataSource source,
            io.devicetruth.core.capability.Repositories.AssetRegistryRepository registry,
            io.devicetruth.application.agent.AssetTools tools) {
        return new io.devicetruth.application.agent.AssetFactService(registry, tools, new JdbcTemplate(source));
    }

    static String agentReasonerName(Environment environment, String fallback) {
        // 显式配置（命令行/测试注入的属性、环境变量）优先于私有文件，
        // 避免工作目录中的本地文件压过应用显式指定；文件只作缺省来源。
        String value = environment.getProperty("devicetruth.agent.reasoner");
        if (value == null || value.isBlank()) value = environment.getProperty("DEVICETRUTH_AGENT_REASONER");
        if (value == null || value.isBlank())
            value = io.devicetruth.adapter.ModelConfiguration.local().apply("DEVICETRUTH_AGENT_REASONER");
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 通用 Agent 推理器工厂：每 run 新建实例，避免共享可变的模型连接与尝试记录。
     * 未知名称不让容器启动失败，运行开始前以稳定错误码 FAILED，健康端点仍可用。
     */
    static Supplier<GeneralReasoner> agentReasoners(String name) {
        return switch (name == null ? "" : name.trim().toLowerCase()) {
            case "qwen" -> AgentModelConnections::qwen;
            case "openai" -> AgentModelConnections::openai;
            case "scripted" -> ScriptedAgentReasoner::new;
            default -> () -> {
                throw new IllegalStateException("UNSUPPORTED_AGENT_REASONER_CONFIGURATION");
            };
        };
    }
}
