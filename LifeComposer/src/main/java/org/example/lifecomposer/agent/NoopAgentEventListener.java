package org.example.lifecomposer.agent;

/** No-op listener for the legacy non-streaming /api/chat/send path. */
public final class NoopAgentEventListener implements AgentEventListener {

    public static final NoopAgentEventListener INSTANCE = new NoopAgentEventListener();

    private NoopAgentEventListener() {
    }
}
