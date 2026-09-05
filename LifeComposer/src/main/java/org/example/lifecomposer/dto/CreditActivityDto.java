package org.example.lifecomposer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreditActivityDto {
    private Long id;

    /** 可选关联的 college_credit_rules.id（可先记录后关联） */
    private Long ruleId;

    /** graduation = 双创分, recommendation = 保研加分 */
    @NotBlank(message = "加分类型不能为空")
    private String creditType;

    @NotBlank(message = "来源类别不能为空")
    private String category;

    private String compName;
    private String compLevel;
    private String awardTier;

    @NotNull(message = "分值不能为空")
    private Double credits;

    private String obtainedDate;
    private String certificateRef;

    /** 审核状态 0=未审核, 1=已审核；由服务端控制，用户不可自设 */
    private Integer verified;

    private String notes;
    private String createdAt;
}
