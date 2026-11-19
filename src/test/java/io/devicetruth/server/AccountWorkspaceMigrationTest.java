package io.devicetruth.server;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** 仅创建随机隔离库，不从产品配置读取数据库。 */
class AccountWorkspaceMigrationTest {
    protected String base;
    protected String user;
    protected String password;
    protected String database;
    private JdbcTemplate jdbc;

    @BeforeEach void createDatabase() throws Exception {
        base = required("DEVICETRUTH_TEST_PG_BASE_URL");
        user = required("DEVICETRUTH_TEST_PG_USERNAME");
        password = required("DEVICETRUTH_TEST_PG_PASSWORD");
        if (!base.matches("jdbc:postgresql://[A-Za-z0-9.\\-\\[\\]:]+/"))
            throw new IllegalArgumentException("Test PG base URL must contain host/port only");
        String candidate = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base + "postgres", user, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + candidate);
        }
        database = candidate;
        jdbc = new JdbcTemplate(new DriverManagerDataSource(base + database, user, password));
    }

    @AfterEach void dropDatabase() throws Exception {
        if (database == null) return;
        try (var connection = DriverManager.getConnection(base + "postgres", user, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE " + database);
        }
    }

    @Test void emptyDatabaseEnforcesAccountWorkspaceAndSessionConstraints() {
        migrate(null);
        UUID account = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name) VALUES (?, 'a@example.com','encoded','甲')", account);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name) VALUES (?, ' A@example.com','encoded','乙')", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name) VALUES (?, 'a@example.com','encoded','乙')", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        UUID workspace = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_workspace(id,kind,name,personal_user_id,owner_id,project_id) VALUES (?, 'PERSONAL','个人',?,'new-owner','new-project')", workspace, account);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dt_workspace(id,kind,name,personal_user_id,owner_id,project_id) VALUES (?, 'PERSONAL','重复',?,'other','other')", UUID.randomUUID(), account))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dt_workspace(id,kind,name,owner_id,project_id) VALUES (?, 'TEAM','重复','new-owner','new-project')", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'ADMIN')", workspace, UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String session = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO spring_session VALUES (?,?,0,0,28800,28800000,?)", session, session, account.toString());
        jdbc.update("INSERT INTO spring_session_attributes VALUES (?,'security',?)", session, new byte[]{1});
        jdbc.update("DELETE FROM spring_session WHERE primary_id=?", session);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session_attributes", Long.class)).isZero();
    }

    @Test void v18UpgradePreservesUnclaimedLegacyConversation() {
        migrate("18");
        UUID conversation = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,created_at,updated_at) VALUES (?,'legacy','legacy',now(),now())", conversation);
        for (int seq = 1; seq <= 2; seq++) {
            UUID message = UUID.randomUUID();
            jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) VALUES (?,?,'legacy','legacy',?,'USER','历史补充',now(),now())", message, conversation, seq);
            jdbc.update("INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,budget,created_at,updated_at) VALUES (?,?,'legacy','legacy',?,'QUEUED',now(),now(),'{}',now(),now())", UUID.randomUUID(), conversation, message);
        }
        migrate(null);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM dt_conversation WHERE id=?", String.class, conversation)).isEqualTo("legacy");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_account", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_agent_run WHERE initiated_by_user_id IS NULL AND execution_actor_user_id IS NULL AND status='QUEUED'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_conversation WHERE workspace_id IS NULL AND created_by_user_id IS NULL", Long.class)).isEqualTo(1);
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name) VALUES (?,'historian@example.com','encoded','记录人')", actor);
        UUID discussion = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_investigation_discussion(id,conversation_id,owner_id,project_id,author_user_id,seq,content) VALUES (?,?,'legacy','legacy',?,1,'未核实讨论')", discussion, conversation, actor);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dt_investigation_discussion(id,conversation_id,owner_id,project_id,author_user_id,seq,content) VALUES (?,?,'legacy','legacy',?,1,'重复')", UUID.randomUUID(), conversation, actor))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid='dt_agent_run'::regclass AND contype='f'", Long.class)).isPositive();
    }

    private void migrate(String target) {
        var configuration = Flyway.configure().dataSource(base + database, user, password);
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    @Test void workspaceScopeUsesCurrentMembershipAndNeverHeaderIdentity() {
        migrate(null);
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name) VALUES (?,'actor@example.com','encoded','甲'),(?,'other@example.com','encoded','乙')", actor, other);
        UUID personal = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        jdbc.update("INSERT INTO dt_workspace(id,kind,name,personal_user_id,owner_id,project_id) VALUES (?,'PERSONAL','个人',?,'personal','project')", personal, actor);
        jdbc.update("INSERT INTO dt_workspace(id,kind,name,owner_id,project_id) VALUES (?,'TEAM','团队','team','project'),(?,'TEAM','其他团队','hidden','project')", team, hidden);
        jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'ADMIN'),(?,?,'COLLABORATOR'),(?,?,'ADMIN')", personal, actor, team, actor, hidden, other);
        var resolver = new io.devicetruth.application.agent.WorkspaceScopeResolver(jdbc);
        assertThat(resolver.resolve(actor, null).workspaceId()).isEqualTo(personal);
        assertThat(resolver.resolve(actor, team).storageIdentity().ownerId()).isEqualTo("team");
        assertThatThrownBy(() -> resolver.resolve(actor, hidden)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> resolver.resolve(null, team)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> resolver.resolveResource(actor, team, personal)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var previous = resolver.resolve(actor, team);
        jdbc.update("UPDATE dt_workspace_member SET role='VIEWER' WHERE workspace_id=? AND user_id=?", team, actor);
        assertThat(resolver.resolve(actor, team).canWrite()).isFalse();
        assertThatThrownBy(() -> resolver.requireWrite(previous)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("DELETE FROM dt_workspace_member WHERE workspace_id=? AND user_id=?", team, actor);
        assertThatThrownBy(() -> resolver.resolveResource(actor, team, null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test configuration: " + name);
        return value;
    }
}
