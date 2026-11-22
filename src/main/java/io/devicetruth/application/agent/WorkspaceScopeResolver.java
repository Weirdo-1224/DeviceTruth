package io.devicetruth.application.agent;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;

/** actor 只能由认证层提供，不能取请求体中的 userId。每次调用重新查询当前成员关系。 */
public final class WorkspaceScopeResolver {
    private final JdbcTemplate jdbc;
    public WorkspaceScopeResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AccessScope resolve(UUID authenticatedActor, UUID requestedWorkspace) {
        if (authenticatedActor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM dt_account WHERE id=? AND status='ACTIVE')", Boolean.class, authenticatedActor)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        var scopes = jdbc.query("""
                SELECT w.id,w.owner_id,w.project_id,m.role,a.status_version
                FROM dt_workspace w JOIN dt_workspace_member m ON m.workspace_id=w.id JOIN dt_account a ON a.id=m.user_id AND a.status='ACTIVE'
                WHERE m.user_id=? AND
                    ((?::uuid IS NOT NULL AND w.id=?::uuid) OR
                     (?::uuid IS NULL AND w.personal_user_id=?))
                """, (rs, row) -> new AccessScope(authenticatedActor, rs.getObject("id", UUID.class),
                        AccessScope.Role.valueOf(rs.getString("role")),
                        new ServerIdentity(rs.getString("owner_id"), rs.getString("project_id")), rs.getLong("status_version")),
                authenticatedActor, requestedWorkspace, requestedWorkspace, requestedWorkspace, authenticatedActor);
        if (scopes.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return scopes.getFirst();
    }

    /** UUID 路由先确定资源空间；Header 只能缩小范围，不参与选择其他资源。 */
    public AccessScope resolveResource(UUID actor, UUID resourceWorkspace, UUID headerWorkspace) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (resourceWorkspace == null || (headerWorkspace != null && !headerWorkspace.equals(resourceWorkspace)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return resolve(actor, resourceWorkspace);
    }

    public AccessScope requireWrite(AccessScope scope) {
        AccessScope current = resolve(scope.actorUserId(), scope.workspaceId());
        if (!current.canWrite()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return current;
    }
}
