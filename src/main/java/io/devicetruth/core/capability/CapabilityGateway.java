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

public final class CapabilityGateway {
    private record Registration(Descriptor descriptor, ReadOnlyHandler handler) {}
    private final Map<String,Registration> registrations=new LinkedHashMap<>();

    public void register(Descriptor descriptor,ReadOnlyHandler handler) {
        id(descriptor.id(),"capabilityId"); required(handler,"handler");
        if(registrations.putIfAbsent(descriptor.id(),new Registration(descriptor,handler))!=null)
            throw new IllegalArgumentException("Capability already registered");
    }
    public List<Descriptor> catalog() { return registrations.values().stream().map(Registration::descriptor).toList(); }
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
    public static Descriptor descriptor(String id,String description,Set<String> required,Set<String> optional,boolean readOnly) {
        Map<String,Object> properties=new LinkedHashMap<>();
        java.util.stream.Stream.concat(required.stream(),optional.stream()).sorted()
                .forEach(key->properties.put(key,Map.of("type","string","pattern","^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")));
        return new Descriptor(id,description,required,optional,"*",readOnly,
                Map.of("type","object","properties",Map.copyOf(properties),"required",required.stream().sorted().toList(),"additionalProperties",false),
                "Observation{records:EvidenceRecord[],discovered:EvidenceMetadata[],rules:Rule[],attributes:Map<String,String>}");
    }
    public static CapabilityGateway standard(AssetRepository assets,EvidenceRepository evidence,RuleRepository rules) {
        var gateway=new CapabilityGateway();
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
