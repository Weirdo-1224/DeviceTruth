package io.devicetruth.knowledge;

import java.util.*;

/** Scoring only. Input identity is independent of supplier response order. */
public interface RerankerClient {
    String DOMAIN_INSTRUCTION="Retrieve technical evidence that directly supports equipment fault investigation, including applicable diagnostic criteria, required observations, configuration constraints, and verification requirements.";
    record Scope(String owner,String project,String release) {
        public Scope {if(owner==null||project==null||release==null)throw new IllegalArgumentException("RERANK_SCOPE_REQUIRED");}
    }
    record Document(UUID chunkId,String text) {
        public Document {if(chunkId==null||text==null||text.isBlank())throw new IllegalArgumentException("RERANK_DOCUMENT_REQUIRED");}
    }
    record Request(Scope scope,String query,String instruction,List<Document> documents) {
        public Request {
            Objects.requireNonNull(scope);if(query==null||query.isBlank())throw new IllegalArgumentException("RERANK_QUERY_REQUIRED");
            instruction=instruction==null?"":instruction;documents=List.copyOf(documents);
            if(documents.size()>500||documents.stream().map(Document::chunkId).distinct().count()!=documents.size())
                throw new IllegalArgumentException("RERANK_CANDIDATE_IDENTITIES_INVALID");
        }
    }
    record Score(int index,UUID chunkId,double score) {}
    record Response(List<Score> scores,String requestId,Long inputTokens,int attempts,long elapsedMillis,boolean cacheHit) {
        public Response { scores=List.copyOf(scores); }
    }
    String configurationKey();
    Response score(Request request);
    static void validate(Request request,Response response) {
        if(response==null||response.scores().size()!=request.documents().size())throw new RerankerException("RERANK_MISSING_SCORES");
        var indices=new HashSet<Integer>();
        for(var s:response.scores()) {
            if(s.index()<0||s.index()>=request.documents().size()||!indices.add(s.index()))throw new RerankerException("RERANK_INVALID_INDEX");
            if(!request.documents().get(s.index()).chunkId().equals(s.chunkId()))throw new RerankerException("RERANK_IDENTITY_MISMATCH");
            if(!Double.isFinite(s.score())||s.score()<0||s.score()>1)throw new RerankerException("RERANK_INVALID_SCORE");
        }
        if(response.inputTokens()!=null&&response.inputTokens()<0)throw new RerankerException("RERANK_INVALID_USAGE");
    }
}
