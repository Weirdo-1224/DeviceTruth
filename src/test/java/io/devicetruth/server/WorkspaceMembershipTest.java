package io.devicetruth.server;

import io.devicetruth.application.agent.AccessScope.Role;
import java.util.UUID;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class WorkspaceMembershipTest {
    private final AccountWorkspaceMigrationTest fixture = new AccountWorkspaceMigrationTest();
    private JdbcTemplate jdbc;
    private WorkspaceService service;
    private TransactionTemplate transactions;
    private UUID admin;
    private UUID colleague;
    private UUID reader;
    @BeforeEach void setup() throws Exception {
        fixture.createDatabase();
        var source = new DriverManagerDataSource(fixture.base + fixture.database, fixture.user, fixture.password);
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new WorkspaceService(jdbc, transactions);
        var accounts = new AccountService(jdbc, transactions, new ProductAuthConfiguration().accountPasswordEncoder());
        admin = accounts.register("admin@example.com", "long-password-123", "管理员").userId();
        colleague = accounts.register("colleague@example.com", "long-password-123", "协作同事").userId();
        reader = accounts.register("reader@example.com", "long-password-123", "只读同事").userId();
    }
    @AfterEach void cleanup() throws Exception { fixture.dropDatabase(); }
    private void member(UUID workspace, UUID user, Role role) {
        jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,?)", workspace, user, role.name());
    }
    @Test void teamsAreIndependentAndOnlyAdminsManage() {
        UUID team = service.createTeam(admin, "维修团队").workspaceId();
        UUID other = service.createTeam(colleague, "其他团队").workspaceId();
        member(team, colleague, Role.COLLABORATOR); member(team, reader, Role.VIEWER);
        assertThat(service.list(admin)).hasSize(2).noneMatch(w -> w.workspaceId().equals(other));
        assertThat(service.members(reader, team)).hasSize(3);
        for (UUID actor : java.util.List.of(colleague, reader)) {
            assertThatThrownBy(() -> service.rename(actor, team, "越权改名")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
            assertThatThrownBy(() -> service.changeRole(actor, team, actor, Role.ADMIN)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
            assertThatThrownBy(() -> service.remove(actor, team, admin)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        }
        assertThatThrownBy(() -> service.members(admin, other)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThat(service.rename(admin, team, " 新团队名 ").name()).isEqualTo("新团队名");
        service.changeRole(admin, team, colleague, Role.VIEWER);
        service.remove(admin, team, reader);
        assertThatThrownBy(() -> service.members(reader, team)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_account WHERE id=?", Long.class, reader)).isEqualTo(1);
    }
    @Test void personalMembershipAndLastAdminCannotBeChanged() {
        UUID personal = service.list(admin).getFirst().workspaceId();
        assertThatThrownBy(() -> service.rename(admin, personal, "改名")).hasMessageContaining("PERSONAL_WORKSPACE_IMMUTABLE");
        assertThatThrownBy(() -> service.remove(admin, personal, admin)).hasMessageContaining("PERSONAL_WORKSPACE_IMMUTABLE");
        UUID team = service.createTeam(admin, "团队").workspaceId();
        assertThatThrownBy(() -> service.changeRole(admin, team, admin, Role.VIEWER)).hasMessageContaining("LAST_WORKSPACE_ADMIN");
        assertThatThrownBy(() -> service.remove(admin, team, admin)).hasMessageContaining("LAST_WORKSPACE_ADMIN");
        assertThatThrownBy(() -> service.createTeam(admin, " ")).hasMessageContaining("INVALID_WORKSPACE_NAME");
    }
    @Test void concurrentSelfDemotionAndRemovalPreserveOneAdminAcrossServiceInstances() throws Exception {
        for (boolean remove : java.util.List.of(false, true)) {
            UUID team = service.createTeam(admin, "并发团队").workspaceId();
            member(team, colleague, Role.ADMIN);
            var second = new WorkspaceService(jdbc, transactions);
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                Callable<Boolean> a = () -> attempt(service, admin, team, remove, start);
                Callable<Boolean> b = () -> attempt(second, colleague, team, remove, start);
                var x = executor.submit(a); var y = executor.submit(b); start.countDown();
                assertThat(java.util.List.of(x.get(10, TimeUnit.SECONDS), y.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_workspace_member WHERE workspace_id=? AND role='ADMIN'", Long.class, team)).isEqualTo(1);
        }
    }
    private boolean attempt(WorkspaceService instance, UUID actor, UUID workspace, boolean remove, CountDownLatch start) throws Exception {
        start.await();
        try { if (remove) instance.remove(actor, workspace, actor); else instance.changeRole(actor, workspace, actor, Role.VIEWER); return true; }
        catch (ResponseStatusException e) { assertThat(e.getStatusCode().value()).isEqualTo(409); return false; }
    }
}
