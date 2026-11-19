package io.devicetruth.knowledge;

import io.devicetruth.core.capability.ToolContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit opt-in candidate reranking and context recovery. No reference writes here. */
public final class EnhancedRetrievalService {
    public enum Range { TOP_20, FULL_POOL }
    public record Options(boolean rerank,String instruction,Range range,ContextRecoveryService.Options context) {
        public Options {Objects.requireNonNull(range);Objects.requireNonNull(context);}
    }
    public record Result(HybridRetrievalService.Result retrieval,RerankingService.Result reranking,ContextRecoveryService.Result context) {}
    private final HybridRetrievalService.Mode mode;
    private final JdbcTemplate jdbc;
    private final HybridRetrievalService hybrid;
    private final RerankingService reranker;
    private final ContextRecoveryService recovery;
    public EnhancedRetrievalService(JdbcTemplate jdbc,HybridRetrievalService hybrid,RerankingService reranker,ContextRecoveryService recovery) {
        this(jdbc,hybrid,reranker,recovery,HybridRetrievalService.Mode.HYBRID_DUAL);
    }
    /** Evaluation opt-in reuses existing modes; the original constructor remains dual retrieval. */
    public EnhancedRetrievalService(JdbcTemplate jdbc,HybridRetrievalService hybrid,RerankingService reranker,ContextRecoveryService recovery,HybridRetrievalService.Mode mode) {
        if(mode!=HybridRetrievalService.Mode.DENSE&&mode!=HybridRetrievalService.Mode.HYBRID_DUAL)throw new IllegalArgumentException("ENHANCED_MODE_INVALID");
        this.mode=mode;
        this.jdbc=Objects.requireNonNull(jdbc);this.hybrid=Objects.requireNonNull(hybrid);this.reranker=reranker;this.recovery=Objects.requireNonNull(recovery);
    }
    public Result retrieve(ToolContext ctx,String query,KnowledgeSearchService.KnowledgeFilters filters,Options options) {
        // Each lane remains at its configured budget (20); full fusion union can contain 40 children.
        var result=hybrid.retrieve(ctx,query,filters,Integer.MAX_VALUE,mode);
        var f=filters==null?KnowledgeSearchService.KnowledgeFilters.NONE:filters;var snap=result.snapshot();
        var extractor=new IdentifierExtractor();var lex=new TreeMap<String,IdentifierExtractor.Type>();snap.children().forEach(c->lex.putAll(extractor.lexicon(c.metadata())));
        var target=HybridRetrievalService.resolveTarget(f,snap,extractor.extract(query,lex));
        var pool=options.range()==Range.TOP_20?result.candidates().stream().limit(20).toList():result.candidates();
        // This validation is also required before sending any text to an external provider.
        recovery.recover(ctx,snap,f,target,pool,Map.of(),new ContextRecoveryService.Options(ContextRecoveryService.Strategy.NONE,50,Integer.MAX_VALUE,Integer.MAX_VALUE));
        if(pool.stream().map(c->c.child().releaseId()).distinct().count()>1)throw new KnowledgeException("RERANK_SINGLE_RELEASE_REQUIRED",409);
        RerankingService.Result ranked=null;List<HybridRetrievalService.Candidate> ordered=pool;
        if(options.rerank()) {
            if(reranker==null)throw new KnowledgeException("RERANK_NOT_CONFIGURED",503);
            String release=pool.isEmpty()?"EMPTY":Objects.toString(pool.getFirst().child().releaseId(),"LEGACY_FLAT");
            ranked=reranker.rank(new RerankerClient.Scope(ctx.ownerId(),ctx.projectId(),release),query,options.instruction(),pool);
            ordered=ranked.ranked().stream().map(RerankingService.Ranked::candidate).toList();
        }
        var parents=loadParents(ctx,snap);
        var recovered=recovery.recover(ctx,snap,f,target,ordered,parents,options.context());
        var byId=new HashMap<UUID,HybridRetrievalService.Candidate>();pool.forEach(c->byId.put(c.child().id(),c));var output=new ArrayList<HybridRetrievalService.Candidate>();
        for(var p:recovered.parts()) {
            var old=byId.get(p.child().id());var decision=new ApplicabilityPolicy().assess(p.child().metadata(),target);
            output.add(new HybridRetrievalService.Candidate(p.child(),decision,old==null?List.of():old.matchedIdentifiers(),old==null?null:old.denseScore(),old==null?null:old.denseRank(),old==null?null:old.bm25Score(),old==null?null:old.bm25Rank(),null,old==null?0:old.rrfScore(),p.finalRank()));
        }
        var errors=new LinkedHashMap<>(result.trace().failures());if(ranked!=null&&ranked.degraded())errors.put("RERANKER",ranked.reason());
        var t=result.trace();var trace=new HybridRetrievalService.Trace(t.requestedMode(),t.actualChannels(),errors,t.denseSearch(),t.identifierSkipped(),t.elapsedMicros());
        return new Result(new HybridRetrievalService.Result(List.copyOf(output),trace,snap),ranked,recovered);
    }
    public Map<UUID,ContextRecoveryService.Parent> loadParents(ToolContext ctx,ScopedKnowledgeStore.Snapshot snapshot) {
        if(!ctx.ownerId().equals(snapshot.owner())||!ctx.projectId().equals(snapshot.project()))throw new KnowledgeException("NOT_FOUND_OR_DENIED",404);
        var parents=new LinkedHashMap<UUID,ContextRecoveryService.Parent>();
        var ids=snapshot.children().stream().map(ScopedKnowledgeStore.Child::parentId).filter(Objects::nonNull).distinct().toList();if(ids.isEmpty())return parents;
        var args=new ArrayList<Object>(List.of(ctx.ownerId(),ctx.projectId()));args.addAll(ids);
        jdbc.query("SELECT id,document_id,release_id,owner_id,project_id,content FROM dt_knowledge_parent WHERE owner_id=? AND project_id=? AND id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",r->{
            var p=new ContextRecoveryService.Parent(r.getObject("id",UUID.class),r.getObject("document_id",UUID.class),r.getObject("release_id",UUID.class),r.getString("owner_id"),r.getString("project_id"),r.getString("content"));parents.put(p.id(),p);
        },args.toArray());
        // Existing body spans are original UTF-16 Parent offsets; no corpus/index mutation.
        var spans=new HashMap<UUID,Map<UUID,StoredSectionBoundary.Span>>();
        jdbc.query("SELECT id,parent_id,body_start,body_end FROM dt_knowledge_chunk WHERE owner_id=? AND project_id=? AND parent_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",r->{
            Integer start=r.getObject("body_start",Integer.class),end=r.getObject("body_end",Integer.class);
            if(start!=null&&end!=null)spans.computeIfAbsent(r.getObject("parent_id",UUID.class),k->new HashMap<>()).put(r.getObject("id",UUID.class),new StoredSectionBoundary.Span(start,end));
        },args.toArray());
        parents.replaceAll((id,p)->new ContextRecoveryService.Parent(p.id(),p.documentId(),p.releaseId(),p.owner(),p.project(),p.content(),spans.getOrDefault(id,Map.of())));
        return parents;
    }
}
