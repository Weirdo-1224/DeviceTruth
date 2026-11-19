package io.devicetruth.application.agent;

import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.reasoning.ActionProposal;
import io.devicetruth.core.reasoning.GeneralReasoner;
import io.devicetruth.core.reasoning.Reasoner;
import io.devicetruth.knowledge.EmbeddingException;
import io.devicetruth.knowledge.KnowledgeException;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Prepares external inputs outside persistence locks, using this run's effective Skill grants. */
public final class PreparingToolReasoner implements GeneralReasoner {
    private final GeneralReasoner delegate;
    private final ToolContext scope;
    private final BiConsumer<ToolContext, ActionProposal.CallTool> preparation;

    public PreparingToolReasoner(GeneralReasoner delegate, ToolContext scope,
                                BiConsumer<ToolContext, ActionProposal.CallTool> preparation) {
        this.delegate = delegate;
        this.scope = scope;
        this.preparation = preparation;
    }

    @Override public String name() { return delegate.name(); }
    @Override public List<Reasoner.Attempt> drainAttempts() { return delegate.drainAttempts(); }
    @Override public ActionProposal propose(Context context) { return propose(context, () -> null); }

    @Override public ActionProposal propose(Context context, Supplier<String> abort) {
        var action = delegate.propose(context, abort);
        if (abort.get() == null && action instanceof ActionProposal.CallTool call) {
            var allowed = new HashSet<String>();
            context.tools().forEach(tool -> allowed.add(tool.name()));
            allowed.retainAll(scope.allowedTools());
            if (allowed.contains(call.toolName())) {
                var current = new ToolContext(scope.ownerId(), scope.projectId(), scope.conversationId(), scope.runId(),
                        scope.selectedAssetId(), scope.requestedAt(), scope.asOf(), allowed);
                try { preparation.accept(current, call); }
                catch (KnowledgeException | EmbeddingException | IllegalArgumentException expected) {
                    // The normal gateway reports invalid arguments/outages; hybrid can still use BM25.
                    // Cache misses during the locked step cannot issue external HTTP requests.
                }
            }
        }
        return action;
    }
}
