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
     * 产品通用 Agent 服务：知识工具经 agentCapabilityGateway 进入唯一主循环，
     * 每 run 新建推理器实例，worker 有界；worker-enabled=false 时只保留 M0 的 QUEUED 持久化合同。
     * initMethod=recoverQueued 在装配完成后接手上一个进程遗留的 QUEUED 任务（M0 队列交接）。
     */
    @Bean(initMethod = "recoverQueued")
    @DependsOn("productFlyway")
    GeneralAgentService generalAgentService(HikariDataSource source, Environment environment,
            @Qualifier("agentCapabilityGateway") CapabilityGateway gateway,
            @Value("${devicetruth.agent.reasoner:scripted}") String reasonerName,
            @Value("${devicetruth.agent.max-concurrent-runs:2}") int maxConcurrentRuns,
            @Value("${devicetruth.agent.max-queue:10}") int maxQueue,
            @Value("${devicetruth.agent.worker-enabled:true}") boolean workerEnabled) {
        var jdbc = new JdbcTemplate(source);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        return new GeneralAgentService(new ConversationRepository(jdbc), new AgentRunRepository(jdbc), transactions,
                new ServerIdentity(environment.getRequiredProperty("devicetruth.product.owner-id"),
                        environment.getRequiredProperty("devicetruth.product.project-id")),
                gateway, agentReasoners(reasonerName), maxConcurrentRuns, maxQueue, workerEnabled);
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
