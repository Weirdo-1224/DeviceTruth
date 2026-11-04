package io.devicetruth.core.contract;

import java.util.List;
import java.util.Objects;

public final class Checks {
    private Checks() {}
    public static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    public static String id(String value, String field) {
        text(value, field);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw new IllegalArgumentException("Invalid " + field);
        return value;
    }
    public static <T> List<T> list(List<T> values) { return values == null ? List.of() : List.copyOf(values); }
    public static <T> T required(T value, String field) { return Objects.requireNonNull(value, field + " is required"); }
}
