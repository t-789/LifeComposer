package org.example.lifecomposer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-use-case LLM configuration bound from {@code llm.useCases.*} properties.
 * Defaults point to local Ollama with all use cases disabled, so the app
 * starts without cloud API keys and without Ollama running. Configuration
 * only — does not call any LLM.
 */
@Configuration
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
public class LlmConfig {

    private Map<String, UseCaseConfig> useCases = new HashMap<>();

    /**
     * Resolve a use-case config by name (case-insensitive), returning a safe
     * disabled default for unknown/missing/null use cases. Never returns null.
     */
    public UseCaseConfig resolveOrDefault(String useCaseName) {
        if (useCaseName == null || useCaseName.isBlank()) {
            return UseCaseConfig.defaultConfig();
        }
        return useCases.getOrDefault(useCaseName.toLowerCase(), UseCaseConfig.defaultConfig());
    }

    public UseCaseConfig getUseCase(String name) {
        return resolveOrDefault(name);
    }

    @Getter
    @Setter
    public static class UseCaseConfig {

        private String provider = "ollama";
        private String baseUrl = "http://localhost:11434/v1";
        private String model = "lfm2.5:8b";

        /**
         * Name of the environment variable holding the API key (e.g.
         * "DEEPSEEK_API_KEY"). Stores the NAME only, never the value.
         * Empty means no auth needed (local Ollama) or deterministic fallback.
         */
        private String apiKeyEnv = "";

        /** Disabled by default so startup needs no cloud key or running Ollama. */
        private boolean enabled = false;

        private int timeoutMillis = 30000;

        /** Sampling temperature, or null to let the provider decide. */
        private Double temperature = null;

        public static UseCaseConfig defaultConfig() {
            UseCaseConfig config = new UseCaseConfig();
            config.enabled = false;
            return config;
        }
    }
}
