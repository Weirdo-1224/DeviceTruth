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

/** 决策守卫：对推理器提出的决策（ProposeDecision）做完整校验，是"下结论"的唯一关口。 */
public final class DecisionGuard {
    /** 校验结论：是否放行、终局结论（若直接结束运行）、原因码、命中的规则、缺口需求与支撑证据。 */
    public record Verdict(boolean allowed,Outcome terminalOutcome,String reason,Rule rule,
                          List<EvidenceNeed> needs,List<String> evidenceIds) {
        public Verdict { needs=List.copyOf(needs); evidenceIds=List.copyOf(evidenceIds); }
    }
    /** 校验链：规则存在且在任务域内 → 建议等级匹配 → 引用的事实均已成立且在任务时间内 →
     *  引用的证据有效 → 引用的假设未失效且依赖已引用 → 事实与证据的 subject/predicate/value 一致 →
     *  规则条件全部由引用事实满足 → 无证据冲突 → 必需证据齐备 → 是否需要人工复核。任一环节不过即拒绝。 */
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
        // 当前证据存在同权威冲突时，不允许下结论，转为 CONFLICT 终局
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
    /** 规则视角的审查：对所有候选规则列出证据需求的满足状态，并汇总同权威冲突的事实。 */
    public record Review(List<EvidenceNeed> needs,boolean conflict,List<String> conflictingEvidenceIds) {
        public Review { needs=List.copyOf(needs); conflictingEvidenceIds=List.copyOf(conflictingEvidenceIds); }
    }
    /** 逐个规则检查其 requiredEvidence 是否已被有效证据（来源类型匹配、任务时间内）满足。 */
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
    /** 调查是否完成 = 已查更换历史 + 已做不限定条件的证据搜索 + 所有已发现证据均已取回。 */
    public boolean investigationComplete(DiagnosisState state,Map<String,EvidenceRecord> evidence) {
        boolean history=completed(state,"asset.history.query",false);
        // evidence.search 需以仅含 assetId 的参数调用过（即做过全量发现，而非按组件过滤）
        boolean discovery=completed(state,"evidence.search",true);
        return history&&discovery&&state.discoveredEvidence().stream().allMatch(e->evidence.containsKey(e.evidenceId()));
    }
    /** 在行动历史中查找某能力是否已成功调用过；unfiltered=true 时要求参数仅含 assetId。 */
    private boolean completed(DiagnosisState state,String capability,boolean unfiltered) {
        return state.actionHistory().stream().anyMatch(h->h.proposal() instanceof CallCapability c&&c.capabilityId().equals(capability)
                &&(!unfiltered||c.arguments().keySet().equals(java.util.Set.of("assetId")))
                &&(unfiltered||!c.arguments().containsKey("componentId"))&&h.observation()!=null&&h.observation().success());
    }
    /** 证据是否可用于当前任务：完整性已验证、属于本资产、且在任务 asOf 时刻有效。 */
    private boolean valid(EvidenceRecord record,DiagnosisState state) {
        return record!=null&&record.integrityStatus()==IntegrityStatus.VERIFIED&&record.assetId().equals(state.task().assetId())&&record.validAt(state.task().asOf());
    }
    private boolean sameField(EvidenceRequirement r,Fact f) { return r.subject().equals(f.subject())&&r.predicate().equals(f.predicate()); }
    /** 需求 ID 由规则 ID+描述内容确定性生成（UUID v3），保证同一缺口的需求 ID 稳定。 */
    private EvidenceNeed need(Rule rule,String description,NeedStatus status) {
        String id="NEED-"+UUID.nameUUIDFromBytes((rule.ruleId()+":"+description).getBytes(StandardCharsets.UTF_8));
        return new EvidenceNeed(id,description,NeedSource.RULE,status,rule.ruleId());
    }
    /** 构造拒绝结论，同时给出一条"先提出合法提案"的开放需求，引导推理器修正行为。 */
    private Verdict deny(String reason,Rule rule) {
        return new Verdict(false,null,reason,rule,List.of(new EvidenceNeed("NEED-VALID-PROPOSAL",reason,NeedSource.AGENT,NeedStatus.OPEN,rule==null?null:rule.ruleId())),List.of());
    }
}
