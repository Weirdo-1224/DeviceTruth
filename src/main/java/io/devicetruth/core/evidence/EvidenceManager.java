package io.devicetruth.core.evidence;

import io.devicetruth.core.contract.Models.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;

/** 证据管理器：唯一接触原始证据记录的地方。负责证据归并、事实推导与依赖失效。
 *  证据与状态分离保存，模型提案永远无法直接写入证据。 */
public final class EvidenceManager {
    /** 分析结果：归并后的证据集合 + 由证据推导出的全部事实。 */
    public record Analysis(Map<String,EvidenceRecord> records,List<Fact> facts) {
        public Analysis { records=Map.copyOf(records); facts=List.copyOf(facts); }
    }
    /** 归并新证据并重新推导事实。每条证据生成一个事实，状态按同字段组内竞争结果判定。 */
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
            // 同字段组内在任务时刻仍然"活"着的证据参与竞争
            var live=group.stream().filter(e->e.validAt(task.asOf()))
                    .filter(e->{var limit=effectiveEnd(e,group,task);return limit==null||task.asOf().isBefore(limit);}).toList();
            // 竞争规则：来源等级（rank）最高者优先；同级再比权威值（authority）；都相同则并列
            int bestRank=live.stream().mapToInt(EvidenceManager::rank).max().orElse(-1);
            int bestAuthority=live.stream().filter(e->rank(e)==bestRank).mapToInt(EvidenceRecord::authority).max().orElse(-1);
            var strongest=live.stream().filter(e->rank(e)==bestRank&&e.authority()==bestAuthority).toList();
            // 本证据若非最强者即为 SUPERSEDED；最强者之间取值一致为 SUPPORTED，不一致为 CONTESTED
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
    /** 来源等级：现场确证 > 更换记录 > 其他（出厂档案等）。 */
    private static int rank(EvidenceRecord evidence) {
        return switch(evidence.sourceType()) {case "FIELD_CONFIRMATION"->2;case "REPLACEMENT"->1;default->0;};
    }
    /** 证据的有效期终点：显式 validTo 与"被更晚生效的更换记录取代"两者取更早者；
     *  FIELD_CONFIRMATION（现场确证）不受更换记录影响。 */
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
    /** 依赖失效：事实或证据不再成立后，依赖它们的假设一律置为 INVALIDATED（幂等，已失效的不变）。 */
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
