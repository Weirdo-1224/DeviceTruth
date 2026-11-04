package io.devicetruth.core;

import io.devicetruth.adapter.*;
import io.devicetruth.adapter.JsonRepositories.*;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.reasoning.*;
import io.devicetruth.core.reasoning.ActionProposal.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ExtensibilityTest {
    private final RulePack automotiveRules=readRules();
    private static RulePack readRules() {
        try {return JsonRepositories.readResource("skills/automotive-demo/rules.json",RulePack.class);}
        catch(Exception e) {throw new IllegalStateException(e);}
    }
    @Test void editingOnlyFixtureJsonChangesVersionMissingAndConflictResults(@TempDir Path directory) throws Exception {
        var original=JsonRepositories.readResource("datasets/fixtures/b/asset.json",Dataset.class);
        var changed=new ArrayList<>(original.records());
        var field=changed.getLast();changed.set(changed.size()-1,withValue(field,"D-120A"));
        assertThat(runFile(directory,original.assetId(),changed).result().appliedRuleIds()).containsExactly("DEMO-D-120A");
        changed.removeLast();
        assertThat(runFile(directory,original.assetId(),changed).result().outcome()).isEqualTo(Outcome.NEED_EVIDENCE);
        changed.add(field);
        var conflicting=new EvidenceRecord("EXTRA-CURRENT-RECORD",field.assetId(),field.sourceType(),"simulated:independent-check",
                field.recordedAt(),field.effectiveAt(),field.observedAt(),field.validTo(),field.authority(),
                Map.of("subject",field.payload().get("subject"),"predicate",field.payload().get("predicate"),"value","D-120A"),field.integrityStatus());
        changed.add(conflicting);
        assertThat(runFile(directory,original.assetId(),changed).result().outcome()).isEqualTo(Outcome.CONFLICT);
    }
    private static EvidenceRecord withValue(EvidenceRecord e,String value) {
        return new EvidenceRecord(e.evidenceId(),e.assetId(),e.sourceType(),e.sourceRef(),e.recordedAt(),e.effectiveAt(),e.observedAt(),e.validTo(),e.authority(),
                Map.of("subject",e.payload().get("subject"),"predicate",e.payload().get("predicate"),"value",value),e.integrityStatus());
    }
    private io.devicetruth.core.runtime.TaskOrchestrator.RunReport runFile(Path directory,String asset,List<EvidenceRecord> records) throws Exception {
        Path fixture=directory.resolve("perturbed.json");Json.MAPPER.writeValue(fixture.toFile(),new Dataset("SIMULATED",asset,records));
        var repositories=new JsonRepositories(List.of(JsonRepositories.readDataset(fixture)),automotiveRules);
        return AcceptanceTest.run(repositories,AcceptanceTest.request("B"),new ScriptedReasoner());
    }
    @Test void renamedAssetAndEvidenceIdsPreserveBehavior() throws Exception {
        var original=JsonRepositories.readResource("datasets/fixtures/b/asset.json",Dataset.class);
        var renamed=original.records().stream().map(e->new EvidenceRecord("RENAMED-"+original.records().indexOf(e),"DEVICE-987",e.sourceType(),e.sourceRef(),e.recordedAt(),e.effectiveAt(),e.observedAt(),e.validTo(),e.authority(),e.payload(),e.integrityStatus())).toList();
        var repositories=new JsonRepositories(List.of(new Dataset("SIMULATED","DEVICE-987",renamed)),automotiveRules);
        var request=new DiagnosisRequest("DEVICE-987","automotive-demo","COMM_TIMEOUT",AcceptanceTest.request("B").asOf());
        var report=AcceptanceTest.run(repositories,request,new ScriptedReasoner());
        assertThat(report.result().outcome()).isEqualTo(Outcome.RESOLVED);assertThat(report.result().appliedRuleIds()).containsExactly("DEMO-D-120B");
    }
    @Test void industrialDomainUsesDifferentSubjectsPredicatesValuesAndSetMembershipWithoutCoreChanges() {
        Instant time=Instant.parse("2026-11-03T03:00:00Z");
        var factory=new EvidenceRecord("PLC-BASE","PLC-7","FACTORY","simulated:industrial/factory",time.minusSeconds(86400),time.minusSeconds(86400),null,null,10,
                Map.of("subject","DRIVE-2","predicate","firmwareRevision","value","R1"),IntegrityStatus.VERIFIED);
        var field=new EvidenceRecord("PLC-INSPECTION","PLC-7","FIELD_CONFIRMATION","simulated:industrial/inspection",time,time,time,null,10,
                Map.of("subject","DRIVE-2","predicate","firmwareRevision","value","R7"),IntegrityStatus.VERIFIED);
        var rule=new Rule("INDUSTRIAL-R7","industrial-test","CYCLE_LAG",List.of(new Condition("DRIVE-2","firmwareRevision","IN",List.of("R7","R8"))),
                List.of(new EvidenceRequirement("DRIVE-2","firmwareRevision","FIELD_CONFIRMATION")),"SIMULATION_ONLY","SIMULATED industrial training classification; no equipment action.",false);
        var repository=new JsonRepositories(List.of(new Dataset("SIMULATED","PLC-7",List.of(factory,field))),new RulePack("SIMULATED","0.1",List.of(rule)));
        var task=new DiagnosisRequest("PLC-7","industrial-test","CYCLE_LAG",time.plusSeconds(3600));
        var report=AcceptanceTest.run(repository,task,new ScriptedReasoner());
        assertThat(report.result().outcome()).isEqualTo(Outcome.RESOLVED);assertThat(report.result().appliedRuleIds()).containsExactly("INDUSTRIAL-R7");
        assertThat(report.result().supportingEvidenceIds()).containsExactly("PLC-INSPECTION");
    }
    @Test void earlyDecisionCannotSkipTheSecondDiscoveredConflictingRecord() throws Exception {
        var scripted=new ScriptedReasoner();
        Reasoner early=new Reasoner() {
            public String name(){return "scripted";}
            public ActionProposal propose(Context c) {
                boolean attempted=c.state().actionHistory().stream().anyMatch(h->h.proposal() instanceof ProposeDecision);
                var current=c.state().facts().stream().filter(f->f.status()==FactStatus.SUPPORTED&&f.evidenceIds().stream()
                        .anyMatch(id->c.evidence().stream().anyMatch(e->e.evidenceId().equals(id)&&e.sourceType().equals("FIELD_CONFIRMATION")))).toList();
                if(!attempted&&!current.isEmpty()) {
                    var fact=current.getFirst();var rule=c.state().knownRules().stream().filter(r->r.conditions().stream().allMatch(condition->condition.matches(fact))).findFirst().orElseThrow();
                    return new ProposeDecision(rule.ruleId(),List.of(fact.factId()),fact.evidenceIds(),List.of(),rule.allowedAdviceLevel(),"premature");
                }
                return scripted.propose(c);
            }
        };
        var report=AcceptanceTest.run("C",early);
        assertThat(report.result().outcome()).isEqualTo(Outcome.CONFLICT);
        assertThat(report.state().actionHistory()).anySatisfy(h->assertThat(h.result()).isEqualTo("MISSING_REQUIRED_EVIDENCE"));
    }
}
