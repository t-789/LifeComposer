package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanningHistoryDto {
    private Long id;
    private String type;
    private String requestJson;
    private String responseJson;
    private String provider;
    private String model;
    private String status;
    private String errorMessage;
    private String createdAt;
}
