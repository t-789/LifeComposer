package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ResourceSummaryDto {
    private String resourceId;
    private String name;
    private String type;
    private String difficulty;
    private String preparationPeriod;
    private String dataQuality;
    private String sourceUrl;
    private List<String> teachesSkills;
    private List<String> requiredSkills;
}
