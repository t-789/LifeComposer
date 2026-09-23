package org.example.lifecomposer.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lifecomposer.Entity.CapabilityReference;
import org.example.lifecomposer.Entity.CapabilityTag;
import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.example.lifecomposer.Repository.CapabilityTagRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * v0.1 M4: deterministic capability normalisation and template expansion.
 *
 * The LLM never decides tag equivalence: every mapping here comes from
 * capability_reference (skill_mapping / tags_to_merge / skill_profiles /
 * role_profiles) plus capability_tags aliases.
 */
@Service
public class CapabilityNormalizationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CapabilityTagRepository capabilityTagRepository;
    private final CapabilityReferenceRepository capabilityReferenceRepository;

    public CapabilityNormalizationService(CapabilityTagRepository capabilityTagRepository,
                                          CapabilityReferenceRepository capabilityReferenceRepository) {
        this.capabilityTagRepository = capabilityTagRepository;
        this.capabilityReferenceRepository = capabilityReferenceRepository;
    }

    public record CapabilityGap(List<String> userTags, List<String> requiredTags,
                                List<String> matchedTags, List<String> missingTags) {
    }

    public List<String> standardTags() {
        return capabilityTagRepository.findAll().stream().map(CapabilityTag::getName).toList();
    }

    /** Map arbitrary student input (e.g. "Python基础") to standard capability tags. */
    public List<String> normalizeSkills(Collection<String> rawSkills) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return List.of();
        }
        Dictionary dictionary = dictionary();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : rawSkills) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            normalized.addAll(normalizeOne(raw.trim(), dictionary));
        }
        return new ArrayList<>(normalized);
    }

    private List<String> normalizeOne(String raw, Dictionary dictionary) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw.isEmpty()) {
            return List.of();
        }
        String lower = raw.toLowerCase(Locale.ROOT);

        String exactTag = dictionary.tagsByLower().get(lower);
        if (exactTag != null) {
            result.add(exactTag);
            return new ArrayList<>(result);
        }

        List<String> mapped = dictionary.skillMapping().get(lower);
        if (mapped != null) {
            result.addAll(mapped);
            return new ArrayList<>(result);
        }

        String mergeTarget = dictionary.tagsToMerge().get(lower);
        if (mergeTarget != null) {
            for (String tag : dictionary.standardTags()) {
                if (mergeTarget.contains(tag)) {
                    result.add(tag);
                }
            }
            if (!result.isEmpty()) {
                return new ArrayList<>(result);
            }
        }

        // Natural-language variants ("数据库基础" vs "数据库") fall back to
        // longest key containment before alias matching.
        for (Map.Entry<String, List<String>> entry : dictionary.skillMapping().entrySet()) {
            String key = entry.getKey();
            if (key.length() >= 2 && (lower.contains(key) || key.contains(lower))) {
                result.addAll(entry.getValue());
            }
        }
        if (!result.isEmpty()) {
            return new ArrayList<>(result);
        }

        for (Map.Entry<String, String> entry : dictionary.tagsToMerge().entrySet()) {
            String key = entry.getKey();
            if (key.length() >= 2 && (lower.contains(key) || key.contains(lower))) {
                for (String tag : dictionary.standardTags()) {
                    if (entry.getValue().contains(tag)) {
                        result.add(tag);
                    }
                }
            }
        }
        if (!result.isEmpty()) {
            return new ArrayList<>(result);
        }

        List<String> aliasTags = dictionary.aliasesByLower().get(lower);
        if (aliasTags != null) {
            result.addAll(aliasTags);
            return new ArrayList<>(result);
        }

        for (Map.Entry<String, List<String>> entry : dictionary.aliasesByLower().entrySet()) {
            String alias = entry.getKey();
            if (alias.length() >= 2 && (lower.contains(alias) || alias.contains(lower))) {
                result.addAll(entry.getValue());
            }
        }
        if (!result.isEmpty()) {
            return new ArrayList<>(result);
        }

        for (String tag : dictionary.standardTags()) {
            if (lower.contains(tag.toLowerCase(Locale.ROOT))) {
                result.add(tag);
            }
        }
        return new ArrayList<>(result);
    }

    /** Expand resources.required_skills_json, including profile references and extra/exclude. */
    public List<String> expandRequiredSkills(String requiredSkillsJson) {
        if (requiredSkillsJson == null || requiredSkillsJson.isBlank()) {
            return List.of();
        }
        JsonNode node = readTree("required_skills_json", requiredSkillsJson);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
            return new ArrayList<>(result);
        }
        if (!node.isObject()) {
            return List.of();
        }
        if (node.hasNonNull("profile")) {
            result.addAll(expandProfile("skill_profiles", node.get("profile").asText()));
        }
        if (node.has("skills") && node.get("skills").isArray()) {
            for (JsonNode item : node.get("skills")) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
        }
        if (node.has("extra") && node.get("extra").isArray()) {
            for (JsonNode item : node.get("extra")) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
        }
        if (node.has("exclude") && node.get("exclude").isArray()) {
            for (JsonNode item : node.get("exclude")) {
                result.remove(item.asText());
            }
        }
        return new ArrayList<>(result);
    }

    /** Expand resources.team_roles_json from the role_profiles dictionary. */
    public List<String> expandRoleProfile(String roleJson) {
        if (roleJson == null || roleJson.isBlank()) {
            return List.of();
        }
        JsonNode node = readTree("team_roles_json", roleJson);
        if (node.isArray()) {
            List<String> roles = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    roles.add(item.asText());
                }
            }
            return roles;
        }
        if (node.isObject() && node.hasNonNull("profile")) {
            return expandProfile("role_profiles", node.get("profile").asText());
        }
        return List.of();
    }

    private List<String> expandProfile(String section, String profileKey) {
        CapabilityReference reference = capabilityReferenceRepository.findBySectionAndKey(section, profileKey);
        if (reference == null) {
            return List.of();
        }
        JsonNode value = readTree(section, reference.getRefValue());
        if (value.isArray()) {
            List<String> result = new ArrayList<>();
            for (JsonNode item : value) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
            return result;
        }
        if (value.isObject() && value.has("skills") && value.get("skills").isArray()) {
            List<String> result = new ArrayList<>();
            for (JsonNode item : value.get("skills")) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
            return result;
        }
        return List.of();
    }

    /** Difference between a profile's standard tags and a resource's expanded requirements. */
    public CapabilityGap gap(Collection<String> userSkills, String requiredSkillsJson) {
        List<String> userTags = normalizeSkills(userSkills);
        List<String> requiredTags = expandRequiredSkills(requiredSkillsJson);
        List<String> matched = requiredTags.stream().filter(userTags::contains).toList();
        List<String> missing = requiredTags.stream().filter(tag -> !userTags.contains(tag)).toList();
        return new CapabilityGap(userTags, requiredTags, matched, missing);
    }

    private JsonNode readTree(String field, String value) {
        try {
            JsonNode node = MAPPER.readTree(value);
            return node == null ? MAPPER.createArrayNode() : node;
        } catch (Exception e) {
            throw new IllegalStateException(field + " 不是合法 JSON: " + e.getMessage(), e);
        }
    }

    private Dictionary dictionary() {
        List<CapabilityTag> tags = capabilityTagRepository.findAll();
        List<String> standard = tags.stream().map(CapabilityTag::getName).toList();
        Map<String, String> tagsByLower = new LinkedHashMap<>();
        Map<String, List<String>> aliasesByLower = new LinkedHashMap<>();
        for (CapabilityTag tag : tags) {
            tagsByLower.put(tag.getName().toLowerCase(Locale.ROOT), tag.getName());
            if (tag.getSkillAliasesJson() == null || tag.getSkillAliasesJson().isBlank()) {
                continue;
            }
            JsonNode aliases = readTree("skill_aliases_json", tag.getSkillAliasesJson());
            if (aliases.isArray()) {
                for (JsonNode alias : aliases) {
                    if (alias.isTextual() && !alias.asText().isBlank()) {
                        aliasesByLower.computeIfAbsent(alias.asText().trim().toLowerCase(Locale.ROOT),
                                key -> new ArrayList<>()).add(tag.getName());
                    }
                }
            }
        }

        Map<String, List<String>> skillMapping = new LinkedHashMap<>();
        for (CapabilityReference reference : capabilityReferenceRepository.findBySection("skill_mapping")) {
            skillMapping.put(reference.getRefKey().toLowerCase(Locale.ROOT), stringArray(reference.getRefValue()));
        }

        Map<String, String> tagsToMerge = new LinkedHashMap<>();
        for (CapabilityReference reference : capabilityReferenceRepository.findBySection("tags_to_merge")) {
            tagsToMerge.put(reference.getRefKey().toLowerCase(Locale.ROOT), reference.getRefValue());
        }

        return new Dictionary(standard, tagsByLower, aliasesByLower, skillMapping, tagsToMerge);
    }

    private List<String> stringArray(String json) {
        JsonNode node = readTree("ref_value", json);
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private record Dictionary(List<String> standardTags,
                              Map<String, String> tagsByLower,
                              Map<String, List<String>> aliasesByLower,
                              Map<String, List<String>> skillMapping,
                              Map<String, String> tagsToMerge) {
    }
}
