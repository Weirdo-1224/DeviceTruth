package io.devicetruth.skill;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 按需读取 Skill 正文与 references。
 * 读取前必须命中 Registry 已发现的标识，读取时再核对大小、编码与版本，文件在发现后被改动则拒绝激活。
 * 返回的正文属于不可信材料，运行时只能作为领域指引使用，不能据此注册工具或扩大权限。
 */
public final class SkillContextLoader {
    private final SkillRegistry registry;

    public SkillContextLoader(SkillRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** 读取指定 Skill 的正文；version 取自本次读取到的 frontmatter，供 run 检查点记录。 */
    public SkillInstructions instructions(String skillId) {
        SkillMetadata discovered = registry.require(skillId);
        Path directory = registry.directoryOf(discovered.name());
        SkillManifestParser.SkillDocument document = SkillManifestParser.readDocument(
                directory.resolve(SkillManifestParser.SKILL_FILE), registry.limits());
        if (!document.metadata().equals(discovered.withReferences(List.of())))
            throw SkillException.changed(discovered.name());
        if (document.content().isBlank()) throw SkillException.invalidDocument("instructions are empty");
        return new SkillInstructions(discovered.name(), discovered.version(), document.content());
    }

    /** 读取单篇 reference：只接受发现阶段登记过的平铺文件名，不拼接用户提供的路径。 */
    public String readReference(String skillId, String referenceName) {
        SkillMetadata discovered = registry.require(skillId);
        if (referenceName == null || !discovered.references().contains(referenceName))
            throw SkillException.referenceNotFound();
        Path references = registry.directoryOf(discovered.name()).resolve(SkillRegistry.REFERENCES_DIRECTORY);
        Path file = references.resolve(referenceName);
        try {
            Path realBase = references.toRealPath();
            Path realFile = file.toRealPath();
            if (!realFile.startsWith(realBase) || !Files.isRegularFile(realFile))
                throw SkillException.invalidPath("reference escapes the skill directory");
            if (Files.size(realFile) > registry.limits().maxReferenceBytes())
                throw SkillException.tooLarge("reference");
            byte[] bytes = Files.readAllBytes(realFile);
            if (bytes.length > registry.limits().maxReferenceBytes())
                throw SkillException.tooLarge("reference");
            String content = decode(bytes);
            if (content.isBlank()) throw SkillException.invalidDocument("reference is empty");
            return content;
        } catch (IOException e) {
            throw SkillException.unreadable();
        }
    }

    private static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) throw SkillException.invalidEncoding();
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw SkillException.invalidEncoding();
        }
    }
}
