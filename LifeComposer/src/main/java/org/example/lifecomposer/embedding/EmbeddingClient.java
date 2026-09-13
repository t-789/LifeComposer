package org.example.lifecomposer.embedding;

/** Independent embedding client. Never used for generative chat completions. */
public interface EmbeddingClient {

    EmbeddingResult embed(String text) throws EmbeddingException;

    String getModel();

    boolean isEnabled();
}
