package io.devicetruth.skill;

import java.util.List;
import java.util.Objects;

/**
 * 一个标准 Skill 的元数据：只描述适用范围、证据要求、风险与引用规范，以及它<b>声明</b>要用的工具。
 * 声明不等于授权——工具是否可见、可执行仍由服务器身份授权与 Skill 绑定共同决定（04-01 集成）。
 * references 只是目录清单，正文按需读取，不在发现阶段灌入。
 */
public record SkillMetadata(
        String name, String description, String version,
        List<String> devices, List<String> tasks,
        List<String> tools, List<String> evidence, List<String> risks,
        List<String> citations, List<String> references) {

    /** Skill 标识：小写字母数字与连字符，最长 64，必须与所在目录同名。 */
    public static final String NAME_PATTERN = "[a-z0-9][a-z0-9-]{0,63}";
    /** 版本：三段数字，供 run 检查点记录与恢复重验。 */
    public static final String VERSION_PATTERN = "[0-9]+\\.[0-9]+\\.[0-9]+";

    public SkillMetadata {
        Objects.requireNonNull(name, "name");
        if (!name.matches(NAME_PATTERN)) throw new IllegalArgumentException("Invalid skill name");
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) throw new IllegalArgumentException("description is required");
        Objects.requireNonNull(version, "version");
        if (!version.matches(VERSION_PATTERN)) throw new IllegalArgumentException("Invalid skill version");
        devices = List.copyOf(Objects.requireNonNull(devices, "devices"));
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        if (devices.isEmpty() || tasks.isEmpty()) throw new IllegalArgumentException("Skill applicability is required");
        tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        risks = List.copyOf(Objects.requireNonNull(risks, "risks"));
        citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
        if (citations.isEmpty()) throw new IllegalArgumentException("citation rules are required");
        references = List.copyOf(Objects.requireNonNull(references, "references"));
    }

    /** 补上目录发现的 references 清单；其余字段原样保留。 */
    public SkillMetadata withReferences(List<String> discovered) {
        return new SkillMetadata(name, description, version, devices, tasks, tools, evidence, risks, citations,
                discovered);
    }

    /** 只回答"Skill 是否声明了这个工具"，不构成授权判断。 */
    public boolean declaresTool(String tool) {
        return tools.contains(tool);
    }
}
