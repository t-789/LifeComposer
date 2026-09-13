package org.example.lifecomposer.rag;

import org.example.lifecomposer.Entity.RagChunk;
import org.example.lifecomposer.Repository.RagChunkRepository;
import org.example.lifecomposer.config.RagConfig;
import org.example.lifecomposer.embedding.EmbeddingClient;
import org.example.lifecomposer.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagSearchServiceTest {

    @Test
    void sortsBySimilarityThenChunkIdAndFiltersLowScoresAndMissingEmbeddings() {
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(anyString()))
                .thenReturn(new EmbeddingResult(List.of(1.0, 0.0), "test-model", 2));
        when(embeddingClient.getModel()).thenReturn("test-model");
        when(embeddingClient.isEnabled()).thenReturn(true);

        RagChunkRepository repository = mock(RagChunkRepository.class);
        RagChunk high = chunk("rag_002", "[0.9,0.1]");
        RagChunk top = chunk("rag_001", "[1.0,0.0]");
        RagChunk orthogonal = chunk("rag_003", "[0.0,1.0]");
        RagChunk invalid = chunk("rag_004", "not-json");
        when(repository.findAllWithEmbedding()).thenReturn(List.of(high, orthogonal, invalid, top));

        RagConfig config = new RagConfig();
        config.setDefaultTopK(5);
        config.setMinSimilarity(0.2);
        RagSearchService service = new RagSearchService(embeddingClient, repository, config);

        List<RagHit> hits = service.search("数学建模", null);

        assertEquals(2, hits.size());
        assertEquals("rag_001", hits.get(0).chunkId());
        assertEquals("rag_002", hits.get(1).chunkId());
        assertTrue(hits.get(0).similarity() >= hits.get(1).similarity());
    }

    @Test
    void respectsRequestedTopKAndMaximum() {
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(anyString()))
                .thenReturn(new EmbeddingResult(List.of(1.0), "test-model", 1));
        when(embeddingClient.isEnabled()).thenReturn(true);

        RagChunkRepository repository = mock(RagChunkRepository.class);
        when(repository.findAllWithEmbedding()).thenReturn(List.of(
                chunk("rag_001", "[1.0]"),
                chunk("rag_002", "[1.0]"),
                chunk("rag_003", "[1.0]")));

        RagConfig config = new RagConfig();
        config.setDefaultTopK(5);
        config.setMaxTopK(2);
        config.setMinSimilarity(0.2);
        RagSearchService service = new RagSearchService(embeddingClient, repository, config);

        List<RagHit> hits = service.search("query", 10);
        assertEquals(2, hits.size());
    }

    private RagChunk chunk(String id, String embeddingJson) {
        RagChunk chunk = new RagChunk();
        chunk.setChunkId(id);
        chunk.setTitle(id);
        chunk.setText("text");
        chunk.setEmbeddingJson(embeddingJson);
        return chunk;
    }
}
