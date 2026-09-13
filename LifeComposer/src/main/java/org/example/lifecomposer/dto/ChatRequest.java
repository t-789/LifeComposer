package org.example.lifecomposer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    @NotBlank
    @Size(max = 5000, message = "消息长度不能超过5000字符")
    private String message;

    /** Optional client hint; server truncates it to the configured maximum. */
    private Integer maxTokens;
}
