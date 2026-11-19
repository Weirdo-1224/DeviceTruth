package io.devicetruth.knowledge;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContextRecoveryBoundaryTest {
    static final UUID DOCUMENT=ContextRecoveryTest.DOCUMENT,PARENT=ContextRecoveryTest.PARENT,RELEASE=ContextRecoveryTest.RELEASE;
    final UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3);
    final ContextRecoveryTest fixture=new ContextRecoveryTest();
    io.devicetruth.core.capability.ToolContext ctx(){return fixture.ctx();}
    ScopedKnowledgeStore.Snapshot snapshot(List<ScopedKnowledgeStore.Child> c){return fixture.snapshot(c);}
    Map<UUID,ContextRecoveryService.Parent> parent(String text){return fixture.parent(text);}
    ContextRecoveryService recovery(){return fixture.recovery();}
    ContextRecoveryService.Options options(ContextRecoveryService.Strategy s){return fixture.options(s);}
    ScopedKnowledgeStore.Child child(UUID id,int no,String body){return ContextRecoveryTest.child(id,no,body);}
    HybridRetrievalService.Candidate candidate(UUID id,int no,int rank,String body){return ContextRecoveryTest.candidate(id,no,rank,body);}

    ScopedKnowledgeStore.Child section(UUID id,int no,String body,String path) {
        var old=child(id,no,body);
        return new ScopedKnowledgeStore.Child(id,old.documentId(),old.parentId(),old.releaseId(),old.knowledgeId(),no,null,path,
                old.title(),old.documentVersion(),body,old.dataNature(),old.domain(),old.deviceModel(),old.metadata(),old.sourceLocations());
    }
    ContextRecoveryService.Result expand(String body,List<ScopedKnowledgeStore.Child> children,int count,int tokens) {
        var first=children.getFirst();var seed=new HybridRetrievalService.Candidate(first,
                new ApplicabilityPolicy.Decision(ApplicabilityPolicy.Status.UNKNOWN_APPLICABILITY,false,List.of()),List.of(),.8,1,1.0,1,null,.01,1);
        return recovery().recover(ctx(),snapshot(children),KnowledgeSearchService.KnowledgeFilters.NONE,ApplicabilityPolicy.Target.UNKNOWN,
                List.of(seed),parent(body),new ContextRecoveryService.Options(ContextRecoveryService.Strategy.ADJACENT,count,tokens,10000));
    }
    @Test void sameStableSectionOccurrenceExpandsAndPreservesOriginalParts() {
        String body="# Diagnostics\nfirst\nsecond\n";
        var cs=List.of(section(a,1,"first\n","Diagnostics"),section(b,2,"second\n","Diagnostics"));
        var r=expand(body,cs,10,1000);assertThat(r.parts()).extracting(x->x.child()).containsExactlyElementsOf(cs);
        assertThat(r.parts().get(1).reason()).isEqualTo("ADJACENT_SAME_PARENT_SECTION");
    }
    @Test void sameDocumentTitleDoesNotAllowDifferentSections() {
        var r=expand("# A\nfirst\n# B\nsecond\n",List.of(section(a,1,"first\n","A"),section(b,2,"second\n","B")),10,1000);
        assertThat(r.parts()).extracting(x->x.child().id()).containsExactly(a);
    }
    @Test void repeatedSectionHeadingAtDifferentPositionsIsNotTheSameIdentity() {
        var r=expand("# Repeated\nfirst\n# Repeated\nsecond\n",List.of(section(a,1,"first\n","Repeated"),section(b,2,"second\n","Repeated")),10,1000);
        assertThat(r.parts()).hasSize(1);
    }
    @Test void fullMultilevelPathAndOccurrenceMustMatch() {
        String body="# A\n## Diagnostics\nfirst\nsecond\n# B\n## Diagnostics\nthird\n";
        var r=expand(body,List.of(section(a,1,"first\n","A / Diagnostics"),section(b,2,"second\n","A / Diagnostics"),section(c,3,"third\n","B / Diagnostics")),10,1000);
        assertThat(r.parts()).extracting(x->x.child().id()).containsExactly(a,b);
    }
    @Test void missingParentOrMissingSectionMetadataIsConservative() {
        var first=section(a,1,"first","");var second=section(b,2,"second","");
        assertThat(expand("first\nsecond",List.of(first,second),10,1000).parts()).hasSize(1);
        var seed=candidate(a,1,1,"first");
        assertThat(recovery().recover(ctx(),snapshot(List.of(seed.child(),child(b,2,"second"))),KnowledgeSearchService.KnowledgeFilters.NONE,
                ApplicabilityPolicy.Target.UNKNOWN,List.of(seed),Map.of(),options(ContextRecoveryService.Strategy.ADJACENT)).parts()).hasSize(1);
    }
    @Test void childSpanningMultipleHeadingsDoesNotExpand() {
        var r=expand("# A\nfirst\n# B\nsecond\nthird",List.of(section(a,1,"# A\nfirst\n# B\nsecond\n","A"),section(b,2,"third","B")),10,1000);
        assertThat(r.parts()).hasSize(1);
    }
    @Test void countAndTokenBudgetsStillApplyAtStableBoundary() {
        var cs=List.of(section(a,1,"first","A"),section(b,2,"second","A"));
        assertThat(expand("# A\nfirst\nsecond",cs,1,1000).parts()).hasSize(1);
        int cost=cs.getFirst().title().length()+1+cs.getFirst().content().length()+1;
        var r=expand("# A\nfirst\nsecond",cs,10,cost);assertThat(r.parts()).hasSize(1);assertThat(r.outputTokens()).isEqualTo(cost);
    }
    @Test void explicitVerifiedBodyOffsetsDisambiguateEqualText() {
        String body="# A\nnormal\n# A\nnormal\n";var x=section(a,1,"normal\n","A");var y=section(b,2,"normal\n","A");
        var p=new ContextRecoveryService.Parent(PARENT,DOCUMENT,RELEASE,"owner","project",body,
                Map.of(a,new StoredSectionBoundary.Span(4,11),b,new StoredSectionBoundary.Span(15,22)));
        assertThat(StoredSectionBoundary.identity(x,p)).isNotNull().isNotEqualTo(StoredSectionBoundary.identity(y,p));
    }
}
