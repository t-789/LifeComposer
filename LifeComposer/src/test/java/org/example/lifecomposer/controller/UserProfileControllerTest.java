package org.example.lifecomposer.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @BeforeEach
    void cleanupProfiles() {
        jdbcTemplate.execute("DELETE FROM user_profiles");
    }

    @Test
    @DisplayName("GET /api/profiles/me - unauthenticated returns 403")
    void getProfile_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/profiles/me")
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/profiles/me - unauthenticated returns 403")
    void putProfile_unauthenticated_returnsForbidden() throws Exception {
        String body = "{\"college\":\"计算机学院\",\"major\":\"计算机科学与技术\"}";
        mockMvc.perform(put("/api/profiles/me")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/profiles/me - authenticated upsert returns 200")
    void putProfile_authenticated_returnsOk() throws Exception {
        MockHttpSession session = registerAndLogin("profileuser", "pass123");

        String body = "{\"college\":\"计算机学院\",\"major\":\"计算机科学与技术\",\"grade\":\"2024\","
                + "\"studentId\":\"2024123456\",\"skillsJson\":\"[\\\"Java\\\",\\\"Python\\\"]\","
                + "\"interestsJson\":\"[\\\"AI\\\",\\\"后端开发\\\"]\","
                + "\"experiencesJson\":\"[{\\\"type\\\":\\\"竞赛\\\",\\\"name\\\":\\\"蓝桥杯\\\"}]\","
                + "\"preferencesJson\":\"{\\\"learning_style\\\":\\\"视频\\\",\\\"target\\\":\\\"考研\\\"}\"}";

        mockMvc.perform(put("/api/profiles/me").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("计算机学院"))
                .andExpect(jsonPath("$.major").value("计算机科学与技术"))
                .andExpect(jsonPath("$.grade").value("2024"))
                .andExpect(jsonPath("$.studentId").value("2024123456"));
    }

    @Test
    @DisplayName("GET /api/profiles/me - returns profile after PUT")
    void getProfile_afterPut_returnsProfileData() throws Exception {
        MockHttpSession session = registerAndLogin("getuser", "pass123");

        // First, create the profile
        String profileBody = "{\"college\":\"软件学院\",\"major\":\"软件工程\",\"grade\":\"2023\","
                + "\"studentId\":\"2023000001\"}";
        mockMvc.perform(put("/api/profiles/me").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(profileBody))
                .andExpect(status().isOk());

        // Then GET should return it
        mockMvc.perform(get("/api/profiles/me").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("软件学院"))
                .andExpect(jsonPath("$.major").value("软件工程"))
                .andExpect(jsonPath("$.grade").value("2023"))
                .andExpect(jsonPath("$.studentId").value("2023000001"));
    }

    @Test
    @DisplayName("GET /api/profiles/me - no profile exists returns 404")
    void getProfile_noProfile_returnsNotFound() throws Exception {
        MockHttpSession session = registerAndLogin("noprofileuser", "pass123");

        mockMvc.perform(get("/api/profiles/me").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/profiles/me - cross-user isolation: user A cannot see user B's profile")
    void putAndGet_profiles_areUserIsolated() throws Exception {
        // User A creates profile
        MockHttpSession sessionA = registerAndLogin("userA", "pass123");
        String bodyA = "{\"college\":\"A学院\",\"major\":\"A专业\"}";
        mockMvc.perform(put("/api/profiles/me").session(sessionA)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(bodyA))
                .andExpect(status().isOk());

        // User B creates profile
        MockHttpSession sessionB = registerAndLogin("userB", "pass456");
        String bodyB = "{\"college\":\"B学院\",\"major\":\"B专业\"}";
        mockMvc.perform(put("/api/profiles/me").session(sessionB)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(bodyB))
                .andExpect(status().isOk());

        // User B gets their own profile (should be B's data, not A's)
        mockMvc.perform(get("/api/profiles/me").session(sessionB)
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("B学院"))
                .andExpect(jsonPath("$.major").value("B专业"));

        // User A gets their own profile (should be A's data, not B's)
        mockMvc.perform(get("/api/profiles/me").session(sessionA)
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("A学院"))
                .andExpect(jsonPath("$.major").value("A专业"));
    }

    @Test
    @DisplayName("PUT /api/profiles/me - update existing profile preserves data")
    void putProfile_updateExisting_overwritesData() throws Exception {
        MockHttpSession session = registerAndLogin("updateuser", "pass123");

        // First PUT
        String firstBody = "{\"college\":\"第一学院\",\"major\":\"第一专业\"}";
        mockMvc.perform(put("/api/profiles/me").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("第一学院"));

        // Second PUT - update
        String secondBody = "{\"college\":\"第二学院\",\"major\":\"第二专业\",\"grade\":\"2024\"}";
        mockMvc.perform(put("/api/profiles/me").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(secondBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("第二学院"))
                .andExpect(jsonPath("$.major").value("第二专业"))
                .andExpect(jsonPath("$.grade").value("2024"));

        // GET should reflect updated data
        mockMvc.perform(get("/api/profiles/me").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("第二学院"))
                .andExpect(jsonPath("$.grade").value("2024"));
    }
}
