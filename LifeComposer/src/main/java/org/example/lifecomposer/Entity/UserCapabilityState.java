package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

/** Minimal persisted capability state for one user. */
@Getter
@Setter
public class UserCapabilityState {
    private Long id;
    private Long userId;
    private String tagName;
    /** L1 / L2 / L3, or unknown. */
    private String level;
    private String evidenceJson;
    /** USER_FORM / CHAT_CONFIRMED / EVIDENCE_INFERRED */
    private String source;
    private Double confidence;
    private String createdAt;
    private String updatedAt;
}
