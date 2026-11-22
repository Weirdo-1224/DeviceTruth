package io.devicetruth.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("product")
@RequestMapping("/api/admin/auth")
public final class AdminAuthenticationController {
    private final AccountService accounts;
    private final AuthRateLimiter limits;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;

    public AdminAuthenticationController(AccountService accounts, AuthRateLimiter limits,
            HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf) {
        this.accounts = accounts; this.limits = limits; this.contexts = contexts; this.csrf = csrf;
    }

    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token, HttpServletRequest request) {
        request.getSession().setAttribute(LoginRealm.SESSION_ATTRIBUTE, LoginRealm.ADMIN.name());
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login") AccountService.AccountView login(@RequestBody AccountController.Login body,
            HttpServletRequest req, HttpServletResponse res) {
        limits.check("admin-login", req.getRemoteAddr(), body.email());
        var account = accounts.authenticate(body.email(), body.password(), LoginRealm.ADMIN);
        if (req.getSession(false) != null) req.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(account.userId().toString(), null, List.of()));
        SecurityContextHolder.setContext(context);
        csrf.saveToken(null, req, res);
        contexts.saveContext(context, req, res);
        req.getSession().setAttribute(LoginRealm.SESSION_ATTRIBUTE, LoginRealm.ADMIN.name());
        return account;
    }

    @GetMapping("/me") AccountService.AccountView me(Authentication authentication) {
        return accounts.get(UUID.fromString(authentication.getName()), LoginRealm.ADMIN);
    }

    @PostMapping("/logout") Map<String,Boolean> logout(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
        csrf.saveToken(null, req, res);
        new SecurityContextLogoutHandler().logout(req, res, auth);
        return Map.of("loggedOut", true);
    }

    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e instanceof AccountService.RealmMismatch mismatch
                ? Map.of("code", e.getReason(), "targetRealm", mismatch.target().name()) : Map.of("code", e.getReason()));
    }
}
