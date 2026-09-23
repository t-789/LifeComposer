package org.example.lifecomposer.controller;

import org.example.lifecomposer.Exception.ProfileApiException;
import org.example.lifecomposer.Repository.ProfileChangeCandidateRepository;
import org.example.lifecomposer.Service.ChatQuotaService;
import org.example.lifecomposer.Service.ProfileChangeService;
import org.example.lifecomposer.agent.AgentToolContext;
import org.example.lifecomposer.agent.ToolRegistry;
import org.example.lifecomposer.agent.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileChangeFlowTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Autowired
    private ProfileChangeService profileChangeService;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ChatQuotaService chatQuotaService;

    @Autowired
    private ProfileChangeCandidateRepository candidateRepository;

    @Autowired
    private Clock clock;

    @BeforeEach
    void cleanupCandidates() {
        jdbcTemplate.execute("DELETE FROM profile_change_candidates");
    }

    private Long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    @Test
    @DisplayName("propose only creates a pending candidate and never writes the formal profile")
    void proposeCreatesPendingCandidate() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser1", "pass123");
        Long id = userId("changeuser1");

        List<org.example.lifecomposer.Entity.ProfileChangeCandidate> created = profileChangeService.propose(
                id, List.of(new ProfileChangeService.ProfileChangeProposal(
                        "availableTime", "每周 6 小时", "用户说每周能投入 6 小时")), "测试");

        assertEquals(1, created.size());
        assertEquals("PENDING_CONFIRMATION", created.get(0).getStatus());

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/profiles/change-candidates").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].field").value("availableTime"))
                .andExpect(jsonPath("$[0].newValue").value("每周 6 小时"))
                .andExpect(jsonPath("$[0].status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$[0].rationale").value("用户说每周能投入 6 小时"));
    }

    @Test
    @DisplayName("confirm merges into the profile and repeated confirm is idempotent")
    void confirmMergesAndIsIdempotent() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser2", "pass123");
        Long id = userId("changeuser2");

        mockMvc.perform(put("/api/profiles/me").session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"skillsJson\":\"[\\\"Java\\\"]\"}"))
                .andExpect(status().isOk());

        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("skillsJson", "[\"Python\"]", "用户说会 Python")), "测试");
        String candidateId = created.get(0).getCandidateId();

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.mergedVersion").isNumber())
                .andExpect(jsonPath("$.agentMessage").isString());

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillsJson").value("[\"Java\",\"Python\"]"));

        // Second confirm returns the original decision without re-merging or bumping the version again.
        Long versionAfterFirst = jdbcTemplate.queryForObject(
                "SELECT version FROM user_profiles WHERE user_id = ?", Long.class, id);
        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        Long versionAfterSecond = jdbcTemplate.queryForObject(
                "SELECT version FROM user_profiles WHERE user_id = ?", Long.class, id);
        assertEquals(versionAfterFirst, versionAfterSecond);
    }

    @Test
    @DisplayName("reject records the optional reason and never writes the profile")
    void rejectDoesNotWriteProfile() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser3", "pass123");
        Long id = userId("changeuser3");

        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("goals", "[\"参加竞赛\"]", "用户提到想参赛")), "测试");
        String candidateId = created.get(0).getCandidateId();

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"REJECT\",\"reason\":\"暂时不想参赛\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/profiles/change-candidates/" + candidateId)
                        .session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("暂时不想参赛"));
    }

    @Test
    @DisplayName("a candidate is scoped to its owner")
    void candidateIsUserScoped() throws Exception {
        registerAndLogin("changeuserA", "pass123");
        Long userA = userId("changeuserA");
        var created = profileChangeService.propose(userA, List.of(
                new ProfileChangeService.ProfileChangeProposal("availableTime", "3 hours/week", "A 说的")), "测试");
        String candidateId = created.get(0).getCandidateId();

        MockHttpSession sessionB = registerAndLogin("changeuserB", "pass123");
        mockMvc.perform(get("/api/profiles/change-candidates/" + candidateId)
                        .session(sessionB).header("User-Agent", UA))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(sessionB).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("expired candidate is not merged even when the user later confirms")
    void expiredCandidateIsNotMerged() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser4", "pass123");
        Long id = userId("changeuser4");
        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("availableTime", "5 hours/week", "过期测试")), "测试");
        String candidateId = created.get(0).getCandidateId();
        jdbcTemplate.update("UPDATE profile_change_candidates SET expires_at = datetime('now', '-1 hour') "
                + "WHERE candidate_id = ?", candidateId);

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a concurrent profile update turns the candidate into CONFLICT")
    void staleCandidateBecomesConflict() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser5", "pass123");
        Long id = userId("changeuser5");
        mockMvc.perform(put("/api/profiles/me").session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"college\":\"计算机学院\"}"))
                .andExpect(status().isOk());

        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("availableTime", "4 hours/week", "冲突测试")), "测试");
        String candidateId = created.get(0).getCandidateId();

        // Another request changes the SAME field, so this is a real conflict.
        mockMvc.perform(put("/api/profiles/me").session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"availableTime\":\"1 hour/week\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFLICT"));

        String stored = jdbcTemplate.queryForObject(
                "SELECT available_time FROM user_profiles WHERE user_id = ?", String.class, id);
        assertEquals("1 hour/week", stored, "冲突时应保留另一方写入的值，而不是候选值");
    }

    @Test
    @DisplayName("chat-editable fields exclude identity fields")
    void identityFieldsCannotBeProposed() throws Exception {
        registerAndLogin("changeuser6", "pass123");
        Long id = userId("changeuser6");
        ProfileApiException ex = assertThrows(ProfileApiException.class, () -> profileChangeService.propose(
                id, List.of(new ProfileChangeService.ProfileChangeProposal("studentId", "20240001", "猜测")), "测试"));
        assertEquals("FIELD_NOT_ALLOWED", ex.getCode());
    }

    @Test
    @DisplayName("a concurrent REJECT before confirmation wins the claim and no profile is written")
    void confirmLosesRaceToReject() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser10", "pass123");
        Long id = userId("changeuser10");
        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("availableTime", "9 hours/week", "竞态拒绝测试")), "测试");
        String candidateId = created.get(0).getCandidateId();

        // Simulate another request rejecting the candidate after it was read.
        jdbcTemplate.update("UPDATE profile_change_candidates SET status = 'REJECTED', decided_at = datetime('now') "
                + "WHERE candidate_id = ?", candidateId);

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("""
                                {"decision":"CONFIRM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isNotFound());
        assertEquals("REJECTED", jdbcTemplate.queryForObject(
                "SELECT status FROM profile_change_candidates WHERE candidate_id = ?", String.class, candidateId));
    }

    @Test
    @DisplayName("a concurrent EXPIRY before confirmation wins the claim and no profile is written")
    void confirmLosesRaceToExpiry() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser11", "pass123");
        Long id = userId("changeuser11");
        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("goals", "[]", "竞态过期测试")), "测试");
        String candidateId = created.get(0).getCandidateId();
        jdbcTemplate.update("UPDATE profile_change_candidates SET expires_at = datetime('now', '-2 hour') "
                + "WHERE candidate_id = ?", candidateId);

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("""
                                {"decision":"CONFIRM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("claim SQL rejects a candidate that expired between expireStale and claim")
    void claimValidatesExpiryInsideTheSql() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser12", "pass123");
        Long id = userId("changeuser12");
        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("availableTime", "11 hours/week", "边界过期测试")), "测试");
        String candidateId = created.get(0).getCandidateId();

        String now = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // Keep the row PENDING but already expired: this is exactly the narrow
        // window between expireStale() and the atomic claim.
        jdbcTemplate.update("UPDATE profile_change_candidates SET expires_at = ? WHERE candidate_id = ?",
                now, candidateId);
        assertEquals("PENDING_CONFIRMATION", jdbcTemplate.queryForObject(
                "SELECT status FROM profile_change_candidates WHERE candidate_id = ?", String.class, candidateId));

        assertFalse(candidateRepository.claimForConfirmation(candidateId, id, now),
                "过期候选不能被原子抢占");
        assertEquals("PENDING_CONFIRMATION", jdbcTemplate.queryForObject(
                "SELECT status FROM profile_change_candidates WHERE candidate_id = ?", String.class, candidateId),
                "抢占失败不应改变候选状态");

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("multiple candidates from one multi-field proposal can be confirmed one by one")
    void multipleCandidatesCanBeConfirmedSequentially() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser13", "pass123");
        Long id = userId("changeuser13");
        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("skillsJson", "[\"Python\"]", "技能"),
                new ProfileChangeService.ProfileChangeProposal("interestsJson", "[\"AI\"]", "兴趣"),
                new ProfileChangeService.ProfileChangeProposal("goals", "[\"保研\"]", "目标"),
                new ProfileChangeService.ProfileChangeProposal("availableTime", "10 hours/week", "时间")), "测试");
        assertEquals(4, created.size(), "同一轮提议应生成 4 个候选");

        for (int i = 0; i < created.size(); i++) {
            var action = mockMvc.perform(
                    post("/api/profiles/change-candidates/" + created.get(i).getCandidateId() + "/decision")
                            .session(session).header("User-Agent", UA)
                            .contentType("application/json")
                            .content("{\"decision\":\"CONFIRM\"}"));
            action.andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
            if (i < created.size() - 1) {
                action.andExpect(jsonPath("$.continuationDeferred").value(true));
            } else {
                action.andExpect(jsonPath("$.continuationDeferred").value(false));
            }
        }

        Integer usedAfterBatch = jdbcTemplate.queryForObject(
                "SELECT request_count FROM chat_usage_daily WHERE user_id = ? AND usage_date = ?",
                Integer.class, id, chatQuotaService.today());
        assertEquals(1, usedAfterBatch, "整批确认只应在全部处理完后消耗 1 次续答额度");

        String batchContext = profileChangeService.batchContext(id, created.get(0).getBatchId());
        assertNotNull(batchContext, "批次 context 应可用于一次性续答");
        assertTrue(batchContext.contains("技能"));
        assertTrue(batchContext.contains("目标"));

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillsJson").value("[\"Python\"]"))
                .andExpect(jsonPath("$.interestsJson").value("[\"AI\"]"))
                .andExpect(jsonPath("$.goals").value("[\"保研\"]"))
                .andExpect(jsonPath("$.availableTime").value("10 hours/week"));
    }

    @Test
    @DisplayName("a duplicate candidate whose value is already applied is confirmed without another write")
    void duplicateAlreadyAppliedCandidateIsIdempotent() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser14", "pass123");
        Long id = userId("changeuser14");
        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("skillsJson", "[\"Python\"]", "技能")), "测试");
        String candidateId = created.get(0).getCandidateId();

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        Long versionAfterFirst = jdbcTemplate.queryForObject(
                "SELECT version FROM user_profiles WHERE user_id = ?", Long.class, id);

        // Another card for the same value (e.g. the continuation proposed it again).
        var duplicate = new org.example.lifecomposer.Entity.ProfileChangeCandidate();
        duplicate.setCandidateId("chg_duplicate");
        duplicate.setUserId(id);
        duplicate.setFieldName("skillsJson");
        duplicate.setOldValue(null);
        duplicate.setNewValue("[\"Python\"]");
        duplicate.setSource("chat");
        duplicate.setStatus("PENDING_CONFIRMATION");
        duplicate.setBaseVersion(0L);
        duplicate.setExpiresAt("2099-01-01 00:00:00");
        candidateRepository.insert(duplicate);

        mockMvc.perform(post("/api/profiles/change-candidates/chg_duplicate/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.continuationDeferred").value(false));

        Long versionAfterDuplicate = jdbcTemplate.queryForObject(
                "SELECT version FROM user_profiles WHERE user_id = ?", Long.class, id);
        assertEquals(versionAfterFirst, versionAfterDuplicate, "已生效的重复候选不应再次写画像");
        Integer usedAfterDuplicate = jdbcTemplate.queryForObject(
                "SELECT request_count FROM chat_usage_daily WHERE user_id = ? AND usage_date = ?",
                Integer.class, id, chatQuotaService.today());
        assertEquals(1, usedAfterDuplicate, "已生效的重复候选不应再次调用 LLM");
    }

    @Test
    @DisplayName("decision continuation consumes chat quota exactly once and repeated decisions skip the LLM")
    void decisionContinuationConsumesQuotaOnce() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser8", "pass123");
        Long id = userId("changeuser8");
        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("availableTime", "7 hours/week", "额度测试")), "测试");
        String candidateId = created.get(0).getCandidateId();

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.agentMessage").isString());

        Integer usedAfterFirst = jdbcTemplate.queryForObject(
                "SELECT request_count FROM chat_usage_daily WHERE user_id = ? AND usage_date = ?",
                Integer.class, id, chatQuotaService.today());
        assertEquals(1, usedAfterFirst, "首次续答应消耗 1 次聊天额度");

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.quotaExceeded").value(false));

        Integer usedAfterSecond = jdbcTemplate.queryForObject(
                "SELECT request_count FROM chat_usage_daily WHERE user_id = ? AND usage_date = ?",
                Integer.class, id, chatQuotaService.today());
        assertEquals(1, usedAfterSecond, "重复确认是幂等业务操作，不应再次消耗额度或触发 LLM");
    }

    @Test
    @DisplayName("exhausted daily quota still persists the decision but skips the agent continuation")
    void exhaustedQuotaSkipsContinuation() throws Exception {
        MockHttpSession session = registerAndLogin("changeuser9", "pass123");
        Long id = userId("changeuser9");
        var created = profileChangeService.propose(id, List.of(
                new ProfileChangeService.ProfileChangeProposal("availableTime", "2 hours/week", "额度耗尽测试")), "测试");
        String candidateId = created.get(0).getCandidateId();

        jdbcTemplate.update("INSERT INTO chat_usage_daily(user_id, usage_date, request_count) "
                        + "VALUES (?, ?, 100) ON CONFLICT(user_id, usage_date) DO UPDATE SET request_count = 100",
                id, chatQuotaService.today());

        mockMvc.perform(post("/api/profiles/change-candidates/" + candidateId + "/decision")
                        .session(session).header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"decision\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.quotaExceeded").value(true))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        mockMvc.perform(get("/api/profiles/me").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTime").value("2 hours/week"));

        Integer used = jdbcTemplate.queryForObject(
                "SELECT request_count FROM chat_usage_daily WHERE user_id = ? AND usage_date = ?",
                Integer.class, id, chatQuotaService.today());
        assertEquals(100, used, "额度耗尽时不应继续累加");
    }

    @Test
    @DisplayName("the agent tool returns awaitingConfirmation and persists candidates")
    void agentToolReturnsAwaitingConfirmation() throws Exception {
        registerAndLogin("changeuser7", "pass123");
        Long id = userId("changeuser7");

        ToolResult result = toolRegistry.execute("propose_profile_update", new AgentToolContext(id.intValue()),
                "{\"changes\":[{\"field\":\"interestsJson\",\"newValue\":\"[\\\"AI\\\"]\",\"rationale\":\"用户说对 AI 感兴趣\"}]}");

        assertTrue(result.ok());
        assertTrue(result.data() instanceof Map<?, ?>);
        Map<?, ?> data = (Map<?, ?>) result.data();
        assertEquals(Boolean.TRUE, data.get("awaitingConfirmation"));
        assertEquals("PENDING_CONFIRMATION", data.get("status"));
    }
}