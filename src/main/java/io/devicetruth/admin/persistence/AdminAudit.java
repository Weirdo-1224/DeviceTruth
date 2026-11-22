package io.devicetruth.admin.persistence;

import io.devicetruth.admin.api.AdminFailure;
import io.devicetruth.adapter.Json;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 成功审计在业务事务内；只保存枚举及有限状态。 */
public final class AdminAudit {
    private static final Set<String> STATE_KEYS = Set.of("status","enabled","revision","platformRole","capabilities","accountType");
    private final JdbcTemplate jdbc;
    public AdminAudit(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public UUID append(UUID actor, String operator, UUID workspace, String type, String target, String action,
            String result, String reason, String error, UUID request, Map<String,?> before, Map<String,?> after) {
        if ((before!=null && !STATE_KEYS.containsAll(before.keySet())) || (after!=null && !STATE_KEYS.containsAll(after.keySet())))
            throw new IllegalArgumentException("Invalid audit state");
        UUID id=UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO dt_admin_audit(id,actor_kind,actor_user_id,operator_ref,workspace_id,target_type,target_id,
                      action,result,reason_code,error_code,request_id,before_state,after_state)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)
                    """,id,operator==null?"ACCOUNT":"MAINTENANCE",actor,operator,workspace,type,target,action,result,
                    reason,error,request,before==null?null:Json.write(before),after==null?null:Json.write(after));
            return id;
        } catch (org.springframework.dao.DataAccessException e) { throw new AdminFailure(503,"ADMIN_AUDIT_UNAVAILABLE"); }
    }
}
