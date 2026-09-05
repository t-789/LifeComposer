package org.example.lifecomposer.controller;

import org.example.lifecomposer.Entity.RagChunk;
import org.example.lifecomposer.Repository.RagChunkRepository;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RagChunkControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";
    private static final int SAMPLE_CHUNK_COUNT = 28;   // 样例/rag/chunks.json

    @Autowired
    private RagChunkRepository ragChunkRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @BeforeEach
    void cleanupReferenceTables() {
        jdbcTemplate.execute("DELETE FROM rag_chunks");
        jdbcTemplate.execute("DELETE FROM resources");
    }

    private void insertSampleChunks() {
        for (RagChunk chunk : ReferenceDictionarySamples.ragChunks()) {
            ragChunkRepository.upsert(chunk);
        }
    }

    @Test
    @DisplayName("Round trip: chunks.json 的 28 条 sample-shaped 行经 repository 插入后可完整读回")
    void roundTrip_sampleChunksJson_rowsPersistAndReadBack() {
        insertSampleChunks();

        List<RagChunk> all = ragChunkRepository.findAll();
        assertEquals(SAMPLE_CHUNK_COUNT, all.size(), "rag_chunks 行数应与 chunks.json 一致");

        // 主键 chunk_id 与业务字段原样往返
        RagChunk chunk = ragChunkRepository.findById("rag_001");
        assertNotNull(chunk, "应能按 chunk_id=rag_001 读回");
        assertEquals("数学建模竞赛参赛基本要求", chunk.getTitle());
        assertTrue(chunk.getText().contains("数学建模"));
        assertEquals("web", chunk.getSourceType());
        assertEquals("https://www.mcm.edu.cn", chunk.getSourceUrl());
        assertEquals("参赛须知", chunk.getPageOrSection());
        assertEquals("competition_001", chunk.getRelatedResourceId(),
                "related_resource_id 应引用 resources.resource_id 业务键");
        assertEquals("2026-06-06", chunk.getCreatedAt());

        // 8/28 条不关联资源：related_resource_id 允许 NULL 且可读回
        RagChunk unrelated = ReferenceDictionarySamples.ragChunks().stream()
                .filter(c -> c.getRelatedResourceId() == null).findFirst().orElseThrow();
        RagChunk readBack = ragChunkRepository.findById(unrelated.getChunkId());
        assertNotNull(readBack);
        assertNull(readBack.getRelatedResourceId());

        // 重复 upsert（chunk_id 主键冲突）不产生重复行
        insertSampleChunks();
        assertEquals(SAMPLE_CHUNK_COUNT, ragChunkRepository.findAll().size());
    }

    @Test
    @DisplayName("Round trip: chunks.json 的 related_resource_id 与 resources.json 业务键一一对应")
    void roundTrip_sampleChunks_referenceResourceBusinessKeys() {
        insertSampleChunks();
        // 无需先插 resources：related 是逻辑关联；此处校验集合完整覆盖样例 18 个资源
        var related = ragChunkRepository.findAll().stream()
                .map(RagChunk::getRelatedResourceId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(18, related.size(),
                "样例切片共关联 18 个不同资源业务键（12 竞赛 + 6 课程）");
        assertTrue(related.contains("competition_001"));
        assertTrue(related.contains("course_001"));
    }

    @Test
    @DisplayName("GET /api/rag-chunks - 未登录返回 403")
    void listRagChunks_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/rag-chunks").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/rag-chunks - 空库返回空数组")
    void listRagChunks_empty_returnsEmptyArray() throws Exception {
        MockHttpSession session = registerAndLogin("raguser", "pass123");
        mockMvc.perform(get("/api/rag-chunks").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/rag-chunks - 返回全部 28 条")
    void listRagChunks_all_returnsAll() throws Exception {
        insertSampleChunks();
        MockHttpSession session = registerAndLogin("raguser", "pass123");

        mockMvc.perform(get("/api/rag-chunks").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SAMPLE_CHUNK_COUNT))
                .andExpect(jsonPath("$[0].chunkId").value("rag_001"))
                .andExpect(jsonPath("$[0].title").value("数学建模竞赛参赛基本要求"));
    }

    @Test
    @DisplayName("GET /api/rag-chunks?relatedResourceId=competition_001 - 按资源业务键过滤（2 条）")
    void listRagChunks_byRelatedResourceId_filters() throws Exception {
        insertSampleChunks();
        MockHttpSession session = registerAndLogin("raguser", "pass123");

        mockMvc.perform(get("/api/rag-chunks").session(session)
                        .param("relatedResourceId", "competition_001")
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.relatedResourceId != 'competition_001')]").isEmpty());
    }

    @Test
    @DisplayName("GET /api/rag-chunks?relatedResourceId=course_001 - 按课程业务键过滤（1 条）")
    void listRagChunks_byCourseBusinessKey_filters() throws Exception {
        insertSampleChunks();
        MockHttpSession session = registerAndLogin("raguser", "pass123");

        mockMvc.perform(get("/api/rag-chunks").session(session)
                        .param("relatedResourceId", "course_001")
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/rag-chunks?relatedResourceId= - 空参等价于不过滤")
    void listRagChunks_blankParam_returnsAll() throws Exception {
        insertSampleChunks();
        MockHttpSession session = registerAndLogin("raguser", "pass123");

        mockMvc.perform(get("/api/rag-chunks").session(session)
                        .param("relatedResourceId", "")
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SAMPLE_CHUNK_COUNT));
    }

    @Test
    @DisplayName("GET /api/rag-chunks?relatedResourceId=xxx - 未命中返回空数组")
    void listRagChunks_unknownRelatedResource_returnsEmpty() throws Exception {
        insertSampleChunks();
        MockHttpSession session = registerAndLogin("raguser", "pass123");

        mockMvc.perform(get("/api/rag-chunks").session(session)
                        .param("relatedResourceId", "no_such_resource")
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
