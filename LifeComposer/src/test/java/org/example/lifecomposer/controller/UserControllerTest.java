package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.assertj.core.api.Assertions.assertThat;

class UserControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    void publicPagesAndAdminLoginHaveSeparateRoutes() throws Exception {
        mockMvcWithoutCsrf.perform(get("/front/login").header("User-Agent", UA))
                .andExpect(status().isOk()).andExpect(view().name("zhitu_auth"));
        mockMvcWithoutCsrf.perform(get("/front/register").header("User-Agent", UA))
                .andExpect(status().isOk()).andExpect(view().name("zhitu_auth"));
        mockMvcWithoutCsrf.perform(get("/adminlogin").header("User-Agent", UA))
                .andExpect(status().isOk()).andExpect(view().name("login"));
    }

    @Test
    void registerPersistsEmailAndNameAndSupportsCaseInsensitiveEmailLogin() throws Exception {
        mockMvc.perform(post("/api/users/register").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"20261234\",\"realName\":\"小王\","
                                + "\"email\":\"Student@Example.edu\",\"password\":\"pass12345\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE username = '20261234'", String.class))
                .isEqualTo("student@example.edu");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT real_name FROM users WHERE username = '20261234'", String.class))
                .isEqualTo("小王");
        mockMvc.perform(post("/api/users/login").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"STUDENT@example.edu\",\"password\":\"pass12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("20261234"));
        mockMvc.perform(post("/api/users/register").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"20261235\",\"email\":\"student@EXAMPLE.edu\","
                                + "\"password\":\"pass12345\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEmailIsRejectedAndLegacyRegistrationKeepsNullEmail() throws Exception {
        mockMvc.perform(post("/api/users/register").header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"bademailuser\",\"email\":\"not-an-email\","
                                + "\"password\":\"pass12345\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.email").exists());
        registerUser("legacyuser", "pass123");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE username = 'legacyuser'", String.class)).isNull();
    }

    @Test
    @DisplayName("POST /api/users/register - happy path returns 200")
    void register_happyPath_returnsOk() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/users/register - duplicate username returns 400")
    void register_duplicateUsername_returnsBadRequest() throws Exception {
        registerUser("dupuser", "pass123");

        mockMvc.perform(post("/api/users/register")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"dupuser\",\"password\":\"pass456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/users/register - username too short returns 400")
    void register_usernameTooShort_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"ab\",\"password\":\"pass123\"}"))
                .andExpect(status().isBadRequest())
                // real Spring context: Bean Validation must be owned by the
                // field-map advice, not the global plain-text catch-all
                .andExpect(jsonPath("$.username").exists());
    }

    @Test
    @DisplayName("POST /api/users/register - blank password returns 400")
    void register_blankPassword_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"validuser\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").exists());
    }

    @Test
    @DisplayName("POST /api/users/login - happy path returns 200 with session")
    void login_happyPath_returnsOk() throws Exception {
        registerUser("loginuser", "pass123");

        mockMvc.perform(post("/api/users/login")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"loginuser\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/users/login - wrong password returns 400")
    void login_wrongPassword_returnsBadRequest() throws Exception {
        registerUser("wrongpwuser", "pass123");

        mockMvc.perform(post("/api/users/login")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"wrongpwuser\",\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/users/login - non-existent user returns 400")
    void login_nonExistentUser_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/login")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"username\":\"ghostuser\",\"password\":\"pass123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/users/current - unauthenticated returns 403 (Spring Security default)")
    void current_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/current")
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/current - authenticated returns user info")
    void current_authenticated_returnsUserInfo() throws Exception {
        MockHttpSession session = registerAndLogin("currentuser", "pass123");

        mockMvc.perform(get("/api/users/current").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("currentuser"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("POST /api/users/logout - clears session")
    void logout_clearsSession() throws Exception {
        MockHttpSession session = registerAndLogin("logoutuser", "pass123");

        mockMvc.perform(post("/api/users/logout").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/current").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/all - non-admin gets 403")
    void allUsers_nonAdmin_returnsForbidden() throws Exception {
        MockHttpSession session = registerAndLogin("normaluser", "pass123");

        mockMvc.perform(get("/api/users/all").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/all - admin gets 200")
    void allUsers_admin_returnsOk() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/api/users/all").session(session)
                        .header("User-Agent", UA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users/all - unauthenticated gets 403")
    void allUsers_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/users/all")
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }
}
