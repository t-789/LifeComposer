package org.example.lifecomposer.recommendation;

import org.example.lifecomposer.Entity.CapabilityReference;
import org.example.lifecomposer.Entity.CapabilityTag;
import org.example.lifecomposer.Entity.Resource;
import org.example.lifecomposer.Entity.UserProfile;
import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.example.lifecomposer.Repository.CapabilityTagRepository;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.example.lifecomposer.Repository.UserProfileRepository;
import org.example.lifecomposer.Service.AvailableTimeParser;
import org.example.lifecomposer.Service.CapabilityNormalizationService;
import org.example.lifecomposer.controller.ReferenceDictionarySamples;
import org.example.lifecomposer.dto.DirectionRecommendationDto;
import org.example.lifecomposer.dto.PathPlanDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v0.1 M5 regression fixtures: suitable / partially suitable / insufficient
 * information, plus deterministic ordering and path explainability.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecommendationServiceTest {

    private static final long USER_ID = 987654L;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private CapabilityTagRepository capabilityTagRepository;

    @Autowired
    private CapabilityReferenceRepository capabilityReferenceRepository;

    @Autowired
    private CapabilityNormalizationService normalizationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareFixtures() {
        jdbcTemplate.execute("DELETE FROM capability_reference");
        jdbcTemplate.execute("DELETE FROM capability_tags");
        jdbcTemplate.execute("DELETE FROM resources");
        jdbcTemplate.execute("DELETE FROM user_profiles");
        for (CapabilityTag tag : ReferenceDictionarySamples.capabilityTags()) {
            capabilityTagRepository.upsert(tag);
        }
        for (CapabilityReference reference : ReferenceDictionarySamples.capabilityReferences()) {
            capabilityReferenceRepository.upsert(reference);
        }
        for (Resource resource : ReferenceDictionarySamples.resources()) {
            resourceRepository.upsert(resource);
        }
    }

    private void profile(String skillsJson, String availableTime, String goals) {
        UserProfile profile = new UserProfile();
        profile.setUserId(USER_ID);
        profile.setMajor("计算机科学与技术");
        profile.setGrade("大二");
        profile.setSkillsJson(skillsJson);
        profile.setAvailableTime(availableTime);
        profile.setGoals(goals);
        userProfileRepository.upsert(profile);
    }

    @Test
    @DisplayName("suitable fixture: strong skills + enough time + matching goal")
    void suitableFixture() {
        profile("[\"Python\",\"C语言\",\"算法\",\"LeetCode\"]",
                "10 hours/week", "[\"参加算法竞赛\",\"提升编程能力\"]");

        List<DirectionRecommendationDto> recommendations = recommendationService.recommend(USER_ID);
        assertEquals(4, recommendations.size());
        DirectionRecommendationDto top = recommendations.get(0);
        assertEquals("algorithm_contest", top.getDirectionId());
        assertEquals("SUITABLE", top.getClassification());
        assertTrue(top.getMatchedTags().contains("编程基础"));
        assertNotNull(top.getScoreBreakdown().get("skillMatch"));
        assertEquals("experimental-v1", top.getScoringVersion());
        assertEquals("1", top.getExplanationPromptVersion(),
                "推荐结果应记录方向解释 Prompt 版本");
        assertTrue(top.isInformationSufficient());
    }

    @Test
    @DisplayName("partial fixture: only basic programming and medium time")
    void partialFixture() {
        profile("[\"Python基础\"]", "6 hours/week", "[]");
        List<DirectionRecommendationDto> recommendations = recommendationService.recommend(USER_ID);
        DirectionRecommendationDto algorithm = recommendations.stream()
                .filter(r -> r.getDirectionId().equals("algorithm_contest"))
                .findFirst().orElseThrow();
        assertEquals("PARTIALLY_SUITABLE", algorithm.getClassification());
        assertTrue(algorithm.getMissingTags().contains("算法与数据结构"));
        assertTrue(algorithm.getTimeFitScore() > 0.5 && algorithm.getTimeFitScore() < 1.0);
    }

    @Test
    @DisplayName("insufficient fixture: no profile means questions instead of confident ranking")
    void insufficientFixture() {
        List<DirectionRecommendationDto> recommendations = recommendationService.recommend(USER_ID);
        assertFalse(recommendations.isEmpty());
        for (DirectionRecommendationDto dto : recommendations) {
            assertEquals("INSUFFICIENT_INFO", dto.getClassification());
            assertFalse(dto.isInformationSufficient());
            assertFalse(dto.getFollowUpQuestions().isEmpty());
        }
    }

    @Test
    @DisplayName("scoring is deterministic and reproducible")
    void deterministicScoring() {
        profile("[\"Python\",\"数据分析\"]", "6 hours/week", "[\"做数据分析项目\"]");
        List<DirectionRecommendationDto> first = recommendationService.recommend(USER_ID);
        List<DirectionRecommendationDto> second = recommendationService.recommend(USER_ID);
        assertEquals(first.stream().map(DirectionRecommendationDto::getDirectionId).toList(),
                second.stream().map(DirectionRecommendationDto::getDirectionId).toList());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getScore(), second.get(i).getScore(), 0.000001);
        }
    }

    @Test
    @DisplayName("path plan lists gap tasks, practice tasks and resource quality")
    void pathPlanIsExplainable() {
        profile("[\"Python基础\"]", "6 hours/week", "[\"参加算法竞赛\"]");
        PathPlanDto plan = recommendationService.path(USER_ID, "algorithm_contest");
        assertNotNull(plan);
        assertEquals("algorithm_contest", plan.getDirectionId());
        assertTrue(plan.getMissingTags().contains("算法与数据结构"));
        assertTrue(plan.getGapTasks().stream().anyMatch(t -> t.getTag().equals("算法与数据结构")
                && t.getSuggestedResources().stream().anyMatch(r -> r.getResourceId().equals("course_002"))));
        assertTrue(plan.getPracticeTasks().stream().anyMatch(t -> t.getResourceId().equals("competition_002")));
        assertTrue(plan.getResources().stream().allMatch(r -> r.getDataQuality() != null));
        assertTrue(plan.getExpectedInvestment().contains("每周约 8 小时"));
        assertEquals("experimental-v1", plan.getScoringVersion());
        assertEquals("1", plan.getSuggestionPromptVersion(),
                "路径计划应记录路径建议 Prompt 版本");
    }

    @Test
    @DisplayName("gap is computed from standard tags, not raw skill strings")
    void gapUsesStandardTags() {
        profile("[\"Python基础\"]", "6 hours/week", "[]");
        CapabilityNormalizationService.CapabilityGap gap = recommendationService.gap(USER_ID, "algorithm_contest");
        assertEquals(List.of("编程基础"), gap.userTags());
        assertEquals(List.of("算法与数据结构"), gap.missingTags());
        assertEquals(List.of("编程基础"), gap.matchedTags());
    }

    @Test
    @DisplayName("available time parser handles Chinese and English forms")
    void availableTimeParser() {
        assertEquals(6.0, AvailableTimeParser.parseHoursPerWeek("6 hours/week").orElseThrow(), 0.0001);
        assertEquals(6.0, AvailableTimeParser.parseHoursPerWeek("每周 6 小时").orElseThrow(), 0.0001);
        assertEquals(14.0, AvailableTimeParser.parseHoursPerWeek("2 hours/day").orElseThrow(), 0.0001);
        assertEquals(OptionalDouble.empty(), AvailableTimeParser.parseHoursPerWeek("不确定"));
    }
}
