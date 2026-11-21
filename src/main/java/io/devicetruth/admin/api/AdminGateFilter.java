package io.devicetruth.admin.api;

import io.devicetruth.admin.authorization.AdminAuthorization;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 位于统一安全链内；不建立第二个 principal/session。 */
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
            if(admin) {
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
