package org.example.lifecomposer.agent;

/** Structured tool result handed back to the model. */
public record ToolResult(boolean ok, Object data, String errorCode, String message) {

    public static ToolResult ok(Object data) {
        return new ToolResult(true, data, null, null);
    }

    public static ToolResult error(String errorCode, String message) {
        return new ToolResult(false, null, errorCode, message);
    }
}
