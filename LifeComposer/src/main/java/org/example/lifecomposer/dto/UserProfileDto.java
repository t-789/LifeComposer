package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfileDto {
    private String college;
    private String major;
    private String grade;
    private String studentId;
    private String skillsJson;
    private String interestsJson;
    private String experiencesJson;
    private String preferencesJson;
    private String availableTime;
    private String goals;
    /**
     * Optional optimistic-lock version. When present the server requires the
     * stored row to still be at this version, otherwise the update is rejected
     * with {@code PROFILE_VERSION_CONFLICT}. Omitted keeps v0.0.7 overwrite
     * semantics for old clients.
     */
    private Long version;
}
