package io.devicetruth.application;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 有限症状别名：只把白名单内的表述映射到 COMM_TIMEOUT，不做任意自然语言猜测。 */
public final class SymptomResolver {
    public static final String SUPPORTED_SYMPTOM_CODE = "COMM_TIMEOUT";
    private static final List<String> ALIASES = List.of(
            "通信超时", "通信中断", "通信异常", "通讯超时", "comm timeout", "communication timeout");

    public Optional<String> resolve(String text) {
        String normalized = normalize(text);
        for (String alias : ALIASES)
            if (normalized.contains(normalize(alias))) return Optional.of(SUPPORTED_SYMPTOM_CODE);
        return Optional.empty();
    }

    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\u3000]+", "");
    }
}