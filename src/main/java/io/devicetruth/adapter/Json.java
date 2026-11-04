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

public final class Json {
    private Json() {}
    public static final ObjectMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    static {
        MAPPER.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer,CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float,CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean,CoercionAction.Fail);
    }
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot encode protocol value", e); }
    }
}
