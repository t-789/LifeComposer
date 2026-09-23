package org.example.lifecomposer.agent.tools;

import com.google.gson.JsonObject;
import org.example.lifecomposer.agent.AgentTool;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.AgentToolSupport;
import org.example.lifecomposer.agent.ToolResult;
import org.example.lifecomposer.dto.PathPlanDto;
import org.example.lifecomposer.recommendation.RecommendationService;
import org.springframework.stereotype.Component;

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
                + "转述时只能使用返回的阶段和资源，不要新增未给出的资源。";
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
        return ToolResult.ok(plan);
    }
}
