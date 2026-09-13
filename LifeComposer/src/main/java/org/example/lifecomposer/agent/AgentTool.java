package org.example.lifecomposer.agent;

import com.google.gson.JsonObject;

import java.util.Map;

/** A server-registered, read-only agent tool. */
public interface AgentTool {

    String name();

    /** Tool description sent to the LLM. */
    String description();

    /** Short user-facing description shown during SSE streaming. */
    String displayDescription();

    Map<String, Object> parameterSchema();

    ToolResult execute(AgentToolContext context, JsonObject arguments);
}
