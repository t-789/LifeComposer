package org.example.lifecomposer.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import org.example.lifecomposer.Entity.Resource;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.example.lifecomposer.agent.AgentTool;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.AgentToolSupport;
import org.example.lifecomposer.agent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keyword/type/difficulty/major search over the curated resource library. */
@Component
public class SearchResourcesTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ResourceRepository resourceRepository;

    public SearchResourcesTool(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Override
    public String name() {
        return "search_resources";
    }

    @Override
    public String description() {
        return "按关键词、资源类型（competition/course）、难度（easy/medium/hard）、专业大类筛选竞赛和课程资源。";
    }

    @Override
    public String displayDescription() {
        return "检索成长资源库";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "关键词，匹配资源名称或描述"));
        properties.put("type", Map.of("type", "string", "enum", List.of("competition", "course")));
        properties.put("difficulty", Map.of("type", "string", "enum", List.of("easy", "medium", "hard")));
        properties.put("majorCategory", Map.of("type", "string", "description", "专业大类，如 工科/理科/文科"));
        return Map.of("type", "object", "properties", properties, "additionalProperties", false);
    }

    @Override
    public ToolResult execute(AgentToolContext context, JsonObject arguments) {
        String keyword = AgentToolSupport.stringArg(arguments, "keyword");
        String type = AgentToolSupport.stringArg(arguments, "type");
        String difficulty = AgentToolSupport.stringArg(arguments, "difficulty");
        String majorCategory = AgentToolSupport.stringArg(arguments, "majorCategory");
        List<Resource> resources = resourceRepository.search(keyword, type, difficulty, majorCategory);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Resource resource : resources) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", resource.getResourceId());
            item.put("name", resource.getName());
            item.put("type", resource.getType());
            item.put("difficulty", resource.getDifficulty());
            item.put("description", resource.getDescription());
            item.put("targetMajors", parseJson(resource.getTargetMajorsJson()));
            item.put("requiredSkills", parseJson(resource.getRequiredSkillsJson()));
            item.put("bonusPoint", parseJson(resource.getBonusPointJson()));
            item.put("sourceUrl", resource.getSourceUrl());
            items.add(item);
        }
        return ToolResult.ok(Map.of("count", items.size(), "items", items));
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
