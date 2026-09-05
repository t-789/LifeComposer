package org.example.lifecomposer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CollegeCreditRuleDto {
    private Long id;

    @NotBlank(message = "学院名称不能为空")
    private String college;

    /** graduation = 双创分, recommendation = 保研加分 */
    @NotBlank(message = "加分类型不能为空")
    private String creditType;

    @NotBlank(message = "来源类别不能为空")
    private String category;

    private String compLevel;
    private String compName;
    private String awardTier;

    @NotNull(message = "分值不能为空")
    private Double credits;

    private Double categoryCap;
    private String teamFormula;
    private String studentCohort;
    private String docSource;
    private String levelsJson;
    private String notes;
    private String createdAt;
}
