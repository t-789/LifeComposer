package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatResponse {
    private String role;       // always "assistant"
    private String content;
    private String createTime; // formatted timestamp string
    private Boolean mocked;
    private String error;
    /** True when the turn stopped to wait for a profile-change confirmation. */
    private Boolean awaitingConfirmation;
    /** Versioned prompt ids actually used for this turn (traceability). */
    private java.util.Map<String, String> promptVersions;
}
