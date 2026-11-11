package io.devicetruth.knowledge;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 切片上限合同：配置上限必须按码点严格执行，长单行、换行与补充字符都不能越界。 */
class KnowledgeChunkerTest {
    private static int codePoints(String value) { return value.codePointCount(0, value.length()); }

    @Test void singleLongLineNeverExceedsConfiguredLimit() {
        String content = "充电中断排查".repeat(40); // 200 个码点，无换行
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.of(content, false, 80);
        assertThat(chunks).hasSize(3);
        assertThat(chunks).allMatch(chunk -> codePoints(chunk.content()) <= 80);
        assertThat(chunks.stream().map(KnowledgeChunker.Chunk::content).reduce("", String::concat))
                .isEqualTo(content);
        assertThat(chunks).extracting(KnowledgeChunker.Chunk::chunkNo).containsExactly(1, 2, 3);
        assertThat(chunks).allMatch(chunk -> chunk.section().matches("第 \\d+ 段"));
    }

    @Test void newlinesAndParagraphBreaksCountTowardLimit() {
        String first = "交".repeat(40);
        String second = "直".repeat(40);
        String content = first + "\n\n" + second; // 中间空行产生一个换行，拼接后 81 个码点
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.of(content, false, 80);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        assertThat(chunks).allMatch(chunk -> codePoints(chunk.content()) <= 80);
        assertThat(chunks.stream().map(KnowledgeChunker.Chunk::content).reduce("", (a, b) -> a + b))
                .contains(first).contains(second);
    }

    @Test void supplementaryUnicodeIsCountedByCodePoint() {
        String content = "🔋".repeat(100); // 100 个码点（200 个 UTF-16 char）
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.of(content, false, 10);
        assertThat(chunks).hasSize(10);
        assertThat(chunks).allMatch(chunk -> codePoints(chunk.content()) == 10);
        assertThat(chunks).allMatch(chunk -> chunk.content().length() == 20);
    }

    @Test void markdownHeadingsBecomeSectionLocators() {
        String content = """
                # 充电系统手册

                交流慢充排查段落一。

                ## 直流快充中断

                直流快充排查段落二，包含充电桩日志与电池温度。
                """;
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.of(content, true, 200);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).section()).isEqualTo("充电系统手册");
        assertThat(chunks.get(chunks.size() - 1).section()).isEqualTo("充电系统手册 / 直流快充中断");
        assertThat(chunks).allMatch(chunk -> codePoints(chunk.content()) <= 200);
    }

    @Test void unsupportedLimitsAreRejected() {
        assertThatThrownBy(() -> KnowledgeChunker.of("内容", false, 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KnowledgeChunker.of("内容", false, 4001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
