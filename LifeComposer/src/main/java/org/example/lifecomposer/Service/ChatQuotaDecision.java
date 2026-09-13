package org.example.lifecomposer.Service;

/** Result of one chat quota admission check. */
public record ChatQuotaDecision(
        boolean allowed,
        String error,
        String message,
        long retryAfterSeconds,
        int dailyLimit,
        int usedToday,
        int remainingToday,
        int minuteLimit,
        int minuteRemaining
) {
}
