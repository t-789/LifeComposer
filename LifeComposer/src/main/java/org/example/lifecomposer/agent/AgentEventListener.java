package org.example.lifecomposer.agent;

import java.time.Instant;

/** Callback contract used by the SSE layer to render agent process events. */
public interface AgentEventListener {

    default void onThinkingStart(Instant startedAt) {
    }

    default void onThinkingTick(long elapsedSeconds) {
    }

    default void onThinkingEnd(long elapsedMs) {
    }

    default void onToolCall(String callId, String name, String description, String argumentsJson) {
    }

    default void onToolResult(String callId, String name, boolean ok, String resultJson) {
    }

    default void onToken(String delta) {
    }

    default void onAssistantMessage(String content, String createTime) {
    }

    default void onError(String code, String message) {
    }

    default void onDone() {
    }
}
