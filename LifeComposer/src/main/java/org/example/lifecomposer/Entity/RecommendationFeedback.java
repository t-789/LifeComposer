package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

/** v0.1 M6: user feedback on a recommended direction, kept auditable. */
@Getter
@Setter
public class RecommendationFeedback {
    private Long id;
    private Long userId;
    private String directionId;
    /** useful / irrelevant / too_hard / time_mismatch / goal_changed */
    private String feedbackType;
    private String note;
    private String scoringVersion;
    private String recommendationSnapshotJson;
    private String createdAt;
}
