package org.example.lifecomposer.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationFeedbackControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @BeforeEach
    void cleanupFeedback() {
        jdbcTemplate.execute("DELETE FROM recommendation_feedback");
    }

    @Test
    @DisplayName("POST /api/recommendation-feedback records allowed feedback and GET returns it")
    void recordsAndListsFeedback() throws Exception {
        MockHttpSession session = registerAndLogin("feedbackuser1", "pass123");

        mockMvc.perform(post("/api/recommendation-feedback").session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"directionId\":\"algorithm_contest\",\"feedbackType\":\"too_hard\",\"note\":\"算法太难\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directionId").value("algorithm_contest"))
                .andExpect(jsonPath("$.feedbackType").value("too_hard"));

        mockMvc.perform(get("/api/recommendation-feedback/me").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].feedbackType").value("too_hard"))
                .andExpect(jsonPath("$[0].note").value("算法太难"));
    }

    @Test
    @DisplayName("POST /api/recommendation-feedback rejects an unknown feedback type")
    void rejectsUnknownFeedbackType() throws Exception {
        MockHttpSession session = registerAndLogin("feedbackuser2", "pass123");
        mockMvc.perform(post("/api/recommendation-feedback").session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"directionId\":\"algorithm_contest\",\"feedbackType\":\"whatever\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FEEDBACK"));
    }

    @Test
    @DisplayName("POST /api/recommendation-feedback rejects an unknown direction")
    void rejectsUnknownDirection() throws Exception {
        MockHttpSession session = registerAndLogin("feedbackuser3", "pass123");
        mockMvc.perform(post("/api/recommendation-feedback").session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"directionId\":\"not_a_direction\",\"feedbackType\":\"useful\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DIRECTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("unauthenticated feedback submission is forbidden")
    void unauthenticatedIsForbidden() throws Exception {
        mockMvc.perform(post("/api/recommendation-feedback").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"directionId\":\"algorithm_contest\",\"feedbackType\":\"useful\"}"))
                .andExpect(status().isForbidden());
    }
}
