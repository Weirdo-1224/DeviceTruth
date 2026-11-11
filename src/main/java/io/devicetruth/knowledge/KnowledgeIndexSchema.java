package io.devicetruth.knowledge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/** 读取索引列的真实维度；数据库列是唯一事实来源，配置不符必须在写入/检索前失败。 */
final class KnowledgeIndexSchema {
    private static final Pattern VECTOR_TYPE = Pattern.compile("^vector\\((\\d+)\\)$");

    private KnowledgeIndexSchema() {}

    static int vectorDimension(JdbcTemplate jdbc) {
        String type = jdbc.queryForObject("""
                SELECT format_type(atttypid, atttypmod) FROM pg_attribute
                WHERE attrelid = 'dt_knowledge_chunk'::regclass AND attname = 'embedding' AND NOT attisdropped
                """, String.class);
        if (type == null) throw new KnowledgeException("KNOWLEDGE_INDEX_NOT_READY", 500);
        Matcher matcher = VECTOR_TYPE.matcher(type);
        if (!matcher.matches()) throw new KnowledgeException("KNOWLEDGE_INDEX_NOT_READY", 500);
        return Integer.parseInt(matcher.group(1));
    }
}
