package io.devicetruth.admin.application;

import static io.devicetruth.admin.application.AdminService.*;
import io.devicetruth.admin.api.*;
import io.devicetruth.admin.api.AdminPages.*;
import io.devicetruth.admin.authorization.AdminAuthorization;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** 内容从不 SELECT；事件只投影标量，宿主码二次白名单。 */
public final class AdminReadModels {
    private final AdminService service;
    private final AdminPages pages;
    private final AdminAuthorization authorization;
    private static final Set<String> CODES=Set.of("AUTHORIZATION_REVOKED","QUEUE_REJECTED","TOTAL_TIMEOUT","PERSIST_FAILURE","CANCELLED","AGENT_RUN_FAILURE","MODEL_CONFIGURATION_MISSING","CHECKPOINT_PERSIST_FAILURE","MODEL_TRANSPORT_ERROR","MODEL_TIMEOUT","MODEL_PROTOCOL_ERROR","TOOL_EXECUTION_FAILED","SKILL_UNAVAILABLE","INVALID_TOOL_ARGUMENTS");
    private static final String RUN_SQL="""
            SELECT r.id AS run_id,c.workspace_id,CASE WHEN c.workspace_id IS NULL THEN 'LEGACY_UNCLAIMED' ELSE 'BOUND' END scope_state,
              r.initiated_by_user_id,r.execution_actor_user_id,r.status,r.requested_at,r.started_at,r.finished_at,
              CASE WHEN r.started_at IS NULL THEN NULL ELSE r.active_duration_ms END active_duration_ms,
              GREATEST(0,extract(epoch FROM (CASE WHEN r.status IN ('COMPLETED','FAILED','CANCELLED') THEN COALESCE(r.finished_at,r.updated_at) ELSE ?::timestamptz END-r.requested_at))*1000)::bigint wall_duration_ms,
              (SELECT safe_payload->>'code' FROM dt_run_event e WHERE e.run_id=r.id AND e.type='RUN_FAILED' ORDER BY seq DESC LIMIT 1) failure_code,
              (SELECT count(*) FROM dt_run_event e WHERE e.run_id=r.id AND e.type='RUN_MODEL') model_attempt_count,
              (SELECT count(*) FROM dt_run_event e WHERE e.run_id=r.id AND e.type='RUN_TOOL') tool_call_count
            FROM dt_agent_run r JOIN dt_conversation c ON c.id=r.conversation_id
            """;
    public AdminReadModels(AdminService service,AdminPages pages) {this.service=service;this.pages=pages;this.authorization=service.authorization;}
    private Query runQuery(Position pos,Map<String,String> query) {
        var sql=new Query(RUN_SQL+" WHERE r.requested_at>=? AND r.requested_at<? AND r.created_at<=?",Timestamp.from(pos.snapshotAt()),Timestamp.from(pos.from()),Timestamp.from(pos.to()),Timestamp.from(pos.snapshotAt()));
        for(String key:List.of("workspaceId","initiatedByUserId","executionActorUserId")) if(query.containsKey(key)) sql.add(" AND "+(key.equals("workspaceId")?"c.workspace_id":"r."+snake(key))+"=?",AdminPages.uuid(query.get(key)));
        if(query.containsKey("status")) {AdminPages.choice(query.get("status"),"QUEUED","RUNNING","WAITING_FOR_USER","COMPLETED","FAILED","CANCELLED");sql.add(" AND r.status=?",query.get("status"));}
        if(query.containsKey("failureCode")) {
            if(!CODES.contains(query.get("failureCode"))) throw AdminFailure.input();
            sql.add(" AND (SELECT safe_payload->>'code' FROM dt_run_event e WHERE e.run_id=r.id AND e.type='RUN_FAILED' ORDER BY seq DESC LIMIT 1)=?",query.get("failureCode"));
        }
        return sql;
    }
    public Page<Map<String,Object>> runs(UUID actor,Map<String,String> query) {
        AdminPages.validate(query,"from","to","workspaceId","initiatedByUserId","executionActorUserId","status","failureCode");
        var context=authorization.require(actor);Position pos=AdminPages.window(pages.position("runs",context,query),query);
        var sql=runQuery(pos,query);descending(sql,pos,"r.requested_at","r.id");sql.add(" ORDER BY r.requested_at DESC,r.id DESC LIMIT ?",AdminPages.limit(query)+1);
        var values=service.rows(sql);values.forEach(this::enrichRun);
        return service.page("runs",context,query,pos,values,"requestedAt","runId");
    }
    public Map<String,Object> run(UUID actor,UUID id) {
        authorization.require(actor);var row=service.one(new Query(RUN_SQL+" WHERE r.id=?",Timestamp.from(Instant.now()),id));enrichRun(row);
        long phases=((Number)service.one(new Query("SELECT count(*) n FROM dt_run_event WHERE run_id=? AND type='RUN_PHASE'",id)).get("n")).longValue();
        return map("run",row,"eventCounts",map("model",row.get("modelAttemptCount"),"tool",row.get("toolCallCount"),"phase",phases),"usageAttribution","INCOMPLETE");
    }
    private void enrichRun(Map<String,Object> row) {
        if(row.get("failureCode")!=null && !CODES.contains(row.get("failureCode"))) row.put("failureCode",null);
        var attempts=attemptRows((UUID)row.get("runId"),"MODEL",null,0,100001);
        row.put("models",attempts.stream().map(r->r.get("model")).filter(Objects::nonNull).distinct().sorted(Comparator.comparing(Object::toString)).toList());
        row.put("usage",usage(attempts));
    }
    private List<Map<String,Object>> attemptRows(UUID run,String kind,Instant snapshot,long after,int limit) {
        var sql=new Query("""
                SELECT seq,recorded_at,CASE WHEN type='RUN_MODEL' THEN 'MODEL' ELSE 'TOOL' END kind,
                  CASE WHEN type='RUN_MODEL' THEN safe_payload->>'model' END model,
                  CASE WHEN type='RUN_TOOL' THEN safe_payload->>'toolName' END tool_name,
                  safe_payload->>'attempt' attempt_number,safe_payload->>'outcome' outcome,
                  safe_payload->>'success' success,safe_payload->>'errorCode' error_code,
                  safe_payload->>'durationMs' duration_ms,safe_payload->>'retryable' retryable,
                  safe_payload->>'inputTokens' input_tokens,safe_payload->>'outputTokens' output_tokens,
                  safe_payload->>'cost' reported_cost
                FROM dt_run_event WHERE run_id=? AND type=? AND seq>?
                """,run,kind.equals("MODEL")?"RUN_MODEL":"RUN_TOOL",after);
        if(snapshot!=null) sql.add(" AND recorded_at<=?",Timestamp.from(snapshot));
        sql.add(" ORDER BY seq LIMIT ?",limit);
        var values=service.rows(sql);
        for(var row:values) {
            row.put("model",identifier(row.get("model")));row.put("toolName",identifier(row.get("toolName")));
            row.put("outcome",kind.equals("MODEL")?outcome(row.get("outcome")):"true".equals(row.remove("success"))?"SUCCESS":"FAILURE");row.remove("success");
            if(!CODES.contains(String.valueOf(row.get("errorCode")))) row.put("errorCode",null);
            for(String key:List.of("attemptNumber","durationMs","inputTokens","outputTokens")) row.put(key,number(row.get(key)));
            Object retryable=row.get("retryable");row.put("retryable",List.of("true","false").contains(String.valueOf(retryable))?Boolean.valueOf(retryable.toString()):null);
            // 当前事件合同没有可信币种。已报告 cost 不作为任何货币总额。
            row.put("unknownCurrencyCost",decimal(row.remove("reportedCost"))!=null);row.put("cost",null);
        }
        return values;
    }
    public Page<Map<String,Object>> attempts(UUID actor,UUID run,Map<String,String> query) {
        AdminPages.validate(query,"kind");AdminPages.choice(query.get("kind"),"MODEL","TOOL");if(!query.containsKey("kind"))throw AdminFailure.input();
        var context=authorization.require(actor);service.one(new Query("SELECT id FROM dt_agent_run WHERE id=?",run));
        String endpoint="attempts/"+run;Position pos=pages.position(endpoint,context,query);
        var values=attemptRows(run,query.get("kind"),pos.snapshotAt(),pos.lastId()==null?0:Long.parseLong(pos.lastId()),AdminPages.limit(query)+1);
        values.forEach(row->row.remove("unknownCurrencyCost"));
        return service.page(endpoint,context,query,pos,values,null,"seq");
    }
    public Map<String,Object> usageSummary(UUID actor,Map<String,String> query) {
        AdminPages.validate(query,"from","to","timezone","workspaceId","initiatedByUserId","groupBy");
        if(query.containsKey("cursor") || query.containsKey("limit"))throw AdminFailure.input();
        AdminPages.choice(query.get("groupBy"),"NONE","DAY","WORKSPACE");
        authorization.require(actor);Position pos=AdminPages.window(new Position(Instant.now(),null,null,null,null),query);
        var sql=runQuery(pos,query);sql.add(" ORDER BY r.requested_at,r.id LIMIT 10001");var runs=service.rows(sql);
        if(runs.size()>10000)throw new AdminFailure(400,"INVALID_TIME_RANGE");
        var attempts=new ArrayList<Map<String,Object>>();var statuses=new TreeMap<String,Long>();
        for(String status:List.of("QUEUED","RUNNING","WAITING_FOR_USER","COMPLETED","FAILED","CANCELLED")) statuses.put(status,0L);
        var grouped=new LinkedHashMap<String,List<Map<String,Object>>>();
        String groupBy=query.getOrDefault("groupBy","NONE");
        for(var run:runs) {
            statuses.merge(run.get("status").toString(),1L,Long::sum);
            var records=attemptRows((UUID)run.get("runId"),"MODEL",pos.snapshotAt(),0,100001);attempts.addAll(records);
            String groupKey=groupBy.equals("DAY")?((Instant)run.get("requestedAt")).atZone(ZoneId.of(query.getOrDefault("timezone","Asia/Shanghai"))).toLocalDate().toString():String.valueOf(run.get("workspaceId"));
            run.put("attempts",records);if(!groupBy.equals("NONE"))grouped.computeIfAbsent(groupKey,k->new ArrayList<>()).add(run);
        }
        var groups=new ArrayList<Map<String,Object>>();
        grouped.forEach((key,items)->{
            var counts=new TreeMap<String,Long>();statuses.keySet().forEach(s->counts.put(s,0L));var records=new ArrayList<Map<String,Object>>();
            for(var item:items){counts.merge(item.get("status").toString(),1L,Long::sum);@SuppressWarnings("unchecked") var r=(List<Map<String,Object>>)item.get("attempts");records.addAll(r);}
            groups.add(map("key",key,"runCount",items.size(),"statusCounts",counts,"usage",usage(records),"amountBuckets",List.of()));
        });
        return map("window",map("from",pos.from(),"to",pos.to(),"timezone",query.getOrDefault("timezone","Asia/Shanghai")),"generatedAt",pos.snapshotAt(),
                "runCount",runs.size(),"statusCounts",statuses,"modelAttemptCount",attempts.size(),"usage",usage(attempts),"amountBuckets",List.of(),
                "unknownCurrencyCostCount",attempts.stream().filter(r->Boolean.TRUE.equals(r.get("unknownCurrencyCost"))).count(),"attributionStatus",attempts.isEmpty()?"UNKNOWN":"INCOMPLETE","unattributedAttemptCount",attempts.size(),"groups",groups);
    }
    private static Map<String,Object> usage(List<Map<String,Object>> attempts) {
        long complete=attempts.stream().filter(r->r.get("inputTokens")!=null && r.get("outputTokens")!=null).count();
        Long in=sum(attempts,"inputTokens"),out=sum(attempts,"outputTokens");
        return map("status",in==null && out==null?"UNKNOWN":"PARTIAL","inputTokens",in,"outputTokens",out,"attemptCount",attempts.size(),
                "tokenReportedAttemptCount",complete,"costReportedAttemptCount",0,"tokenCoverage",attempts.isEmpty()?null:(double)complete/attempts.size(),"costCoverage",attempts.isEmpty()?null:0.0);
    }
    private static Long sum(List<Map<String,Object>> rows,String key) {var numbers=rows.stream().map(r->(Long)r.get(key)).filter(Objects::nonNull).toList();return numbers.isEmpty()?null:numbers.stream().reduce(0L,Math::addExact);}
    private static String identifier(Object value) {return value!=null && value.toString().matches("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,127}")?value.toString():null;}
    private static String outcome(Object value) {return value!=null && Set.of("VALID_PROPOSAL","INVALID_JSON_OR_SCHEMA","MODEL_TRANSPORT_OR_TIMEOUT","TOTAL_TIMEOUT","TRANSPORT_ERROR","PROTOCOL_ERROR","TIMEOUT","SUCCESS","FAILURE").contains(value.toString())?value.toString():"UNKNOWN";}
    private static Long number(Object value) {try {if(value==null)return null;long n=Long.parseLong(value.toString());return n<0?null:n;}catch(NumberFormatException e){return null;}}
    private static java.math.BigDecimal decimal(Object value) {try {if(value==null)return null;return new java.math.BigDecimal(value.toString());}catch(NumberFormatException e){return null;}}
}
