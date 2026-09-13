package org.example.lifecomposer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatControllerTest extends BaseControllerTest {

    @Test
    void sendMessage_withAuth_returnsServiceUnavailableWhenChatDisabled() throws Exception {
        MockHttpSession session = registerAndLogin("chatuser1", "testpass123");

        mockMvc.perform(post("/api/chat/send")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("LLM 不可用，请稍后重试"));
    }

    @Test
    void sendMessage_withoutAuth_isDeniedBySecurity() throws Exception {
        mockMvc.perform(post("/api/chat/send")
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\u4f60\u597d\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void streamMessage_withoutAuth_isDeniedBySecurity() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\u4f60\u597d\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getHistory_afterDisabledSend_keepsOnlyUserMessage() throws Exception {
        MockHttpSession session = registerAndLogin("chatuser2", "testpass123");

        mockMvc.perform(post("/api/chat/send")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/chat/history")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("你好"));
    }

    @Test
    void clearContext_withAuth_returnsDeletedCount() throws Exception {
        MockHttpSession session = registerAndLogin("chatuser3", "testpass123");

        mockMvc.perform(post("/api/chat/send")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"测试消息\"}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(delete("/api/chat/context")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/chat/history")
                        .session(session)
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getHistory_returnsOnlyOwnMessages() throws Exception {
        MockHttpSession sessionA = registerAndLogin("chatuserA", "testpass123");

        mockMvc.perform(post("/api/chat/send")
                        .session(sessionA)
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"用户A的消息\"}"))
                .andExpect(status().isServiceUnavailable());

        MockHttpSession sessionB = registerAndLogin("chatuserB", "testpass123");

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
