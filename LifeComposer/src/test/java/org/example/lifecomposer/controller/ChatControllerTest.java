package org.example.lifecomposer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatControllerTest extends BaseControllerTest {

    @Test
    void sendMessage_withAuth_returnsOk() throws Exception {
        MockHttpSession session = registerAndLogin("chatuser1", "testpass123");

        mockMvc.perform(post("/api/chat/send")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.mocked").value(true));
    }

    @Test
    void sendMessage_withoutAuth_isDeniedBySecurity() throws Exception {
        // /api/chat/** is registered in WebSecurityConfig's authenticated() chain.
        // Spring Security returns 403 for unauthenticated requests to protected endpoints.
        mockMvc.perform(post("/api/chat/send")
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\u4f60\u597d\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getHistory_withAuth_returnsList() throws Exception {
        MockHttpSession session = registerAndLogin("chatuser2", "testpass123");

        // Send a message first to populate history
        mockMvc.perform(post("/api/chat/send")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk());

        // Then get history — should contain user + assistant messages
        mockMvc.perform(get("/api/chat/history")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].role").value("assistant"));
    }

    @Test
    void clearContext_withAuth_returnsDeletedCount() throws Exception {
        MockHttpSession session = registerAndLogin("chatuser3", "testpass123");

        // Send a message
        mockMvc.perform(post("/api/chat/send")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"测试消息\"}"))
                .andExpect(status().isOk());

        // Verify history is non-empty
        mockMvc.perform(get("/api/chat/history")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // Clear context
        mockMvc.perform(delete("/api/chat/context")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // Verify history is now empty
        mockMvc.perform(get("/api/chat/history")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getHistory_returnsOnlyOwnMessages() throws Exception {
        MockHttpSession sessionA = registerAndLogin("chatuserA", "testpass123");

        // Send a message as user A
        mockMvc.perform(post("/api/chat/send")
                        .session(sessionA)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"用户A的消息\"}"))
                .andExpect(status().isOk());

        // Login as user B
        MockHttpSession sessionB = registerAndLogin("chatuserB", "testpass123");

        // User B should have empty history
        mockMvc.perform(get("/api/chat/history")
                        .session(sessionB)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sendMessage_withEmptyMessage_returns400() throws Exception {
        MockHttpSession session = registerAndLogin("chatuser4", "testpass123");

        mockMvc.perform(post("/api/chat/send")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
