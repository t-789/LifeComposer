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
}
