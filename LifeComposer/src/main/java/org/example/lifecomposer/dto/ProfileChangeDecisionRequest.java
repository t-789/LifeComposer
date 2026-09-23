package org.example.lifecomposer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileChangeDecisionRequest {

    @NotBlank(message = "decision 不能为空")
    private String decision;

    @Size(max = 500, message = "拒绝理由不能超过500字符")
    private String reason;
}
