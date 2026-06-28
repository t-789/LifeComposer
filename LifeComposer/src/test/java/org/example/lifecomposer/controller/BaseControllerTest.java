package org.example.lifecomposer.controller;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseControllerTest {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM planning_history");
        jdbcTemplate.execute("DELETE FROM feedback");
        jdbcTemplate.execute("DELETE FROM goals");
        jdbcTemplate.execute("DELETE FROM user_profiles");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("INSERT INTO users(username, password_hash, type, is_banned, created_at, updated_at) " +
                "VALUES ('admin', '$2a$10$dummyhashforadmintestonly1', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    protected MockHttpSession registerAndLogin(String username, String password) throws Exception {
        registerUser(username, password);
        return loginUser(username, password);
    }

    protected void registerUser(String username, String password) throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .header("User-Agent", "TestClient/1.0")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
    }

    protected MockHttpSession loginUser(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/login")
                        .header("User-Agent", "TestClient/1.0")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected MockHttpSession loginAsAdmin() throws Exception {
        jdbcTemplate.execute("UPDATE users SET password_hash = '" +
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("admin") +
                "' WHERE username = 'admin'");
        return loginUser("admin", "admin");
    }
}
