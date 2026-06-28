package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    @DisplayName("POST /api/goals - unauthenticated returns 403")
    void postGoal_unauthenticated_returnsForbidden() throws Exception {
        String body = "{\"title\":\"通过英语六级\"}";
        mockMvc.perform(post("/api/goals")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/goals - unauthenticated returns 403")
    void listGoals_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/goals").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/goals - empty list returns []")
    void listGoals_authenticated_emptyList() throws Exception {
        MockHttpSession session = registerAndLogin("emptyuser", "pass123");
        mockMvc.perform(get("/api/goals").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST /api/goals - valid goal returns 200")
    void postGoal_valid_returnsOk() throws Exception {
        MockHttpSession session = registerAndLogin("goalcreator", "pass123");
        String body = "{\"title\":\"通过英语六级\",\"description\":\"2026年12月前通过CET-6\",\"category\":\"学业\",\"priority\":\"HIGH\",\"targetDate\":\"2026-12-31\",\"progress\":0}";
        mockMvc.perform(post("/api/goals").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("通过英语六级"))
                .andExpect(jsonPath("$.category").value("学业"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST + GET - created goal appears in list")
    void postGoal_thenList_containsIt() throws Exception {
        MockHttpSession session = registerAndLogin("listuser", "pass123");
        String body = "{\"title\":\"目标一\"}";
        mockMvc.perform(post("/api/goals").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/goals").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("目标一"));
    }

    @Test
    @DisplayName("GET /api/goals/{id} - returns goal")
    void getGoalById_returnsGoal() throws Exception {
        MockHttpSession session = registerAndLogin("getuser", "pass123");

        // Create goal
        MvcResult createResult = mockMvc.perform(post("/api/goals").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"title\":\"GET测试目标\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = createResult.getResponse().getContentAsString();
        long goalId = extractIdFromJson(responseContent);

        mockMvc.perform(get("/api/goals/" + goalId).session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("GET测试目标"))
                .andExpect(jsonPath("$.id").value((int) goalId));
    }

    @Test
    @DisplayName("PUT /api/goals/{id} - update status returns 200")
    void updateGoal_returnsOk() throws Exception {
        MockHttpSession session = registerAndLogin("updateuser", "pass123");

        // Create goal
        MvcResult createResult = mockMvc.perform(post("/api/goals").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"title\":\"PUT测试目标\",\"status\":\"ACTIVE\",\"progress\":10}"))
                .andExpect(status().isOk())
                .andReturn();

        long goalId = extractIdFromJson(createResult.getResponse().getContentAsString());

        // Update
        String updateBody = "{\"title\":\"PUT测试目标\",\"status\":\"COMPLETED\",\"progress\":100}";
        mockMvc.perform(put("/api/goals/" + goalId).session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress").value(100));
    }

    @Test
    @DisplayName("DELETE /api/goals/{id} - soft archive returns 200")
    void deleteGoal_archivesReturnsOk() throws Exception {
        MockHttpSession session = registerAndLogin("deleteuser", "pass123");

        // Create goal
        MvcResult createResult = mockMvc.perform(post("/api/goals").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"title\":\"DELETE测试目标\"}"))
                .andExpect(status().isOk())
                .andReturn();

        long goalId = extractIdFromJson(createResult.getResponse().getContentAsString());

        // Delete (archive)
        mockMvc.perform(delete("/api/goals/" + goalId).session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("目标已归档"));

        // Archived goal should not appear in list
        mockMvc.perform(get("/api/goals").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Cross-user ownership: user B cannot access user A's goal")
    void goal_crossUserOwnership_returnsForbidden() throws Exception {
        MockHttpSession sessionA = registerAndLogin("ownerA", "pass123");
        MockHttpSession sessionB = registerAndLogin("otherB", "pass456");

        // User A creates goal
        MvcResult createResult = mockMvc.perform(post("/api/goals").session(sessionA)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"title\":\"A的目标\"}"))
                .andExpect(status().isOk())
                .andReturn();

        long goalId = extractIdFromJson(createResult.getResponse().getContentAsString());

        // User B tries to GET -> 400 (ownership check fails)
        mockMvc.perform(get("/api/goals/" + goalId).session(sessionB).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        // User B tries to PUT -> 400
        mockMvc.perform(put("/api/goals/" + goalId).session(sessionB)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"title\":\"修改目标\"}"))
                .andExpect(status().isBadRequest());

        // User B tries to DELETE -> 400
        mockMvc.perform(delete("/api/goals/" + goalId).session(sessionB).header("User-Agent", UA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/goals - missing title returns 400")
    void postGoal_missingTitle_returnsBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin("notitleuser", "pass123");
        mockMvc.perform(post("/api/goals").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"description\":\"no title\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/goals - invalid status returns 400")
    void postGoal_invalidStatus_returnsBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin("badstatususer", "pass123");
        mockMvc.perform(post("/api/goals").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"title\":\"测试目标\",\"status\":\"INVALID_STATUS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/goals/{id} - invalid status returns 400")
    void updateGoal_invalidStatus_returnsBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin("badupdateuser", "pass123");

        // Create goal first
        MvcResult createResult = mockMvc.perform(post("/api/goals").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"title\":\"正常目标\"}"))
                .andExpect(status().isOk())
                .andReturn();

        long goalId = extractIdFromJson(createResult.getResponse().getContentAsString());

        String updateBody = "{\"title\":\"正常目标\",\"status\":\"DELETED_FOREVER\"}";
        mockMvc.perform(put("/api/goals/" + goalId).session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isBadRequest());
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
