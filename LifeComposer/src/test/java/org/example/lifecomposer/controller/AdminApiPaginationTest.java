package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 7 acceptance: pagination, filters and invalid parameters all have a
 * deterministic response, and the server enforces the page-size ceiling.
 */
class AdminApiPaginationTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    @DisplayName("default envelope is page=1, pageSize=50 with totals")
    void defaultEnvelope() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/admin/users").session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    @DisplayName("an empty table answers with an empty list, not an error")
    void emptyTable() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/admin/feedback").session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("page size above the 200 ceiling is rejected")
    void pageSizeCeiling() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/admin/users").param("pageSize", "201")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/admin/users").param("pageSize", "200")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(200));
    }

    @Test
    @DisplayName("page number, sort field and direction are validated")
    void invalidParameters() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/admin/users").param("page", "0")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/users").param("page", "abc")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/users").param("sort", "password_hash")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/admin/users").param("sort", "id; DROP TABLE users")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/users").param("dir", "sideways")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/feedback").param("resolved", "maybe")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/feedback").param("dateFrom", "2026-13-99")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/rag-chunks").param("embeddingStatus", "WHATEVER")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unknown query parameters are rejected instead of silently ignored")
    void unknownParameterRejected() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/admin/users").param("select", "*")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNKNOWN_PARAMETER"));
    }

    @Test
    @DisplayName("seeded rows page correctly and total pages are stable")
    void pagingAcrossPages() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        int adminId = adminId();
        for (int i = 0; i < 120; i++) {
            jdbcTemplate.update("INSERT INTO chat_messages(user_id, role, content, create_time) "
                    + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)", adminId, "user", "message-" + i);
        }

        mockMvc.perform(get("/api/admin/chat-messages").param("pageSize", "50")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(120))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(50));

        mockMvc.perform(get("/api/admin/chat-messages").param("pageSize", "50").param("page", "3")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20));

        // A page beyond the end returns an empty page with a consistent total.
        mockMvc.perform(get("/api/admin/chat-messages").param("pageSize", "50").param("page", "4")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(120));
    }

    @Test
    @DisplayName("the user role/state filters actually restrict the result set")
    void userFiltersRestrictResults() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        registerUser("filteredAdmin", "pass123");
        registerUser("filteredUser", "pass123");
        jdbcTemplate.update("UPDATE users SET type = 2 WHERE username = 'filteredAdmin'");

        mockMvc.perform(get("/api/admin/users").param("type", "2")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.username=='filteredAdmin')]").exists())
                .andExpect(jsonPath("$.items[?(@.username=='filteredUser')]").doesNotExist());

        mockMvc.perform(get("/api/admin/users").param("type", "1")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("filteredUser"));

        mockMvc.perform(get("/api/admin/users").param("q", "filteredAdmin")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("filteredAdmin"));

        mockMvc.perform(get("/api/admin/users").param("hasProfile", "0")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("filters combine and date ranges are inclusive")
    void filtersWork() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        int adminId = adminId();
        jdbcTemplate.update("INSERT INTO chat_messages(user_id, role, content, create_time) "
                + "VALUES (?, 'user', 'hello today', CURRENT_TIMESTAMP)", adminId);
        jdbcTemplate.update("INSERT INTO chat_messages(user_id, role, content, create_time) "
                + "VALUES (?, 'tool', '{\"tool\":\"search_rag\"}', CURRENT_TIMESTAMP)", adminId);

        mockMvc.perform(get("/api/admin/chat-messages").param("role", "tool")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].structured").value(true));

        String today = jdbcTemplate.queryForObject(
                "SELECT date('now')", String.class);
        mockMvc.perform(get("/api/admin/chat-messages")
                        .param("dateFrom", today).param("dateTo", today)
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/admin/chat-messages").param("userId", String.valueOf(adminId + 999))
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("sorting follows the whitelist and returns ordered data")
    void sortingWorks() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        int adminId = adminId();
        jdbcTemplate.update("INSERT INTO chat_messages(user_id, role, content, create_time) "
                + "VALUES (?, 'user', 'aaa', CURRENT_TIMESTAMP)", adminId);
        jdbcTemplate.update("INSERT INTO chat_messages(user_id, role, content, create_time) "
                + "VALUES (?, 'user', 'bbb', CURRENT_TIMESTAMP)", adminId);

        String firstAsc = mockMvc.perform(get("/api/admin/chat-messages")
                        .param("sort", "id").param("dir", "asc")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String firstDesc = mockMvc.perform(get("/api/admin/chat-messages")
                        .param("sort", "id").param("dir", "desc")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(firstAsc).isNotEqualTo(firstDesc);
    }

    private int adminId() {
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'admin'", Integer.class);
        assertThat(id).isNotNull();
        return id;
    }
}
