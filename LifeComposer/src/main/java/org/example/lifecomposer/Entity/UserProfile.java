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
    private String createdAt;
    private String updatedAt;
}
