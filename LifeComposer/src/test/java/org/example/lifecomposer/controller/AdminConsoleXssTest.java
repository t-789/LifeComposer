package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 7 acceptance: hostile HTML stored by users stays data.
 *
 * <p>The API answers with {@code application/json} and the console renders every
 * value through DOM text nodes (see {@link AdminFrontendSafetyTest}), so a stored
 * {@code <script>} payload is displayed as text and never executed.</p>
 */
class AdminConsoleXssTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";
    private static final String PAYLOAD = "<img src=x onerror=alert(1)>";

    @Test
    @DisplayName("a malicious username is returned as JSON data, not as HTML")
    void maliciousUsernameStaysData() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        jdbcTemplate.update("INSERT INTO users(username, password_hash, type, is_banned, created_at, updated_at) "
                + "VALUES (?, '$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 1, 0, "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", PAYLOAD);

        String body = mockMvc.perform(get("/api/admin/users").param("q", "onerror")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString();

        // The raw value is preserved for the operator but the response is JSON,
        // so a browser never treats it as markup.
        assertThat(body).contains(PAYLOAD);
        assertThat(mockMvc.perform(get("/api/admin/users").param("q", "onerror")
                        .session(admin).header("User-Agent", UA))
                .andReturn().getResponse().getContentType()).doesNotContain("text/html");
    }

    @Test
    @DisplayName("malicious feedback content is returned verbatim inside JSON")
    void maliciousFeedbackStaysData() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String payload = "<script>alert('stored-xss')</script>";
        jdbcTemplate.update("INSERT INTO feedback(user_id, username, content, type, create_time, resolved) "
                + "VALUES (NULL, 'attacker', ?, 'user', CURRENT_TIMESTAMP, 0)", payload);

        String body = mockMvc.perform(get("/api/admin/feedback")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(payload);
    }

    @Test
    @DisplayName("a LIKE filter cannot inject SQL: wildcards are escaped")
    void likeWildcardsAreEscaped() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        jdbcTemplate.update("INSERT INTO users(username, password_hash, type, is_banned, created_at, updated_at) "
                + "VALUES ('percent_100', '$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', "
                + "1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO users(username, password_hash, type, is_banned, created_at, updated_at) "
                + "VALUES ('percentX100', '$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', "
                + "1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        // "_" must be a literal underscore, so the second user is not matched.
        mockMvc.perform(get("/api/admin/users").param("q", "percent_100")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/admin/users").param("q", "percent%100")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.total").value(0));
    }
}
