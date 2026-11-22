package io.devicetruth.server;

import io.devicetruth.admin.application.AdminMaintenance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 只为独立预览库创建合成身份；不进入产品 JAR，也不操作用户的真实账号。 */
public final class RealmBrowserFixture {
    public static void main(String[] args) {
        String url=System.getenv("SPRING_DATASOURCE_URL");
        if(url==null || !url.matches("jdbc:postgresql://postgres:5432/devicetruth_login_preview_[a-z0-9_]+"))
            throw new IllegalArgumentException("Isolated preview database required");
        var ds=new DriverManagerDataSource(url,System.getenv("SPRING_DATASOURCE_USERNAME"),System.getenv("SPRING_DATASOURCE_PASSWORD"));
        var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var accounts=new AccountService(jdbc,tx,AccountService.passwordEncoder());
        var user=accounts.register("user@preview.example","isolated-preview-password-123","检修测试员");
        var maintenance=new AdminMaintenance(jdbc,tx);
        var admin=maintenance.createAdmin("login-preview","admin@preview.example","管理测试员","isolated-preview-password-123");
        maintenance.platform("login-preview",admin.userId(),true,true,"OPS_REQUEST");
        var space=new WorkspaceService(jdbc,tx).createTeam(user.userId(),"登录隔离测试空间");
        maintenance.grant("login-preview",admin.userId(),space.workspaceId(),"KNOWLEDGE_MANAGE",true,"OPS_REQUEST");
        maintenance.grant("login-preview",admin.userId(),space.workspaceId(),"SKILL_MANAGE",true,"OPS_REQUEST");
        System.out.println("PREVIEW_IDENTITIES_READY");
    }
}
