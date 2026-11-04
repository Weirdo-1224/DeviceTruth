package io.devicetruth.adapter;

import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.state.DiagnosisState;
import io.devicetruth.core.runtime.TaskOrchestrator.ReasonerFailure;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class QwenReasonerTest {
    private Reasoner.Context context() {
        return new Reasoner.Context(DiagnosisState.initial("task-1",new DiagnosisRequest("fixture","generic","TIMEOUT",Instant.parse("2026-11-03T04:00:00Z")),Budget.defaults()),List.of(),List.of());
    }
    @Test void malformedOutputRetriesExactlyOnceAndUsesCurrentStateProjection() {
        AtomicInteger count=new AtomicInteger();
        var reasoner=new QwenReasoner((system,input)->{
            assertThat(input).contains("\"version\":0","\"capabilities\":[]","\"evidence\":[]");
            assertThat(system).doesNotContain("CAR-B","D-120A","DEMO-D-120B");
            return count.incrementAndGet()==1?"not-json":"{\"type\":\"ESCALATE\",\"reason\":\"human review\"}";
        });
        assertThat(reasoner.propose(context())).isInstanceOf(ActionProposal.Escalate.class);
        assertThat(count).hasValue(2); assertThat(reasoner.drainAttempts()).extracting(Reasoner.Attempt::outcome).containsExactly("INVALID_JSON_OR_SCHEMA","VALID_PROPOSAL");
    }
    @Test void persistentInvalidJsonAndTransportFailureAreTechnicalFailures() {
        AtomicInteger count=new AtomicInteger();var invalid=new QwenReasoner((system,input)->{count.incrementAndGet();return "{}";});
        assertThatThrownBy(()->invalid.propose(context())).isInstanceOf(ReasonerFailure.class).hasMessage("MODEL_OUTPUT_INVALID_AFTER_RETRY");
        assertThat(count).hasValue(2);
        var transport=new QwenReasoner((system,input)->{throw new RuntimeException("private token detail");});
        assertThatThrownBy(()->transport.propose(context())).hasMessage("MODEL_TRANSPORT_OR_TIMEOUT");
    }
}
