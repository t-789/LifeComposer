package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 7 acceptance: every admin console page and API is admin-only.
 */
class AdminApiSecurityTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @ParameterizedTest
    @ValueSource(strings = {
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
            "/api/admin/chat-usage"
    })
    @DisplayName("list endpoints: admin gets 200")
    void listEndpointsAdminOk(String endpoint) throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get(endpoint).session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/users/1/profile",
            "/api/admin/planning-history/999999",
            "/api/admin/chat-messages/999999",
            "/api/admin/feedback/999999",
            "/api/admin/college-credit-rules/999999",
            "/api/admin/credit-activities/999999",
            "/api/admin/resources/999999",
            "/api/admin/rag-chunks/does-not-exist",
            "/api/admin/capability-tags/不存在的标签",
            "/api/admin/capability-reference/999999"
    })
    @DisplayName("detail endpoints: admin gets 404 for a missing record, never 403/500")
    void detailEndpointsAdminNotFound(String endpoint) throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get(endpoint).session(admin).header("User-Agent", UA))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/dashboard",
            "/api/admin/users",
            "/api/admin/feedback",
            "/api/admin/rag-chunks",
            "/api/admin/chat-usage"
    })
    @DisplayName("unauthenticated API access is rejected")
    void unauthenticatedRejected(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint).header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/dashboard",
            "/api/admin/users",
            "/api/admin/feedback",
            "/api/admin/rag-chunks",
            "/api/admin/chat-usage"
    })
    @DisplayName("a normal user is rejected from every admin API")
    void normalUserRejected(String endpoint) throws Exception {
        MockHttpSession user = registerAndLogin("consoleNormalUser", "pass123");

        mockMvc.perform(get(endpoint).session(user).header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin",
            "/admin/user",
            "/admin/profile",
            "/admin/planning",
            "/admin/chat",
            "/admin/feedback_management",
            "/admin/credit-rules",
            "/admin/credit-activities",
            "/admin/resources",
            "/admin/rag",
            "/admin/capability-tags",
            "/admin/capability-reference",
            "/admin/usage"
    })
    @DisplayName("admin console pages are rejected for a normal user")
    void normalUserRejectedFromPages(String page) throws Exception {
        MockHttpSession user = registerAndLogin("consolePageUser", "pass123");

        mockMvc.perform(get(page).session(user).header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/user", "/admin/usage"})
    @DisplayName("admin console pages are rejected for anonymous visitors")
    void unauthenticatedRejectedFromPages(String page) throws Exception {
        mockMvc.perform(get(page).header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an administrator can open the console pages")
    void adminOpensConsolePages() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        for (String page : new String[]{"/admin", "/admin/user", "/admin/usage"}) {
            mockMvc.perform(get(page).session(admin).header("User-Agent", UA))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("data-admin-view")));
        }
    }
}
