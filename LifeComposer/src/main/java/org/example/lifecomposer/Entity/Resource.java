package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class Resource {
    private Long id;
    /** 数据侧业务 id（UNIQUE），如 competition_001 / course_001 */
    private String resourceId;
    private String name;
    /** competition = 竞赛, course = 课程 */
    private String type;
    private String levelsJson;
    private String stagesJson;
    private String targetMajorsJson;
    private String registrationStart;
    private String registrationDeadline;
    private String requiredSkillsJson;
    private String difficulty;
    private String preparationPeriod;
    private String teamRolesJson;
    private String bonusPointJson;
    private String provider;
    private String courseLink;
    private String description;
    private String teachesSkillsJson;
    private String sourceUrl;
    private String sourceUrlsJson;
    private String sourceFile;
    private String notesJson;
    private String dataQuality;
    private String updatedAt;
    private Timestamp createdAt;
}
