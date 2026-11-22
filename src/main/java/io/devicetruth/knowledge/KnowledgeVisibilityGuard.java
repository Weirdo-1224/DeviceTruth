package io.devicetruth.knowledge;

import io.devicetruth.core.capability.ToolContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Scope lock -> ordered release locks -> ordered source locks -> fresh visibility check.
 * The caller registers citations in the SAME transaction. No provider call is allowed here.
 * Release switches take the exclusive scope lock before touching any source row.
 */
public final class KnowledgeVisibilityGuard {
    private final JdbcTemplate jdbc;
    private final ScopedKnowledgeStore store;
    public KnowledgeVisibilityGuard(JdbcTemplate jdbc,EmbeddingProfile profile) {
        this.jdbc=jdbc;this.store=new ScopedKnowledgeStore(jdbc,profile);
    }
    public static void lockScope(JdbcTemplate jdbc,String owner,String project,boolean exclusive) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("KNOWLEDGE_VISIBILITY_TRANSACTION_REQUIRED");
        long key=KnowledgeCorpus.stableId(owner+"|"+project).getMostSignificantBits();
        jdbc.queryForObject("SELECT "+(exclusive?"pg_advisory_xact_lock":"pg_advisory_xact_lock_shared")+"(?)",Object.class,key);
    }
    public void validate(ToolContext context,KnowledgeSearchService.KnowledgeFilters filters,
                         ScopedKnowledgeStore.Snapshot original,List<UUID> ids) {
        if(!context.ownerId().equals(original.owner())||!context.projectId().equals(original.project()))
            throw new KnowledgeException("NOT_FOUND_OR_DENIED",404);
        lockScope(jdbc,context.ownerId(),context.projectId(),false);
        var byId=new HashMap<UUID,ScopedKnowledgeStore.Child>();original.children().forEach(c->byId.put(c.id(),c));
        var selected=ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).map(id->{
            var c=byId.get(id);if(c==null)throw new KnowledgeException("KNOWLEDGE_SNAPSHOT_CHANGED",409);return c;
        }).toList();
        for(var release:selected.stream().map(ScopedKnowledgeStore.Child::releaseId).filter(Objects::nonNull).distinct().sorted(Comparator.comparing(UUID::toString)).toList())
            jdbc.queryForList("SELECT id FROM dt_knowledge_release WHERE id=? AND owner_id=? AND project_id=? FOR SHARE",release,context.ownerId(),context.projectId());
        for(var c:selected)
            jdbc.queryForList("SELECT c.id FROM dt_knowledge_chunk c JOIN dt_knowledge_document d ON d.id=c.document_id AND d.owner_id=c.owner_id AND d.project_id=c.project_id WHERE c.id=? AND c.owner_id=? AND c.project_id=? FOR SHARE OF c,d",c.id(),context.ownerId(),context.projectId());
        if(!store.load(context,filters).fingerprint().equals(original.fingerprint()))
            throw new KnowledgeException("KNOWLEDGE_SNAPSHOT_CHANGED",409);
    }
}
