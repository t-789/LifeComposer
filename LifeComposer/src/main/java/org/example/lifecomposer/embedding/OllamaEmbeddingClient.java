package org.example.lifecomposer.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.config.EmbeddingConfig;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Calls the fixed Ollama OpenAI-compatible endpoint:
 * {@code POST {baseUrl}/embeddings} with {@code {"model": "...", "input": "..."}}.
 * Reads {@code data[0].embedding}; dimensions come from the response length.
 * No API key is read, logged or sent.
 */
@Service
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final Logger LOG = LogManager.getLogger(OllamaEmbeddingClient.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_ERROR_BODY_LEN = 200;

    private final EmbeddingConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    public OllamaEmbeddingClient(EmbeddingConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeoutMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeoutMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (!config.isEnabled()) {
            throw new EmbeddingException("embedding disabled by configuration");
        }
        if (text == null || text.isBlank()) {
            throw new EmbeddingException("embedding input is blank");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.getModel());
        payload.addProperty("input", text);

        Request request = new Request.Builder()
                .url(config.embeddingsUrl())
                .post(RequestBody.create(gson.toJson(payload), JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json; charset=utf-8")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                String sanitized = body.length() > MAX_ERROR_BODY_LEN
                        ? body.substring(0, MAX_ERROR_BODY_LEN) + "...(truncated)"
                        : body;
                String message = "HTTP " + response.code() + " from embeddings endpoint"
                        + (sanitized.isBlank() ? "" : " — " + sanitized);
                LOG.warn("Ollama embedding failed: {}", message);
                throw new EmbeddingException(message);
            }
            return parseEmbedding(body);
        } catch (IOException e) {
            String message = "embedding network error: " + e.getClass().getSimpleName();
            LOG.warn("Ollama embedding network error", e);
            throw new EmbeddingException(message, e);
        }
    }

    private EmbeddingResult parseEmbedding(String body) {
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonArray data = json != null ? json.getAsJsonArray("data") : null;
            if (data == null || data.isEmpty()) {
                throw new EmbeddingException("embeddings response has no data[0]");
            }
            JsonObject first = data.get(0).getAsJsonObject();
            JsonElement embeddingEl = first.get("embedding");
            if (embeddingEl == null || !embeddingEl.isJsonArray()) {
                throw new EmbeddingException("embeddings response data[0].embedding is not an array");
            }
            JsonArray arr = embeddingEl.getAsJsonArray();
            if (arr.isEmpty()) {
                throw new EmbeddingException("embeddings response data[0].embedding is empty");
            }
            List<Double> vector = new ArrayList<>(arr.size());
            for (JsonElement element : arr) {
                vector.add(element.getAsDouble());
            }
            return new EmbeddingResult(vector, config.getModel(), vector.size());
        } catch (EmbeddingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EmbeddingException("invalid embeddings response format", e);
        }
    }

    @Override
    public String getModel() {
        return config.getModel();
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }
}
