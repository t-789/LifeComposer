package org.example.lifecomposer.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.lifecomposer.Exception.ProfileApiException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic profile-field rules. Prompts may extract values, but only this
 * class decides what a valid profile field is and how arrays/objects are
 * normalised. Used by both the formal PUT endpoint and the chat candidate flow.
 */
@Service
public class ProfileFieldValidator {

    public static final Set<String> EDITABLE_FIELDS = Set.of(
            "college", "major", "grade", "studentId",
            "skillsJson", "interestsJson", "experiencesJson", "preferencesJson",
            "availableTime", "goals");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TEXT_LENGTH = 2000;

    private static final Set<String> STRING_ARRAY_FIELDS = Set.of("skillsJson", "interestsJson", "goals");

    /**
     * Validates and normalises one field value. {@code rejectDuplicates} is true
     * for the formal form (duplicate entries are a client mistake) and false for
     * chat candidates (the merge step is idempotent anyway).
     */
    public String validateAndNormalize(String field, String rawValue, boolean rejectDuplicates) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (STRING_ARRAY_FIELDS.contains(field)) {
            return normalizeStringArray(field, value, rejectDuplicates);
        }
        if ("experiencesJson".equals(field)) {
            return normalizeExperiences(value, rejectDuplicates);
        }
        if ("preferencesJson".equals(field)) {
            return normalizeObject(field, value);
        }
        if ("studentId".equals(field)) {
            if (value.length() > 40) {
                throw new ProfileApiException("INVALID_FIELD_VALUE", "学号长度超过限制");
            }
            return value;
        }
        if ("availableTime".equals(field)) {
            if (value.length() > 200) {
                throw new ProfileApiException("INVALID_FIELD_VALUE", "可投入时间描述过长");
            }
            return value;
        }
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new ProfileApiException("INVALID_FIELD_VALUE", field + " 内容过长");
        }
        return value;
    }

    private String normalizeStringArray(String field, String value, boolean rejectDuplicates) {
        JsonNode node = readTree(field, value);
        if (!node.isArray()) {
            throw new ProfileApiException("INVALID_PROFILE_JSON", field + " 必须是 JSON 数组");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> items = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new ProfileApiException("INVALID_PROFILE_JSON", field + " 的元素必须是字符串");
            }
            String text = item.asText().trim();
            if (text.isEmpty()) {
                throw new ProfileApiException("INVALID_FIELD_VALUE", field + " 不允许空字符串元素");
            }
            if (!seen.add(text) && rejectDuplicates) {
                throw new ProfileApiException("DUPLICATE_FIELD_VALUE", field + " 存在重复值: " + text);
            }
            if (!items.contains(text)) {
                items.add(text);
            }
        }
        return writeJson(items);
    }

    private String normalizeExperiences(String value, boolean rejectDuplicates) {
        JsonNode node = readTree("experiencesJson", value);
        if (!node.isArray()) {
            throw new ProfileApiException("INVALID_PROFILE_JSON", "experiencesJson 必须是 JSON 数组");
        }
        Map<String, JsonNode> unique = new LinkedHashMap<>();
        for (JsonNode item : node) {
            if (!item.isTextual() && !item.isObject()) {
                throw new ProfileApiException("INVALID_PROFILE_JSON", "experiencesJson 的元素必须是字符串或对象");
            }
            String key = item.toString();
            if (unique.containsKey(key)) {
                if (rejectDuplicates) {
                    throw new ProfileApiException("DUPLICATE_FIELD_VALUE", "experiencesJson 存在重复经历");
                }
                continue;
            }
            unique.put(key, item);
        }
        ArrayNode array = MAPPER.createArrayNode();
        unique.values().forEach(array::add);
        return writeJson(array);
    }

    private String normalizeObject(String field, String value) {
        JsonNode node = readTree(field, value);
        if (!node.isObject()) {
            throw new ProfileApiException("INVALID_PROFILE_JSON", field + " 必须是 JSON 对象");
        }
        return writeJson(node);
    }

    private JsonNode readTree(String field, String value) {
        try {
            return MAPPER.readTree(value);
        } catch (JsonProcessingException e) {
            throw new ProfileApiException("INVALID_PROFILE_JSON", field + " 不是合法 JSON");
        }
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ProfileApiException("INVALID_PROFILE_JSON", "画像字段序列化失败");
        }
    }

    /** Union-merge for confirmed chat candidates: existing items first, deduplicated. */
    public String mergeConfirmedValue(String field, String currentValue, String confirmedValue) {
        if ("availableTime".equals(field) || "college".equals(field) || "major".equals(field)
                || "grade".equals(field) || "studentId".equals(field)) {
            return confirmedValue;
        }
        if ("preferencesJson".equals(field)) {
            ObjectNode merged = MAPPER.createObjectNode();
            if (currentValue != null && !currentValue.isBlank()) {
                JsonNode current = readTree(field, currentValue);
                if (current.isObject()) {
                    merged.setAll((ObjectNode) current);
                }
            }
            if (confirmedValue != null && !confirmedValue.isBlank()) {
                JsonNode incoming = readTree(field, confirmedValue);
                if (incoming.isObject()) {
                    merged.setAll((ObjectNode) incoming);
                }
            }
            return writeJson(merged);
        }
        if (STRING_ARRAY_FIELDS.contains(field)) {
            return mergeStringArrays(field, currentValue, confirmedValue);
        }
        if ("experiencesJson".equals(field)) {
            return mergeExperienceArrays(currentValue, confirmedValue);
        }
        return confirmedValue;
    }

    private String mergeStringArrays(String field, String currentValue, String confirmedValue) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (currentValue != null && !currentValue.isBlank()) {
            JsonNode current = readTree(field, currentValue);
            if (current.isArray()) {
                for (JsonNode item : current) {
                    if (item.isTextual() && !item.asText().trim().isEmpty()) {
                        merged.add(item.asText().trim());
                    }
                }
            }
        }
        if (confirmedValue != null && !confirmedValue.isBlank()) {
            JsonNode incoming = readTree(field, confirmedValue);
            if (incoming.isArray()) {
                for (JsonNode item : incoming) {
                    if (item.isTextual() && !item.asText().trim().isEmpty()) {
                        merged.add(item.asText().trim());
                    }
                }
            }
        }
        return writeJson(new ArrayList<>(merged));
    }

    private String mergeExperienceArrays(String currentValue, String confirmedValue) {
        Map<String, JsonNode> merged = new LinkedHashMap<>();
        if (currentValue != null && !currentValue.isBlank()) {
            JsonNode current = readTree("experiencesJson", currentValue);
            if (current.isArray()) {
                for (JsonNode item : current) {
                    merged.put(item.toString(), item);
                }
            }
        }
        if (confirmedValue != null && !confirmedValue.isBlank()) {
            JsonNode incoming = readTree("experiencesJson", confirmedValue);
            if (incoming.isArray()) {
                for (JsonNode item : incoming) {
                    merged.put(item.toString(), item);
                }
            }
        }
        ArrayNode array = MAPPER.createArrayNode();
        merged.values().forEach(array::add);
        return writeJson(array);
    }
}
