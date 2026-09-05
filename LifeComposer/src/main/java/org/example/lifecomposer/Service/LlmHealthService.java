package org.example.lifecomposer.Service;

import jakarta.annotation.PostConstruct;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.config.LlmConfig;
import org.example.lifecomposer.config.LlmConfig.UseCaseConfig;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class LlmHealthService {

    private static final Logger LOG = LogManager.getLogger(LlmHealthService.class);

    private final LlmConfig config;
    private final Map<String, HealthStatus> statusMap = new ConcurrentHashMap<>();

    public LlmHealthService(LlmConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        Map<String, UseCaseConfig> useCases = config.getUseCases();
        if (useCases == null || useCases.isEmpty()) {
            LOG.info("No LLM use cases configured");
            return;
        }
        for (Map.Entry<String, UseCaseConfig> entry : useCases.entrySet()) {
            String name = entry.getKey();
            UseCaseConfig uc = entry.getValue();
            if (!uc.isEnabled()) {
                statusMap.put(name, new HealthStatus(false, "disabled", uc.getProvider(), uc.getModel()));
                continue;
            }
            probeAndStore(name, uc);
        }
    }

    private void probeAndStore(String name, UseCaseConfig uc) {
        String apiKeyEnv = uc.getApiKeyEnv();
        String apiKey = (apiKeyEnv != null && !apiKeyEnv.isBlank()) ? System.getenv(apiKeyEnv) : null;

        // For non-ollama providers, API key is required
        boolean requiresKey = !"ollama".equalsIgnoreCase(uc.getProvider());
        if (requiresKey && (apiKey == null || apiKey.isBlank())) {
            statusMap.put(name, new HealthStatus(false,
                "API key env var '" + apiKeyEnv + "' is not set",
                uc.getProvider(), uc.getModel()));
            LOG.warn("LLM health [{}]: API key not configured (env={})", name, apiKeyEnv);
            return;
        }

        try {
            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

            // Use /models endpoint for lightweight connectivity check
            String baseUrl = uc.getBaseUrl();
            // Strip trailing /v1 or /v1/ to get the API root, then append /models
            String modelsUrl = baseUrl.replaceAll("/v1/?$", "") + "/models";

            Request.Builder reqBuilder = new Request.Builder().url(modelsUrl).get();
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            try (Response resp = client.newCall(reqBuilder.build()).execute()) {
                if (resp.isSuccessful()) {
                    statusMap.put(name, new HealthStatus(true, null, uc.getProvider(), uc.getModel()));
                    LOG.info("LLM health [{}]: available (provider={}, model={})",
                        name, uc.getProvider(), uc.getModel());
                } else {
                    String reason = "HTTP " + resp.code();
                    statusMap.put(name, new HealthStatus(false, reason, uc.getProvider(), uc.getModel()));
                    LOG.warn("LLM health [{}]: unavailable — {}", name, reason);
                }
            }
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            statusMap.put(name, new HealthStatus(false, reason, uc.getProvider(), uc.getModel()));
            LOG.warn("LLM health [{}]: probe failed — {}", name, reason);
        }
    }

    /**
     * Get cached health status for a use case.
     * Returns a default "not configured" status for unknown use cases.
     */
    public HealthStatus getStatus(String useCase) {
        if (useCase == null || useCase.isBlank()) {
            useCase = "qa";
        }
        return statusMap.getOrDefault(useCase.toLowerCase(),
            new HealthStatus(false, "Not configured", "unknown", "unknown"));
    }

    /**
     * Immutable health status record.
     */
    public record HealthStatus(
        boolean available,
        String reason,
        String provider,
        String model
    ) {}
}
