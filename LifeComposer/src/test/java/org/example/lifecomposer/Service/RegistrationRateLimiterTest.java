package org.example.lifecomposer.Service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationRateLimiterTest {

    @Test
    void rejectsAfterPerIpLimit() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter();

        assertTrue(limiter.tryAcquire("10.0.0.1", 1));
        assertFalse(limiter.tryAcquire("10.0.0.1", 1));
        assertTrue(limiter.retryAfterSeconds("10.0.0.1") > 0);
        assertTrue(limiter.tryAcquire("10.0.0.2", 1));
    }
}
