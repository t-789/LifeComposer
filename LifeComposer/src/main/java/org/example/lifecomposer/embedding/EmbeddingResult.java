package org.example.lifecomposer.embedding;

import java.util.List;

/**
 * Result of a single embedding request. Dimensions are taken from the
 * provider response array length and are never hard-coded by callers.
 */
public record EmbeddingResult(List<Double> vector, String model, int dimensions) {

    public EmbeddingResult {
        if (vector == null) {
            throw new IllegalArgumentException("embedding vector must not be null");
        }
        dimensions = vector.size();
    }
}
