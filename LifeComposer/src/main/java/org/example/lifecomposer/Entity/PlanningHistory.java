package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanningHistory {
    private Long id;
    private Long userId;
    private String type;
    private String requestJson;
    private String responseJson;
    private String provider;
    private String model;
    private String status;
    private String errorMessage;
    private String createdAt;
}
