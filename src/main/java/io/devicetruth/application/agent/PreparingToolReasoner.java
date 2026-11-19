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
        return prepare(context, abort, delegate.propose(context, abort));
    }

    @Override public ActionProposal propose(Context context, Supplier<String> abort,
                                            java.util.function.LongSupplier remainingMillis) {
        return prepare(context, abort, delegate.propose(context, abort, remainingMillis));
    }

    private ActionProposal prepare(Context context, Supplier<String> abort, ActionProposal action) {
        List<ActionProposal.CallTool> calls = action instanceof ActionProposal.CallTool call ? List.of(call)
                : action instanceof ActionProposal.CallTools batch ? batch.calls().stream()
                    .map(call -> new ActionProposal.CallTool(call.toolName(), call.arguments(), call.purpose())).toList()
                : List.of();
        for (var call : calls) {
            if (abort.get() != null) break;
            var allowed = new HashSet<String>();
            context.tools().forEach(tool -> allowed.add(tool.name()));
            allowed.retainAll(scope.allowedTools());
            if (allowed.contains(call.toolName())) {
                var current = new ToolContext(scope.ownerId(), scope.projectId(), scope.conversationId(), scope.runId(),
                        scope.selectedAssetId(), scope.requestedAt(), scope.asOf(), allowed, scope.actorUserId(), scope.workspaceId());
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
