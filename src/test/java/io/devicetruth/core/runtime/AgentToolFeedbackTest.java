package io.devicetruth.core.runtime;

import io.devicetruth.adapter.StructuredAgentReasoner;
import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.ToolError;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.GeneralReasoner;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agent Runtime 第一轮修复的离线回归：以确定性替身捕获每轮 GeneralReasoner.Context，
 * 断言“下一轮模型实际收到的输入”，覆盖工具结果可见性、原文扩展、重复调用与上下文边界。
 */
class AgentToolFeedbackTest {

    /** 按脚本顺序给出动作、并捕获每次推理上下文的替身推理器。 */
    private static final class ScriptedStub implements GeneralReasoner {
        private final Deque<ActionProposal> script;
        private final List<Context> seen = new ArrayList<>();

        ScriptedStub(ActionProposal... actions) { this.script = new ArrayDeque<>(List.of(actions)); }

        @Override public ActionProposal propose(Context context) {
            seen.add(context);
            var next = script.pollFirst();
            if (next == null) throw new IllegalStateException("script exhausted");
            return next;
        }

        @Override public String name() { return "stub"; }

        Context contextAt(int round) { return seen.get(round); }

        int rounds() { return seen.size(); }
    }

    private static ToolSpec spec(String name) {
        // 白名单参数集覆盖本测试用到的全部键；网关对未列出的键仍会拒绝
        Map<String, Object> properties = Map.of(
                "attribute", Map.of("type", "string"), "componentId", Map.of("type", "string"),
                "query", Map.of("type", "string"), "documentId", Map.of("type", "string"),
                "chunkId", Map.of("type", "string"), "n", Map.of("type", "integer"),
                "second", Map.of("type", "boolean"), "name", Map.of("type", "string"));
        return new ToolSpec(name, "stub tool " + name, "test", true,
                Map.of("type", "object", "additionalProperties", false, "properties", properties,
                        "required", List.of()), "Result{}");
    }

    private static ToolContext toolContext(Set<String> allowed) {
        return new ToolContext("owner-a", "project-a", UUID.randomUUID(), UUID.randomUUID(),
                null, Instant.parse("2026-05-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"), allowed);
    }

    private static AgentTaskInput input(int maxSteps) {
        return new AgentTaskInput("owner-a", "project-a", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "检查设备充电异常", List.of(new AgentTaskInput.Message("USER", "检查设备充电异常")),
                null, Instant.parse("2026-05-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"),
                0, 0, maxSteps);
    }

    private static ActionProposal.Answer plainAnswer() {
        return new ActionProposal.Answer("KNOWLEDGE", "一般性说明，无设备结论。",
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** 计数并固定返回值的工具注册。 */
    private record StubTool(AtomicInteger executions, BiFunction<Map<String, Object>, ToolContext, ToolOutcome> handler) { }

    private static StubTool register(CapabilityGateway gateway, String name,
                                     BiFunction<Map<String, Object>, ToolContext, ToolOutcome> handler) {
        var executions = new AtomicInteger();
        gateway.registerTool(spec(name), (arguments, context) -> {
            executions.incrementAndGet();
            return handler.apply(arguments, context);
        });
        return new StubTool(executions, handler);
    }

    private static ToolOutcome ok(String tool, Map<String, String> modelContext, Reference... references) {
        return ToolOutcome.ok(UUID.randomUUID().toString(), tool, Map.of(), List.of(references),
                "SIMULATED", modelContext);
    }

    private static Reference knowledgeReference(String id, String excerpt) {
        return new Reference(id, "KNOWLEDGE_DOCUMENT", "doc-1", "2026.1", "chunk-1",
                "手册", 1, "第2节", excerpt, "SIMULATED");
    }

    // T01：资产查询的关键字段出现在下一轮模型输入
    @Test void assetQueryFieldsReachNextModelContext() {
        var gateway = new CapabilityGateway();
        register(gateway, "get_asset", (arguments, context) -> ok("get_asset",
                Map.of("assetId", "CAR-B", "componentCount", "2", "components", "motor,inverter")));
        var reasoner = new ScriptedStub(new ActionProposal.CallTool("get_asset", Map.of(), "查询资产"),
                plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        var results = reasoner.contextAt(1).toolResults();
        assertThat(results).hasSize(1);
        var status = results.getFirst().status();
        assertThat(status)
                .containsEntry("success", "true")
                .containsEntry("assetId", "CAR-B")
                .containsEntry("componentCount", "2")
                .containsEntry("components", "motor,inverter")
                .containsEntry("dataNature", "SIMULATED");
    }

    // T02：配置状态、冲突、待核验要求与引用正确传递
    @Test void configurationStatusConflictAndReferencesReachModel() {
        var gateway = new CapabilityGateway();
        var reference = new Reference("cfg-1", "CONFIGURATION", "CAR-B", "v1", "record-1",
                "配置记录", null, "chargingController.model", "attribute=model;value=V2;verification=DOCUMENTED",
                "SIMULATED");
        register(gateway, "get_configuration", (arguments, context) -> ok("get_configuration",
                Map.of("installationStatus", "UNCONFIRMED", "conflict", "NONE",
                        "requiredChecks", "需要对 chargingController.model 做现场核验",
                        "entries", "1"), reference));
        var reasoner = new ScriptedStub(new ActionProposal.CallTool("get_configuration",
                Map.of("attribute", "model"), "查询配置"), plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        var context = reasoner.contextAt(1);
        assertThat(context.references()).extracting(Reference::referenceId).contains("cfg-1");
        var status = context.toolResults().getFirst().status();
        assertThat(status)
                .containsEntry("installationStatus", "UNCONFIRMED")
                .containsEntry("conflict", "NONE")
                .containsEntry("requiredChecks", "需要对 chargingController.model 做现场核验")
                .containsEntry("referenceIds", "cfg-1")
                .containsEntry("success", "true");
    }

    // T03：维修历史关键事实可见，且核实状态保持“资料记载”而非确诊
    @Test void maintenanceHistoryFactsStayDocumentedNotConfirmed() {
        var gateway = new CapabilityGateway();
        var reference = new Reference("mnt-1", "CONFIGURATION", "CAR-B", "v1", "record-9",
                "维修记录", null, "chargingController.replace",
                "operation=replace;after=V2;verification=DOCUMENTED;validFrom=2025-01-01", "SIMULATED");
        register(gateway, "get_maintenance_history", (arguments, context) -> ok("get_maintenance_history",
                Map.of("entries", "1", "limitations", "维修记录不等同当前已现场确认"), reference));
        var reasoner = new ScriptedStub(new ActionProposal.CallTool("get_maintenance_history",
                Map.of(), "查询维修史"), plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        var context = reasoner.contextAt(1);
        assertThat(context.toolResults().getFirst().status()).containsEntry("entries", "1");
        var stored = context.references().stream().filter(item -> item.referenceId().equals("mnt-1")).findFirst().orElseThrow();
        assertThat(stored.excerpt()).contains("verification=DOCUMENTED").doesNotContain("FIELD_VERIFIED");
    }

    // T04：知识检索的命中摘要与引用 ID 可达模型
    @Test void knowledgeSearchHitsAndReferenceIdsReachModel() {
        var gateway = new CapabilityGateway();
        register(gateway, "search_knowledge", (arguments, context) -> ok("search_knowledge",
                Map.of("hits", "1", "empty", "false"), knowledgeReference("ref-1", "命中摘要：检查充电控制器连接。")));
        var reasoner = new ScriptedStub(new ActionProposal.CallTool("search_knowledge",
                Map.of("query", "充电中断"), "检索资料"), plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        var context = reasoner.contextAt(1);
        assertThat(context.references()).extracting(Reference::referenceId).contains("ref-1");
        assertThat(context.toolResults().getFirst().status())
                .containsEntry("hits", "1")
                .containsEntry("empty", "false")
                .containsEntry("referenceIds", "ref-1");
    }

    // T05：原文扩展后模型看到更长原文，引用身份（ID/文档/版本/chunk）不变
    @Test void documentExcerptEnrichesReferenceKeepingIdentity() {
        var gateway = new CapabilityGateway();
        // 原文长度控制在单值上限内，保证 status 里的 excerpt 原样可见；引用摘录通道另可承载至 2000 字符
        String longText = "扩展原文：" + "详".repeat(900);
        var searchRef = knowledgeReference("ref-1", "短摘要。");
        var excerptRef = knowledgeReference("ref-1", longText);
        register(gateway, "search_knowledge", (arguments, context) -> ok("search_knowledge",
                Map.of("hits", "1", "empty", "false"), searchRef));
        register(gateway, "get_document_excerpt", (arguments, context) -> ok("get_document_excerpt",
                Map.of("documentId", "doc-1", "chunkId", "chunk-1", "title", "手册", "excerpt", longText), excerptRef));
        var reasoner = new ScriptedStub(
                new ActionProposal.CallTool("search_knowledge", Map.of("query", "充电"), "检索"),
                new ActionProposal.CallTool("get_document_excerpt", Map.of("documentId", "doc-1", "chunkId", "chunk-1"), "读原文"),
                plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        var context = reasoner.contextAt(2);
        var enriched = context.references().stream().filter(item -> item.referenceId().equals("ref-1")).findFirst().orElseThrow();
        assertThat(enriched.excerpt()).isEqualTo(longText);
        assertThat(enriched.sourceId()).isEqualTo("doc-1");
        assertThat(enriched.sourceVersion()).isEqualTo("2026.1");
        assertThat(enriched.chunkId()).isEqualTo("chunk-1");
        var excerptStatus = context.toolResults().stream()
                .filter(result -> result.toolName().equals("get_document_excerpt")).findFirst().orElseThrow().status();
        assertThat(excerptStatus.get("excerpt")).isEqualTo(longText);
    }

    // T06：工具成功但无数据时，模型获得明确的空状态而不是静默成功
    @Test void emptyToolResultReportsExplicitStatus() {
        var gateway = new CapabilityGateway();
        register(gateway, "search_knowledge", (arguments, context) -> ok("search_knowledge",
                Map.of("hits", "0", "empty", "true")));
        var reasoner = new ScriptedStub(new ActionProposal.CallTool("search_knowledge",
                Map.of("query", "不存在的内容"), "检索"), plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        assertThat(reasoner.contextAt(1).toolResults().getFirst().status())
                .containsEntry("success", "true")
                .containsEntry("empty", "true")
                .containsEntry("referenceCount", "0");
    }

    // T07：工具失败只给安全错误码，不产生任何证据
    @Test void toolFailureExposesOnlySafeErrorCode() {
        var gateway = new CapabilityGateway();
        register(gateway, "get_asset", (arguments, context) -> ToolOutcome.failed(
                UUID.randomUUID().toString(), "get_asset",
                new ToolError("ASSET_NOT_FOUND", "denied", false)));
        var reasoner = new ScriptedStub(new ActionProposal.CallTool("get_asset", Map.of(), "查询资产"),
                plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        var context = reasoner.contextAt(1);
        assertThat(context.feedback()).isEqualTo("工具调用失败：ASSET_NOT_FOUND");
        assertThat(context.references()).isEmpty();
        assertThat(context.toolResults().getFirst().status())
                .containsEntry("success", "false")
                .containsEntry("errorCode", "ASSET_NOT_FOUND")
                .doesNotContainKey("assetId");
    }

    // T08：完全相同（含键顺序不同）的成功调用被拦截，且拦截不算成功取证
    @Test void identicalSuccessfulCallIsIntercepted() {
        var gateway = new CapabilityGateway();
        var stub = register(gateway, "get_configuration",
                (arguments, context) -> ok("get_configuration", Map.of("conflict", "NONE")));
        var reasoner = new ScriptedStub(
                new ActionProposal.CallTool("get_configuration", Map.of("attribute", "model", "componentId", "motor"), "查询"),
                new ActionProposal.CallTool("get_configuration", Map.of("componentId", "motor", "attribute", "model"), "重复查询"),
                plainAnswer());

        var outcome = new TaskOrchestrator(gateway).runAgent(input(12), gateway,
                toolContext(gateway.toolNames()), List.of(), reasoner);

        assertThat(stub.executions()).hasValue(1);
        assertThat(reasoner.contextAt(2).feedback()).contains("结果仍可使用");
        assertThat(reasoner.contextAt(2).toolResults()).hasSize(1);
        assertThat(outcome.events()).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("TOOL_RESULT");
            assertThat(event.data()).containsEntry("success", false).containsEntry("errorCode", "DUPLICATE_CALL");
        });
    }

    // T09a：相同工具、不同参数正常执行
    @Test void sameToolWithDifferentArgumentsExecutesTwice() {
        var gateway = new CapabilityGateway();
        var stub = register(gateway, "get_configuration",
                (arguments, context) -> ok("get_configuration", Map.of("attribute", String.valueOf(arguments.get("attribute")))));
        var reasoner = new ScriptedStub(
                new ActionProposal.CallTool("get_configuration", Map.of("attribute", "model"), "查型号"),
                new ActionProposal.CallTool("get_configuration", Map.of("attribute", "swVersion"), "查软件版本"),
                plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        assertThat(stub.executions()).hasValue(2);
        assertThat(reasoner.contextAt(2).toolResults()).hasSize(2);
    }

    // T09b：失败后以相同参数重试不被拦截
    @Test void failedCallCanBeRetriedWithSameArguments() {
        var gateway = new CapabilityGateway();
        var executions = new AtomicInteger();
        gateway.registerTool(spec("get_asset"), (arguments, context) -> {
            executions.incrementAndGet();
            if (executions.get() == 1)
                return ToolOutcome.failed(UUID.randomUUID().toString(), "get_asset",
                        new ToolError("TOOL_FAILURE", "transient", true));
            return ok("get_asset", Map.of("assetId", "CAR-B"));
        });
        var reasoner = new ScriptedStub(
                new ActionProposal.CallTool("get_asset", Map.of(), "第一次"),
                new ActionProposal.CallTool("get_asset", Map.of(), "重试"),
                plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        assertThat(executions).hasValue(2);
        assertThat(reasoner.contextAt(2).toolResults().getLast().status())
                .containsEntry("success", "true");
    }

    // T10：超长结果被安全截断，多次调用后上下文累计长度有界
    @Test void oversizedToolResultsAreBounded() {
        var gateway = new CapabilityGateway();
        register(gateway, "big_tool", (arguments, context) -> ok("big_tool",
                Map.of("blob", "x".repeat(5000), "conflict", "NONE", "installationStatus", "UNCONFIRMED")));
        var script = new ArrayList<ActionProposal>();
        for (int index = 0; index < 12; index++)
            script.add(new ActionProposal.CallTool("big_tool", Map.of("n", index), "第 " + index + " 次"));
        script.add(plainAnswer());
        var reasoner = new ScriptedStub(script.toArray(ActionProposal[]::new));

        new TaskOrchestrator(gateway).runAgent(input(20), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);

        var results = reasoner.contextAt(12).toolResults();
        assertThat(results).hasSizeLessThanOrEqualTo(8);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.status().get("blob")).endsWith("…(截断)");
            assertThat(result.status().get("blob").length())
                    .isLessThanOrEqualTo(1000);
            // 识别结论所需的关键字段不丢
            assertThat(result.status()).containsEntry("conflict", "NONE");
            assertThat(result.status()).containsEntry("installationStatus", "UNCONFIRMED");
        });
        int total = results.stream().mapToInt(result ->
                result.toolName().length() + result.dataNature().length()
                        + result.status().entrySet().stream().mapToInt(e -> e.getKey().length() + e.getValue().length()).sum()).sum();
        assertThat(total).isLessThanOrEqualTo(6000);
    }

    @Test void evictedResultCanBeFetchedAgainButImmediateRepeatIsBlocked() {
        var gateway = new CapabilityGateway();
        var oldest = register(gateway, "get_asset", (arguments, context) -> ok("get_asset", Map.of("components", "A")));
        register(gateway, "fill", (arguments, context) -> ok("fill", Map.of("blob", "x".repeat(1000))));
        var asset = new ActionProposal.CallTool("get_asset", Map.of(), "查询资产");
        var script = new ArrayList<ActionProposal>(List.of(asset, asset));
        for (int index = 0; index < 6; index++)
            script.add(new ActionProposal.CallTool("fill", Map.of("n", index), "取资料"));
        script.add(asset);
        script.add(plainAnswer());
        var reasoner = new ScriptedStub(script.toArray(ActionProposal[]::new));
        var outcome = new TaskOrchestrator(gateway).runAgent(input(12), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);
        assertThat(outcome.status()).isEqualTo(TaskOrchestrator.AgentStatus.COMPLETED);
        assertThat(reasoner.contextAt(8).toolResults()).noneMatch(result -> result.toolName().equals("get_asset"));
        assertThat(oldest.executions()).hasValue(2);
    }

    @Test void reportCanRefreshAfterAnotherToolContributesEvidence() {
        var gateway = new CapabilityGateway();
        var report = register(gateway, "generate_diagnosis_report", (arguments, context) ->
                ok("generate_diagnosis_report", Map.of("references", "0")));
        register(gateway, "lookup", (arguments, context) ->
                ok("lookup", Map.of(), knowledgeReference("ref-1", "手册证据")));
        var generate = new ActionProposal.CallTool("generate_diagnosis_report", Map.of(), "生成报告");
        var reasoner = new ScriptedStub(generate, generate,
                new ActionProposal.CallTool("lookup", Map.of(), "查阅资料"), generate, generate, plainAnswer());
        var outcome = new TaskOrchestrator(gateway).runAgent(input(10), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);
        assertThat(outcome.status()).isEqualTo(TaskOrchestrator.AgentStatus.COMPLETED);
        assertThat(report.executions()).hasValue(2);
        assertThat(reasoner.contextAt(3).toolResults())
                .noneMatch(result -> result.toolName().equals("generate_diagnosis_report"));
    }

    @Test void switchingSkillReferenceAllowsReopeningFirstReference() {
        var gateway = new CapabilityGateway();
        var active = new java.util.concurrent.atomic.AtomicReference<GeneralReasoner.SkillReference>();
        var read = register(gateway, "read_skill_reference", (arguments, context) -> {
            String name = (String) arguments.get("name");
            active.set(new GeneralReasoner.SkillReference(name, "模拟资料"));
            return ok("read_skill_reference", Map.of("name", name));
        });
        var first = new ActionProposal.CallTool("read_skill_reference", Map.of("name", "a.md"), "取 A");
        var second = new ActionProposal.CallTool("read_skill_reference", Map.of("name", "b.md"), "取 B");
        var reasoner = new ScriptedStub(first, first, second, first, plainAnswer());
        var toolContext = toolContext(gateway.toolNames());
        var skill = new GeneralReasoner.SkillSummary("demo", "1", "模拟", List.of(), List.of());
        var outcome = new TaskOrchestrator(gateway).runAgent(input(6), gateway, toolContext, List.of(), reasoner,
                () -> null, () -> new TaskOrchestrator.SkillStep(toolContext, List.of(),
                        new GeneralReasoner.ActiveSkill(skill, "模拟", List.of(), List.of(), List.of(),
                                List.of("a.md", "b.md"), active.get())));
        assertThat(outcome.status()).isEqualTo(TaskOrchestrator.AgentStatus.COMPLETED);
        assertThat(read.executions()).hasValue(3);
        assertThat(reasoner.contextAt(4).activeSkill().reference().name()).isEqualTo("a.md");
    }

    @Test void summaryKeepsVerificationAndSourcesBeforeLongOptionalFields() {
        var gateway = new CapabilityGateway();
        register(gateway, "get_configuration", (arguments, context) -> ok("get_configuration", Map.of(
                "aaOptional", "a".repeat(2000), "zzOptional", "z".repeat(2000),
                "installationStatus", "UNCONFIRMED", "verificationStatus", "DOCUMENTED", "conflict", "NONE",
                "sourceId", "source-1", "requiredChecks", "查铭牌" + "🔍".repeat(900)),
                knowledgeReference("ref-1", "手册证据")));
        var reasoner = new ScriptedStub(new ActionProposal.CallTool("get_configuration", Map.of(), "查配置"),
                plainAnswer());
        new TaskOrchestrator(gateway).runAgent(input(4), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);
        var status = reasoner.contextAt(1).toolResults().getLast().status();
        assertThat(status).containsEntry("installationStatus", "UNCONFIRMED").containsEntry("conflict", "NONE")
                .containsEntry("verificationStatus", "DOCUMENTED").containsEntry("sourceId", "source-1")
                .containsEntry("referenceIds", "ref-1").containsEntry("omittedFields", "…(截断)");
        assertThat(status.get("requiredChecks")).startsWith("查铭牌").endsWith("…(截断)").hasSizeLessThanOrEqualTo(1000);
        assertThat(status.get("requiredChecks").codePoints().allMatch(cp -> cp < 0xD800 || cp > 0xDFFF)).isTrue();
        assertThat(status.entrySet().stream().mapToInt(e -> e.getKey().length() + e.getValue().length()).sum())
                .isLessThanOrEqualTo(1600);
    }

    @Test void realModelProposalsExerciseReuseRefreshAndBoundedSummary() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("devicetruth.test.real-agent"));
        var model = io.devicetruth.adapter.AgentModelConnections.openai();
        var gateway = new CapabilityGateway();
        var report = register(gateway, "generate_diagnosis_report", (arguments, context) ->
                ok("generate_diagnosis_report", Map.of("status", "RUNNING")));
        var search = register(gateway, "search_knowledge", (arguments, context) ->
                ok("search_knowledge", Map.of("requiredChecks", "先核对模拟铭牌。" + "查".repeat(2000),
                        "installationStatus", "UNCONFIRMED", "conflict", "NONE", "sourceType", "MANUAL"),
                        knowledgeReference("ref-demo", "模拟手册要求核对铭牌。")));
        var expected = List.of("generate_diagnosis_report", "generate_diagnosis_report",
                "search_knowledge", "generate_diagnosis_report");
        var round = new AtomicInteger();
        var reasoner = new GeneralReasoner() {
            @Override public String name() { return model.name(); }
            @Override public ActionProposal propose(Context context) {
                int step = round.getAndIncrement();
                if (step == 3) {
                    var status = context.toolResults().getLast().status();
                    assertThat(status).containsEntry("installationStatus", "UNCONFIRMED")
                            .containsEntry("conflict", "NONE").containsEntry("sourceType", "MANUAL")
                            .containsEntry("referenceIds", "ref-demo");
                    assertThat(status.get("requiredChecks")).endsWith("…(截断)").hasSizeLessThanOrEqualTo(1000);
                }
                String request = step < expected.size()
                        ? "模拟验收，请提议一次 CALL_TOOL，toolName 为 " + expected.get(step)
                          + (step == 2 ? "，参数仅 query=模拟铭牌。" : "，arguments 为空对象。")
                          + (step == 1 ? " 本步专门检查重复调用的服务器保护，请仍提议本动作。" : "")
                        : "请直接 ANSWER，用 KNOWLEDGE 类别写一句模拟资料说明，所有数组留空。";
                var guided = new Context(request, List.of(new AgentTaskInput.Message("USER", request)), context.tools(),
                        context.references(), context.attemptedTools(), context.stepsUsed(), context.maxSteps(),
                        context.feedback(), List.of(), null, null, context.asOf(), context.toolResults());
                var action = model.propose(guided);
                if (step < expected.size()) {
                    assertThat(action).isInstanceOf(ActionProposal.CallTool.class);
                    assertThat(((ActionProposal.CallTool) action).toolName()).isEqualTo(expected.get(step));
                }
                return action;
            }
        };
        var outcome = new TaskOrchestrator(gateway).runAgent(input(5), gateway, toolContext(gateway.toolNames()),
                List.of(), reasoner);
        assertThat(outcome.status()).as(outcome.stopReason()).isEqualTo(TaskOrchestrator.AgentStatus.COMPLETED);
        assertThat(report.executions()).hasValue(2);
        assertThat(search.executions()).hasValue(1);
        assertThat(outcome.events()).anySatisfy(event ->
                assertThat(event.data()).containsEntry("errorCode", "DUPLICATE_CALL"));
    }

    // T11：未注册工具与未授权工具仍被网关拒绝，模型只得到安全错误码
    @Test void unauthorizedAndUnknownToolsStayRejected() {
        var gateway = new CapabilityGateway();
        register(gateway, "registered_but_denied",
                (arguments, context) -> ok("registered_but_denied", Map.of()));
        var reasoner = new ScriptedStub(
                new ActionProposal.CallTool("not_registered", Map.of(), "编造工具"),
                new ActionProposal.CallTool("registered_but_denied", Map.of(), "越权工具"),
                plainAnswer());

        new TaskOrchestrator(gateway).runAgent(input(12), gateway,
                toolContext(Set.of()), List.of(), reasoner);

        assertThat(reasoner.contextAt(1).feedback()).isEqualTo("工具调用失败：UNKNOWN_TOOL");
        assertThat(reasoner.contextAt(2).feedback()).isEqualTo("工具调用失败：TOOL_NOT_ALLOWED");
    }

    // T12a：ASK_USER 闭环不退化
    @Test void askUserPathStillWaitsForUser() {
        var gateway = new CapabilityGateway();
        var reasoner = new ScriptedStub(new ActionProposal.AskUser("缺少充电方式，是交流还是直流？",
                List.of("充电方式")));

        var outcome = new TaskOrchestrator(gateway).runAgent(input(12), gateway,
                toolContext(Set.of()), List.of(), reasoner);

        assertThat(outcome.status()).isEqualTo(TaskOrchestrator.AgentStatus.WAITING_FOR_USER);
        assertThat(outcome.question().question()).contains("交流还是直流");
    }

    // T12b：确诊类答案仍被 AnswerGuard 拦截，修正后正常完成
    @Test void answerGuardStillRejectsUnconfirmedFinding() {
        var gateway = new CapabilityGateway();
        var confirmed = new ActionProposal.Answer("CONFIRMED_FINDING", "已确诊为充电控制器损坏。",
                List.of(), List.of(), List.of(), List.of(), List.of());
        var reasoner = new ScriptedStub(confirmed, plainAnswer());

        var outcome = new TaskOrchestrator(gateway).runAgent(input(12), gateway,
                toolContext(Set.of()), List.of(), reasoner);

        assertThat(outcome.status()).isEqualTo(TaskOrchestrator.AgentStatus.COMPLETED);
        assertThat(reasoner.contextAt(1).feedback()).contains("UNCONFIRMED_FINDING");
        assertThat(outcome.answer().category().name()).isEqualTo("KNOWLEDGE");
    }

    // T12c：步数预算仍是最终约束
    @Test void stepBudgetStillTerminatesTheLoop() {
        var gateway = new CapabilityGateway();
        register(gateway, "get_asset", (arguments, context) -> ok("get_asset", Map.of()));
        var reasoner = new ScriptedStub(new ActionProposal.CallTool("get_asset", Map.of(), "查询"),
                new ActionProposal.CallTool("get_asset", Map.of("second", true), "第二次"));

        var outcome = new TaskOrchestrator(gateway).runAgent(input(1), gateway,
                toolContext(gateway.toolNames()), List.of(), reasoner);

        assertThat(outcome.status()).isEqualTo(TaskOrchestrator.AgentStatus.FAILED);
        assertThat(outcome.stopReason()).isEqualTo("BUDGET_EXHAUSTED");
    }

    // 结构化协议适配器必须真正收到工具结果：补全输入 JSON 含 toolResults 白名单
    @Test void structuredReasonerInputCarriesToolResults() {        var captured = new ArrayList<String>();
        StructuredAgentReasoner reasoner = new StructuredAgentReasoner("stub", (system, input) -> {
            captured.add(input);
            return "{\"type\":\"CALL_TOOL\",\"toolName\":\"search_knowledge\",\"arguments\":{\"query\":\"充电\"},\"purpose\":\"检索\"}";
        });
        var context = new GeneralReasoner.Context("目标",
                List.<AgentTaskInput.Message>of(), List.<io.devicetruth.core.capability.AgentToolTypes.ToolSpec>of(),
                List.<Reference>of(), List.<String>of(), 0, 12, null,
                List.<GeneralReasoner.SkillSummary>of(), null, null, null,
                List.of(new GeneralReasoner.ToolResult("get_configuration", "SIMULATED",
                        Map.of("conflict", "NONE", "installationStatus", "UNCONFIRMED"))));

        reasoner.propose(context);

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst())
                .contains("\"toolResults\"")
                .contains("\"toolName\":\"get_configuration\"")
                .contains("\"installationStatus\":\"UNCONFIRMED\"")
                .contains("\"conflict\":\"NONE\"");
    }

    // 真实模型笔误归一化：type 误写工具名但有 toolName 字段时归为 CALL_TOOL（仍走网关校验）
    @Test void toolNameMisplacedInTypeFieldIsNormalizedToCallTool() {
        StructuredAgentReasoner reasoner = new StructuredAgentReasoner("stub", (system, input) ->
                "{\"type\":\"activate_skill\",\"toolName\":\"activate_skill\","
                        + "\"arguments\":{\"skillId\":\"automotive-diagnosis\"},\"purpose\":\"选择领域指引\"}");
        var context = new GeneralReasoner.Context("目标", List.of(), List.of(), List.of(),
                List.of(), 0, 12, null);

        var action = reasoner.propose(context);

        assertThat(action).isInstanceOf(ActionProposal.CallTool.class);
        assertThat(((ActionProposal.CallTool) action).toolName()).isEqualTo("activate_skill");
    }

    // 归一化不放宽安全：ASK_USER 载荷混入 toolName 仍被严格拒绝（未知字段报错），不猜测语义
    @Test void mixedAskUserWithToolNameStaysRejected() {
        StructuredAgentReasoner reasoner = new StructuredAgentReasoner("stub", (system, input) ->
                input.contains("请纠正")
                        ? "{\"type\":\"ASK_USER\",\"question\":\"缺少什么？\",\"requestedFields\":[\"字段\"],\"toolName\":\"x\"}"
                        : "{\"type\":\"ASK_USER\",\"question\":\"缺少什么？\",\"requestedFields\":[\"字段\"],\"toolName\":\"x\"}");

        assertThatThrownBy(() -> reasoner.propose(context()))
                .hasMessageContaining("MODEL_OUTPUT_INVALID_AFTER_RETRY");
    }

    private static GeneralReasoner.Context context() {
        return new GeneralReasoner.Context("目标", List.of(), List.of(), List.of(), List.of(), 0, 12, null);
    }
}
