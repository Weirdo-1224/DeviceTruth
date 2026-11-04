package io.devicetruth.core.evidence;

import io.devicetruth.core.contract.Models.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;

/** Evidence is held separately from the state and never supplied by a model proposal. */
public final class EvidenceManager {
    public record Analysis(Map<String,EvidenceRecord> records,List<Fact> facts) {
        public Analysis { records=Map.copyOf(records); facts=List.copyOf(facts); }
    }
    public Analysis analyze(DiagnosisRequest task,Map<String,EvidenceRecord> existing,List<EvidenceRecord> incoming) {
        var records=new LinkedHashMap<>(existing);
        for(var record:incoming) {
            if(!task.assetId().equals(record.assetId())) throw new IllegalArgumentException("Evidence asset mismatch");
            if(record.integrityStatus()!=IntegrityStatus.VERIFIED) throw new IllegalArgumentException("Evidence integrity rejected");
            var previous=records.putIfAbsent(record.evidenceId(),record);
            if(previous!=null&&!previous.equals(record)) throw new IllegalArgumentException("Evidence ID collision");
        }
        var ordered=records.values().stream().sorted(Comparator.comparing(EvidenceRecord::evidenceId)).toList();
        var facts=new ArrayList<Fact>();
        for(var record:ordered) {
            var group=ordered.stream().filter(e->sameField(e,record)).toList();
            var end=effectiveEnd(record,group,task);
            var live=group.stream().filter(e->e.validAt(task.asOf()))
                    .filter(e->{var limit=effectiveEnd(e,group,task);return limit==null||task.asOf().isBefore(limit);}).toList();
            int bestRank=live.stream().mapToInt(EvidenceManager::rank).max().orElse(-1);
            int bestAuthority=live.stream().filter(e->rank(e)==bestRank).mapToInt(EvidenceRecord::authority).max().orElse(-1);
            var strongest=live.stream().filter(e->rank(e)==bestRank&&e.authority()==bestAuthority).toList();
            FactStatus status=FactStatus.SUPERSEDED;
            if(strongest.contains(record)) {
                status=strongest.stream().map(e->e.payload().get("value")).distinct().count()>1?FactStatus.CONTESTED:FactStatus.SUPPORTED;
            }
            facts.add(new Fact("F:"+record.evidenceId(),record.payload().get("subject"),record.payload().get("predicate"),
                    record.payload().get("value"),record.validFrom(),end,List.of(record.evidenceId()),status));
        }
        return new Analysis(records,facts);
    }
    private static boolean sameField(EvidenceRecord a,EvidenceRecord b) {
        return a.payload().get("subject").equals(b.payload().get("subject"))&&a.payload().get("predicate").equals(b.payload().get("predicate"));
    }
    private static int rank(EvidenceRecord evidence) {
        return switch(evidence.sourceType()) {case "FIELD_CONFIRMATION"->2;case "REPLACEMENT"->1;default->0;};
    }
    private static java.time.Instant effectiveEnd(EvidenceRecord record,List<EvidenceRecord> group,DiagnosisRequest task) {
        var end=record.validTo();
        if(!record.sourceType().equals("FIELD_CONFIRMATION")) {
            var replacement=group.stream().filter(e->e.sourceType().equals("REPLACEMENT"))
                    .filter(e->!e.recordedAt().isAfter(task.asOf())&&e.validFrom().isAfter(record.validFrom()))
                    .map(EvidenceRecord::validFrom).min(Comparator.naturalOrder()).orElse(null);
            if(replacement!=null&&(end==null||replacement.isBefore(end))) end=replacement;
        }
        return end;
    }
    public List<Hypothesis> invalidateDependencies(List<Hypothesis> hypotheses,List<Fact> facts,Map<String,EvidenceRecord> records) {
        var supported=facts.stream().filter(f->f.status()==FactStatus.SUPPORTED).toList();
        var validFacts=supported.stream().map(Fact::factId).toList();
        var validEvidence=supported.stream().flatMap(f->f.evidenceIds().stream()).toList();
        return hypotheses.stream().map(h->{
            if(h.status()==HypothesisStatus.INVALIDATED) return h;
            if(validFacts.containsAll(h.dependsOnFactIds())&&validEvidence.containsAll(h.dependsOnEvidenceIds())&&records.keySet().containsAll(h.dependsOnEvidenceIds())) return h;
            return new Hypothesis(h.hypothesisId(),h.statement(),h.dependsOnFactIds(),h.dependsOnEvidenceIds(),
                    HypothesisStatus.INVALIDATED,"Dependent facts/evidence were superseded, contested or outside task time");
        }).toList();
    }
}
