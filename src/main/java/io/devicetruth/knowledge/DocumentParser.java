package io.devicetruth.knowledge;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Markdown 上传解析：严格校验 UTF-8，并复用章节切片保留可定位引用。 */
public final class DocumentParser {
    public List<KnowledgeChunker.Chunk> parseMarkdown(byte[] bytes, int maxChunkChars) {
        try {
            String content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.of(content, true, maxChunkChars);
            if (chunks.isEmpty()) throw KnowledgeException.invalid("document has no indexable text");
            return chunks;
        } catch (CharacterCodingException e) {
            throw new KnowledgeException("INVALID_TEXT_ENCODING", 400);
        }
    }
}
