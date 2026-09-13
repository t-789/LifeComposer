package org.example.lifecomposer.rag;

/** One ranked RAG chunk returned to the agent tool layer. */
public record RagHit(
        String chunkId,
        String title,
        String text,
        String sourceType,
        String sourceUrl,
        String sourceFile,
        String pageOrSection,
        String relatedResourceId,
        double similarity
) {
}
