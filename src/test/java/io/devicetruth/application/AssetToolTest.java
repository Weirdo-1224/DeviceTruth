package io.devicetruth.application;

import io.devicetruth.adapter.persistence.AssetRegistryRepository;
import io.devicetruth.application.agent.AssetSnapshotImporter;
import io.devicetruth.application.agent.AssetTools;
import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.Repositories.AssetComponent;
import io.devicetruth.core.capability.Repositories.AssetConfigurationRecord;
import io.devicetruth.core.capability.Repositories.AssetIdentity;
import io.devicetruth.core.capability.Repositories.AssetMaintenanceRecord;
import io.devicetruth.core.capability.Repositories.AssetSnapshot;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AssetFacts.AssetOutcome;
import io.devicetruth.core.contract.AssetFacts.ConfigurationEntry;
import io.devicetruth.core.contract.AssetFacts.ConfigurationOutcome;
import io.devicetruth.core.contract.AssetFacts.ConflictStatus;
import io.devicetruth.core.contract.AssetFacts.InstallationStatus;
import io.devicetruth.core.contract.AssetFacts.MaintenanceEntry;
import io.devicetruth.core.contract.AssetFacts.MaintenanceOutcome;
import io.devicetruth.core.contract.AssetFacts.VerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * 03-01-T1/T2：真实 PostgreSQL 下的设备结构化记录与查询事实边界。
 * 数据来自打包的只读模拟夹具（CAR-B），不修改 fixture；查询时点由用例显式给出。
 * 只证明“按资产/时点查询来源、核实状态与冲突”的结构性行为，不代表真实现场数据。
 */
class AssetToolTest {
    private static String databaseName;
    private static String databaseBaseUrl;
    private static String username;
    private static String password;
    private static JdbcTemplate jdbc;
    private static AssetRegistryRepository registry;
    private static AssetTools tools;
    private static ToolContext context;
    private static UUID runId;

    private static final String OWNER = "owner-a";
    private static final String PROJECT = "project-a";
    private static final String ASSET = "CAR-B";
    private static final Instant FIELD_OBSERVED = Instant.parse("2026-11-03T04:00:00Z");
    /** 夹具 CAR-B-FIELD-1 的实际观察时刻（fixture 只读，期望以它为准）。 */
    private static final Instant FIELD_CONFIRMATION_OBSERVED = Instant.parse("2026-11-03T03:00:00Z");
    private static final Instant AFTER_FIELD_WINDOW = Instant.parse("2026-11-05T00:00:00Z");

    @BeforeAll static void start() throws Exception {
        databaseBaseUrl = requiredEnvironment("DEVICETRUTH_TEST_PG_BASE_URL");
        username = requiredEnvironment("DEVICETRUTH_TEST_PG_USERNAME");
        password = requiredEnvironment("DEVICETRUTH_TEST_PG_PASSWORD");
        databaseName = "devicetruth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(databaseBaseUrl + "postgres", username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        // 会话时区固定 UTC，让库内 TIMESTAMPTZ 的文本渲染与用例给出的业务时点一致（比较语义本身与时区无关）。
        var source = new DriverManagerDataSource(databaseBaseUrl + databaseName + "?options=-c%20timezone%3DUTC",
                username, password);
        migrate(source);
        jdbc = new JdbcTemplate(source);
        registry = new AssetRegistryRepository(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)));
        AssetSnapshotImporter.importFixture(registry, OWNER, PROJECT, ASSET);
        runId = seedRun();
        var writer = new RecordingReferenceWriter(jdbc);
        tools = new AssetTools(registry, writer);
        context = new ToolContext(OWNER, PROJECT, UUID.randomUUID(), runId, ASSET,
                FIELD_OBSERVED, FIELD_OBSERVED, Set.of(AssetTools.GET_ASSET, AssetTools.GET_CONFIGURATION,
                AssetTools.GET_MAINTENANCE_HISTORY));
    }

    @AfterAll static void stop() throws Exception {
        if (databaseName != null && databaseName.matches("devicetruth_test_[0-9a-f]{32}")) {
            try (var connection = java.sql.DriverManager.getConnection(databaseBaseUrl + "postgres", username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + databaseName);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test configuration: " + name);
        return value;
    }

    private static void migrate(javax.sql.DataSource source) {
        org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .cleanDisabled(true).validateMigrationNaming(true).load().migrate();
    }

    private static UUID seedRun() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dt_conversation(id,owner_id,project_id,version,created_at,updated_at)
                VALUES (?,?,?,0,now(),now())
                """, conversationId, OWNER, PROJECT);
        jdbc.update("""
                INSERT INTO dt_message(id,conversation_id,request_key,seq,role,content,selected_asset_id,
                    requested_at,as_of,owner_id,project_id)
                VALUES (?,?,?,1,'USER','CAR-B 当前 CTRL-01 是什么型号',?,now(),now(),?,?)
                """, messageId, conversationId, UUID.randomUUID(), ASSET, OWNER, PROJECT);
        jdbc.update("""
                INSERT INTO dt_agent_run(id,conversation_id,goal_message_id,status,version,requested_at,as_of,
                    budget,created_at,updated_at,owner_id,project_id,round_number)
                VALUES (?,?,?,'COMPLETED',0,now(),?,CAST(? AS jsonb),now(),now(),?,?,1)
                """, id, conversationId, messageId, java.sql.Timestamp.from(FIELD_OBSERVED),
                "{\"maxSteps\":12,\"maxRepeatedActions\":2}", OWNER, PROJECT);
        return id;
    }

    // ---------- T1：结构化记录与导入 ----------

    @Test
    void fixtureImportKeepsIdentitySourceAndIsSimulated() {
        AssetIdentity identity = registry.asset(OWNER, PROJECT, ASSET);
        assertThat(identity.assetId()).isEqualTo(ASSET);
        assertThat(registry.components(OWNER, PROJECT, ASSET)).extracting(AssetComponent::componentId)
                .containsExactly("CTRL-01");
        var records = registry.configurations(OWNER, PROJECT, ASSET, null, "moduleVersion",
                Instant.parse("2025-06-01T00:00:00Z"));
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().value()).isEqualTo("D-120A");
        assertThat(records.getFirst().sourceType()).isEqualTo("FACTORY");
        assertThat(records.getFirst().dataNature()).isEqualTo("SIMULATED");
        assertThat(records.getFirst().verificationStatus()).isEqualTo("DOCUMENTED");
    }

    @Test
    void fixtureImportIsIdempotentAndDoesNotRewriteExistingRecords() throws Exception {
        long before = count("dt_asset_configuration");
        AssetSnapshotImporter.importFixture(registry, OWNER, PROJECT, ASSET);
        AssetSnapshotImporter.importFixture(registry, OWNER, PROJECT, ASSET);
        assertThat(count("dt_asset_configuration")).isEqualTo(before);
        // 维修计数按资产限定：库内其他用例的资产（如 CAR-BMF）不算进来
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_asset_maintenance WHERE asset_id=?",
                Long.class, ASSET)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dt_asset WHERE id=?", Integer.class, ASSET))
                .isEqualTo(1);
    }

    @Test
    void maintenanceRecordReturnsBeforeAndAfterInsteadOfFinalDiagnosis() {
        MaintenanceEntry entry = tools.maintenance(new AssetTools.MaintenanceRequest(ASSET, "CTRL-01",
                FIELD_OBSERVED), context).entries().stream()
                .filter(e -> "CTRL-01".equals(e.componentId())).findFirst().orElseThrow();
        assertThat(entry.operation()).isEqualTo("REPLACEMENT");
        assertThat(entry.beforeValue()).isNull();
        assertThat(entry.afterValue()).isEqualTo("D-120B");
        assertThat(entry.sourceType()).isEqualTo("REPLACEMENT");
        assertThat(entry.verificationStatus()).isEqualTo(VerificationStatus.DOCUMENTED);
    }

    @Test
    void snapshotCannotImportRecordsForAnotherAsset() {
        AssetIdentity identity = new AssetIdentity(ASSET, OWNER, PROJECT);
        var foreign = new AssetConfigurationRecord(UUID.randomUUID().toString(), "CAR-A", "CTRL-01",
                "moduleVersion", "D-120A", "FACTORY", "EVIL-1", "simulated:EVIL", "SIMULATED", "DOCUMENTED",
                10, Instant.now(), null, Instant.now(), null);
        var snapshot = new AssetSnapshot(identity, List.of(new AssetComponent(ASSET, "CTRL-01", "CTRL-01")),
                List.of(foreign), List.of());
        assertThatThrownBy(() -> registry.importSnapshot(snapshot)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- T2：查询事实边界 ----------

    @Test
    void assetQueryReturnsIdentityAndComponentsOnly() {
        AssetOutcome outcome = tools.asset(new AssetTools.AssetRequest(ASSET), context);
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.asset().componentIds()).containsExactly("CTRL-01");
        assertThat(outcome.asset().dataNature()).isEqualTo("SIMULATED");
        // 身份视图不带任何配置值，避免把身份当成当前配置结论
        assertThat(outcome.asset().toString()).doesNotContain("D-120");
    }

    @Test
    void latestDocumentWithoutFieldVerificationStaysUnconfirmed() {
        // 2026-11-05 已超出 FIELD-1 的有效期（validTo 2026-11-04），此时只剩资料/更换记录
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(ASSET, "CTRL-01",
                "moduleVersion", AFTER_FIELD_WINDOW), context);
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.installationStatus()).isEqualTo(InstallationStatus.UNCONFIRMED);
        assertThat(outcome.requiredChecks()).isNotEmpty();
        assertThat(outcome.entries()).isNotEmpty();
        assertThat(outcome.entries()).allSatisfy(entry -> assertThat(entry.fieldVerified()).isFalse());
        assertThat(outcome.limitations()).anyMatch(text -> text.contains("不能证明"));
        // 最新记录确实写着 D-120B，但不得被当作“当前已安装已确认”
        assertThat(outcome.entries()).extracting(ConfigurationEntry::value).contains("D-120B");
    }

    @Test
    void fieldConfirmationAtValidTimeIsTheOnlyConfirmedPath() {
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(ASSET, "CTRL-01",
                "moduleVersion", FIELD_OBSERVED), context);
        assertThat(outcome.installationStatus()).isEqualTo(InstallationStatus.FIELD_CONFIRMED);
        assertThat(outcome.requiredChecks()).isEmpty();
        assertThat(outcome.entries()).anySatisfy(entry -> {
            assertThat(entry.fieldVerified()).isTrue();
            assertThat(entry.sourceType()).isEqualTo("FIELD_CONFIRMATION");
            assertThat(entry.observedAt()).isEqualTo(FIELD_CONFIRMATION_OBSERVED);
        });
    }

    @Test
    void sameAuthorityConflictIsExplicit() {
        // 专用资产，避免污染 CAR-B 的其他用例；两条同等权威记录同时有效但取值不同
        String asset = "CAR-BXC";
        registry.importSnapshot(new AssetSnapshot(new AssetIdentity(asset, OWNER, PROJECT),
                List.of(new AssetComponent(asset, "CTRL-01", "CTRL-01")),
                List.of(conflict(asset, "CONFLICT-A", "D-120C"), conflict(asset, "CONFLICT-B", "D-120D")),
                List.of()));
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01",
                "moduleVersion", FIELD_OBSERVED), context);
        assertThat(outcome.conflict()).isEqualTo(ConflictStatus.CONFLICT);
        // 冲突时仍不确诊：冲突本身说明当前现场取值不确定
        assertThat(outcome.entries()).extracting(ConfigurationEntry::value).contains("D-120C", "D-120D");
    }

    @Test
    void userStatementIsDocumentedNotFieldVerified() {
        // 专用资产，避免污染 CAR-B 的其他用例
        String asset = "CAR-BUS";
        registry.importSnapshot(new AssetSnapshot(new AssetIdentity(asset, OWNER, PROJECT),
                List.of(new AssetComponent(asset, "CTRL-01", "CTRL-01")),
                List.of(userStatement(asset, "USER-1", "D-999")), List.of()));
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01",
                "moduleVersion", Instant.parse("2026-10-01T00:00:00Z")), context);
        assertThat(outcome.installationStatus()).isEqualTo(InstallationStatus.UNCONFIRMED);
        assertThat(outcome.entries()).anySatisfy(entry -> {
            if ("USER_STATEMENT".equals(entry.sourceType())) {
                assertThat(entry.verificationStatus()).isEqualTo(VerificationStatus.DOCUMENTED);
                assertThat(entry.value()).isEqualTo("D-999");
            }
        });
        assertThat(outcome.limitations()).anyMatch(text -> text.contains("用户陈述"));
    }

    @Test
    void recordsOutsideValidityWindowAreNotReturned() {
        // 2025-06-01 只能看到出厂记录，更换与现场记录尚未生效
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(ASSET, "CTRL-01",
                "moduleVersion", Instant.parse("2025-06-01T00:00:00Z")), context);
        assertThat(outcome.entries()).extracting(ConfigurationEntry::value).containsExactly("D-120A");
    }

    @Test
    void crossOwnerAssetIsIndistinguishableFromMissing() {
        ToolContext other = new ToolContext("owner-b", PROJECT, context.conversationId(), context.runId(), ASSET,
                FIELD_OBSERVED, FIELD_OBSERVED, context.allowedTools());
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(ASSET, "CTRL-01",
                "moduleVersion", FIELD_OBSERVED), other);
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("ASSET_NOT_FOUND");

        ConfigurationOutcome missing = tools.configuration(new AssetTools.ConfigurationRequest("CAR-ZZ", "CTRL-01",
                "moduleVersion", FIELD_OBSERVED), context);
        assertThat(missing.success()).isFalse();
        assertThat(missing.errorCode()).isEqualTo("ASSET_NOT_FOUND");
    }

    @Test
    void invalidArgumentsAndUnauthorizedToolAreRejectedDistinctly() {
        // attribute 必填是“模型工具入口”的契约：模型必须指明字段，不能一次拉全量配置。
        // 服务层组合视图传 null 表示全部字段（HTTP 资产视图使用），因此这里经网关验证真实边界。
        var gateway = new CapabilityGateway();
        tools.registerInto(gateway);
        ToolOutcome missingAttribute = gateway.executeTool(context, AssetTools.GET_CONFIGURATION,
                Map.of("assetId", ASSET, "componentId", "CTRL-01", "asOf", FIELD_OBSERVED.toString()));
        assertThat(missingAttribute.success()).isFalse();
        assertThat(missingAttribute.error().code()).isEqualTo("INVALID_ARGUMENTS");

        // 服务层显式查全量字段（HTTP 组合视图路径）应成功且返回记录
        ConfigurationOutcome allAttributes = tools.configuration(new AssetTools.ConfigurationRequest(ASSET,
                "CTRL-01", null, FIELD_OBSERVED), context);
        assertThat(allAttributes.success()).isTrue();
        assertThat(allAttributes.entries()).isNotEmpty();

        ToolContext notAllowed = new ToolContext(OWNER, PROJECT, context.conversationId(), context.runId(), ASSET,
                FIELD_OBSERVED, FIELD_OBSERVED, Set.of(AssetTools.GET_ASSET));
        ConfigurationOutcome denied = tools.configuration(new AssetTools.ConfigurationRequest(ASSET, "CTRL-01",
                "moduleVersion", FIELD_OBSERVED), notAllowed);
        assertThat(denied.success()).isFalse();
        assertThat(denied.errorCode()).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void maintenanceHistoryRespectsTimePoint() {
        MaintenanceOutcome before = tools.maintenance(new AssetTools.MaintenanceRequest(ASSET, null,
                Instant.parse("2026-05-01T00:00:00Z")), context);
        assertThat(before.entries()).isEmpty();
        MaintenanceOutcome after = tools.maintenance(new AssetTools.MaintenanceRequest(ASSET, null,
                FIELD_OBSERVED), context);
        assertThat(after.entries()).hasSize(1);
        assertThat(after.entries()).allSatisfy(entry -> assertThat(entry.sourceType()).isEqualTo("REPLACEMENT"));
    }

    // ---------- 工具注册与网关链路 ----------

    @Test
    void toolsAreRegisteredAndCallableThroughTheGateway() {
        var gateway = new CapabilityGateway();
        tools.registerInto(gateway);
        assertThat(gateway.toolNames()).containsExactlyInAnyOrder(AssetTools.GET_ASSET,
                AssetTools.GET_CONFIGURATION, AssetTools.GET_MAINTENANCE_HISTORY);
        // 专用 run：引用登记按 run 累计，不与其他用例共享计数
        UUID dedicatedRun = seedRun();
        ToolContext dedicated = new ToolContext(OWNER, PROJECT, UUID.randomUUID(), dedicatedRun, ASSET,
                FIELD_OBSERVED, FIELD_OBSERVED, context.allowedTools());
        ToolOutcome outcome = gateway.executeTool(dedicated, AssetTools.GET_CONFIGURATION,
                Map.of("assetId", ASSET, "componentId", "CTRL-01", "attribute", "moduleVersion",
                        "asOf", FIELD_OBSERVED.toString()));
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.dataNature()).isEqualTo("SIMULATED");
        // 同一 run 同一来源记录只登记一次引用：重复调用后计数不变
        gateway.executeTool(dedicated, AssetTools.GET_CONFIGURATION,
                Map.of("assetId", ASSET, "componentId", "CTRL-01", "attribute", "moduleVersion",
                        "asOf", FIELD_OBSERVED.toString()));
        long distinctSources = jdbc.queryForObject(
                "SELECT count(DISTINCT source_id) FROM dt_evidence_reference WHERE run_id=? AND source_type='CONFIGURATION'",
                Long.class, dedicatedRun);
        long total = jdbc.queryForObject(
                "SELECT count(*) FROM dt_evidence_reference WHERE run_id=? AND source_type='CONFIGURATION'",
                Long.class, dedicatedRun);
        assertThat(total).isEqualTo(distinctSources).isEqualTo(3);
    }

    @Test
    void gatewayRejectsUnknownArgumentWithoutCallingTheTool() {
        var gateway = new CapabilityGateway();
        tools.registerInto(gateway);
        ToolOutcome outcome = gateway.executeTool(context, AssetTools.GET_CONFIGURATION,
                Map.of("assetId", ASSET, "attribute", "moduleVersion", "ownerId", "owner-b"));
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error().code()).isEqualTo("INVALID_ARGUMENTS");
    }

    @Test
    void gatewayRejectsToolOutsideServerAuthorizedScope() {
        var gateway = new CapabilityGateway();
        tools.registerInto(gateway);
        ToolContext scoped = new ToolContext(OWNER, PROJECT, context.conversationId(), context.runId(), ASSET,
                FIELD_OBSERVED, FIELD_OBSERVED, Set.of(AssetTools.GET_ASSET));
        ToolOutcome outcome = gateway.executeTool(scoped, AssetTools.GET_MAINTENANCE_HISTORY, Map.of());
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error().code()).isEqualTo("TOOL_NOT_ALLOWED");
    }

    // ---------- 复审修复（4 项 P1）回归 ----------

    @Test
    void invalidToolArgumentsAreRejectedWithoutQueryingRegistry() {
        // P1-1：handler 内参数解析失败必须直接拒绝，不得退化为默认查询
        var mocked = org.mockito.Mockito.mock(
                io.devicetruth.core.capability.Repositories.AssetRegistryRepository.class);
        var gateway = new CapabilityGateway();
        new AssetTools(mocked, new RecordingReferenceWriter(jdbc)).registerInto(gateway);
        Long before = jdbc.queryForObject(
                "SELECT count(*) FROM dt_evidence_reference WHERE run_id=? AND source_type='CONFIGURATION'",
                Long.class, context.runId());

        ToolOutcome badTime = gateway.executeTool(context, AssetTools.GET_CONFIGURATION,
                Map.of("assetId", ASSET, "attribute", "moduleVersion", "asOf", "not-a-date"));
        assertThat(badTime.success()).isFalse();
        assertThat(badTime.error().code()).isEqualTo("INVALID_ARGUMENTS");

        // 字段类型错误
        ToolOutcome badType = gateway.executeTool(context, AssetTools.GET_CONFIGURATION,
                Map.of("assetId", ASSET, "attribute", 123));
        assertThat(badType.success()).isFalse();
        assertThat(badType.error().code()).isEqualTo("INVALID_ARGUMENTS");

        ToolOutcome blankComponent = gateway.executeTool(context, AssetTools.GET_CONFIGURATION,
                Map.of("assetId", ASSET, "attribute", "moduleVersion", "componentId", "  "));
        assertThat(blankComponent.success()).isFalse();
        assertThat(blankComponent.error().code()).isEqualTo("INVALID_ARGUMENTS");

        // 三个非法请求都不允许触碰仓储；引用计数与本用例开始前持平（共享 run 上其他用例的登记不计）
        org.mockito.Mockito.verify(mocked, org.mockito.Mockito.never())
                .configurations(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        Long after = jdbc.queryForObject(
                "SELECT count(*) FROM dt_evidence_reference WHERE run_id=? AND source_type='CONFIGURATION'",
                Long.class, context.runId());
        assertThat(after).isEqualTo(before);
    }

    @Test
    void referencesPersistFactExcerptAndActualQueryScope() {
        // P1-2：引用必须携带事实摘要，并挂到实际查询的资产与时点（非上下文默认值）
        UUID dedicatedRun = seedRun();
        ToolContext dedicated = new ToolContext(OWNER, PROJECT, UUID.randomUUID(), dedicatedRun, ASSET,
                FIELD_OBSERVED, FIELD_OBSERVED, Set.of(AssetTools.GET_CONFIGURATION));
        Instant historical = Instant.parse("2025-06-01T00:00:00Z");
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(ASSET, "CTRL-01",
                "moduleVersion", historical), dedicated);
        assertThat(outcome.success()).isTrue();

        var rows = jdbc.queryForList("""
                SELECT asset_id, as_of, citation_excerpt FROM dt_evidence_reference
                WHERE run_id=? AND source_type='CONFIGURATION'
                """, dedicatedRun);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("asset_id")).isEqualTo(ASSET);
            assertThat(((java.sql.Timestamp) row.get("as_of")).toInstant()).isEqualTo(historical);
            assertThat((String) row.get("citation_excerpt")).contains("attribute=moduleVersion").contains("D-120A");
        });

        // 经仓储读取的引用视图必须能还原事实摘要，可核对、可引用
        var runRepository = new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc);
        var view = runRepository.references(
                new io.devicetruth.application.agent.GeneralAgentService.ServerIdentity(OWNER, PROJECT), dedicatedRun);
        assertThat(view).isNotEmpty();
        assertThat(view).allSatisfy(reference -> {
            assertThat(reference.excerpt()).isNotBlank();
            assertThat(reference.excerpt()).contains("attribute=moduleVersion");
        });
    }

    @Test
    void mixedVerificationAcrossComponentsKeepsChecksForUnverifiedGroups() {
        // P1-3：一项已核实、一项仅文档记载时，不得输出无待核验项的整体确认
        String asset = "CAR-BMV";
        registry.importSnapshot(new AssetSnapshot(new AssetIdentity(asset, OWNER, PROJECT),
                List.of(new AssetComponent(asset, "COMP-A", "COMP-A"), new AssetComponent(asset, "COMP-B", "COMP-B")),
                List.of(fieldConfirmed(asset, "COMP-A", "FA-1", "D-1"), documented(asset, "COMP-B", "FB-1", "D-2")),
                List.of()));
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(asset, null,
                "moduleVersion", FIELD_OBSERVED), context);
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.installationStatus()).isEqualTo(InstallationStatus.UNCONFIRMED);
        assertThat(outcome.conflict()).isEqualTo(ConflictStatus.NONE);
        assertThat(outcome.requiredChecks()).hasSize(1);
        assertThat(outcome.requiredChecks().getFirst()).contains("COMP-B");
    }

    @Test
    void conflictingFieldVerificationsAreNotConfirmed() {
        // P1-3：两条现场核实值冲突时，不得确认、必须保留待核验要求
        String asset = "CAR-BFC";
        registry.importSnapshot(new AssetSnapshot(new AssetIdentity(asset, OWNER, PROJECT),
                List.of(new AssetComponent(asset, "CTRL-01", "CTRL-01")),
                List.of(fieldConfirmed(asset, "CTRL-01", "FC-1", "D-X"),
                        fieldConfirmed(asset, "CTRL-01", "FC-2", "D-Y")), List.of()));
        ConfigurationOutcome outcome = tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01",
                "moduleVersion", FIELD_OBSERVED), context);
        assertThat(outcome.conflict()).isEqualTo(ConflictStatus.CONFLICT);
        assertThat(outcome.installationStatus()).isEqualTo(InstallationStatus.UNCONFIRMED);
        assertThat(outcome.requiredChecks()).isNotEmpty();
    }

    @Test
    void sameSourceCanCarryMultipleFactsAndReimportIsStable() {
        // P1-4：同一来源可携带多条不同事实；重复导入幂等且不改写历史
        String asset = "CAR-BMF";
        AssetSnapshot snapshot = new AssetSnapshot(new AssetIdentity(asset, OWNER, PROJECT),
                List.of(new AssetComponent(asset, "CTRL-01", "CTRL-01")),
                List.of(configFact(asset, "SRC-MF", "moduleVersion", "D-A"),
                        configFact(asset, "SRC-MF", "firmwareVersion", "D-B")),
                List.of(maintenanceFact(asset, "SRC-MM", Instant.parse("2026-01-01T00:00:00Z")),
                        maintenanceFact(asset, "SRC-MM", Instant.parse("2026-02-01T00:00:00Z"))));
        registry.importSnapshot(snapshot);
        assertThat(countBySource("dt_asset_configuration", asset, "SRC-MF")).isEqualTo(2);
        assertThat(countBySource("dt_asset_maintenance", asset, "SRC-MM")).isEqualTo(2);
        registry.importSnapshot(snapshot);
        assertThat(countBySource("dt_asset_configuration", asset, "SRC-MF")).isEqualTo(2);
        assertThat(countBySource("dt_asset_maintenance", asset, "SRC-MM")).isEqualTo(2);
    }


    @Test
    void sameSourceSameTimeMultiFactsRegisterDistinctReferences() {
        // P1（第二轮）：引用身份 = 事实记录，同一来源同一记录时间的多条事实各自成引用
        String asset = "CAR-BRI";
        registry.importSnapshot(new AssetSnapshot(new AssetIdentity(asset, OWNER, PROJECT),
                List.of(new AssetComponent(asset, "CTRL-01", "CTRL-01")),
                List.of(configFact(asset, "SRC-RI", "moduleVersion", "D-A"),
                        configFact(asset, "SRC-RI", "firmwareVersion", "D-B")),
                List.of(maintenanceFact(asset, "SRC-RM", Instant.parse("2026-01-01T00:00:00Z")))));
        UUID dedicatedRun = seedRun();
        ToolContext dedicated = new ToolContext(OWNER, PROJECT, UUID.randomUUID(), dedicatedRun, asset,
                FIELD_OBSERVED, FIELD_OBSERVED, Set.of(AssetTools.GET_CONFIGURATION, AssetTools.GET_MAINTENANCE_HISTORY));

        assertThat(tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01",
                "moduleVersion", FIELD_OBSERVED), dedicated).success()).isTrue();
        assertThat(tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01",
                "firmwareVersion", FIELD_OBSERVED), dedicated).success()).isTrue();
        assertThat(tools.maintenance(new AssetTools.MaintenanceRequest(asset, "CTRL-01",
                FIELD_OBSERVED), dedicated).success()).isTrue();
        // 重复查询同一事实必须幂等，不产生新引用
        assertThat(tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01",
                "moduleVersion", FIELD_OBSERVED), dedicated).success()).isTrue();

        var rows = jdbc.queryForList("""
                SELECT id, citation_excerpt FROM dt_evidence_reference
                WHERE run_id=? AND source_type='CONFIGURATION' ORDER BY citation_excerpt
                """, dedicatedRun);
        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("id")).distinct()).hasSize(3);
        assertThat(rows).anySatisfy(row -> assertThat((String) row.get("citation_excerpt"))
                .contains("attribute=moduleVersion").contains("value=D-A"));
        assertThat(rows).anySatisfy(row -> assertThat((String) row.get("citation_excerpt"))
                .contains("attribute=firmwareVersion").contains("value=D-B"));
        assertThat(rows).anySatisfy(row -> assertThat((String) row.get("citation_excerpt"))
                .contains("operation=REPLACEMENT"));
    }

    @Test
    void sameSourceSameAttributeHistoryIsKeptAndQueryFollowsValidity() {
        // P1（第二轮）：配置事实身份含生效时点，同来源同属性的历史版本不再被丢弃
        String asset = "CAR-BHT";
        AssetSnapshot history = new AssetSnapshot(new AssetIdentity(asset, OWNER, PROJECT),
                List.of(new AssetComponent(asset, "CTRL-01", "CTRL-01")),
                List.of(historyFact(asset, "SRC-HT", "D-OLD", Instant.parse("2026-01-01T00:00:00Z"),
                                Instant.parse("2026-06-01T00:00:00Z")),
                        historyFact(asset, "SRC-HT", "D-NEW", Instant.parse("2026-06-01T00:00:00Z"), null)),
                List.of());
        registry.importSnapshot(history);
        assertThat(countBySource("dt_asset_configuration", asset, "SRC-HT")).isEqualTo(2);

        ConfigurationOutcome before = tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01",
                "moduleVersion", Instant.parse("2026-03-01T00:00:00Z")), context);
        assertThat(before.entries()).extracting(ConfigurationEntry::value).containsExactly("D-OLD");
        ConfigurationOutcome after = tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01",
                "moduleVersion", Instant.parse("2026-09-01T00:00:00Z")), context);
        assertThat(after.entries()).extracting(ConfigurationEntry::value).containsExactly("D-NEW");

        // 重复导入不增加记录、不改写历史
        registry.importSnapshot(history);
        assertThat(countBySource("dt_asset_configuration", asset, "SRC-HT")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT value FROM dt_asset_configuration WHERE asset_id=? AND source_id=? AND valid_from=?",
                String.class, asset, "SRC-HT", java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))))
                .isEqualTo("D-OLD");
    }

    @Test
    void configurationReferencesKeepEachQueryTimeAndRemainIdempotent() {
        assertQueryTimesAreAuditable(false);
    }

    @Test
    void maintenanceReferencesKeepEachQueryTimeAndRemainIdempotent() {
        assertQueryTimesAreAuditable(true);
    }

    private void assertQueryTimesAreAuditable(boolean maintenance) {
        String asset = maintenance ? "TIME-MAINT" : "TIME-CONFIG";
        registry.importSnapshot(new AssetSnapshot(new AssetIdentity(asset, OWNER, PROJECT),
                List.of(new AssetComponent(asset, "CTRL-01", "CTRL-01")),
                List.of(configFact(asset, "TIME-SRC", "moduleVersion", "D-A")),
                List.of(maintenanceFact(asset, "TIME-MAINT-SRC", Instant.parse("2026-01-01T00:00:00Z")))));
        UUID run = seedRun();
        ToolContext scoped = new ToolContext(OWNER, PROJECT, UUID.randomUUID(), run, ASSET,
                FIELD_OBSERVED, FIELD_OBSERVED, Set.of(AssetTools.GET_CONFIGURATION, AssetTools.GET_MAINTENANCE_HISTORY));
        var writer = new io.devicetruth.adapter.persistence.AssetFactReferenceWriter(jdbc);
        var ids = new java.util.ArrayList<String>();
        Instant first = Instant.parse("2026-03-01T00:00:00Z");
        Instant second = Instant.parse("2026-04-01T00:00:00Z");
        for (Instant at : List.of(first, second, first, second)) {
            List<Reference> refs;
            if (maintenance) {
                var outcome = tools.maintenance(new AssetTools.MaintenanceRequest(asset, "CTRL-01", at), scoped);
                assertThat(outcome.success()).isTrue();
                refs = writer.forOutcome(scoped, outcome);
            } else {
                var outcome = tools.configuration(new AssetTools.ConfigurationRequest(asset, "CTRL-01", "moduleVersion", at), scoped);
                assertThat(outcome.success()).isTrue();
                refs = writer.forOutcome(scoped, outcome);
            }
            assertThat(refs).hasSize(1);
            ids.add(refs.getFirst().referenceId());
            var row = jdbc.queryForMap("SELECT asset_id, as_of FROM dt_evidence_reference WHERE id=?",
                    UUID.fromString(refs.getFirst().referenceId()));
            assertThat(row.get("asset_id")).isEqualTo(asset);
            assertThat(((java.sql.Timestamp) row.get("as_of")).toInstant()).isEqualTo(at);
        }
        assertThat(ids.get(0)).isEqualTo(ids.get(2)).isNotEqualTo(ids.get(1));
        assertThat(ids.get(1)).isEqualTo(ids.get(3));
        assertThat(new io.devicetruth.adapter.persistence.AgentRunRepository(jdbc).references(
                new io.devicetruth.application.agent.GeneralAgentService.ServerIdentity(OWNER, PROJECT), run)).hasSize(2);
    }

    // ---------- helpers ----------

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long countBySource(String table, String assetId, String sourceId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE asset_id=? AND source_id=?",
                Long.class, assetId, sourceId);
    }

    private static AssetConfigurationRecord configFact(String assetId, String sourceId, String attribute,
                                                       String value) {
        return new AssetConfigurationRecord(UUID.randomUUID().toString(), assetId, "CTRL-01", attribute, value,
                "CONFIGURATION", sourceId, "simulated:" + assetId + "/" + sourceId + "/" + attribute, "SIMULATED",
                "DOCUMENTED", 10, Instant.parse("2026-01-01T00:00:00Z"), null,
                Instant.parse("2026-01-02T00:00:00Z"), null);
    }

    private static AssetConfigurationRecord historyFact(String assetId, String sourceId, String value,
                                                        Instant validFrom, Instant validTo) {
        return new AssetConfigurationRecord(UUID.randomUUID().toString(), assetId, "CTRL-01", "moduleVersion",
                value, "CONFIGURATION", sourceId, "simulated:" + assetId + "/" + sourceId + "/" + validFrom,
                "SIMULATED", "DOCUMENTED", 10, validFrom, validTo, validFrom, null);
    }

    private static AssetMaintenanceRecord maintenanceFact(String assetId, String sourceId, Instant validFrom) {
        return new AssetMaintenanceRecord(UUID.randomUUID().toString(), assetId, "CTRL-01", "REPLACEMENT",
                "D-OLD", "D-NEW", "REPLACEMENT", sourceId, "simulated:" + assetId + "/" + sourceId + "/" + validFrom,
                "SIMULATED", "DOCUMENTED", 10, validFrom, null, validFrom, null);
    }

    private static AssetConfigurationRecord fieldConfirmed(String assetId, String componentId, String sourceId,
                                                           String value) {
        return new AssetConfigurationRecord(UUID.randomUUID().toString(), assetId, componentId, "moduleVersion",
                value, "FIELD_CONFIRMATION", sourceId, "simulated:" + assetId + "/" + sourceId, "SIMULATED",
                "FIELD_VERIFIED", 10, Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"));
    }

    private static AssetConfigurationRecord documented(String assetId, String componentId, String sourceId,
                                                       String value) {
        return new AssetConfigurationRecord(UUID.randomUUID().toString(), assetId, componentId, "moduleVersion",
                value, "FACTORY", sourceId, "simulated:" + assetId + "/" + sourceId, "SIMULATED", "DOCUMENTED", 10,
                Instant.parse("2026-01-01T00:00:00Z"), null, Instant.parse("2026-01-02T00:00:00Z"), null);
    }

    private static AssetConfigurationRecord conflict(String assetId, String sourceId, String value) {
        return new AssetConfigurationRecord(UUID.randomUUID().toString(), assetId, "CTRL-01", "moduleVersion", value,
                "CONFIGURATION", sourceId, "simulated:" + assetId + "/" + sourceId, "SIMULATED", "DOCUMENTED", 10,
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"), null);
    }

    private static AssetConfigurationRecord userStatement(String assetId, String sourceId, String value) {
        return new AssetConfigurationRecord(UUID.randomUUID().toString(), assetId, "CTRL-01", "moduleVersion", value,
                "USER_STATEMENT", sourceId, "user:" + sourceId, "SIMULATED", "DOCUMENTED", 0,
                Instant.parse("2026-09-01T00:00:00Z"), null, Instant.parse("2026-09-01T00:00:00Z"), null);
    }

    /** 测试用引用登记器：真实写入 dt_evidence_reference，便于验证 run 引用集合与幂等。 */
    static final class RecordingReferenceWriter implements AssetTools.FactReferenceWriter {
        private final io.devicetruth.adapter.persistence.AssetFactReferenceWriter delegate;

        RecordingReferenceWriter(JdbcTemplate jdbc) {
            this.delegate = new io.devicetruth.adapter.persistence.AssetFactReferenceWriter(jdbc);
        }

        @Override
        public boolean runBound(ToolContext context) {
            return delegate.runBound(context);
        }

        @Override
        public UUID register(ToolContext context, String assetId, Instant asOf, String factKey, String sourceId,
                             String sourceVersion, String title, String excerpt, String dataNature) {
            return delegate.register(context, assetId, asOf, factKey, sourceId, sourceVersion, title, excerpt,
                    dataNature);
        }

        @Override
        public List<Reference> forOutcome(ToolContext context, ConfigurationOutcome outcome) {
            return delegate.forOutcome(context, outcome);
        }

        @Override
        public List<Reference> forOutcome(ToolContext context, MaintenanceOutcome outcome) {
            return delegate.forOutcome(context, outcome);
        }
    }
}