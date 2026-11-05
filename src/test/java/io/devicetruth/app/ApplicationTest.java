package io.devicetruth.app;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationTest {
    @Test void helpDocumentsOfflineAndOnlineModes() {
        assertThat(DeviceTruthApplication.help()).contains("scripted", "qwen", "openai", "SIMULATED");
    }
    @Test void acceptsAllReasonersAndKeepsOfflineDefault() {
        assertThat(CliOptions.parse(new String[]{}).reasoner()).isEqualTo("scripted");
        for(String reasoner:new String[]{"scripted","qwen","openai"})
            assertThat(CliOptions.parse(new String[]{"--reasoner="+reasoner}).reasoner()).isEqualTo(reasoner);
        assertThatThrownBy(()->CliOptions.parse(new String[]{"--reasoner=unknown"})).isInstanceOf(IllegalArgumentException.class);
    }
}
