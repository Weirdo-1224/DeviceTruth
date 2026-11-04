package io.devicetruth.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;

/** 全局唯一的 Jackson 序列化器：全部开启严格模式——任何宽松反序列化行为都被显式关闭，
 *  从源头防止脏数据悄悄进入协议值。 */
public final class Json {
    private Json() {}
    public static final ObjectMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)   // 时间统一 ISO-8601 文本
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)    // 输出按 key 排序，保证确定性
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // 多余字段即报错
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)    // 一个 JSON 后不允许尾随内容
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)  // 枚举不接受数字
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES) // 原始类型不接受 null
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)       // 浮点不允许冒充整数
            .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    static {
        // 字符串字段进一步拒绝数字/浮点/布尔值的隐式强转
        MAPPER.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer,CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float,CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean,CoercionAction.Fail);
    }
    /** 序列化为 JSON 字符串；编码失败说明协议值本身非法，统一抛 IllegalArgumentException。 */
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot encode protocol value", e); }
    }
}
