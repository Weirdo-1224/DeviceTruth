package io.devicetruth.core;

import io.devicetruth.adapter.*;
import io.devicetruth.core.capability.*;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.decision.DecisionGuard;
import io.devicetruth.core.evidence.EvidenceManager;
import io.devicetruth.core.reasoning.*;
import io.devicetruth.core.reasoning.ActionProposal.*;
import io.devicetruth.core.runtime.TaskOrchestrator;
import io.devicetruth.core.state.StateManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RuntimeTest {
    private final DiagnosisRequest request=new DiagnosisRequest("CAR-A","automotive-demo","COMM_TIMEOUT",Instant.parse("2026-11-03T04:00:00Z"));
    private TaskOrchestrator orchestrator() throws Exception {
        var r=JsonRepositories.fromClasspath(); return new TaskOrchestrator(CapabilityGateway.standard(r,r,r),new EvidenceManager(),new DecisionGuard());
    }
    @Test void scriptedLoopCommitsBeforeNextReasoningAndWritesAllReports(@TempDir Path output) throws Exception {
        var versions=new ArrayList<Long>();var scripted=new ScriptedReasoner();
        Reasoner recording=new Reasoner() {
            public String name(){return "scripted";}
            public ActionProposal propose(Context c){versions.add(c.state().version());return scripted.propose(c);}
        };
        var report=orchestrator().run(request,Budget.defaults(),recording);
        assertThat(report.state().status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(report.result().stateVersion()).isEqualTo(report.result().stepsUsed());
        for(int i=0;i<versions.size();i++) assertThat(versions.get(i)).isEqualTo((long)i);
        assertThat(report.state().actionHistory()).anySatisfy(h->{assertThat(h.proposal()).isInstanceOf(CallCapability.class);assertThat(((CallCapability)h.proposal()).capabilityId()).isEqualTo("evidence.get");});
        var directory=new RunReportWriter().write(output,report);
        for(String name:List.of("result.json","final_state.json","events.jsonl","evidence.json")) assertThat(directory.resolve(name)).isRegularFile();
        for(String line:Files.readAllLines(directory.resolve("events.jsonl"))) assertThat(Json.MAPPER.readTree(line).get("sequence").asInt()).isPositive();
        assertThat(Json.MAPPER.readTree(directory.resolve("result.json").toFile()).get("reasoner").asText()).isEqualTo("scripted");
        assertThat(report.events()).anySatisfy(e->assertThat(e.type()).isEqualTo("GUARD_RESULT"));
    }
    @Test void requestEscalateInvalidDependenciesBudgetAndRepeatedToolsAreBounded() throws Exception {
        assertThat(orchestrator().run(request,Budget.defaults(),constant(new RequestInformation("need input",List.of("confirmation")))).result().outcome()).isEqualTo(Outcome.NEED_EVIDENCE);
        assertThat(orchestrator().run(request,Budget.defaults(),constant(new Escalate("human review"))).result().outcome()).isEqualTo(Outcome.ESCALATE);
        var invalid=orchestrator().run(request,new Budget(2,2),constant(new ProposeHypothesis("untrusted",List.of("FORGED"),List.of())));
        assertThat(invalid.state().hypotheses()).isEmpty(); assertThat(invalid.result().stopReason()).isEqualTo("EXECUTION_BUDGET_EXHAUSTED");
        var repeated=orchestrator().run(request,Budget.defaults(),constant(new CallCapability("missing.tool",Map.of(),"retry",List.of())));
        assertThat(repeated.result().outcome()).isEqualTo(Outcome.NEED_EVIDENCE); assertThat(repeated.result().stepsUsed()).isEqualTo(3);
        assertThat(repeated.events().stream().filter(e->e.type().equals("CAPABILITY_RESULT"))).hasSize(2);
    }
    @Test void optimisticCommitsRejectStaleVersionAndSnapshotsAreIndependent() {
        var manager=new StateManager(request,Budget.defaults());var original=manager.snapshot();
        manager.commit(0,new Escalate("test"),null,List.of(),List.of(),List.of(),null,List.of(),RunStatus.ACTIVE,"test",Map.of());
        assertThat(original.version()).isZero(); assertThat(original.actionHistory()).isEmpty(); assertThat(manager.snapshot().version()).isEqualTo(1);
        assertThatThrownBy(()->manager.commit(0,new Escalate("stale"),null,List.of(),List.of(),List.of(),null,List.of(),RunStatus.ACTIVE,"test",Map.of())).isInstanceOf(IllegalStateException.class);
    }
    @Test void repeatedAdapterFailureEscalatesWithoutUnboundedRetries() {
        var gateway=new CapabilityGateway();
        gateway.register(CapabilityGateway.descriptor("adapter.failure","simulated failure",java.util.Set.of(),java.util.Set.of(),true),a->{throw new IllegalStateException("adapter unavailable");});
        var report=new TaskOrchestrator(gateway,new EvidenceManager(),new DecisionGuard()).run(request,Budget.defaults(),
                constant(new CallCapability("adapter.failure",Map.of(),"try read-only adapter",List.of())));
        assertThat(report.result().outcome()).isEqualTo(Outcome.ESCALATE);assertThat(report.result().stopReason()).isEqualTo("TOOL_FAILURE_NO_PROGRESS");
        assertThat(report.events().stream().filter(e->e.type().equals("CAPABILITY_RESULT"))).hasSize(2);
        assertThat(report.result().stepsUsed()).isEqualTo(3);
    }
    static Reasoner constant(ActionProposal action) {
        return new Reasoner(){public String name(){return "scripted";}public ActionProposal propose(Context c){return action;}};
    }
}
