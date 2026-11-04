package io.devicetruth.core;

import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.decision.DecisionGuard;
import io.devicetruth.core.reasoning.ActionProposal.ProposeDecision;
import io.devicetruth.core.reasoning.ScriptedReasoner;
import io.devicetruth.core.runtime.TaskOrchestrator.RunReport;
import io.devicetruth.core.state.DiagnosisState;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DecisionGuardTest {
    private final DecisionGuard guard=new DecisionGuard();
    private Map<String,EvidenceRecord> evidence(RunReport report) {return report.evidence().stream().collect(Collectors.toMap(EvidenceRecord::evidenceId,Function.identity()));}
    private ProposeDecision decision(RunReport report) {return (ProposeDecision)report.state().actionHistory().getLast().proposal();}
    @Test void validProofPassesWhileWrongRulesReferencesAdviceAndInvalidatedHypothesesFail() throws Exception {
        var report=AcceptanceTest.run("B",new ScriptedReasoner());var s=report.state();var records=evidence(report);var d=decision(report);
        assertThat(guard.evaluate(s,records,d).allowed()).isTrue();
        List<ProposeDecision> bad=List.of(
                new ProposeDecision("UNKNOWN",d.claimedFactIds(),d.claimedEvidenceIds(),List.of(),d.adviceLevel(),"unknown rule"),
                new ProposeDecision(d.candidateRuleId(),List.of("F-FORGED"),d.claimedEvidenceIds(),List.of(),d.adviceLevel(),"bad fact"),
                new ProposeDecision(d.candidateRuleId(),d.claimedFactIds(),List.of("FORGED"),List.of(),d.adviceLevel(),"bad evidence"),
                new ProposeDecision(d.candidateRuleId(),List.of("F:CAR-B-FACTORY"),List.of("CAR-B-FACTORY"),List.of(),d.adviceLevel(),"old fact"),
                new ProposeDecision(d.candidateRuleId(),d.claimedFactIds(),d.claimedEvidenceIds(),List.of("H-1"),d.adviceLevel(),"invalidated hypothesis"),
                new ProposeDecision(d.candidateRuleId(),d.claimedFactIds(),d.claimedEvidenceIds(),List.of(),"PHYSICAL_CONTROL","unsafe level"));
        for(var action:bad) assertThat(guard.evaluate(s,records,action).allowed()).isFalse();
        var field=records.get("CAR-B-FIELD-1");var foreign=new EvidenceRecord(field.evidenceId(),"OTHER-ASSET",field.sourceType(),field.sourceRef(),field.recordedAt(),field.effectiveAt(),field.observedAt(),field.validTo(),field.authority(),field.payload(),field.integrityStatus());
        var corrupt=new java.util.HashMap<>(records);corrupt.put(field.evidenceId(),foreign);
        assertThat(guard.evaluate(s,corrupt,d).reason()).isEqualTo("INVALID_EVIDENCE_REFERENCE");
    }
    @Test void missingConfirmationWrongDomainAndHumanRuleHaveExplicitOutcomes() throws Exception {
        var missing=AcceptanceTest.run("D",new ScriptedReasoner());
        assertThat(guard.evaluate(missing.state(),evidence(missing),decision(missing)).terminalOutcome()).isEqualTo(Outcome.NEED_EVIDENCE);
        var report=AcceptanceTest.run("A",new ScriptedReasoner());var original=report.state().knownRules().getFirst();
        var human=new Rule(original.ruleId(),original.domain(),original.symptomCode(),original.conditions(),original.requiredEvidence(),original.allowedAdviceLevel(),original.simulatedAdvice(),true);
        assertThat(guard.evaluate(withRules(report.state(),List.of(human)),evidence(report),decision(report)).terminalOutcome()).isEqualTo(Outcome.ESCALATE);
        var wrongDomain=new Rule(original.ruleId(),"wrong-domain",original.symptomCode(),original.conditions(),original.requiredEvidence(),original.allowedAdviceLevel(),original.simulatedAdvice(),false);
        assertThat(guard.evaluate(withRules(report.state(),List.of(wrongDomain)),evidence(report),decision(report)).reason()).isEqualTo("RULE_OUTSIDE_TASK_SCOPE");
    }
    private DiagnosisState withRules(DiagnosisState s,List<Rule> rules) {
        return new DiagnosisState(s.taskId(),s.version(),s.task(),s.facts(),s.hypotheses(),s.evidenceNeeds(),s.selectedRuleId(),s.selectedRuleFactIds(),s.actionHistory(),s.budget(),s.status(),rules,s.discoveredEvidence());
    }
}
