package io.devicetruth.core.capability;

import io.devicetruth.core.contract.Models.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static io.devicetruth.core.contract.Checks.*;

/** 能力相关的协议类型定义。 */
public final class CapabilityTypes {
    private CapabilityTypes() {}
    /** 能力描述：ID、说明、必填/可选参数、所属域、是否只读及输入/输出模式。 */
    public record Descriptor(String id, String description, Set<String> requiredParameters,
                             Set<String> optionalParameters, String domain, boolean readOnly,
                             Map<String, Object> inputSchema, String outputSchema) {
        public Descriptor {
            requiredParameters = Set.copyOf(requiredParameters); optionalParameters = Set.copyOf(optionalParameters);
            inputSchema = Map.copyOf(inputSchema);
        }
    }
    /** 能力执行的返回视图：原始记录、发现的证据元数据、相关规则、附加属性，可部分为 null。 */
    public record Observation(List<EvidenceRecord> records, List<EvidenceMetadata> discovered,
                              List<Rule> rules, Map<String, String> attributes) {
        public Observation {
            records = list(records); discovered = list(discovered); rules = list(rules);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
        public static Observation records(List<EvidenceRecord> records) { return new Observation(records,null,null,null); }
    }
    /** 能力执行失败的错误信息：错误码、消息、是否可重试。 */
    public record ToolError(String code, String message, boolean retryable) {}
    /** 能力执行结果：success 时 data 非空，否则 error 非空。 */
    public record CapabilityResult(boolean success, String requestId, String capabilityId,
                                   String sourceRef, Observation data, ToolError error) {}
    /** 只读能力处理器函数接口：接收字符串参数，返回观察结果。 */
    @FunctionalInterface public interface ReadOnlyHandler { Observation execute(Map<String, String> arguments); }
}
