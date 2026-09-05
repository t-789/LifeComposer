package org.example.lifecomposer.controller;

import org.example.lifecomposer.Entity.CapabilityTag;
import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.example.lifecomposer.Repository.CapabilityTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CapabilityTagControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";
    private static final int SAMPLE_TAG_COUNT = 10;   // capability-tags.json 的 tags 节

    @Autowired
    private CapabilityTagRepository capabilityTagRepository;

    @Autowired
    private CapabilityReferenceRepository capabilityReferenceRepository;

    @BeforeEach
    void cleanupReferenceTables() {
        jdbcTemplate.execute("DELETE FROM capability_reference");
        jdbcTemplate.execute("DELETE FROM capability_tags");
    }

    private void insertSampleTags() {
        for (CapabilityTag tag : ReferenceDictionarySamples.capabilityTags()) {
            capabilityTagRepository.upsert(tag);
        }
    }

    @Test
    @DisplayName("Round trip: capability-tags.json 的 tags 节 10 条 sample-shaped 行插入后可完整读回")
    void roundTrip_sampleCapabilityTags_rowsPersistAndReadBack() {
        insertSampleTags();

        List<CapabilityTag> all = capabilityTagRepository.findAll();
        assertEquals(SAMPLE_TAG_COUNT, all.size(), "capability_tags 行数应与文件 tags 节一致");

        CapabilityTag tag = capabilityTagRepository.findByName("编程基础");
        assertNotNull(tag, "应能按标签主键 name=编程基础 读回");
        assertEquals("技术能力", tag.getCategory());
        assertEquals("能用一门语言完成课程作业（如数据结构课设）", tag.getLevel1Desc());
        assertTrue(tag.getLevel2Desc().contains("小型项目"));
        assertTrue(tag.getLevel3Desc().contains("新技术栈"));
        assertEquals("[\"C语言\",\"C++\",\"Python\",\"Python基础\",\"Java\",\"Go\",\"Rust\",\"JavaScript\",\"MATLAB\"]",
                tag.getSkillAliasesJson());
        assertTrue(tag.getTypicalEvidenceJson().contains("开源贡献"));

        // 通用能力标签
        CapabilityTag general = capabilityTagRepository.findByName("英语阅读");
        assertNotNull(general);
        assertEquals("通用能力", general.getCategory());

        // 默认入库时间已填充
        assertTrue(all.stream().allMatch(t -> t.getCreatedAt() != null && t.getUpdatedAt() != null));

        // 重复 upsert（name 主键冲突）不产生重复行
        insertSampleTags();
        assertEquals(SAMPLE_TAG_COUNT, capabilityTagRepository.findAll().size());
    }

    @Test
    @DisplayName("GET /api/capability-tags - 未登录返回 403")
    void listCapabilityTags_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/capability-tags").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/capability-tags - 空库返回空数组")
    void listCapabilityTags_empty_returnsEmptyArray() throws Exception {
        MockHttpSession session = registerAndLogin("taguser", "pass123");
        mockMvc.perform(get("/api/capability-tags").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/capability-tags - 返回全部 10 个标准标签及等级描述")
    void listCapabilityTags_all_returnsAll() throws Exception {
        insertSampleTags();
        MockHttpSession session = registerAndLogin("taguser", "pass123");

        mockMvc.perform(get("/api/capability-tags").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SAMPLE_TAG_COUNT))
                .andExpect(jsonPath("$[?(@.name == '编程基础')].category").value(hasItem("技术能力")))
                .andExpect(jsonPath("$[?(@.name == '英语阅读')].category").value(hasItem("通用能力")))
                .andExpect(jsonPath("$[?(@.name == '编程基础')].level1Desc")
                        .value(hasItem("能用一门语言完成课程作业（如数据结构课设）")))
                .andExpect(jsonPath("$[?(@.name == '数学建模')].skillAliasesJson")
                        .value(hasItem("[\"数学建模\",\"数学模型\",\"MATLAB\",\"Lingo\",\"建模\"]")));
    }
}
