package io.devicetruth.core.reasoning;

import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.reasoning.ActionProposal.*;
import java.util.List;
import java.util.Map;

/** Deterministic policy over visible state only; never reads repositories or case expectations. */
public final class ScriptedReasoner implements Reasoner {
    @Override public String name() { return "scripted"; }
    @Override public ActionProposal propose(Context context) {
        var s=context.state(); var task=s.task();
        if(!called(context,"diagnosis.rules.query")) return call("diagnosis.rules.query",Map.of("domain",task.domain(),"symptomCode",task.symptomCode()),"Discover rule prerequisites");
        if(!called(context,"asset.profile.get")) return call("asset.profile.get",Map.of("assetId",task.assetId()),"Read baseline configuration");
        var supported=s.facts().stream().filter(f->f.status()==FactStatus.SUPPORTED).toList();
        if(s.hypotheses().isEmpty()&&!supported.isEmpty()) {
            var fact=supported.getFirst();
            return new ProposeHypothesis("Tentative configuration: "+fact.subject()+" "+fact.predicate()+"="+fact.value(),List.of(fact.factId()),fact.evidenceIds());
        }
        if(!called(context,"asset.history.query")) return call("asset.history.query",Map.of("assetId",task.assetId()),"Check changes since baseline");
        if(!called(context,"evidence.search")) return call("evidence.search",Map.of("assetId",task.assetId()),"Discover available confirmation records");
        for(var meta:s.discoveredEvidence()) {
            if(context.evidence().stream().noneMatch(e->e.evidenceId().equals(meta.evidenceId())))
                return call("evidence.get",Map.of("assetId",task.assetId(),"evidenceId",meta.evidenceId()),"Read discovered evidence");
        }
        for(var rule:s.knownRules()) {
            if(rule.conditions().stream().allMatch(c->supported.stream().anyMatch(c::matches))) {
                var facts=supported.stream().filter(f->rule.conditions().stream().anyMatch(c->c.matches(f))).toList();
                return new ProposeDecision(rule.ruleId(),facts.stream().map(Fact::factId).toList(),
                        facts.stream().flatMap(f->f.evidenceIds().stream()).distinct().toList(),List.of(),rule.allowedAdviceLevel(),"Check visible facts against candidate prerequisites");
            }
        }
        return new RequestInformation("Available evidence does not satisfy a candidate rule",List.of("current configuration confirmation or conflict adjudication"));
    }
    private boolean called(Context context,String id) {
        return context.state().actionHistory().stream().anyMatch(h->h.proposal() instanceof CallCapability c&&
                c.capabilityId().equals(id)&&h.observation()!=null&&h.observation().success());
    }
    private CallCapability call(String id,Map<String,String> args,String purpose) { return new CallCapability(id,args,purpose,List.of()); }
}
