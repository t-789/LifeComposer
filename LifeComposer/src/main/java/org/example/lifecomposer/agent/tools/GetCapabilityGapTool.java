package org.example.lifecomposer.agent.tools;

import com.google.gson.JsonObject;
import org.example.lifecomposer.Service.CapabilityNormalizationService;
import org.example.lifecomposer.agent.AgentTool;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.AgentToolSupport;
import org.example.lifecomposer.agent.ToolResult;
import org.example.lifecomposer.dto.DirectionRecommendationDto;
import org.example.lifecomposer.recommendation.RecommendationService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** v0.1 M6: capability gap for a direction (or the top recommendation). */
@Component
public class GetCapabilityGapTool implements AgentTool {

    private final RecommendationService recommendationService;

    public GetCapabilityGapTool(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Override
    public String name() {
        return "get_capability_gap";
    }

    @Override
    public String description() {
        return "查看当前用户在某个成长方向上已经具备和仍然缺失的标准能力标签。"
                + "返回中文键名，回答时不要输出 userTags/missingTags 等内部字段名。"
                + "不传 directionId 时使用当前评分最高的方向。";
    }

    @Override
    public String displayDescription() {
        return "计算能力差集";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "directionId", Map.of("type", "string", "description", "成长方向 id，可选")),
                "additionalProperties", false);
    }

    @Override
    public ToolResult execute(AgentToolContext context, JsonObject arguments) {
        Long userId = context.userId().longValue();
        String directionId = AgentToolSupport.stringArg(arguments, "directionId");
        if (directionId == null) {
            List<DirectionRecommendationDto> recommendations = recommendationService.recommend(userId);
            if (recommendations.isEmpty()) {
                return ToolResult.error("NO_RECOMMENDATION", "当前没有可用的成长方向");
            }
            directionId = recommendations.get(0).getDirectionId();
        }
        CapabilityNormalizationService.CapabilityGap gap = recommendationService.gap(userId, directionId);
        if (gap == null) {
            return ToolResult.error("DIRECTION_NOT_FOUND", "成长方向不存在: " + directionId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("directionId", directionId);
        data.put("你的能力标签", gap.userTags());
        data.put("方向要求标签", gap.requiredTags());
        data.put("已匹配标签", gap.matchedTags());
        data.put("仍缺少标签", gap.missingTags());
        data.put("displayInstruction", "用中文标签向用户说明已具备与仍缺少的能力；不要输出 userTags/missingTags 等字段名。");
        return ToolResult.ok(data);
    }
}
