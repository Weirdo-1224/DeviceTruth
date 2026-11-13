package io.devicetruth.skill;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.devicetruth.skill.SkillFixtures.assertFailure;
import static org.assertj.core.api.Assertions.assertThat;

/** 按需读取合同：正文与 references 只在请求时读取，读取时复验大小、编码与版本。 */
class SkillContextLoaderTest {
    @Test void instructionsExcludeFrontmatterAndRecordVersion(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillContextLoader loader = new SkillContextLoader(SkillRegistry.load(root));

        SkillInstructions instructions = loader.instructions("alpha-skill");

        assertThat(instructions.skillId()).isEqualTo("alpha-skill");
        assertThat(instructions.version()).isEqualTo("1.0.0");
        assertThat(instructions.content()).contains("# 演示指引").contains("先核对现场确认记录")
                .doesNotContain("name: alpha-skill").doesNotContain("version: 1.0.0");
    }

    @Test void discoveryNeedsOnlyMetadataHeadWhileLoadingReadsWholeBody(@TempDir Path root) throws Exception {
        SkillLimits limits = new SkillLimits(1024, 8192, 4096, 4);
        String body = "\n# 长正文\n\n" + "排查要点。".repeat(500);
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.manifest("alpha-skill") + body);
        SkillContextLoader loader = new SkillContextLoader(SkillRegistry.load(root, limits));

        assertThat(loader.instructions("alpha-skill").content()).isEqualTo(body);
    }

    @Test void repositorySkillsProvideInstructionsAndReferences() {
        SkillRegistry registry = SkillRegistry.load(Path.of("skills"));
        SkillContextLoader loader = new SkillContextLoader(registry);

        SkillInstructions automotive = loader.instructions("automotive-diagnosis");
        assertThat(automotive.version()).matches(SkillMetadata.VERSION_PATTERN);
        assertThat(automotive.content()).isNotBlank().doesNotContain("name: automotive-diagnosis");
        assertThat(loader.instructions("factory-equipment").content()).isNotBlank()
                .doesNotContain("name: factory-equipment");
        for (String reference : registry.require("automotive-diagnosis").references()) {
            assertThat(loader.readReference("automotive-diagnosis", reference)).isNotBlank();
        }
        for (String reference : registry.require("factory-equipment").references()) {
            assertThat(loader.readReference("factory-equipment", reference)).isNotBlank();
        }
    }

    @Test void unknownOrUnsafeSkillIdsAreRejected(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillContextLoader loader = new SkillContextLoader(SkillRegistry.load(root));

        assertFailure(() -> loader.instructions("missing-skill"), "SKILL_NOT_FOUND");
        assertFailure(() -> loader.instructions(null), "SKILL_NOT_FOUND");
        assertFailure(() -> loader.instructions(".."), "SKILL_NOT_FOUND");
        assertFailure(() -> loader.instructions("../alpha-skill"), "SKILL_NOT_FOUND");
    }

    @Test void documentGrowthAfterDiscoveryIsRejected(@TempDir Path root) throws Exception {
        SkillLimits limits = new SkillLimits(1024, 4096, 1024, 4);
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillContextLoader loader = new SkillContextLoader(SkillRegistry.load(root, limits));

        Files.writeString(root.resolve("alpha-skill").resolve("SKILL.md"),
                SkillFixtures.manifest("alpha-skill") + "x".repeat(5000), StandardCharsets.UTF_8);

        assertFailure(() -> loader.instructions("alpha-skill"), "SKILL_TOO_LARGE");
    }

    @Test void metadataChangeAfterDiscoveryIsRejected(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillContextLoader loader = new SkillContextLoader(SkillRegistry.load(root));

        Files.writeString(root.resolve("alpha-skill").resolve("SKILL.md"),
                SkillFixtures.manifest("alpha-skill").replace("version: 1.0.0", "version: 1.0.1")
                        + SkillFixtures.BODY, StandardCharsets.UTF_8);

        assertFailure(() -> loader.instructions("alpha-skill"), "SKILL_CHANGED");
    }

    @Test void emptyInstructionsAreRejected(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.manifest("alpha-skill"));
        SkillContextLoader loader = new SkillContextLoader(SkillRegistry.load(root));

        assertFailure(() -> loader.instructions("alpha-skill"), "INVALID_SKILL_DOCUMENT");
    }

    @Test void referencesAreListedAtDiscoveryAndReadOnDemand(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillFixtures.writeReference(root, "alpha-skill", "guide.md", "# 参考\n\n现场核对要点。");
        SkillRegistry registry = SkillRegistry.load(root);
        SkillContextLoader loader = new SkillContextLoader(registry);

        assertThat(registry.require("alpha-skill").references()).containsExactly("guide.md");
        assertThat(loader.readReference("alpha-skill", "guide.md")).contains("现场核对要点。");
    }

    @Test void referenceNamesOutsideTheDiscoveredListAreRejected(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillFixtures.writeReference(root, "alpha-skill", "guide.md", "内容");
        SkillContextLoader loader = new SkillContextLoader(SkillRegistry.load(root));

        assertFailure(() -> loader.readReference("alpha-skill", "../SKILL.md"), "SKILL_REFERENCE_NOT_FOUND");
        assertFailure(() -> loader.readReference("alpha-skill", "references/guide.md"),
                "SKILL_REFERENCE_NOT_FOUND");
        assertFailure(() -> loader.readReference("alpha-skill", "..\\guide.md"), "SKILL_REFERENCE_NOT_FOUND");
        assertFailure(() -> loader.readReference("alpha-skill", "C:\\Windows\\win.ini"),
                "SKILL_REFERENCE_NOT_FOUND");
        assertFailure(() -> loader.readReference("alpha-skill", null), "SKILL_REFERENCE_NOT_FOUND");
        assertFailure(() -> loader.readReference("missing-skill", "guide.md"), "SKILL_NOT_FOUND");
    }

    @Test void oversizedAndNonUtf8ReferencesAreRejected(@TempDir Path root) throws Exception {
        SkillLimits limits = new SkillLimits(1024, 4096, 1024, 4);
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillFixtures.writeReference(root, "alpha-skill", "guide.md", "内容");
        SkillContextLoader loader = new SkillContextLoader(SkillRegistry.load(root, limits));

        Files.writeString(root.resolve("alpha-skill").resolve("references").resolve("guide.md"),
                "长".repeat(2000), StandardCharsets.UTF_8);
        assertFailure(() -> loader.readReference("alpha-skill", "guide.md"), "SKILL_TOO_LARGE");

        Files.write(root.resolve("alpha-skill").resolve("references").resolve("guide.md"),
                new byte[] {(byte) 0xFF, (byte) 0xFE});
        assertFailure(() -> loader.readReference("alpha-skill", "guide.md"), "INVALID_SKILL_ENCODING");
    }
}
