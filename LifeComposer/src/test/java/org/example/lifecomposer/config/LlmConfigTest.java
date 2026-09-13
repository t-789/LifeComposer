package org.example.lifecomposer.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class LlmConfigTest {

    @Autowired
    private LlmConfig llmConfig;

    @Test
    void qaUseCaseResolvesToDeepseekFlashDefaults() {
        LlmConfig.UseCaseConfig qa = llmConfig.resolveOrDefault("qa");

        assertEquals("deepseek", qa.getProvider());
        assertEquals("https://api.deepseek.com/v1", qa.getBaseUrl());
        assertEquals("deepseek-flash", qa.getModel());
        assertEquals("DEEPSEEK_API_KEY", qa.getApiKeyEnv());
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
        assertNotNull(qa.getApiKeyEnv());
    }

    @Test
    void allUseCasesExistWithDeepseekFlashDefaults() {
        for (LlmUseCase useCase : LlmUseCase.values()) {
            LlmConfig.UseCaseConfig config = llmConfig.resolveOrDefault(useCase.configKey());

            assertNotNull(config);
            assertEquals("deepseek", config.getProvider());
            assertEquals("https://api.deepseek.com/v1", config.getBaseUrl());
            assertEquals("deepseek-flash", config.getModel());
            assertFalse(config.isEnabled());
        }
    }

    @Test
    void chatUseCaseIsRegistered() {
        LlmConfig.UseCaseConfig chat = llmConfig.resolveOrDefault("chat");

        assertEquals("deepseek", chat.getProvider());
        assertEquals("deepseek-flash", chat.getModel());
    }
}
