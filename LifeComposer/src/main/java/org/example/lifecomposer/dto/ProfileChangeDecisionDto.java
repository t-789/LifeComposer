package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileChangeDecisionDto {
    private String candidateId;
    private String status;
    private String field;
    private String oldValue;
    private String newValue;
    private Long mergedVersion;
    private String decidedAt;
    /** Deterministic agent continuation; present when the LLM answered, fallback text otherwise. */
    private String agentMessage;
    private Boolean agentAnswered;
    /** True when the decision succeeded but the agent continuation was blocked by chat quota. */
    private Boolean quotaExceeded;
    /** True when more pending cards exist, so the LLM continuation was postponed. */
    private Boolean continuationDeferred;
    private Long retryAfterSeconds;
    /** Prompt versions used for the continuation (traceability). */
    private java.util.Map<String, String> promptVersions;
}
