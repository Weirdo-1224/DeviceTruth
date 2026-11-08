package io.devicetruth.core;

import io.devicetruth.adapter.*;
import io.devicetruth.core.capability.*;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.decision.DecisionGuard;
import io.devicetruth.core.evidence.EvidenceManager;
import io.devicetruth.core.reasoning.*;
import io.devicetruth.core.reasoning.ActionProposal.*;
import io.devicetruth.core.runtime.TaskOrchestrator;
import io.devicetruth.core.runtime.TaskOrchestrator.RunReport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AcceptanceTest {
    static DiagnosisRequest request(String scenario) {
        return new DiagnosisRequest("CAR-"+scenario,"automotive-demo","COMM_TIMEOUT",Instant.parse("2026-11-03T04:00:00Z"));
    }
    static RunReport run(String scenario,Reasoner reasoner) throws Exception {
        var r=JsonRepositories.fromClasspath();return run(r,request(scenario),reasoner);
    }
    static RunReport run(JsonRepositories r,DiagnosisRequest task,Reasoner reasoner) {
        return new TaskOrchestrator(CapabilityGateway.standard(r,r,r),new EvidenceManager(),new DecisionGuard()).run(task,Budget.defaults(),reasoner);
    }
    @Test void casesABCDProduceEvidenceConstrainedOutcomes() throws Exception {
        var a=run("A",new ScriptedReasoner());
        assertThat(a.result().outcome()).isEqualTo(Outcome.RESOLVED);assertThat(a.result().appliedRuleIds()).containsExactly("DEMO-D-120A");
        assertThat(a.result().supportingEvidenceIds()).contains("CAR-A-FIELD-1");
        var b=run("B",new ScriptedReasoner());
        assertThat(b.result().outcome()).isEqualTo(Outcome.RESOLVED);assertThat(b.result().appliedRuleIds()).containsExactly("DEMO-D-120B");
        assertThat(b.result().supportingEvidenceIds()).contains("CAR-B-FIELD-1").doesNotContain("CAR-B-FACTORY");
        assertThat(b.state().hypotheses()).anySatisfy(h->assertThat(h.status()).isEqualTo(HypothesisStatus.INVALIDATED));
        assertThat(b.events()).anySatisfy(e->assertThat(e.type()).isEqualTo("HYPOTHESIS_INVALIDATED"));
        var c=run("C",new ScriptedReasoner());
        assertThat(c.result().outcome()).isEqualTo(Outcome.CONFLICT);assertThat(c.result().appliedRuleIds()).isEmpty();
        assertThat(c.result().supportingEvidenceIds()).containsExactly("CAR-C-FIELD-1","CAR-C-FIELD-2");
        assertThat(c.result().diagnosisSummary()).doesNotContain("培训诊断");
        var d=run("D",new ScriptedReasoner());
        assertThat(d.result().outcome()).isEqualTo(Outcome.NEED_EVIDENCE);assertThat(d.result().appliedRuleIds()).isEmpty();
        assertThat(d.result().missingEvidenceNeeds()).anySatisfy(n->assertThat(n.description()).contains("FIELD_CONFIRMATION"));
        for(var report:List.of(a,b,c,d)) {
            assertThat(report.result().runStatus()).isEqualTo(RunStatus.COMPLETED);
            assertThat(report.result().stepsUsed()).isLessThanOrEqualTo(12);
            assertThat(report.result().stateVersion()).isEqualTo(report.state().actionHistory().size());
            assertThat(report.result().dataNature()).isEqualTo("SIMULATED");
        }
    }
    @Test void caseEUnknownToolAndForgedEvidenceNeverResolve() throws Exception {
        var unknown=run("A",RuntimeTest.constant(new CallCapability("not.registered",Map.of(),"invalid tool",List.of())));
        assertThat(unknown.result().outcome()).isNotEqualTo(Outcome.RESOLVED);
        assertThat(unknown.events()).anySatisfy(e->assertThat(e.type()).isEqualTo("CAPABILITY_RESULT"));
        var scripted=new ScriptedReasoner();
        Reasoner forged=new Reasoner() {
            public String name(){return "scripted";}
            public ActionProposal propose(Context context) {
                var next=scripted.propose(context);
                return next instanceof ProposeDecision d?new ProposeDecision(d.candidateRuleId(),d.claimedFactIds(),List.of("FORGED-EVIDENCE"),List.of(),d.adviceLevel(),"forged"):next;
            }
        };
        var result=run("A",forged);
        assertThat(result.result().outcome()).isNotEqualTo(Outcome.RESOLVED);assertThat(result.result().appliedRuleIds()).isEmpty();
        assertThat(result.state().actionHistory()).anySatisfy(h->assertThat(h.result()).isEqualTo("INVALID_EVIDENCE_REFERENCE"));
    }
    @Test void invalidModelJsonIsFailedWithTwoAttemptsAndNoCapabilityExecution() throws Exception {
        var result=run("A",new QwenReasoner((system,input)->"not JSON"));
        assertThat(result.result().runStatus()).isEqualTo(RunStatus.FAILED);assertThat(result.result().outcome()).isNull();
        assertThat(result.result().stopReason()).isEqualTo("MODEL_OUTPUT_INVALID_AFTER_RETRY");
        assertThat(result.events().stream().filter(e->e.type().equals("MODEL_ATTEMPT"))).hasSize(2);
        assertThat(result.events()).noneMatch(e->e.type().equals("CAPABILITY_RESULT"));
    }
    @Test void scopedHistoryLeavesAnActionableNeedAfterOtherEvidenceIsFetched() throws Exception {
        var step=new java.util.concurrent.atomic.AtomicInteger();
        Reasoner replay=new Reasoner() {
            public String name() { return "offline-trace-replay"; }
            public ActionProposal propose(Context context) {
                int index=step.getAndIncrement();
                if(index==0) return new CallCapability("diagnosis.rules.query",Map.of("domain","automotive-demo","symptomCode","COMM_TIMEOUT"),"rules",List.of());
                if(index==1) return new CallCapability("asset.history.query",Map.of("assetId","CAR-B","componentId","CTRL-01"),"scoped history",List.of());
                if(index==2) return new CallCapability("evidence.search",Map.of("assetId","CAR-B"),"discover all",List.of());
                if(index==3) return new CallCapability("evidence.get",Map.of("assetId","CAR-B","evidenceId","CAR-B-FIELD-1"),"confirmation",List.of());
                if(index==5) return new CallCapability("evidence.get",Map.of("assetId","CAR-B","evidenceId","CAR-B-FACTORY"),"remaining record",List.of());
                if(index==6) {
                    var pending=context.state().evidenceNeeds().stream().filter(n->n.status()==NeedStatus.OPEN
                            &&n.description().contains("asset.history.query")).toList();
                    assertThat(pending).as("unfiltered history must remain actionable after an unrelated tool refresh").hasSize(1);
                    assertThat(pending.getFirst().description()).contains("assetId","componentId");
                    return new CallCapability("asset.history.query",Map.of("assetId","CAR-B"),"complete history",List.of(pending.getFirst().needId()));
                }
                var fact=context.state().facts().stream().filter(f->f.status()==FactStatus.SUPPORTED).findFirst().orElseThrow();
                return new ProposeDecision("DEMO-D-120B",List.of(fact.factId()),fact.evidenceIds(),List.of(),"SIMULATION_ONLY","current supported confirmation");
            }
        };
        var report=run("B",replay);
        assertThat(report.result().outcome()).isEqualTo(Outcome.RESOLVED);
        assertThat(report.result().appliedRuleIds()).containsExactly("DEMO-D-120B");
        assertThat(report.result().stepsUsed()).isEqualTo(8);
        assertThat(report.state().actionHistory()).noneMatch(h->h.result().contains("unknown targetEvidenceNeedIds"));
    }

    @Test void provisionalRuleAndBaselineHypothesisAreInvalidatedWhenHistoryArrives() throws Exception {
        var scripted=new ScriptedReasoner();
        Reasoner early=new Reasoner() {
            public String name(){return "scripted";}
            public ActionProposal propose(Context c) {
                boolean attempted=c.state().actionHistory().stream().anyMatch(h->h.proposal() instanceof ProposeDecision);
                if(!attempted&&!c.state().hypotheses().isEmpty()) {
                    var fact=c.state().facts().getFirst();var rule=c.state().knownRules().stream().filter(r->r.conditions().stream().allMatch(condition->condition.matches(fact))).findFirst().orElseThrow();
                    return new ProposeDecision(rule.ruleId(),List.of(fact.factId()),fact.evidenceIds(),List.of(),rule.allowedAdviceLevel(),"provisional baseline");
                }
                return scripted.propose(c);
            }
        };
        var report=run("B",early);
        assertThat(report.result().outcome()).isEqualTo(Outcome.RESOLVED);assertThat(report.result().appliedRuleIds()).containsExactly("DEMO-D-120B");
        assertThat(report.events()).anySatisfy(e->assertThat(e.type()).isEqualTo("RULE_SELECTION_INVALIDATED"));
        assertThat(report.state().hypotheses()).anySatisfy(h->assertThat(h.status()).isEqualTo(HypothesisStatus.INVALIDATED));
    }
}
