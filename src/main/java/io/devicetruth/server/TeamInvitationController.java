package io.devicetruth.server;

import java.util.*;
import io.devicetruth.application.agent.AccessScope.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("product")
public class TeamInvitationController {
    public record Create(String email, Role role) {}
    public record Accept(String token) {}
    private final TeamInvitationService service;
    private final AuthRateLimiter limits;
    public TeamInvitationController(TeamInvitationService service,AuthRateLimiter limits){this.service=service;this.limits=limits;}
    @ModelAttribute void matchWorkspace(@PathVariable(value="id",required=false) UUID workspace,@RequestHeader(value="X-Workspace-ID",required=false) UUID header){
        if(workspace!=null && header!=null && !workspace.equals(header))throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
    }
    @GetMapping("/api/workspaces/{id}/invitations") Object list(Authentication auth,@PathVariable UUID id){return service.list(actor(auth),id);}
    @PostMapping("/api/workspaces/{id}/invitations") Object create(Authentication auth,@PathVariable UUID id,@RequestBody Create input,HttpServletRequest req){
        limits.check("invitation",req.getRemoteAddr(),input.email());return service.create(actor(auth),id,input.email(),input.role());
    }
    @PostMapping("/api/workspaces/{id}/invitations/{invitationId}/resend") Object resend(Authentication auth,@PathVariable UUID id,@PathVariable UUID invitationId,HttpServletRequest req){
        limits.check("invitation",req.getRemoteAddr(),null);return service.resend(actor(auth),id,invitationId);
    }
    @PostMapping("/api/workspaces/{id}/invitations/{invitationId}/revoke") Object revoke(Authentication auth,@PathVariable UUID id,@PathVariable UUID invitationId){service.revoke(actor(auth),id,invitationId);return Map.of("revoked",true);}
    @PostMapping("/api/invitations/accept") Object accept(Authentication auth,@RequestBody Accept input,HttpServletRequest req){limits.check("invitation-accept",req.getRemoteAddr(),null);return Map.of("workspaceId",service.accept(actor(auth),input.token()));}
    private UUID actor(Authentication auth){return UUID.fromString(auth.getName());}
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("code",e.getReason()==null?"RESOURCE_NOT_FOUND":e.getReason()));}
}
