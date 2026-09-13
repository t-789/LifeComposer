package org.example.lifecomposer.Service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-instance sliding-window minute limiter. For multi-instance deployment
 * this must be replaced by a shared store (e.g. Redis).
 */
@Component
public class InMemoryMinuteRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<Integer, Deque<Long>> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(Integer userId, int limit) {
        if (userId == null || limit <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (window) {
            prune(window, now);
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /** Undo the most recent acquire for a rejected request. */
    public void refund(Integer userId) {
        Deque<Long> window = windows.get(userId);
        if (window == null) {
            return;
        }
        synchronized (window) {
            window.pollLast();
            if (window.isEmpty()) {
                windows.remove(userId, window);
            }
        }
    }

    public void reset(Integer userId) {
        windows.remove(userId);
    }

    public void resetAll() {
        windows.clear();
    }

    public long retryAfterSeconds(Integer userId) {
        Deque<Long> window = windows.get(userId);
        if (window == null) {
            return 0L;
        }
        synchronized (window) {
            prune(window, System.currentTimeMillis());
            Long oldest = window.peekFirst();
            if (oldest == null) {
                return 0L;
            }
            long remaining = WINDOW_MILLIS - (System.currentTimeMillis() - oldest);
            return Math.max(1L, (remaining + 999) / 1000);
        }
    }

    private void prune(Deque<Long> window, long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
            window.pollFirst();
        }
    }
}
