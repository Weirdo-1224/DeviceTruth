package io.devicetruth.api;

import io.devicetruth.api.ApiDtos.CreateSessionResponse;
import io.devicetruth.api.ApiDtos.HealthResponse;
import io.devicetruth.api.ApiDtos.MessageRequest;
import io.devicetruth.api.ApiDtos.MessageView;
import io.devicetruth.api.ApiDtos.MessagesResponse;
import io.devicetruth.api.ApiDtos.QueryView;
import io.devicetruth.api.ApiDtos.RouteResponse;
import io.devicetruth.api.ApiDtos.SourceView;
import io.devicetruth.application.ConversationFlowService;
import io.devicetruth.application.ConversationMessage;
import io.devicetruth.application.ConversationService;
import io.devicetruth.application.QueryAnswer;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S1 会话与消息入口；诊断状态/事件端点由后续里程碑补充。 */
@RestController
@RequestMapping("/api")
public class ConversationController {
    private final ConversationService conversations;
    private final ConversationFlowService flow;

    public ConversationController(ConversationService conversations, ConversationFlowService flow) {
        this.conversations = conversations;
        this.flow = flow;
    }

    @PostMapping("/sessions")
    public ResponseEntity<CreateSessionResponse> createSession() {
        var session = conversations.create();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateSessionResponse(session.sessionId(), session.createdAt(), ApiDtos.SIMULATED));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public MessagesResponse messages(@PathVariable UUID sessionId) {
        var session = conversations.require(sessionId);
        var views = session.messages().stream().map(this::view).toList();
        return new MessagesResponse(session.sessionId(), views, ApiDtos.SIMULATED);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<RouteResponse> message(@PathVariable UUID sessionId, @RequestBody MessageRequest request) {
        var result = flow.handle(sessionId, request.content(), request.assetId());
        return ResponseEntity.ok(new RouteResponse(result.route().name(), result.messageId(), result.reply(),
                ApiDtos.SIMULATED, result.assetId(), result.requestedAt(), view(result.queryAnswer())));
    }

    @GetMapping("/health")
    public HealthResponse health() { return new HealthResponse("UP", ApiDtos.SIMULATED); }

    private MessageView view(ConversationMessage message) {
        return new MessageView(message.messageId(), message.role().name(),
                message.route() == null ? null : message.route().name(), message.content(), message.createdAt());
    }

    private static QueryView view(QueryAnswer answer) {
        if (answer == null) return null;
        return new QueryView(answer.scope(), answer.assetId(), answer.answer(),
                answer.sources().stream()
                        .map(s -> new SourceView(s.evidenceId(), s.sourceRef(), s.sourceType(), s.dataNature()))
                        .toList(),
                answer.limitations(), answer.dataNature());
    }
}