package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class CollegeCreditRule {
    private Long id;
    private String college;
    /** graduation = 双创分, recommendation = 保研加分 */
    private String creditType;
    private String category;
    private String compLevel;
    private String compName;
    private String awardTier;
    private Double credits;
    private Double categoryCap;
    private String teamFormula;
    private String studentCohort;
    private String docSource;
    private String levelsJson;
    private String notes;
    private Timestamp createdAt;
}
