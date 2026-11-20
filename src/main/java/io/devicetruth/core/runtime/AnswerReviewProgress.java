package io.devicetruth.core.runtime;

import io.devicetruth.core.contract.AnswerReview.Review;
import io.devicetruth.core.reasoning.ActionProposal.Answer;

/** 原 Run 检查点中的答案阶段，草案仅服务内部恢复，不投影到公开事件。 */
public record AnswerReviewProgress(int reviews, int corrections, Phase phase,
                                   Answer candidate, Answer reviewedDraft, Review review, Integer protocolVersion) {
    public AnswerReviewProgress(int reviews, int corrections, Phase phase, Answer candidate, Answer reviewedDraft, Review review) {
        this(reviews, corrections, phase, candidate, reviewedDraft, review, 1);
    }
    public enum Phase { NONE, REVIEWING, REVIEWED, CORRECTING, FINISHED }
    public static AnswerReviewProgress empty() {
        return new AnswerReviewProgress(0, 0, Phase.NONE, null, null, null);
    }
    public AnswerReviewProgress {
        if (protocolVersion == null) protocolVersion = 1;
        if (protocolVersion != 1 && protocolVersion != 2) throw new IllegalArgumentException("Review protocol version");
        if (reviews < 0 || reviews > 2 || corrections < 0 || corrections > 1 || phase == null
                || (review == null) != (reviewedDraft == null)
                || phase != Phase.NONE && candidate == null)
            throw new IllegalArgumentException("Invalid answer review progress");
    }
}
