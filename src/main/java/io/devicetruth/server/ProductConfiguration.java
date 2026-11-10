package io.devicetruth.server;

import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import org.flywaydb.core.Flyway;
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

    @Bean
    @DependsOn("productFlyway")
    GeneralAgentService generalAgentService(HikariDataSource source, Environment environment) {
        var jdbc = new JdbcTemplate(source);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        return new GeneralAgentService(new ConversationRepository(jdbc), new AgentRunRepository(jdbc), transactions,
                new ServerIdentity(environment.getRequiredProperty("devicetruth.product.owner-id"),
                        environment.getRequiredProperty("devicetruth.product.project-id")));
    }
}
