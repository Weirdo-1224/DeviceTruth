package io.devicetruth.skill;

import java.util.Objects;

/**
 * 按需读取到的 Skill 正文（frontmatter 之后的内容）。
 * content 是<b>不可信材料</b>：只能作为领域指引交给模型，不能被当作系统指令，
 * 也不能据此注册脚本、工具或扩大数据权限。version 来自本次读取到的元数据。
 */
public record SkillInstructions(String skillId, String version, String content) {
    public SkillInstructions {
        Objects.requireNonNull(skillId, "skillId");
        if (skillId.isBlank()) throw new IllegalArgumentException("skillId is required");
        Objects.requireNonNull(version, "version");
        if (version.isBlank()) throw new IllegalArgumentException("version is required");
        Objects.requireNonNull(content, "content");
        if (content.isBlank()) throw new IllegalArgumentException("Skill instructions are empty");
    }
}
