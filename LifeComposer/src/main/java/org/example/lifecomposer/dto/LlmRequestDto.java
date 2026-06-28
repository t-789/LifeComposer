package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * Request DTO for LLM interaction. Carries the user message, optional system
 * prompt, sampling parameters, and the use-case key for config lookup.
 */
@Getter
@Setter
public class LlmRequestDto {

    /** Required. The user message or prompt to send to the LLM. */
    private String message;

    /** Optional. System prompt to set behavior/context. */
    private String systemPrompt;

    /** Optional. Sampling temperature (0.0–2.0), or null to use provider default. */
    private Double temperature;

    /** Optional. Maximum tokens to generate, or null for provider default. */
    private Integer maxTokens;

    /** Required for config lookup. Maps to llm.useCases.<useCase>.* properties. */
    private String useCase;

    /** Optional. Conversation history for multi-turn chat. */
    private List<ConversationMessage> conversationHistory;
}
