package io.devicetruth.skill;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SKILL.md 的严格解析器：只支持受控的 YAML 子集——顶层标量键与两空格缩进的字符串序列。
 * 不支持注释、锚点、别名、多行块、嵌套结构和制表符，遇到即失败，避免歧义解析被用来藏指令。
 * 发现阶段只读限定大小的文件头，正文不进入返回值；完整读取由 {@link #readDocument} 显式触发。
 */
final class SkillManifestParser {
    private SkillManifestParser() {}

    static final String SKILL_FILE = "SKILL.md";
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final Pattern TOOL_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> SCALAR_KEYS = Set.of("name", "description", "version");
    private static final Set<String> LIST_KEYS = Set.of("devices", "tasks", "tools", "evidence", "risk", "citation");
    private static final List<String> REQUIRED_KEYS = List.of("name", "description", "version", "devices", "tasks",
            "tools", "evidence", "risk", "citation");
    private static final int MAX_ENTRIES = 32;
    private static final int MAX_ENTRY_CHARS = 300;
    private static final int MAX_DESCRIPTION_CHARS = 1000;

    /** 完整文档：元数据与正文一起返回，供运行时按需激活。 */
    record SkillDocument(SkillMetadata metadata, String content) {}

    /** 只读文件头解析元数据；正文即使在同一读取窗口内也不解析、不返回。 */
    static SkillMetadata readMetadata(Path skillFile, SkillLimits limits) {
        byte[] head;
        try (InputStream input = Files.newInputStream(skillFile)) {
            head = input.readNBytes(limits.maxManifestBytes() + 1);
        } catch (IOException e) {
            throw SkillException.unreadable();
        }
        FrontMatter split = split(head);
        if (split == null) {
            if (head.length > limits.maxManifestBytes()) throw SkillException.manifestTooLarge();
            decode(head, 0, head.length);   // 未闭合时先排除编码问题，错误码才指向真实原因
            throw SkillException.invalidMetadata("frontmatter is not closed");
        }
        if (split.manifestEnd() - split.manifestStart() > limits.maxManifestBytes())
            throw SkillException.manifestTooLarge();
        return parseManifest(decode(head, split.manifestStart(), split.manifestEnd()));
    }

    /** 读取完整 SKILL.md：大小与编码再次校验，返回解析出的元数据与正文。 */
    static SkillDocument readDocument(Path skillFile, SkillLimits limits) {
        byte[] bytes = readAll(skillFile, limits);
        FrontMatter split = split(bytes);
        if (split == null) throw SkillException.invalidMetadata("frontmatter is not closed");
        if (split.manifestEnd() - split.manifestStart() > limits.maxManifestBytes())
            throw SkillException.manifestTooLarge();
        String manifest = decode(bytes, split.manifestStart(), split.manifestEnd());
        String body = decode(bytes, split.bodyStart(), bytes.length);
        return new SkillDocument(parseManifest(manifest), body);
    }

    private static byte[] readAll(Path skillFile, SkillLimits limits) {
        try {
            if (Files.size(skillFile) > limits.maxDocumentBytes()) throw SkillException.tooLarge(SKILL_FILE);
            byte[] bytes = Files.readAllBytes(skillFile);
            if (bytes.length > limits.maxDocumentBytes()) throw SkillException.tooLarge(SKILL_FILE);
            return bytes;
        } catch (IOException e) {
            throw SkillException.unreadable();
        }
    }

    /** frontmatter 区间：三个偏移都落在字节边界（分隔符是 ASCII），因此解码不会截断多字节字符。 */
    private record FrontMatter(int manifestStart, int manifestEnd, int bodyStart) {}

    /** 找到 frontmatter 区间；文件头被截断、缺少结束分隔符时返回 null，由调用方区分"太大"与"未闭合"。 */
    private static FrontMatter split(byte[] bytes) {
        if (hasBom(bytes)) throw SkillException.invalidEncoding();
        int firstLineEnd = lineEnd(bytes, 0);
        if (firstLineEnd < 0 || !isDelimiterLine(bytes, 0, firstLineEnd))
            throw SkillException.invalidMetadata("frontmatter must start with ---");
        int lineStart = firstLineEnd + 1;
        while (lineStart <= bytes.length) {
            int lineEnd = lineEnd(bytes, lineStart);
            if (lineEnd < 0) {
                if (isDelimiterLine(bytes, lineStart, bytes.length))
                    return new FrontMatter(firstLineEnd + 1, lineStart, bytes.length);
                return null;
            }
            if (isDelimiterLine(bytes, lineStart, lineEnd))
                return new FrontMatter(firstLineEnd + 1, lineStart, lineEnd + 1);
            lineStart = lineEnd + 1;
        }
        return null;
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    private static int lineEnd(byte[] bytes, int from) {
        for (int i = from; i < bytes.length; i++) if (bytes[i] == '\n') return i;
        return -1;
    }

    private static boolean isDelimiterLine(byte[] bytes, int start, int end) {
        int length = end - start;
        if (length == 4 && bytes[end - 1] == '\r') length = 3;
        return length == 3 && bytes[start] == '-' && bytes[start + 1] == '-' && bytes[start + 2] == '-';
    }

    private static String decode(byte[] bytes, int from, int to) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, from, to - from)).toString();
        } catch (CharacterCodingException e) {
            throw SkillException.invalidEncoding();
        }
    }

    /** 解析受控子集；未知键、重复键、标量/序列类型错配、越界条数一律失败。 */
    private static SkillMetadata parseManifest(String manifest) {
        Map<String, Object> values = new LinkedHashMap<>();
        Set<String> declaredEmpty = new LinkedHashSet<>();
        String openList = null;
        for (String rawLine : manifest.split("\n", -1)) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.indexOf('\t') >= 0) throw SkillException.invalidMetadata("tabs are not supported");
            if (line.isBlank()) continue;
            if (line.startsWith(" ")) {
                if (openList == null || !line.startsWith("  - "))
                    throw SkillException.invalidMetadata("only two-space sequence items are supported");
                String item = line.substring(4).trim();
                if (item.isEmpty()) throw SkillException.invalidMetadata("empty sequence item");
                if (item.length() > MAX_ENTRY_CHARS) throw SkillException.invalidMetadata("sequence item is too long");
                @SuppressWarnings("unchecked")
                List<String> items = (List<String>) values.get(openList);
                if (items.size() >= MAX_ENTRIES) throw SkillException.invalidMetadata("too many sequence items");
                items.add(item);
                continue;
            }
            openList = null;
            if (line.startsWith("#")) throw SkillException.invalidMetadata("comments are not supported");
            int colon = line.indexOf(':');
            if (colon <= 0) throw SkillException.invalidMetadata("malformed metadata line");
            String key = line.substring(0, colon);
            if (!KEY.matcher(key).matches()) throw SkillException.invalidMetadata("invalid metadata key");
            if (values.containsKey(key)) throw SkillException.invalidMetadata("duplicate metadata key: " + key);
            String value = line.substring(colon + 1).trim();
            if (SCALAR_KEYS.contains(key)) {
                if (value.isEmpty()) throw SkillException.invalidMetadata("scalar value is required: " + key);
                validateScalar(key, value);
                values.put(key, value);
            } else if (LIST_KEYS.contains(key)) {
                if (value.isEmpty()) {
                    values.put(key, new ArrayList<String>());
                    openList = key;
                } else if (value.equals("[]")) {
                    values.put(key, new ArrayList<String>());
                    declaredEmpty.add(key);
                } else {
                    throw SkillException.invalidMetadata("list value must be a block sequence: " + key);
                }
            } else {
                throw SkillException.invalidMetadata("unknown metadata key: " + key);
            }
        }
        for (String key : REQUIRED_KEYS) {
            if (!values.containsKey(key)) throw SkillException.invalidMetadata("missing metadata key: " + key);
        }
        for (String key : LIST_KEYS) {
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) values.get(key);
            if (items.isEmpty() && !declaredEmpty.contains(key))
                throw SkillException.invalidMetadata("sequence requires at least one item: " + key);
        }
        List<String> tools = list(values, "tools");
        for (String tool : tools) {
            if (!TOOL_NAME.matcher(tool).matches())
                throw SkillException.invalidMetadata("invalid tool name: " + keyFor(tool));
        }
        try {
            return new SkillMetadata((String) values.get("name"), (String) values.get("description"),
                    (String) values.get("version"), list(values, "devices"), list(values, "tasks"),
                    tools, list(values, "evidence"), list(values, "risk"), list(values, "citation"),
                    List.of());
        } catch (IllegalArgumentException e) {
            throw SkillException.invalidMetadata(e.getMessage());
        }
    }

    /** 避免把任意长度的非法工具名原样带进错误信息。 */
    private static String keyFor(String value) {
        return value.length() <= 64 ? value : value.substring(0, 64) + "...";
    }

    private static void validateScalar(String key, String value) {
        if (key.equals("description") && value.length() > MAX_DESCRIPTION_CHARS)
            throw SkillException.invalidMetadata("description is too long");
        char first = value.charAt(0);
        if ("|>&*![{%".indexOf(first) >= 0 || value.startsWith("- "))
            throw SkillException.invalidMetadata("unsupported value syntax: " + key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> values, String key) {
        return List.copyOf((List<String>) values.get(key));
    }
}
