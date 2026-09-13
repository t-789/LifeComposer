package org.example.lifecomposer.Repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ChatUsageRepositoryConcurrencyTest {

    @Autowired
    private ChatUsageRepository chatUsageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentIncrementsNeverExceedDailyLimit() throws Exception {
        jdbcTemplate.execute("DELETE FROM chat_usage_daily");
        int dailyLimit = 5;
        int workers = 20;

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return chatUsageRepository.tryIncrement(99, "2026-09-13", dailyLimit);
                }));
            }
            start.countDown();

            int admitted = 0;
            for (Future<Integer> future : futures) {
                admitted += future.get(15, TimeUnit.SECONDS);
            }
            assertEquals(dailyLimit, admitted);
            assertEquals(dailyLimit, chatUsageRepository.getCount(99, "2026-09-13"));
        } finally {
            pool.shutdownNow();
        }
    }
}
