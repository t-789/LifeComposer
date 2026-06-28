package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Goal {
    private Long id;
    private Long userId;
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
