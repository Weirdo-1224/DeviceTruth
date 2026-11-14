package io.devicetruth.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.devicetruth.skill.SkillFixtures.assertFailure;
import static org.assertj.core.api.Assertions.*;

/** Skill 目录发现合同：只认 SKILL.md，元数据严格解析，路径/大小/格式异常一律失败。 */
class SkillRegistryTest {
    @Test void repositorySkillsAreDiscoveredAndLegacyRulesPackageIsNotASkill() {
        SkillRegistry registry = SkillRegistry.load(Path.of("skills"));

        assertThat(registry.skills()).extracting(SkillMetadata::name)
                .containsExactly("automotive-diagnosis", "carrier-rtu-evidence-triage",
                        "ev-charging-triage", "ev-maintenance-inspection", "ev-powertrain-safety-investigation",
                        "factory-equipment", "industrial-configuration-investigation", "industrial-controller-fault-triage");
        SkillMetadata automotive = registry.require("automotive-diagnosis");
        assertThat(automotive.version()).matches(SkillMetadata.VERSION_PATTERN);
        assertThat(automotive.tools()).contains("search_knowledge", "get_document_excerpt", "get_asset",
                "get_configuration", "get_maintenance_history", "get_observations");
        assertThat(automotive.references()).contains("charging-troubleshooting.md");
        assertThat(registry.require("factory-equipment").declaresTool("get_maintenance_history")).isTrue();
        assertThat(registry.require("factory-equipment").declaresTool("run_shell")).isFalse();
        assertThat(registry.find("automotive-demo")).isEmpty();
        assertThat(registry.find("rules")).isEmpty();
    }

    @Test void importedSkillsLoadAllReferencesAndDeclareOnlySupportedReadTools() {
        SkillRegistry registry = SkillRegistry.load(Path.of("skills"));
        SkillContextLoader loader = new SkillContextLoader(registry);
        var supported = java.util.Set.of("get_asset", "get_configuration", "get_maintenance_history",
                "get_observations", "search_knowledge", "get_document_excerpt");
        for (SkillMetadata skill : registry.skills()) {
            assertThat(loader.instructions(skill.name()).content()).isNotBlank();
            assertThat(supported).containsAll(skill.tools());
            assertThat(skill.references()).contains("domain-knowledge.md", "investigation-guide.md",
                    "evidence-requirements.md", "decision-rules.md", "safety-guidelines.md");
            for (String reference : skill.references()) {
                assertThat(loader.readReference(skill.name(), reference)).isNotBlank();
            }
        }
        assertThat(registry.require("carrier-rtu-evidence-triage").declaresTool("get_observations")).isFalse();
    }

    @Test void metadataFieldsComeFromFrontmatter(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));

        SkillMetadata metadata = SkillRegistry.load(root).require("alpha-skill");

        assertThat(metadata.description()).isEqualTo("演示领域指引");
        assertThat(metadata.devices()).containsExactly("演示设备");
        assertThat(metadata.tasks()).containsExactly("演示任务");
        assertThat(metadata.tools()).containsExactly("search_knowledge");
        assertThat(metadata.evidence()).containsExactly("现场确认记录");
        assertThat(metadata.risks()).containsExactly("仅供参考");
        assertThat(metadata.citations()).containsExactly("引用需给出文档标题与章节");
        assertThat(metadata.references()).isEmpty();
    }

    @Test void directoriesWithoutSkillManifestAreIgnored(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("legacy-rules"));
        Files.writeString(root.resolve("legacy-rules").resolve("rules.json"), "{}", StandardCharsets.UTF_8);
        Files.createDirectories(root.resolve("notes"));
        Files.writeString(root.resolve("notes").resolve("readme.md"), "随手记", StandardCharsets.UTF_8);

        assertThat(SkillRegistry.load(root).skills()).isEmpty();
    }

    @Test void missingRootOrRootFileFails(@TempDir Path root) throws Exception {
        Path file = Files.writeString(root.resolve("not-a-directory"), "x", StandardCharsets.UTF_8);

        assertFailure(() -> SkillRegistry.load(root.resolve("missing")), "SKILL_ROOT_INVALID");
        assertFailure(() -> SkillRegistry.load(file), "SKILL_ROOT_INVALID");
    }

    @Test void missingRequiredKeyIsRejected(@TempDir Path root) throws Exception {
        String content = SkillFixtures.manifest("alpha-skill").replace("tools:\n  - search_knowledge\n", "");
        SkillFixtures.writeSkill(root, "alpha-skill", content);

        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");
    }

    @Test void unknownAndDuplicateKeysAreRejected(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "unknown", SkillFixtures.manifest("unknown").replace(
                "citation:", "owner: 某团队\ncitation:"));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");

        Files.delete(root.resolve("unknown").resolve("SKILL.md"));
        SkillFixtures.writeSkill(root, "duplicate", SkillFixtures.manifest("duplicate").replace(
                "citation:", "version: 1.0.0\ncitation:"));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");
    }

    @Test void malformedFrontmatterIsRejected(@TempDir Path root) throws Exception {
        String valid = SkillFixtures.manifest("unclosed");
        String unclosed = valid.substring(0, valid.lastIndexOf("---"));
        SkillFixtures.writeSkill(root, "unclosed", unclosed);
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");

        Files.delete(root.resolve("unclosed").resolve("SKILL.md"));
        SkillFixtures.writeSkill(root, "no-delimiter", "# 没有 frontmatter\n\n正文\n");
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");
    }

    @Test void unsupportedYamlSubsetIsRejected(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "tabbed", SkillFixtures.manifest("tabbed").replace(
                "  - 演示设备", "\t- 演示设备"));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");

        Files.delete(root.resolve("tabbed").resolve("SKILL.md"));
        SkillFixtures.writeSkill(root, "block-scalar", SkillFixtures.manifest("block-scalar").replace(
                "description: 演示领域指引", "description: |"));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");

        Files.delete(root.resolve("block-scalar").resolve("SKILL.md"));
        SkillFixtures.writeSkill(root, "comment", SkillFixtures.manifest("comment").replace(
                "version: 1.0.0", "# 版本\nversion: 1.0.0"));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");
    }

    @Test void invalidNameVersionAndToolNamesAreRejected(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.manifest("alpha-skill").replace(
                "version: 1.0.0", "version: 1.0"));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");

        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.manifest("alpha-skill").replace(
                "  - search_knowledge", "  - run_shell; rm -rf /"));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");

        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.manifest("beta-skill"));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_PATH");
    }

    @Test void duplicateDeclaredNameIsRejected(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.manifest("alpha-skill"));
        SkillFixtures.writeSkill(root, "beta-skill", SkillFixtures.manifest("alpha-skill"));

        assertFailure(() -> SkillRegistry.load(root), "DUPLICATE_SKILL_NAME");
    }

    @Test void oneBrokenSkillFailsTheWholeLoad(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillFixtures.writeSkill(root, "broken-skill", "---\nname: broken-skill\n---\n正文\n");

        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_METADATA");
    }

    @Test void invalidUtf8AndBomAreRejected(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("alpha-skill"));
        Files.write(dir.resolve("SKILL.md"), new byte[] {'-', '-', '-', '\n', (byte) 0xFF, '\n'});

        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_ENCODING");

        Files.write(dir.resolve("SKILL.md"), ("\uFEFF" + SkillFixtures.skill("alpha-skill"))
                .getBytes(StandardCharsets.UTF_8));
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_ENCODING");
    }

    @Test void documentAndManifestSizeLimitsAreEnforced(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.manifest("alpha-skill") + "x".repeat(3000));
        assertFailure(() -> SkillRegistry.load(root, new SkillLimits(1024, 2048, 4096, 4)), "SKILL_TOO_LARGE");

        Files.delete(root.resolve("alpha-skill").resolve("SKILL.md"));
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.manifest("alpha-skill").replace(
                "description: 演示领域指引", "description: " + "长".repeat(600)));
        assertFailure(() -> SkillRegistry.load(root, new SkillLimits(512, 4096, 4096, 4)),
                "SKILL_METADATA_TOO_LARGE");
    }

    @Test void referenceListIsBoundedAndValidated(@TempDir Path root) throws Exception {
        SkillFixtures.writeSkill(root, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        SkillFixtures.writeReference(root, "alpha-skill", "one.md", "第一篇");
        SkillFixtures.writeReference(root, "alpha-skill", "two.md", "第二篇");

        assertFailure(() -> SkillRegistry.load(root, new SkillLimits(1024, 8192, 4096, 1)), "SKILL_TOO_LARGE");

        Files.delete(root.resolve("alpha-skill").resolve("references").resolve("two.md"));
        Files.writeString(root.resolve("alpha-skill").resolve("references").resolve("Bad Name.md"), "x");
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_PATH");

        Files.delete(root.resolve("alpha-skill").resolve("references").resolve("Bad Name.md"));
        Files.writeString(root.resolve("alpha-skill").resolve("references").resolve("script.sh"), "rm -rf /");
        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_PATH");
    }

    @Test void symlinkedSkillDirectoryEscapingRootIsRejected(@TempDir Path root, @TempDir Path outside)
            throws Exception {
        SkillFixtures.writeSkill(outside, "alpha-skill", SkillFixtures.skill("alpha-skill"));
        try {
            Files.createSymbolicLink(root.resolve("alpha-skill"), outside.resolve("alpha-skill"));
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("当前环境无法创建符号链接：" + e.getMessage());
        }

        assertFailure(() -> SkillRegistry.load(root), "INVALID_SKILL_PATH");
    }

    @Test void limitsRejectOutOfRangeValues() {
        assertThatThrownBy(() -> new SkillLimits(100, 1024, 1024, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkillLimits(1024, 512, 1024, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkillLimits(1024, 2048, 1024, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SkillLimits.defaults().maxDocumentBytes()).isGreaterThan(SkillLimits.defaults().maxManifestBytes());
    }
}
