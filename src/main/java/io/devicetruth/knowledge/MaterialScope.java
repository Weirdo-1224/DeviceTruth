package io.devicetruth.knowledge;

/** SQL 别名仅由源码提供；参数值始终通过 JDBC 绑定。空间读权不等于 Run 材料资格。 */
public final class MaterialScope {
    private MaterialScope() {}
    public static String allowed(String run, String document) {
        return """
            EXISTS (SELECT 1 FROM dt_agent_run mr WHERE mr.id=%s AND (
              EXISTS (SELECT 1 FROM dt_knowledge_document_file ml JOIN dt_uploaded_file mf ON mf.id=ml.file_id
                WHERE ml.document_id=%s AND mf.owner_id=mr.owner_id AND mf.project_id=mr.project_id
                  AND mf.usage_scope='SPACE_KNOWLEDGE')
              OR EXISTS (SELECT 1 FROM dt_knowledge_document md JOIN dt_uploaded_file mf ON mf.id=md.source_file_id
                WHERE md.id=%s AND md.owner_id=mr.owner_id AND md.project_id=mr.project_id
                  AND mf.usage_scope='SPACE_KNOWLEDGE')
              OR EXISTS (SELECT 1 FROM dt_knowledge_publication mp WHERE mp.document_id=%s
                AND mp.owner_id=mr.owner_id AND mp.project_id=mr.project_id)
              OR EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(mr.input_snapshot->'materialVersions','[]'::jsonb)) ma
                WHERE ma->>'documentId'=(%s)::text)))
            """.formatted(run,document,document,document,document);
    }
}
