package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DirectionRecommendationDto {
    private String directionId;
    private String name;
    private String description;
    private double score;
    /** SUITABLE / PARTIALLY_SUITABLE / NOT_RECOMMENDED / INSUFFICIENT_INFO */
    private String classification;
    private List<String> matchedTags;
    private List<String> missingTags;
    private List<String> userTags;
    private Double availableHoursPerWeek;
    private Integer requiredHoursPerWeek;
    private double timeFitScore;
    private String timeNote;
    private double goalRelevance;
    private String difficulty;
    private int preparationMonths;
    private List<String> resourceIds;
    private Map<String, Object> scoreBreakdown = new LinkedHashMap<>();
    private String scoringVersion;
    /** Version of the direction-explanation prompt that must be used to explain this result. */
    private String explanationPromptVersion;
    private boolean informationSufficient;
    private List<String> followUpQuestions;
}
