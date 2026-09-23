package org.example.lifecomposer.agent.tools;

import com.google.gson.JsonObject;
import org.example.lifecomposer.agent.AgentTool;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.ToolResult;
import org.example.lifecomposer.dto.DirectionRecommendationDto;
import org.example.lifecomposer.recommendation.RecommendationService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** v0.1 M6: read-only direction recommendations for the agent. */
@Component
public class ListGrowthDirectionsTool implements AgentTool {

    private final RecommendationService recommendationService;

    public ListGrowthDirectionsTool(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Override
    public String name() {
        return "list_growth_directions";
    }

    @Override
    public String description() {
        return "返回当前用户可考虑的成长方向候选，包含确定性评分、匹配/缺失能力标签、时间依据和评分明细。"
                + "解释推荐时必须引用这些字段，不要自行发明方向或分数。";
    }

    @Override
    public String displayDescription() {
        return "计算成长方向推荐";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }

    @Override
    public ToolResult execute(AgentToolContext context, JsonObject arguments) {
        List<DirectionRecommendationDto> recommendations =
                recommendationService.recommend(context.userId().longValue());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", recommendations.size());
        data.put("scoringVersion", recommendations.isEmpty() ? null : recommendations.get(0).getScoringVersion());
        data.put("recommendations", recommendations);
        return ToolResult.ok(data);
    }
}
