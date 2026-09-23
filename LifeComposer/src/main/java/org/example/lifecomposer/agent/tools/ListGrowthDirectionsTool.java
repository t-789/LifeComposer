package org.example.lifecomposer.agent.tools;

import com.google.gson.JsonObject;
import org.example.lifecomposer.agent.AgentTool;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.ToolResult;
import org.example.lifecomposer.dto.DirectionRecommendationDto;
import org.example.lifecomposer.recommendation.RecommendationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        return "返回当前用户可考虑的成长方向候选，包含确定性评分、匹配/缺失能力标签、时间依据和中文评分维度。"
                + "解释推荐时必须引用这些字段，不要自行发明方向或分数；不要向用户输出英文字段名。";
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
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (DirectionRecommendationDto dto : recommendations) {
            sanitized.add(sanitize(dto));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", recommendations.size());
        data.put("scoringVersion", recommendations.isEmpty() ? null : recommendations.get(0).getScoringVersion());
        data.put("recommendations", sanitized);
        data.put("displayInstruction",
                "向用户解释时请使用中文维度名（技能匹配、时间匹配、目标相关性、难度、准备周期），"
                        + "禁止输出 scoreBreakdown、goalRelevance、skillMatch、matchedTags、missingTags 等字段名；"
                        + "维度为 0 若因信息缺失，请说明缺少什么并追问。");
        return ToolResult.ok(data);
    }

    /**
     * The agent must never see the raw English scoring keys (goalRelevance etc.),
     * otherwise it tends to echo them into user-facing answers.
     */
    private Map<String, Object> sanitize(DirectionRecommendationDto dto) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("directionId", dto.getDirectionId());
        item.put("name", dto.getName());
        item.put("description", dto.getDescription());
        item.put("score", dto.getScore());
        item.put("classification", dto.getClassification());
        item.put("matchedTags", dto.getMatchedTags());
        item.put("missingTags", dto.getMissingTags());
        item.put("timeNote", dto.getTimeNote());
        item.put("followUpQuestions", dto.getFollowUpQuestions());
        item.put("informationSufficient", dto.isInformationSufficient());
        Map<String, Object> breakdown = dto.getScoreBreakdown();
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("技能匹配", breakdown.get("skillMatch"));
        hints.put("时间匹配", breakdown.get("timeFit"));
        hints.put("目标相关性", breakdown.get("goalRelevance"));
        hints.put("难度匹配", breakdown.get("difficultyFit"));
        hints.put("准备周期", breakdown.get("preparationFit"));
        item.put("explanationHints", hints);
        return item;
    }
}
