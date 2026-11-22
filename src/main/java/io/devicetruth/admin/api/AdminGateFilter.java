package io.devicetruth.admin.api;

import io.devicetruth.admin.authorization.AdminAuthorization;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 当前会话必须属于请求域；域标记和账号用途都由服务器核对。 */
public final class AdminGateFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    public AdminGateFilter(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        boolean admin=request.getRequestURI().startsWith("/api/admin/");
        UUID requestId=UUID.randomUUID();
        if(admin) { request.setAttribute("adminRequestId",requestId); response.setHeader("X-Request-ID",requestId.toString()); }
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        UUID actor=null;
        if(authentication!=null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            try { actor=UUID.fromString(authentication.getName()); } catch(IllegalArgumentException ignored) { }
        }
        try {
            if (request.getSession(false) != null
                    && !io.devicetruth.server.LoginRealm.forRequest(request).accepts(request.getSession(false)))
                throw new AdminFailure(401,"AUTHENTICATION_REQUIRED");
            boolean publicAdmin = request.getRequestURI().equals("/api/admin/auth/login")
                    || request.getRequestURI().equals("/api/admin/auth/csrf");
            if (actor != null) {
                var realm = io.devicetruth.server.LoginRealm.forRequest(request);
                if (!realm.accepts(request.getSession(false))) throw new AdminFailure(401,"AUTHENTICATION_REQUIRED");
                var types = jdbc.queryForList("SELECT account_type FROM dt_account WHERE id=?",String.class,actor);
                if (types.isEmpty() || !realm.name().equals(types.getFirst())) throw new AdminFailure(401,"AUTHENTICATION_REQUIRED");
            }
            if(admin && !publicAdmin) {
                if(actor==null) throw new AdminFailure(401,"AUTHENTICATION_REQUIRED");
                new AdminAuthorization(jdbc).require(actor);
            } else if(actor!=null && request.getRequestURI().startsWith("/api/")) {
                var states=jdbc.queryForList("SELECT status FROM dt_account WHERE id=?",String.class,actor);
                if(states.isEmpty() || !states.getFirst().equals("ACTIVE")) throw new AdminFailure(401,"ACCOUNT_DISABLED");
            }
            chain.doFilter(request,response);
        } catch(AdminFailure e) {
            if(admin) {
                try {new io.devicetruth.admin.persistence.AdminAudit(jdbc).append(actor,null,null,"ADMIN_ACCESS",null,"ADMIN_ACCESS","DENIED",null,e.code,requestId,null,null);}
                catch(RuntimeException ignored) { /* 拒绝保持拒绝；不写底层异常。 */ }
            }
            response.setStatus(e.status); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\""+e.code+"\",\"requestId\":\""+requestId+"\"}");
        }
    }
}
