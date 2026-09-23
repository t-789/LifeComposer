package org.example.lifecomposer.agent.tools;

import com.google.gson.JsonObject;
import org.example.lifecomposer.agent.AgentTool;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.AgentToolSupport;
import org.example.lifecomposer.agent.ToolResult;
import org.example.lifecomposer.dto.PathPlanDto;
import org.example.lifecomposer.dto.ResourceSummaryDto;
import org.example.lifecomposer.recommendation.RecommendationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** v0.1 M6: staged path plan for one direction. */
@Component
public class GetPathPlanTool implements AgentTool {

    private final RecommendationService recommendationService;

    public GetPathPlanTool(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Override
    public String name() {
        return "get_path_plan";
    }

    @Override
    public String description() {
        return "返回某个成长方向的阶段化路径：当前基础、补足任务、实践任务、资源和预期投入。"
                + "转述时只能使用返回的阶段和资源，不要新增未给出的资源；不要输出 pathPlan/dataQuality 等内部字段名。";
    }

    @Override
    public String displayDescription() {
        return "生成路径计划";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "directionId", Map.of("type", "string", "description", "成长方向 id")),
                "required", java.util.List.of("directionId"),
                "additionalProperties", false);
    }

    @Override
    public ToolResult execute(AgentToolContext context, JsonObject arguments) {
        String directionId = AgentToolSupport.stringArg(arguments, "directionId");
        if (directionId == null) {
            return ToolResult.error("INVALID_ARGUMENTS", "directionId 不能为空");
        }
        PathPlanDto plan = recommendationService.path(context.userId().longValue(), directionId);
        if (plan == null) {
            return ToolResult.error("DIRECTION_NOT_FOUND", "成长方向不存在: " + directionId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("directionId", plan.getDirectionId());
        data.put("name", plan.getName());
        data.put("description", plan.getDescription());
        data.put("currentLevel", plan.getCurrentLevel());
        data.put("matchedTags", plan.getMatchedTags());
        data.put("missingTags", plan.getMissingTags());
        data.put("expectedInvestment", plan.getExpectedInvestment());
        data.put("informationSufficient", plan.isInformationSufficient());
        data.put("followUpQuestions", plan.getFollowUpQuestions());
        data.put("gapTasks", plan.getGapTasks().stream().map(this::gapTask).toList());
        data.put("practiceTasks", plan.getPracticeTasks().stream().map(this::practiceTask).toList());
        data.put("resources", plan.getResources().stream().map(this::resource).toList());
        data.put("displayInstruction",
                "用中文描述当前基础、补足任务、实践任务、推荐资源和预期投入；"
                        + "数据质量待复核时提示“需要人工复核”；不要输出 pathPlan/dataQuality 等字段名。");
        return ToolResult.ok(data);
    }

    private Map<String, Object> gapTask(PathPlanDto.GapTask task) {
        List<Map<String, Object>> suggested = new ArrayList<>();
        for (ResourceSummaryDto resource : task.getSuggestedResources()) {
            suggested.add(resource(resource));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tag", task.getTag());
        map.put("action", task.getAction());
        map.put("suggestedResources", suggested);
        return map;
    }

    private Map<String, Object> practiceTask(PathPlanDto.PracticeTask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resourceId", task.getResourceId());
        map.put("name", task.getName());
        map.put("note", task.getNote());
        return map;
    }

    private Map<String, Object> resource(ResourceSummaryDto dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resourceId", dto.getResourceId());
        map.put("name", dto.getName());
        map.put("type", dto.getType());
        map.put("difficulty", dto.getDifficulty());
        map.put("preparationPeriod", dto.getPreparationPeriod());
        map.put("sourceUrl", dto.getSourceUrl());
        map.put("teachesSkills", dto.getTeachesSkills());
        map.put("requiredSkills", dto.getRequiredSkills());
        map.put("数据质量", qualityLabel(dto.getDataQuality()));
        return map;
    }

    private String qualityLabel(String dataQuality) {
        if (dataQuality == null) {
            return "未知";
        }
        return switch (dataQuality) {
            case "complete" -> "完整";
            case "partial" -> "部分字段未确认";
            case "needs_review" -> "待人工复核";
            default -> dataQuality;
        };
    }
}
