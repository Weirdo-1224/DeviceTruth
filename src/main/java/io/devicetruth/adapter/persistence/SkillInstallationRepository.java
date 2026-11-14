package io.devicetruth.adapter.persistence;

import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Skill 许可和 run 检查点均由服务器归属约束。 */
public final class SkillInstallationRepository {
    public record Installation(String skillId, String version, Set<String> allowedTools) {}
    public record Selection(String skillId, String version, List<String> devices, List<String> tasks,
                            Set<String> allowedTools, String documentDigest) {}

    private final JdbcTemplate jdbc;

    public SkillInstallationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 默认许可不覆盖运维停用状态或已有白名单。 */
    public void installDefault(ServerIdentity identity, String skillId, String version, Set<String> tools) {
        jdbc.update("""
                INSERT INTO dt_skill_installation(owner_id,project_id,skill_id,version,allowed_tools)
                VALUES (?,?,?,?,?)
                ON CONFLICT DO NOTHING
                """, identity.ownerId(), identity.projectId(), skillId, version, tools.toArray(String[]::new));
    }

    public List<Installation> enabled(ServerIdentity identity) {
        return jdbc.query("""
                SELECT skill_id, version, allowed_tools FROM dt_skill_installation
                WHERE owner_id=? AND project_id=? AND enabled
                """, (row, index) -> new Installation(row.getString("skill_id"), row.getString("version"),
                Set.copyOf(strings(row, "allowed_tools"))), identity.ownerId(), identity.projectId());
    }

    public Selection selection(ServerIdentity identity, UUID runId) {
        return jdbc.query("""
                SELECT skill_id, version, devices, tasks, allowed_tools, document_digest FROM dt_run_skill
                WHERE run_id=? AND owner_id=? AND project_id=?
                """, (row, index) -> new Selection(row.getString("skill_id"), row.getString("version"),
                strings(row, "devices"), strings(row, "tasks"), Set.copyOf(strings(row, "allowed_tools")),
                row.getString("document_digest")), runId, identity.ownerId(), identity.projectId())
                .stream().findFirst().orElse(null);
    }

    public boolean select(ServerIdentity identity, UUID runId, long leaseVersion, Selection selection) {
        // 租约在运行段内固定；旧 worker 不得替后续运行段保存 Skill 检查点。
        return jdbc.update("""
                WITH current_run AS (
                    SELECT id, conversation_id, owner_id, project_id FROM dt_agent_run
                    WHERE id=? AND owner_id=? AND project_id=? AND version=? AND status='RUNNING'
                      AND cancel_requested_at IS NULL AND lease_expires_at > clock_timestamp() FOR NO KEY UPDATE
                )
                INSERT INTO dt_run_skill(run_id,conversation_id,owner_id,project_id,skill_id,version,devices,tasks,
                    allowed_tools,document_digest)
                SELECT r.id,r.conversation_id,r.owner_id,r.project_id,i.skill_id,i.version,?,?,?,?
                FROM current_run r JOIN dt_skill_installation i
                    ON i.owner_id=r.owner_id AND i.project_id=r.project_id
                WHERE i.skill_id=? AND i.version=? AND i.enabled AND ?::text[] <@ i.allowed_tools
                ON CONFLICT (run_id) DO NOTHING
                """, runId, identity.ownerId(), identity.projectId(), leaseVersion,
                selection.devices().toArray(String[]::new), selection.tasks().toArray(String[]::new),
                selection.allowedTools().toArray(String[]::new), selection.documentDigest(), selection.skillId(),
                selection.version(), selection.allowedTools().toArray(String[]::new)) == 1;
    }

    /** 在提交答案的事务中锁住授权记录，防止撤销许可后仍发布结果。 */
    public boolean lockEnabledSelection(ServerIdentity identity, UUID runId) {
        return !jdbc.query("""
                SELECT i.skill_id FROM dt_run_skill s JOIN dt_skill_installation i
                  USING (owner_id, project_id, skill_id, version)
                WHERE s.run_id=? AND s.owner_id=? AND s.project_id=? AND i.enabled
                  AND s.allowed_tools <@ i.allowed_tools
                FOR SHARE OF i
                """, (row, index) -> row.getString("skill_id"), runId, identity.ownerId(), identity.projectId())
                .isEmpty();
    }

    public String referenceDigest(ServerIdentity identity, UUID runId, String name) {
        return jdbc.query("""
                SELECT r.content_digest FROM dt_run_skill_reference r JOIN dt_run_skill s USING (run_id)
                WHERE r.run_id=? AND s.owner_id=? AND s.project_id=? AND r.name=?
                """, (row, index) -> row.getString("content_digest"), runId, identity.ownerId(), identity.projectId(),
                name).stream().findFirst().orElse(null);
    }

    public String lastReferenceName(ServerIdentity identity, UUID runId) {
        return jdbc.query("""
                SELECT active_reference_name FROM dt_run_skill WHERE run_id=? AND owner_id=? AND project_id=?
                """, (row, index) -> row.getString(1), runId, identity.ownerId(), identity.projectId()).getFirst();
    }

    public void pinReference(ServerIdentity identity, UUID runId, long leaseVersion, String name, String digest) {
        jdbc.update("""
                WITH current_run AS (
                    SELECT id FROM dt_agent_run
                    WHERE id=? AND owner_id=? AND project_id=? AND version=? AND status='RUNNING'
                      AND cancel_requested_at IS NULL AND lease_expires_at > clock_timestamp() FOR NO KEY UPDATE
                ), pinned AS (
                    INSERT INTO dt_run_skill_reference(run_id,name,content_digest)
                    SELECT id,?,? FROM current_run
                    ON CONFLICT DO NOTHING RETURNING run_id
                )
                UPDATE dt_run_skill s SET active_reference_name=?
                FROM current_run r
                WHERE s.run_id=r.id AND (EXISTS(SELECT 1 FROM pinned) OR EXISTS(
                    SELECT 1 FROM dt_run_skill_reference p
                    WHERE p.run_id=r.id AND p.name=? AND p.content_digest=?
                ))
                """, runId, identity.ownerId(), identity.projectId(), leaseVersion, name, digest, name, name, digest);
    }

    private static List<String> strings(ResultSet row, String column) throws SQLException {
        Array array = row.getArray(column);
        try {
            return List.of((String[]) array.getArray());
        } finally {
            array.free();
        }
    }
}
