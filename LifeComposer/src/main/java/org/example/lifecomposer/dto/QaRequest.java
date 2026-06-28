package org.example.lifecomposer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for the Q&A ask endpoint.
 */
@Getter
@Setter
public class QaRequest {

    /** Required. The user's question or message (max 5000 chars). */
    @NotBlank(message = "消息不能为空")
    @Size(max = 5000, message = "消息长度不能超过5000字符")
    private String message;

    /** Optional. Use-case key for LLM config lookup (default: "qa"). */
    private String useCase = "qa";

    /** Optional. Conversation/thread identifier for grouping related Q&A. */
    private String conversationId;

    /** Optional. Extra metadata as a JSON string. */
    private String metadata;
}
