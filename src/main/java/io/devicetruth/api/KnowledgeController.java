package io.devicetruth.api;

import io.devicetruth.api.ApiDtos.ErrorResponse;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.knowledge.KnowledgeException;
import io.devicetruth.knowledge.KnowledgeImportService;
import io.devicetruth.knowledge.KnowledgeSearchService;
import io.devicetruth.knowledge.KnowledgeTools;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * product 专用知识接口：身份、项目与 run 全部由服务器解析，请求体不能提供 owner/project/permissions。
 * 检索返回的每个片段都登记为本 run 的引用；原文读取只允许打开本 run 已取回的引用。
 */
@RestController
@Profile("product")
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeController.class);

    public record SearchRequest(UUID runId, String query,
                                KnowledgeSearchService.KnowledgeFilters filters, Integer limit, String mode) {}
    public record SearchResponse(UUID runId, List<KnowledgeSearchService.KnowledgeHit> hits, boolean empty) {}
    public record ToolCatalog(List<KnowledgeTools.ToolDescriptor> tools) {}

    private final KnowledgeImportService imports;
    private final KnowledgeSearchService searches;
    private final KnowledgeTools tools;
    private final io.devicetruth.server.HttpAccess access;
    private String ownerId() { return access.current().storageIdentity().ownerId(); }
    private String projectId() { return access.current().storageIdentity().projectId(); }

    public KnowledgeController(KnowledgeImportService imports, KnowledgeSearchService searches, KnowledgeTools tools,
                               io.devicetruth.server.HttpAccess access) {
        this.imports = imports;
        this.searches = searches;
        this.tools = tools;
        this.access = access;
    }

    @PostMapping("/documents")
    ResponseEntity<KnowledgeImportService.ImportResult> importDocument(
            @RequestBody KnowledgeImportService.ImportRequest request) {
        var result = imports.importDocument(ownerId(), projectId(), request);
        return ResponseEntity.created(URI.create("/api/knowledge/documents/" + result.document().documentId()))
                .body(result);
    }

    @GetMapping("/documents/{documentId}")
    KnowledgeImportService.DocumentMetadata document(@PathVariable UUID documentId) {
        return imports.document(ownerId(), projectId(), documentId);
    }

    /** 暴露服务端工具合同，便于联调；本切片不宣称已接入 Runtime 工具目录。 */
    @GetMapping("/tools")
    ToolCatalog tools() { return new ToolCatalog(tools.descriptors()); }

    @PostMapping("/search")
    SearchResponse search(@RequestBody SearchRequest request) {
        if (request.runId() == null) throw new IllegalArgumentException("runId is required");
        if (request.mode() != null && !request.mode().isBlank()) {
            var channel = KnowledgeSearchService.SearchChannel.valueOf(request.mode().toUpperCase(java.util.Locale.ROOT));
            int limit = request.limit() == null ? 5 : request.limit();
            var outcome = searches.searchChannel(context(request.runId()), request.query(), request.filters(), limit, channel);
            return new SearchResponse(request.runId(), outcome.hits(), outcome.empty());
        }
        var outcome = tools.search(new KnowledgeTools.SearchRequest(request.query(), request.filters(), request.limit()),
                context(request.runId()));
        if (!outcome.success()) throw failure(outcome.error().code());
        return new SearchResponse(request.runId(), outcome.hits(), outcome.empty());
    }

    @GetMapping("/references/{referenceId}")
    KnowledgeSearchService.Excerpt reference(@PathVariable UUID referenceId, @RequestParam UUID runId) {
        return searches.excerptByReference(context(runId), referenceId);
    }

    @GetMapping("/documents/{documentId}/chunks/{chunkId}")
    KnowledgeSearchService.Excerpt excerpt(@PathVariable UUID documentId, @PathVariable UUID chunkId,
                                           @RequestParam UUID runId) {
        return searches.excerpt(context(runId), documentId, chunkId);
    }

    private ToolContext context(UUID runId) {
        var storage=access.run(runId).storageIdentity();
        return new ToolContext(storage.ownerId(), storage.projectId(), null, runId, null, Instant.now(), null,
                Set.of(KnowledgeTools.SEARCH, KnowledgeTools.EXCERPT));
    }

    private static KnowledgeException failure(String code) {
        int status = switch (code) {
            case "INVALID_ARGUMENTS" -> 400;
            case "TOOL_NOT_ALLOWED" -> 403;
            case "NOT_FOUND_OR_DENIED" -> 404;
            case "INVALID_CITATION", "EMBEDDING_DIMENSION_MISMATCH", "EMBEDDING_PROFILE_CONFLICT",
                 "DOCUMENT_NEEDS_REBUILD" -> 409;
            case "EMBEDDING_RESPONSE_INVALID" -> 502;
            case "EMBEDDING_UNAVAILABLE", "EMBEDDING_CONFIGURATION_MISSING" -> 503;
            default -> 500;
        };
        return new KnowledgeException(code, status);
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<?> accessFailure(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("code", e.getReason()==null?"NOT_FOUND_OR_DENIED":e.getReason()));
    }

    @ExceptionHandler(KnowledgeException.class)
    ResponseEntity<ErrorResponse> knowledgeFailure(KnowledgeException failure) {
        return error(failure.status(), failure.code());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> invalidRequest() {
        return error(400, "INVALID_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> internalError(Exception exception) {
        LOG.error("Knowledge API failed: {}", exception.getClass().getSimpleName());
        return error(500, "INTERNAL_ERROR");
    }

    private static ResponseEntity<ErrorResponse> error(int status, String code) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, "请求无法处理。",
                UUID.randomUUID().toString()));
    }
}
