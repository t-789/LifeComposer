package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FeedbackControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    @DisplayName("POST /api/feedback/submit - without auth succeeds (public endpoint)")
    void submitFeedback_withoutAuth_succeeds() throws Exception {
        mockMvc.perform(post("/api/feedback/submit")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"content\":\"Test feedback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("反馈提交成功"));
    }

    @Test
    @DisplayName("POST /api/feedback/submit - valid feedback with auth succeeds")
    void submitFeedback_withAuth_succeeds() throws Exception {
        MockHttpSession session = registerAndLogin("fbuser", "pass123");

        mockMvc.perform(post("/api/feedback/submit")
                        .session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"content\":\"Authenticated feedback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("反馈提交成功"));
    }

    @Test
    @DisplayName("POST /api/feedback/submit - empty content returns 400")
    void submitFeedback_emptyContent_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/feedback/submit")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("反馈内容不能为空"));
    }

    @Test
    @DisplayName("POST /api/feedback/system-error - public endpoint succeeds")
    void submitSystemError_withoutAuth_succeeds() throws Exception {
        mockMvc.perform(post("/api/feedback/system-error")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"content\":\"NullPointer at line 42\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("系统错误报告已提交"));
    }

    @Test
    @DisplayName("GET /api/feedback/all - non-admin gets 403")
    void allFeedback_nonAdmin_returnsForbidden() throws Exception {
        MockHttpSession session = registerAndLogin("fbnormal", "pass123");

        mockMvc.perform(get("/api/feedback/all").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/feedback/all - admin gets 200")
    void allFeedback_admin_returnsOk() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/feedback/all").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/feedback/all - unauthenticated gets 403")
    void allFeedback_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/feedback/all")
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }
}
