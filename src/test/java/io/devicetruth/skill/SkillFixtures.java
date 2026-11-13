package io.devicetruth.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 测试用最小 SKILL.md 生成器：只写受控变体，避免测试互相依赖仓库真实内容。 */
final class SkillFixtures {
    private SkillFixtures() {}

    static void assertFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SkillException.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }

    static final String BODY = """

            # 演示指引

            先核对现场确认记录，再检索手册章节。
            """;

    static String manifest(String name) {
        return """
                ---
                name: %s
                description: 演示领域指引
                version: 1.0.0
                devices:
                  - 演示设备
                tasks:
                  - 演示任务
                tools:
                  - search_knowledge
                evidence:
                  - 现场确认记录
                risk:
                  - 仅供参考
                citation:
                  - 引用需给出文档标题与章节
                ---
                """.formatted(name);
    }

    static String skill(String name) {
        return manifest(name) + BODY;
    }

    static Path writeSkill(Path root, String directory, String content) throws IOException {
        Path dir = Files.createDirectories(root.resolve(directory));
        return Files.writeString(dir.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
    }

    static Path writeReference(Path root, String directory, String name, String content) throws IOException {
        Path dir = Files.createDirectories(root.resolve(directory).resolve("references"));
        return Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    static Path skillDirectory(Path root, String directory) {
        return root.resolve(directory);
    }
}
