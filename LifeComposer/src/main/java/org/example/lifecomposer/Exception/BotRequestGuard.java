package org.example.lifecomposer.Exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

/**
 * Single owner of the "bot-looking client" policy, shared by both exception
 * advices.
 *
 * <p>Review follow-up (v0.0.7): {@link ValidationExceptionHandler} runs with the
 * highest precedence, so when the bot check lived only in
 * {@link GlobalExceptionHandler} a bot or empty User-Agent client could reach a
 * 400 by triggering an input error first. Keeping the policy here makes the
 * documented "empty User-Agent / crawler keyword → 403" rule hold no matter
 * which exception is raised.
 */
public final class BotRequestGuard {

    private static final Logger logger = LogManager.getLogger(BotRequestGuard.class);

    private static final Set<String> BOT_USER_AGENTS = Set.of(
            "bot", "crawler", "spider", "scraper", "python", "wget"
    );

    private BotRequestGuard() {
    }

    /** An absent or empty User-Agent is treated as a bot, as before. */
    public static boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return true;
        }
        String ua = userAgent.toLowerCase();
        return BOT_USER_AGENTS.stream().anyMatch(ua::contains);
    }

    /**
     * @return the shared 403 response for a bot-looking request, or {@code null}
     *         when the caller should keep handling the exception normally
     */
    public static ResponseEntity<String> rejectIfBot(HttpServletRequest request) {
        if (request == null || !isBot(request.getHeader("User-Agent"))) {
            return null;
        }
        logger.warn("Bot request: {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
    }
}
