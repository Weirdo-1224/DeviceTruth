import io.devicetruth.skill.SkillContextLoader;
import io.devicetruth.skill.SkillRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** 使用产品真实解析器检查格式，再核对工作手册的目录、链接与默认启用要求。 */
public class ValidateSkills {
    private static final Set<String> STANDARD = Set.of("domain-knowledge.md", "investigation-guide.md",
            "evidence-requirements.md", "decision-rules.md", "safety-guidelines.md");
    private static final Set<String> TOOLS = Set.of("get_asset", "get_configuration", "get_maintenance_history",
            "get_observations", "search_knowledge", "get_document_excerpt");
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)]+)\\)");

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "skills" : args[0]).toRealPath();
        var registry = SkillRegistry.load(root);
        var loader = new SkillContextLoader(registry);
        require(!registry.skills().isEmpty(), "No Skills discovered");
        String property = Files.readAllLines(root.getParent().resolve("src/main/resources/application-product.properties"))
                .stream().filter(line -> line.startsWith("devicetruth.skills.installations=")).findFirst().orElseThrow();
        Set<String> installed = Set.of(property.substring(property.indexOf(':') + 1, property.length() - 1).split(","));
        int referenceCount = 0;
        for (var skill : registry.skills()) {
            Path directory = root.resolve(skill.name());
            String document = Files.readString(directory.resolve("SKILL.md"));
            String manifest = document.substring(document.indexOf('\n') + 1, document.indexOf("\n---", 4));
            require(!manifest.contains("[]"), skill.name() + ": inline lists forbidden by handbook");
            require(!skill.evidence().isEmpty() && !skill.risks().isEmpty() && !skill.citations().isEmpty(),
                    skill.name() + ": empty policy list");
            require(TOOLS.containsAll(skill.tools()), skill.name() + ": unsupported tool");
            require(skill.references().containsAll(STANDARD), skill.name() + ": missing standard references");
            require(installed.contains(skill.name() + "@" + skill.version()), skill.name() + ": default version mismatch");
            String body = loader.instructions(skill.name()).content();
            for (String reference : STANDARD)
                require(body.contains("references/" + reference), skill.name() + ": standard reference not routed");
            checkLinks(directory, directory.resolve("SKILL.md"), document);
            try (var entries = Files.list(directory.resolve("references"))) {
                for (Path entry : entries.toList())
                    require(Files.isRegularFile(entry) && entry.toRealPath().startsWith(directory.toRealPath()),
                            skill.name() + ": references must contain flat files");
            }
            for (String reference : skill.references()) {
                String content = loader.readReference(skill.name(), reference);
                require(!content.isBlank(), skill.name() + ": empty reference");
                checkLinks(directory, directory.resolve("references").resolve(reference), content);
            }
            referenceCount += skill.references().size();
            System.out.println("PASS " + skill.name() + "@" + skill.version() + " references=" + skill.references().size());
        }
        System.out.println("PASS Skills=" + registry.skills().size() + " references=" + referenceCount);
    }

    private static void checkLinks(Path directory, Path document, String content) throws Exception {
        var matcher = LINK.matcher(content);
        while (matcher.find()) {
            String target = matcher.group(1);
            if (target.startsWith("#") || target.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*")) continue;
            Path path = document.getParent().resolve(target.split("#", 2)[0]);
            require(Files.isRegularFile(path) && path.toRealPath().startsWith(directory.toRealPath()),
                    document + ": broken or external local link " + target);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
