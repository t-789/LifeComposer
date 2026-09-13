package org.example.lifecomposer.rag;

/** Raised when RAG retrieval cannot run, e.g. the local embedding provider is down. */
public class RagSearchException extends RuntimeException {

    public RagSearchException(String message) {
        super(message);
    }

    public RagSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
