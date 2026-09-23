package org.example.lifecomposer.agent.tools;

import com.google.gson.JsonObject;
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

/** v0.1 M6: score breakdown behind one recommendation. */
@Component
public class GetRecommendationReasonsTool implements AgentTool {

    private final RecommendationService recommendationService;

    public GetRecommendationReasonsTool(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Override
    public String name() {
        return "get_recommendation_reasons";
    }

    @Override
    public String description() {
        return "返回某个方向推荐分数的可解释明细（技能匹配、时间匹配、目标相关性、难度、准备周期和权重）。"
                + "不传 directionId 时使用当前评分最高的方向。";
    }

    @Override
    public String displayDescription() {
        return "读取推荐评分明细";
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
        List<DirectionRecommendationDto> recommendations =
                recommendationService.recommend(context.userId().longValue());
        if (recommendations.isEmpty()) {
            return ToolResult.error("NO_RECOMMENDATION", "当前没有可用的成长方向");
        }
        String directionId = AgentToolSupport.stringArg(arguments, "directionId");
        DirectionRecommendationDto selected = directionId == null
                ? recommendations.get(0)
                : recommendations.stream()
                        .filter(dto -> dto.getDirectionId().equals(directionId))
                        .findFirst()
                        .orElse(null);
        if (selected == null) {
            return ToolResult.error("DIRECTION_NOT_FOUND", "成长方向不存在: " + directionId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("directionId", selected.getDirectionId());
        data.put("name", selected.getName());
        data.put("score", selected.getScore());
        data.put("classification", selected.getClassification());
        data.put("matchedTags", selected.getMatchedTags());
        data.put("missingTags", selected.getMissingTags());
        data.put("timeNote", selected.getTimeNote());
        Map<String, Object> hints = new LinkedHashMap<>();
        Map<String, Object> breakdown = selected.getScoreBreakdown();
        hints.put("技能匹配", breakdown.get("skillMatch"));
        hints.put("时间匹配", breakdown.get("timeFit"));
        hints.put("目标相关性", breakdown.get("goalRelevance"));
        hints.put("难度匹配", breakdown.get("difficultyFit"));
        hints.put("准备周期", breakdown.get("preparationFit"));
        data.put("explanationHints", hints);
        data.put("displayInstruction",
                "请使用 explanationHints 的中文维度向用户解释；禁止输出 scoreBreakdown、goalRelevance 等字段名。"
                        + "如果某个维度为 0 是因为用户信息缺失，请说明缺少什么并追问。");
        data.put("scoringVersion", selected.getScoringVersion());
        data.put("informationSufficient", selected.isInformationSufficient());
        data.put("followUpQuestions", selected.getFollowUpQuestions());
        return ToolResult.ok(data);
    }
}
