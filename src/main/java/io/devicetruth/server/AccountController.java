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
    private final AccountService accounts;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;
    public AccountController(AccountService accounts, HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf) {
        this.accounts = accounts; this.contexts = contexts; this.csrf = csrf;
    }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @PostMapping("/register") AccountService.AccountView register(@RequestBody Registration body, HttpServletRequest req, HttpServletResponse res) {
        var account = accounts.register(body.email(), body.password(), body.displayName());
        signIn(account, req, res);
        return account;
    }
    @PostMapping("/login") AccountService.AccountView login(@RequestBody Login body, HttpServletRequest req, HttpServletResponse res) {
        var account = accounts.authenticate(body.email(), body.password());
        signIn(account, req, res);
        return account;
    }
    @GetMapping("/me") AccountService.AccountView me(Authentication authentication) {
        return accounts.get(UUID.fromString(authentication.getName()));
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
