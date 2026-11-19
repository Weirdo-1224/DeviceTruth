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
                .authorizeHttpRequests(c -> c.requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        // 全链 scope 尚未接通时拒绝资源访问，不把新账号落入固定演示归属。
                        .requestMatchers("/api/**").denyAll().anyRequest().permitAll());
        return http.build();
    }
}
