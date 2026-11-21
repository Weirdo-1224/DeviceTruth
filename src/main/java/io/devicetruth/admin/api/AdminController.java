package io.devicetruth.admin.api;

import io.devicetruth.admin.application.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/admin")
public final class AdminController {
    private final AdminService service;
    private final AdminPages pages;
    private final AdminKnowledgeOperations operations;
    private final AdminReadModels reads;
    private final AdminKnowledgeViews knowledge;
    public AdminController(AdminService service,AdminPages pages,AdminKnowledgeOperations operations) {this.service=service;this.pages=pages;this.operations=operations;reads=new AdminReadModels(service,pages);knowledge=new AdminKnowledgeViews(service,pages);}
    private UUID actor(HttpServletRequest req) {return UUID.fromString(req.getUserPrincipal().getName());}
    private UUID requestId(HttpServletRequest req) {return (UUID)req.getAttribute("adminRequestId");}
    private void global(HttpServletRequest req) {if(req.getHeader("X-Workspace-ID")!=null)throw AdminFailure.input();}
    private UUID workspace(Map<String,String> query) {return AdminPages.uuid(query.get("workspaceId"));}
    private String header(HttpServletRequest req) {return req.getHeader("X-Workspace-ID");}
    @GetMapping("/access") Object access(HttpServletRequest req,@RequestParam Map<String,String> query) {global(req);AdminPages.validate(query);if(!query.isEmpty())throw AdminFailure.input();return service.access(actor(req));}
    @GetMapping("/workspaces") Object workspaces(HttpServletRequest req,@RequestParam Map<String,String> query) {global(req);return service.workspaces(actor(req),query);}
    @GetMapping("/users") Object users(HttpServletRequest req,@RequestParam Map<String,String> query) {global(req);return service.users(actor(req),query);}
    @PostMapping("/users") ResponseEntity<?> create(HttpServletRequest req,@RequestParam Map<String,String> query,@RequestBody Map<String,Object> body) {
        global(req);none(query);keys(body,"email","displayName","initialPassword");
        return ResponseEntity.status(201).body(service.createUser(actor(req),requestId(req),string(body,"email"),string(body,"displayName"),string(body,"initialPassword")));
    }
    @PatchMapping("/users/{id}/status") Object status(HttpServletRequest req,@PathVariable String id,@RequestParam Map<String,String> query,@RequestBody Map<String,Object> body) {
        global(req);none(query);keys(body,"status","expectedVersion","reasonCode");AdminPages.uuid(id);version(body);AdminService.reason(string(body,"reasonCode"));AdminPages.choice(string(body,"status"),"ACTIVE","DISABLED");
        return service.userStatus(actor(req),requestId(req),AdminPages.uuid(id),string(body,"status"),version(body),string(body,"reasonCode"));
    }
    @GetMapping("/runs") Object runs(HttpServletRequest req,@RequestParam Map<String,String> query) {global(req);return reads.runs(actor(req),query);}
    @GetMapping("/runs/{id}") Object run(HttpServletRequest req,@PathVariable String id,@RequestParam Map<String,String> query) {global(req);none(query);return reads.run(actor(req),AdminPages.uuid(id));}
    @GetMapping("/runs/{id}/attempts") Object attempts(HttpServletRequest req,@PathVariable String id,@RequestParam Map<String,String> query) {global(req);return reads.attempts(actor(req),AdminPages.uuid(id),query);}
    @GetMapping("/usage/summary") Object usage(HttpServletRequest req,@RequestParam Map<String,String> query) {global(req);return reads.usageSummary(actor(req),query);}
    @GetMapping("/quotas") Object quotas(HttpServletRequest req,@RequestParam Map<String,String> query) {
        global(req);return new AdminQuotaViews(service,pages).list(actor(req),query);
    }

    @GetMapping("/skills") Object skills(HttpServletRequest req,@RequestParam Map<String,String> query) {global(req);return service.catalog(actor(req),query);}
    @GetMapping("/skills/{id}") Object skill(HttpServletRequest req,@PathVariable String id,@RequestParam Map<String,String> query) {global(req);none(query);return service.skill(actor(req),id,null);}
    @GetMapping("/skills/{id}/references/{name}") Object reference(HttpServletRequest req,@PathVariable String id,@PathVariable String name,@RequestParam Map<String,String> query) {global(req);none(query);return service.skill(actor(req),id,name);}
    @GetMapping("/workspaces/{id}/skill-installations") Object installations(HttpServletRequest req,@PathVariable String id,@RequestParam Map<String,String> query) {return service.installations(actor(req),AdminPages.uuid(id),header(req),query);}
    @PostMapping("/workspaces/{id}/skill-installations") Object install(HttpServletRequest req,@PathVariable String id,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
        none(query);keys(body,"skillId","version","reasonCode");return ResponseEntity.status(201).body(service.install(actor(req),requestId(req),AdminPages.uuid(id),header(req),string(body,"skillId"),string(body,"version"),string(body,"reasonCode")));
    }
    @PatchMapping("/workspaces/{id}/skill-installations/{skill}/versions/{version}/status") Object installationStatus(HttpServletRequest req,@PathVariable String id,@PathVariable String skill,@PathVariable String version,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
        none(query);keys(body,"enabled","expectedVersion","reasonCode");if(!(body.get("enabled") instanceof Boolean enabled))throw AdminFailure.input();return service.skillStatus(actor(req),requestId(req),AdminPages.uuid(id),header(req),skill,version,enabled,version(body),string(body,"reasonCode"));
    }
    @GetMapping("/knowledge/documents") Object documents(HttpServletRequest req,@RequestParam Map<String,String> query) {return knowledge.documents(actor(req),workspace(query),header(req),query);}
    @GetMapping("/knowledge/documents/{id}") Object document(HttpServletRequest req,@PathVariable String id,@RequestParam Map<String,String> query) {AdminPages.validate(query,"workspaceId");if(query.size()!=1)throw AdminFailure.input();return knowledge.document(actor(req),workspace(query),header(req),AdminPages.uuid(id));}
    @GetMapping("/knowledge/documents/{id}/sections") Object sections(HttpServletRequest req,@PathVariable String id,@RequestParam Map<String,String> query) {return knowledge.sections(actor(req),workspace(query),header(req),AdminPages.uuid(id),query);}
    @GetMapping("/knowledge/files") Object files(HttpServletRequest req,@RequestParam Map<String,String> query) {return knowledge.emptyManaged(actor(req),workspace(query),header(req),query,"files");}
    @GetMapping("/knowledge/sources") Object sources(HttpServletRequest req,@RequestParam Map<String,String> query) {return operations.sourcesPage(actor(req),workspace(query),header(req),query);}
    @GetMapping("/knowledge/files/{id}") Object file(HttpServletRequest req,@PathVariable String id,@RequestParam Map<String,String> query) {AdminPages.validate(query,"workspaceId");return knowledge.file(actor(req),workspace(query),header(req),AdminPages.uuid(id));}
    @PostMapping("/knowledge/imports") Object upload(HttpServletRequest req,@RequestParam Map<String,String> query,@RequestPart("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        Set<String> allowed=Set.of("workspaceId","file","title","domain","models","version","sourceId","licenseCode");
        var seen=new HashSet<String>();for(var part:req.getParts())if(!allowed.contains(part.getName()) || !seen.add(part.getName()))throw AdminFailure.input();
        if(!allowed.containsAll(query.keySet()))throw AdminFailure.input();
        return ResponseEntity.accepted().body(operations.upload(actor(req),requestId(req),workspace(query),header(req),file,query));}
    @PostMapping("/knowledge/files/{id}/retry") Object retry(HttpServletRequest req,@PathVariable String id,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {none(query);keys(body,"workspaceId","expectedVersion","reasonCode");return ResponseEntity.accepted().body(operations.retry(actor(req),requestId(req),AdminPages.uuid(string(body,"workspaceId")),header(req),AdminPages.uuid(id),version(body),string(body,"reasonCode")));}
    @PatchMapping("/knowledge/documents/{id}/status") Object knowledgeStatus(HttpServletRequest req,@PathVariable String id,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {none(query);keys(body,"workspaceId","status","expectedVersion","reasonCode");return operations.status(actor(req),requestId(req),AdminPages.uuid(string(body,"workspaceId")),header(req),AdminPages.uuid(id),string(body,"status"),version(body),string(body,"reasonCode"));}
    @GetMapping("/system/health") Object health(HttpServletRequest req,@RequestParam Map<String,String> query) {global(req);none(query);return service.health(actor(req));}
    @GetMapping("/audit-events") Object audit(HttpServletRequest req,@RequestParam Map<String,String> query) {global(req);return service.audits(actor(req),query);}
    @RequestMapping("/**") Object missingAdminRoute() {throw AdminFailure.missing();}
    private static void none(Map<String,String> query) {if(!query.isEmpty())throw AdminFailure.input();}
    private static void keys(Map<String,Object> body,String... keys) {if(!body.keySet().equals(Set.of(keys)))throw AdminFailure.input();}
    private static String string(Map<String,Object> body,String key) {if(!(body.get(key) instanceof String s))throw AdminFailure.input();return s;}
    private static long version(Map<String,Object> body) {Object value=body.get("expectedVersion");if(!(value instanceof Integer || value instanceof Long) || ((Number)value).longValue()<0)throw AdminFailure.input();return ((Number)value).longValue();}
    @ExceptionHandler(Exception.class) ResponseEntity<?> error(Exception e,HttpServletRequest req) {
        int status=500;String code="ADMIN_REQUEST_FAILED";Map<String,Object> details=Map.of();
        if(e instanceof AdminFailure f) {status=f.status;code=f.code;details=f.details;}
        else if(e instanceof io.devicetruth.knowledge.KnowledgeException f) {status=f.status();code="KNOWLEDGE_OPERATION_FAILED";}
        else if(e instanceof ResponseStatusException f) {status=f.getStatusCode().value();code=Set.of("INVALID_ACCOUNT_INPUT","EMAIL_ALREADY_REGISTERED").contains(String.valueOf(f.getReason()))?f.getReason():"INVALID_ADMIN_INPUT";}
        else if(e instanceof org.springframework.web.multipart.MaxUploadSizeExceededException) {status=413;code="FILE_TOO_LARGE";}
        else if(e instanceof org.springframework.web.multipart.support.MissingServletRequestPartException || e instanceof org.springframework.web.bind.MissingServletRequestParameterException) {status=400;code="INVALID_ADMIN_INPUT";}
        else if(e instanceof IllegalArgumentException) {status=400;code="INVALID_ADMIN_INPUT";}
        else if(e instanceof org.springframework.http.converter.HttpMessageNotReadableException || e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) {status=400;code="INVALID_ADMIN_INPUT";}
        service.recordFailure(actor(req),requestId(req),code,status);
        var body=new LinkedHashMap<String,Object>();body.put("code",code);body.put("requestId",requestId(req));if(!details.isEmpty())body.put("details",details);
        return ResponseEntity.status(status).body(body);
    }
}
