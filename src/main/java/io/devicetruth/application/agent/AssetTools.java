package io.devicetruth.application.agent;

import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.ToolError;
import io.devicetruth.core.capability.Repositories.AssetComponent;
import io.devicetruth.core.capability.Repositories.AssetConfigurationRecord;
import io.devicetruth.core.capability.Repositories.AssetIdentity;
import io.devicetruth.core.capability.Repositories.AssetMaintenanceRecord;
import io.devicetruth.core.capability.Repositories.AssetRegistryRepository;
import io.devicetruth.core.capability.Repositories.QueryException;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AssetFacts.AssetOutcome;
import io.devicetruth.core.contract.AssetFacts.AssetView;
import io.devicetruth.core.contract.AssetFacts.ConfigurationEntry;
import io.devicetruth.core.contract.AssetFacts.ConfigurationOutcome;
import io.devicetruth.core.contract.AssetFacts.ConflictStatus;
import io.devicetruth.core.contract.AssetFacts.InstallationStatus;
import io.devicetruth.core.contract.AssetFacts.MaintenanceEntry;
import io.devicetruth.core.contract.AssetFacts.MaintenanceOutcome;
import io.devicetruth.core.contract.AssetFacts.VerificationStatus;
import io.devicetruth.core.contract.Checks;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 设备事实工具的服务端强类型接口（03-01）：get_asset / get_configuration / get_maintenance_history。
 * 输入是受限参数，输出只描述记录、来源、时点、核实状态与冲突，不输出诊断结论。
 * 用户口述“已更换”只作为 USER_STATEMENT 资料，不会被当作现场已核实。
 * 最新资料或 RAG 相似度不能替代当前装机事实：没有时点内有效的 FIELD_VERIFIED 记录时一律 UNCONFIRMED。
 * 每条成功结果都在当前 run 登记可审计的 CONFIGURATION 引用，原文读取仍按 run 归属校验。
 */
public final class AssetTools {
    public static final String GET_ASSET = "get_asset";
    public static final String GET_CONFIGURATION = "get_configuration";
    public static final String GET_MAINTENANCE_HISTORY = "get_maintenance_history";

    static final String USER_STATEMENT_ONLY =
            "用户陈述（如“已更换”）仅作为 USER_STATEMENT 资料记录，不属于现场已核实事实。";
    static final String RAG_NOT_FACT =
            "手册、安装说明或检索结果只说明资料这么写，不能证明该设备当前实际安装的型号。";

    private static final Set<String> ASSET_ARGUMENTS = Set.of("assetId");
    private static final Set<String> CONFIGURATION_ARGUMENTS = Set.of("assetId", "componentId", "attribute", "asOf");
    private static final Set<String> MAINTENANCE_ARGUMENTS = Set.of("assetId", "componentId", "asOf");
    private static final String ATTRIBUTE_PATTERN = "^[A-Za-z][A-Za-z0-9_.:-]{0,127}$";

    /** 强类型查询请求：asOf 缺省取工具上下文的业务时点，assetId 缺省使用服务器选定的资产。 */
    public record AssetRequest(String assetId) {}
    public record ConfigurationRequest(String assetId, String componentId, String attribute, Instant asOf) {}
    public record MaintenanceRequest(String assetId, String componentId, Instant asOf) {}

    private final AssetRegistryRepository registry;
    private final FactReferenceWriter references;

    public AssetTools(AssetRegistryRepository registry, FactReferenceWriter references) {
        Checks.required(registry, "registry");
        Checks.required(references, "references");
        this.registry = registry;
        this.references = references;
    }

    /** 只读引用登记端口：由持久层实现，工具本身不接触数据库事务细节。 */
    public interface FactReferenceWriter {
        /**
         * 为本次资产事实查询登记一条可审计引用，返回引用 ID。
         * 引用身份 = factKey（事实记录 ID）：同来源的多条事实是独立引用；
         * 同 run 重复查询同一事实复用同一条引用。引用携带实际查询的资产、时点与事实摘要。
         */
        UUID register(ToolContext context, String assetId, Instant asOf, String factKey, String sourceId,
                      String sourceVersion, String title, String excerpt, String dataNature);

        /** 上下文是否对应库中真实 run；仅真实 run 才需要（也才允许）登记引用。 */
        boolean runBound(ToolContext context);

        /** 本次查询已登记的引用视图，供 Agent 答案引用使用；查询失败时返回空列表。 */
        List<Reference> forOutcome(ToolContext context, ConfigurationOutcome outcome);

        List<Reference> forOutcome(ToolContext context, MaintenanceOutcome outcome);
    }

    public AssetOutcome asset(AssetRequest request, ToolContext context) {
        String requestId = UUID.randomUUID().toString();
        try {
            requireTool(context, GET_ASSET);
            String assetId = assetId(request.assetId(), context);
            AssetIdentity identity = registry.asset(context.ownerId(), context.projectId(), assetId);
            List<String> componentIds = registry.components(context.ownerId(), context.projectId(), assetId)
                    .stream().map(AssetComponent::componentId).toList();
            List<AssetConfigurationRecord> configurations = registry.configurations(context.ownerId(),
                    context.projectId(), assetId, null, null, context.asOf());
            String dataNature = conservativeNature(configurations.isEmpty() ? List.of()
                    : configurations.stream().map(AssetConfigurationRecord::dataNature).toList());
            return new AssetOutcome(true, requestId, GET_ASSET,
                    new AssetView(identity.assetId(), identity.ownerId(), identity.projectId(), componentIds,
                            context.asOf(), dataNature),
                    null, null, false);
        } catch (RuntimeException failure) {
            return new AssetOutcome(false, requestId, GET_ASSET, null, code(failure), message(failure),
                    retryable(failure));
        }
    }

    public ConfigurationOutcome configuration(ConfigurationRequest request, ToolContext context) {
        String requestId = UUID.randomUUID().toString();
        try {
            requireTool(context, GET_CONFIGURATION);
            String assetId = assetId(request.assetId(), context);
            // 先确认资产在授权范围内登记：查不到与越权统一为 ASSET_NOT_FOUND，不区分、不放行
            registry.asset(context.ownerId(), context.projectId(), assetId);
            Instant asOf = asOf(request.asOf(), context);
            var records = registry.configurations(context.ownerId(), context.projectId(), assetId,
                    request.componentId(), request.attribute(), asOf);
            List<ConfigurationEntry> entries = records.stream().map(AssetTools::entry).toList();
            ConflictStatus conflict = conflict(entries);
            InstallationStatus installation = installation(entries);
            List<String> requiredChecks = installation == InstallationStatus.FIELD_CONFIRMED ? List.of()
                    : entries.isEmpty() ? requiredChecks(request.componentId(), request.attribute())
                    : requiredChecks(entries);
            String dataNature = conservativeNature(records.stream().map(AssetConfigurationRecord::dataNature).toList());
            ConfigurationOutcome outcome = new ConfigurationOutcome(true, requestId, GET_CONFIGURATION, assetId,
                    request.componentId(), request.attribute(), asOf, entries, installation, conflict,
                    requiredChecks, limitations(), dataNature, null, null, false);
            registerReferences(context, outcome);
            return outcome;
        } catch (RuntimeException failure) {
            return new ConfigurationOutcome(false, requestId, GET_CONFIGURATION, safeId(request.assetId()),
                    safeId(request.componentId()), safeAttribute(request.attribute()), context == null || context.asOf() == null
                    ? Instant.EPOCH : context.asOf(), List.of(), InstallationStatus.UNCONFIRMED, ConflictStatus.NONE,
                    List.of(), List.of(), "SIMULATED", code(failure), message(failure), retryable(failure));
        }
    }

    public MaintenanceOutcome maintenance(MaintenanceRequest request, ToolContext context) {
        String requestId = UUID.randomUUID().toString();
        try {
            requireTool(context, GET_MAINTENANCE_HISTORY);

            String assetId = assetId(request.assetId(), context);
            // 与 configuration 同一口径：不存在/跨归属先在这一步拒绝
            registry.asset(context.ownerId(), context.projectId(), assetId);
            Instant asOf = asOf(request.asOf(), context);
            var records = registry.maintenanceHistory(context.ownerId(), context.projectId(), assetId,
                    request.componentId(), asOf);
            List<MaintenanceEntry> entries = records.stream().map(AssetTools::entry).toList();
            String dataNature = conservativeNature(records.stream().map(AssetMaintenanceRecord::dataNature).toList());
            MaintenanceOutcome outcome = new MaintenanceOutcome(true, requestId, GET_MAINTENANCE_HISTORY, assetId,
                    request.componentId(), asOf, entries, limitations(), dataNature, null, null, false);
            registerReferences(context, outcome);
            return outcome;
        } catch (RuntimeException failure) {
            return new MaintenanceOutcome(false, requestId, GET_MAINTENANCE_HISTORY, safeId(request.assetId()),
                    safeId(request.componentId()), context == null || context.asOf() == null
                    ? Instant.EPOCH : context.asOf(), List.of(), List.of(), "SIMULATED",
                    code(failure), message(failure), retryable(failure));
        }
    }

    /** 注册进产品 Agent 网关：注册即服务器授权，模型只能看到并调用这里公开的工具。 */
    public void registerInto(CapabilityGateway gateway) {
        Checks.required(gateway, "gateway");
        for (ToolSpec spec : specs()) {
            if (GET_ASSET.equals(spec.name())) gateway.registerTool(spec, this::assetOutcome);
            else if (GET_CONFIGURATION.equals(spec.name())) gateway.registerTool(spec, this::configurationOutcome);
            else if (GET_MAINTENANCE_HISTORY.equals(spec.name())) gateway.registerTool(spec, this::maintenanceOutcome);
        }
    }

    public List<ToolSpec> specs() {
        return List.of(
                new ToolSpec(GET_ASSET, "查询已登记设备身份与组件清单；只返回授权范围内的登记信息，不返回当前配置结论",
                        "asset", true,
                        schema(Map.of("assetId", Map.of("type", "string", "pattern", ID_PATTERN)),
                                List.of()),
                        "AssetOutcome{success,requestId,toolName,asset{assetId,ownerId,projectId,componentIds,"
                                + "asOf,dataNature},errorCode,message,retryable}"),
                new ToolSpec(GET_CONFIGURATION,
                        "按设备与业务时点查询配置记录：返回来源、有效期、记录/观测时间与核实状态；"
                                + "没有时点内有效的现场核实记录时明确 UNCONFIRMED，不把最新资料当当前装机",
                        "asset", true,
                        schema(Map.of(
                                "assetId", Map.of("type", "string", "pattern", ID_PATTERN),
                                "componentId", Map.of("type", "string", "pattern", ID_PATTERN),
                                "attribute", Map.of("type", "string", "pattern", ATTRIBUTE_PATTERN),
                                "asOf", Map.of("type", "string", "format", "date-time")),
                                List.of("attribute")),
                        "ConfigurationOutcome{success,requestId,toolName,assetId,componentId,attribute,asOf,"
                                + "entries[{recordId,componentId,attribute,value,sourceType,sourceId,sourceRef,"
                                + "dataNature,verificationStatus,authority,validFrom,validTo,recordedAt,observedAt}],"
                                + "installationStatus,conflict,requiredChecks,limitations,dataNature,error}"),
                new ToolSpec(GET_MAINTENANCE_HISTORY,
                        "查询设备维修/更换历史：返回 before/after 取值、来源与核实状态；不返回诊断结论",
                        "asset", true,
                        schema(Map.of(
                                "assetId", Map.of("type", "string", "pattern", ID_PATTERN),
                                "componentId", Map.of("type", "string", "pattern", ID_PATTERN),
                                "asOf", Map.of("type", "string", "format", "date-time")),
                                List.of()),
                        "MaintenanceOutcome{success,requestId,toolName,assetId,componentId,asOf,"
                                + "entries[{recordId,componentId,operation,beforeValue,afterValue,sourceType,"
                                + "sourceId,sourceRef,dataNature,verificationStatus,authority,validFrom,validTo,"
                                + "recordedAt,observedAt}],limitations,dataNature,error}"));
    }

    private ToolOutcome assetOutcome(Map<String, Object> arguments, ToolContext context) {
        AssetOutcome outcome = asset(new AssetRequest(optionalId(arguments, "assetId")), context);
        if (!outcome.success()) return ToolOutcome.failed(outcome.requestId(), GET_ASSET, error(outcome));
        // 模型可见摘要：身份与组件清单（白名单字段），不输出配置结论
        return ToolOutcome.ok(outcome.requestId(), GET_ASSET, outcome, List.of(), outcome.asset().dataNature(),
                Map.of("assetId", outcome.asset().assetId(),
                        "asOf", outcome.asset().asOf().toString(),
                        "componentCount", String.valueOf(outcome.asset().componentIds().size()),
                        "components", String.join(",", outcome.asset().componentIds())));
    }

    private ToolOutcome configurationOutcome(Map<String, Object> arguments, ToolContext context) {
        ConfigurationOutcome outcome;
        try {
            checkArguments(arguments, CONFIGURATION_ARGUMENTS);
            // 模型必须指明要查哪个字段，不允许一次拉全量配置记录（服务层组合视图不受此限）
            if (optionalId(arguments, "attribute") == null)
                throw new QueryException("INVALID_ARGUMENTS", "attribute is required");
            outcome = configuration(new ConfigurationRequest(optionalId(arguments, "assetId"),
                    optionalId(arguments, "componentId"), optionalId(arguments, "attribute"),
                    optionalInstant(arguments, "asOf")), context);
        } catch (RuntimeException failure) {
            // 参数解析失败直接拒绝，绝不退化为默认查询：替代查询会用默认资产/时点返回与原请求无关的事实
            return ToolOutcome.failed(UUID.randomUUID().toString(), GET_CONFIGURATION,
                    new ToolError(code(failure), message(failure), retryable(failure)));
        }
        if (!outcome.success()) return ToolOutcome.failed(outcome.requestId(), GET_CONFIGURATION, error(outcome));
        // 模型可见摘要：确认/冲突状态、条目数、待核验要求与限制说明；未核实信息保持 UNCONFIRMED 语义
        Map<String, String> visible = new LinkedHashMap<>(Map.of(
                "assetId", outcome.assetId(), "asOf", outcome.asOf().toString(),
                "installationStatus", outcome.installationStatus().name(), "conflict", outcome.conflict().name(),
                "entries", String.valueOf(outcome.entries().size()),
                "requiredChecks", String.join("；", outcome.requiredChecks()),
                "limitations", String.join("；", outcome.limitations()),
                "dataNature", outcome.dataNature()));
        return ToolOutcome.ok(outcome.requestId(), GET_CONFIGURATION, outcome,
                references.forOutcome(context, outcome), outcome.dataNature(), visible);
    }

    private ToolOutcome maintenanceOutcome(Map<String, Object> arguments, ToolContext context) {
        MaintenanceOutcome outcome;
        try {
            checkArguments(arguments, MAINTENANCE_ARGUMENTS);
            outcome = maintenance(new MaintenanceRequest(optionalId(arguments, "assetId"),
                    optionalId(arguments, "componentId"), optionalInstant(arguments, "asOf")), context);
        } catch (RuntimeException failure) {
            // 与 configurationOutcome 同一口径：解析失败即拒绝，不执行替代查询
            return ToolOutcome.failed(UUID.randomUUID().toString(), GET_MAINTENANCE_HISTORY,
                    new ToolError(code(failure), message(failure), retryable(failure)));
        }
        if (!outcome.success())
            return ToolOutcome.failed(outcome.requestId(), GET_MAINTENANCE_HISTORY, error(outcome));
        // 模型可见摘要：条目数与限制说明；条目事实经引用摘录传递，维修史不等同当前确认
        return ToolOutcome.ok(outcome.requestId(), GET_MAINTENANCE_HISTORY, outcome,
                references.forOutcome(context, outcome), outcome.dataNature(), Map.of(
                        "assetId", outcome.assetId(), "asOf", outcome.asOf().toString(),
                        "entries", String.valueOf(outcome.entries().size()),
                        "limitations", String.join("；", outcome.limitations()),
                        "dataNature", outcome.dataNature()));
    }

    /**
     * 为本次查询登记可审计引用。登记只在上下文对应真实 run 时才有意义：
     * API 只读查询没有 run（http 直查时 runId 是派生占位），此时跳过登记，
     * 不能因为“无法挂到某个 run”就把已经读到的记录整体变成失败。
     */
    private void registerReferences(ToolContext context, ConfigurationOutcome outcome) {
        if (!references.runBound(context)) return;
        for (ConfigurationEntry entry : outcome.entries()) {
            // 引用身份是事实记录本身（recordId）；资产与时点取实际查询范围（来自 outcome）
            references.register(context, outcome.assetId(), outcome.asOf(), entry.recordId(), entry.sourceId(),
                    entry.recordedAt().toString(),
                    "配置记录 " + entry.componentId() + "." + entry.attribute(),
                    summary(entry), outcome.dataNature());
        }
    }

    private void registerReferences(ToolContext context, MaintenanceOutcome outcome) {
        if (!references.runBound(context)) return;
        for (MaintenanceEntry entry : outcome.entries()) {
            references.register(context, outcome.assetId(), outcome.asOf(), entry.recordId(), entry.sourceId(),
                    entry.recordedAt().toString(),
                    "维修记录 " + entry.componentId() + "." + entry.operation(),
                    summary(entry), outcome.dataNature());
        }
    }

    /** 引用摘要只含来源、时点与核实状态，不含任何推理或诊断措辞。 */
    static String summary(ConfigurationEntry entry) {
        return "attribute=" + entry.attribute() + ";value=" + entry.value() + ";source=" + entry.sourceType()
                + ";verification=" + entry.verificationStatus() + ";validFrom=" + entry.validFrom()
                + (entry.validTo() == null ? "" : ";validTo=" + entry.validTo());
    }

    static String summary(MaintenanceEntry entry) {
        return "operation=" + entry.operation() + ";before=" + entry.beforeValue() + ";after=" + entry.afterValue()
                + ";source=" + entry.sourceType() + ";verification=" + entry.verificationStatus()
                + ";validFrom=" + entry.validFrom();
    }

    /**
     * 冲突判定按 组件+属性 分组。组内存在现场核实记录时，只以核实记录的最高权威取值判定
     * （资料记载被现场核实取代，不与核实行并列成冲突）；无核实记录时才看资料记载的最高权威取值。
     */
    static ConflictStatus conflict(List<ConfigurationEntry> entries) {
        return groups(entries).stream().anyMatch(group -> bestValues(decisive(group)).size() > 1)
                ? ConflictStatus.CONFLICT : ConflictStatus.NONE;
    }

    /**
     * 当前装机状态按 组件+属性 分组判定：只有当每个分组都存在时点内有效的 FIELD_VERIFIED
     * 记录、且核实记录的最高权威取值不冲突时，整体才是 FIELD_CONFIRMED。
     * 局部确认不得扩大为整体确认；未核实或冲突的分组保留各自的待核验要求。
     */
    static InstallationStatus installation(List<ConfigurationEntry> entries) {
        if (entries.isEmpty()) return InstallationStatus.UNCONFIRMED;
        return groups(entries).stream().allMatch(AssetTools::groupConfirmed)
                ? InstallationStatus.FIELD_CONFIRMED : InstallationStatus.UNCONFIRMED;
    }

    private static boolean groupConfirmed(List<ConfigurationEntry> group) {
        List<ConfigurationEntry> verified = group.stream().filter(ConfigurationEntry::fieldVerified).toList();
        return !verified.isEmpty() && bestValues(verified).size() == 1;
    }

    /** 判定冲突时的有效集合：有核实记录只看核实记录，否则看全部资料记载。 */
    private static List<ConfigurationEntry> decisive(List<ConfigurationEntry> group) {
        List<ConfigurationEntry> verified = group.stream().filter(ConfigurationEntry::fieldVerified).toList();
        return verified.isEmpty() ? group : verified;
    }

    /** 组内最高权威记录的取值集合：分叉即冲突，单一取值才可能构成确认。 */
    private static List<String> bestValues(List<ConfigurationEntry> group) {
        int bestAuthority = group.stream().mapToInt(ConfigurationEntry::authority).max().orElse(0);
        return group.stream().filter(entry -> entry.authority() == bestAuthority)
                .map(ConfigurationEntry::value).distinct().toList();
    }

    /** 按 组件+属性 分组；查询跨组件/属性时确认范围逐组判定。 */
    static List<List<ConfigurationEntry>> groups(List<ConfigurationEntry> entries) {
        var map = new LinkedHashMap<String, List<ConfigurationEntry>>();
        for (ConfigurationEntry entry : entries)
            map.computeIfAbsent(entry.componentId() + "." + entry.attribute(), key -> new ArrayList<>()).add(entry);
        return List.copyOf(map.values());
    }

    /** 每个未确认分组的待核验要求；全部确认时为空。 */
    static List<String> requiredChecks(List<ConfigurationEntry> entries) {
        var checks = new ArrayList<String>();
        for (List<ConfigurationEntry> group : groups(entries)) {
            if (groupConfirmed(group)) continue;
            ConfigurationEntry first = group.getFirst();
            checks.add("需要对该设备 " + first.componentId() + "." + first.attribute()
                    + " 做现场核验（拍照/铭牌/读数）后才能确认当前实际配置");
        }
        return checks.isEmpty() ? List.of() : checks;
    }

    private static List<String> requiredChecks(String componentId, String attribute) {
        String target = (componentId == null ? "相关部件" : componentId) + "." + attribute;
        return List.of("需要对该设备 " + target + " 做现场核验（拍照/铭牌/读数）后才能确认当前实际配置");
    }

    static List<String> limitations() {
        return List.of(RAG_NOT_FACT, USER_STATEMENT_ONLY, "维修/更换记录说明曾经发生过更换，不等同于当前已现场确认。");
    }

    static ConfigurationEntry entry(AssetConfigurationRecord record) {
        return new ConfigurationEntry(record.recordId(), record.componentId(), record.attribute(), record.value(),
                record.sourceType(), record.sourceId(), record.sourceRef(), record.dataNature(),
                verification(record.verificationStatus()), record.authority(), record.validFrom(),
                record.validTo(), record.recordedAt(), record.observedAt());
    }

    static MaintenanceEntry entry(AssetMaintenanceRecord record) {
        return new MaintenanceEntry(record.recordId(), record.componentId(), record.operation(),
                record.beforeValue(), record.afterValue(), record.sourceType(), record.sourceId(),
                record.sourceRef(), record.dataNature(), verification(record.verificationStatus()),
                record.authority(), record.validFrom(), record.validTo(), record.recordedAt(),
                record.observedAt());
    }

    private static VerificationStatus verification(String value) {
        return VerificationStatus.valueOf(value);
    }

    /** 任一记录为 SIMULATED 时整体保持 SIMULATED，避免把模拟数据冒充为已授权设备事实。 */
    static String conservativeNature(List<String> natures) {
        if (natures.isEmpty()) return "SIMULATED";
        return natures.stream().allMatch("AUTHORIZED"::equals) ? "AUTHORIZED" : "SIMULATED";
    }

    /**
     * 请求未带 assetId 时使用服务器选定的资产；两者都没有则拒绝，绝不猜测一台设备。
     * 请求带了 assetId 但它不在上下文选中范围内时，由仓储的归属校验决定是否存在、是否越权。
     */
    private String assetId(String requested, ToolContext context) {
        String selected = context.selectedAssetId();
        if (requested == null || requested.isBlank()) {
            if (selected == null) throw new QueryException("INVALID_ARGUMENTS", "assetId is required");
            return selected;
        }
        Checks.id(requested, "assetId");
        return requested;
    }

    private static Instant asOf(Instant requested, ToolContext context) {
        Instant asOf = requested == null ? context.asOf() : requested;
        if (asOf == null) throw new QueryException("INVALID_ARGUMENTS", "asOf is required");
        return asOf;
    }

    private static void requireTool(ToolContext context, String tool) {
        if (context == null) throw new QueryException("INVALID_ARGUMENTS", "context is required");
        if (!context.allows(tool)) throw new QueryException("PERMISSION_DENIED", "Tool outside server-authorized scope");
    }

    private static void checkArguments(Map<String, Object> arguments, Set<String> allowed) {
        Map<String, Object> values = arguments == null ? Map.of() : arguments;
        for (String key : values.keySet())
            if (!allowed.contains(key)) throw new QueryException("INVALID_ARGUMENTS", "Unknown parameter");
    }

    private static String optionalId(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) return null;
        if (!(value instanceof String text) || text.isBlank())
            throw new QueryException("INVALID_ARGUMENTS", key + " must be a non-blank string");
        return Checks.id(text, key);
    }

    private static Instant optionalInstant(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) return null;
        if (!(value instanceof String text))
            throw new QueryException("INVALID_ARGUMENTS", key + " must be an ISO-8601 instant");
        try {
            return Instant.parse(text);
        } catch (RuntimeException e) {
            throw new QueryException("INVALID_ARGUMENTS", key + " must be an ISO-8601 instant");
        }
    }

    private static String safeId(String value) {
        try {
            return value == null ? null : Checks.id(value, "assetId");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String safeAttribute(String value) {
        try {
            return value == null ? null : Checks.id(value, "attribute");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String code(RuntimeException failure) {
        if (failure instanceof QueryException query) return query.code();
        if (failure instanceof IllegalArgumentException) return "INVALID_ARGUMENTS";
        return "TOOL_FAILURE";
    }

    private static String message(RuntimeException failure) {
        String code = code(failure);
        return switch (code) {
            case "ASSET_NOT_FOUND" -> "该设备未在授权范围内登记";
            case "PERMISSION_DENIED" -> "无权访问该设备或工具";
            case "INVALID_ARGUMENTS" -> "工具参数不合法";
            default -> "读取设备事实失败";
        };
    }

    private static boolean retryable(RuntimeException failure) { return "TOOL_FAILURE".equals(code(failure)); }

    private static ToolError error(ConfigurationOutcome outcome) {
        return new ToolError(outcome.errorCode(), outcome.message(), outcome.retryable());
    }

    private static ToolError error(MaintenanceOutcome outcome) {
        return new ToolError(outcome.errorCode(), outcome.message(), outcome.retryable());
    }

    private static ToolError error(AssetOutcome outcome) {
        return new ToolError(outcome.errorCode(), outcome.message(), outcome.retryable());
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static final String ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$";
}
