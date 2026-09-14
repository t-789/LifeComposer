package org.example.lifecomposer.controller;

import org.example.lifecomposer.support.AuditLogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 6 acceptance: administrator password reset no longer uses or returns
 * the fixed {@code 000000} password, and stays behind CSRF + ROLE_ADMIN.
 */
class AdminPasswordResetTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";
    private static final String NEW_PASSWORD = "Fresh-Temp-Pass!2026";

    @Test
    @DisplayName("unauthenticated reset is rejected")
    void unauthenticatedRejected() throws Exception {
        int targetId = registerTarget("resetAnon", "pass123");

        mockMvc.perform(post("/api/users/admin/reset-password/" + targetId)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a normal user cannot reset passwords")
    void normalUserRejected() throws Exception {
        int targetId = registerTarget("resetTarget1", "pass123");
        MockHttpSession userSession = loginUser("resetTarget1", "pass123");

        mockMvc.perform(post("/api/users/admin/reset-password/" + targetId)
                        .session(userSession)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("without a CSRF token the reset is rejected")
    void missingCsrfRejected() throws Exception {
        int targetId = registerTarget("resetTarget2", "pass123");
        MockHttpSession adminSession = loginAsAdmin();

        mockMvcWithoutCsrf.perform(post("/api/users/admin/reset-password/" + targetId)
                        .session(adminSession)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("weak temporary passwords are rejected and nothing changes")
    void weakPasswordRejected() throws Exception {
        int targetId = registerTarget("resetTarget3", "pass123");
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(post("/api/users/admin/reset-password/" + targetId)
                        .session(adminSession)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("WEAK_PASSWORD"));

        // The original password still works, the weak one was not applied.
        loginUser("resetTarget3", "pass123");
        mockMvc.perform(post("/api/users/login")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"resetTarget3\",\"password\":\"000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("missing request body is rejected")
    void missingBodyRejected() throws Exception {
        int targetId = registerTarget("resetTarget4", "pass123");
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(post("/api/users/admin/reset-password/" + targetId)
                        .session(adminSession)
                        .header("User-Agent", UA)
                        .contentType("application/json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unknown target user returns 404")
    void unknownUserReturnsNotFound() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();

        mockMvc.perform(post("/api/users/admin/reset-password/999999")
                        .session(adminSession)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("valid reset succeeds without echoing the password and rotates the hash")
    void validResetSucceedsWithoutEcho() throws Exception {
        int targetId = registerTarget("resetTarget5", "pass123");
        MockHttpSession adminSession = loginAsAdmin();

        String body;
        try (AuditLogCapture audit = new AuditLogCapture()) {
            body = mockMvc.perform(post("/api/users/admin/reset-password/" + targetId)
                            .session(adminSession)
                            .header("User-Agent", UA)
                            .contentType("application/json")
                            .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(audit.contains("event=admin_reset_password")).isTrue();
            assertThat(audit.joined()).doesNotContain(NEW_PASSWORD);
        }

        assertThat(body).doesNotContain(NEW_PASSWORD);
        assertThat(body).doesNotContain("000000");
        assertThat(body).contains("临时密码");

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, targetId);
        assertThat(storedHash).isNotNull().startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches(NEW_PASSWORD, storedHash)).isTrue();
        assertThat(storedHash).isNotEqualTo(NEW_PASSWORD);

        // Review follow-up: the issued password is temporary, not a permanent reset.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_reset_required FROM users WHERE id = ?", Integer.class, targetId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT temp_password_expires_at FROM users WHERE id = ?", Object.class, targetId))
                .isNotNull();

        // The temporary password works (forced change) and the old one does not.
        mockMvc.perform(post("/api/users/login")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"resetTarget5\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mockMvc.perform(post("/api/users/login")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"resetTarget5\",\"password\":\"pass123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the last loginable administrator cannot be demoted")
    void lastAdminCannotBeDemoted() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        Integer adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'admin'", Integer.class);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/users/" + adminId + "/revoke-admin")
                        .session(adminSession)
                        .header("User-Agent", UA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LAST_ADMIN_PROTECTED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT type FROM users WHERE id = ?", Integer.class, adminId)).isEqualTo(2);
    }

    private int registerTarget(String username, String password) throws Exception {
        registerUser(username, password);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, username);
        assertThat(id).isNotNull();
        return id;
    }
}
