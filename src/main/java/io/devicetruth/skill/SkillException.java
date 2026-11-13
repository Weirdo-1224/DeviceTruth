package io.devicetruth.skill;

/**
 * Skill 加载域的可公开失败：code 稳定、message 固定，不携带宿主路径、正文、脚本文本或凭据。
 * 这里不绑定 HTTP 语义；运行时接入时按 code 映射成对外错误码。
 */
public final class SkillException extends RuntimeException {
    private final String code;

    SkillException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }

    static SkillException rootInvalid() {
        return new SkillException("SKILL_ROOT_INVALID", "Skill root is not a readable directory");
    }

    static SkillException unreadable() {
        return new SkillException("SKILL_UNREADABLE", "Skill file is unreadable");
    }

    static SkillException notFound(String skillId) {
        return new SkillException("SKILL_NOT_FOUND", "Unknown skill: " + display(skillId));
    }

    static SkillException referenceNotFound() {
        return new SkillException("SKILL_REFERENCE_NOT_FOUND", "Unknown skill reference");
    }

    static SkillException invalidMetadata(String detail) {
        return new SkillException("INVALID_SKILL_METADATA", "Invalid skill metadata: " + detail);
    }

    static SkillException manifestTooLarge() {
        return new SkillException("SKILL_METADATA_TOO_LARGE", "Skill metadata exceeds the configured limit");
    }

    static SkillException tooLarge(String part) {
        return new SkillException("SKILL_TOO_LARGE", part + " exceeds the configured limit");
    }

    static SkillException invalidEncoding() {
        return new SkillException("INVALID_SKILL_ENCODING", "Skill file must be UTF-8 without BOM");
    }

    static SkillException invalidDocument(String detail) {
        return new SkillException("INVALID_SKILL_DOCUMENT", "Invalid skill document: " + detail);
    }

    static SkillException invalidPath(String detail) {
        return new SkillException("INVALID_SKILL_PATH", "Invalid skill path: " + detail);
    }

    static SkillException duplicateName(String name) {
        return new SkillException("DUPLICATE_SKILL_NAME", "Duplicate skill name: " + display(name));
    }

    static SkillException changed(String skillId) {
        return new SkillException("SKILL_CHANGED", "Skill changed after discovery: " + display(skillId));
    }

    /** 只在错误信息里回显受控长度的标识，避免把任意输入原样放大。 */
    private static String display(String value) {
        if (value == null) return "<null>";
        return value.length() <= 64 ? value : value.substring(0, 64) + "...";
    }
}
