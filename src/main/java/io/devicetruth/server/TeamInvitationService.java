package io.devicetruth.server;

import io.devicetruth.application.agent.AccessScope.Role;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public final class TeamInvitationService {
    public interface Mail { boolean available(); void send(String email, String token); }
    public record InvitationView(UUID invitationId, UUID workspaceId, String email, Role role, String status,
                                 String deliveryStatus, Instant expiresAt) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final WorkspaceService workspaces;
    private final Mail mail;
    private final java.time.Duration lifetime;
    public TeamInvitationService(JdbcTemplate jdbc, TransactionTemplate transactions, WorkspaceService workspaces, Mail mail) {
        this(jdbc,transactions,workspaces,mail,java.time.Duration.ofDays(7));
    }
    public TeamInvitationService(JdbcTemplate jdbc, TransactionTemplate transactions, WorkspaceService workspaces, Mail mail, java.time.Duration lifetime) {
        if(lifetime.toSeconds()<1)throw new IllegalArgumentException("Invitation lifetime must be positive");
        this.jdbc=jdbc; this.transactions=transactions; this.workspaces=workspaces; this.mail=mail;this.lifetime=lifetime;
    }
    public List<InvitationView> list(UUID actor, UUID workspace) {
        return transactions.execute(s -> { workspaces.lockManagedTeam(actor,workspace); return views(workspace,null); });
    }
    public InvitationView create(UUID actor, UUID workspace, String email, Role role) {
        String normalized=AccountService.normalizeEmail(email);
        if(role==null) throw error(400,"INVALID_ROLE");
        String token=token(); UUID id=UUID.randomUUID();
        transactions.executeWithoutResult(s -> {
            workspaces.lockManagedTeam(actor,workspace);
            if(!mail.available()) throw error(503,"MAIL_UNAVAILABLE");
            jdbc.update("INSERT INTO dt_team_invitation(id,workspace_id,email,role,created_by_user_id,token_hash,expires_at) VALUES (?,?,?,?,?,?,now()+(? * interval '1 second'))",id,workspace,normalized,role.name(),actor,hash(token),lifetime.toSeconds());
        });
        deliver(id,normalized,token);
        return list(actor,workspace).stream().filter(v->v.invitationId().equals(id)).findFirst().orElseThrow();
    }
    public InvitationView resend(UUID actor, UUID workspace, UUID id) {
        String token=token();
        String email=transactions.execute(s -> {
            workspaces.lockManagedTeam(actor,workspace);
            var view=require(workspace,id);
            if(!view.status().equals("PENDING") && !view.status().equals("EXPIRED")) throw error(409,"INVITATION_NOT_PENDING");
            if(!mail.available()) throw error(503,"MAIL_UNAVAILABLE");
            jdbc.update("UPDATE dt_team_invitation SET token_hash=?,expires_at=now()+(? * interval '1 second'),delivery_status='PENDING' WHERE id=?",hash(token),lifetime.toSeconds(),id);
            return view.email();
        });
        deliver(id,email,token); return require(workspace,id);
    }
    public void revoke(UUID actor, UUID workspace, UUID id) {
        transactions.executeWithoutResult(s -> {
            workspaces.lockManagedTeam(actor,workspace); var view=require(workspace,id);
            if(view.status().equals("ACCEPTED")) throw error(409,"INVITATION_ALREADY_ACCEPTED");
            jdbc.update("UPDATE dt_team_invitation SET status='REVOKED' WHERE id=?",id);
        });
    }
    public UUID accept(UUID actor, String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw error(400,"INVALID_INVITATION");
        return transactions.execute(s -> {
            var ids=jdbc.queryForList("SELECT workspace_id FROM dt_team_invitation WHERE token_hash=?",UUID.class,hash(token));
            if(ids.isEmpty()) throw error(400,"INVALID_INVITATION");
            UUID workspace=ids.getFirst();
            jdbc.queryForObject("SELECT id FROM dt_workspace WHERE id=? FOR UPDATE",UUID.class,workspace);
            var rows=jdbc.queryForList("SELECT * FROM dt_team_invitation WHERE token_hash=? AND status='PENDING' AND expires_at>now() AND delivery_status<>'FAILED' FOR UPDATE",hash(token));
            if(rows.isEmpty()) throw error(409,"INVITATION_NOT_PENDING");
            var row=rows.getFirst();
            String email=jdbc.queryForObject("SELECT email FROM dt_account WHERE id=?",String.class,actor);
            if(!row.get("email").equals(email)) throw error(403,"INVITATION_EMAIL_MISMATCH");
            jdbc.update("INSERT INTO dt_workspace_member(workspace_id,user_id,role) VALUES (?,?,?) ON CONFLICT DO NOTHING",workspace,actor,row.get("role"));
            jdbc.update("UPDATE dt_team_invitation SET status='ACCEPTED',accepted_by_user_id=? WHERE id=?",actor,row.get("id"));
            return workspace;
        });
    }
    private void deliver(UUID id,String email,String token) {
        String state;
        try { mail.send(email,token); state="ACCEPTED_BY_PROVIDER"; }
        catch(RuntimeException e) { state="FAILED"; }
        // 只更新本次凭证，迟到发送结果不能覆盖更新后的重发状态。
        jdbc.update("UPDATE dt_team_invitation SET delivery_status=? WHERE id=? AND token_hash=?",state,id,hash(token));
    }
    private List<InvitationView> views(UUID workspace,UUID id) {
        return jdbc.query("SELECT *,CASE WHEN status='PENDING' AND expires_at<=now() THEN 'EXPIRED' ELSE status END AS effective_status FROM dt_team_invitation WHERE workspace_id=? AND (?::uuid IS NULL OR id=?::uuid) ORDER BY created_at,id",
                (rs,n)->new InvitationView(rs.getObject("id",UUID.class),workspace,rs.getString("email"),Role.valueOf(rs.getString("role")),rs.getString("effective_status"),rs.getString("delivery_status"),rs.getTimestamp("expires_at").toInstant()),workspace,id,id);
    }
    private InvitationView require(UUID workspace,UUID id) { return views(workspace,id).stream().findFirst().orElseThrow(()->error(404,"INVITATION_NOT_FOUND")); }
    private static String token() { byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String token) { try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException();} }
    private static ResponseStatusException error(int status,String code) {return new ResponseStatusException(HttpStatus.valueOf(status),code);}
}
