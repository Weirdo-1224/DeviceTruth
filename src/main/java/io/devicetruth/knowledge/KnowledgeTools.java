package io.devicetruth.knowledge;

import io.devicetruth.core.capability.AgentToolTypes.ToolOutcome;
import io.devicetruth.core.capability.AgentToolTypes.ToolSpec;
import io.devicetruth.core.capability.CapabilityGateway;
import io.devicetruth.core.capability.CapabilityTypes.ToolError;
import io.devicetruth.core.capability.ToolContext;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.Checks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 知识工具的服务端强类型接口与 JSON Schema。
 * query 允许中文、空格与标点；filters 是受限的嵌套对象；documentId/chunkId 保持严格标识校验。
 * registerInto 把两个工具注册进 CapabilityGateway：注册即服务器授权，模型只能用这里公开的模式。
 */
public final class KnowledgeTools {
    public static final String SEARCH = "search_knowledge";
    public static final String EXCERPT = "get_document_excerpt";
    private static final String ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$";
    private static final Set<String> SEARCH_ARGUMENTS = Set.of("query", "filters", "limit");
    private static final Set<String> FILTER_ARGUMENTS = Set.of("domain", "deviceModel", "documentId");
    private static final Set<String> EXCERPT_ARGUMENTS = Set.of("documentId", "chunkId");

    /** 强类型检索请求：limit 省略时使用服务端默认上限。 */
    public record SearchRequest(String query, KnowledgeSearchService.KnowledgeFilters filters, Integer limit) {
        public SearchRequest {
            query = Checks.text(query, "query");
            if (query.codePointCount(0, query.length()) > KnowledgeSearchService.MAX_QUERY_CHARS)
                throw new IllegalArgumentException("query too long");
            filters = filters == null ? KnowledgeSearchService.KnowledgeFilters.NONE : filters;
            if (limit != null && (limit < 1 || limit > 50)) throw new IllegalArgumentException("limit out of range");
        }
    }

    /** 强类型原文读取请求：必须指向本 run 已取回过的文档片段。 */
    public record ExcerptRequest(String documentId, String chunkId) {
        public ExcerptRequest {
            Checks.text(documentId, "documentId");
            Checks.text(chunkId, "chunkId");
            try {
                UUID.fromString(documentId);
                UUID.fromString(chunkId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid chunk reference");
            }
        }
    }

    public record SearchOutcome(boolean success, String requestId, String toolName,
                                List<KnowledgeSearchService.KnowledgeHit> hits, boolean empty, ToolError error) {
        public SearchOutcome { hits = List.copyOf(hits); }
    }

    public record ExcerptOutcome(boolean success, String requestId, String toolName,
                                 KnowledgeSearchService.Excerpt excerpt, ToolError error) {}

    public record ToolDescriptor(String name, String description, String domain, boolean readOnly,
                                 Map<String, Object> inputSchema, String outputSchema) {}

    private final KnowledgeSearchService searchService;

    public KnowledgeTools(KnowledgeSearchService searchService) { this.searchService = searchService; }

    public SearchOutcome search(SearchRequest request, ToolContext context) {
        String requestId = UUID.randomUUID().toString();
        try {
            requireTool(context, SEARCH);
            var outcome = searchService.search(context, request.query(), request.filters(),
                    request.limit() == null ? searchService.maxHits() : request.limit());
            return new SearchOutcome(true, requestId, SEARCH, outcome.hits(), outcome.empty(), null);
        } catch (KnowledgeException failure) {
            return new SearchOutcome(false, requestId, SEARCH, List.of(), true, error(failure));
        }
    }

    public ExcerptOutcome excerpt(ExcerptRequest request, ToolContext context) {
        String requestId = UUID.randomUUID().toString();
        try {
            requireTool(context, EXCERPT);
            var excerpt = searchService.excerpt(context, UUID.fromString(request.documentId()),
                    UUID.fromString(request.chunkId()));
            return new ExcerptOutcome(true, requestId, EXCERPT, excerpt, null);
        } catch (KnowledgeException failure) {
            return new ExcerptOutcome(false, requestId, EXCERPT, null, error(failure));
        }
    }

    /** 通用 JSON 参数入口：供后续 Runtime 以对象参数调用；未知键与错误类型在此被拒绝。 */
    public SearchOutcome search(Map<String, Object> arguments, ToolContext context) {
        try {
            requireTool(context, SEARCH);
            return search(parseSearch(arguments), context);
        } catch (KnowledgeException | IllegalArgumentException failure) {
            return new SearchOutcome(false, UUID.randomUUID().toString(), SEARCH, List.of(), true, error(failure));
        }
    }

    public ExcerptOutcome excerpt(Map<String, Object> arguments, ToolContext context) {
        try {
            requireTool(context, EXCERPT);
            return excerpt(parseExcerpt(arguments), context);
        } catch (KnowledgeException | IllegalArgumentException failure) {
            return new ExcerptOutcome(false, UUID.randomUUID().toString(), EXCERPT, null, error(failure));
        }
    }

    /**
     * 把两个知识工具注册进网关：注册即服务器授权，参数模式与输出模式同时公开给推理器。
     * 未注册的工具在网关处直接拒绝，模型无法通过编造工具名扩大权限。
     */
    public void registerInto(CapabilityGateway gateway) {
        Checks.required(gateway, "gateway");
        for (ToolDescriptor descriptor : descriptors()) {
            ToolSpec spec = new ToolSpec(descriptor.name(), descriptor.description(), descriptor.domain(),
                    descriptor.readOnly(), descriptor.inputSchema(), descriptor.outputSchema());
            if (SEARCH.equals(descriptor.name())) gateway.registerTool(spec, this::searchOutcome);
            else if (EXCERPT.equals(descriptor.name())) gateway.registerTool(spec, this::excerptOutcome);
        }
    }

    private ToolOutcome searchOutcome(Map<String, Object> arguments, ToolContext context) {
        SearchOutcome outcome = search(arguments, context);
        if (!outcome.success()) return ToolOutcome.failed(outcome.requestId(), SEARCH, outcome.error());
        List<Reference> references = outcome.hits().stream().map(KnowledgeTools::hitReference).toList();
        return ToolOutcome.ok(outcome.requestId(), SEARCH, outcome, references, dataNature(references));
    }

    private ToolOutcome excerptOutcome(Map<String, Object> arguments, ToolContext context) {
        ExcerptOutcome outcome = excerpt(arguments, context);
        if (!outcome.success()) return ToolOutcome.failed(outcome.requestId(), EXCERPT, outcome.error());
        var excerpt = outcome.excerpt();
        var reference = new Reference(excerpt.referenceId().toString(), "KNOWLEDGE_DOCUMENT",
                excerpt.documentId().toString(), excerpt.documentVersion(), excerpt.chunkId().toString(),
                excerpt.title(), excerpt.page(), excerpt.section(), truncate(excerpt.content(), 2000),
                excerpt.dataNature());
        return ToolOutcome.ok(outcome.requestId(), EXCERPT, outcome, List.of(reference), excerpt.dataNature());
    }

    private static Reference hitReference(KnowledgeSearchService.KnowledgeHit hit) {
        return new Reference(hit.referenceId().toString(), "KNOWLEDGE_DOCUMENT", hit.documentId().toString(),
                hit.documentVersion(), hit.chunkId().toString(), hit.title(), hit.page(), hit.section(),
                truncate(hit.content(), 600), hit.dataNature());
    }

    /** 任一引用为模拟数据时整体保持 SIMULATED，避免把模拟手册冒充为已授权现场资料。 */
    private static String dataNature(List<Reference> references) {
        return references.stream().allMatch(reference -> "AUTHORIZED".equals(reference.dataNature()))
                ? "AUTHORIZED" : "SIMULATED";
    }

    private static String truncate(String content, int max) {
        if (content == null) return "";
        return content.length() <= max ? content : content.substring(0, max);
    }

    public List<ToolDescriptor> descriptors() {
        return List.of(
                new ToolDescriptor(SEARCH,
                        "在本项目已授权的知识资料中做语义检索，返回带文档版本与章节/页码的可定位引用；不返回设备当前配置事实",
                        "knowledge", true, searchSchema(),
                        "SearchOutcome{success,requestId,toolName,hits[{referenceId,documentId,documentVersion,"
                                + "chunkId,chunkNo,page,section,similarity,dataNature}],empty,error}"),
                new ToolDescriptor(EXCERPT,
                        "读取本 run 已取回引用的原文片段与定位信息；未取回、跨项目或伪造标识一律拒绝",
                        "knowledge", true, excerptSchema(),
                        "ExcerptOutcome{success,requestId,toolName,excerpt{documentId,documentVersion,chunkId,"
                                + "chunkNo,page,section,content,dataNature},error}"));
    }

    private SearchRequest parseSearch(Map<String, Object> arguments) {
        Map<String, Object> values = arguments == null ? Map.of() : arguments;
        for (String key : values.keySet())
            if (!SEARCH_ARGUMENTS.contains(key)) throw KnowledgeException.invalid("unknown argument");
        Object query = values.get("query");
        if (!(query instanceof String text)) throw KnowledgeException.invalid("query is required");
        Integer limit = null;
        Object rawLimit = values.get("limit");
        if (rawLimit != null) {
            if (!(rawLimit instanceof Number number) || number.doubleValue() != number.intValue())
                throw KnowledgeException.invalid("limit must be an integer");
            limit = number.intValue();
        }
        return new SearchRequest(text, parseFilters(values.get("filters")), limit);
    }

    private KnowledgeSearchService.KnowledgeFilters parseFilters(Object raw) {
        if (raw == null) return KnowledgeSearchService.KnowledgeFilters.NONE;
        if (!(raw instanceof Map<?, ?> map)) throw KnowledgeException.invalid("filters must be an object");
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !FILTER_ARGUMENTS.contains(key))
                throw KnowledgeException.invalid("unknown filter");
            if (!(entry.getValue() instanceof String))
                throw KnowledgeException.invalid("filters must be strings");
        }
        UUID documentId = null;
        Object rawDocumentId = map.get("documentId");
        if (rawDocumentId != null) {
            try {
                documentId = UUID.fromString((String) rawDocumentId);
            } catch (IllegalArgumentException e) {
                throw KnowledgeException.invalid("invalid documentId");
            }
        }
        return new KnowledgeSearchService.KnowledgeFilters((String) map.get("domain"),
                (String) map.get("deviceModel"), documentId);
    }

    private ExcerptRequest parseExcerpt(Map<String, Object> arguments) {
        Map<String, Object> values = arguments == null ? Map.of() : arguments;
        for (String key : values.keySet())
            if (!EXCERPT_ARGUMENTS.contains(key)) throw KnowledgeException.invalid("unknown argument");
        if (!(values.get("documentId") instanceof String documentId)
                || !(values.get("chunkId") instanceof String chunkId))
            throw KnowledgeException.invalid("documentId and chunkId are required");
        return new ExcerptRequest(documentId, chunkId);
    }

    private static void requireTool(ToolContext context, String tool) {
        if (context == null) throw KnowledgeException.invalid("context is required");
        if (!context.allows(tool)) throw new KnowledgeException("TOOL_NOT_ALLOWED", 403);
    }

    private static ToolError error(RuntimeException failure) {
        if (failure instanceof KnowledgeException knowledge)
            return new ToolError(knowledge.code(), knowledge.getMessage(),
                    "EMBEDDING_UNAVAILABLE".equals(knowledge.code()));
        return new ToolError("INVALID_ARGUMENTS", "Invalid tool arguments", false);
    }

    private Map<String, Object> searchSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "string", "minLength", 1,
                "maxLength", KnowledgeSearchService.MAX_QUERY_CHARS,
                "description", "自然语言问题，可含中文、空格与标点"));
        properties.put("filters", Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "domain", Map.of("type", "string", "pattern", ID_PATTERN),
                        "deviceModel", Map.of("type", "string", "pattern", ID_PATTERN),
                        "documentId", Map.of("type", "string", "format", "uuid")),
                "required", List.of()));
        properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", searchService.maxHits()));
        return Map.of("type", "object", "additionalProperties", false, "properties", properties,
                "required", List.of("query"));
    }

    private Map<String, Object> excerptSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "documentId", Map.of("type", "string", "format", "uuid"),
                        "chunkId", Map.of("type", "string", "format", "uuid")),
                "required", List.of("documentId", "chunkId"));
    }
}
