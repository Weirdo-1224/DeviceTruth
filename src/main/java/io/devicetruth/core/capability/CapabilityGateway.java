package io.devicetruth.core.capability;

import io.devicetruth.core.capability.AgentToolTypes.*;
import io.devicetruth.core.capability.CapabilityTypes.*;
import io.devicetruth.core.capability.Repositories.*;
import io.devicetruth.core.contract.Models.DiagnosisRequest;
import io.devicetruth.core.reasoning.ActionProposal.CallCapability;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static io.devicetruth.core.contract.Checks.*;

/** 能力网关：所有数据读取的唯一入口。只做注册、鉴权与参数校验，本身不接触数据。 */
public final class CapabilityGateway {
    private record Registration(Descriptor descriptor, ReadOnlyHandler handler) {}
    private record ToolRegistration(ToolSpec spec, AgentToolHandler handler) {}
    private final Map<String,Registration> registrations=new LinkedHashMap<>();
    /** 通用 Agent 工具（允许嵌套参数）：与 legacy 单值能力分开注册，互不共享参数校验规则。 */
    private final Map<String,ToolRegistration> tools=new LinkedHashMap<>();

    /** 注册一个只读能力；ID 重复即拒绝。 */
    public void register(Descriptor descriptor,ReadOnlyHandler handler) {
        id(descriptor.id(),"capabilityId"); required(handler,"handler");
        if(registrations.putIfAbsent(descriptor.id(),new Registration(descriptor,handler))!=null)
            throw new IllegalArgumentException("Capability already registered");
    }
    /** 能力目录：推理器通过它了解可调用的能力及其参数模式。 */
    public List<Descriptor> catalog() { return registrations.values().stream().map(Registration::descriptor).toList(); }

    /**
     * 注册一个通用只读工具；名称重复即拒绝。
     * 注册动作本身就是服务器授权：模型只能看到并调用这里登记过的工具。
     */
    public void registerTool(ToolSpec spec, AgentToolHandler handler) {
        required(spec, "spec"); required(handler, "handler");
        if (tools.putIfAbsent(spec.name(), new ToolRegistration(spec, handler)) != null)
            throw new IllegalArgumentException("Tool already registered");
    }

    /** 为当前 run 增加受控的会话工具；共享网关注册不受影响。 */
    public CapabilityGateway copyToolCatalog() {
        var copy = new CapabilityGateway();
        copy.tools.putAll(tools);
        return copy;
    }

    /** 当前上下文可见的工具目录：已注册 ∩ 服务器授权（ToolContext.allowedTools）。 */
    public List<ToolSpec> toolCatalog(ToolContext context) {
        return tools.values().stream().map(ToolRegistration::spec)
                .filter(spec -> spec.readOnly() && (context == null || context.allows(spec.name()))).toList();
    }

    /** 已注册工具名（服务端授权的来源），用于构造 ToolContext.allowedTools。 */
    public Set<String> toolNames() { return Collections.unmodifiableSet(new LinkedHashSet<>(tools.keySet())); }

    /**
     * 执行一次通用工具调用：未注册、非只读、未授权、顶层参数缺失或未知一律拒绝，绝不执行 handler。
     * 嵌套参数（如 filters）由工具自己的强类型解析拒绝，错误统一映射为 ToolError。
     */
    public ToolOutcome executeTool(ToolContext context, String toolName, Map<String, Object> arguments) {
        String requestId = UUID.randomUUID().toString();
        try {
            if (context == null) throw new QueryException("INVALID_ARGUMENTS", "Tool context is required");
            var entry = tools.get(toolName);
            if (entry == null) throw new QueryException("UNKNOWN_TOOL", "Tool is not registered");
            var spec = entry.spec();
            if (!spec.readOnly()) throw new QueryException("NOT_READ_ONLY", "Only read-only tools may execute");
            if (!context.allows(spec.name()))
                throw new QueryException("TOOL_NOT_ALLOWED", "Tool outside server-authorized scope");
            Map<String, Object> args = arguments == null ? Map.of() : arguments;
            var allowed = spec.allowedParameters();
            for (String key : args.keySet())
                if (!allowed.contains(key)) throw new QueryException("INVALID_ARGUMENTS", "Unknown parameter");
            for (String key : spec.requiredParameters())
                if (args.get(key) == null) throw new QueryException("INVALID_ARGUMENTS", "Missing required parameter");
            var outcome = entry.handler().execute(Map.copyOf(args), context);
            if (outcome == null || !spec.name().equals(outcome.toolName()))
                throw new QueryException("INVALID_TOOL_OUTPUT", "Tool returned an inconsistent outcome");
            return outcome;
        } catch (QueryException e) {
            return ToolOutcome.failed(requestId, safeToolName(toolName), new ToolError(e.code(), e.getMessage(), false));
        } catch (Exception e) {
            return ToolOutcome.failed(requestId, safeToolName(toolName),
                    new ToolError("TOOL_FAILURE", "Read-only tool failed", true));
        }
    }

    /** 失败结果的工具名也必须是合法标识；模型给出的非法名称不能反过来破坏审计记录。 */
    private static String safeToolName(String toolName) {
        try { return id(toolName, "toolName"); } catch (RuntimeException e) { return "unknown.tool"; }
    }

    /** 执行一次能力调用。依次校验：已注册、只读、任务域内、参数合法、无越权（跨资产/域/症状），
     *  最后检查工具返回的记录未超出任务资产范围。任何失败都包装为 ToolError 而非抛异常。 */
    public CapabilityResult execute(DiagnosisRequest scope,CallCapability call) {
        String requestId=UUID.randomUUID().toString();
        try {
            var entry=registrations.get(call.capabilityId());
            if(entry==null) throw new QueryException("UNKNOWN_CAPABILITY","Capability is not registered");
            var descriptor=entry.descriptor(); var args=call.arguments();
            if(!descriptor.readOnly()) throw new QueryException("NOT_READ_ONLY","Only read-only capabilities may execute");
            if(!descriptor.domain().equals("*")&&!descriptor.domain().equals(scope.domain()))
                throw new QueryException("DOMAIN_DENIED","Capability domain outside task scope");
            if(!args.keySet().containsAll(descriptor.requiredParameters())) throw new QueryException("INVALID_ARGUMENTS","Missing required parameters");
            for(var key:args.keySet()) {
                if(!descriptor.requiredParameters().contains(key)&&!descriptor.optionalParameters().contains(key))
                    throw new QueryException("INVALID_ARGUMENTS","Unknown parameter: "+key);
                try { id(args.get(key),key); } catch(IllegalArgumentException e) { throw new QueryException("INVALID_ARGUMENTS","Invalid parameter: "+key); }
            }
            if(args.containsKey("assetId")&&!scope.assetId().equals(args.get("assetId"))) throw new QueryException("ASSET_DENIED","Cross-asset access denied");
            if(args.containsKey("domain")&&!scope.domain().equals(args.get("domain"))) throw new QueryException("DOMAIN_DENIED","Cross-domain access denied");
            if(args.containsKey("symptomCode")&&!scope.symptomCode().equals(args.get("symptomCode"))) throw new QueryException("SYMPTOM_DENIED","Symptom outside task scope");
            var data=entry.handler().execute(args);
            if(data.records().stream().anyMatch(e->!e.assetId().equals(scope.assetId()))||
                    data.discovered().stream().anyMatch(e->!e.assetId().equals(scope.assetId())))
                throw new QueryException("INVALID_TOOL_OUTPUT","Tool returned out-of-scope records");
            return new CapabilityResult(true,requestId,call.capabilityId(),"repository:"+call.capabilityId(),data,null);
        } catch(QueryException e) {
            return new CapabilityResult(false,requestId,call.capabilityId(),"gateway",null,new ToolError(e.code(),e.getMessage(),false));
        } catch(Exception e) {
            return new CapabilityResult(false,requestId,call.capabilityId(),"gateway",null,new ToolError("TOOL_FAILURE","Read-only adapter failed",true));
        }
    }
    /** 按 JSON Schema 风格生成能力的输入参数描述（供协议提示词与校验共用）。 */
    public static Descriptor descriptor(String id,String description,Set<String> required,Set<String> optional,boolean readOnly) {
        Map<String,Object> properties=new LinkedHashMap<>();
        java.util.stream.Stream.concat(required.stream(),optional.stream()).sorted()
                .forEach(key->properties.put(key,Map.of("type","string","pattern","^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")));
        return new Descriptor(id,description,required,optional,"*",readOnly,
                Map.of("type","object","properties",Map.copyOf(properties),"required",required.stream().sorted().toList(),"additionalProperties",false),
                "Observation{records:EvidenceRecord[],discovered:EvidenceMetadata[],rules:Rule[],attributes:Map<String,String>}");
    }
    /** 标准能力集：5 个只读能力，覆盖查规则、读档案、查历史、搜证据、取单条证据。 */
    public static CapabilityGateway standard(AssetRepository assets,EvidenceRepository evidence,RuleRepository rules) {
        var gateway=new CapabilityGateway();
        // 出厂原始记录（FACTORY 来源）
        gateway.register(descriptor("asset.profile.get","Read original factory records",Set.of("assetId"),Set.of(),true),
                a->Observation.records(assets.profile(a.get("assetId"))));
        gateway.register(descriptor("asset.history.query","Read replacement events",Set.of("assetId"),Set.of("componentId"),true),
                a->Observation.records(assets.history(a.get("assetId"),a.get("componentId"))));
        gateway.register(descriptor("evidence.search","Discover record IDs and metadata for this asset",Set.of("assetId"),Set.of("componentId","sourceType"),true),
                a->new Observation(null,evidence.search(a.get("assetId"),a.get("componentId"),a.get("sourceType")),null,null));
        gateway.register(descriptor("evidence.get","Read one raw evidence record",Set.of("assetId","evidenceId"),Set.of(),true),
                a->Observation.records(List.of(evidence.get(a.get("assetId"),a.get("evidenceId")))));
        gateway.register(descriptor("diagnosis.rules.query","Read candidate rules and required evidence; does not decide applicability",Set.of("domain","symptomCode"),Set.of(),true),
                a->new Observation(null,null,rules.query(a.get("domain"),a.get("symptomCode")),null));
        return gateway;
    }
}
