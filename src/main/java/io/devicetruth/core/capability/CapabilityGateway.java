package io.devicetruth.core.capability;

import io.devicetruth.core.capability.CapabilityTypes.*;
import io.devicetruth.core.capability.Repositories.*;
import io.devicetruth.core.contract.Models.DiagnosisRequest;
import io.devicetruth.core.reasoning.ActionProposal.CallCapability;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static io.devicetruth.core.contract.Checks.*;

/** 能力网关：所有数据读取的唯一入口。只做注册、鉴权与参数校验，本身不接触数据。 */
public final class CapabilityGateway {
    private record Registration(Descriptor descriptor, ReadOnlyHandler handler) {}
    private final Map<String,Registration> registrations=new LinkedHashMap<>();

    /** 注册一个只读能力；ID 重复即拒绝。 */
    public void register(Descriptor descriptor,ReadOnlyHandler handler) {
        id(descriptor.id(),"capabilityId"); required(handler,"handler");
        if(registrations.putIfAbsent(descriptor.id(),new Registration(descriptor,handler))!=null)
            throw new IllegalArgumentException("Capability already registered");
    }
    /** 能力目录：推理器通过它了解可调用的能力及其参数模式。 */
    public List<Descriptor> catalog() { return registrations.values().stream().map(Registration::descriptor).toList(); }
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
