package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.capability.ToolContext;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContextRecoveryTest {
    static final UUID DOCUMENT=new UUID(0,100),PARENT=new UUID(0,200),RELEASE=new UUID(0,300);
    static ScopedKnowledgeStore.Child child(UUID id,int no,String content) {
        return new ScopedKnowledgeStore.Child(id,DOCUMENT,PARENT,RELEASE,"TEST",no,null,"正文","technical title","v1",content,"SIMULATED","industrial",null,Json.MAPPER.createObjectNode(),Json.MAPPER.createArrayNode());
    }
    static HybridRetrievalService.Candidate candidate(UUID id,int no,int rank,String content) {
        return new HybridRetrievalService.Candidate(child(id,no,content),new ApplicabilityPolicy.Decision(ApplicabilityPolicy.Status.UNKNOWN_APPLICABILITY,false,List.of()),List.of(),.8,rank,1.0,rank,null,1.0/(60+rank),rank);
    }
    final UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3);
    ToolContext ctx(){return new ToolContext("owner","project",UUID.randomUUID(),UUID.randomUUID(),null,Instant.now(),null,Set.of(KnowledgeTools.SEARCH));}
    ScopedKnowledgeStore.Snapshot snapshot(List<ScopedKnowledgeStore.Child> cs){return new ScopedKnowledgeStore.Snapshot("owner","project","test","fingerprint",cs);}
    Map<UUID,ContextRecoveryService.Parent> parent(String text){return Map.of(PARENT,new ContextRecoveryService.Parent(PARENT,DOCUMENT,RELEASE,"owner","project",text));}
    ContextRecoveryService recovery(){return new ContextRecoveryService(String::length,"TEST_CHAR_UNITS");}
    ContextRecoveryService.Options options(ContextRecoveryService.Strategy strategy){return new ContextRecoveryService.Options(strategy,10,1000,1000);}
    ContextRecoveryService.Result run(ContextRecoveryService.Strategy strategy,List<ScopedKnowledgeStore.Child> cs,List<HybridRetrievalService.Candidate> hits){return recovery().recover(ctx(),snapshot(cs),KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,hits,parent("first\nsecond\nthird"),options(strategy));}
    @Test void noneReturnsOnlyActualSeeds(){var seed=candidate(b,2,1,"second");var r=run(ContextRecoveryService.Strategy.NONE,List.of(child(a,1,"first"),seed.child(),child(c,3,"third")),List.of(seed));assertThat(r.parts()).hasSize(1);assertThat(r.blocks().getFirst().completeParent()).isFalse();}
    @Test void adjacentKeepsEveryOriginalIdentityAndSource() {var seed=candidate(b,2,1,"second");var cs=List.of(child(a,1,"first"),seed.child(),child(c,3,"third"));var r=run(ContextRecoveryService.Strategy.ADJACENT,cs,List.of(seed));assertThat(r.parts()).extracting(p->p.child().id()).containsExactly(b,a,c);assertThat(r.blocks()).hasSize(1);assertThat(r.blocks().getFirst().completeParent()).isTrue();for(var p:r.parts())assertThat(cs).contains(p.child());}
    @Test void conditionalParentRequiresRealCompleteStoredBody() {var first=candidate(a,1,1,"Table first");var second=candidate(b,2,2,"second");var cs=List.of(first.child(),second.child());var r=recovery().recover(ctx(),snapshot(cs),KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,List.of(first),parent("Table first\nsecond"),options(ContextRecoveryService.Strategy.CONDITIONAL_PARENT));assertThat(r.parts()).hasSize(2);assertThat(r.blocks().getFirst().completeParent()).isTrue();}
    @Test void missingOriginalPageCannotBeInventedByParentRestore() {var first=candidate(a,1,1,"Table first");var r=recovery().recover(ctx(),snapshot(List.of(first.child(),child(b,2,"second"))),KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,List.of(first),parent("Table first\nmissing original page\nsecond"),options(ContextRecoveryService.Strategy.CONDITIONAL_PARENT));assertThat(r.parts()).hasSize(1);assertThat(r.limitations()).contains("PARENT_INCOMPLETE_OR_INAPPLICABLE");}
    @Test void mergeDoesNotDuplicateParentOrShortDocument() {var first=candidate(a,1,1,"first");var second=candidate(b,2,2,"second");var r=run(ContextRecoveryService.Strategy.MERGE_HITS,List.of(first.child(),second.child()),List.of(first,second));assertThat(r.parts()).hasSize(2);assertThat(r.blocks()).hasSize(1);var shortDoc=run(ContextRecoveryService.Strategy.ADJACENT,List.of(first.child()),List.of(first));assertThat(shortDoc.parts()).hasSize(1);}
    @Test void countAndTokenBudgetsApplyToExpansionAndSeeds() {var first=candidate(a,1,1,"first");var r=recovery().recover(ctx(),snapshot(List.of(first.child(),child(b,2,"second"))),KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,List.of(first),parent("first\nsecond"),new ContextRecoveryService.Options(ContextRecoveryService.Strategy.ADJACENT,1,100,100));assertThat(r.parts()).hasSize(1);assertThat(r.outputTokens()).isLessThanOrEqualTo(100);var empty=recovery().recover(ctx(),snapshot(List.of(first.child())),KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,List.of(first),Map.of(),new ContextRecoveryService.Options(ContextRecoveryService.Strategy.NONE,10,1,100));assertThat(empty.parts()).isEmpty();}
    @Test void otherProjectAndParentReleaseCannotBeExpanded() {var seed=candidate(a,1,1,"first");var bad=new ScopedKnowledgeStore.Snapshot("stranger","project","test","f",List.of(seed.child()));assertThatThrownBy(()->recovery().recover(ctx(),bad,KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,List.of(seed),Map.of(),options(ContextRecoveryService.Strategy.ADJACENT))).hasMessage("NOT_FOUND_OR_DENIED");var wrong=Map.of(PARENT,new ContextRecoveryService.Parent(PARENT,DOCUMENT,UUID.randomUUID(),"owner","project","first"));assertThatThrownBy(()->recovery().recover(ctx(),snapshot(List.of(seed.child())),KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,List.of(seed),wrong,options(ContextRecoveryService.Strategy.ADJACENT))).hasMessage("CONTEXT_PARENT_SCOPE_INVALID");}
    @Test void modelAndVersionConflictsRemainHardBoundaries() {var seed=candidate(a,1,1,"first");var raw=child(b,2,"second");var meta=Json.MAPPER.createObjectNode();meta.putArray("device_models").add("CPU1215C");meta.putObject("applicability_scope").put("scope_level","MODEL");var other=new ScopedKnowledgeStore.Child(raw.id(),raw.documentId(),raw.parentId(),raw.releaseId(),raw.knowledgeId(),raw.chunkNo(),raw.page(),raw.section(),raw.title(),raw.documentVersion(),raw.content(),raw.dataNature(),raw.domain(),raw.deviceModel(),meta,raw.sourceLocations());var target=new ApplicabilityPolicy.Target("CPU1214C",null,null,null,null,null,null);var r=recovery().recover(ctx(),snapshot(List.of(seed.child(),other)),KnowledgeSearchService.KnowledgeFilters.NONE,target,List.of(seed),parent("first\nsecond"),options(ContextRecoveryService.Strategy.ADJACENT));assertThat(r.parts()).extracting(p->p.child().id()).containsExactly(a);}
    @Test void duplicateChildContentDoesNotConsumeBudgetTwice() {var seed=candidate(a,1,1,"first");var r=run(ContextRecoveryService.Strategy.ADJACENT,List.of(seed.child(),child(b,2,"first")),List.of(seed));assertThat(r.parts()).hasSize(1);}
    @Test void forgedSeedTextCannotReachExpansion() {var seed=candidate(a,1,1,"forged generated answer");assertThatThrownBy(()->run(ContextRecoveryService.Strategy.ADJACENT,List.of(child(a,1,"original")),List.of(seed))).hasMessage("CONTEXT_SEED_NOT_AUTHORIZED");}
    @Test void equalWordingInDifferentSectionsRemainsDistinctEvidence() {
        var seed=candidate(a,1,1,"normal");var raw=child(b,2,"normal");
        var other=new ScopedKnowledgeStore.Child(raw.id(),raw.documentId(),raw.parentId(),raw.releaseId(),raw.knowledgeId(),raw.chunkNo(),raw.page(),"other component",raw.title(),raw.documentVersion(),raw.content(),raw.dataNature(),raw.domain(),raw.deviceModel(),raw.metadata(),raw.sourceLocations());
        var otherSeed=new HybridRetrievalService.Candidate(other,seed.applicability(),List.of(),.7,2,1.0,2,null,.01,2);
        var r=recovery().recover(ctx(),snapshot(List.of(seed.child(),other)),KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,List.of(seed,otherSeed),Map.of(),options(ContextRecoveryService.Strategy.NONE));
        assertThat(r.parts()).hasSize(2);
    }
}
