package io.devicetruth.skill;

/**
 * Skill 加载上限：发现阶段只读元数据头部，正文与 references 按需读取，各自有独立大小上限。
 * 上限越小越安全，但必须容得下正常领域指引；默认值面向仓库内 Markdown 指引。
 */
public record SkillLimits(int maxManifestBytes, int maxDocumentBytes, int maxReferenceBytes, int maxReferences) {
    public SkillLimits {
        if (maxManifestBytes < 512 || maxManifestBytes > 64 * 1024)
            throw new IllegalArgumentException("maxManifestBytes out of range");
        if (maxDocumentBytes < maxManifestBytes || maxDocumentBytes > 1024 * 1024)
            throw new IllegalArgumentException("maxDocumentBytes out of range");
        if (maxReferenceBytes < 1024 || maxReferenceBytes > 1024 * 1024)
            throw new IllegalArgumentException("maxReferenceBytes out of range");
        if (maxReferences < 1 || maxReferences > 128)
            throw new IllegalArgumentException("maxReferences out of range");
    }

    public static SkillLimits defaults() {
        return new SkillLimits(8 * 1024, 128 * 1024, 256 * 1024, 32);
    }
}
