package org.example.lifecomposer.Service;

import org.example.lifecomposer.Repository.ChatUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.chat-per-minute=10",
        "app.security.chat-per-day=3"
})
class ChatQuotaServiceTest {

    @Autowired
    private ChatQuotaService chatQuotaService;

    @Autowired
    private ChatUsageRepository chatUsageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearUsage() {
        jdbcTemplate.execute("DELETE FROM chat_usage_daily");
    }

    @Test
    void admitsConfiguredDailyCountThenRejectsWithoutIncrementing() {
        assertTrue(chatQuotaService.tryConsume(7).allowed());
        assertTrue(chatQuotaService.tryConsume(7).allowed());
        assertTrue(chatQuotaService.tryConsume(7).allowed());

        ChatQuotaDecision rejected = chatQuotaService.tryConsume(7);
        assertFalse(rejected.allowed());
        assertEquals("CHAT_DAILY_LIMIT", rejected.error());
        assertEquals(3, rejected.usedToday());
        assertEquals(0, rejected.remainingToday());
        assertTrue(rejected.retryAfterSeconds() > 0);

        assertEquals(3, chatUsageRepository.getCount(7, chatQuotaService.today()));
    }

    @Test
    void resetTodayAllowsRequestsAgain() {
        assertTrue(chatQuotaService.tryConsume(8).allowed());
        assertTrue(chatQuotaService.tryConsume(8).allowed());
        assertTrue(chatQuotaService.tryConsume(8).allowed());
        assertFalse(chatQuotaService.tryConsume(8).allowed());

        ChatQuotaDecision reset = chatQuotaService.resetToday(8);
        assertEquals(0, reset.usedToday());
        assertEquals(3, reset.remainingToday());
        assertTrue(chatQuotaService.tryConsume(8).allowed());
    }

    @Test
    void usageIsScopedPerUserAndPerDay() {
        assertTrue(chatQuotaService.tryConsume(11).allowed());
        assertTrue(chatQuotaService.tryConsume(12).allowed());
        assertEquals(1, chatUsageRepository.getCount(11, chatQuotaService.today()));
        assertEquals(1, chatUsageRepository.getCount(12, chatQuotaService.today()));
    }
}
