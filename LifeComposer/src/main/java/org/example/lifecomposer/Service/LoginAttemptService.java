package org.example.lifecomposer.Service;

import org.example.lifecomposer.config.AppSecurityProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory consecutive-failure lockout for account/IP pairs. */
@Component
public class LoginAttemptService {

    private final AppSecurityProperties properties;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(AppSecurityProperties properties) {
        this.properties = properties;
    }

    public boolean isLocked(String username, String clientIp) {
        Attempt attempt = attempts.get(key(username, clientIp));
        if (attempt == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (attempt.lockedUntil > now) {
            return true;
        }
        if (attempt.lockedUntil > 0 && attempt.lockedUntil <= now) {
            attempts.remove(key(username, clientIp));
        }
        return false;
    }

    public long retryAfterSeconds(String username, String clientIp) {
        Attempt attempt = attempts.get(key(username, clientIp));
        if (attempt == null || attempt.lockedUntil <= System.currentTimeMillis()) {
            return 0L;
        }
        return Math.max(1L, (attempt.lockedUntil - System.currentTimeMillis() + 999) / 1000);
    }

    public void recordFailure(String username, String clientIp) {
        attempts.compute(key(username, clientIp), (k, existing) -> {
            int failures = existing == null ? 0 : existing.failures;
            failures++;
            long lockedUntil = failures >= properties.getLoginMaxFailures()
                    ? System.currentTimeMillis() + properties.getLoginLockMillis()
                    : 0L;
            return new Attempt(failures, lockedUntil);
        });
    }

    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    public void reset() {
        attempts.clear();
    }

    private String key(String username, String clientIp) {
        return (username == null ? "" : username) + "|" + (clientIp == null ? "" : clientIp);
    }

    private record Attempt(int failures, long lockedUntil) {
    }
}
