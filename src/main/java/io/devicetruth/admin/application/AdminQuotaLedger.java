package io.devicetruth.admin.application;

import io.devicetruth.admin.api.AdminFailure;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 原子资源帐本。产品提交适配器必须先处理原幂等回执，再在同一事务调用；本类不另建任务。 */
public final class AdminQuotaLedger {
    public record Demand(String dimension,String subjectId,String resource,long amount) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public AdminQuotaLedger(JdbcTemplate jdbc,TransactionTemplate transactions){this.jdbc=jdbc;this.transactions=transactions;}
    public boolean reserve(UUID key,String payloadDigest,UUID run,List<Demand> demands) {
        if(key==null || payloadDigest==null || !payloadDigest.matches("[a-f0-9]{64}") || demands==null || demands.isEmpty() || demands.size()>30)throw AdminFailure.input();
        var ordered=demands.stream().sorted(Comparator.comparing(Demand::dimension).thenComparing(Demand::subjectId).thenComparing(Demand::resource)).toList();
        if(new HashSet<>(ordered.stream().map(d->d.dimension()+"/"+d.subjectId()+"/"+d.resource()).toList()).size()!=ordered.size())throw AdminFailure.input();
        return transactions.execute(tx->{
            // 锁顺序固定；同一资源策略行裁决所有实例，整个准入失败时不留下部分维度。
            jdbc.queryForList("SELECT dimension,resource FROM dt_quota_policy ORDER BY dimension,resource FOR UPDATE");
            var prior=jdbc.queryForList("SELECT dimension,subject_id,resource,amount,payload_digest FROM dt_quota_reservation WHERE reservation_key=?",key);
            if(!prior.isEmpty()) {
                if(prior.size()!=ordered.size() || prior.stream().anyMatch(row->!payloadDigest.equals(row.get("payload_digest")) || ordered.stream().noneMatch(d->d.dimension().equals(row.get("dimension")) && d.subjectId().equals(row.get("subject_id")) && d.resource().equals(row.get("resource")) && d.amount()==((Number)row.get("amount")).longValue())))
                    throw new AdminFailure(409,"IDEMPOTENCY_PAYLOAD_CONFLICT");
                return false;
            }
            Instant now=Instant.now();
            for(var demand:ordered) {
                if(!List.of("ACCOUNT","WORKSPACE","PLATFORM").contains(demand.dimension()) || !AdminQuotaViews.RESOURCES.contains(demand.resource()) || demand.amount()<0 || demand.subjectId()==null)throw AdminFailure.input();
                var policies=jdbc.queryForList("SELECT configured_limit,period_kind,timezone FROM dt_quota_policy WHERE dimension=? AND resource=?",demand.dimension(),demand.resource());
                if(policies.isEmpty())throw new AdminFailure(503,"QUOTA_POLICY_NOT_CONFIGURED");var policy=policies.getFirst();
                String kind=policy.get("period_kind").toString();
                Instant period=kind.equals("DAY")?now.atZone(ZoneId.of(policy.get("timezone").toString())).toLocalDate().atStartOfDay(ZoneId.of(policy.get("timezone").toString())).toInstant():Instant.EPOCH;
                Long used=jdbc.queryForObject("SELECT COALESCE(sum(amount),0)::bigint FROM dt_quota_reservation WHERE dimension=? AND subject_id=? AND resource=? AND period_start=? AND state IN ('RESERVED','CONSUMED')",Long.class,demand.dimension(),demand.subjectId(),demand.resource(),Timestamp.from(period));
                long limit=((Number)policy.get("configured_limit")).longValue();
                if(demand.amount()>limit || used>limit-demand.amount())throw new AdminFailure(429,"QUOTA_EXCEEDED",AdminService.map("resource",demand.resource(),"dimension",demand.dimension(),"retryAt",kind.equals("DAY")?period.atZone(ZoneId.of(policy.get("timezone").toString())).plusDays(1).toInstant():null));
                jdbc.update("INSERT INTO dt_quota_reservation(reservation_key,dimension,subject_id,resource,period_start,amount,payload_digest,run_id,state) VALUES (?,?,?,?,?,?,?,?,'RESERVED')",key,demand.dimension(),demand.subjectId(),demand.resource(),Timestamp.from(period),demand.amount(),payloadDigest,run);
            }
            return true;
        });
    }
    /** 只有在途占用可以释放；消耗与持久存储不因取消被返还。 */
    public void release(UUID key) {
        transactions.executeWithoutResult(tx->jdbc.update("UPDATE dt_quota_reservation r SET state='RELEASED',updated_at=clock_timestamp() FROM dt_quota_policy p WHERE r.reservation_key=? AND p.dimension=r.dimension AND p.resource=r.resource AND p.period_kind='OCCUPANCY' AND r.state='RESERVED'",key));
    }
    public void consumed(UUID key) {
        transactions.executeWithoutResult(tx->jdbc.update("UPDATE dt_quota_reservation r SET state='CONSUMED',updated_at=clock_timestamp() FROM dt_quota_policy p WHERE r.reservation_key=? AND p.dimension=r.dimension AND p.resource=r.resource AND p.period_kind IN ('DAY','PERSISTENT') AND r.state='RESERVED'",key));
    }
    public void releaseRun(UUID run,boolean waiting) {
        transactions.executeWithoutResult(tx->jdbc.update("UPDATE dt_quota_reservation SET state='RELEASED',updated_at=clock_timestamp() WHERE run_id=? AND state='RESERVED' AND (resource='ACTIVE_RUN' OR (NOT ? AND resource='UNRESOLVED_RUN'))",run,waiting));
    }
    public void reconcileRuns() {
        transactions.executeWithoutResult(tx->jdbc.update("UPDATE dt_quota_reservation q SET state='RELEASED',updated_at=clock_timestamp() FROM dt_agent_run r WHERE r.id=q.run_id AND q.state='RESERVED' AND ((r.status='WAITING_FOR_USER' AND q.resource='ACTIVE_RUN') OR (r.status IN ('COMPLETED','FAILED','CANCELLED') AND q.resource IN ('ACTIVE_RUN','UNRESOLVED_RUN')) )"));
    }
}
