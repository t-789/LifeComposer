package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecommendationFeedbackDto {
    private Long id;
    private String directionId;
    private String feedbackType;
    private String note;
    private String scoringVersion;
    private String createdAt;
}
