package io.devicetruth.application.agent;

import java.util.Objects;
import java.util.UUID;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;

/** 由服务器成员查询产生；不作为 HTTP 请求 DTO，也不从模型参数反序列化。 */
public record AccessScope(UUID actorUserId, UUID workspaceId, Role role, ServerIdentity storageIdentity, long accountStatusVersion) {
    public AccessScope(UUID actorUserId, UUID workspaceId, Role role, ServerIdentity storageIdentity) {
        this(actorUserId, workspaceId, role, storageIdentity, 0);
    }
    public enum Role { ADMIN, COLLABORATOR, VIEWER }
    public AccessScope {
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(storageIdentity);
    }
    public boolean canWrite() { return role != Role.VIEWER; }
    public boolean canManage() { return role == Role.ADMIN; }
}
