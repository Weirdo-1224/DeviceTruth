package io.devicetruth.server;

import io.devicetruth.admin.application.*;
import io.devicetruth.admin.api.AdminFailure;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class AdminQuotaLedgerTest extends AccountWorkspaceMigrationTest {
    @Test void twoInstancesContendAtomicallyAndRetryNeverDuplicatesOrRefundsConsumption() throws Exception {
        org.flywaydb.core.Flyway.configure().dataSource(base+database,user,password).load().migrate();
        var source=new DriverManagerDataSource(base+database,user,password);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        for(String dimension:List.of("ACCOUNT","WORKSPACE","PLATFORM"))jdbc.update("INSERT INTO dt_quota_policy VALUES (?,'ACTIVE_RUN',1,'OCCUPANCY','Asia/Shanghai','test')",dimension);
        var first=new AdminQuotaLedger(jdbc,tx);var second=new AdminQuotaLedger(new JdbcTemplate(source),new TransactionTemplate(new DataSourceTransactionManager(source)));
        var demands=List.of(new AdminQuotaLedger.Demand("ACCOUNT",UUID.randomUUID().toString(),"ACTIVE_RUN",1),new AdminQuotaLedger.Demand("WORKSPACE",UUID.randomUUID().toString(),"ACTIVE_RUN",1),new AdminQuotaLedger.Demand("PLATFORM","PLATFORM","ACTIVE_RUN",1));
        var otherDemands=List.of(new AdminQuotaLedger.Demand("ACCOUNT",UUID.randomUUID().toString(),"ACTIVE_RUN",1),new AdminQuotaLedger.Demand("WORKSPACE",UUID.randomUUID().toString(),"ACTIVE_RUN",1),new AdminQuotaLedger.Demand("PLATFORM","PLATFORM","ACTIVE_RUN",1));
        UUID key=UUID.randomUUID(),other=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=pool.submit(()->{barrier.await();try{first.reserve(key,"a".repeat(64),null,demands);return "SUCCESS";}catch(AdminFailure e){return e.code;}});
            var b=pool.submit(()->{barrier.await();try{second.reserve(other,"a".repeat(64),null,otherDemands);return "SUCCESS";}catch(AdminFailure e){return e.code;}});
            var resultA=a.get();var resultB=b.get();assertThat(List.of(resultA,resultB)).containsExactlyInAnyOrder("SUCCESS","QUOTA_EXCEEDED");
            UUID winner=resultA.equals("SUCCESS")?key:other;var winningDemands=resultA.equals("SUCCESS")?demands:otherDemands;assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_quota_reservation",Long.class)).isEqualTo(3);
            assertThat(first.reserve(winner,"a".repeat(64),null,winningDemands)).isFalse();
            assertThatThrownBy(()->second.reserve(winner,"b".repeat(64),null,winningDemands)).hasMessage("IDEMPOTENCY_PAYLOAD_CONFLICT");
            first.release(winner);first.release(winner);assertThat(second.reserve(UUID.randomUUID(),"c".repeat(64),null,demands)).isTrue();
            jdbc.update("INSERT INTO dt_quota_policy VALUES ('PLATFORM','RUN_CREATE',100,'DAY','Asia/Shanghai','test')");UUID consumed=UUID.randomUUID();first.reserve(consumed,"d".repeat(64),null,List.of(new AdminQuotaLedger.Demand("PLATFORM","PLATFORM","RUN_CREATE",1)));first.consumed(consumed);first.release(consumed);
            assertThat(jdbc.queryForObject("SELECT state FROM dt_quota_reservation WHERE reservation_key=?",String.class,consumed)).isEqualTo("CONSUMED");
            AdminQuotaPolicy.install(jdbc,tx,"test-operator","OPS_REQUEST");assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_quota_policy",Long.class)).isEqualTo(27);
        } finally {pool.shutdownNow();}
    }
    @Test void waitingAndTerminalReconciliationOnlyReleaseOccupancy() {
        org.flywaydb.core.Flyway.configure().dataSource(base+database,user,password).load().migrate();
        var source=new DriverManagerDataSource(base+database,user,password);var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        for(String resource:List.of("ACTIVE_RUN","UNRESOLVED_RUN"))jdbc.update("INSERT INTO dt_quota_policy VALUES ('PLATFORM',?,10,'OCCUPANCY','Asia/Shanghai','test')",resource);
        jdbc.update("INSERT INTO dt_quota_policy VALUES ('PLATFORM','RUN_CREATE',100,'DAY','Asia/Shanghai','test')");
        UUID conversation=UUID.randomUUID(),message=UUID.randomUUID(),run=UUID.randomUUID();
        jdbc.update("INSERT INTO dt_conversation(id,owner_id,project_id,created_at,updated_at) VALUES (?,'quota-test','test',now(),now())",conversation);
        jdbc.update("INSERT INTO dt_message(id,conversation_id,owner_id,project_id,seq,role,content,requested_at,as_of) VALUES (?,?,'quota-test','test',1,'USER','fixture',now(),now())",message,conversation);
        jdbc.update("INSERT INTO dt_agent_run(id,conversation_id,owner_id,project_id,goal_message_id,status,requested_at,as_of,budget,created_at,updated_at,round_number) VALUES (?,?,'quota-test','test',?,'QUEUED',now(),now(),'{}',now(),now(),1)",run,conversation,message);
        var ledger=new AdminQuotaLedger(jdbc,tx);UUID key=UUID.randomUUID();ledger.reserve(key,"e".repeat(64),run,List.of(new AdminQuotaLedger.Demand("PLATFORM","PLATFORM","ACTIVE_RUN",1),new AdminQuotaLedger.Demand("PLATFORM","PLATFORM","UNRESOLVED_RUN",1),new AdminQuotaLedger.Demand("PLATFORM","PLATFORM","RUN_CREATE",1)));ledger.consumed(key);
        jdbc.update("UPDATE dt_agent_run SET status='WAITING_FOR_USER',pending_question_id=?,question_version=1,pending_question='{}'::jsonb WHERE id=?",UUID.randomUUID(),run);ledger.reconcileRuns();
        assertThat(jdbc.queryForObject("SELECT state FROM dt_quota_reservation WHERE resource='ACTIVE_RUN'",String.class)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT state FROM dt_quota_reservation WHERE resource='UNRESOLVED_RUN'",String.class)).isEqualTo("RESERVED");
        jdbc.update("UPDATE dt_agent_run SET status='FAILED',pending_question_id=NULL,pending_question=NULL WHERE id=?",run);ledger.reconcileRuns();ledger.reconcileRuns();
        assertThat(jdbc.queryForObject("SELECT state FROM dt_quota_reservation WHERE resource='UNRESOLVED_RUN'",String.class)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT state FROM dt_quota_reservation WHERE resource='RUN_CREATE'",String.class)).isEqualTo("CONSUMED");
    }

}
