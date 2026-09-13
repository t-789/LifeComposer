package org.example.lifecomposer.Service;

import org.example.lifecomposer.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    @Test
    void locksAfterConfiguredFailuresAndUnlocksOnSuccess() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setLoginMaxFailures(2);
        properties.setLoginLockMillis(60_000L);
        LoginAttemptService service = new LoginAttemptService(properties);

        assertFalse(service.isLocked("alice", "10.0.0.1"));
        service.recordFailure("alice", "10.0.0.1");
        assertFalse(service.isLocked("alice", "10.0.0.1"));
        service.recordFailure("alice", "10.0.0.1");
        assertTrue(service.isLocked("alice", "10.0.0.1"));
        assertTrue(service.retryAfterSeconds("alice", "10.0.0.1") > 0);

        service.recordSuccess("alice", "10.0.0.1");
        assertFalse(service.isLocked("alice", "10.0.0.1"));
    }

    @Test
    void lockIsScopedByUsernameAndIp() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setLoginMaxFailures(1);
        LoginAttemptService service = new LoginAttemptService(properties);

        service.recordFailure("alice", "10.0.0.1");
        assertTrue(service.isLocked("alice", "10.0.0.1"));
        assertFalse(service.isLocked("alice", "10.0.0.2"));
        assertFalse(service.isLocked("bob", "10.0.0.1"));
    }
}
