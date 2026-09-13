package org.example.lifecomposer.agent.tools;

import com.google.gson.JsonObject;
import org.example.lifecomposer.Entity.CollegeCreditRule;
import org.example.lifecomposer.Repository.CollegeCreditRuleRepository;
import org.example.lifecomposer.agent.AgentTool;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.AgentToolSupport;
import org.example.lifecomposer.agent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only query over the imported college credit rules. */
@Component
public class QueryCreditRulesTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final CollegeCreditRuleRepository collegeCreditRuleRepository;

    public QueryCreditRulesTool(CollegeCreditRuleRepository collegeCreditRuleRepository) {
        this.collegeCreditRuleRepository = collegeCreditRuleRepository;
    }

    @Override
    public String name() {
        return "query_credit_rules";
    }

    @Override
    public String description() {
        return "查询学院加分/保研加分的具体规则，可按学院、加分类型、类别、竞赛级别、奖项筛选。";
    }

    @Override
    public String displayDescription() {
        return "查询加分规则";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("college", Map.of("type", "string", "description", "学院名称，支持部分匹配"));
        properties.put("creditType", Map.of("type", "string", "enum", List.of("graduation", "recommendation")));
        properties.put("category", Map.of("type", "string", "description", "competition/lecture/course/project/paper/patent/sports/arts/veteran"));
        properties.put("compLevel", Map.of("type", "string", "description", "S/A+/A/B+/B/national/provincial/school"));
        properties.put("awardTier", Map.of("type", "string", "enum", List.of("first", "second", "third", "special", "participation")));
        properties.put("limit", Map.of("type", "integer", "description", "最多返回条数，默认 20，最大 50"));
        return Map.of("type", "object", "properties", properties, "additionalProperties", false);
    }

    @Override
    public ToolResult execute(AgentToolContext context, JsonObject arguments) {
        String college = AgentToolSupport.stringArg(arguments, "college");
        String creditType = AgentToolSupport.stringArg(arguments, "creditType");
        String category = AgentToolSupport.stringArg(arguments, "category");
        String compLevel = AgentToolSupport.stringArg(arguments, "compLevel");
        String awardTier = AgentToolSupport.stringArg(arguments, "awardTier");
        Integer limit = AgentToolSupport.intArg(arguments, "limit");
        if (limit == null || limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else {
            limit = Math.min(limit, MAX_LIMIT);
        }

        List<CollegeCreditRule> rules = collegeCreditRuleRepository.search(
                college, creditType, category, compLevel, awardTier, limit);
        List<Map<String, Object>> items = new ArrayList<>();
        for (CollegeCreditRule rule : rules) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rule.getId());
            item.put("college", rule.getCollege());
            item.put("creditType", rule.getCreditType());
            item.put("category", rule.getCategory());
            item.put("compLevel", rule.getCompLevel());
            item.put("compName", rule.getCompName());
            item.put("awardTier", rule.getAwardTier());
            item.put("credits", rule.getCredits());
            item.put("categoryCap", rule.getCategoryCap());
            item.put("teamFormula", rule.getTeamFormula());
            item.put("studentCohort", rule.getStudentCohort());
            item.put("notes", rule.getNotes());
            items.add(item);
        }
        return ToolResult.ok(Map.of("count", items.size(), "items", items));
    }
}
