package io.devicetruth.server;

import io.devicetruth.adapter.Json;
import io.devicetruth.admin.application.AdminMaintenance;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅测试类路径入口，必须给随机隔离库；不打入发布 JAR。 */
public final class AdminBrowserFixture {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("SPRING_DATASOURCE_URL");
        if(url==null || !url.matches("jdbc:postgresql://127.0.0.1:54329/devicetruth_test_[a-f0-9]{32}"))throw new IllegalArgumentException("Isolated database required");
        var app=new SpringApplicationBuilder(DeviceTruthServerApplication.class).run("--spring.profiles.active=product","--server.port=0","--debug=false","--logging.level.org.springframework=WARN","--devicetruth.auth.cookie-secure=false","--devicetruth.agent.worker-enabled=true","--devicetruth.agent.reasoner=openai","--devicetruth.embedding.provider=deterministic");
        var accounts=app.getBean(AccountService.class);
        var admin=accounts.register("admin@isolated.example","isolated-browser-password-123","平台运营");
        var member=accounts.register("member@isolated.example","isolated-browser-password-123","设备检修员");
        var source=app.getBean(HikariDataSource.class);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        var maintenance=new AdminMaintenance(jdbc,tx);maintenance.platform("browser-fixture",admin.userId(),true,true,"OPS_REQUEST");
        UUID workspace=jdbc.queryForObject("SELECT id FROM dt_workspace WHERE personal_user_id=?",UUID.class,member.userId());
        maintenance.grant("browser-fixture",admin.userId(),workspace,"KNOWLEDGE_MANAGE",true,"OPS_REQUEST");maintenance.grant("browser-fixture",admin.userId(),workspace,"SKILL_MANAGE",true,"OPS_REQUEST");
        // 模拟资料只用既有解析/导入能力，不伪装管理端异步导入验收。
        var doc=app.getBean(io.devicetruth.knowledge.KnowledgeImportService.class).importDocument("workspace-"+workspace,"default",new io.devicetruth.knowledge.KnowledgeImportService.ImportRequest("模拟检修手册","general",null,"2026.1","fixture.md","text/markdown","# 检查记录\n\n先记录设备型号和故障时间，再按手册核验。","SIMULATED"));
        Files.writeString(Path.of(args[0]),Json.write(Map.of("backend","http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort(),"workspaceId",workspace,"adminId",admin.userId(),"memberId",member.userId(),"documentId",doc.document().documentId())));
    }
}
