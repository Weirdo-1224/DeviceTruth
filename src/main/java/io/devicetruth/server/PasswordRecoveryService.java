package io.devicetruth.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 邮件接口只在发送时取得原始凭证，数据库仅保存验证摘要。 */
public final class PasswordRecoveryService {
    public interface ResetMailSender {
        boolean available();
        void send(String email, String token);
    }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwords;
    private final ResetMailSender mail;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(JdbcTemplate jdbc, TransactionTemplate transactions, PasswordEncoder passwords,
            ResetMailSender mail, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) throw new IllegalArgumentException("Reset lifetime must be positive");
        this.jdbc = jdbc; this.transactions = transactions; this.passwords = passwords;
        this.mail = mail; this.lifetime = lifetime;
    }

    public void request(String email) {
        String normalized = AccountService.normalizeEmail(email);
        // 未配置时对所有邮箱同样拒绝，不能通过是否有账号枚举配置错误。
        if (!mail.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID reset = transactions.execute(status -> {
            var users = jdbc.queryForList("SELECT id FROM dt_account WHERE email=? FOR UPDATE", UUID.class, normalized);
            if (users.isEmpty()) return null;
            UUID user = users.getFirst();
            jdbc.update("DELETE FROM dt_password_reset WHERE user_id=?", user);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO dt_password_reset(id,user_id,token_hash,expires_at) VALUES (?,?,?,CURRENT_TIMESTAMP + (? * interval '1 second'))",
                    id, user, digest(token), lifetime.toSeconds());
            return id;
        });
        if (reset != null) {
            try {
                mail.send(normalized, token);
                jdbc.update("UPDATE dt_password_reset SET delivery_status='ACCEPTED_BY_PROVIDER' WHERE id=?", reset);
            }
            catch (RuntimeException e) {
                // 发送失败不返回不同的账号存在性应答；不保留可消费的失败凭证。
                jdbc.update("UPDATE dt_password_reset SET delivery_status='FAILED',consumed_at=CURRENT_TIMESTAMP WHERE id=?", reset);
            }
        }
    }

    public void reset(String token, String password, String confirmation) {
        AccountService.validatePassword(password);
        if (!Objects.equals(password, confirmation))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH");
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
        String hash = digest(token);
        String encoded = passwords.encode(password);
        transactions.executeWithoutResult(status -> {
            var users = jdbc.queryForList("SELECT user_id FROM dt_password_reset WHERE token_hash=?", UUID.class, hash);
            if (users.isEmpty()) throw invalidToken();
            UUID user = users.getFirst();
            // 同账号先锁账号，再消费凭证；多令牌/并发消费遵守相同锁顺序。
            jdbc.queryForObject("SELECT id FROM dt_account WHERE id=? FOR UPDATE", UUID.class, user);
            int consumed = jdbc.update("UPDATE dt_password_reset SET consumed_at=CURRENT_TIMESTAMP WHERE token_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP", hash);
            if (consumed != 1) throw invalidToken();
            jdbc.update("UPDATE dt_account SET password_hash=? WHERE id=?", encoded, user);
            jdbc.update("DELETE FROM spring_session WHERE principal_name=?", user.toString());
            jdbc.update("UPDATE dt_password_reset SET consumed_at=COALESCE(consumed_at,CURRENT_TIMESTAMP) WHERE user_id=?", user);
        });
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_RESET_TOKEN");
    }
    private static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
