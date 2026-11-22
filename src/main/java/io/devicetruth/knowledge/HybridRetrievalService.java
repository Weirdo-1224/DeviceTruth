package io.devicetruth.knowledge;

import io.devicetruth.core.capability.ToolContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Candidate-only pipeline. No intermediate candidate can register a formal reference. */
public final class HybridRetrievalService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(HybridRetrievalService.class);
    public enum Mode { LEGACY, DENSE, BM25, IDENTIFIER, HYBRID_DUAL, HYBRID_TRIPLE }
    public enum DenseSearch { EXACT, HNSW }
    public record Configuration(int candidates,int rrfK,double denseWeight,double bm25Weight,double identifierWeight,
                                double denseMinSimilarity,DenseSearch denseSearch,boolean fullApplicability) {
        public static final Configuration DEFAULT=new Configuration(20,60,1,1,1,0.45,DenseSearch.EXACT,true);
        public Configuration {
            if(!Set.of(10,20,30).contains(candidates)||rrfK<1||denseSearch==null||!Double.isFinite(denseMinSimilarity)||denseMinSimilarity< -1||denseMinSimilarity>1
                    || java.util.stream.DoubleStream.of(denseWeight,bm25Weight,identifierWeight).anyMatch(x->!Double.isFinite(x)||x<=0))
                throw new IllegalArgumentException("invalid hybrid configuration");
        }
    }
    public record Trace(String requestedMode,List<String> actualChannels,Map<String,String> failures,String denseSearch,
                        boolean identifierSkipped,long elapsedMicros) {
        public Trace { actualChannels=List.copyOf(actualChannels);failures=Map.copyOf(failures); }
    }
    public record Candidate(ScopedKnowledgeStore.Child child,ApplicabilityPolicy.Decision applicability,
                            List<IdentifierExtractor.Identifier> matchedIdentifiers,Double denseScore,Integer denseRank,
                            Double bm25Score,Integer bm25Rank,Integer identifierRank,double rrfScore,int finalRank) {}
    public record Result(List<Candidate> candidates,Trace trace,ScopedKnowledgeStore.Snapshot snapshot) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ScopedKnowledgeStore store;
    private final KnowledgeVisibilityGuard visibility;
    private final QueryVectorCache cache;
    private final LuceneBm25Index index;
    private final Configuration config;
    private final Set<String> preparedIndexes = new HashSet<>();
    private final IdentifierExtractor extractor=new IdentifierExtractor();
    private final ApplicabilityPolicy policy=new ApplicabilityPolicy();
    public HybridRetrievalService(JdbcTemplate jdbc,TransactionTemplate tx,EmbeddingClient embeddings,LuceneBm25Index index,Configuration config) {
        this.jdbc=jdbc;this.tx=tx;this.store=new ScopedKnowledgeStore(jdbc,embeddings.profile());
        this.visibility=new KnowledgeVisibilityGuard(jdbc,embeddings.profile());
        this.cache=new QueryVectorCache(jdbc,embeddings);this.index=index;this.config=config;
    }
    public LuceneBm25Index.Report build(ToolContext context,KnowledgeSearchService.KnowledgeFilters filters){return index.build(store.load(context,filters));}
    public void prepareIndex(ToolContext context, KnowledgeSearchService.KnowledgeFilters filters) {
        prepareIndex(store.load(context, filters));
    }
    private synchronized void prepareIndex(ScopedKnowledgeStore.Snapshot snapshot) {
        if (snapshot.children().isEmpty()) return;
        String key = snapshot.owner() + "|" + snapshot.project() + "|" + snapshot.fingerprint();
        if (preparedIndexes.contains(key)) return;
        try { index.validate(snapshot, true); }
        catch (KnowledgeException missing) {
            if (missing.status() < 500) throw missing;
            index.build(snapshot);
        }
        if (preparedIndexes.size() >= 64) preparedIndexes.clear();
        preparedIndexes.add(key);
    }
    public Result retrieve(ToolContext context,String query,KnowledgeSearchService.KnowledgeFilters filters,int limit,Mode mode) {
        if(mode==null || mode==Mode.LEGACY)throw KnowledgeException.invalid("new retrieval mode required");
        long started=System.nanoTime();var f=filters==null?KnowledgeSearchService.KnowledgeFilters.NONE:filters;
        var snapshot=store.load(context,f);var eligible=new LinkedHashMap<UUID,ScopedKnowledgeStore.Child>();
        var decisions=new HashMap<UUID,ApplicabilityPolicy.Decision>();var lexicon=new TreeMap<String,IdentifierExtractor.Type>();
        for(var c:snapshot.children())lexicon.putAll(extractor.lexicon(c.metadata()));
        var ids=extractor.extract(query,lexicon);var target=resolveTarget(f,snapshot,ids);
        for(var c:snapshot.children()) {
            if(f.domain()!=null&&!f.domain().equals(c.domain()) || f.documentId()!=null&&!f.documentId().equals(c.documentId()))continue;
            var decision=policy.assess(c.metadata(),target);
            if(decision.status()==ApplicabilityPolicy.Status.NOT_APPLICABLE){LOG.debug("Excluded child {}: {}",c.id(),decision.limitations());continue;}
            // Old flat uploads have no corpus metadata; their explicit model still enforces scope.
            if(c.metadata().isEmpty() && f.deviceModel()!=null && !Objects.equals(f.deviceModel(),c.deviceModel()))continue;
            eligible.put(c.id(),c);decisions.put(c.id(),decision);
        }
        var dense=List.<LuceneBm25Index.Scored>of();var bm25=List.<LuceneBm25Index.Scored>of();var identifier=List.<LuceneBm25Index.Scored>of();
        var used=new ArrayList<String>();var failures=new LinkedHashMap<String,String>();boolean skip=ids.isEmpty();
        if(!eligible.isEmpty()) {
            if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                    && Set.of(Mode.BM25, Mode.IDENTIFIER, Mode.HYBRID_DUAL, Mode.HYBRID_TRIPLE).contains(mode))
                prepareIndex(snapshot);
            if(Set.of(Mode.DENSE,Mode.HYBRID_DUAL,Mode.HYBRID_TRIPLE).contains(mode)) {
                try{dense=dense(context,query,eligible.keySet());used.add("DENSE");}
                catch(EmbeddingException e){failures.put("DENSE",e.code());}
                catch(KnowledgeException e){if(!e.code().startsWith("EMBEDDING"))throw e;failures.put("DENSE",e.code());}
            }
            if(Set.of(Mode.BM25,Mode.HYBRID_DUAL,Mode.HYBRID_TRIPLE).contains(mode)) {
                try{bm25=index.bm25(snapshot,eligible.keySet(),query,config.candidates());used.add("BM25");}
                catch(KnowledgeException e){if(e.status()<500)throw e;failures.put("BM25",e.code());}
            }
            if(!skip && Set.of(Mode.IDENTIFIER,Mode.HYBRID_TRIPLE).contains(mode)) {
                try{identifier=index.identifier(snapshot,eligible.keySet(),ids,config.candidates());used.add("IDENTIFIER");}
                catch(KnowledgeException e){failures.put("IDENTIFIER",e.code());}
            }
        }
        var dr=scores(dense);var br=scores(bm25);var fused=ReciprocalRankFusion.fuse(keys(dense),keys(bm25),keys(identifier),config.rrfK(),config.denseWeight(),config.bm25Weight(),config.identifierWeight());
        var ordered=new ArrayList<>(fused);
        if(config.fullApplicability())ordered.sort(Comparator.<ReciprocalRankFusion.Ranked,Boolean>comparing(r->decisions.get(r.childId()).verifiedApplicable()).reversed()
                .thenComparing(Comparator.comparingDouble(ReciprocalRankFusion.Ranked::score).reversed()).thenComparing(r->r.childId().toString()));
        var result=new ArrayList<Candidate>();
        for(var r:ordered.stream().limit(limit).toList()) {
            var child=eligible.get(r.childId());String text=child.content()+"\n"+child.title()+"\n"+child.metadata().toString();
            var available=extractor.extract(text,lexicon).stream().map(IdentifierExtractor.Identifier::key).collect(java.util.stream.Collectors.toSet());
            var matched=ids.stream().filter(i->available.contains(i.key())).toList();
            result.add(new Candidate(child,decisions.get(child.id()),matched,dr.get(child.id()),r.denseRank(),br.get(child.id()),r.bm25Rank(),r.identifierRank(),r.score(),result.size()+1));
        }
        if(!failures.isEmpty())LOG.warn("Retrieval {} used {} with channel failures {}",mode,used,failures);
        LOG.debug("Retrieval {} dense={} bm25={} identifier={} final={}",mode,keys(dense),keys(bm25),keys(identifier),result.stream().map(c->c.child().id()).toList());
        return new Result(List.copyOf(result),new Trace(mode.name(),used,failures,config.denseSearch().name(),skip,(System.nanoTime()-started)/1000),snapshot);
    }
    public void revalidate(ToolContext context,KnowledgeSearchService.KnowledgeFilters filters,Result result) {
        visibility.validate(context,filters,result.snapshot(),
                result.candidates().stream().map(c->c.child().id()).toList());
    }
    static ApplicabilityPolicy.Target resolveTarget(KnowledgeSearchService.KnowledgeFilters f,ScopedKnowledgeStore.Snapshot s,List<IdentifierExtractor.Identifier> ids) {
        var t=f.applicability();
        String model=f.deviceModel();
        if(model!=null && model.startsWith("model-")) {
            final String key=model;
            model=s.children().stream().flatMap(c->ApplicabilityPolicy.strings(c.metadata().path("device_models")).stream())
                    .filter(x->KnowledgeCorpus.modelKey(x).equals(key)).findFirst().orElse(model);
        }
        if(t!=null && model!=null && t.model()!=null && !IdentifierExtractor.normalize(model).equals(IdentifierExtractor.normalize(t.model())))
            throw KnowledgeException.invalid("conflicting model filters");
        if(t==null)t=ApplicabilityPolicy.Target.UNKNOWN;
        if(t.model()!=null)model=t.model();
        return new ApplicabilityPolicy.Target(model==null?unique(ids,IdentifierExtractor.Type.MODEL):model,
                t.family()==null?unique(ids,IdentifierExtractor.Type.FAMILY):t.family(),
                t.manufacturer()==null?unique(ids,IdentifierExtractor.Type.MANUFACTURER):t.manufacturer(),
                t.firmwareVersion()==null?unique(ids,IdentifierExtractor.Type.FIRMWARE):t.firmwareVersion(),
                t.softwareVersion()==null?unique(ids,IdentifierExtractor.Type.SOFTWARE):t.softwareVersion(),t.modelYear(),t.market());
    }
    private static String unique(List<IdentifierExtractor.Identifier> ids,IdentifierExtractor.Type type) {
        var values=ids.stream().filter(i->i.type()==type).map(IdentifierExtractor.Identifier::original).distinct().toList();
        return values.size()==1?values.getFirst():null;
    }
    private List<LuceneBm25Index.Scored> dense(ToolContext context,String query,Set<UUID> eligible) {
        return denseVector(context,cache.get(context,query),eligible);
    }
    /** Exact/HNSW engineering comparison on an already available vector; makes no API request. */
    List<LuceneBm25Index.Scored> denseVector(ToolContext context,float[] queryVector,Set<UUID> eligible) {
        if(eligible.isEmpty())return List.of();
        EmbeddingVectors.validate(queryVector,KnowledgeIndexSchema.vectorDimension(jdbc));
        String vector=VectorLiteral.of(queryVector);
        String in=String.join(",",Collections.nCopies(eligible.size(),"?"));
        String where="c.owner_id=? AND c.project_id=? AND c.id IN ("+in+")";
        var args=new ArrayList<Object>();args.add(vector);args.add(context.ownerId());args.add(context.projectId());args.addAll(eligible);args.add(vector);args.add(config.candidates());
        String sql=config.denseSearch()==DenseSearch.EXACT
                ?"WITH eligible AS MATERIALIZED (SELECT c.id,c.embedding FROM dt_knowledge_chunk c WHERE "+where+") SELECT id,1-(embedding <=> CAST(? AS vector)) AS score FROM eligible ORDER BY embedding <=> CAST(? AS vector),id LIMIT ?"
                :"SELECT c.id,1-(c.embedding <=> CAST(? AS vector)) AS score FROM dt_knowledge_chunk c WHERE "+where+" ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?";
        if(config.denseSearch()==DenseSearch.EXACT){args.clear();args.add(context.ownerId());args.add(context.projectId());args.addAll(eligible);args.add(vector);args.add(vector);args.add(config.candidates());}
        final List<Object> parameters=args;
        List<LuceneBm25Index.Scored> rows;
        if(config.denseSearch()==DenseSearch.HNSW)rows=tx.execute(status->{
            String previous=jdbc.queryForObject("SHOW enable_seqscan",String.class);
            String scan=jdbc.queryForObject("SELECT COALESCE(current_setting('hnsw.iterative_scan',true),'off')",String.class);
            try {
                jdbc.execute("SET LOCAL enable_seqscan=off");jdbc.execute("SET LOCAL hnsw.iterative_scan=strict_order");
                return jdbc.query(sql,(r,n)->new LuceneBm25Index.Scored(r.getObject("id",UUID.class),r.getDouble("score")),parameters.toArray());
            }finally {jdbc.execute("SET LOCAL enable_seqscan="+("on".equals(previous)?"on":"off"));jdbc.execute("SET LOCAL hnsw.iterative_scan="+scan);}
        });
        else rows=jdbc.query(sql,(r,n)->new LuceneBm25Index.Scored(r.getObject("id",UUID.class),r.getDouble("score")),parameters.toArray());
        return rows.stream().filter(r->r.score()>=config.denseMinSimilarity()).sorted(Comparator.comparingDouble(LuceneBm25Index.Scored::score).reversed().thenComparing(r->r.childId().toString())).toList();
    }
    private static List<UUID> keys(List<LuceneBm25Index.Scored> list){return list.stream().map(LuceneBm25Index.Scored::childId).toList();}
    private static Map<UUID,Double> scores(List<LuceneBm25Index.Scored> list){var result=new HashMap<UUID,Double>();list.forEach(r->result.put(r.childId(),r.score()));return result;}
}
