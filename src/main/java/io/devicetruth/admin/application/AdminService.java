package io.devicetruth.admin.application;

import io.devicetruth.admin.api.*;
import io.devicetruth.admin.api.AdminPages.*;
import io.devicetruth.admin.authorization.AdminAuthorization;
import io.devicetruth.admin.authorization.AdminAuthorization.Context;
import io.devicetruth.admin.persistence.AdminAudit;
import io.devicetruth.server.AccountService;
import io.devicetruth.skill.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 管理编排仅返回显式投影；不调用产品内容详情或模拟指标。 */
public final class AdminService {
    public static final List<String> CAPABILITIES=List.of("USER_READ","USER_CREATE","USER_STATUS","RUN_READ","USAGE_READ","QUOTA_READ","SKILL_CATALOG_READ","SYSTEM_READ","AUDIT_READ");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AccountService accounts;
    private final SkillRuntime skills;
    private final AdminPages pages;
    private final io.devicetruth.application.agent.GeneralAgentService agent;
    private final String reasonerMode;
    private final Set<String> registeredTools;
    public final AdminAuthorization authorization;
    private final AdminAudit audit;
    private java.util.function.BooleanSupplier knowledgeReadiness=()->false;
    public void knowledgeReadiness(java.util.function.BooleanSupplier readiness){knowledgeReadiness=readiness;}
    public AdminService(JdbcTemplate jdbc,TransactionTemplate transactions,AccountService accounts,SkillRuntime skills,AdminPages pages,io.devicetruth.application.agent.GeneralAgentService agent,String reasonerMode,Set<String> registeredTools) {
        this.jdbc=jdbc;this.transactions=transactions;this.accounts=accounts;this.skills=skills;this.pages=pages;this.agent=agent;this.reasonerMode=reasonerMode;this.registeredTools=Set.copyOf(registeredTools);
        authorization=new AdminAuthorization(jdbc);audit=new AdminAudit(jdbc);
    }
    public Map<String,Object> access(UUID actor) {
        Context context=authorization.require(actor);
        var features=new ArrayList<Map<String,Object>>();
        for(String feature:List.of("USER_CREATE","USER_STATUS","KNOWLEDGE_WRITE","SKILL_WRITE","QUOTA_ENFORCEMENT")) {
            boolean ready=List.of("USER_CREATE","USER_STATUS","SKILL_WRITE").contains(feature) || feature.equals("KNOWLEDGE_WRITE") && knowledgeReadiness.getAsBoolean();
            features.add(map("feature",feature,"state",ready?"READY":feature.equals("QUOTA_ENFORCEMENT") && jdbc.queryForObject("SELECT count(*) FROM dt_quota_policy",Long.class)==0?"UNCONFIGURED":"PENDING_INTEGRATION","reasonCode",ready?null:feature.equals("QUOTA_ENFORCEMENT")?"RESOURCE_ADAPTERS_PENDING":"MATERIAL_PUBLICATION_ADAPTER_PENDING"));
        }
        return map("userId",actor,"platformRole","PLATFORM_ADMIN","grantRevision",context.revision(),"capabilities",CAPABILITIES,
                "workspaceCapabilities",List.of(),"workspaceCapabilitiesComplete",false,"features",features);
    }
    private static final String USER_SQL="""
            SELECT a.id AS user_id,a.email,a.display_name,CASE WHEN p.user_id IS NULL THEN 'NONE' ELSE 'PLATFORM_ADMIN' END platform_role,
              a.status,a.status_version,a.created_at,a.status_changed_at
            FROM dt_account a LEFT JOIN dt_platform_admin p ON p.user_id=a.id
            """;
    public Page<Map<String,Object>> users(UUID actor,Map<String,String> query) {
        AdminPages.validate(query,"q","status","platformRole");
        AdminPages.choice(query.get("status"),"ACTIVE","DISABLED");AdminPages.choice(query.get("platformRole"),"NONE","PLATFORM_ADMIN");
        Context context=authorization.require(actor);Position pos=pages.position("users",context,query);
        var sql=new Query(USER_SQL+" WHERE a.created_at<=?",Timestamp.from(pos.snapshotAt()));
        String q=search(query);
        if(q!=null) sql.add(" AND (position(? in lower(a.email))>0 OR position(? in lower(a.display_name))>0)",q,q);
        if(query.containsKey("status")) sql.add(" AND a.status=?",query.get("status"));
        if(query.containsKey("platformRole")) sql.add(query.get("platformRole").equals("NONE")?" AND p.user_id IS NULL":" AND p.user_id IS NOT NULL");
        descending(sql,pos,"a.created_at","a.id");sql.add(" ORDER BY a.created_at DESC,a.id DESC LIMIT ?",AdminPages.limit(query)+1);
        return page("users",context,query,pos,rows(sql),"createdAt","userId");
    }
    public Map<String,Object> createUser(UUID actor,UUID request,String email,String name,String password) {
        return transactions.execute(tx -> {
            authorization.lock(actor);
            var created=accounts.register(email,password,name);
            UUID workspace=jdbc.queryForObject("SELECT id FROM dt_workspace WHERE personal_user_id=?",UUID.class,created.userId());
            UUID auditId=audit.append(actor,null,null,"ACCOUNT",created.userId().toString(),"USER_CREATE","SUCCESS","OPS_REQUEST",null,request,null,Map.of("status","ACTIVE","revision",0));
            var user=one(new Query(USER_SQL+" WHERE a.id=?",created.userId()));
            return map("userId",created.userId(),"email",created.email(),"displayName",created.displayName(),"personalWorkspaceId",workspace,
                    "status","ACTIVE","platformRole","NONE","statusVersion",0,"createdAt",user.get("createdAt"),"auditId",auditId);
        });
    }
    /** 状态核心已实现，HTTP 在持续授权接入验收前保持关闭。 */
    public Map<String,Object> userStatus(UUID actor,UUID request,UUID target,String status,long version,String reason) {
        AdminPages.choice(status,"ACTIVE","DISABLED"); if(status==null || version<0) throw AdminFailure.input();reason(reason);
        return transactions.execute(tx -> {
            authorization.maintenanceLock();authorization.require(actor);
            var states=jdbc.queryForList("SELECT status,status_version FROM dt_account WHERE id=? FOR UPDATE",target);
            if(states.isEmpty()) throw AdminFailure.missing();
            var state=states.getFirst();long current=((Number)state.get("status_version")).longValue();
            if(current!=version) throw new AdminFailure(409,"STATE_VERSION_CONFLICT",Map.of("currentVersion",current));
            boolean changed=!status.equals(state.get("status"));
            if(changed) {
                if(status.equals("DISABLED")) authorization.protectLast(target);
                jdbc.update("UPDATE dt_account SET status=?,status_version=status_version+1,status_changed_at=clock_timestamp(),status_reason_code=? WHERE id=?",status,reason,target);
                if(status.equals("DISABLED")) {
                    jdbc.update("DELETE FROM spring_session WHERE principal_name=?",target.toString());
                    jdbc.update("UPDATE dt_password_reset SET consumed_at=COALESCE(consumed_at,clock_timestamp()) WHERE user_id=?",target);
                }
                authorization.revised();
            }
            UUID auditId=audit.append(actor,null,null,"ACCOUNT",target.toString(),"USER_STATUS",changed?"SUCCESS":"NO_CHANGE",reason,null,request,
                    map("status",state.get("status"),"revision",current),map("status",status,"revision",changed?current+1:current));
            return map("user",one(new Query(USER_SQL+" WHERE a.id=?",target)),"changed",changed,"auditId",auditId);
        });
    }
    public Page<Map<String,Object>> workspaces(UUID actor,Map<String,String> query) {
        AdminPages.validate(query,"capability");AdminPages.choice(query.get("capability"),"KNOWLEDGE_READ","KNOWLEDGE_MANAGE","SKILL_READ","SKILL_MANAGE");
        Context context=authorization.require(actor);Position pos=pages.position("workspaces",context,query);
        var sql=new Query("""
                SELECT w.id AS workspace_id,w.kind,w.name,array_agg(DISTINCT g.capability ORDER BY g.capability) capabilities
                FROM dt_workspace w JOIN dt_admin_workspace_grant g ON w.id=g.workspace_id
                WHERE g.user_id=? AND w.created_at<=?
                """,actor,Timestamp.from(pos.snapshotAt()));
        if(query.containsKey("capability")) sql.add(" AND EXISTS(SELECT 1 FROM dt_admin_workspace_grant f WHERE f.workspace_id=w.id AND f.user_id=? AND f.capability IN (?,?))",actor,query.get("capability"),query.get("capability").replace("_READ","_MANAGE"));
        if(pos.lastId()!=null) sql.add(" AND w.id>?::uuid",pos.lastId());
        sql.add(" GROUP BY w.id ORDER BY w.id LIMIT ?",AdminPages.limit(query)+1);
        var values=rows(sql);
        for(var row:values) row.put("capabilities",expanded((List<?>)row.get("capabilities")));
        return page("workspaces",context,query,pos,values,null,"workspaceId");
    }
    private static List<String> expanded(List<?> raw) {
        var values=new TreeSet<String>();for(var value:raw) {String s=value.toString();values.add(s);values.add(s.replace("_MANAGE","_READ"));}return List.copyOf(values);
    }
    public Page<Map<String,Object>> catalog(UUID actor,Map<String,String> query) {
        AdminPages.validate(query);Context context=authorization.require(actor);Position pos=pages.position("skills",context,query);
        var values=skills.registry().skills().stream().filter(s->pos.lastId()==null || s.name().compareTo(pos.lastId())>0)
                .limit(AdminPages.limit(query)+1L).map(AdminService::catalogItem).toList();
        return page("skills",context,query,pos,values,null,"skillId");
    }
    public Map<String,Object> skill(UUID actor,String id,String reference) {
        authorization.require(actor);
        try {
            var metadata=skills.registry().require(id);var loader=new SkillContextLoader(skills.registry());
            if(reference!=null) return map("skillId",id,"registryVersion",metadata.version(),"name",reference,"bodyMarkdown",loader.readReference(id,reference));
            var references=metadata.references().stream().map(name->map("name",name,"sizeBytes",loader.readReference(id,name).getBytes(java.nio.charset.StandardCharsets.UTF_8).length)).toList();
            return map("skill",catalogItem(metadata),"bodyMarkdown",loader.instructions(id).content(),"references",references,"referencesComplete",true);
        } catch(SkillException e) { throw AdminFailure.missing(); }
    }
    private static Map<String,Object> catalogItem(SkillMetadata s) {
        return map("skillId",s.name(),"name",s.name(),"description",s.description(),"registryVersion",s.version(),"devices",s.devices(),"tasks",s.tasks(),"declaredTools",s.tools(),"loadedAt",null);
    }
    public Page<Map<String,Object>> installations(UUID actor,UUID workspace,String header,Map<String,String> query) {
        AdminPages.validate(query);var target=authorization.workspace(actor,workspace,"SKILL_READ",header);
        Context context=authorization.require(actor);String endpoint="installations/"+workspace;Position pos=pages.position(endpoint,context,query);
        var sql=new Query("SELECT skill_id,version,enabled,revision,allowed_tools,updated_at FROM dt_skill_installation WHERE owner_id=? AND project_id=? AND updated_at<=?",target.owner(),target.project(),Timestamp.from(pos.snapshotAt()));
        if(pos.lastId()!=null) {String[] last=pos.lastId().split("@",2);sql.add(" AND (skill_id,version)>(?,?)",last[0],last[1]);}
        sql.add(" ORDER BY skill_id,version LIMIT ?",AdminPages.limit(query)+1);
        var values=rows(sql); values.forEach(row->installation(row,workspace));
        return pages.page(endpoint,context,query,pos,values,r->null,r->r.get("skillId")+"@"+r.get("version"));
    }
    private void installation(Map<String,Object> row,UUID workspace) {
        var metadata=skills.registry().find((String)row.get("skillId")).orElse(null);
        boolean match=metadata!=null && metadata.version().equals(row.get("version"));
        var effective=new TreeSet<>(skills.serverTools());effective.retainAll(registeredTools);
        effective.retainAll((List<?>)row.remove("allowedTools"));if(metadata!=null) effective.retainAll(metadata.tools());
        row.put("workspaceId",workspace);row.put("registryVersion",metadata==null?null:metadata.version());row.put("effectiveTools",match?List.copyOf(effective):List.of());
        row.put("availability",metadata==null?"NOT_LOADED":!match?"VERSION_MISMATCH":!Boolean.TRUE.equals(row.get("enabled"))?"DISABLED":"AVAILABLE");
    }
    public Map<String,Object> install(UUID actor,UUID request,UUID workspace,String header,String skillId,String version,String reason) {
        reason(reason);
        return transactions.execute(tx -> {
            authorization.lock(actor);var target=authorization.workspace(actor,workspace,"SKILL_MANAGE",header);
            var metadata=skills.registry().find(skillId).orElseThrow(AdminFailure::missing);
            if(!metadata.version().equals(version))throw new AdminFailure(409,"SKILL_VERSION_UNAVAILABLE");
            var tools=new TreeSet<>(skills.serverTools());tools.retainAll(registeredTools);tools.retainAll(metadata.tools());
            try { jdbc.update("INSERT INTO dt_skill_installation(owner_id,project_id,skill_id,version,allowed_tools) VALUES (?,?,?,?,?)",target.owner(),target.project(),skillId,version,tools.toArray(String[]::new)); }
            catch(org.springframework.dao.DuplicateKeyException e) {throw new AdminFailure(409,"INSTALLATION_ALREADY_EXISTS");}
            UUID auditId=audit.append(actor,null,workspace,"SKILL_INSTALLATION",skillId+"@"+version,"SKILL_INSTALL","SUCCESS",reason,null,request,null,map("enabled",true,"revision",0));
            var row=one(new Query("SELECT skill_id,version,enabled,revision,allowed_tools,updated_at FROM dt_skill_installation WHERE owner_id=? AND project_id=? AND skill_id=? AND version=?",target.owner(),target.project(),skillId,version));installation(row,workspace);
            return map("installation",row,"auditId",auditId);
        });
    }
    public Map<String,Object> skillStatus(UUID actor,UUID request,UUID workspace,String header,String skillId,String version,boolean enabled,long revision,String reason) {
        reason(reason);if(revision<0)throw AdminFailure.input();
        return transactions.execute(tx -> {
            authorization.lock(actor);var target=authorization.workspace(actor,workspace,"SKILL_MANAGE",header);
            var row=one(new Query("SELECT skill_id,version,enabled,revision,allowed_tools,updated_at FROM dt_skill_installation WHERE owner_id=? AND project_id=? AND skill_id=? AND version=? FOR UPDATE",target.owner(),target.project(),skillId,version));
            long current=((Number)row.get("revision")).longValue();
            if(current!=revision)throw new AdminFailure(409,"STATE_VERSION_CONFLICT",Map.of("currentVersion",current));
            if(enabled && skills.registry().find(skillId).filter(m->m.version().equals(version)).isEmpty())throw new AdminFailure(409,"SKILL_VERSION_UNAVAILABLE");
            boolean changed=enabled!=(Boolean)row.get("enabled");
            if(changed && !enabled) {
                // 不取 Run 行锁：选择持 Run 锁后取 installation SHARE；这里 UPDATE 后只读已提交选择。
                var blockers=rows(new Query("SELECT r.id run_id FROM dt_run_skill s JOIN dt_agent_run r ON r.id=s.run_id WHERE s.owner_id=? AND s.project_id=? AND s.skill_id=? AND s.version=? AND r.status IN ('QUEUED','RUNNING','WAITING_FOR_USER') ORDER BY r.requested_at,r.id LIMIT 21",target.owner(),target.project(),skillId,version));
                if(!blockers.isEmpty())throw new AdminFailure(409,"SKILL_HAS_UNRESOLVED_RUNS",map("runIds",blockers.subList(0,Math.min(20,blockers.size())).stream().map(r->r.get("runId")).toList(),"hasMore",blockers.size()>20));
            }
            if(changed)jdbc.update("UPDATE dt_skill_installation SET enabled=?,revision=revision+1,updated_at=clock_timestamp() WHERE owner_id=? AND project_id=? AND skill_id=? AND version=?",enabled,target.owner(),target.project(),skillId,version);
            UUID auditId=audit.append(actor,null,workspace,"SKILL_INSTALLATION",skillId+"@"+version,"SKILL_STATUS",changed?"SUCCESS":"NO_CHANGE",reason,null,request,map("enabled",row.get("enabled"),"revision",current),map("enabled",enabled,"revision",changed?current+1:current));
            row=one(new Query("SELECT skill_id,version,enabled,revision,allowed_tools,updated_at FROM dt_skill_installation WHERE owner_id=? AND project_id=? AND skill_id=? AND version=?",target.owner(),target.project(),skillId,version));installation(row,workspace);
            return map("installation",row,"changed",changed,"auditId",auditId);
        });
    }
    public Map<String,Object> health(UUID actor) {
        authorization.require(actor);Instant now=Instant.now();var components=new ArrayList<Map<String,Object>>();
        components.add(component("APPLICATION","UP",now,null));
        long start=System.nanoTime();
        try {jdbc.queryForObject("SELECT 1",Integer.class);components.add(component("DATABASE","UP",now,(System.nanoTime()-start)/1_000_000));}
        catch(org.springframework.dao.DataAccessException e) {components.add(component("DATABASE","DOWN",now,null));}
        var operational=agent.operationalStatus();
        var worker=component("WORKER",operational.workerEnabled()?"UP":"DEGRADED",now,null);
        worker.put("reasonCode",operational.workerEnabled()?"LOCAL_PROCESS_ONLY":"WORKER_DISABLED");
        worker.put("metrics",map("latencyMs",null,"activeWorkers",operational.activeWorkers(),"queuedRuns",null,"queueCapacity",null,"recentSuccessCount",null,"recentFailureCount",null));components.add(worker);
        var queue=component("QUEUE","UP",now,null);
        queue.put("reasonCode","LOCAL_EXECUTOR_ONLY");queue.put("metrics",map("latencyMs",null,"activeWorkers",null,"queuedRuns",operational.queuedTasks(),"queueCapacity",operational.queueCapacity(),"recentSuccessCount",null,"recentFailureCount",null));components.add(queue);
        boolean configured=false;
        try {var model=io.devicetruth.adapter.ModelConfiguration.local();configured=switch(reasonerMode){case "openai"->List.of("OPENAI_API_KEY","OPENAI_BASE_URL","OPENAI_MODEL").stream().allMatch(k->{String v=model.apply(k);return v!=null&&!v.isBlank();});case "qwen"->{String v=model.apply("DASHSCOPE_API_KEY");yield v!=null&&!v.isBlank();}default->false;};}catch(RuntimeException ignored){}
        var model=component("MODEL_CONFIG",configured?"UP":reasonerMode.equals("scripted")?"DEGRADED":"UNKNOWN",now,null);model.put("reasonCode",reasonerMode.equals("scripted")?"OFFLINE_REASONER_CONFIGURED":configured?"CONFIGURATION_ONLY_NO_NETWORK_PROBE":"MODEL_CONFIGURATION_NOT_VERIFIED");components.add(model);
        var recent=one(new Query("SELECT count(*) FILTER(WHERE safe_payload->>'outcome'='VALID_PROPOSAL') success,count(*) FILTER(WHERE safe_payload->>'outcome' IN ('TRANSPORT_ERROR','PROTOCOL_ERROR','TIMEOUT','MODEL_TRANSPORT_OR_TIMEOUT','INVALID_JSON_OR_SCHEMA','TOTAL_TIMEOUT')) failure FROM dt_run_event WHERE type='RUN_MODEL' AND recorded_at>=? AND recorded_at<?",Timestamp.from(now.minus(java.time.Duration.ofHours(24))),Timestamp.from(now)));
        long successes=((Number)recent.get("success")).longValue(),failures=((Number)recent.get("failure")).longValue();
        var recentComponent=component("RECENT_MODEL_CALLS",successes+failures==0?"UNKNOWN":failures>0?"DEGRADED":"UP",now,null);
        recentComponent.put("reasonCode","PERSISTED_LAST_24H_NOT_CURRENT_CONNECTIVITY");recentComponent.put("evidenceWindow",map("from",now.minus(java.time.Duration.ofHours(24)),"to",now));
        recentComponent.put("metrics",map("latencyMs",null,"activeWorkers",null,"queuedRuns",null,"queueCapacity",null,"recentSuccessCount",successes,"recentFailureCount",failures));components.add(recentComponent);
        return map("generatedAt",now,"components",components);
    }
    private static Map<String,Object> component(String name,String status,Instant now,Long latency) {
        return map("name",name,"status",status,"checkedAt",now,"evidenceWindow",null,"reasonCode",status.equals("UNKNOWN")?"NO_PROBE_EVIDENCE":null,
                "metrics",map("latencyMs",latency,"activeWorkers",null,"queuedRuns",null,"queueCapacity",null,"recentSuccessCount",null,"recentFailureCount",null));
    }
    public Page<Map<String,Object>> audits(UUID actor,Map<String,String> query) {
        AdminPages.validate(query,"from","to","actorUserId","action","targetType","targetId","result","requestId");
        AdminPages.choice(query.get("result"),"SUCCESS","NO_CHANGE","FAILURE","DENIED");
        Context context=authorization.require(actor);Position pos=AdminPages.window(pages.position("audit",context,query),query);
        var sql=new Query("SELECT id AS audit_id,occurred_at,actor_kind,actor_user_id,operator_ref,workspace_id,target_type,target_id,action,result,reason_code,error_code,request_id,before_state,after_state FROM dt_admin_audit WHERE occurred_at>=? AND occurred_at<? AND occurred_at<=?",Timestamp.from(pos.from()),Timestamp.from(pos.to()),Timestamp.from(pos.snapshotAt()));
        for(String key:List.of("actorUserId","action","targetType","targetId","result","requestId")) if(query.containsKey(key)) {
            if(key.equals("actorUserId") || key.equals("requestId")) sql.add(" AND "+snake(key)+"=?",AdminPages.uuid(query.get(key)));
            else {if(query.get(key).length()>128) throw AdminFailure.input();sql.add(" AND "+snake(key)+"=?",query.get(key));}
        }
        descending(sql,pos,"occurred_at","id");sql.add(" ORDER BY occurred_at DESC,id DESC LIMIT ?",AdminPages.limit(query)+1);
        var values=rows(sql);
        for(var row:values) {row.put("before",row.remove("beforeState"));row.put("after",row.remove("afterState"));}
        return page("audit",context,query,pos,values,"occurredAt","auditId");
    }
    public void recordFailure(UUID actor,UUID request,String code,int status) {
        try { audit.append(actor,null,null,"ADMIN_REQUEST",null,"ADMIN_REQUEST",status==403 || status==404?"DENIED":"FAILURE",null,code,request,null,null); }
        catch(RuntimeException ignored) { /* 独立失败记录不改变原拒绝，也不记录秘密。 */ }
    }
    public void unavailable(UUID actor,UUID workspace,String capability,String header) {
        authorization.require(actor);if(workspace!=null) authorization.workspace(actor,workspace,capability,header);
        throw new AdminFailure(503,"INTEGRATION_NOT_READY");
    }
    public static void reason(String reason) {
        if(reason==null) throw AdminFailure.input();AdminPages.choice(reason,"OPS_REQUEST","SECURITY_RESPONSE","ACCOUNT_RECOVERY","DATA_OUTDATED","PERMISSION_CHANGE","OTHER");
    }
    static String search(Map<String,String> query) {
        String value=query.get("q");if(value!=null && value.length()>100) throw AdminFailure.input();return value==null?null:value.strip().toLowerCase(Locale.ROOT);
    }
    static Map<String,Object> map(Object... pairs) {var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);return result;}
    static String snake(String value) {return value.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
    static final class Query {
        final StringBuilder sql;final List<Object> args=new ArrayList<>();
        Query(String sql,Object... args) {this.sql=new StringBuilder(sql);this.args.addAll(Arrays.asList(args));}
        void add(String text,Object... args) {sql.append(text);this.args.addAll(Arrays.asList(args));}
    }
    static void descending(Query sql,Position pos,String time,String id) {
        if(pos.lastId()!=null) sql.add(" AND ("+time+","+id+")<(?::timestamptz,?::uuid)",pos.lastTime(),pos.lastId());
    }
    List<Map<String,Object>> rows(Query query) {
        return jdbc.query(query.sql.toString(),(rs,n)->{
            var row=new LinkedHashMap<String,Object>();var metadata=rs.getMetaData();
            for(int i=1;i<=metadata.getColumnCount();i++) {
                String key=metadata.getColumnLabel(i);StringBuilder camel=new StringBuilder();boolean upper=false;
                for(char c:key.toCharArray()) {if(c=='_')upper=true;else {camel.append(upper?Character.toUpperCase(c):c);upper=false;}}
                Object value=rs.getObject(i);
                if(value instanceof Timestamp t) value=t.toInstant();
                if(value instanceof java.sql.Array a) {try {value=List.of((Object[])a.getArray());}finally{a.free();}}
                if(value!=null && metadata.getColumnTypeName(i).equals("jsonb")) {
                    try {value=io.devicetruth.adapter.Json.MAPPER.readValue(rs.getString(i),Object.class);}catch(Exception e){throw new SQLException("Invalid stored audit state");}
                }
                row.put(camel.toString(),value);
            }return row;
        },query.args.toArray());
    }
    Map<String,Object> one(Query query) {var rows=rows(query);if(rows.isEmpty())throw AdminFailure.missing();return rows.getFirst();}
    Page<Map<String,Object>> page(String endpoint,Context context,Map<String,String> query,Position pos,List<Map<String,Object>> values,String time,String id) {
        return pages.page(endpoint,context,query,pos,values,r->time==null?null:r.get(time).toString(),r->r.get(id).toString());
    }
}
