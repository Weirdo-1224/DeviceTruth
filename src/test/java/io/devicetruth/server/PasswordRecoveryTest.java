package io.devicetruth.server;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** 真实 PG 锁/事务与邮件捕获替身；不计真实 SMTP 验收。 */
class PasswordRecoveryTest {
    private final AccountWorkspaceMigrationTest databaseFixture = new AccountWorkspaceMigrationTest();
    private JdbcTemplate jdbc;
    private AccountService accounts;
    private PasswordRecoveryService recovery;
    private final Mail mail = new Mail();
    private UUID actor;
    private static final String OLD = "original-password-123";
    private static final String NEW = "replacement-password-456";

    static final class Mail implements PasswordRecoveryService.ResetMailSender {
        String token;
        boolean enabled = true;
        boolean fail;
        public boolean available() { return enabled; }
        public void send(String email, String value) { token = value; if (fail) throw new IllegalStateException("test failure"); }
    }
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { databaseFixture.dropDatabase(); }
    @BeforeEach void initializeRecovery() throws Exception {
        databaseFixture.createDatabase();
        var source = new DriverManagerDataSource(databaseFixture.base + databaseFixture.database, databaseFixture.user, databaseFixture.password);
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var passwords = new ProductAuthConfiguration().accountPasswordEncoder();
        accounts = new AccountService(jdbc, transactions, passwords);
        recovery = new PasswordRecoveryService(jdbc, transactions, passwords, mail, Duration.ofMinutes(30));
        actor = accounts.register("repair@example.com", OLD, "工程师").userId();
    }
    @Test void successfulResetConsumesTokenAndRevokesAllSessions() {
        for (int i = 0; i < 2; i++) {
            String session = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO spring_session VALUES (?,?,0,0,28800,28800000,?)", session, session, actor.toString());
            jdbc.update("INSERT INTO spring_session_attributes VALUES (?,'test',?)", session, new byte[]{1});
        }
        recovery.request(" Repair@Example.com ");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM dt_password_reset", String.class)).hasSize(64).isNotEqualTo(mail.token);
        assertThat(jdbc.queryForObject("SELECT expires_at>now()+interval '29 minutes' FROM dt_password_reset", Boolean.class)).isTrue();
        recovery.reset(mail.token, NEW, NEW);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session_attributes", Long.class)).isZero();
        assertThat(accounts.authenticate("repair@example.com", NEW).userId()).isEqualTo(actor);
        assertThatThrownBy(() -> accounts.authenticate("repair@example.com", OLD)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> recovery.reset(mail.token, OLD, OLD)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void mismatchExpiryAndRotationDoNotChangePassword() {
        recovery.request("repair@example.com");
        String first = mail.token;
        assertThatThrownBy(() -> recovery.reset(first, NEW, OLD)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(accounts.authenticate("repair@example.com", OLD).userId()).isEqualTo(actor);
        recovery.request("repair@example.com");
        assertThatThrownBy(() -> recovery.reset(first, NEW, NEW)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE dt_password_reset SET expires_at=now()-interval '1 second'");
        assertThatThrownBy(() -> recovery.reset(mail.token, NEW, NEW)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(accounts.authenticate("repair@example.com", OLD).userId()).isEqualTo(actor);
    }
    @Test void concurrentConsumptionHasOnlyOneWinner() throws Exception {
        recovery.request("repair@example.com");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> consume = () -> { start.await(); try { recovery.reset(mail.token, NEW, NEW); return true; }
                catch (org.springframework.web.server.ResponseStatusException e) { return false; } };
            var a = executor.submit(consume); var b = executor.submit(consume); start.countDown();
            assertThat(java.util.List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }
    @Test void unknownMailboxAndFailedDeliveryDoNotExposeCredentials() {
        recovery.request("unknown@example.com");
        assertThat(mail.token).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_password_reset", Long.class)).isZero();
        mail.fail = true;
        recovery.request("repair@example.com");
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM dt_password_reset", String.class)).isEqualTo("FAILED");
        assertThatThrownBy(() -> recovery.reset(mail.token, NEW, NEW)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        mail.enabled = false;
        for (String email : java.util.List.of("repair@example.com", "unknown@example.com")) {
            assertThatThrownBy(() -> recovery.request(email)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("MAIL_UNAVAILABLE");
        }
    }
}
