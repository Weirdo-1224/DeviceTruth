package io.devicetruth.server;
import io.devicetruth.application.agent.AccessScope.Role;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
class TeamInvitationTest {
    private final AccountWorkspaceMigrationTest fixture=new AccountWorkspaceMigrationTest();
    private TeamInvitationService service;
    private WorkspaceService workspaces;
    private JdbcTemplate jdbc;
    private UUID admin,guest,wrong,team;
    private final Mail mail=new Mail();
    static class Mail implements TeamInvitationService.Mail {String token;boolean fail;public boolean available(){return true;}public void send(String email,String token){this.token=token;if(fail)throw new IllegalStateException();}}
    @BeforeEach void setup() throws Exception {
        fixture.createDatabase();var source=new DriverManagerDataSource(fixture.base+fixture.database,fixture.user,fixture.password);
        Flyway.configure().dataSource(source).load().migrate();jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        var accounts=new AccountService(jdbc,tx,new ProductAuthConfiguration().accountPasswordEncoder());
        admin=accounts.register("admin@example.com","long-password-123","管理员").userId();
        guest=accounts.register("guest@example.com","long-password-123","同事").userId();
        wrong=accounts.register("wrong@example.com","long-password-123","其他人").userId();
        workspaces=new WorkspaceService(jdbc,tx);team=workspaces.createTeam(admin,"团队").workspaceId();service=new TeamInvitationService(jdbc,tx,workspaces,mail);
    }
    @AfterEach void cleanup() throws Exception {fixture.dropDatabase();}
    @Test void acceptedInvitationRequiresMatchingEmailAndDoesNotEscalateExistingRole() {
        service.create(admin,team,"guest@example.com",Role.VIEWER);String token=mail.token;
        assertThat(workspaces.list(guest)).hasSize(1);
        assertThatThrownBy(()->service.accept(wrong,token)).hasMessageContaining("INVITATION_EMAIL_MISMATCH");
        assertThat(service.accept(guest,token)).isEqualTo(team);
        assertThatThrownBy(()->service.accept(guest,token)).hasMessageContaining("INVITATION_NOT_PENDING");
        service.create(admin,team,"guest@example.com",Role.ADMIN);service.accept(guest,mail.token);
        assertThat(workspaces.members(guest,team)).filteredOn(m->m.userId().equals(guest)).allMatch(m->m.role()==Role.VIEWER);
    }
    @Test void revokedExpiredRotatedAndFailedInvitationsCannotGrantMembership() {
        var invitation=service.create(admin,team,"guest@example.com",Role.COLLABORATOR);String first=mail.token;
        service.resend(admin,team,invitation.invitationId());assertThatThrownBy(()->service.accept(guest,first)).hasMessageContaining("INVALID_INVITATION");
        String current=mail.token;service.revoke(admin,team,invitation.invitationId());assertThatThrownBy(()->service.accept(guest,current)).hasMessageContaining("INVITATION_NOT_PENDING");
        service.create(admin,team,"guest@example.com",Role.COLLABORATOR);jdbc.update("UPDATE dt_team_invitation SET expires_at=now()-interval '1 second'");
        assertThatThrownBy(()->service.accept(guest,mail.token)).hasMessageContaining("INVITATION_NOT_PENDING");
        mail.fail=true;assertThat(service.create(admin,team,"guest@example.com",Role.COLLABORATOR).deliveryStatus()).isEqualTo("FAILED");
        assertThatThrownBy(()->service.accept(guest,mail.token)).hasMessageContaining("INVITATION_NOT_PENDING");assertThat(workspaces.list(guest)).hasSize(1);
    }
}
