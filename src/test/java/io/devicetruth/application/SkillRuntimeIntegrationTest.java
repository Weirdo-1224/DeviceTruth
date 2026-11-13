package io.devicetruth.application;

import com.zaxxer.hikari.HikariDataSource;
import io.devicetruth.adapter.persistence.AgentRunRepository;
import io.devicetruth.adapter.persistence.ConversationRepository;
import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.GeneralAgentService.AgentTaskInput;
import io.devicetruth.application.agent.GeneralAgentService.RunView;
import io.devicetruth.application.agent.GeneralAgentService.ServerIdentity;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.server.DeviceTruthServerApplication;
import io.devicetruth.skill.SkillRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class SkillRuntimeIntegrationTest {
    private static final String ID = "warehouse-equipment";
    private static final ServerIdentity IDENTITY = new ServerIdentity("owner-a", "project-a");
    private static final String ORIGINAL_GUIDE = "# 检查搬运设备的记录来源\n";
    @TempDir static Path root;
    private static Path guide;
    private static String pgBaseUrl;
    private static String user;
    private static String password;
    private static String database;
    private static ConfigurableApplicationContext app;
    private static JdbcTemplate jdbc;

    @BeforeAll static void start() throws Exception {
        pgBaseUrl = requireEnv("DEVICETRUTH_TEST_PG_BASE_URL");
        user = requireEnv("DEVICETRUTH_TEST_PG_USERNAME");
        password = requireEnv("DEVICETRUTH_TEST_PG_PASSWORD");
        if (!pgBaseUrl.matches("jdbc:postgresql://[A-Za-z0-9.\\-\\[\\]:]+/"))
            throw new IllegalArgumentException("PG test URL must be a host and port without credentials");
        database = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        adminSql("CREATE DATABASE " + database);
        Path skill = Files.createDirectories(root.resolve("skills").resolve(ID));
        Files.writeString(skill.resolve("SKILL.md"), """
                ---
                name: warehouse-equipment
                description: 仓储搬运设备资料核对
                version: 2.1.0
                devices:
                  - 仓储搬运设备
                tasks:
                  - 型号核对
                tools:
                  - search_knowledge
                  - get_asset
                  - unregistered_tool
                evidence:
                  - 台账
                risk:
                  - 由专业人员核对
                citation:
                  - 引用台账来源
                ---
                # 仓储搬运调查指引
                """);
        guide = Files.createDirectories(skill.resolve("references")).resolve("guide.md");
        Files.writeString(guide, ORIGINAL_GUIDE);
        app = open();
        jdbc = new JdbcTemplate(app.getBean(HikariDataSource.class));
    }

    @AfterAll static void stop() throws Exception {
        if (app != null) app.close();
        if (database != null && database.matches("devicetruth_test_[0-9a-f]{32}"))
            adminSql("DROP DATABASE IF EXISTS " + database);
    }

    private static ConfigurableApplicationContext open() {
        return new SpringApplicationBuilder(DeviceTruthServerApplication.class).run(
                "--spring.profiles.active=product", "--server.port=0", "--debug=false",
                "--devicetruth.core.reasoner=scripted", "--devicetruth.agent.reasoner=scripted",
                "--devicetruth.agent.worker-enabled=false", "--spring.datasource.url=" + pgBaseUrl + database,
                "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                "--devicetruth.product.owner-id=" + IDENTITY.ownerId(),
                "--devicetruth.product.project-id=" + IDENTITY.projectId(),
                "--devicetruth.embedding.provider=deterministic", "--devicetruth.files.root=" + root.resolve("files"),
                "--devicetruth.skills.root=" + root.resolve("skills"),
                "--devicetruth.skills.installations=" + ID + "@2.1.0",
                "--devicetruth.skills.allowed-tools=search_knowledge,unregistered_tool");
    }

    @Test void loadsOnlyAuthorizedGuidanceAndPinsVersionAcrossServiceRestart() throws Exception {
        RunView waiting;
        try (var firstSession = service(app, context -> {
            if (context.activeSkill() == null) {
                assertThat(context.availableSkills()).extracting(GeneralReasoner.SkillSummary::skillId)
                        .containsExactly(ID);
                assertThat(context.tools()).extracting(ToolSpec::name).contains("activate_skill")
                        .doesNotContain("read_skill_reference");
                return activate();
            }
            assertThat(context.activeSkill().content()).contains("仓储搬运调查指引");
            assertThat(context.tools()).extracting(ToolSpec::name)
                    .containsExactlyInAnyOrder("search_knowledge", "read_skill_reference");
            if (!context.attempted("get_asset"))
                return call("get_asset", Map.of("assetId", "warehouse-1"));
            if (context.activeSkill().reference() == null) {
                assertThat(context.feedback()).contains("TOOL_NOT_ALLOWED");
                return readReference();
            }
            assertThat(context.activeSkill().reference().content()).isEqualTo(ORIGINAL_GUIDE);
            return question();
        })) {
            var first = firstSession.service();
            waiting = start(first);
            assertThat(waiting.status()).isEqualTo("WAITING_FOR_USER");
            assertThat(first.toolEvents(waiting.runId())).anySatisfy(event -> {
                assertThat(event.toolName()).isEqualTo("get_asset");
                assertThat(event.errorCode()).isEqualTo("TOOL_NOT_ALLOWED");
            });
        }

        var port = ((WebServerApplicationContext) app).getWebServer().getPort();
        var audit = new TestRestTemplate().getForEntity(
                "http://127.0.0.1:" + port + "/api/runs/" + waiting.runId() + "/skill", Map.class);
        assertThat(audit.getStatusCode().value()).isEqualTo(200);
        assertThat(audit.getBody().get("skillId")).isEqualTo(ID);
        assertThat(audit.getBody().get("version")).isEqualTo("2.1.0");
        assertThat(audit.getBody().get("devices")).isEqualTo(List.of("仓储搬运设备"));

        try (var restarted = open(); var second = service(restarted, context -> {
            assertThat(context.activeSkill().metadata().skillId()).isEqualTo(ID);
            assertThat(context.activeSkill().metadata().version()).isEqualTo("2.1.0");
            assertThat(context.activeSkill().reference()).isNull();
            return answer();
        })) {
            assertThat(resume(second.service(), waiting).status()).isEqualTo("COMPLETED");
        }
    }

    @Test void revokedGrantStopsSameRunOnResumeAndDoesNotEnterModel() throws Exception {
        RunView waiting;
        try (var first = service(app, context -> context.activeSkill() == null ? activate() : question())) {
            waiting = start(first.service());
        }
        setEnabled(false);
        try (var restarted = open()) {
            var called = new AtomicBoolean();
            try (var second = service(restarted, context -> {
                called.set(true);
                return answer();
            })) {
                var result = resume(second.service(), waiting);
                assertThat(result.status()).isEqualTo("FAILED");
                assertThat(result.failureCode()).isEqualTo("SKILL_UNAVAILABLE");
                assertThat(called).isFalse();
            }
        } finally {
            setEnabled(true);
        }
    }

    @Test void rejectsChangedReferenceAndLateProposalAfterRevocation() throws Exception {
        RunView waiting;
        try (var first = service(app, context -> {
            if (context.activeSkill() == null) return activate();
            return context.activeSkill().reference() == null ? readReference() : question();
        })) {
            waiting = start(first.service());
        }
        Files.writeString(guide, "# 已更改的同版本参考资料\n");
        try (var second = service(app, context -> {
            assertThat(context.activeSkill().reference()).isNull();
            if (!context.attempted("read_skill_reference")) return readReference();
            assertThat(context.feedback()).contains("SKILL_CHANGED");
            setEnabled(false);
            return answer();
        })) {
            var result = resume(second.service(), waiting);
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.failureCode()).isEqualTo("SKILL_UNAVAILABLE");
            assertThat(result.result()).isNull();
        } finally {
            Files.writeString(guide, ORIGINAL_GUIDE);
            setEnabled(true);
        }
    }

    private static void setEnabled(boolean enabled) {
        jdbc.update("UPDATE dt_skill_installation SET enabled=? WHERE owner_id=? AND project_id=? AND skill_id=?",
                enabled, IDENTITY.ownerId(), IDENTITY.projectId(), ID);
    }

    private record RunningService(GeneralAgentService service) implements AutoCloseable {
        @Override public void close() { service.shutdown(); }
    }

    private static RunningService service(ConfigurableApplicationContext context,
                                          Function<GeneralReasoner.Context, ActionProposal> propose) {
        var source = context.getBean(HikariDataSource.class);
        var db = new JdbcTemplate(source);
        return new RunningService(new GeneralAgentService(new ConversationRepository(db), new AgentRunRepository(db),
                new TransactionTemplate(new DataSourceTransactionManager(source)), IDENTITY,
                context.getBean("agentCapabilityGateway", CapabilityGateway.class), () -> new GeneralReasoner() {
                    @Override public String name() { return "skill-test"; }
                    @Override public ActionProposal propose(Context input) { return propose.apply(input); }
                }, 1, 2, true, null, 60, 3, context.getBean(SkillRuntime.class)));
    }

    private static RunView start(GeneralAgentService service) throws InterruptedException {
        var submitted = service.submit(service.create().conversationId(), UUID.randomUUID(),
                new AgentTaskInput(1, "请帮我核对仓储搬运设备型号", null, null, null, null, null));
        return await(service, submitted.runId());
    }

    private static RunView resume(GeneralAgentService service, RunView waiting) throws InterruptedException {
        assertThat(waiting.pendingQuestion()).isNotNull();
        var pending = waiting.pendingQuestion();
        service.submit(waiting.conversationId(), UUID.randomUUID(), new AgentTaskInput(1, "搬运设备型号未知",
                null, null, null, null, Map.of("runId", waiting.runId().toString(), "pendingQuestionId",
                        pending.pendingQuestionId().toString(), "questionVersion", pending.questionVersion())));
        return await(service, waiting.runId());
    }

    private static RunView await(GeneralAgentService service, UUID run) throws InterruptedException {
        long end = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < end) {
            RunView view = service.run(run);
            if (!List.of("QUEUED", "RUNNING").contains(view.status())) return view;
            Thread.sleep(20);
        }
        return fail("Run remained active after timeout");
    }

    private static ActionProposal activate() { return call("activate_skill", Map.of("skillId", ID)); }
    private static ActionProposal readReference() { return call("read_skill_reference", Map.of("name", "guide.md")); }
    private static ActionProposal call(String tool, Map<String, Object> args) {
        return new ActionProposal.CallTool(tool, args, "核对调查材料");
    }
    private static ActionProposal question() {
        return new ActionProposal.AskUser("请补充设备型号。", List.of("设备型号"));
    }
    private static ActionProposal answer() {
        return new ActionProposal.Answer("KNOWLEDGE", "目前型号仍待现场确认。", List.of(), List.of(),
                List.of("型号未知"), List.of(), List.of());
    }

    private static void adminSql(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(pgBaseUrl + "postgres", user, password);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test configuration: " + key);
        return value;
    }
}
