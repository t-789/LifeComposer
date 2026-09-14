package org.example.lifecomposer.Service;

import org.springframework.stereotype.Component;

/**
 * Milestone 7: dedicated sliding-window limiter for admin console reads.
 *
 * <p>Deliberately a separate window store from {@link InMemoryMinuteRateLimiter}
 * (which backs the chat quota) so console browsing can never consume a user's
 * chat allowance or vice versa.</p>
 */
@Component
public class AdminQueryRateLimiter {

    private final InMemoryMinuteRateLimiter delegate = new InMemoryMinuteRateLimiter();

    public boolean tryAcquire(Integer adminUserId, int limit) {
        return delegate.tryAcquire(adminUserId, limit);
    }

    public long retryAfterSeconds(Integer adminUserId) {
        return delegate.retryAfterSeconds(adminUserId);
    }

    public void reset(Integer adminUserId) {
        delegate.reset(adminUserId);
    }

    public void resetAll() {
        delegate.resetAll();
    }
}
