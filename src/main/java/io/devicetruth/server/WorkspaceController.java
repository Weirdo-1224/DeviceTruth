package io.devicetruth.server;

import io.devicetruth.application.agent.AccessScope.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("product")
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    public record NameInput(String name) {}
    public record RoleInput(Role role) {}
    private final WorkspaceService service;
    public WorkspaceController(WorkspaceService service) { this.service = service; }
    @ModelAttribute void matchWorkspaceHeader(@PathVariable(value = "id", required = false) UUID workspace,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID header) {
        if (workspace != null && header != null && !workspace.equals(header))
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
    @GetMapping List<WorkspaceService.WorkspaceView> list(Authentication auth) { return service.list(actor(auth)); }
    @PostMapping WorkspaceService.WorkspaceView create(Authentication auth, @RequestBody NameInput input) {
        return service.createTeam(actor(auth), input.name());
    }
    @PatchMapping("/{id}") WorkspaceService.WorkspaceView rename(Authentication auth, @PathVariable UUID id, @RequestBody NameInput input) {
        return service.rename(actor(auth), id, input.name());
    }
    @GetMapping("/{id}/members") List<WorkspaceService.MemberView> members(Authentication auth, @PathVariable UUID id) {
        return service.members(actor(auth), id);
    }
    @PatchMapping("/{id}/members/{userId}") Map<String,Boolean> role(Authentication auth, @PathVariable UUID id, @PathVariable UUID userId, @RequestBody RoleInput input) {
        service.changeRole(actor(auth), id, userId, input.role()); return Map.of("updated", true);
    }
    @DeleteMapping("/{id}/members/{userId}") Map<String,Boolean> remove(Authentication auth, @PathVariable UUID id, @PathVariable UUID userId) {
        service.remove(actor(auth), id, userId); return Map.of("removed", true);
    }
    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("code", e.getReason() == null ? "RESOURCE_NOT_FOUND" : e.getReason()));
    }
}
