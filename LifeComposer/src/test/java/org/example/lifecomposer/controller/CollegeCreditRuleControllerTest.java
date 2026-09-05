package org.example.lifecomposer.controller;

import org.example.lifecomposer.Entity.CollegeCreditRule;
import org.example.lifecomposer.Repository.CollegeCreditRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollegeCreditRuleControllerTest extends BaseControllerTest {

    private static final String UA = "TestClient/1.0";

    @Autowired
    private CollegeCreditRuleRepository collegeCreditRuleRepository;

    @BeforeEach
    void cleanupCreditTables() {
        jdbcTemplate.execute("DELETE FROM credit_activities");
        jdbcTemplate.execute("DELETE FROM college_credit_rules");
    }

    @Test
    @DisplayName("GET /api/college-credit-rules - unauthenticated returns 403")
    void listRules_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/college-credit-rules").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/college-credit-rules/{id} - unauthenticated returns 403")
    void getRule_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/college-credit-rules/1").header("User-Agent", UA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/college-credit-rules - unauthenticated returns 403")
    void postRule_unauthenticated_returnsForbidden() throws Exception {
        String body = "{\"college\":\"计算机学院\",\"creditType\":\"graduation\",\"category\":\"lecture\",\"credits\":0.2}";
        mockMvc.perform(post("/api/college-credit-rules")
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/college-credit-rules - normal user (not admin) returns 403")
    void postRule_normalUser_returnsForbidden() throws Exception {
        MockHttpSession session = registerAndLogin("normaluser", "pass123");
        String body = "{\"college\":\"计算机学院\",\"creditType\":\"graduation\",\"category\":\"lecture\",\"credits\":0.2}";
        mockMvc.perform(post("/api/college-credit-rules").session(session)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/college-credit-rules - admin creates a rule")
    void postRule_admin_createsRule() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String body = "{\"college\":\"计算机学院\",\"creditType\":\"recommendation\",\"category\":\"competition\","
                + "\"compLevel\":\"national\",\"compName\":\"全国大学生电子设计竞赛\",\"awardTier\":\"first\","
                + "\"credits\":2.5,\"notes\":\"国一按一等奖认定\"}";
        mockMvc.perform(post("/api/college-credit-rules").session(admin)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.college").value("计算机学院"))
                .andExpect(jsonPath("$.creditType").value("recommendation"))
                .andExpect(jsonPath("$.category").value("competition"))
                .andExpect(jsonPath("$.compLevel").value("national"))
                .andExpect(jsonPath("$.compName").value("全国大学生电子设计竞赛"))
                .andExpect(jsonPath("$.awardTier").value("first"))
                .andExpect(jsonPath("$.credits").value(2.5))
                .andExpect(jsonPath("$.notes").value("国一按一等奖认定"));
    }

    @Test
    @DisplayName("POST /api/college-credit-rules - invalid enum values return 400")
    void postRule_invalidEnums_returnsBadRequest() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String[] invalidBodies = {
                // invalid creditType
                "{\"college\":\"计算机学院\",\"creditType\":\"innovation\",\"category\":\"lecture\",\"credits\":1.0}",
                // invalid category
                "{\"college\":\"计算机学院\",\"creditType\":\"graduation\",\"category\":\"volunteer\",\"credits\":1.0}",
                // invalid compLevel
                "{\"college\":\"计算机学院\",\"creditType\":\"recommendation\",\"category\":\"competition\",\"compLevel\":\"world\",\"credits\":1.0}",
                // invalid awardTier
                "{\"college\":\"计算机学院\",\"creditType\":\"recommendation\",\"category\":\"competition\",\"awardTier\":\"gold\",\"credits\":1.0}"
        };
        for (String body : invalidBodies) {
            mockMvc.perform(post("/api/college-credit-rules").session(admin)
                            .header("User-Agent", UA)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("POST /api/college-credit-rules - missing credits returns 400")
    void postRule_missingCredits_returnsBadRequest() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String body = "{\"college\":\"计算机学院\",\"creditType\":\"graduation\",\"category\":\"lecture\"}";
        mockMvc.perform(post("/api/college-credit-rules").session(admin)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/college-credit-rules - empty list returns []")
    void listRules_authenticated_emptyList() throws Exception {
        MockHttpSession session = registerAndLogin("ruleemptyuser", "pass123");
        mockMvc.perform(get("/api/college-credit-rules").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST + GET - any authenticated user can read rules created by admin")
    void postRule_thenList_containsRule() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String body = "{\"college\":\"信通院\",\"creditType\":\"graduation\",\"category\":\"lecture\",\"credits\":0.2}";
        mockMvc.perform(post("/api/college-credit-rules").session(admin)
                        .header("User-Agent", UA)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        MockHttpSession reader = registerAndLogin("rulereader", "pass123");
        mockMvc.perform(get("/api/college-credit-rules").session(reader).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].college").value("信通院"))
                .andExpect(jsonPath("$[0].creditType").value("graduation"))
                .andExpect(jsonPath("$[0].credits").value(0.2));
    }

    @Test
    @DisplayName("GET /api/college-credit-rules - college/creditType/category filters work")
    void listRules_filters_work() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        createRuleAsAdmin(admin, "{\"college\":\"信通院\",\"creditType\":\"graduation\",\"category\":\"lecture\",\"credits\":0.2}");
        createRuleAsAdmin(admin, "{\"college\":\"计算机学院\",\"creditType\":\"recommendation\",\"category\":\"competition\",\"compLevel\":\"national\",\"credits\":2.5}");

        MockHttpSession reader = registerAndLogin("rulefilteruser", "pass123");

        // creditType filter
        mockMvc.perform(get("/api/college-credit-rules").session(reader).header("User-Agent", UA)
                        .param("creditType", "graduation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].college").value("信通院"));

        // category filter
        mockMvc.perform(get("/api/college-credit-rules").session(reader).header("User-Agent", UA)
                        .param("category", "competition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].college").value("计算机学院"));

        // college partial match filter
        mockMvc.perform(get("/api/college-credit-rules").session(reader).header("User-Agent", UA)
                        .param("college", "计算机"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].creditType").value("recommendation"));

        // combined filters without a match return empty
        mockMvc.perform(get("/api/college-credit-rules").session(reader).header("User-Agent", UA)
                        .param("creditType", "graduation")
                        .param("category", "competition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // no match
        mockMvc.perform(get("/api/college-credit-rules").session(reader).header("User-Agent", UA)
                        .param("college", "经济管理学院"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/college-credit-rules - invalid enum filter returns 400")
    void listRules_invalidFilter_returnsBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin("rulebadfilter", "pass123");
        mockMvc.perform(get("/api/college-credit-rules").session(session).header("User-Agent", UA)
                        .param("creditType", "innovation"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/college-credit-rules").session(session).header("User-Agent", UA)
                        .param("category", "volunteer"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/college-credit-rules/{id} - returns the seeded rule")
    void getRuleById_returnsRule() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        long ruleId = createRuleAsAdmin(admin,
                "{\"college\":\"信通院\",\"creditType\":\"graduation\",\"category\":\"lecture\",\"credits\":0.2,\"notes\":\"讲座\"}");

        MockHttpSession reader = registerAndLogin("rulegetuser", "pass123");
        mockMvc.perform(get("/api/college-credit-rules/" + ruleId).session(reader).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) ruleId))
                .andExpect(jsonPath("$.college").value("信通院"))
                .andExpect(jsonPath("$.category").value("lecture"))
                .andExpect(jsonPath("$.notes").value("讲座"));
    }

    @Test
    @DisplayName("GET /api/college-credit-rules/{id} - nonexistent rule returns 400")
    void getRuleById_notFound_returnsBadRequest() throws Exception {
        MockHttpSession session = registerAndLogin("rulenotfound", "pass123");
        mockMvc.perform(get("/api/college-credit-rules/999999").session(session).header("User-Agent", UA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Repository insert (seeding path) - inserted rule is readable via API")
    void repositoryInsert_seedsRule_readableViaApi() throws Exception {
        CollegeCreditRule rule = new CollegeCreditRule();
        rule.setCollege("计算机学院");
        rule.setCreditType("recommendation");
        rule.setCategory("competition");
        rule.setCompLevel("national");
        rule.setCompName("全国大学生数学建模竞赛");
        rule.setAwardTier("second");
        rule.setCredits(1.5);
        Long insertedId = collegeCreditRuleRepository.insert(rule);
        assertNotNull(insertedId);

        MockHttpSession session = registerAndLogin("ruleseeduser", "pass123");
        mockMvc.perform(get("/api/college-credit-rules").session(session).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].compName").value("全国大学生数学建模竞赛"))
                .andExpect(jsonPath("$[0].credits").value(1.5));
    }

    private long createRuleAsAdmin(MockHttpSession admin, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/college-credit-rules").session(admin)
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
