package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 7 acceptance: the admin API answers with allow-listed projections.
 * Password hashes, raw embedding vectors, certificate paths and unmasked student
 * ids must never leave the server.
 */
class AdminApiPrivacyTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Test
    @DisplayName("user list never exposes password_hash or the stored hash")
    void usersNeverExposePasswordHash() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE username = 'admin'", String.class);

        String body = mockMvc.perform(get("/api/admin/users").session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hasProfile").exists())
                .andExpect(jsonPath("$.items[0].usedToday").exists())
                .andExpect(jsonPath("$.items[0].dailyLimit").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password_hash");
        assertThat(body).doesNotContain("passwordHash");
        assertThat(hash).isNotNull();
        assertThat(body).doesNotContain(hash);
    }

    @Test
    @DisplayName("dashboard and usage payloads contain no credentials")
    void dashboardNeverExposesCredentials() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE username = 'admin'", String.class);

        String dashboard = mockMvc.perform(get("/api/admin/dashboard")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String usage = mockMvc.perform(get("/api/admin/chat-usage")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(dashboard).doesNotContain(hash);
        assertThat(usage).doesNotContain(hash);
        assertThat(dashboard).doesNotContain("password_hash");
    }

    @Test
    @DisplayName("profile list masks the student id and hides preferences")
    void profilesMaskSensitiveFields() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        int adminId = adminId();
        jdbcTemplate.update("INSERT INTO user_profiles(user_id, college, major, grade, student_id, "
                        + "skills_json, interests_json, experiences_json, preferences_json, goals, "
                        + "created_at, updated_at) VALUES (?, '计算机学院', '软件工程', '大三', "
                        + "'2023210123', '[\"Java\"]', '[\"AI\"]', '[]', '{\"theme\":\"dark\"}', '[\"考研\"]', "
                        + "datetime('now'), datetime('now'))", adminId);

        String list = mockMvc.perform(get("/api/admin/user-profiles")
                        .param("userId", String.valueOf(adminId))
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].studentId").value("202****23"))
                .andExpect(jsonPath("$.items[0].hasPreferences").value(true))
                .andExpect(jsonPath("$.items[0].preferencesJson").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(list).doesNotContain("2023210123");
        assertThat(list).doesNotContain("theme");

        mockMvc.perform(get("/api/admin/users/" + adminId + "/profile")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value("2023210123"))
                .andExpect(jsonPath("$.preferencesJson").value("{\"theme\":\"dark\"}"));
    }

    @Test
    @DisplayName("RAG views expose embedding metadata but never the vector itself")
    void ragNeverExposesEmbeddingVector() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String vector = "[" + "0.123456,".repeat(20) + "0.5]";
        jdbcTemplate.update("INSERT INTO rag_chunks(chunk_id, title, text, source_type, embedding_json, "
                        + "embedding_model, embedding_dimensions, embedding_status, content_hash, created_at) "
                        + "VALUES ('privacy_probe_1', '隐私探针', '正文', 'web', ?, 'test-model', 768, "
                        + "'SUCCESS', 'hash-privacy-probe', '2026-09-14')", vector);

        String list = mockMvc.perform(get("/api/admin/rag-chunks")
                        .param("q", "privacy_probe")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].embeddingJsonLength").value(vector.length()))
                .andExpect(jsonPath("$.items[0].embeddingDimensions").value(768))
                .andExpect(jsonPath("$.items[0].embeddingJson").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String detail = mockMvc.perform(get("/api/admin/rag-chunks/privacy_probe_1")
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHash").value("hash-privacy-probe"))
                .andExpect(jsonPath("$.embeddingJson").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(list).doesNotContain("0.123456");
        assertThat(detail).doesNotContain("0.123456");
        jdbcTemplate.update("DELETE FROM rag_chunks WHERE chunk_id = 'privacy_probe_1'");
    }

    @Test
    @DisplayName("credit activities expose certificate presence, not the stored path")
    void creditActivitiesHideCertificatePath() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        int adminId = adminId();
        jdbcTemplate.update("INSERT INTO credit_activities(user_id, credit_type, category, comp_name, "
                        + "credits, certificate_ref, verified, notes, created_at) VALUES (?, 'graduation', "
                        + "'competition', '隐私竞赛', 2.0, '/srv/secret/certificates/abc.pdf', 1, '备注', "
                        + "datetime('now'))", adminId);

        String body = mockMvc.perform(get("/api/admin/credit-activities")
                        .param("userId", String.valueOf(adminId))
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hasCertificate").value(true))
                .andExpect(jsonPath("$.items[0].certificateRef").doesNotExist())
                .andExpect(jsonPath("$.items[0].certificate_ref").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("/srv/secret");
    }

    @Test
    @DisplayName("feedback list carries no stack trace; the detail view truncates it")
    void feedbackDiagnosticsAreControlled() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String stackTrace = "java.lang.IllegalStateException: boom\n" + "\tat com.example.Foo.bar(Foo.java:1)\n".repeat(400);
        jdbcTemplate.update("INSERT INTO feedback(user_id, username, content, type, url, user_agent, "
                        + "stack_trace, create_time, resolved) VALUES (NULL, 'tester', '崩溃了', 'system', "
                        + "'/api/boom', 'JUnit', ?, CURRENT_TIMESTAMP, 0)", stackTrace);

        Long feedbackId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM feedback", Long.class);

        String list = mockMvc.perform(get("/api/admin/feedback").session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hasStackTrace").value(true))
                .andExpect(jsonPath("$.items[0].stackTrace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(list).doesNotContain("Foo.java");

        String detail = mockMvc.perform(get("/api/admin/feedback/" + feedbackId)
                        .session(admin).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stackTrace").exists())
                .andExpect(jsonPath("$.stackTraceLength").value(stackTrace.length()))
                .andReturn().getResponse().getContentAsString();

        assertThat(detail.length()).isLessThan(stackTrace.length());
        assertThat(detail).contains("Foo.java");
    }

    @Test
    @DisplayName("no admin response mentions forbidden internal fields")
    void noForbiddenFieldNamesAnywhere() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        String[] endpoints = {
                "/api/admin/dashboard", "/api/admin/users", "/api/admin/user-profiles",
                "/api/admin/planning-history", "/api/admin/chat-messages", "/api/admin/feedback",
                "/api/admin/college-credit-rules", "/api/admin/credit-activities",
                "/api/admin/resources", "/api/admin/rag-chunks",
                "/api/admin/capability-tags", "/api/admin/capability-reference", "/api/admin/chat-usage"
        };
        for (String endpoint : endpoints) {
            String body = mockMvc.perform(get(endpoint).session(admin).header("User-Agent", UA))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body)
                    .as("endpoint %s must not leak sensitive fields", endpoint)
                    .doesNotContain("password_hash")
                    .doesNotContain("passwordHash")
                    .doesNotContain("embedding_json")
                    .doesNotContain("embeddingJson\"")
                    .doesNotContain("certificate_ref")
                    .doesNotContain("certificateRef");
        }
    }

    private int adminId() {
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'admin'", Integer.class);
        assertThat(id).isNotNull();
        return id;
    }
}
