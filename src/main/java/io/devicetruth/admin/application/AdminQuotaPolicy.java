package io.devicetruth.admin.application;

import io.devicetruth.admin.api.AdminFailure;
import io.devicetruth.admin.persistence.AdminAudit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 小团队试运行策略。安装策略不等于开放准入适配器；只可通过可信维护入口配置。 */
public final class AdminQuotaPolicy {
    public record Rule(String resource,String period,long account,long workspace,long platform) {}
    public static final List<Rule> PILOT=List.of(
            new Rule("ACTIVE_RUN","OCCUPANCY",2,4,8),
            new Rule("UNRESOLVED_RUN","OCCUPANCY",10,40,100),
            new Rule("RUN_CREATE","DAY",100,500,2000),
            new Rule("UPLOAD_BYTES","DAY",20_000_000,100_000_000,1_000_000_000),
            new Rule("IMPORT_JOB","OCCUPANCY",2,4,8),
            new Rule("RETRIEVAL","DAY",1000,5000,20000),
            new Rule("EMBEDDING_TOKENS","DAY",500_000,2_000_000,10_000_000),
            new Rule("SSE_CONNECTION","OCCUPANCY",3,12,64),
            new Rule("STORAGE_BYTES","PERSISTENT",100_000_000,1_000_000_000,10_000_000_000L));
    private AdminQuotaPolicy() {}
    public static void install(JdbcTemplate jdbc,TransactionTemplate transactions,String operator,String reason) {
        if(operator==null || !operator.matches("[A-Za-z0-9][A-Za-z0-9._@-]{0,99}"))throw AdminFailure.input();AdminService.reason(reason);
        transactions.executeWithoutResult(tx->{
            jdbc.queryForObject("SELECT revision FROM dt_admin_control WHERE id=1 FOR UPDATE",Long.class);
            for(var rule:PILOT)for(String dimension:List.of("ACCOUNT","WORKSPACE","PLATFORM")) {
                long limit=switch(dimension){case "ACCOUNT"->rule.account();case "WORKSPACE"->rule.workspace();default->rule.platform();};
                jdbc.update("INSERT INTO dt_quota_policy(dimension,resource,configured_limit,period_kind,timezone,policy_version) VALUES (?,?,?,?,'Asia/Shanghai','pilot-v1') ON CONFLICT(dimension,resource) DO UPDATE SET configured_limit=excluded.configured_limit,period_kind=excluded.period_kind,timezone=excluded.timezone,policy_version=excluded.policy_version",dimension,rule.resource(),limit,rule.period());
            }
            jdbc.update("UPDATE dt_quota_reconciliation SET state='REQUIRED'");
            new AdminAudit(jdbc).append(null,operator,null,"QUOTA_POLICY","pilot-v1","QUOTA_POLICY_INSTALL","SUCCESS",reason,null,UUID.randomUUID(),null,Map.of("status","CONFIGURED_PENDING_INTEGRATION"));
        });
    }
}
