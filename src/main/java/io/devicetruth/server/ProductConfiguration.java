package io.devicetruth.server;

import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.AgentModelConnections;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.adapter.persistence.SkillInstallationRepository;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.core.reasoning.ScriptedAgentReasoner;
import io.devicetruth.skill.SkillRegistry;
import io.devicetruth.skill.SkillRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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

    @Bean
    @DependsOn("productFlyway")
    SkillRuntime productSkillRuntime(HikariDataSource source, ServerIdentity identity,
            @Value("${devicetruth.skills.root}") String root,
            @Value("${devicetruth.skills.installations}") String[] defaults,
            @Value("${devicetruth.skills.allowed-tools}") String[] tools) {
        return new SkillRuntime(SkillRegistry.load(Path.of(root)), new SkillInstallationRepository(new JdbcTemplate(source)),
                identity, Set.copyOf(List.of(tools)), List.of(defaults));
    }

    /**
     * 产品通用 Agent 服务：知识工具经 agentCapabilityGateway 进入唯一主循环，
     * 每 run 新建推理器实例，worker 有界；worker-enabled=false 时只保留 M0 的 QUEUED 持久化合同。
     * initMethod=recoverQueued 在装配完成后接手上一个进程遗留的 QUEUED 任务（M0 队列交接）。
     * 事件通知钩子取 RunEventStreamService.publish：只在事件事务提交后调用（先提交后发布）。
     */
    @Bean(initMethod = "recoverAuthenticatedQueued")
    @DependsOn("productFlyway")
    GeneralAgentService generalAgentService(HikariDataSource source, Environment environment,
            @Qualifier("agentCapabilityGateway") CapabilityGateway gateway,
            ServerIdentity identity, SkillRuntime skills,
            io.devicetruth.knowledge.FileUploadService fileUploadService,
            io.devicetruth.application.agent.RunEventStreamService runEventStreamService,
            @Value("${devicetruth.agent.reasoner:scripted}") String reasonerName,
            @Value("${devicetruth.agent.max-concurrent-runs:2}") int maxConcurrentRuns,
            @Value("${devicetruth.agent.max-queue:10}") int maxQueue,
            @Value("${devicetruth.agent.worker-enabled:true}") boolean workerEnabled,
            @Value("${devicetruth.agent.lease-seconds:60}") long leaseSeconds,
            @Value("${devicetruth.agent.max-recovery-attempts:3}") int maxRecoveryAttempts,
            @Value("${devicetruth.agent.recovery-scan-limit:50}") int recoveryScanLimit,
            org.springframework.beans.factory.ObjectProvider<io.devicetruth.adapter.StructuredAgentReasoner.ProtocolObserver> observers) {
        var jdbc = new JdbcTemplate(source);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        return new GeneralAgentService(new ConversationRepository(jdbc),
                new AgentRunRepository(jdbc, environment.getProperty("devicetruth.agent.max-active-duration-ms",
                        Long.class, 120_000L), transactions),
                transactions,
                identity, gateway, observedReasoners(agentReasoners(agentReasonerName(environment, reasonerName)), observers.getIfAvailable()), maxConcurrentRuns,
                maxQueue, workerEnabled, fileUploadService, leaseSeconds, maxRecoveryAttempts, skills,
                runEventStreamService::publish, recoveryScanLimit);
    }

    private static Supplier<GeneralReasoner> observedReasoners(Supplier<GeneralReasoner> factory,
            io.devicetruth.adapter.StructuredAgentReasoner.ProtocolObserver observer) {
        if (observer == null) return factory;
        return () -> {
            var reasoner = factory.get();
            return reasoner instanceof io.devicetruth.adapter.StructuredAgentReasoner structured
                    ? structured.observing(observer) : reasoner;
        };
    }

    /**
     * 复验 §8.2：SSE relay 专用有界线程池，容量=devicetruth.stream.max-connections（默认 64），
     * relay 不占用 Tomcat 请求线程；全局容量判定在 RunEventStreamService 同一锁内完成，
     * 池饱和的竞态窗口由 execute 拒绝兜底为 503 STREAM_CAPACITY。
     */
    @Bean(destroyMethod = "shutdown")
    java.util.concurrent.ThreadPoolExecutor runStreamRelayExecutor(
            @Value("${devicetruth.stream.max-connections:64}") int maxConnections) {
        var seq = new java.util.concurrent.atomic.AtomicInteger();
        return new java.util.concurrent.ThreadPoolExecutor(maxConnections, maxConnections, 60,
                java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(maxConnections),
                runnable -> {
                    Thread thread = new Thread(runnable, "devicetruth-stream-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** 05-01 实时事件流：内存通知 + 低频兜底补读；单 run 订阅有界（默认 16，超出 409）、
     *  全局有界（默认 64，超出 503）、单连接期限 ttl（默认 60 分钟，超时统一清理，复验 §8.2）。 */
    @Bean
    @DependsOn("productFlyway")
    io.devicetruth.application.agent.RunEventStreamService runEventStreamService(
            HikariDataSource source, ServerIdentity identity,
            java.util.concurrent.ThreadPoolExecutor runStreamRelayExecutor,
            @Value("${devicetruth.stream.max-subscriptions:16}") int maxSubscriptions,
            @Value("${devicetruth.stream.max-connections:64}") int maxConnections,
            @Value("${devicetruth.stream.ttl-minutes:60}") long ttlMinutes) {
        return new io.devicetruth.application.agent.RunEventStreamService(
                new AgentRunRepository(new JdbcTemplate(source)), identity, maxSubscriptions,
                maxConnections, java.time.Duration.ofMinutes(ttlMinutes), runStreamRelayExecutor);
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
