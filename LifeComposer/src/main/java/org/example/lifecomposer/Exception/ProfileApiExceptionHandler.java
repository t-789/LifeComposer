package org.example.lifecomposer.Exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps profile-domain business codes to HTTP status codes. It is scoped by
 * exception type (not URL), keeps the bot policy, and never writes system
 * feedback: these are expected user-facing outcomes, not server defects.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProfileApiExceptionHandler {

    @ExceptionHandler(ProfileApiException.class)
    public ResponseEntity<?> handleProfileApiException(ProfileApiException ex, HttpServletRequest request) {
        ResponseEntity<String> botResponse = BotRequestGuard.rejectIfBot(request);
        if (botResponse != null) {
            return botResponse;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getCode());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(statusFor(ex.getCode())).body(body);
    }

    private HttpStatus statusFor(String code) {
        return switch (code) {
            case "CANDIDATE_NOT_FOUND", "DIRECTION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PROFILE_VERSION_CONFLICT", "CANDIDATE_VERSION_CONFLICT",
                 "CANDIDATE_EXPIRED", "CANDIDATE_ALREADY_DECIDED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
