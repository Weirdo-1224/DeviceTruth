package io.devicetruth.server;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
@Profile("product")
@EnableWebSecurity
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 28800)
@DependsOn("productFlyway")
public class ProductAuthConfiguration {
    @Bean TeamInvitationService teamInvitationService(HikariDataSource source, WorkspaceService workspaces,
            PasswordRecoveryService.ResetMailSender sender, Environment environment) {
        return new TeamInvitationService(new JdbcTemplate(source),new TransactionTemplate(new DataSourceTransactionManager(source)),workspaces,
                new TeamInvitationService.Mail(){public boolean available(){return sender instanceof SmtpResetMailSender smtp && smtp.available();}
                    public void send(String email,String token){((SmtpResetMailSender)sender).sendInvitation(email,token);}},
                environment.getProperty("devicetruth.auth.invitation-token-lifetime",java.time.Duration.class,java.time.Duration.ofDays(7)));
    }
    @Bean WorkspaceService workspaceService(HikariDataSource source) {
        return new WorkspaceService(new JdbcTemplate(source), new TransactionTemplate(new DataSourceTransactionManager(source)));
    }
    @Bean AuthRateLimiter authRateLimiter(Environment environment) {
        return new AuthRateLimiter(environment.getProperty("devicetruth.auth.rate-limit", Integer.class, 20),
                environment.getProperty("devicetruth.auth.rate-capacity", Integer.class, 10000),
                environment.getProperty("devicetruth.auth.rate-window", java.time.Duration.class, java.time.Duration.ofMinutes(15)));
    }
    @Bean PasswordRecoveryService.ResetMailSender resetMailSender(Environment environment) {
        var sender = new org.springframework.mail.javamail.JavaMailSenderImpl();
        sender.setHost(environment.getProperty("devicetruth.mail.host", ""));
        sender.setPort(environment.getProperty("devicetruth.mail.port", Integer.class, 587));
        sender.setUsername(environment.getProperty("devicetruth.mail.username", ""));
        sender.setPassword(environment.getProperty("devicetruth.mail.password", ""));
        sender.setDefaultEncoding("UTF-8");
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(!sender.getUsername().isBlank()));
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.debug", "false");
        for (String timeout : java.util.List.of("connectiontimeout", "timeout", "writetimeout")) {
            int value = environment.getProperty("devicetruth.mail." + timeout + "-ms", Integer.class, 5000);
            if (value < 1 || value > 30000) throw new IllegalArgumentException("SMTP timeout must be 1..30000 ms");
            properties.setProperty("mail.smtp." + timeout, Integer.toString(value));
        }
        return new SmtpResetMailSender(sender, environment.getProperty("devicetruth.mail.from", ""),
                environment.getProperty("devicetruth.mail.public-url", ""));
    }
    @Bean PasswordEncoder accountPasswordEncoder() {
        return new DelegatingPasswordEncoder("pbkdf2", java.util.Map.of("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
    @Bean AccountService accountService(HikariDataSource source, PasswordEncoder encoder) {
        return new AccountService(new JdbcTemplate(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)), encoder);
    }
    @Bean HttpSessionSecurityContextRepository accountSecurityContexts() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean PasswordRecoveryService passwordRecoveryService(HikariDataSource source, PasswordEncoder passwords,
            org.springframework.beans.factory.ObjectProvider<PasswordRecoveryService.ResetMailSender> senders,
            Environment environment) {
        var sender = senders.getIfAvailable(() -> new PasswordRecoveryService.ResetMailSender() {
            public boolean available() { return false; }
            public void send(String email, String token) { throw new IllegalStateException("MAIL_UNAVAILABLE"); }
        });
        return new PasswordRecoveryService(new JdbcTemplate(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)), passwords, sender,
                environment.getProperty("devicetruth.auth.reset-token-lifetime", java.time.Duration.class, java.time.Duration.ofMinutes(30)));
    }
    @Bean HttpSessionCsrfTokenRepository accountCsrfTokens() { return new HttpSessionCsrfTokenRepository(); }
    @Bean DefaultCookieSerializer cookieSerializer(Environment environment) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("DTSESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(environment.getProperty("devicetruth.auth.cookie-secure", Boolean.class, true));
        serializer.setSameSite("Lax");
        return serializer;
    }
    @Bean SecurityFilterChain productSecurity(HttpSecurity http, HttpSessionSecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository csrf) throws Exception {
        http.securityContext(c -> c.securityContextRepository(contexts))
                .csrf(c -> c.csrfTokenRepository(csrf))
                .requestCache(c -> c.disable())
                .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
                .exceptionHandling(c -> c.authenticationEntryPoint((req,res,e) -> {
                    res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":\"AUTHENTICATION_REQUIRED\"}");
                }).accessDeniedHandler((req,res,e) -> {
                    res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":\"ACCESS_DENIED\"}");
                }))
                .authorizeHttpRequests(c -> c.requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/register", "/api/auth/login",
                        "/api/auth/password-reset-requests", "/api/auth/password-resets").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/workspaces", "/api/workspaces/**", "/api/invitations/accept").authenticated()
                        // 全链 scope 尚未接通时拒绝资源访问，不把新账号落入固定演示归属。
                        .requestMatchers("/api/conversations", "/api/conversations/**", "/api/runs/**", "/api/assets", "/api/assets/**", "/api/files", "/api/files/**", "/api/knowledge/**").authenticated()
                        .requestMatchers("/api/**", "/actuator/**").denyAll().anyRequest().permitAll());
        return http.build();
    }
}
