package org.example.lifecomposer.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lifecomposer.Entity.UserCapabilityState;
import org.example.lifecomposer.Repository.UserCapabilityStateRepository;
import org.example.lifecomposer.dto.CapabilityStateDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Records where each capability came from and exposes it for the debug panel.
 *
 * <p>The persisted state is a projection of the current profile: rows whose tag
 * is no longer produced by the profile are deleted, so a cleared skill cannot
 * keep appearing in {@code /api/profiles/me/capabilities}.
 */
@Service
public class CapabilityStateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserCapabilityStateRepository repository;
    private final CapabilityNormalizationService normalizationService;

    public CapabilityStateService(UserCapabilityStateRepository repository,
                                  CapabilityNormalizationService normalizationService) {
        this.repository = repository;
        this.normalizationService = normalizationService;
    }

    public void recordSkills(Long userId, String skillsJson, String evidenceJson, String source) {
        if (userId == null) {
            return;
        }
        // Blank / missing skills means the profile no longer claims any skill:
        // clear the projection instead of leaving stale tags behind.
        if (skillsJson == null || skillsJson.isBlank()) {
            repository.deleteByUserIdAndTagNotIn(userId, List.of());
            return;
        }
        List<String> rawSkills = rawSkillArray(skillsJson);
        List<String> tags = normalizationService.normalizeSkills(rawSkills);
        if (tags.isEmpty()) {
            repository.deleteByUserIdAndTagNotIn(userId, List.of());
            return;
        }

        String normalizedEvidence = normalizeEvidence(evidenceJson);
        for (String tag : tags) {
            UserCapabilityState state = new UserCapabilityState();
            state.setUserId(userId);
            state.setTagName(tag);
            state.setLevel("L1");
            state.setEvidenceJson(normalizedEvidence);
            state.setSource(source);
            repository.upsert(state);
        }
        repository.deleteByUserIdAndTagNotIn(userId, tags);
    }

    public List<CapabilityStateDto> statesFor(Long userId) {
        List<CapabilityStateDto> result = new ArrayList<>();
        for (UserCapabilityState state : repository.findByUserId(userId)) {
            CapabilityStateDto dto = new CapabilityStateDto();
            dto.setTag(state.getTagName());
            dto.setLevel(state.getLevel());
            dto.setEvidence(evidenceList(state.getEvidenceJson()));
            dto.setSource(state.getSource());
            dto.setConfidence(state.getConfidence());
            dto.setUpdatedAt(state.getUpdatedAt());
            result.add(dto);
        }
        return result;
    }

    /** Only textual array items count as raw profile skills. */
    private List<String> rawSkillArray(String json) {
        JsonNode node = readTree(json);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        }
        return new ArrayList<>(values);
    }

    /**
     * Experience arrays may contain objects; store a readable string list so the
     * evidence field is never silently lost.
     */
    private String normalizeEvidence(String experiencesJson) {
        if (experiencesJson == null || experiencesJson.isBlank()) {
            return "[]";
        }
        JsonNode node = readTree(experiencesJson);
        if (node == null || !node.isArray()) {
            return "[]";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            } else if (item.isObject()) {
                String label = evidenceLabel(item);
                if (!label.isBlank()) {
                    values.add(label);
                }
            }
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String evidenceLabel(JsonNode item) {
        String name = item.path("name").asText("");
        String type = item.path("type").asText("");
        if (!name.isBlank() && !type.isBlank()) {
            return type + "：" + name;
        }
        if (!name.isBlank()) {
            return name;
        }
        String title = item.path("title").asText("");
        if (!title.isBlank()) {
            return title;
        }
        return item.toString();
    }

    private List<String> evidenceList(String json) {
        JsonNode node = readTree(json);
        if (node == null) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    values.add(item.asText());
                } else if (item.isObject()) {
                    String label = evidenceLabel(item);
                    if (!label.isBlank()) {
                        values.add(label);
                    }
                }
            }
            return values;
        }
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        return List.of();
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
