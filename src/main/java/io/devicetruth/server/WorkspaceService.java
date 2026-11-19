package io.devicetruth.server;

import io.devicetruth.application.agent.AccessScope.Role;
import io.devicetruth.application.agent.WorkspaceScopeResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 空间管理与平台管理分离；所有成员变更先锁空间，再复查操作者当前角色。 */
public final class WorkspaceService {
    public record WorkspaceView(UUID workspaceId, String kind, String name, Role role) {}
    public record MemberView(UUID userId, String displayName, String email, Role role) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final WorkspaceScopeResolver scopes;
    public WorkspaceService(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc; this.transactions = transactions; this.scopes = new WorkspaceScopeResolver(jdbc);
    }
    public List<WorkspaceView> list(UUID actor) {
        authenticated(actor);
        return jdbc.query("SELECT w.id,w.kind,w.name,m.role FROM dt_workspace w JOIN dt_workspace_member m ON m.workspace_id=w.id WHERE m.user_id=? ORDER BY w.kind,w.created_at,w.id",
                (rs,row) -> new WorkspaceView(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("name"), Role.valueOf(rs.getString("role"))), actor);
    }
    public WorkspaceView createTeam(UUID actor, String name) {
        authenticated(actor); String title = name(name);
        return transactions.execute(status -> {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO dt_workspace(id,kind,name,owner_id,project_id) VALUES (?,'TEAM',?,?,'default')", id, title, "workspace-" + id);
            jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'ADMIN')", id, actor);
            return new WorkspaceView(id, "TEAM", title, Role.ADMIN);
        });
    }
    public WorkspaceView rename(UUID actor, UUID workspace, String name) {
        String title = name(name);
        return transactions.execute(status -> {
            lockManagedTeam(actor, workspace);
            jdbc.update("UPDATE dt_workspace SET name=? WHERE id=?", title, workspace);
            return new WorkspaceView(workspace, "TEAM", title, Role.ADMIN);
        });
    }
    public List<MemberView> members(UUID actor, UUID workspace) {
        scopes.resolve(actor, workspace);
        return jdbc.query("SELECT a.id,a.display_name,a.email,m.role FROM dt_workspace_member m JOIN dt_account a ON a.id=m.user_id WHERE m.workspace_id=? ORDER BY m.joined_at,a.id",
                (rs,row) -> new MemberView(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("email"), Role.valueOf(rs.getString("role"))), workspace);
    }
    public void changeRole(UUID actor, UUID workspace, UUID target, Role role) {
        if (role == null) throw error(HttpStatus.BAD_REQUEST, "INVALID_ROLE");
        transactions.executeWithoutResult(status -> {
            lockManagedTeam(actor, workspace);
            protectLastAdmin(workspace, target, role);
            jdbc.update("UPDATE dt_workspace_member SET role=? WHERE workspace_id=? AND user_id=?", role.name(), workspace, target);
        });
    }
    public void remove(UUID actor, UUID workspace, UUID target) {
        transactions.executeWithoutResult(status -> {
            lockManagedTeam(actor, workspace);
            protectLastAdmin(workspace, target, null);
            jdbc.update("DELETE FROM dt_workspace_member WHERE workspace_id=? AND user_id=?", workspace, target);
        });
    }
    void lockManagedTeam(UUID actor, UUID workspace) {
        authenticated(actor);
        // 先验证可见性，避免未授权请求锁住其他团队；拿锁后再次授权以覆盖撤权竞态。
        scopes.resolve(actor, workspace);
        String kind = jdbc.queryForObject("SELECT kind FROM dt_workspace WHERE id=? FOR UPDATE", String.class, workspace);
        if (!scopes.resolve(actor, workspace).canManage()) throw error(HttpStatus.FORBIDDEN, "WORKSPACE_ADMIN_REQUIRED");
        if (!"TEAM".equals(kind)) throw error(HttpStatus.FORBIDDEN, "PERSONAL_WORKSPACE_IMMUTABLE");
    }
    private void protectLastAdmin(UUID workspace, UUID target, Role replacement) {
        var roles = jdbc.queryForList("SELECT role FROM dt_workspace_member WHERE workspace_id=? AND user_id=?", String.class, workspace, target);
        if (roles.isEmpty()) throw error(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND");
        if ("ADMIN".equals(roles.getFirst()) && replacement != Role.ADMIN &&
                jdbc.queryForObject("SELECT count(*) FROM dt_workspace_member WHERE workspace_id=? AND role='ADMIN'", Long.class, workspace) == 1)
            throw error(HttpStatus.CONFLICT, "LAST_WORKSPACE_ADMIN");
    }
    private static void authenticated(UUID actor) {
        if (actor == null) throw error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
    }
    private static String name(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_WORKSPACE_NAME");
        return name.strip();
    }
    private static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
}
