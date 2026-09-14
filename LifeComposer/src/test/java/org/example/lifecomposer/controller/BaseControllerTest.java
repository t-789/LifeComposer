package org.example.lifecomposer.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.example.lifecomposer.Service.InMemoryMinuteRateLimiter;
import org.example.lifecomposer.Service.LoginAttemptService;
import org.example.lifecomposer.Service.RegistrationRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.MockMvcConfigurer;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseControllerTest {

    /** Strong-enough administrator password used by tests (never the legacy default). */
    protected static final String ADMIN_PASSWORD = "AdminTestPassw0rd!2026";

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RegistrationRateLimiter registrationRateLimiter;

    @Autowired
    protected LoginAttemptService loginAttemptService;

    @Autowired
    protected InMemoryMinuteRateLimiter inMemoryMinuteRateLimiter;

    /** CSRF-enabled MockMvc: state-changing requests automatically carry a valid token. */
    protected MockMvc mockMvc;

    /** Plain MockMvc for explicit CSRF rejection tests. */
    protected MockMvc mockMvcWithoutCsrf;

    @BeforeEach
    void setupMockMvc() {
        mockMvcWithoutCsrf = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .apply(csrfTokenConfigurer())
                .build();

        jdbcTemplate.execute("DELETE FROM chat_usage_daily");
        jdbcTemplate.execute("DELETE FROM chat_messages");
        jdbcTemplate.execute("DELETE FROM planning_history");
        jdbcTemplate.execute("DELETE FROM feedback");
        jdbcTemplate.execute("DELETE FROM user_profiles");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("INSERT INTO users(username, password_hash, type, is_banned, created_at, updated_at) " +
                "VALUES ('admin', '$2a$10$dummyhashforadmintestonly1', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        registrationRateLimiter.reset();
        loginAttemptService.reset();
        inMemoryMinuteRateLimiter.resetAll();
    }

    private static MockMvcConfigurer csrfTokenConfigurer() {
        return new MockMvcConfigurer() {
            @Override
            public RequestPostProcessor beforeMockMvcCreated(ConfigurableMockMvcBuilder<?> builder,
                                                             WebApplicationContext context) {
                return withValidCsrfToken();
            }
        };
    }

    /**
     * Creates a CSRF token/cookie pair accepted by CookieCsrfTokenRepository and
     * sends it through the X-XSRF-TOKEN alias header.
     */
    protected static RequestPostProcessor withValidCsrfToken() {
        return request -> {
            String token = UUID.randomUUID().toString();
            Cookie csrfCookie = new Cookie("XSRF-TOKEN", token);
            Cookie[] existing = request.getCookies();
            if (existing == null || existing.length == 0) {
                request.setCookies(csrfCookie);
            } else {
                Cookie[] merged = Arrays.copyOf(existing, existing.length + 1);
                merged[existing.length] = csrfCookie;
                request.setCookies(merged);
            }
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
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
        // Milestone 6: tests must not write the removed legacy default password
        // into the database — the startup guard rejects it on the next boot.
        jdbcTemplate.execute("UPDATE users SET password_hash = '" +
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(ADMIN_PASSWORD) +
                "' WHERE username = 'admin'");
        return loginUser("admin", ADMIN_PASSWORD);
    }
}
