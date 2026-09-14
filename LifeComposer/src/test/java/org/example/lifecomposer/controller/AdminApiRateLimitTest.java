package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 7 acceptance: console reads are rate limited per administrator so a
 * single operator cannot exhaust the 2GB/2-core host with repeated wide queries.
 *
 * <p>Runs in its own context with a deliberately tiny limit.</p>
 */
@SpringBootTest(properties = "lifecomposer.admin.console-queries-per-minute=3")
class AdminApiRateLimitTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    @DisplayName("the fourth query in the same minute is rejected with 429")
    void fourthQueryIsRateLimited() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/admin/users").session(admin).header("User-Agent", UA))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/admin/users").session(admin).header("User-Agent", UA))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("ADMIN_RATE_LIMIT"));
    }
}
