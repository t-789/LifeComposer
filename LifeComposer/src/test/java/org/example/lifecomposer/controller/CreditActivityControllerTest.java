package org.example.lifecomposer.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreditActivityControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    private static final String VALID_ACTIVITY_BODY =
            "{\"creditType\":\"recommendation\",\"category\":\"competition\","
                    + "\"compName\":\"全国大学生电子设计竞赛\",\"compLevel\":\"national\",\"awardTier\":\"first\","
                    + "\"credits\":2.5,\"obtainedDate\":\"2026-08-01\",\"notes\":\"国一\"}";

    @BeforeEach
    void cleanupCreditTables() {
        jdbcTemplate.execute("DELETE FROM credit_activities");
        jdbcTemplate.execute("DELETE FROM college_credit_rules");
    }

    @Test
    @DisplayName("All endpoints - unauthenticated returns 403")
    void endpoints_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/credit-activities").header("User-Agent", UA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/credit-activities/1").header("User-Agent", UA))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/credit-activities").header("User-Agent", UA)
                        .contentType("application/json")
                        .content(VALID_ACTIVITY_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/credit-activities/1").header("User-Agent", UA)
                        .contentType("application/json")
                        .content(VALID_ACTIVITY_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/credit-activities/1").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/credit-activities - valid activity returns 200")
    void postActivity_valid_returnsOk() throws Exception {
        MockHttpSession session = registerAndLogin("activitycreator", "pass123");
        mockMvc.perform(post("/api/credit-activities").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(VALID_ACTIVITY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditType").value("recommendation"))
                .andExpect(jsonPath("$.category").value("competition"))
                .andExpect(jsonPath("$.compName").value("全国大学生电子设计竞赛"))
                .andExpect(jsonPath("$.compLevel").value("national"))
                .andExpect(jsonPath("$.awardTier").value("first"))
                .andExpect(jsonPath("$.credits").value(2.5))
                .andExpect(jsonPath("$.obtainedDate").value("2026-08-01"))
                .andExpect(jsonPath("$.verified").value(0))
                .andExpect(jsonPath("$.notes").value("国一"));
    }

    @Test
    @DisplayName("POST /api/credit-activities - verified is server-controlled (ignores client value)")
    void postActivity_verifiedIgnored_returnsZero() throws Exception {
        MockHttpSession session = registerAndLogin("verifyuser", "pass123");
        String body = "{\"creditType\":\"recommendation\",\"category\":\"competition\",\"credits\":1.0,\"verified\":1}";
        mockMvc.perform(post("/api/credit-activities").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(0));
    }

    @Test
    @DisplayName("POST /api/credit-activities - invalid input returns 400")
    void postActivity_invalidInput_returnsBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin("badactivityuser", "pass123");
        String[] invalidBodies = {
                // invalid creditType
                "{\"creditType\":\"innovation\",\"category\":\"competition\",\"credits\":1.0}",
                // invalid category
                "{\"creditType\":\"recommendation\",\"category\":\"volunteer\",\"credits\":1.0}",
                // invalid compLevel
                "{\"creditType\":\"recommendation\",\"category\":\"competition\",\"compLevel\":\"world\",\"credits\":1.0}",
                // invalid awardTier
                "{\"creditType\":\"recommendation\",\"category\":\"competition\",\"awardTier\":\"gold\",\"credits\":1.0}",
                // missing credits
                "{\"creditType\":\"recommendation\",\"category\":\"competition\"}",
                // negative credits
                "{\"creditType\":\"recommendation\",\"category\":\"competition\",\"credits\":-1.0}",
                // blank category
                "{\"creditType\":\"recommendation\",\"category\":\" \",\"credits\":1.0}",
                // nonexistent linked rule
                "{\"creditType\":\"recommendation\",\"category\":\"competition\",\"credits\":1.0,\"ruleId\":999999}"
        };
        for (String body : invalidBodies) {
            mockMvc.perform(post("/api/credit-activities").session(session)
                            .header("User-Agent", UA)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("POST /api/credit-activities - can link an existing rule via ruleId")
    void postActivity_withExistingRuleId_returnsOk() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        MvcResult ruleResult = mockMvc.perform(post("/api/college-credit-rules").session(admin)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"college\":\"计算机学院\",\"creditType\":\"recommendation\",\"category\":\"competition\",\"credits\":2.5}"))
                .andExpect(status().isOk())
                .andReturn();
        long ruleId = extractIdFromJson(ruleResult.getResponse().getContentAsString());

        MockHttpSession session = registerAndLogin("rulelinkuser", "pass123");
        String body = "{\"creditType\":\"recommendation\",\"category\":\"competition\",\"credits\":2.5,\"ruleId\":" + ruleId + "}";
        mockMvc.perform(post("/api/credit-activities").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").value((int) ruleId));
    }

    @Test
    @DisplayName("GET /api/credit-activities - empty list returns []")
    void listActivities_authenticated_emptyList() throws Exception {
        MockHttpSession session = registerAndLogin("emptyactivityuser", "pass123");
        mockMvc.perform(get("/api/credit-activities").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST + GET - created activities appear in list, newest first")
    void postActivity_thenList_containsBoth() throws Exception {
        MockHttpSession session = registerAndLogin("activitylistuser", "pass123");

        mockMvc.perform(post("/api/credit-activities").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"creditType\":\"graduation\",\"category\":\"lecture\",\"credits\":0.2,\"notes\":\"第一条\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/credit-activities").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"creditType\":\"recommendation\",\"category\":\"competition\",\"credits\":2.5,\"notes\":\"第二条\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/credit-activities").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].notes").value("第二条"))
                .andExpect(jsonPath("$[1].notes").value("第一条"));
    }

    @Test
    @DisplayName("GET /api/credit-activities/{id} - returns own activity; nonexistent returns 400")
    void getActivityById_returnsActivity() throws Exception {
        MockHttpSession session = registerAndLogin("activitygetuser", "pass123");
        long activityId = createActivity(session, VALID_ACTIVITY_BODY);

        mockMvc.perform(get("/api/credit-activities/" + activityId).session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) activityId))
                .andExpect(jsonPath("$.compName").value("全国大学生电子设计竞赛"))
                .andExpect(jsonPath("$.credits").value(2.5));

        mockMvc.perform(get("/api/credit-activities/999999").session(session).header("User-Agent", UA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/credit-activities/{id} - updates own activity")
    void putActivity_updatesOwnActivity() throws Exception {
        MockHttpSession session = registerAndLogin("activityupdateuser", "pass123");
        long activityId = createActivity(session, VALID_ACTIVITY_BODY);

        String updateBody = "{\"creditType\":\"recommendation\",\"category\":\"competition\","
                + "\"compName\":\"全国大学生电子设计竞赛\",\"awardTier\":\"special\",\"credits\":3.0,\"notes\":\"更新后特等奖\"}";
        mockMvc.perform(put("/api/credit-activities/" + activityId).session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) activityId))
                .andExpect(jsonPath("$.awardTier").value("special"))
                .andExpect(jsonPath("$.credits").value(3.0))
                .andExpect(jsonPath("$.notes").value("更新后特等奖"))
                .andExpect(jsonPath("$.verified").value(0));
    }

    @Test
    @DisplayName("PUT /api/credit-activities/{id} - full replace clears omitted optional fields")
    void putActivity_fullReplace_clearsOptionals() throws Exception {
        MockHttpSession session = registerAndLogin("activityreplaceuser", "pass123");
        long activityId = createActivity(session, VALID_ACTIVITY_BODY);

        String replaceBody = "{\"creditType\":\"graduation\",\"category\":\"lecture\",\"credits\":1.0}";
        mockMvc.perform(put("/api/credit-activities/" + activityId).session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(replaceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditType").value("graduation"))
                .andExpect(jsonPath("$.category").value("lecture"))
                .andExpect(jsonPath("$.credits").value(1.0))
                .andExpect(jsonPath("$.compName").doesNotExist())
                .andExpect(jsonPath("$.compLevel").doesNotExist())
                .andExpect(jsonPath("$.verified").value(0));
    }

    @Test
    @DisplayName("PUT /api/credit-activities/{id} - invalid input returns 400")
    void putActivity_invalidInput_returnsBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin("badupdateuser", "pass123");
        long activityId = createActivity(session, VALID_ACTIVITY_BODY);

        // invalid enum value
        mockMvc.perform(put("/api/credit-activities/" + activityId).session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content("{\"creditType\":\"recommendation\",\"category\":\"competition\",\"awardTier\":\"gold\",\"credits\":1.0}"))
                .andExpect(status().isBadRequest());

        // nonexistent activity
        mockMvc.perform(put("/api/credit-activities/999999").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(VALID_ACTIVITY_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/credit-activities/{id} - deletes own activity")
    void deleteActivity_returnsOk() throws Exception {
        MockHttpSession session = registerAndLogin("activitydeleteuser", "pass123");
        long activityId = createActivity(session, VALID_ACTIVITY_BODY);

        mockMvc.perform(delete("/api/credit-activities/" + activityId).session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("加分记录已删除"));

        // list no longer contains it
        mockMvc.perform(get("/api/credit-activities").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // fetching it afterwards returns 400
        mockMvc.perform(get("/api/credit-activities/" + activityId).session(session).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        // deleting it again returns 400
        mockMvc.perform(delete("/api/credit-activities/" + activityId).session(session).header("User-Agent", UA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Cross-user ownership - user B cannot access user A's activity")
    void activity_crossUserOwnership_returnsBadRequest() throws Exception {
        MockHttpSession sessionA = registerAndLogin("activityOwnerA", "pass123");
        MockHttpSession sessionB = registerAndLogin("activityOtherB", "pass456");

        long activityId = createActivity(sessionA, VALID_ACTIVITY_BODY);

        // User B tries GET / PUT / DELETE -> 400
        mockMvc.perform(get("/api/credit-activities/" + activityId).session(sessionB).header("User-Agent", UA))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/credit-activities/" + activityId).session(sessionB)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(VALID_ACTIVITY_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/credit-activities/" + activityId).session(sessionB).header("User-Agent", UA))
                .andExpect(status().isBadRequest());

        // User A can still GET their own activity
        mockMvc.perform(get("/api/credit-activities/" + activityId).session(sessionA).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) activityId));

        // User B's list stays empty
        mockMvc.perform(get("/api/credit-activities").session(sessionB).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private long createActivity(MockHttpSession session, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/credit-activities").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return extractIdFromJson(result.getResponse().getContentAsString());
    }

    private long extractIdFromJson(String json) {
        int idx = json.indexOf("\"id\":");
        int start = json.indexOf(":", idx) + 1;
        int end = json.indexOf(",", start);
        if (end == -1) {
            end = json.indexOf("}", start);
        }
        return Long.parseLong(json.substring(start, end).trim());
    }
}
