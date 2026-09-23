package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

/** Safe, renderable view of a profile change candidate (never exposes user ids). */
@Getter
@Setter
public class ProfileChangeCandidateDto {
    private String candidateId;
    private String field;
    private String oldValue;
    private String newValue;
    private String rationale;
    private String status;
    private String source;
    private String createdAt;
    private String expiresAt;
    private String decidedAt;
    private String decisionReason;
    private Long mergedVersion;
}
