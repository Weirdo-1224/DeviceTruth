package io.devicetruth.admin.application;

import io.devicetruth.admin.api.*;
import io.devicetruth.admin.authorization.AdminAuthorization;
import io.devicetruth.admin.persistence.AdminAudit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅可信本机维护入口使用，不注册 HTTP Controller。 */
public final class AdminMaintenance {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AdminAuthorization authorization;
    private final AdminAudit audit;
    public AdminMaintenance(JdbcTemplate jdbc,TransactionTemplate transactions) {
        this.jdbc=jdbc;this.transactions=transactions;authorization=new AdminAuthorization(jdbc);audit=new AdminAudit(jdbc);
    }
    public void platform(String operator,UUID target,boolean enabled,boolean bootstrap,String reason) {
        validate(operator,reason);
        transactions.executeWithoutResult(tx -> {
            authorization.maintenanceLock(); if(enabled)active(target);else existing(target);
            long current=jdbc.queryForObject("SELECT count(*) FROM dt_platform_admin",Long.class);
            if(bootstrap && (current!=0 || !enabled)) throw new AdminFailure(409,"PLATFORM_ALREADY_INITIALIZED");
            if(!enabled) authorization.protectLast(target);
            boolean exists=jdbc.queryForObject("SELECT count(*) FROM dt_platform_admin WHERE user_id=?",Long.class,target)==1;
            if(enabled) jdbc.update("INSERT INTO dt_platform_admin VALUES (?) ON CONFLICT DO NOTHING",target);
            else jdbc.update("DELETE FROM dt_platform_admin WHERE user_id=?",target);
            if(exists!=enabled) authorization.revised();
            audit.append(null,operator,null,"PLATFORM_ROLE",target.toString(),bootstrap?"BOOTSTRAP":"PLATFORM_ROLE_SET",
                    exists==enabled?"NO_CHANGE":"SUCCESS",reason,null,UUID.randomUUID(),Map.of("platformRole",exists?"PLATFORM_ADMIN":"NONE"),Map.of("platformRole",enabled?"PLATFORM_ADMIN":"NONE"));
        });
    }
    public void grant(String operator,UUID target,UUID workspace,String capability,boolean enabled,String reason) {
        validate(operator,reason); AdminPages.choice(capability,"KNOWLEDGE_READ","KNOWLEDGE_MANAGE","SKILL_READ","SKILL_MANAGE");
        if(capability==null) throw AdminFailure.input();
        transactions.executeWithoutResult(tx -> {
            authorization.maintenanceLock();if(enabled)active(target);else existing(target);
            if(jdbc.queryForObject("SELECT count(*) FROM dt_workspace WHERE id=?",Long.class,workspace)!=1) throw AdminFailure.missing();
            int changed=enabled?jdbc.update("INSERT INTO dt_admin_workspace_grant VALUES (?,?,?) ON CONFLICT DO NOTHING",target,workspace,capability)
                    :jdbc.update("DELETE FROM dt_admin_workspace_grant WHERE user_id=? AND workspace_id=? AND capability=?",target,workspace,capability);
            if(changed>0) authorization.revised();
            audit.append(null,operator,workspace,"WORKSPACE_GRANT",target.toString(),"WORKSPACE_GRANT_SET",changed==0?"NO_CHANGE":"SUCCESS",reason,null,UUID.randomUUID(),null,Map.of("capabilities",enabled?List.of(capability):List.of()));
        });
    }
    private void existing(UUID target) {
        if(jdbc.queryForList("SELECT id FROM dt_account WHERE id=? FOR UPDATE",UUID.class,target).isEmpty())throw AdminFailure.missing();
    }
    private void active(UUID target) {
        if(jdbc.queryForList("SELECT id FROM dt_account WHERE id=? AND status='ACTIVE' FOR UPDATE",UUID.class,target).isEmpty()) throw AdminFailure.missing();
    }
    private static void validate(String operator,String reason) {
        if(operator==null || !operator.matches("[A-Za-z0-9][A-Za-z0-9._@-]{0,99}")) throw AdminFailure.input();
        AdminService.reason(reason);
    }
}
