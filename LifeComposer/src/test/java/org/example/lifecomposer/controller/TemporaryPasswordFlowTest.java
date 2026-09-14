package org.example.lifecomposer.controller;

import org.example.lifecomposer.support.AuditLogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review follow-up (P1/P2): an administrator-issued temporary password is now
 * genuinely temporary — real state, expiry, forced first change and immediate
 * invalidation of the sessions that authenticated with the previous password.
 */
class TemporaryPasswordFlowTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";
    private static final String TEMP_PASSWORD = "Temp-Issued-Pass!2026";
    private static final String NEW_PASSWORD = "My-Own-Password!2026";

    @Test
    @DisplayName("temporary password: usable only for the forced change, then dead")
    void temporaryPasswordMustBeChangedFirst() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        int userId = createUser("tempFlowUser", "pass123");

        mockMvc.perform(post("/api/users/admin/reset-password/" + userId)
                        .session(adminSession).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.tempPasswordExpiresAt").exists())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("临时密码")));

        assertThat(intFlag(userId, "password_reset_required")).isEqualTo(1);
        assertThat(rawValue(userId, "temp_password_expires_at")).isNotNull();

        // Login with the temporary password: allowed, but flagged for change.
        MvcResult loginResult = mockMvc.perform(post("/api/users/login").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"tempFlowUser\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Only the change-password surface (plus current-user / logout / csrf) works.
        mockMvc.perform(get("/api/users/current").session(session).header("User-Agent", UA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/front/change-password").session(session).header("User-Agent", UA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/chat/history").session(session).header("User-Agent", UA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));

        // Wrong current password, weak new password and unchanged password are refused.
        changePassword(session, "not-the-temp-password", NEW_PASSWORD, 400);
        changePassword(session, TEMP_PASSWORD, "short", 400);
        changePassword(session, TEMP_PASSWORD, TEMP_PASSWORD, 400);

        // The real change succeeds and the same session keeps working.
        changePassword(session, TEMP_PASSWORD, NEW_PASSWORD, 200);
        mockMvc.perform(get("/api/users/current").session(session).header("User-Agent", UA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/chat/history").session(session).header("User-Agent", UA))
                .andExpect(status().isOk());

        assertThat(intFlag(userId, "password_reset_required")).isZero();
        assertThat(rawValue(userId, "temp_password_expires_at")).isNull();
        assertThat(intFlag(userId, "credential_version")).isGreaterThan(1);

        // The temporary password is no longer accepted; the new one is.
        mockMvc.perform(post("/api/users/login").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"tempFlowUser\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/login").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"tempFlowUser\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
    }

    @Test
    @DisplayName("an expired temporary password cannot be used to log in")
    void expiredTemporaryPasswordIsRefused() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        int userId = createUser("expiredTempUser", "pass123");

        mockMvc.perform(post("/api/users/admin/reset-password/" + userId)
                        .session(adminSession).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // Simulate the TTL having elapsed.
        jdbcTemplate.update("UPDATE users SET temp_password_expires_at = ? WHERE id = ?",
                new Timestamp(System.currentTimeMillis() - 60_000L), userId);

        mockMvc.perform(post("/api/users/login").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"expiredTempUser\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("TEMP_PASSWORD_EXPIRED"));
    }

    @Test
    @DisplayName("issuing a temporary password invalidates the sessions that used the old one")
    void resetInvalidatesExistingSessions() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        int userId = createUser("sessionVictim", "pass123");
        MockHttpSession victimSession = loginUser("sessionVictim", "pass123");

        mockMvc.perform(get("/api/users/current").session(victimSession).header("User-Agent", UA))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/admin/reset-password/" + userId)
                        .session(adminSession).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // The pre-reset session is dropped on its next request.
        mockMvc.perform(get("/api/users/current").session(victimSession).header("User-Agent", UA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_EXPIRED"));

        // A fresh login with the temporary password works.
        mockMvc.perform(post("/api/users/login").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"sessionVictim\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("changing the password keeps the acting session and drops the other devices")
    void forcedChangeKeepsOnlyTheActingSession() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        int userId = createUser("twoDeviceUser", "pass123");

        mockMvc.perform(post("/api/users/admin/reset-password/" + userId)
                        .session(adminSession).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"newPassword\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        MockHttpSession deviceOne = loginViaApi("twoDeviceUser", TEMP_PASSWORD);
        MockHttpSession deviceTwo = loginViaApi("twoDeviceUser", TEMP_PASSWORD);

        changePassword(deviceOne, TEMP_PASSWORD, NEW_PASSWORD, 200);

        mockMvc.perform(get("/api/users/current").session(deviceOne).header("User-Agent", UA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/current").session(deviceTwo).header("User-Agent", UA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("administrator resets are audited without ever logging the password")
    void resetAuditNeverContainsThePassword() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        int userId = createUser("auditTempUser", "pass123");

        try (AuditLogCapture audit = new AuditLogCapture()) {
            mockMvc.perform(post("/api/users/admin/reset-password/" + userId)
                            .session(adminSession).header("User-Agent", UA)
                            .contentType("application/json")
                            .content("{\"newPassword\":\"" + TEMP_PASSWORD + "\"}"))
                    .andExpect(status().isOk());

            assertThat(audit.contains("event=admin_reset_password")).isTrue();
            assertThat(audit.contains("tempPassword=true")).isTrue();
            assertThat(audit.contains("event=password_changed")).isFalse();
            assertThat(audit.joined()).doesNotContain(TEMP_PASSWORD);
        }

        MockHttpSession session = loginViaApi("auditTempUser", TEMP_PASSWORD);
        try (AuditLogCapture audit = new AuditLogCapture()) {
            changePassword(session, TEMP_PASSWORD, NEW_PASSWORD, 200);
            assertThat(audit.contains("event=password_changed")).isTrue();
            assertThat(audit.contains("forced=true")).isTrue();
            assertThat(audit.joined())
                    .doesNotContain(TEMP_PASSWORD)
                    .doesNotContain(NEW_PASSWORD);
        }
    }

    @Test
    @DisplayName("the change endpoint only ever updates the session user")
    void changePasswordAlwaysTargetsTheSessionUser() throws Exception {
        MockHttpSession session = registerAndLogin("selfChangeUser", "pass123");

        // There is no user parameter: the endpoint always updates the caller.
        changePassword(session, "pass123", "Another-Own-Pass!2026", 200);

        loginUser("selfChangeUser", "Another-Own-Pass!2026");
    }

    // ------------------------------------------------------------------ helpers

    private void changePassword(MockHttpSession session, String current, String next,
                                int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/users/password").session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private MockHttpSession loginViaApi(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/login").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Registers a user and returns its id. */
    private int createUser(String username, String password) throws Exception {
        registerUser(username, password);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, username);
        assertThat(id).isNotNull();
        return id;
    }

    private int intFlag(int userId, String column) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM users WHERE id = ?", Integer.class, userId);
        return value == null ? 0 : value;
    }

    private Object rawValue(int userId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM users WHERE id = ?", Object.class, userId);
    }
}
