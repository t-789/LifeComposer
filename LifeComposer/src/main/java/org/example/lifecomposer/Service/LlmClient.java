package org.example.lifecomposer.Service;

import org.example.lifecomposer.dto.LlmRequestDto;
import org.example.lifecomposer.dto.LlmResponseDto;

public interface LlmClient {
    LlmResponseDto chat(LlmRequestDto request);
    boolean isAvailable();
    String getProvider();
    String getModel();
}
