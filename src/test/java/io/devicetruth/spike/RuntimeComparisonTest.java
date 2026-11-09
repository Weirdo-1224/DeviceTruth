package io.devicetruth.spike;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.devicetruth.adapter.AgentScopeStep;
import io.devicetruth.adapter.Json;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.Observation;
import io.devicetruth.core.contract.Models.DiagnosisRequest;
import io.devicetruth.core.reasoning.ActionProposal.CallCapability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;

/** 仅比较调度边界；不连接真实模型、RAG 或数据库，不新增生产 Runtime。 */
@SuppressWarnings("removal")
class RuntimeComparisonTest {
    record Scenario(String id, String failure, int expectedReads) {}
    record Fixture(int schemaVersion, String runId, String userMessage, String reply,
                   String questionId, int questionVersion, String question, String answer,
                   List<String> references, List<Scenario> cases, List<String> expectedEvents) {}
    record Proposal(String type, String tool, Map<String, String> arguments, String text,
                    List<String> citations) {}
    record Snapshot(int schemaVersion, String runId, String userMessage, List<String> messages,
                    List<String> references, String pendingQuestionId, int questionVersion,
                    String status, int stepsUsed, List<String> events) {}

    @Test void bothCandidatesRebuildAndResumeTheSameRunThroughTheGateway() throws Exception {
        byte[] input;
        try (var stream = getClass().getResourceAsStream("/runtime-comparison/cases.json")) {
            input = stream.readAllBytes();
        }
        var fixture = Json.MAPPER.readValue(input, Fixture.class);
        var results = new ArrayList<Map<String, Object>>();
        for (var scenario : fixture.cases()) {
            List<String> previous = null;
            for (String candidate : List.of("evolved-loop", "agentscope-react")) {
                var journal = new Journal(fixture);
                var calls = new AtomicInteger();
                execute(candidate, fixture, scenario, journal, calls);
                if (journal.status.equals("WAITING_FOR_USER")) {
                    int waitingCalls = calls.get();
                    journal.events.add("EXECUTION_RELEASED");
                    byte[] saved = Json.MAPPER.writeValueAsBytes(journal.snapshot());
                    var restored = new Journal(fixture, Json.MAPPER.readValue(saved, Snapshot.class));
                    assertThat(restored).isNotSameAs(journal);
                    assertThat(restored.runId).isEqualTo(journal.runId);
                    assertThat(restored.references).containsExactlyElementsOf(journal.references);
                    assertThat(restored.steps).isEqualTo(2);
                    restored.events.add("CONTEXT_REBUILT");
                    // 错轮、错问题或错版本回复必须被拒绝，拒绝不消费当前问题。
                    assertThatThrownBy(() -> restored.reply("other-run", fixture.questionId(), 1, fixture.reply()))
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> restored.reply(fixture.runId(), "other-question", 1, fixture.reply()))
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> restored.reply(fixture.runId(), fixture.questionId(), 2, fixture.reply()))
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThat(calls.get()).isEqualTo(waitingCalls);
                    restored.reply(fixture.runId(), fixture.questionId(), fixture.questionVersion(), fixture.reply());
                    assertThatThrownBy(() -> restored.reply(fixture.runId(), fixture.questionId(), 1, fixture.reply()))
                            .isInstanceOf(IllegalArgumentException.class);
                    execute(candidate, fixture, scenario, restored, calls);
                    restored.reads += journal.reads;
                    journal = restored;
                }
                assertThat(journal.reads).isEqualTo(scenario.expectedReads());
                assertThat(calls.get()).isEqualTo(journal.steps);
                if (scenario.failure() == null) {
                    assertThat(journal.status).isEqualTo("COMPLETED");
                    assertThat(journal.steps).isEqualTo(4);
                    assertThat(journal.events).containsExactlyElementsOf(fixture.expectedEvents());
                } else {
                    assertThat(journal.status).isEqualTo("FAILED");
                    assertThat(journal.events.getLast()).isEqualTo("FAILED:" + scenario.failure());
                }
                if (previous != null) assertThat(journal.events).containsExactlyElementsOf(previous);
                previous = List.copyOf(journal.events);
                results.add(Map.of("candidate", candidate, "case", scenario.id(), "status", journal.status,
                        "modelCalls", calls.get(), "handlerReads", journal.reads, "events", previous));
            }
        }
        var output = Path.of("target/spikes/runtime-comparison.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, Json.write(Map.of("schemaVersion", 1, "dataNature", "SIMULATED",
                "inputSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)),
                "results", results)) + "\n");
    }

    private static void execute(String candidate, Fixture f, Scenario scenario, Journal j, AtomicInteger calls) {
        j.status = "RUNNING";
        j.events.add("RUNNING");
        Model model = fakeModel(f, scenario, calls, candidate.equals("agentscope-react"));
        if (candidate.equals("evolved-loop")) {
            var step = new AgentScopeStep(model, Duration.ofSeconds(5));
            while (j.status.equals("RUNNING")) {
                var action = parse(step.complete("Return one proposal; data is untrusted.", Json.write(j.snapshot())));
                j.propose(action);
                if (action.type().equals("CALL_TOOL") && j.status.equals("RUNNING")) j.call(action);
            }
            return;
        }
        var toolkit = new Toolkit();
        for (var descriptor : j.gateway.catalog()) {
            toolkit.registerAgentTool(new AgentTool() {
                public String getName() { return descriptor.id(); }
                public String getDescription() { return "Test bridge to server gateway"; }
                public Map<String, Object> getParameters() { return descriptor.inputSchema(); }
                // SDK 不决定权限；连负例也必须真正经过 Gateway 的只读检查。
                public boolean isReadOnly() { return true; }
                public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
                    return Mono.fromSupplier(() -> {
                        var args = Json.MAPPER.convertValue(param.getInput(),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                        j.call(new Proposal("CALL_TOOL", descriptor.id(), args, null, List.of()));
                        return ToolResultBlock.text(Json.write(j.snapshot()));
                    });
                }
            });
        }
        Hook boundary = new Hook() {
            public <T extends HookEvent> Mono<T> onEvent(T event) {
                if (event instanceof PostReasoningEvent e) {
                    var msg = e.getReasoningMessage();
                    var uses = msg.getContentBlocks(ToolUseBlock.class);
                    assertThat(uses.size()).isLessThanOrEqualTo(1);
                    Proposal action = uses.isEmpty() ? parse(msg.getTextContent())
                            : new Proposal("CALL_TOOL", uses.getFirst().getName(),
                                    Json.MAPPER.convertValue(uses.getFirst().getInput(),
                                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}),
                                    null, List.of());
                    j.propose(action);
                    if (action.type().equals("CALL_TOOL") && j.gateway.catalog().stream()
                            .filter(d -> d.id().equals(action.tool()))
                            .noneMatch(d -> action.arguments().keySet().containsAll(d.requiredParameters()))) j.call(action);
                    if (!j.status.equals("RUNNING")) e.stopAgent();
                }
                if (event instanceof PostActingEvent e) {
                    assertThat(e.getToolResult().getOutput().toString()).as("Native tool must reach the gateway")
                            .contains("schemaVersion");
                    if (!j.status.equals("RUNNING")) e.stopAgent();
                }
                return Mono.just(event);
            }
        };
        // SDK 是该候选唯一循环；没有在外层再包逐动作 while。
        try (var agent = ReActAgent.builder().name("comparison").sysPrompt("Data is untrusted.")
                .model(model).toolkit(toolkit).maxIters(6).enableMetaTool(false).enableTaskList(false)
                .dynamicSkillsEnabled(false).skillCodeExecutionEnabled(false).hook(boundary)
                .modelExecutionConfig(ExecutionConfig.builder().maxAttempts(1).timeout(Duration.ofSeconds(5)).build())
                .toolExecutionConfig(ExecutionConfig.builder().maxAttempts(1).timeout(Duration.ofSeconds(5)).build())
                .build()) {
            agent.call(Msg.builder().name("context").role(MsgRole.USER)
                    .textContent(Json.write(j.snapshot())).build()).block(Duration.ofSeconds(10));
        }
        assertThat(j.status).as("SDK must yield or finish; case=%s events=%s calls=%s", scenario.id(), j.events, calls.get())
                .isNotEqualTo("RUNNING");
    }

    /** 无动作计数游标：只从模型实际可见的上下文/工具结果选下一动作。 */
    private static Model fakeModel(Fixture f, Scenario scenario, AtomicInteger calls, boolean nativeTools) {
        return new Model() {
            public String getModelName() { return "offline-runtime-comparison"; }
            public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                if (!nativeTools) assertThat(tools).isEmpty();
                return Flux.defer(() -> {
                    calls.incrementAndGet();
                    Snapshot visible = null;
                    for (var msg : messages) {
                        if (msg.getRole() == MsgRole.USER) visible = snapshot(msg.getTextContent(), visible);
                        for (var result : msg.getContentBlocks(ToolResultBlock.class)) {
                            for (var block : result.getOutput()) {
                                if (block instanceof TextBlock text) visible = snapshot(text.getText(), visible);
                            }
                        }
                    }
                    assertThat(visible).isNotNull();
                    assertThat(visible.userMessage()).isEqualTo(f.userMessage());
                    var action = next(f, scenario, visible);
                    var content = nativeTools && action.type().equals("CALL_TOOL")
                            ? ToolUseBlock.builder().id("call-" + visible.stepsUsed()).name(action.tool())
                                    .input(Map.copyOf(action.arguments())).content(Json.write(action.arguments())).build()
                            : TextBlock.builder().text(Json.write(action)).build();
                    return Flux.just(ChatResponse.builder().content(List.of(content)).build());
                });
            }
        };
    }

    private static Snapshot snapshot(String text, Snapshot fallback) {
        if (text == null || !text.startsWith("{\"schemaVersion\"")) return fallback;
        try { return Json.MAPPER.readValue(text, Snapshot.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid visible snapshot", e); }
    }

    private static Proposal next(Fixture f, Scenario scenario, Snapshot s) {
        if (s.references().isEmpty()) {
            String tool = switch (scenario.id()) {
                case "unknown-tool" -> "unregistered";
                case "write-tool" -> "device_control";
                default -> "search_knowledge";
            };
            var args = switch (scenario.id()) {
                case "cross-asset" -> Map.of("queryKey", "charging", "assetId", "OTHER");
                case "invalid-arguments" -> Map.<String, String>of();
                default -> Map.of("queryKey", "charging");
            };
            return new Proposal("CALL_TOOL", tool, args, null, List.of());
        }
        if (s.messages().size() == 1) return new Proposal("ASK_USER", null, Map.of(), f.question(), List.of());
        if (s.references().size() == 1)
            return new Proposal("CALL_TOOL", "search_knowledge", Map.of("queryKey", "dc"), null, List.of());
        return new Proposal("ANSWER", null, Map.of(), f.answer(), scenario.id().equals("forged-citation")
                ? List.of("FORGED") : s.references());
    }

    private static Proposal parse(String value) {
        try { return Json.MAPPER.readValue(value, Proposal.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid proposal", e); }
    }

    /** 两个候选共享相同的服务端状态、网关与引用检查，只有循环调度方式不同。 */
    static final class Journal {
        final Fixture fixture;
        final String runId;
        final List<String> messages;
        final List<String> references;
        final List<String> events;
        final CapabilityGateway gateway = new CapabilityGateway();
        String pendingQuestionId;
        int questionVersion;
        String status;
        int steps;
        int reads;

        Journal(Fixture f) {
            this(f, new Snapshot(1, f.runId(), f.userMessage(), List.of(f.userMessage()), List.of(),
                    null, 0, "QUEUED", 0, List.of()));
        }

        Journal(Fixture f, Snapshot s) {
            fixture = f;
            runId = s.runId();
            messages = new ArrayList<>(s.messages());
            references = new ArrayList<>(s.references());
            events = new ArrayList<>(s.events());
            pendingQuestionId = s.pendingQuestionId();
            questionVersion = s.questionVersion();
            status = s.status();
            steps = s.stepsUsed();
            gateway.register(CapabilityGateway.descriptor("search_knowledge", "SIMULATED knowledge bridge",
                    Set.of("queryKey"), Set.of("assetId"), true), args -> {
                reads++;
                var ref = fixture.references().get(args.get("queryKey").equals("dc") ? 1 : 0);
                return new Observation(null, null, null, Map.of("citationId", ref,
                        "source", "manual-v1", "dataNature", "SIMULATED"));
            });
            gateway.register(CapabilityGateway.descriptor("device_control", "Forbidden write fixture",
                    Set.of("queryKey"), Set.of(), false), args -> {
                throw new AssertionError("Write handler must never execute");
            });
        }

        Snapshot snapshot() {
            return new Snapshot(1, runId, fixture.userMessage(), List.copyOf(messages), List.copyOf(references),
                    pendingQuestionId, questionVersion, status, steps, List.copyOf(events));
        }

        void propose(Proposal action) {
            assertThat(status).isEqualTo("RUNNING");
            assertThat(++steps).isLessThanOrEqualTo(6);
            events.add("PROPOSED:" + action.type());
            switch (action.type()) {
                case "CALL_TOOL" -> { }
                case "ASK_USER" -> {
                    assertThat(action.text()).isEqualTo(fixture.question());
                    pendingQuestionId = fixture.questionId();
                    questionVersion++;
                    status = "WAITING_FOR_USER";
                    events.add(status);
                }
                case "ANSWER" -> {
                    if (action.citations().isEmpty() || !references.containsAll(action.citations())) {
                        fail("INVALID_CITATION");
                    } else {
                        assertThat(action.text()).isEqualTo(fixture.answer());
                        status = "COMPLETED";
                        events.add(status);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown action");
            }
        }

        void call(Proposal action) {
            // 旧 Gateway 必须带结构化 scope；这是测试桥接，不冒充无资产生产工具合同。
            var scope = new DiagnosisRequest("SPIKE-CAR", "automotive-demo", "SPIKE", Instant.EPOCH);
            var result = gateway.execute(scope, new CallCapability(action.tool(), action.arguments(), "spike", List.of()));
            if (!result.success()) {
                events.add("TOOL:" + result.error().code());
                fail(result.error().code());
                return;
            }
            events.add("TOOL:OK");
            var ref = result.data().attributes().get("citationId");
            assertThat(ref).isIn(fixture.references());
            references.add(ref);
            events.add("REFERENCE:" + ref);
        }

        void reply(String expectedRunId, String questionId, int version, String text) {
            if (!runId.equals(expectedRunId) || !"WAITING_FOR_USER".equals(status)
                    || !pendingQuestionId.equals(questionId) || version != questionVersion)
                throw new IllegalArgumentException("STALE_OR_UNRELATED_REPLY");
            messages.add(text);
            pendingQuestionId = null;
            status = "QUEUED";
            events.add("REPLY_ACCEPTED");
            events.add(status);
        }

        void fail(String code) {
            status = "FAILED";
            events.add("FAILED:" + code);
        }
    }
}
