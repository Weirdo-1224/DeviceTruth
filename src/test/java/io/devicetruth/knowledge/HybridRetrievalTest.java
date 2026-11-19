package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HybridRetrievalTest {
    @TempDir Path temp;
    private final IdentifierExtractor ids=new IdentifierExtractor();
    private final ApplicabilityPolicy policy=new ApplicabilityPolicy();
    private static com.fasterxml.jackson.databind.JsonNode json(String value)throws Exception{return Json.MAPPER.readTree(value);}
    @Test void extractsTypedExamplesWithoutInventingEquipmentRelationships() {
        var found=ids.extract("CPU 1214C S7-1200 ICCU OBC P0A94 9B5 PROFINET MERV13");
        assertThat(found).extracting(IdentifierExtractor.Identifier::value).contains("CPU1214C","S71200","ICCU","OBC","P0A94","9B5","PROFINET","MERV13");
        assertThat(found).anyMatch(x->x.type()==IdentifierExtractor.Type.DTC&&x.value().equals("P0A94"));
        assertThat(found).anyMatch(x->x.type()==IdentifierExtractor.Type.ALARM&&x.value().equals("9B5"));
    }
    @Test void normalizationPreservesVersionsAndModelSuffixes() {
        assertThat(ids.extract("s7 1200")).extracting(IdentifierExtractor.Identifier::value).contains("S71200");
        assertThat(IdentifierExtractor.normalize("CPU 1214C")).isNotEqualTo(IdentifierExtractor.normalize("CPU 1215C"));
        assertThat(ids.extract("FW V4.6.1")).extracting(IdentifierExtractor.Identifier::value).contains("4.6.1");
        assertThat(ids.extract("FW4.6.1.2.3")).noneMatch(i->i.type()==IdentifierExtractor.Type.FIRMWARE);
        assertThat(ids.extract("software 4.6")).anyMatch(i->i.type()==IdentifierExtractor.Type.SOFTWARE);
        assertThat(IdentifierExtractor.normalize("4.6.1")).isNotEqualTo(IdentifierExtractor.normalize("4.61"));
        assertThat(ids.extract("abc123 random-123 P0A940 OBCX")).isEmpty();
        assertThat(IdentifierExtractor.normalize("AB-12")).isNotEqualTo(IdentifierExtractor.normalize("AB12"));
    }
    @Test void verifiedManufacturerAndModelsUseBoundaryAwareMatching() {
        var lex=Map.of("Siemens",IdentifierExtractor.Type.MANUFACTURER,"EV6",IdentifierExtractor.Type.MODEL);
        assertThat(ids.extract("siemens EV6",lex)).hasSize(2);
        assertThat(ids.extract("EV60 SiemensXYZ",lex)).isEmpty();
    }
    @Test void rrfAbsentChannelIsZeroAndDuplicateChildCountsOnce() {
        UUID a=new UUID(0,1),b=new UUID(0,2);
        var result=ReciprocalRankFusion.fuse(List.of(a,a,b),List.of(b),List.of(),60,1,1,1);
        assertThat(result.getFirst().childId()).isEqualTo(b);
        assertThat(result.getFirst().score()).isCloseTo(1.0/62+1.0/61,within(1e-12));
        assertThat(result.get(1).identifierRank()).isNull();
    }
    @Test void rrfTieBreakUsesStableChildIdAndWeightsAreValidated() {
        UUID a=new UUID(0,1),b=new UUID(0,2);
        assertThat(ReciprocalRankFusion.fuse(List.of(b),List.of(a),List.of(),60,1,1,1)).extracting(ReciprocalRankFusion.Ranked::childId).containsExactly(a,b);
        assertThatThrownBy(()->ReciprocalRankFusion.fuse(List.of(),List.of(),List.of(),0,1,1,1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unknownModelIsNotVerifiedAndDifferentModelsAreExcluded()throws Exception {
        var m=json("{\"device_models\":[\"CPU 1214C\"],\"manufacturer\":\"Siemens\",\"applicability_scope\":{\"scope_level\":\"MODEL\"}}");
        assertThat(policy.assess(m,ApplicabilityPolicy.Target.UNKNOWN).status()).isEqualTo(ApplicabilityPolicy.Status.UNKNOWN_APPLICABILITY);
        assertThat(policy.assess(m,target("CPU 1215C",null)).status()).isEqualTo(ApplicabilityPolicy.Status.NOT_APPLICABLE);
        assertThat(policy.assess(m,target("CPU-1214C",null)).verifiedApplicable()).isTrue();
    }
    @Test void firmwareSoftwareYearAndMarketConflictsAreMandatory()throws Exception {
        var m=json("{\"device_models\":[\"CPU 1214C\"],\"verified_applicability\":{\"firmware_versions\":[\"4.6.1\"],\"software_versions\":[\"V18\"],\"model_years\":[\"2024\"],\"markets\":[\"CN\"]}}");
        assertThat(policy.assess(m,target("CPU 1214C","4.6.2")).status()).isEqualTo(ApplicabilityPolicy.Status.NOT_APPLICABLE);
        assertThat(policy.assess(m,target("CPU 1214C",null)).verifiedApplicable()).isFalse();
        var matching=new ApplicabilityPolicy.Target("CPU 1214C",null,null,"4.6.1","V18","2024","CN");
        assertThat(policy.assess(m,matching).verifiedApplicable()).isTrue();
        for(var t:List.of(new ApplicabilityPolicy.Target("CPU 1214C",null,null,"4.6.1","V19","2024","CN"),
                new ApplicabilityPolicy.Target("CPU 1214C",null,null,"4.6.1","V18","2025","CN"),
                new ApplicabilityPolicy.Target("CPU 1214C",null,null,"4.6.1","V18","2024","US")))
            assertThat(policy.assess(m,t).status()).isEqualTo(ApplicabilityPolicy.Status.NOT_APPLICABLE);
    }
    @Test void sourceEditionIsNeverAssumedToBeFirmware()throws Exception {
        var m=json("{\"device_models\":[\"S7-1200\"],\"applicable_versions\":[\"V4.6 / A5E02486680-AP\"],\"applicability_scope\":{\"version_semantics\":\"UNCLASSIFIED_SOURCE_STATEMENTS\"}}");
        var d=policy.assess(m,target("S7-1200","4.6"));
        assertThat(d.status()).isEqualTo(ApplicabilityPolicy.Status.UNKNOWN_APPLICABILITY);assertThat(d.verifiedApplicable()).isFalse();
    }
    @Test void familyAndManufacturerRemainLimitedLeads()throws Exception {
        var m=json("{\"manufacturer\":\"Siemens\",\"device_models\":[\"S7-1200\"]}");
        assertThat(policy.assess(m,new ApplicabilityPolicy.Target(null,"S7-1200",null,null,null,null,null)).status()).isEqualTo(ApplicabilityPolicy.Status.FAMILY_MATCH);
        assertThat(policy.assess(m,new ApplicabilityPolicy.Target(null,null,"Siemens",null,null,null,null)).verifiedApplicable()).isFalse();
    }
    @Test void generalKnowledgeCanCoexistWithModelSpecificKnowledge()throws Exception {
        assertThat(policy.assess(json("{\"applicability_scope\":{\"scope_level\":\"GENERAL\"}}"),target("CPU1214C",null)).status()).isEqualTo(ApplicabilityPolicy.Status.DOMAIN_GENERAL);
    }
    private static ApplicabilityPolicy.Target target(String model,String firmware){return new ApplicabilityPolicy.Target(model,null,null,firmware,null,null,null);}
    private ScopedKnowledgeStore.Child child(UUID id,String content,String model)throws Exception {
        return new ScopedKnowledgeStore.Child(id,new UUID(0,10),new UUID(0,11),new UUID(0,12),"TEST",1,null,"诊断/通信","CPU诊断","v1",content,"SIMULATED","industrial",null,
                json("{\"device_models\":[\""+model+"\"],\"components\":[\"PLC\"],\"keywords\":[\"通信诊断\"],\"manufacturer\":\"Siemens\"}"),json("[]"));
    }
    private ScopedKnowledgeStore.Snapshot snapshot(List<ScopedKnowledgeStore.Child> c){return new ScopedKnowledgeStore.Snapshot("owner","project","contract",KnowledgeCorpus.hash(Json.write(c)),c);}
    @Test void luceneChineseEnglishAndExactDtcUseSeparateFields()throws Exception {
        UUID a=new UUID(0,1),b=new UUID(0,2);var snap=snapshot(List.of(child(a,"PROFINET communication 过程数据 故障码 P0A94","CPU 1214C"),child(b,"故障码 P0A95","CPU 1215C")));
        var index=new LuceneBm25Index(temp);assertThat(index.build(snap).integrity()).isTrue();
        assertThat(index.bm25(snap,Set.of(a,b),"PROFINET过程数据",20)).extracting(LuceneBm25Index.Scored::childId).contains(a);
        assertThat(index.identifier(snap,Set.of(a,b),ids.extract("P0A94"),20)).extracting(LuceneBm25Index.Scored::childId).containsExactly(a);
        assertThat(index.identifier(snap,Set.of(a,b),ids.extract("P0A940"),20)).isEmpty();
    }
    @Test void indexRebuildIsIdempotentAndPostgresContentChangesUseNewGeneration()throws Exception {
        var snap=snapshot(List.of(child(new UUID(0,1),"原始文本 P0A94","CPU 1214C")));var index=new LuceneBm25Index(temp);
        var first=index.build(snap);var second=index.build(snap);assertThat(second.reused()).isTrue();assertThat(second.path()).isEqualTo(first.path());
        var changed=snapshot(List.of(child(new UUID(0,1),"修订文本 P0A95","CPU 1214C")));assertThat(index.build(changed).path()).isNotEqualTo(first.path());
        assertThat(index.validate(snap,true).integrity()).isTrue();
    }
    @Test void incompleteAndCorruptGenerationDoesNotBecomeVisibleAndRebuildRecovers()throws Exception {
        var snap=snapshot(List.of(child(new UUID(0,1),"P0A94","CPU1214C")));var index=new LuceneBm25Index(temp);
        assertThatThrownBy(()->index.bm25(snap,Set.of(new UUID(0,1)),"P0A94",20)).hasMessage("LUCENE_INDEX_NOT_READY");
        var report=index.build(snap);
        try(var files=Files.list(Path.of(report.path()))){for(var p:files.filter(p->p.getFileName().toString().startsWith("segments_")).toList())Files.delete(p);}
        assertThatThrownBy(()->index.validate(snap,true)).isInstanceOf(KnowledgeException.class);
        assertThat(index.build(snap).integrity()).isTrue();
    }
    @Test void allowedIdsAreFilteredBeforeLuceneTopKAndOwnerScopeChangesPath()throws Exception {
        UUID a=new UUID(0,1),b=new UUID(0,2);var snap=snapshot(List.of(child(a,"P0A94","CPU1214C"),child(b,"P0A94","CPU1215C")));var index=new LuceneBm25Index(temp);var report=index.build(snap);
        assertThat(index.identifier(snap,Set.of(b),ids.extract("P0A94"),1)).extracting(LuceneBm25Index.Scored::childId).containsExactly(b);
        var other=new ScopedKnowledgeStore.Snapshot("other","project",snap.embeddingProfile(),snap.fingerprint(),snap.children());
        assertThat(index.build(other).path()).isNotEqualTo(report.path());
    }
    @Test void analyzersRetainNumbersAndEnglishAndNoRelevantKnowledgeIsEmpty()throws Exception {
        for(var kind:LuceneBm25Index.Analysis.values())assertThat(LuceneBm25Index.tokens(kind,"PROFINET CPU1214C P0A94 版本4.6")).anyMatch(x->x.toLowerCase(Locale.ROOT).contains("profinet"));
        var snap=snapshot(List.of(child(new UUID(0,1),"PROFINET通信","CPU 1214C")));var index=new LuceneBm25Index(temp);index.build(snap);
        assertThat(index.bm25(snap,Set.of(new UUID(0,1)),"芒果慕斯蛋糕烘焙",20)).isEmpty();
    }
}
