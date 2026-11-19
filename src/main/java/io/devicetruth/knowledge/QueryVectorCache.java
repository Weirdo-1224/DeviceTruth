package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.capability.ToolContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Bounded process cache plus existing scoped PG cache; query inputs never reuse corpus cache keys. */
public final class QueryVectorCache {
    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddings;
    private final Map<String,float[]> memory=new LinkedHashMap<>(64,0.75f,true);
    public QueryVectorCache(JdbcTemplate jdbc,EmbeddingClient embeddings){this.jdbc=jdbc;this.embeddings=embeddings;}
    public synchronized float[] get(ToolContext context,String query) {
        var p=embeddings.profile();String config=KnowledgeCorpus.hash("QUERY-v1|"+p.profileVersion()+"|"+p.baseUrl()+"|"+p.sendDimensions());
        String input=KnowledgeCorpus.hash(query), key=context.ownerId()+"|"+context.projectId()+"|"+config+"|"+input;
        if(memory.containsKey(key))return memory.get(key).clone();
        var rows=jdbc.queryForList("SELECT embedding::text FROM dt_knowledge_embedding_cache WHERE owner_id=? AND project_id=? AND config_checksum=? AND input_checksum=? AND embedding_profile=? AND is_test=?",String.class,context.ownerId(),context.projectId(),config,input,p.profileVersion(),p.deterministic());
        float[] v;
        if(!rows.isEmpty()) {
            try{v=Json.MAPPER.readValue(rows.getFirst(),float[].class);}catch(Exception e){throw new EmbeddingException("QUERY_CACHE_INVALID");}
        } else {
            if (!p.deterministic() && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                throw new EmbeddingException("QUERY_NOT_PREPARED_OUTSIDE_TRANSACTION");
            v=embeddings.embed(query);EmbeddingVectors.validate(v,p.dimension());
            jdbc.update("INSERT INTO dt_knowledge_embedding_cache(owner_id,project_id,config_checksum,input_checksum,embedding_profile,embedding,is_test,created_at) VALUES (?,?,?,?,?,CAST(? AS vector),?,now()) ON CONFLICT DO NOTHING",context.ownerId(),context.projectId(),config,input,p.profileVersion(),VectorLiteral.of(v),p.deterministic());
        }
        EmbeddingVectors.validate(v,p.dimension());memory.put(key,v.clone());if(memory.size()>512)memory.remove(memory.keySet().iterator().next());return v.clone();
    }
}
