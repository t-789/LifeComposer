package org.example.lifecomposer.Exception;

import org.springframework.http.HttpStatus;

/**
 * Milestone 7: stable, browser-safe error for the admin console. Carries a
 * machine readable code and an operator-facing message; never SQL, table names,
 * stack traces or inspected content.
 */
public class AdminApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AdminApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AdminApiException badRequest(String code, String message) {
        return new AdminApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static AdminApiException notFound(String message) {
        return new AdminApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static AdminApiException rateLimited(String message) {
        return new AdminApiException(HttpStatus.TOO_MANY_REQUESTS, "ADMIN_RATE_LIMIT", message);
    }

    public static AdminApiException queryFailed(String message) {
        return new AdminApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ADMIN_QUERY_FAILED", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
