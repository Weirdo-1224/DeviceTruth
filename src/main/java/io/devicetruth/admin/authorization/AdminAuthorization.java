package io.devicetruth.admin.authorization;

import io.devicetruth.admin.api.AdminFailure;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 管理范围独立于产品成员角色。管理锁用于授权写入和最后管理员裁决。 */
public final class AdminAuthorization {
    public record Context(UUID userId, long revision) {}
    public record Workspace(UUID id, String owner, String project) {}
    private final JdbcTemplate jdbc;
    public AdminAuthorization(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Context require(UUID actor) {
        var states = jdbc.queryForList("SELECT status FROM dt_account WHERE id=? AND account_type='ADMIN'", String.class, actor);
        if (states.isEmpty()) throw new AdminFailure(401,"AUTHENTICATION_REQUIRED");
        if (!states.getFirst().equals("ACTIVE")) throw new AdminFailure(401,"ACCOUNT_DISABLED");
        if (jdbc.queryForObject("SELECT count(*) FROM dt_platform_admin WHERE user_id=?", Long.class, actor) != 1)
            throw new AdminFailure(403,"PLATFORM_ADMIN_REQUIRED");
        return new Context(actor, jdbc.queryForObject("SELECT revision FROM dt_admin_control WHERE id=1", Long.class));
    }
    public Context lock(UUID actor) {
        jdbc.queryForObject("SELECT revision FROM dt_admin_control WHERE id=1 FOR SHARE", Long.class);
        return require(actor);
    }
    public void maintenanceLock() { jdbc.queryForObject("SELECT revision FROM dt_admin_control WHERE id=1 FOR UPDATE", Long.class); }
    public void revised() { jdbc.update("UPDATE dt_admin_control SET revision=revision+1 WHERE id=1"); }
    public Workspace workspace(UUID actor, UUID target, String capability, String header) {
        require(actor);
        if (target == null || (header != null && !header.equals(target.toString()))) throw AdminFailure.missing();
        String manage = capability.replace("_READ", "_MANAGE");
        var values = jdbc.query("""
                SELECT w.id,w.owner_id,w.project_id FROM dt_workspace w
                WHERE w.id=? AND EXISTS(SELECT 1 FROM dt_admin_workspace_grant g
                  WHERE g.workspace_id=w.id AND g.user_id=? AND g.capability IN (?,?))
                """, (rs,n) -> new Workspace(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),
                target,actor,capability,manage);
        if (values.isEmpty()) throw AdminFailure.missing();
        return values.getFirst();
    }
    public void protectLast(UUID target) {
        if (jdbc.queryForObject("SELECT count(*) FROM dt_platform_admin p JOIN dt_account a ON a.id=p.user_id WHERE a.status='ACTIVE' AND a.account_type='ADMIN' AND a.id=?",Long.class,target)==1
                && jdbc.queryForObject("SELECT count(*) FROM dt_platform_admin p JOIN dt_account a ON a.id=p.user_id WHERE a.status='ACTIVE' AND a.account_type='ADMIN'",Long.class)<=1)
            throw new AdminFailure(409,"LAST_PLATFORM_ADMIN");
    }
}
