package io.devicetruth.application;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SymptomResolverTest {
    private final SymptomResolver resolver = new SymptomResolver();

    @Test void matchesOnlyFiniteAliases() {
        for (String text : List.of("通信超时", "通信中断", "通信异常", "通讯超时", "Comm Timeout", " communication   timeout "))
            assertThat(resolver.resolve(text)).contains("COMM_TIMEOUT");
    }

    @Test void unknownSymptomIsNotMappedToCommTimeout() {
        assertThat(resolver.resolve("电池过热")).isEmpty();
        assertThat(resolver.resolve("刹车异响")).isEmpty();
        assertThat(resolver.resolve("通信不良")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
    }
}