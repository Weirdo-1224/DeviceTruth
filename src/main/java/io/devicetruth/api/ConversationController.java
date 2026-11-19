package io.devicetruth.api;

import io.devicetruth.api.ApiDtos.CreateSessionResponse;
import io.devicetruth.api.ApiDtos.DiagnosisEventsResponse;
import io.devicetruth.api.ApiDtos.DiagnosisView;
import io.devicetruth.api.ApiDtos.MessageRequest;
import io.devicetruth.api.ApiDtos.MessageView;
import io.devicetruth.api.ApiDtos.MessagesResponse;
import io.devicetruth.api.ApiDtos.QueryView;
import io.devicetruth.api.ApiDtos.RouteResponse;
import io.devicetruth.api.ApiDtos.SourceView;
import io.devicetruth.application.ConversationFlowService;
import io.devicetruth.application.ConversationMessage;
import io.devicetruth.application.ConversationService;
import io.devicetruth.application.DiagnosisRunService;
import io.devicetruth.application.DiagnosisRunService.RunSnapshot;
import io.devicetruth.application.ExecutionStatus;
import io.devicetruth.application.QueryAnswer;
import io.devicetruth.application.Route;
import io.devicetruth.application.RunEventViews;
import io.devicetruth.application.SafePublicFields;
import io.devicetruth.core.contract.Models.DiagnosisResult;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S1 会话、消息与诊断状态入口；诊断由有界异步执行器真实调用 Core。仅 legacy profile 装配。 */
@RestController
@RequestMapping("/api")
@Profile("legacy")
public class ConversationController {
    private final ConversationService conversations;
    private final ConversationFlowService flow;
    private final DiagnosisRunService runs;

    public ConversationController(ConversationService conversations, ConversationFlowService flow,
                                  DiagnosisRunService runs) {
        this.conversations = conversations;
        this.flow = flow;
        this.runs = runs;
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
        var body = new RouteResponse(result.route().name(), result.messageId(), result.reply(), ApiDtos.SIMULATED,
                result.assetId(), result.requestedAt(), view(result.queryAnswer()), result.diagnosisId(),
                result.route() == Route.DIAGNOSE ? ExecutionStatus.QUEUED : null);
        if (result.status() == HttpStatus.ACCEPTED.value())
            // 诊断 POST 永远返回 202 + QUEUED + Location，真实状态只能 GET。
            return ResponseEntity.accepted()
                    .location(URI.create("/api/diagnoses/" + result.diagnosisId()))
                    .body(body);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/diagnoses/{diagnosisId}")
    public DiagnosisView diagnosis(@PathVariable UUID diagnosisId,
                                   @RequestParam(required = false) UUID sessionId) {
        return diagnosisView(runs.require(diagnosisId, sessionId));
    }

    @GetMapping("/diagnoses/{diagnosisId}/events")
    public DiagnosisEventsResponse events(@PathVariable UUID diagnosisId,
                                          @RequestParam(required = false) UUID sessionId) {
        RunSnapshot snapshot = runs.require(diagnosisId, sessionId);
        return new DiagnosisEventsResponse(snapshot.diagnosisId(), RunEventViews.available(snapshot),
                RunEventViews.of(snapshot), ApiDtos.SIMULATED);
    }

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

    /** 未结束时不预填任何结论字段；完成后所有派生文本都经 §4.8 白名单过滤。 */
    private static DiagnosisView diagnosisView(RunSnapshot snapshot) {
        ExecutionStatus status = snapshot.executionStatus();
        if (!snapshot.finished()) {
            return new DiagnosisView(snapshot.diagnosisId(), snapshot.sessionId(), status, null,
                    snapshot.requestedAt(), snapshot.diagnosisAsOf(), snapshot.createdAt(), snapshot.startedAt(),
                    null, null, null, null, null, null, List.of(), null, List.of(), null, ApiDtos.SIMULATED);
        }
        DiagnosisResult result = snapshot.report() == null ? null : snapshot.report().result();
        if (result == null) {
            return new DiagnosisView(snapshot.diagnosisId(), snapshot.sessionId(), status, snapshot.coreTaskId(),
                    snapshot.requestedAt(), snapshot.diagnosisAsOf(), snapshot.createdAt(), snapshot.startedAt(),
                    snapshot.completedAt(), null, null, null, null, null, List.of(), null, List.of(),
                    snapshot.errorCode(), ApiDtos.SIMULATED);
        }
        return new DiagnosisView(snapshot.diagnosisId(), snapshot.sessionId(), status, snapshot.coreTaskId(),
                snapshot.requestedAt(), snapshot.diagnosisAsOf(), snapshot.createdAt(), snapshot.startedAt(),
                snapshot.completedAt(),
                result.outcome() == null ? null : result.outcome().name(),
                result.stepsUsed(),
                SafePublicFields.diagnosisSummary(result),
                List.copyOf(result.supportingEvidenceIds()),
                List.copyOf(result.appliedRuleIds()),
                SafePublicFields.warnings(result.warnings()),
                SafePublicFields.stopReason(result.stopReason()),
                SafePublicFields.openNeeds(result.missingEvidenceNeeds()),
                status == ExecutionStatus.FAILED ? snapshot.errorCode() : null,
                ApiDtos.SIMULATED);
    }
}