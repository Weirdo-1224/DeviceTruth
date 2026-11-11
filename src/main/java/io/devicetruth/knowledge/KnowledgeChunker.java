package io.devicetruth.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 章节/段落切片：Markdown 保留标题路径作为 section，纯文本保留段落序位。
 * 页码来自 PDF 解析，本切片只导入 MD/TXT，因此 page 为 null；section 始终非空，
 * 保证“页面或章节至少其一”的引用可定位要求。
 */
public final class KnowledgeChunker {
    public static final String PROFILE = "section-paragraph-v1";
    /** 与 dt_knowledge_chunk.content 的数据库检查保持一致。 */
    private static final int MAX_STORED_CHARS = 4000;
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(\\S.*)$");

    public record Chunk(int chunkNo, Integer page, String section, String content) {}

    private KnowledgeChunker() {}

    public static List<Chunk> of(String content, boolean markdown, int maxChars) {
        if (maxChars < 8 || maxChars > MAX_STORED_CHARS)
            throw new IllegalArgumentException("maxChunkChars out of range");
        Builder builder = new Builder(maxChars);
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            String trimmed = line.strip();
            if (markdown) {
                Matcher heading = HEADING.matcher(trimmed);
                if (heading.matches()) {
                    builder.heading(heading.group(1).length(), heading.group(2).strip());
                    continue;
                }
            }
            if (trimmed.isEmpty()) builder.paragraphBreak();
            else builder.line(trimmed);
        }
        builder.flush();
        return builder.chunks;
    }

    private static final class Builder {
        private final int maxChars;
        private final List<Chunk> chunks = new ArrayList<>();
        private final List<String> headings = new ArrayList<>();
        private final StringBuilder buffer = new StringBuilder();

        private Builder(int maxChars) {
            this.maxChars = maxChars;
        }

        private void heading(int level, String title) {
            flush();
            while (headings.size() >= level) headings.removeLast();
            while (headings.size() < level - 1) headings.add("");
            headings.add(title);
        }

        private void line(String text) {
            String remaining = text;
            while (!remaining.isEmpty()) {
                int current = buffer.codePointCount(0, buffer.length());
                int separator = buffer.isEmpty() ? 0 : 1;
                int available = maxChars - current - separator;
                if (available <= 0) {
                    flush();
                    continue;
                }
                int[] codePoints = remaining.codePoints().toArray();
                if (codePoints.length <= available) {
                    if (separator == 1) buffer.append('\n');
                    buffer.append(remaining);
                    return;
                }
                // 单行超过上限：按码点切到刚好填满当前块，剩余部分继续写入下一块。
                if (separator == 1) buffer.append('\n');
                buffer.append(new String(codePoints, 0, available));
                flush();
                remaining = new String(codePoints, available, codePoints.length - available);
            }
        }

        private void paragraphBreak() {
            if (buffer.isEmpty()) return;
            // 段落分隔也计入块长度；放不下就先换块，避免换行把块撑过上限。
            if (buffer.codePointCount(0, buffer.length()) + 1 > maxChars) {
                flush();
                return;
            }
            buffer.append('\n');
        }

        private void flush() {
            String body = buffer.toString().strip();
            buffer.setLength(0);
            if (body.isEmpty()) return;
            chunks.add(new Chunk(chunks.size() + 1, null, locator(), body));
        }

        private String locator() {
            List<String> path = headings.stream().filter(heading -> !heading.isEmpty()).toList();
            if (!path.isEmpty()) return String.join(" / ", path);
            // 纯文本没有标题：用段序位作为可定位章节，避免 page/section 同时为空。
            return "第 " + (chunks.size() + 1) + " 段";
        }
    }
}
