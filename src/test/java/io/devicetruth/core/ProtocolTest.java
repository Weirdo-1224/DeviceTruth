package io.devicetruth.core;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.ActionProposal.*;
import io.devicetruth.core.state.DiagnosisState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProtocolTest {
    @Test void allFiveActionsRoundTrip() throws Exception {
        List<ActionProposal> actions = List.of(
                new CallCapability("asset.profile.get",Map.of("assetId","fixture"),"read",List.of()),
                new ProposeHypothesis("tentative",List.of("F-1"),List.of("E-1")),
                new ProposeDecision("R-1",List.of("F-1"),List.of("E-1"),List.of(),"SIMULATION_ONLY","check"),
                new RequestInformation("missing",List.of("confirmation")), new Escalate("human"));
        for (var action : actions) {
            assertThat(Json.MAPPER.readValue(Json.MAPPER.writerFor(ActionProposal.class).writeValueAsString(action),ActionProposal.class)).isEqualTo(action);
        }
    }
    @Test void unknownTypeFieldsAndInvalidRequiredValuesAreRejected() {
        for(String json:List.of("{\"type\":\"WRITE_DEVICE\"}","{\"type\":\"ESCALATE\",\"reason\":\"\"}",
                "{\"type\":\"ESCALATE\",\"reason\":\"x\",\"facts\":[]}",
                "{\"type\":\"CALL_CAPABILITY\",\"capabilityId\":\"../file\",\"arguments\":{},\"purpose\":\"x\"}",
                "{\"type\":\"PROPOSE_HYPOTHESIS\",\"statement\":\"unfounded\"}"))
            assertThatThrownBy(()->Json.MAPPER.readValue(json,ActionProposal.class)).isInstanceOf(Exception.class);
    }
    @Test void stateStartsAtVersionZeroAndCollectionsCannotBeMutated() throws Exception {
        var request=new DiagnosisRequest("fixture","test-domain","TIMEOUT",Instant.parse("2026-11-03T04:00:00Z"));
        var state=DiagnosisState.initial("task-1",request,Budget.defaults());
        assertThat(state.version()).isZero(); assertThat(state.status()).isEqualTo(RunStatus.ACTIVE);
        assertThat(state.budget().maxSteps()).isEqualTo(12); assertThat(state.actionHistory()).isEmpty();
        assertThatThrownBy(()->state.facts().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(Json.MAPPER.readValue(Json.write(state),DiagnosisState.class)).isEqualTo(state);
        assertThatThrownBy(()->new Budget(0,2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DiagnosisRequest("../fixture","domain","CODE",request.asOf())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void evidenceRejectsInvalidTimes() {
        var now=Instant.parse("2026-11-03T04:00:00Z");
        assertThatThrownBy(()->new EvidenceRecord("E-1","fixture","FIELD_CONFIRMATION","simulated:1",now,
                now.plusSeconds(1),now,null,1,Map.of("subject","part","predicate","version","value","x"),IntegrityStatus.VERIFIED))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void numericArgumentsDuplicateKeysAndNumericEnumsCannotBypassStrictTypes() {
        for(String json:List.of(
                "{\"type\":\"CALL_CAPABILITY\",\"capabilityId\":\"asset.profile.get\",\"arguments\":{\"assetId\":123},\"purpose\":\"read\"}",
                "{\"type\":\"CALL_CAPABILITY\",\"capabilityId\":\"asset.profile.get\",\"arguments\":{\"assetId\":true},\"purpose\":\"read\"}",
                "{\"type\":\"ESCALATE\",\"reason\":\"first\",\"reason\":\"second\"}"))
            assertThatThrownBy(()->Json.MAPPER.readValue(json,ActionProposal.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(()->Json.MAPPER.readValue("0",RunStatus.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(()->Json.MAPPER.readValue("{\"maxSteps\":3.1,\"maxRepeatedActions\":2}",Budget.class)).isInstanceOf(Exception.class);
    }
}
