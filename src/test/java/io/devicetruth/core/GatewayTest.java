package io.devicetruth.core;

import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.Observation;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.reasoning.ActionProposal.CallCapability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GatewayTest {
    private final DiagnosisRequest task=new DiagnosisRequest("CAR-B","automotive-demo","COMM_TIMEOUT",Instant.parse("2026-11-03T04:00:00Z"));
    private CapabilityGateway gateway() throws Exception {
        var repository=JsonRepositories.fromClasspath(); return CapabilityGateway.standard(repository,repository,repository);
    }
    @Test void fixturesAndFiveReadOnlyCapabilitiesReturnRawRecordsAndRules() throws Exception {
        var g=gateway();
        assertThat(g.catalog()).hasSize(5).allSatisfy(d->{ assertThat(d.readOnly()).isTrue(); assertThat(d.inputSchema()).containsKey("properties"); assertThat(d.outputSchema()).isNotBlank(); });
        var profile=g.execute(task,call("asset.profile.get",Map.of("assetId",task.assetId())));
        assertThat(profile.success()).isTrue(); assertThat(profile.requestId()).isNotBlank();
        assertThat(profile.data().records()).singleElement().satisfies(e->{assertThat(e.sourceType()).isEqualTo("FACTORY");assertThat(e.payload().get("value")).isEqualTo("D-120A");});
        var history=g.execute(task,call("asset.history.query",Map.of("assetId",task.assetId())));
        assertThat(history.data().records()).singleElement().satisfies(e->assertThat(e.sourceType()).isEqualTo("REPLACEMENT"));
        var discovered=g.execute(task,call("evidence.search",Map.of("assetId",task.assetId())));
        assertThat(discovered.data().records()).isEmpty(); assertThat(discovered.data().discovered()).hasSize(3);
        var record=g.execute(task,call("evidence.get",Map.of("assetId",task.assetId(),"evidenceId","CAR-B-FIELD-1")));
        assertThat(record.data().records()).singleElement().satisfies(e->assertThat(e.sourceType()).isEqualTo("FIELD_CONFIRMATION"));
        var rules=g.execute(task,call("diagnosis.rules.query",Map.of("domain",task.domain(),"symptomCode",task.symptomCode())));
        assertThat(rules.data().rules()).hasSize(2).allSatisfy(r->assertThat(r.requiredEvidence()).isNotEmpty());
        assertThat(profile.data().rules()).isEmpty();
        for(String caseId:List.of("A","B","C","D")) {
            var req=new DiagnosisRequest("CAR-"+caseId,task.domain(),task.symptomCode(),task.asOf());
            assertThat(g.execute(req,call("asset.profile.get",Map.of("assetId",req.assetId()))).success()).isTrue();
        }
    }
    @Test void unknownCrossAssetUnknownEvidenceTraversalAndWrongParameterAreRejected() throws Exception {
        var g=gateway();
        var calls=List.of(call("unknown.capability",Map.of()),call("asset.profile.get",Map.of("assetId","CAR-A")),
                call("evidence.get",Map.of("assetId","CAR-B","evidenceId","CAR-A-FIELD-1")),
                call("evidence.get",Map.of("assetId","CAR-B","evidenceId","FORGED")),
                call("asset.profile.get",Map.of("assetId","../secret")),call("asset.profile.get",Map.of()),
                call("asset.profile.get",Map.of("assetId","CAR-B","path","fixture.json")),
                call("diagnosis.rules.query",Map.of("domain","other-domain","symptomCode","COMM_TIMEOUT")));
        for(var c:calls) assertThat(g.execute(task,c)).satisfies(r->{assertThat(r.success()).isFalse();assertThat(r.data()).isNull();assertThat(r.error().code()).isNotBlank();});
    }
    @Test void nonReadOnlyHandlerNeverExecutesAndAdapterFailureIsStructured() {
        var g=new CapabilityGateway(); var executed=new AtomicBoolean();
        g.register(CapabilityGateway.descriptor("device.write","forbidden",Set.of(),Set.of(),false),a->{executed.set(true);return Observation.records(List.of());});
        assertThat(g.execute(task,call("device.write",Map.of())).error().code()).isEqualTo("NOT_READ_ONLY");
        assertThat(executed).isFalse();
        g.register(CapabilityGateway.descriptor("tool.fail","failure",Set.of(),Set.of(),true),a->{throw new IllegalStateException("private detail");});
        var result=g.execute(task,call("tool.fail",Map.of()));
        assertThat(result.error().retryable()).isTrue(); assertThat(result.error().message()).doesNotContain("private detail");
    }
    private CallCapability call(String id,Map<String,String> args) {return new CallCapability(id,args,"contract test",List.of());}
}
