package org.example.lifecomposer.controller;

import org.example.lifecomposer.support.AuditLogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 7 acceptance: console access and business actions are audited with
 * identity/action/result only — never the inspected content.
 */
class AdminAuditLogTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    @DisplayName("a console read writes an admin_query audit line with dataset and size")
    void readIsAudited() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        try (AuditLogCapture audit = new AuditLogCapture()) {
            mockMvc.perform(get("/api/admin/users").session(admin).header("User-Agent", UA))
                    .andExpect(status().isOk());

            assertThat(audit.contains("event=admin_query")).isTrue();
            assertThat(audit.contains("dataset=users")).isTrue();
            assertThat(audit.contains("admin=admin")).isTrue();
            assertThat(audit.contains("total=")).isTrue();
        }
    }

    @Test
    @DisplayName("the audit trail never records the content being inspected")
    void inspectedContentIsNotLogged() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String secret = "TOP-SECRET-FEEDBACK-CONTENT";
        jdbcTemplate.update("INSERT INTO feedback(user_id, username, content, type, create_time, resolved) "
                + "VALUES (NULL, 'reporter', ?, 'user', CURRENT_TIMESTAMP, 0)", secret);

        try (AuditLogCapture audit = new AuditLogCapture()) {
            mockMvc.perform(get("/api/admin/feedback").session(admin).header("User-Agent", UA))
                    .andExpect(status().isOk());

            assertThat(audit.contains("dataset=feedback")).isTrue();
            assertThat(audit.joined()).doesNotContain(secret);
        }
    }

    @Test
    @DisplayName("a rejected query is audited without echoing the offending value")
    void rejectedQueryIsAudited() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        try (AuditLogCapture audit = new AuditLogCapture()) {
            mockMvc.perform(get("/api/admin/users").param("sort", "evil-column")
                            .session(admin).header("User-Agent", UA))
                    .andExpect(status().isBadRequest());

            assertThat(audit.contains("event=admin_query_rejected")).isTrue();
            assertThat(audit.joined()).doesNotContain("evil-column");
        }
    }

    @Test
    @DisplayName("console reads do not consume the chat quota limiter")
    void consoleReadsDoNotTouchChatQuota() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        int adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'admin'", Integer.class);

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/admin/users").session(admin).header("User-Agent", UA))
                    .andExpect(status().isOk());
        }

        assertThat(inMemoryMinuteRateLimiter.retryAfterSeconds(adminId)).isZero();
    }
}
