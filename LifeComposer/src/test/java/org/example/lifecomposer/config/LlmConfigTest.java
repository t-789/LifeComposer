package org.example.lifecomposer.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class LlmConfigTest {

    @Autowired
    private LlmConfig llmConfig;

    @Test
    void qaUseCaseResolvesToOllamaDefaults() {
        LlmConfig.UseCaseConfig qa = llmConfig.resolveOrDefault("qa");

        assertEquals("ollama", qa.getProvider());
        assertEquals("http://localhost:11434/v1", qa.getBaseUrl());
        assertEquals("lfm2.5:8b", qa.getModel());
    }

    @Test
    void defaultUseCaseIsNotEnabled() {
        LlmConfig.UseCaseConfig qa = llmConfig.resolveOrDefault("qa");

        assertFalse(qa.isEnabled());
    }

    @Test
    void unknownUseCaseReturnsSafeDefaultNotNull() {
        LlmConfig.UseCaseConfig unknown = llmConfig.resolveOrDefault("nonexistent-use-case");

        assertNotNull(unknown);
        assertFalse(unknown.isEnabled());
    }

    @Test
    void missingApiKeyEnvVarDoesNotCrashConfigLoading() {
        assertNotNull(llmConfig);

        LlmConfig.UseCaseConfig qa = llmConfig.resolveOrDefault("qa");
        assertNotNull(qa);
        assertTrue(qa.getApiKeyEnv() == null || qa.getApiKeyEnv().isEmpty());
    }

    @Test
    void allFourRequiredUseCasesExistWithValidDefaults() {
        for (LlmUseCase useCase : LlmUseCase.values()) {
            LlmConfig.UseCaseConfig config = llmConfig.resolveOrDefault(useCase.configKey());

            assertNotNull(config);
            assertEquals("ollama", config.getProvider());
            assertEquals("http://localhost:11434/v1", config.getBaseUrl());
            assertEquals("lfm2.5:8b", config.getModel());
            assertFalse(config.isEnabled());
            assertEquals(30000, config.getTimeoutMillis());
        }
    }
}
