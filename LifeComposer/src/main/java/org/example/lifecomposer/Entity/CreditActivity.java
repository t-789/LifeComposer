package org.example.lifecomposer.Entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class CreditActivity {
    private Long id;
    private Long userId;
    private Long ruleId;
    /** graduation = 双创分, recommendation = 保研加分 */
    private String creditType;
    private String category;
    private String compName;
    private String compLevel;
    private String awardTier;
    private Double credits;
    private String obtainedDate;
    private String certificateRef;
    private Integer verified;
    private String notes;
    private Timestamp createdAt;
}
