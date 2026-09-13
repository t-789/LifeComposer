package org.example.lifecomposer.Service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryMinuteRateLimiterTest {

    @Test
    void rejectsAfterLimitWithinWindow() {
        InMemoryMinuteRateLimiter limiter = new InMemoryMinuteRateLimiter();

        assertTrue(limiter.tryAcquire(1, 2));
        assertTrue(limiter.tryAcquire(1, 2));
        assertFalse(limiter.tryAcquire(1, 2));
        assertTrue(limiter.retryAfterSeconds(1) > 0);
    }

    @Test
    void refundReleasesMostRecentToken() {
        InMemoryMinuteRateLimiter limiter = new InMemoryMinuteRateLimiter();

        assertTrue(limiter.tryAcquire(1, 1));
        assertFalse(limiter.tryAcquire(1, 1));
        limiter.refund(1);
        assertTrue(limiter.tryAcquire(1, 1));
    }

    @Test
    void resetAllClearsWindows() {
        InMemoryMinuteRateLimiter limiter = new InMemoryMinuteRateLimiter();
        assertTrue(limiter.tryAcquire(1, 1));
        assertFalse(limiter.tryAcquire(1, 1));
        limiter.resetAll();
        assertTrue(limiter.tryAcquire(1, 1));
    }
}
