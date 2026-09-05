package org.example.lifecomposer.controller;

import org.example.lifecomposer.Entity.CapabilityReference;
import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CapabilityReferenceControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    // capability-tags.json 除 tags/_说明 外各节拍平后的行数
    private static final Map<String, Integer> SAMPLE_SECTION_COUNTS = Map.of(
            "tags_to_merge", 12,
            "skill_mapping", 39,
            "skill_profiles", 6,
            "role_profiles", 5,
            "major_categories", 6);
    private static final int SAMPLE_REFERENCE_COUNT =
            SAMPLE_SECTION_COUNTS.values().stream().mapToInt(Integer::intValue).sum(); // 68

    @Autowired
    private CapabilityReferenceRepository capabilityReferenceRepository;

    @BeforeEach
    void cleanupReferenceTables() {
        jdbcTemplate.execute("DELETE FROM capability_reference");
    }

    private void insertSampleReferences() {
        for (CapabilityReference ref : ReferenceDictionarySamples.capabilityReferences()) {
            capabilityReferenceRepository.upsert(ref);
        }
    }

    @Test
    @DisplayName("Round trip: capability-tags.json 各节拍平为 capability_reference 行后可完整读回")
    void roundTrip_sampleCapabilityReferences_rowsPersistAndReadBack() {
        insertSampleReferences();

        List<CapabilityReference> all = capabilityReferenceRepository.findAll();
        assertEquals(SAMPLE_REFERENCE_COUNT, all.size(), "拍平行数应等于各节键数之和");

        // 各类键值/数组/对象按 (section, ref_key) 原样读回
        CapabilityReference mapping = capabilityReferenceRepository.findBySectionAndKey("skill_mapping", "C语言");
        assertNotNull(mapping);
        assertEquals("[\"编程基础\"]", mapping.getRefValue());

        CapabilityReference merge = capabilityReferenceRepository.findBySectionAndKey("tags_to_merge", "Python编程");
        assertNotNull(merge);
        assertEquals("合并到 编程基础", merge.getRefValue());

        CapabilityReference profile = capabilityReferenceRepository.findBySectionAndKey("skill_profiles", "algorithm_contest");
        assertNotNull(profile);
        assertEquals("{\"skills\":[\"编程基础\",\"算法与数据结构\"],\"note\":\"算法竞赛通用：蓝桥杯、ACM、CCSP 等\"}",
                profile.getRefValue());

        CapabilityReference roles = capabilityReferenceRepository.findBySectionAndKey("role_profiles", "modeling_team");
        assertNotNull(roles);
        assertEquals("[\"建模\",\"编程\",\"论文写作\"]", roles.getRefValue());

        CapabilityReference majors = capabilityReferenceRepository.findBySectionAndKey("major_categories", "工科");
        assertNotNull(majors);
        assertTrue(majors.getRefValue().contains("计算机科学与技术"));
        assertTrue(majors.getRefValue().contains("人工智能"));

        // note 填入该节 _说明（可空），且 _ 前缀键不单独成行
        assertTrue(all.stream().noneMatch(ref -> ref.getRefKey().startsWith("_")),
                "_ 前缀键（如 _说明）不应拍平成行");
        assertNotNull(mapping.getNote());
        assertTrue(mapping.getNote().contains("推荐模块以此为准做匹配"));

        // 重复 upsert（(section, ref_key) 唯一约束）不产生重复行
        insertSampleReferences();
        assertEquals(SAMPLE_REFERENCE_COUNT, capabilityReferenceRepository.findAll().size());
    }

    @Test
    @DisplayName("GET /api/capability-reference - 未登录返回 403")
    void listCapabilityReferences_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/capability-reference").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/capability-reference - 空库返回空数组")
    void listCapabilityReferences_empty_returnsEmptyArray() throws Exception {
        MockHttpSession session = registerAndLogin("refuser", "pass123");
        mockMvc.perform(get("/api/capability-reference").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/capability-reference - 返回全部拍平行")
    void listCapabilityReferences_all_returnsAll() throws Exception {
        insertSampleReferences();
        MockHttpSession session = registerAndLogin("refuser", "pass123");

        mockMvc.perform(get("/api/capability-reference").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SAMPLE_REFERENCE_COUNT));
    }

    @Test
    @DisplayName("GET /api/capability-reference?section=xxx - 按字典节过滤")
    void listCapabilityReferences_bySection_filters() throws Exception {
        insertSampleReferences();
        MockHttpSession session = registerAndLogin("refuser", "pass123");

        mockMvc.perform(get("/api/capability-reference").session(session)
                        .param("section", "skill_mapping").header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(39))
                .andExpect(jsonPath("$[?(@.section != 'skill_mapping')]").isEmpty());

        mockMvc.perform(get("/api/capability-reference").session(session)
                        .param("section", "tags_to_merge").header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[?(@.section != 'tags_to_merge')]").isEmpty());

        mockMvc.perform(get("/api/capability-reference").session(session)
                        .param("section", "role_profiles").header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.section != 'role_profiles')]").isEmpty());

        mockMvc.perform(get("/api/capability-reference").session(session)
                        .param("section", "major_categories").header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    @DisplayName("GET /api/capability-reference?section=xxx - 非法 section 返回 400")
    void listCapabilityReferences_invalidSection_returnsBadRequest() throws Exception {
        insertSampleReferences();
        MockHttpSession session = registerAndLogin("refuser", "pass123");

        mockMvc.perform(get("/api/capability-reference").session(session)
                        .param("section", "not_a_section").header("User-Agent", UA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/capability-reference?section= - 空参等价于不过滤")
    void listCapabilityReferences_blankSection_returnsAll() throws Exception {
        insertSampleReferences();
        MockHttpSession session = registerAndLogin("refuser", "pass123");

        mockMvc.perform(get("/api/capability-reference").session(session)
                        .param("section", "").header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SAMPLE_REFERENCE_COUNT));
    }
}
