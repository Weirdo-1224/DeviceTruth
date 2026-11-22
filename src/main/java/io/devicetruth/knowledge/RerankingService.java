package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import java.util.*;

/** RRF identity/ranks remain auditable; failures retain the input order exactly. */
public final class RerankingService {
    public record Ranked(HybridRetrievalService.Candidate candidate,int rrfRank,Double rerankerScore,Integer rerankerRank,int finalRank) {}
    public record Result(List<Ranked> ranked,boolean degraded,String reason,RerankerClient.Response usage,long elapsedMillis) {
        public Result {ranked=List.copyOf(ranked);}
    }
    private final RerankerClient client;
    public RerankingService(RerankerClient client){this.client=Objects.requireNonNull(client);}
    /** Only deterministic stored metadata is included; no generated summaries or evaluation labels. */
    public static String documentText(ScopedKnowledgeStore.Child c) {
        var m=new LinkedHashMap<String,Object>();
        for(String k:List.of("manufacturer","device_models","applicable_versions","source_ids","scope_level"))
            if(c.metadata().has(k))m.put(k,c.metadata().get(k));
        return "Title: "+c.title()+"\nSource metadata (document assertions, not verified field facts): "+Json.write(m)+"\nOriginal child:\n"+c.content();
    }
    public Result rank(RerankerClient.Scope scope,String query,String instruction,List<HybridRetrievalService.Candidate> candidates) {
        long start=System.nanoTime();
        var originals=new LinkedHashMap<UUID,Integer>();
        for(var c:candidates)if(originals.put(c.child().id(),originals.size()+1)!=null)throw new RerankerException("RERANK_DUPLICATE_CANDIDATE");
        var request=new RerankerClient.Request(scope,query,instruction,candidates.stream().map(c->new RerankerClient.Document(c.child().id(),documentText(c.child()))).toList());
        try {
            var response=client.score(request);RerankerClient.validate(request,response);
            var scores=new HashMap<UUID,Double>();response.scores().forEach(s->scores.put(s.chunkId(),s.score()));
            var sorted=candidates.stream().sorted(Comparator.<HybridRetrievalService.Candidate>comparingDouble(c->scores.get(c.child().id())).reversed()
                    .thenComparingInt(c->originals.get(c.child().id())).thenComparing(c->c.child().id().toString())).toList();
            var out=new ArrayList<Ranked>();for(var c:sorted)out.add(new Ranked(c,originals.get(c.child().id()),scores.get(c.child().id()),out.size()+1,out.size()+1));
            return new Result(out,false,"NONE",response,(System.nanoTime()-start)/1_000_000);
        }catch(RerankerException e){return fallback(candidates,e.code(),start);}
        catch(RuntimeException e){return fallback(candidates,"RERANK_UNAVAILABLE",start);}
    }
    private static Result fallback(List<HybridRetrievalService.Candidate> candidates,String reason,long start) {
        var out=new ArrayList<Ranked>();for(var c:candidates)out.add(new Ranked(c,out.size()+1,null,null,out.size()+1));
        return new Result(out,true,reason,null,(System.nanoTime()-start)/1_000_000);
    }
}
