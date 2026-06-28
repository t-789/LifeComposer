package org.example.lifecomposer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QaControllerTest extends BaseControllerTest {

    @Test
    void health_withoutAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/qa/health")
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.mocked").exists());
    }

    @Test
    void ask_withoutAuth_returns403() throws Exception {
        mockMvc.perform(post("/api/qa/ask")
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ask_authenticated_returns200_mocked_withHistoryId() throws Exception {
        MockHttpSession session = registerAndLogin("qauser", "testpass123");

        mockMvc.perform(post("/api/qa/ask")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"如何规划英语四级学习？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mocked").value(true))
                .andExpect(jsonPath("$.historyId").isNumber())
                .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    @Test
    void ask_blankMessage_returns400() throws Exception {
        MockHttpSession session = registerAndLogin("qauser2", "testpass123");

        mockMvc.perform(post("/api/qa/ask")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ask_oversizedMessage_returns400() throws Exception {
        MockHttpSession session = registerAndLogin("qauser5", "testpass123");

        String hugeMessage = "A".repeat(5001);
        String body = String.format("{\"message\":\"%s\"}", hugeMessage);

        mockMvc.perform(post("/api/qa/ask")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ask_unknownUseCase_returns400() throws Exception {
        MockHttpSession session = registerAndLogin("qauser3", "testpass123");

        mockMvc.perform(post("/api/qa/ask")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"test\",\"useCase\":\"nonexistent_xyz\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ask_createsHistoryRecord() throws Exception {
        MockHttpSession session = registerAndLogin("qauser4", "testpass123");

        mockMvc.perform(post("/api/qa/ask")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"test planning history\"}"))
                .andExpect(status().isOk());

        // Verify history record was created
        mockMvc.perform(get("/api/planning/history")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].type").value("QA"));
    }
}
