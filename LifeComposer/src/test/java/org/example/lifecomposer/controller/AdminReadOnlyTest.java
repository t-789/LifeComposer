package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 7 acceptance: browsing the console is strictly read-only. Every GET
 * endpoint (list and detail) is exercised and the full content of all 12 business
 * tables must be byte-identical afterwards.
 */
class AdminReadOnlyTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    private static final List<String> TABLES = List.of(
            "users", "feedback", "user_profiles", "planning_history", "chat_messages",
            "college_credit_rules", "credit_activities", "resources", "rag_chunks",
            "capability_tags", "capability_reference", "chat_usage_daily");

    private static final List<String> ENDPOINTS = List.of(
            "/api/admin/dashboard",
            "/api/admin/users",
            "/api/admin/user-profiles",
            "/api/admin/planning-history",
            "/api/admin/chat-messages",
            "/api/admin/feedback",
            "/api/admin/college-credit-rules",
            "/api/admin/credit-activities",
            "/api/admin/resources",
            "/api/admin/rag-chunks",
            "/api/admin/capability-tags",
            "/api/admin/capability-reference",
            "/api/admin/chat-usage");

    @Test
    @DisplayName("all console reads leave every business table unchanged")
    void consoleReadsAreReadOnly() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        seedRows();

        Map<String, String> before = snapshot();

        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint).session(admin).header("User-Agent", UA))
                    .andExpect(status().isOk());
        }
        // Detail endpoints as well; missing keys must 404 without touching data.
        for (String detail : List.of(
                "/api/admin/users/999999/profile",
                "/api/admin/planning-history/999999",
                "/api/admin/chat-messages/999999",
                "/api/admin/feedback/999999",
                "/api/admin/college-credit-rules/999999",
                "/api/admin/credit-activities/999999",
                "/api/admin/resources/999999",
                "/api/admin/rag-chunks/nope",
                "/api/admin/capability-tags/nope",
                "/api/admin/capability-reference/999999")) {
            mockMvc.perform(get(detail).session(admin).header("User-Agent", UA))
                    .andExpect(status().isNotFound());
        }

        assertThat(snapshot()).isEqualTo(before);
    }

    private void seedRows() {
        Integer adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'admin'", Integer.class);
        jdbcTemplate.update("INSERT INTO chat_messages(user_id, role, content, create_time) "
                + "VALUES (?, 'user', 'readonly-probe', CURRENT_TIMESTAMP)", adminId);
        jdbcTemplate.update("INSERT INTO feedback(user_id, username, content, type, create_time, resolved) "
                + "VALUES (?, 'admin', 'readonly-probe', 'user', CURRENT_TIMESTAMP, 0)", adminId);
        jdbcTemplate.update("INSERT INTO chat_usage_daily(user_id, usage_date, request_count) "
                + "VALUES (?, '2026-09-14', 2)", adminId);
    }

    private Map<String, String> snapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String table : TABLES) {
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList("SELECT * FROM " + table + " ORDER BY 1");
            snapshot.put(table, rows.size() + "|" + rows);
        }
        return snapshot;
    }
}
