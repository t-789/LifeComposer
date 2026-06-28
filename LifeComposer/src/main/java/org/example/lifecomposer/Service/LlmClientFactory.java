package org.example.lifecomposer.Service;

import org.example.lifecomposer.config.LlmConfig;
import org.example.lifecomposer.config.LlmConfig.UseCaseConfig;
import org.springframework.stereotype.Service;

@Service
public class LlmClientFactory {

    private final LlmConfig config;

    public LlmClientFactory(LlmConfig config) {
        this.config = config;
    }

    /**
     * Returns an LlmClient for the given use-case name.
     * Never returns null. Falls back to FallbackLlmClient for
     * unknown, null, or disabled use-cases.
     */
    public LlmClient getClient(String useCaseName) {
        UseCaseConfig uc = config.resolveOrDefault(useCaseName);
        if (!uc.isEnabled()) {
            return new FallbackLlmClient(uc);
        }
        return new OpenAiCompatibleLlmClient(uc);
    }
}
