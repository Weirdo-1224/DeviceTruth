package io.devicetruth.admin.application;

import static io.devicetruth.admin.application.AdminService.*;
import io.devicetruth.admin.api.*;
import io.devicetruth.admin.api.AdminPages.*;
import java.time.*;
import java.util.*;

/** 部署策略与已对账占用分开；执行适配器未接齐时始终 PENDING_INTEGRATION。 */
public final class AdminQuotaViews {
    public static final List<String> RESOURCES=List.of("ACTIVE_RUN","EMBEDDING_TOKENS","IMPORT_JOB","RETRIEVAL","RUN_CREATE","SSE_CONNECTION","STORAGE_BYTES","UNRESOLVED_RUN","UPLOAD_BYTES");
    private final AdminService service;
    private final AdminPages pages;
    public AdminQuotaViews(AdminService service,AdminPages pages){this.service=service;this.pages=pages;}
    public Page<Map<String,Object>> list(UUID actor,Map<String,String> query) {
        AdminPages.validate(query,"dimension","subjectId","resource");var context=service.authorization.require(actor);
        String dimension=AdminPages.choice(query.get("dimension"),"ACCOUNT","WORKSPACE","PLATFORM");if(dimension==null)throw AdminFailure.input();
        String subject=query.get("subjectId");if(!dimension.equals("PLATFORM"))AdminPages.uuid(subject);else if(subject!=null)throw AdminFailure.input();
        if(query.containsKey("resource") && !RESOURCES.contains(query.get("resource")))throw AdminFailure.input();
        Position pos=pages.position("quotas",context,query);var items=new ArrayList<Map<String,Object>>();
        for(String resource:RESOURCES) {
            if(query.containsKey("resource") && !resource.equals(query.get("resource")) || pos.lastId()!=null && resource.compareTo(pos.lastId())<=0)continue;
            var policies=service.rows(new Query("SELECT configured_limit,period_kind,timezone,policy_version FROM dt_quota_policy WHERE dimension=? AND resource=?",dimension,resource));
            var row=map("dimension",dimension,"subjectId",subject,"resource",resource,"configuredLimit",null,"consumed",null,"reserved",null,"remaining",null,"periodStart",null,"periodEnd",null,"timezone",null,"unit",resource.endsWith("BYTES")?"bytes":resource.endsWith("TOKENS")?"tokens":"count","policyVersion",null,"enforcementState","UNCONFIGURED","reconciliationState","UNKNOWN","measuredAt",pos.snapshotAt());
            if(!policies.isEmpty()) {
                var policy=policies.getFirst();row.put("configuredLimit",policy.get("configuredLimit"));row.put("policyVersion",policy.get("policyVersion"));row.put("timezone",policy.get("timezone"));row.put("enforcementState","PENDING_INTEGRATION");
                if(policy.get("periodKind").equals("DAY")) {
                    var start=pos.snapshotAt().atZone(ZoneId.of(policy.get("timezone").toString())).toLocalDate().atStartOfDay(ZoneId.of(policy.get("timezone").toString()));row.put("periodStart",start.toInstant());row.put("periodEnd",start.plusDays(1).toInstant());
                }
                var measurements=service.rows(new Query("SELECT state,measured_at FROM dt_quota_reconciliation WHERE dimension=? AND subject_id=? AND resource=?",dimension,subject==null?"PLATFORM":subject,resource));
                if(!measurements.isEmpty()) {row.put("reconciliationState",measurements.getFirst().get("state"));row.put("measuredAt",measurements.getFirst().get("measuredAt"));}
                // 新增帐本尚未覆盖提交/文件/检索等所有路径，不从空表推断已用为零。
            }
            items.add(row);if(items.size()>AdminPages.limit(query))break;
        }
        return service.page("quotas",context,query,pos,items,null,"resource");
    }
}
