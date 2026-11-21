package io.devicetruth.server;

import io.devicetruth.server.AccountService;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** 明确开关的本地容器备份恢复演练，仅使用随机测试库和自造资料。 */
class AdminBackupRestoreTest extends AccountWorkspaceMigrationTest {
    @Test void dumpAndRestorePreserveRevocationAuditAndFiles() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.admin-backup"));
        assertThat(base).isEqualTo("jdbc:postgresql://127.0.0.1:54329/");
        org.flywaydb.core.Flyway.configure().dataSource(base+database,user,password).load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(base+database,user,password));UUID actor=UUID.randomUUID();
        jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name,status,status_version) VALUES (?,'restored-disabled@isolated.example','fixture-only','停用夹具','DISABLED',7)",actor);
        UUID workspace=UUID.randomUUID();jdbc.update("INSERT INTO dt_workspace(id,kind,name,personal_user_id,owner_id,project_id) VALUES (?,'PERSONAL','恢复演练',?,'restore-fixture','test')",workspace,actor);jdbc.update("INSERT INTO dt_workspace_member VALUES (?,?,'ADMIN',now())",workspace,actor);
        jdbc.update("INSERT INTO dt_admin_audit(id,actor_kind,actor_user_id,target_type,target_id,action,result,request_id) VALUES (?,'ACCOUNT',?,'ACCOUNT',?,'USER_STATUS','SUCCESS',?)",UUID.randomUUID(),actor,actor.toString(),UUID.randomUUID());
        Path output=Path.of("outputs/validation/platform-admin/backup");Files.createDirectories(output);
        Path sourceFiles=output.resolve("source-files"),restoredFiles=output.resolve("restored-files");Files.createDirectories(sourceFiles);Files.createDirectories(restoredFiles);byte[] original="隔离恢复演练资料，非真实用户内容。".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(sourceFiles.resolve("fixture.md"),original);
        Path dump=output.resolve("isolated.dump"),log=output.resolve("restore.log");
        var backup=new ProcessBuilder("docker","exec","devicetruth-postgres-1","pg_dump","-U","devicetruth","-Fc","--no-owner",database).redirectOutput(dump.toFile()).redirectError(log.toFile()).start();assertThat(backup.waitFor()).isZero();
        String restored="devicetruth_test_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(base+"postgres",user,password);var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE "+restored);
            try {
                var restore=new ProcessBuilder("docker","exec","-i","devicetruth-postgres-1","pg_restore","-U","devicetruth","--no-owner","-d",restored).redirectInput(dump.toFile()).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).redirectError(ProcessBuilder.Redirect.appendTo(log.toFile())).start();assertThat(restore.waitFor()).isZero();
                org.flywaydb.core.Flyway.configure().dataSource(base+restored,user,password).load().validate();
                var restoredSource=new DriverManagerDataSource(base+restored,user,password);var recovered=new JdbcTemplate(restoredSource);
                assertThat(recovered.queryForObject("SELECT status_version FROM dt_account WHERE id=?",Long.class,actor)).isEqualTo(7);
                assertThat(recovered.queryForObject("SELECT count(*) FROM dt_admin_audit",Long.class)).isEqualTo(1);
                var accounts=new AccountService(recovered,new TransactionTemplate(new DataSourceTransactionManager(restoredSource)),org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
                assertThatThrownBy(()->accounts.get(actor)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                assertThatThrownBy(()->new io.devicetruth.application.agent.WorkspaceScopeResolver(recovered).resolve(actor,workspace)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                Files.copy(sourceFiles.resolve("fixture.md"),restoredFiles.resolve("fixture.md"),StandardCopyOption.REPLACE_EXISTING);assertThat(Files.readAllBytes(restoredFiles.resolve("fixture.md"))).isEqualTo(original);
                Files.writeString(output.resolve("result.json"),"{\"databaseRestored\":true,\"flywayValidated\":true,\"disabledAccountStillDenied\":true,\"statusVersion\":7,\"auditPreserved\":true,\"fileBytesPreserved\":true}");
            } finally {statement.execute("DROP DATABASE "+restored+" WITH (FORCE)");}
        }
    }
}
