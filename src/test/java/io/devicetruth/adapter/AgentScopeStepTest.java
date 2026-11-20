package io.devicetruth.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.message.Msg;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.core.reasoning.Reasoner;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class AgentScopeStepTest {
    @Test void flashJsonStepsDisableThinkingWithoutChangingOtherModels() throws Exception {
        var requests = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.add(Json.MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{}\"},\"finish_reason\":\"stop\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            for (String model : List.of("deepseek-flash", "other-compatible-model"))
                AgentScopeStep.openai("test-key", "http://127.0.0.1:" + server.getAddress().getPort(), model)
                        .complete("JSON", "test");
            assertThat(requests.get(0).path("thinking").path("type").asText()).isEqualTo("disabled");
            assertThat(requests.get(1).has("thinking")).isFalse();
            assertThat(requests).allSatisfy(request -> assertThat(request.path("stream").asBoolean()).isFalse());
        } finally { server.stop(0); }
    }
    @Test void oneSubscriptionReturnsOneJsonProposalWithoutExecutingTools() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Model model = new Model() {
            public String getModelName() { return "offline-sdk-contract"; }
            public Flux<ChatResponse> stream(List<Msg> messages,List<ToolSchema> tools,GenerateOptions options) {
                assertThat(tools).isEmpty();
                assertThat(options.getExecutionConfig().getMaxAttempts()).isEqualTo(1);
                return Flux.defer(() -> {
                    calls.incrementAndGet();
                    return Flux.just(ChatResponse.builder().content(List.of(TextBlock.builder()
                            .text("{\"type\":\"ESCALATE\",\"reason\":\"spike\"}").build())).build());
                });
            }
        };
        String json = new AgentScopeStep(model, Duration.ofSeconds(1)).complete("JSON protocol", "state version 0");
        assertThat(new ObjectMapper().readTree(json).get("type").asText()).isEqualTo("ESCALATE");
        assertThat(calls).hasValue(1);
    }

    @Test void missingKeyFailsExplicitly() {
        assertThatThrownBy(() -> AgentScopeStep.qwen(null)).hasMessageContaining("DASHSCOPE_API_KEY");
    }
    @Test void modelTimeoutIsBounded() {
        Model never=new Model() {
            public String getModelName() {return "offline-timeout";}
            public Flux<ChatResponse> stream(List<Msg> m,List<ToolSchema> t,GenerateOptions o) {return Flux.never();}
        };
        assertThatThrownBy(()->new AgentScopeStep(never,Duration.ofMillis(20)).complete("JSON","state")).hasMessageContaining("Timeout");
    }

    @Test void requestTimeoutUsesSmallerRemainingBudget() {
        var observed = new java.util.concurrent.atomic.AtomicReference<Duration>();
        Model never = new Model() {
            public String getModelName() { return "offline-remaining-budget"; }
            public Flux<ChatResponse> stream(List<Msg> m, List<ToolSchema> t, GenerateOptions o) {
                observed.set(o.getExecutionConfig().getTimeout());
                return Flux.never();
            }
        };
        var step = new AgentScopeStep(never, Duration.ofSeconds(45));

        assertThatThrownBy(() -> step.completeWithUsage("JSON", "state", Duration.ofMillis(20)))
                .hasMessageContaining("Timeout");
        assertThat(observed.get()).isEqualTo(Duration.ofMillis(20));
    }

    @Test void protocolRetryUsesLatestRemainingBudgetInsteadOfResetting() {
        var budgets = new java.util.ArrayList<Duration>();
        var reasoner = StructuredAgentReasoner.withBudgetedUsage("stub", (system, input, remaining) -> {
            budgets.add(remaining);
            return new StructuredAgentReasoner.CompletionResult("invalid", "stub", null);
        });
        var context = new GeneralReasoner.Context("问题", List.of(), List.of(), List.of(), List.of(), 0, 12, null);

        assertThatThrownBy(() -> reasoner.propose(context, () -> null,
                () -> 3000L - budgets.size() * 1000L)).hasMessage("MODEL_OUTPUT_INVALID_AFTER_RETRY");
        assertThat(budgets).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(2));
    }

    @Test void reportedUsageSurvivesAnInvalidProposalButDeadlinePreventsRetry() {
        var expired = new AtomicBoolean();
        var calls = new AtomicInteger();
        Model model = new Model() {
            public String getModelName() { return "provider-model"; }
            public Flux<ChatResponse> stream(List<Msg> m, List<ToolSchema> t, GenerateOptions o) {
                calls.incrementAndGet();
                expired.set(true);
                return Flux.just(ChatResponse.builder().usage(new ChatUsage(31, 9, 0.2))
                        .content(List.of(TextBlock.builder().text("invalid JSON").build())).build());
            }
        };
        var step = new AgentScopeStep(model, Duration.ofSeconds(1));
        var reasoner = StructuredAgentReasoner.withUsage("qwen", step::completeWithUsage);
        var context = new GeneralReasoner.Context("问题", List.of(), List.of(), List.of(), List.of(), 0, 12, null);
        assertThatThrownBy(() -> reasoner.propose(context, () -> expired.get() ? "TOTAL_TIMEOUT" : null))
                .hasMessage("TOTAL_TIMEOUT");
        assertThat(calls).hasValue(1);
        assertThat(reasoner.drainAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.model()).isEqualTo("provider-model");
            assertThat(attempt.outcome()).isEqualTo("INVALID_JSON_OR_SCHEMA");
            assertThat(attempt.usage()).isEqualTo(new Reasoner.TokenUsage(31, 9));
        });
    }

    @Test void openAiCompatibleResponseReportsTokensAndLeavesAbsentUsageUnknown() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                var payload = new java.util.LinkedHashMap<String, Object>();
                payload.put("id", "test-model-response");
                payload.put("object", "chat.completion");
                payload.put("created", 0);
                payload.put("model", "test-openai-model");
                payload.put("choices", List.of(java.util.Map.of("index", 0, "finish_reason", "stop",
                        "message", java.util.Map.of("role", "assistant", "content",
                                "{\"type\":\"ASK_USER\",\"question\":\"设备编号是多少？\",\"requestedFields\":[\"assetId\"]}"))));
                int attempt = calls.getAndIncrement();
                if (attempt == 0) {
                    payload.put("usage", java.util.Map.of("prompt_tokens", 39, "completion_tokens", 13,
                            "total_tokens", 52, "cost", new java.math.BigDecimal("0.0123456789123456789")));
                } else if (attempt == 2) {
                    payload.put("usage", java.util.Map.of("prompt_tokens", 0));
                }
                byte[] bytes = Json.write(payload).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var step = AgentScopeStep.openai("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "test-openai-model");
            var reasoner = StructuredAgentReasoner.withUsage("openai", step::completeWithUsage);
            var context = new GeneralReasoner.Context("问题", List.of(), List.of(), List.of(), List.of(), 0, 12, null);
            assertThat(reasoner.propose(context)).isInstanceOf(io.devicetruth.core.reasoning.ActionProposal.AskUser.class);
            assertThat(reasoner.drainAttempts()).singleElement().satisfies(attempt -> {
                assertThat(attempt.model()).isEqualTo("test-openai-model");
                assertThat(attempt.usage()).isEqualTo(new Reasoner.TokenUsage(39L, 13L,
                        new java.math.BigDecimal("0.0123456789123456789")));
            });
            reasoner.propose(context);
            assertThat(reasoner.drainAttempts()).singleElement().satisfies(attempt -> assertThat(attempt.usage()).isNull());
            reasoner.propose(context);
            assertThat(reasoner.drainAttempts()).singleElement().satisfies(attempt ->
                    assertThat(attempt.usage()).isEqualTo(new Reasoner.TokenUsage(0L, null, null)));
        } finally {
            server.stop(0);
        }
    }
}
