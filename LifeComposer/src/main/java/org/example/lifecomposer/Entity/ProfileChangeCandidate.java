package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

/** A chat-proposed profile change that must be confirmed by the user. */
@Getter
@Setter
public class ProfileChangeCandidate {

    public static final String PENDING = "PENDING_CONFIRMATION";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CONFLICT = "CONFLICT";

    private Long id;
    private String candidateId;
    private Long userId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String rationale;
    private String source;
    private String status;
    private Long baseVersion;
    private String createdAt;
    private String expiresAt;
    private String decidedAt;
    private String decisionReason;
    private Long mergedVersion;
}
