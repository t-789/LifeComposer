package org.example.lifecomposer.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lifecomposer.Entity.Resource;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.1 M4 preflight report. It reads the sample student profiles and the
 * already-imported resource/dictionary rows, then records skill coverage,
 * unmatched skills and template-expansion results. It is deliberately a
 * production service so the report can be regenerated after a real
 * {@code import.sh} run, not only inside a test.
 */
@Service
public class CapabilityPreflightService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CapabilityNormalizationService normalizationService;
    private final ResourceRepository resourceRepository;

    public CapabilityPreflightService(CapabilityNormalizationService normalizationService,
                                      ResourceRepository resourceRepository) {
        this.normalizationService = normalizationService;
        this.resourceRepository = resourceRepository;
    }

    public Map<String, Object> preflight(Path studentProfilesPath) {
        List<String> standardTags = normalizationService.standardTags();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("standardTagCount", standardTags.size());

        List<Map<String, Object>> profiles = new ArrayList<>();
        List<String> unmatchedSkills = new ArrayList<>();
        int totalSkills = 0;
        int matchedSkills = 0;

        JsonNode root = readJson(studentProfilesPath);
        if (root != null && root.isArray()) {
            for (JsonNode profile : root) {
                List<String> rawSkills = new ArrayList<>();
                for (JsonNode skill : profile.path("skills")) {
                    if (skill.isTextual() && !skill.asText().isBlank()) {
                        rawSkills.add(skill.asText().trim());
                    }
                }
                List<String> normalized = normalizationService.normalizeSkills(rawSkills);
                List<String> profileUnmatched = new ArrayList<>();
                for (String raw : rawSkills) {
                    totalSkills++;
                    if (normalizationService.normalizeSkills(List.of(raw)).isEmpty()) {
                        profileUnmatched.add(raw);
                        unmatchedSkills.add(raw);
                    } else {
                        matchedSkills++;
                    }
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("major", profile.path("major").asText(null));
                entry.put("rawSkills", rawSkills);
                entry.put("normalizedTags", normalized);
                entry.put("unmatchedSkills", profileUnmatched);
                profiles.add(entry);
            }
        }

        report.put("profiles", profiles);
        report.put("totalSkillCount", totalSkills);
        report.put("matchedSkillCount", matchedSkills);
        report.put("skillCoverage", totalSkills == 0 ? 1.0 : (double) matchedSkills / totalSkills);
        report.put("unmatchedSkills", unmatchedSkills);

        List<Map<String, Object>> expansions = new ArrayList<>();
        List<String> unmatchedTemplateTags = new ArrayList<>();
        for (Resource resource : resourceRepository.findAll()) {
            String required = resource.getRequiredSkillsJson();
            if (required == null || !required.contains("profile")) {
                continue;
            }
            List<String> expanded = normalizationService.expandRequiredSkills(required);
            List<String> notStandard = expanded.stream().filter(tag -> !standardTags.contains(tag)).toList();
            unmatchedTemplateTags.addAll(notStandard);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("resourceId", resource.getResourceId());
            entry.put("name", resource.getName());
            entry.put("requiredSkillsJson", required);
            entry.put("expandedTags", expanded);
            entry.put("unmatchedTags", notStandard);
            expansions.add(entry);
        }
        report.put("templateExpansions", expansions);
        report.put("unmatchedTemplateTags", unmatchedTemplateTags);
        return report;
    }

    private JsonNode readJson(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return MAPPER.readTree(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("无法读取预检数据文件: " + path, e);
        }
    }
}
