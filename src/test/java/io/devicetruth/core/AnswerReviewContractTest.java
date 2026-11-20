package io.devicetruth.core;

import io.devicetruth.adapter.Json;
import io.devicetruth.core.contract.AgentResult;
import io.devicetruth.core.contract.AnswerReview;
import io.devicetruth.core.contract.AnswerReview.*;
import io.devicetruth.core.reasoning.ActionProposal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnswerReviewContractTest {
    private Unit unit(String id, int index, List<String> dependencies) {
        return new Unit(id, Position.CLAIM, index, "手册版本为 2026.1", Nature.DOCUMENT_FACT,
                null, null, List.of(new Support("r1", "版本为 2026.1")), dependencies);
    }

    @Test void legacyAnswerAndResultKeepTheirOriginalMeaning() throws Exception {
        var proposal = Json.MAPPER.readValue("""
                {"type":"ANSWER","category":"KNOWLEDGE","summary":"旧资料说明",
                 "claims":[],"suggestions":[],"uncertainties":[],"limitations":[],"referenceIds":[]}
                """, ActionProposal.class);
        var answer = (ActionProposal.Answer) proposal;
        assertThat(answer.reviewVersion()).isNull();
        assertThat(answer.units()).isEmpty();
        var result = Json.MAPPER.readValue("""
                {"summary":"旧答案","category":"KNOWLEDGE","claims":[],"suggestions":[],
                 "uncertainties":[],"limitations":[],"references":[],"riskLevel":"LOW",
                 "safetyDisposition":"NORMAL","dataNature":"SIMULATED"}
                """, AgentResult.class);
        assertThat(result.review()).isNull();
        assertThat(result.dataNature()).isEqualTo("SIMULATED");
        assertThat(AgentResult.Certainty.valueOf("CONFIRMED")).isEqualTo(AgentResult.Certainty.CONFIRMED);
    }

    @Test void versionedReviewRoundTripsWithoutGrantingAnything() throws Exception {
        var review = new Review(1, List.of(unit("claim-0", 0, List.of())),
                List.of(new Decision("claim-0", Disposition.PENDING, "EVIDENCE_REVIEW_REQUIRED", List.of("r1"))), true);
        assertThat(Json.MAPPER.readValue(Json.write(review), Review.class)).isEqualTo(review);
        assertThatThrownBy(() -> new Review(3, List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Json.MAPPER.readValue(
                Json.write(review).replace("\"version\":1", "\"trusted\":true,\"version\":1"), Review.class))
                .isInstanceOf(Exception.class);
    }

    @Test void dependencyGraphRejectsUnknownDuplicateAndCyclicUnits() {
        assertThatThrownBy(() -> AnswerReview.validateUnits(List.of(unit("a", 0, List.of("missing")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnswerReview.validateUnits(List.of(unit("a", 0, List.of()), unit("a", 1, List.of()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnswerReview.validateUnits(List.of(unit("a", 0, List.of("b")), unit("b", 1, List.of("a")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> AnswerReview.validateUnits(List.of(unit("a", 0, List.of()), unit("b", 1, List.of("a")))))
                .doesNotThrowAnyException();
    }

    @Test void decisionsMustCoverExactlyTheGivenUnits() {
        assertThatThrownBy(() -> new Review(1, List.of(unit("a", 0, List.of())), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Review(1, List.of(unit("a", 0, List.of())),
                List.of(new Decision("invented", Disposition.RETAINED, "OK", List.of())), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
