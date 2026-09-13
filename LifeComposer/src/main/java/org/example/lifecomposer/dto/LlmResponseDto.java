package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Response DTO from LLM interaction. Encapsulates content, tool calls,
 * provider metadata, and an explicit failure state (no fabricated fallback).
 */
@Getter
@Setter
public class LlmResponseDto {

    /** The text content returned by the LLM; null when the call failed. */
    private String content;

    /** True only when this response came from an explicit fallback/mock client. */
    private boolean mocked;

    /** True when the provider call failed (network, HTTP, format, missing key). */
    private boolean failed;

    /** Machine-readable error code, e.g. LLM_UNAVAILABLE. */
    private String errorCode;

    /** Provider name: e.g. "ollama", "deepseek", "mock". */
    private String provider;

    /** Model name that produced the response. */
    private String model;

    /** Optional error message when the call failed. Null on success. */
    private String errorMessage;

    /** Tool calls requested by the model, or null/empty for a plain answer. */
    private List<LlmToolCall> toolCalls;

    /** Reasoning content when the provider exposes it separately. */
    private String reasoningContent;

    /** Provider finish reason: stop, tool_calls, length, ... */
    private String finishReason;

    /** Epoch millisecond timestamp when this response was created. */
    private long timestamp;

    public LlmResponseDto() {
        this.timestamp = System.currentTimeMillis();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
