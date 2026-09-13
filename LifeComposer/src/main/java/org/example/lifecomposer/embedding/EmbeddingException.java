package org.example.lifecomposer.embedding;

/** Raised when the local embedding provider is unavailable or returns an invalid payload. */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
