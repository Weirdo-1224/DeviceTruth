package io.devicetruth.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@RestController
@Profile("product")
@RequestMapping("/api/auth")
public class AccountController {
    public record Registration(String email, String password, String displayName) {}
    public record Login(String email, String password) {}
    public record ResetRequest(String email) {}
    public record PasswordReset(String token, String password, String confirmPassword) {}
    private final PasswordRecoveryService recovery;
    private final AuthRateLimiter limits;
    private final AccountService accounts;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;
    public AccountController(AccountService accounts, HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf, PasswordRecoveryService recovery, AuthRateLimiter limits) {
        this.accounts = accounts; this.contexts = contexts; this.csrf = csrf;
        this.recovery = recovery;
        this.limits = limits;
    }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @PostMapping("/register") AccountService.AccountView register(@RequestBody Registration body, HttpServletRequest req, HttpServletResponse res) {
        limits.check("register", req.getRemoteAddr(), body.email());
        var account = accounts.register(body.email(), body.password(), body.displayName());
        signIn(account, req, res);
        return account;
    }
    @PostMapping("/login") AccountService.AccountView login(@RequestBody Login body, HttpServletRequest req, HttpServletResponse res) {
        limits.check("login", req.getRemoteAddr(), body.email());
        var account = accounts.authenticate(body.email(), body.password());
        signIn(account, req, res);
        return account;
    }
    @GetMapping("/me") AccountService.AccountView me(Authentication authentication) {
        return accounts.get(UUID.fromString(authentication.getName()));
    }
    @PostMapping("/password-reset-requests") Map<String,String> requestReset(@RequestBody ResetRequest body, HttpServletRequest req) {
        limits.check("password-reset-request", req.getRemoteAddr(), body.email());
        recovery.request(body.email());
        return Map.of("status", "REQUEST_ACCEPTED", "message", "如果该邮箱已注册，将尝试发送重置邮件。");
    }
    @PostMapping("/password-resets") Map<String,Boolean> reset(@RequestBody PasswordReset body, HttpServletRequest req) {
        limits.check("password-reset", req.getRemoteAddr(), null);
        recovery.reset(body.token(), body.password(), body.confirmPassword());
        return Map.of("passwordReset", true);
    }
    @PostMapping("/logout") Map<String,Boolean> logout(Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        csrf.saveToken(null, req, res);
        new SecurityContextLogoutHandler().logout(req, res, authentication);
        return Map.of("loggedOut", true);
    }
    private void signIn(AccountService.AccountView account, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) req.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(account.userId().toString(), null, List.of()));
        SecurityContextHolder.setContext(context);
        csrf.saveToken(null, req, res);
        contexts.saveContext(context, req, res);
    }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("code", e.getReason()));
    }
}
