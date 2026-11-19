package io.devicetruth.knowledge;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.Json;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.server.DeviceTruthServerApplication;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Fresh database + real product composition + local HTTP protocol fixture; no paid providers. */
class KnowledgeOperationsIntegrationTest {
    @TempDir Path temp;
    String database;
    HttpServer provider;
    ConfigurableApplicationContext app;
    final Map<String, String> environment = new HashMap<>();
    final AtomicInteger requests = new AtomicInteger();

    @BeforeEach void start() throws Exception {
        String base = System.getenv("DEVICETRUTH_TEST_PG_BASE_URL");
        String user = System.getenv("DEVICETRUTH_TEST_PG_USERNAME");
        String password = System.getenv("DEVICETRUTH_TEST_PG_PASSWORD");
        Assumptions.assumeTrue(base != null && user != null && password != null, "isolated PG required");
        database = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(base + "postgres", user, password);
             var statement = connection.createStatement()) { statement.execute("CREATE DATABASE " + database); }
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/embeddings", exchange -> {
            requests.incrementAndGet();
            var input = Json.MAPPER.readTree(exchange.getRequestBody()).path("input");
            var result = Json.MAPPER.createObjectNode(); var rows = result.putArray("data");
            for (int i = 0; i < input.size(); i++) {
                var row = rows.addObject(); row.put("index", i); var vector = row.putArray("embedding");
                vector.add(1); for (int d = 1; d < 1024; d++) vector.add(0);
            }
            result.putObject("usage").put("total_tokens", input.size());
            byte[] bytes = Json.MAPPER.writeValueAsBytes(result);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        provider.start();
        environment.put("SPRING_DATASOURCE_URL", base + database);
        environment.put("SPRING_DATASOURCE_USERNAME", user);
        environment.put("SPRING_DATASOURCE_PASSWORD", password);
        environment.put("DEVICETRUTH_OWNER_ID", "portable-owner");
        environment.put("DEVICETRUTH_PROJECT_ID", "portable-project");
        environment.put("DEVICETRUTH_EMBEDDING_PROVIDER", "openai");
        environment.put("DEVICETRUTH_EMBEDDING_BASE_URL", "http://127.0.0.1:" + provider.getAddress().getPort());
        environment.put("DEVICETRUTH_EMBEDDING_API_KEY", "local-fixture-only");
        environment.put("DEVICETRUTH_EMBEDDING_MODEL", "text-embedding-v4");
        environment.put("DEVICETRUTH_EMBEDDING_DIMENSION", "1024");
        environment.put("DEVICETRUTH_EMBEDDING_PROFILE_REVISION", "1");
        environment.put("DEVICETRUTH_KNOWLEDGE_LUCENE_ROOT", temp.resolve("index").toString());
    }

    @AfterEach void stop() throws Exception {
        if (app != null) app.close();
        if (provider != null) provider.stop(0);
        if (database != null) try (var connection = java.sql.DriverManager.getConnection(
                System.getenv("DEVICETRUTH_TEST_PG_BASE_URL") + "postgres",
                System.getenv("DEVICETRUTH_TEST_PG_USERNAME"), System.getenv("DEVICETRUTH_TEST_PG_PASSWORD"));
                var statement = connection.createStatement()) { statement.execute("DROP DATABASE " + database + " WITH (FORCE)"); }
    }

    Map<String, Object> operation(String action, String... additional) throws Exception {
        var args = new ArrayList<>(List.of("--action=" + action, "--state-root=" + temp.resolve("state")));
        args.addAll(List.of(additional));
        return KnowledgeOperations.execute(args.toArray(String[]::new), environment::get);
    }

    void openProduct() {
        var args = new ArrayList<String>();
        environment.forEach((key, value) -> args.add("--" + key + "=" + value));
        args.addAll(List.of("--server.port=0", "--spring.datasource.url=" + environment.get("SPRING_DATASOURCE_URL"),
                "--spring.datasource.username=" + environment.get("SPRING_DATASOURCE_USERNAME"),
                "--spring.datasource.password=" + environment.get("SPRING_DATASOURCE_PASSWORD"),
                "--devicetruth.agent.reasoner=scripted", "--devicetruth.knowledge.mode=HYBRID_DUAL",
                "--devicetruth.knowledge.lucene-root=" + temp.resolve("product-index"),
                "--devicetruth.files.root=" + temp.resolve("files")));
        app = new SpringApplicationBuilder(DeviceTruthServerApplication.class).profiles("product").run(args.toArray(String[]::new));
    }

    @Test void newDeploymentImportsResumesActivatesUsesSkillsAndRestartsWithoutExperimentFiles() throws Exception {
        assertThat(operation("preflight")).containsEntry("documents", 343).containsEntry("children", 682);
        assertThat(requests).hasValue(0);
        var imported = operation("import", "--budget-cny=10", "--rate-cny-per-1000-tokens=0.0005");
        assertThat(imported).containsEntry("status", "READY_INACTIVE").containsEntry("documents", 343).containsEntry("children", 682);
        int initial = requests.get();
        assertThat(initial).isPositive();
        assertThat(operation("import", "--budget-cny=10", "--rate-cny-per-1000-tokens=0.0005"))
                .containsEntry("reused_documents", 343);
        assertThat(requests).hasValue(initial);
        String release = imported.get("release_id").toString();
        operation("activate", "--release-id=" + release);
        openProduct();
        var service = app.getBean(GeneralAgentService.class);
        var conversation = service.create();
        var run = service.submit(conversation.conversationId(), UUID.randomUUID(), new GeneralAgentService.AgentTaskInput(
                1, "automotive-diagnosis Kia EV6 SC327 ICCU 直流快充低压供电排查资料", null,
                List.of(), List.of(), Instant.now(), null));
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        do { run = service.run(run.runId()); if (!Set.of("QUEUED", "RUNNING").contains(run.status())) break;
            Thread.sleep(25); } while (System.nanoTime() < until);
        assertThat(run.status()).isIn("WAITING_FOR_USER", "COMPLETED");
        assertThat(service.runSkill(run.runId())).isNotNull();
        assertThat(service.references(run.runId())).isNotEmpty();
        assertThat(service.toolEvents(run.runId())).anyMatch(event -> event.toolName().equals("search_knowledge"));
        var jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_skill_installation WHERE owner_id='portable-owner' AND project_id='portable-project'", Integer.class)).isEqualTo(8);
        var context = new io.devicetruth.core.capability.ToolContext("portable-owner", "portable-project",
                conversation.conversationId(), run.runId(), null, Instant.now(), null, Set.of(KnowledgeTools.SEARCH));
        for (var mode : List.of(HybridRetrievalService.Mode.LEGACY, HybridRetrievalService.Mode.HYBRID_DUAL)) {
            var search = app.getBean(KnowledgeSearchService.class);
            search.prepare(context, "SC327 ICCU " + mode, KnowledgeSearchService.KnowledgeFilters.NONE, mode);
            int before = requests.get();
            var tx = new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(app.getBean(HikariDataSource.class)));
            var hits = tx.execute(s -> search.searchMode(context, "SC327 ICCU " + mode, KnowledgeSearchService.KnowledgeFilters.NONE, 5, mode));
            assertThat(hits.hits()).isNotEmpty();
            assertThat(requests).hasValue(before);
        }
        app.close(); app = null;
        openProduct();
        assertThat(app.getBean(GeneralAgentService.class).references(run.runId())).isNotEmpty();
        assertThat(new JdbcTemplate(app.getBean(HikariDataSource.class)).queryForObject(
                "SELECT count(*) FROM dt_skill_installation WHERE owner_id='portable-owner' AND project_id='portable-project'", Integer.class)).isEqualTo(8);
        operation("rollback", "--release-id=" + release);
        assertThat(new JdbcTemplate(app.getBean(HikariDataSource.class)).queryForObject(
                "SELECT active FROM dt_knowledge_release WHERE id=?", Boolean.class, UUID.fromString(release))).isFalse();
        assertThat(app.getBean(GeneralAgentService.class).references(run.runId())).isNotEmpty();
    }

    @Test void importRequiresBudgetBeforeMigrationOrNetwork() {
        assertThatThrownBy(() -> operation("import")).isInstanceOf(KnowledgeException.class);
        assertThat(requests).hasValue(0);
    }
}
