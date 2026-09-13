package org.example.lifecomposer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Local Ollama embedding configuration. Kept separate from generative LLM
 * use-case config: embeddings must not consume a cloud API key and must never
 * be mixed with chat completion fallbacks.
 */
@Configuration
@ConfigurationProperties(prefix = "embedding")
@Getter
@Setter
public class EmbeddingConfig {

    /** When false the importer records embedding as SKIPPED and RAG search reports unavailable. */
    private boolean enabled = true;

    /** OpenAI-compatible embeddings base, e.g. http://localhost:11434/v1 */
    private String baseUrl = "http://localhost:11434/v1";

    /** Ollama embedding model. */
    private String model = "nomic-embed-text-v2-moe:latest";

    private int timeoutMillis = 30000;

    public String embeddingsUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/embeddings";
    }
}
