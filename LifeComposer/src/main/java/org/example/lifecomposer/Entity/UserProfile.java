package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfile {
    private Long id;
    private Long userId;
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
    /** Monotonic optimistic-lock version; 0 means "no row / never written". */
    private Long version;
    private String createdAt;
    private String updatedAt;
}
