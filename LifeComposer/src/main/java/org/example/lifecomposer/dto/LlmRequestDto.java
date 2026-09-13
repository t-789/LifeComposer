package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request DTO for LLM interaction. Supports both the legacy
 * message/conversationHistory shape and the full agent message list with tools.
 */
@Getter
@Setter
public class LlmRequestDto {

    /** Required. The user message or prompt (legacy shape). */
    private String message;

    /** Optional. System prompt to set behavior/context. */
    private String systemPrompt;

    /** Optional. Sampling temperature (0.0–2.0), or null to use provider default. */
    private Double temperature;

    /** Optional. Maximum tokens to generate, or null for provider default. */
    private Integer maxTokens;

    /** Required for config lookup. Maps to llm.useCases.<useCase>.* properties. */
    private String useCase;

    /** Optional. Conversation history for multi-turn chat (legacy shape). */
    private List<ConversationMessage> conversationHistory;

    /** Optional. Full provider-neutral messages; takes precedence when non-empty. */
    private List<LlmChatMessage> messages;

    /** Optional. Tool definitions available to the model. */
    private List<LlmToolDefinition> tools;

    /** Optional. Whether the provider should stream the response. */
    private Boolean stream;
}
