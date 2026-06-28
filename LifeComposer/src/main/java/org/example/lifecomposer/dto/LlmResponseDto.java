package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO from LLM interaction. Encapsulates the content, provider
 * metadata, and indicates whether the response is mocked (fallback).
 */
@Getter
@Setter
public class LlmResponseDto {

    /** The text content returned by the LLM (or fallback). */
    private String content;

    /** True if this response came from a fallback/mock, not a real API call. */
    private boolean mocked;

    /** Provider name: e.g. "ollama", "openai", "mock". */
    private String provider;

    /** Model name that produced the response. */
    private String model;

    /** Optional error message when the call degraded to fallback. Null on success. */
    private String errorMessage;

    /** Epoch millisecond timestamp when this response was created. */
    private long timestamp;

    public LlmResponseDto() {
        this.timestamp = System.currentTimeMillis();
    }
}
