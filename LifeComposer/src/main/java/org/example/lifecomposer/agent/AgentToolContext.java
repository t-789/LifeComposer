package org.example.lifecomposer.agent;

/** Execution context for a tool call. Always bound to the authenticated user. */
public record AgentToolContext(Integer userId) {
}
