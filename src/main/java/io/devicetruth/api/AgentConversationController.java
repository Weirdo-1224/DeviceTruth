package io.devicetruth.api;

import io.devicetruth.adapter.Json;
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
    private final io.devicetruth.application.agent.RunEventStreamService streams;
    public AgentConversationController(GeneralAgentService service,
                                       io.devicetruth.application.agent.RunEventStreamService streams) {
        this.service = service;
        this.streams = streams;
    }

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

    /** 公开已选版本与适用范围，正文和文件路径仅在服务端读取。 */
    @GetMapping("/api/runs/{id}/skill")
    io.devicetruth.skill.SkillRuntime.SelectionView skill(@PathVariable UUID id) {
        return service.runSkill(id);
    }

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

    /** 事件历史（05-01）：按 seq 升序；无 after 返回全部，after=N 返回 seq>N；非法游标 400。 */
    @GetMapping("/api/runs/{id}/events")
    java.util.List<io.devicetruth.application.agent.SafeAgentEventViews.PublicEvent> runEvents(
            @PathVariable UUID id, @RequestParam(value = "after", required = false) String after) {
        return service.events(id, parseAfter(after));
    }

    /** 实时事件流（05-01 + 复验 §8.2）：SSE 异步流；校验（400/404/409/503）在提交响应前完成。
     *  错误体直接手写 JSON：SSE 客户端的 Accept 只有 text/event-stream，
     *  走 @ExceptionHandler 的内容协商会 406 升级为 500（复验实测）。 */
    @GetMapping(path = "/api/runs/{id}/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    Object runEventStream(@PathVariable UUID id,
                          @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                          jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        try {
            return streams.open(id, lastEventId);
        } catch (GeneralAgentService.RequestFailure failure) {
            response.setStatus(failure.status());
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getOutputStream().write(Json.MAPPER.writeValueAsBytes(Map.of(
                    "code", failure.code(), "message", "请求无法处理。",
                    "retryable", false, "traceId", UUID.randomUUID().toString())));
            return null;
        }
    }

    private static long parseAfter(String after) {
        if (after == null) return 0;
        long value;
        try {
            value = Long.parseLong(after.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid after cursor");
        }
        if (value < 0) throw new IllegalArgumentException("Invalid after cursor");
        return value;
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

    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    void disconnectedStream() {
        // 连接已经断开，订阅由 SSE 结束回调清理。
    }

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
