package org.example.lifecomposer.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.example.lifecomposer.config.LlmConfig.UseCaseConfig;
import org.example.lifecomposer.dto.ConversationMessage;
import org.example.lifecomposer.dto.LlmRequestDto;
import org.example.lifecomposer.dto.LlmResponseDto;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger LOG = LogManager.getLogger(OpenAiCompatibleLlmClient.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final UseCaseConfig config;
    private final OkHttpClient httpClient;
    private final String apiKey;

    public OpenAiCompatibleLlmClient(UseCaseConfig config) {
        this.config = config;
        this.apiKey = resolveApiKey(config.getApiKeyEnv());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeoutMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeoutMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    OpenAiCompatibleLlmClient(UseCaseConfig config, OkHttpClient client, String apiKey) {
        this.config = config;
        this.apiKey = apiKey;
        this.httpClient = client;
    }

    private static String resolveApiKey(String envVarName) {
        if (envVarName == null || envVarName.isBlank()) {
            return null;
        }
        String value = System.getenv(envVarName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    @Override
    public LlmResponseDto chat(LlmRequestDto request) {
        String errorMsg = null;

        // Ollama local models don't need API key
        boolean requiresApiKey = !"ollama".equalsIgnoreCase(config.getProvider());
        if (requiresApiKey && apiKey == null) {
            errorMsg = "API key env var '" + config.getApiKeyEnv() + "' is not set or empty";
            return buildFallbackResponse(errorMsg);
        }

        String url = config.getBaseUrl() + "/chat/completions";
        JsonObject payload = buildPayload(request);
        String json = new Gson().toJson(payload);

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json; charset=utf-8");
        // Ollama local models don't need Authorization header
        if (apiKey != null) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String bodyStr = "";
                if (response.body() != null) {
                    try {
                        bodyStr = response.body().string();
                    } catch (IOException ignored) {
                    }
                }
                // FIX: truncate provider error body to limit data leakage (max 200 chars)
                final int MAX_ERROR_BODY_LEN = 200;
                String sanitizedBody = bodyStr.length() > MAX_ERROR_BODY_LEN
                        ? bodyStr.substring(0, MAX_ERROR_BODY_LEN) + "...(truncated)"
                        : bodyStr;
                errorMsg = "HTTP " + response.code() + " from " + config.getBaseUrl()
                        + (sanitizedBody.isEmpty() ? "" : " — " + sanitizedBody);
                LOG.warn("LLM [{}] HTTP {} failure: {} (model={}, useCase={})",
                        config.getProvider(), response.code(), errorMsg,
                        config.getModel(), config);
                return buildFallbackResponse(errorMsg);
            }

            String body = response.body() != null ? response.body().string() : "";
            String content = parseContentFromBody(body);
            if (content == null) {
                errorMsg = "Unexpected response format from provider";
                LOG.warn("LLM [{}] unexpected response format: {} (model={})",
                        config.getProvider(), errorMsg, config.getModel());
                return buildFallbackResponse(errorMsg);
            }

            LlmResponseDto dto = new LlmResponseDto();
            dto.setContent(content);
            dto.setMocked(false);
            dto.setProvider(config.getProvider());
            dto.setModel(config.getModel());
            dto.setErrorMessage(null);
            return dto;

        } catch (IOException e) {
            errorMsg = "Network error: " + e.getClass().getSimpleName() + " — " + e.getMessage();
            LOG.warn("LLM [{}] network error: {} (model={}, url={})",
                    config.getProvider(), errorMsg, config.getModel(), url);
            return buildFallbackResponse(errorMsg);
        }
    }
    private LlmResponseDto buildFallbackResponse(String errorMsg) {
        LlmResponseDto dto = new LlmResponseDto();
        dto.setMocked(true);
        dto.setProvider(config.getProvider());
        dto.setModel(config.getModel());
        dto.setContent(
                "[Fallback] Request degraded: " + errorMsg
                        + ". Original request: useCase='" + config + "'"
        );
        dto.setErrorMessage(errorMsg);
        return dto;
    }

    private JsonObject buildPayload(LlmRequestDto request) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.getModel());

        if (config.getTemperature() != null) {
            root.addProperty("temperature", config.getTemperature());
        }

        JsonArray messages = new JsonArray();

        if (request != null && request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", request.getSystemPrompt());
            messages.add(sys);

        }


        // Insert conversation history between system prompt and current user message
        if (request != null && request.getConversationHistory() != null) {
            for (ConversationMessage histMsg : request.getConversationHistory()) {
                JsonObject hist = new JsonObject();
                hist.addProperty("role", histMsg.getRole());
                hist.addProperty("content", histMsg.getContent());
                messages.add(hist);
            }
        }

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", request != null && request.getMessage() != null
                ? request.getMessage()
                : "");
        messages.add(user);

        root.add("messages", messages);

        if (request != null && request.getMaxTokens() != null) {
            root.addProperty("max_tokens", request.getMaxTokens());
        }

        return root;
    }

    private String parseContentFromBody(String body) {
        try {
            JsonObject json = new Gson().fromJson(body, JsonObject.class);
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonElement first = choices.get(0);
                JsonObject message = first.getAsJsonObject().getAsJsonObject("message");
                if (message != null) {
                    JsonElement contentEl = message.get("content");
                    if (contentEl != null && !contentEl.isJsonNull()) {
                        return contentEl.getAsString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public boolean isAvailable() {
        // Ollama local models are always available (no API key required)
        if ("ollama".equalsIgnoreCase(config.getProvider())) {
            return true;
        }
        return apiKey != null && !apiKey.isBlank();
    }
    @Override
    public String getProvider() {
        return config.getProvider();
    }

    @Override
    public String getModel() {
        return config.getModel();
    }
}
