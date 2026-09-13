package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A provider-neutral chat message. The agent layer uses this shape for system,
 * user, assistant (including tool calls) and tool result messages.
 */
@Getter
@Setter
public class LlmChatMessage {

    private String role;
    private String content;
    /** Tool name for role=tool. */
    private String name;
    /** Corresponding call id for role=tool. */
    private String toolCallId;
    /** Tool calls for role=assistant. */
    private List<LlmToolCall> toolCalls;

    public LlmChatMessage() {
    }

    public LlmChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static LlmChatMessage user(String content) {
        return new LlmChatMessage("user", content);
    }

    public static LlmChatMessage system(String content) {
        return new LlmChatMessage("system", content);
    }

    public static LlmChatMessage assistant(String content) {
        return new LlmChatMessage("assistant", content);
    }

    public static LlmChatMessage toolResult(String callId, String name, String content) {
        LlmChatMessage message = new LlmChatMessage("tool", content);
        message.setToolCallId(callId);
        message.setName(name);
        return message;
    }

    public static LlmChatMessage assistantToolCalls(List<LlmToolCall> toolCalls) {
        LlmChatMessage message = new LlmChatMessage("assistant", null);
        message.setToolCalls(toolCalls);
        return message;
    }
}
