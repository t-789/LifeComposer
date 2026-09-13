package org.example.lifecomposer.Service;

import org.example.lifecomposer.dto.LlmResponseDto;

/**
 * Callback contract for streaming chat completions. The default methods make
 * non-streaming consumers usable without boilerplate.
 */
public interface LlmStreamListener {

    default void onThinkingStart() {
    }

    default void onReasoningDelta(String delta) {
    }

    /** Called when the provider explicitly ends the reasoning phase. */
    default void onThinkingEnd() {
    }

    default void onContentDelta(String delta) {
    }

    default void onToolCallDelta() {
    }

    default void onComplete(LlmResponseDto response) {
    }

    default void onError(LlmResponseDto error) {
    }
}
