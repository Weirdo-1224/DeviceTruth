package io.devicetruth.knowledge;

import io.devicetruth.adapter.Json;
import io.devicetruth.adapter.ModelConfiguration;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.Checks;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Deployable import/switch CLI. No historical experiment receipts or local container assumptions. */
public final class KnowledgeOperations {
    private KnowledgeOperations() {}

    public static void main(String[] args) {
        try { System.out.println(Json.write(execute(args, System::getenv))); }
        catch (Exception failure) {
            String code = failure instanceof KnowledgeException e ? e.code()
                    : failure instanceof EmbeddingException e ? e.code() : "KNOWLEDGE_OPERATION_FAILED";
            System.err.println(Json.write(Map.of("status", "FAILED", "code", code)));
            System.exit(1);
        }
    }

    public static Map<String, Object> execute(String[] args, Function<String, String> environment) throws Exception {
        var options = new LinkedHashMap<String, String>();
        var allowed = Set.of("action", "knowledge-root", "state-root", "release-id", "budget-cny", "rate-cny-per-1000-tokens");
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (!arg.startsWith("--") || equals < 3 || !allowed.contains(arg.substring(2, equals))
                    || options.putIfAbsent(arg.substring(2, equals), arg.substring(equals + 1)) != null)
                throw KnowledgeException.invalid("invalid operation arguments");
        }
        String action = options.getOrDefault("action", "preflight");
        if (!Set.of("preflight", "import", "status", "activate", "rollback", "build-index").contains(action))
            throw KnowledgeException.invalid("invalid action");
        String owner = setting(environment, "DEVICETRUTH_OWNER_ID", "local-owner");
        String project = setting(environment, "DEVICETRUTH_PROJECT_ID", "local-project");
        Checks.id(owner, "owner"); Checks.id(project, "project");
        var profile = profile(environment);
        KnowledgeCorpusService.Plan plan = null;
        if (Set.of("preflight", "import").contains(action)) {
            var documents = KnowledgeCorpus.load(Path.of(options.getOrDefault("knowledge-root",
                    setting(environment, "DEVICETRUTH_KNOWLEDGE_ROOT", "datasets/knowledge"))));
            plan = KnowledgeCorpusService.prepare(owner, project, setting(environment, "DEVICETRUTH_KNOWLEDGE_RELEASE", "kb-v1.0"),
                    new KnowledgeIndexProfile("E3", 512, KnowledgeIndexProfile.Context.METADATA), profile.profileVersion(), documents);
            if (action.equals("preflight")) return Map.of("status", "VERIFIED_NO_API_CALLS", "release_id", plan.releaseId(),
                    "documents", documents.size(), "children", plan.chunks(), "embedding_profile", plan.embeddingProfile(),
                    "input_token_upper", EmbeddingBudget.upperTokens(plan.documents().stream().flatMap(d -> d.children().stream())
                            .map(ContextualKnowledgeChunker.Child::embeddingText).distinct().toList()));
        }
        UUID release = Set.of("activate", "rollback", "build-index").contains(action)
                ? UUID.fromString(options.get("release-id")) : null;
        BigDecimal cap = null, rate = null;
        if (action.equals("import")) {
            cap = positive(options.get("budget-cny")); rate = positive(options.get("rate-cny-per-1000-tokens"));
            if (profile.deterministic()) throw KnowledgeCorpus.invalid("MOCK_EMBEDDING_NOT_ALLOWED");
        }
        var dataSource = new DriverManagerDataSource(required(environment, "SPRING_DATASOURCE_URL"),
                required(environment, "SPRING_DATASOURCE_USERNAME"), required(environment, "SPRING_DATASOURCE_PASSWORD"));
        var jdbc = new JdbcTemplate(dataSource);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        if (action.equals("import")) Flyway.configure().dataSource(dataSource).cleanDisabled(true).load().migrate();
        if (action.equals("status")) return Map.of("releases", jdbc.queryForList(
                "SELECT id,kb_release,index_profile,embedding_profile,status,active,is_test,expected_documents,expected_chunks FROM dt_knowledge_release WHERE owner_id=? AND project_id=? ORDER BY created_at", owner, project));
        EmbeddingClient client = profile.deterministic() ? new DeterministicEmbeddingClient(profile) : new OpenAiCompatibleEmbeddingClient(profile);
        var service = new KnowledgeCorpusService(jdbc, tx, client);
        if (!action.equals("import")) {
            var rows = jdbc.queryForList("SELECT embedding_profile FROM dt_knowledge_release WHERE id=? AND owner_id=? AND project_id=?", release, owner, project);
            if (rows.size() != 1) throw KnowledgeException.notFoundOrDenied();
            if (!profile.profileVersion().equals(rows.getFirst().get("embedding_profile")))
                throw new KnowledgeException("IMPORT_EMBEDDING_PROFILE_MISMATCH", 409);
            if (action.equals("rollback")) service.deactivate(release, owner, project);
            else {
                var context = new ToolContext(owner, project, null, UUID.randomUUID(), null, Instant.now(), null, Set.of(KnowledgeTools.SEARCH));
                var filters = new KnowledgeSearchService.KnowledgeFilters(null, null, null, release, null);
                var snapshot = new ScopedKnowledgeStore(jdbc, profile).load(context, filters);
                if (snapshot.children().isEmpty()) throw new KnowledgeException("RELEASE_NOT_ACTIVATABLE", 409);
                new LuceneBm25Index(Path.of(setting(environment, "DEVICETRUTH_KNOWLEDGE_LUCENE_ROOT", "outputs/knowledge-lucene"))).build(snapshot);
                if (action.equals("activate")) service.activate(release, owner, project);
            }
            return Map.of("status", "PASS", "action", action, "release_id", release);
        }
        Path state = Path.of(options.getOrDefault("state-root", "outputs/knowledge-admin"));
        Files.createDirectories(state);
        String key = KnowledgeCorpus.hash(required(environment, "SPRING_DATASOURCE_URL") + "|" + plan.releaseId()
                + "|" + profile.baseUrl() + "|" + profile.sendDimensions());
        try (var channel = FileChannel.open(state.resolve(key + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new KnowledgeException("RELEASE_IMPORT_BUSY", 409);
            String config = KnowledgeCorpus.hash(profile.profileVersion() + "|" + profile.baseUrl() + "|" + profile.sendDimensions() + "|test=false");
            var cached = Set.copyOf(jdbc.queryForList("SELECT input_checksum FROM dt_knowledge_embedding_cache WHERE owner_id=? AND project_id=? AND config_checksum=? AND embedding_profile=? AND NOT is_test",
                    String.class, owner, project, config, profile.profileVersion()));
            var pending = plan.documents().stream().flatMap(d -> d.children().stream())
                    .filter(c -> !cached.contains(c.embeddingChecksum())).map(ContextualKnowledgeChunker.Child::embeddingText).distinct().toList();
            var budget = new EmbeddingBudget(state.resolve(key + ".json"), cap, rate, EmbeddingBudget.upperTokens(pending));
            var importer = new KnowledgeCorpusService(jdbc, tx, new OpenAiCompatibleEmbeddingClient(profile, budget));
            var result = importer.stagePlan(plan);
            importer.markReady(plan);
            boolean active = Boolean.TRUE.equals(jdbc.queryForObject("SELECT active FROM dt_knowledge_release WHERE id=?", Boolean.class, result.releaseId()));
            return Map.of("status", active ? "READY_ACTIVE" : "READY_INACTIVE", "release_id", result.releaseId(), "documents", result.documents(),
                    "children", result.chunks(), "reused_documents", result.reusedDocuments(), "budget", budget.summary());
        }
    }

    private static BigDecimal positive(String value) {
        if (value == null) throw KnowledgeException.invalid("explicit budget and price required");
        BigDecimal number = new BigDecimal(value);
        if (number.signum() <= 0) throw KnowledgeException.invalid("budget and price must be positive");
        return number;
    }

    static EmbeddingProfile profile(Function<String, String> environment) {
        var local = ModelConfiguration.local();
        Function<String, String> model = key -> setting(environment, key, local.apply(key));
        return EmbeddingProfile.of(setting(model, "DEVICETRUTH_EMBEDDING_PROVIDER", "openai"),
                setting(model, "DEVICETRUTH_EMBEDDING_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                setting(model, "DEVICETRUTH_EMBEDDING_API_KEY", setting(model, "DASHSCOPE_API_KEY", "")),
                setting(model, "DEVICETRUTH_EMBEDDING_MODEL", "text-embedding-v4"),
                Integer.parseInt(setting(model, "DEVICETRUTH_EMBEDDING_DIMENSION", "1024")),
                setting(model, "DEVICETRUTH_EMBEDDING_PROFILE_REVISION", "1"),
                Long.parseLong(setting(model, "DEVICETRUTH_EMBEDDING_TIMEOUT_MS", "15000")),
                Boolean.parseBoolean(setting(model, "DEVICETRUTH_EMBEDDING_SEND_DIMENSIONS", "true")));
    }

    private static String required(Function<String, String> environment, String key) {
        String value = environment.apply(key);
        if (value == null || value.isBlank()) throw new KnowledgeException("OPERATION_CONFIGURATION_MISSING", 400);
        return value;
    }

    private static String setting(Function<String, String> environment, String key, String fallback) {
        String value = environment.apply(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
