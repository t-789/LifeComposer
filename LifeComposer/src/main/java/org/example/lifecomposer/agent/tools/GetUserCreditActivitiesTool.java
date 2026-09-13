package org.example.lifecomposer.agent.tools;

import com.google.gson.JsonObject;
import org.example.lifecomposer.Entity.CreditActivity;
import org.example.lifecomposer.Repository.CreditActivityRepository;
import org.example.lifecomposer.agent.AgentTool;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read the authenticated user's credit activities. */
@Component
public class GetUserCreditActivitiesTool implements AgentTool {

    private final CreditActivityRepository creditActivityRepository;

    public GetUserCreditActivitiesTool(CreditActivityRepository creditActivityRepository) {
        this.creditActivityRepository = creditActivityRepository;
    }

    @Override
    public String name() {
        return "get_user_credit_activities";
    }

    @Override
    public String description() {
        return "查询当前登录用户的加分记录（双创分/保研加分活动）。";
    }

    @Override
    public String displayDescription() {
        return "读取当前用户加分记录";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "additionalProperties", false);
    }

    @Override
    public ToolResult execute(AgentToolContext context, JsonObject arguments) {
        List<CreditActivity> activities = creditActivityRepository.findByUserId(context.userId().longValue());
        List<Map<String, Object>> items = new ArrayList<>();
        for (CreditActivity activity : activities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", activity.getId());
            item.put("creditType", activity.getCreditType());
            item.put("category", activity.getCategory());
            item.put("compName", activity.getCompName());
            item.put("compLevel", activity.getCompLevel());
            item.put("awardTier", activity.getAwardTier());
            item.put("credits", activity.getCredits());
            item.put("obtainedDate", activity.getObtainedDate());
            item.put("verified", activity.getVerified());
            item.put("notes", activity.getNotes());
            items.add(item);
        }
        return ToolResult.ok(Map.of("count", items.size(), "items", items));
    }
}
