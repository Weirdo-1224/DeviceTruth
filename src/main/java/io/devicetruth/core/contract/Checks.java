package io.devicetruth.core.contract;

import java.util.List;
import java.util.Objects;

/** 协议值的通用校验工具：所有 record 的紧缩构造函数都通过这里做防御性检查。 */
public final class Checks {
    private Checks() {}
    /** 非空且非空白字符串。 */
    public static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    /** ID 格式：字母或数字开头，仅含 [A-Za-z0-9_.:-]，最长 128 字符。 */
    public static String id(String value, String field) {
        text(value, field);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw new IllegalArgumentException("Invalid " + field);
        return value;
    }
    /** 列表防御性拷贝；null 视为空列表。 */
    public static <T> List<T> list(List<T> values) { return values == null ? List.of() : List.copyOf(values); }
    /** 非 null 必填值。 */
    public static <T> T required(T value, String field) { return Objects.requireNonNull(value, field + " is required"); }
}
