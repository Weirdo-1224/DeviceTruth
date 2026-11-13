package io.devicetruth.api;

import io.devicetruth.application.agent.GeneralAgentService;
import io.devicetruth.application.agent.GeneralAgentService.*;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** product专用API；身份取服务器Bean，请求体不能提供owner/project/permissions。 */
@RestController
@Profile("product")
public class AgentConversationController {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentConversationController.class);
    private final GeneralAgentService service;
    public AgentConversationController(GeneralAgentService service) { this.service = service; }

    @PostMapping("/api/conversations")
    ResponseEntity<ConversationView> create(@RequestBody(required = false) Map<String, Object> body) {
        if (body != null && !body.isEmpty()) throw new IllegalArgumentException("Empty body required");
        var conversation = service.create();
        return ResponseEntity.created(URI.create("/api/conversations/" + conversation.conversationId())).body(conversation);
    }

    @GetMapping("/api/conversations")
    List<ConversationView> list() { return service.list(); }

    @GetMapping("/api/conversations/{id}")
    ConversationDetail read(@PathVariable UUID id) { return service.read(id); }

    @GetMapping("/api/conversations/{id}/messages")
    List<MessageView> messages(@PathVariable UUID id) { return service.read(id).messages(); }

    @PostMapping("/api/conversations/{id}/messages")
    ResponseEntity<RunView> submit(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID requestKey,
                                   @RequestBody AgentTaskInput input) {
        var run = service.submit(id, requestKey, input);
        return ResponseEntity.accepted().location(URI.create("/api/runs/" + run.runId())).body(run);
    }

    @GetMapping("/api/runs/{id}")
    RunView run(@PathVariable UUID id) { return service.run(id); }

    /** 本 run 已取回的知识引用（含文档版本与章节/页码），按归属过滤；跨身份读取返回 404。 */
    @GetMapping("/api/runs/{id}/references")
    java.util.List<io.devicetruth.core.contract.AgentResult.Reference> references(@PathVariable UUID id) {
        return service.references(id);
    }

    @GetMapping("/api/runs/{id}/tool-events")
    java.util.List<io.devicetruth.adapter.persistence.AgentRunRepository.ToolEvent> toolEvents(@PathVariable UUID id) {
        return service.toolEvents(id);
    }

    /** 模型尝试审计：版本、重试次数、结果码与单次耗时；无密钥/Prompt/原始输出。 */
    @GetMapping("/api/runs/{id}/model-events")
    java.util.List<io.devicetruth.adapter.persistence.AgentRunRepository.ModelEvent> modelEvents(@PathVariable UUID id) {
        return service.modelEvents(id);
    }

    /** 取消（03-03）：QUEUED/WAITING 立即 CANCELLED；RUNNING 协作式收尾；终态再取消 409。 */
    @PostMapping("/api/runs/{id}/cancel")
    ResponseEntity<RunView> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @ExceptionHandler(RequestFailure.class)
    ResponseEntity<Map<String, Object>> requestFailure(RequestFailure failure) {
        return error(failure.status(), failure.code());
    }

    @ExceptionHandler(io.devicetruth.knowledge.KnowledgeException.class)
    ResponseEntity<Map<String, Object>> knowledgeFailure(io.devicetruth.knowledge.KnowledgeException failure) {
        return error(failure.status(), failure.code());
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, Object>> invalidRequest() { return error(400, "INVALID_REQUEST"); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> internalError(Exception exception) {
        LOG.error("Product API failed: {}", exception.getClass().getSimpleName());
        return error(500, "INTERNAL_ERROR");
    }

    private ResponseEntity<Map<String, Object>> error(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", "请求无法处理。",
                "retryable", false, "traceId", UUID.randomUUID().toString()));
    }
}
