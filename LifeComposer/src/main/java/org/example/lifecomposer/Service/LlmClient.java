package org.example.lifecomposer.Service;

import org.example.lifecomposer.dto.LlmRequestDto;
import org.example.lifecomposer.dto.LlmResponseDto;

public interface LlmClient {

    LlmResponseDto chat(LlmRequestDto request);

    /**
     * Streaming variant. Implementations that cannot stream may keep the
     * default (unsupported) behaviour; the agent layer only uses streaming
     * clients for /api/chat/stream.
     */
    default void chatStream(LlmRequestDto request, LlmStreamListener listener) {
        throw new UnsupportedOperationException(
                "streaming is not supported by provider " + getProvider());
    }

    boolean isAvailable();

    String getProvider();

    String getModel();
}
