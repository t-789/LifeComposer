package org.example.lifecomposer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoalDto {
    private Long id;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;
    private String category;
    private String priority;
    private String status;
    private String targetDate;
    private Integer progress;
    private String createdAt;
    private String updatedAt;
}
