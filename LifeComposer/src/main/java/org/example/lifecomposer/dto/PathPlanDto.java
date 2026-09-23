package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PathPlanDto {
    private String directionId;
    private String name;
    private String description;
    private String currentLevel;
    private List<String> matchedTags = new ArrayList<>();
    private List<String> missingTags = new ArrayList<>();
    private List<GapTask> gapTasks = new ArrayList<>();
    private List<PracticeTask> practiceTasks = new ArrayList<>();
    private List<ResourceSummaryDto> resources = new ArrayList<>();
    private String expectedInvestment;
    private String scoringVersion;
    /** Version of the path-suggestion prompt that must be used to narrate this plan. */
    private String suggestionPromptVersion;
    private boolean informationSufficient;
    private List<String> followUpQuestions = new ArrayList<>();

    @Getter
    @Setter
    public static class GapTask {
        private String tag;
        private String action;
        private List<ResourceSummaryDto> suggestedResources = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class PracticeTask {
        private String resourceId;
        private String name;
        private String note;
    }
}
