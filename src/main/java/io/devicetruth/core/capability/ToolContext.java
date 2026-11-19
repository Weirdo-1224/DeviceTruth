package io.devicetruth.core.capability;

import io.devicetruth.core.contract.Checks;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 通用工具调用上下文：身份、项目与 run 全部由服务器确定，模型不能填写或扩大。
 * 资产可空，因此无资产的知识问答同样可以携带明确身份与授权范围。
 * allowedTools 是“已注册 ∩ 服务器授权”的服务端结果，调用前必须命中。
 */
public record ToolContext(String ownerId, String projectId, UUID conversationId, UUID runId,
                          String selectedAssetId, Instant requestedAt, Instant asOf,
                          Set<String> allowedTools, UUID actorUserId, UUID workspaceId) {
    public ToolContext(String ownerId,String projectId,UUID conversationId,UUID runId,String selectedAssetId,Instant requestedAt,Instant asOf,Set<String> allowedTools) {
        this(ownerId,projectId,conversationId,runId,selectedAssetId,requestedAt,asOf,allowedTools,null,null);
    }
    public ToolContext {
        Checks.id(ownerId, "ownerId");
        Checks.id(projectId, "projectId");
        Checks.required(runId, "runId");
        if (selectedAssetId != null) Checks.id(selectedAssetId, "selectedAssetId");
        requestedAt = Checks.required(requestedAt, "requestedAt");
        if (asOf == null) asOf = requestedAt;
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
    }

    public boolean allows(String tool) { return allowedTools.contains(tool); }
}
