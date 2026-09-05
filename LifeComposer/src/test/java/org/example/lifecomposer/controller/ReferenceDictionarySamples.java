package org.example.lifecomposer.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lifecomposer.Entity.CapabilityReference;
import org.example.lifecomposer.Entity.CapabilityTag;
import org.example.lifecomposer.Entity.RagChunk;
import org.example.lifecomposer.Entity.Resource;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 测试夹具：把 样例/data/resources.json、样例/rag/chunks.json、样例/draft/capability-tags.json
 * 解析成与数据库列一一对应的实体（sample-shaped rows），供 repository 往返测试使用。
 *
 * 说明：
 * - resources.json 中的 "id" 是数据侧业务键，映射到 resources.resource_id；
 *   嵌套字段（levels/stages/target_majors/required_skills/team_roles/bonus_point/
 *   teaches_skills/source_urls/_notes）原样序列化为对应 *_json 文本列。
 * - capability-tags.json 除 tags 外的各节按 (section, ref_key, ref_value, note) 拍平
 *   成 capability_reference 行，_ 前缀键（如 _说明）不单独成行，其文本放入同行 note。
 */
public final class ReferenceDictionarySamples {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] SECTIONS = {
            "tags_to_merge", "skill_mapping", "skill_profiles",
            "role_profiles", "major_categories"
    };

    private ReferenceDictionarySamples() {
    }

    // ------------------------------------------------------------------ files

    private static Path sampleRoot() {
        String cwd = System.getProperty("user.dir");
        List<Path> candidates = List.of(
                Path.of(cwd, "样例"),
                Path.of(cwd, "..", "样例"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法定位样例目录 样例/（尝试基于 user.dir=" + cwd + "）");
    }

    private static JsonNode readJson(String relativePath) {
        Path file = sampleRoot().resolve(relativePath);
        try {
            return MAPPER.readTree(Files.readAllBytes(file));
        } catch (Exception e) {
            throw new UncheckedIOException("读取样例文件失败: " + file, new java.io.IOException(e));
        }
    }

    // ------------------------------------------------------- resources (18 条)

    public static List<Resource> resources() {
        JsonNode root = readJson("data/resources.json");
        List<Resource> list = new ArrayList<>();
        for (JsonNode node : root) {
            list.add(toResource(node));
        }
        return list;
    }

    static Resource toResource(JsonNode node) {
        Resource r = new Resource();
        r.setResourceId(text(node, "id"));
        r.setName(text(node, "name"));
        r.setType(text(node, "type"));
        r.setLevelsJson(jsonText(node, "levels"));
        r.setStagesJson(jsonText(node, "stages"));
        r.setTargetMajorsJson(jsonText(node, "target_majors"));
        r.setRegistrationStart(text(node, "registration_start"));
        r.setRegistrationDeadline(text(node, "registration_deadline"));
        r.setRequiredSkillsJson(jsonText(node, "required_skills"));
        r.setDifficulty(text(node, "difficulty"));
        r.setPreparationPeriod(text(node, "preparation_period"));
        r.setTeamRolesJson(jsonText(node, "team_roles"));
        r.setBonusPointJson(jsonText(node, "bonus_point"));
        r.setProvider(text(node, "provider"));
        r.setCourseLink(text(node, "course_link"));
        r.setDescription(text(node, "description"));
        r.setTeachesSkillsJson(jsonText(node, "teaches_skills"));
        r.setSourceUrl(text(node, "source_url"));
        r.setSourceUrlsJson(jsonText(node, "source_urls"));
        r.setSourceFile(text(node, "source_file"));
        r.setNotesJson(jsonText(node, "_notes"));
        r.setUpdatedAt(text(node, "updated_at"));
        r.setDataQuality(text(node, "data_quality"));
        return r;
    }

    // --------------------------------------------------------- rag_chunks (28 条)

    public static List<RagChunk> ragChunks() {
        JsonNode root = readJson("rag/chunks.json");
        List<RagChunk> list = new ArrayList<>();
        for (JsonNode node : root) {
            list.add(toRagChunk(node));
        }
        return list;
    }

    static RagChunk toRagChunk(JsonNode node) {
        RagChunk chunk = new RagChunk();
        chunk.setChunkId(text(node, "chunk_id"));
        chunk.setTitle(text(node, "title"));
        chunk.setText(text(node, "text"));
        chunk.setSourceType(text(node, "source_type"));
        chunk.setSourceUrl(text(node, "source_url"));
        chunk.setSourceFile(text(node, "source_file"));
        chunk.setPageOrSection(text(node, "page_or_section"));
        chunk.setRelatedResourceId(text(node, "related_resource_id"));
        chunk.setCreatedAt(text(node, "created_at"));
        return chunk;
    }

    // --------------------------------------------------- capability_tags (10 条)

    public static List<CapabilityTag> capabilityTags() {
        JsonNode root = readJson("draft/capability-tags.json");
        JsonNode tags = root.get("tags");
        List<CapabilityTag> list = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = tags.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode node = entry.getValue();
            CapabilityTag tag = new CapabilityTag();
            tag.setName(name);
            tag.setCategory(text(node, "category"));
            tag.setLevel1Desc(text(node, "L1"));
            tag.setLevel2Desc(text(node, "L2"));
            tag.setLevel3Desc(text(node, "L3"));
            tag.setSkillAliasesJson(jsonText(node, "skill_aliases"));
            tag.setTypicalEvidenceJson(jsonText(node, "typical_evidence"));
            list.add(tag);
        }
        return list;
    }

    // ------------------------------------------- capability_reference (拍平各节)

    public static List<CapabilityReference> capabilityReferences() {
        JsonNode root = readJson("draft/capability-tags.json");
        List<CapabilityReference> list = new ArrayList<>();
        for (String section : SECTIONS) {
            JsonNode sectionNode = root.get(section);
            if (sectionNode == null) {
                continue;
            }
            String sectionNote = sectionNode.has("_说明") && !sectionNode.get("_说明").isNull()
                    ? sectionNode.get("_说明").asText() : null;
            Iterator<Map.Entry<String, JsonNode>> fields = sectionNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                if (key.startsWith("_")) {
                    continue;
                }
                CapabilityReference ref = new CapabilityReference();
                ref.setSection(section);
                ref.setRefKey(key);
                ref.setRefValue(valueText(entry.getValue()));
                ref.setNote(sectionNote);
                list.add(ref);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ helpers

    /** 标量文本原样入库；数组/对象写为 JSON 文本。 */
    private static String valueText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return json(node);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static String jsonText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : json(value);
    }

    private static String json(JsonNode value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
