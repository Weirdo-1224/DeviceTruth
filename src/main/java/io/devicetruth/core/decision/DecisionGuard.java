package io.devicetruth.core.decision;

import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.reasoning.ActionProposal.ProposeDecision;
import io.devicetruth.core.state.DiagnosisState;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import io.devicetruth.core.reasoning.ActionProposal.CallCapability;

public final class DecisionGuard {
    public record Verdict(boolean allowed,Outcome terminalOutcome,String reason,Rule rule,
                          List<EvidenceNeed> needs,List<String> evidenceIds) {
        public Verdict { needs=List.copyOf(needs); evidenceIds=List.copyOf(evidenceIds); }
    }
    public Verdict evaluate(DiagnosisState state,Map<String,EvidenceRecord> evidence,ProposeDecision proposal) {
        Rule rule=state.knownRules().stream().filter(r->r.ruleId().equals(proposal.candidateRuleId())).findFirst().orElse(null);
        if(rule==null) return deny("UNKNOWN_RULE",null);
        if(!rule.domain().equals(state.task().domain())||!rule.symptomCode().equals(state.task().symptomCode())) return deny("RULE_OUTSIDE_TASK_SCOPE",rule);
        if(!rule.allowedAdviceLevel().equals(proposal.adviceLevel())) return deny("ADVICE_LEVEL_DENIED",rule);
        var claimed=state.facts().stream().filter(f->proposal.claimedFactIds().contains(f.factId())).toList();
        if(claimed.size()!=proposal.claimedFactIds().stream().distinct().count()||claimed.isEmpty()) return deny("INVALID_FACT_REFERENCE",rule);
        if(claimed.stream().anyMatch(f->f.status()!=FactStatus.SUPPORTED)) return deny("FACT_NOT_SUPPORTED",rule);
        if(claimed.stream().anyMatch(f->f.validFrom().isAfter(state.task().asOf())||(f.validTo()!=null&&!state.task().asOf().isBefore(f.validTo())))) return deny("FACT_OUTSIDE_TASK_TIME",rule);
        if(proposal.claimedEvidenceIds().isEmpty()||proposal.claimedEvidenceIds().stream().anyMatch(id->!valid(evidence.get(id),state))) return deny("INVALID_EVIDENCE_REFERENCE",rule);
        var claimedHypotheses=state.hypotheses().stream().filter(h->proposal.claimedHypothesisIds().contains(h.hypothesisId())).toList();
        if(claimedHypotheses.size()!=proposal.claimedHypothesisIds().stream().distinct().count()||claimedHypotheses.stream().anyMatch(h->h.status()==HypothesisStatus.INVALIDATED)) return deny("INVALID_HYPOTHESIS_REFERENCE",rule);
        for(var hypothesis:claimedHypotheses) {
            if(!proposal.claimedFactIds().containsAll(hypothesis.dependsOnFactIds())||!proposal.claimedEvidenceIds().containsAll(hypothesis.dependsOnEvidenceIds())) return deny("HYPOTHESIS_DEPENDENCIES_NOT_CITED",rule);
        }
        for(var fact:claimed) {
            if(!proposal.claimedEvidenceIds().containsAll(fact.evidenceIds())||fact.evidenceIds().isEmpty()) return deny("FACT_EVIDENCE_NOT_CITED",rule);
            for(String id:fact.evidenceIds()) {
                var record=evidence.get(id);
                if(!valid(record,state)||!fact.subject().equals(record.payload().get("subject"))||!fact.predicate().equals(record.payload().get("predicate"))||!fact.value().equals(record.payload().get("value"))) return deny("FACT_EVIDENCE_MISMATCH",rule);
            }
        }
        if(rule.conditions().stream().anyMatch(c->claimed.stream().noneMatch(c::matches))) return deny("RULE_CONDITIONS_NOT_SATISFIED",rule);
        var review=review(state,evidence);
        if(review.conflict()) return new Verdict(false,Outcome.CONFLICT,"CURRENT_EVIDENCE_CONFLICT",rule,review.needs(),review.conflictingEvidenceIds());
        var missing=new ArrayList<EvidenceNeed>();
        for(var requirement:rule.requiredEvidence()) {
            boolean satisfied=claimed.stream().filter(f->sameField(requirement,f)).flatMap(f->f.evidenceIds().stream())
                    .filter(proposal.claimedEvidenceIds()::contains).map(evidence::get)
                    .anyMatch(e->valid(e,state)&&e.sourceType().equals(requirement.sourceType()));
            if(!satisfied) missing.add(need(rule,"Required "+requirement.sourceType()+" for "+requirement.subject()+"."+requirement.predicate(),investigationComplete(state,evidence)?NeedStatus.BLOCKED:NeedStatus.OPEN));
        }
        if(!investigationComplete(state,evidence)) missing.add(need(rule,"Read asset history, discover all evidence and fetch pending records before deciding",NeedStatus.OPEN));
        if(!missing.isEmpty()) return new Verdict(false,investigationComplete(state,evidence)?Outcome.NEED_EVIDENCE:null,"MISSING_REQUIRED_EVIDENCE",rule,missing,List.of());
        if(rule.requiresHuman()) return new Verdict(false,Outcome.ESCALATE,"RULE_REQUIRES_HUMAN_REVIEW",rule,List.of(),List.of());
        return new Verdict(true,Outcome.RESOLVED,"RULE_AND_EVIDENCE_VERIFIED",rule,List.of(),proposal.claimedEvidenceIds().stream().distinct().sorted().toList());
    }
    public record Review(List<EvidenceNeed> needs,boolean conflict,List<String> conflictingEvidenceIds) {
        public Review { needs=List.copyOf(needs); conflictingEvidenceIds=List.copyOf(conflictingEvidenceIds); }
    }
    public Review review(DiagnosisState state,Map<String,EvidenceRecord> evidence) {
        var relevant=state.knownRules().stream().filter(r->r.domain().equals(state.task().domain())&&r.symptomCode().equals(state.task().symptomCode())).toList();
        var contested=state.facts().stream().filter(f->f.status()==FactStatus.CONTESTED)
                .filter(f->relevant.stream().flatMap(r->r.conditions().stream()).anyMatch(c->c.subject().equals(f.subject())&&c.predicate().equals(f.predicate()))).toList();
        var needs=new ArrayList<EvidenceNeed>();
        var candidates=relevant.stream().filter(r->r.conditions().stream().allMatch(c->state.facts().stream().anyMatch(c::matches))).toList();
        if(candidates.isEmpty()) candidates=relevant;
        for(var rule:candidates) for(var requirement:rule.requiredEvidence()) {
            boolean satisfied=state.facts().stream().filter(f->f.status()==FactStatus.SUPPORTED&&sameField(requirement,f))
                    .flatMap(f->f.evidenceIds().stream()).map(evidence::get)
                    .anyMatch(e->valid(e,state)&&requirement.sourceType().equals(e.sourceType()));
            needs.add(need(rule,"Required "+requirement.sourceType()+" for "+requirement.subject()+"."+requirement.predicate(),
                    satisfied?NeedStatus.SATISFIED:investigationComplete(state,evidence)?NeedStatus.BLOCKED:NeedStatus.OPEN));
        }
        if(!contested.isEmpty()) needs.add(new EvidenceNeed("NEED-CONFLICT","Adjudicate overlapping equal-authority current evidence",NeedSource.RULE,NeedStatus.BLOCKED,null));
        return new Review(needs,!contested.isEmpty(),contested.stream().flatMap(f->f.evidenceIds().stream()).distinct().sorted().toList());
    }
    public boolean investigationComplete(DiagnosisState state,Map<String,EvidenceRecord> evidence) {
        boolean history=completed(state,"asset.history.query",false);
        boolean discovery=completed(state,"evidence.search",true);
        return history&&discovery&&state.discoveredEvidence().stream().allMatch(e->evidence.containsKey(e.evidenceId()));
    }
    private boolean completed(DiagnosisState state,String capability,boolean unfiltered) {
        return state.actionHistory().stream().anyMatch(h->h.proposal() instanceof CallCapability c&&c.capabilityId().equals(capability)
                &&(!unfiltered||c.arguments().keySet().equals(java.util.Set.of("assetId")))
                &&(unfiltered||!c.arguments().containsKey("componentId"))&&h.observation()!=null&&h.observation().success());
    }
    private boolean valid(EvidenceRecord record,DiagnosisState state) {
        return record!=null&&record.integrityStatus()==IntegrityStatus.VERIFIED&&record.assetId().equals(state.task().assetId())&&record.validAt(state.task().asOf());
    }
    private boolean sameField(EvidenceRequirement r,Fact f) { return r.subject().equals(f.subject())&&r.predicate().equals(f.predicate()); }
    private EvidenceNeed need(Rule rule,String description,NeedStatus status) {
        String id="NEED-"+UUID.nameUUIDFromBytes((rule.ruleId()+":"+description).getBytes(StandardCharsets.UTF_8));
        return new EvidenceNeed(id,description,NeedSource.RULE,status,rule.ruleId());
    }
    private Verdict deny(String reason,Rule rule) {
        return new Verdict(false,null,reason,rule,List.of(new EvidenceNeed("NEED-VALID-PROPOSAL",reason,NeedSource.AGENT,NeedStatus.OPEN,rule==null?null:rule.ruleId())),List.of());
    }
}
