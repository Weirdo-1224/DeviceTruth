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
        return register(email, password, displayName, LoginRealm.USER);
    }

    /** 仅由可信维护入口调用；不自动授予平台角色或业务空间。 */
    public AccountView registerAdmin(String email, String password, String displayName) {
        return register(email, password, displayName, LoginRealm.ADMIN);
    }

    private AccountView register(String email, String password, String displayName, LoginRealm realm) {
        String normalized = normalizeEmail(email);
        validatePassword(password);
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_INPUT");
        String encoded = passwords.encode(password);
        try {
            return transactions.execute(status -> {
                UUID user = UUID.randomUUID();
                UUID workspace = UUID.randomUUID();
                jdbc.update("INSERT INTO dt_account(id,email,password_hash,display_name,account_type) VALUES (?,?,?,?,?)",
                        user, normalized, encoded, displayName.strip(), realm.name());
                if (realm == LoginRealm.USER) {
                    jdbc.update("INSERT INTO dt_workspace(id,kind,name,personal_user_id,owner_id,project_id) VALUES (?,'PERSONAL',?,?,?,?)",
                            workspace, "个人空间", user, "workspace-" + workspace, "default");
                    jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,'ADMIN')", workspace, user);
                }
                return new AccountView(user, normalized, displayName.strip());
            });
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED");
        }
    }

    public AccountView authenticate(String email, String password) {
        return authenticate(email, password, LoginRealm.USER);
    }

    public AccountView authenticate(String email, String password, LoginRealm realm) {
        String normalized = normalizeEmail(email);
        validatePassword(password);
        var rows = jdbc.query("SELECT id,email,display_name,password_hash,status,account_type FROM dt_account WHERE email=?",
                (rs, row) -> new Object[]{new AccountView(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("display_name")), rs.getString("password_hash"), rs.getString("status"), rs.getString("account_type")}, normalized);
        String encoded = rows.isEmpty() ? dummyPassword : (String) rows.getFirst()[1];
        boolean matches = passwords.matches(password, encoded);
        if (rows.isEmpty() || !matches || !"ACTIVE".equals(rows.getFirst()[2])) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        if (!realm.name().equals(rows.getFirst()[3])) throw new RealmMismatch(LoginRealm.valueOf((String) rows.getFirst()[3]));
        if (realm == LoginRealm.ADMIN && jdbc.queryForObject("SELECT count(*) FROM dt_platform_admin WHERE user_id=?", Long.class,
                ((AccountView) rows.getFirst()[0]).userId()) != 1)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PLATFORM_ADMIN_REQUIRED");
        return (AccountView) rows.getFirst()[0];
    }

    public AccountView get(UUID user) {
        return get(user, LoginRealm.USER);
    }

    public AccountView get(UUID user, LoginRealm realm) {
        return jdbc.query("SELECT id,email,display_name FROM dt_account WHERE id=? AND status='ACTIVE' AND account_type=?",
                (rs, row) -> new AccountView(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name")), user,
                realm.name()).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));
    }

    public static PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.password.DelegatingPasswordEncoder("pbkdf2", java.util.Map.of(
                "pbkdf2", org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    public static final class RealmMismatch extends ResponseStatusException {
        private final LoginRealm target;
        RealmMismatch(LoginRealm target) { super(HttpStatus.CONFLICT, "LOGIN_REALM_MISMATCH"); this.target = target; }
        public LoginRealm target() { return target; }
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
