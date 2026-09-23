package org.example.lifecomposer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecommendationFeedbackRequest {

    @NotBlank(message = "directionId 不能为空")
    private String directionId;

    @NotBlank(message = "feedbackType 不能为空")
    private String feedbackType;

    @Size(max = 500, message = "反馈说明不能超过500字符")
    private String note;
}
