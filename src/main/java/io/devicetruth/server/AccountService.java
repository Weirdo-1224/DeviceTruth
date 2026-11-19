package io.devicetruth.server;

import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 共享账号底座；不提供平台管理权限或管理端操作。 */
public final class AccountService {
    public record AccountView(UUID userId, String email, String displayName) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwords;
    private final String dummyPassword;

    public AccountService(JdbcTemplate jdbc, TransactionTemplate transactions, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.passwords = passwords;
        this.dummyPassword = passwords.encode(UUID.randomUUID().toString());
    }

    public AccountView register(String email, String password, String displayName) {
        String normalized = normalizeEmail(email);
        validatePassword(password);
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_INPUT");
        String encoded = passwords.encode(password);
        try {
            return transactions.execute(status -> {
                UUID user = UUID.randomUUID();
                UUID workspace = UUID.randomUUID();
                jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name) VALUES (?,?,?,?)",
                        user, normalized, encoded, displayName.strip());
                jdbc.update("INSERT INTO dt_workspace(id,kind,name,personal_user_id,owner_id,project_id) VALUES (?,'PERSONAL',?,?,?,?)",
                        workspace, "个人空间", user, "workspace-" + workspace, "default");
                jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'ADMIN')", workspace, user);
                return new AccountView(user, normalized, displayName.strip());
            });
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED");
        }
    }

    public AccountView authenticate(String email, String password) {
        String normalized = normalizeEmail(email);
        validatePassword(password);
        var rows = jdbc.query("SELECT id,email,display_name,password_hash FROM dt_account WHERE email=?",
                (rs, row) -> new Object[]{new AccountView(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("display_name")), rs.getString("password_hash")}, normalized);
        String encoded = rows.isEmpty() ? dummyPassword : (String) rows.getFirst()[1];
        boolean matches = passwords.matches(password, encoded);
        if (rows.isEmpty() || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        return (AccountView) rows.getFirst()[0];
    }

    public AccountView get(UUID user) {
        return jdbc.query("SELECT id,email,display_name FROM dt_account WHERE id=?",
                (rs, row) -> new AccountView(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name")), user)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));
    }

    static String normalizeEmail(String email) {
        if (email == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_INPUT");
        String value = email.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_INPUT");
        return value;
    }

    static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_INPUT");
    }
}
