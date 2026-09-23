package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.CapabilityReference;
import org.example.lifecomposer.Entity.CapabilityTag;
import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.example.lifecomposer.Repository.CapabilityTagRepository;
import org.example.lifecomposer.controller.ReferenceDictionarySamples;
import org.example.lifecomposer.dto.CapabilityStateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** v0.1 M4: capability state must stay a projection of the current profile. */
@SpringBootTest
@ActiveProfiles("test")
class CapabilityStateServiceTest {

    private static final long USER_ID = 13579L;

    @Autowired
    private CapabilityStateService capabilityStateService;

    @Autowired
    private CapabilityTagRepository capabilityTagRepository;

    @Autowired
    private CapabilityReferenceRepository capabilityReferenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void fixtures() {
        jdbcTemplate.execute("DELETE FROM user_capability_states");
        jdbcTemplate.execute("DELETE FROM capability_reference");
        jdbcTemplate.execute("DELETE FROM capability_tags");
        for (CapabilityTag tag : ReferenceDictionarySamples.capabilityTags()) {
            capabilityTagRepository.upsert(tag);
        }
        for (CapabilityReference reference : ReferenceDictionarySamples.capabilityReferences()) {
            capabilityReferenceRepository.upsert(reference);
        }
    }

    @Test
    @DisplayName("clearing skills removes the stale capability rows")
    void clearingSkillsRemovesStates() {
        capabilityStateService.recordSkills(USER_ID, "[\"数据分析\"]", "[]", "USER_FORM");
        assertEquals(List.of("数据分析"),
                capabilityStateService.statesFor(USER_ID).stream().map(CapabilityStateDto::getTag).toList());

        capabilityStateService.recordSkills(USER_ID, "[]", "[]", "USER_FORM");
        assertTrue(capabilityStateService.statesFor(USER_ID).isEmpty(),
                "清空技能后不应残留旧能力状态");
    }

    @Test
    @DisplayName("removing one skill removes exactly its stale tag")
    void removingOneSkillRemovesOnlyThatTag() {
        capabilityStateService.recordSkills(USER_ID, "[\"数据分析\",\"Python基础\"]", "[]", "USER_FORM");
        assertEquals(2, capabilityStateService.statesFor(USER_ID).size());

        capabilityStateService.recordSkills(USER_ID, "[\"Python基础\"]", "[]", "USER_FORM");
        List<String> tags = capabilityStateService.statesFor(USER_ID).stream()
                .map(CapabilityStateDto::getTag).toList();
        assertTrue(tags.contains("编程基础"));
        assertFalse(tags.contains("数据分析"), "被删除技能对应的标签必须移除");
    }

    @Test
    @DisplayName("object-shaped experiences are preserved as readable evidence")
    void objectExperiencesBecomeEvidence() {
        capabilityStateService.recordSkills(USER_ID, "[\"Python基础\"]",
                "[{\"type\":\"竞赛\",\"name\":\"蓝桥杯\"}]", "CHAT_CONFIRMED");
        CapabilityStateDto state = capabilityStateService.statesFor(USER_ID).get(0);
        assertEquals("编程基础", state.getTag());
        assertEquals("CHAT_CONFIRMED", state.getSource());
        assertFalse(state.getEvidence().isEmpty(), "对象型经历不能丢失证据");
        assertTrue(state.getEvidence().get(0).contains("蓝桥杯"));
    }
}
