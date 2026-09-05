package org.example.lifecomposer.controller;

import org.example.lifecomposer.Entity.Resource;
import org.example.lifecomposer.Repository.RagChunkRepository;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";
    private static final int SAMPLE_RESOURCE_COUNT = 18;   // 样例/data/resources.json
    private static final int SAMPLE_COMPETITION_COUNT = 12;
    private static final int SAMPLE_COURSE_COUNT = 6;
    private static final int SAMPLE_CHUNK_COUNT = 28;      // 样例/rag/chunks.json

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private RagChunkRepository ragChunkRepository;

    @BeforeEach
    void cleanupReferenceTables() {
        jdbcTemplate.execute("DELETE FROM rag_chunks");
        jdbcTemplate.execute("DELETE FROM resources");
        jdbcTemplate.execute("DELETE FROM capability_tags");
        jdbcTemplate.execute("DELETE FROM capability_reference");
    }

    // ------------------------------------------------------------- persistence

    private void insertSampleResources() {
        for (Resource resource : ReferenceDictionarySamples.resources()) {
            resourceRepository.upsert(resource);
        }
    }

    @Test
    @DisplayName("Round trip: resources.json 的 18 条 sample-shaped 行经 repository 插入后可完整读回")
    void roundTrip_sampleResourcesJson_rowsPersistAndReadBack() {
        insertSampleResources();

        List<Resource> all = resourceRepository.findAll();
        assertEquals(SAMPLE_RESOURCE_COUNT, all.size(), "resources 行数应与 resources.json 一致");

        // resource_id 业务键（AUTOINCREMENT id 之外的 UNIQUE 键）必须原样保留
        Set<String> businessIds = all.stream().map(Resource::getResourceId).collect(Collectors.toSet());
        assertEquals(SAMPLE_RESOURCE_COUNT, businessIds.size(), "resource_id 应保持 UNIQUE");
        assertTrue(businessIds.contains("competition_001"));
        assertTrue(businessIds.contains("course_001"));

        // 物理主键 id（AUTOINCREMENT）已分配
        assertTrue(all.stream().allMatch(r -> r.getId() != null && r.getId() > 0));
        // 入库时间由数据库默认填充
        assertTrue(all.stream().allMatch(r -> r.getCreatedAt() != null));

        // 类型过滤（repository 层）
        assertEquals(SAMPLE_COMPETITION_COUNT,
                resourceRepository.findByType("competition").size());
        assertEquals(SAMPLE_COURSE_COUNT,
                resourceRepository.findByType("course").size());

        // 嵌套 JSON 字段原样往返
        Resource competition = resourceRepository.findByResourceId("competition_001");
        assertNotNull(competition, "应能按业务键 competition_001 读回");
        assertEquals("全国大学生数学建模竞赛", competition.getName());
        assertEquals("competition", competition.getType());
        assertEquals("[\"provincial\",\"national\"]", competition.getLevelsJson());
        assertTrue(competition.getStagesJson().contains("\"stage\":\"provincial\""));
        assertEquals("{\"categories\":[\"不限\"]}", competition.getTargetMajorsJson());
        assertEquals("{\"profile\":\"modeling_contest\"}", competition.getRequiredSkillsJson());
        assertTrue(competition.getBonusPointJson().contains("国家级一等奖及以上"));
        assertEquals("2026-09-07", competition.getRegistrationDeadline());
        assertEquals("medium", competition.getDifficulty());
        assertEquals("complete", competition.getDataQuality());
        assertEquals("2026-09-04", competition.getUpdatedAt());

        Resource course = resourceRepository.findByResourceId("course_001");
        assertNotNull(course);
        assertEquals("course", course.getType());
        assertEquals("[\"编程基础\"]", course.getTeachesSkillsJson());
        assertTrue(course.getDescription().contains("翁恺"));
        assertEquals("[]", course.getRequiredSkillsJson());

        // 物理主键查找也能读回同一个业务键
        Resource byPhysicalId = resourceRepository.findById(competition.getId());
        assertNotNull(byPhysicalId);
        assertEquals("competition_001", byPhysicalId.getResourceId());
    }

    @Test
    @DisplayName("Round trip: 重复 upsert 同一交付包不产生重复行（resource_id UNIQUE）")
    void roundTrip_reUpsert_isIdempotent() {
        insertSampleResources();
        insertSampleResources(); // 第二遍重复导入

        assertEquals(SAMPLE_RESOURCE_COUNT, resourceRepository.findAll().size());
        assertNotNull(resourceRepository.findByResourceId("competition_001"));
    }

    @Test
    @DisplayName("数据一致性: rag_chunks.related_resource_id 引用的是 resources.resource_id 业务键")
    void sampleData_ragChunkLinksReferenceResourceBusinessKeys() {
        insertSampleResources();
        for (var chunk : ReferenceDictionarySamples.ragChunks()) {
            ragChunkRepository.upsert(chunk);
        }

        List<Resource> resources = resourceRepository.findAll();
        Set<String> businessIds = resources.stream()
                .map(Resource::getResourceId).collect(Collectors.toSet());
        assertEquals(SAMPLE_RESOURCE_COUNT, businessIds.size());

        var chunks = ragChunkRepository.findAll();
        assertEquals(SAMPLE_CHUNK_COUNT, chunks.size());
        // 每个非 NULL related_resource_id 必须命中 resources.resource_id 业务键（而非物理 id）
        for (var chunk : chunks) {
            if (chunk.getRelatedResourceId() != null) {
                assertTrue(businessIds.contains(chunk.getRelatedResourceId()),
                        "切片 " + chunk.getChunkId() + " 引用了不存在的资源业务键 "
                                + chunk.getRelatedResourceId());
            }
        }
        assertThat(chunks).extracting("relatedResourceId")
                .filteredOn(related -> related != null)
                .hasSize(20); // 样例中 20/28 条关联资源，8 条为 NULL
    }

    // ------------------------------------------------------------------ HTTP

    @Test
    @DisplayName("GET /api/resources - 未登录返回 403")
    void listResources_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/resources").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/resources - 空库返回空数组")
    void listResources_empty_returnsEmptyArray() throws Exception {
        MockHttpSession session = registerAndLogin("resourceuser", "pass123");
        mockMvc.perform(get("/api/resources").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/resources - 返回全部 18 条")
    void listResources_all_returnsAll() throws Exception {
        insertSampleResources();
        MockHttpSession session = registerAndLogin("resourceuser", "pass123");

        mockMvc.perform(get("/api/resources").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SAMPLE_RESOURCE_COUNT))
                .andExpect(jsonPath("$[0].resourceId").value("competition_001"))
                .andExpect(jsonPath("$[0].name").value("全国大学生数学建模竞赛"));
    }

    @Test
    @DisplayName("GET /api/resources?type=competition - 只返回竞赛（12 条）")
    void listResources_typeCompetition_filters() throws Exception {
        insertSampleResources();
        MockHttpSession session = registerAndLogin("resourceuser", "pass123");

        mockMvc.perform(get("/api/resources").session(session)
                        .param("type", "competition").header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SAMPLE_COMPETITION_COUNT))
                .andExpect(jsonPath("$[?(@.type != 'competition')]").isEmpty())
                .andExpect(jsonPath("$[0].resourceId").value("competition_001"));
    }

    @Test
    @DisplayName("GET /api/resources?type=course - 只返回课程（6 条）")
    void listResources_typeCourse_filters() throws Exception {
        insertSampleResources();
        MockHttpSession session = registerAndLogin("resourceuser", "pass123");

        mockMvc.perform(get("/api/resources").session(session)
                        .param("type", "course").header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SAMPLE_COURSE_COUNT))
                .andExpect(jsonPath("$[?(@.type != 'course')]").isEmpty())
                .andExpect(jsonPath("$[0].resourceId").value("course_001"));
    }

    @Test
    @DisplayName("GET /api/resources?type=xxx - 非法 type 返回 400")
    void listResources_invalidType_returnsBadRequest() throws Exception {
        insertSampleResources();
        MockHttpSession session = registerAndLogin("resourceuser", "pass123");

        mockMvc.perform(get("/api/resources").session(session)
                        .param("type", "hackathon").header("User-Agent", UA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/resources/{id} - 按业务键 resource_id 读回竞赛")
    void getResource_byBusinessKey_returnsResource() throws Exception {
        insertSampleResources();
        MockHttpSession session = registerAndLogin("resourceuser", "pass123");

        mockMvc.perform(get("/api/resources/competition_001").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.resourceId").value("competition_001"))
                .andExpect(jsonPath("$.type").value("competition"))
                .andExpect(jsonPath("$.name").value("全国大学生数学建模竞赛"))
                .andExpect(jsonPath("$.difficulty").value("medium"))
                .andExpect(jsonPath("$.dataQuality").value("complete"))
                .andExpect(jsonPath("$.levelsJson").value("[\"provincial\",\"national\"]"))
                .andExpect(jsonPath("$.targetMajorsJson").value("{\"categories\":[\"不限\"]}"))
                .andExpect(jsonPath("$.registrationDeadline").value("2026-09-07"));
    }

    @Test
    @DisplayName("GET /api/resources/{id} - 纯数字 id 回退到物理主键也可读回同一资源")
    void getResource_byPhysicalId_returnsSameResource() throws Exception {
        insertSampleResources();
        MockHttpSession session = registerAndLogin("resourceuser", "pass123");

        Resource competition = resourceRepository.findByResourceId("competition_001");
        assertNotNull(competition);
        mockMvc.perform(get("/api/resources/" + competition.getId()).session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("competition_001"));
    }

    @Test
    @DisplayName("GET /api/resources/{id} - 不存在的 id 返回 404")
    void getResource_missing_returnsNotFound() throws Exception {
        insertSampleResources();
        MockHttpSession session = registerAndLogin("resourceuser", "pass123");

        mockMvc.perform(get("/api/resources/nonexistent_999").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resources/999999999").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isNotFound());
    }
}
