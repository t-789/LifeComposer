package org.example.lifecomposer.config;

/**
 * LLM use cases supported by LifeComposer. The config map is keyed by
 * {@link #configKey()} (lowercase) so Spring relaxed binding can populate it.
 */
public enum LlmUseCase {
    QA,
    PLANNING,
    PROFILE,
    SQL,
    CHAT;

    /** Lowercase key matching {@code llm.useCases.<key>.*} property names. */
    public String configKey() {
        return name().toLowerCase();
    }
}
