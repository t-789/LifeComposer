package org.example.lifecomposer.agent;

/** Raised when /api/chat/* cannot reach a real LLM and must not fake a reply. */
public class LlmUnavailableException extends RuntimeException {

    private final String code;

    public LlmUnavailableException(String message) {
        this("LLM_UNAVAILABLE", message);
    }

    public LlmUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
