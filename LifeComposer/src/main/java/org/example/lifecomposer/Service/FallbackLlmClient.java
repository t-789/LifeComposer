package org.example.lifecomposer.Service;

import org.example.lifecomposer.config.LlmConfig.UseCaseConfig;
import org.example.lifecomposer.dto.LlmRequestDto;
import org.example.lifecomposer.dto.LlmResponseDto;

public class FallbackLlmClient implements LlmClient {

    private final String provider;
    private final String model;

    public FallbackLlmClient(UseCaseConfig config) {
        this.provider = config != null ? config.getProvider() : "mock";
        this.model = config != null ? config.getModel() : "fallback";
    }

    public FallbackLlmClient(String provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    @Override
    public LlmResponseDto chat(LlmRequestDto request) {
        LlmResponseDto response = new LlmResponseDto();
        response.setMocked(true);
        response.setProvider("mock");
        response.setModel(this.model);
        String echoed = request != null && request.getMessage() != null
                ? request.getMessage()
                : "";
        int inputLen = echoed.length();
        response.setContent(
                "[Fallback] Received " + inputLen + " characters for use-case '"
                        + (request != null ? request.getUseCase() : "none")
                        + "'. Configure a real LLM provider to get live responses."
        );
        response.setErrorMessage(null);
        return response;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getProvider() {
        return "mock";
    }

    @Override
    public String getModel() {
        return this.model;
    }
}
