package io.devicetruth.core;

import io.devicetruth.adapter.JsonRepositories;
import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.evidence.EvidenceManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EvidenceManagerTest {
    private final EvidenceManager manager=new EvidenceManager();
    private final Instant asOf=Instant.parse("2026-11-03T04:00:00Z");
    private DiagnosisRequest task(String asset) {return new DiagnosisRequest(asset,"test-domain","TIMEOUT",asOf);}
    @Test void replacementClosesFactoryIntervalAndInvalidatesFactAndEvidenceDependencies() throws Exception {
        var repository=JsonRepositories.fromClasspath();var request=task("CAR-B");
        var initial=manager.analyze(request,Map.of(),repository.profile(request.assetId()));
        var fact=initial.facts().getFirst();
        var hypotheses=List.of(new Hypothesis("H-1","baseline inference",List.of(fact.factId()),List.of(),HypothesisStatus.OPEN,null),
                new Hypothesis("H-2","evidence-only inference",List.of(),fact.evidenceIds(),HypothesisStatus.OPEN,null));
        var revised=manager.analyze(request,initial.records(),repository.history(request.assetId(),null));
        assertThat(revised.facts()).filteredOn(f->f.factId().equals(fact.factId())).singleElement().satisfies(f->{
            assertThat(f.status()).isEqualTo(FactStatus.SUPERSEDED);assertThat(f.validTo()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        });
        assertThat(revised.facts()).noneMatch(f->f.status()==FactStatus.CONTESTED);
        assertThat(manager.invalidateDependencies(hypotheses,revised.facts(),revised.records())).allSatisfy(h->{assertThat(h.status()).isEqualTo(HypothesisStatus.INVALIDATED);assertThat(h.invalidationReason()).isNotBlank();});
        var field=repository.get(request.assetId(),"CAR-B-FIELD-1");
        var current=manager.analyze(request,revised.records(),List.of(field));
        assertThat(current.facts()).filteredOn(f->f.status()==FactStatus.SUPPORTED).singleElement().satisfies(f->assertThat(f.value()).isEqualTo("D-120B"));
        assertThat(current.records()).hasSize(3); assertThat(initial.facts().getFirst().status()).isEqualTo(FactStatus.SUPPORTED);
    }
    @Test void equalAuthorityOverlappingFieldRecordsConflictEvenIfRecordedLater() throws Exception {
        var r=JsonRepositories.fromClasspath();var t=task("CAR-C");
        var result=manager.analyze(t,Map.of(),List.of(r.get(t.assetId(),"CAR-C-FIELD-1"),r.get(t.assetId(),"CAR-C-FIELD-2")));
        assertThat(result.facts()).hasSize(2).allSatisfy(f->assertThat(f.status()).isEqualTo(FactStatus.CONTESTED));
    }
    @Test void duplicatesCollisionsOwnershipIntegrityAndHalfOpenTimeBoundaries() {
        var record=field("E-1","fixture","x",asOf.minusSeconds(60),asOf.plusSeconds(60),10);
        var t=task("fixture");var first=manager.analyze(t,Map.of(),List.of(record,record));
        assertThat(first.records()).hasSize(1);assertThat(manager.analyze(t,first.records(),List.of(record)).facts()).hasSize(1);
        assertThatThrownBy(()->manager.analyze(t,first.records(),List.of(field("E-1","fixture","y",record.observedAt(),record.validTo(),10)))).hasMessageContaining("collision");
        assertThatThrownBy(()->manager.analyze(task("other"),Map.of(),List.of(record))).hasMessageContaining("asset");
        var rejected=new EvidenceRecord("E-BAD","fixture","FIELD_CONFIRMATION","sim:bad",record.recordedAt(),record.effectiveAt(),record.observedAt(),null,10,record.payload(),IntegrityStatus.REJECTED);
        assertThatThrownBy(()->manager.analyze(t,Map.of(),List.of(rejected))).hasMessageContaining("integrity");
        var expired=field("E-END","fixture","x",asOf.minusSeconds(60),asOf,10);
        var future=field("E-FUTURE","fixture","y",asOf.plusSeconds(1),null,10);
        var start=field("E-START","fixture","z",asOf,null,10);
        assertThat(manager.analyze(t,Map.of(),List.of(expired,future,start)).facts()).filteredOn(f->f.status()==FactStatus.SUPPORTED)
                .singleElement().satisfies(f->assertThat(f.value()).isEqualTo("z"));
        var lateRecord=new EvidenceRecord("E-LATE","fixture","FIELD_CONFIRMATION","sim:late",asOf.plusSeconds(1),asOf.minusSeconds(60),asOf.minusSeconds(60),null,10,record.payload(),IntegrityStatus.VERIFIED);
        assertThat(manager.analyze(t,Map.of(),List.of(lateRecord)).facts().getFirst().status()).isEqualTo(FactStatus.SUPERSEDED);
    }
    @Test void expiredAndSequentialObservationsDoNotConflictAndExplicitAuthorityCanAdjudicate() {
        var old=field("E-OLD","fixture","old",asOf.minusSeconds(120),asOf.minusSeconds(60),10);
        var current=field("E-NOW","fixture","new",asOf.minusSeconds(60),null,10);
        assertThat(manager.analyze(task("fixture"),Map.of(),List.of(old,current)).facts()).filteredOn(f->f.status()==FactStatus.SUPPORTED)
                .singleElement().satisfies(f->assertThat(f.value()).isEqualTo("new"));
        var stronger=field("E-STRONG","fixture","reviewed",asOf.minusSeconds(60),null,20);
        assertThat(manager.analyze(task("fixture"),Map.of(),List.of(current,stronger)).facts()).filteredOn(f->f.status()==FactStatus.SUPPORTED)
                .singleElement().satisfies(f->assertThat(f.value()).isEqualTo("reviewed"));
    }
    static EvidenceRecord field(String id,String asset,String value,Instant from,Instant to,int authority) {
        return new EvidenceRecord(id,asset,"FIELD_CONFIRMATION","simulated:"+id,from,from,from,to,authority,
                Map.of("subject","generic-component","predicate","revision","value",value),IntegrityStatus.VERIFIED);
    }
}
