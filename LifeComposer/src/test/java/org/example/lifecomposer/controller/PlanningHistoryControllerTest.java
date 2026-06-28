package org.example.lifecomposer.controller;

import org.example.lifecomposer.Service.PlanningHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanningHistoryControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Autowired
    private PlanningHistoryService planningHistoryService;

    @Test
    @DisplayName("GET /api/planning/history - unauthenticated returns 403")
    void listHistory_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/planning/history").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/planning/history/{id} - unauthenticated returns 403")
    void getHistoryById_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/planning/history/1").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/planning/history - empty list returns []")
    void listHistory_authenticated_emptyList() throws Exception {
        MockHttpSession session = registerAndLogin("emptyuser2", "pass123");
        mockMvc.perform(get("/api/planning/history").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Append QA record then GET returns it")
    void appendRecord_thenList_containsIt() throws Exception {
        MockHttpSession session = registerAndLogin("historyuser", "pass123");

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "historyuser");

        planningHistoryService.appendRecord(
                userId, "QA",
                "{\"message\":\"如何规划英语学习？\"}",
                "{\"answer\":\"建议每天背单词\"}",
                "mock", "lfm2.5:8b", "MOCKED", null);

        mockMvc.perform(get("/api/planning/history").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("QA"))
                .andExpect(jsonPath("$[0].provider").value("mock"))
                .andExpect(jsonPath("$[0].model").value("lfm2.5:8b"))
                .andExpect(jsonPath("$[0].status").value("MOCKED"));
    }

    @Test
    @DisplayName("GET /api/planning/history/{id} - returns record")
    void getHistoryById_returnsRecord() throws Exception {
        MockHttpSession session = registerAndLogin("getuser2", "pass123");

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "getuser2");

        Long recordId = planningHistoryService.appendRecord(
                userId, "PLAN",
                "{\"goal\":\"考研\"}",
                "{\"plan\":\"第一阶段：基础\"}",
                "mock", "lfm2.5:8b", "MOCKED", null);

        mockMvc.perform(get("/api/planning/history/" + recordId).session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PLAN"))
                .andExpect(jsonPath("$.id").value(recordId.intValue()));
    }

    @Test
    @DisplayName("Cross-user ownership: user B cannot access user A's record")
    void history_crossUserOwnership_denied() throws Exception {
        MockHttpSession sessionA = registerAndLogin("ownerA2", "pass123");
        MockHttpSession sessionB = registerAndLogin("otherB2", "pass456");

        Long userAId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "ownerA2");

        planningHistoryService.appendRecord(
                userAId, "QA",
                "{\"message\":\"test\"}",
                "{\"answer\":\"test answer\"}",
                "mock", "lfm2.5:8b", "MOCKED", null);

        MvcResult listResult = mockMvc.perform(get("/api/planning/history").session(sessionA).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andReturn();

        String json = listResult.getResponse().getContentAsString();
        long recordId = extractIdFromJson(json);

        mockMvc.perform(get("/api/planning/history/" + recordId).session(sessionB).header("User-Agent", UA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Record JSON does not contain raw secrets")
    void record_sanitization_noRawSecrets() throws Exception {
        MockHttpSession session = registerAndLogin("sanituser", "pass123");

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "sanituser");

        planningHistoryService.appendRecord(
                userId, "QA",
                "{\"message\":\"query\"}",
                "{\"result\":\"clean\"}",
                "mock", "lfm2.5:8b", "SUCCESS", null);

        MvcResult result = mockMvc.perform(get("/api/planning/history").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assert json.startsWith("[");
    }

    private long extractIdFromJson(String json) {
        int idx = json.indexOf("\"id\":");
        int start = json.indexOf(":", idx) + 1;
        int end = json.indexOf(",", start);
        if (end == -1) {
            end = json.indexOf("}", start);
        }
        return Long.parseLong(json.substring(start, end).trim());
    }
}
