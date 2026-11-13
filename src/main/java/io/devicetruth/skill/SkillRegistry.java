package io.devicetruth.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Skill 目录发现：只扫描根目录下一层、含 {@code SKILL.md} 的子目录，解析元数据并登记版本。
 * 只有 rules.json 等非标准目录会被忽略——legacy 规则包不能冒充标准 Skill。
 * 发现阶段不加载正文；任何畸形元数据都让装载失败（fail closed），被发现即固定 Skill 与目录的绑定关系。
 */
public final class SkillRegistry {
    private static final Pattern REFERENCE_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    static final String REFERENCES_DIRECTORY = "references";

    private final SkillLimits limits;
    private final Map<String, SkillMetadata> metadata;
    private final Map<String, Path> directories;

    private SkillRegistry(SkillLimits limits, Map<String, SkillMetadata> metadata, Map<String, Path> directories) {
        this.limits = limits;
        this.metadata = Collections.unmodifiableMap(new TreeMap<>(metadata));
        this.directories = Collections.unmodifiableMap(new TreeMap<>(directories));
    }

    public static SkillRegistry load(Path root) {
        return load(root, SkillLimits.defaults());
    }

    public static SkillRegistry load(Path root, SkillLimits limits) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(limits, "limits");
        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException e) {
            throw SkillException.rootInvalid();
        }
        if (!Files.isDirectory(realRoot) || !Files.isReadable(realRoot)) throw SkillException.rootInvalid();

        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> entries = Files.list(realRoot)) {
            entries.filter(Files::isDirectory).sorted().forEach(candidates::add);
        } catch (IOException e) {
            throw SkillException.rootInvalid();
        }

        Map<String, SkillMetadata> discovered = new TreeMap<>();
        Map<String, Path> directories = new TreeMap<>();
        for (Path directory : candidates) {
            Path skillFile = directory.resolve(SkillManifestParser.SKILL_FILE);
            if (!Files.isRegularFile(skillFile)) continue;   // 没有 SKILL.md 的目录不是标准 Skill
            Path realDirectory = contained(realRoot, directory);
            Path realFile = contained(realDirectory, skillFile);
            try {
                if (Files.size(realFile) > limits.maxDocumentBytes()) throw SkillException.tooLarge("SKILL.md");
            } catch (IOException e) {
                throw SkillException.unreadable();
            }
            SkillMetadata found = SkillManifestParser.readMetadata(realFile, limits)
                    .withReferences(listReferences(realDirectory, limits));
            if (discovered.containsKey(found.name())) throw SkillException.duplicateName(found.name());
            if (!found.name().equals(realDirectory.getFileName().toString()))
                throw SkillException.invalidPath("skill name must match its directory");
            discovered.put(found.name(), found);
            directories.put(found.name(), realDirectory);
        }
        return new SkillRegistry(limits, Map.copyOf(discovered), Map.copyOf(directories));
    }

    /** 按名称排序的元数据清单；正文与 references 内容不在其中。 */
    public List<SkillMetadata> skills() {
        return List.copyOf(metadata.values());
    }

    public Optional<SkillMetadata> find(String skillId) {
        return skillId == null ? Optional.empty() : Optional.ofNullable(metadata.get(skillId));
    }

    /** 未知 Skill 直接失败：调用方不得用用户提供的名称拼路径。 */
    public SkillMetadata require(String skillId) {
        if (skillId == null) throw SkillException.notFound(null);
        SkillMetadata found = metadata.get(skillId);
        if (found == null) throw SkillException.notFound(skillId);
        return found;
    }

    SkillLimits limits() { return limits; }

    Path directoryOf(String skillId) {
        Path directory = skillId == null ? null : directories.get(skillId);
        if (directory == null) throw SkillException.notFound(skillId);
        return directory;
    }

    /** 符号链接或目录穿越必须仍落在容器目录内；越界一律拒绝。 */
    private static Path contained(Path container, Path candidate) {
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(container)) throw SkillException.invalidPath("entry escapes the skill directory");
            return real;
        } catch (IOException e) {
            throw SkillException.unreadable();
        }
    }

    /** 只登记 references/ 下平铺的 .md 文件清单；内容按需读取，不在发现阶段加载。 */
    private static List<String> listReferences(Path realDirectory, SkillLimits limits) {
        Path references = realDirectory.resolve(REFERENCES_DIRECTORY);
        if (!Files.isDirectory(references)) return List.of();
        List<Path> entries;
        try (Stream<Path> stream = Files.list(references)) {
            entries = stream.sorted().toList();
        } catch (IOException e) {
            throw SkillException.unreadable();
        }
        List<String> names = new ArrayList<>();
        for (Path entry : entries) {
            if (!Files.isRegularFile(entry)) continue;
            String name = entry.getFileName().toString();
            if (!name.endsWith(".md") || !REFERENCE_NAME.matcher(name).matches())
                throw SkillException.invalidPath("invalid reference file name");
            names.add(name);
        }
        if (names.size() > limits.maxReferences()) throw SkillException.tooLarge("references");
        return List.copyOf(names);
    }
}
