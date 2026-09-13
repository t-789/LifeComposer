package org.example.lifecomposer.Service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-IP registration rate limiter (single instance). */
@Component
public class RegistrationRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String clientIp, int limit) {
        if (clientIp == null || limit <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(clientIp, key -> new ArrayDeque<>());
        synchronized (window) {
            prune(window, now);
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    public long retryAfterSeconds(String clientIp) {
        Deque<Long> window = windows.get(clientIp);
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

    public void reset() {
        windows.clear();
    }

    private void prune(Deque<Long> window, long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
            window.pollFirst();
        }
    }
}
