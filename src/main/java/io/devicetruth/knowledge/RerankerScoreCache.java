package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import java.nio.file.*;
import java.util.*;

/** Cache a whole scoring request: supplier scores are relative to the submitted pool. */
public final class RerankerScoreCache implements RerankerClient {
    private final RerankerClient delegate;
    private final Path root;
    private final Map<String,Response> memory=new LinkedHashMap<>();
    public RerankerScoreCache(RerankerClient delegate,Path root){this.delegate=Objects.requireNonNull(delegate);this.root=Objects.requireNonNull(root);}
    public String configurationKey(){return delegate.configurationKey();}
    public String key(Request r) {
        return KnowledgeCorpus.hash(Json.write(List.of("rerank-request-cache-v1",configurationKey(),r.scope(),r.query(),r.instruction(),
                r.documents().stream().map(d->List.of(d.chunkId().toString(),KnowledgeCorpus.hash(d.text()))).toList())));
    }
    public synchronized Response score(Request r) {
        String key=key(r);Path file=root.resolve(key+".json");Response result=memory.get(key);
        if(result!=null){RerankerClient.validate(r,result);return hit(result);}
        try {
            if(Files.exists(file)) {
                var json=Json.MAPPER.readTree(file.toFile());
                if(!key.equals(json.path("key").asText()))throw new RerankerException("RERANK_CACHE_IDENTITY_INVALID");
                var payload=json.path("response");
                if(!KnowledgeCorpus.hash(Json.write(payload)).equals(json.path("checksum").asText()))throw new RerankerException("RERANK_CACHE_CORRUPT");
                result=Json.MAPPER.treeToValue(payload,Response.class);RerankerClient.validate(r,result);remember(key,result);return hit(result);
            }
            result=delegate.score(r);RerankerClient.validate(r,result);
            Files.createDirectories(root);Path temp=Files.createTempFile(root,key,".tmp");
            try {
                var payload=Json.MAPPER.valueToTree(result);
                Files.writeString(temp,Json.write(Map.of("key",key,"checksum",KnowledgeCorpus.hash(Json.write(payload)),"response",payload)));
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(temp);}
            remember(key,result);return result;
        }catch(RerankerException e){throw e;}
        catch(Exception e){throw new RerankerException("RERANK_CACHE_UNAVAILABLE");}
    }
    private void remember(String key,Response r){if(memory.size()>=1000)memory.remove(memory.keySet().iterator().next());memory.put(key,r);}
    private static Response hit(Response r){return new Response(r.scores(),r.requestId(),r.inputTokens(),0,0,true);}
}
