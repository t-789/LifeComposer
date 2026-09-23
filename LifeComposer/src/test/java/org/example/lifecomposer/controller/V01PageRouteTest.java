package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.1 page routes: the formal profile page and the chat_test debug workbench. */
class V01PageRouteTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    @DisplayName("GET /front/profile renders the formal profile form")
    void profilePageRenders() throws Exception {
        MockHttpSession session = registerAndLogin("pageuser1", "pass123");
        mockMvc.perform(get("/front/profile").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("我的成长画像")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/profiles/me")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&quot;type&quot;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("蓝桥杯")));
    }

    @Test
    @DisplayName("GET /front/chat_test renders confirmation card area and reusable modules")
    void chatTestRendersDebugWorkbench() throws Exception {
        MockHttpSession session = registerAndLogin("pageuser2", "pass123");
        mockMvc.perform(get("/front/chat_test").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("confirmationArea")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/sse-client.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("debugRecommendations")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("app-shell")));
    }

    @Test
    @DisplayName("v0.1 pages require authentication")
    void pagesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/front/profile").header("User-Agent", UA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/front/chat_test").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }
}
