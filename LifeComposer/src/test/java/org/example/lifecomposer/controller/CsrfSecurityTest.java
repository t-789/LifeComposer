package org.example.lifecomposer.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CsrfSecurityTest extends BaseControllerTest {

    @Test
    void stateChangingRequestWithoutTokenIsForbidden() throws Exception {
        mockMvcWithoutCsrf.perform(post("/api/users/register")
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"csrfuser1\",\"password\":\"testpass123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void stateChangingRequestWithMismatchedTokenIsForbidden() throws Exception {
        mockMvcWithoutCsrf.perform(post("/api/users/register")
                        .header("User-Agent", "TestClient/1.0")
                        .cookie(new Cookie("XSRF-TOKEN", "cookie-token"))
                        .header("X-XSRF-TOKEN", "header-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"csrfuser2\",\"password\":\"testpass123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void stateChangingRequestWithValidTokenIsAccepted() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .header("User-Agent", "TestClient/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"csrfuser3\",\"password\":\"testpass123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void safeGetDoesNotRequireToken() throws Exception {
        mockMvcWithoutCsrf.perform(get("/api/qa/health")
                        .header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk());
    }

    @Test
    void csrfEndpointExposesToken() throws Exception {
        mockMvcWithoutCsrf.perform(get("/api/csrf").header("User-Agent", "TestClient/1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
