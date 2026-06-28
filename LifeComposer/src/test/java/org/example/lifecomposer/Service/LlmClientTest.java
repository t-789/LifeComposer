package org.example.lifecomposer.Service;

import org.example.lifecomposer.config.LlmConfig;
import org.example.lifecomposer.config.LlmConfig.UseCaseConfig;
import org.example.lifecomposer.dto.LlmRequestDto;
import org.example.lifecomposer.dto.LlmResponseDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

class LlmClientTest {

    @Test
    void fallbackReturnsMockedTrueAndProviderMock() {
        FallbackLlmClient client = new FallbackLlmClient("ollama", "lfm2.5:8b");

        assertEquals("mock", client.getProvider());
        assertEquals("lfm2.5:8b", client.getModel());
    }

    @Test
    void fallbackIsAvailableReturnsFalse() {
        FallbackLlmClient client = new FallbackLlmClient("ollama", "test-model");

        assertFalse(client.isAvailable());
    }

    @Test
    void fallbackChatReturnsDeterministicResponseWithEchoedInput() {
        FallbackLlmClient client = new FallbackLlmClient("ollama", "test-model");

        LlmRequestDto req = new LlmRequestDto();
        req.setMessage("Hello world");
        req.setUseCase("qa");

        LlmResponseDto resp = client.chat(req);

        assertTrue(resp.isMocked());
        assertEquals("mock", resp.getProvider());
        assertEquals("test-model", resp.getModel());
        assertTrue(resp.getContent().contains("11"));
        assertTrue(resp.getContent().contains("qa"));
        assertNull(resp.getErrorMessage());
    }

    @Test
    void factoryReturnsFallbackForNullUseCase() {
        LlmConfig config = new LlmConfig();
        LlmClientFactory factory = new LlmClientFactory(config);

        LlmClient client = factory.getClient(null);

        assertNotNull(client);
        assertFalse(client.isAvailable());
        assertEquals("mock", client.getProvider());
    }

    @Test
    void factoryReturnsFallbackForUnknownUseCase() {
        LlmConfig config = new LlmConfig();
        LlmClientFactory factory = new LlmClientFactory(config);

        LlmClient client = factory.getClient("nonexistent-use-case");

        assertNotNull(client);
        assertFalse(client.isAvailable());
        assertEquals("mock", client.getProvider());
    }

    @Test
    void factoryReturnsFallbackForDisabledUseCase() {
        LlmConfig config = new LlmConfig();
        UseCaseConfig qa = new UseCaseConfig();
        qa.setProvider("deepseek");
        qa.setModel("deepseek-chat");
        qa.setEnabled(false);
        config.getUseCases().put("qa", qa);
        LlmClientFactory factory = new LlmClientFactory(config);

        LlmClient client = factory.getClient("qa");

        assertNotNull(client);
        assertFalse(client.isAvailable());
        assertEquals("mock", client.getProvider());
    }

    @Test
    void factoryReturnsRealClientForEnabledUseCase() {
        LlmConfig config = new LlmConfig();
        UseCaseConfig qa = new UseCaseConfig();
        qa.setProvider("deepseek");
        qa.setBaseUrl("http://localhost:11434/v1");
        qa.setModel("deepseek-chat");
        qa.setEnabled(true);
        qa.setApiKeyEnv("DEEPSEEK_API_KEY");
        config.getUseCases().put("qa", qa);
        LlmClientFactory factory = new LlmClientFactory(config);

        LlmClient client = factory.getClient("qa");

        assertNotNull(client);
        assertEquals("deepseek", client.getProvider());
        assertEquals("deepseek-chat", client.getModel());
    }

    @Test
    void openAiClientConstructorDoesNotCrashWithMissingApiKey() {
        UseCaseConfig config = new UseCaseConfig();
        config.setProvider("openai");
        config.setBaseUrl("https://api.openai.com");
        config.setModel("gpt-4");
        config.setEnabled(true);
        config.setApiKeyEnv("NONEXISTENT_API_KEY_12345");

        OpenAiCompatibleLlmClient client = assertDoesNotThrow(() -> new OpenAiCompatibleLlmClient(config));
        assertFalse(client.isAvailable());
    }

    @Test
    void openAiClientHandlesTimeoutGracefully() {
        UseCaseConfig config = new UseCaseConfig();
        config.setProvider("openai");
        config.setBaseUrl("http://localhost:1");
        config.setModel("gpt-4");
        config.setEnabled(true);
        config.setApiKeyEnv("LLM_API_KEY");
        config.setTimeoutMillis(1000);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build();

        OpenAiCompatibleLlmClient llmClient = new OpenAiCompatibleLlmClient(config, client, "test-key-dummy");

        LlmRequestDto req = new LlmRequestDto();
        req.setMessage("test");
        req.setUseCase("qa");

        LlmResponseDto resp = llmClient.chat(req);

        assertTrue(resp.isMocked());
        assertNotNull(resp.getErrorMessage());
    }
}
