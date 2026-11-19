package io.devicetruth.server;

import io.devicetruth.application.agent.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;

/** 仅 HTTP 边界使用请求上下文；worker 从持久 Run 恢复，不调用此组件。 */
@Component @Profile("product")
public final class HttpAccess {
    private final JdbcTemplate jdbc;
    private final WorkspaceScopeResolver scopes;
    private final org.springframework.session.jdbc.JdbcIndexedSessionRepository sessions;
    public HttpAccess(HikariDataSource source,org.springframework.session.jdbc.JdbcIndexedSessionRepository sessions){jdbc=new JdbcTemplate(source);scopes=new WorkspaceScopeResolver(jdbc);this.sessions=sessions;}
    public Runnable streamGuard(UUID run) {
        var scope=run(run);String session=request().getSession(false).getId();
        return ()->{ if(sessions.findById(session)==null) throw new GeneralAgentService.RequestFailure("AUTHENTICATION_REQUIRED",401);
            try {scopes.resolve(scope.actorUserId(),scope.workspaceId());} catch(RuntimeException e){throw new GeneralAgentService.RequestFailure("AUTHORIZATION_REVOKED",403);} };
    }
    public HttpServletRequest request(){return ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();}
    public AccessScope current() {
        var request=request(); String[] parts=request.getRequestURI().split("/");
        UUID resource=null;
        if(parts.length>3) {
            String kind=parts[2];
            if(java.util.Set.of("conversations","runs","files").contains(kind) && !parts[3].equals("summaries"))
                resource=workspace(kind,UUID.fromString(parts[3]));
            if(kind.equals("knowledge") && parts.length>4 && parts[3].equals("documents")) resource=workspace("documents",UUID.fromString(parts[4]));
        }
        if(resource==null && request.getParameter("runId")!=null) resource=workspace("runs",UUID.fromString(request.getParameter("runId")));
        return resolve(resource);
    }
    public AccessScope run(UUID run){return resolve(workspace("runs",run));}
    private UUID workspace(String type,UUID id) {
        String sql=switch(type){
            case "conversations"->"SELECT workspace_id FROM dt_conversation WHERE id=?";
            case "runs"->"SELECT c.workspace_id FROM dt_agent_run r JOIN dt_conversation c ON c.id=r.conversation_id WHERE r.id=?";
            case "files"->"SELECT w.id FROM dt_uploaded_file f JOIN dt_workspace w ON w.owner_id=f.owner_id AND w.project_id=f.project_id WHERE f.id=?";
            case "documents"->"SELECT w.id FROM dt_knowledge_document d JOIN dt_workspace w ON w.owner_id=d.owner_id AND w.project_id=d.project_id WHERE d.id=?";
            default->throw new IllegalArgumentException();};
        var values=jdbc.queryForList(sql,UUID.class,id);
        if(values.isEmpty() || values.getFirst()==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
        return values.getFirst();
    }
    private AccessScope resolve(UUID resource) {
        var request=request();
        if(request.getUserPrincipal()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED");
        UUID actor=UUID.fromString(request.getUserPrincipal().getName());
        String value=request.getHeader("X-Workspace-ID");UUID header=value==null?null:UUID.fromString(value);
        var scope=resource==null?scopes.resolve(actor,header):scopes.resolveResource(actor,resource,header);
        if(!java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) return scopes.requireWrite(scope);
        return scope;
    }
}
