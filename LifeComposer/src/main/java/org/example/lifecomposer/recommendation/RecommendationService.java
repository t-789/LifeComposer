package org.example.lifecomposer.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lifecomposer.Entity.Resource;
import org.example.lifecomposer.Entity.UserProfile;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.example.lifecomposer.Repository.UserProfileRepository;
import org.example.lifecomposer.Service.AvailableTimeParser;
import org.example.lifecomposer.Service.CapabilityNormalizationService;
import org.example.lifecomposer.Service.PromptCatalog;
import org.example.lifecomposer.Service.PromptId;
import org.example.lifecomposer.config.RecommendationProperties;
import org.example.lifecomposer.dto.DirectionRecommendationDto;
import org.example.lifecomposer.dto.PathPlanDto;
import org.example.lifecomposer.dto.ResourceSummaryDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * v0.1 M5: deterministic, explainable direction scoring and path generation.
 *
 * The LLM only explains these DTOs; it never invents scores or tags. Weights and
 * thresholds come from {@link RecommendationProperties}, directions from
 * {@link GrowthDirectionCatalog}.
 */
@Service
public class RecommendationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserProfileRepository userProfileRepository;
    private final ResourceRepository resourceRepository;
    private final CapabilityNormalizationService normalizationService;
    private final GrowthDirectionCatalog directionCatalog;
    private final RecommendationProperties properties;
    private final PromptCatalog promptCatalog;

    public RecommendationService(UserProfileRepository userProfileRepository,
                                 ResourceRepository resourceRepository,
                                 CapabilityNormalizationService normalizationService,
                                 GrowthDirectionCatalog directionCatalog,
                                 RecommendationProperties properties,
                                 PromptCatalog promptCatalog) {
        this.userProfileRepository = userProfileRepository;
        this.resourceRepository = resourceRepository;
        this.normalizationService = normalizationService;
        this.directionCatalog = directionCatalog;
        this.properties = properties;
        this.promptCatalog = promptCatalog;
    }

    public List<DirectionRecommendationDto> recommend(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId);
        List<String> rawSkills = stringArray(profile == null ? null : profile.getSkillsJson());
        List<String> userTags = normalizationService.normalizeSkills(rawSkills);
        List<String> goals = stringArray(profile == null ? null : profile.getGoals());
        OptionalDouble hours = AvailableTimeParser.parseHoursPerWeek(profile == null ? null : profile.getAvailableTime());
        boolean sufficient = profile != null && !userTags.isEmpty() && hours.isPresent();
        List<String> followUp = followUpQuestions(profile, userTags, hours, goals);

        List<DirectionRecommendationDto> result = new ArrayList<>();
        for (GrowthDirection direction : directionCatalog.all()) {
            result.add(score(direction, userTags, goals, hours, sufficient, followUp));
        }
        result.sort((a, b) -> {
            int byScore = Double.compare(b.getScore(), a.getScore());
            return byScore != 0 ? byScore : a.getDirectionId().compareTo(b.getDirectionId());
        });
        return result;
    }

    /** Finds one scored recommendation by direction id, or null when unknown. */
    public DirectionRecommendationDto recommendationFor(Long userId, String directionId) {
        if (directionId == null) {
            return null;
        }
        return recommend(userId).stream()
                .filter(dto -> directionId.equals(dto.getDirectionId()))
                .findFirst()
                .orElse(null);
    }

    public PathPlanDto path(Long userId, String directionId) {
        GrowthDirection direction = directionCatalog.byId(directionId);
        if (direction == null) {
            return null;
        }
        UserProfile profile = userProfileRepository.findByUserId(userId);
        List<String> userTags = normalizationService.normalizeSkills(
                stringArray(profile == null ? null : profile.getSkillsJson()));
        List<String> required = direction.getTargetTags();
        List<String> matched = required.stream().filter(userTags::contains).toList();
        List<String> missing = required.stream().filter(tag -> !userTags.contains(tag)).toList();
        OptionalDouble hours = AvailableTimeParser.parseHoursPerWeek(profile == null ? null : profile.getAvailableTime());

        PathPlanDto plan = new PathPlanDto();
        plan.setDirectionId(direction.getId());
        plan.setName(direction.getName());
        plan.setDescription(direction.getDescription());
        plan.setCurrentLevel(matched.isEmpty() ? "尚未建立相关能力" : String.join("、", matched));
        plan.setMatchedTags(matched);
        plan.setMissingTags(missing);
        plan.setScoringVersion(properties.getScoringVersion());
        plan.setSuggestionPromptVersion(promptCatalog.version(PromptId.PATH_SUGGESTION));

        for (String tag : missing) {
            PathPlanDto.GapTask task = new PathPlanDto.GapTask();
            task.setTag(tag);
            task.setAction("补齐 " + tag + "：先完成对应课程，再用一个小项目或竞赛验证。");
            task.setSuggestedResources(resourcesTeaching(tag, direction));
            plan.getGapTasks().add(task);
        }

        for (String resourceId : direction.getResourceIds()) {
            Resource resource = resourceRepository.findByResourceId(resourceId);
            if (resource == null) {
                continue;
            }
            plan.getResources().add(toSummary(resource));
            if ("competition".equals(resource.getType())) {
                PathPlanDto.PracticeTask practice = new PathPlanDto.PracticeTask();
                practice.setResourceId(resource.getResourceId());
                practice.setName(resource.getName());
                practice.setNote(missing.isEmpty()
                        ? "能力已基本匹配，可作为第一个实践目标。"
                        : "建议先补齐 " + String.join("、", missing) + " 后再报名。");
                plan.getPracticeTasks().add(practice);
            }
        }

        double available = hours.orElse(0);
        String investment = "每周约 " + direction.getRequiredHoursPerWeek() + " 小时，持续约 "
                + direction.getPreparationMonths() + " 个月";
        if (hours.isPresent() && available < direction.getRequiredHoursPerWeek()) {
            investment = investment + "（你当前填写的是每周 " + trimNumber(available) + " 小时，建议先调整到 "
                    + direction.getRequiredHoursPerWeek() + " 小时或选择投入更小的方向）";
        } else if (hours.isEmpty()) {
            investment = investment + "（尚未填写每周可投入时间）";
        }
        plan.setExpectedInvestment(investment);

        boolean sufficient = profile != null && !userTags.isEmpty();
        plan.setInformationSufficient(sufficient);
        plan.setFollowUpQuestions(followUpQuestions(profile, userTags, hours,
                stringArray(profile == null ? null : profile.getGoals())));
        return plan;
    }

    public CapabilityNormalizationService.CapabilityGap gap(Long userId, String directionId) {
        GrowthDirection direction = directionCatalog.byId(directionId);
        if (direction == null) {
            return null;
        }
        UserProfile profile = userProfileRepository.findByUserId(userId);
        List<String> userTags = normalizationService.normalizeSkills(
                stringArray(profile == null ? null : profile.getSkillsJson()));
        List<String> required = direction.getTargetTags();
        List<String> matched = required.stream().filter(userTags::contains).toList();
        List<String> missing = required.stream().filter(tag -> !userTags.contains(tag)).toList();
        return new CapabilityNormalizationService.CapabilityGap(userTags, required, matched, missing);
    }

    public GrowthDirectionCatalog catalog() {
        return directionCatalog;
    }

    private DirectionRecommendationDto score(GrowthDirection direction,
                                             List<String> userTags,
                                             List<String> goals,
                                             OptionalDouble hours,
                                             boolean sufficient,
                                             List<String> followUp) {
        List<String> required = direction.getTargetTags();
        List<String> matched = required.stream().filter(userTags::contains).toList();
        List<String> missing = required.stream().filter(tag -> !userTags.contains(tag)).toList();
        double skillScore = required.isEmpty() ? 0.0 : (double) matched.size() / required.size();

        double timeFit = 0.5;
        String timeNote = "未填写每周可投入时间";
        if (hours.isPresent()) {
            double available = hours.getAsDouble();
            timeFit = clamp(available / Math.max(1, direction.getRequiredHoursPerWeek()));
            timeNote = "每周可投入 " + trimNumber(available) + " 小时 / 方向建议 "
                    + direction.getRequiredHoursPerWeek() + " 小时";
        }

        double goalScore = goalRelevance(goals, direction);
        double difficultyScore = difficultyFit(direction.getDifficulty(), skillScore);
        double preparationScore = clamp(1.0 - (direction.getPreparationMonths() - 2) / 12.0);

        double total = skillScore * properties.getSkillWeight()
                + timeFit * properties.getTimeWeight()
                + goalScore * properties.getGoalWeight()
                + difficultyScore * properties.getDifficultyWeight()
                + preparationScore * properties.getPreparationWeight();

        String classification;
        if (!sufficient) {
            classification = "INSUFFICIENT_INFO";
        } else if (total >= properties.getSuitableThreshold()) {
            classification = "SUITABLE";
        } else if (total >= properties.getPartialThreshold()) {
            classification = "PARTIALLY_SUITABLE";
        } else {
            classification = "NOT_RECOMMENDED";
        }

        DirectionRecommendationDto dto = new DirectionRecommendationDto();
        dto.setDirectionId(direction.getId());
        dto.setName(direction.getName());
        dto.setDescription(direction.getDescription());
        dto.setScore(round(total));
        dto.setClassification(classification);
        dto.setMatchedTags(matched);
        dto.setMissingTags(missing);
        dto.setUserTags(userTags);
        dto.setAvailableHoursPerWeek(hours.isPresent() ? round(hours.getAsDouble()) : null);
        dto.setRequiredHoursPerWeek(direction.getRequiredHoursPerWeek());
        dto.setTimeFitScore(round(timeFit));
        dto.setTimeNote(timeNote);
        dto.setGoalRelevance(round(goalScore));
        dto.setDifficulty(direction.getDifficulty());
        dto.setPreparationMonths(direction.getPreparationMonths());
        dto.setResourceIds(direction.getResourceIds());
        dto.setScoringVersion(properties.getScoringVersion());
        dto.setExplanationPromptVersion(promptCatalog.version(PromptId.DIRECTION_EXPLANATION));
        dto.setInformationSufficient(sufficient);
        dto.setFollowUpQuestions(followUp);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("skillMatch", round(skillScore));
        breakdown.put("timeFit", round(timeFit));
        breakdown.put("goalRelevance", round(goalScore));
        breakdown.put("difficultyFit", round(difficultyScore));
        breakdown.put("preparationFit", round(preparationScore));
        breakdown.put("weights", Map.of(
                "skillMatch", properties.getSkillWeight(),
                "timeFit", properties.getTimeWeight(),
                "goalRelevance", properties.getGoalWeight(),
                "difficultyFit", properties.getDifficultyWeight(),
                "preparationFit", properties.getPreparationWeight()));
        breakdown.put("total", round(total));
        breakdown.put("scoringVersion", properties.getScoringVersion());
        dto.setScoreBreakdown(breakdown);
        return dto;
    }

    private double goalRelevance(List<String> goals, GrowthDirection direction) {
        if (goals == null || goals.isEmpty()) {
            return 0.0;
        }
        for (String goal : goals) {
            String lower = goal.toLowerCase(Locale.ROOT);
            for (String keyword : direction.getKeywords()) {
                if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return 1.0;
                }
            }
            if (lower.contains(direction.getName().toLowerCase(Locale.ROOT))) {
                return 1.0;
            }
            for (String tag : direction.getTargetTags()) {
                if (lower.contains(tag.toLowerCase(Locale.ROOT))) {
                    return 0.8;
                }
            }
        }
        return 0.0;
    }

    private double difficultyFit(String difficulty, double skillScore) {
        String value = difficulty == null ? "medium" : difficulty.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "easy" -> clamp(0.7 + 0.3 * skillScore);
            case "hard" -> clamp(0.2 + 0.7 * skillScore);
            default -> clamp(0.4 + 0.6 * skillScore);
        };
    }

    private List<ResourceSummaryDto> resourcesTeaching(String tag, GrowthDirection direction) {
        List<ResourceSummaryDto> result = new ArrayList<>();
        for (String resourceId : direction.getResourceIds()) {
            Resource resource = resourceRepository.findByResourceId(resourceId);
            if (resource == null) {
                continue;
            }
            if (stringArray(resource.getTeachesSkillsJson()).contains(tag)) {
                result.add(toSummary(resource));
            }
        }
        if (result.isEmpty()) {
            for (Resource resource : resourceRepository.findByType("course")) {
                if (stringArray(resource.getTeachesSkillsJson()).contains(tag)) {
                    result.add(toSummary(resource));
                }
            }
        }
        return result;
    }

    private ResourceSummaryDto toSummary(Resource resource) {
        ResourceSummaryDto dto = new ResourceSummaryDto();
        dto.setResourceId(resource.getResourceId());
        dto.setName(resource.getName());
        dto.setType(resource.getType());
        dto.setDifficulty(resource.getDifficulty());
        dto.setPreparationPeriod(resource.getPreparationPeriod());
        dto.setDataQuality(resource.getDataQuality());
        dto.setSourceUrl(resource.getSourceUrl());
        dto.setTeachesSkills(stringArray(resource.getTeachesSkillsJson()));
        dto.setRequiredSkills(normalizationService.expandRequiredSkills(resource.getRequiredSkillsJson()));
        return dto;
    }

    private List<String> followUpQuestions(UserProfile profile, List<String> userTags,
                                           OptionalDouble hours, List<String> goals) {
        List<String> questions = new ArrayList<>();
        if (profile == null) {
            questions.add("请先填写画像：专业、年级、技能和每周可投入时间。");
            return questions;
        }
        if (userTags.isEmpty()) {
            questions.add("你的技能列表为空或无法归一，请补充至少一项技能（如 Python、数据分析）。");
        }
        if (hours.isEmpty()) {
            questions.add("请补充每周可投入时间（例如：6 hours/week）。");
        }
        if (goals.isEmpty()) {
            questions.add("请补充你的成长目标，便于判断方向相关性。");
        }
        return questions;
    }

    private List<String> stringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    values.add(item.asText().trim());
                }
            }
            return new ArrayList<>(values);
        } catch (Exception e) {
            return List.of();
        }
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String trimNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(round(value));
    }
}
