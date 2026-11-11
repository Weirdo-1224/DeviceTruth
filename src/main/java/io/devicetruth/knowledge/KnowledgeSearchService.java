package io.devicetruth.knowledge;

import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.Checks;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 受权限约束的向量召回与引用读取。
 * owner/project（以及可选领域/型号/文档）条件在 SQL 中先过滤候选，再做向量排序；
 * 每次返回给 run 的片段都在同一事务登记 evidence_reference，原文读取必须命中该 run 已取集合。
 */
public final class KnowledgeSearchService {
    public static final int MAX_QUERY_CHARS = 2000;

    /** 强类型嵌套过滤：只允许领域、设备型号与文档 ID，其他键在工具层即拒绝。 */
    public record KnowledgeFilters(String domain, String deviceModel, UUID documentId) {
        public static final KnowledgeFilters NONE = new KnowledgeFilters(null, null, null);

        public KnowledgeFilters {
            domain = domain == null || domain.isBlank() ? null : Checks.id(domain, "domain");
            deviceModel = deviceModel == null || deviceModel.isBlank() ? null : Checks.id(deviceModel, "deviceModel");
        }
    }

    public record KnowledgeHit(UUID referenceId, UUID documentId, String documentVersion, UUID chunkId,
                               int chunkNo, Integer page, String section, String title, String content,
                               double similarity, String dataNature) {}

    public record Excerpt(UUID referenceId, UUID documentId, String documentVersion, UUID chunkId, int chunkNo,
                          Integer page, String section, String title, String content, String dataNature) {}

    public record SearchOutcome(List<KnowledgeHit> hits, boolean empty) {
        public SearchOutcome { hits = List.copyOf(hits); }
    }

    private record RunScope(UUID conversationId, Instant asOf) {}

    private record HitRow(UUID chunkId, int chunkNo, Integer page, String section, String content,
                          UUID documentId, String title, String documentVersion, String dataNature,
                          double similarity) {}

    private static final RowMapper<HitRow> HIT_MAPPER = (r, n) -> new HitRow(
            r.getObject("chunk_id", UUID.class), r.getInt("chunk_no"), r.getObject("page_no", Integer.class),
            r.getString("section"), r.getString("content"), r.getObject("document_id", UUID.class),
            r.getString("title"), r.getString("doc_version"), r.getString("data_nature"),
            r.getDouble("similarity"));

    private static final RowMapper<Excerpt> EXCERPT_MAPPER = (r, n) -> new Excerpt(
            r.getObject("reference_id", UUID.class), r.getObject("document_id", UUID.class),
            r.getString("source_version"), r.getObject("chunk_id", UUID.class), r.getInt("chunk_no"),
            r.getObject("citation_page", Integer.class), r.getString("citation_section"), r.getString("title"),
            r.getString("content"), r.getString("data_nature"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmbeddingClient embeddings;
    private final int maxHits;
    private final double minSimilarity;

    public KnowledgeSearchService(JdbcTemplate jdbc, TransactionTemplate transactions, EmbeddingClient embeddings,
                                  int maxHits, double minSimilarity) {
        if (maxHits < 1 || maxHits > 50) throw new IllegalArgumentException("maxHits out of range");
        if (minSimilarity < 0 || minSimilarity > 1) throw new IllegalArgumentException("minSimilarity out of range");
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.embeddings = embeddings;
        this.maxHits = maxHits;
        this.minSimilarity = minSimilarity;
    }

    public SearchOutcome search(ToolContext context, String query, KnowledgeFilters filters, int limit) {
        String normalized = query == null ? null : query.strip();
        if (normalized == null || normalized.isEmpty())
            throw KnowledgeException.invalid("query is required");
        if (normalized.codePointCount(0, normalized.length()) > MAX_QUERY_CHARS)
            throw KnowledgeException.invalid("query too long");
        if (limit < 1) throw KnowledgeException.invalid("limit out of range");
        int bounded = Math.min(limit, maxHits);
        KnowledgeFilters effective = filters == null ? KnowledgeFilters.NONE : filters;
        RunScope run = requireRun(context);
        int databaseDimension = KnowledgeIndexSchema.vectorDimension(jdbc);
        if (databaseDimension != embeddings.profile().dimension())
            throw new KnowledgeException("EMBEDDING_DIMENSION_MISMATCH", 409);
        float[] vector = embed(normalized);
        String literal = VectorLiteral.of(vector);

        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS chunk_id, c.chunk_no, c.page_no, c.section, c.content,
                       d.id AS document_id, d.title, d.doc_version, d.data_nature,
                       1 - (c.embedding <=> CAST(? AS vector)) AS similarity
                FROM dt_knowledge_chunk c
                JOIN dt_knowledge_document d
                  ON d.id = c.document_id AND d.owner_id = c.owner_id AND d.project_id = c.project_id
                WHERE c.owner_id = ? AND c.project_id = ?
                  AND c.embedding_profile = ? AND d.embedding_profile = ?
                  AND c.embedding_profile = d.embedding_profile
                  AND d.embedding_model = ? AND d.embedding_dimension = ?
                  AND d.ingest_status = 'READY'
                  AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(literal, context.ownerId(), context.projectId(),
                embeddings.profile().profileVersion(), embeddings.profile().profileVersion(),
                embeddings.profile().model(), embeddings.profile().dimension(),
                literal, minSimilarity));
        if (effective.domain() != null) {
            sql.append(" AND d.domain = ?");
            parameters.add(effective.domain());
        }
        if (effective.deviceModel() != null) {
            sql.append(" AND d.device_model = ?");
            parameters.add(effective.deviceModel());
        }
        if (effective.documentId() != null) {
            sql.append(" AND d.id = ?");
            parameters.add(effective.documentId());
        }
        sql.append(" ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?");
        parameters.add(literal);
        parameters.add(bounded);

        List<HitRow> rows = jdbc.query(sql.toString(), HIT_MAPPER, parameters.toArray());
        if (rows.isEmpty()) return new SearchOutcome(List.of(), true);
        List<KnowledgeHit> hits = transactions.execute(status ->
                rows.stream().map(row -> register(context, run, row)).toList());
        return new SearchOutcome(hits, false);
    }

    public Excerpt excerpt(ToolContext context, UUID documentId, UUID chunkId) {
        RunScope run = requireRun(context);
        List<Excerpt> rows = jdbc.query("""
                SELECT r.id AS reference_id, r.source_version, r.citation_page, r.citation_section, r.data_nature,
                       r.chunk_id, c.chunk_no, c.content, c.document_id, d.title
                FROM dt_evidence_reference r
                JOIN dt_knowledge_chunk c
                  ON c.id = r.chunk_id AND c.owner_id = r.owner_id AND c.project_id = r.project_id
                JOIN dt_knowledge_document d
                  ON d.id = c.document_id AND d.owner_id = c.owner_id AND d.project_id = c.project_id
                WHERE r.run_id = ? AND r.owner_id = ? AND r.project_id = ?
                  AND r.source_type = 'KNOWLEDGE_DOCUMENT'
                  AND r.source_id = ? AND r.chunk_id = ?
                """, EXCERPT_MAPPER, context.runId(), context.ownerId(), context.projectId(),
                documentId.toString(), chunkId);
        if (!rows.isEmpty()) return rows.getFirst();
        throw visibleChunk(context, documentId, chunkId)
                ? KnowledgeException.invalidCitation() : KnowledgeException.notFoundOrDenied();
    }

    public Excerpt excerptByReference(ToolContext context, UUID referenceId) {
        requireRun(context);
        List<Excerpt> rows = jdbc.query("""
                SELECT r.id AS reference_id, r.source_version, r.citation_page, r.citation_section, r.data_nature,
                       r.chunk_id, c.chunk_no, c.content, c.document_id, d.title
                FROM dt_evidence_reference r
                JOIN dt_knowledge_chunk c
                  ON c.id = r.chunk_id AND c.owner_id = r.owner_id AND c.project_id = r.project_id
                JOIN dt_knowledge_document d
                  ON d.id = c.document_id AND d.owner_id = c.owner_id AND d.project_id = c.project_id
                WHERE r.id = ? AND r.run_id = ? AND r.owner_id = ? AND r.project_id = ?
                  AND r.source_type = 'KNOWLEDGE_DOCUMENT'
                """, EXCERPT_MAPPER, referenceId, context.runId(), context.ownerId(), context.projectId());
        if (!rows.isEmpty()) return rows.getFirst();
        Boolean ownReference = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM dt_evidence_reference WHERE id=? AND owner_id=? AND project_id=?)",
                Boolean.class, referenceId, context.ownerId(), context.projectId());
        throw Boolean.TRUE.equals(ownReference)
                ? KnowledgeException.invalidCitation() : KnowledgeException.notFoundOrDenied();
    }

    private KnowledgeHit register(ToolContext context, RunScope run, HitRow row) {
        UUID referenceId = jdbc.queryForObject("""
                INSERT INTO dt_evidence_reference(id,run_id,conversation_id,owner_id,project_id,source_type,
                    source_id,source_version,chunk_id,asset_id,as_of,citation_page,citation_section,
                    retrieved_at,data_nature)
                VALUES (?,?,?,?,?,'KNOWLEDGE_DOCUMENT',?,?,?,?,?,?,?,?,?)
                ON CONFLICT (run_id,owner_id,project_id,chunk_id) DO UPDATE SET retrieved_at = EXCLUDED.retrieved_at
                RETURNING id
                """, UUID.class, UUID.randomUUID(), context.runId(), run.conversationId(), context.ownerId(),
                context.projectId(), row.documentId().toString(), row.documentVersion(), row.chunkId(),
                context.selectedAssetId(), Timestamp.from(run.asOf()), row.page(), row.section(),
                Timestamp.from(Instant.now()), row.dataNature());
        return new KnowledgeHit(referenceId, row.documentId(), row.documentVersion(), row.chunkId(), row.chunkNo(),
                row.page(), row.section(), row.title(), row.content(), row.similarity(), row.dataNature());
    }

    private RunScope requireRun(ToolContext context) {
        if (context == null) throw KnowledgeException.invalid("context is required");
        var rows = jdbc.query("SELECT conversation_id,as_of FROM dt_agent_run WHERE id=? AND owner_id=? AND project_id=?",
                (r, n) -> new RunScope(r.getObject("conversation_id", UUID.class),
                        r.getTimestamp("as_of").toInstant()),
                context.runId(), context.ownerId(), context.projectId());
        if (rows.isEmpty()) throw KnowledgeException.notFoundOrDenied();
        return rows.getFirst();
    }

    private boolean visibleChunk(ToolContext context, UUID documentId, UUID chunkId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM dt_knowledge_chunk
                              WHERE id=? AND document_id=? AND owner_id=? AND project_id=?)
                """, Boolean.class, chunkId, documentId, context.ownerId(), context.projectId()));
    }

    private float[] embed(String query) {
        try {
            return embeddings.embed(query);
        } catch (EmbeddingException failure) {
            throw KnowledgeException.fromEmbedding(failure);
        }
    }

    /** 供工具层描述 schema 时使用，避免把上限写成魔法数字。 */
    public int maxHits() { return maxHits; }

    public String embeddingProfileVersion() { return embeddings.profile().profileVersion(); }
}
