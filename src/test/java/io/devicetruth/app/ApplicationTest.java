package io.devicetruth.app;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTest {
    @Test void helpDocumentsOfflineAndOnlineModes() {
        assertThat(DeviceTruthApplication.help()).contains("scripted", "qwen", "SIMULATED");
    }
}
