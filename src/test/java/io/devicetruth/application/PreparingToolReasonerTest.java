package io.devicetruth.application;

import io.devicetruth.application.agent.PreparingToolReasoner;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.reasoning.*;
import io.devicetruth.knowledge.KnowledgeTools;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PreparingToolReasonerTest {
    @Test void batchPreparationPreservesAuthenticatedScopeAndRemainingTime() {
        UUID actor = UUID.randomUUID(), workspace = UUID.randomUUID();
        var scope = new ToolContext("owner", "project", UUID.randomUUID(), UUID.randomUUID(), null,
                Instant.now(), null, Set.of(KnowledgeTools.SEARCH), actor, workspace);
        var queries = new ArrayList<String>();
        GeneralReasoner delegate = new GeneralReasoner() {
            public String name() { return "batch-fixture"; }
            public ActionProposal propose(Context context) { throw new AssertionError("remaining budget must be forwarded"); }
            public ActionProposal propose(Context context, java.util.function.Supplier<String> abort,
                                          java.util.function.LongSupplier remaining) {
                assertThat(remaining.getAsLong()).isEqualTo(1234);
                return new ActionProposal.CallTools(List.of(
                        new ActionProposal.ToolCall(KnowledgeTools.SEARCH, Map.of("query", "first"), "lookup"),
                        new ActionProposal.ToolCall(KnowledgeTools.SEARCH, Map.of("query", "second"), "lookup")));
            }
        };
        var wrapper = new PreparingToolReasoner(delegate, scope, (current, call) -> {
            assertThat(current.actorUserId()).isEqualTo(actor);
            assertThat(current.workspaceId()).isEqualTo(workspace);
            queries.add((String) call.arguments().get("query"));
        });
        var spec = new io.devicetruth.core.capability.AgentToolTypes.ToolSpec(KnowledgeTools.SEARCH, "lookup", "knowledge",
                true, Map.of("type", "object"), "result");
        var context = new GeneralReasoner.Context("goal", List.of(), List.of(spec), List.of(), List.of(), 0, 10, null);
        assertThat(wrapper.propose(context, () -> null, () -> 1234)).isInstanceOf(ActionProposal.CallTools.class);
        assertThat(queries).containsExactly("first", "second");
    }

    @Test void prefetchUsesGeneratedQueryAndEffectiveSkillGrantAndHonorsCancellation() {
        var calls = new AtomicInteger();
        var action = new ActionProposal.CallTool(KnowledgeTools.SEARCH, Map.of("query", "generated query"), "lookup");
        GeneralReasoner delegate = new GeneralReasoner() {
            public String name() { return "fixture"; }
            public ActionProposal propose(Context context) { return action; }
        };
        var scope = new ToolContext("owner", "project", UUID.randomUUID(), UUID.randomUUID(), null,
                Instant.now(), null, Set.of(KnowledgeTools.SEARCH, KnowledgeTools.EXCERPT));
        var wrapper = new PreparingToolReasoner(delegate, scope, (current, call) -> {
            calls.incrementAndGet();
            assertThat(call.arguments()).containsEntry("query", "generated query");
            assertThat(current.runId()).isEqualTo(scope.runId());
            assertThat(current.allowedTools()).containsExactly(KnowledgeTools.SEARCH);
        });
        var tools = new KnowledgeTools(new io.devicetruth.knowledge.KnowledgeSearchService(null, null,
                new io.devicetruth.knowledge.DeterministicEmbeddingClient(io.devicetruth.knowledge.EmbeddingProfile.of(
                        "deterministic", "", "", "deterministic-char2", 1024, "1", 1000, false)), 5, .45)).descriptors();
        var descriptor = tools.getFirst();
        var spec = new io.devicetruth.core.capability.AgentToolTypes.ToolSpec(descriptor.name(), descriptor.description(),
                descriptor.domain(), true, descriptor.inputSchema(), descriptor.outputSchema());
        var authorized = new GeneralReasoner.Context("goal", List.of(), List.of(spec), List.of(), List.of(), 0, 10, null);
        assertThat(wrapper.propose(authorized)).isEqualTo(action);
        wrapper.propose(authorized, () -> "CANCELLED");
        wrapper.propose(new GeneralReasoner.Context("goal", List.of(), List.of(), List.of(), List.of(), 0, 10, null));
        assertThat(calls).hasValue(1);
    }
}
