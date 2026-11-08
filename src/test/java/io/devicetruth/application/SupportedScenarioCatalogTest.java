package io.devicetruth.application;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportedScenarioCatalogTest {
    @Test void catalogIsDerivedFromPackagedScenarios() throws Exception {
        var catalog = SupportedScenarioCatalog.fromClasspath();
        assertThat(catalog.all().stream().map(SupportedScenarioCatalog.SupportedDiagnosis::assetId).toList())
                .containsExactlyInAnyOrder("CAR-A", "CAR-B", "CAR-C", "CAR-D");
        var b = catalog.find("CAR-B").orElseThrow();
        assertThat(b.domain()).isEqualTo("automotive-demo");
        assertThat(b.symptomCode()).isEqualTo("COMM_TIMEOUT");
        assertThat(b.diagnosisAsOf()).isEqualTo(Instant.parse("2026-11-03T04:00:00Z"));
    }

    @Test void unknownAssetIsNotMappedToCarB() throws Exception {
        var catalog = SupportedScenarioCatalog.fromClasspath();
        assertThat(catalog.find("SN-0245")).isEmpty();
        assertThat(catalog.supports("SN-0245")).isFalse();
    }

    @Test void duplicateAssetsAreRejected() {
        var entry = new SupportedScenarioCatalog.SupportedDiagnosis(
                "CAR-B", "automotive-demo", "COMM_TIMEOUT", Instant.parse("2026-11-03T04:00:00Z"));
        assertThatThrownBy(() -> new SupportedScenarioCatalog(List.of(entry, entry)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}