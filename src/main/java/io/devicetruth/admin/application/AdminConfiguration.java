package io.devicetruth.admin.application;

import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.admin.api.AdminController;
import io.devicetruth.admin.api.AdminPages;
import io.devicetruth.server.AccountService;
import io.devicetruth.skill.SkillRuntime;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods=false) @Profile("product") @DependsOn("productFlyway")
@Import(AdminController.class)
public class AdminConfiguration {
    @Bean AdminKnowledgeOperations adminKnowledgeOperations(AdminService service,HikariDataSource source,
            io.devicetruth.knowledge.FileUploadService files,io.devicetruth.knowledge.KnowledgeImportService imports,
            org.springframework.core.env.Environment environment,AdminPages pages) {
        return new AdminKnowledgeOperations(service,new JdbcTemplate(source),new TransactionTemplate(new DataSourceTransactionManager(source)),files,imports,
                java.nio.file.Path.of(environment.getProperty("devicetruth.admin.knowledge-root","datasets/knowledge")),pages);
    }
    @Bean AdminPages adminPages() { return new AdminPages(); }
    @Bean AdminService adminService(HikariDataSource source,AccountService accounts,SkillRuntime skills,AdminPages pages,io.devicetruth.application.agent.GeneralAgentService agent,org.springframework.core.env.Environment environment,
            @org.springframework.beans.factory.annotation.Qualifier("agentCapabilityGateway") io.devicetruth.core.capability.CapabilityGateway gateway) {
        return new AdminService(new JdbcTemplate(source),new TransactionTemplate(new DataSourceTransactionManager(source)),accounts,skills,pages,agent,environment.getProperty("devicetruth.agent.reasoner","scripted"),gateway.toolNames());
    }
}
