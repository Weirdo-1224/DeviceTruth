package io.devicetruth.skill;

import io.devicetruth.adapter.persistence.SkillInstallationRepository;
import io.devicetruth.adapter.persistence.SkillInstallationRepository.Installation;
import io.devicetruth.adapter.persistence.SkillInstallationRepository.Selection;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.ToolError;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.reasoning.GeneralReasoner.ActiveSkill;
import io.devicetruth.core.reasoning.GeneralReasoner.SkillReference;
import io.devicetruth.core.reasoning.GeneralReasoner.SkillSummary;
import io.devicetruth.core.runtime.TaskOrchestrator.SkillStep;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 服务器按归属安装 Skill，每个 run 只可激活一个版本。文档只能缩小工具范围。 */
public final class SkillRuntime {
    public static final String ACTIVATE = "activate_skill";
    public static final String READ_REFERENCE = "read_skill_reference";
    private static final ToolSpec ACTIVATE_SPEC = spec(ACTIVATE, "选取当前任务适用的已启用 Skill", "skillId");
    private static final ToolSpec READ_SPEC = spec(READ_REFERENCE, "按名称读取当前 Skill 的一篇参考资料", "name");

    public record SelectionView(String skillId, String version, List<String> devices, List<String> tasks) {}

    private final SkillRegistry registry;
    private final SkillContextLoader loader;
    private final SkillInstallationRepository repository;
    private final ServerIdentity identity;
    private final Set<String> serverTools;

    public SkillRuntime(SkillRegistry registry, SkillInstallationRepository repository, ServerIdentity identity,
                        Set<String> serverTools, List<String> defaults) {
        this.registry = registry;
        this.loader = new SkillContextLoader(registry);
        this.repository = repository;
        this.identity = identity;
        this.serverTools = Set.copyOf(serverTools);
        for (String configured : defaults) {
            String[] binding = configured.split("@", -1);
            if (binding.length != 2 || !binding[0].matches(SkillMetadata.NAME_PATTERN)
                    || !binding[1].matches(SkillMetadata.VERSION_PATTERN))
                throw new IllegalArgumentException("Invalid server Skill installation");
            repository.installDefault(identity, binding[0], binding[1], this.serverTools);
        }
    }

    public SelectionView selection(UUID runId) {
        Selection selected = repository.selection(identity, runId);
        return selected == null ? null : new SelectionView(selected.skillId(), selected.version(),
                selected.devices(), selected.tasks());
    }

    public void verifyActiveGrant(UUID runId) {
        Selection selected = repository.selection(identity, runId);
        if (selected != null && !repository.lockEnabledSelection(identity, runId)) throw unavailable();
    }

    public Session open(CapabilityGateway gateway, ToolContext context, long leaseVersion) {
        if (!identity.ownerId().equals(context.ownerId()) || !identity.projectId().equals(context.projectId()))
            throw new IllegalArgumentException("Skill scope must match server identity");
        return new Session(gateway, context, leaseVersion);
    }

    public final class Session {
        private final CapabilityGateway gateway;
        private final ToolContext base;
        private final long leaseVersion;
        private Selection selected;
        private SkillReference reference;

        private Session(CapabilityGateway gateway, ToolContext base, long leaseVersion) {
            this.gateway = gateway.copyToolCatalog();
            this.base = base;
            this.leaseVersion = leaseVersion;
            this.selected = repository.selection(identity, base.runId());
            this.gateway.registerTool(ACTIVATE_SPEC, (arguments, context) -> invoke(ACTIVATE,
                    arguments, () -> activate(arguments.get("skillId"))));
            this.gateway.registerTool(READ_SPEC, (arguments, context) -> invoke(READ_REFERENCE,
                    arguments, () -> readReference(arguments.get("name"))));
        }

        public CapabilityGateway gateway() { return gateway; }

        public SkillStep step() {
            var installed = installed();
            var allowed = new HashSet<>(base.allowedTools());
            ActiveSkill active = null;
            if (selected == null) {
                if (!installed.isEmpty()) allowed.add(ACTIVATE);
            } else {
                var metadata = requireSelected(installed);
                var instructions = loader.instructions(selected.skillId());
                if (!selected.documentDigest().equals(digest(metadata, instructions)))
                    throw SkillException.changed(selected.skillId());
                allowed.retainAll(selected.allowedTools());
                if (!metadata.references().isEmpty()) allowed.add(READ_REFERENCE);
                active = new ActiveSkill(summary(metadata), instructions.content(), metadata.evidence(),
                        metadata.risks(), metadata.citations(), metadata.references(), reference);
            }
            var context = new ToolContext(base.ownerId(), base.projectId(), base.conversationId(), base.runId(),
                    base.selectedAssetId(), base.requestedAt(), base.asOf(), allowed);
            var available = installed.keySet().stream().map(registry::require).map(SkillRuntime::summary).toList();
            return new SkillStep(context, available, active);
        }

        private void activate(Object value) {
            if (!(value instanceof String skillId) || !skillId.matches(SkillMetadata.NAME_PATTERN))
                throw new SkillException("INVALID_ARGUMENTS", "Skill id must be a catalog id");
            var installed = installed();
            var grant = installed.get(skillId);
            if (selected != null || grant == null)
                throw unavailable();
            var metadata = registry.require(skillId);
            var instructions = loader.instructions(skillId);
            var allowed = new HashSet<>(base.allowedTools());
            allowed.retainAll(grant.allowedTools());
            allowed.retainAll(serverTools);
            allowed.retainAll(metadata.tools());
            var candidate = new Selection(skillId, metadata.version(), metadata.devices(), metadata.tasks(),
                    Set.copyOf(allowed), digest(metadata, instructions));
            if (!repository.select(identity, base.runId(), leaseVersion, candidate))
                throw unavailable();
            selected = candidate;
        }

        private void readReference(Object value) {
            requireSelected(installed());
            if (!(value instanceof String name)) throw SkillException.referenceNotFound();
            String content = loader.readReference(selected.skillId(), name);
            String hash = digest(content);
            repository.pinReference(identity, base.runId(), leaseVersion, name, hash);
            if (!hash.equals(repository.referenceDigest(identity, base.runId(), name)))
                throw SkillException.changed(selected.skillId());
            reference = new SkillReference(name, content);
        }

        private SkillMetadata requireSelected(Map<String, Installation> installed) {
            if (selected == null) throw unavailable();
            var grant = installed.get(selected.skillId());
            if (grant == null || !grant.version().equals(selected.version())
                    || !grant.allowedTools().containsAll(selected.allowedTools())
                    || !serverTools.containsAll(selected.allowedTools())) throw unavailable();
            return registry.require(selected.skillId());
        }
    }

    private Map<String, Installation> installed() {
        var grants = new LinkedHashMap<String, Installation>();
        for (var grant : repository.enabled(identity)) {
            registry.find(grant.skillId()).filter(metadata -> metadata.version().equals(grant.version()))
                    .ifPresent(metadata -> grants.put(metadata.name(), grant));
        }
        return new java.util.TreeMap<>(grants);
    }

    private static SkillException unavailable() {
        return new SkillException("SKILL_UNAVAILABLE", "Skill is not enabled for this run");
    }

    private static SkillSummary summary(SkillMetadata metadata) {
        return new SkillSummary(metadata.name(), metadata.version(), metadata.description(), metadata.devices(),
                metadata.tasks());
    }

    private static String digest(SkillMetadata metadata, SkillInstructions instructions) {
        return digest(io.devicetruth.adapter.Json.write(metadata) + "\n" + instructions.content());
    }

    private static String digest(String content) {
        try {
            byte[] bytes = content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ToolOutcome invoke(String tool, Map<String, Object> arguments, Runnable action) {
        String requestId = UUID.randomUUID().toString();
        try {
            action.run();
            // 激活/阅读的效果下一轮经 activeSkill 上下文可见；这里回执调用参数作为最小可见确认
            Map<String, String> visible = new LinkedHashMap<>();
            if (arguments != null)
                for (var entry : arguments.entrySet())
                    if (entry.getValue() != null) visible.put(entry.getKey(), String.valueOf(entry.getValue()));
            return ToolOutcome.ok(requestId, tool, Map.of(), List.of(), "SIMULATED", visible);
        } catch (SkillException e) {
            return ToolOutcome.failed(requestId, tool, new ToolError(e.code(), "Skill request denied", false));
        }
    }

    private static ToolSpec spec(String name, String description, String argument) {
        return new ToolSpec(name, description, "skill", true, Map.of("type", "object",
                "properties", Map.of(argument, Map.of("type", "string")), "required", List.of(argument),
                "additionalProperties", false), "Skill context");
    }
}
