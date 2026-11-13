package io.devicetruth.core.capability;

import io.devicetruth.core.capability.CapabilityTypes.ToolError;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.Checks;
import java.util.List;
import java.util.Map;

/**
 * 通用 Agent 工具合同：与 legacy 的 Map&lt;String,String&gt; 单值参数分离。
 * inputSchema 是 JSON Schema 形状（允许嵌套对象与中文自由文本），由网关做顶层键与必填校验，
 * 嵌套结构由各工具自己的强类型解析拒绝未知字段。
 */
public final class AgentToolTypes {
    private AgentToolTypes() {}

    /** 工具描述：名称、说明、输入/输出模式与只读标记；模型只能看到这里公开的字段。 */
    public record ToolSpec(String name, String description, String domain, boolean readOnly,
                           Map<String, Object> inputSchema, String outputSchema) {
        public ToolSpec {
            Checks.id(name, "toolName");
            Checks.text(description, "description");
            Checks.id(domain, "domain");
            Checks.required(inputSchema, "inputSchema");
            Checks.text(outputSchema, "outputSchema");
        }

        /** 顶层必填参数名，来自 JSON Schema 的 required。 */
        @SuppressWarnings("unchecked")
        public List<String> requiredParameters() {
            Object required = inputSchema.get("required");
            return required instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
        }

        /** 顶层允许的参数名，来自 JSON Schema 的 properties；未列出的键一律拒绝。 */
        @SuppressWarnings("unchecked")
        public java.util.Set<String> allowedParameters() {
            Object properties = inputSchema.get("properties");
            if (!(properties instanceof Map<?, ?> map)) return java.util.Set.of();
            return map.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    /** 工具执行结果：success 与 error 互斥；references 是本 run 可取回的引用集合。 */
    public record ToolOutcome(boolean success, String requestId, String toolName, Object data,
                              List<Reference> references, ToolError error, String dataNature,
                              Map<String, String> modelContext) {
        public ToolOutcome {
            Checks.text(requestId, "requestId");
            Checks.id(toolName, "toolName");
            references = Checks.list(references);
            modelContext = Map.copyOf(modelContext);
            if (success && error != null) throw new IllegalArgumentException("success and error are exclusive");
            if (!success && error == null) throw new IllegalArgumentException("failure requires an error");
            if (!"SIMULATED".equals(dataNature) && !"AUTHORIZED".equals(dataNature))
                throw new IllegalArgumentException("Invalid dataNature");
        }

        public static ToolOutcome ok(String requestId, String toolName, Object data, List<Reference> references,
                                     String dataNature) {
            return ok(requestId, toolName, data, references, dataNature, Map.of());
        }

        public static ToolOutcome ok(String requestId, String toolName, Object data, List<Reference> references,
                                     String dataNature, Map<String, String> modelContext) {
            return new ToolOutcome(true, requestId, toolName, data, references, null, dataNature, modelContext);
        }

        public static ToolOutcome failed(String requestId, String toolName, ToolError error) {
            return new ToolOutcome(false, requestId, toolName, null, List.of(), error, "SIMULATED", Map.of());
        }
    }

    /** 只读工具处理器：参数已通过网关顶层校验，仍需在实现内做嵌套类型与权限校验。 */
    @FunctionalInterface
    public interface AgentToolHandler {
        ToolOutcome execute(Map<String, Object> arguments, ToolContext context);
    }
}
