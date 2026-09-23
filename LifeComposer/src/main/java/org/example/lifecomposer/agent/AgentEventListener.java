package org.example.lifecomposer.agent;

import java.time.Instant;
import java.util.Map;

/** Callback contract used by the SSE layer to render agent process events. */
public interface AgentEventListener {

    default void onThinkingStart(Instant startedAt) {
    }

    /** Versioned prompt ids used for this agent turn. */
    default void onPromptVersions(Map<String, String> versions) {
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

    /** JSON array of pending profile change candidates produced by a tool call. */
    default void onProfileChangeProposal(String proposalsJson) {
    }

    /** Emitted after a proposal tool call: the current turn ends without an LLM answer. */
    default void onAwaitingConfirmation(String proposalsJson) {
    }

    default void onDone() {
    }
}
