package io.devicetruth.knowledge;

import io.devicetruth.core.capability.ToolContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Actual isolated PG and Lucene; local scoring substitute, not supplier quality evidence. */
class RetrievalEnhancementIntegrationTest {
    @TempDir Path temp;
    final HybridRetrievalIntegrationTest fixture=new HybridRetrievalIntegrationTest();
    @BeforeAll static void start()throws Exception{HybridRetrievalIntegrationTest.start();}
    @AfterAll static void stop()throws Exception{HybridRetrievalIntegrationTest.stop();}
    HybridRetrievalService hybrid(){return new HybridRetrievalService(HybridRetrievalIntegrationTest.jdbc,HybridRetrievalIntegrationTest.tx,HybridRetrievalIntegrationTest.vectors,new LuceneBm25Index(temp),new HybridRetrievalService.Configuration(20,60,1,1,1,.45,HybridRetrievalService.DenseSearch.EXACT,false));}
    KnowledgeSearchService search(HybridRetrievalService h){return new KnowledgeSearchService(HybridRetrievalIntegrationTest.jdbc,HybridRetrievalIntegrationTest.tx,HybridRetrievalIntegrationTest.vectors,3,.45).withHybrid(h,HybridRetrievalService.Mode.LEGACY);}
    RerankerClient scorer(AtomicInteger calls,boolean fail){return new RerankerClient(){public String configurationKey(){return "CONTRACT_ONLY";}public Response score(Request r){calls.incrementAndGet();if(fail)throw new RerankerException("RERANK_TIMEOUT_OR_IO");var scores=new ArrayList<Score>();for(int i=0;i<r.documents().size();i++)scores.add(new Score(i,r.documents().get(i).chunkId(),.5));return new Response(scores,"CONTRACT_ONLY",0L,1,1,false);}};}
    EnhancedRetrievalService enhanced(HybridRetrievalService h,RerankerClient c){return new EnhancedRetrievalService(HybridRetrievalIntegrationTest.jdbc,h,new RerankingService(c),new ContextRecoveryService(ContextualKnowledgeChunker::tokens,"CL100K_BASE_PROXY"));}
    EnhancedRetrievalService.Options options(ContextRecoveryService.Strategy strategy){return new EnhancedRetrievalService.Options(true,"",EnhancedRetrievalService.Range.FULL_POOL,new ContextRecoveryService.Options(strategy,10,4096,2048));}
    @Test void onlyReturnedOriginalChildrenRegisterReferencesAndParentBlocksResolve() {
        var ctx=fixture.context("owner","project");var h=hybrid();var f=fixture.filters(HybridRetrievalIntegrationTest.p);h.build(ctx,f);var calls=new AtomicInteger();var service=search(h);
        var result=service.searchEnhanced(ctx,"PROFINET",f,enhanced(h,scorer(calls,false)),options(ContextRecoveryService.Strategy.ADJACENT));
        assertThat(calls.get()).isEqualTo(1);assertThat(result.evidence().hits()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(HybridRetrievalIntegrationTest.jdbc.queryForObject("SELECT count(*) FROM dt_evidence_reference WHERE run_id=?",Integer.class,ctx.runId())).isEqualTo(result.evidence().hits().size());
        assertThat(result.context().parts()).extracting(p->p.child().id()).containsExactlyElementsOf(result.evidence().hits().stream().map(KnowledgeSearchService.KnowledgeHit::chunkId).toList());
        for(var hit:result.evidence().hits()) {
            assertThat(hit.releaseId()).isEqualTo(HybridRetrievalIntegrationTest.p.releaseId());
            assertThat(service.excerptByReference(ctx,hit.referenceId()).content()).isEqualTo(hit.content());
            assertThat(hit.content()).isEqualTo(HybridRetrievalIntegrationTest.jdbc.queryForObject("SELECT content FROM dt_knowledge_chunk WHERE id=?",String.class,hit.chunkId()));
        }
        String trace=io.devicetruth.adapter.Json.write(result.ranking());
        assertThat(trace).doesNotContain("content", "original child", "metadata");
    }
    @Test void unauthorizedRunRejectedBeforeExternalScoring() {
        var ctx=fixture.context("owner","project");var h=hybrid();var calls=new AtomicInteger();
        var bad=new ToolContext("stranger","project",ctx.conversationId(),ctx.runId(),null,Instant.now(),null,Set.of(KnowledgeTools.SEARCH));
        assertThatThrownBy(()->search(h).searchEnhanced(bad,"PROFINET",fixture.filters(HybridRetrievalIntegrationTest.p),enhanced(h,scorer(calls,false)),options(ContextRecoveryService.Strategy.NONE))).hasMessage("NOT_FOUND_OR_DENIED");assertThat(calls.get()).isZero();
    }
    @Test void inactiveReleaseDefaultAndOtherProjectNeverReachScoringDocuments() {
        var ctx=fixture.context("owner","project");var h=hybrid();var calls=new AtomicInteger();var e=enhanced(h,scorer(calls,false));
        var r=search(h).searchEnhanced(ctx,"PROFINET",KnowledgeSearchService.KnowledgeFilters.NONE,e,options(ContextRecoveryService.Strategy.ADJACENT));
        assertThat(r.evidence().hits()).isEmpty();
        var other=fixture.context("owner","other-project");var r2=search(h).searchEnhanced(other,"PROFINET",fixture.filters(HybridRetrievalIntegrationTest.p),e,options(ContextRecoveryService.Strategy.ADJACENT));assertThat(r2.evidence().hits()).isEmpty();
    }
    @Test void timeoutFallsBackToSameRrfAndNoIntermediateReferences() {
        var ctx=fixture.context("owner","project");var h=hybrid();var f=fixture.filters(HybridRetrievalIntegrationTest.p);h.build(ctx,f);
        var expected=h.retrieve(ctx,"PROFINET",f,3,HybridRetrievalService.Mode.HYBRID_DUAL).candidates().stream().map(c->c.child().id()).toList();
        var r=search(h).searchEnhanced(ctx,"PROFINET",f,enhanced(h,scorer(new AtomicInteger(),true)),options(ContextRecoveryService.Strategy.NONE));
        assertThat(r.ranking().degraded()).isTrue();assertThat(r.evidence().trace().failures()).containsEntry("RERANKER","RERANK_TIMEOUT_OR_IO");
        assertThat(r.evidence().hits()).extracting(KnowledgeSearchService.KnowledgeHit::chunkId).containsExactlyElementsOf(expected);
    }
    @Test void firmwareModelConflictsExcludedBeforeScoringAndExpansion() {
        var ctx=fixture.context("owner","project");var h=hybrid();var f=fixture.filters(HybridRetrievalIntegrationTest.p);h.build(ctx,f);
        var filtered=new KnowledgeSearchService.KnowledgeFilters(null,null,null,f.releaseId(),f.indexProfile(),new ApplicabilityPolicy.Target("CPU1214C",null,"Siemens","4.7",null,null,null));
        var r=search(h).searchEnhanced(ctx,"PROFINET",filtered,enhanced(h,scorer(new AtomicInteger(),false)),options(ContextRecoveryService.Strategy.ADJACENT));
        assertThat(r.evidence().hits()).allMatch(hit->hit.knowledgeId().equals("TEST-G"));
    }
    @Test void multipleStoredChildrenCanRestoreParentWithoutCitingHiddenText() {
        var body=String.join("\n\n",List.of("# Table diagnostic context",("CPU 1214C PROFINET diagnostic evidence observations ").repeat(70),("Original configuration and required verification ").repeat(70)));
        var doc=HybridRetrievalIntegrationTest.doc("TEST-MULTI","CPU 1214C",body);
        var plan=HybridRetrievalIntegrationTest.plan("project","multi-child-contract",List.of(doc));
        new KnowledgeCorpusService(HybridRetrievalIntegrationTest.jdbc,HybridRetrievalIntegrationTest.tx,HybridRetrievalIntegrationTest.vectors,true).importPlan(plan);
        var ctx=fixture.context("owner","project");var h=hybrid();var f=fixture.filters(plan);h.build(ctx,f);
        var r=search(h).searchEnhanced(ctx,"PROFINET",f,enhanced(h,scorer(new AtomicInteger(),false)),options(ContextRecoveryService.Strategy.CONDITIONAL_PARENT));
        assertThat(r.context().parts().size()).isGreaterThan(1);assertThat(r.context().blocks()).hasSize(1);
        assertThat(r.context().blocks().getFirst().completeParent()).isTrue();
        assertThat(r.evidence().hits()).hasSameSizeAs(r.context().parts());
    }
}
