package io.devicetruth.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.message.Msg;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;

class AgentScopeStepTest {
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
}
